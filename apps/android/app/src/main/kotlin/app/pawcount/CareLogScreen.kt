package app.pawcount

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/* CareLogScreen.kt - vet visits, vaccines, weight, medicine and notes for your dog. Stays on the device and in the backup. */

private val LOG_KINDS = listOf("vet" to ("cross" to "Vet visit"), "vaccine" to ("shield" to "Vaccine"), "weight" to ("scale" to "Weight"), "meds" to ("pill" to "Medicine"), "note" to ("edit" to "Note"))
private const val KG_PER_LB = 0.45359237

private fun showWeight(kg: Double, unit: String): String {
    val v = if (unit == "lb") kg / KG_PER_LB else kg
    return "${Math.round(v * 10) / 10.0} ${if (unit == "lb") "lb" else "kg"}"
}

private fun fmtDay(iso: String): String = runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrDefault(iso)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CareLogScreen(vm: AppViewModel) {
    val data by vm.data.collectAsState()
    val s = data.settings
    val pc = LocalPaw.current
    var filter by remember { mutableStateOf("all") }
    var adding by remember { mutableStateOf(false) }
    val all = data.log.sortedByDescending { it.date }
    val list = if (filter == "all") all else all.filter { it.kind == filter }
    val weights = all.filter { it.kind == "weight" && it.weightKg != null }.reversed()
    val name = s.dogName.ifBlank { "your dog" }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 40.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton("back", "Back", { vm.pop() }); H("Care log", 22, Modifier.weight(1f), TextAlign.Center); IconButton("plus", "Add an entry", { adding = true })
        }
        Muted("Vet visits, vaccines, weight and medicine for $name. Stays on this device and in your backup.", Modifier.padding(top = 12.dp), size = 15)
        if (all.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                all.firstOrNull { it.kind == "vet" }?.let { Fact(Modifier.weight(1f), fmtDay(it.date), "Last vet visit") }
                weights.lastOrNull()?.let { Fact(Modifier.weight(1f), showWeight(it.weightKg!!, s.weightUnit), "Latest weight") }
                Fact(Modifier.weight(1f), "${all.size}", if (all.size == 1) "entry" else "entries")
            }
        }
        if (weights.size > 1) Panel(Modifier.padding(top = 14.dp)) {
            PanelTitle("Weight", "scale") {}
            val line = pc.kibble
            Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                val lo = weights.minOf { it.weightKg!! }; val hi = weights.maxOf { it.weightKg!! }; val span = if (hi - lo == 0.0) 1.0 else hi - lo
                val pad = 10.dp.toPx()
                val pts = weights.mapIndexed { i, e -> Offset(pad + i * (size.width - 2 * pad) / (weights.size - 1), (size.height - pad - ((e.weightKg!! - lo) / span).toFloat() * (size.height - 2 * pad))) }
                for (i in 0 until pts.size - 1) drawLine(line, pts[i], pts[i + 1], strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                pts.forEach { drawCircle(line, 4.dp.toPx(), it) }
            }
        }
        Row(Modifier.padding(top = 14.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf("all" to "All") + LOG_KINDS.map { it.first to it.second.second }).forEach { (k, label) ->
                Chip(label, Modifier.clickable(role = Role.RadioButton) { filter = k }, filled = if (filter == k) pc.kibble else null, textColor = if (filter == k) onAccent(pc.kibble) else null)
            }
        }
        if (list.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                H("No entries yet", 24, align = TextAlign.Center)
                Muted("Add the next vet visit, a vaccine, or $name's weight. Everything is kept in one place.", Modifier.padding(top = 8.dp), 15, TextAlign.Center)
                Box(Modifier.padding(top = 14.dp)) { PrimaryButton("Add the first entry", { adding = true }) }
            }
        } else Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { e ->
                val info = LOG_KINDS.firstOrNull { it.first == e.kind }?.second ?: ("edit" to "Note")
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(pc.surface).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(pc.kibble.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Icon(info.first, pc.ink, 22.dp) }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        B(info.second + (if (e.kind == "weight" && e.weightKg != null) " · " + showWeight(e.weightKg, s.weightUnit) else ""), weight = FontWeight.ExtraBold)
                        Muted(fmtDay(e.date) + (if (e.note.isNotBlank()) " · " + e.note else ""), size = 13)
                    }
                    Box(Modifier.size(44.dp).clickable(role = Role.Button, onClickLabel = "Delete this entry") { vm.repo.update { d -> d.copy(log = d.log.filter { it.id != e.id }) }; vm.say("Entry removed") }, contentAlignment = Alignment.Center) { Icon("trash", pc.ink2, 20.dp) }
                }
            }
        }
    }

    if (adding) {
        var kind by remember { mutableStateOf("vet") }
        var date by remember { mutableStateOf(LocalDate.now()) }
        var weight by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var showDate by remember { mutableStateOf(false) }
        PawSheet({ adding = false }) {
            H("Add to the care log", 22, Modifier.fillMaxWidth().padding(bottom = 8.dp), TextAlign.Center)
            Seg(LOG_KINDS.map { it.first to it.second.second }, kind, { kind = it }, label = "Kind of entry")
            PickerRow("Date", date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))) { showDate = true }
            if (kind == "weight") Field("Weight (${s.weightUnit})", weight, { weight = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, placeholder = if (s.weightUnit == "lb") "35" else "16", keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal), maxLen = 6)
            Field("Note (optional)", note, { note = it }, placeholder = when (kind) { "vaccine" -> "Rabies booster"; "vet" -> "Yearly checkup"; "meds" -> "Flea & tick"; else -> "" }, maxLen = 200)
            Box(Modifier.padding(top = 18.dp)) {
                PrimaryButton("Save", {
                    val raw = weight.toDoubleOrNull()
                    if (kind == "weight" && (raw == null || raw <= 0 || raw >= 500)) vm.say("Enter a weight")
                    else {
                        val kg = if (kind == "weight") Math.round((if (s.weightUnit == "lb") raw!! * KG_PER_LB else raw!!) * 10) / 10.0 else null
                        vm.repo.update { d -> d.copy(log = (d.log + LogEntry(newId(), kind, date.toString(), note.trim().take(200), kg)).takeLast(500)) }
                        vm.say("Added to the care log"); adding = false
                    }
                }, Modifier.fillMaxWidth())
            }
            Box(Modifier.padding(top = 10.dp)) { GhostButton("Cancel", { adding = false }) }
        }
        if (showDate) {
            val st = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli())
            androidx.compose.material3.DatePickerDialog({ showDate = false },
                confirmButton = { androidx.compose.material3.TextButton({ st.selectedDateMillis?.let { ms -> val d = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC).toLocalDate(); if (!d.isAfter(LocalDate.now())) date = d }; showDate = false }) { androidx.compose.material3.Text("OK") } },
                dismissButton = { androidx.compose.material3.TextButton({ showDate = false }) { androidx.compose.material3.Text("Cancel") } }) { androidx.compose.material3.DatePicker(st) }
        }
    }
}

@Composable
private fun Fact(modifier: Modifier, big: String, small: String) {
    val pc = LocalPaw.current
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(pc.surface).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        H(big, 17, align = TextAlign.Center); Muted(small, size = 12, align = TextAlign.Center)
    }
}
