package app.pawcount

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Services.kt - everything that touches the phone rather than the screen:
 *   Repo        saves the app data (one JSON file) and the memory photos
 *   Haptics     taps, purrs and the success buzz
 *   Sounds      barks and chimes, synthesised on the fly (no audio files)
 *   ShakeDetector  accelerometer jerk detector
 *   Reminders   schedules the notifications with AlarmManager so they fire with the app closed
 */

/* ------------------------------ storage ------------------------------ */

class Repo(private val ctx: Context) {
    private val file = File(ctx.filesDir, "pawcount.json")
    private val photoDir = File(ctx.filesDir, "photos").apply { mkdirs() }
    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data

    private fun load(): AppData = runCatching { appDataFromJson(JSONObject(file.readText())) }.getOrDefault(AppData())

    fun update(f: (AppData) -> AppData) {
        val next = f(_data.value)
        _data.value = next
        runCatching { file.writeText(next.toJson().toString()) }
        Reminders.reschedule(ctx, next)
        PawWidget.updateAll(ctx)         // keep home-screen widgets in step with what was just saved
    }

    fun export(): String = JSONObject().put("app", "pawcount").put("version", 1).put("exportedAt", java.time.Instant.now().toString()).put("data", _data.value.toJson()).toString(2)

    /** Copies a picked photo into the app's own folder, scaled down so backups and memory stay small. */
    fun importPhoto(uri: Uri, maxSide: Float = 960f): String? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / sample > 1600) sample *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val scale = minOf(1f, maxSide / maxOf(bmp.width, bmp.height))
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val name = newId() + ".jpg"
        File(photoDir, name).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        name
    }.getOrNull()

    fun photoFile(name: String) = File(photoDir, name)
    fun deletePhoto(name: String) { photoFile(name).delete() }
}

/* ------------------------------ haptics ------------------------------ */

object Haptics {
    private fun vib(ctx: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    fun play(ctx: Context, enabled: Boolean, kind: String) {
        if (!enabled) return
        val v = vib(ctx) ?: return
        val effect = when (kind) {
            "purr" -> VibrationEffect.createWaveform(longArrayOf(0, 12, 40, 12, 40, 12), -1)
            "success" -> VibrationEffect.createWaveform(longArrayOf(0, 18, 50, 18, 50, 70), -1)
            "soft" -> VibrationEffect.createOneShot(5, VibrationEffect.DEFAULT_AMPLITUDE)
            else -> VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE)   // tick
        }
        runCatching { v.vibrate(effect) }
    }
}

/* ------------------------------ sounds ------------------------------ */

object Sounds {
    private const val RATE = 22050

    /** one tone: frequency (optionally sliding to [slide]), length in seconds, 0..1 volume, saw or sine wave */
    private fun tone(freq: Float, slide: Float?, secs: Float, vol: Float, saw: Boolean): ShortArray {
        val n = (RATE * secs).toInt(); val out = ShortArray(n); var phase = 0.0
        for (i in 0 until n) {
            val t = i.toFloat() / n
            val f = if (slide != null) freq * Math.pow((slide / freq).toDouble(), t.toDouble()).toFloat() else freq
            phase += 2 * PI * f / RATE
            val s = if (saw) ((phase / PI) % 2.0 - 1.0) else sin(phase)
            val env = (minOf(1f, i / 300f) * exp(-3.0 * t)).toFloat()
            out[i] = (s * env * vol * 32767).toInt().toShort()
        }
        return out
    }

    private fun build(kind: String): ShortArray = when (kind) {
        "bark" -> tone(520f, 260f, 0.12f, 0.3f, true) + ShortArray((RATE * 0.04).toInt()) + tone(480f, 240f, 0.14f, 0.28f, true)
        "chime" -> listOf(523f, 659f, 784f, 1047f).map { tone(it, null, 0.3f, 0.3f, false) }.reduce { a, b -> a.copyOf(a.size - (RATE * 0.21).toInt().coerceAtMost(a.size)) + b }
        else -> tone(1568f, null, 0.2f, 0.2f, false) + tone(1976f, null, 0.2f, 0.2f, false)   // jingle
    }

    fun play(enabled: Boolean, kind: String) {
        if (!enabled) return
        Thread {
            runCatching {
                val pcm = build(kind)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(pcm.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
                track.write(pcm, 0, pcm.size); track.play()
                Thread.sleep((pcm.size * 1000L / RATE) + 100); track.release()
            }
        }.start()
    }
}

/* ------------------------------ shake ------------------------------ */

/** Calls [onShake] when the phone is shaken: a big, sudden change between two accelerometer samples. */
class ShakeDetector(ctx: Context, private val onShake: () -> Unit) : SensorEventListener {
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var prev: FloatArray? = null
    private var last = 0L

    fun start() { sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) } }
    fun stop() { sm.unregisterListener(this); prev = null }

    override fun onSensorChanged(e: SensorEvent) {
        val p = prev
        if (p != null) {
            val dx = e.values[0] - p[0]; val dy = e.values[1] - p[1]; val dz = e.values[2] - p[2]
            val jerk = sqrt(dx * dx + dy * dy + dz * dz)
            val now = System.currentTimeMillis()
            if (jerk > 18f && now - last > 1500) { last = now; onShake() }
        }
        prev = e.values.copyOf()
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}

/* ------------------------------ reminders ------------------------------ */

object Reminders {
    private const val CHANNEL = "reminders"
    private const val PREFS = "reminders"

    fun hasPermission(ctx: Context) =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun channel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Countdown reminders", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Milestones and the morning of your big days" })
        }
    }

    private fun pending(ctx: Context, p: PlannedReminder): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).putExtra("id", p.id).putExtra("title", p.title).putExtra("body", p.body)
        return PendingIntent.getBroadcast(ctx, p.id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Cancel what we scheduled last time, then schedule the current plan (one per day, in the future). */
    fun reschedule(ctx: Context, data: AppData) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet("ids", emptySet())!!.forEach { id ->
            val i = Intent(ctx, ReminderReceiver::class.java)
            PendingIntent.getBroadcast(ctx, id.toInt(), i, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { am.cancel(it); it.cancel() }
        }
        if (!data.settings.notifications) { prefs.edit().putStringSet("ids", emptySet()).apply(); return }
        val plan = planReminders(data.countdowns)
        plan.forEach { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it.at, pending(ctx, it)) }
        prefs.edit().putStringSet("ids", plan.map { it.id.toString() }.toSet()).apply()
        scheduleLive(ctx, data, am, prefs)
    }

    /* ---- the live countdown: in the last 24 hours a ticking, ongoing notification counts down to the moment ---- */

    private fun liveId(c: Countdown) = 1_000_000_000 + Math.abs(c.id.hashCode()) % 900_000_000

    private fun scheduleLive(ctx: Context, data: AppData, am: AlarmManager, prefs: android.content.SharedPreferences) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        prefs.getStringSet("live", emptySet())!!.forEach { id ->
            nm.cancel(id.toInt())
            val i = Intent(ctx, ReminderReceiver::class.java)
            PendingIntent.getBroadcast(ctx, id.toInt(), i, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { am.cancel(it); it.cancel() }
        }
        val now = System.currentTimeMillis(); val ids = HashSet<String>()
        for (c in data.countdowns.filter { !it.archived }) {
            val k = compute(c, now)
            if (k.phase != Phase.UPCOMING || k.remaining > 7 * DAY) continue
            ids += liveId(c).toString()
            if (k.remaining <= DAY) showLive(ctx, c, k.target, c.dog.name)
            else {
                val i = Intent(ctx, ReminderReceiver::class.java).putExtra("live", true).putExtra("id", liveId(c)).putExtra("title", "${c.dog.name} · ${c.title}")
                    .putExtra("body", "Zoomies activated! ${plainTitle(c.title)} is almost here.").putExtra("target", k.target)
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, k.target - DAY, PendingIntent.getBroadcast(ctx, liveId(c), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
        }
        prefs.edit().putStringSet("live", ids).apply()
    }

    fun showLive(ctx: Context, c: Countdown, target: Long, name: String, id: Int = liveId(c)) = showLiveRaw(ctx, id, "$name · ${c.title}", "Zoomies activated! ${plainTitle(c.title)} is almost here.", target)

    fun showLiveRaw(ctx: Context, id: Int, title: String, body: String, target: Long) {
        if (!hasPermission(ctx)) return
        channel(ctx)
        val open = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_paw).setColor(0xFFE8A15C.toInt())
            .setContentTitle(title).setContentText(body).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setUsesChronometer(true).setChronometerCountDown(true).setWhen(target).setShowWhen(true)
            .setTimeoutAfter(maxOf(60_000L, target - System.currentTimeMillis() + 60_000L)).setContentIntent(open).build()
        ctx.getSystemService(NotificationManager::class.java).notify(id, n)
    }

    fun show(ctx: Context, id: Int, title: String, body: String) {
        if (!hasPermission(ctx)) return
        channel(ctx)
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_paw).setColor(0xFFE8A15C.toInt())
            .setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body)).setAutoCancel(true).setContentIntent(open).build()
        ctx.getSystemService(NotificationManager::class.java).notify(id, n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val id = i.getIntExtra("id", 1); val title = i.getStringExtra("title") ?: "Pawcount"; val body = i.getStringExtra("body") ?: ""
        if (i.getBooleanExtra("live", false)) Reminders.showLiveRaw(ctx, id, title, body, i.getLongExtra("target", System.currentTimeMillis()))
        else Reminders.show(ctx, id, title, body)
    }
}

/** Alarms are cleared when the phone restarts, so put them back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED) Reminders.reschedule(ctx, Repo(ctx).data.value)
    }
}

/* ------------------------------ alternate app icons ------------------------------ */

/**
 * The launcher icon is chosen by which <activity-alias> in the manifest is enabled. Exactly one is on at a time;
 * flipping them swaps the icon on the home screen without restarting the app.
 */
object IconSwitcher {
    val breeds = listOf("scott", "golden", "corgi", "shiba", "dachshund", "husky", "mutt")
    private fun alias(ctx: Context, breed: String) = android.content.ComponentName(ctx, "app.pawcount.Icon${breed.replaceFirstChar { it.uppercase() }}")

    fun current(ctx: Context): String {
        val pm = ctx.packageManager
        return breeds.firstOrNull { b ->
            val st = pm.getComponentEnabledSetting(alias(ctx, b))
            st == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (b == "scott" && st == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        } ?: "scott"
    }

    fun set(ctx: Context, breed: String) {
        val pm = ctx.packageManager
        // enable the new one first so there is never a moment with no launcher icon
        pm.setComponentEnabledSetting(alias(ctx, breed), android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP)
        breeds.filter { it != breed }.forEach {
            pm.setComponentEnabledSetting(alias(ctx, it), android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP)
        }
    }
}
