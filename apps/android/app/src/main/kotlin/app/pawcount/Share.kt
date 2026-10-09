package app.pawcount

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File

/*
 * Share.kt - "share with your person".
 *
 * Two ways to share a countdown, neither needs an account or a server:
 *   1. A picture card (scene + dog + title + big number) for chats and stories.
 *   2. A link that carries the countdown itself (title, date, time zone, dog...). Opening it in Pawcount - on
 *      Android, iPhone or the web - adds the same countdown to the other person's app.
 * (Live two-way sync needs a server; this is the no-account version and works today.)
 */

object Share {
    const val WEB = "https://p00rmans.github.io/countdownapp/"
    private val dropKeys = listOf("id", "createdAt", "archived", "memoryPhotos", "sample", "notifications")

    /** compact, URL-safe text that contains the countdown */
    fun code(c: Countdown): String {
        val j = c.toJson(); dropKeys.forEach { j.remove(it) }; j.put("v", 1)
        return Base64.encodeToString(j.toString().toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun link(c: Countdown) = "$WEB#/import/${code(c)}"
    fun appLink(c: Countdown) = "pawcount://import?d=${code(c)}"

    /** accepts a full link (web or pawcount://) or the bare code; returns a new countdown for this phone, or null */
    fun parse(text: String): Countdown? = runCatching {
        val raw = text.trim().substringAfterLast("/import/").substringAfter("d=").substringBefore("&").substringBefore("#").trim()
        val json = JSONObject(String(Base64.decode(raw, Base64.URL_SAFE or Base64.NO_PADDING)))
        json.put("id", newId()); json.put("createdAt", java.time.Instant.now().toString())
        val base = countdownFromJson(json)
        base.copy(reminders = defaultReminders(base.type), sample = false, archived = false)
    }.getOrNull()

    /** the picture card as a 1080x1350 image */
    fun card(ctx: Context, art: Art, fonts: PawFonts, c: Countdown, k: Computed): Bitmap {
        val w = 1080; val h = 1350; val sceneH = 820
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val accent = parseHex(c.accent)
        fredoka = fonts.fredokaTypeface
        cv.drawColor(0xFFFFF8EE.toInt())
        // scene + dog, drawn once in a pleasant mid-animation pose
        val palette = dogPalette(art.meta, c.dog, accent)
        cv.save(); cv.clipRect(0, 0, w, sceneH)
        art.scene(c.type, k.stage == Stage.WAITING || k.stage == Stage.PACKING)?.let { drawScene(cv, it, w.toFloat(), sceneH.toFloat(), DrawState(palette, 1.2f, k.stage, art.anim.sceneAll)) }
        art.dog(c.dog.variant, k.stage, c.type)?.let { drawDog(cv, it, (w - 640) / 2f, sceneH - 700f, 640f, DrawState(palette, 1.2f, k.stage, dogAnims(art.anim, k.stage))) }
        cv.restore()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // soft edge between picture and text
        p.shader = LinearGradient(0f, sceneH - 40f, 0f, sceneH.toFloat(), 0x00FFF8EE, 0xFFFFF8EE.toInt(), Shader.TileMode.CLAMP); cv.drawRect(0f, sceneH - 40f, w.toFloat(), sceneH.toFloat(), p); p.shader = null
        p.typeface = fonts.fredokaTypeface; p.textAlign = Paint.Align.CENTER; p.color = 0xFF3D2616.toInt()
        p.textSize = 84f; cv.drawText(c.title.take(26), w / 2f, sceneH + 100f, p)
        p.color = accent; p.textSize = 150f
        val big = when {
            k.phase == Phase.TODAY -> "It's today! 🎉"
            k.phase == Phase.PAST -> plural(k.daysSince, "day") + " ago"
            c.displayMode == DisplayMode.SLEEPS -> plural(k.sleeps, "sleep")
            c.displayMode == DisplayMode.WEEKS && k.weeks > 0 -> "${k.weeks} wk ${k.weekDays} d"
            else -> plural(k.daysCeil, "day")
        }
        if (Color.luminance(accent) > 0.6f) p.color = 0xFF3D2616.toInt()
        cv.drawText(big, w / 2f, sceneH + 270f, p)
        p.typeface = Typeface_nunito(ctx); p.color = 0xFF765039.toInt(); p.textSize = 42f
        cv.drawText(fmtInstant(k.target, k.zone, c.allDay), w / 2f, sceneH + 350f, p)
        p.typeface = fonts.fredokaTypeface; p.textSize = 44f; p.color = 0xFFE8A15C.toInt()
        cv.drawText("🐾 Pawcount", w / 2f, h - 60f, p)
        return bmp
    }

    private fun Typeface_nunito(ctx: Context) = android.graphics.Typeface.createFromAsset(ctx.assets, "fonts/Nunito-ExtraBold.ttf")

    /** opens the system share sheet with the card image and the link */
    fun shareCard(ctx: Context, art: Art, fonts: PawFonts, c: Countdown) {
        val bmp = card(ctx, art, fonts, c, compute(c))
        val f = File(ctx.cacheDir, "pawcount-card.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, "${c.dog.name} is counting down to ${plainTitle(c.title)}! ${link(c)}").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share countdown"))
    }

    fun shareLink(ctx: Context, c: Countdown) {
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "Count down with me! ${c.title}: ${link(c)}"), "Share with your person"))
    }
}
