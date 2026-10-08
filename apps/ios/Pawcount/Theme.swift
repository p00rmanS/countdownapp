import SwiftUI
import UIKit

/*
 Theme.swift - the design tokens from the web app's css/styles.css: colours (light + dark), the two fonts
 (Fredoka for headings and numbers, Nunito for text) and a few text styles.
*/

struct PawColors {
    var bg: Color, surface: Color, surface2: Color, ink: Color, ink2: Color, line: Color
    var dark: Bool
    let kibble = Color(red: 0.91, green: 0.63, blue: 0.36)
    let collar = Color(red: 0.90, green: 0.34, blue: 0.31)
    let ball = Color(red: 0.83, green: 0.88, blue: 0.34)
    let night = Color(red: 0.12, green: 0.14, blue: 0.20)
    let onKibble = Color(red: 0.23, green: 0.14, blue: 0.08)

    static let light = PawColors(bg: Color(hex: "#FFF8EE"), surface: .white, surface2: Color(hex: "#FFF0DC"), ink: Color(hex: "#3D2616"), ink2: Color(hex: "#765039"),
                                 line: Color(hex: "#8A5A3B").opacity(0.16), dark: false)
    static let dark = PawColors(bg: Color(hex: "#171B28"), surface: Color(hex: "#252B3D"), surface2: Color(hex: "#2E3550"), ink: Color(hex: "#FFF1E0"), ink2: Color(hex: "#CDBFAE"),
                                line: Color(hex: "#FFF1E0").opacity(0.13), dark: true)
}

extension Color {
    init(hex: String) { let c = parseHex(hex); self.init(red: c.r, green: c.g, blue: c.b) }
    func mixed(_ other: Color, _ t: Double) -> Color {
        let a = UIColor(self).rgba, b = UIColor(other).rgba
        return Color(red: a.0 + (b.0 - a.0) * t, green: a.1 + (b.1 - a.1) * t, blue: a.2 + (b.2 - a.2) * t)
    }
    /// dark ink or white, whichever reads better on this colour
    var onColor: Color {
        let (r, g, b, _) = UIColor(self).rgba
        func lin(_ v: Double) -> Double { v <= 0.03928 ? v / 12.92 : pow((v + 0.055) / 1.055, 2.4) }
        let l = 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
        let darkInk = 0.0286     // luminance of #3B2314
        return (l + 0.05) / (darkInk + 0.05) >= 1.05 / (l + 0.05) ? Color(hex: "#3B2314") : .white
    }
}

extension UIColor {
    var rgba: (Double, Double, Double, Double) {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        getRed(&r, green: &g, blue: &b, alpha: &a)
        return (Double(r), Double(g), Double(b), Double(a))
    }
}

private struct PawKey: EnvironmentKey { static let defaultValue = PawColors.light }
private struct ReduceKey: EnvironmentKey { static let defaultValue = false }
extension EnvironmentValues {
    var paw: PawColors { get { self[PawKey.self] } set { self[PawKey.self] = newValue } }
    /// true when the user (or the phone) asked for less motion
    var pawReduceMotion: Bool { get { self[ReduceKey.self] } set { self[ReduceKey.self] = newValue } }
}

/* ---------------- fonts ---------------- */

enum PawFont {
    private static func exists(_ n: String) -> Bool { UIFont(name: n, size: 12) != nil }
    /// Fredoka (headings, numbers). weight: 500 / 600 / 700
    static func display(_ size: CGFloat, _ weight: Int = 600) -> Font {
        let n = weight >= 700 ? "FredokaLight-Bold" : (weight >= 600 ? "FredokaLight-SemiBold" : "FredokaLight-Medium")
        return exists(n) ? .custom(n, size: size) : .system(size: size, weight: .semibold, design: .rounded)
    }
    /// Nunito (text). weight: 400 / 700 / 800
    static func body(_ size: CGFloat, _ weight: Int = 400) -> Font {
        let n = weight >= 800 ? "NunitoExtraLight-ExtraBold" : (weight >= 700 ? "NunitoExtraLight-Bold" : "NunitoExtraLight-Regular")
        return exists(n) ? .custom(n, size: size) : .system(size: size, weight: weight >= 700 ? .bold : .regular, design: .rounded)
    }
    static func registerNames() { if exists("FredokaLight-Bold") { fredokaName = "FredokaLight-Bold" } else { fredokaName = UIFont.systemFont(ofSize: 12, weight: .bold).fontName } }
}
