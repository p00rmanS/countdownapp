package app.pawcount

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.aspectRatio
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.produceState
import androidx.compose.runtime.State
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import java.util.Random

/*
 * SceneView.kt - one animated scene: backdrop + atmosphere (stars, sun rays, confetti...) + the dog + hearts/ball,
 * and the speech bubble. The same composable is used big (detail screen), medium (home hero) and small (cards).
 *
 * All drawing happens in one Canvas that redraws every frame from a clock, so animation is smooth and
 * stops automatically when the scene leaves the screen.
 */

enum class SceneSize(val dogHeight: Float, val dogBottom: Float, val ratio: Float?) {
    HERO(0.74f, 0.03f, 4f / 3.15f), CARD(0.70f, 0.05f, 1f / 0.98f), DETAIL(0.60f, 0.23f, null), PREVIEW(0.74f, 0.03f, 16f / 11f)
}

class Heart(val x: Float, val y: Float, val born: Float, val dx: Float, val size: Float, val glyph: String)

/** State of one interactive scene: reactions in progress, floating hearts, the speech bubble. */
@Stable
class SceneController {
    val reactions = Reactions()
    val hearts = mutableStateListOf<Heart>()
    var bubble by mutableStateOf<String?>(null)
    var shakenAt by mutableStateOf<Float?>(null)
    var fetchAt by mutableStateOf<Float?>(null)
    var hint by mutableStateOf(true)
    var clock = 0f
    /** a sleeping dog opens his eyes for a few seconds when you play with him */
    var awake by mutableStateOf(false)
    private var wakeJob: kotlinx.coroutines.Job? = null
    fun wake(scope: kotlinx.coroutines.CoroutineScope) {
        awake = true; wakeJob?.cancel()
        wakeJob = scope.launch { delay(6000); awake = false }
    }
}

@Composable
fun rememberClock(): State<Float> = produceState(0f) { while (true) withFrameNanos { value = it / 1_000_000_000f } }

private val confettiColors = intArrayOf(0xFFFF7DAA.toInt(), 0xFFFFD35C.toInt(), 0xFF5DADE8.toInt(), 0xFFD4E157.toInt(), 0xFFFFFFFF.toInt(), 0xFFE5574F.toInt())

@Composable
fun SceneView(
    c: Countdown, k: Computed, art: Art, fonts: PawFonts, size: SceneSize, modifier: Modifier = Modifier,
    stage: Stage = k.stage, controller: SceneController? = null, reduceMotion: Boolean = false, bell: Boolean = false,
    onBark: () -> Unit = {}, onPetStart: () -> Unit = {}, onPetEnd: () -> Unit = {}, interactive: Boolean = false,
    dogOverride: Dog? = null, typeOverride: String? = null, accentOverride: String? = null,
) {
    val dog = dogOverride ?: c.dog
    val type = typeOverride ?: c.type
    val accent = accentColor(accentOverride ?: c.accent)
    val palette = remember(dog, accent) { dogPalette(art.meta, dog, accent.toArgb()) }
    val showDoor = stage == Stage.WAITING || stage == Stage.PACKING
    val sceneNode = remember(type, showDoor) { art.scene(type, showDoor) }
    val dogStage = if (controller?.awake == true && stage == Stage.NAP) Stage.CURIOUS else stage
    val dogNode = remember(dog.variant, dogStage, type, bell) { art.dog(dog.variant, dogStage, type, bell) }
    val clock by rememberClock()
    val anims = remember(dogStage) { dogAnims(art.anim, dogStage) }
    val seed = remember(c.id) { c.id.hashCode() }
    val ctrl = controller
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    fredoka = fonts.fredokaTypeface
    val ratioMod = size.ratio?.let { Modifier.aspectRatio(it) } ?: Modifier.fillMaxSize()

    BoxWithConstraints(modifier.then(ratioMod).clipToBounds()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width; val h = this.size.height
            val t = clock
            ctrl?.clock = t
            drawIntoCanvas { cv ->
                val nc = cv.nativeCanvas
                val bg = DrawState(palette, t, stage, art.anim.sceneAll, reduceMotion = reduceMotion,
                    shakenSince = ctrl?.shakenAt?.let { t - it }?.takeIf { it < 0.8f }, shakenDefs = art.anim.sceneShaken)
                sceneNode?.let { drawScene(nc, it, w, h, bg) }
                drawAtmosphere(nc, stage, w, h, t, seed, size == SceneSize.CARD, reduceMotion)
                val dogSize = h * size.dogHeight
                val st = DrawState(palette, t, dogStage, anims, ctrl?.reactions, art.anim.state, reduceMotion = reduceMotion)
                val fetching = ctrl?.fetchAt?.let { t - it }?.takeIf { it < 2f }
                val stDog = if (fetching != null) DrawState(palette, t, dogStage, anims, ctrl.reactions, art.anim.state + ("fetching" to art.anim.state.getValue("fetching")), reduceMotion = reduceMotion) else st
                if (fetching != null && ctrl.reactions.since("fetching", t) == null) ctrl.reactions.start("fetching", ctrl.fetchAt!!)
                dogNode?.let { drawDog(nc, it, (w - dogSize) / 2f, h - h * size.dogBottom - dogSize, dogSize, stDog) }
                // floating hearts (pet) and the fetch ball
                if (ctrl != null) {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG)
                    ctrl.hearts.removeAll { t - it.born > 1.5f }
                    for (hr in ctrl.hearts) {
                        val u = (t - hr.born) / 1.4f
                        p.textSize = hr.size * density * (0.5f + 0.7f * u.coerceAtMost(1f))
                        p.color = android.graphics.Color.argb(((if (u < 0.15f) u / 0.15f else 1f - ((u - 0.15f) / 0.85f)).coerceIn(0f, 1f) * 255).toInt(), 229, 87, 79)
                        nc.drawText(hr.glyph, hr.x + hr.dx * u * density, hr.y - 110f * u * density, p)
                    }
                    if (fetching != null) {
                        val u = (fetching / 1.2f).coerceIn(0f, 1f)
                        p.color = 0xFFD4E157.toInt(); p.alpha = ((1f - u * u) * 255).toInt()
                        nc.drawCircle(w * 0.88f - u * w * 0.45f, h * -0.04f + u * h * 0.62f + sin(u * PI.toFloat()) * -h * 0.1f, 11f * density, p)
                    }
                }
            }
        }
        if (interactive && ctrl != null) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = maxHeight * size.dogBottom).fillMaxSize(size.dogHeight)
                .semantics { contentDescription = "${dog.name}. Tap to bark, press and hold to pet." }
                .pointerInput(Unit) {
                    detectTapGestures(onPress = { pos ->
                        var petting = false
                        val job = scope.launch {
                            delay(380); petting = true
                            ctrl.hint = false; ctrl.reactions.start("petting", ctrl.clock); onPetStart()
                            var n = 0
                            while (true) {
                                ctrl.hearts += Heart(pos.x + (this@pointerInput.size.width * 0f), pos.y, ctrl.clock, (Random().nextFloat() - 0.5f) * 70f, 16f + Random().nextFloat() * 14f, if (Random().nextFloat() < 0.2f) "🐾" else "♥")
                                if (n++ % 3 == 0) Haptics.play(ctx, true, "purr")
                                delay(280)
                            }
                        }
                        val released = tryAwaitRelease()
                        job.cancel()
                        if (petting) { ctrl.reactions.stop("petting"); onPetEnd() }
                        else if (released) { ctrl.hint = false; ctrl.reactions.start("bark", ctrl.clock); onBark() }
                    })
                })
        }
        // speech bubble
        val show = ctrl?.bubble != null
        val a by animateFloatAsState(if (show) 1f else 0f, label = "bubble")
        if (ctrl != null && a > 0.01f) {
            Text(ctrl.bubble ?: "", Modifier.align(Alignment.TopCenter).padding(top = if (size == SceneSize.DETAIL) maxHeight * 0.2f + 62.dp else maxHeight * 0.18f).widthIn(max = maxWidth * 0.78f)
                .graphicsLayerCompat(a).shadow(8.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(horizontal = 16.dp, vertical = 9.dp),
                style = T.display(17).copy(color = Color(0xFF3D2616)), textAlign = TextAlign.Center)
        }
    }
}

private fun Modifier.graphicsLayerCompat(a: Float) = this.then(Modifier.graphicsLayer { alpha = a; scaleX = 0.7f + 0.3f * a; scaleY = 0.7f + 0.3f * a; translationY = (1 - a) * 12f })

/** stars + moon (nap), sun rays + confetti (today), speed lines (zoomies), "?" (curious), warm glow (memory) */
private fun drawAtmosphere(c: android.graphics.Canvas, stage: Stage, w: Float, h: Float, t: Float, seed: Int, mini: Boolean, still: Boolean) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val rnd = Random(seed.toLong())
    when (stage) {
        Stage.NAP -> {
            // dim the room and put a moon and stars in the sky
            p.shader = android.graphics.LinearGradient(0f, 0f, 0f, h, intArrayOf(0xFF10163F.toInt(), 0xFF272C66.toInt(), 0xFF3A3570.toInt()), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
            p.alpha = (0.62f * 255).toInt(); c.drawRect(0f, 0f, w, h, p); p.shader = null; p.alpha = 255
            val mr = minOf(w, h) * 0.055f; val mx = w * 0.83f; val my = h * 0.15f
            // crescent: a full disc with a shifted disc cut out of it
            val moon = Path().apply { addCircle(mx, my, mr, Path.Direction.CW) }
            moon.op(Path().apply { addCircle(mx + mr * 0.55f, my - mr * 0.2f, mr * 0.9f, Path.Direction.CW) }, Path.Op.DIFFERENCE)
            p.color = 0xFFFFF4CC.toInt(); c.drawPath(moon, p)
            repeat(if (mini) 4 else 8) {
                val sx = w * (0.06f + rnd.nextFloat() * 0.88f); val sy = h * (0.06f + rnd.nextFloat() * 0.40f)
                val tw = if (still) 0.8f else 0.25f + 0.75f * (0.5f + 0.5f * sin(t * 2.1f + it * 1.7f))
                p.color = android.graphics.Color.argb((tw * 255).toInt(), 255, 255, 255); c.drawCircle(sx, sy, 1.6f + tw * 1.4f, p)
            }
        }
        Stage.TODAY -> {
            // slowly turning sun rays, then confetti falling
            val cx = w / 2f; val cy = h * 0.52f; val rad = w * 0.95f
            p.shader = RadialGradient(cx, cy, w * 0.62f, intArrayOf(0x57FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            val path = Path(); val rot = if (still) 0f else t / 36f * 360f
            for (i in 0 until 15) {
                val a0 = Math.toRadians((rot + i * 24.0)); val a1 = Math.toRadians((rot + i * 24.0 + 8))
                path.reset(); path.moveTo(cx, cy)
                path.lineTo(cx + rad * cos(a0).toFloat(), cy + rad * sin(a0).toFloat()); path.lineTo(cx + rad * cos(a1).toFloat(), cy + rad * sin(a1).toFloat()); path.close()
                c.drawPath(path, p)
            }
            p.shader = null
            if (!still) repeat(if (mini) 9 else 18) {
                val x = w * (0.02f + rnd.nextFloat() * 0.96f); val dur = 3f + rnd.nextFloat() * 2.5f; val off = rnd.nextFloat() * 3f
                val u = ((t + off) / dur) % 1f
                p.color = confettiColors[it % confettiColors.size]; p.alpha = (u.coerceAtMost(0.1f) / 0.1f * 230).toInt()
                c.save(); c.translate(x, -14f + u * (h + 40f)); c.rotate(u * 560f); c.drawRoundRect(-3.5f, -6.5f, 3.5f, 6.5f, 2f, 2f, p); c.restore()
            }
        }
        Stage.ZOOMIES -> if (!still) for ((i, line) in listOf(Triple(0.06f, 0.50f, 0.14f), Triple(0.66f, 0.62f, 0.20f), Triple(0.30f, 0.72f, 0.10f)).withIndex()) {
            val u = ((t + i * 0.25f) / 0.7f) % 1f
            p.color = android.graphics.Color.WHITE; p.alpha = ((if (u < 0.3f) u / 0.3f else 1f - (u - 0.3f) / 0.7f) * 180).toInt().coerceIn(0, 180)
            val x = w * line.first + (40f - 100f * u); c.drawRoundRect(x, h * line.second, x + w * line.third, h * line.second + 3.5f, 2f, 2f, p)
        }
        Stage.CURIOUS -> {
            val bob = if (still) 0f else sin(t * 2.6f) * 8f
            p.typeface = fredoka; p.textSize = (if (mini) 26f else 38f) * (w / 360f).coerceIn(0.8f, 1.6f); p.color = android.graphics.Color.WHITE
            p.setShadowLayer(0f, 0f, 3f, 0x2E000000)
            c.save(); c.rotate(if (still) -8f else sin(t * 2.6f) * 8f, w * 0.76f, h * 0.2f); c.drawText("?", w * 0.74f, h * 0.2f + bob, p); c.restore()
        }
        Stage.MEMORY -> {
            p.shader = RadialGradient(w / 2f, h * 0.7f, w * 0.6f, intArrayOf(0x73FFD68C, 0x00FFD68C), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p); p.shader = null
            p.color = 0x2E6E4614; c.drawRect(0f, 0f, w, h, p)   // sepia wash
        }
        else -> {}
    }
}
