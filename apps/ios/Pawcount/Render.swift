import SwiftUI

/*
 Render.swift - draws a Node tree into a SwiftUI Canvas and animates it.

 Colours: a Palette resolves the symbolic colours ($fur, $accent, mix(...)).
 Animation: AnimTable is the port of the CSS keyframes (native/assets/anim.json). Each frame, for every node we look
 up which animation definitions match its class names and turn the current time into a small transform
 (translate / scale / rotate about an origin) before drawing it.
*/

/* ---------------- colours ---------------- */

struct RGB: Equatable {
    var r: Double, g: Double, b: Double
    var color: Color { Color(red: r, green: g, blue: b) }
    static let white = RGB(r: 1, g: 1, b: 1), black = RGB(r: 0, g: 0, b: 0)
    func mixed(_ o: RGB, _ t: Double) -> RGB { RGB(r: r + (o.r - r) * t, g: g + (o.g - g) * t, b: b + (o.b - b) * t) }
}

func parseHex(_ s: String) -> RGB {
    var h = s.hasPrefix("#") ? String(s.dropFirst()) : s
    if h.count == 3 { h = h.map { "\($0)\($0)" }.joined() }
    guard let v = UInt32(h.prefix(6), radix: 16) else { return RGB(r: 1, g: 0, b: 1) }
    return RGB(r: Double((v >> 16) & 255) / 255, g: Double((v >> 8) & 255) / 255, b: Double(v & 255) / 255)
}

private var paletteCounter = 0

final class Palette {
    let fur: RGB, dark: RGB, light: RGB, paw: RGB, accent: RGB
    let id: Int
    init(fur: RGB, dark: RGB, light: RGB, paw: RGB, accent: RGB) {
        self.fur = fur; self.dark = dark; self.light = light; self.paw = paw; self.accent = accent
        paletteCounter += 1; id = paletteCounter
    }
}

func dogPalette(_ meta: Meta, _ dog: Dog, accent: RGB) -> Palette {
    let b = meta.breeds[dog.breed] ?? meta.breeds["mutt"]!
    if dog.breed != "mutt" { return Palette(fur: parseHex(b.fur), dark: parseHex(b.dark), light: parseHex(b.light), paw: parseHex(b.paw), accent: accent) }
    let fur = parseHex(dog.furColor ?? b.fur)       // a mutt derives its other shades from the coat colour the user picked
    return Palette(fur: fur, dark: fur.mixed(.black, 0.2), light: fur.mixed(.white, 0.62), paw: fur.mixed(.white, 0.55), accent: accent)
}

func resolveColor(_ token: String, _ p: Palette) -> RGB {
    if token.hasPrefix("#") { return parseHex(token) }
    if token.hasPrefix("$") {
        switch token {
        case "$fur": return p.fur; case "$dark": return p.dark; case "$light": return p.light; case "$paw": return p.paw; case "$accent": return p.accent
        default: return RGB(r: 1, g: 0, b: 1)
        }
    }
    if token.hasPrefix("mix(") && token.hasSuffix(")") {
        // mix(a,b,t) where a / b may themselves be tokens; split on the top-level commas
        let inner = String(token.dropFirst(4).dropLast())
        var parts: [String] = [], depth = 0, cur = ""
        for ch in inner {
            if ch == "(" { depth += 1 } else if ch == ")" { depth -= 1 }
            if ch == "," && depth == 0 { parts.append(cur); cur = "" } else { cur.append(ch) }
        }
        parts.append(cur)
        if parts.count == 3, let t = Double(parts[2]) { return resolveColor(parts[0], p).mixed(resolveColor(parts[1], p), t) }
    }
    return RGB(r: 1, g: 0, b: 1)
}

/* ---------------- animation ---------------- */

struct Props {
    var tx = 0.0, ty = 0.0, r = 0.0, sx = 1.0, sy = 1.0, op = 1.0
    func lerp(_ o: Props, _ k: Double) -> Props {
        Props(tx: tx + (o.tx - tx) * k, ty: ty + (o.ty - ty) * k, r: r + (o.r - r) * k, sx: sx + (o.sx - sx) * k, sy: sy + (o.sy - sy) * k, op: op + (o.op - op) * k)
    }
}

final class AnimDef {
    let sel: [String]
    let origin: (Double, Double)?
    let box: (Double, Double)?
    let d: Double, delay: Double, seq: Double, ramp: Double
    let alt: Bool, once: Bool, linear: Bool
    let n: Int
    let kf: [(Double, Props)]

    init(_ j: [String: Any]) {
        func num(_ k: String, _ def: Double = 0) -> Double { (j[k] as? NSNumber)?.doubleValue ?? def }
        sel = (j["sel"] as? String ?? "").split(separator: " ").map(String.init)
        func pair(_ k: String) -> (Double, Double)? { (j[k] as? [NSNumber]).flatMap { $0.count == 2 ? ($0[0].doubleValue, $0[1].doubleValue) : nil } }
        origin = pair("o"); box = pair("b")
        d = num("d", 1); delay = num("delay"); seq = num("seq"); ramp = num("ramp")
        alt = j["alt"] as? Bool ?? false; once = j["once"] as? Bool ?? false; linear = (j["ease"] as? String) == "l"
        n = Int(num("n"))
        kf = (j["kf"] as? [[Any]] ?? []).map { e in
            var p = Props()
            let o = (e[1] as? [String: NSNumber]) ?? [:]
            for (k, v) in o {
                switch k { case "tx": p.tx = v.doubleValue; case "ty": p.ty = v.doubleValue; case "r": p.r = v.doubleValue
                           case "sx": p.sx = v.doubleValue; case "sy": p.sy = v.doubleValue; case "op": p.op = v.doubleValue; default: break }
            }
            return ((e[0] as? NSNumber)?.doubleValue ?? 0, p)
        }
    }

    func matches(_ node: Node) -> Bool { sel.allSatisfy { node.has($0) } }

    /// value at `elapsed` seconds since this animation began (nil = finished)
    func value(at elapsedIn: Double, index: Int) -> Props? {
        let e = elapsedIn - delay - seq * Double(index)
        let loops = e / d
        if loops < 0 { return interpolate(0) }                    // still waiting for the delay
        if once && loops >= 1 { return nil }
        if n > 0 && loops >= Double(n) { return nil }
        var frac = loops - floor(loops)
        if alt && Int(floor(loops)) % 2 == 1 { frac = 1 - frac }
        return interpolate(frac)
    }

    private func interpolate(_ f: Double) -> Props {
        guard let first = kf.first, let last = kf.last else { return Props() }
        if f <= first.0 { return first.1 }
        for i in 1..<kf.count {
            let a = kf[i - 1], b = kf[i]
            if f <= b.0 {
                var u = b.0 == a.0 ? 1 : (f - a.0) / (b.0 - a.0)
                if !linear { u = (1 - cos(.pi * u)) / 2 }
                return a.1.lerp(b.1, u)
            }
        }
        return last.1
    }
}

final class AnimTable {
    let sit: [AnimDef], lie: [AnimDef], sceneAll: [AnimDef], sceneShaken: [AnimDef]
    let stage: [String: [AnimDef]]
    let state: [String: [AnimDef]]
    init(_ j: [String: Any]) {
        func list(_ a: Any?) -> [AnimDef] { (a as? [[String: Any]] ?? []).map { AnimDef($0) } }
        let dog = j["dog"] as? [String: Any] ?? [:], scene = j["scene"] as? [String: Any] ?? [:]
        sit = list(dog["sit"]); lie = list(dog["lie"]); sceneAll = list(scene["all"]); sceneShaken = list(scene["shaken"])
        var st: [String: [AnimDef]] = [:]
        for s in Stage.allCases { st[s.rawValue] = list(dog[s.rawValue]) }
        stage = st
        var sx: [String: [AnimDef]] = [:]
        for s in ["bark", "sneeze", "petting", "fetching"] { sx[s] = list(dog[s]) }
        state = sx
    }
    func dogAnims(_ stage: Stage) -> [AnimDef] { (stage == .nap || stage == .memory ? lie : sit) + (self.stage[stage.rawValue] ?? []) }
}

/// transient reactions of the dog (tap / pet / shake / fetch): name -> time (seconds) it started
final class Reactions {
    private var started: [String: Double] = [:]
    func start(_ name: String, _ t: Double) { started[name] = t }
    func stop(_ name: String) { started[name] = nil }
    func since(_ name: String, _ now: Double) -> Double? { started[name].map { now - $0 } }
    func active(_ name: String, _ now: Double, _ length: Double) -> Bool { since(name, now).map { $0 >= 0 && $0 <= length } ?? false }
}

/// Everything the renderer needs to draw one frame of a dog or a scene.
struct DrawState {
    var palette: Palette
    var time: Double
    var stage: Stage
    var anims: [AnimDef]
    var reactions: Reactions? = nil
    var stateDefs: [String: [AnimDef]] = [:]
    var shakenSince: Double? = nil
    var shakenDefs: [AnimDef] = []
    var reduceMotion = false

    // flags that used to be CSS classes on the <svg>
    var bark: Bool { reactions?.active("bark", time, 0.5) ?? false }
    var sneeze: Bool { reactions?.active("sneeze", time, 1) ?? false }
    var petting: Bool { reactions?.since("petting", time) != nil }
    var mouthOpen: Bool { stage == .packing || stage == .zoomies || stage == .today || bark || sneeze }
    var eyesShut: Bool { stage == .today || petting || sneeze }
}

/* ---------------- drawing ---------------- */

var fredokaName = "FredokaLight-Bold"

private func visible(_ n: Node, _ s: DrawState) -> Bool {
    if n.has("mouth-open") { return s.mouthOpen }
    if n.has("mouth-closed") { return !s.mouthOpen }
    if n.has("eye") { return !s.eyesShut }
    if n.has("eye-shut") { return s.eyesShut }
    return !n.hidden
}

private func solid(_ n: Node, stroke: Bool, _ p: Palette) -> Color {
    if n.cachedPalette != p.id {
        n.cachedPalette = p.id
        if case .solid(let t)? = n.fill { n.cachedFill = resolveColor(t, p).color }
        if case .solid(let t)? = n.stroke { n.cachedStroke = resolveColor(t, p).color }
    }
    return stroke ? n.cachedStroke : n.cachedFill
}

/// final class so the "n-th matching node" counters survive recursion
final class SeqCounter { var counts: [ObjectIdentifier: Int] = [:] }

private func applyAnimation(_ c: inout GraphicsContext, _ n: Node, _ s: DrawState, _ seq: SeqCounter) -> Double {
    if n.classes.isEmpty || s.reduceMotion { return 1 }
    var base: AnimDef? = nil
    for a in s.anims where a.matches(n) { base = a }
    var state: AnimDef? = nil
    var stateStart = 0.0
    if let rx = s.reactions {
        for (name, defs) in s.stateDefs {
            guard let since = rx.since(name, s.time) else { continue }
            for a in defs where a.matches(n) { state = a; stateStart = since }
        }
    }
    var shaken: AnimDef? = nil
    if s.shakenSince != nil { for a in s.shakenDefs where a.matches(n) { shaken = a } }
    if base == nil && state == nil && shaken == nil { return 1 }

    var index = 0
    if let b = base, b.seq != 0 {
        let id = ObjectIdentifier(b); index = seq.counts[id, default: 0]; seq.counts[id] = index + 1
    }
    var pr: Props? = base?.value(at: s.time, index: index)
    var def = base
    if let st = state, let sp = st.value(at: stateStart, index: 0) {
        let k = st.ramp > 0 ? min(1, max(0, stateStart / st.ramp)) : 1
        pr = (pr == nil || k >= 1) ? sp : pr!.lerp(sp, k)
        def = st
    }
    if let sh = shaken, let since = s.shakenSince, let sp = sh.value(at: since, index: 0) { pr = sp; def = sh }
    guard let p = pr, let d = def else { return 1 }
    var ox = 0.0, oy = 0.0
    if let o = d.origin { ox = o.0; oy = o.1 }
    else if let b = d.box { let bb = n.bounds; ox = Double(bb.minX) + b.0 * Double(bb.width); oy = Double(bb.minY) + b.1 * Double(bb.height) }
    c.translateBy(x: ox + p.tx, y: oy + p.ty)        // order matches the CSS: translate, then scale, then rotate
    c.scaleBy(x: p.sx, y: p.sy)
    c.rotate(by: .degrees(p.r))
    c.translateBy(x: -ox, y: -oy)
    return p.op
}

func drawNode(_ ctx: inout GraphicsContext, _ n: Node, _ s: DrawState, alpha: Double = 1, seq: SeqCounter = SeqCounter()) {
    if !visible(n, s) { return }
    var c = ctx                                       // a copy = "save"; changes below don't leak to siblings
    for t in n.transform {
        switch Int(t[0]) {
        case 0: c.translateBy(x: t[1], y: t[2])
        case 1: c.scaleBy(x: t[1], y: t[2])
        default: c.translateBy(x: t[2], y: t[3]); c.rotate(by: .degrees(t[1])); c.translateBy(x: -t[2], y: -t[3])
        }
    }
    if let clip = n.clip, let p = clip.shape { c.clip(to: p) }
    let a = alpha * n.opacity * applyAnimation(&c, n, s, seq)

    if n.type == "g" { for ch in n.children { drawNode(&c, ch, s, alpha: a, seq: seq) }; return }
    if n.type == "text" {
        if case .solid(let t)? = n.fill {
            let col = resolveColor(t, s.palette).color.opacity(a)
            let txt = Text(n.text).font(.custom(fredokaName, size: n.fontSize)).foregroundColor(col)
            c.draw(txt, at: CGPoint(x: n.x, y: n.y), anchor: n.anchor == "middle" ? .bottom : .bottomLeading)
        }
        return
    }
    guard let shape = n.shape else { return }
    if let f = n.fill {
        switch f {
        case .solid: c.fill(shape, with: .color(solid(n, stroke: false, s.palette).opacity(a * n.fillOpacity)))
        case .gradient(let stops, let x1, let y1, let x2, let y2):
            let bb = n.bounds
            let g = Gradient(stops: stops.map { Gradient.Stop(color: resolveColor($0.1, s.palette).color, location: $0.0) })
            c.opacity = a * n.fillOpacity
            c.fill(shape, with: .linearGradient(g, startPoint: CGPoint(x: bb.minX + x1 * bb.width, y: bb.minY + y1 * bb.height),
                                                endPoint: CGPoint(x: bb.minX + x2 * bb.width, y: bb.minY + y2 * bb.height)))
            c.opacity = 1
        }
    }
    if case .solid? = n.stroke {
        let cap: CGLineCap = n.cap == "round" ? .round : (n.cap == "square" ? .square : .butt)
        let join: CGLineJoin = n.join == "round" ? .round : (n.join == "bevel" ? .bevel : .miter)
        c.stroke(shape, with: .color(solid(n, stroke: true, s.palette).opacity(a)),
                 style: StrokeStyle(lineWidth: n.strokeWidth, lineCap: cap, lineJoin: join, dash: (n.dash ?? []).map { CGFloat($0) }))
    }
}

/// Scales the 400x300 scene drawing to fill the canvas, cropping like CSS "slice" anchored bottom-centre.
func drawScene(_ ctx: inout GraphicsContext, _ scene: Node, size: CGSize, _ s: DrawState) {
    let k = max(size.width / 400, size.height / 300)
    var c = ctx
    c.translateBy(x: (size.width - 400 * k) / 2, y: size.height - 300 * k)
    c.scaleBy(x: k, y: k)
    drawNode(&c, scene, s)
}

/// Draws a 240x240 dog into the square (left, top, size).
func drawDog(_ ctx: inout GraphicsContext, _ dog: Node, left: CGFloat, top: CGFloat, size: CGFloat, _ s: DrawState) {
    var c = ctx
    c.translateBy(x: left, y: top)
    c.scaleBy(x: size / 240, y: size / 240)
    drawNode(&c, dog, s)
}
