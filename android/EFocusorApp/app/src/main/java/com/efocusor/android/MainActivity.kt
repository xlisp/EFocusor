package com.efocusor.android

import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.efocusor.android.ui.ConnectScreen
import com.efocusor.android.ui.ControlScreen
import com.efocusor.android.ui.EFocusorTheme
import com.efocusor.android.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            val ui by vm.ui.collectAsState()
            EFocusorTheme(nightMode = ui.nightMode) {
                AppRoot(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // USB device plugged in while the app is open: refresh the picker.
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            viewModel.refreshUsbDevices()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshUsbDevices()
    }

    // Volume rocker = fine focus (+/-10 steps) while the control screen is open.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                viewModel.volumeKey(keyCode == KeyEvent.KEYCODE_VOLUME_UP)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}

@Composable
private fun AppRoot(vm: MainViewModel) {
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.padding(padding)) {
            when (ui.screen) {
                Screen.Connect -> ConnectScreen(vm)
                Screen.Control -> ControlScreen(vm)
                Screen.Settings -> SettingsScreen(vm)
            }
        }
    }
}
