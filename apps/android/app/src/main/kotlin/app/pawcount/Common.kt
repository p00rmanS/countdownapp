package app.pawcount

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/* Common.kt - pieces shared by several screens: toast, tab bar, rolling numerals, the leash progress bar. */

@Composable
fun ToastHost(vm: AppViewModel) {
    val msg = vm.toast
    LaunchedEffect(msg) { if (msg != null) { delay(2800); if (vm.toast == msg) vm.toast = null } }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(msg != null, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
            val c = LocalPaw.current
            Text(msg ?: "", Modifier.padding(horizontal = 20.dp).shadow(8.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(if (c.dark) Color(0xFFFFF1E0) else c.night).padding(horizontal = 18.dp, vertical = 12.dp),
                style = T.body(14, FontWeight.ExtraBold).copy(color = if (c.dark) c.night else Color(0xFFFFF1E0)), textAlign = TextAlign.Center)
        }
    }
}

/** Floating tab bar: Home | + | Memories */
@Composable
fun BoxScopeTabBar(vm: AppViewModel, current: Route, modifier: Modifier = Modifier) {
    val c = LocalPaw.current
    Box(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth().height(68.dp).shadow(10.dp, RoundedCornerShape(34.dp)).clip(RoundedCornerShape(34.dp)).background(c.surface.copy(alpha = 0.94f)), verticalAlignment = Alignment.CenterVertically) {
            Tab("home", "Home", current == Route.Home, Modifier.weight(1f)) { vm.tab(Route.Home) }
            Box(Modifier.width(84.dp))
            Tab("album", "Memories", current == Route.Memories, Modifier.weight(1f)) { vm.tab(Route.Memories) }
        }
        Box(Modifier.align(Alignment.TopCenter).offset(y = (-16).dp).size(66.dp).shadow(10.dp, CircleShape).clip(CircleShape).background(c.kibble)
            .clickable(role = Role.Button) { vm.push(Route.Flow(null)) }.semantics { contentDescription = "New countdown" }, contentAlignment = Alignment.Center) {
            Icon("plus", c.onKibble, 30.dp)
        }
    }
}

@Composable
private fun Tab(icon: String, label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalPaw.current
    Column(modifier.fillMaxSize().clickable(role = Role.Tab, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, if (on) c.kibble else c.ink2, 26.dp)
        Text(label, style = T.body(12, FontWeight.ExtraBold).copy(color = if (on) c.ink else c.ink2))
        Box(Modifier.padding(top = 2.dp).size(5.dp).clip(CircleShape).background(if (on) c.kibble else Color.Transparent))
    }
}

/* ---------------- rolling numerals ---------------- */

/**
 * One rolling digit: a spring moves a position 0..9 and we draw only the glyphs near the window.
 * (Drawn on a Canvas rather than as a column of Text views, which is cheaper and lets the glyphs overlap smoothly.)
 */
@Composable
private fun RollingDigit(d: Int, sizeSp: Int, color: Color) {
    val density = LocalDensity.current
    val wDp = with(density) { (sizeSp * 0.62f).sp.toDp() }
    val hDp = with(density) { (sizeSp * 1.1f).sp.toDp() }
    val pos by animateFloatAsState(d.toFloat(), spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "digit")
    Canvas(Modifier.width(wDp).height(hDp).clipToBounds()) {
        val line = size.height
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fredoka; textSize = sizeSp * density.fontScale * density.density * 1f; textAlign = android.graphics.Paint.Align.CENTER; this.color = color.toArgb()
        }
        val base = line * 0.5f - (p.ascent() + p.descent()) / 2f      // vertically centre a glyph in the window
        drawIntoCanvas { c ->
            for (i in 0..9) {
                val y = (i - pos) * line
                if (y < -line || y > line) continue
                c.nativeCanvas.drawText("$i", size.width / 2f, y + base, p)
            }
        }
    }
}

enum class NumSize { HERO, DETAIL }

@Composable
fun Numerals(c: Countdown, k: Computed, size: NumSize, accent: Color, modifier: Modifier = Modifier) {
    val pc = LocalPaw.current
    if (k.phase == Phase.PAST || (k.phase == Phase.TODAY && k.remaining <= 0)) {
        H(if (k.phase == Phase.PAST) "It happened! 💛" else "It's today! 🎉", if (size == NumSize.HERO) 30 else 40, modifier.fillMaxWidth().padding(vertical = 8.dp), TextAlign.Center, FontWeight.Bold)
        return
    }
    val us = units(k, c.displayMode)
    val full = c.displayMode == DisplayMode.FULL
    val fs = when {
        full -> if (size == NumSize.HERO) 34 else 50
        c.displayMode == DisplayMode.WEEKS -> if (size == NumSize.HERO) 50 else 72
        else -> if (size == NumSize.HERO) 62 else 104
    }
    Row(modifier.fillMaxWidth().semantics { contentDescription = spoken(k) + " left" }, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        for (u in us) {
            val text = u.value.toString().padStart(u.pad, '0')
            val cell = Modifier.then(if (full) Modifier.weight(1f).clip(RoundedCornerShape(if (size == NumSize.HERO) 16.dp else 20.dp)).background(mixColor(pc.surface, accent, 0.15f)).padding(top = 10.dp, bottom = 8.dp) else Modifier.padding(horizontal = 12.dp))
            Column(cell, horizontalAlignment = Alignment.CenterHorizontally) {
                Row { text.forEach { RollingDigit(it - '0', fs, pc.ink) } }
                Text(u.label.uppercase(), style = T.body(if (full) 12 else 15, FontWeight.ExtraBold).copy(color = pc.ink2, letterSpacing = 1.sp))
            }
        }
    }
}

/* ---------------- leash progress ---------------- */

@Composable
fun Leash(c: Countdown, k: Computed, accent: Color, modifier: Modifier = Modifier) {
    val pc = LocalPaw.current
    val pct = k.progress.toFloat()
    val anim by animateFloatAsState(pct, label = "leash")
    val label = when (k.phase) { Phase.UPCOMING -> "Day ${k.daysIn} of ${k.daysTotal}"; Phase.TODAY -> "Today is the day!"; else -> "Made it!" }
    Column(modifier.fillMaxWidth().semantics { contentDescription = "Countdown progress ${(pct * 100).roundToInt()} percent" }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(34.dp), contentAlignment = Alignment.CenterStart) {
                val stripe = Brush.linearGradient(0f to accent, 0.5f to accent, 0.5f to mixColor(accent, Color.Black, 0.24f), 1f to mixColor(accent, Color.Black, 0.24f), tileMode = androidx.compose.ui.graphics.TileMode.Repeated, start = androidx.compose.ui.geometry.Offset.Zero, end = androidx.compose.ui.geometry.Offset(24f, 24f))
                Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(pc.ink.copy(alpha = 0.09f)))
                Box(Modifier.fillMaxWidth(anim.coerceIn(0.001f, 1f)).height(12.dp).clip(RoundedCornerShape(6.dp)).background(stripe))
                BoxWithPaw(anim, accent)
            }
            Text(typeEmoji(c.type), Modifier.padding(start = 10.dp), style = T.body(24))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Muted(label, size = 13); Muted("${(pct * 100).roundToInt()}%", size = 13)
        }
    }
}

@Composable
private fun BoxWithPaw(frac: Float, accent: Color) {
    val pc = LocalPaw.current
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(34.dp)) {
        Box(Modifier.offset(x = (maxWidth - 34.dp) * frac, y = 0.dp).size(34.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(pc.surface), contentAlignment = Alignment.Center) {
            Icon("paw", mixColor(accent, Color.Black, 0.18f), 19.dp)
        }
    }
}

fun typeEmoji(type: String) = when (type) { "intl_trip" -> "✈️"; "vacation" -> "🏝️"; "birthday" -> "🎂"; "anniversary" -> "💍"; "holiday" -> "🎄"; else -> "⭐" }
