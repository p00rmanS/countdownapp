import SwiftUI

/*
 FlowView.swift - create / edit a countdown in five steps: What? -> When? -> Which dog? -> Style -> Done.
 Everything typed lives in a Draft; it only becomes a real Countdown when the last button is pressed.
*/

struct Draft {
    var id: String? = nil
    var type = "intl_trip"
    var title = ""
    var titleAuto = true
    var date = Calendar.current.date(byAdding: .day, value: 30, to: Date()) ?? Date()      // day, in the event's own zone
    var time = Calendar.current.date(bySettingHour: 9, minute: 0, second: 0, of: Date()) ?? Date()
    var allDay = false
    var tz = TimeZone.current.identifier
    var yearly = false
    var country = ""
    var personName = ""
    var birthYear = ""
    var breed = "scott"
    var breedTouched = false
    var dogName = "Scott"
    var nameTouched = false
    var fur = "#B98A62"
    var ears = "floppy"
    var accent = "#5DADE8"
    var accentTouched = false
    var mode = DisplayMode.full
    var reminders = defaultReminders("intl_trip")
    var notes = ""
    var checklist: [CheckItem] = []
    var createdAt: Date? = nil
    var sample = false
    var photos: [String] = []
    var archived = false
    var origTarget: String? = nil

    mutating func setType(_ meta: Meta, _ t: String) {
        let info = meta.types[t]!
        let b = breedTouched ? breed : meta.suggestedBreed(t)
        type = t; if !accentTouched { accent = info.accent }; yearly = info.repeats
        allDay = t == "birthday" || t == "anniversary" || t == "holiday"
        breed = b; if !nameTouched { dogName = meta.breeds[b]?.name ?? dogName }
        reminders = defaultReminders(t); if t != "intl_trip" { country = "" }
        if titleAuto { title = "" }
    }

    private var ymd: (Int, Int, Int) { let c = Calendar.current.dateComponents([.year, .month, .day], from: date); return (c.year!, c.month!, c.day!) }
    private var hm: (Int, Int) { let c = Calendar.current.dateComponents([.hour, .minute], from: time); return (c.hour!, c.minute!) }

    func toCountdown() -> Countdown {
        let (y, m, d) = ymd, (h, mi) = hm
        let target = String(format: "%04d-%02d-%02dT%@", y, m, d, allDay ? "00:00" : String(format: "%02d:%02d", h, mi))
        let keep = createdAt != nil && origTarget == "\(target)|\(tz)"
        let dog = breed == "mutt" ? Dog(breed: "mutt", name: dogName.trimmingCharacters(in: .whitespaces).isEmpty ? "Lucky" : dogName, furColor: fur, ears: ears) : Dog(breed: breed, name: dogName.trimmingCharacters(in: .whitespaces))
        let person = type == "birthday" && (!personName.isEmpty || !birthYear.isEmpty) ? Person(name: personName.trimmingCharacters(in: .whitespaces), birthYear: Int(birthYear)) : nil
        return Countdown(id: id ?? newId(), title: title.trimmingCharacters(in: .whitespaces), type: type, targetAt: target, timeZone: tz, allDay: allDay, yearly: yearly,
                         createdAt: keep ? createdAt! : Date(), dog: dog, accent: accent, displayMode: mode, notes: notes, checklist: checklist, destination: nil, person: person,
                         reminders: reminders, archived: archived, photos: photos, sample: sample)
    }

    static func from(_ c: Countdown) -> Draft {
        var d = Draft()
        let w = parseWall(c.targetAt)
        d.id = c.id; d.type = c.type; d.title = c.title; d.titleAuto = false
        d.date = Calendar.current.date(from: DateComponents(year: w.y, month: w.mo, day: w.d)) ?? Date()
        d.time = Calendar.current.date(from: DateComponents(hour: w.h, minute: w.mi)) ?? Date()
        d.allDay = c.allDay; d.tz = c.timeZone; d.yearly = c.yearly; d.country = c.destination?.country ?? ""
        d.personName = c.person?.name ?? ""; d.birthYear = c.person?.birthYear.map(String.init) ?? ""
        d.breed = c.dog.breed; d.breedTouched = true; d.dogName = c.dog.name; d.nameTouched = true
        d.fur = c.dog.furColor ?? "#B98A62"; d.ears = c.dog.ears ?? "floppy"; d.accent = c.accent; d.accentTouched = true
        d.mode = c.displayMode; d.reminders = c.reminders; d.notes = c.notes; d.checklist = c.checklist; d.createdAt = c.createdAt
        d.sample = c.sample; d.photos = c.photos; d.archived = c.archived; d.origTarget = "\(c.targetAt)|\(c.timeZone)"
        return d
    }
}

private let STEPS = ["What?", "When?", "Which dog?", "Style", "Done!"]
private let HEADS = ["What are you counting down to?", "When is the big moment?", "Choose their dog", "Make it yours", ""]

struct FlowView: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.paw) var pc
    let editId: String?
    @State var step = 0
    @State var d = Draft()
    @State var ready = false
    @State var preview = Stage.waiting
    @State var showCountry = false
    @State var showTz = false

    var editing: Bool { editId != nil }

    var body: some View {
        let meta = store.art.meta
        let accent = Color(hex: d.accent)
        let note = whenNote(d, editing)
        let valid = step == 0 ? !d.title.trimmingCharacters(in: .whitespaces).isEmpty : (step == 1 ? note.0 : (step == 2 ? !d.dogName.trimmingCharacters(in: .whitespaces).isEmpty : true))
        VStack(spacing: 0) {
            HStack(spacing: 4) {
                IconButton(icon: step == 0 ? "close" : "back", label: step == 0 ? "Close" : "Previous step") { back() }
                Spacer(minLength: 0)
                ForEach(0..<5, id: \.self) { i in
                    VStack(spacing: 2) {
                        PawIcon(name: "paw", color: i <= step ? accent.onColor : pc.ink2, size: 16).frame(width: i == step ? 34 : 28, height: i == step ? 34 : 28)
                            .background(Circle().fill(i <= step ? accent : pc.ink.opacity(0.08)))
                        Text(STEPS[i]).font(PawFont.body(10, 800)).foregroundColor(i == step ? pc.ink : pc.ink2).lineLimit(1).minimumScaleFactor(0.7)
                    }.frame(width: 48)
                }
                Spacer(minLength: 0)
                Color.clear.frame(width: 48, height: 48)
            }.padding(.horizontal, 20).padding(.vertical, 10)

            ScrollViewReader { proxy in
                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 0) {
                        Color.clear.frame(height: 0).id("top")
                        if step < 4 { Eyebrow("Step \(step + 1) of 5 · \(STEPS[step])"); H(HEADS[step], 30).padding(.top, 4).padding(.bottom, 6) }
                        switch step {
                        case 0: stepWhat(meta, accent)
                        case 1: stepWhen(accent, note)
                        case 2: stepDog(meta, accent)
                        case 3: stepStyle(meta, accent)
                        default: stepDone(meta)
                        }
                    }.padding(.horizontal, 20).padding(.bottom, 24)
                }
                .onChange(of: step) { _ in proxy.scrollTo("top", anchor: .top) }
                .scrollDismissesKeyboard(.interactively)
            }

            PrimaryButton(step == 4 ? (editing ? "Save changes" : "Meet \(d.dogName.isEmpty ? "your dog" : d.dogName)") : (step == 3 ? "Finish" : "Next"), accent: accent, enabled: valid) { next(valid) }
                .padding(.horizontal, 20).padding(.top, 10).padding(.bottom, 8)
        }
        .onAppear {
            guard !ready else { return }
            ready = true
            if let id = editId, let c = store.countdown(id) { d = Draft.from(c) }
            else {
                d = Draft(); d.mode = store.settings.displayMode; d.setType(meta, "intl_trip")
                if store.welcomeBreedChosen { d.breed = store.welcomeBreed; d.breedTouched = true; d.dogName = meta.breeds[d.breed]?.name ?? d.dogName }
            }
        }
        .sheet(isPresented: $showCountry) { countrySheet(meta) }
        .sheet(isPresented: $showTz) { TimeZonePicker(selected: d.tz) { d.tz = $0; showTz = false }.environment(\.paw, pc) }
        .swipeBack()
    }

    func back() { if step == 0 { store.pop() } else { step -= 1 } }

    func next(_ valid: Bool) {
        guard valid else { return }
        if step < 4 {
            step += 1
            if step == 4 { DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) { store.confetti(); Haptics.play(store.settings.haptics, "success"); Sounds.play(store.settings.sound, "chime") } }
            return
        }
        var c = d.toCountdown()
        if d.type == "intl_trip", let co = store.art.meta.countries.first(where: { $0.name == d.country }) { c.destination = Destination(country: co.name, city: co.city, flag: co.flag) }
        store.upsert(c); store.update { $0.onboarded = true }
        store.stack = [.home, .detail(c.id)]
    }

    // MARK: steps

    func stepWhat(_ meta: Meta, _ accent: Color) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            let rows = stride(from: 0, to: meta.typeOrder.count, by: 2).map { Array(meta.typeOrder[$0..<min($0 + 2, meta.typeOrder.count)]) }
            VStack(spacing: 10) {
                ForEach(rows, id: \.self) { row in
                    HStack(spacing: 10) {
                        ForEach(row, id: \.self) { t in
                            let info = meta.types[t]!, on = d.type == t, ta = Color(hex: info.accent)
                            Button { d.setType(meta, t); Haptics.play(store.settings.haptics, "tick") } label: {
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(info.emoji).font(.system(size: 30)); H(info.label, 17); Muted(info.hint, 13)
                                }.padding(14).frame(maxWidth: .infinity, minHeight: 108, alignment: .topLeading)
                                .background(RoundedRectangle(cornerRadius: 22).fill(on ? pc.surface.mixed(ta, 0.14) : pc.surface).shadow(color: .black.opacity(0.08), radius: 4, y: 1))
                                .overlay(RoundedRectangle(cornerRadius: 22).stroke(on ? ta : .clear, lineWidth: 3))
                            }.buttonStyle(.plain).accessibilityAddTraits(on ? [.isSelected] : [])
                        }
                    }
                }
            }.padding(.top, 8)
            Field(label: "Name it", text: Binding(get: { d.title }, set: { d.title = $0; d.titleAuto = false }), placeholder: meta.types[d.type]!.placeholder, maxLen: 48, accent: accent)
            if d.type == "intl_trip" {
                PickerRow(label: "Destination country", value: meta.countries.first(where: { $0.name == d.country }).map { "\($0.flag) \($0.name)" } ?? "Choose a country…") { showCountry = true }
                Muted("We'll set the time zone for you.", 13).padding(.top, 6)
            }
            if d.type == "birthday" {
                HStack(alignment: .top, spacing: 12) {
                    Field(label: "Whose birthday?", text: Binding(get: { d.personName }, set: { n in
                        d.personName = n
                        if d.titleAuto || d.title.isEmpty { d.title = n.isEmpty ? "" : "\(n.trimmingCharacters(in: .whitespaces))'s birthday"; d.titleAuto = true }
                    }), placeholder: "Mia", maxLen: 30, accent: accent)
                    Field(label: "Birth year", text: Binding(get: { d.birthYear }, set: { d.birthYear = String($0.filter(\.isNumber).prefix(4)) }), placeholder: "1996", maxLen: 4, number: true, accent: accent).frame(width: 120)
                }
            }
        }
    }

    func stepWhen(_ accent: Color, _ note: (Bool, String)) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 7) {
                    Text("Date").font(PawFont.body(14, 800)).foregroundColor(pc.ink2)
                    DatePicker("Date", selection: $d.date, in: (editing || d.yearly ? Date.distantPast : Calendar.current.startOfDay(for: Date()))..., displayedComponents: .date).labelsHidden().tint(accent)
                        .padding(.horizontal, 12).frame(maxWidth: .infinity, minHeight: 54, alignment: .leading).background(RoundedRectangle(cornerRadius: 18).fill(pc.surface))
                        .overlay(RoundedRectangle(cornerRadius: 18).stroke(pc.line, lineWidth: 2))
                }
                if !d.allDay {
                    VStack(alignment: .leading, spacing: 7) {
                        Text("Time").font(PawFont.body(14, 800)).foregroundColor(pc.ink2)
                        DatePicker("Time", selection: $d.time, displayedComponents: .hourAndMinute).labelsHidden().tint(accent)
                            .padding(.horizontal, 12).frame(minHeight: 54).background(RoundedRectangle(cornerRadius: 18).fill(pc.surface))
                            .overlay(RoundedRectangle(cornerRadius: 18).stroke(pc.line, lineWidth: 2))
                    }
                }
            }.padding(.top, 16)
            Panel(flush: true) {
                HStack { VStack(alignment: .leading, spacing: 1) { B("All day", 16, weight: 800); Muted("Counts down to midnight", 13) }; Spacer(); PawSwitch(on: d.allDay, label: "All day") { d.allDay = $0 } }.frame(minHeight: 64)
                Divider2()
                HStack { VStack(alignment: .leading, spacing: 1) { B("Repeat every year", 16, weight: 800); Muted("Birthdays & anniversaries roll over", 13) }; Spacer(); PawSwitch(on: d.yearly, label: "Repeat every year") { d.yearly = $0 } }.frame(minHeight: 64)
            }
            PickerRow(label: "Time zone", value: tzLabel(d.tz)) { showTz = true }
            Muted("Pick where the moment happens. Pawcount counts to it exactly, even across daylight-saving changes.", 13).padding(.top, 6)
            Text(note.1).font(PawFont.body(16, 700)).foregroundColor(pc.ink).frame(maxWidth: .infinity, alignment: .leading).padding(16)
                .background(RoundedRectangle(cornerRadius: 18).fill(note.0 ? pc.surface.mixed(accent, 0.16) : pc.collar.opacity(0.14))).padding(.top, 18)
        }
    }

    func stepDog(_ meta: Meta, _ accent: Color) -> some View {
        var pc0 = d.toCountdown(); if pc0.title.isEmpty { pc0.title = meta.types[d.type]!.placeholder }; pc0.createdAt = Date().addingTimeInterval(-20 * DAY)
        return VStack(alignment: .leading, spacing: 0) {
            SceneView(c: pc0, k: compute(pc0), art: store.art, size: .preview, stage: preview, reduceMotion: store.reduceMotion)
                .clipShape(RoundedRectangle(cornerRadius: 28)).shadow(color: .black.opacity(0.14), radius: 6, y: 3).padding(.top, 4)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach([Stage.nap, .curious, .waiting, .packing, .zoomies, .today], id: \.self) { s in
                        let on = preview == s
                        Button { preview = s } label: {
                            Text("\(s.emoji) \(s == .today ? "Party" : s.label)").font(PawFont.body(13.5, 800)).foregroundColor(on ? accent.onColor : pc.ink2)
                                .padding(.horizontal, 13).frame(minHeight: 38).background(Capsule().fill(on ? accent : pc.surface))
                        }.buttonStyle(.plain).accessibilityAddTraits(on ? [.isSelected] : [])
                    }
                }.padding(.vertical, 12)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(meta.breedOrder, id: \.self) { b in
                        let br = meta.breeds[b]!, on = d.breed == b, sug = (meta.breedBest[b] ?? []).contains(d.type)
                        Button { d.breed = b; d.breedTouched = true; if !d.nameTouched { d.dogName = br.name }; Haptics.play(store.settings.haptics, "tick") } label: {
                            VStack(spacing: 1) {
                                DogIcon(dog: Dog(breed: b, name: br.name, furColor: b == "mutt" ? d.fur : nil, ears: b == "mutt" ? d.ears : nil)).frame(width: 68, height: 68).padding(.bottom, 4)
                                H(br.name, 18); B(br.label, 13, weight: 700, align: .center); Muted(br.vibe, 12, align: .center)
                            }.padding(.horizontal, 10).padding(.top, 16).padding(.bottom, 14).frame(width: 138)
                            .background(RoundedRectangle(cornerRadius: 24).fill(pc.surface).shadow(color: .black.opacity(on ? 0.16 : 0.08), radius: on ? 8 : 4, y: 2))
                            .overlay(RoundedRectangle(cornerRadius: 24).stroke(on ? accent : .clear, lineWidth: 3))
                            .overlay(alignment: .top) { if sug { Text("Great match").font(PawFont.body(11, 800)).foregroundColor(Color(hex: "#3B2314")).padding(.horizontal, 10).padding(.vertical, 2).background(Capsule().fill(pc.ball)).offset(y: -8) } }
                        }.buttonStyle(.plain).accessibilityAddTraits(on ? [.isSelected] : [])
                    }
                }.padding(.vertical, 12).padding(.horizontal, 2)
            }
            Field(label: "Dog's name", text: Binding(get: { d.dogName }, set: { d.dogName = $0; d.nameTouched = true }), maxLen: 18, accent: accent)
            if d.breed == "mutt" {
                Panel {
                    PanelTitle("Customize \(d.dogName.isEmpty ? "Lucky" : d.dogName)")
                    Muted("Coat colour", 14)
                    FlowLayout(spacing: 12) { ForEach(meta.muttFurs, id: \.self) { f in Swatch(color: Color(hex: f), on: d.fur == f, label: "Coat \(f)") { d.fur = f } } }.padding(.vertical, 10)
                    Muted("Ears", 14).padding(.bottom, 6)
                    Seg(options: meta.earTypes, selected: d.ears, label: "Ear style") { d.ears = $0 }
                }
            }
        }
    }

    func stepStyle(_ meta: Meta, _ accent: Color) -> some View {
        var c0 = d.toCountdown(); if c0.title.isEmpty { c0.title = meta.types[d.type]!.placeholder }; c0.createdAt = Date().addingTimeInterval(-20 * DAY)
        let k0 = compute(c0)
        return VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                SceneView(c: c0, k: k0, art: store.art, size: .card, stage: .waiting, reduceMotion: store.reduceMotion)
                VStack(alignment: .leading, spacing: 3) { H(c0.title, 17).lineLimit(1); H(shortCount(c0, k0), 22) }.padding(13).frame(maxWidth: .infinity, alignment: .leading)
            }
            .background(pc.surface).clipShape(RoundedRectangle(cornerRadius: 28)).shadow(color: .black.opacity(0.14), radius: 8, y: 3)
            .frame(width: 230).frame(maxWidth: .infinity).padding(.top, 8)
            Panel { PanelTitle("Accent colour"); FlowLayout(spacing: 12) { ForEach(meta.accents, id: \.0) { v, n in Swatch(color: Color(hex: v), on: d.accent == v, label: n) { d.accent = v; d.accentTouched = true; Haptics.play(store.settings.haptics, "tick") } } } }
            Panel { PanelTitle("Show the countdown as"); Seg(options: DisplayMode.allCases.map { ($0.rawValue, $0.label) }, selected: d.mode.rawValue, label: "Display mode") { d.mode = DisplayMode(rawValue: $0) ?? .full } }
            Panel {
                PanelTitle("Reminders", icon: "bell")
                ForEach(Array(d.reminders.enumerated()), id: \.offset) { i, r in
                    if i > 0 { Divider2() }
                    HStack { B(r.label, 16, weight: 700); Spacer(); PawSwitch(on: r.enabled, label: r.label) { d.reminders[i].enabled = $0 } }.frame(minHeight: 52)
                }
            }
        }
    }

    func stepDone(_ meta: Meta) -> some View {
        var c0 = d.toCountdown(); if c0.title.isEmpty { c0.title = "Your countdown" }; c0.createdAt = Date().addingTimeInterval(-20 * DAY)
        return VStack(spacing: 8) {
            SceneView(c: c0, k: compute(c0), art: store.art, size: .hero, stage: .today, reduceMotion: store.reduceMotion)
                .clipShape(RoundedRectangle(cornerRadius: 34)).shadow(color: .black.opacity(0.15), radius: 10, y: 4).padding(.top, 10)
            H("\(d.dogName.isEmpty ? "Your dog" : d.dogName) is already excited!", 30, align: .center).padding(.top, 22)
            Muted(editing ? "Looking good. Save your changes and tail-wags will update." : "\(c0.title) is ready. \(d.dogName) the \(meta.breeds[d.breed]?.label ?? "dog") will nap, wait, pack and finally lose their mind as the day gets closer.", 16, align: .center)
        }
    }

    func countrySheet(_ meta: Meta) -> some View {
        VStack(spacing: 8) {
            H("Destination", 22, align: .center).padding(.top, 20)
            List(meta.countries, id: \.name) { co in
                Button {
                    d.country = co.name; d.tz = co.tz
                    if d.titleAuto || d.title.isEmpty { d.title = "\(co.name) \(co.flag)"; d.titleAuto = true }
                    showCountry = false
                } label: { Text("\(co.flag)  \(co.name)").font(PawFont.body(17, 700)).foregroundColor(pc.ink).frame(minHeight: 44, alignment: .leading) }
                .listRowBackground(pc.bg)
            }.listStyle(.plain).scrollContentBackground(.hidden)
        }.background(pc.bg.ignoresSafeArea())
    }
}

struct TimeZonePicker: View {
    @Environment(\.paw) var pc
    let selected: String; let pick: (String) -> Void
    @State var q = ""
    var body: some View {
        let all = TimeZone.knownTimeZoneIdentifiers.filter { $0.contains("/") && !$0.hasPrefix("Etc") }.sorted()
        let list = [TimeZone.current.identifier] + all.filter { q.isEmpty || $0.replacingOccurrences(of: "_", with: " ").localizedCaseInsensitiveContains(q) }
        VStack(spacing: 6) {
            H("Time zone", 22, align: .center).padding(.top, 20)
            Field(label: "", text: $q, placeholder: "Search (e.g. Tokyo)").padding(.horizontal, 20)
            List(list, id: \.self) { z in
                Button { pick(z) } label: {
                    Text(z == TimeZone.current.identifier ? "My time zone — \(z.replacingOccurrences(of: "_", with: " "))" : z.replacingOccurrences(of: "_", with: " ")).font(PawFont.body(16, 700)).foregroundColor(pc.ink).frame(minHeight: 40, alignment: .leading)
                }.listRowBackground(pc.bg)
            }.listStyle(.plain).scrollContentBackground(.hidden)
        }.background(pc.bg.ignoresSafeArea())
    }
}

/// (ok, text) - tells the user how far away the date is, or what's wrong with it
func whenNote(_ d: Draft, _ editing: Bool) -> (Bool, String) {
    let c = d.toCountdown()
    let k = compute(c)
    if k.phase == .past && !editing { return (false, "That moment has already passed — pick a date in the future.") }
    if k.phase == .today { return (true, "That's today! 🎉") }
    let days = Int((k.remaining / DAY).rounded(.up))
    let tzNote = k.zone.identifier != TimeZone.current.identifier ? " Counts to the moment in \(d.tz.split(separator: "/").last.map { $0.replacingOccurrences(of: "_", with: " ") } ?? d.tz)." : ""
    let chosenYear = Calendar.current.component(.year, from: d.date)
    let lead = d.yearly && k.occYear != chosenYear ? "Next up is" : "That's"
    return (true, "\(lead) in \(plural(days, "day"))\(days >= 14 ? " — about \(Int((Double(days) / 7).rounded())) weeks" : "").\(tzNote)")
}
