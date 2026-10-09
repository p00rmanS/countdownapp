import WidgetKit
import SwiftUI

/*
 PawcountWidget.swift - the home-screen and lock-screen widgets.

 They show the soonest countdown. Widgets can't run our animation engine, so the scene and dog are drawn once as a
 still with the app's own renderer (WidgetCard.swift). We give iOS a timeline with one entry per hour for the next day,
 so the number and the dog's mood keep changing even while the app is closed. Tapping opens that countdown.
*/

struct PawEntry: TimelineEntry {
    let date: Date
    let countdown: Countdown?
}

struct PawProvider: TimelineProvider {
    func placeholder(in context: Context) -> PawEntry { PawEntry(date: Date(), countdown: makeSamples().first) }

    func getSnapshot(in context: Context, completion: @escaping (PawEntry) -> Void) {
        completion(PawEntry(date: Date(), countdown: context.isPreview ? makeSamples().first : soonest(at: Date())))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<PawEntry>) -> Void) {
        let now = Date()
        let entries = (0..<24).map { h -> PawEntry in
            let d = now.addingTimeInterval(Double(h) * HOUR)
            return PawEntry(date: d, countdown: soonest(at: d))
        }
        completion(Timeline(entries: entries, policy: .atEnd))
    }

    private func soonest(at date: Date) -> Countdown? {
        SharedStore.load().countdowns.filter { !$0.archived }
            .map { ($0, compute($0, now: date)) }.filter { $0.1.phase != .past }
            .min { a, b in
                let ra = a.1.phase == .today ? 0 : 1, rb = b.1.phase == .today ? 0 : 1
                return ra != rb ? ra < rb : a.1.target < b.1.target
            }?.0
    }
}

struct PawWidgetView: View {
    @Environment(\.widgetFamily) var family
    let entry: PawEntry
    let art = Art()

    var body: some View {
        let c = entry.countdown
        let k = c.map { compute($0, now: entry.date) }
        Group {
            switch family {
            case .accessoryInline:
                Text(c.map { "🐾 \(plainTitle($0.title)): \(bigCount($0, k!))" } ?? "🐾 Pawcount")
            case .accessoryRectangular:
                VStack(alignment: .leading, spacing: 1) {
                    Text(c?.title ?? "Pawcount").font(.headline).lineLimit(1)
                    Text(c.map { bigCount($0, k!) } ?? "No countdowns yet").font(.title3.bold())
                    if let c, let k { Text(stageLabel(c, k)).font(.caption) }
                }
            case .systemMedium:
                WidgetCard(c: c, art: art, wide: true, now: entry.date)
            default:
                WidgetCard(c: c, art: art, wide: false, now: entry.date)
            }
        }
        .widgetURL(URL(string: c.map { "pawcount://detail/\($0.id)" } ?? "pawcount://home"))
        .containerBackgroundCompat()
    }
}

extension View {
    /// iOS 17 wants widgets to declare a container background; older systems just draw the view
    @ViewBuilder func containerBackgroundCompat() -> some View {
        if #available(iOSApplicationExtension 17.0, *) { self.containerBackground(Color(hex: "#FFF8EE"), for: .widget) } else { self }
    }
}

struct PawcountWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "PawcountWidget", provider: PawProvider()) { entry in
            PawWidgetView(entry: entry)
        }
        .configurationDisplayName("Pawcount")
        .description("Your soonest countdown, with the dog who can't wait.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular, .accessoryInline])
    }
}

@main
struct PawcountWidgetBundle: WidgetBundle {
    init() { PawFont.registerNames() }
    var body: some Widget { PawcountWidget() }
}
