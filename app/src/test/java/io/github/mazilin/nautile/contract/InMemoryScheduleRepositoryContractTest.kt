package io.github.mazilin.nautile.contract

import io.github.mazilin.nautile.domain.model.ScheduleTag
import io.github.mazilin.nautile.domain.model.ScheduleTagId
import io.github.mazilin.nautile.domain.repository.ScheduleRepository
import io.github.mazilin.nautile.testing.InMemoryScheduleRepository

/** 内存替身的日程仓库契约实现（R6-G2-V1）。 */
class InMemoryScheduleRepositoryContractTest : ScheduleRepositoryContract() {

    private val courseTag = ScheduleTag(ScheduleTagId(1L), "课程表", "COURSE", 0)

    override val schedules: ScheduleRepository =
        InMemoryScheduleRepository(initialTags = listOf(courseTag))

    override suspend fun ensureCourseTag(): ScheduleTagId = courseTag.id
}
