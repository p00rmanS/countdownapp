package app.pawcount

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/*
 * FlowScreen.kt - create / edit a countdown in five steps: What? -> When? -> Which dog? -> Style -> Done.
 * Everything typed lives in a Draft; it only becomes a real Countdown when the last button is pressed.
 */

data class Draft(
    val id: String? = null, val type: String = "intl_trip", val title: String = "", val titleAuto: Boolean = true,
    val date: LocalDate = LocalDate.now().plusDays(30), val time: LocalTime = LocalTime.of(9, 0), val allDay: Boolean = false,
    val tz: String = ZoneId.systemDefault().id, val yearly: Boolean = false, val country: String = "",
    val personName: String = "", val birthYear: String = "", val breed: String = "scott", val breedTouched: Boolean = false,
    val dogName: String = "Scott", val nameTouched: Boolean = false, val fur: String = "#B98A62", val ears: String = "floppy",
    val accent: String = "#5DADE8", val accentTouched: Boolean = false, val mode: DisplayMode = DisplayMode.FULL,
    val reminders: List<Reminder> = defaultReminders("intl_trip"), val notes: String = "", val checklist: List<CheckItem> = emptyList(),
    val createdAt: Long? = null, val sample: Boolean = false, val photos: List<String> = emptyList(), val archived: Boolean = false, val origTarget: String? = null,
)

private fun Draft.withType(meta: Meta, t: String): Draft {
    val info = meta.types.getValue(t)
    val b = if (breedTouched) breed else meta.suggestedBreed(t)
    return copy(type = t, accent = if (accentTouched) accent else info.accent, yearly = info.repeats, allDay = t == "birthday" || t == "anniversary" || t == "holiday",
        breed = b, dogName = if (nameTouched) dogName else meta.breeds.getValue(b).name, reminders = defaultReminders(t), country = if (t == "intl_trip") country else "",
        title = if (titleAuto) "" else title)
}

private fun Draft.toCountdown(): Countdown {
    val target = "${date}T${if (allDay) "00:00" else time.toString().take(5)}"
    val keep = createdAt != null && origTarget == "$target|$tz"
    val dog = if (breed == "mutt") Dog("mutt", dogName.trim().ifEmpty { "Lucky" }, fur, ears) else Dog(breed, dogName.trim())
    return Countdown(id ?: newId(), title.trim(), type, target, tz, allDay, yearly, if (keep) createdAt!! else System.currentTimeMillis(), dog, accent, mode, notes, checklist,
        destination = null, person = if (type == "birthday" && (personName.isNotBlank() || birthYear.isNotBlank())) Person(personName.trim(), birthYear.toIntOrNull()) else null,
        reminders = reminders, archived = archived, photos = photos, sample = sample)
}

private fun draftOf(c: Countdown, meta: Meta) = Draft(
    id = c.id, type = c.type, title = c.title, titleAuto = false, date = LocalDate.parse(c.targetAt.take(10)), time = runCatching { LocalTime.parse(c.targetAt.substring(11, 16)) }.getOrDefault(LocalTime.of(9, 0)),
    allDay = c.allDay, tz = c.timeZone, yearly = c.yearly, country = c.destination?.country ?: "", personName = c.person?.name ?: "", birthYear = c.person?.birthYear?.toString() ?: "",
    breed = c.dog.breed, breedTouched = true, dogName = c.dog.name, nameTouched = true, fur = c.dog.furColor ?: "#B98A62", ears = c.dog.ears ?: "floppy", accent = c.accent, accentTouched = true,
    mode = c.displayMode, reminders = c.reminders, notes = c.notes, checklist = c.checklist, createdAt = c.createdAt, sample = c.sample, photos = c.photos, archived = c.archived, origTarget = "${c.targetAt}|${c.timeZone}",
)

private val STEPS = listOf("What?", "When?", "Which dog?", "Style", "Done!")
private val HEADS = listOf("What are you counting down to?", "When is the big moment?", "Choose their dog", "Make it yours", "")

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FlowScreen(vm: AppViewModel, editId: String?) {
    val data by vm.data.collectAsState()
    val meta = vm.art.meta
    val editing = editId != null
    var step by remember { mutableStateOf(0) }
    var d by remember {
        mutableStateOf(
            data.countdowns.firstOrNull { it.id == editId }?.let { draftOf(it, meta) }
                ?: Draft(mode = data.settings.displayMode).withType(meta, "intl_trip").let { base ->
                    // the companion chosen on the welcome screens becomes the first dog
                    if (vm.welcomeBreedChosen) base.copy(breed = vm.welcomeBreed, breedTouched = true, dogName = meta.breeds.getValue(vm.welcomeBreed).name) else base
                },
        )
    }
    val pc = LocalPaw.current
    val accent = accentColor(d.accent)
    val reduce = LocalReduceMotion.current
    val note = whenNote(d, editing)
    val valid = when (step) { 0 -> d.title.isNotBlank(); 1 -> note.first; 2 -> d.dogName.isNotBlank(); else -> true }
    var preview by remember { mutableStateOf(Stage.WAITING) }
    var showDate by remember { mutableStateOf(false) }; var showTime by remember { mutableStateOf(false) }
    var showCountry by remember { mutableStateOf(false) }; var showTz by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    LaunchedEffect(step) { scroll.scrollTo(0); if (step == 4) { vm.confetti(); } }

    fun back() { if (step == 0) vm.pop() else step-- }
    androidx.activity.compose.BackHandler(enabled = step > 0) { back() }
    fun next() {
        if (!valid) return
        if (step < 4) { step++; return }
        val c = d.toCountdown().let { c0 ->
            val country = meta.countries.firstOrNull { it.name == d.country }
            c0.copy(destination = if (d.type == "intl_trip" && country != null) Destination(country.name, country.city, country.flag) else null)
        }
        vm.upsert(c); vm.repo.update { it.copy(onboarded = true) }
        vm.stack.clear(); vm.stack.add(Route.Home); vm.push(Route.Detail(c.id))
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(if (step == 0) "close" else "back", if (step == 0) "Close" else "Previous step", { back() })
            Box(Modifier.weight(1f))
            Box(Modifier.size(48.dp))
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 20.dp)) {
            if (step < 4) {
                Eyebrow("Step ${step + 1} of 5 · ${STEPS[step]}"); H(HEADS[step], 30, Modifier.padding(top = 4.dp, bottom = 6.dp))
            }
            when (step) {
                0 -> {
                    val rows = meta.typeOrder.chunked(2)
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        rows.forEach { r -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { r.forEach { t ->
                            val info = meta.types.getValue(t); val on = d.type == t; val ta = accentColor(info.accent)
                            Column(Modifier.weight(1f).heightIn(min = 108.dp).shadow(3.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(if (on) mixColor(pc.surface, ta, 0.14f) else pc.surface)
                                .border(3.dp, if (on) ta else Color.Transparent, RoundedCornerShape(22.dp)).clickable(role = Role.RadioButton) { d = d.withType(meta, t) }.padding(14.dp)) {
                                Icon(info.icon, ta, 30.dp); H(info.label, 17); Muted(info.hint, size = 13)
                            } } } }
                    }
                    Field("Name it", d.title, { d = d.copy(title = it, titleAuto = false) }, placeholder = meta.types.getValue(d.type).placeholder, accent = accent, maxLen = 48)
                    if (d.type == "intl_trip") {
                        PickerRow("Destination country", meta.countries.firstOrNull { it.name == d.country }?.let { "${it.flag} ${it.name}" } ?: "Choose a country…") { showCountry = true }
                        Muted("We'll set the time zone for you.", Modifier.padding(top = 6.dp), 13)
                    }
                    if (d.type == "birthday") Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) { Field("Whose birthday?", d.personName, { n -> d = d.copy(personName = n, title = if (d.titleAuto || d.title.isBlank()) (if (n.isBlank()) "" else "${n.trim()}'s birthday") else d.title, titleAuto = d.titleAuto || d.title.isBlank()) }, placeholder = "Mia", accent = accent, maxLen = 30) }
                        Box(Modifier.width(120.dp)) { Field("Birth year", d.birthYear, { d = d.copy(birthYear = it.filter(Char::isDigit).take(4)) }, placeholder = "1996", keyboard = KeyboardOptions(keyboardType = KeyboardType.Number), accent = accent, maxLen = 4) }
                    }
                }
                1 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) { PickerRow("Date", d.date.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault()))) { showDate = true } }
                        if (!d.allDay) Box(Modifier.width(140.dp)) { PickerRow("Time", DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(d.time)) { showTime = true } }
                    }
                    Panel(flush = true) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { B("All day", size = 16, weight = FontWeight.ExtraBold); Muted("Counts down to midnight", size = 13) }
                            PawSwitch(d.allDay, "All day") { d = d.copy(allDay = it) }
                        }
                        Divider()
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { B("Repeat every year", size = 16, weight = FontWeight.ExtraBold); Muted("Birthdays & anniversaries roll over", size = 13) }
                            PawSwitch(d.yearly, "Repeat every year") { d = d.copy(yearly = it) }
                        }
                    }
                    PickerRow("Time zone", tzLabel(d.tz)) { showTz = true }
                    Muted("Pick where the moment happens. Pawcount counts to it exactly, even across daylight-saving changes.", Modifier.padding(top = 6.dp), 13)
                    Text(note.second, Modifier.padding(top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (note.first) mixColor(pc.surface, accent, 0.16f) else pc.collar.copy(alpha = 0.14f)).padding(16.dp), style = T.body(16, FontWeight.Bold))
                }
                2 -> {
                    val previewC = d.toCountdown().let { it.copy(title = it.title.ifBlank { meta.types.getValue(d.type).placeholder }, createdAt = System.currentTimeMillis() - 20 * DAY) }
                    val pk = remember(previewC) { compute(previewC) }
                    Box(Modifier.padding(top = 4.dp).fillMaxWidth().shadow(6.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp))) {
                        SceneView(previewC, pk, vm.art, vm.fonts, SceneSize.PREVIEW, stage = preview, reduceMotion = reduce)
                    }
                    LazyRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf(Stage.NAP, Stage.CURIOUS, Stage.WAITING, Stage.PACKING, Stage.ZOOMIES, Stage.TODAY)) { s ->
                            val on = preview == s
                            Row(Modifier.heightIn(min = 38.dp).clip(RoundedCornerShape(50)).background(if (on) accent else pc.surface).clickable(role = Role.RadioButton) { preview = s }.padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                val tint = if (on) onAccent(accent) else pc.ink2
                                Icon(s.icon, tint, 16.dp); Box(Modifier.width(5.dp))
                                Text(if (s == Stage.TODAY) "Party" else s.label, style = T.body(13.5.toInt(), FontWeight.ExtraBold).copy(color = tint))
                            }
                        }
                    }
                    LazyRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(meta.breedOrder) { b ->
                            val B = meta.breeds.getValue(b); val on = d.breed == b; val sug = d.type in (meta.breedBest[b] ?: emptyList())
                            Box {
                                Column(Modifier.width(138.dp).shadow(if (on) 8.dp else 3.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(pc.surface).border(3.dp, if (on) accent else Color.Transparent, RoundedCornerShape(24.dp))
                                    .clickable(role = Role.RadioButton) { d = d.copy(breed = b, breedTouched = true, dogName = if (d.nameTouched) d.dogName else B.name) }.padding(10.dp, 16.dp, 10.dp, 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    DogIcon(vm.art, Dog(b, B.name, if (b == "mutt") d.fur else null, if (b == "mutt") d.ears else null), Modifier.size(68.dp))
                                    H(B.name, 18, Modifier.padding(top = 4.dp)); B(B.label, size = 13, weight = FontWeight.Bold, align = TextAlign.Center); Muted(B.vibe, size = 12, align = TextAlign.Center)
                                }
                                if (sug) Text("Great match", Modifier.align(Alignment.TopCenter).padding(top = 0.dp).clip(RoundedCornerShape(50)).background(pc.ball).padding(horizontal = 10.dp, vertical = 2.dp), style = T.body(11, FontWeight.ExtraBold).copy(color = Color(0xFF3B2314)))
                            }
                        }
                    }
                    Field("Dog's name", d.dogName, { d = d.copy(dogName = it, nameTouched = true) }, accent = accent, maxLen = 18)
                    if (d.breed == "mutt") Panel {
                        PanelTitle("Customize ${d.dogName.ifBlank { "Lucky" }}")
                        Muted("Coat colour", size = 14)
                        FlowRow(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            meta.muttFurs.forEach { f -> Swatch(accentColor(f), d.fur == f, "Coat $f") { d = d.copy(fur = f) } }
                        }
                        Muted("Ears", Modifier.padding(bottom = 6.dp), 14)
                        Seg(meta.earTypes, d.ears, { d = d.copy(ears = it) }, label = "Ear style")
                    }
                }
                3 -> {
                    val c0 = d.toCountdown().let { it.copy(title = it.title.ifBlank { meta.types.getValue(d.type).placeholder }, createdAt = System.currentTimeMillis() - 20 * DAY) }
                    val k0 = remember(c0) { compute(c0) }
                    Box(Modifier.padding(top = 8.dp).fillMaxWidth(0.62f).align(Alignment.CenterHorizontally).shadow(8.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp)).background(pc.surface)) {
                        Column {
                            SceneView(c0, k0, vm.art, vm.fonts, SceneSize.CARD, stage = Stage.WAITING, reduceMotion = reduce)
                            Column(Modifier.padding(13.dp)) { H(c0.title, 17); H(shortCount(c0, k0), 22) }
                        }
                    }
                    Panel {
                        PanelTitle("Accent colour")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            meta.accents.forEach { (v, n) -> Swatch(accentColor(v), d.accent == v, n) { d = d.copy(accent = v, accentTouched = true) } }
                        }
                    }
                    Panel { PanelTitle("Show the countdown as"); Seg(DisplayMode.entries.map { it.key to it.label }, d.mode.key, { d = d.copy(mode = DisplayMode.of(it)) }, label = "Display mode") }
                    Panel {
                        PanelTitle("Reminders", "bell")
                        d.reminders.forEachIndexed { i, r ->
                            if (i > 0) Divider()
                            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                                B(r.label, Modifier.weight(1f), 16, FontWeight.Bold)
                                PawSwitch(r.enabled, r.label) { on -> d = d.copy(reminders = d.reminders.mapIndexed { j, x -> if (j == i) x.copy(enabled = on) else x }) }
                            }
                        }
                    }
                }
                else -> {
                    val c0 = d.toCountdown().let { it.copy(title = it.title.ifBlank { "Your countdown" }, createdAt = System.currentTimeMillis() - 20 * DAY) }
                    val k0 = remember(c0) { compute(c0) }
                    Box(Modifier.padding(top = 10.dp).fillMaxWidth().shadow(10.dp, RoundedCornerShape(34.dp)).clip(RoundedCornerShape(34.dp))) { SceneView(c0, k0, vm.art, vm.fonts, SceneSize.HERO, stage = Stage.TODAY, reduceMotion = reduce) }
                    H("${d.dogName.ifBlank { "Your dog" }} is already excited!", 30, Modifier.fillMaxWidth().padding(top = 22.dp), TextAlign.Center)
                    Muted(if (editing) "Looking good. Save your changes and tail-wags will update." else "${c0.title} is ready. ${d.dogName} the ${meta.breeds.getValue(d.breed).label} will nap, wait, pack and finally lose their mind as the day gets closer.",
                        Modifier.fillMaxWidth().padding(top = 8.dp), 16, TextAlign.Center)
                }
            }
            Box(Modifier.padding(bottom = 24.dp))
        }
        Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, pc.bg, pc.bg))).windowInsetsPadding(WindowInsets.navigationBars).windowInsetsPadding(WindowInsets.ime).padding(horizontal = 20.dp, vertical = 14.dp)) {
            PrimaryButton(if (step == 4) (if (editing) "Save changes" else "Meet ${d.dogName.ifBlank { "your dog" }}") else if (step == 3) "Finish" else "Next", { next() }, Modifier.fillMaxWidth(), accent = accent, enabled = valid)
        }
    }

    if (showDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = d.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog({ showDate = false }, confirmButton = { TextButton({ st.selectedDateMillis?.let { ms -> d = d.copy(date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()) }; showDate = false }) { Text("OK") } },
            dismissButton = { TextButton({ showDate = false }) { Text("Cancel") } }) { DatePicker(st) }
    }
    if (showTime) {
        val st = rememberTimePickerState(d.time.hour, d.time.minute, false)
        androidx.compose.material3.AlertDialog({ showTime = false }, confirmButton = { TextButton({ d = d.copy(time = LocalTime.of(st.hour, st.minute)); showTime = false }) { Text("OK") } },
            dismissButton = { TextButton({ showTime = false }) { Text("Cancel") } }, text = { TimePicker(st) })
    }
    if (showCountry) PawSheet({ showCountry = false }) {
        H("Destination", 22, Modifier.fillMaxWidth().padding(bottom = 8.dp), TextAlign.Center)
        LazyColumn(Modifier.heightIn(max = 480.dp)) {
            items(meta.countries) { co ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button) {
                    d = d.copy(country = co.name, tz = co.tz, title = if (d.titleAuto || d.title.isBlank()) "${co.name} ${co.flag}" else d.title, titleAuto = d.titleAuto || d.title.isBlank()); showCountry = false
                }, verticalAlignment = Alignment.CenterVertically) { B("${co.flag}  ${co.name}", size = 17, weight = FontWeight.Bold) }
            }
        }
    }
    if (showTz) {
        var q by remember { mutableStateOf("") }
        val all = remember { ZoneId.getAvailableZoneIds().filter { it.contains('/') && !it.startsWith("Etc") && !it.startsWith("SystemV") }.sorted() }
        PawSheet({ showTz = false }) {
            H("Time zone", 22, Modifier.fillMaxWidth(), TextAlign.Center)
            Field("", q, { q = it }, placeholder = "Search (e.g. Tokyo)", accent = accent)
            LazyColumn(Modifier.padding(top = 8.dp).heightIn(max = 420.dp)) {
                items(listOf(ZoneId.systemDefault().id) + all.filter { q.isBlank() || it.replace('_', ' ').contains(q, true) }) { z ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable(role = Role.Button) { d = d.copy(tz = z); showTz = false }, verticalAlignment = Alignment.CenterVertically) {
                        B(if (z == ZoneId.systemDefault().id) "My time zone — ${z.replace('_', ' ')}" else z.replace('_', ' '), size = 16, weight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Swatch(color: Color, on: Boolean, label: String, onClick: () -> Unit) {
    val pc = LocalPaw.current
    Box(Modifier.size(44.dp).then(if (on) Modifier.border(3.dp, pc.surface, CircleShape).padding(3.dp).border(3.dp, color, CircleShape) else Modifier).clip(CircleShape).background(color)
        .clickable(role = Role.RadioButton, onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        if (on) Icon("check", onAccent(color), 22.dp)
    }
}

/** (ok, text) - tells the user how far away the date is, or what's wrong with it */
private fun whenNote(d: Draft, editing: Boolean): Pair<Boolean, String> {
    val c = d.toCountdown()
    val k = runCatching { compute(c) }.getOrNull() ?: return false to "That date doesn’t look right."
    if (k.phase == Phase.PAST && !editing) return false to "That moment has already passed — pick a date in the future."
    if (k.phase == Phase.TODAY) return true to "That's today! 🎉"
    val days = Math.ceil(k.remaining.toDouble() / DAY).toInt()
    val tzNote = if (k.zone != ZoneId.systemDefault()) " Counts to the moment in ${d.tz.substringAfterLast('/').replace('_', ' ')}." else ""
    val lead = if (d.yearly && k.occYear != d.date.year) "Next up is" else "That's"
    return true to "$lead in ${plural(days, "day")}${if (days >= 14) " — about ${Math.round(days / 7.0)} weeks" else ""}.$tzNote"
}
