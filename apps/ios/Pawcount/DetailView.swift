import SwiftUI
import PhotosUI

/*
 DetailView.swift - one countdown full screen: the interactive scene (tap = bark, hold = pet, shake = sneeze),
 big rolling numerals, the leash progress bar, then the details below (when, checklist, notes, reminders...).
*/

struct DetailView: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.paw) var pc
    let id: String
    @StateObject var ctrl = SceneController()
    @State var menu = false
    @State var confirmDelete = false
    @State var sharing = false
    @State var shake = ShakeDetector()

    var body: some View {
        if let c = store.countdown(id) {
            content(c)
        } else {
            Color.clear.onAppear { store.pop() }
        }
    }

    @ViewBuilder
    func content(_ c: Countdown) -> some View {
        Ticker { now in
            let k = compute(c, now: now)
            let accent = Color(hex: c.accent)
            let past = k.phase == .past
            let type = store.art.meta.types[c.type]!
            let s = store.settings
            GeometryReader { geo in
                let sceneH = max(330, min(geo.size.width * 1.04, geo.size.height * 0.62))
                ScrollView(showsIndicators: false) {
                    VStack(spacing: 0) {
                        ZStack(alignment: .top) {
                            SceneView(c: c, k: k, art: store.art, size: .detail, controller: ctrl, reduceMotion: store.reduceMotion, interactive: true,
                                      onBark: { ctrl.bubble = dogLine(c, compute(c), kind: "tap"); Haptics.play(s.haptics, "tick"); Sounds.play(s.sound, "bark") },
                                      onPetStart: { ctrl.bubble = dogLine(c, compute(c), kind: "pet") })
                                .frame(height: sceneH + geo.safeAreaInsets.top).offset(y: 0)
                            LinearGradient(colors: [Color.black.opacity(0.32), .clear], startPoint: .top, endPoint: .bottom).frame(height: 110 + geo.safeAreaInsets.top).allowsHitTesting(false)
                            HStack {
                                IconButton(icon: "back", label: "Back", glass: true) { store.pop() }
                                Spacer()
                                Text("\(k.stage.emoji) \(stageLabel(c, k))").font(PawFont.body(14, 800)).foregroundColor(Color(hex: "#3D2616"))
                                    .padding(.horizontal, 14).padding(.vertical, 8).background(Capsule().fill(Color.white.opacity(0.78)).shadow(color: .black.opacity(0.12), radius: 4, y: 2))
                                Spacer()
                                IconButton(icon: "more", label: "More options for \(c.title)", glass: true) { menu = true }
                            }.padding(.horizontal, 16).padding(.top, geo.safeAreaInsets.top + 6)
                            VStack { Spacer()
                                HStack(spacing: 8) {
                                    if ctrl.hint { Text("Tap to bark · Hold to pet · Shake").font(PawFont.body(12, 800)).foregroundColor(.white).padding(.horizontal, 14).padding(.vertical, 6).background(Capsule().fill(Color(hex: "#1F2433").opacity(0.66))) }
                                    Button {
                                        ctrl.hint = false; ctrl.fetchAt = ctrl.clock; Haptics.play(s.haptics, "soft"); ctrl.bubble = dogLine(c, compute(c), kind: "fetch")
                                    } label: { Text("🎾 Fetch").font(PawFont.body(12, 800)).foregroundColor(.white).padding(.horizontal, 14).padding(.vertical, 6).background(Capsule().fill(Color(hex: "#1F2433").opacity(0.66))) }.buttonStyle(.plain)
                                }.padding(.bottom, 50)
                            }.frame(height: sceneH + geo.safeAreaInsets.top)
                        }
                        // ---- sheet with the numbers ----
                        VStack(spacing: 0) {
                            FlowLayout(spacing: 6) {
                                Chip(text: "\(type.emoji) \(type.short)")
                                if let d = c.destination { Chip(text: "\(d.flag) \(d.city ?? d.country)") }
                                if c.type == "birthday", let a = k.age { Chip(text: "\(c.person?.name.isEmpty == false ? c.person!.name : "They") turn\(c.person?.name.isEmpty == false ? "s" : "") \(a)") }
                                if c.type == "anniversary", let t = k.together { Chip(text: "Together for \(t.formatted()) days") }
                                if c.yearly { Chip(text: "Repeats every year") }
                            }.frame(maxWidth: .infinity)
                            H(c.title, 31, align: .center).padding(.top, 12)
                            let w = whenText(c, k)
                            Muted(k.phase == .today ? "Happening today" : w.0, 15, align: .center).padding(.top, 4)
                            if let l = w.1, !past { Muted("\(l) your time", 13, align: .center) }
                            if past { H("\(plural(k.daysSince, "day")) ago 💛", 40, weight: 700, align: .center).padding(.top, 14) }
                            else { Numerals(c: c, k: k, size: .detail, accent: accent).padding(.top, 14); Leash(c: c, k: k, accent: accent).padding(.top, 18) }
                            Text(stageSentence(c, k) + ".").font(PawFont.body(14, 700)).foregroundColor(pc.ink2).multilineTextAlignment(.center)
                                .padding(.horizontal, 14).padding(.vertical, 10).background(RoundedRectangle(cornerRadius: 14).fill(pc.surface.mixed(accent, 0.18))).padding(.top, 16)
                        }
                        .padding(.horizontal, 20).padding(.top, 24).padding(.bottom, 8).frame(maxWidth: .infinity)
                        .background(UnevenRoundedRectangle(topLeadingRadius: 36, topTrailingRadius: 36).fill(pc.bg).shadow(color: .black.opacity(0.14), radius: 14, y: -4))
                        .offset(y: -36).padding(.bottom, -36)

                        VStack(spacing: 0) {
                            if past { PhotosPanel(c: c, accent: accent) }
                            Panel { PanelTitle("Display"); Seg(options: DisplayMode.allCases.map { ($0.rawValue, $0.label) }, selected: c.displayMode.rawValue, label: "Countdown display mode") { m in var n = c; n.displayMode = DisplayMode(rawValue: m) ?? .full; store.upsert(n) } }
                            WhenPanel(c: c, k: k)
                            ChecklistPanel(c: c, accent: accent)
                            NotesPanel(c: c)
                            Panel {
                                PanelTitle("Reminders", icon: "bell"); Muted("At most one per day, never spammy.", 13).padding(.bottom, 6)
                                ForEach(Array(c.reminders.enumerated()), id: \.offset) { i, r in
                                    if i > 0 { Divider2() }
                                    HStack { B(r.label, 16, weight: 700); Spacer(); PawSwitch(on: r.enabled, label: r.label) { on in var n = c; n.reminders[i].enabled = on; store.upsert(n) } }.frame(minHeight: 52)
                                }
                            }
                            Panel {
                                PanelTitle("Milestones")
                                HStack(spacing: 8) {
                                    ForEach(store.art.meta.milestones, id: \.self) { m in
                                        let got = past || k.phase == .today || (k.daysCeil <= m && k.daysTotal >= m)
                                        VStack(spacing: 0) {
                                            Text("\(m)").font(PawFont.display(20, 700)).foregroundColor(got ? accent.onColor : pc.ink2)
                                            Text(m == 1 ? "DAY" : "DAYS").font(PawFont.body(9, 800)).foregroundColor(got ? accent.onColor : pc.ink2)
                                        }.frame(maxWidth: .infinity).aspectRatio(1, contentMode: .fit).background(RoundedRectangle(cornerRadius: 16).fill(got ? accent : pc.ink.opacity(0.06)))
                                    }
                                }
                            }
                            Panel {
                                HStack(spacing: 14) {
                                    DogIcon(dog: c.dog).frame(width: 56, height: 56)
                                    VStack(alignment: .leading, spacing: 2) { B(c.dog.name, 17, weight: 800); Muted("\(store.art.meta.breeds[c.dog.breed]?.label ?? "") · \(store.art.meta.breeds[c.dog.breed]?.vibe ?? "")") }
                                }
                            }
                        }.padding(.horizontal, 20).padding(.bottom, 40)
                    }
                }.ignoresSafeArea(edges: .top)
            }
            .onAppear { milestone(c, k) }
        }
        .onAppear {
            shake.onShake = {
                let cc = store.countdown(id) ?? c
                ctrl.hint = false; ctrl.reactions.start("sneeze", ctrl.clock); ctrl.shakenAt = ctrl.clock
                ctrl.bubble = dogLine(cc, compute(cc), kind: "sneeze"); Haptics.play(store.settings.haptics, "success"); Sounds.play(store.settings.sound, "bark")
            }
            shake.start()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
                let k = compute(c)
                ctrl.bubble = k.stage == .nap ? "Zzz…" : k.stage == .today ? "IT'S TODAY!!! 🎉" : k.stage == .zoomies ? "ZOOM!" : k.stage == .memory ? "Remember this? 💛" : "Woof!"
            }
        }
        .onDisappear { shake.stop() }
        .sheet(isPresented: $menu) {
            VStack(spacing: 0) {
                H(c.title, 22, align: .center).padding(.bottom, 12)
                SheetItem(icon: "edit", text: "Edit countdown") { menu = false; store.push(.flow(c.id)) }
                SheetItem(icon: "heart", text: "Share…") { menu = false; DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { sharing = true } }
                SheetItem(icon: "archive", text: c.archived ? "Move back to Home" : "Archive to Memories") {
                    menu = false; var n = c; n.archived.toggle(); store.upsert(n); store.say(n.archived ? "Archived to Memories" : "Back on Home"); if n.archived { store.tab(.memories) }
                }
                SheetItem(icon: "trash", text: "Delete…", danger: true) { menu = false; DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { confirmDelete = true } }
                Spacer(minLength: 0)
            }.padding(20).background(pc.bg.ignoresSafeArea()).presentationDetents([.height(400)])
        }
        .sheet(isPresented: $sharing) { ShareSheet(c: c).presentationDetents([.large]).environment(\.paw, pc) }
        .sheet(isPresented: $confirmDelete) {
            VStack(spacing: 10) {
                H("Delete “\(c.title)”?", 22, align: .center)
                Muted("\(c.dog.name) will be very confused. This can’t be undone.", 15, align: .center)
                PrimaryButton("Delete forever", accent: pc.collar) { confirmDelete = false; store.remove(c.id); store.say("Deleted"); store.goHome() }.padding(.top, 8)
                GhostButton("Keep it") { confirmDelete = false }
                Spacer(minLength: 0)
            }.padding(20).background(pc.bg.ignoresSafeArea()).presentationDetents([.height(300)])
        }
        .swipeBack()
    }

    /// milestone celebration (100, 50, 30, 10, 7, 1 days) - once per countdown & year
    private func milestone(_ c: Countdown, _ k: Computed) {
        let key = "\(c.id):\(k.occYear):\(k.daysCeil)"
        guard k.phase == .upcoming, store.art.meta.milestones.contains(k.daysCeil), !store.data.celebrated.contains(key) else { return }
        store.update { $0.celebrated.insert(key) }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) {
            store.confetti()
            store.say(k.daysCeil == 1 ? "⚡ Zoomies activated! \(c.title) is tomorrow." : "🐕 \(c.dog.name) just counted: \(k.daysCeil) days until \(plainTitle(c.title))!")
            Haptics.play(store.settings.haptics, "success"); Sounds.play(store.settings.sound, "chime")
        }
    }
}

struct WhenPanel: View {
    @EnvironmentObject var store: AppStore
    let c: Countdown; let k: Computed
    var body: some View {
        let sameTz = k.zone.identifier == TimeZone.current.identifier
        let w = whenText(c, k)
        Panel {
            PanelTitle("When", icon: "globe")
            kv(sameTz ? "Date" : "\(c.destination?.city ?? c.timeZone.split(separator: "/").last.map { $0.replacingOccurrences(of: "_", with: " ") } ?? "") time", w.0)
            if let l = w.1 { kv("Your time", l) }
            kv("Time zone", tzLabel(c.timeZone))
            if c.yearly { kv("Repeats", "Every year · next is \(k.occYear)") }
        }
    }
    private func kv(_ a: String, _ b: String) -> some View {
        HStack(alignment: .top) { Muted(a, 14.5); Spacer(minLength: 16); B(b, 14, weight: 800, align: .trailing) }.padding(.vertical, 8)
    }
}

struct ChecklistPanel: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.paw) var pc
    let c: Countdown; let accent: Color
    @State var text = ""
    var body: some View {
        let title = ["intl_trip", "vacation"].contains(c.type) ? "Packing list" : (c.type == "birthday" ? "Gift ideas" : "Checklist")
        Panel {
            PanelTitle(title) { Muted("\(c.checklist.filter { $0.done }.count)/\(c.checklist.count)") }
            ForEach(Array(c.checklist.enumerated()), id: \.element.id) { i, it in
                if i > 0 { Divider2() }
                HStack {
                    Button { var n = c; n.checklist[i].done.toggle(); store.upsert(n); Haptics.play(store.settings.haptics, "tick") } label: {
                        HStack(spacing: 12) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 10).fill(it.done ? accent : pc.surface).frame(width: 28, height: 28)
                                RoundedRectangle(cornerRadius: 10).stroke(it.done ? accent : pc.line, lineWidth: 2.5).frame(width: 28, height: 28)
                                if it.done { PawIcon(name: "check", color: accent.onColor, size: 18) }
                            }
                            B(it.text, 16, weight: 700, color: it.done ? pc.ink2 : nil).strikethrough(it.done)
                            Spacer()
                        }.frame(minHeight: 54)
                    }.buttonStyle(.plain).accessibilityAddTraits(it.done ? [.isSelected] : [])
                    Button { var n = c; n.checklist.remove(at: i); store.upsert(n) } label: { PawIcon(name: "close", color: pc.ink2, size: 18).frame(width: 44, height: 44) }.buttonStyle(.plain).accessibilityLabel("Remove \(it.text)")
                }
            }
            HStack(spacing: 8) {
                Field(label: "", text: $text, placeholder: "Add something…", maxLen: 80, minHeight: 46).padding(.top, -16)
                SmallButton(title: "Add", tint: pc.surface.mixed(accent, 0.18)) {
                    let t = text.trimmingCharacters(in: .whitespaces); guard !t.isEmpty else { return }
                    var n = c; n.checklist.append(CheckItem(id: newId(), text: t, done: false)); store.upsert(n); text = ""
                }
            }.padding(.top, 8)
        }
    }
}

struct NotesPanel: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.paw) var pc
    let c: Countdown
    @State var text = ""
    @State var loaded = false
    @State var saveTask: Task<Void, Never>? = nil
    var body: some View {
        Panel {
            PanelTitle("Notes")
            ZStack(alignment: .topLeading) {
                if text.isEmpty { Text("Itinerary, ideas, inside jokes…").font(PawFont.body(16)).foregroundColor(pc.ink2.opacity(0.6)).padding(.horizontal, 6).padding(.vertical, 8) }
                TextEditor(text: $text).font(PawFont.body(16)).foregroundColor(pc.ink).scrollContentBackground(.hidden).frame(minHeight: 92)
            }
            .padding(8).background(RoundedRectangle(cornerRadius: 18).fill(pc.ink.opacity(0.04)))
        }
        .onAppear { if !loaded { text = c.notes; loaded = true } }
        .onChange(of: text) { v in
            // save a moment after typing stops (every save also re-plans reminders)
            guard loaded, v != c.notes else { return }
            saveTask?.cancel()
            saveTask = Task { @MainActor in
                try? await Task.sleep(nanoseconds: 700_000_000)
                if Task.isCancelled { return }
                var n = c; n.notes = v; store.upsert(n)
            }
        }
    }
}

struct PhotosPanel: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.paw) var pc
    let c: Countdown; let accent: Color
    @State var picked: [PhotosPickerItem] = []
    var body: some View {
        Panel {
            PanelTitle("Memory photos", icon: "camera") {
                PhotosPicker(selection: $picked, maxSelectionCount: max(1, 6 - c.photos.count), matching: .images) {
                    Text("Add photos").font(PawFont.display(15)).foregroundColor(pc.ink).padding(.horizontal, 16).frame(minHeight: 44).background(RoundedRectangle(cornerRadius: 14).fill(pc.surface.mixed(accent, 0.18)))
                }
            }
            if c.photos.isEmpty { Muted("\(c.dog.name) is keeping this spot warm. Add a few photos and this countdown becomes a memory card.", 15) }
            else {
                HStack(spacing: 10) {
                    ForEach(Array(c.photos.prefix(3)), id: \.self) { name in
                        PhotoThumb(name: name).aspectRatio(1, contentMode: .fit).frame(maxWidth: .infinity)
                    }
                }
            }
        }
        .onChange(of: picked) { items in
            guard !items.isEmpty else { return }
            Task {
                var names: [String] = []
                for it in items { if let d = try? await it.loadTransferable(type: Data.self), let n = Photos.save(d) { names.append(n) } }
                await MainActor.run {
                    var n = c; n.photos += names; store.upsert(n); picked = []
                    if !names.isEmpty { store.say("Added to \(c.title) 💛"); Haptics.play(store.settings.haptics, "success") }
                }
            }
        }
    }
}

struct PhotoThumb: View {
    let name: String
    var body: some View {
        Group {
            if let img = Photos.image(name) { Image(uiImage: img).resizable().scaledToFill() } else { Color.gray.opacity(0.2) }
        }.clipShape(RoundedRectangle(cornerRadius: 4)).padding(4).background(RoundedRectangle(cornerRadius: 6).fill(.white))
    }
}

/// the head of a dog as a small round picture (used for breed cards and settings)
/// the person's profile picture: their own photo if they added one, otherwise Scott's face
struct Avatar: View {
    @EnvironmentObject var store: AppStore
    var size: CGFloat
    var body: some View {
        Group {
            if !store.settings.profilePhoto.isEmpty, let img = Photos.image(store.settings.profilePhoto) { Image(uiImage: img).resizable().scaledToFill() }
            else { DogIcon(dog: Dog(breed: "scott", name: "Scott")) }
        }.frame(width: size, height: size).clipShape(Circle())
    }
}

struct DogIcon: View {
    @EnvironmentObject var store: AppStore
    let dog: Dog
    var bg = Color(hex: "#FFF1DC")
    var body: some View {
        let palette = dogPalette(store.art.meta, dog, accent: RGB.black)
        let node = store.art.icon(dog.variant)
        Canvas { ctx, sz in
            var c = ctx
            let s = sz.width / 240 * 0.98
            c.translateBy(x: sz.width / 2, y: sz.height * 0.53); c.scaleBy(x: s, y: s); c.translateBy(x: -120, y: -108)
            if let n = node { drawNode(&c, n, DrawState(palette: palette, time: 0, stage: .waiting, anims: [], reduceMotion: true)) }
        }
        .background(RoundedRectangle(cornerRadius: 24 / 100 * 56).fill(bg))
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .accessibilityHidden(true)
    }
}
