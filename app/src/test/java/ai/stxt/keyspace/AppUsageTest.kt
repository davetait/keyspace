package ai.stxt.keyspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

class AppUsageTest {

    private val day = AppUsage.DAY_MILLIS.toLong()

    private fun AppUsage?.launch(at: Long, halfLife: Double = AppUsage.DEFAULT_HALF_LIFE_DAYS) =
        this?.recordLaunch(at, halfLife) ?: AppUsage.firstLaunch(at)

    /** Launches [perDay] times a day, evenly spaced, for days [from] until [to]. */
    private fun AppUsage?.useDaily(from: Long, to: Long, perDay: Int, halfLife: Double = AppUsage.DEFAULT_HALF_LIFE_DAYS): AppUsage? {
        var usage = this
        var t = from * day
        val step = day / perDay
        while (t < to * day) {
            usage = usage.launch(t, halfLife)
            t += step
        }
        return usage
    }

    @Test
    fun `first launch scores 1 and decays by ln 2 per half-life`() {
        val usage = AppUsage.firstLaunch(0)
        assertEquals(0.0, usage.rankAt(0), 1e-9)
        assertEquals(-ln(2.0), usage.rankAt((AppUsage.DEFAULT_HALF_LIFE_DAYS * day).toLong()), 1e-9)
    }

    /** First day (in half-day steps) on which the new app outranks the old one. */
    private fun overtakeDay(oldKeepsGoing: Boolean, halfLife: Double = AppUsage.DEFAULT_HALF_LIFE_DAYS): Double {
        // Old app: once a day for a year, ending at day 365.
        var old = null.useDaily(0, 365, perDay = 1, halfLife)!!
        var new: AppUsage? = null
        // From day 365, the new app is opened twice a day.
        for (halfDay in 1..60) {
            val start = 365 * day + (halfDay - 1) * day / 2
            val now = start + day / 2
            new = new.launch(start, halfLife)
            if (oldKeepsGoing && halfDay % 2 == 1) old = old.recordLaunch(start, halfLife)
            if (new.rankAt(now, halfLife) > old.rankAt(now, halfLife)) return halfDay / 2.0
        }
        error("new app never overtook the old one")
    }

    @Test
    fun `new app used twice a day overtakes an old daily app within days`() {
        val day = overtakeDay(oldKeepsGoing = true)
        assertTrue("overtook on day $day", day in 2.5..4.0)
    }

    @Test
    fun `new app overtakes an old app that stopped being used even sooner`() {
        val day = overtakeDay(oldKeepsGoing = false)
        assertTrue("overtook on day $day", day in 1.5..2.5)
    }

    @Test
    fun `fast adapt speed overtakes sooner than slow`() {
        val fast = overtakeDay(oldKeepsGoing = true, halfLife = AdaptSpeed.Fast.halfLifeDays)
        val slow = overtakeDay(oldKeepsGoing = true, halfLife = AdaptSpeed.Slow.halfLifeDays)
        assertTrue("fast $fast vs slow $slow", fast < slow)
    }

    @Test
    fun `idle apps keep their relative order forever`() {
        val heavy = null.useDaily(0, 100, perDay = 3)!!
        val light = null.useDaily(0, 100, perDay = 1)!!
        for (years in listOf(1L, 50L)) {
            val now = (100 + years * 365) * day
            val h = heavy.rankAt(now)
            val l = light.rankAt(now)
            assertFalse(h.isNaN() || l.isNaN() || h.isInfinite() || l.isInfinite())
            assertTrue("after $years years: $h vs $l", h > l)
        }
    }

    @Test
    fun `a single launch today doesn't beat a frequently used app`() {
        val frequent = null.useDaily(0, 30, perDay = 2)!!
        val now = 30 * day
        val once = AppUsage.firstLaunch(now)
        assertTrue(frequent.rankAt(now) > once.rankAt(now))
    }

    @Test
    fun `a clock set backwards does not inflate the score`() {
        val usage = AppUsage.firstLaunch(10 * day)
        assertEquals(usage.rankAt(10 * day), usage.rankAt(5 * day), 1e-9)
    }
}
