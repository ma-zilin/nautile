package io.github.mazilin.nautile.domain.port

import java.time.Instant

/** 提醒调度端口；实现放 platform/alarm。 */
interface AlarmScheduler {
    fun schedule(id: String, at: Instant)

    fun cancel(id: String)
}
