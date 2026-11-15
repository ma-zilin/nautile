package io.github.mazilin.nautile.testing

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeClockTest {

    @Test
    fun advanceSeconds_moves_clock() {
        val clock = FakeClock(Instant.parse("2026-11-15T00:00:00Z"))

        clock.advanceSeconds(90)

        assertEquals(Instant.parse("2026-11-15T00:01:30Z"), clock.now())
    }
}
