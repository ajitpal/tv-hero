package com.ajit.tvhero.remote.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ajit.tvhero.remote.ui.hig.Hig
import com.ajit.tvhero.remote.ui.hig.HigType
import kotlin.math.hypot
import kotlin.math.roundToInt

/** A button that reports press and release separately (games need held buttons), with a tap of haptics. */
@Composable
fun HoldButton(
    label: String? = null,
    icon: ImageVector? = null,
    size: Dp = 64.dp,
    color: Color? = null,
    textColor: Color? = null,
    round: Boolean = true,
    contentDescription: String? = label,
    onDown: () -> Unit,
    onUp: () -> Unit = {},
) {
    val view = LocalView.current
    val c = Hig.colors
    var pressed by remember { mutableStateOf(false) }
    val base = color ?: if (c.dark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    Box(
        Modifier.size(size).clip(if (round) CircleShape else RoundedCornerShape(14.dp))
            .background(if (pressed) base.copy(alpha = 0.55f) else base)
            .semantics { role = Role.Button; if (contentDescription != null) this.contentDescription = contentDescription }
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
        val tint = textColor ?: c.label
        when {
            icon != null -> Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.42f))
            label != null -> Text(label, style = HigType.headline.copy(fontSize = if (label.length <= 2) 20.sp else 15.sp, fontWeight = FontWeight.SemiBold), color = tint)
        }
    }
}

/** A thumbstick: x, y in -1..1 while dragged, and 0, 0 when let go. */
@Composable
fun Stick(size: Dp = 132.dp, onMove: (Float, Float) -> Unit) {
    val c = Hig.colors
    var knob by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.size(size).clip(CircleShape).background(if (c.dark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))
            .semantics { contentDescription = "Thumbstick" }
            .pointerInput(Unit) {
                val radius = this.size.width / 2f
                detectDragGestures(
                    onDragStart = { start -> knob = clamp(start - Offset(radius, radius), radius); onMove(knob.x / radius, knob.y / radius) },
                    onDragEnd = { knob = Offset.Zero; onMove(0f, 0f) },
                    onDragCancel = { knob = Offset.Zero; onMove(0f, 0f) },
                ) { change, drag ->
                    change.consume()
                    knob = clamp(knob + drag, radius)
                    onMove(knob.x / radius, knob.y / radius)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.offset { IntOffset(knob.x.roundToInt(), knob.y.roundToInt()) }
            .size(size / 2.4f).clip(CircleShape).background(c.tint))
    }
}

private fun clamp(o: Offset, r: Float): Offset {
    val d = hypot(o.x, o.y)
    return if (d <= r) o else o * (r / d)
}
