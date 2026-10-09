import SwiftUI

/* HomeView.swift - greeting, the hero card for the soonest countdown, and a two-column grid of the rest. */

struct HomeView: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.paw) var pc
    @Environment(\.pawReduceMotion) var reduce
    @State var sort = "soonest"

    var body: some View {
        Ticker { now in content(now) }
    }

    /// everything on the screen for one tick of the clock (a normal function so it can use plain Swift statements)
    func content(_ now: Date) -> some View {
            let all = store.data.countdowns.filter { !$0.archived }.map { ($0, compute($0, now: now)) }
            let items = all.filter { $0.1.phase != .past }.sorted { a, b in
                let ra = a.1.phase == .today ? 0 : 1, rb = b.1.phase == .today ? 0 : 1
                return ra != rb ? ra < rb : a.1.target < b.1.target
            }
            let pastCount = store.data.countdowns.filter { compute($0, now: now).phase == .past }.count
            var rest = Array(items.dropFirst())
            if sort == "type" {
                let order = store.art.meta.typeOrder
                rest.sort { a, b in
                    let ia = order.firstIndex(of: a.0.type) ?? 0, ib = order.firstIndex(of: b.0.type) ?? 0
                    return ia != ib ? ia < ib : a.1.target < b.1.target
                }
            }
            return ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(alignment: .center) {
                        VStack(alignment: .leading, spacing: 2) {
                            Eyebrow(dateLine(now))
                            H(greeting(now) + (store.settings.profileName.isEmpty ? "" : ", \(store.settings.profileName)"), 32)
                            if !items.isEmpty { Muted(items.count == 1 ? "1 adventure on the way" : "\(items.count) adventures on the way", 16) }
                        }
                        Spacer()
                        Button { store.push(.settings) } label: { Avatar(size: 46) }.buttonStyle(.plain).accessibilityLabel("Your profile")
                        IconButton(icon: "sliders", label: "Settings") { store.push(.settings) }
                    }
                    .padding(.bottom, 18)
                    if let hero = items.first {
                        HeroCard(c: hero.0, k: hero.1)
                        if !rest.isEmpty {
                            HStack {
                                H("Coming up", 22); Spacer()
                                Seg(options: [("soonest", "Soonest"), ("type", "By type")], selected: sort, label: "Sort countdowns") { sort = $0 }.frame(width: 190)
                            }.padding(.top, 24).padding(.bottom, 12)
                            let rows = stride(from: 0, to: rest.count, by: 2).map { Array(rest[$0..<min($0 + 2, rest.count)]) }
                            VStack(spacing: 14) {
                                ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                                    HStack(spacing: 14) {
                                        ForEach(row, id: \.0.id) { c, k in GridCard(c: c, k: k) }
                                        if row.count == 1 { Color.clear.frame(maxWidth: .infinity) }
                                    }
                                }
                            }
                        }
                        Muted("Tap a card to meet the dog", 13, align: .center).frame(maxWidth: .infinity).padding(.top, 20)
                    } else {
                        EmptyHome(pastCount: pastCount)
                    }
                }
                .padding(.horizontal, 20).padding(.top, 18).padding(.bottom, 120)
            }
    }

    private func dateLine(_ d: Date) -> String { let f = DateFormatter(); f.setLocalizedDateFormatFromTemplate("EEEEMMMMd"); return f.string(from: d) }
    private func greeting(_ d: Date) -> String {
        let h = Calendar.current.component(.hour, from: d)
        return h < 5 ? "Late night, huh?" : h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening"
    }
}

private struct EmptyHome: View {
    @EnvironmentObject var store: AppStore
    let pastCount: Int
    var body: some View {
        let demo = Countdown(id: "empty", title: "", type: "custom", targetAt: "2030-01-01T00:00", timeZone: "UTC", allDay: false, yearly: false, createdAt: Date().addingTimeInterval(-DAY),
                             dog: Dog(breed: "scott", name: "Scott"), accent: "#E8A15C", displayMode: .full)
        VStack(spacing: 8) {
            SceneView(c: demo, k: compute(demo), art: store.art, size: .hero, stage: .nap, reduceMotion: store.reduceMotion)
                .clipShape(RoundedRectangle(cornerRadius: 34)).shadow(color: .black.opacity(0.15), radius: 10, y: 4)
            H("Nothing to wait for… yet", 26, align: .center).padding(.top, 14)
            Muted((pastCount > 0 ? "Your past adventures live in Memories. " : "") + "Start a countdown and Scott will do the rest.", 16, align: .center)
            VStack(spacing: 10) {
                PrimaryButton("New countdown", icon: "plus") { store.push(.flow(nil)) }
                GhostButton("Peek at sample adventures") { store.loadSamples(); store.say("Seven sample adventures added 🐾") }
            }.padding(.top, 10)
        }
    }
}

private struct HeroCard: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.paw) var pc
    let c: Countdown; let k: Computed
    var body: some View {
        let accent = Color(hex: c.accent)
        let type = store.art.meta.types[c.type]!
        let w = whenText(c, k)
        Button { store.push(.detail(c.id)) } label: {
            VStack(alignment: .leading, spacing: 0) {
                SceneView(c: c, k: k, art: store.art, size: .hero, reduceMotion: store.reduceMotion)
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 6) {
                        Chip(text: stageLabel(c, k), filled: accent, textColor: accent.onColor, icon: k.stage.icon)
                        Chip(text: type.short, icon: type.icon)
                    }
                    H(c.title, 28).padding(.top, 10)
                    Muted(k.phase == .today ? "Happening today" : w.0, 14)
                    Numerals(c: c, k: k, size: .hero, accent: accent).padding(.top, 12)
                    Leash(c: c, k: k, accent: accent).padding(.top, 14)
                }.padding(.horizontal, 18).padding(.top, 16).padding(.bottom, 18)
            }
            .background(pc.surface)
            .clipShape(RoundedRectangle(cornerRadius: 34))
            .shadow(color: .black.opacity(0.14), radius: 12, y: 5)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(c.title). \(spoken(k)) left. \(describeDog(c, k))").accessibilityAddTraits(.isButton)
    }
}

private struct GridCard: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.paw) var pc
    let c: Countdown; let k: Computed
    var body: some View {
        let accent = Color(hex: c.accent)
        Button { store.push(.detail(c.id)) } label: {
            VStack(alignment: .leading, spacing: 0) {
                SceneView(c: c, k: k, art: store.art, size: .card, reduceMotion: store.reduceMotion)
                VStack(alignment: .leading, spacing: 3) {
                    H(c.title, 17).lineLimit(1)
                    H(shortCount(c, k), 22).padding(.bottom, 4)
                    Chip(text: stageLabel(c, k), filled: accent, textColor: accent.onColor, small: true, icon: k.stage.icon)
                }.padding(.horizontal, 13).padding(.top, 11).padding(.bottom, 14).frame(maxWidth: .infinity, alignment: .leading)
            }
            .background(pc.surface)
            .clipShape(RoundedRectangle(cornerRadius: 28))
            .shadow(color: .black.opacity(0.1), radius: 6, y: 3)
        }
        .buttonStyle(.plain).frame(maxWidth: .infinity)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(c.title). \(shortCount(c, k)). \(stageSentence(c, k))").accessibilityAddTraits(.isButton)
    }
}
