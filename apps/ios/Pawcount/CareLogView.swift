import SwiftUI

/* CareLogView.swift - vet visits, vaccines, weight, medicine and notes for your dog. Stays on the device and in the backup. */

private let LOG_KINDS: [(key: String, icon: String, label: String)] = [("vet", "cross", "Vet visit"), ("vaccine", "shield", "Vaccine"), ("weight", "scale", "Weight"), ("meds", "pill", "Medicine"), ("note", "edit", "Note")]
private let KG_PER_LB = 0.45359237

private func showWeight(_ kg: Double, _ unit: String) -> String {
    let v = unit == "lb" ? kg / KG_PER_LB : kg
    return "\((v * 10).rounded() / 10) \(unit == "lb" ? "lb" : "kg")"
}

private func fmtDay(_ iso: String) -> String {
    let f = DateFormatter(); f.calendar = Calendar(identifier: .gregorian); f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "yyyy-MM-dd"
    guard let d = f.date(from: iso) else { return iso }
    return d.formatted(date: .abbreviated, time: .omitted)
}

private func isoDay(_ d: Date) -> String {
    let f = DateFormatter(); f.calendar = Calendar(identifier: .gregorian); f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "yyyy-MM-dd"
    return f.string(from: d)
}

struct CareLogView: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.paw) var pc
    @State var filter = "all"
    @State var adding = false

    var body: some View {
        let s = store.settings
        let all = store.data.log.sorted { $0.date > $1.date }
        let list = filter == "all" ? all : all.filter { $0.kind == filter }
        let weights = all.filter { $0.kind == "weight" && $0.weightKg != nil }.reversed().map { $0 }
        let name = s.dogName.isEmpty ? "your dog" : s.dogName
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                HStack { IconButton(icon: "back", label: "Back") { store.pop() }; Spacer(); H("Care log", 22); Spacer(); IconButton(icon: "plus", label: "Add an entry") { adding = true } }
                Muted("Vet visits, vaccines, weight and medicine for \(name). Stays on this device and in your backup.", 15).padding(.top, 12)
                if !all.isEmpty {
                    HStack(spacing: 10) {
                        if let v = all.first(where: { $0.kind == "vet" }) { fact(fmtDay(v.date), "Last vet visit") }
                        if let w = weights.last, let kg = w.weightKg { fact(showWeight(kg, s.weightUnit), "Latest weight") }
                        fact("\(all.count)", all.count == 1 ? "entry" : "entries")
                    }.padding(.top, 14)
                }
                if weights.count > 1 {
                    Panel {
                        PanelTitle("Weight", icon: "scale") { EmptyView() }
                        WeightChart(values: weights.compactMap { $0.weightKg }).foregroundColor(pc.kibble).frame(height: 90)
                    }.padding(.top, 14)
                }
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach([("all", "All")] + LOG_KINDS.map { ($0.key, $0.label) }, id: \.0) { k, label in
                            Button { filter = k } label: { Chip(text: label, filled: filter == k ? pc.kibble : nil, textColor: filter == k ? pc.kibble.onColor : nil) }.buttonStyle(.plain)
                                .accessibilityAddTraits(filter == k ? [.isSelected] : [])
                        }
                    }
                }.padding(.top, 14)
                if list.isEmpty {
                    VStack(spacing: 8) {
                        H("No entries yet", 24, align: .center)
                        Muted("Add the next vet visit, a vaccine, or \(name)'s weight. Everything is kept in one place.", 15, align: .center)
                        PrimaryButton("Add the first entry") { adding = true }.padding(.top, 10)
                    }.frame(maxWidth: .infinity).padding(.top, 36)
                } else {
                    VStack(spacing: 8) {
                        ForEach(list) { e in
                            let info = LOG_KINDS.first(where: { $0.key == e.kind }) ?? LOG_KINDS[4]
                            HStack(spacing: 12) {
                                PawIcon(name: info.icon, color: pc.ink, size: 22).frame(width: 40, height: 40).background(RoundedRectangle(cornerRadius: 14).fill(pc.kibble.opacity(0.18)))
                                VStack(alignment: .leading, spacing: 1) {
                                    B(info.label + (e.kind == "weight" && e.weightKg != nil ? " · " + showWeight(e.weightKg!, s.weightUnit) : ""), 16, weight: 800)
                                    Muted(fmtDay(e.date) + (e.note.isEmpty ? "" : " · " + e.note), 13)
                                }
                                Spacer()
                                Button { store.update { d in d.log.removeAll { $0.id == e.id } }; store.say("Entry removed") } label: { PawIcon(name: "trash", color: pc.ink2, size: 20).frame(width: 44, height: 44) }
                                    .buttonStyle(.plain).accessibilityLabel("Delete this entry")
                            }.padding(.horizontal, 12).padding(.vertical, 10).background(RoundedRectangle(cornerRadius: 18).fill(pc.surface))
                        }
                    }.padding(.top, 12)
                }
            }.padding(.horizontal, 20).padding(.top, 18).padding(.bottom, 40)
        }
        .sheet(isPresented: $adding) { AddLogSheet().environmentObject(store).environment(\.paw, pc).presentationDetents([.large]) }
    }

    func fact(_ big: String, _ small: String) -> some View {
        VStack(spacing: 2) { H(big, 17, align: .center); Muted(small, 12, align: .center) }
            .frame(maxWidth: .infinity).padding(12).background(RoundedRectangle(cornerRadius: 18).fill(pc.surface))
    }
}

struct WeightChart: View {
    let values: [Double]
    var body: some View {
        GeometryReader { geo in
            let lo = values.min() ?? 0, hi = values.max() ?? 1, span = hi - lo == 0 ? 1 : hi - lo
            let pad: CGFloat = 10
            let pts: [CGPoint] = values.enumerated().map { i, v in
                CGPoint(x: pad + CGFloat(i) * (geo.size.width - 2 * pad) / CGFloat(max(1, values.count - 1)), y: geo.size.height - pad - CGFloat((v - lo) / span) * (geo.size.height - 2 * pad))
            }
            ZStack {
                Path { p in if let f = pts.first { p.move(to: f); pts.dropFirst().forEach { p.addLine(to: $0) } } }
                    .stroke(style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
                ForEach(Array(pts.enumerated()), id: \.offset) { _, pt in Circle().frame(width: 8, height: 8).position(pt) }
            }
        }
    }
}

struct AddLogSheet: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.paw) var pc
    @Environment(\.dismiss) var dismiss
    @State var kind = "vet"
    @State var date = Date()
    @State var weight = ""
    @State var note = ""

    var body: some View {
        let unit = store.settings.weightUnit
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                H("Add to the care log", 22, align: .center).frame(maxWidth: .infinity).padding(.top, 20).padding(.bottom, 8)
                Seg(options: LOG_KINDS.map { ($0.key, $0.label) }, selected: kind, label: "Kind of entry") { kind = $0 }
                VStack(alignment: .leading, spacing: 7) {
                    Text("Date").font(PawFont.body(14, 800)).foregroundColor(pc.ink2)
                    DatePicker("Date", selection: $date, in: ...Date(), displayedComponents: .date).labelsHidden().tint(pc.kibble)
                        .padding(.horizontal, 12).frame(maxWidth: .infinity, minHeight: 54, alignment: .leading).background(RoundedRectangle(cornerRadius: 18).fill(pc.surface))
                        .overlay(RoundedRectangle(cornerRadius: 18).stroke(pc.line, lineWidth: 2))
                }.padding(.top, 14)
                if kind == "weight" { Field(label: "Weight (\(unit))", text: $weight, placeholder: unit == "lb" ? "35" : "16", maxLen: 6, number: true) }
                Field(label: "Note (optional)", text: $note, placeholder: kind == "vaccine" ? "Rabies booster" : kind == "vet" ? "Yearly checkup" : kind == "meds" ? "Flea & tick" : "", maxLen: 200)
                PrimaryButton("Save") { save() }.padding(.top, 18)
                GhostButton("Cancel") { dismiss() }.padding(.top, 10)
            }.padding(.horizontal, 20).padding(.bottom, 30)
        }.background(pc.bg.ignoresSafeArea())
    }

    func save() {
        let raw = Double(weight.replacingOccurrences(of: ",", with: "."))
        if kind == "weight" && (raw == nil || raw! <= 0 || raw! >= 500) { store.say("Enter a weight"); return }
        let kg: Double? = kind == "weight" ? ((toKg(raw!) * 10).rounded() / 10) : nil
        let e = LogEntry(id: newId(), kind: kind, date: isoDay(date), note: String(note.trimmingCharacters(in: .whitespaces).prefix(200)), weightKg: kg)
        store.update { d in d.log.append(e); if d.log.count > 500 { d.log.removeFirst(d.log.count - 500) } }
        store.say("Added to the care log"); dismiss()
    }
    private func toKg(_ v: Double) -> Double { store.settings.weightUnit == "lb" ? v * KG_PER_LB : v }
}
