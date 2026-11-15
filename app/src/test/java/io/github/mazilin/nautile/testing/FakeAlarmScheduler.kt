package io.github.mazilin.nautile.testing

import io.github.mazilin.nautile.domain.port.AlarmScheduler
import java.time.Instant

/** 内存版提醒调度替身。 */
class FakeAlarmScheduler : AlarmScheduler {

    val scheduled = mutableMapOf<String, Instant>()

    override fun schedule(id: String, at: Instant) {
        scheduled[id] = at
    }

    override fun cancel(id: String) {
        scheduled.remove(id)
    }
}
