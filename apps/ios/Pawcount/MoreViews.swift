import SwiftUI
import UniformTypeIdentifiers

/* MoreViews.swift - Welcome (3 pages), Memories and Settings. */

/// just the dog (no scenery), animated; used on the welcome pages
struct DogOnly: View {
    @EnvironmentObject var store: AppStore
    let dog: Dog; var stage: Stage = .waiting; var bell = false
    var body: some View {
        let node = store.art.dog(dog.variant, stage, "custom", bell: bell)
        let palette = dogPalette(store.art.meta, dog, accent: parseHex("#E8A15C"))
        let anims = store.art.anim.dogAnims(stage)
        let reduce = store.reduceMotion
        TimelineView(.animation(paused: reduce)) { tl in
            Canvas { ctx, sz in
                var c = ctx
                let t = tl.date.timeIntervalSinceReferenceDate - clockEpoch
                if let n = node { drawDog(&c, n, left: 0, top: 0, size: min(sz.width, sz.height), DrawState(palette: palette, time: t, stage: stage, anims: anims, reduceMotion: reduce)) }
            }
        }.aspectRatio(1, contentMode: .fit).accessibilityHidden(true)
    }
}

/* ------------------------------ Welcome ------------------------------ */

struct WelcomeView: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.paw) var pc
    @State var page = 0

    func mutt(_ b: String) -> Dog { Dog(breed: b, name: store.art.meta.breeds[b]?.name ?? "", furColor: b == "mutt" ? "#B98A62" : nil, ears: b == "mutt" ? "floppy" : nil) }

    var body: some View {
        let meta = store.art.meta
        VStack(spacing: 0) {
            HStack {
                HStack(spacing: 8) { ForEach(0..<3, id: \.self) { PawIcon(name: "paw", color: $0 <= page ? pc.kibble : pc.ink.opacity(0.2), size: 22) } }
                Spacer()
                if page < 2 { Button { page = 2 } label: { Text("Skip").font(PawFont.body(16, 800)).foregroundColor(pc.ink2).padding(10) }.buttonStyle(.plain) }
            }.frame(minHeight: 48).padding(.horizontal, 22).padding(.top, 8)

            ScrollView(showsIndicators: false) {
                VStack(spacing: 0) {
                    switch page {
                    case 0:
                        HStack(alignment: .bottom, spacing: 0) {
                            ForEach(Array(meta.breedOrder.enumerated()), id: \.offset) { i, b in
                                DogOnly(dog: mutt(b), stage: i % 3 == 0 ? .packing : (i % 2 == 0 ? .waiting : .curious)).frame(maxWidth: .infinity).padding(.bottom, i % 2 == 0 ? 22 : 0)
                            }
                        }.padding(.bottom, 24).padding(.top, 40)
                        H("Countdowns with a dog who can't wait either.", 38, align: .center)
                        B("Every trip, birthday and big day gets a companion that gets more excited as it gets closer.", 17, weight: 700, align: .center, color: pc.ink2).padding(.top, 12)
                    case 1:
                        H("Pick your first companion", 31, align: .center).padding(.top, 20)
                        B("Every breed has its own personality. You can mix and match later.", 16.5, weight: 700, align: .center, color: pc.ink2).padding(.top, 10)
                        let rows = stride(from: 0, to: meta.breedOrder.count, by: 2).map { Array(meta.breedOrder[$0..<min($0 + 2, meta.breedOrder.count)]) }
                        VStack(spacing: 12) {
                            ForEach(rows, id: \.self) { row in
                                HStack(spacing: 12) {
                                    ForEach(row, id: \.self) { b in
                                        let br = meta.breeds[b]!, on = store.welcomeBreed == b && store.welcomeBreedChosen
                                        Button { store.welcomeBreed = b; store.welcomeBreedChosen = true; Haptics.play(store.settings.haptics, "tick") } label: {
                                            VStack(spacing: 2) {
                                                DogOnly(dog: mutt(b), stage: on ? .curious : .waiting).frame(maxWidth: 110)
                                                H(br.name, 18); Muted(br.label, 13); B(br.vibe, 13, weight: 700, align: .center)
                                            }.padding(.horizontal, 10).padding(.top, 10).padding(.bottom, 14).frame(maxWidth: .infinity)
                                            .background(RoundedRectangle(cornerRadius: 24).fill(pc.surface).shadow(color: .black.opacity(on ? 0.16 : 0.08), radius: on ? 8 : 4, y: 2))
                                            .overlay(RoundedRectangle(cornerRadius: 24).stroke(on ? pc.kibble : .clear, lineWidth: 3))
                                        }.buttonStyle(.plain).accessibilityAddTraits(on ? [.isSelected] : [])
                                    }
                                }
                            }
                        }.padding(.top, 18)
                    default:
                        let b = store.welcomeBreed
                        DogOnly(dog: mutt(b), stage: .waiting, bell: true).frame(width: 230).padding(.top, 30)
                        H("\(meta.breeds[b]?.name ?? "") will ring the bell", 31, align: .center).padding(.top, 8)
                        B("Allow reminders so \(meta.breeds[b]?.name ?? "your dog") can tap you on the shoulder at 100, 50, 30, 7 and 1 days — and the morning of. One a day, tops.", 16, weight: 700, align: .center, color: pc.ink2).padding(.top, 12)
                    }
                }.padding(.horizontal, 22).frame(maxWidth: .infinity)
            }

            VStack(spacing: 8) {
                switch page {
                case 0:
                    PrimaryButton("Let's go") { page = 1 }
                    GhostButton("Peek at sample adventures") { store.loadSamples(); store.goHome() }
                case 1:
                    PrimaryButton("Choose \(meta.breeds[store.welcomeBreed]?.name ?? "")") { store.welcomeBreedChosen = true; page = 2 }
                default:
                    PrimaryButton("Allow reminders") {
                        Sounds.play(store.settings.sound, "jingle")
                        Reminders.request { ok in store.setSettings { $0.notifications = ok }; finish() }
                    }
                    GhostButton("Maybe later") { finish() }
                }
            }.padding(.horizontal, 22).padding(.bottom, 8).padding(.top, 8)
        }
        .background(LinearGradient(colors: [pc.kibble.opacity(0.28), pc.bg], startPoint: .top, endPoint: .init(x: 0.5, y: 0.4)).ignoresSafeArea())
        .animation(.easeInOut(duration: 0.3), value: page)
    }

    func finish() { store.update { $0.onboarded = true }; store.stack = [.home, .flow(nil)] }
}

/* ------------------------------ Memories ------------------------------ */

struct MemoriesView: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.paw) var pc
    var body: some View {
        Ticker { now in
            let items = store.data.countdowns.map { ($0, compute($0, now: now)) }.filter { $0.1.phase == .past || $0.0.archived }.sorted { $0.1.target > $1.1.target }
            let year = Calendar.current.component(.year, from: now)
            let thisYear = items.filter { Calendar.current.component(.year, from: $0.1.target) == year }.count
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 0) {
                    HStack { VStack(alignment: .leading, spacing: 2) { Eyebrow("Keepsakes"); H("Memories", 32) }; Spacer(); IconButton(icon: "sliders", label: "Settings") { store.push(.settings) } }
                    if items.isEmpty {
                        VStack(spacing: 8) {
                            DogOnly(dog: Dog(breed: "dachshund", name: "Noodle"), stage: .memory).frame(maxWidth: 260).padding(.top, 28)
                            H("No memories yet", 26, align: .center)
                            Muted("When a countdown ends, it lands here as a keepsake — with your photos and a very proud dog.", 16, align: .center)
                        }.frame(maxWidth: .infinity)
                    } else {
                        HStack(spacing: 16) {
                            Text("\(items.count)").font(PawFont.display(56, 700)).foregroundColor(Color(hex: "#3D2616"))
                            Text("\(items.count == 1 ? "adventure" : "adventures") counted down together\(thisYear > 0 ? " · \(thisYear) in \(year)" : "").").font(PawFont.body(16, 700)).foregroundColor(Color(hex: "#3D2616"))
                        }
                        .padding(.horizontal, 20).padding(.vertical, 18).frame(maxWidth: .infinity, alignment: .leading)
                        .background(RoundedRectangle(cornerRadius: 28).fill(LinearGradient(colors: [Color(hex: "#FFE3B0"), Color(hex: "#F9C4B8")], startPoint: .topLeading, endPoint: .bottomTrailing)))
                        .padding(.top, 18)
                        let rows = stride(from: 0, to: items.count, by: 2).map { Array(items[$0..<min($0 + 2, items.count)]) }
                        VStack(spacing: 22) {
                            ForEach(Array(rows.enumerated()), id: \.offset) { r, row in
                                HStack(spacing: 16) {
                                    ForEach(Array(row.enumerated()), id: \.element.0.id) { i, it in polaroid(it.0, it.1, tilt: (r * 2 + i) % 2 == 0 ? -1.6 : 1.6) }
                                    if row.count == 1 { Color.clear.frame(maxWidth: .infinity) }
                                }
                            }
                        }.padding(.top, 24).padding(.horizontal, 4)
                    }
                }.padding(.horizontal, 20).padding(.top, 18).padding(.bottom, 120)
            }
        }
    }

    func polaroid(_ c: Countdown, _ k: Computed, tilt: Double) -> some View {
        Button { store.push(.detail(c.id)) } label: {
            VStack(alignment: .leading, spacing: 10) {
                Group {
                    if let p = c.photos.first, let img = Photos.image(p) { Image(uiImage: img).resizable().scaledToFill() }
                    else { SceneView(c: c, k: k, art: store.art, size: .card, stage: .memory, reduceMotion: store.reduceMotion) }
                }.aspectRatio(1, contentMode: .fit).frame(maxWidth: .infinity).clipShape(RoundedRectangle(cornerRadius: 4))
                VStack(alignment: .leading, spacing: 0) {
                    Text(c.title).font(PawFont.display(17)).foregroundColor(Color(hex: "#3D2616")).lineLimit(1)
                    Text("\(typeEmoji(c.type)) \(fmtInstant(k.target, k.zone, allDay: true))\(c.archived ? " · archived" : "")").font(PawFont.body(13, 700)).foregroundColor(Color(hex: "#765039")).lineLimit(1)
                }
            }
            .padding(EdgeInsets(top: 9, leading: 9, bottom: 14, trailing: 9)).background(RoundedRectangle(cornerRadius: 8).fill(.white).shadow(color: .black.opacity(0.18), radius: 8, y: 4))
            .rotationEffect(.degrees(tilt))
        }.buttonStyle(.plain).frame(maxWidth: .infinity).accessibilityLabel("\(c.title), \(fmtInstant(k.target, k.zone, allDay: true))")
    }
}

/* ------------------------------ Settings ------------------------------ */

struct SettingsView: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.paw) var pc
    @State var confirmReset = false
    @State var importing = false

    var body: some View {
        let s = store.settings
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                HStack { IconButton(icon: "back", label: "Back") { store.pop() }; Spacer(); H("Settings", 22); Spacer(); Color.clear.frame(width: 48, height: 48) }
                HStack(spacing: 14) {
                    DogIcon(dog: Dog(breed: "golden", name: "Sunny")).frame(width: 64, height: 64)
                    VStack(alignment: .leading, spacing: 2) { B("Pawcount", 17, weight: 800); Muted("Every day closer is another tail wag.") }
                }.padding(.top, 12)

                group("Look & feel")
                Panel(flush: true) {
                    col("Default display") { Seg(options: DisplayMode.allCases.map { ($0.rawValue, $0.label) }, selected: s.displayMode.rawValue, label: "Default display mode") { m in store.setSettings { $0.displayMode = DisplayMode(rawValue: m) ?? .full } } }
                    Divider2()
                    col("Theme") { Seg(options: [("system", "System"), ("light", "Light"), ("dark", "Dark")], selected: s.theme, label: "Theme") { m in store.setSettings { $0.theme = m } } }
                    Divider2()
                    col("Reduce motion", sub: "Swaps loops for still poses. The dog keeps their expression.") { Seg(options: [("system", "System"), ("on", "On"), ("off", "Off")], selected: s.reduceMotion, label: "Reduce motion") { m in store.setSettings { $0.reduceMotion = m } } }
                }

                group("Feedback")
                Panel(flush: true) {
                    row("Haptics", "Taps, purrs and milestone bursts", s.haptics) { on in store.setSettings { $0.haptics = on }; if on { Haptics.play(true, "purr") } }
                    Divider2()
                    row("Sounds", "Soft barks & chimes · off by default", s.sound) { on in store.setSettings { $0.sound = on }; Sounds.play(on, "bark") }
                    Divider2()
                    row("Reminders", "Milestones & the morning of", s.notifications) { on in
                        if !on { store.setSettings { $0.notifications = false } }
                        else { Reminders.request { ok in store.setSettings { $0.notifications = ok }; store.say(ok ? "Reminders are on 🔔" : "Reminders are blocked in Settings › Notifications") } }
                    }
                }

                group("Your data")
                Panel(flush: true) {
                    Muted("Everything stays on this phone. No account, no tracking, no ads.", 14).padding(.vertical, 14)
                    if let url = store.exportJSON() { ShareLink(item: url) { linkRow("download", "Export backup") } }
                    link("upload", "Import backup") { importing = true }
                    link("paw", "Load sample adventures") { store.loadSamples(); store.say("Seven sample adventures added 🐾") }
                    if store.data.countdowns.contains(where: { $0.sample }) { link("trash", "Remove sample adventures") { store.update { $0.countdowns.removeAll { $0.sample } }; store.say("Samples removed") } }
                    link("trash", "Erase everything", danger: true) { confirmReset = true }
                }

                HStack(spacing: 14) {
                    HStack(spacing: -14) { DogIcon(dog: Dog(breed: "corgi", name: "")).frame(width: 46, height: 46); DogIcon(dog: Dog(breed: "dachshund", name: "")).frame(width: 46, height: 46) }
                    VStack(alignment: .leading, spacing: 2) { B("For two · coming in v1.1", 16, weight: 800); Muted("Share a countdown with your person. Both phones stay in sync and you'll see who petted the dog today.", 13) }
                }.padding(16).overlay(RoundedRectangle(cornerRadius: 28).stroke(pc.line, lineWidth: 2)).padding(.top, 26)
                Muted("Pawcount · v1.0", 13, align: .center).frame(maxWidth: .infinity).padding(.top, 26)
            }.padding(.horizontal, 20).padding(.top, 18).padding(.bottom, 40)
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.json, .data]) { res in
            if case .success(let url) = res {
                let ok = url.startAccessingSecurityScopedResource(); defer { if ok { url.stopAccessingSecurityScopedResource() } }
                if let d = try? Data(contentsOf: url), store.importJSON(d) { store.say("Backup restored 🐾") } else { store.say("That file isn’t a Pawcount backup") }
            }
        }
        .sheet(isPresented: $confirmReset) {
            VStack(spacing: 10) {
                H("Erase everything?", 22, align: .center)
                Muted("All countdowns, memories and settings on this phone will be removed.", 15, align: .center)
                PrimaryButton("Erase everything", accent: pc.collar) { confirmReset = false; store.update { $0 = AppData() }; store.stack = [.welcome] }.padding(.top, 8)
                GhostButton("Cancel") { confirmReset = false }
                Spacer(minLength: 0)
            }.padding(20).background(pc.bg.ignoresSafeArea()).presentationDetents([.height(300)])
        }
        .swipeBack()
    }

    func group(_ t: String) -> some View { Text(t.uppercased()).font(PawFont.body(13, 800)).tracking(1).foregroundColor(pc.ink2).padding(.leading, 4).padding(.top, 26) }
    func col<C: View>(_ title: String, sub: String? = nil, @ViewBuilder content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 8) { VStack(alignment: .leading, spacing: 1) { B(title, 16, weight: 800); if let s = sub { Muted(s, 13) } }; content() }.padding(.vertical, 14).frame(maxWidth: .infinity, alignment: .leading)
    }
    func row(_ title: String, _ sub: String, _ on: Bool, _ change: @escaping (Bool) -> Void) -> some View {
        HStack { VStack(alignment: .leading, spacing: 1) { B(title, 16, weight: 800); Muted(sub, 13) }; Spacer(); PawSwitch(on: on, label: title, onChange: change) }.frame(minHeight: 68)
    }
    func linkRow(_ icon: String, _ text: String, danger: Bool = false) -> some View {
        VStack(spacing: 0) {
            Divider2()
            HStack(spacing: 12) { PawIcon(name: icon, color: danger ? pc.collar : pc.ink, size: 22); Text(text).font(PawFont.body(16, 800)).foregroundColor(danger ? pc.collar : pc.ink); Spacer(); PawIcon(name: "chevron", color: pc.ink2, size: 18) }.frame(minHeight: 56)
        }.contentShape(Rectangle())
    }
    func link(_ icon: String, _ text: String, danger: Bool = false, _ action: @escaping () -> Void) -> some View { Button(action: action) { linkRow(icon, text, danger: danger) }.buttonStyle(.plain) }
}
