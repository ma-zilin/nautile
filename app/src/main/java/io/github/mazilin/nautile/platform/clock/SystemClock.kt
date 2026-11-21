package io.github.mazilin.nautile.platform.clock

import io.github.mazilin.nautile.domain.port.Clock
import java.time.Instant

/** 生产时钟（S1-T05 步骤 4）；装配由 T06 的 AppContainer 完成。 */
class SystemClock : Clock {

    override fun now(): Instant = Instant.now()
}
