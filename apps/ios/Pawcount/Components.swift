import SwiftUI

/*
 Components.swift - the small reusable pieces: icons, buttons, chips, segmented control, switch, panels, fields.
 They follow the web app's look (rounded, warm, a "tactile" edge under buttons).
*/

/* ------------------------------ icons ------------------------------ */

private func circlePath(_ cx: Double, _ cy: Double, _ r: Double) -> String { "M\(cx - r) \(cy) a\(r) \(r) 0 1 0 \(2 * r) 0 a\(r) \(r) 0 1 0 \(-2 * r) 0" }

private let ICONS: [String: String] = [
    "home": "M3 11.5 12 4l9 7.5M5.5 10v9.5h13V10M10 19.5v-5h4v5",
    "album": "M12 20s-7.5-4.6-7.5-10.3A4.2 4.2 0 0 1 12 7.4a4.2 4.2 0 0 1 7.5 2.3C19.5 15.4 12 20 12 20z",
    "sliders": "M4 7h9M17 7h3M4 17h3M11 17h9 \(circlePath(15, 7, 2)) \(circlePath(9, 17, 2))",
    "plus": "M12 5v14M5 12h14", "back": "m15 5-7 7 7 7", "close": "m6 6 12 12M18 6 6 18",
    "check": "m5 12.5 4.5 4.5L19 7.5", "trash": "M4 7h16M9 7V4.5h6V7M6.5 7l1 13h9l1-13", "edit": "M4 20h4L19.5 8.5l-4-4L4 16z",
    "bell": "M6 16.5V11a6 6 0 0 1 12 0v5.5l1.8 2H4.2zM10 21a2 2 0 0 0 4 0",
    "camera": "M4 8h3l1.8-2.6h6.4L17 8h3v11H4z \(circlePath(12, 13.2, 3.5))",
    "archive": "M4 6h16v4H4zM6 10v10h12V10M10 14h4", "download": "M12 4v11m0 0-4-4m4 4 4-4M5 20h14", "upload": "M12 16V5m0 0L8 9m4-4 4 4M5 20h14",
    "chevron": "m9 5 7 7-7 7", "globe": "\(circlePath(12, 12, 8.5)) M3.5 12h17M12 3.5c3 3.2 3 13.8 0 17M12 3.5c-3 3.2-3 13.8 0 17",
    "clock": "\(circlePath(12, 12, 8.5)) M12 7.5V12l3 2", "repeat": "M4 11V9a3 3 0 0 1 3-3h11l-3-3M20 13v2a3 3 0 0 1-3 3H6l3 3",
    "heart": "M12 20s-7.5-4.6-7.5-10.3A4.2 4.2 0 0 1 12 7.4a4.2 4.2 0 0 1 7.5 2.3C19.5 15.4 12 20 12 20z",
    "plane": "M21 15.5v-1.8L13.5 9V4.5a1.5 1.5 0 0 0-3 0V9L3 13.7v1.8l7.5-2.2v4.2L8.5 19v1.5l3.5-1 3.5 1V19l-2-1.5v-4.2z",
    "beach": "M7 14a5 5 0 0 1 10 0M3 14h18M4 18c1.5-1.2 2.5-1.2 4 0s2.5 1.2 4 0 2.5-1.2 4 0 2.5 1.2 4 0M12 5v1.5M5.6 7.6l1 1M18.4 7.6l-1 1",
    "cake": "M4 20h16M5 20v-6h14v6M5 16.5c2 1.5 3 1.5 5 0s3-1.5 5 0 2 1 4 0M12 14v-3M12 9.5c-1-1 0-2.4 0-3 1 .8 1.2 2 0 3z",
    "rings": "M4 14a5 5 0 1 0 10 0a5 5 0 1 0 -10 0M10 14a5 5 0 1 0 10 0a5 5 0 1 0 -10 0M10 4.5h4l1.2 1.7L12 9.4 8.8 6.2z",
    "snow": "M12 3v18M4.2 7.5l15.6 9M19.8 7.5 4.2 16.5M9.5 4.5 12 6.5l2.5-2M9.5 19.5 12 17.5l2.5 2",
    "star": "m12 3.5 2.6 5.4 5.9.8-4.3 4.1 1 5.9L12 16.9l-5.2 2.8 1-5.9L3.5 9.7l5.9-.8z",
    "moon": "M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z",
    "eye": "M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12zM9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
    "hourglass": "M7 3.5h10M7 20.5h10M8 3.5c0 4 4 4.5 4 8.5s-4 4.5-4 8.5M16 3.5c0 4-4 4.5-4 8.5s4 4.5 4 8.5",
    "suitcase": "M5 8h14a1.5 1.5 0 0 1 1.5 1.5v9A1.5 1.5 0 0 1 19 20H5a1.5 1.5 0 0 1-1.5-1.5v-9A1.5 1.5 0 0 1 5 8zM9 8V5.5h6V8M3.5 13h17",
    "bolt": "M13 3 5 13.5h6L10 21l8-10.5h-6z",
    "sparkles": "M11 3c.6 4.5 2.5 6.4 7 7-4.5.6-6.4 2.5-7 7-.6-4.5-2.5-6.4-7-7 4.5-.6 6.4-2.5 7-7zM19 3v3M17.5 4.5h3",
    "drop": "M12 3.5c3.2 4.2 6 7 6 10.2a6 6 0 0 1-12 0c0-3.2 2.8-6 6-10.2z",
    "bone": "M7 12h10M3 9.5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0M3 14.5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0M17 9.5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0M17 14.5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
    "bowl": "M3.5 11h17a8.5 8.5 0 0 1-17 0zM9 21h6M8 8.5l1 1.5M12 6l.5 2.5M16 8.5l-1 1.5",
    "ball": "M3.5 12a8.5 8.5 0 1 0 17.0 0a8.5 8.5 0 1 0 -17.0 0M5 7.5c4 1 6 5 5.5 9M19 7.5c-4 1-6 5-5.5 9",
    "smile": "M3.5 12a8.5 8.5 0 1 0 17.0 0a8.5 8.5 0 1 0 -17.0 0M8 14c1.2 2 2.5 3 4 3s2.8-1 4-3M9 9.5v.5M15 9.5v.5",
    "ticket": "M3.5 8.5a2 2 0 0 1 2-2h13a2 2 0 0 1 2 2v1.5a2 2 0 0 0 0 4v1.5a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2V14a2 2 0 0 0 0-4zM14 6.5v11",
    "more": "\(circlePath(5, 12, 1.4)) \(circlePath(12, 12, 1.4)) \(circlePath(19, 12, 1.4))",
]
private let PAW = "M4 10.4a2.2 2.9 0 1 0 4.4 0a2.2 2.9 0 1 0 -4.4 0M8 6.4a2.2 3 0 1 0 4.4 0a2.2 3 0 1 0 -4.4 0M12.6 6.4a2.2 3 0 1 0 4.4 0a2.2 3 0 1 0 -4.4 0M16.6 10.4a2.2 2.9 0 1 0 4.4 0a2.2 2.9 0 1 0 -4.4 0M12.5 11.2c3.2 0 6 3.6 6 6.2 0 2-1.8 2.7-3.2 2.4-1.2-.3-1.9-.7-2.8-.7s-1.6.4-2.8.7c-1.4.3-3.2-.4-3.2-2.4 0-2.6 2.8-6.2 6-6.2z"

private var iconCache: [String: Path] = [:]
private func iconPath(_ name: String) -> Path {
    if let p = iconCache[name] { return p }
    let p = SVGPath.parse(name == "paw" ? PAW : (ICONS[name] ?? ""))
    iconCache[name] = p; return p
}

private struct IconShape: Shape {
    let name: String
    func path(in rect: CGRect) -> Path {
        iconPath(name).applying(CGAffineTransform(scaleX: rect.width / 24, y: rect.height / 24))
    }
}

struct PawIcon: View {
    @Environment(\.paw) var pc
    let name: String
    var color: Color? = nil
    var size: CGFloat = 24
    var body: some View {
        Group {
            if name == "paw" || name == "more" { IconShape(name: name).fill(color ?? pc.ink) }
            else { IconShape(name: name).stroke(color ?? pc.ink, style: StrokeStyle(lineWidth: 2 * size / 24, lineCap: .round, lineJoin: .round)) }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

/* ------------------------------ text ------------------------------ */

struct H: View {
    @Environment(\.paw) var pc
    let text: String; var size: CGFloat = 28; var weight = 600; var align: TextAlignment = .leading; var color: Color? = nil
    init(_ t: String, _ size: CGFloat = 28, weight: Int = 600, align: TextAlignment = .leading, color: Color? = nil) { text = t; self.size = size; self.weight = weight; self.align = align; self.color = color }
    var body: some View { Text(text).font(PawFont.display(size, weight)).foregroundColor(color ?? pc.ink).multilineTextAlignment(align).fixedSize(horizontal: false, vertical: true) }
}

struct B: View {
    @Environment(\.paw) var pc
    let text: String; var size: CGFloat; var weight: Int; var align: TextAlignment; var color: Color?
    init(_ t: String, _ size: CGFloat = 16, weight: Int = 400, align: TextAlignment = .leading, color: Color? = nil) { text = t; self.size = size; self.weight = weight; self.align = align; self.color = color }
    var body: some View { Text(text).font(PawFont.body(size, weight)).foregroundColor(color ?? pc.ink).multilineTextAlignment(align).fixedSize(horizontal: false, vertical: true) }
}

struct Muted: View {
    @Environment(\.paw) var pc
    let text: String; var size: CGFloat; var align: TextAlignment
    init(_ t: String, _ size: CGFloat = 14, align: TextAlignment = .leading) { text = t; self.size = size; self.align = align }
    var body: some View { Text(text).font(PawFont.body(size, 700)).foregroundColor(pc.ink2).multilineTextAlignment(align).fixedSize(horizontal: false, vertical: true) }
}

struct Eyebrow: View {
    @Environment(\.paw) var pc
    let text: String
    init(_ t: String) { text = t }
    var body: some View { Text(text.uppercased()).font(PawFont.body(12, 800)).tracking(1).foregroundColor(pc.ink2) }
}

/* ------------------------------ buttons ------------------------------ */

/// Big tactile button: a darker edge sits 4pt below and the face sinks onto it when pressed.
struct PrimaryStyle: ButtonStyle {
    var accent: Color
    var enabled = true
    func makeBody(configuration: Configuration) -> some View {
        let face = enabled ? accent : accent.opacity(0.4)
        return configuration.label
            .font(PawFont.display(18)).foregroundColor(accent.onColor)
            .frame(maxWidth: .infinity, minHeight: 54)
            .background(RoundedRectangle(cornerRadius: 20).fill(face))
            .offset(y: configuration.isPressed ? 3 : 0)
            .padding(.bottom, 4)
            .background(RoundedRectangle(cornerRadius: 20).fill(face.mixed(.black, 0.38)).padding(.top, 4))
            .animation(.spring(response: 0.25, dampingFraction: 0.6), value: configuration.isPressed)
    }
}

struct PrimaryButton: View {
    @Environment(\.paw) var pc
    let title: String; var accent: Color? = nil; var enabled = true; var icon: String? = nil; let action: () -> Void
    init(_ title: String, accent: Color? = nil, enabled: Bool = true, icon: String? = nil, action: @escaping () -> Void) { self.title = title; self.accent = accent; self.enabled = enabled; self.icon = icon; self.action = action }
    var body: some View {
        let a = accent ?? pc.kibble
        Button(action: action) {
            HStack(spacing: 8) { if let i = icon { PawIcon(name: i, color: a.onColor) }; Text(title) }
        }
        .buttonStyle(PrimaryStyle(accent: a, enabled: enabled)).disabled(!enabled)
    }
}

struct GhostButton: View {
    @Environment(\.paw) var pc
    let title: String; var danger = false; let action: () -> Void
    init(_ title: String, danger: Bool = false, action: @escaping () -> Void) { self.title = title; self.danger = danger; self.action = action }
    var body: some View {
        Button(action: action) {
            Text(title).font(PawFont.display(17)).foregroundColor(danger ? pc.collar : pc.ink)
                .frame(maxWidth: .infinity, minHeight: 52)
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(pc.line, lineWidth: 2))
        }.buttonStyle(.plain)
    }
}

struct SmallButton: View {
    let title: String; let tint: Color; let action: () -> Void
    @Environment(\.paw) var pc
    var body: some View {
        Button(action: action) { Text(title).font(PawFont.display(15)).foregroundColor(pc.ink).padding(.horizontal, 16).frame(minHeight: 44).background(RoundedRectangle(cornerRadius: 14).fill(tint)) }.buttonStyle(.plain)
    }
}

struct IconButton: View {
    @Environment(\.paw) var pc
    let icon: String; let label: String; var glass = false; let action: () -> Void
    var body: some View {
        Button(action: action) {
            PawIcon(name: icon, color: glass ? Color(hex: "#3D2616") : pc.ink)
                .frame(width: 48, height: 48)
                .background(glass ? AnyShapeStyle(Color.white.opacity(0.78)) : AnyShapeStyle(pc.surface), in: glass ? AnyShape(Circle()) : AnyShape(RoundedRectangle(cornerRadius: 16)))
                .shadow(color: .black.opacity(glass ? 0.14 : 0.1), radius: 5, y: 2)
        }.buttonStyle(.plain).accessibilityLabel(label)
    }
}

struct Chip: View {
    @Environment(\.paw) var pc
    let text: String; var filled: Color? = nil; var textColor: Color? = nil; var small = false; var icon: String? = nil
    var body: some View {
        HStack(spacing: 5) {
            if let icon { PawIcon(name: icon, color: textColor ?? (filled != nil ? pc.ink : pc.ink2), size: small ? 13 : 15) }
            Text(text).font(PawFont.body(small ? 12 : 13, 800)).foregroundColor(textColor ?? (filled != nil ? pc.ink : pc.ink2)).lineLimit(1)
        }
            .padding(.horizontal, small ? 9 : 11).padding(.vertical, small ? 2 : 4)
            .background(Capsule().fill(filled ?? .clear))
            .overlay(Capsule().stroke(filled == nil ? pc.line : .clear, lineWidth: 1.5))
    }
}

/* ------------------------------ controls ------------------------------ */

struct Seg: View {
    @Environment(\.paw) var pc
    let options: [(String, String)]; let selected: String; let label: String; let onSelect: (String) -> Void
    var body: some View {
        HStack(spacing: 4) {
            ForEach(options, id: \.0) { v, l in
                let on = v == selected
                Button { onSelect(v) } label: {
                    Text(l).font(PawFont.body(14, 800)).foregroundColor(on ? pc.ink : pc.ink2).multilineTextAlignment(.center).minimumScaleFactor(0.8).lineLimit(1)
                        .frame(maxWidth: .infinity, minHeight: 44).padding(.horizontal, 4)
                        .background(RoundedRectangle(cornerRadius: 12).fill(on ? pc.surface : .clear).shadow(color: .black.opacity(on ? 0.12 : 0), radius: 4, y: 1))
                }.buttonStyle(.plain).accessibilityAddTraits(on ? [.isSelected] : [])
            }
        }
        .padding(4).background(RoundedRectangle(cornerRadius: 16).fill(pc.ink.opacity(0.07)))
        .accessibilityElement(children: .contain).accessibilityLabel(label)
    }
}

struct PawSwitch: View {
    @Environment(\.paw) var pc
    let on: Bool; let label: String; let onChange: (Bool) -> Void
    var body: some View {
        Button { onChange(!on) } label: {
            ZStack(alignment: on ? .trailing : .leading) {
                Capsule().fill(on ? pc.kibble : pc.ink.opacity(0.18)).frame(width: 54, height: 32)
                Circle().fill(.white).frame(width: 26, height: 26).padding(3).shadow(color: .black.opacity(0.25), radius: 2, y: 1)
            }.animation(.spring(response: 0.3, dampingFraction: 0.65), value: on)
        }.buttonStyle(.plain).accessibilityLabel(label).accessibilityValue(on ? "On" : "Off").accessibilityAddTraits(.isButton)
    }
}

struct Panel<Content: View>: View {
    @Environment(\.paw) var pc
    var flush = false
    @ViewBuilder var content: () -> Content
    var body: some View {
        VStack(alignment: .leading, spacing: 0, content: content)
            .padding(flush ? EdgeInsets(top: 4, leading: 18, bottom: 4, trailing: 18) : EdgeInsets(top: 18, leading: 18, bottom: 18, trailing: 18))
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 28).fill(pc.surface).shadow(color: .black.opacity(0.08), radius: 6, y: 2))
            .padding(.top, 14)
    }
}

struct PanelTitle<Trailing: View>: View {
    let text: String; var icon: String? = nil; let trailing: Trailing
    @Environment(\.paw) var pc
    init(_ text: String, icon: String? = nil, @ViewBuilder trailing: () -> Trailing = { EmptyView() }) { self.text = text; self.icon = icon; self.trailing = trailing() }
    var body: some View {
        HStack(spacing: 8) {
            if let i = icon { PawIcon(name: i, color: pc.ink2, size: 20) }
            H(text, 18); Spacer(minLength: 0); trailing
        }.padding(.bottom, 10)
    }
}

struct Divider2: View { @Environment(\.paw) var pc; var body: some View { Rectangle().fill(pc.line).frame(height: 1) } }

struct Field: View {
    @Environment(\.paw) var pc
    let label: String; @Binding var text: String
    var placeholder = ""; var maxLen = 60; var number = false; var accent: Color? = nil; var minHeight: CGFloat = 54
    @FocusState var focused: Bool
    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            if !label.isEmpty { Text(label).font(PawFont.body(14, 800)).foregroundColor(pc.ink2) }
            ZStack(alignment: .leading) {
                if text.isEmpty { Text(placeholder).font(PawFont.body(17)).foregroundColor(pc.ink2.opacity(0.6)) }
                TextField("", text: $text).font(PawFont.body(17)).foregroundColor(pc.ink).tint(accent ?? pc.kibble).focused($focused)
                    .keyboardType(number ? .numberPad : .default)
                    .onChange(of: text) { v in if v.count > maxLen { text = String(v.prefix(maxLen)) } }
            }
            .padding(.horizontal, 16).frame(maxWidth: .infinity, minHeight: minHeight, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 18).fill(pc.surface))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(focused ? (accent ?? pc.kibble) : pc.line, lineWidth: 2))
            .contentShape(Rectangle()).onTapGesture { focused = true }
        }.padding(.top, 16)
    }
}

/// a tappable row used by pickers (country, time zone...)
struct PickerRow: View {
    @Environment(\.paw) var pc
    let label: String; let value: String; let action: () -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            Text(label).font(PawFont.body(14, 800)).foregroundColor(pc.ink2)
            Button(action: action) {
                HStack { B(value, 17).lineLimit(1); Spacer(); PawIcon(name: "chevron", color: pc.ink2, size: 18) }
                    .padding(.horizontal, 16).frame(maxWidth: .infinity, minHeight: 54)
                    .background(RoundedRectangle(cornerRadius: 18).fill(pc.surface))
                    .overlay(RoundedRectangle(cornerRadius: 18).stroke(pc.line, lineWidth: 2))
            }.buttonStyle(.plain)
        }.padding(.top, 16)
    }
}

struct SheetItem: View {
    @Environment(\.paw) var pc
    let icon: String; let text: String; var danger = false; let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) { PawIcon(name: icon, color: danger ? pc.collar : pc.ink); Text(text).font(PawFont.body(17, 800)).foregroundColor(danger ? pc.collar : pc.ink); Spacer() }
                .padding(.horizontal, 18).frame(maxWidth: .infinity, minHeight: 56)
                .background(RoundedRectangle(cornerRadius: 18).fill(pc.surface).shadow(color: .black.opacity(0.08), radius: 4, y: 1))
        }.buttonStyle(.plain).padding(.vertical, 4)
    }
}

/// wrapping row of views (accent swatches, chips)
struct FlowLayout: Layout {
    var spacing: CGFloat = 10
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxW = proposal.width ?? 320
        var x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0
        for s in subviews {
            let sz = s.sizeThatFits(.unspecified)
            if x + sz.width > maxW && x > 0 { x = 0; y += rowH + spacing; rowH = 0 }
            x += sz.width + spacing; rowH = max(rowH, sz.height)
        }
        return CGSize(width: maxW, height: y + rowH)
    }
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowH: CGFloat = 0
        for s in subviews {
            let sz = s.sizeThatFits(.unspecified)
            if x + sz.width > bounds.maxX && x > bounds.minX { x = bounds.minX; y += rowH + spacing; rowH = 0 }
            s.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(sz)); x += sz.width + spacing; rowH = max(rowH, sz.height)
        }
    }
}

struct Swatch: View {
    @Environment(\.paw) var pc
    let color: Color; let on: Bool; let label: String; let action: () -> Void
    var body: some View {
        Button(action: action) {
            ZStack {
                Circle().fill(color).frame(width: 44, height: 44)
                if on { Circle().stroke(pc.surface, lineWidth: 3).frame(width: 44, height: 44); Circle().stroke(color, lineWidth: 3).frame(width: 52, height: 52); PawIcon(name: "check", color: color.onColor, size: 22) }
            }.frame(width: 52, height: 52)
        }.buttonStyle(.plain).accessibilityLabel(label).accessibilityAddTraits(on ? [.isSelected] : [])
    }
}
