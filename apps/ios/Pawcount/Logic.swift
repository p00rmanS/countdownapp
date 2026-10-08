import Foundation

/*
 Logic.swift - the brain of the app (no UI). Port of js/countdown.js and the Android Logic.kt; unit-tested in
 PawcountTests/LogicTests.swift.

 - A countdown stores its target as a wall-clock time + an IANA zone ("Asia/Tokyo"). Calendar resolves that to a
   real instant, including daylight-saving changes.
 - compute() works out everything the screens need: time left, sleeps, progress, which of the 7 dog stages applies,
   and where a yearly repeat rolls over.
 - Stage rules: nap >75% time left, curious 75-40%, waiting 40-15%, packing <15% or the last 7 days,
   zoomies the last 24 h, today on the day itself, memory afterwards.
*/

let DAY: TimeInterval = 86_400
let HOUR: TimeInterval = 3_600
let MIN: TimeInterval = 60

enum Stage: String, CaseIterable {
    case nap, curious, waiting, packing, zoomies, today, memory
    var label: String {
        switch self {
        case .nap: return "Napping"; case .curious: return "Curious"; case .waiting: return "Waiting"; case .packing: return "Packing"
        case .zoomies: return "Zoomies"; case .today: return "It's today!"; case .memory: return "Memory"
        }
    }
    var emoji: String {
        switch self {
        case .nap: return "😴"; case .curious: return "👀"; case .waiting: return "🐕"; case .packing: return "🎒"
        case .zoomies: return "⚡"; case .today: return "🎉"; case .memory: return "💛"
        }
    }
}

enum Phase { case upcoming, today, past }

struct Parts: Equatable { var d: Int; var h: Int; var m: Int; var s: Int }

struct Computed {
    var zone: TimeZone
    var target: Date
    var start: Date
    var total: TimeInterval
    var remaining: TimeInterval
    var leftPct: Double
    var progress: Double
    var phase: Phase
    var stage: Stage
    var parts: Parts
    var sleeps: Int
    var daysCeil: Int
    var weeks: Int
    var weekDays: Int
    var daysTotal: Int
    var daysIn: Int
    var age: Int?
    var together: Int?
    var occYear: Int
    var daysSince: Int

    /// changes whenever the screen needs a re-draw (new stage, or the moment the timer hits zero)
    var key: String { "\(stage.rawValue):\(phase):\(remaining <= 0)" }
}

func zoneOf(_ id: String) -> TimeZone { TimeZone(identifier: id) ?? .current }

private func gregorian(_ tz: TimeZone) -> Calendar {
    var c = Calendar(identifier: .gregorian); c.timeZone = tz; c.locale = Locale(identifier: "en_US_POSIX"); return c
}

struct Wall { var y: Int; var mo: Int; var d: Int; var h: Int; var mi: Int }

func parseWall(_ s: String) -> Wall {
    // "2027-03-14T09:30" (seconds / zone suffix ignored)
    let digits = s.split(whereSeparator: { !$0.isNumber }).compactMap { Int($0) }
    func at(_ i: Int) -> Int { i < digits.count ? digits[i] : 0 }
    return Wall(y: at(0), mo: max(1, at(1)), d: max(1, at(2)), h: at(3), mi: at(4))
}

private func daysIn(year: Int, month: Int) -> Int {
    let cal = gregorian(TimeZone(identifier: "UTC")!)
    let d = cal.date(from: DateComponents(year: year, month: month, day: 1))!
    return cal.range(of: .day, in: .month, for: d)?.count ?? 30
}

func compute(_ c: Countdown, now: Date = Date()) -> Computed {
    let tz = zoneOf(c.timeZone)
    let cal = gregorian(tz)
    let base = parseWall(c.targetAt)

    // 29 Feb falls back to 28 Feb in non-leap years
    func occ(_ y: Int) -> (Int, Int, Int) { (y, base.mo, min(base.d, daysIn(year: y, month: base.mo))) }
    func instant(_ d: (Int, Int, Int)) -> Date {
        cal.date(from: DateComponents(year: d.0, month: d.1, day: d.2, hour: c.allDay ? 0 : base.h, minute: c.allDay ? 0 : base.mi)) ?? now
    }
    func dayStart(_ d: (Int, Int, Int)) -> Date { cal.date(from: DateComponents(year: d.0, month: d.1, day: d.2)) ?? now }
    func dayEnd(_ d: (Int, Int, Int)) -> Date { cal.date(byAdding: .day, value: 1, to: dayStart(d)) ?? now }

    // Yearly repeats: walk forward until an occurrence whose day isn't over yet.
    var date = (base.y, base.mo, base.d)
    var prevEnd: Date? = nil
    if c.yearly {
        var y = base.y
        for _ in 0..<400 {
            date = occ(y)
            let end = dayEnd(date)
            if end > now { break }
            prevEnd = end; y += 1
        }
    }
    let target = instant(date)
    let start0 = dayStart(date), finish = dayEnd(date)
    let start = prevEnd.map { max(c.createdAt, $0) } ?? c.createdAt
    let total = max(target.timeIntervalSince(start), HOUR)
    let remaining = target.timeIntervalSince(now)
    let leftPct = min(1, max(0, remaining / total))

    let phase: Phase = now < start0 ? .upcoming : (now < finish ? .today : .past)
    let stage: Stage
    if phase == .today { stage = .today }
    else if phase == .past { stage = .memory }
    else if remaining <= DAY { stage = .zoomies }
    else if remaining <= 7 * DAY || leftPct <= 0.15 { stage = .packing }
    else if leftPct <= 0.40 { stage = .waiting }
    else if leftPct <= 0.75 { stage = .curious }
    else { stage = .nap }

    let rem = max(0, remaining)
    let parts = Parts(d: Int(rem / DAY), h: Int((rem.truncatingRemainder(dividingBy: DAY)) / HOUR),
                      m: Int((rem.truncatingRemainder(dividingBy: HOUR)) / MIN), s: Int(rem.truncatingRemainder(dividingBy: MIN)))
    // "sleeps" = local midnights between now and the target, on the phone's own calendar
    let here = Calendar.current
    let sleeps = max(0, here.dateComponents([.day], from: here.startOfDay(for: now), to: here.startOfDay(for: target)).day ?? 0)
    let progress = phase == .upcoming ? 1 - leftPct : 1
    let daysTotal = max(1, Int((total / DAY).rounded()))
    let daysIn = min(daysTotal, max(0, Int(now.timeIntervalSince(start) / DAY) + 1))
    let age = c.person?.birthYear.map { date.0 - $0 }
    var together: Int? = nil
    if c.type == "anniversary" {
        let b = cal.date(from: DateComponents(year: base.y, month: base.mo, day: base.d)) ?? now
        together = max(0, Int(now.timeIntervalSince(b) / DAY))
    }
    return Computed(
        zone: tz, target: target, start: start, total: total, remaining: remaining, leftPct: leftPct, progress: progress, phase: phase, stage: stage,
        parts: parts, sleeps: sleeps, daysCeil: Int((rem / DAY).rounded(.up)), weeks: parts.d / 7, weekDays: parts.d % 7, daysTotal: daysTotal,
        daysIn: daysIn, age: age, together: together, occYear: date.0,
        daysSince: phase == .past ? max(1, Int(now.timeIntervalSince(finish) / DAY) + 1) : 0)
}

/* ---------- text helpers ---------- */

func plural(_ n: Int, _ w: String) -> String { "\(n) \(w)\(n == 1 ? "" : "s")" }

func spoken(_ k: Computed) -> String {
    if k.phase == .today && k.remaining <= 0 { return "It's today" }
    if k.phase == .past { return plural(k.daysSince, "day") + " ago" }
    let p = k.parts
    if p.d >= 1 { return plural(p.d, "day") + (p.h > 0 ? " and \(plural(p.h, "hour"))" : "") }
    if p.h >= 1 { return plural(p.h, "hour") + (p.m > 0 ? " and \(plural(p.m, "minute"))" : "") }
    return plural(max(1, p.m), "minute")
}

/// one cell of the big number display
struct NumUnit { var key: String; var value: Int; var label: String; var pad: Int }

func units(_ k: Computed, _ mode: DisplayMode) -> [NumUnit] {
    func u(_ key: String, _ v: Int, _ w: String, _ pad: Int = 1) -> NumUnit { NumUnit(key: key, value: v, label: v == 1 ? w : w + "s", pad: pad) }
    switch mode {
    case .days: return [u("d", k.daysCeil, "day")]
    case .sleeps: return [u("sl", k.sleeps, "sleep")]
    case .weeks: return [u("w", k.weeks, "week"), u("wd", k.weekDays, "day")]
    case .full: return [u("d", k.parts.d, "day"), NumUnit(key: "h", value: k.parts.h, label: "hrs", pad: 2), NumUnit(key: "m", value: k.parts.m, label: "min", pad: 2), NumUnit(key: "s", value: k.parts.s, label: "sec", pad: 2)]
    }
}

func shortCount(_ c: Countdown, _ k: Computed) -> String {
    if k.phase == .today { return "It's today! 🎉" }
    if k.phase == .past { return plural(k.daysSince, "day") + " ago" }
    switch c.displayMode {
    case .days: return plural(k.daysCeil, "day")
    case .sleeps: return plural(k.sleeps, "sleep")
    case .weeks: return k.weeks > 0 ? "\(k.weeks) wk\(k.weeks == 1 ? "" : "s") \(k.weekDays) d" : plural(k.parts.d, "day")
    case .full: return k.parts.d > 0 ? "\(k.parts.d)d \(k.parts.h)h" : "\(k.parts.h)h \(String(format: "%02d", k.parts.m))m"
    }
}

private let packLabel: [String: (String, String)] = [
    "intl_trip": ("Packing", "dragging a suitcase and clutching a passport"),
    "vacation": ("Packing", "wearing sunglasses beside a suitcase"),
    "birthday": ("Party prep", "wearing a party hat beside a present"),
    "anniversary": ("Flowers ready", "holding a bouquet with a flower crown"),
    "holiday": ("Wrapping", "in a holiday hat beside a present"),
    "custom": ("Ready", "ready with a tennis ball"),
]

func stageLabel(_ c: Countdown, _ k: Computed) -> String { k.stage == .packing ? (packLabel[c.type] ?? packLabel["custom"]!).0 : k.stage.label }

func stageSentence(_ c: Countdown, _ k: Computed) -> String {
    let d: String
    switch k.stage {
    case .nap: d = "curled up asleep, dreaming of the big day"
    case .curious: d = "sitting up with a curious head tilt"
    case .waiting: d = "sitting by the door, tail wagging slowly"
    case .packing: d = (packLabel[c.type] ?? packLabel["custom"]!).1
    case .zoomies: d = "running laps with a tail that is a blur"
    case .today: d = "celebrating with confetti"
    case .memory: d = "lying on a souvenir, remembering"
    }
    return "\(c.dog.name) is \(d)"
}

func describeDog(_ c: Countdown, _ k: Computed) -> String { "\(stageSentence(c, k)). \(spoken(k))\(k.phase == .past ? "" : " left")." }

/// title without emoji / flags, for sentences ("30 days until Japan")
func plainTitle(_ t: String) -> String {
    let cleaned = String(t.unicodeScalars.filter { !($0.properties.isEmojiPresentation || $0.value == 0xFE0F || $0.value == 0x200D || ($0.value >= 0x1F1E6 && $0.value <= 0x1F1FF)) })
    let s = cleaned.split(separator: " ").joined(separator: " ")
    return s.isEmpty ? "the big day" : s
}

/// what the dog says when tapped / petted / shaken / fetching
func dogLine(_ c: Countdown, _ k: Computed, kind: String = "tap") -> String {
    switch kind {
    case "pet": return ["*happy sigh* 💛", "Best human. Ever.", "Mmm… more pets, please.", "*leans in*"].randomElement()!
    case "sneeze": return ["Ah… ah… ACHOO!", "Achoo! Who shook the room?!"].randomElement()!
    case "fetch": return ["Got it! Throw again?", "Fetched! Good dog? GOOD DOG."].randomElement()!
    default: break
    }
    let t = plainTitle(c.title)
    let sl = k.sleeps == 1 ? "1 more sleep" : "\(k.sleeps) more sleeps"
    let lines: [String]
    switch k.stage {
    case .nap: lines = ["Zzz… \(sl)… zzz…", "*snore* five more minutes…", "Mmf. Wake me for \(t)."]
    case .curious: lines = ["Is it \(t) yet? \(sl)!", "*head tilt* \(sl)?", "Ooh! You came to visit!"]
    case .waiting: lines = ["I'm by the door! \(sl)!!", "Woof! \(k.daysCeil) days to go!", "Still waiting… tail says hi."]
    case .packing: lines = ["Packed! \(sl)!!", "Did you zip the suitcase? \(sl)!", "Almost time!! Almost time!!"]
    case .zoomies: lines = ["ZOOOOM! Almost here!!", "\(k.parts.h + (k.parts.d > 0 ? 24 : 0)) hours!! I can't sit still!!", "WOOF WOOF WOOF!"]
    case .today: lines = ["IT'S TODAY!!! 🎉", "BEST. DAY. EVER.", "WOOF! \(t.uppercased())!"]
    case .memory: lines = ["That was the best. 💛", "I remember \(t)!", "Can we do it again?"]
    }
    return lines.randomElement()!
}

/* ---------- dates for display ---------- */

func fmtInstant(_ date: Date, _ zone: TimeZone, allDay: Bool, noYear: Bool = false) -> String {
    let f = DateFormatter(); f.timeZone = zone; f.locale = Locale.current
    f.setLocalizedDateFormatFromTemplate(noYear ? "EEEMMMd" : "EEEMMMdyyyy")
    var s = f.string(from: date)
    if !allDay {
        let t = DateFormatter(); t.timeZone = zone; t.locale = Locale.current; t.timeStyle = .short; t.dateStyle = .none
        s += " · " + t.string(from: date)
    }
    return s
}

func tzLabel(_ id: String) -> String {
    let z = zoneOf(id)
    let city = id.split(separator: "/").last.map { $0.replacingOccurrences(of: "_", with: " ") } ?? id
    let off = z.secondsFromGMT(for: Date())
    let h = abs(off) / 3600, m = abs(off) % 3600 / 60
    return "\(city) (GMT\(off < 0 ? "-" : "+")\(h)\(m != 0 ? ":" + String(format: "%02d", m) : ""))"
}

func whenText(_ c: Countdown, _ k: Computed) -> (String, String?) {
    let dest = fmtInstant(k.target, k.zone, allDay: c.allDay)
    let here = TimeZone.current
    let same = c.allDay || k.zone.secondsFromGMT(for: k.target) == here.secondsFromGMT(for: k.target)
    return (dest, same ? nil : fmtInstant(k.target, here, allDay: false))
}

/* ---------- reminders & samples ---------- */

func defaultReminders(_ type: String) -> [Reminder] {
    var r = [
        Reminder(offsetMinutes: 100 * 1440, label: "100 days to go", enabled: true), Reminder(offsetMinutes: 50 * 1440, label: "50 days to go", enabled: true),
        Reminder(offsetMinutes: 30 * 1440, label: "30 days to go", enabled: true), Reminder(offsetMinutes: 7 * 1440, label: "One week to go", enabled: true),
        Reminder(offsetMinutes: 1440, label: "Tomorrow (zoomies!)", enabled: true), Reminder(offsetMinutes: 0, label: "Morning of", enabled: true),
    ]
    if type == "intl_trip" { r.append(Reminder(offsetMinutes: 90 * 1440, label: "Passport check · 90 days", enabled: true, passport: true)) }
    return r
}

struct PlannedReminder { var id: String; var title: String; var body: String; var at: Date }

/// The reminder schedule: at most one per local day, only in the future, capped at 60 (iOS allows 64 pending)
func planReminders(_ list: [Countdown], now: Date = Date()) -> [PlannedReminder] {
    var out: [PlannedReminder] = []
    for c in list where !c.archived {
        let k = compute(c, now: now)
        if k.phase == .past { continue }
        let title = plainTitle(c.title), name = c.dog.name
        for n in c.reminders where n.enabled {
            let at: Date
            if n.offsetMinutes == 0 {
                var cal = gregorian(k.zone); cal.timeZone = k.zone
                let p = cal.dateComponents([.year, .month, .day], from: k.target)
                at = Calendar.current.date(from: DateComponents(year: p.year, month: p.month, day: p.day, hour: 9)) ?? k.target
            } else { at = k.target.addingTimeInterval(-Double(n.offsetMinutes) * MIN) }
            if at <= now.addingTimeInterval(MIN) || at > now.addingTimeInterval(400 * DAY) { continue }
            let d = Int((Double(n.offsetMinutes) / 1440).rounded())
            let body: String
            if n.passport == true { body = "🛂 Quick check — are your passports valid 6+ months past the trip?" }
            else if n.offsetMinutes == 0 { body = "🎉 IT'S TODAY! \(name) is losing it." }
            else if d == 1 { body = "⚡ Zoomies activated. Tomorrow is \(title)!" }
            else if d == 7 { body = "🎒 \(name) started packing. One week until \(title)!" }
            else { body = "🐕 \(name) just counted: \(d) days until \(title)!" }
            out.append(PlannedReminder(id: "\(c.id):\(n.offsetMinutes)", title: "\(name) · \(c.title)", body: body, at: at))
        }
    }
    out.sort { $0.at < $1.at }
    var seen = Set<DateComponents>(); var kept: [PlannedReminder] = []
    for r in out {
        let day = Calendar.current.dateComponents([.year, .month, .day], from: r.at)
        if seen.insert(day).inserted { kept.append(r) }
    }
    return Array(kept.prefix(60))
}

func typeAccent(_ type: String) -> String {
    switch type {
    case "intl_trip": return "#5DADE8"; case "vacation": return "#3FBFA8"; case "birthday": return "#FF7DAA"
    case "anniversary": return "#EE8FA0"; case "holiday": return "#3E9C6C"; default: return "#E8A15C"
    }
}

func makeSamples(now: Date = Date()) -> [Countdown] {
    let here = Calendar.current
    func wall(_ date: Date, _ tz: String, _ h: Int, _ mi: Int) -> String {
        var cal = Calendar(identifier: .gregorian); cal.timeZone = zoneOf(tz)
        let p = cal.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02dT%02d:%02d", p.year!, p.month!, p.day!, h, mi)
    }
    func ymd(_ date: Date, yearShift: Int = 0, h: Int = 0, mi: Int = 0) -> String {
        let p = here.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02dT%02d:%02d", p.year! + yearShift, p.month!, p.day!, h, mi)
    }
    func mk(_ key: String, _ title: String, _ type: String, _ targetAt: String, since: Double, dog: Dog, tz: String = TimeZone.current.identifier, allDay: Bool = false,
            yearly: Bool = false, mode: DisplayMode = .full, notes: String = "", checks: [CheckItem] = [], dest: Destination? = nil, person: Person? = nil, accent: String? = nil) -> Countdown {
        Countdown(id: "sample-\(key)", title: title, type: type, targetAt: targetAt, timeZone: tz, allDay: allDay, yearly: yearly,
                  createdAt: now.addingTimeInterval(-since * DAY), dog: dog, accent: accent ?? typeAccent(type), displayMode: mode, notes: notes,
                  checklist: checks, destination: dest, person: person, reminders: defaultReminders(type), archived: false, photos: [], sample: true)
    }
    let year = here.component(.year, from: now)
    let nowHour = here.component(.hour, from: now)
    return [
        mk("japan", "Japan 🇯🇵", "intl_trip", wall(now.addingTimeInterval(190 * DAY), "Asia/Tokyo", 9, 30), since: 30, dog: Dog(breed: "shiba", name: "Miso"), tz: "Asia/Tokyo",
           notes: "Book the ryokan. Learn how to say \"good dog\" in Japanese.", checks: [CheckItem(id: "a", text: "Renew passport", done: true), CheckItem(id: "b", text: "JR Pass", done: false), CheckItem(id: "c", text: "Portable Wi-Fi", done: false)],
           dest: Destination(country: "Japan", city: "Tokyo", flag: "🇯🇵")),
        mk("mia", "Mia's birthday", "birthday", ymd(now.addingTimeInterval(52 * DAY), yearShift: -30), since: 40, dog: Dog(breed: "corgi", name: "Biscuit"), allDay: true, yearly: true,
           mode: .days, notes: "Gift ideas: pottery class, the blue scarf.", person: Person(name: "Mia", birthYear: here.component(.year, from: now.addingTimeInterval(52 * DAY)) - 30)),
        mk("bali", "Bali getaway", "vacation", wall(now.addingTimeInterval(28 * DAY), "Asia/Makassar", 14, 0), since: 52, dog: Dog(breed: "golden", name: "Sunny"), tz: "Asia/Makassar", mode: .sleeps,
           checks: [CheckItem(id: "a", text: "Sunscreen", done: true), CheckItem(id: "b", text: "Reef-safe snorkel gear", done: false)], dest: Destination(country: "Indonesia", city: "Bali", flag: "🇮🇩")),
        mk("anniv", "Our anniversary", "anniversary", ymd(now.addingTimeInterval(5 * DAY), yearShift: -3, h: 19), since: 60, dog: Dog(breed: "dachshund", name: "Noodle"), yearly: true,
           mode: .weeks, notes: "Book the little place with the candles."),
        // tomorrow at (current hour - 2): always inside the last 24 h but not "today"
        mk("ski", "Ski weekend", "vacation", ymd(now.addingTimeInterval(DAY), h: max(0, nowHour - 2)), since: 18, dog: Dog(breed: "husky", name: "Blizzard"), accent: "#5DADE8"),
        mk("dad", "Dad's birthday", "birthday", ymd(now, yearShift: -61), since: 21, dog: Dog(breed: "mutt", name: "Lucky", furColor: "#B98A62", ears: "floppy"), allDay: true, yearly: true,
           person: Person(name: "Dad", birthYear: year - 61)),
        mk("lisbon", "Lisbon 🇵🇹", "intl_trip", wall(now.addingTimeInterval(-30 * DAY), "Europe/Lisbon", 11, 0), since: 100, dog: Dog(breed: "golden", name: "Sunny"), tz: "Europe/Lisbon",
           notes: "Pastéis de nata at midnight. Worth it.", dest: Destination(country: "Portugal", city: "Lisbon", flag: "🇵🇹")),
    ]
}
