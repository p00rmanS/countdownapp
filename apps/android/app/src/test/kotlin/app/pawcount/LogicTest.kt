package app.pawcount

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/* Unit tests for Logic.kt. They mirror tests/countdown.test.js so the web, Android and iOS apps agree. */
class LogicTest {
    private fun utc(y: Int, m: Int, d: Int, h: Int = 0, mi: Int = 0) = ZonedDateTime.of(y, m, d, h, mi, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
    private val now = utc(2030, 6, 1, 12, 0)
    private fun cd(target: String, created: Long = utc(2030, 1, 1), tz: String = "UTC", allDay: Boolean = false, yearly: Boolean = false, type: String = "custom", person: Person? = null) =
        Countdown("x", "t", type, target, tz, allDay, yearly, created, Dog("golden", "D"), "#E8A15C", DisplayMode.FULL, person = person)
    private fun at(daysLeft: Long, daysSince: Long) = cd(Instant.ofEpochMilli(now + daysLeft * DAY).toString().take(16), now - daysSince * DAY)

    @Test fun tokyoIsNineHoursAhead() {
        val k = compute(cd("2030-06-01T21:00", tz = "Asia/Tokyo"), now)
        assertEquals(utc(2030, 6, 1, 12, 0), k.target)
    }
    @Test fun daylightSavingNightIs23Hours() {
        val a = compute(cd("2030-03-09T12:00", tz = "America/New_York"), 0).target
        val b = compute(cd("2030-03-10T12:00", tz = "America/New_York"), 0).target
        assertEquals(23L, (b - a) / HOUR)
    }
    @Test fun stages() {
        assertEquals(Stage.NAP, compute(at(90, 10), now).stage)
        assertEquals(Stage.CURIOUS, compute(at(60, 40), now).stage)
        assertEquals(Stage.WAITING, compute(at(30, 70), now).stage)
        assertEquals(Stage.PACKING, compute(at(5, 5), now).stage)
        assertEquals(Stage.ZOOMIES, compute(cd("2030-06-02T08:00", utc(2030, 5, 1)), now).stage)
        assertEquals(Stage.TODAY, compute(cd("2030-06-01T20:00", utc(2030, 5, 1)), now).stage)
        assertEquals(Stage.MEMORY, compute(cd("2030-05-20T10:00", utc(2030, 4, 1)), now).stage)
    }
    @Test fun partsAndWeeks() {
        val k = compute(cd("2030-06-03T15:30", utc(2030, 5, 1)), now)
        assertEquals(Parts(2, 3, 30, 0), k.parts)
        val w = compute(cd("2030-06-18T12:00", utc(2030, 5, 1)), now)
        assertEquals(2 to 3, w.weeks to w.weekDays)
    }
    @Test fun birthdayRollsToNextYear() {
        val k = compute(cd("1990-03-05T00:00", allDay = true, yearly = true, created = utc(2030, 1, 1)), now)
        assertEquals(2031, k.occYear); assertEquals(Phase.UPCOMING, k.phase)
    }
    @Test fun celebratesAllDayThenRolls() {
        val c = cd("1990-03-05T00:00", allDay = true, yearly = true)
        val day = utc(2030, 3, 5, 15)
        assertEquals(Phase.TODAY, compute(c, day).phase)
        assertEquals(2031, compute(c, day + DAY).occYear)
    }
    @Test fun leapDayFallsBackTo28th() {
        val k = compute(cd("2028-02-29T00:00", allDay = true, yearly = true), utc(2030, 6, 1))
        assertEquals(28, Instant.ofEpochMilli(k.target).atZone(ZoneId.of("UTC")).dayOfMonth)
    }
    @Test fun ageTurning() {
        assertEquals(41, compute(cd("1990-03-05T00:00", allDay = true, yearly = true, type = "birthday", person = Person("M", 1990)), now).age)
    }
    @Test fun samplesCoverEveryStage() {
        val stages = makeSamples(now).map { compute(it, now).stage }.toSet()
        assertEquals(Stage.entries.toSet(), stages)
    }
    @Test fun reminderPlanHasOnePerDayInTheFuture() {
        val plan = planReminders(makeSamples(now), now)
        val days = plan.map { Instant.ofEpochMilli(it.at).atZone(ZoneId.systemDefault()).toLocalDate() }
        assertEquals(days.size, days.toSet().size)
        assertTrue(plan.all { it.at > now })
    }
    @Test fun backupRoundTrip() {
        val data = AppData(true, makeSamples(now))
        val back = appDataFromJson(data.toJson())
        assertEquals(data.countdowns.size, back.countdowns.size)
        assertEquals(data.countdowns[0].title, back.countdowns[0].title)
    }
}
