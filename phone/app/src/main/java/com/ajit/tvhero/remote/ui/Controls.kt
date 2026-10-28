package com.ajit.tvhero.remote.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.roundToInt

/** A button that reports press and release separately (games need held buttons). */
@Composable
fun HoldButton(
    label: String? = null,
    icon: ImageVector? = null,
    size: Dp = 64.dp,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    round: Boolean = true,
    contentDescription: String? = label,
    onDown: () -> Unit,
    onUp: () -> Unit = {},
) {
    val view = LocalView.current
    var pressed by remember { mutableStateOf(false) }
    val shape = if (round) CircleShape else RoundedCornerShape(16.dp)
    Box(
        Modifier.size(size).clip(shape)
            .background(if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else color)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onDown()
                    try { tryAwaitRelease() } finally { pressed = false; onUp() }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            icon != null -> Icon(icon, contentDescription)
            label != null -> Text(label, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** A thumbstick: reports x, y in -1..1 while dragged, and 0, 0 when let go. */
@Composable
fun Stick(size: Dp = 140.dp, onMove: (Float, Float) -> Unit) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
            .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
            .pointerInput(Unit) {
                val radius = this.size.width / 2f
                detectDragGestures(
                    onDragStart = { start -> knob = clamp(start - Offset(radius, radius), radius); report(knob, radius, onMove) },
                    onDragEnd = { knob = Offset.Zero; onMove(0f, 0f) },
                    onDragCancel = { knob = Offset.Zero; onMove(0f, 0f) },
                ) { change, drag ->
                    change.consume()
                    knob = clamp(knob + drag, radius)
                    report(knob, radius, onMove)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.offset { IntOffset(knob.x.roundToInt(), knob.y.roundToInt()) }
            .size(size / 2.6f).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)))
    }
}

private fun clamp(o: Offset, r: Float): Offset {
    val d = hypot(o.x, o.y)
    return if (d <= r) o else o * (r / d)
}

private fun report(o: Offset, r: Float, onMove: (Float, Float) -> Unit) = onMove(o.x / r, o.y / r)
