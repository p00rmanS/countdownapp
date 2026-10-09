package app.pawcount

import android.graphics.Paint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.toArgb

/*
 * Components.kt - the small reusable pieces: icons, buttons, chips, segmented control, switch, panels, fields,
 * and the bottom sheet. They follow the web app's look (rounded, warm, a "tactile" shadow under buttons).
 */

/* ------------------------------ icons ------------------------------ */

private val circle = { cx: Float, cy: Float, r: Float -> "M${cx - r} $cy a$r $r 0 1 0 ${2 * r} 0 a$r $r 0 1 0 ${-2 * r} 0" }
private val ICONS: Map<String, String> = mapOf(
    "home" to "M3 11.5 12 4l9 7.5M5.5 10v9.5h13V10M10 19.5v-5h4v5",
    "heart" to "M12 20s-7.5-4.6-7.5-10.3A4.2 4.2 0 0 1 12 7.4a4.2 4.2 0 0 1 7.5 2.3C19.5 15.4 12 20 12 20z",
    "album" to "M12 20s-7.5-4.6-7.5-10.3A4.2 4.2 0 0 1 12 7.4a4.2 4.2 0 0 1 7.5 2.3C19.5 15.4 12 20 12 20z",
    "sliders" to "M4 7h9M17 7h3M4 17h3M11 17h9 ${circle(15f, 7f, 2f)} ${circle(9f, 17f, 2f)}",
    "plus" to "M12 5v14M5 12h14", "back" to "m15 5-7 7 7 7", "close" to "m6 6 12 12M18 6 6 18",
    "check" to "m5 12.5 4.5 4.5L19 7.5", "trash" to "M4 7h16M9 7V4.5h6V7M6.5 7l1 13h9l1-13", "edit" to "M4 20h4L19.5 8.5l-4-4L4 16z",
    "bell" to "M6 16.5V11a6 6 0 0 1 12 0v5.5l1.8 2H4.2zM10 21a2 2 0 0 0 4 0",
    "camera" to "M4 8h3l1.8-2.6h6.4L17 8h3v11H4z ${circle(12f, 13.2f, 3.5f)}",
    "archive" to "M4 6h16v4H4zM6 10v10h12V10M10 14h4", "download" to "M12 4v11m0 0-4-4m4 4 4-4M5 20h14", "upload" to "M12 16V5m0 0L8 9m4-4 4 4M5 20h14",
    "chevron" to "m9 5 7 7-7 7", "globe" to "${circle(12f, 12f, 8.5f)} M3.5 12h17M12 3.5c3 3.2 3 13.8 0 17M12 3.5c-3 3.2-3 13.8 0 17",
    "clock" to "${circle(12f, 12f, 8.5f)} M12 7.5V12l3 2",
    "plane" to "M21 15.5v-1.8L13.5 9V4.5a1.5 1.5 0 0 0-3 0V9L3 13.7v1.8l7.5-2.2v4.2L8.5 19v1.5l3.5-1 3.5 1V19l-2-1.5v-4.2z",
    "beach" to "M7 14a5 5 0 0 1 10 0M3 14h18M4 18c1.5-1.2 2.5-1.2 4 0s2.5 1.2 4 0 2.5-1.2 4 0 2.5 1.2 4 0M12 5v1.5M5.6 7.6l1 1M18.4 7.6l-1 1",
    "cake" to "M4 20h16M5 20v-6h14v6M5 16.5c2 1.5 3 1.5 5 0s3-1.5 5 0 2 1 4 0M12 14v-3M12 9.5c-1-1 0-2.4 0-3 1 .8 1.2 2 0 3z",
    "rings" to "M4 14a5 5 0 1 0 10 0a5 5 0 1 0 -10 0M10 14a5 5 0 1 0 10 0a5 5 0 1 0 -10 0M10 4.5h4l1.2 1.7L12 9.4 8.8 6.2z",
    "snow" to "M12 3v18M4.2 7.5l15.6 9M19.8 7.5 4.2 16.5M9.5 4.5 12 6.5l2.5-2M9.5 19.5 12 17.5l2.5 2",
    "star" to "m12 3.5 2.6 5.4 5.9.8-4.3 4.1 1 5.9L12 16.9l-5.2 2.8 1-5.9L3.5 9.7l5.9-.8z",
    "moon" to "M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z",
    "eye" to "M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12zM9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
    "hourglass" to "M7 3.5h10M7 20.5h10M8 3.5c0 4 4 4.5 4 8.5s-4 4.5-4 8.5M16 3.5c0 4-4 4.5-4 8.5s4 4.5 4 8.5",
    "suitcase" to "M5 8h14a1.5 1.5 0 0 1 1.5 1.5v9A1.5 1.5 0 0 1 19 20H5a1.5 1.5 0 0 1-1.5-1.5v-9A1.5 1.5 0 0 1 5 8zM9 8V5.5h6V8M3.5 13h17",
    "bolt" to "M13 3 5 13.5h6L10 21l8-10.5h-6z",
    "sparkles" to "M11 3c.6 4.5 2.5 6.4 7 7-4.5.6-6.4 2.5-7 7-.6-4.5-2.5-6.4-7-7 4.5-.6 6.4-2.5 7-7zM19 3v3M17.5 4.5h3",
    "bone" to "M7 12h10M3 9.5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0M3 14.5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0M17 9.5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0M17 14.5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
    "bowl" to "M3.5 11h17a8.5 8.5 0 0 1-17 0zM9 21h6M8 8.5l1 1.5M12 6l.5 2.5M16 8.5l-1 1.5",
    "ball" to "M3.5 12a8.5 8.5 0 1 0 17.0 0a8.5 8.5 0 1 0 -17.0 0M5 7.5c4 1 6 5 5.5 9M19 7.5c-4 1-6 5-5.5 9",
    "smile" to "M3.5 12a8.5 8.5 0 1 0 17.0 0a8.5 8.5 0 1 0 -17.0 0M8 14c1.2 2 2.5 3 4 3s2.8-1 4-3M9 9.5v.5M15 9.5v.5",
    "ticket" to "M3.5 8.5a2 2 0 0 1 2-2h13a2 2 0 0 1 2 2v1.5a2 2 0 0 0 0 4v1.5a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2V14a2 2 0 0 0 0-4zM14 6.5v11", "repeat" to "M4 11V9a3 3 0 0 1 3-3h11l-3-3M20 13v2a3 3 0 0 1-3 3H6l3 3",
)
private const val PAW = "M4 10.4a2.2 2.9 0 1 0 4.4 0a2.2 2.9 0 1 0 -4.4 0M8 6.4a2.2 3 0 1 0 4.4 0a2.2 3 0 1 0 -4.4 0M12.6 6.4a2.2 3 0 1 0 4.4 0a2.2 3 0 1 0 -4.4 0M16.6 10.4a2.2 2.9 0 1 0 4.4 0a2.2 2.9 0 1 0 -4.4 0M12.5 11.2c3.2 0 6 3.6 6 6.2 0 2-1.8 2.7-3.2 2.4-1.2-.3-1.9-.7-2.8-.7s-1.6.4-2.8.7c-1.4.3-3.2-.4-3.2-2.4 0-2.6 2.8-6.2 6-6.2z"
private val parsed = HashMap<String, android.graphics.Path>()

@Composable
fun Icon(name: String, tint: Color = LocalPaw.current.ink, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        val path = parsed.getOrPut(name) { androidx.core.graphics.PathParser.createPathFromPathData(if (name == "paw") PAW else ICONS[name] ?: "") }
        drawIntoCanvas { c ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = tint.toArgb()
            if (name == "paw") p.style = Paint.Style.FILL
            else { p.style = Paint.Style.STROKE; p.strokeWidth = 2f * s; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND }
            c.nativeCanvas.save(); c.nativeCanvas.scale(s, s)
            if (name != "paw") p.strokeWidth = 2f
            c.nativeCanvas.drawPath(path, p)
            if (name == "more") { p.style = Paint.Style.FILL; listOf(5f, 12f, 19f).forEach { c.nativeCanvas.drawCircle(it, 12f, 1.6f, p) } }
            c.nativeCanvas.restore()
        }
    }
}

/* ------------------------------ text helpers ------------------------------ */

@Composable
fun H(text: String, size: Int = 28, modifier: Modifier = Modifier, align: TextAlign? = null, weight: FontWeight = FontWeight.SemiBold, color: Color? = null) =
    Text(text, modifier, style = T.display(size, weight).let { if (color != null) it.copy(color = color) else it }, textAlign = align)

@Composable
fun B(text: String, modifier: Modifier = Modifier, size: Int = 16, weight: FontWeight = FontWeight.Normal, align: TextAlign? = null, color: Color? = null, maxLines: Int = Int.MAX_VALUE) =
    Text(text, modifier, style = T.body(size, weight).let { if (color != null) it.copy(color = color) else it }, textAlign = align, maxLines = maxLines)

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, size: Int = 14, align: TextAlign? = null) =
    Text(text, modifier, style = T.muted(size), textAlign = align)

@Composable
fun Eyebrow(text: String) = Text(text.uppercase(), style = T.muted(12, FontWeight.ExtraBold).copy(letterSpacing = 1.sp))

/* ------------------------------ buttons ------------------------------ */

/** Big tactile button: a darker copy sits 4dp below and the face sinks onto it when pressed. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, accent: Color = LocalPaw.current.kibble, enabled: Boolean = true, icon: String? = null) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val drop by animateDpAsState(if (pressed) 1.dp else 4.dp, spring(Spring.DampingRatioMediumBouncy), label = "drop")
    val face = if (enabled) accent else accent.copy(alpha = 0.4f)
    val shape = RoundedCornerShape(20.dp)
    Box(modifier) {
        // bottom layer: the darker "edge" visible under the face
        Box(Modifier.matchParentSize().padding(top = 4.dp).clip(shape).background(mixColor(face, Color.Black, 0.38f)))
        Box(
            Modifier.padding(bottom = 4.dp).offset(y = 4.dp - drop).fillMaxWidth().heightIn(min = 54.dp).clip(shape).background(face)
                .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 24.dp), contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (icon != null) Icon(icon, onAccent(accent))
                Text(text, style = T.display(18).copy(color = onAccent(accent)), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false) {
    val c = LocalPaw.current
    Box(modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(18.dp)).border(2.dp, c.line, RoundedCornerShape(18.dp))
        .clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, style = T.display(17).copy(color = if (danger) c.collar else c.ink))
    }
}

@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) = PrimaryButton(text, onClick, modifier.fillMaxWidth(), accent = LocalPaw.current.collar)

@Composable
fun SmallButton(text: String, onClick: () -> Unit, accentSoft: Color, modifier: Modifier = Modifier) {
    Box(modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp)).background(accentSoft).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Text(text, style = T.display(15))
    }
}

@Composable
fun IconButton(icon: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, glass: Boolean = false) {
    val c = LocalPaw.current
    val shape = if (glass) CircleShape else RoundedCornerShape(16.dp)
    Box(
        modifier.size(48.dp).shadow(if (glass) 4.dp else 3.dp, shape).clip(shape).background(if (glass) Color(0xC7FFFFFF) else c.surface)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, if (glass) Color(0xFF3D2616) else c.ink) }
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier, filled: Color? = null, textColor: Color? = null, small: Boolean = false, icon: String? = null) {
    val c = LocalPaw.current
    val shape = RoundedCornerShape(50)
    val m = if (filled != null) Modifier.background(filled, shape) else Modifier.border(1.5.dp, c.line, shape)
    val tint = textColor ?: if (filled != null) c.ink else c.ink2
    Row(modifier.then(m).padding(horizontal = if (small) 9.dp else 11.dp, vertical = if (small) 2.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, tint, if (small) 13.dp else 15.dp); Box(Modifier.width(5.dp)) }
        Text(text, style = T.body(if (small) 12 else 13, FontWeight.ExtraBold).copy(color = tint), maxLines = 1)
    }
}

/* ------------------------------ controls ------------------------------ */

@Composable
fun Seg(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, label: String = "") {
    val c = LocalPaw.current
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.ink.copy(alpha = 0.07f)).padding(4.dp).semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((v, l) in options) {
            val on = v == selected
            val bg by animateColorAsState(if (on) c.surface else Color.Transparent, label = "seg")
            Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(bg).selectable(on, role = Role.RadioButton) { onSelect(v) }.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                Text(l, style = T.body(14, FontWeight.ExtraBold).copy(color = if (on) c.ink else c.ink2), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun PawSwitch(on: Boolean, label: String, onChange: (Boolean) -> Unit) {
    val c = LocalPaw.current
    val x by animateDpAsState(if (on) 22.dp else 0.dp, spring(Spring.DampingRatioMediumBouncy), label = "sw")
    val track by animateColorAsState(if (on) c.kibble else c.ink.copy(alpha = 0.18f), label = "swc")
    Box(Modifier.width(54.dp).height(32.dp).clip(CircleShape).background(track).toggleable(on, role = Role.Switch, onValueChange = onChange).semantics { contentDescription = label }) {
        Box(Modifier.offset(x = 3.dp + x, y = 3.dp).size(26.dp).shadow(2.dp, CircleShape).background(Color.White, CircleShape))
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, flush: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalPaw.current
    Column(modifier.fillMaxWidth().padding(top = 14.dp).shadow(3.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp)).background(c.surface)
        .padding(if (flush) PaddingValues(horizontal = 18.dp, vertical = 4.dp) else PaddingValues(18.dp)), content = content)
}

@Composable
fun PanelTitle(text: String, icon: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, LocalPaw.current.ink2, 20.dp); Box(Modifier.width(8.dp)) }
        H(text, 18, Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(LocalPaw.current.line))

@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "", keyboard: KeyboardOptions = KeyboardOptions.Default, accent: Color = LocalPaw.current.kibble, maxLen: Int = 60, minHeight: Dp = 54.dp, singleLine: Boolean = true) {
    val c = LocalPaw.current
    // the real text area is only as wide as the text, so tapping anywhere in the rounded box must focus it
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val kb = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    Column(modifier.padding(top = 16.dp)) {
        if (label.isNotEmpty()) Text(label, Modifier.padding(bottom = 7.dp), style = T.muted(14, FontWeight.ExtraBold))
        BasicTextField(
            value, { if (it.length <= maxLen) onChange(it) }, Modifier.fillMaxWidth().focusRequester(focus), singleLine = singleLine, keyboardOptions = keyboard,
            textStyle = T.body(17), cursorBrush = SolidColor(accent),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().heightIn(min = minHeight).clip(RoundedCornerShape(18.dp)).background(c.surface).border(2.dp, c.line, RoundedCornerShape(18.dp))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { focus.requestFocus(); kb?.show() }.padding(horizontal = 16.dp, vertical = 14.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = T.body(17).copy(color = c.ink2.copy(alpha = 0.6f)))
                    inner()
                }
            },
        )
    }
}

/** A tappable row used by pickers (country, time zone...) */
@Composable
fun PickerRow(label: String, value: String, onClick: () -> Unit) {
    val c = LocalPaw.current
    Column(Modifier.padding(top = 16.dp)) {
        Text(label, Modifier.padding(bottom = 7.dp), style = T.muted(14, FontWeight.ExtraBold))
        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(18.dp)).background(c.surface).border(2.dp, c.line, RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            B(value, Modifier.weight(1f), 17, maxLines = 1); Icon("chevron", c.ink2, 18.dp)
        }
    }
}

/* ------------------------------ bottom sheet ------------------------------ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PawSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalPaw.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.bg, shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), content = content)
    }
}

@Composable
fun SheetItem(icon: String, text: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = LocalPaw.current
    Row(Modifier.padding(vertical = 4.dp).fillMaxWidth().heightIn(min = 56.dp).shadow(2.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(c.surface).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, if (danger) c.collar else c.ink); Text(text, style = T.body(17, FontWeight.ExtraBold).copy(color = if (danger) c.collar else c.ink))
    }
}
