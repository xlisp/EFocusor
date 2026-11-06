package com.efocusor.android.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.efocusor.android.MainViewModel
import com.efocusor.android.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(vm: MainViewModel) {
    val ui by vm.ui.collectAsState()
    val info = ui.info

    var showMoveDialog by remember { mutableStateOf(false) }
    var showSetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(ui.connectedLabel ?: "", fontSize = 15.sp, maxLines = 1)
                        Text(
                            if (info.firmware > 0) "固件 v${info.firmware} · ${info.maxSpeed}步/秒"
                            else " ",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.setNightMode(!ui.nightMode) }) {
                        Icon(
                            Icons.Filled.DarkMode,
                            contentDescription = "夜视模式",
                            tint = if (ui.nightMode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { vm.navigate(Screen.Settings) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                    IconButton(onClick = { vm.disconnect() }) {
                        Icon(Icons.Filled.Close, contentDescription = "断开")
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
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(10.dp))

            // -------- status strip --------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusBadge(
                    label = if (info.isMoving) "移动中" else "静止",
                    moving = info.isMoving,
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (info.isMoving && info.target != info.position)
                            "→ %,d".format(info.target) else "%,d".format(info.position),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = buildString {
                            append("目标 %,d / %,d".format(info.target, info.maxSteps))
                            if (info.hasTemp) {
                                append("  ·  %.1f℃".format(info.lastTemp))
                                if (info.tempComp) append(" 补偿")
                            }
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                }
            }

            if (ui.commWarnings > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "⚠ 通信不稳定 (${ui.commWarnings})",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(10.dp))

            // -------- the wheel --------
            FocusWheel(
                stepsPerTurn = ui.wheelStepsPerTurn,
                position = info.position,
                target = info.target,
                maxSteps = info.maxSteps,
                wheelDelta = ui.wheelDelta,
                inverted = ui.invertWheel,
                onWheelBegin = vm::wheelBegin,
                onWheelDelta = vm::wheelDelta,
                onWheelEnd = vm::wheelEnd,
            )

            Spacer(Modifier.height(12.dp))

            // -------- wheel scale --------
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "一圈 =", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOf(100, 500, 1000, 8160).forEach { s ->
                    FilterChip(
                        selected = ui.wheelStepsPerTurn == s,
                        onClick = { vm.setWheelStepsPerTurn(s) },
                        label = { Text("$s 步") },
                    )
                }
                FilterChip(
                    selected = ui.invertWheel,
                    onClick = { vm.setInvertWheel(!ui.invertWheel) },
                    label = { Text("反转") },
                )
            }

            Spacer(Modifier.height(16.dp))

            // -------- quick moves --------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickButton("−1000", Modifier.weight(1f)) { vm.moveBy(-1000) }
                QuickButton("−100", Modifier.weight(1f)) { vm.moveBy(-100) }
                QuickButton("−10", Modifier.weight(1f)) { vm.moveBy(-10) }
                QuickButton("+10", Modifier.weight(1f)) { vm.moveBy(10) }
                QuickButton("+100", Modifier.weight(1f)) { vm.moveBy(100) }
                QuickButton("+1000", Modifier.weight(1f)) { vm.moveBy(1000) }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { vm.halt() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier
                        .weight(1.6f)
                        .height(46.dp),
                ) { Text("■ 停止", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = { vm.moveTo(0) },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                ) {
                    Icon(Icons.Filled.Flag, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("回零")
                }
                OutlinedButton(
                    onClick = { vm.setPosition(0) },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                ) { Text("置零") }
                OutlinedButton(
                    onClick = { showMoveDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                ) { Text("定位…") }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "音量键 = ±10 步微调 · 拖动滚轮 = 模拟调焦",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showMoveDialog) {
        NumberDialog(
            title = "移动到绝对位置",
            placeholder = info.position.toString(),
            confirmLabel = "移动",
            range = 0L..info.maxSteps,
            onConfirm = { vm.moveTo(it); showMoveDialog = false },
            onDismiss = { showMoveDialog = false },
        )
    }
    if (showSetDialog) {
        NumberDialog(
            title = "把当前位置设为…",
            placeholder = info.position.toString(),
            confirmLabel = "设置",
            range = 0L..info.maxSteps,
            onConfirm = { vm.setPosition(it); showSetDialog = false },
            onDismiss = { showSetDialog = false },
        )
    }
}

@Composable
private fun QuickButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(46.dp)) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusBadge(label: String, moving: Boolean) {
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.small,
        color = if (moving) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (moving) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(angle),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (moving) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun NumberDialog(
    title: String,
    placeholder: String,
    confirmLabel: String,
    range: LongRange,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(placeholder) }
    val value = text.toLongOrNull()
    val valid = value != null && value in range
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { t -> text = t.filter { c -> c.isDigit() || c == '-' } },
                singleLine = true,
                isError = !valid,
                supportingText = {
                    Text("范围 %,d – %,d".format(range.first, range.last))
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
            )
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { value?.let(onConfirm) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
