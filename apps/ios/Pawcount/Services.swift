import SwiftUI
import UIKit
import AVFoundation
import CoreMotion
import UserNotifications
import WidgetKit

/*
 Services.swift - everything that touches the phone rather than the screen:
   AppStore       app state, saved as one JSON file, plus the simple back stack
   Haptics        taps, purrs and the success buzz
   Sounds         barks and chimes, synthesised on the fly (no audio files)
   ShakeDetector  accelerometer jerk detector
   Reminders      schedules local notifications so they fire with the app closed
   Photos         memory photos kept in the app's own folder
*/

enum Route: Hashable {
    case welcome, home, memories, settings
    case detail(String)
    case flow(String?)
}

final class AppStore: ObservableObject {
    @Published var data: AppData
    @Published var stack: [Route]
    @Published var toast: String? = nil
    /// bumps every time something should rain confetti (the overlay watches this)
    @Published var confettiBurst = 0
    /// the companion picked on the welcome screens, so the create flow can start with it
    @Published var welcomeBreed = "scott"
    @Published var welcomeBreedChosen = false
    @Published var systemReduceMotion = UIAccessibility.isReduceMotionEnabled

    let art = Art()
    private let file: URL
    private var toastTask: Task<Void, Never>?

    init() {
        file = SharedStore.fileURL            // shared with the widget (App Group) when the app is signed for it
        let loaded = SharedStore.load()
        data = loaded
        stack = [loaded.onboarded || !loaded.countdowns.isEmpty ? .home : .welcome]
        PawFont.registerNames()
        // `-demo <screen>` launch argument: used by the CI to take screenshots of each screen with sample data
        if let demo = UserDefaults.standard.string(forKey: "demo") {
            data.onboarded = true; data.countdowns = makeSamples()
            switch demo {
            case "detail": stack = [.home, .detail("sample-dad")]
            case "detail-nap": stack = [.home, .detail("sample-japan")]
            case "detail-zoom": stack = [.home, .detail("sample-ski")]
            case "detail-memory": stack = [.home, .detail("sample-lisbon")]
            case "flow": stack = [.home, .flow(nil)]
            case "settings": stack = [.home, .settings]
            case "memories": stack = [.memories]
            case "welcome": data.onboarded = false; data.countdowns = []; stack = [.welcome]
            default: stack = [.home]
            }
        }
        NotificationCenter.default.addObserver(forName: UIAccessibility.reduceMotionStatusDidChangeNotification, object: nil, queue: .main) { [weak self] _ in
            self?.systemReduceMotion = UIAccessibility.isReduceMotionEnabled
        }
    }

    var route: Route { stack.last ?? .home }
    var settings: Settings { data.settings }
    var reduceMotion: Bool { settings.reduceMotion == "on" || (settings.reduceMotion == "system" && systemReduceMotion) }

    func push(_ r: Route) { stack.append(r) }
    func pop() { if stack.count > 1 { stack.removeLast() } else { stack = [.home] } }
    func goHome() { stack = [.home] }
    func tab(_ r: Route) { stack = [r] }
    func say(_ msg: String) {
        toast = msg
        toastTask?.cancel()
        toastTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 2_800_000_000)
            if !Task.isCancelled { self?.toast = nil }
        }
    }
    func confetti() { confettiBurst += 1 }

    /// change the data, save it, and re-plan the reminders
    func update(_ f: (inout AppData) -> Void) {
        var d = data; f(&d); data = d
        if let enc = try? JSONEncoder().encode(d) { try? enc.write(to: file, options: .atomic) }
        Reminders.reschedule(d)
        WidgetCenter.shared.reloadAllTimelines()     // keep home-screen widgets in step with what was just saved
    }
    func upsert(_ c: Countdown) { update { d in if let i = d.countdowns.firstIndex(where: { $0.id == c.id }) { d.countdowns[i] = c } else { d.countdowns.append(c) } } }
    func remove(_ id: String) { update { $0.countdowns.removeAll { $0.id == id } } }
    func setSettings(_ f: (inout Settings) -> Void) { update { f(&$0.settings) } }
    func loadSamples() { update { $0.onboarded = true; $0.countdowns = $0.countdowns.filter { !$0.sample } + makeSamples() } }
    /// pawcount://import?d=...  (a shared countdown)  or  pawcount://detail/<id>  (from a widget)
    func handle(url: URL) {
        guard url.scheme == "pawcount" else { return }
        if url.host == "import" {
            if let c = Share.parse(url.absoluteString) { upsert(c); update { $0.onboarded = true }; stack = [.home, .detail(c.id)]; say("Added to your countdowns 🐾") }
            else { say("That link isn’t a Pawcount countdown") }
        } else if url.host == "detail" {
            let id = url.lastPathComponent
            if countdown(id) != nil { stack = [.home, .detail(id)] }
        }
    }
    /// adds a countdown from pasted text; returns its id or nil
    func importShared(_ text: String) -> String? {
        guard let c = Share.parse(text) else { return nil }
        upsert(c); update { $0.onboarded = true }
        return c.id
    }
    func countdown(_ id: String) -> Countdown? { data.countdowns.first { $0.id == id } }

    func exportJSON() -> URL? {
        let enc = JSONEncoder(); enc.outputFormatting = [.prettyPrinted, .sortedKeys]
        struct Wrapper: Encodable { var app = "pawcount"; var version = 1; var data: AppData }
        guard let bytes = try? enc.encode(Wrapper(data: data)) else { return nil }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("pawcount-backup.json")
        try? bytes.write(to: url)
        return url
    }
    func importJSON(_ bytes: Data) -> Bool {
        guard let d = try? JSONDecoder().decode(AppData.self, from: bytes) else { return false }
        update { $0 = d }
        return true
    }
}

/* ------------------------------ haptics ------------------------------ */

enum Haptics {
    static func play(_ enabled: Bool, _ kind: String) {
        guard enabled else { return }
        switch kind {
        case "success": UINotificationFeedbackGenerator().notificationOccurred(.success)
        case "purr":
            let g = UIImpactFeedbackGenerator(style: .soft)
            for i in 0..<3 { DispatchQueue.main.asyncAfter(deadline: .now() + 0.05 * Double(i)) { g.impactOccurred(intensity: 0.8) } }
        case "soft": UIImpactFeedbackGenerator(style: .soft).impactOccurred()
        default: UIImpactFeedbackGenerator(style: .light).impactOccurred()    // tick
        }
    }
}

/* ------------------------------ sounds ------------------------------ */

enum Sounds {
    private static let rate = 22050.0
    private static let engine = AVAudioEngine()
    private static let player = AVAudioPlayerNode()
    private static var ready = false

    /// one tone: frequency (optionally sliding to `slide`), length in seconds, volume, saw or sine wave
    private static func tone(_ freq: Double, slide: Double?, secs: Double, vol: Double, saw: Bool) -> [Float] {
        let n = Int(rate * secs); var out = [Float](repeating: 0, count: n); var phase = 0.0
        for i in 0..<n {
            let t = Double(i) / Double(n)
            let f = slide.map { freq * pow($0 / freq, t) } ?? freq
            phase += 2 * .pi * f / rate
            let s = saw ? ((phase / .pi).truncatingRemainder(dividingBy: 2) - 1) : sin(phase)
            let env = min(1, Double(i) / 300) * exp(-3 * t)
            out[i] = Float(s * env * vol)
        }
        return out
    }

    private static func build(_ kind: String) -> [Float] {
        switch kind {
        case "bark": return tone(520, slide: 260, secs: 0.12, vol: 0.3, saw: true) + [Float](repeating: 0, count: Int(rate * 0.04)) + tone(480, slide: 240, secs: 0.14, vol: 0.28, saw: true)
        case "chime": return [523.0, 659, 784, 1047].flatMap { tone($0, slide: nil, secs: 0.25, vol: 0.3, saw: false) }
        default: return tone(1568, slide: nil, secs: 0.2, vol: 0.2, saw: false) + tone(1976, slide: nil, secs: 0.2, vol: 0.2, saw: false)   // jingle
        }
    }

    static func play(_ enabled: Bool, _ kind: String) {
        guard enabled else { return }
        DispatchQueue.global(qos: .userInitiated).async {
            let samples = build(kind)
            guard let fmt = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: rate, channels: 1, interleaved: false),
                  let buf = AVAudioPCMBuffer(pcmFormat: fmt, frameCapacity: AVAudioFrameCount(samples.count)) else { return }
            buf.frameLength = AVAudioFrameCount(samples.count)
            samples.withUnsafeBufferPointer { buf.floatChannelData![0].update(from: $0.baseAddress!, count: samples.count) }
            DispatchQueue.main.async {
                try? AVAudioSession.sharedInstance().setCategory(.ambient)
                if !ready { engine.attach(player); engine.connect(player, to: engine.mainMixerNode, format: fmt); ready = true }
                if !engine.isRunning { try? engine.start() }
                player.scheduleBuffer(buf, at: nil, options: .interrupts)
                player.play()
            }
        }
    }
}

/* ------------------------------ shake ------------------------------ */

/// Calls `onShake` when the phone is shaken: a big, sudden change between two accelerometer samples.
final class ShakeDetector {
    private let mm = CMMotionManager()
    private var prev: (Double, Double, Double)? = nil
    private var last = Date.distantPast
    var onShake: () -> Void = {}

    func start() {
        guard mm.isAccelerometerAvailable else { return }
        mm.accelerometerUpdateInterval = 1.0 / 50
        mm.startAccelerometerUpdates(to: .main) { [weak self] data, _ in
            guard let self, let a = data?.acceleration else { return }
            let cur = (a.x * 9.81, a.y * 9.81, a.z * 9.81)     // CoreMotion reports g; the threshold is in m/s²
            if let p = self.prev {
                let jerk = ((cur.0 - p.0) * (cur.0 - p.0) + (cur.1 - p.1) * (cur.1 - p.1) + (cur.2 - p.2) * (cur.2 - p.2)).squareRoot()
                if jerk > 18 && Date().timeIntervalSince(self.last) > 1.5 { self.last = Date(); self.onShake() }
            }
            self.prev = cur
        }
    }
    func stop() { mm.stopAccelerometerUpdates(); prev = nil }
}

/* ------------------------------ reminders ------------------------------ */

enum Reminders {
    private static let prefix = "pawcount."

    static func status(_ done: @escaping (UNAuthorizationStatus) -> Void) {
        UNUserNotificationCenter.current().getNotificationSettings { s in DispatchQueue.main.async { done(s.authorizationStatus) } }
    }

    static func request(_ done: @escaping (Bool) -> Void) {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { ok, _ in DispatchQueue.main.async { done(ok) } }
    }

    /// Remove what we scheduled before, then schedule the current plan (one per day, in the future).
    static func reschedule(_ data: AppData) {
        let center = UNUserNotificationCenter.current()
        center.getPendingNotificationRequests { pending in
            center.removePendingNotificationRequests(withIdentifiers: pending.map { $0.identifier }.filter { $0.hasPrefix(prefix) })
            guard data.settings.notifications else { return }
            for p in planReminders(data.countdowns) {
                let content = UNMutableNotificationContent()
                content.title = p.title; content.body = p.body; content.sound = .default
                let comps = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: p.at)
                let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
                center.add(UNNotificationRequest(identifier: prefix + p.id, content: content, trigger: trigger))
            }
        }
    }
}

/* ------------------------------ photos ------------------------------ */

enum Photos {
    private static var dir: URL {
        let d = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("photos")
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }

    /// Saves a picked photo scaled down so backups and memory stay small. Returns the file name.
    static func save(_ data: Data, maxSide: CGFloat = 960) -> String? {
        guard let img = UIImage(data: data) else { return nil }
        let scale = min(1, maxSide / max(img.size.width, img.size.height))
        let size = CGSize(width: img.size.width * scale, height: img.size.height * scale)
        let out = UIGraphicsImageRenderer(size: size).image { _ in img.draw(in: CGRect(origin: .zero, size: size)) }
        guard let jpg = out.jpegData(compressionQuality: 0.82) else { return nil }
        let name = newId() + ".jpg"
        try? jpg.write(to: dir.appendingPathComponent(name))
        return name
    }
    /// photo files are named <uuid>.jpg; anything else (e.g. "../../x") could point outside the photo folder
    static func isSafeName(_ s: String) -> Bool { s.range(of: "^[A-Za-z0-9-]{1,64}\\.jpg$", options: .regularExpression) != nil }
    static func image(_ name: String) -> UIImage? { isSafeName(name) ? UIImage(contentsOfFile: dir.appendingPathComponent(name).path) : nil }
    static func delete(_ name: String) { if isSafeName(name) { try? FileManager.default.removeItem(at: dir.appendingPathComponent(name)) } }
}

/* ------------------------------ alternate app icons ------------------------------ */

enum AppIcon {
    static let breeds = ["scott", "golden", "corgi", "shiba", "dachshund", "husky", "mutt"]
    /// the dog on the home screen right now ("AppIcon" itself is Scott)
    static var current: String { UIApplication.shared.alternateIconName.map { String($0.dropFirst("AppIcon-".count)) } ?? "scott" }
    static func set(_ breed: String, done: @escaping (Bool) -> Void) {
        guard UIApplication.shared.supportsAlternateIcons else { done(false); return }
        UIApplication.shared.setAlternateIconName(breed == "scott" ? nil : "AppIcon-\(breed)") { err in DispatchQueue.main.async { done(err == nil) } }
    }
}
