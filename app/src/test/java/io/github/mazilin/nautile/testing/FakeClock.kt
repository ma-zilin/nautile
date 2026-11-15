package io.github.mazilin.nautile.testing

import io.github.mazilin.nautile.domain.port.Clock
import java.time.Instant

/** 可推进的测试时钟。 */
class FakeClock(private var current: Instant) : Clock {

    override fun now(): Instant = current

    fun advanceSeconds(seconds: Long) {
        current = current.plusSeconds(seconds)
    }
}
