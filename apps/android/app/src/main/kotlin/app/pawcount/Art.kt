package app.pawcount

import android.content.Context
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.PathParser
import org.json.JSONArray
import org.json.JSONObject

/*
 * Art.kt - loads the dog and scene drawings that tools/export-native.js produced from the web app's SVG
 * (the JSON files in native/assets) and turns them into a tree of Node objects ready to draw.
 *
 * A Node is one SVG element: group / ellipse / circle / rect / path / text. Colours are kept symbolic
 * ($fur, $dark, $light, $paw, $accent, mix(a,b,t)) and resolved by a Palette at draw time, which is how one
 * drawing serves every breed colour and event colour.
 */

sealed class PaintSpec
data class Solid(val token: String) : PaintSpec()
data class Gradient(val stops: List<Pair<Float, String>>, val x1: Float, val y1: Float, val x2: Float, val y2: Float) : PaintSpec()

class Node(
    val type: String,
    val classes: List<String>,
    val transform: List<FloatArray>,      // ["t",x,y] | ["s",x,y] | ["r",deg,cx,cy] applied in order
    val hidden: Boolean,
    val opacity: Float,
    val clip: Node?,
    val fill: PaintSpec?,
    val stroke: PaintSpec?,
    val strokeWidth: Float,
    val cap: String?,
    val join: String?,
    val dash: FloatArray?,
    val fillOpacity: Float,
    val children: List<Node>,
    private val j: JSONObject,
) {
    // geometry
    val cx = j.optDouble("cx").toFloat(); val cy = j.optDouble("cy").toFloat()
    val rx = j.optDouble("rx").toFloat(); val ry = j.optDouble("ry").toFloat(); val r = j.optDouble("r").toFloat()
    val x = j.optDouble("x").toFloat(); val y = j.optDouble("y").toFloat()
    val w = j.optDouble("w").toFloat(); val h = j.optDouble("h").toFloat()
    val text: String = j.optString("s", "")
    val fontSize = j.optDouble("fs", 16.0).toFloat()
    val bold = (j.optString("fw", "400").toIntOrNull() ?: 400) >= 600 || j.optString("fw") == "bold"
    val anchor: String = j.optString("anchor", "start")

    /** parsed lazily; android's PathParser understands every SVG path command (arcs included) */
    val path: Path? by lazy { j.optString("d").takeIf { it.isNotEmpty() }?.let { runCatching { PathParser.createPathFromPathData(it) }.getOrNull() } }

    /** bounding box in the node's own coordinates (used for "origin = % of the box" animations and gradients) */
    val bounds: RectF by lazy {
        when (type) {
            "ellipse" -> RectF(cx - rx, cy - ry, cx + rx, cy + ry)
            "circle" -> RectF(cx - r, cy - r, cx + r, cy + r)
            "rect" -> RectF(x, y, x + w, y + h)
            "path" -> RectF().also { path?.computeBounds(it, true) }
            "text" -> RectF(x - fontSize * 0.4f, y - fontSize, x + fontSize * 0.4f, y)
            else -> {
                val u = RectF(); var first = true
                for (c in children) { val b = c.bounds; if (b.isEmpty && b.width() == 0f && b.height() == 0f) continue
                    if (first) { u.set(b); first = false } else u.union(b) }
                u
            }
        }
    }
    fun has(token: String) = classes.contains(token)

    // colour cache: resolved once per palette (palette ids are unique)
    var cachedPalette = -1; var cachedFill = 0; var cachedStroke = 0
}

private fun JSONObject.str(k: String): String? = if (has(k) && !isNull(k)) optString(k) else null

private fun paintOf(o: Any?): PaintSpec? = when (o) {
    is String -> Solid(o)
    is JSONObject -> {
        val g = o.getJSONArray("g")
        Gradient((0 until g.length()).map { g.getJSONArray(it).let { s -> s.getDouble(0).toFloat() to s.getString(1) } },
            o.optDouble("x1").toFloat(), o.optDouble("y1").toFloat(), o.optDouble("x2").toFloat(), o.optDouble("y2").toFloat())
    }
    else -> null
}

fun parseNode(j: JSONObject): Node {
    val tfA = j.optJSONArray("tf")
    val tf = if (tfA == null) emptyList() else (0 until tfA.length()).map { i ->
        val a = tfA.getJSONArray(i)
        FloatArray(a.length() - 1 + 1) { k -> if (k == 0) a.getString(0)[0].code.toFloat() else a.getDouble(k).toFloat() }
    }
    val ch = j.optJSONArray("ch")
    val dashA = j.optJSONArray("dash")
    return Node(
        type = j.getString("t"),
        classes = j.optString("cls", "").split(' ').filter { it.isNotEmpty() },
        transform = tf, hidden = j.optBoolean("hid"), opacity = j.optDouble("op", 1.0).toFloat(),
        clip = j.optJSONObject("clip")?.let { parseNode(it) },
        fill = if (j.has("fill")) paintOf(j.opt("fill")) else null,
        stroke = if (j.has("stroke")) paintOf(j.opt("stroke")) else null,
        strokeWidth = j.optDouble("sw", 1.0).toFloat(), cap = j.str("cap"), join = j.str("join"),
        dash = dashA?.let { a -> FloatArray(a.length()) { a.getDouble(it).toFloat() } },
        fillOpacity = j.optDouble("fop", 1.0).toFloat(),
        children = if (ch == null) emptyList() else (0 until ch.length()).map { parseNode(ch.getJSONObject(it)) },
        j = j,
    )
}

/** Breed table + the other data tables (native/assets/meta.json) */
class Breed(val key: String, val name: String, val label: String, val vibe: String, val best: String,
            val fur: String, val dark: String, val light: String, val paw: String, val ears: String)

class TypeInfo(val key: String, val label: String, val short: String, val emoji: String, val icon: String, val accent: String, val hint: String, val placeholder: String, val repeats: Boolean)
class Country(val name: String, val flag: String, val tz: String, val city: String)

/** what the dog says for each kind of play ({who} = Mama / Papa / your name); loaded from meta.json so every platform shares one list */
object Lines { @Volatile var pools: Map<String, List<String>> = emptyMap() }

class Meta(j: JSONObject) {
    init {
        j.optJSONObject("lines")?.let { l -> Lines.pools = l.keys().asSequence().associateWith { k -> l.getJSONArray(k).let { a -> (0 until a.length()).map { a.getString(it) } } } }
    }
    val breedOrder: List<String> = j.getJSONArray("breedOrder").let { a -> (0 until a.length()).map { a.getString(it) } }
    val breeds: Map<String, Breed> = j.getJSONObject("breeds").let { b ->
        b.keys().asSequence().associateWith { k -> b.getJSONObject(k).let { o ->
            Breed(k, o.getString("name"), o.getString("label"), o.getString("vibe"), o.getString("best"), o.getString("fur"), o.getString("dark"), o.getString("light"), o.getString("paw"), o.getString("ears")) } }
    }
    val typeOrder: List<String> = j.getJSONArray("typeOrder").let { a -> (0 until a.length()).map { a.getString(it) } }
    val types: Map<String, TypeInfo> = j.getJSONObject("types").let { t ->
        t.keys().asSequence().associateWith { k -> t.getJSONObject(k).let { o ->
            TypeInfo(k, o.getString("label"), o.getString("short"), o.getString("emoji"), o.optString("icon", "star"), o.getString("accent"), o.getString("hint"), o.getString("ph"), o.getBoolean("repeat")) } }
    }
    val accents: List<Pair<String, String>> = j.getJSONArray("accents").let { a -> (0 until a.length()).map { a.getJSONObject(it).let { o -> o.getString("v") to o.getString("n") } } }
    val countries: List<Country> = j.getJSONArray("countries").let { a -> (0 until a.length()).map { a.getJSONArray(it).let { c -> Country(c.getString(0), c.getString(1), c.getString(2), c.getString(3)) } } }
    val muttFurs: List<String> = j.getJSONArray("muttFurs").let { a -> (0 until a.length()).map { a.getString(it) } }
    val earTypes: List<Pair<String, String>> = j.getJSONArray("earTypes").let { a -> (0 until a.length()).map { a.getJSONArray(it).let { e -> e.getString(0) to e.getString(1) } } }
    val breedBest: Map<String, List<String>> = j.getJSONObject("breedBest").let { b ->
        b.keys().asSequence().associateWith { k -> b.getJSONArray(k).let { a -> (0 until a.length()).map { a.getString(it) } } }
    }
    fun suggestedBreed(type: String) = breedBest.entries.firstOrNull { type in it.value }?.key ?: "golden"
}

/** Everything loaded from assets, parsed on first use. */
class Art(ctx: Context) {
    private val assets = ctx.assets
    private fun read(path: String) = assets.open(path).bufferedReader().use { it.readText() }
    val meta = Meta(JSONObject(read("assets/meta.json")))
    val anim = AnimTable(JSONObject(read("assets/anim.json")))
    private val dogsJson = JSONObject(read("assets/dogs.json"))
    private val scenesJson = JSONObject(read("assets/scenes.json"))
    private val cache = HashMap<String, Node?>()

    @Synchronized
    private fun node(src: JSONObject, key: String): Node? = cache.getOrPut(src.hashCode().toString() + key) { src.optJSONObject(key)?.let { parseNode(it) } }

    /** the dog drawing for a variant ("golden", "mutt-floppy"), stage and event type */
    fun dog(variant: String, stage: Stage, type: String, bell: Boolean = false): Node? {
        val key = when {
            bell -> "$variant/bell"
            stage == Stage.PACKING || stage == Stage.TODAY -> "$variant/${stage.key}/$type"
            else -> "$variant/${stage.key}"
        }
        return node(dogsJson, key)
    }
    fun icon(variant: String): Node? = node(dogsJson, "$variant/icon")
    fun scene(type: String, door: Boolean): Node? = node(scenesJson, if (door) "$type/door" else type)
}
