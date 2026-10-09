package app.pawcount

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/* HomeScreen.kt - greeting, the hero card for the soonest countdown, and a two-column grid of the rest. */

@Composable
fun HomeScreen(vm: AppViewModel) {
    val data by vm.data.collectAsState()
    val now by rememberNow()
    val reduce = LocalReduceMotion.current
    var sort by rememberSaveable { mutableStateOf("soonest") }
    val items = data.countdowns.filter { !it.archived }.map { it to compute(it, now) }.filter { it.second.phase != Phase.PAST }
        .sortedWith(compareBy({ if (it.second.phase == Phase.TODAY) 0 else 1 }, { it.second.target }))
    val hero = items.firstOrNull()
    var rest = items.drop(1)
    if (sort == "type") rest = rest.sortedWith(compareBy({ vm.art.meta.typeOrder.indexOf(it.first.type) }, { it.second.target }))
    val pastCount = data.countdowns.count { compute(it, now).phase == Phase.PAST }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 130.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow(java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())))
                    H(greeting() + data.settings.profileName.let { if (it.isBlank()) "" else ", $it" }, 32)
                    if (items.isNotEmpty()) Muted(if (items.size == 1) "1 adventure on the way" else "${items.size} adventures on the way", size = 16)
                }
                Box(Modifier.clickable(role = Role.Button, onClickLabel = "Profile and settings") { vm.push(Route.Settings) }.semantics { contentDescription = "Profile and settings" }) { Avatar(vm, 46.dp) }
            }
            Box(Modifier.padding(top = 18.dp))
            if (hero == null) {
                EmptyHome(vm, pastCount)
            } else {
                HeroCard(vm, hero.first, hero.second, reduce)
                if (rest.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        H("Coming up", 22, Modifier.weight(1f))
                        Box(Modifier.width(190.dp)) { Seg(listOf("soonest" to "Soonest", "type" to "By type"), sort, { sort = it }, label = "Sort countdowns") }
                    }
                    rest.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            row.forEach { (c, k) -> Box(Modifier.weight(1f)) { GridCard(vm, c, k, reduce) } }
                            if (row.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                }
                Muted("Tap a card to meet the dog", Modifier.fillMaxWidth().padding(top = 12.dp), align = TextAlign.Center, size = 13)
            }
        }
        BoxScopeTabBar(vm, Route.Home, Modifier.align(Alignment.BottomCenter))
    }
}

private fun greeting(): String { val h = LocalDateTime.now().hour; return when { h < 5 -> "Late night, huh?"; h < 12 -> "Good morning"; h < 18 -> "Good afternoon"; else -> "Good evening" } }

@Composable
private fun EmptyHome(vm: AppViewModel, past: Int) {
    val demo = remember { Countdown("empty", "", "custom", "2030-01-01T00:00", "UTC", false, false, System.currentTimeMillis(), Dog("scott", "Scott"), "#E8A15C", DisplayMode.FULL) }
    val k = remember { compute(demo.copy(createdAt = System.currentTimeMillis() - DAY), System.currentTimeMillis()) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(34.dp)).clip(RoundedCornerShape(34.dp))) {
            SceneView(demo, k, vm.art, vm.fonts, SceneSize.HERO, stage = Stage.NAP, reduceMotion = LocalReduceMotion.current)
        }
        H("Nothing to wait for… yet", 26, Modifier.padding(top = 20.dp), TextAlign.Center)
        Muted((if (past > 0) "Your past adventures live in Memories. " else "") + "Start a countdown and Scott will do the rest.", Modifier.padding(top = 8.dp), 16, TextAlign.Center)
        Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("New countdown", { vm.push(Route.Flow(null)) }, Modifier.fillMaxWidth(), icon = "plus")
            GhostButton("Peek at sample adventures", { vm.loadSamples(); vm.say("Seven sample adventures added 🐾") })
        }
    }
}

@Composable
private fun HeroCard(vm: AppViewModel, c: Countdown, k: Computed, reduce: Boolean) {
    val pc = LocalPaw.current
    val accent = accentColor(c.accent)
    val type = vm.art.meta.types[c.type]!!
    Column(Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(34.dp)).clip(RoundedCornerShape(34.dp)).background(pc.surface)
        .clickable(role = Role.Button) { vm.push(Route.Detail(c.id)) }
        .semantics { contentDescription = "${c.title}. ${spoken(k)} left. ${describeDog(c, k)}" }) {
        SceneView(c, k, vm.art, vm.fonts, SceneSize.HERO, reduceMotion = reduce)
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(stageLabel(c, k), filled = accent, textColor = onAccent(accent), icon = k.stage.icon)
                Chip(type.short, icon = type.icon)
            }
            H(c.title, 28, Modifier.padding(top = 10.dp))
            Muted(if (k.phase == Phase.TODAY) "Happening today" else fmtInstant(k.target, k.zone, c.allDay), size = 14)
            Numerals(c, k, NumSize.HERO, accent, Modifier.padding(top = 12.dp))
            Leash(c, k, accent, Modifier.padding(top = 14.dp))
        }
    }
}

@Composable
private fun GridCard(vm: AppViewModel, c: Countdown, k: Computed, reduce: Boolean) {
    val pc = LocalPaw.current
    val accent = accentColor(c.accent)
    Column(Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp)).background(pc.surface)
        .clickable(role = Role.Button) { vm.push(Route.Detail(c.id)) }
        .semantics { contentDescription = "${c.title}. ${shortCount(c, k)}. ${stageSentence(c, k)}" }) {
        SceneView(c, k, vm.art, vm.fonts, SceneSize.CARD, reduceMotion = reduce)
        Column(Modifier.padding(start = 13.dp, end = 13.dp, top = 11.dp, bottom = 14.dp)) {
            H(c.title, 17, maxLinesCompat())
            H(shortCount(c, k), 22, Modifier.padding(top = 3.dp, bottom = 7.dp))
            Chip(stageLabel(c, k), filled = accent, textColor = onAccent(accent), small = true, icon = k.stage.icon)
        }
    }
}

private fun maxLinesCompat() = Modifier
