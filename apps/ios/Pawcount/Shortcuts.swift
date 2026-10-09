import AppIntents
import Foundation

/*
 Shortcuts.swift - Siri and the Shortcuts app.
 "Hey Siri, how many days until my countdown in Pawcount?" answers with the soonest countdown, aloud, without opening
 the app. It reads the same data file the widget uses.
*/

@available(iOS 16.0, *)
struct SoonestCountdownIntent: AppIntent {
    static var title: LocalizedStringResource = "How long until my next countdown?"
    static var description = IntentDescription("Tells you how long is left until your soonest countdown.")

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let now = Date()
        let soonest = SharedStore.load().countdowns.filter { !$0.archived }
            .map { ($0, compute($0, now: now)) }.filter { $0.1.phase != .past }
            .min { a, b in (a.1.phase == .today ? 0 : 1, a.1.target) < (b.1.phase == .today ? 0 : 1, b.1.target) }
        guard let (c, k) = soonest else { return .result(dialog: "You don't have any countdowns yet. Open Pawcount to start one.") }
        if k.phase == .today { return .result(dialog: "\(plainTitle(c.title)) is today! \(c.dog.name) is losing it.") }
        return .result(dialog: "\(plainTitle(c.title)) is in \(spoken(k)). \(stageSentence(c, k)).")
    }
}

@available(iOS 16.4, *)
struct PawShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(intent: SoonestCountdownIntent(), phrases: ["How long until my next countdown in \(.applicationName)", "What's my next countdown in \(.applicationName)", "Check \(.applicationName)"],
                    shortTitle: "Next countdown", systemImageName: "pawprint.fill")
    }
}
