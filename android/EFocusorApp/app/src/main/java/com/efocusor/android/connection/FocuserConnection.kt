package com.efocusor.android.connection

import android.content.Context
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * One `#`-terminated command / one `#`-terminated reply transport,
 * mirroring the firmware's text protocol used by the ASCOM driver
 * and the INDI driver (see AGENTS.md "Protocol Flow").
 */
interface FocuserConnection {
    val label: String

    fun open()

    fun close()

    /** Sends `command#`, blocks until the `#`-terminated reply arrives or [timeoutMs] passes. */
    fun transceive(command: String, timeoutMs: Long = 1500): String
}

/**
 * Shared framing logic. The firmware only speaks when spoken to over
 * serial/TCP (broadcasts go to the WebSocket), so a strict
 * send -> await-`#` transaction is safe. Firmware 1105 may chunk
 * replies while the motor is stepping; reads are simply continued
 * until the terminator shows up inside the timeout window.
 */
abstract class FramedConnection : FocuserConnection {

    protected var open = false
        private set

    private val lock = Any()
    private val partial = StringBuilder()

    protected abstract fun writeBytes(data: ByteArray)

    /** Blocking read; returns byte count (0 on idle timeout), throws on I/O failure. */
    protected abstract fun readBytes(buf: ByteArray, timeoutMs: Int): Int

    final override fun open() {
        synchronized(lock) {
            check(!open) { "connection already open" }
            doOpen()
            open = true
        }
    }

    final override fun close() {
        synchronized(lock) {
            if (!open) return
            open = false
            try {
                doClose()
            } catch (_: Exception) {
            }
            partial.clear()
        }
    }

    final override fun transceive(command: String, timeoutMs: Long): String {
        synchronized(lock) {
            if (!open) throw IOException("connection closed")
            // Drop anything left over from a timed-out previous transaction.
            partial.clear()
            drain()
            writeBytes("$command#".toByteArray(Charsets.US_ASCII))
            val deadline = System.currentTimeMillis() + timeoutMs
            val buf = ByteArray(256)
            while (true) {
                val remaining = (deadline - System.currentTimeMillis()).toInt()
                if (remaining <= 0) throw IOException("reply timeout: $command")
                val n = try {
                    readBytes(buf, remaining.coerceAtMost(150))
                } catch (e: SocketTimeoutException) {
                    continue
                }
                if (n > 0) {
                    for (i in 0 until n) {
                        when (val c = buf[i].toInt().toChar()) {
                            '#' -> {
                                val frame = partial.toString()
                                partial.clear()
                                return frame
                            }
                            // Reply frames never contain newlines; a bare newline
                            // (boot banner) invalidates whatever we accumulated.
                            '\r', '\n' -> partial.clear()
                            else -> if (partial.length < 512) partial.append(c)
                        }
                    }
                }
            }
        }

    }

    /** Non-blocking drain of any buffered bytes left in the OS/driver queue. */
    private fun drain() {
        val buf = ByteArray(256)
        while (true) {
            val n = try {
                readBytes(buf, 1)
            } catch (_: Exception) {
                return
            }
            if (n <= 0) return
        }
    }

    protected abstract fun doOpen()

    protected abstract fun doClose()
}

/** USB serial via usb-serial-for-android (CH340/CH9102, CP210x, FTDI, PL2303, CDC). */
class UsbSerialFocuserConnection(
    private val port: UsbSerialPort,
    private val baud: Int,
) : FramedConnection() {

    override val label: String = "USB ${port.javaClass.simpleName.replace("Driver", "")} @ $baud"

    override fun doOpen() {
        port.open(UsbSerialPort.FLOW_CONTROL_OFF)
        // Leave DTR/RTS deasserted: asserting them resets Wemos/NodemCU
        // boards through the CH340 auto-program circuit.
        port.dtr = false
        port.rts = false
        port.baudRate = baud
        port.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
    }

    override fun doClose() {
        port.close()
    }

    override fun writeBytes(data: ByteArray) {
        port.write(data, 1000)
    }

    override fun readBytes(buf: ByteArray, timeoutMs: Int): Int = port.read(buf, timeoutMs)
}

/** Raw TCP to the firmware's ASCOM-compatible text protocol (default port 4030). */
class TcpFocuserConnection(
    private val host: String,
    private val port: Int,
) : FramedConnection() {

    override val label: String = "TCP $host:$port"

    private var socket: Socket? = null

    override fun doOpen() {
        val s = Socket()
        s.tcpNoDelay = true
        s.connect(InetSocketAddress(host, port), 3000)
        s.soTimeout = 150
        socket = s
    }

    override fun doClose() {
        socket?.close()
        socket = null
    }

    override fun writeBytes(data: ByteArray) {
        val s = socket ?: throw IOException("socket closed")
        s.getOutputStream().apply {
            write(data)
            flush()
        }
    }

    override fun readBytes(buf: ByteArray, timeoutMs: Int): Int {
        val s = socket ?: throw IOException("socket closed")
        return try {
            s.getInputStream().read(buf, 0, buf.size)
        } catch (e: SocketTimeoutException) {
            0
        }
    }
}

data class UsbDeviceEntry(
    val device: android.hardware.usb.UsbDevice,
    val displayName: String,
    val hasPermission: Boolean,
    val port: UsbSerialPort,
)

object UsbDeviceRegistry {
    fun list(context: Context): List<UsbDeviceEntry> {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        return drivers.flatMap { driver ->
            val device = driver.device
            val name = "${device.manufacturerName ?: "?"} ${device.productName ?: ""}".trim()
                .ifEmpty { driver.javaClass.simpleName }
            driver.ports.map { port ->
                UsbDeviceEntry(
                    device = device,
                    displayName = name,
                    hasPermission = manager.hasPermission(device),
                    port = port,
                )
            }
        }
    }

    fun manager(context: Context): UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager
}
