package app.pawcount

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/*
 * Model.kt - the data the user owns. Mirrors the web app's JSON so backups can move between the web app,
 * Android and iOS. Everything is plain data classes + hand-written JSON (org.json ships with Android).
 */

enum class DisplayMode(val key: String, val label: String) {
    FULL("full", "Full timer"), DAYS("days", "Days"), SLEEPS("sleeps", "Sleeps"), WEEKS("weeks", "Weeks");

    companion object { fun of(k: String?) = entries.firstOrNull { it.key == k } ?: FULL }
}

data class Dog(
    val breed: String,
    val name: String,
    val furColor: String? = null,   // mutt only: "#RRGGBB"
    val ears: String? = null,       // mutt only: floppy | pointy | tall | long
) {
    /** key of the exported drawing: "golden" or "mutt-floppy" */
    val variant: String get() = if (breed == "mutt") "mutt-${ears ?: "floppy"}" else breed
}

data class CheckItem(val id: String, val text: String, val done: Boolean)
data class Reminder(val offsetMinutes: Int, val label: String, val enabled: Boolean, val passport: Boolean = false)
data class Destination(val country: String, val city: String?, val flag: String)
data class Person(val name: String, val birthYear: Int?)

data class Countdown(
    val id: String,
    val title: String,
    val type: String,
    val targetAt: String,          // wall clock "2027-03-14T09:30" in [timeZone]
    val timeZone: String,
    val allDay: Boolean,
    val yearly: Boolean,
    val createdAt: Long,           // epoch ms; start of the progress bar
    val dog: Dog,
    val accent: String,
    val displayMode: DisplayMode,
    val notes: String = "",
    val checklist: List<CheckItem> = emptyList(),
    val destination: Destination? = null,
    val person: Person? = null,
    val reminders: List<Reminder> = emptyList(),
    val archived: Boolean = false,
    val photos: List<String> = emptyList(), // file names inside the app's photo folder
    val sample: Boolean = false,
)

data class Settings(
    val displayMode: DisplayMode = DisplayMode.FULL,
    val haptics: Boolean = true,
    val sound: Boolean = false,
    val reduceMotion: String = "system",   // system | on | off
    val theme: String = "system",          // system | light | dark
    val notifications: Boolean = false,
    val profileName: String = "",
    val profilePhoto: String = "",        // file name in the photo folder
    val parentTitle: String = "",         // what the dog calls the person: mama | papa | parent
)

data class AppData(
    val onboarded: Boolean = false,
    val countdowns: List<Countdown> = emptyList(),
    val celebrated: Set<String> = emptySet(),
    val settings: Settings = Settings(),
)

fun newId(): String = UUID.randomUUID().toString()

/* ---------- JSON ---------- */

private fun JSONObject.optStr(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null

fun Countdown.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("title", title); put("type", type); put("targetAt", targetAt); put("timeZone", timeZone)
    put("allDay", allDay); put("recurrence", if (yearly) "yearly" else "none")
    put("createdAt", java.time.Instant.ofEpochMilli(createdAt).toString())
    put("dog", JSONObject().put("breed", dog.breed).put("name", dog.name).apply {
        if (dog.furColor != null) put("colors", JSONObject().put("fur", dog.furColor))
        if (dog.ears != null) put("ears", dog.ears)
    })
    put("accent", accent); put("displayMode", displayMode.key); put("notes", notes)
    put("checklist", JSONArray(checklist.map { JSONObject().put("id", it.id).put("text", it.text).put("done", it.done) }))
    destination?.let { put("destination", JSONObject().put("country", it.country).put("city", it.city ?: JSONObject.NULL).put("flag", it.flag)) }
    person?.let { put("person", JSONObject().put("name", it.name).apply { if (it.birthYear != null) put("birthYear", it.birthYear) }) }
    put("notifications", JSONArray(reminders.map { JSONObject().put("offsetMinutes", it.offsetMinutes).put("label", it.label).put("enabled", it.enabled).apply { if (it.passport) put("passport", true) } }))
    put("archived", archived); put("memoryPhotos", JSONArray(photos)); put("sample", sample)
}

/* ---------- untrusted data (shared links, backup files): only known-good shapes survive ---------- */
private val TYPE_KEYS = setOf("intl_trip", "vacation", "birthday", "anniversary", "holiday", "custom")
private val BREED_KEYS = setOf("scott", "golden", "corgi", "shiba", "dachshund", "husky", "mutt")
private val EAR_KEYS = setOf("floppy", "pointy", "tall", "long")
private val HEX6 = Regex("^#[0-9A-Fa-f]{6}$")
private val WALL = Regex("^\\d{4}-\\d{2}-\\d{2}(T\\d{2}:\\d{2})?$")
/** photo files are named <uuid>.jpg; anything else (e.g. "../../x") could point outside the photo folder */
val SAFE_PHOTO = Regex("^[A-Za-z0-9-]{1,64}\\.jpg$")

private fun Countdown.sanitized(): Countdown {
    require(title.isNotBlank() && WALL.matches(targetAt)) { "not a countdown" }
    runCatching { if (targetAt.length > 10) java.time.LocalDateTime.parse(targetAt) else java.time.LocalDate.parse(targetAt) }.getOrElse { throw IllegalArgumentException("bad date") }
    val zone = runCatching { java.time.ZoneId.of(timeZone); timeZone }.getOrDefault(java.util.TimeZone.getDefault().id)
    return copy(
        id = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64).ifEmpty { newId() },
        title = title.trim().take(80), type = if (type in TYPE_KEYS) type else "custom", timeZone = zone, notes = notes.take(2000),
        accent = if (HEX6.matches(accent)) accent else "#E8A15C",
        dog = dog.copy(breed = if (dog.breed in BREED_KEYS) dog.breed else "mutt", name = dog.name.trim().take(30).ifBlank { "Buddy" },
            furColor = dog.furColor?.takeIf { HEX6.matches(it) }, ears = dog.ears?.takeIf { it in EAR_KEYS }),
        checklist = checklist.take(100).map { it.copy(text = it.text.take(200)) }, reminders = reminders.take(12),
        photos = photos.filter { SAFE_PHOTO.matches(it) }.take(6),
        destination = destination?.let { it.copy(country = it.country.take(60), city = it.city?.take(60), flag = it.flag.take(8)) },
        person = person?.let { it.copy(name = it.name.take(40)) },
    )
}

fun countdownFromJson(o: JSONObject): Countdown = rawCountdownFromJson(o).sanitized()

private fun rawCountdownFromJson(o: JSONObject): Countdown {
    val dogO = o.getJSONObject("dog")
    val created = o.optStr("createdAt")?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() } ?: System.currentTimeMillis()
    val checks = o.optJSONArray("checklist") ?: JSONArray()
    val notifs = o.optJSONArray("notifications") ?: JSONArray()
    val photos = o.optJSONArray("memoryPhotos") ?: JSONArray()
    return Countdown(
        id = o.getString("id"), title = o.getString("title"), type = o.getString("type"),
        targetAt = o.getString("targetAt"), timeZone = o.optString("timeZone", java.util.TimeZone.getDefault().id),
        allDay = o.optBoolean("allDay"), yearly = o.optString("recurrence") == "yearly", createdAt = created,
        dog = Dog(dogO.getString("breed"), dogO.getString("name"), dogO.optJSONObject("colors")?.optStr("fur"), dogO.optStr("ears")),
        accent = o.optString("accent", "#E8A15C"), displayMode = DisplayMode.of(o.optStr("displayMode")), notes = o.optString("notes", ""),
        checklist = (0 until checks.length()).map { checks.getJSONObject(it).let { c -> CheckItem(c.optString("id", newId()), c.getString("text"), c.optBoolean("done")) } },
        destination = o.optJSONObject("destination")?.let { Destination(it.getString("country"), it.optStr("city"), it.optString("flag", "")) },
        person = o.optJSONObject("person")?.let { Person(it.optString("name", ""), if (it.has("birthYear")) it.optInt("birthYear") else null) },
        reminders = (0 until notifs.length()).map { notifs.getJSONObject(it).let { n -> Reminder(n.getInt("offsetMinutes"), n.optString("label", ""), n.optBoolean("enabled", true), n.optBoolean("passport")) } },
        archived = o.optBoolean("archived"), photos = (0 until photos.length()).mapNotNull { photos.optString(it).takeIf { s -> s.isNotEmpty() && !s.startsWith("data:") } },
        sample = o.optBoolean("sample"),
    )
}

fun AppData.toJson(): JSONObject = JSONObject().apply {
    put("onboarded", onboarded)
    put("countdowns", JSONArray(countdowns.map { it.toJson() }))
    put("celebrated", JSONObject().apply { celebrated.forEach { put(it, 1) } })
    put("settings", JSONObject().apply {
        put("displayMode", settings.displayMode.key); put("haptics", settings.haptics); put("sound", settings.sound)
        put("motion", settings.reduceMotion); put("theme", settings.theme); put("notifications", settings.notifications)
        put("profileName", settings.profileName); put("profilePhoto", settings.profilePhoto); put("parentTitle", settings.parentTitle)
    })
}

fun appDataFromJson(raw: JSONObject): AppData {
    val d = raw.optJSONObject("data") ?: raw          // accept both a bare store and a "backup" wrapper
    val arr = d.optJSONArray("countdowns") ?: throw IllegalArgumentException("Not a Pawcount backup")
    val s = d.optJSONObject("settings")
    val cel = d.optJSONObject("celebrated")
    return AppData(
        onboarded = d.optBoolean("onboarded"),
        countdowns = (0 until arr.length()).mapNotNull { runCatching { countdownFromJson(arr.getJSONObject(it)) }.getOrNull() },
        celebrated = cel?.keys()?.asSequence()?.toSet() ?: emptySet(),
        settings = Settings(
            displayMode = DisplayMode.of(s?.optStr("displayMode")), haptics = s?.optBoolean("haptics", true) ?: true,
            sound = s?.optBoolean("sound") ?: false, reduceMotion = s?.optString("motion", "system") ?: "system",
            theme = s?.optString("theme", "system") ?: "system", notifications = s?.optBoolean("notifications") ?: false,
            profileName = (s?.optString("profileName", "") ?: "").take(30), profilePhoto = (s?.optString("profilePhoto", "") ?: "").takeIf { SAFE_PHOTO.matches(it) } ?: "",
            parentTitle = (s?.optString("parentTitle", "") ?: "").takeIf { it in setOf("mama", "papa", "parent") } ?: "",
        ),
    )
}
