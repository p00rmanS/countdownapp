package app.pawcount

import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * DetailScreen.kt - one countdown full screen: the interactive scene (tap = bark, hold = pet, shake = sneeze),
 * big rolling numerals, the leash progress bar, then the details below (when, checklist, notes, reminders...).
 */

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(vm: AppViewModel, id: String) {
    val data by vm.data.collectAsState()
    val c = data.countdowns.firstOrNull { it.id == id }
    if (c == null) { LaunchedEffect(Unit) { vm.pop() }; return }
    val ctx = LocalContext.current
    val pc = LocalPaw.current
    val now by rememberNow()
    val k = compute(c, now)
    val accent = accentColor(c.accent)
    val reduce = LocalReduceMotion.current
    val ctrl = remember(id) { SceneController() }
    val settings = data.settings
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val past = k.phase == Phase.PAST
    val type = vm.art.meta.types[c.type]!!

    // the dog reacts: say something, buzz, bark
    fun say(text: String) { ctrl.bubble = text }
    LaunchedEffect(ctrl.bubble) { if (ctrl.bubble != null) { delay(2400); ctrl.bubble = null } }
    LaunchedEffect(id) { delay(600); say(when (k.stage) { Stage.NAP -> "Zzz…"; Stage.TODAY -> "IT'S TODAY!!! 🎉"; Stage.ZOOMIES -> "ZOOM!"; Stage.MEMORY -> "Remember this? 💛"; else -> "Woof!" }) }

    // play: treat / feed / ball / tickle, and a joy meter that fades while you are away
    Who.name = Who.of(settings.parentTitle)
    var joy by remember(id) { mutableStateOf(Joy.now(ctx, id)) }
    var needs by remember(id) { mutableStateOf(Joy.needs(ctx, id)) }
    LaunchedEffect(id) {
        delay(2600)
        val (food, water) = Joy.needs(ctx, id)
        if (water < 30f) ctrl.bubble = dogLine(c, compute(c), "thirsty") else if (food < 30f) ctrl.bubble = dogLine(c, compute(c), "hungry")
    }
    val scope = rememberCoroutineScope()
    fun play(what: String) {
        ctrl.hint = false; ctrl.wake(scope)
        val lines = when (what) { "Treat" -> listOf("Nom nom! 🦴", "Best human ever!", "More? 👀"); "Feed" -> listOf("Yum yum yum…"); "Tickle" -> listOf("Hehehe!!", "That tickles! 😆", "Again, again!"); "Water" -> listOf("Glug glug glug!", "Ahh, so refreshing!", "Thank you, ${Who.name}! 💧"); else -> emptyList() }
        when (what) {
            "Ball" -> { ctrl.fetchAt = ctrl.clock; Haptics.play(ctx, settings.haptics, "soft"); ctrl.bubble = dogLine(c, compute(c), "fetch") }
            "Tickle" -> { ctrl.reactions.start("petting", ctrl.clock); ctrl.bubble = lines.random(); Haptics.play(ctx, settings.haptics, "purr"); scope.launch { delay(1600); ctrl.reactions.stop("petting") } }
            else -> { ctrl.bubble = lines.random(); Haptics.play(ctx, settings.haptics, if (what == "Treat") "tick" else "purr"); Sounds.play(settings.sound, "chime")
                needs = when (what) { "Feed" -> Joy.fill(ctx, id, 55f, 0f); "Water" -> Joy.fill(ctx, id, 0f, 60f); else -> Joy.fill(ctx, id, 8f, 0f) }
                scope.launch { repeat(if (what == "Feed" || what == "Water") 5 else 2) { ctrl.reactions.start("bark", ctrl.clock); delay(500) } } }
        }
        val before = joy; joy = Joy.add(ctx, id, when (what) { "Feed" -> 10f; "Ball" -> 6f; else -> 8f })
        if (before < 100f && joy >= 100f) { vm.confetti(); ctrl.bubble = "${c.dog.name} loves you! 💛"; Haptics.play(ctx, settings.haptics, "success") }
    }

    // shake the phone -> sneeze
    DisposableEffect(id) {
        val det = ShakeDetector(ctx) {
            ctrl.hint = false; ctrl.reactions.start("sneeze", ctrl.clock); ctrl.shakenAt = ctrl.clock
            ctrl.bubble = dogLine(c, compute(c), "sneeze"); Haptics.play(ctx, settings.haptics, "success"); Sounds.play(settings.sound, "bark")
        }
        det.start(); onDispose { det.stop() }
    }
    // milestone celebration (100, 50, 30, 10, 7, 1 days) - once per countdown & year
    LaunchedEffect(id) {
        val key = "${c.id}:${k.occYear}:${k.daysCeil}"
        if (k.phase == Phase.UPCOMING && k.daysCeil in vm.art.meta.milestones() && key !in data.celebrated) {
            vm.repo.update { it.copy(celebrated = it.celebrated + key) }
            delay(700); vm.confetti(); vm.say(if (k.daysCeil == 1) "⚡ Zoomies activated! ${c.title} is tomorrow." else "🐕 ${c.dog.name} just counted: ${k.daysCeil} days until ${plainTitle(c.title)}!")
            Haptics.play(ctx, settings.haptics, "success"); Sounds.play(settings.sound, "chime")
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sceneH = minOf(maxWidth * 1.04f, maxHeight * 0.62f).coerceAtLeast(330.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(sceneH)) {
                SceneView(c, k, vm.art, vm.fonts, SceneSize.DETAIL, controller = ctrl, interactive = true, reduceMotion = reduce,
                    onBark = { ctrl.wake(scope); ctrl.bubble = dogLine(c, compute(c), "tap"); Haptics.play(ctx, settings.haptics, "tick"); Sounds.play(settings.sound, "bark") },
                    onPetStart = { ctrl.wake(scope); ctrl.bubble = dogLine(c, compute(c), "pet") }, onPetEnd = {})
                // dark strip so status-bar icons stay readable over a bright scene
                Box(Modifier.fillMaxWidth().height(110.dp).background(Brush.verticalGradient(listOf(Color(0x52140C08), Color.Transparent))))
                Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton("back", "Back", { vm.pop() }, glass = true)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Row(Modifier.shadow(4.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(Color(0xC7FFFFFF)).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(k.stage.icon, Color(0xFF3D2616), 16.dp); Box(Modifier.width(6.dp))
                            Text(stageLabel(c, k), style = T.body(14, FontWeight.ExtraBold).copy(color = Color(0xFF3D2616)))
                        }
                    }
                    IconButton("more", "More options for ${c.title}", { menu = true }, glass = true)
                }
                Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 44.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (ctrl.hint) Text("Tap to bark · Hold to pet · Shake", Modifier.clip(RoundedCornerShape(50)).background(Color(0xA81F2433)).padding(horizontal = 14.dp, vertical = 6.dp), style = T.body(12, FontWeight.ExtraBold).copy(color = Color.White))
                    Text("♥  ${c.dog.name} is ${Joy.label(joy)}${if (needs.second < 30f) " · thirsty" else if (needs.first < 30f) " · hungry" else ""}", Modifier.clip(RoundedCornerShape(50)).background(Color(0xA81F2433)).padding(horizontal = 14.dp, vertical = 6.dp), style = T.body(12, FontWeight.ExtraBold).copy(color = Color.White))
                    Row(Modifier.padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("bone" to "Treat", "bowl" to "Feed", "drop" to "Water", "ball" to "Ball", "smile" to "Tickle").forEach { (iconName, label) ->
                            Column(Modifier.weight(1f).shadow(4.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color(0xEBFFFFFF)).clickable(role = Role.Button, onClickLabel = "$label ${c.dog.name}") { play(label) }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(iconName, Color(0xFF3D2616), 22.dp); Text(label, style = T.body(12, FontWeight.ExtraBold).copy(color = Color(0xFF3D2616)))
                            }
                        }
                    }
                }
            }

            // ---- sheet with the numbers ----
            Column(Modifier.offset(y = (-36).dp).fillMaxWidth().shadow(14.dp, RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)).clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)).background(pc.bg).padding(horizontal = 20.dp).padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(type.short, icon = type.icon)
                    c.destination?.let { Chip("${it.flag} ${it.city ?: it.country}") }
                    if (c.type == "birthday" && k.age != null) Chip("${c.person?.name?.ifBlank { "They" } ?: "They"} turn${if (c.person?.name.isNullOrBlank() || c.person?.name == "You" || c.person?.name == "They") "" else "s"} ${k.age}")
                    if (c.type == "anniversary" && k.together != null) Chip("Together for ${"%,d".format(k.together)} days")
                    if (c.yearly) Chip("Repeats every year")
                }
                H(c.title, 31, Modifier.padding(top = 12.dp), TextAlign.Center)
                val w = whenText(c, k)
                Muted(if (past) w.first else if (k.phase == Phase.TODAY) "Happening today" else w.first, Modifier.padding(top = 4.dp), 15, TextAlign.Center)
                if (w.second != null && !past) Muted("${w.second} your time", size = 13, align = TextAlign.Center)
                if (past) H("${plural(k.daysSince, "day")} ago 💛", 40, Modifier.padding(top = 14.dp), TextAlign.Center, FontWeight.Bold)
                else { Numerals(c, k, NumSize.DETAIL, accent, Modifier.padding(top = 14.dp)); Leash(c, k, accent, Modifier.padding(top = 18.dp)) }
                Text(describeDog(c, k).substringBefore(". ${spoken(k)}") + ".", Modifier.padding(top = 16.dp).clip(RoundedCornerShape(14.dp)).background(mixColor(pc.surface, accent, 0.18f)).padding(horizontal = 14.dp, vertical = 10.dp),
                    style = T.body(14, FontWeight.Bold).copy(color = pc.ink2), textAlign = TextAlign.Center)
            }

            Column(Modifier.offset(y = (-36).dp).padding(horizontal = 20.dp).padding(bottom = 40.dp)) {
                if (past) PhotosPanel(vm, c, accent)
                Panel { PanelTitle("Display"); Seg(DisplayMode.entries.map { it.key to it.label }, c.displayMode.key, { vm.upsert(c.copy(displayMode = DisplayMode.of(it))) }, label = "Countdown display mode") }
                WhenPanel(c, k)
                ChecklistPanel(vm, c, accent)
                NotesPanel(vm, c)
                Panel {
                    PanelTitle("Reminders", "bell"); Muted("At most one per day, never spammy.", Modifier.padding(bottom = 6.dp), 13)
                    c.reminders.forEachIndexed { i, r ->
                        if (i > 0) Divider()
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                            B(r.label, Modifier.weight(1f), 16, FontWeight.Bold)
                            PawSwitch(r.enabled, r.label) { on -> vm.upsert(c.copy(reminders = c.reminders.mapIndexed { j, x -> if (j == i) x.copy(enabled = on) else x })) }
                        }
                    }
                }
                Panel {
                    PanelTitle("Milestones")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        vm.art.meta.milestones().forEach { m ->
                            val got = past || k.phase == Phase.TODAY || (k.daysCeil <= m && k.daysTotal >= m)
                            Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(if (got) accent else pc.ink.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$m", style = T.display(20, FontWeight.Bold).copy(color = if (got) onAccent(accent) else pc.ink2))
                                    Text(if (m == 1) "DAY" else "DAYS", style = T.body(9, FontWeight.ExtraBold).copy(color = if (got) onAccent(accent) else pc.ink2))
                                }
                            }
                        }
                    }
                }
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        DogIcon(vm.art, c.dog, Modifier.size(56.dp))
                        Column { B(c.dog.name, size = 17, weight = FontWeight.ExtraBold); Muted("${vm.art.meta.breeds[c.dog.breed]?.label} · ${vm.art.meta.breeds[c.dog.breed]?.vibe}") }
                    }
                }
            }
        }
    }

    if (menu) PawSheet({ menu = false }) {
        H(c.title, 22, Modifier.fillMaxWidth().padding(bottom = 12.dp), TextAlign.Center)
        SheetItem("edit", "Edit countdown") { menu = false; vm.push(Route.Flow(c.id)) }
        SheetItem("upload", "Share picture card") { menu = false; Share.shareCard(ctx, vm.art, vm.fonts, c) }
        SheetItem("heart", "Share with your person") { menu = false; Share.shareLink(ctx, c) }
        SheetItem("archive", if (c.archived) "Move back to Home" else "Archive to Memories") { menu = false; vm.upsert(c.copy(archived = !c.archived)); vm.say(if (c.archived) "Back on Home" else "Archived to Memories"); if (!c.archived) vm.tab(Route.Memories) }
        SheetItem("trash", "Delete…", danger = true) { menu = false; confirmDelete = true }
    }
    if (confirmDelete) PawSheet({ confirmDelete = false }) {
        H("Delete “${c.title}”?", 22, Modifier.fillMaxWidth(), TextAlign.Center)
        Muted("${c.dog.name} will be very confused. This can’t be undone.", Modifier.fillMaxWidth().padding(vertical = 10.dp), 15, TextAlign.Center)
        DangerButton("Delete forever", { confirmDelete = false; vm.remove(c.id); vm.say("Deleted"); vm.goHome() })
        Box(Modifier.padding(top = 10.dp)) { GhostButton("Keep it", { confirmDelete = false }) }
    }
}

private fun Meta.milestones() = listOf(100, 50, 30, 10, 7, 1)

/** "Thu, Nov 5, 2026 · 2:00 PM" and, when the event is in another time zone, the same moment on the phone's clock */
fun whenText(c: Countdown, k: Computed): Pair<String, String?> {
    val dest = fmtInstant(k.target, k.zone, c.allDay)
    val here = java.time.ZoneId.systemDefault()
    val local = if (c.allDay || k.zone == here || k.zone.rules.getOffset(java.time.Instant.ofEpochMilli(k.target)) == here.rules.getOffset(java.time.Instant.ofEpochMilli(k.target))) null else fmtInstant(k.target, here, false)
    return dest to local
}

@Composable
private fun WhenPanel(c: Countdown, k: Computed) {
    val here = java.time.ZoneId.systemDefault()
    val sameTz = k.zone == here
    val (dest, local) = whenText(c, k)
    Panel {
        PanelTitle("When", "globe")
        Kv(if (sameTz) "Date" else "${c.destination?.city ?: c.timeZone.substringAfterLast('/').replace('_', ' ')} time", dest)
        if (local != null) Kv("Your time", local)
        Kv("Time zone", tzLabel(c.timeZone))
        if (c.yearly) Kv("Repeats", "Every year · next is ${k.occYear}")
    }
}

@Composable
private fun Kv(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Muted(k, Modifier.padding(end = 16.dp), 14.5.toInt()); B(v, size = 14, weight = FontWeight.ExtraBold, align = TextAlign.End)
    }
}

@Composable
private fun ChecklistPanel(vm: AppViewModel, c: Countdown, accent: Color) {
    val pc = LocalPaw.current
    var text by remember { mutableStateOf("") }
    val title = when (c.type) { "intl_trip", "vacation" -> "Packing list"; "birthday" -> "Gift ideas"; else -> "Checklist" }
    Panel {
        PanelTitle(title) { Muted("${c.checklist.count { it.done }}/${c.checklist.size}") }
        c.checklist.forEachIndexed { i, it ->
            if (i > 0) Divider()
            Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clickable(role = Role.Checkbox) { vm.upsert(c.copy(checklist = c.checklist.map { x -> if (x.id == it.id) x.copy(done = !x.done) else x })) }, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(10.dp)).background(if (it.done) accent else Color.Transparent).then(if (it.done) Modifier else Modifier.background(pc.line)).padding(if (it.done) 0.dp else 2.5.dp), contentAlignment = Alignment.Center) {
                        if (!it.done) Box(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(pc.surface)) else Icon("check", onAccent(accent), 18.dp)
                    }
                    B(it.text, Modifier.padding(start = 12.dp), 16, FontWeight.Bold, color = if (it.done) pc.ink2 else null)
                }
                Box(Modifier.size(44.dp).clickable(role = Role.Button) { vm.upsert(c.copy(checklist = c.checklist.filterNot { x -> x.id == it.id })) }.semantics { contentDescription = "Remove ${it.text}" }, contentAlignment = Alignment.Center) { Icon("close", pc.ink2, 18.dp) }
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Field("", text, { text = it }, placeholder = "Add something…", maxLen = 80, minHeight = 46.dp, modifier = Modifier.padding(top = 0.dp)) }
            SmallButton("Add", { if (text.isNotBlank()) { vm.upsert(c.copy(checklist = c.checklist + CheckItem(newId(), text.trim(), false))); text = "" } }, mixColor(pc.surface, accent, 0.18f))
        }
    }
}

@Composable
private fun NotesPanel(vm: AppViewModel, c: Countdown) {
    val pc = LocalPaw.current
    var text by remember(c.id) { mutableStateOf(c.notes) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    // save a moment after typing stops (every save also re-plans reminders, so don't do it per keystroke)
    LaunchedEffect(text) { if (text != c.notes) { delay(700); vm.upsert(c.copy(notes = text)) } }
    Panel {
        PanelTitle("Notes")
        BasicTextField(text, { text = it }, Modifier.fillMaxWidth().focusRequester(focus), textStyle = T.body(16), cursorBrush = SolidColor(pc.kibble), minLines = 3,
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().heightIn(min = 92.dp).clip(RoundedCornerShape(18.dp)).background(pc.surface).background(pc.ink.copy(alpha = 0.03f)).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { focus.requestFocus() }.padding(14.dp)) {
                    if (text.isEmpty()) Text("Itinerary, ideas, inside jokes…", style = T.body(16).copy(color = pc.ink2.copy(alpha = 0.6f))); inner()
                }
            })
    }
}

@Composable
private fun PhotosPanel(vm: AppViewModel, c: Countdown, accent: Color) {
    val pc = LocalPaw.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
        val added = uris.take(6 - c.photos.size).mapNotNull { vm.repo.importPhoto(it) }
        if (added.isNotEmpty()) { vm.upsert(c.copy(photos = c.photos + added)); vm.say("Added to ${c.title} 💛") }
    }
    Panel {
        PanelTitle("Memory photos", "camera") { SmallButton("Add photos", { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, mixColor(pc.surface, accent, 0.18f)) }
        if (c.photos.isEmpty()) Muted("${c.dog.name} is keeping this spot warm. Add a few photos and this countdown becomes a memory card.", size = 15)
        else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            c.photos.take(3).forEach { name -> Box(Modifier.weight(1f)) { PhotoThumb(vm, name, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(Color.White).padding(4.dp)) } }
        }
    }
}

@Composable
fun PhotoThumb(vm: AppViewModel, name: String, modifier: Modifier) {
    val bmp = remember(name) { runCatching { BitmapFactory.decodeFile(vm.repo.photoFile(name).path, BitmapFactory.Options().apply { inSampleSize = 2 }) }.getOrNull() }
    if (bmp != null) Image(bmp.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop) else Box(modifier.background(Color(0xFFEEEEEE)))
}

/** the person's profile picture: their own photo if they added one, otherwise Scott's face */
@Composable
fun Avatar(vm: AppViewModel, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val photo = vm.data.collectAsState().value.settings.profilePhoto
    val bmp = remember(photo) { if (photo.isEmpty()) null else runCatching { BitmapFactory.decodeFile(vm.repo.photoFile(photo).path) }.getOrNull() }
    if (bmp != null) Image(bmp.asImageBitmap(), null, modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape), contentScale = ContentScale.Crop)
    else DogIcon(vm.art, Dog("scott", "Scott"), modifier.size(size), circle = true)
}

/** the head of a dog as a small round picture (used for breed cards and settings) */
@Composable
fun DogIcon(art: Art, dog: Dog, modifier: Modifier = Modifier, bg: Color = Color(0xFFFFF1DC), circle: Boolean = false) {
    val palette = remember(dog) { dogPalette(art.meta, dog, 0) }
    val node = remember(dog.variant) { art.icon(dog.variant) }
    androidx.compose.foundation.Canvas(modifier.clip(if (circle) CircleShape else RoundedCornerShape(24)).background(bg)) {
        drawIntoCanvas { cv ->
            val s = size.width / 240f * 0.98f
            cv.nativeCanvas.save(); cv.nativeCanvas.translate(size.width / 2f, size.height * 0.53f); cv.nativeCanvas.scale(s, s); cv.nativeCanvas.translate(-120f, -108f)
            node?.let { drawNode(cv.nativeCanvas, it, DrawState(palette, 0f, Stage.WAITING, emptyList(), reduceMotion = true)) }
            cv.nativeCanvas.restore()
        }
    }
}

/** How happy each dog is (0-100). Goes up with treats and play, and fades by about 6 points an hour. Kept per countdown on this device only. */
object Joy {
    private fun prefs(ctx: android.content.Context) = ctx.getSharedPreferences("joy", android.content.Context.MODE_PRIVATE)
    fun now(ctx: android.content.Context, id: String): Float {
        val raw = prefs(ctx).getString(id, null) ?: return 20f
        val (v, t) = raw.split("|").let { it[0].toFloat() to it[1].toLong() }
        return maxOf(0f, v - (System.currentTimeMillis() - t) / 3_600_000f * 6f)
    }
    fun add(ctx: android.content.Context, id: String, n: Float): Float {
        val v = minOf(100f, now(ctx, id) + n)
        prefs(ctx).edit().putString(id, "$v|${System.currentTimeMillis()}").apply()
        return v
    }
    /** hunger and thirst (0-100, full = 100): they run down while you are away, about 5 and 8 points an hour */
    fun needs(ctx: android.content.Context, id: String): Pair<Float, Float> {
        val raw = prefs(ctx).getString("n.$id", null) ?: return 70f to 70f
        val p = raw.split("|"); val h = (System.currentTimeMillis() - p[2].toLong()) / 3_600_000f
        return maxOf(0f, p[0].toFloat() - h * 5f) to maxOf(0f, p[1].toFloat() - h * 8f)
    }
    fun fill(ctx: android.content.Context, id: String, food: Float, water: Float): Pair<Float, Float> {
        val (f, w) = needs(ctx, id); val nf = minOf(100f, f + food); val nw = minOf(100f, w + water)
        prefs(ctx).edit().putString("n.$id", "$nf|$nw|${System.currentTimeMillis()}").apply()
        return nf to nw
    }
    fun label(v: Float) = when { v < 25f -> "sleepy"; v < 55f -> "content"; v < 85f -> "happy"; else -> "over the moon" }
}
