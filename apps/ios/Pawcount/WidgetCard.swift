import SwiftUI

/*
 WidgetCard.swift - the picture of a countdown used by the home-screen widget and by the "share picture card" feature.
 It is a still (no animation clock): the scene and dog are drawn once with the app's own renderer, with the title and the
 time left underneath. Shared by the app and the widget extension so both look the same.
*/

struct StillScene: View {
    let c: Countdown; let k: Computed; let art: Art
    var body: some View {
        let stage = k.stage
        let palette = dogPalette(art.meta, c.dog, accent: parseHex(c.accent))
        let scene = art.scene(c.type, door: stage == .waiting || stage == .packing)
        let dog = art.dog(c.dog.variant, stage, c.type)
        let anims = art.anim.dogAnims(stage)
        Canvas { ctx, sz in
            var cx = ctx
            if let s = scene { drawScene(&cx, s, size: sz, DrawState(palette: palette, time: 1.2, stage: stage, anims: art.anim.sceneAll, reduceMotion: true)) }
            let ds = sz.height * 0.8
            if let d = dog { drawDog(&cx, d, left: (sz.width - ds) / 2, top: sz.height - sz.height * 0.04 - ds, size: ds, DrawState(palette: palette, time: 1.2, stage: stage, anims: anims, reduceMotion: true)) }
        }
    }
}

/// text for the big number ("52 days", "Today! 🎉", "3h 20m")
func bigCount(_ c: Countdown, _ k: Computed) -> String {
    if k.phase == .today { return "Today! 🎉" }
    if k.phase == .past { return plural(k.daysSince, "day") + " ago" }
    switch c.displayMode {
    case .sleeps: return plural(k.sleeps, "sleep")
    case .weeks where k.weeks > 0: return "\(k.weeks)w \(k.weekDays)d"
    default: return k.parts.d == 0 ? "\(k.parts.h)h \(k.parts.m)m" : plural(k.daysCeil, "day")
    }
}

/// the card: scene on top, title + count below. `wide` puts the text beside the picture (medium widget)
struct WidgetCard: View {
    let c: Countdown?; let art: Art; var wide = false; var now = Date()
    var body: some View {
        let demo = c ?? Countdown(id: "w", title: "Add a countdown", type: "custom", targetAt: "2030-01-01T00:00", timeZone: "UTC", allDay: false, yearly: false,
                                  createdAt: Date().addingTimeInterval(-DAY), dog: Dog(breed: "golden", name: "Sunny"), accent: "#E8A15C", displayMode: .full)
        var k = compute(demo, now: now)
        if c == nil { k.stage = .nap }
        let accent = Color(hex: demo.accent)
        let ink = Color(hex: "#3D2616")
        Group {
            if wide {
                HStack(spacing: 0) {
                    StillScene(c: demo, k: k, art: art).frame(maxWidth: .infinity).clipped().layoutPriority(0.9)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(demo.title).font(PawFont.display(17)).foregroundColor(ink).lineLimit(2).minimumScaleFactor(0.7)
                        Text(c == nil ? "Tap to start" : bigCount(demo, k)).font(PawFont.display(30, 700)).foregroundColor(accent.luminanceIsHigh ? ink : accent).minimumScaleFactor(0.5).lineLimit(1)
                        if c != nil { Text(stageLabel(demo, k)).font(PawFont.body(12, 800)).foregroundColor(Color(hex: "#765039")) }
                    }.padding(12).frame(maxWidth: .infinity, alignment: .leading)
                }
            } else {
                VStack(spacing: 0) {
                    StillScene(c: demo, k: k, art: art).frame(maxHeight: .infinity).clipped()
                    VStack(alignment: .leading, spacing: 0) {
                        Text(demo.title).font(PawFont.display(14)).foregroundColor(ink).lineLimit(1).minimumScaleFactor(0.7)
                        Text(c == nil ? "Tap to start" : bigCount(demo, k)).font(PawFont.display(24, 700)).foregroundColor(accent.luminanceIsHigh ? ink : accent).lineLimit(1).minimumScaleFactor(0.5)
                    }.padding(.horizontal, 12).padding(.vertical, 8).frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }.background(Color(hex: "#FFF8EE"))
    }
}

extension Color {
    var luminanceIsHigh: Bool { let (r, g, b, _) = UIColor(self).rgba; return 0.2126 * r + 0.7152 * g + 0.0722 * b > 0.6 }
}

/// the 1080x1350 picture people share
struct ShareCard: View {
    let c: Countdown; let art: Art
    var body: some View {
        let k = compute(c)
        let accent = Color(hex: c.accent), ink = Color(hex: "#3D2616")
        VStack(spacing: 0) {
            StillScene(c: c, k: k, art: art).frame(width: 360, height: 273).clipped()
            VStack(spacing: 6) {
                Text(c.title).font(PawFont.display(28)).foregroundColor(ink).lineLimit(1).minimumScaleFactor(0.6)
                Text(bigCount(c, k)).font(PawFont.display(50, 700)).foregroundColor(accent.luminanceIsHigh ? ink : accent)
                Text(fmtInstant(k.target, k.zone, allDay: c.allDay)).font(PawFont.body(14, 800)).foregroundColor(Color(hex: "#765039"))
                Spacer(minLength: 0)
                Text("🐾 Pawcount").font(PawFont.display(15)).foregroundColor(Color(hex: "#E8A15C")).padding(.bottom, 14)
            }.padding(.top, 18).frame(width: 360, height: 177).background(Color(hex: "#FFF8EE"))
        }.frame(width: 360, height: 450).background(Color(hex: "#FFF8EE"))
    }
}
