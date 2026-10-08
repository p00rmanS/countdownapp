package app.pawcount

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/*
 * Logic.kt - the brain of the app (no UI in here, so it is unit-tested in src/test).
 * Port of js/countdown.js; the iOS app has the same logic in Logic.swift.
 *
 *  - A countdown stores its target as a wall-clock time + an IANA zone ("Asia/Tokyo"). java.time resolves that
 *    to a real instant, including daylight-saving gaps and overlaps.
 *  - compute() works out everything the screens need: time left, sleeps, progress, which of the 7 dog stages
 *    applies, and where a yearly repeat rolls over.
 *  - Stage rules: nap >75% time left, curious 75-40%, waiting 40-15%, packing <15% or the last 7 days,
 *    zoomies the last 24 h, today on the day itself, memory afterwards.
 */

const val DAY = 86_400_000L
const val HOUR = 3_600_000L
const val MIN = 60_000L

enum class Stage(val key: String, val label: String, val emoji: String) {
    NAP("nap", "Napping", "😴"), CURIOUS("curious", "Curious", "👀"), WAITING("waiting", "Waiting", "🐕"),
    PACKING("packing", "Packing", "🎒"), ZOOMIES("zoomies", "Zoomies", "⚡"), TODAY("today", "It's today!", "🎉"),
    MEMORY("memory", "Memory", "💛");
}

enum class Phase { UPCOMING, TODAY, PAST }

data class Parts(val d: Long, val h: Long, val m: Long, val s: Long)

data class Computed(
    val zone: ZoneId, val target: Long, val start: Long, val total: Long, val remaining: Long, val leftPct: Double,
    val progress: Double, val phase: Phase, val stage: Stage, val parts: Parts, val sleeps: Int, val daysCeil: Int,
    val weeks: Int, val weekDays: Int, val daysTotal: Int, val daysIn: Int, val age: Int?, val together: Int?,
    val occYear: Int, val daysSince: Int,
) {
    /** changes whenever the screen needs a re-draw (new stage or the moment the timer hits zero) */
    val key: String get() = "${stage.key}:${phase.name}:${remaining <= 0}"
}

fun zoneOf(id: String): ZoneId = runCatching { ZoneId.of(id) }.getOrElse { ZoneId.systemDefault() }

fun compute(c: Countdown, now: Long = System.currentTimeMillis()): Computed {
    val zone = zoneOf(c.timeZone)
    val base = LocalDateTime.parse(c.targetAt)
    fun occ(year: Int): LocalDate {
        val ym = YearMonth.of(year, base.month)
        return ym.atDay(min(base.dayOfMonth, ym.lengthOfMonth()))   // 29 Feb -> 28 Feb in non-leap years
    }
    fun instant(d: LocalDate): Long =
        ZonedDateTime.of(d, if (c.allDay) LocalTime.MIDNIGHT else base.toLocalTime(), zone).toInstant().toEpochMilli()
    fun dayEnd(d: LocalDate) = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    // Yearly repeats: walk forward until an occurrence whose day isn't over yet.
    var date = base.toLocalDate()
    var prevEnd: Long? = null
    if (c.yearly) {
        var y = base.year
        for (i in 0 until 400) {
            date = occ(y)
            val end = dayEnd(date)
            if (end > now) break
            prevEnd = end; y++
        }
    }
    val target = instant(date)
    val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayFinish = dayEnd(date)
    val start = if (prevEnd != null) max(c.createdAt, prevEnd) else c.createdAt
    val total = max(target - start, HOUR)
    val remaining = target - now
    val leftPct = (remaining.toDouble() / total).coerceIn(0.0, 1.0)

    val phase = when { now < dayStart -> Phase.UPCOMING; now < dayFinish -> Phase.TODAY; else -> Phase.PAST }
    val stage = when {
        phase == Phase.TODAY -> Stage.TODAY
        phase == Phase.PAST -> Stage.MEMORY
        remaining <= DAY -> Stage.ZOOMIES
        remaining <= 7 * DAY || leftPct <= 0.15 -> Stage.PACKING
        leftPct <= 0.40 -> Stage.WAITING
        leftPct <= 0.75 -> Stage.CURIOUS
        else -> Stage.NAP
    }
    val rem = max(0, remaining)
    val parts = Parts(rem / DAY, (rem % DAY) / HOUR, (rem % HOUR) / MIN, (rem % MIN) / 1000)
    // "sleeps" = local midnights between now and the target, in the phone's own time zone
    val here = ZoneId.systemDefault()
    val sleeps = max(0, ChronoUnit.DAYS.between(Instant.ofEpochMilli(now).atZone(here).toLocalDate(), Instant.ofEpochMilli(target).atZone(here).toLocalDate()).toInt())
    val progress = if (phase == Phase.UPCOMING) 1 - leftPct else 1.0
    val daysTotal = max(1, Math.round(total.toDouble() / DAY).toInt())
    val daysIn = min(daysTotal, max(0, ((now - start) / DAY).toInt() + 1))
    val age = c.person?.birthYear?.let { date.year - it }
    val together = if (c.type == "anniversary")
        max(0, ((now - base.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()) / DAY).toInt()) else null
    return Computed(
        zone, target, start, total, remaining, leftPct, progress, phase, stage, parts, sleeps,
        ceil(rem.toDouble() / DAY).toInt(), (parts.d / 7).toInt(), (parts.d % 7).toInt(), daysTotal, daysIn, age, together,
        date.year, if (phase == Phase.PAST) max(1, ((now - dayFinish) / DAY).toInt() + 1) else 0,
    )
}

/* ---------- text helpers ---------- */

fun plural(n: Int, w: String) = "$n $w${if (n == 1) "" else "s"}"
fun plural(n: Long, w: String) = plural(n.toInt(), w)

fun spoken(k: Computed): String {
    if (k.phase == Phase.TODAY && k.remaining <= 0) return "It's today"
    if (k.phase == Phase.PAST) return plural(k.daysSince, "day") + " ago"
    val (d, h, m) = k.parts.let { Triple(it.d, it.h, it.m) }
    return when {
        d >= 1 -> plural(d, "day") + if (h > 0) " and ${plural(h, "hour")}" else ""
        h >= 1 -> plural(h, "hour") + if (m > 0) " and ${plural(m, "minute")}" else ""
        else -> plural(max(1, m), "minute")
    }
}

/** one cell of the big number display */
data class NumUnit(val key: String, val value: Long, val label: String, val pad: Int)

fun units(k: Computed, mode: DisplayMode): List<NumUnit> {
    fun u(key: String, v: Long, w: String, pad: Int = 1) = NumUnit(key, v, if (v == 1L) w else w + "s", pad)
    return when (mode) {
        DisplayMode.DAYS -> listOf(u("d", k.daysCeil.toLong(), "day"))
        DisplayMode.SLEEPS -> listOf(u("sl", k.sleeps.toLong(), "sleep"))
        DisplayMode.WEEKS -> listOf(u("w", k.weeks.toLong(), "week"), u("wd", k.weekDays.toLong(), "day"))
        DisplayMode.FULL -> listOf(u("d", k.parts.d, "day"), NumUnit("h", k.parts.h, "hrs", 2), NumUnit("m", k.parts.m, "min", 2), NumUnit("s", k.parts.s, "sec", 2))
    }
}

fun shortCount(c: Countdown, k: Computed): String {
    if (k.phase == Phase.TODAY) return "It's today! 🎉"
    if (k.phase == Phase.PAST) return plural(k.daysSince, "day") + " ago"
    return when (c.displayMode) {
        DisplayMode.DAYS -> plural(k.daysCeil, "day")
        DisplayMode.SLEEPS -> plural(k.sleeps, "sleep")
        DisplayMode.WEEKS -> if (k.weeks > 0) "${k.weeks} wk${if (k.weeks == 1) "" else "s"} ${k.weekDays} d" else plural(k.parts.d, "day")
        DisplayMode.FULL -> if (k.parts.d > 0) "${k.parts.d}d ${k.parts.h}h" else "${k.parts.h}h ${k.parts.m.toString().padStart(2, '0')}m"
    }
}

private val packLabel = mapOf(
    "intl_trip" to ("Packing" to "dragging a suitcase and clutching a passport"),
    "vacation" to ("Packing" to "wearing sunglasses beside a suitcase"),
    "birthday" to ("Party prep" to "wearing a party hat beside a present"),
    "anniversary" to ("Flowers ready" to "holding a bouquet with a flower crown"),
    "holiday" to ("Wrapping" to "in a holiday hat beside a present"),
    "custom" to ("Ready" to "ready with a tennis ball"),
)

/** label + sentence for the current stage ("Sunny is curled up asleep…") */
fun stageLabel(c: Countdown, k: Computed): String = if (k.stage == Stage.PACKING) (packLabel[c.type] ?: packLabel["custom"]!!).first else k.stage.label

fun stageSentence(c: Countdown, k: Computed): String {
    val d = when (k.stage) {
        Stage.NAP -> "curled up asleep, dreaming of the big day"
        Stage.CURIOUS -> "sitting up with a curious head tilt"
        Stage.WAITING -> "sitting by the door, tail wagging slowly"
        Stage.PACKING -> (packLabel[c.type] ?: packLabel["custom"]!!).second
        Stage.ZOOMIES -> "running laps with a tail that is a blur"
        Stage.TODAY -> "celebrating with confetti"
        Stage.MEMORY -> "lying on a souvenir, remembering"
    }
    return "${c.dog.name} is $d"
}

fun describeDog(c: Countdown, k: Computed) = "${stageSentence(c, k)}. ${spoken(k)}${if (k.phase == Phase.PAST) "" else " left"}."

fun plainTitle(t: String) = t.replace(Regex("[\\x{1F000}-\\x{1FFFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{200D}]"), "").replace(Regex("\\s+"), " ").trim().ifEmpty { "the big day" }

/** what the dog says when tapped / petted / shaken / fetching */
fun dogLine(c: Countdown, k: Computed, kind: String = "tap", rnd: java.util.Random = java.util.Random()): String {
    fun <T> pick(a: List<T>) = a[rnd.nextInt(a.size)]
    when (kind) {
        "pet" -> return pick(listOf("*happy sigh* 💛", "Best human. Ever.", "Mmm… more pets, please.", "*leans in*"))
        "sneeze" -> return pick(listOf("Ah… ah… ACHOO!", "Achoo! Who shook the room?!"))
        "fetch" -> return pick(listOf("Got it! Throw again?", "Fetched! Good dog? GOOD DOG."))
    }
    val t = plainTitle(c.title)
    val sl = if (k.sleeps == 1) "1 more sleep" else "${k.sleeps} more sleeps"
    val lines = when (k.stage) {
        Stage.NAP -> listOf("Zzz… $sl… zzz…", "*snore* five more minutes…", "Mmf. Wake me for $t.")
        Stage.CURIOUS -> listOf("Is it $t yet? $sl!", "*head tilt* $sl?", "Ooh! You came to visit!")
        Stage.WAITING -> listOf("I'm by the door! $sl!!", "Woof! ${k.daysCeil} days to go!", "Still waiting… tail says hi.")
        Stage.PACKING -> listOf("Packed! $sl!!", "Did you zip the suitcase? $sl!", "Almost time!! Almost time!!")
        Stage.ZOOMIES -> listOf("ZOOOOM! Almost here!!", "${k.parts.h + if (k.parts.d > 0) 24 else 0} hours!! I can't sit still!!", "WOOF WOOF WOOF!")
        Stage.TODAY -> listOf("IT'S TODAY!!! 🎉", "BEST. DAY. EVER.", "WOOF! ${t.uppercase()}!")
        Stage.MEMORY -> listOf("That was the best. 💛", "I remember $t!", "Can we do it again?")
    }
    return pick(lines)
}

/* ---------- dates for display ---------- */

fun fmtInstant(ms: Long, zone: ZoneId, allDay: Boolean, noYear: Boolean = false): String {
    val z = Instant.ofEpochMilli(ms).atZone(zone)
    val date = DateTimeFormatter.ofPattern(if (noYear) "EEE, MMM d" else "EEE, MMM d, yyyy", Locale.getDefault()).format(z)
    return if (allDay) date else date + " · " + DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()).format(z)
}

fun tzLabel(id: String): String {
    val z = zoneOf(id)
    val city = id.substringAfterLast('/').replace('_', ' ')
    val off = z.rules.getOffset(Instant.now()).totalSeconds
    val sign = if (off < 0) "-" else "+"
    val h = Math.abs(off) / 3600; val m = Math.abs(off) % 3600 / 60
    return "$city (GMT$sign$h${if (m != 0) ":" + m.toString().padStart(2, '0') else ""})"
}

/* ---------- reminders & samples ---------- */

fun defaultReminders(type: String): List<Reminder> = buildList {
    add(Reminder(100 * 1440, "100 days to go", true)); add(Reminder(50 * 1440, "50 days to go", true))
    add(Reminder(30 * 1440, "30 days to go", true)); add(Reminder(7 * 1440, "One week to go", true))
    add(Reminder(1440, "Tomorrow (zoomies!)", true)); add(Reminder(0, "Morning of", true))
    if (type == "intl_trip") add(Reminder(90 * 1440, "Passport check · 90 days", true, passport = true))
}

data class PlannedReminder(val id: Int, val title: String, val body: String, val at: Long)

/** The reminder schedule: at most one per local day, only in the future, iOS-style cap of 60 */
fun planReminders(list: List<Countdown>, now: Long = System.currentTimeMillis()): List<PlannedReminder> {
    val out = mutableListOf<PlannedReminder>()
    for (c in list.filter { !it.archived }) {
        val k = compute(c, now)
        if (k.phase == Phase.PAST) continue
        val title = plainTitle(c.title); val name = c.dog.name
        for (n in c.reminders.filter { it.enabled }) {
            val at = if (n.offsetMinutes == 0) {
                val d = Instant.ofEpochMilli(k.target).atZone(k.zone).toLocalDate()
                d.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } else k.target - n.offsetMinutes * MIN
            if (at <= now + MIN || at > now + 400 * DAY) continue
            val d = Math.round(n.offsetMinutes / 1440.0).toInt()
            val body = when {
                n.passport -> "🛂 Quick check — are your passports valid 6+ months past the trip?"
                n.offsetMinutes == 0 -> "🎉 IT'S TODAY! $name is losing it."
                d == 1 -> "⚡ Zoomies activated. Tomorrow is $title!"
                d == 7 -> "🎒 $name started packing. One week until $title!"
                else -> "🐕 $name just counted: $d days until $title!"
            }
            out += PlannedReminder(Math.abs((c.id + ":" + n.offsetMinutes).hashCode()) % 2_000_000_000 + 1, "$name · ${c.title}", body, at)
        }
    }
    out.sortBy { it.at }
    val seen = HashSet<LocalDate>()
    return out.filter { seen.add(Instant.ofEpochMilli(it.at).atZone(ZoneId.systemDefault()).toLocalDate()) }.take(60)
}

fun makeSamples(now: Long = System.currentTimeMillis()): List<Countdown> {
    val here = ZoneId.systemDefault()
    fun wall(ms: Long, tz: String, h: Int, mi: Int): String = Instant.ofEpochMilli(ms).atZone(zoneOf(tz)).toLocalDate().atTime(h, mi).toString().take(16)
    fun mk(key: String, title: String, type: String, targetAt: String, since: Long, dog: Dog, tz: String = here.id, allDay: Boolean = false,
           yearly: Boolean = false, mode: DisplayMode = DisplayMode.FULL, notes: String = "", checks: List<CheckItem> = emptyList(),
           dest: Destination? = null, person: Person? = null, accent: String? = null) =
        Countdown("sample-$key", title, type, targetAt, tz, allDay, yearly, now - since * DAY, dog, accent ?: typeAccent(type), mode, notes, checks, dest, person,
            defaultReminders(type), sample = true)
    val today = Instant.ofEpochMilli(now).atZone(here).toLocalDate()
    val nowHour = Instant.ofEpochMilli(now).atZone(here).hour
    return listOf(
        mk("japan", "Japan 🇯🇵", "intl_trip", wall(now + 190 * DAY, "Asia/Tokyo", 9, 30), 30, Dog("shiba", "Miso"), "Asia/Tokyo",
            notes = "Book the ryokan. Learn how to say \"good dog\" in Japanese.", checks = listOf(CheckItem("a", "Renew passport", true), CheckItem("b", "JR Pass", false), CheckItem("c", "Portable Wi-Fi", false)),
            dest = Destination("Japan", "Tokyo", "🇯🇵")),
        mk("mia", "Mia's birthday", "birthday", today.plusDays(52).minusYears(30).atStartOfDay().toString().take(16), 40, Dog("corgi", "Biscuit"), allDay = true, yearly = true,
            mode = DisplayMode.DAYS, notes = "Gift ideas: pottery class, the blue scarf.", person = Person("Mia", today.plusDays(52).year - 30)),
        mk("bali", "Bali getaway", "vacation", wall(now + 28 * DAY, "Asia/Makassar", 14, 0), 52, Dog("golden", "Sunny"), "Asia/Makassar", mode = DisplayMode.SLEEPS,
            checks = listOf(CheckItem("a", "Sunscreen", true), CheckItem("b", "Reef-safe snorkel gear", false)), dest = Destination("Indonesia", "Bali", "🇮🇩")),
        mk("anniv", "Our anniversary", "anniversary", today.plusDays(5).minusYears(3).atTime(19, 0).toString().take(16), 60, Dog("dachshund", "Noodle"), yearly = true,
            mode = DisplayMode.WEEKS, notes = "Book the little place with the candles."),
        // tomorrow at (current hour - 2): always inside the last 24 h but not "today"
        mk("ski", "Ski weekend", "vacation", today.plusDays(1).atTime(max(0, nowHour - 2), 0).toString().take(16), 18, Dog("husky", "Blizzard"), accent = "#5DADE8"),
        mk("dad", "Dad's birthday", "birthday", today.minusYears(61).atStartOfDay().toString().take(16), 21, Dog("mutt", "Lucky", "#B98A62", "floppy"), allDay = true, yearly = true,
            person = Person("Dad", today.year - 61)),
        mk("lisbon", "Lisbon 🇵🇹", "intl_trip", wall(now - 30 * DAY, "Europe/Lisbon", 11, 0), 100, Dog("golden", "Sunny"), "Europe/Lisbon",
            notes = "Pastéis de nata at midnight. Worth it.", dest = Destination("Portugal", "Lisbon", "🇵🇹")),
    )
}

fun typeAccent(type: String) = when (type) {
    "intl_trip" -> "#5DADE8"; "vacation" -> "#3FBFA8"; "birthday" -> "#FF7DAA"; "anniversary" -> "#EE8FA0"; "holiday" -> "#3E9C6C"; else -> "#E8A15C"
}
