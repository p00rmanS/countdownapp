import SwiftUI

/*
 SceneView.swift - one animated scene: backdrop + atmosphere (stars, sun rays, confetti...) + the dog + hearts/ball,
 and the speech bubble. The same view is used big (detail screen), medium (home hero) and small (cards).

 All drawing happens in one Canvas that redraws every frame from a TimelineView clock, so animation is smooth and
 stops automatically when the scene leaves the screen.
*/

enum SceneSize {
    case hero, card, detail, preview
    var dogHeight: CGFloat { switch self { case .hero, .preview: return 0.74; case .card: return 0.70; case .detail: return 0.60 } }
    var dogBottom: CGFloat { switch self { case .hero, .preview: return 0.03; case .card: return 0.05; case .detail: return 0.23 } }
    var ratio: CGFloat? { switch self { case .hero: return 4 / 3.15; case .card: return 1 / 0.98; case .preview: return 16 / 11; case .detail: return nil } }
}

final class Heart { let x: CGFloat, y: CGFloat, born: Double, dx: CGFloat, size: CGFloat, glyph: String
    init(x: CGFloat, y: CGFloat, born: Double, dx: CGFloat, size: CGFloat, glyph: String) { self.x = x; self.y = y; self.born = born; self.dx = dx; self.size = size; self.glyph = glyph } }

/// State of one interactive scene: reactions in progress, floating hearts, the speech bubble.
final class SceneController: ObservableObject {
    let reactions = Reactions()
    var hearts: [Heart] = []
    @Published var bubble: String? = nil
    @Published var hint = true
    var shakenAt: Double? = nil
    var fetchAt: Double? = nil
    var clock = 0.0
}

/// seconds since the app started drawing (a small, continuous clock for the animations)
let clockEpoch = Date().timeIntervalSinceReferenceDate

struct SeededRandom: RandomNumberGenerator {
    var state: UInt64
    mutating func next() -> UInt64 { state &+= 0x9E3779B97F4A7C15; var z = state; z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9; z = (z ^ (z >> 27)) &* 0x94D049BB133111EB; return z ^ (z >> 31) }
}

private let confettiColors: [Color] = [Color(hex: "#FF7DAA"), Color(hex: "#FFD35C"), Color(hex: "#5DADE8"), Color(hex: "#D4E157"), .white, Color(hex: "#E5574F")]

struct SceneView: View {
    let c: Countdown
    let k: Computed
    let art: Art
    var size: SceneSize
    var stage: Stage? = nil
    var controller: SceneController? = nil
    var reduceMotion = false
    var bell = false
    var interactive = false
    var onBark: () -> Void = {}
    var onPetStart: () -> Void = {}
    var dogOverride: Dog? = nil
    var typeOverride: String? = nil

    @State var holdTask: Task<Void, Never>? = nil
    @State var petting = false
    @State var pressing = false
    @State var pressPoint = CGPoint.zero

    var body: some View {
        let st = stage ?? k.stage
        let dog = dogOverride ?? c.dog
        let type = typeOverride ?? c.type
        let accent = parseHex(c.accent)
        let palette = dogPalette(art.meta, dog, accent: accent)
        let showDoor = st == .waiting || st == .packing
        let sceneNode = art.scene(type, door: showDoor)
        let dogNode = art.dog(dog.variant, st, type, bell: bell)
        let anims = art.anim.dogAnims(st)
        let seed = UInt64(truncatingIfNeeded: abs(c.id.hashValue))

        GeometryReader { geo in
            ZStack {
                TimelineView(.animation(minimumInterval: nil, paused: reduceMotion)) { tl in
                    Canvas { ctx, sz in
                        let t = tl.date.timeIntervalSinceReferenceDate - clockEpoch
                        controller?.clock = t
                        var c0 = ctx
                        let bg = DrawState(palette: palette, time: t, stage: st, anims: art.anim.sceneAll,
                                           shakenSince: controller?.shakenAt.map { t - $0 }.flatMap { $0 < 0.8 ? $0 : nil }, shakenDefs: art.anim.sceneShaken, reduceMotion: reduceMotion)
                        if let s = sceneNode { drawScene(&c0, s, size: sz, bg) }
                        drawAtmosphere(&c0, st, sz, t, seed, mini: size == .card, still: reduceMotion)
                        let dogSize = sz.height * size.dogHeight
                        var state = DrawState(palette: palette, time: t, stage: st, anims: anims, reactions: controller?.reactions, stateDefs: art.anim.state, reduceMotion: reduceMotion)
                        if let ctrl = controller, let f = ctrl.fetchAt, t - f < 2 {
                            if ctrl.reactions.since("fetching", t) == nil { ctrl.reactions.start("fetching", f) }
                        }
                        state.reactions = controller?.reactions
                        if let d = dogNode { drawDog(&c0, d, left: (sz.width - dogSize) / 2, top: sz.height - sz.height * size.dogBottom - dogSize, size: dogSize, state) }
                        if let ctrl = controller { drawOverlays(&c0, ctrl, sz, t) }
                    }
                }
                if interactive, let ctrl = controller {
                    let dogW = geo.size.height * size.dogHeight
                    Color.clear.contentShape(Rectangle())
                        .frame(width: dogW, height: dogW)
                        .position(x: geo.size.width / 2, y: geo.size.height - geo.size.height * size.dogBottom - dogW / 2)
                        .gesture(DragGesture(minimumDistance: 0)
                            .onChanged { v in
                                pressPoint = v.location
                                if !pressing {
                                    pressing = true; petting = false
                                    holdTask = Task { @MainActor in
                                        try? await Task.sleep(nanoseconds: 380_000_000)
                                        if Task.isCancelled { return }
                                        petting = true; ctrl.hint = false; ctrl.reactions.start("petting", ctrl.clock); onPetStart()
                                        var n = 0
                                        while !Task.isCancelled {
                                            let origin = CGPoint(x: geo.size.width / 2 - dogW / 2 + pressPoint.x, y: geo.size.height - geo.size.height * size.dogBottom - dogW + pressPoint.y)
                                            ctrl.hearts.append(Heart(x: origin.x, y: origin.y, born: ctrl.clock, dx: CGFloat.random(in: -35...35), size: CGFloat.random(in: 16...30), glyph: Int.random(in: 0..<5) == 0 ? "🐾" : "♥"))
                                            if n % 3 == 0 { Haptics.play(true, "purr") }
                                            n += 1
                                            try? await Task.sleep(nanoseconds: 280_000_000)
                                        }
                                    }
                                }
                            }
                            .onEnded { _ in
                                holdTask?.cancel(); holdTask = nil; pressing = false
                                if petting { petting = false; ctrl.reactions.stop("petting") }
                                else { ctrl.hint = false; ctrl.reactions.start("bark", ctrl.clock); onBark() }
                            })
                        .accessibilityElement().accessibilityLabel("\(dog.name). Tap to bark, press and hold to pet.")
                        .accessibilityAddTraits(.isButton).accessibilityAction { ctrl.reactions.start("bark", ctrl.clock); onBark() }
                }
                if let ctrl = controller { BubbleView(ctrl: ctrl, detail: size == .detail, height: geo.size.height) }
            }
        }
        .modifier(AspectIfNeeded(ratio: size.ratio))
        .clipped()
    }
}

private struct AspectIfNeeded: ViewModifier {
    let ratio: CGFloat?
    func body(content: Content) -> some View { if let r = ratio { content.aspectRatio(r, contentMode: .fit) } else { content } }
}

private struct BubbleView: View {
    @ObservedObject var ctrl: SceneController
    let detail: Bool
    let height: CGFloat
    var body: some View {
        VStack {
            if let b = ctrl.bubble {
                Text(b).font(PawFont.display(17)).foregroundColor(Color(hex: "#3D2616")).multilineTextAlignment(.center)
                    .padding(.horizontal, 16).padding(.vertical, 9)
                    .background(RoundedRectangle(cornerRadius: 20).fill(.white).shadow(color: .black.opacity(0.2), radius: 8, y: 3))
                    .transition(.scale(scale: 0.7).combined(with: .opacity)).id(b)
            }
            Spacer()
        }
        .padding(.top, detail ? height * 0.2 + 62 : height * 0.18).padding(.horizontal, 30)
        .animation(.spring(response: 0.35, dampingFraction: 0.6), value: ctrl.bubble)
        .allowsHitTesting(false)
        .onChange(of: ctrl.bubble) { v in
            guard let v else { return }
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.4) { if ctrl.bubble == v { ctrl.bubble = nil } }
        }
    }
}

/* ---------- hearts + fetch ball ---------- */

private func drawOverlays(_ c: inout GraphicsContext, _ ctrl: SceneController, _ sz: CGSize, _ t: Double) {
    ctrl.hearts.removeAll { t - $0.born > 1.5 }
    for h in ctrl.hearts {
        let u = (t - h.born) / 1.4
        let alpha = u < 0.15 ? u / 0.15 : 1 - (u - 0.15) / 0.85
        let txt = Text(h.glyph).font(.system(size: h.size * (0.5 + 0.7 * min(1, u)))).foregroundColor(Color(hex: "#E5574F").opacity(max(0, min(1, alpha))))
        c.draw(txt, at: CGPoint(x: h.x + h.dx * u, y: h.y - 110 * u))
    }
    if let f = ctrl.fetchAt, t - f < 1.4 {
        let u = min(1, (t - f) / 1.2)
        let x = sz.width * 0.88 - u * sz.width * 0.45, y = sz.height * -0.04 + u * sz.height * 0.62 - sin(u * .pi) * sz.height * 0.1
        c.fill(Path(ellipseIn: CGRect(x: x - 11, y: y - 11, width: 22, height: 22)), with: .color(Color(hex: "#D4E157").opacity(1 - u * u)))
    }
}

/* ---------- atmosphere ---------- */

/// stars + moon (nap), sun rays + confetti (today), speed lines (zoomies), "?" (curious), warm glow (memory)
private func drawAtmosphere(_ c: inout GraphicsContext, _ stage: Stage, _ sz: CGSize, _ t: Double, _ seed: UInt64, mini: Bool, still: Bool) {
    let w = sz.width, h = sz.height
    var rnd = SeededRandom(state: seed)
    func r() -> Double { Double.random(in: 0..<1, using: &rnd) }
    switch stage {
    case .nap:
        // dim the room and put a moon and stars in the sky
        c.fill(Path(CGRect(origin: .zero, size: sz)), with: .linearGradient(Gradient(stops: [.init(color: Color(hex: "#10163F").opacity(0.62), location: 0), .init(color: Color(hex: "#272C66").opacity(0.62), location: 0.7), .init(color: Color(hex: "#3A3570").opacity(0.62), location: 1)]), startPoint: .zero, endPoint: CGPoint(x: 0, y: h)))
        let mr = min(w, h) * 0.055, mx = w * 0.83, my = h * 0.15
        c.fill(Path(ellipseIn: CGRect(x: mx - mr, y: my - mr, width: mr * 2, height: mr * 2)), with: .color(Color(hex: "#FFF4CC")))
        c.fill(Path(ellipseIn: CGRect(x: mx + mr * 0.55 - mr * 0.9, y: my - mr * 0.2 - mr * 0.9, width: mr * 1.8, height: mr * 1.8)), with: .color(Color(hex: "#272C66")))
        for i in 0..<(mini ? 4 : 8) {
            let sx = w * (0.06 + r() * 0.88), sy = h * (0.06 + r() * 0.40)
            let tw = still ? 0.8 : 0.25 + 0.75 * (0.5 + 0.5 * sin(t * 2.1 + Double(i) * 1.7))
            let rad = 1.6 + tw * 1.4
            c.fill(Path(ellipseIn: CGRect(x: sx - rad, y: sy - rad, width: rad * 2, height: rad * 2)), with: .color(.white.opacity(tw)))
        }
    case .today:
        // slowly turning sun rays, then confetti falling
        let cx = w / 2, cy = h * 0.52, rad = w * 0.95, rot = still ? 0 : t / 36 * 360
        let shade = GraphicsContext.Shading.radialGradient(Gradient(colors: [.white.opacity(0.34), .white.opacity(0)]), center: CGPoint(x: cx, y: cy), startRadius: 0, endRadius: w * 0.62)
        for i in 0..<15 {
            let a0 = (rot + Double(i) * 24) * .pi / 180, a1 = (rot + Double(i) * 24 + 8) * .pi / 180
            var p = Path(); p.move(to: CGPoint(x: cx, y: cy))
            p.addLine(to: CGPoint(x: cx + rad * cos(a0), y: cy + rad * sin(a0))); p.addLine(to: CGPoint(x: cx + rad * cos(a1), y: cy + rad * sin(a1))); p.closeSubpath()
            c.fill(p, with: shade)
        }
        if !still {
            for i in 0..<(mini ? 9 : 18) {
                let x = w * (0.02 + r() * 0.96), dur = 3 + r() * 2.5, off = r() * 3
                let u = ((t + off) / dur).truncatingRemainder(dividingBy: 1)
                var cc = c
                cc.translateBy(x: x, y: -14 + u * (h + 40)); cc.rotate(by: .degrees(u * 560))
                cc.fill(Path(roundedRect: CGRect(x: -3.5, y: -6.5, width: 7, height: 13), cornerRadius: 2), with: .color(confettiColors[i % confettiColors.count].opacity(min(u, 0.1) / 0.1 * 0.9)))
            }
        }
    case .zoomies:
        if !still {
            for (i, line) in [(0.06, 0.50, 0.14), (0.66, 0.62, 0.20), (0.30, 0.72, 0.10)].enumerated() {
                let u = ((t + Double(i) * 0.25) / 0.7).truncatingRemainder(dividingBy: 1)
                let a = (u < 0.3 ? u / 0.3 : 1 - (u - 0.3) / 0.7) * 0.7
                let x = w * line.0 + (40 - 100 * u)
                c.fill(Path(roundedRect: CGRect(x: x, y: h * line.1, width: w * line.2, height: 3.5), cornerRadius: 2), with: .color(.white.opacity(max(0, a))))
            }
        }
    case .curious:
        let bob = still ? 0 : sin(t * 2.6) * 8
        var cc = c
        cc.translateBy(x: w * 0.76, y: h * 0.2); cc.rotate(by: .degrees(still ? -8 : sin(t * 2.6) * 8))
        cc.draw(Text("?").font(.custom(fredokaName, size: (mini ? 26 : 38) * min(1.6, max(0.8, w / 360)))).foregroundColor(.white), at: CGPoint(x: 0, y: bob))
    case .memory:
        c.fill(Path(CGRect(origin: .zero, size: sz)), with: .radialGradient(Gradient(colors: [Color(hex: "#FFD68C").opacity(0.45), Color(hex: "#FFD68C").opacity(0)]), center: CGPoint(x: w / 2, y: h * 0.7), startRadius: 0, endRadius: w * 0.6))
        c.fill(Path(CGRect(origin: .zero, size: sz)), with: .color(Color(hex: "#6E4614").opacity(0.18)))   // sepia wash
    default: break
    }
}
