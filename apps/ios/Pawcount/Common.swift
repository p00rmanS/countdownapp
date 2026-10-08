import SwiftUI

/* Common.swift - pieces shared by several screens: toast, tab bar, rolling numerals, the leash progress bar, confetti. */

struct ToastView: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.paw) var pc
    var body: some View {
        VStack {
            if let t = store.toast {
                Text(t).font(PawFont.body(14, 800)).multilineTextAlignment(.center)
                    .foregroundColor(pc.dark ? pc.night : Color(hex: "#FFF1E0"))
                    .padding(.horizontal, 18).padding(.vertical, 12)
                    .background(RoundedRectangle(cornerRadius: 20).fill(pc.dark ? Color(hex: "#FFF1E0") : pc.night).shadow(color: .black.opacity(0.25), radius: 8, y: 3))
                    .padding(.horizontal, 20).padding(.top, 8)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .accessibilityAddTraits(.updatesFrequently)
            }
            Spacer()
        }
        .animation(.spring(response: 0.45, dampingFraction: 0.7), value: store.toast)
        .allowsHitTesting(false)
    }
}

/// Floating tab bar: Home | + | Memories
struct TabBar: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.paw) var pc
    let current: Route
    var body: some View {
        ZStack(alignment: .top) {
            HStack(spacing: 0) {
                tab("home", "Home", current == .home) { store.tab(.home) }
                Spacer().frame(width: 84)
                tab("album", "Memories", current == .memories) { store.tab(.memories) }
            }
            .frame(height: 68)
            .background(Capsule().fill(pc.surface.opacity(0.94)).shadow(color: .black.opacity(0.18), radius: 10, y: 4))
            Button { store.push(.flow(nil)) } label: {
                PawIcon(name: "plus", color: pc.onKibble, size: 30).frame(width: 66, height: 66)
                    .background(Circle().fill(pc.kibble).shadow(color: .black.opacity(0.25), radius: 8, y: 4))
            }.buttonStyle(.plain).offset(y: -16).accessibilityLabel("New countdown")
        }
        .padding(.horizontal, 18).padding(.bottom, 8).padding(.top, 20)
    }
    private func tab(_ icon: String, _ label: String, _ on: Bool, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 1) {
                PawIcon(name: icon, color: on ? pc.kibble : pc.ink2, size: 26)
                Text(label).font(PawFont.body(12, 800)).foregroundColor(on ? pc.ink : pc.ink2)
                Circle().fill(on ? pc.kibble : .clear).frame(width: 5, height: 5)
            }.frame(maxWidth: .infinity, maxHeight: .infinity)
        }.buttonStyle(.plain).accessibilityAddTraits(on ? [.isSelected] : [])
    }
}

/* ---------------- rolling numerals ---------------- */

private struct RollingDigit: View {
    let digit: Int; let size: CGFloat; let color: Color
    var body: some View {
        let line = size * 1.1
        VStack(spacing: 0) {
            ForEach(0..<10, id: \.self) { i in Text("\(i)").font(PawFont.display(size, 600)).foregroundColor(color).frame(width: size * 0.62, height: line) }
        }
        .offset(y: -CGFloat(digit) * line + line * 4.5)       // centre the visible digit in a 1-line window
        .frame(width: size * 0.62, height: line, alignment: .center).clipped()
        .animation(.spring(response: 0.6, dampingFraction: 0.65), value: digit)
    }
}

enum NumSize { case hero, detail }

struct Numerals: View {
    @Environment(\.paw) var pc
    let c: Countdown; let k: Computed; let size: NumSize; let accent: Color
    var body: some View {
        if k.phase == .past || (k.phase == .today && k.remaining <= 0) {
            H(k.phase == .past ? "It happened! 💛" : "It's today! 🎉", size == .hero ? 30 : 40, weight: 700, align: .center).frame(maxWidth: .infinity).padding(.vertical, 8)
        } else {
            let us = units(k, c.displayMode)
            let full = c.displayMode == .full
            let fs: CGFloat = full ? (size == .hero ? 34 : 50) : (c.displayMode == .weeks ? (size == .hero ? 50 : 72) : (size == .hero ? 62 : 104))
            HStack(alignment: .top, spacing: 8) {
                ForEach(us, id: \.key) { u in
                    let text = String(format: "%0\(u.pad)d", u.value)
                    VStack(spacing: 2) {
                        HStack(spacing: 0) { ForEach(Array(text.enumerated()), id: \.offset) { _, ch in RollingDigit(digit: Int(String(ch)) ?? 0, size: fs, color: pc.ink) } }
                        Text(u.label.uppercased()).font(PawFont.body(full ? 12 : 15, 800)).tracking(1).foregroundColor(pc.ink2)
                    }
                    .frame(maxWidth: full ? .infinity : nil)
                    .padding(.top, full ? 10 : 0).padding(.bottom, full ? 8 : 0).padding(.horizontal, full ? 0 : 12)
                    .background(full ? AnyView(RoundedRectangle(cornerRadius: size == .hero ? 16 : 20).fill(pc.surface.mixed(accent, 0.15))) : AnyView(EmptyView()))
                }
            }
            .frame(maxWidth: .infinity)
            .accessibilityElement(children: .ignore).accessibilityLabel(spoken(k) + " left")
        }
    }
}

/* ---------------- leash progress ---------------- */

struct Leash: View {
    @Environment(\.paw) var pc
    let c: Countdown; let k: Computed; let accent: Color
    var body: some View {
        let pct = k.progress
        let label = k.phase == .upcoming ? "Day \(k.daysIn) of \(k.daysTotal)" : (k.phase == .today ? "Today is the day!" : "Made it!")
        VStack(spacing: 6) {
            HStack(spacing: 10) {
                GeometryReader { g in
                    ZStack(alignment: .leading) {
                        RoundedRectangle(cornerRadius: 6).fill(pc.ink.opacity(0.09)).frame(height: 12)
                        RoundedRectangle(cornerRadius: 6).fill(LinearGradient(colors: [accent, accent, accent.mixed(.black, 0.24), accent.mixed(.black, 0.24)], startPoint: .topLeading, endPoint: .bottomTrailing))
                            .frame(width: max(12, g.size.width * pct), height: 12)
                        PawIcon(name: "paw", color: accent.mixed(.black, 0.18), size: 19).frame(width: 34, height: 34)
                            .background(Circle().fill(pc.surface).shadow(color: .black.opacity(0.22), radius: 4, y: 2))
                            .offset(x: (g.size.width - 34) * pct)
                    }.frame(height: 34)
                }.frame(height: 34)
                Text(typeEmoji(c.type)).font(.system(size: 24))
            }
            HStack { Muted(label, 13); Spacer(); Muted("\(Int((pct * 100).rounded()))%", 13) }
        }
        .animation(.easeOut(duration: 1), value: pct)
        .accessibilityElement(children: .ignore).accessibilityLabel("Countdown progress \(Int((pct * 100).rounded())) percent")
    }
}

func typeEmoji(_ type: String) -> String {
    switch type { case "intl_trip": return "✈️"; case "vacation": return "🏝️"; case "birthday": return "🎂"; case "anniversary": return "💍"; case "holiday": return "🎄"; default: return "⭐" }
}

/* ---------------- confetti ---------------- */

struct Bit { var x: Double, y: Double, vx: Double, vy: Double, rot: Double, vr: Double, color: Color, size: Double, life: Double, shape: Int }

/// Full-screen confetti. Each time `burst` changes a new shower pops from the upper middle of the screen.
struct ConfettiOverlay: View {
    let burst: Int; let reduceMotion: Bool
    @State var bits: [Bit] = []
    @State var lastBurst = 0
    @State var last = Date()
    private let colors: [Color] = [Color(hex: "#FF7DAA"), Color(hex: "#FFD35C"), Color(hex: "#5DADE8"), Color(hex: "#D4E157"), Color(hex: "#E8A15C"), Color(hex: "#F6B7B0"), Color(hex: "#E5574F"), .white]
    var body: some View {
        GeometryReader { geo in
            TimelineView(.animation(paused: bits.isEmpty)) { tl in
                Canvas { ctx, size in
                    for b in bits {
                        var c = ctx
                        c.translateBy(x: b.x, y: b.y); c.rotate(by: .radians(b.rot))
                        let col = b.color.opacity(min(1, b.life / 30))
                        if b.shape == 1 { c.fill(Path(ellipseIn: CGRect(x: -b.size / 2.4, y: -b.size / 2.4, width: b.size / 1.2, height: b.size / 1.2)), with: .color(col)) }
                        else { c.fill(Path(CGRect(x: -b.size / 2, y: -b.size / 4, width: b.size, height: b.size / 2)), with: .color(col)) }
                    }
                }
                .onChange(of: tl.date) { now in step(now, geo.size) }
            }
            .onChange(of: burst) { _ in if burst != lastBurst { lastBurst = burst; spawn(geo.size) } }
        }
        .allowsHitTesting(false).accessibilityHidden(true)
    }
    private func spawn(_ size: CGSize) {
        if reduceMotion { return }
        last = Date()
        for i in 0..<110 {
            let a = Double.random(in: 0..<(2 * .pi)), v = Double.random(in: 2...9) * 1.4
            bits.append(Bit(x: size.width / 2, y: size.height * 0.32, vx: cos(a) * v, vy: sin(a) * v - 5, rot: Double.random(in: 0..<6), vr: Double.random(in: -0.2...0.2),
                            color: colors[i % colors.count], size: Double.random(in: 6...14), life: Double.random(in: 140...220), shape: Int.random(in: 0..<3)))
        }
    }
    private func step(_ now: Date, _ size: CGSize) {
        let dt = min(3, max(0.2, now.timeIntervalSince(last) * 60)); last = now
        bits = bits.compactMap { b in
            var b = b
            b.vy += 0.16 * 1.4 * dt; b.vx *= 0.992; b.x += b.vx * dt; b.y += b.vy * dt; b.rot += b.vr * dt; b.life -= dt
            return b.life <= 0 || b.y > size.height + 40 ? nil : b
        }
    }
}

/// edge swipe to go back, like a normal iPhone app
struct SwipeBack: ViewModifier {
    @EnvironmentObject var store: AppStore
    func body(content: Content) -> some View {
        content.simultaneousGesture(DragGesture(minimumDistance: 24).onEnded { v in
            if v.startLocation.x < 28 && v.translation.width > 90 && abs(v.translation.height) < 80 { store.pop() }
        })
    }
}
extension View { func swipeBack() -> some View { modifier(SwipeBack()) } }

/// ticks once a second so countdowns count down
struct Ticker<Content: View>: View {
    @ViewBuilder var content: (Date) -> Content
    var body: some View { TimelineView(.periodic(from: .now, by: 1)) { tl in content(tl.date) } }
}
