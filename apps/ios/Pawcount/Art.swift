import SwiftUI

/*
 Art.swift - loads the dog and scene drawings that tools/export-native.js produced from the web app's SVG
 (the JSON files in native/assets) and turns them into a tree of Node objects ready to draw.

 A Node is one SVG element: group / ellipse / circle / rect / path / text. Colours stay symbolic
 ($fur, $dark, $light, $paw, $accent, mix(a,b,t)) and are resolved by a Palette at draw time, which is how one
 drawing serves every breed colour and event colour.
*/

enum PaintSpec {
    case solid(String)
    case gradient(stops: [(Double, String)], x1: Double, y1: Double, x2: Double, y2: Double)
}

final class Node {
    let type: String
    let classes: [String]
    let transform: [[Double]]      // ["t",x,y] -> [0,x,y]; ["s",x,y] -> [1,x,y]; ["r",a,cx,cy] -> [2,a,cx,cy]
    let hidden: Bool
    let opacity: Double
    let clip: Node?
    let fill: PaintSpec?
    let stroke: PaintSpec?
    let strokeWidth: Double
    let cap: String?
    let join: String?
    let dash: [Double]?
    let fillOpacity: Double
    let children: [Node]
    // geometry
    let cx: Double, cy: Double, rx: Double, ry: Double, r: Double
    let x: Double, y: Double, w: Double, h: Double
    let text: String
    let fontSize: Double
    let anchor: String

    /// the shape as a SwiftUI Path (ellipse / circle / rect / svg path); nil for groups and text
    let shape: Path?
    /// bounding box in the node's own coordinates (for "origin = % of the box" animations and gradients)
    private(set) lazy var bounds: CGRect = {
        if let s = shape { return s.boundingRect }
        if type == "text" { return CGRect(x: x - fontSize * 0.4, y: y - fontSize, width: fontSize * 0.8, height: fontSize) }
        var u: CGRect? = nil
        for c in children { let b = c.bounds; if b.width == 0 && b.height == 0 { continue }; u = u.map { $0.union(b) } ?? b }
        return u ?? .zero
    }()

    // colour cache: resolved once per palette
    var cachedPalette = -1
    var cachedFill: Color = .clear
    var cachedStroke: Color = .clear

    func has(_ token: String) -> Bool { classes.contains(token) }

    init(_ j: [String: Any]) {
        func d(_ k: String, _ def: Double = 0) -> Double { (j[k] as? NSNumber)?.doubleValue ?? def }
        type = j["t"] as? String ?? "g"
        classes = (j["cls"] as? String ?? "").split(separator: " ").map(String.init)
        transform = (j["tf"] as? [[Any]] ?? []).map { t in
            let op = (t[0] as? String) ?? "t"
            let nums = t.dropFirst().compactMap { ($0 as? NSNumber)?.doubleValue }
            return [op == "t" ? 0 : (op == "s" ? 1 : 2)] + nums
        }
        hidden = j["hid"] as? Bool ?? false
        opacity = d("op", 1)
        clip = (j["clip"] as? [String: Any]).map { Node($0) }
        func paint(_ v: Any?) -> PaintSpec? {
            if let s = v as? String { return .solid(s) }
            if let o = v as? [String: Any], let g = o["g"] as? [[Any]] {
                let stops = g.map { s in ((s[0] as? NSNumber)?.doubleValue ?? 0, (s[1] as? String) ?? "#000") }
                return .gradient(stops: stops, x1: (o["x1"] as? NSNumber)?.doubleValue ?? 0, y1: (o["y1"] as? NSNumber)?.doubleValue ?? 0,
                                 x2: (o["x2"] as? NSNumber)?.doubleValue ?? 0, y2: (o["y2"] as? NSNumber)?.doubleValue ?? 1)
            }
            return nil
        }
        fill = paint(j["fill"]); stroke = paint(j["stroke"])
        strokeWidth = d("sw", 1); cap = j["cap"] as? String; join = j["join"] as? String
        dash = (j["dash"] as? [NSNumber])?.map { $0.doubleValue }
        fillOpacity = d("fop", 1)
        children = (j["ch"] as? [[String: Any]] ?? []).map { Node($0) }
        cx = d("cx"); cy = d("cy"); rx = d("rx"); ry = d("ry"); r = d("r")
        x = d("x"); y = d("y"); w = d("w"); h = d("h")
        text = j["s"] as? String ?? ""
        fontSize = d("fs", 16)
        anchor = j["anchor"] as? String ?? "start"
        switch type {
        case "ellipse": shape = Path(ellipseIn: CGRect(x: cx - rx, y: cy - ry, width: rx * 2, height: ry * 2))
        case "circle": shape = Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2))
        case "rect":
            let rect = CGRect(x: x, y: y, width: w, height: h)
            shape = rx > 0 ? Path(roundedRect: rect, cornerRadius: rx) : Path(rect)
        case "path": shape = SVGPath.parse(j["d"] as? String ?? "")
        default: shape = nil
        }
    }
}

/* ---------------- SVG path data -> Path ---------------- */

enum SVGPath {
    static func parse(_ d: String) -> Path {
        var p = Path()
        let s = Array(d.unicodeScalars)
        var i = 0
        var cur = CGPoint.zero, start = CGPoint.zero, lastCtl: CGPoint? = nil, lastCmd: Character = " "

        func skipSep() { while i < s.count, s[i] == " " || s[i] == "," || s[i] == "\n" || s[i] == "\t" || s[i] == "\r" { i += 1 } }
        func number() -> Double? {
            skipSep()
            var j = i
            if j < s.count, s[j] == "-" || s[j] == "+" { j += 1 }
            var seenDot = false, digits = false
            while j < s.count {
                let c = s[j]
                if c.value >= 48 && c.value <= 57 { digits = true; j += 1 }
                else if c == "." && !seenDot { seenDot = true; j += 1 }
                else { break }
            }
            if !digits { return nil }
            if j < s.count, s[j] == "e" || s[j] == "E" {            // exponent
                var k = j + 1
                if k < s.count, s[k] == "-" || s[k] == "+" { k += 1 }
                var ed = false
                while k < s.count, s[k].value >= 48 && s[k].value <= 57 { ed = true; k += 1 }
                if ed { j = k }
            }
            let str = String(String.UnicodeScalarView(s[i..<j]))
            i = j
            return Double(str)
        }
        func flag() -> Bool? {                                          // arc flags may be glued to the next number
            skipSep()
            guard i < s.count, s[i] == "0" || s[i] == "1" else { return nil }
            let v = s[i] == "1"; i += 1; return v
        }
        func pt(_ rel: Bool) -> CGPoint? {
            guard let x = number(), let y = number() else { return nil }
            return rel ? CGPoint(x: cur.x + x, y: cur.y + y) : CGPoint(x: x, y: y)
        }

        while true {
            skipSep()
            guard i < s.count else { break }
            var cmd: Character
            let ch = Character(s[i])
            if ch.isLetter { cmd = ch; i += 1 } else { cmd = lastCmd; if cmd == "M" { cmd = "L" } else if cmd == "m" { cmd = "l" } }   // implicit repeat
            let rel = cmd.isLowercase
            switch cmd.uppercased().first! {
            case "M":
                guard let q = pt(rel) else { return p }
                p.move(to: q); cur = q; start = q; lastCtl = nil
            case "L":
                guard let q = pt(rel) else { return p }
                p.addLine(to: q); cur = q; lastCtl = nil
            case "H":
                guard let x = number() else { return p }
                cur = CGPoint(x: rel ? cur.x + x : x, y: cur.y); p.addLine(to: cur); lastCtl = nil
            case "V":
                guard let y = number() else { return p }
                cur = CGPoint(x: cur.x, y: rel ? cur.y + y : y); p.addLine(to: cur); lastCtl = nil
            case "C":
                guard let a = pt(rel), let b = pt(rel), let q = pt(rel) else { return p }
                p.addCurve(to: q, control1: a, control2: b); lastCtl = b; cur = q
            case "S":
                let a = lastCtl.map { CGPoint(x: 2 * cur.x - $0.x, y: 2 * cur.y - $0.y) } ?? cur
                guard let b = pt(rel), let q = pt(rel) else { return p }
                p.addCurve(to: q, control1: a, control2: b); lastCtl = b; cur = q
            case "Q":
                guard let a = pt(rel), let q = pt(rel) else { return p }
                p.addQuadCurve(to: q, control: a); lastCtl = a; cur = q
            case "T":
                let a = lastCtl.map { CGPoint(x: 2 * cur.x - $0.x, y: 2 * cur.y - $0.y) } ?? cur
                guard let q = pt(rel) else { return p }
                p.addQuadCurve(to: q, control: a); lastCtl = a; cur = q
            case "A":
                guard let rx = number(), let ry = number(), let rot = number(), let large = flag(), let sweep = flag(), let q = pt(rel) else { return p }
                arc(&p, from: cur, rx: rx, ry: ry, rotation: rot, large: large, sweep: sweep, to: q); cur = q; lastCtl = nil
            case "Z":
                p.closeSubpath(); cur = start; lastCtl = nil
            default: return p
            }
            lastCmd = cmd
        }
        return p
    }

    /// SVG elliptical arc -> cubic Béziers (the standard endpoint-to-centre conversion)
    private static func arc(_ p: inout Path, from a: CGPoint, rx rx0: Double, ry ry0: Double, rotation: Double, large: Bool, sweep: Bool, to b: CGPoint) {
        var rx = abs(rx0), ry = abs(ry0)
        if rx == 0 || ry == 0 || (a.x == b.x && a.y == b.y) { p.addLine(to: b); return }
        let phi = rotation * .pi / 180, cp = cos(phi), sp = sin(phi)
        let dx = (a.x - b.x) / 2, dy = (a.y - b.y) / 2
        let x1 = cp * dx + sp * dy, y1 = -sp * dx + cp * dy
        let lam = (x1 * x1) / (rx * rx) + (y1 * y1) / (ry * ry)
        if lam > 1 { let s = lam.squareRoot(); rx *= s; ry *= s }
        let sign: Double = large == sweep ? -1 : 1
        let num = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1
        let den = rx * rx * y1 * y1 + ry * ry * x1 * x1
        let co = sign * (max(0, num / den)).squareRoot()
        let cxp = co * rx * y1 / ry, cyp = -co * ry * x1 / rx
        let cx = cp * cxp - sp * cyp + (a.x + b.x) / 2, cy = sp * cxp + cp * cyp + (a.y + b.y) / 2
        func ang(_ ux: Double, _ uy: Double, _ vx: Double, _ vy: Double) -> Double {
            let dot = ux * vx + uy * vy, len = (ux * ux + uy * uy).squareRoot() * (vx * vx + vy * vy).squareRoot()
            var a = acos(max(-1, min(1, dot / len)))
            if ux * vy - uy * vx < 0 { a = -a }
            return a
        }
        let th1 = ang(1, 0, (x1 - cxp) / rx, (y1 - cyp) / ry)
        var dth = ang((x1 - cxp) / rx, (y1 - cyp) / ry, (-x1 - cxp) / rx, (-y1 - cyp) / ry)
        if !sweep && dth > 0 { dth -= 2 * .pi } else if sweep && dth < 0 { dth += 2 * .pi }
        let segs = Int(ceil(abs(dth) / (.pi / 2)))
        let delta = dth / Double(segs), t = 4.0 / 3.0 * tan(delta / 4)
        var th = th1
        for _ in 0..<segs {
            let c1 = cos(th), s1 = sin(th), c2 = cos(th + delta), s2 = sin(th + delta)
            func map(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: cp * rx * x - sp * ry * y + cx, y: sp * rx * x + cp * ry * y + cy) }
            // control points on the unit circle, then scaled / rotated / moved
            let p1 = map(c1 - t * s1, s1 + t * c1), p2 = map(c2 + t * s2, s2 - t * c2), e = map(c2, s2)
            p.addCurve(to: e, control1: p1, control2: p2)
            th += delta
        }
    }
}

/* ---------------- data tables (native/assets/meta.json) ---------------- */

struct Breed { let key, name, label, vibe, best, fur, dark, light, paw, ears: String }
struct TypeInfo { let key, label, short, emoji, icon, accent, hint, placeholder: String; let repeats: Bool }
struct Country { let name, flag, tz, city: String }

final class Meta {
    let breedOrder: [String]
    let breeds: [String: Breed]
    let typeOrder: [String]
    let types: [String: TypeInfo]
    let accents: [(String, String)]
    let countries: [Country]
    let muttFurs: [String]
    let earTypes: [(String, String)]
    let breedBest: [String: [String]]
    let milestones = [100, 50, 30, 10, 7, 1]

    init(_ j: [String: Any]) {
        breedOrder = j["breedOrder"] as? [String] ?? []
        var b: [String: Breed] = [:]
        for (k, v) in (j["breeds"] as? [String: [String: Any]] ?? [:]) {
            func s(_ n: String) -> String { v[n] as? String ?? "" }
            b[k] = Breed(key: k, name: s("name"), label: s("label"), vibe: s("vibe"), best: s("best"), fur: s("fur"), dark: s("dark"), light: s("light"), paw: s("paw"), ears: s("ears"))
        }
        breeds = b
        typeOrder = j["typeOrder"] as? [String] ?? []
        var t: [String: TypeInfo] = [:]
        for (k, v) in (j["types"] as? [String: [String: Any]] ?? [:]) {
            func s(_ n: String) -> String { v[n] as? String ?? "" }
            t[k] = TypeInfo(key: k, label: s("label"), short: s("short"), emoji: s("emoji"), icon: (v["icon"] as? String) ?? "star", accent: s("accent"), hint: s("hint"), placeholder: s("ph"), repeats: v["repeat"] as? Bool ?? false)
        }
        types = t
        accents = (j["accents"] as? [[String: String]] ?? []).map { ($0["v"] ?? "#E8A15C", $0["n"] ?? "") }
        countries = (j["countries"] as? [[String]] ?? []).map { Country(name: $0[0], flag: $0[1], tz: $0[2], city: $0[3]) }
        muttFurs = j["muttFurs"] as? [String] ?? []
        earTypes = (j["earTypes"] as? [[String]] ?? []).map { ($0[0], $0[1]) }
        breedBest = j["breedBest"] as? [String: [String]] ?? [:]
    }
    func suggestedBreed(_ type: String) -> String { breedBest.first(where: { $0.value.contains(type) })?.key ?? "golden" }
}

/// Everything loaded from the app bundle, parsed on first use.
final class Art {
    let meta: Meta
    let anim: AnimTable
    private let dogsJson: [String: Any]
    private let scenesJson: [String: Any]
    private var cache: [String: Node] = [:]

    init() {
        func load(_ name: String) -> [String: Any] {
            guard let url = Bundle.main.url(forResource: name, withExtension: "json"), let data = try? Data(contentsOf: url),
                  let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return [:] }
            return obj
        }
        meta = Meta(load("meta"))
        anim = AnimTable(load("anim"))
        dogsJson = load("dogs")
        scenesJson = load("scenes")
    }

    private func node(_ src: [String: Any], _ prefix: String, _ key: String) -> Node? {
        let ck = prefix + key
        if let n = cache[ck] { return n }
        guard let j = src[key] as? [String: Any] else { return nil }
        let n = Node(j); cache[ck] = n; return n
    }

    /// the dog drawing for a variant ("golden", "mutt-floppy"), stage and event type
    func dog(_ variant: String, _ stage: Stage, _ type: String, bell: Bool = false) -> Node? {
        let key: String
        if bell { key = "\(variant)/bell" }
        else if stage == .packing || stage == .today { key = "\(variant)/\(stage.rawValue)/\(type)" }
        else { key = "\(variant)/\(stage.rawValue)" }
        return node(dogsJson, "d:", key)
    }
    func icon(_ variant: String) -> Node? { node(dogsJson, "d:", "\(variant)/icon") }
    func scene(_ type: String, door: Bool) -> Node? { node(scenesJson, "s:", door ? "\(type)/door" : type) }
}
