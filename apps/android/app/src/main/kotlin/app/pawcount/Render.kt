package app.pawcount

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * Render.kt - draws a Node tree on an android.graphics.Canvas and animates it.
 *
 * Colours: a Palette resolves the symbolic colours ($fur, $accent, mix(...)).
 * Animation: AnimTable is the port of the CSS keyframes (native/assets/anim.json). Each frame, for every node
 * we look up which animation definitions match its class names and turn the current time into a small
 * transform (translate / scale / rotate about an origin) before drawing it.
 */

/* ---------------- colours ---------------- */

private var paletteCounter = 0

class Palette(val fur: Int, val dark: Int, val light: Int, val paw: Int, val accent: Int) {
    val id = ++paletteCounter
}

fun parseHex(s: String): Int {
    var h = s.removePrefix("#")
    if (h.length == 3) h = h.map { "$it$it" }.joinToString("")
    return (0xFF000000L or h.toLong(16)).toInt()
}

fun mixColors(a: Int, b: Int, t: Float): Int {
    fun ch(x: Int, y: Int) = (x + (y - x) * t).toInt().coerceIn(0, 255)
    return Color.rgb(ch(Color.red(a), Color.red(b)), ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b)))
}

fun dogPalette(meta: Meta, dog: Dog, accent: Int): Palette {
    val b = meta.breeds[dog.breed] ?: meta.breeds.getValue("mutt")
    if (dog.breed != "mutt") return Palette(parseHex(b.fur), parseHex(b.dark), parseHex(b.light), parseHex(b.paw), accent)
    val fur = parseHex(dog.furColor ?: b.fur)   // a mutt derives its other shades from the coat colour the user picked
    return Palette(fur, mixColors(fur, Color.BLACK, 0.2f), mixColors(fur, Color.WHITE, 0.62f), mixColors(fur, Color.WHITE, 0.55f), accent)
}

fun resolveColor(token: String, p: Palette): Int {
    if (token.startsWith("#")) return parseHex(token)
    if (token.startsWith("$")) return when (token) {
        "\$fur" -> p.fur; "\$dark" -> p.dark; "\$light" -> p.light; "\$paw" -> p.paw; "\$accent" -> p.accent; else -> Color.MAGENTA
    }
    if (token.startsWith("mix(")) {
        // mix(a,b,t) where a / b may themselves be tokens; split on the top-level commas
        val inner = token.substring(4, token.length - 1)
        val parts = ArrayList<String>(); var depth = 0; var last = 0
        for (i in inner.indices) when (inner[i]) { '(' -> depth++; ')' -> depth--; ',' -> if (depth == 0) { parts += inner.substring(last, i); last = i + 1 } }
        parts += inner.substring(last)
        return mixColors(resolveColor(parts[0], p), resolveColor(parts[1], p), parts[2].toFloat())
    }
    return Color.MAGENTA
}

/* ---------------- animation ---------------- */

data class Props(var tx: Float = 0f, var ty: Float = 0f, var r: Float = 0f, var sx: Float = 1f, var sy: Float = 1f, var op: Float = 1f)

class Keyframe(val t: Float, val v: Props, val has: Set<String>)

class AnimDef(j: JSONObject) {
    val sel: List<String> = j.getString("sel").split(' ')
    val origin: FloatArray? = j.optJSONArray("o")?.let { floatArrayOf(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) }
    val box: FloatArray? = j.optJSONArray("b")?.let { floatArrayOf(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) }
    val d = j.getDouble("d").toFloat()
    val delay = j.optDouble("delay", 0.0).toFloat()
    val seq = j.optDouble("seq", 0.0).toFloat()
    val alt = j.optBoolean("alt")
    val once = j.optBoolean("once")
    val n = j.optInt("n", 0)
    val linear = j.optString("ease") == "l"
    val ramp = j.optDouble("ramp", 0.0).toFloat()
    val kf: List<Keyframe> = j.getJSONArray("kf").let { a ->
        (0 until a.length()).map { i ->
            val e = a.getJSONArray(i); val o = e.getJSONObject(1); val p = Props()
            val has = HashSet<String>()
            o.keys().forEach { k -> has += k; val x = o.getDouble(k).toFloat()
                when (k) { "tx" -> p.tx = x; "ty" -> p.ty = x; "r" -> p.r = x; "sx" -> p.sx = x; "sy" -> p.sy = x; "op" -> p.op = x } }
            Keyframe(e.getDouble(0).toFloat(), p, has)
        }
    }
    fun matches(n: Node) = sel.all { n.has(it) }

    /** value at [elapsed] seconds since this animation began (null = not started / finished) */
    fun valueAt(elapsedIn: Float, index: Int): Props? {
        val e = elapsedIn - delay - seq * index
        val loops = e / d
        if (loops < 0f) return interpolate(0f)              // still waiting for the delay
        if (once && loops >= 1f) return null
        if (n > 0 && loops >= n) return null
        var frac = loops - floor(loops)
        if (alt && floor(loops).toInt() % 2 == 1) frac = 1f - frac
        return interpolate(frac)
    }

    private fun interpolate(f: Float): Props {
        if (f <= kf.first().t) return kf.first().v.copy()
        for (i in 1 until kf.size) {
            val a = kf[i - 1]; val b = kf[i]
            if (f <= b.t) {
                var u = if (b.t == a.t) 1f else (f - a.t) / (b.t - a.t)
                if (!linear) u = (1f - cos(PI.toFloat() * u)) / 2f
                fun l(x: Float, y: Float) = x + (y - x) * u
                return Props(l(a.v.tx, b.v.tx), l(a.v.ty, b.v.ty), l(a.v.r, b.v.r), l(a.v.sx, b.v.sx), l(a.v.sy, b.v.sy), l(a.v.op, b.v.op))
            }
        }
        return kf.last().v.copy()
    }
}

class AnimTable(j: JSONObject) {
    private fun list(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).map { AnimDef(a.getJSONObject(it)) }
    private val dog = j.getJSONObject("dog")
    private val scene = j.getJSONObject("scene")
    val sit = list(dog.optJSONArray("sit")); val lie = list(dog.optJSONArray("lie"))
    val stage: Map<String, List<AnimDef>> = listOf("nap", "memory", "curious", "waiting", "packing", "zoomies", "today").associateWith { list(dog.optJSONArray(it)) }
    val state: Map<String, List<AnimDef>> = listOf("bark", "sneeze", "petting", "fetching").associateWith { list(dog.optJSONArray(it)) }
    val sceneAll = list(scene.optJSONArray("all"))
    val sceneShaken = list(scene.optJSONArray("shaken"))
}

/** transient reactions of the dog (tap / pet / shake / fetch): name -> time (seconds) it started */
class Reactions {
    private val started = HashMap<String, Float>()
    fun start(name: String, t: Float) { started[name] = t }
    fun stop(name: String) { started.remove(name) }
    fun since(name: String, now: Float): Float? = started[name]?.let { now - it }
    fun active(name: String, now: Float, length: Float): Boolean = since(name, now)?.let { it in 0f..length } ?: false
}

/** Everything the renderer needs to draw one frame of a dog or a scene. */
class DrawState(
    val palette: Palette,
    val time: Float,                       // seconds, continuous clock
    val stage: Stage,
    val anims: List<AnimDef>,              // stage/base animations
    val reactions: Reactions? = null,
    val stateDefs: Map<String, List<AnimDef>> = emptyMap(),
    val shakenSince: Float? = null,        // scenes: seconds since shake began
    val shakenDefs: List<AnimDef> = emptyList(),
    val reduceMotion: Boolean = false,
) {
    // flags that used to be CSS classes on the <svg>
    val bark = reactions?.active("bark", time, 0.5f) == true
    val sneeze = reactions?.active("sneeze", time, 1f) == true
    val petting = reactions?.since("petting", time) != null
    val mouthOpen = stage == Stage.PACKING || stage == Stage.ZOOMIES || stage == Stage.TODAY || bark || sneeze
    val eyesShut = stage == Stage.TODAY || petting || sneeze
}

/* ---------------- drawing ---------------- */

private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
private val shapePath = Path()
var fredoka: Typeface = Typeface.DEFAULT_BOLD

private fun withAlpha(c: Int, a: Float): Int = Color.argb((Color.alpha(c) * a.coerceIn(0f, 1f)).toInt(), Color.red(c), Color.green(c), Color.blue(c))

private fun visible(n: Node, s: DrawState): Boolean = when {
    n.has("mouth-open") -> s.mouthOpen
    n.has("mouth-closed") -> !s.mouthOpen
    n.has("eye") -> !s.eyesShut
    n.has("eye-shut") -> s.eyesShut
    else -> !n.hidden
}

private fun colorOf(n: Node, which: Int, spec: PaintSpec?, p: Palette): Int {
    if (spec !is Solid) return 0
    if (n.cachedPalette != p.id) {
        n.cachedPalette = p.id
        n.cachedFill = (n.fill as? Solid)?.let { resolveColor(it.token, p) } ?: 0
        n.cachedStroke = (n.stroke as? Solid)?.let { resolveColor(it.token, p) } ?: 0
    }
    return if (which == 0) n.cachedFill else n.cachedStroke
}

private fun shapeBounds(n: Node) = n.bounds

private fun buildShape(n: Node): Path? {
    shapePath.reset()
    when (n.type) {
        "ellipse" -> shapePath.addOval(RectF(n.cx - n.rx, n.cy - n.ry, n.cx + n.rx, n.cy + n.ry), Path.Direction.CW)
        "circle" -> shapePath.addCircle(n.cx, n.cy, n.r, Path.Direction.CW)
        "rect" -> if (n.rx > 0f) shapePath.addRoundRect(RectF(n.x, n.y, n.x + n.w, n.y + n.h), n.rx, n.rx, Path.Direction.CW)
                  else shapePath.addRect(n.x, n.y, n.x + n.w, n.y + n.h, Path.Direction.CW)
        "path" -> { val p = n.path ?: return null; shapePath.set(p) }
        else -> return null
    }
    return shapePath
}

private fun applyTransform(c: Canvas, tf: List<FloatArray>) {
    for (t in tf) when (t[0].toInt().toChar()) {
        't' -> c.translate(t[1], t[2])
        's' -> c.scale(t[1], t[2])
        'r' -> c.rotate(t[1], t[2], t[3])
    }
}

private fun clipTo(c: Canvas, clip: Node) {
    val p = Path()
    when (clip.type) {
        "ellipse" -> p.addOval(RectF(clip.cx - clip.rx, clip.cy - clip.ry, clip.cx + clip.rx, clip.cy + clip.ry), Path.Direction.CW)
        "rect" -> if (clip.rx > 0f) p.addRoundRect(RectF(clip.x, clip.y, clip.x + clip.w, clip.y + clip.h), clip.rx, clip.rx, Path.Direction.CW)
                  else p.addRect(clip.x, clip.y, clip.x + clip.w, clip.y + clip.h, Path.Direction.CW)
        "circle" -> p.addCircle(clip.cx, clip.cy, clip.r, Path.Direction.CW)
        "path" -> clip.path?.let { p.set(it) }
    }
    c.clipPath(p)
}

/** Works out the animated transform for [n] and applies it to the canvas. Returns the opacity multiplier. */
private fun applyAnimation(c: Canvas, n: Node, s: DrawState, seqIndex: IntArray): Float {
    if (n.classes.isEmpty() || s.reduceMotion) return 1f
    var base: AnimDef? = null
    for (a in s.anims) if (a.matches(n)) base = a
    var state: AnimDef? = null; var stateStart = 0f
    s.reactions?.let { rx ->
        for ((name, defs) in s.stateDefs) {
            val since = rx.since(name, s.time) ?: continue
            for (a in defs) if (a.matches(n)) { state = a; stateStart = since }
        }
    }
    var shaken: AnimDef? = null
    if (s.shakenSince != null) for (a in s.shakenDefs) if (a.matches(n)) shaken = a
    if (base == null && state == null && shaken == null) return 1f

    var index = 0
    if (base != null && base.seq != 0f) { index = seqIndex[s.anims.indexOf(base)]++ }
    var pr: Props? = base?.valueAt(s.time, index)
    var def = base
    if (state != null) {
        val sp = state.valueAt(stateStart, 0)
        if (sp != null) {
            val k = if (state.ramp > 0f) (stateStart / state.ramp).coerceIn(0f, 1f) else 1f
            pr = if (pr == null || k >= 1f) sp else Props(
                pr.tx + (sp.tx - pr.tx) * k, pr.ty + (sp.ty - pr.ty) * k, pr.r + (sp.r - pr.r) * k,
                pr.sx + (sp.sx - pr.sx) * k, pr.sy + (sp.sy - pr.sy) * k, pr.op + (sp.op - pr.op) * k)
            def = state
        }
    }
    if (shaken != null) {
        val sp = shaken.valueAt(s.shakenSince!!, 0)
        if (sp != null) { pr = sp; def = shaken }
    }
    if (pr == null || def == null) return 1f
    val ox: Float; val oy: Float
    val o = def.origin; val b = def.box
    if (o != null) { ox = o[0]; oy = o[1] }
    else if (b != null) { val bb = n.bounds; ox = bb.left + b[0] * bb.width(); oy = bb.top + b[1] * bb.height() }
    else { ox = 0f; oy = 0f }
    c.translate(ox + pr.tx, oy + pr.ty)      // order matches the CSS: translate, then scale, then rotate
    c.scale(pr.sx, pr.sy)
    c.rotate(pr.r)
    c.translate(-ox, -oy)
    return pr.op
}

fun drawNode(c: Canvas, n: Node, s: DrawState, alpha: Float = 1f, seqIndex: IntArray = IntArray(64)) {
    if (!visible(n, s)) return
    val save = c.save()
    applyTransform(c, n.transform)
    n.clip?.let { clipTo(c, it) }
    val a = alpha * n.opacity * applyAnimation(c, n, s, seqIndex)
    if (n.type == "g") {
        for (ch in n.children) drawNode(c, ch, s, a, seqIndex)
        c.restoreToCount(save); return
    }
    if (n.type == "text") {
        val fill = n.fill as? Solid
        if (fill != null) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.typeface = fredoka; p.textSize = n.fontSize; p.color = withAlpha(resolveColor(fill.token, s.palette), a)
            p.textAlign = if (n.anchor == "middle") Paint.Align.CENTER else Paint.Align.LEFT
            c.drawText(n.text, n.x, n.y, p)
        }
        c.restoreToCount(save); return
    }
    val shape = buildShape(n)
    if (shape != null) {
        n.fill?.let { spec ->
            fillPaint.shader = null
            when (spec) {
                is Solid -> fillPaint.color = withAlpha(colorOf(n, 0, spec, s.palette), a * n.fillOpacity)
                is Gradient -> {
                    val bb = n.bounds
                    fillPaint.color = Color.WHITE
                    fillPaint.alpha = (255 * (a * n.fillOpacity)).toInt().coerceIn(0, 255)
                    fillPaint.shader = LinearGradient(bb.left + spec.x1 * bb.width(), bb.top + spec.y1 * bb.height(), bb.left + spec.x2 * bb.width(), bb.top + spec.y2 * bb.height(),
                        IntArray(spec.stops.size) { resolveColor(spec.stops[it].second, s.palette) }, FloatArray(spec.stops.size) { spec.stops[it].first }, Shader.TileMode.CLAMP)
                }
            }
            c.drawPath(shape, fillPaint)
            fillPaint.shader = null
        }
        if (n.stroke is Solid) {
            strokePaint.color = withAlpha(colorOf(n, 1, n.stroke, s.palette), a)
            strokePaint.strokeWidth = n.strokeWidth
            strokePaint.strokeCap = when (n.cap) { "round" -> Paint.Cap.ROUND; "square" -> Paint.Cap.SQUARE; else -> Paint.Cap.BUTT }
            strokePaint.strokeJoin = when (n.join) { "round" -> Paint.Join.ROUND; "bevel" -> Paint.Join.BEVEL; else -> Paint.Join.MITER }
            strokePaint.pathEffect = n.dash?.let { DashPathEffect(it, 0f) }
            c.drawPath(shape, strokePaint)
        }
    }
    c.restoreToCount(save)
}

/** which animation lists apply to a dog in this stage (base sit/lie + the stage's own) */
fun dogAnims(table: AnimTable, stage: Stage): List<AnimDef> =
    (if (stage == Stage.NAP || stage == Stage.MEMORY) table.lie else table.sit) + (table.stage[stage.key] ?: emptyList())

/** Scales the 400x300 scene drawing to fill the canvas, cropping like CSS "slice" anchored bottom-centre. */
fun drawScene(c: Canvas, scene: Node, w: Float, h: Float, s: DrawState) {
    val k = max(w / 400f, h / 300f)
    val save = c.save()
    c.translate((w - 400f * k) / 2f, h - 300f * k)
    c.scale(k, k)
    drawNode(c, scene, s)
    c.restoreToCount(save)
}

/** Draws a 240x240 dog into the square (left, top, size). */
fun drawDog(c: Canvas, dog: Node, left: Float, top: Float, size: Float, s: DrawState) {
    val save = c.save()
    c.translate(left, top)
    c.scale(size / 240f, size / 240f)
    drawNode(c, dog, s)
    c.restoreToCount(save)
}
