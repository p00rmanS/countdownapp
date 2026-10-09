package app.pawcount

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Random

/* MoreScreens.kt - Welcome (3 pages), Memories, Settings and the confetti overlay. */

/** just the dog (no scenery), animated; used on the welcome pages */
@Composable
fun DogOnly(vm: AppViewModel, dog: Dog, stage: Stage, modifier: Modifier = Modifier, bell: Boolean = false, type: String = "custom", accent: String = "#E8A15C") {
    val clock by rememberClock()
    val reduce = LocalReduceMotion.current
    val node = remember(dog.variant, stage, bell) { vm.art.dog(dog.variant, stage, type, bell) }
    val palette = remember(dog) { dogPalette(vm.art.meta, dog, parseHex(accent)) }
    val anims = remember(stage) { dogAnims(vm.art.anim, stage) }
    Canvas(modifier.aspectRatio(1f)) {
        drawIntoCanvas { c -> node?.let { drawDog(c.nativeCanvas, it, 0f, 0f, size.width, DrawState(palette, clock, stage, anims, reduceMotion = reduce)) } }
    }
}

/* ------------------------------ Welcome ------------------------------ */

@Composable
fun WelcomeScreen(vm: AppViewModel) {
    val pc = LocalPaw.current
    val ctx = LocalContext.current
    var page by remember { mutableStateOf(0) }
    val meta = vm.art.meta
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        vm.settings { it.copy(notifications = ok || Build.VERSION.SDK_INT < 33) }
        finishWelcome(vm)
    }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(pc.kibble.copy(alpha = 0.28f), pc.bg), endY = 900f)).windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 22.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(3) { Icon("paw", if (it <= page) pc.kibble else pc.ink.copy(alpha = 0.2f), 22.dp) } }
            if (page < 2) Text("Skip", Modifier.clickable(role = Role.Button) { page = 2 }.padding(10.dp), style = T.body(16, FontWeight.ExtraBold).copy(color = pc.ink2))
        }
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            when (page) {
                0 -> {
                    Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.Center) {
                        meta.breedOrder.forEachIndexed { i, b -> DogOnly(vm, Dog(b, meta.breeds.getValue(b).name, if (b == "mutt") "#B98A62" else null, if (b == "mutt") "floppy" else null), if (i % 3 == 0) Stage.PACKING else if (i % 2 == 0) Stage.WAITING else Stage.CURIOUS,
                            Modifier.weight(1f).padding(top = if (i % 2 == 0) 0.dp else 22.dp)) }
                    }
                    H("Countdowns with a dog who can't wait either.", 38, align = TextAlign.Center)
                    B("Every trip, birthday and big day gets a companion that gets more excited as it gets closer.", Modifier.padding(top = 12.dp), 17, FontWeight.Bold, TextAlign.Center, color = pc.ink2)
                }
                1 -> {
                    H("Pick your first companion", 31, align = TextAlign.Center)
                    B("Every breed has its own personality. You can mix and match later.", Modifier.padding(top = 10.dp), 16.5.toInt(), FontWeight.Bold, TextAlign.Center, color = pc.ink2)
                    Column(Modifier.padding(top = 18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        meta.breedOrder.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { row.forEach { b ->
                            val br = meta.breeds.getValue(b); val on = vm.welcomeBreed == b && vm.welcomeBreedChosen
                            Column(Modifier.weight(1f).shadow(if (on) 8.dp else 3.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(pc.surface).border(3.dp, if (on) pc.kibble else Color.Transparent, RoundedCornerShape(24.dp))
                                .clickable(role = Role.RadioButton) { vm.welcomeBreed = b; vm.welcomeBreedChosen = true; Haptics.play(ctx, vm.data.value.settings.haptics, "tick") }.padding(10.dp, 10.dp, 10.dp, 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                DogOnly(vm, Dog(b, br.name, if (b == "mutt") "#B98A62" else null, if (b == "mutt") "floppy" else null), if (on) Stage.CURIOUS else Stage.WAITING, Modifier.fillMaxWidth(0.8f))
                                H(br.name, 18); Muted(br.label, size = 13); B(br.vibe, size = 13, weight = FontWeight.Bold, align = TextAlign.Center)
                            } } } }
                    }
                }
                else -> {
                    val b = vm.welcomeBreed
                    DogOnly(vm, Dog(b, meta.breeds.getValue(b).name, if (b == "mutt") "#B98A62" else null, if (b == "mutt") "floppy" else null), Stage.WAITING, Modifier.fillMaxWidth(0.62f), bell = true)
                    H("${meta.breeds.getValue(b).name} will ring the bell", 31, Modifier.padding(top = 8.dp), TextAlign.Center)
                    B("Allow reminders so ${meta.breeds.getValue(b).name} can tap you on the shoulder at 100, 50, 30, 7 and 1 days — and the morning of. One a day, tops.", Modifier.padding(top = 12.dp), 16, FontWeight.Bold, TextAlign.Center, color = pc.ink2)
                }
            }
        }
        Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (page) {
                0 -> { PrimaryButton("Let's go", { page = 1 }, Modifier.fillMaxWidth()); GhostButton("Peek at sample adventures", { vm.loadSamples(); vm.goHome() }) }
                1 -> PrimaryButton("Choose ${meta.breeds.getValue(vm.welcomeBreed).name}", { vm.welcomeBreedChosen = true; page = 2 }, Modifier.fillMaxWidth())
                else -> {
                    PrimaryButton("Allow reminders", { Sounds.play(vm.data.value.settings.sound, "jingle"); if (Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS) else { vm.settings { it.copy(notifications = true) }; finishWelcome(vm) } }, Modifier.fillMaxWidth())
                    GhostButton("Maybe later", { finishWelcome(vm) })
                }
            }
        }
    }
}

private fun finishWelcome(vm: AppViewModel) { vm.finishWelcome(); vm.stack.clear(); vm.stack.add(Route.Home); vm.push(Route.Flow(null)) }

/* ------------------------------ Memories ------------------------------ */

@Composable
fun MemoriesScreen(vm: AppViewModel) {
    val data by vm.data.collectAsState()
    val now by rememberNow()
    val pc = LocalPaw.current
    val items = data.countdowns.map { it to compute(it, now) }.filter { it.second.phase == Phase.PAST || it.first.archived }.sortedByDescending { it.second.target }
    val thisYear = items.count { java.time.Instant.ofEpochMilli(it.second.target).atZone(java.time.ZoneId.systemDefault()).year == java.time.LocalDate.now().year }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 130.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Eyebrow("Keepsakes"); H("Memories", 32) }
                IconButton("sliders", "Settings", { vm.push(Route.Settings) })
            }
            if (items.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Scott is not just a picture: tap to bark, hold to pet
                    val demo = remember { Countdown("demo", "Memories", "custom", "2020-01-01T00:00", "UTC", false, false, System.currentTimeMillis(), Dog("scott", "Scott"), "#EE8FA0", DisplayMode.FULL) }
                    val ctrl = remember { SceneController() }
                    LaunchedEffect(ctrl.bubble) { if (ctrl.bubble != null) { kotlinx.coroutines.delay(2400); ctrl.bubble = null } }
                    Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(28.dp))) {
                        SceneView(demo, compute(demo, now), vm.art, vm.fonts, SceneSize.DETAIL, stage = Stage.MEMORY, controller = ctrl, interactive = true, reduceMotion = LocalReduceMotion.current,
                            onBark = { ctrl.bubble = dogLine(demo, compute(demo), "tap") }, onPetStart = { ctrl.bubble = dogLine(demo, compute(demo), "pet") })
                    }
                    H("No memories yet", 26, align = TextAlign.Center)
                    Muted("When a countdown ends, it lands here as a keepsake — with your photos and a very proud dog.", Modifier.padding(top = 8.dp), 16, TextAlign.Center)
                }
            } else {
                Row(Modifier.padding(top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFFFFE3B0), Color(0xFFF9C4B8)))).padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("${items.size}", style = T.display(56, FontWeight.Bold).copy(color = Color(0xFF3D2616)))
                    Text("${if (items.size == 1) "adventure" else "adventures"} counted down together${if (thisYear > 0) " · $thisYear in ${java.time.LocalDate.now().year}" else ""}.", style = T.body(16, FontWeight.Bold).copy(color = Color(0xFF3D2616)))
                }
                Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    items.chunked(2).forEachIndexed { r, row -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEachIndexed { i, (c, k) ->
                            Column(Modifier.weight(1f).rotate(if ((r * 2 + i) % 2 == 0) -1.6f else 1.6f).shadow(8.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(Color.White)
                                .clickable(role = Role.Button) { vm.push(Route.Detail(c.id)) }.padding(9.dp, 9.dp, 9.dp, 14.dp)) {
                                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(4.dp)).background(Color(0xFFEEEEEE))) {
                                    if (c.photos.isNotEmpty()) PhotoThumb(vm, c.photos.first(), Modifier.fillMaxSize())
                                    else SceneView(c, k, vm.art, vm.fonts, SceneSize.CARD, stage = Stage.MEMORY, reduceMotion = LocalReduceMotion.current, modifier = Modifier.fillMaxSize())
                                }
                                Text(c.title, Modifier.padding(top = 10.dp), style = T.display(17).copy(color = Color(0xFF3D2616)), maxLines = 1)
                                Text("${typeEmoji(c.type)} ${fmtInstant(k.target, k.zone, true)}${if (c.archived) " · archived" else ""}", style = T.body(13, FontWeight.Bold).copy(color = Color(0xFF765039)), maxLines = 1)
                            }
                        }
                        if (row.size == 1) Box(Modifier.weight(1f))
                    } }
                }
            }
        }
        BoxScopeTabBar(vm, Route.Memories, Modifier.align(Alignment.BottomCenter))
    }
}

/* ------------------------------ Settings ------------------------------ */

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val data by vm.data.collectAsState()
    val s = data.settings
    val pc = LocalPaw.current
    val ctx = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        vm.settings { it.copy(notifications = ok || Build.VERSION.SDK_INT < 33) }; vm.say(if (ok || Build.VERSION.SDK_INT < 33) "Reminders are on 🔔" else "Reminders are blocked in your phone settings")
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val text = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().readText()
            val restored = appDataFromJson(org.json.JSONObject(text))
            vm.repo.update { restored }; vm.say("Backup restored 🐾")
        }.onFailure { vm.say("That file isn’t a Pawcount backup") }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 40.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton("back", "Back", { vm.pop() }); H("Settings", 22, Modifier.weight(1f), TextAlign.Center); Box(Modifier.size(48.dp))
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DogIcon(vm.art, Dog("scott", "Scott"), Modifier.size(64.dp)); Column { B("Pawcount", size = 17, weight = FontWeight.ExtraBold); Muted("Every day closer is another tail wag.") }
        }
        Group("Profile")
        Panel {
            var name by remember(s.profileName) { mutableStateOf(s.profileName) }
            val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                val file = uri?.let { vm.repo.importPhoto(it, 256f) }
                if (file != null) { val old = s.profilePhoto; vm.settings { it.copy(profilePhoto = file) }; if (old.isNotEmpty()) vm.repo.deletePhoto(old) } else if (uri != null) vm.say("Could not read that photo")
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.clickable(role = Role.Button, onClickLabel = "Choose a profile photo") { pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Avatar(vm, 72.dp) }
                Column(Modifier.weight(1f)) {
                    Field("Your name", name, { name = it; vm.settings { st -> st.copy(profileName = it.trim()) } }, placeholder = "What should Scott call you?", maxLen = 30)
                    if (s.profilePhoto.isNotEmpty()) Box(Modifier.padding(top = 10.dp)) { SmallButton("Remove photo", { vm.repo.deletePhoto(s.profilePhoto); vm.settings { it.copy(profilePhoto = "") } }, pc.surface) }
                }
            }
            Muted("Stays on this device and in your backup. Sign-in with Google, so your profile follows you between phones, is planned.", Modifier.padding(top = 10.dp), size = 14)
        }
        Group("Look & feel")
        Panel(flush = true) {
            SettingCol("Default display") { Seg(DisplayMode.entries.map { it.key to it.label }, s.displayMode.key, { m -> vm.settings { it.copy(displayMode = DisplayMode.of(m)) } }, label = "Default display mode") }
            Divider()
            SettingCol("Theme") { Seg(listOf("system" to "System", "light" to "Light", "dark" to "Dark"), s.theme, { m -> vm.settings { it.copy(theme = m) } }, label = "Theme") }
            Divider()
            SettingCol("Reduce motion", "Swaps loops for still poses. The dog keeps their expression.") { Seg(listOf("system" to "System", "on" to "On", "off" to "Off"), s.reduceMotion, { m -> vm.settings { it.copy(reduceMotion = m) } }, label = "Reduce motion") }
        }
        Group("Feedback")
        Panel(flush = true) {
            SettingSwitch("Haptics", "Taps, purrs and milestone bursts", s.haptics) { on -> vm.settings { it.copy(haptics = on) }; if (on) Haptics.play(ctx, true, "purr") }
            Divider()
            SettingSwitch("Sounds", "Soft barks & chimes · off by default", s.sound) { on -> vm.settings { it.copy(sound = on) }; Sounds.play(on, "bark") }
            Divider()
            SettingSwitch("Reminders", "Milestones & the morning of", s.notifications && Reminders.hasPermission(ctx)) { on ->
                if (!on) vm.settings { it.copy(notifications = false) }
                else if (Build.VERSION.SDK_INT >= 33 && !Reminders.hasPermission(ctx)) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                else { vm.settings { it.copy(notifications = true) }; vm.say("Reminders are on 🔔") }
            }
        }
        Group("App icon")
        Panel {
            var icon by remember { mutableStateOf(IconSwitcher.current(ctx)) }
            Muted("Pick the dog that lives on your home screen.", Modifier.padding(bottom = 12.dp), 14)
            vm.art.meta.breedOrder.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { b ->
                        val on = icon == b
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).border(2.5.dp, if (on) pc.kibble else Color.Transparent, RoundedCornerShape(20.dp))
                            .clickable(role = Role.RadioButton) { IconSwitcher.set(ctx, b); icon = b; Haptics.play(ctx, s.haptics, "tick"); vm.say("${vm.art.meta.breeds.getValue(b).name} is your new icon 🐾") }.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            DogIcon(vm.art, Dog(b, "", if (b == "mutt") "#B98A62" else null, if (b == "mutt") "floppy" else null), Modifier.size(62.dp))
                            Text(vm.art.meta.breeds.getValue(b).name, style = T.body(13, FontWeight.ExtraBold).copy(color = if (on) pc.ink else pc.ink2))
                        }
                    }
                }
            }
        }
        Group("Your data")
        Panel(flush = true) {
            Muted("Everything stays on this phone. No account, no tracking, no ads.", Modifier.padding(vertical = 14.dp), 14)
            SettingLink("download", "Export backup") {
                val f = java.io.File(ctx.cacheDir, "pawcount-backup.json").apply { writeText(vm.repo.export()) }
                val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Save backup"))
            }
            SettingLink("upload", "Import backup") { importer.launch(arrayOf("application/json", "*/*")) }
            SettingLink("heart", "Add a shared countdown") { showImport = true }
            SettingLink("paw", "Load sample adventures") { vm.loadSamples(); vm.say("Seven sample adventures added 🐾") }
            if (data.countdowns.any { it.sample }) SettingLink("trash", "Remove sample adventures") { vm.repo.update { it.copy(countdowns = it.countdowns.filterNot { c -> c.sample }) }; vm.say("Samples removed") }
            SettingLink("trash", "Erase everything", danger = true) { confirmReset = true }
        }
        Row(Modifier.padding(top = 26.dp).fillMaxWidth().border(2.dp, pc.line, RoundedCornerShape(28.dp)).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Row { DogIcon(vm.art, Dog("corgi", "Biscuit"), Modifier.size(46.dp)); DogIcon(vm.art, Dog("scott", "Scott"), Modifier.padding(start = 0.dp).size(46.dp)) }
            Column { B("For two · coming in v1.1", size = 16, weight = FontWeight.ExtraBold); Muted("Share a countdown with your person. Both phones stay in sync and you'll see who petted the dog today.", size = 13) }
        }
        Muted("Pawcount · v1.0", Modifier.fillMaxWidth().padding(top = 26.dp), 13, TextAlign.Center)
    }
    if (showImport) {
        var text by remember { mutableStateOf("") }
        PawSheet({ showImport = false }) {
            H("Add a shared countdown", 22, Modifier.fillMaxWidth(), TextAlign.Center)
            Muted("Paste the link your person sent you.", Modifier.fillMaxWidth().padding(top = 6.dp), 15, TextAlign.Center)
            Field("", text, { text = it }, placeholder = "https://…", maxLen = 6000)
            Box(Modifier.padding(top = 14.dp)) {
                PrimaryButton("Add countdown", {
                    val id = vm.importShared(text)
                    if (id != null) { showImport = false; vm.say("Added to your countdowns 🐾"); vm.stack.clear(); vm.stack.add(Route.Home); vm.push(Route.Detail(id)) } else vm.say("That doesn’t look like a Pawcount link")
                }, Modifier.fillMaxWidth(), enabled = text.isNotBlank())
            }
        }
    }
    if (confirmReset) PawSheet({ confirmReset = false }) {
        H("Erase everything?", 22, Modifier.fillMaxWidth(), TextAlign.Center)
        Muted("All countdowns, memories and settings on this phone will be removed.", Modifier.fillMaxWidth().padding(vertical = 10.dp), 15, TextAlign.Center)
        DangerButton("Erase everything", { confirmReset = false; vm.repo.update { AppData() }; vm.stack.clear(); vm.stack.add(Route.Welcome) })
        Box(Modifier.padding(top = 10.dp)) { GhostButton("Cancel", { confirmReset = false }) }
    }
}

@Composable private fun Group(t: String) = Text(t.uppercase(), Modifier.padding(start = 4.dp, top = 26.dp), style = T.muted(13, FontWeight.ExtraBold).copy(letterSpacing = 1.sp))

@Composable
private fun SettingCol(title: String, sub: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        B(title, size = 16, weight = FontWeight.ExtraBold); if (sub != null) Muted(sub, size = 13)
        Box(Modifier.padding(top = 8.dp)) { content() }
    }
}

@Composable
private fun SettingSwitch(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { B(title, size = 16, weight = FontWeight.ExtraBold); Muted(sub, size = 13) }
        PawSwitch(on, title, onChange)
    }
}

@Composable
private fun SettingLink(icon: String, text: String, danger: Boolean = false, onClick: () -> Unit) {
    val pc = LocalPaw.current
    Column { Divider(); Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, if (danger) pc.collar else pc.ink, 22.dp); Text(text, Modifier.weight(1f), style = T.body(16, FontWeight.ExtraBold).copy(color = if (danger) pc.collar else pc.ink)); Icon("chevron", pc.ink2, 18.dp)
    } }
}

private val sp1 = androidx.compose.ui.unit.TextUnit.Unspecified
private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)

/* ------------------------------ confetti ------------------------------ */

private class Bit(var x: Float, var y: Float, var vx: Float, var vy: Float, var rot: Float, var vr: Float, val color: Int, val size: Float, var life: Float, val shape: Int)

/** Full-screen confetti. Each time [burst] changes a new shower pops from the upper middle of the screen. */
@Composable
fun ConfettiOverlay(burst: Int, reduceMotion: Boolean) {
    val bits = remember { mutableStateListOf<Bit>() }
    val clock by rememberClock()
    var last by remember { mutableStateOf(0f) }
    var lastBurst by remember { mutableStateOf(0) }
    val colors = remember { intArrayOf(0xFFFF7DAA.toInt(), 0xFFFFD35C.toInt(), 0xFF5DADE8.toInt(), 0xFFD4E157.toInt(), 0xFFE8A15C.toInt(), 0xFFF6B7B0.toInt(), 0xFFE5574F.toInt(), 0xFFFFFFFF.toInt()) }
    val rnd = remember { Random() }
    Canvas(Modifier.fillMaxSize()) {
        if (burst != lastBurst) {
            lastBurst = burst
            if (!reduceMotion) repeat(110) {
                val a = rnd.nextFloat() * 6.2832f; val v = (2 + rnd.nextFloat() * 7) * density * 1.4f
                bits += Bit(size.width / 2f, size.height * 0.32f, kotlin.math.cos(a) * v, kotlin.math.sin(a) * v - 4f * density, rnd.nextFloat() * 6f, (rnd.nextFloat() - 0.5f) * 0.4f, colors[it % colors.size], (6 + rnd.nextFloat() * 8) * density, 140f + rnd.nextFloat() * 80f, rnd.nextInt(3))
            }
        }
        val dt = ((clock - last) * 60f).coerceIn(0.2f, 3f); last = clock
        if (bits.isNotEmpty()) drawIntoCanvas { cv ->
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            val it = bits.iterator()
            while (it.hasNext()) {
                val b = it.next()
                b.vy += 0.16f * density * dt; b.vx *= 0.992f; b.x += b.vx * dt; b.y += b.vy * dt; b.rot += b.vr * dt; b.life -= dt
                if (b.life <= 0 || b.y > size.height + 40) { it.remove(); continue }
                p.color = b.color; p.alpha = (255 * (b.life / 30f).coerceAtMost(1f)).toInt()
                cv.nativeCanvas.save(); cv.nativeCanvas.translate(b.x, b.y); cv.nativeCanvas.rotate(Math.toDegrees(b.rot.toDouble()).toFloat())
                if (b.shape == 1) cv.nativeCanvas.drawCircle(0f, 0f, b.size / 2.4f, p) else cv.nativeCanvas.drawRect(-b.size / 2, -b.size / 4, b.size / 2, b.size / 4, p)
                cv.nativeCanvas.restore()
            }
        }
    }
}
