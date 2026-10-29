package com.ajit.tvhero.remote.ui.hig

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/*
 * A small Human Interface Guidelines kit for Compose: iOS system colours (secondary text and the
 * tint darkened a little in light mode so they pass WCAG AA on white), the iOS type scale, inset
 * grouped lists, 44-pt-plus rows, an iOS switch, segmented controls, a tab bar, sheets and alerts.
 * Every screen in the app is built from these, so the whole app stays consistent.
 */

@Immutable
data class HigColors(
    val groupedBackground: Color,
    val cell: Color,
    val cellPressed: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val tint: Color,
    val destructive: Color,
    val green: Color,
    val orange: Color,
    val fill: Color,
    val bar: Color,
    val dark: Boolean,
)

private val LightColors = HigColors(
    groupedBackground = Color(0xFFF2F2F7), cell = Color.White, cellPressed = Color(0xFFE5E5EA),
    label = Color(0xFF000000), secondaryLabel = Color(0xFF6C6C70), tertiaryLabel = Color(0xFF8E8E93),
    separator = Color(0xFFC6C6C8), tint = Color(0xFF0062CC), destructive = Color(0xFFD70015),
    green = Color(0xFF34C759), orange = Color(0xFFC93400), fill = Color(0x33787880), bar = Color(0xF7F9F9F9), dark = false,
)

private val DarkColors = HigColors(
    groupedBackground = Color(0xFF000000), cell = Color(0xFF1C1C1E), cellPressed = Color(0xFF2C2C2E),
    label = Color.White, secondaryLabel = Color(0xFF98989F), tertiaryLabel = Color(0xFF6E6E73),
    separator = Color(0xFF38383A), tint = Color(0xFF0A84FF), destructive = Color(0xFFFF453A),
    green = Color(0xFF30D158), orange = Color(0xFFFF9F0A), fill = Color(0x5C787880), bar = Color(0xF0161618), dark = true,
)

/** The iOS text styles (points become sp, so the user's font size still applies). */
object HigType {
    val largeTitle = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.37.sp)
    val title1 = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
    val title2 = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
    val title3 = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
    val headline = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 17.sp, lineHeight = 22.sp)
    val callout = TextStyle(fontSize = 16.sp, lineHeight = 21.sp)
    val subhead = TextStyle(fontSize = 15.sp, lineHeight = 20.sp)
    val footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
    val tab = TextStyle(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium)
}

val LocalHig = staticCompositionLocalOf { LightColors }

object Hig {
    val colors: HigColors @Composable get() = LocalHig.current
}

@Composable
fun HigTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) darkColorScheme(primary = c.tint, background = c.groupedBackground, surface = c.cell, onSurface = c.label)
    else lightColorScheme(primary = c.tint, background = c.groupedBackground, surface = c.cell, onSurface = c.label)
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalHig provides c, LocalContentColor provides c.label, LocalTextStyle provides HigType.body) {
            content()
        }
    }
}

// ---------- screens ----------

/**
 * A scrolling screen with an iOS large title that collapses into a small centred title in the
 * navigation bar. [onBack] shows a "< back" button and handles the system Back gesture.
 */
@Composable
fun HigScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    backLabel: String = "Back",
    largeTitle: Boolean = onBack == null,
    trailing: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val c = Hig.colors
    val list = rememberLazyListState()
    val collapsed by remember { derivedStateOf { !largeTitle || list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 80 } }
    if (onBack != null) BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(c.groupedBackground)) {
        Box(Modifier.fillMaxWidth().background(if (collapsed) c.bar else c.groupedBackground).windowInsetsPadding(WindowInsets.statusBars)) {
            Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack).padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ChevronLeft, null, tint = c.tint, modifier = Modifier.size(32.dp))
                        Text(backLabel, style = HigType.body, color = c.tint, maxLines = 1)
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
            }
            if (collapsed) Text(title, style = HigType.headline, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 96.dp))
            if (collapsed) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(0.5.dp).background(c.separator))
        }
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (largeTitle) item("__title") {
                Text(title, style = HigType.largeTitle, color = c.label, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp))
            }
            content()
        }
    }
}

/** An inset grouped section: optional header and footer, rows on a rounded card. */
fun LazyListScope.section(
    key: String,
    header: String? = null,
    footer: String? = null,
    rows: @Composable SectionScope.() -> Unit,
) {
    item(key) {
        val c = Hig.colors
        Column(Modifier.padding(horizontal = 16.dp).padding(top = if (header == null) 20.dp else 24.dp)) {
            if (header != null) Text(header.uppercase(), style = HigType.footnote, color = c.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, bottom = 7.dp))
            Column(Modifier.clip(RoundedCornerShape(10.dp)).background(c.cell)) { SectionScope().rows() }
            if (footer != null) Text(footer, style = HigType.footnote, color = c.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp))
        }
    }
}

/** Puts a hairline between rows, inset to the text like iOS. */
class SectionScope {
    private var count = 0

    @Composable
    fun divider(inset: Dp) {
        if (count++ > 0) Box(Modifier.padding(start = inset).fillMaxWidth().height(0.5.dp).background(Hig.colors.separator))
    }
}

/** A coloured rounded-square icon, like iOS Settings. */
@Composable
fun IconTile(icon: ImageVector, color: Color, size: Dp = 29.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(7.dp)).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.66f))
    }
}

/**
 * The one row: leading icon or image, title and optional subtitle, trailing value, switch or
 * chevron. Height at least 48 dp; the whole row is the touch target.
 */
@Composable
fun SectionScope.Row(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    leading: (@Composable () -> Unit)? = null,
    chevron: Boolean = false,
    titleColor: Color? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    // Last on purpose: `Row("Title") { … }` must mean "on tap". With `trailing` last, that block was
    // composable content and ran on every recomposition (Settings opened its last page by itself).
    onClick: (() -> Unit)? = null,
) {
    val c = Hig.colors
    divider(if (leading != null) 60.dp else 16.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .then(if (onClick != null && enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(15.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = HigType.body, color = if (!enabled) c.tertiaryLabel else titleColor ?: c.label,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = HigType.subhead, color = c.secondaryLabel, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (value != null) Text(value, style = HigType.body, color = c.secondaryLabel, maxLines = 1,
            modifier = Modifier.padding(start = 8.dp, end = if (chevron) 2.dp else 4.dp))
        if (trailing != null) Box(Modifier.padding(start = 8.dp)) { trailing() }
        if (chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.tertiaryLabel, modifier = Modifier.size(22.dp))
    }
}

/** A row with an iOS switch. */
@Composable
fun SectionScope.ToggleRow(title: String, checked: Boolean, subtitle: String? = null, enabled: Boolean = true,
                           leading: (@Composable () -> Unit)? = null, onChange: (Boolean) -> Unit) {
    Row(title, subtitle, leading = leading, enabled = enabled, onClick = { onChange(!checked) }, trailing = {
        IosSwitch(checked, enabled, onChange)
    })
}

/** A centred or leading text button row, e.g. "Forget This TV" in red. */
@Composable
fun SectionScope.ButtonRow(title: String, destructive: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Hig.colors
    divider(16.dp)
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
        Text(title, style = HigType.body, color = when {
            !enabled -> c.tertiaryLabel
            destructive -> c.destructive
            else -> c.tint
        })
    }
}

/** Any custom content inside a section, with the divider handled. */
@Composable
fun SectionScope.Content(padding: PaddingValues = PaddingValues(16.dp), content: @Composable () -> Unit) {
    divider(16.dp)
    Box(Modifier.fillMaxWidth().padding(padding)) { content() }
}

// ---------- controls ----------

/** The iOS switch: 51 × 31, green when on. */
@Composable
fun IosSwitch(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val c = Hig.colors
    val track by animateColorAsState(if (checked) c.green else c.fill, label = "track")
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, spring(dampingRatio = 0.7f, stiffness = 600f), label = "thumb")
    Box(
        Modifier.size(51.dp, 31.dp).clip(CircleShape).background(track.copy(alpha = if (enabled) 1f else 0.45f))
            .clickable(enabled = enabled, role = Role.Switch, interactionSource = remember { MutableInteractionSource() },
                indication = null) { onChange(!checked) },
    ) {
        Box(Modifier.offset(x = x, y = 2.dp).size(27.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(Color.White))
    }
}

/** iOS segmented control. */
@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val c = Hig.colors
    Row(modifier.clip(RoundedCornerShape(9.dp)).background(c.fill).padding(2.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = 32.dp).clip(RoundedCornerShape(7.dp))
                    .background(if (on) (if (c.dark) Color(0xFF636366) else Color.White) else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) }.padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = HigType.footnote.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                    color = c.label, maxLines = 1)
            }
        }
    }
}

/** A filled iOS button (prominent) or a tinted one. */
@Composable
fun HigButton(title: String, modifier: Modifier = Modifier, prominent: Boolean = true, destructive: Boolean = false,
              enabled: Boolean = true, onClick: () -> Unit) {
    val c = Hig.colors
    val color = if (destructive) c.destructive else c.tint
    Box(
        modifier.heightIn(min = 50.dp).clip(RoundedCornerShape(12.dp))
            .background(if (prominent) color.copy(alpha = if (enabled) 1f else 0.4f) else color.copy(alpha = 0.14f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = HigType.headline, color = if (prominent) Color.White else color)
    }
}

/** An iOS search field. */
@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String = "Search", modifier: Modifier = Modifier) {
    val c = Hig.colors
    Row(modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp)).background(c.fill).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Search, null, tint = c.secondaryLabel, modifier = Modifier.size(20.dp))
        Box(Modifier.padding(start = 6.dp).weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = HigType.body, color = c.secondaryLabel)
            BasicTextField(value, onChange, singleLine = true, textStyle = HigType.body.copy(color = c.label),
                cursorBrush = SolidColor(c.tint), modifier = Modifier.fillMaxWidth())
        }
    }
}

/** A text field row inside a section (iOS forms have no outlines). */
@Composable
fun SectionScope.FieldRow(label: String, value: String, onChange: (String) -> Unit, placeholder: String = "",
                          secure: Boolean = false, keyboard: KeyboardType = KeyboardType.Text) {
    val c = Hig.colors
    divider(16.dp)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = HigType.body, color = c.label, modifier = Modifier.width(120.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = HigType.body, color = c.tertiaryLabel)
            BasicTextField(value, onChange, singleLine = true, textStyle = HigType.body.copy(color = c.label),
                cursorBrush = SolidColor(c.tint), visualTransformation = if (secure) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (secure) KeyboardType.Password else keyboard),
                modifier = Modifier.fillMaxWidth())
        }
    }
}

/** A round avatar with the profile's initial. */
@Composable
fun Avatar(name: String, color: Color, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase().ifEmpty { "?" }, color = Color.White,
            style = TextStyle(fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.SemiBold))
    }
}

val AvatarColors = listOf(0xFF3D7BFF, 0xFFE5484D, 0xFF30A46C, 0xFFE8B931, 0xFF8E4EC6,
    0xFFF76B15, 0xFF12A594, 0xFFD6409F, 0xFF6E56CF, 0xFF0090FF).map { Color(it) }

// ---------- tab bar ----------

data class TabItem(val title: String, val icon: ImageVector, val selectedIcon: ImageVector, val badge: Int = 0)

@Composable
fun TabBar(items: List<TabItem>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Hig.colors
    Column(Modifier.fillMaxWidth().background(c.bar).windowInsetsPadding(WindowInsets.navigationBars)) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.separator))
        Row(Modifier.fillMaxWidth().height(52.dp)) {
            items.forEachIndexed { i, t ->
                val on = i == selected
                Column(Modifier.weight(1f).fillMaxSize().clickable(role = Role.Tab, interactionSource = remember { MutableInteractionSource() },
                    indication = null) { onSelect(i) }, horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Box {
                        Icon(if (on) t.selectedIcon else t.icon, t.title, tint = if (on) c.tint else c.tertiaryLabel, modifier = Modifier.size(26.dp))
                        if (t.badge > 0) Box(Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-4).dp).size(18.dp).clip(CircleShape)
                            .background(c.destructive), contentAlignment = Alignment.Center) {
                            Text(t.badge.coerceAtMost(9).toString(), style = HigType.caption.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                        }
                    }
                    Text(t.title, style = HigType.tab, color = if (on) c.tint else c.tertiaryLabel, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

// ---------- alerts and sheets ----------

/** An iOS alert: centred title and message, buttons separated by hairlines. */
@Composable
fun HigAlert(
    title: String,
    message: String? = null,
    onDismiss: () -> Unit,
    content: (@Composable () -> Unit)? = null,
    buttons: List<Pair<String, () -> Unit>>,
    primaryIndex: Int = buttons.lastIndex,
    destructiveIndex: Int = -1,
    enabled: (Int) -> Boolean = { true },
) {
    val c = Hig.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(290.dp).clip(RoundedCornerShape(14.dp)).background(if (c.dark) Color(0xFF2C2C2E) else Color(0xFFF2F2F2))) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = HigType.headline, color = c.label, textAlign = TextAlign.Center)
                if (message != null) Text(message, style = HigType.footnote, color = c.label, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp))
                if (content != null) Box(Modifier.padding(top = 14.dp)) { content() }
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.separator))
            if (buttons.size == 2) {
                Row(Modifier.fillMaxWidth().height(44.dp)) {
                    buttons.forEachIndexed { i, (label, action) ->
                        if (i == 1) Box(Modifier.width(0.5.dp).fillMaxSize().background(c.separator))
                        AlertButton(label, i == primaryIndex, i == destructiveIndex, enabled(i), Modifier.weight(1f), action)
                    }
                }
            } else {
                buttons.forEachIndexed { i, (label, action) ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.separator))
                    AlertButton(label, i == primaryIndex, i == destructiveIndex, enabled(i), Modifier.fillMaxWidth().height(44.dp), action)
                }
            }
        }
    }
}

@Composable
private fun AlertButton(label: String, primary: Boolean, destructive: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Hig.colors
    Box(modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, style = if (primary) HigType.headline else HigType.body,
            color = when { !enabled -> c.tertiaryLabel; destructive -> c.destructive; else -> c.tint })
    }
}

/** A field for use inside an alert. */
@Composable
fun AlertField(value: String, onChange: (String) -> Unit, placeholder: String, secure: Boolean = false,
               keyboard: KeyboardType = KeyboardType.Text) {
    val c = Hig.colors
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(if (c.dark) Color(0xFF1C1C1E) else Color.White)
        .padding(horizontal = 8.dp, vertical = 8.dp)) {
        if (value.isEmpty()) Text(placeholder, style = HigType.subhead, color = c.tertiaryLabel)
        BasicTextField(value, onChange, singleLine = true, textStyle = HigType.subhead.copy(color = c.label),
            cursorBrush = SolidColor(c.tint), visualTransformation = if (secure) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (secure) KeyboardType.Password else keyboard), modifier = Modifier.fillMaxWidth())
    }
}

/** A full-height modal page with Cancel / title / Done, like an iOS sheet. */
@Composable
fun HigSheet(title: String, onCancel: () -> Unit, done: String? = "Done", doneEnabled: Boolean = true, onDone: () -> Unit = onCancel,
             content: LazyListScope.() -> Unit) {
    val c = Hig.colors
    Dialog(onDismissRequest = onCancel, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().padding(top = 40.dp).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).background(c.groupedBackground)) {
            Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(36.dp, 5.dp).clip(CircleShape).background(c.tertiaryLabel.copy(alpha = 0.5f)))
            }
            Box(Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 16.dp)) {
                Text("Cancel", style = HigType.body, color = c.tint, modifier = Modifier.align(Alignment.CenterStart).clickable(onClick = onCancel))
                Text(title, style = HigType.headline, color = c.label, modifier = Modifier.align(Alignment.Center))
                if (done != null) Text(done, style = HigType.headline, color = if (doneEnabled) c.tint else c.tertiaryLabel,
                    modifier = Modifier.align(Alignment.CenterEnd).clickable(enabled = doneEnabled, onClick = onDone))
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp), content = content)
        }
    }
}
