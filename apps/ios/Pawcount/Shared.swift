import Foundation

/*
 Shared.swift - code used by both the app and the widget extension.

 The widget is a separate program, so it can only see the app's data if both live in a shared "App Group" folder.
 When the app isn't signed with that group (a simulator build, tests) we quietly fall back to the normal Documents
 folder so nothing breaks.
*/

enum SharedStore {
    static let group = "group.app.pawcount.countdown"

    private static var documents: URL { FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0] }

    /// where the app's single JSON data file lives
    static var fileURL: URL {
        let fm = FileManager.default
        guard let container = fm.containerURL(forSecurityApplicationGroupIdentifier: group) else { return documents.appendingPathComponent("pawcount.json") }
        let shared = container.appendingPathComponent("pawcount.json")
        // first launch after the widget was added: move the data over from the old place
        let old = documents.appendingPathComponent("pawcount.json")
        if !fm.fileExists(atPath: shared.path), fm.fileExists(atPath: old.path) { try? fm.copyItem(at: old, to: shared) }
        return shared
    }

    static func load() -> AppData {
        (try? Data(contentsOf: fileURL)).flatMap { try? JSONDecoder().decode(AppData.self, from: $0) } ?? AppData()
    }
}
