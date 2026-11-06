package com.efocusor.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.efocusor.android.MainViewModel
import com.efocusor.android.connection.UsbDeviceEntry

@Composable
fun ConnectScreen(vm: MainViewModel) {
    val devices by vm.usbDevices.collectAsState()
    val ui by vm.ui.collectAsState()

    var mode by remember { mutableStateOf(if (devices.isEmpty()) 1 else 0) } // 0=USB 1=WiFi
    var selectedDevice by remember { mutableStateOf<UsbDeviceEntry?>(null) }
    var baud by remember { mutableIntStateOf(9600) }
    var host by remember { mutableStateOf("192.168.4.1") }
    var port by remember { mutableStateOf("4030") }

    LaunchedEffect(Unit) { vm.refreshUsbDevices() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Icon(
                imageVector = Icons.Filled.Usb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(20.dp)
                    .size(52.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text("EFocusor", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            "ESP8266 电子调焦器 · USB / WiFi 控制",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = mode == 0,
                onClick = { mode = 0 },
                label = { Text("USB 数据线") },
                leadingIcon = { Icon(Icons.Filled.Usb, null, Modifier.size(18.dp)) },
            )
            FilterChip(
                selected = mode == 1,
                onClick = { mode = 1 },
                label = { Text("WiFi (TCP)") },
                leadingIcon = { Icon(Icons.Filled.Wifi, null, Modifier.size(18.dp)) },
            )
        }
        Spacer(Modifier.height(18.dp))

        if (mode == 0) {
            if (devices.isEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "未检测到 USB 串口设备。\n\n请用 OTG 线把调焦器(Wemos D1 mini / NodeMCU 的 USB 口)接到手机,然后点下面的“重新扫描”。",
                        modifier = Modifier.padding(16.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { vm.refreshUsbDevices() }) { Text("重新扫描") }
            } else {
                devices.forEach { dev ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (dev == selectedDevice)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { selectedDevice = dev },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = dev == selectedDevice, onClick = { selectedDevice = dev })
                            Column(Modifier.padding(end = 8.dp)) {
                                Text(dev.displayName, fontWeight = FontWeight.Medium)
                                Text(
                                    dev.device.deviceName +
                                        " · VID %04X/PID %04X".format(dev.device.vendorId, dev.device.productId) +
                                        if (dev.hasPermission) "" else " · 需要授权",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Text(
                    "波特率(固件默认 9600)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start),
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(9600, 19200, 57600, 115200).forEach { b ->
                        FilterChip(
                            selected = baud == b,
                            onClick = { baud = b },
                            label = { Text("$b") },
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { selectedDevice?.let { vm.connectUsb(it, baud) } },
                    enabled = selectedDevice != null && !ui.connecting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    if (ui.connecting) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(if (ui.connecting) "连接中…" else "连接")
                }
            }
        } else {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("IP 地址") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter { c -> c.isDigit() } },
                label = { Text("端口(ASCOM 协议默认 4030)") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "提示:手机连接调焦器热点 Focuser-xxxx(密码 012345678)后,\nIP 填 192.168.4.1;或与其处于同一局域网。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { vm.connectTcp(host, port.toIntOrNull() ?: 4030) },
                enabled = host.isNotBlank() && !ui.connecting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (ui.connecting) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(if (ui.connecting) "连接中…" else "连接")
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}
