package com.efocusor.android

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.efocusor.android.connection.FocuserConnection
import com.efocusor.android.connection.FocuserInfo
import com.efocusor.android.connection.FocuserProtocol
import com.efocusor.android.connection.TcpFocuserConnection
import com.efocusor.android.connection.UsbDeviceEntry
import com.efocusor.android.connection.UsbDeviceRegistry
import com.efocusor.android.connection.UsbSerialFocuserConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

enum class Screen { Connect, Control, Settings }

data class UiState(
    val screen: Screen = Screen.Connect,
    val connecting: Boolean = false,
    val connectedLabel: String? = null,
    val info: FocuserInfo = FocuserInfo(),
    /** Steps accumulated while the wheel is being dragged, null when idle. */
    val wheelDelta: Long? = null,
    val commWarnings: Int = 0,
    val nightMode: Boolean = false,
    val wheelStepsPerTurn: Int = 1000,
    val invertWheel: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        const val ACTION_USB_PERMISSION = "com.efocusor.android.USB_PERMISSION"
        private const val POLL_INTERVAL_MS = 400L
        private const val INFO_EVERY_POLLS = 8          // ~3.2 s
        private const val WHEEL_SEND_MIN_INTERVAL_MS = 120L
        private const val MAX_COMM_WARNINGS = 6
    }

    private val prefs = app.getSharedPreferences("efocusor", Context.MODE_PRIVATE)

    private val _ui = MutableStateFlow(
        UiState(
            nightMode = prefs.getBoolean("nightMode", false),
            wheelStepsPerTurn = prefs.getInt("wheelStepsPerTurn", 1000),
            invertWheel = prefs.getBoolean("invertWheel", false),
        )
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _usbDevices = MutableStateFlow<List<UsbDeviceEntry>>(emptyList())
    val usbDevices: StateFlow<List<UsbDeviceEntry>> = _usbDevices.asStateFlow()

    var connection: FocuserConnection? = null
        private set

    private var pollJob: Job? = null
    private val commWarnings = AtomicInteger(0)

    /** Last commanded absolute target, or null when the motor is settled. */
    private var commandedTarget: Long? = null

    // Wheel drag session state
    private var wheelActive = false
    private var wheelStartBase = 0L     // absolute target when the drag began
    private var wheelAccumulated = 0L   // raw signed steps since drag began (pre-invert)
    private var wheelLastSentAt = 0L
    private var wheelLastSentTarget = 0L

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            refreshUsbDevices()
            _messages.tryEmit(if (granted) "USB 权限已授予" else "USB 权限被拒绝")
        }
    }

    init {
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            app.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            app.registerReceiver(usbReceiver, filter)
        }
        refreshUsbDevices()
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(usbReceiver) }
        disconnect(silent = true)
        super.onCleared()
    }

    fun refreshUsbDevices() {
        _usbDevices.value = UsbDeviceRegistry.list(getApplication())
    }

    fun requestUsbPermission(entry: UsbDeviceEntry) {
        val manager = UsbDeviceRegistry.manager(getApplication())
        val intent = PendingIntent.getBroadcast(
            getApplication(),
            0,
            Intent(ACTION_USB_PERMISSION).setPackage(getApplication<Application>().packageName),
            PendingIntent.FLAG_IMMUTABLE,
        )
        manager.requestPermission(entry.device, intent)
    }

    // ------------------------------------------------------------------ connect

    fun connectUsb(entry: UsbDeviceEntry, baud: Int) {
        val manager = UsbDeviceRegistry.manager(getApplication())
        if (!manager.hasPermission(entry.device)) {
            requestUsbPermission(entry)
            return
        }
        connect { UsbSerialFocuserConnection(manager, entry.device, entry.port, baud) }
    }

    fun connectTcp(host: String, port: Int) {
        connect { TcpFocuserConnection(host.trim(), port) }
    }

    private fun connect(make: () -> FocuserConnection) {
        if (_ui.value.connecting || connection != null) return
        _ui.update { it.copy(connecting = true) }
        viewModelScope.launch {
            val conn = try {
                withContext(Dispatchers.IO) { make().also { it.open() } }
            } catch (e: Exception) {
                _ui.update { it.copy(connecting = false) }
                _messages.emit("连接失败: ${e.message ?: e.javaClass.simpleName}")
                return@launch
            }
            connection = conn
            commWarnings.set(0)
            commandedTarget = null
            _ui.update {
                it.copy(connecting = false, connectedLabel = conn.label, screen = Screen.Control)
            }
            val version = runCatching { send("V") }.getOrNull()
            _messages.emit(
                if (version != null && !FocuserProtocol.isErr(version)) {
                    "已连接 ${conn.label} · $version"
                } else {
                    "已连接 ${conn.label}"
                }
            )
            startPolling()
        }
    }

    fun disconnect(silent: Boolean = false) {
        pollJob?.cancel()
        pollJob = null
        wheelActive = false
        wheelAccumulated = 0
        commandedTarget = null
        connection?.let { c -> runCatching { c.close() } }
        connection = null
        _ui.update {
            UiState(
                nightMode = it.nightMode,
                wheelStepsPerTurn = it.wheelStepsPerTurn,
                invertWheel = it.invertWheel,
            )
        }
        if (!silent) _messages.tryEmit("已断开")
    }

    // ------------------------------------------------------------------ polling

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            var tick = 0
            while (isActive && connection != null) {
                if (!wheelActive) {
                    val statusReply = runCatching { send("G") }.getOrNull()
                    val status = statusReply?.let { FocuserProtocol.parseStatus(it) }
                    if (status != null) {
                        onCommOk(status.position, status.isMoving)
                        // Long JSON refresh only while stationary: keeps the short
                        // motion-poll cadence the firmware is tuned for.
                        if (tick % INFO_EVERY_POLLS == 0 && !status.isMoving) {
                            runCatching { send("I") }.getOrNull()?.let { infoReply ->
                                FocuserProtocol.parseInfo(infoReply)?.let { info ->
                                    _ui.update { it.copy(info = info) }
                                }
                            }
                        }
                    } else if (statusReply != null && FocuserProtocol.isErr(statusReply)) {
                        onCommOk()
                    } else {
                        onCommFail()
                    }
                    tick++
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun onCommOk(position: Long? = null, moving: Boolean? = null) {
        commWarnings.set(0)
        _ui.update { st ->
            st.copy(
                commWarnings = 0,
                info = if (position != null || moving != null) {
                    st.info.copy(
                        position = position ?: st.info.position,
                        isMoving = moving ?: st.info.isMoving,
                        target = commandedTarget ?: st.info.target,
                    )
                } else st.info,
            )
        }
    }

    private fun onCommFail() {
        val n = commWarnings.incrementAndGet()
        _ui.update { it.copy(commWarnings = n) }
        if (n >= MAX_COMM_WARNINGS) {
            _messages.tryEmit("通信多次失败,已断开")
            disconnect(silent = true)
        }
    }

    // ------------------------------------------------------------------ motion

    fun moveBy(delta: Long) {
        val base = commandedTarget ?: _ui.value.info.position
        moveToInternal(base + delta)
    }

    fun moveTo(target: Long) = moveToInternal(target)

    private fun moveToInternal(target: Long) {
        val clamped = target.coerceIn(0, _ui.value.info.maxSteps)
        commandedTarget = clamped
        _ui.update { it.copy(info = it.info.copy(target = clamped)) }
        viewModelScope.launch {
            val reply = runCatching { send("M $clamped") }.getOrNull()
            if (reply == null) {
                onCommFail()
            } else if (FocuserProtocol.isErr(reply)) {
                commandedTarget = null
                _messages.emit("移动失败: ${FocuserProtocol.errText(reply)}")
            } else {
                FocuserProtocol.parseStatus(reply)?.let { onCommOk(it.position, it.isMoving) } ?: onCommOk()
            }
        }
    }

    fun halt() {
        viewModelScope.launch {
            val reply = runCatching { send("S") }.getOrNull()
            if (reply == null) {
                onCommFail()
            } else {
                commandedTarget = null
                onCommOk()
            }
        }
    }

    fun setPosition(value: Long) {
        viewModelScope.launch {
            val reply = runCatching { send("P $value") }.getOrNull()
            if (reply == null) {
                onCommFail()
            } else if (FocuserProtocol.isErr(reply)) {
                _messages.emit("设置位置失败: ${FocuserProtocol.errText(reply)}")
            } else {
                commandedTarget = null
                FocuserProtocol.parseStatus(reply)?.let { onCommOk(it.position, it.isMoving) } ?: onCommOk()
                _messages.emit("当前位置已设为 $value")
            }
        }
    }

    // ------------------------------------------------------------------ wheel

    fun wheelBegin() {
        wheelActive = true
        wheelAccumulated = 0
        wheelStartBase = commandedTarget ?: _ui.value.info.position
        wheelLastSentTarget = wheelStartBase
        _ui.update { it.copy(wheelDelta = 0L) }
    }

    /** Raw (pre-invert) signed steps accumulated since drag start, reported incrementally. */
    fun wheelDelta(deltaSteps: Long) {
        if (!wheelActive) return
        wheelAccumulated += deltaSteps
        val sign = if (_ui.value.invertWheel) -1L else 1L
        val target = (wheelStartBase + wheelAccumulated * sign).coerceIn(0, _ui.value.info.maxSteps)
        _ui.update { it.copy(wheelDelta = wheelAccumulated * sign) }
        val now = System.currentTimeMillis()
        if (target != wheelLastSentTarget && now - wheelLastSentAt >= WHEEL_SEND_MIN_INTERVAL_MS) {
            wheelLastSentTarget = target
            wheelLastSentAt = now
            commandedTarget = target
            viewModelScope.launch {
                val reply = runCatching { send("M $target") }.getOrNull()
                if (reply == null) onCommFail() else onCommOk()
            }
        }
    }

    fun wheelEnd() {
        if (!wheelActive) return
        wheelActive = false
        val sign = if (_ui.value.invertWheel) -1L else 1L
        val target = (wheelStartBase + wheelAccumulated * sign).coerceIn(0, _ui.value.info.maxSteps)
        val sentFull = target == wheelLastSentTarget && commandedTarget == target
        wheelAccumulated = 0
        _ui.update { it.copy(wheelDelta = null) }
        if (sentFull) return
        if (target != wheelStartBase) {
            commandedTarget = target
            viewModelScope.launch {
                val reply = runCatching { send("M $target") }.getOrNull()
                if (reply == null) onCommFail()
                else FocuserProtocol.parseStatus(reply)?.let { onCommOk(it.position, it.isMoving) } ?: onCommOk()
            }
        } else {
            commandedTarget = null
        }
    }

    // ------------------------------------------------------------------ settings

    fun writeSetting(command: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val reply = runCatching { send(command) }.getOrNull()
            when {
                reply == null -> {
                    onCommFail()
                    onDone(false)
                }
                FocuserProtocol.isErr(reply) -> {
                    _messages.emit("写入失败: ${FocuserProtocol.errText(reply)}")
                    onDone(false)
                }
                else -> onDone(true)
            }
        }
    }

    fun refreshInfo() {
        viewModelScope.launch {
            val reply = runCatching { send("I") }.getOrNull()
            if (reply == null) {
                onCommFail()
            } else {
                FocuserProtocol.parseInfo(reply)?.let { info -> _ui.update { it.copy(info = info) } }
                onCommOk()
            }
        }
    }

    // ------------------------------------------------------------------ ui prefs

    fun setNightMode(on: Boolean) {
        prefs.edit().putBoolean("nightMode", on).apply()
        _ui.update { it.copy(nightMode = on) }
    }

    fun setWheelStepsPerTurn(value: Int) {
        val v = value.coerceIn(10, 100_000)
        prefs.edit().putInt("wheelStepsPerTurn", v).apply()
        _ui.update { it.copy(wheelStepsPerTurn = v) }
    }

    fun setInvertWheel(on: Boolean) {
        prefs.edit().putBoolean("invertWheel", on).apply()
        _ui.update { it.copy(invertWheel = on) }
    }

    fun navigate(screen: Screen) {
        _ui.update { it.copy(screen = screen) }
        if (screen == Screen.Settings) refreshInfo()
    }

    // ------------------------------------------------------------------ helpers

    private suspend fun send(command: String): String = withContext(Dispatchers.IO) {
        val conn = connection ?: throw java.io.IOException("not connected")
        conn.transceive(command)
    }

    /** Volume rocker acts as fine focus while the control screen is open. */
    fun volumeKey(up: Boolean) {
        if (connection == null || _ui.value.screen != Screen.Control) return
        moveBy(if (up) 10L else -10L)
    }
}
