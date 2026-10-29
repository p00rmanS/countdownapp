import Foundation

/*
 Model.swift - the data the user owns. It reads and writes the same JSON as the web and Android apps, so a backup
 made on one platform restores on another.
*/

enum DisplayMode: String, CaseIterable, Codable {
    case full, days, sleeps, weeks
    var label: String {
        switch self { case .full: return "Full timer"; case .days: return "Days"; case .sleeps: return "Sleeps"; case .weeks: return "Weeks" }
    }
}

struct Dog: Equatable {
    var breed: String
    var name: String
    var furColor: String? = nil   // mutt only: "#RRGGBB"
    var ears: String? = nil       // mutt only: floppy | pointy | tall | long
    var sex: String = ""           // "girl" (wears a bow) | "boy" | ""

    /// key of the exported drawing: "golden" or "mutt-floppy"
    var variant: String { breed == "mutt" ? "mutt-\(ears ?? "floppy")" : breed }
}

extension Dog: Codable {
    private enum K: String, CodingKey { case breed, name, colors, ears, sex }
    private struct Colors: Codable { var fur: String? }
    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: K.self)
        breed = try c.decode(String.self, forKey: .breed)
        name = try c.decode(String.self, forKey: .name)
        furColor = try c.decodeIfPresent(Colors.self, forKey: .colors)?.fur
        ears = try c.decodeIfPresent(String.self, forKey: .ears)
        let sx = try c.decodeIfPresent(String.self, forKey: .sex) ?? ""
        sex = (sx == "girl" || sx == "boy") ? sx : ""
    }
    func encode(to e: Encoder) throws {
        var c = e.container(keyedBy: K.self)
        try c.encode(breed, forKey: .breed); try c.encode(name, forKey: .name)
        if let f = furColor { try c.encode(Colors(fur: f), forKey: .colors) }
        try c.encodeIfPresent(ears, forKey: .ears)
        if !sex.isEmpty { try c.encode(sex, forKey: .sex) }
    }
}

struct CheckItem: Codable, Equatable, Identifiable { var id: String; var text: String; var done: Bool }
struct Reminder: Codable, Equatable { var offsetMinutes: Int; var label: String; var enabled: Bool; var passport: Bool? = nil }
struct Destination: Codable, Equatable { var country: String; var city: String?; var flag: String }
struct Person: Codable, Equatable { var name: String; var birthYear: Int? }

struct Countdown: Identifiable, Equatable {
    var id: String
    var title: String
    var type: String
    var targetAt: String            // wall clock "2027-03-14T09:30" in `timeZone`
    var timeZone: String
    var allDay: Bool
    var yearly: Bool
    var createdAt: Date             // start of the progress bar
    var dog: Dog
    var accent: String
    var displayMode: DisplayMode
    var notes: String = ""
    var checklist: [CheckItem] = []
    var destination: Destination? = nil
    var person: Person? = nil
    var reminders: [Reminder] = []
    var archived: Bool = false
    var photos: [String] = []       // file names in the app's photo folder
    var sample: Bool = false
    var pinned: Bool = false        // stays at the top of Home
    var monthly: Bool = false       // repeats every month (yearly is the other repeat)
    var weekly: Bool = false        // repeats every week
}

extension Countdown: Codable {
    private enum K: String, CodingKey {
        case id, title, type, targetAt, timeZone, allDay, recurrence, createdAt, dog, accent, displayMode, notes, checklist
        case destination, person, notifications, archived, memoryPhotos, sample, pinned
    }
    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: K.self)
        id = try c.decode(String.self, forKey: .id)
        title = try c.decode(String.self, forKey: .title)
        type = try c.decode(String.self, forKey: .type)
        targetAt = try c.decode(String.self, forKey: .targetAt)
        timeZone = try c.decodeIfPresent(String.self, forKey: .timeZone) ?? TimeZone.current.identifier
        allDay = try c.decodeIfPresent(Bool.self, forKey: .allDay) ?? false
        let rec = try c.decodeIfPresent(String.self, forKey: .recurrence)
        yearly = rec == "yearly"; monthly = rec == "monthly"; weekly = rec == "weekly"
        let iso = try c.decodeIfPresent(String.self, forKey: .createdAt)
        createdAt = iso.flatMap { isoParse($0) } ?? Date()
        dog = try c.decode(Dog.self, forKey: .dog)
        accent = try c.decodeIfPresent(String.self, forKey: .accent) ?? "#E8A15C"
        displayMode = DisplayMode(rawValue: (try c.decodeIfPresent(String.self, forKey: .displayMode)) ?? "") ?? .full
        notes = try c.decodeIfPresent(String.self, forKey: .notes) ?? ""
        checklist = try c.decodeIfPresent([CheckItem].self, forKey: .checklist) ?? []
        destination = try c.decodeIfPresent(Destination.self, forKey: .destination)
        person = try c.decodeIfPresent(Person.self, forKey: .person)
        reminders = try c.decodeIfPresent([Reminder].self, forKey: .notifications) ?? []
        archived = try c.decodeIfPresent(Bool.self, forKey: .archived) ?? false
        // web backups embed photos as data: URLs; only keep file names
        photos = (try c.decodeIfPresent([String].self, forKey: .memoryPhotos) ?? []).filter { !$0.hasPrefix("data:") && !$0.isEmpty }
        sample = try c.decodeIfPresent(Bool.self, forKey: .sample) ?? false
        pinned = try c.decodeIfPresent(Bool.self, forKey: .pinned) ?? false
        try sanitize()
    }

    /// untrusted data (shared links, backup files): only known-good shapes survive. Throws if it is not a usable countdown.
    private mutating func sanitize() throws {
        let types: Set<String> = ["intl_trip", "vacation", "birthday", "anniversary", "holiday", "gotcha", "vet", "custom"]
        let breeds: Set<String> = ["scott", "golden", "corgi", "shiba", "dachshund", "husky", "bulldog", "bully", "labrador", "poodle", "beagle", "pug", "shepherd", "chihuahua", "rottweiler", "doberman", "pomeranian", "collie", "frenchie", "yorkie", "shihtzu", "boxer", "cocker", "dalmatian", "samoyed", "greatdane", "stbernard", "bernese", "aussie", "malamute", "akita", "chow", "schnauzer", "westie", "basset", "greyhound", "jackrussell", "cavalier", "goldendoodle", "mutt"]
        let ears: Set<String> = ["floppy", "pointy", "tall", "long"]
        let hex = try! NSRegularExpression(pattern: "^#[0-9A-Fa-f]{6}$")
        let wall = try! NSRegularExpression(pattern: "^\\d{4}-\\d{2}-\\d{2}(T\\d{2}:\\d{2})?$")
        func ok(_ r: NSRegularExpression, _ s: String) -> Bool { r.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)) != nil }
        title = String(title.trimmingCharacters(in: .whitespaces).prefix(80))
        guard !title.isEmpty, ok(wall, targetAt) else { throw DecodingError.dataCorrupted(.init(codingPath: [], debugDescription: "not a countdown")) }
        id = String(id.filter { $0.isLetter || $0.isNumber || $0 == "-" || $0 == "_" }.prefix(64))
        if id.isEmpty { id = newId() }
        if !types.contains(type) { type = "custom" }
        if TimeZone(identifier: timeZone) == nil { timeZone = TimeZone.current.identifier }
        if !ok(hex, accent) { accent = "#E8A15C" }
        notes = String(notes.prefix(2000))
        if !breeds.contains(dog.breed) { dog.breed = "mutt" }
        dog.name = String(dog.name.trimmingCharacters(in: .whitespaces).prefix(30)); if dog.name.isEmpty { dog.name = "Buddy" }
        if let f = dog.furColor, !ok(hex, f) { dog.furColor = nil }
        if let e = dog.ears, !ears.contains(e) { dog.ears = nil }
        checklist = Array(checklist.prefix(100)).map { var i = $0; i.text = String(i.text.prefix(200)); return i }
        reminders = Array(reminders.prefix(12))
        photos = Array(photos.filter { isSafePhotoName($0) }.prefix(6))
        if var d = destination { d.country = String(d.country.prefix(60)); d.city = d.city.map { String($0.prefix(60)) }; d.flag = String(d.flag.prefix(8)); destination = d }
        if var p = person { p.name = String(p.name.prefix(40)); person = p }
    }
    func encode(to e: Encoder) throws {
        var c = e.container(keyedBy: K.self)
        try c.encode(id, forKey: .id); try c.encode(title, forKey: .title); try c.encode(type, forKey: .type)
        try c.encode(targetAt, forKey: .targetAt); try c.encode(timeZone, forKey: .timeZone); try c.encode(allDay, forKey: .allDay)
        try c.encode(yearly ? "yearly" : (monthly ? "monthly" : (weekly ? "weekly" : "none")), forKey: .recurrence)
        try c.encode(isoFormat(createdAt), forKey: .createdAt)
        try c.encode(dog, forKey: .dog); try c.encode(accent, forKey: .accent); try c.encode(displayMode.rawValue, forKey: .displayMode)
        try c.encode(notes, forKey: .notes); try c.encode(checklist, forKey: .checklist)
        try c.encodeIfPresent(destination, forKey: .destination); try c.encodeIfPresent(person, forKey: .person)
        try c.encode(reminders, forKey: .notifications); try c.encode(archived, forKey: .archived)
        try c.encode(photos, forKey: .memoryPhotos); try c.encode(sample, forKey: .sample); try c.encode(pinned, forKey: .pinned)
    }
}

/// photo files are named <uuid>.jpg; anything else (e.g. "../../x") could point outside the photo folder
func isSafePhotoName(_ s: String) -> Bool { s.range(of: "^[A-Za-z0-9-]{1,64}\\.jpg$", options: .regularExpression) != nil }

struct Settings: Equatable {
    var displayMode: DisplayMode = .full
    var haptics = true
    var sound = false
    var reduceMotion = "system"      // system | on | off
    var theme = "system"             // system | light | dark
    var notifications = false
    var profileName = ""
    var profilePhoto = ""            // file name in the photo folder
    var parentTitle = ""             // what the dog calls the person: mama | papa | parent
    var needsOn = true               // the dog gets hungry and thirsty
    var careReminders = false        // a daily check-in notification
    var careTime = "18:00"
    var dogName = ""
    var dogBirthday = ""             // YYYY-MM-DD
    var weightUnit = "kg"            // kg | lb
}

extension Settings: Codable {
    private enum K: String, CodingKey { case displayMode, haptics, sound, motion, theme, notifications, profileName, profilePhoto, parentTitle, needsOn, careReminders, careTime, dogName, dogBirthday, weightUnit }
    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: K.self)
        displayMode = DisplayMode(rawValue: (try c.decodeIfPresent(String.self, forKey: .displayMode)) ?? "") ?? .full
        haptics = try c.decodeIfPresent(Bool.self, forKey: .haptics) ?? true
        sound = try c.decodeIfPresent(Bool.self, forKey: .sound) ?? false
        reduceMotion = try c.decodeIfPresent(String.self, forKey: .motion) ?? "system"
        theme = try c.decodeIfPresent(String.self, forKey: .theme) ?? "system"
        notifications = try c.decodeIfPresent(Bool.self, forKey: .notifications) ?? false
        profileName = String((try c.decodeIfPresent(String.self, forKey: .profileName) ?? "").prefix(30))
        let pp = try c.decodeIfPresent(String.self, forKey: .profilePhoto) ?? ""
        profilePhoto = isSafePhotoName(pp) ? pp : ""
        let pt = try c.decodeIfPresent(String.self, forKey: .parentTitle) ?? ""
        parentTitle = ["mama", "papa", "parent"].contains(pt) ? pt : ""
        needsOn = try c.decodeIfPresent(Bool.self, forKey: .needsOn) ?? true
        careReminders = try c.decodeIfPresent(Bool.self, forKey: .careReminders) ?? false
        let ct = try c.decodeIfPresent(String.self, forKey: .careTime) ?? "18:00"
        careTime = ["08:00", "12:00", "18:00", "21:00"].contains(ct) ? ct : "18:00"
        dogName = String((try c.decodeIfPresent(String.self, forKey: .dogName) ?? "").prefix(30))
        let db = try c.decodeIfPresent(String.self, forKey: .dogBirthday) ?? ""
        dogBirthday = dogAgeText(db) != nil ? db : ""
        weightUnit = (try c.decodeIfPresent(String.self, forKey: .weightUnit)) == "lb" ? "lb" : "kg"
    }
    func encode(to e: Encoder) throws {
        var c = e.container(keyedBy: K.self)
        try c.encode(displayMode.rawValue, forKey: .displayMode); try c.encode(haptics, forKey: .haptics); try c.encode(sound, forKey: .sound)
        try c.encode(reduceMotion, forKey: .motion); try c.encode(theme, forKey: .theme); try c.encode(notifications, forKey: .notifications)
        try c.encode(profileName, forKey: .profileName); try c.encode(profilePhoto, forKey: .profilePhoto); try c.encode(parentTitle, forKey: .parentTitle)
        try c.encode(needsOn, forKey: .needsOn); try c.encode(careReminders, forKey: .careReminders); try c.encode(careTime, forKey: .careTime); try c.encode(dogName, forKey: .dogName); try c.encode(dogBirthday, forKey: .dogBirthday); try c.encode(weightUnit, forKey: .weightUnit)
    }
}

/// one entry in the care log: vet | vaccine | weight | meds | note. Weight is always stored in kg.
struct LogEntry: Identifiable, Equatable, Codable {
    var id: String
    var kind: String
    var date: String
    var note: String
    var weightKg: Double?
    private enum K: String, CodingKey { case id, kind, date, note, weight }
    init(id: String, kind: String, date: String, note: String, weightKg: Double? = nil) { self.id = id; self.kind = kind; self.date = date; self.note = note; self.weightKg = weightKg }
    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: K.self)
        let k = try c.decode(String.self, forKey: .kind)
        guard ["vet", "vaccine", "weight", "meds", "note"].contains(k) else { throw DecodingError.dataCorrupted(.init(codingPath: [], debugDescription: "bad kind")) }
        let dt = try c.decode(String.self, forKey: .date)
        guard dt.range(of: "^\\d{4}-\\d{2}-\\d{2}$", options: .regularExpression) != nil else { throw DecodingError.dataCorrupted(.init(codingPath: [], debugDescription: "bad date")) }
        var w: Double? = nil
        if k == "weight" {
            let raw = try c.decodeIfPresent(Double.self, forKey: .weight) ?? 0
            guard raw > 0 && raw < 500 else { throw DecodingError.dataCorrupted(.init(codingPath: [], debugDescription: "bad weight")) }
            w = (raw * 10).rounded() / 10
        }
        let rid = String((try c.decodeIfPresent(String.self, forKey: .id) ?? "").filter { $0.isLetter || $0.isNumber || $0 == "-" || $0 == "_" }.prefix(64))
        id = rid.isEmpty ? UUID().uuidString : rid
        kind = k; date = dt; weightKg = w
        note = String((try c.decodeIfPresent(String.self, forKey: .note) ?? "").prefix(200))
    }
    func encode(to e: Encoder) throws {
        var c = e.container(keyedBy: K.self)
        try c.encode(id, forKey: .id); try c.encode(kind, forKey: .kind); try c.encode(date, forKey: .date); try c.encode(note, forKey: .note)
        try c.encodeIfPresent(weightKg, forKey: .weight)
    }
}

struct AppData: Equatable {
    var onboarded = false
    var countdowns: [Countdown] = []
    var celebrated: Set<String> = []
    var settings = Settings()
    var log: [LogEntry] = []
}

extension AppData: Codable {
    private enum K: String, CodingKey { case onboarded, countdowns, celebrated, settings, data, log }
    init(from d: Decoder) throws {
        var c = try d.container(keyedBy: K.self)
        if c.contains(.data) { c = try c.nestedContainer(keyedBy: K.self, forKey: .data) }   // backup wrapper
        onboarded = try c.decodeIfPresent(Bool.self, forKey: .onboarded) ?? false
        countdowns = try c.decode([Countdown].self, forKey: .countdowns)
        celebrated = Set((try c.decodeIfPresent([String: Int].self, forKey: .celebrated) ?? [:]).keys)
        settings = try c.decodeIfPresent(Settings.self, forKey: .settings) ?? Settings()
        // bad entries are dropped one by one, never the whole log
        struct Lenient: Decodable { let value: LogEntry?; init(from d: Decoder) throws { value = try? LogEntry(from: d) } }
        log = Array(((try? c.decodeIfPresent([Lenient].self, forKey: .log)) ?? []).compactMap { $0.value }.suffix(500))
    }
    func encode(to e: Encoder) throws {
        var c = e.container(keyedBy: K.self)
        try c.encode(onboarded, forKey: .onboarded); try c.encode(countdowns, forKey: .countdowns)
        try c.encode(Dictionary(uniqueKeysWithValues: celebrated.map { ($0, 1) }), forKey: .celebrated)
        try c.encode(settings, forKey: .settings)
        try c.encode(log, forKey: .log)
    }
}

func newId() -> String { UUID().uuidString.lowercased() }

private let isoFormatter: ISO8601DateFormatter = {
    let f = ISO8601DateFormatter(); f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]; return f
}()
private let isoPlain = ISO8601DateFormatter()
func isoParse(_ s: String) -> Date? { isoFormatter.date(from: s) ?? isoPlain.date(from: s) }
func isoFormat(_ d: Date) -> String { isoFormatter.string(from: d) }
