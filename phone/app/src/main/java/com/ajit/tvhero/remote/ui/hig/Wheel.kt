package com.ajit.tvhero.remote.ui.hig

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Calendar

private val ItemHeight = 40.dp

/** One iOS-style wheel: the centred item is the value. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Wheel(items: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected)
    val center by remember { derivedStateOf {
        state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > 20) 1 else 0
    } }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress to center }.collect { (moving, idx) ->
            if (!moving) onSelect(idx.coerceIn(0, items.lastIndex))
        }
    }
    Box(modifier.height(ItemHeight * 5)) {
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(ItemHeight).clip(RoundedCornerShape(8.dp)).background(Hig.colors.fill))
        LazyColumn(state = state, flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(vertical = ItemHeight * 2), horizontalAlignment = Alignment.CenterHorizontally) {
            items(items.size) { i ->
                val d = kotlin.math.abs(i - center)
                Box(Modifier.height(ItemHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(items[i], style = if (d == 0) HigType.title3 else HigType.body, color = Hig.colors.label,
                        modifier = Modifier.alpha(when (d) { 0 -> 1f; 1 -> 0.55f; else -> 0.3f }))
                }
            }
        }
    }
}

/** A time picker made of wheels: hour, minute (5-minute steps) and AM/PM where the phone uses 12-hour time. */
@Composable
fun TimeWheels(minuteOfDay: Int, onChange: (Int) -> Unit) {
    val twelve = remember { (DateFormat.getTimeInstance(DateFormat.SHORT) as? java.text.SimpleDateFormat)?.toPattern()?.contains('a') ?: true }
    val h = minuteOfDay / 60
    val m = (minuteOfDay % 60) / 5
    var hour = h
    var minute = m * 5
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        if (twelve) {
            val hours = (1..12).map { it.toString() }
            Wheel(hours, (if (h % 12 == 0) 12 else h % 12) - 1, Modifier.width(72.dp)) { i ->
                val h12 = i + 1
                hour = (if (hour >= 12) 12 else 0) + (h12 % 12)
                onChange(hour * 60 + minute)
            }
        } else {
            Wheel((0..23).map { "%02d".format(it) }, h, Modifier.width(72.dp)) { i -> hour = i; onChange(hour * 60 + minute) }
        }
        Wheel((0 until 12).map { "%02d".format(it * 5) }, m, Modifier.width(72.dp)) { i -> minute = i * 5; onChange(hour * 60 + minute) }
        if (twelve) {
            val am = Calendar.getInstance().let { DateFormat.getTimeInstance(DateFormat.SHORT).format(it.apply { set(Calendar.HOUR_OF_DAY, 9) }.time) }
            val amLabel = if (am.contains("AM", true)) listOf("AM", "PM") else listOf("am", "pm")
            Wheel(amLabel, if (h >= 12) 1 else 0, Modifier.width(72.dp)) { i ->
                hour = (hour % 12) + if (i == 1) 12 else 0
                onChange(hour * 60 + minute)
            }
        }
    }
}
