package com.efocusor.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.efocusor.android.MainViewModel
import com.efocusor.android.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ui by vm.ui.collectAsState()
    val info = ui.info

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设备设置") },
                navigationIcon = {
                    IconButton(onClick = { vm.navigate(Screen.Control) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refreshInfo() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionTitle("运动参数")
            SettingNumberField(
                label = "最大行程 maxSteps (100–9999999)",
                initial = info.maxSteps.toString(),
                suffix = "D",
                validate = { it.toLongOrNull()?.let { v -> v in 100..9_999_999 } == true },
                onCommit = { v -> vm.writeSetting("D $v") { if (it) vm.refreshInfo() } },
            )
            SettingNumberField(
                label = "最大速度 steps/s (1–2000)",
                initial = info.maxSpeed.toString(),
                suffix = "X",
                validate = { it.toIntOrNull()?.let { v -> v in 1..2000 } == true },
                onCommit = { v -> vm.writeSetting("X $v") { if (it) vm.refreshInfo() } },
            )
            SettingNumberField(
                label = "加速度 steps/s² (1–10000)",
                initial = info.acceleration.toString(),
                suffix = "A",
                validate = { it.toIntOrNull()?.let { v -> v in 1..10_000 } == true },
                onCommit = { v -> vm.writeSetting("A $v") { if (it) vm.refreshInfo() } },
            )

            Spacer(Modifier.height(18.dp))
            SectionTitle("电机行为")
            SettingSwitch(
                label = "方向反转 (R)",
                subtitle = "调焦器内外方向对调",
                checked = info.reversed,
                onChange = { on -> vm.writeSetting("R ${if (on) 1 else 0}") { vm.refreshInfo() } },
            )
            SettingSwitch(
                label = "持续保持力矩 (C)",
                subtitle = "关闭可降低发热与功耗,保持精度略降",
                checked = info.hold,
                onChange = { on -> vm.writeSetting("C ${if (on) 1 else 0}") { vm.refreshInfo() } },
            )

            Spacer(Modifier.height(18.dp))
            SectionTitle("温度补偿")
            SettingNumberField(
                label = "温度系数 (T,实际值×1000)",
                initial = ((info.tempCoeff * 1000).toLong()).toString(),
                suffix = "T",
                supporting = "0 = 关闭补偿。正系数 = 温度升高时向外移动",
                validate = { it.toLongOrNull() != null },
                onCommit = { v -> vm.writeSetting("T $v") { vm.refreshInfo() } },
            )
            Text(
                buildString {
                    append("当前温度: ")
                    append(if (info.hasTemp) "%.2f ℃".format(info.lastTemp) else "无传感器")
                    append("  ·  补偿: ")
                    append(if (info.tempComp) "开启" else "关闭")
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(18.dp))
            SectionTitle("只读信息")
            ReadOnlyRow("固件版本", "v${info.firmware}")
            ReadOnlyRow("每转步数 stepsPerRev", "%,d".format(info.stepsPerRev))
            ReadOnlyRow("零点偏移 homeOffset", "%,d".format(info.homeOffsetSteps))
            ReadOnlyRow("温度传感器", if (info.tempSensorPresent) "已连接" else "无")
            if (info.apSsid.isNotBlank()) ReadOnlyRow("设备热点", info.apSsid)

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun ReadOnlyRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingNumberField(
    label: String,
    initial: String,
    suffix: String,
    supporting: String? = null,
    validate: (String) -> Boolean,
    onCommit: (Long) -> Unit,
) {
    var text by rememberSaveable(label) { mutableStateOf(initial) }
    var committed by rememberSaveable(label) { mutableStateOf(initial) }
    val valid = validate(text)
    Column(Modifier.padding(bottom = 12.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter { c -> c.isDigit() || c == '-' } },
            label = { Text(label) },
            singleLine = true,
            isError = text.isNotBlank() && !valid,
            supportingText = supporting?.let { { Text(it, fontSize = 11.sp) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                TextButton(
                    enabled = valid && text != committed,
                    onClick = {
                        text.toLongOrNull()?.let { v ->
                            onCommit(v)
                            committed = text
                        }
                    },
                ) { Text("写入") }
            },
        )
        if (supporting == null) {
            Text(
                "协议命令 $suffix",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
