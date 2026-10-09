package app.pawcount

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import kotlin.math.max
import kotlin.math.min

/*
 * Widget.kt - the home-screen widget. It shows the soonest countdown: the animated-scene artwork (as a still),
 * the dog in its current mood, the title and the time left.
 *
 * Widgets can't run our animation engine, so the whole card is drawn into one Bitmap with the same renderer the app
 * uses and shown in an ImageView. It refreshes whenever the app saves something, and about every 30 minutes
 * (the minimum Android allows) so the number and the dog's mood stay current. Tapping opens that countdown.
 */

class PawWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) { ids.forEach { update(ctx, mgr, it) } }
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, o: Bundle) { update(ctx, mgr, id) }

    companion object {
        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            mgr.getAppWidgetIds(ComponentName(ctx, PawWidget::class.java)).forEach { update(ctx, mgr, it) }
        }

        fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val opt = mgr.getAppWidgetOptions(id)
            val dp = ctx.resources.displayMetrics.density
            // the launcher reports two sizes per axis (portrait / landscape); pick the pair that matches how the phone is held
            val portrait = ctx.resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val wDp = max(opt.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 160), 110)
            val hDp = max(opt.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 160), 110)
            // keep the bitmap small: widgets pass it through a size-limited IPC call
            val scale = min(dp, 560f / wDp)
            val w = (wDp * scale).toInt(); val h = (hDp * scale).toInt()

            val data = Repo(ctx).data.value
            val art = Art(ctx); val fonts = PawFonts(ctx)
            val now = System.currentTimeMillis()
            val pick = data.countdowns.filter { !it.archived }.map { it to compute(it, now) }.filter { it.second.phase != Phase.PAST }
                .minWithOrNull(compareBy({ if (it.second.phase == Phase.TODAY) 0 else 1 }, { it.second.target }))

            val bmp = draw(ctx, art, fonts, pick?.first, pick?.second, w, h, scale)
            val views = RemoteViews(ctx.packageName, R.layout.widget_pawcount)
            views.setImageViewBitmap(R.id.img, bmp)
            val open = Intent(Intent.ACTION_VIEW, Uri.parse(if (pick != null) "pawcount://detail/${pick.first.id}" else "pawcount://home"), ctx, MainActivity::class.java)
            views.setOnClickPendingIntent(R.id.img, PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            views.setContentDescription(R.id.img, if (pick != null) "${pick.first.title}, ${spoken(pick.second)} left" else "Pawcount: no countdowns yet")
            mgr.updateAppWidget(id, views)
        }

        /** the whole card as an image */
        fun draw(ctx: Context, art: Art, fonts: PawFonts, c: Countdown?, k: Computed?, w: Int, h: Int, s: Float): Bitmap {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            fredoka = fonts.fredokaTypeface
            val demo = c ?: Countdown("w", "Add a countdown", "custom", "2030-01-01T00:00", "UTC", false, false, System.currentTimeMillis(), Dog("scott", "Scott"), "#E8A15C", DisplayMode.FULL)
            val kk = k ?: compute(demo.copy(createdAt = System.currentTimeMillis() - DAY))
            val stage = if (c == null) Stage.NAP else kk.stage
            val accent = parseHex(demo.accent)
            val palette = dogPalette(art.meta, demo.dog, accent)
            val radius = 26f * s
            cv.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, Path.Direction.CW) })
            cv.drawColor(0xFFFFF8EE.toInt())

            val wide = w.toFloat() / h > 1.6f
            val sceneW = if (wide) w * 0.46f else w.toFloat()
            val sceneH = if (wide) h.toFloat() else h * 0.62f
            cv.save(); cv.clipRect(0f, 0f, sceneW, sceneH)
            val scene = art.scene(demo.type, stage == Stage.WAITING || stage == Stage.PACKING)
            scene?.let { drawScene(cv, it, sceneW, sceneH, DrawState(palette, 1.2f, stage, art.anim.sceneAll, reduceMotion = true)) }
            val dogSize = sceneH * 0.80f
            art.dog(demo.dog.variant, stage, demo.type)?.let { drawDog(cv, it, (sceneW - dogSize) / 2f, sceneH - sceneH * 0.04f - dogSize, dogSize, DrawState(palette, 1.2f, stage, dogAnims(art.anim, stage), reduceMotion = true)) }
            cv.restore()

            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.typeface = fonts.fredokaTypeface; p.color = 0xFF3D2616.toInt()
            val big = if (c == null) "Tap to start" else when {
                kk.phase == Phase.TODAY -> "Today! 🎉"
                demo.displayMode == DisplayMode.SLEEPS -> plural(kk.sleeps, "sleep")
                demo.displayMode == DisplayMode.WEEKS && kk.weeks > 0 -> "${kk.weeks}w ${kk.weekDays}d"
                kk.parts.d == 0L && kk.phase == Phase.UPCOMING -> "${kk.parts.h}h ${kk.parts.m}m"
                else -> plural(kk.daysCeil, "day")
            }
            val tx = if (wide) sceneW + 16f * s else 14f * s
            val maxW = (if (wide) w - tx - 12f * s else w - 28f * s)
            val ty0 = if (wide) h * 0.30f else sceneH + 28f * s
            p.textSize = 16f * s; p.textAlign = Paint.Align.LEFT
            while (p.measureText(demo.title) > maxW && p.textSize > 9f * s) p.textSize -= 1f
            cv.drawText(demo.title, tx, ty0, p)
            p.textSize = (if (wide) 40f else 30f) * s; p.color = if (androidx.core.graphics.ColorUtils.calculateLuminance(accent) > 0.6) 0xFF3D2616.toInt() else accent
            while (p.measureText(big) > maxW && p.textSize > 12f * s) p.textSize -= 1f
            cv.drawText(big, tx, ty0 + p.textSize + 6f * s, p)
            if (wide && c != null) {
                p.typeface = android.graphics.Typeface.createFromAsset(ctx.assets, "fonts/Nunito-ExtraBold.ttf"); p.color = 0xFF765039.toInt(); p.textSize = 12f * s
                cv.drawText(stageLabel(demo, kk), tx, h * 0.88f, p)
            }
            return bmp
        }
    }
}
