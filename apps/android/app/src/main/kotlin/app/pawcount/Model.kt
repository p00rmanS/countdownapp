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

fun countdownFromJson(o: JSONObject): Countdown {
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
        ),
    )
}
