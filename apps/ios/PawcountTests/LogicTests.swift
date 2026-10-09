import XCTest
@testable import Pawcount

/* Unit tests for Logic.swift. They mirror tests/countdown.test.js and the Android LogicTest so all platforms agree. */
final class LogicTests: XCTestCase {
    func testHostileBackupIsCleaned() throws {
        let json = #"{"id":"../x","title":"t","type":"nope","targetAt":"2031-01-01T10:00","timeZone":"Mars/Base","accent":"zzz","dog":{"breed":"x","name":"n","colors":{"fur":"bad"},"ears":"bad"},"memoryPhotos":["../../x","ok-1.jpg"]}"#
        let c = try JSONDecoder().decode(Countdown.self, from: Data(json.utf8))
        XCTAssertEqual(c.type, "custom"); XCTAssertEqual(c.accent, "#E8A15C"); XCTAssertEqual(c.dog.breed, "mutt"); XCTAssertNil(c.dog.furColor)
        XCTAssertEqual(c.photos, ["ok-1.jpg"]); XCTAssertFalse(c.id.contains("."))
        let bad = #"{"id":"a","title":"t","type":"custom","targetAt":"tomorrow","dog":{"breed":"mutt","name":"n"}}"#
        XCTAssertThrowsError(try JSONDecoder().decode(Countdown.self, from: Data(bad.utf8)))
    }

    let now = Date(timeIntervalSince1970: 1_906_545_600)      // 2030-06-01 12:00 UTC

    func utc(_ y: Int, _ m: Int, _ d: Int, _ h: Int = 0, _ mi: Int = 0) -> Date {
        var cal = Calendar(identifier: .gregorian); cal.timeZone = TimeZone(identifier: "UTC")!
        return cal.date(from: DateComponents(year: y, month: m, day: d, hour: h, minute: mi))!
    }
    func cd(_ target: String, created: Date? = nil, tz: String = "UTC", allDay: Bool = false, yearly: Bool = false, type: String = "custom", person: Person? = nil) -> Countdown {
        Countdown(id: "x", title: "t", type: type, targetAt: target, timeZone: tz, allDay: allDay, yearly: yearly, createdAt: created ?? utc(2030, 1, 1),
                  dog: Dog(breed: "golden", name: "D"), accent: "#E8A15C", displayMode: .full, person: person)
    }
    func at(_ left: Double, _ since: Double) -> Countdown {
        let f = DateFormatter(); f.timeZone = TimeZone(identifier: "UTC"); f.dateFormat = "yyyy-MM-dd'T'HH:mm"; f.locale = Locale(identifier: "en_US_POSIX")
        return cd(f.string(from: now.addingTimeInterval(left * DAY)), created: now.addingTimeInterval(-since * DAY))
    }

    func testTokyoIsNineHoursAhead() {
        XCTAssertEqual(compute(cd("2030-06-01T21:00", tz: "Asia/Tokyo"), now: now).target, now)
    }
    func testDaylightSavingNightIs23Hours() {
        let a = compute(cd("2030-03-09T12:00", tz: "America/New_York"), now: now).target
        let b = compute(cd("2030-03-10T12:00", tz: "America/New_York"), now: now).target
        XCTAssertEqual(b.timeIntervalSince(a) / HOUR, 23)
    }
    func testStages() {
        XCTAssertEqual(compute(at(90, 10), now: now).stage, .nap)
        XCTAssertEqual(compute(at(60, 40), now: now).stage, .curious)
        XCTAssertEqual(compute(at(30, 70), now: now).stage, .waiting)
        XCTAssertEqual(compute(at(5, 5), now: now).stage, .packing)
        XCTAssertEqual(compute(cd("2030-06-02T08:00", created: utc(2030, 5, 1)), now: now).stage, .zoomies)
        XCTAssertEqual(compute(cd("2030-06-01T20:00", created: utc(2030, 5, 1)), now: now).stage, .today)
        XCTAssertEqual(compute(cd("2030-05-20T10:00", created: utc(2030, 4, 1)), now: now).stage, .memory)
    }
    func testPartsAndWeeks() {
        XCTAssertEqual(compute(cd("2030-06-03T15:30", created: utc(2030, 5, 1)), now: now).parts, Parts(d: 2, h: 3, m: 30, s: 0))
        let w = compute(cd("2030-06-18T12:00", created: utc(2030, 5, 1)), now: now)
        XCTAssertEqual(w.weeks, 2); XCTAssertEqual(w.weekDays, 3)
    }
    func testBirthdayRollsToNextYear() {
        let k = compute(cd("1990-03-05T00:00", allDay: true, yearly: true), now: now)
        XCTAssertEqual(k.occYear, 2031); XCTAssertEqual(k.phase, .upcoming)
    }
    func testCelebratesAllDayThenRolls() {
        let c = cd("1990-03-05T00:00", allDay: true, yearly: true), day = utc(2030, 3, 5, 15)
        XCTAssertEqual(compute(c, now: day).phase, .today)
        XCTAssertEqual(compute(c, now: day.addingTimeInterval(DAY)).occYear, 2031)
    }
    func testLeapDayFallsBackTo28th() {
        let k = compute(cd("2028-02-29T00:00", allDay: true, yearly: true), now: utc(2030, 6, 1))
        var cal = Calendar(identifier: .gregorian); cal.timeZone = TimeZone(identifier: "UTC")!
        XCTAssertEqual(cal.component(.day, from: k.target), 28)
    }
    func testAgeTurning() {
        let c = cd("1990-03-05T00:00", allDay: true, yearly: true, type: "birthday", person: Person(name: "M", birthYear: 1990))
        XCTAssertEqual(compute(c, now: now).age, 41)
    }
    func testSamplesCoverEveryStage() {
        let stages = Set(makeSamples(now: now).map { compute($0, now: now).stage })
        XCTAssertEqual(stages, Set(Stage.allCases))
    }
    func testReminderPlanHasOnePerDayInTheFuture() {
        let plan = planReminders(makeSamples(now: now), now: now)
        let days = plan.map { Calendar.current.dateComponents([.year, .month, .day], from: $0.at) }
        XCTAssertEqual(days.count, Set(days).count)
        XCTAssertTrue(plan.allSatisfy { $0.at > now })
    }
    func testBackupRoundTrip() throws {
        var data = AppData(); data.onboarded = true; data.countdowns = makeSamples(now: now)
        let back = try JSONDecoder().decode(AppData.self, from: JSONEncoder().encode(data))
        XCTAssertEqual(back.countdowns.count, data.countdowns.count)
        XCTAssertEqual(back.countdowns[0].title, data.countdowns[0].title)
        XCTAssertEqual(back.countdowns[5].dog, data.countdowns[5].dog)
    }
    func testWebBackupFormatIsReadable() throws {
        // the web app writes dogs with a nested "colors" object and keeps photos as data: URLs
        let json = """
        {"app":"pawcount","data":{"onboarded":true,"countdowns":[{"id":"a","title":"T","type":"custom","targetAt":"2030-01-01T00:00","timeZone":"UTC","allDay":false,"recurrence":"yearly","createdAt":"2029-12-01T00:00:00.000Z","dog":{"breed":"mutt","name":"L","colors":{"fur":"#B98A62"},"ears":"long"},"memoryPhotos":["data:image/jpeg;base64,xx"]}]}}
        """
        let d = try JSONDecoder().decode(AppData.self, from: Data(json.utf8))
        XCTAssertEqual(d.countdowns[0].dog.furColor, "#B98A62"); XCTAssertEqual(d.countdowns[0].dog.variant, "mutt-long")
        XCTAssertTrue(d.countdowns[0].yearly); XCTAssertTrue(d.countdowns[0].photos.isEmpty)
    }
    func testArtParsesAndEveryDogDraws() {
        // all bundled drawings load
        let art = Art()
        XCTAssertFalse(art.meta.breedOrder.isEmpty)
        for b in art.meta.breedOrder { for s in Stage.allCases { XCTAssertNotNil(art.dog(b == "mutt" ? "mutt-floppy" : b, s, "custom"), "\(b) \(s)") } }
        XCTAssertNotNil(art.scene("intl_trip", door: true))
    }
}
