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

    /// key of the exported drawing: "golden" or "mutt-floppy"
    var variant: String { breed == "mutt" ? "mutt-\(ears ?? "floppy")" : breed }
}

extension Dog: Codable {
    private enum K: String, CodingKey { case breed, name, colors, ears }
    private struct Colors: Codable { var fur: String? }
    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: K.self)
        breed = try c.decode(String.self, forKey: .breed)
        name = try c.decode(String.self, forKey: .name)
        furColor = try c.decodeIfPresent(Colors.self, forKey: .colors)?.fur
        ears = try c.decodeIfPresent(String.self, forKey: .ears)
    }
    func encode(to e: Encoder) throws {
        var c = e.container(keyedBy: K.self)
        try c.encode(breed, forKey: .breed); try c.encode(name, forKey: .name)
        if let f = furColor { try c.encode(Colors(fur: f), forKey: .colors) }
        try c.encodeIfPresent(ears, forKey: .ears)
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
}

extension Countdown: Codable {
    private enum K: String, CodingKey {
        case id, title, type, targetAt, timeZone, allDay, recurrence, createdAt, dog, accent, displayMode, notes, checklist
        case destination, person, notifications, archived, memoryPhotos, sample
    }
    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: K.self)
        id = try c.decode(String.self, forKey: .id)
        title = try c.decode(String.self, forKey: .title)
        type = try c.decode(String.self, forKey: .type)
        targetAt = try c.decode(String.self, forKey: .targetAt)
        timeZone = try c.decodeIfPresent(String.self, forKey: .timeZone) ?? TimeZone.current.identifier
        allDay = try c.decodeIfPresent(Bool.self, forKey: .allDay) ?? false
        yearly = (try c.decodeIfPresent(String.self, forKey: .recurrence)) == "yearly"
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
    }
    func encode(to e: Encoder) throws {
        var c = e.container(keyedBy: K.self)
        try c.encode(id, forKey: .id); try c.encode(title, forKey: .title); try c.encode(type, forKey: .type)
        try c.encode(targetAt, forKey: .targetAt); try c.encode(timeZone, forKey: .timeZone); try c.encode(allDay, forKey: .allDay)
        try c.encode(yearly ? "yearly" : "none", forKey: .recurrence)
        try c.encode(isoFormat(createdAt), forKey: .createdAt)
        try c.encode(dog, forKey: .dog); try c.encode(accent, forKey: .accent); try c.encode(displayMode.rawValue, forKey: .displayMode)
        try c.encode(notes, forKey: .notes); try c.encode(checklist, forKey: .checklist)
        try c.encodeIfPresent(destination, forKey: .destination); try c.encodeIfPresent(person, forKey: .person)
        try c.encode(reminders, forKey: .notifications); try c.encode(archived, forKey: .archived)
        try c.encode(photos, forKey: .memoryPhotos); try c.encode(sample, forKey: .sample)
    }
}

struct Settings: Equatable {
    var displayMode: DisplayMode = .full
    var haptics = true
    var sound = false
    var reduceMotion = "system"      // system | on | off
    var theme = "system"             // system | light | dark
    var notifications = false
    var profileName = ""
    var profilePhoto = ""            // file name in the photo folder
}

extension Settings: Codable {
    private enum K: String, CodingKey { case displayMode, haptics, sound, motion, theme, notifications, profileName, profilePhoto }
    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: K.self)
        displayMode = DisplayMode(rawValue: (try c.decodeIfPresent(String.self, forKey: .displayMode)) ?? "") ?? .full
        haptics = try c.decodeIfPresent(Bool.self, forKey: .haptics) ?? true
        sound = try c.decodeIfPresent(Bool.self, forKey: .sound) ?? false
        reduceMotion = try c.decodeIfPresent(String.self, forKey: .motion) ?? "system"
        theme = try c.decodeIfPresent(String.self, forKey: .theme) ?? "system"
        notifications = try c.decodeIfPresent(Bool.self, forKey: .notifications) ?? false
        profileName = try c.decodeIfPresent(String.self, forKey: .profileName) ?? ""
        profilePhoto = try c.decodeIfPresent(String.self, forKey: .profilePhoto) ?? ""
    }
    func encode(to e: Encoder) throws {
        var c = e.container(keyedBy: K.self)
        try c.encode(displayMode.rawValue, forKey: .displayMode); try c.encode(haptics, forKey: .haptics); try c.encode(sound, forKey: .sound)
        try c.encode(reduceMotion, forKey: .motion); try c.encode(theme, forKey: .theme); try c.encode(notifications, forKey: .notifications)
        try c.encode(profileName, forKey: .profileName); try c.encode(profilePhoto, forKey: .profilePhoto)
    }
}

struct AppData: Equatable {
    var onboarded = false
    var countdowns: [Countdown] = []
    var celebrated: Set<String> = []
    var settings = Settings()
}

extension AppData: Codable {
    private enum K: String, CodingKey { case onboarded, countdowns, celebrated, settings, data }
    init(from d: Decoder) throws {
        var c = try d.container(keyedBy: K.self)
        if c.contains(.data) { c = try c.nestedContainer(keyedBy: K.self, forKey: .data) }   // backup wrapper
        onboarded = try c.decodeIfPresent(Bool.self, forKey: .onboarded) ?? false
        countdowns = try c.decode([Countdown].self, forKey: .countdowns)
        celebrated = Set((try c.decodeIfPresent([String: Int].self, forKey: .celebrated) ?? [:]).keys)
        settings = try c.decodeIfPresent(Settings.self, forKey: .settings) ?? Settings()
    }
    func encode(to e: Encoder) throws {
        var c = e.container(keyedBy: K.self)
        try c.encode(onboarded, forKey: .onboarded); try c.encode(countdowns, forKey: .countdowns)
        try c.encode(Dictionary(uniqueKeysWithValues: celebrated.map { ($0, 1) }), forKey: .celebrated)
        try c.encode(settings, forKey: .settings)
    }
}

func newId() -> String { UUID().uuidString.lowercased() }

private let isoFormatter: ISO8601DateFormatter = {
    let f = ISO8601DateFormatter(); f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]; return f
}()
private let isoPlain = ISO8601DateFormatter()
func isoParse(_ s: String) -> Date? { isoFormatter.date(from: s) ?? isoPlain.date(from: s) }
func isoFormat(_ d: Date) -> String { isoFormatter.string(from: d) }
