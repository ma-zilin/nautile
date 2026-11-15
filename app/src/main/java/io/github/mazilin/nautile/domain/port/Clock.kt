package io.github.mazilin.nautile.domain.port

import java.time.Instant

/** 可注入时钟；测试用 FakeClock 控制时间。 */
fun interface Clock {
    fun now(): Instant
}
