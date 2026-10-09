import SwiftUI

/*
 Share.swift - "share with your person".

 Two ways to share a countdown, neither needs an account or a server:
   1. A picture card (scene + dog + title + big number) for chats and stories.
   2. A link that carries the countdown itself (title, date, time zone, dog...). Opening it in Pawcount - on iPhone,
      Android or the web - adds the same countdown to the other person's app.
 (Live two-way sync needs a server; this is the no-account version and works today.)
*/

enum Share {
    static let web = "https://p00rmans.github.io/countdownapp/"
    private static let dropKeys = ["id", "createdAt", "archived", "memoryPhotos", "sample", "notifications"]

    /// compact, URL-safe text that contains the countdown
    static func code(_ c: Countdown) -> String {
        guard let data = try? JSONEncoder().encode(c), var obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return "" }
        dropKeys.forEach { obj.removeValue(forKey: $0) }
        obj["v"] = 1
        let json = (try? JSONSerialization.data(withJSONObject: obj)) ?? Data()
        return json.base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
    }
    static func link(_ c: Countdown) -> URL { URL(string: "\(web)#/import/\(code(c))")! }

    /// accepts a full link (web or pawcount://) or the bare code; returns a new countdown for this phone, or nil
    static func parse(_ text: String) -> Countdown? {
        var raw = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if let r = raw.range(of: "/import/", options: .backwards) { raw = String(raw[r.upperBound...]) }
        if let r = raw.range(of: "d=") { raw = String(raw[r.upperBound...]) }
        raw = raw.components(separatedBy: CharacterSet(charactersIn: "&#")).first ?? raw
        var b64 = raw.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        while b64.count % 4 != 0 { b64 += "=" }
        guard let data = Data(base64Encoded: b64), var obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        obj["id"] = newId(); obj["createdAt"] = isoFormat(Date())
        guard let fixed = try? JSONSerialization.data(withJSONObject: obj), var c = try? JSONDecoder().decode(Countdown.self, from: fixed) else { return nil }
        c.reminders = defaultReminders(c.type); c.sample = false; c.archived = false; c.photos = []
        return c
    }

    /// the picture card as an image (1080x1350)
    @MainActor static func cardImage(_ c: Countdown, _ art: Art) -> UIImage? {
        let r = ImageRenderer(content: ShareCard(c: c, art: art).environment(\.paw, .light))
        r.scale = 3
        return r.uiImage
    }
}

/// the share sheet for one countdown
struct ShareSheet: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.paw) var pc
    let c: Countdown
    @State var image: UIImage? = nil
    var body: some View {
        VStack(spacing: 12) {
            H("Share \(c.title)", 22, align: .center).padding(.top, 8)
            if let img = image {
                Image(uiImage: img).resizable().scaledToFit().frame(maxHeight: 300).clipShape(RoundedRectangle(cornerRadius: 18)).shadow(color: .black.opacity(0.15), radius: 8, y: 3)
                ShareLink(item: Image(uiImage: img), message: Text("\(c.dog.name) is counting down to \(plainTitle(c.title))! \(Share.link(c).absoluteString)"), preview: SharePreview(c.title, image: Image(uiImage: img))) {
                    shareRow("upload", "Share picture card")
                }.buttonStyle(.plain)
            }
            ShareLink(item: Share.link(c), message: Text("Count down with me! \(c.title)")) { shareRow("heart", "Share with your person") }.buttonStyle(.plain)
            Spacer(minLength: 0)
        }
        .padding(20).background(pc.bg.ignoresSafeArea())
        .onAppear { image = Share.cardImage(c, store.art) }
    }
    func shareRow(_ icon: String, _ text: String) -> some View {
        HStack(spacing: 14) { PawIcon(name: icon); Text(text).font(PawFont.body(17, 800)).foregroundColor(pc.ink); Spacer() }
            .padding(.horizontal, 18).frame(maxWidth: .infinity, minHeight: 56)
            .background(RoundedRectangle(cornerRadius: 18).fill(pc.surface).shadow(color: .black.opacity(0.08), radius: 4, y: 1))
    }
}
