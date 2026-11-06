package com.efocusor.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The simulated focus wheel: drag around the knob to move the focuser,
 * exactly like turning the knob on a real crayford focuser.
 *
 * One full revolution equals [stepsPerTurn] steps. Clockwise = inward (positive)
 * unless inverted in the app settings. Every 30 degrees of rotation produces a
 * tick haptic so you can feel the detents without looking.
 */
@Composable
fun FocusWheel(
    modifier: Modifier = Modifier,
    diameter: Dp = 290.dp,
    stepsPerTurn: Int,
    position: Long,
    target: Long,
    maxSteps: Long,
    wheelDelta: Long?,
    inverted: Boolean,
    onWheelBegin: () -> Unit,
    onWheelDelta: (Long) -> Unit,
    onWheelEnd: () -> Unit,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val activeTickColor = MaterialTheme.colorScheme.primary
    val ringColor = MaterialTheme.colorScheme.outline
    val progressColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)

    // Persistent visual rotation so the knob keeps its orientation between drags.
    var visualRotation by remember { mutableFloatStateOf(0f) }

    // Per-drag gesture state.
    var lastAngle by remember { mutableFloatStateOf(0f) }
    var totalDeg by remember { mutableFloatStateOf(0f) }
    var emittedSteps by remember { mutableLongStateOf(0L) }
    var lastHapticZone by remember { mutableLongStateOf(0L) }
    var gestureValid by remember { mutableFloatStateOf(-1f) } // radius when gesture became valid

    val deadzone = with(density) { 36.dp.toPx() }

    fun angleAt(p: Offset, center: Offset): Float =
        (atan2(p.y - center.y, p.x - center.x) * 180.0 / PI).toFloat()

    Box(
        modifier = modifier
            .size(diameter)
            .pointerInput(stepsPerTurn, inverted) {
                val center = Offset(size.width / 2f, size.height / 2f)
                detectDragGestures(
                    onDragStart = { offset ->
                        val radius = hypot(offset.x - center.x, offset.y - center.y)
                        gestureValid = if (radius > deadzone) 1f else 0f
                        if (gestureValid > 0f) {
                            lastAngle = angleAt(offset, center)
                            totalDeg = 0f
                            emittedSteps = 0L
                            lastHapticZone = 0L
                            onWheelBegin()
                        }
                    },
                    onDrag = { change, _ ->
                        val radius = hypot(change.position.x - center.x, change.position.y - center.y)
                        if (radius <= deadzone) return@detectDragGestures
                        if (gestureValid <= 0f) {
                            // Finger re-entered the active ring: re-anchor.
                            gestureValid = 1f
                            lastAngle = angleAt(change.position, center)
                            return@detectDragGestures
                        }
                        val angle = angleAt(change.position, center)
                        var delta = angle - lastAngle
                        while (delta > 180f) delta -= 360f
                        while (delta < -180f) delta += 360f
                        lastAngle = angle
                        totalDeg += delta
                        visualRotation = (visualRotation + delta) % 360f

                        val zone = floor(totalDeg / 30f).toLong()
                        if (zone != lastHapticZone) {
                            lastHapticZone = zone
                            view.performHapticFeedback(
                                android.view.HapticFeedbackConstants.CLOCK_TICK
                            )
                        }

                        val rawSteps = (totalDeg / 360f * stepsPerTurn).toLong()
                        if (rawSteps != emittedSteps) {
                            onWheelDelta(rawSteps - emittedSteps)
                            emittedSteps = rawSteps
                        }
                    },
                    onDragEnd = {
                        if (gestureValid > 0f) {
                            gestureValid = 0f
                            onWheelEnd()
                        }
                        totalDeg = 0f
                        emittedSteps = 0L
                    },
                    onDragCancel = {
                        if (gestureValid > 0f) {
                            gestureValid = 0f
                            onWheelEnd()
                        }
                        totalDeg = 0f
                        emittedSteps = 0L
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val ringStroke = with(density) { Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round) }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val outer = size.minDimension / 2f - 6f
            val inner = outer * 0.74f
            val hub = outer * 0.55f

            // Outer ring
            drawCircle(ringColor, radius = outer, center = c, style = ringStroke)

            // Travel indicator arc: fraction of maxSteps already traversed.
            val sweep = if (maxSteps > 0) (position.toFloat() / maxSteps.toFloat()) * 360f else 0f
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = sweep.coerceIn(0f, 360f),
                useCenter = false,
                topLeft = Offset(c.x - outer, c.y - outer),
                size = androidx.compose.ui.geometry.Size(outer * 2, outer * 2),
                style = ringStroke,
            )

            // Knurled ticks, rotating with the drag.
            val tickCount = 48
            for (i in 0 until tickCount) {
                val a = (visualRotation + i * 360f / tickCount) * PI.toFloat() / 180f
                val long = i % 4 == 0
                val len = if (long) outer * 0.13f else outer * 0.07f
                val w = if (long) 4f else 2f
                val start = Offset(c.x + cos(a) * outer, c.y + sin(a) * outer)
                val end = Offset(c.x + cos(a) * (outer - len), c.y + sin(a) * (outer - len))
                drawLine(
                    color = if (long) activeTickColor else tickColor,
                    start = start,
                    end = end,
                    strokeWidth = w,
                    cap = StrokeCap.Round,
                )
            }

            // Inner hub ring
            drawCircle(ringColor, radius = inner, center = c, style = ringStroke)
            drawCircle(tickColor.copy(alpha = 0.12f), radius = hub, center = c)

            // Fixed index marker at 12 o'clock
            drawLine(
                color = activeTickColor,
                start = Offset(c.x, c.y - outer - 4f),
                end = Offset(c.x, c.y - outer + 12f),
                strokeWidth = 6f,
                cap = StrokeCap.Round,
            )
        }

        // Center readout: live wheel delta while dragging, otherwise position.
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (wheelDelta != null) {
                Text(
                    text = (if (wheelDelta >= 0) "+" else "") + "%,d".format(wheelDelta),
                    color = if (wheelDelta >= 0) activeTickColor else MaterialTheme.colorScheme.secondary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "目标 %,d".format(target),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            } else {
                Text(
                    text = "%,d".format(position),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "位置 / %,d".format(maxSteps),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
