import XCTest
import SwiftUI
@testable import Pawcount

/*
 Renders the widget card and the share card to PNG files so the CI can upload them as screenshots, and checks the
 share link round trip. (Widgets can't be screenshotted from a simulator script, so we draw their views directly.)
*/
@MainActor
final class RenderTests: XCTestCase {
    let art = Art()

    func outDir() -> URL {
        let d = URL(fileURLWithPath: ProcessInfo.processInfo.environment["PAW_SHOTS_DIR"] ?? NSTemporaryDirectory())
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }

    func write<V: View>(_ v: V, _ name: String, scale: CGFloat = 3) {
        let r = ImageRenderer(content: v.environment(\.paw, .light)); r.scale = scale
        guard let png = r.uiImage?.pngData() else { return XCTFail("could not render \(name)") }
        try? png.write(to: outDir().appendingPathComponent(name + ".png"))
    }

    func testWidgetAndShareCardsRender() {
        let samples = makeSamples()
        let dad = samples.first { $0.id == "sample-dad" }!, japan = samples.first { $0.id == "sample-japan" }!
        write(WidgetCard(c: dad, art: art).frame(width: 170, height: 170).clipShape(RoundedRectangle(cornerRadius: 22)), "widget-small")
        write(WidgetCard(c: japan, art: art, wide: true).frame(width: 364, height: 170).clipShape(RoundedRectangle(cornerRadius: 22)), "widget-medium")
        write(WidgetCard(c: nil, art: art).frame(width: 170, height: 170).clipShape(RoundedRectangle(cornerRadius: 22)), "widget-empty")
        write(ShareCard(c: dad, art: art), "share-card")
        XCTAssertNotNil(Share.cardImage(dad, art))
    }

    func testShareLinkRoundTrip() {
        let c = makeSamples().first { $0.id == "sample-japan" }!
        let link = Share.link(c).absoluteString
        let back = Share.parse(link)
        XCTAssertEqual(back?.title, c.title)
        XCTAssertEqual(back?.timeZone, c.timeZone)
        XCTAssertEqual(back?.dog, c.dog)
        XCTAssertNotEqual(back?.id, c.id)                  // a copy, not the same countdown
        XCTAssertNil(Share.parse("https://example.com/nothing"))
        // the app-scheme version and the bare code work too
        XCTAssertEqual(Share.parse("pawcount://import?d=\(Share.code(c))")?.title, c.title)
        XCTAssertEqual(Share.parse(Share.code(c))?.title, c.title)
    }
}
