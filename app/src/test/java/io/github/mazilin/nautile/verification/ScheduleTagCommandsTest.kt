package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.domain.model.ScheduleEvent
import io.github.mazilin.nautile.domain.model.ScheduleId
import io.github.mazilin.nautile.domain.model.ScheduleSource
import io.github.mazilin.nautile.domain.model.ScheduleTag
import io.github.mazilin.nautile.domain.model.ScheduleTagId
import io.github.mazilin.nautile.domain.usecase.ScheduleTagCommandResult
import io.github.mazilin.nautile.domain.usecase.ScheduleTagCommands
import io.github.mazilin.nautile.domain.usecase.ScheduleTagError
import io.github.mazilin.nautile.testing.FakeClock
import io.github.mazilin.nautile.testing.InMemoryScheduleRepository
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * R7-G1 · 日程标签管理用例验证（Q2、#13、架构 §3.2.2）。
 *
 * 覆盖：新建 trim/禁空/禁重名（忽略大小写）；改名用户标签；
 * 系统 COURSE 标签（systemKey != null）严禁改名与删除；
 * 删除用户标签原子解绑关联事件且保留日程本体。
 */
class ScheduleTagCommandsTest {

    private val now: Instant = Instant.parse("2026-11-28T08:00:00Z")
    private val courseTag = ScheduleTag(ScheduleTagId(1L), "课程表", "COURSE", 0)

    private lateinit var repository: InMemoryScheduleRepository
    private lateinit var commands: ScheduleTagCommands
    private lateinit var clock: FakeClock

    @Before
    fun setUp() {
        clock = FakeClock(now)
        repository = InMemoryScheduleRepository(initialTags = listOf(courseTag))
        commands = ScheduleTagCommands(repository, clock)
    }

    private fun event(id: Long, title: String, tagId: ScheduleTagId?): ScheduleEvent =
        ScheduleEvent(
            id = ScheduleId(id),
            title = title,
            note = null,
            startAt = now,
            endAt = now.plusSeconds(3600),
            source = ScheduleSource.MANUAL,
            tagId = tagId,
            seriesId = null,
            originalStartAt = null,
            createdAt = now,
            updatedAt = now,
        )

    @Test
    fun r7_create_trimsValidNameAndAssignsIncrementalSortOrder() = runTest {
        val first = commands.create("  工作  ") as ScheduleTagCommandResult.Success
        val second = commands.create("运动") as ScheduleTagCommandResult.Success

        assertEquals("工作", first.tag.name)
        assertNull("用户标签 systemKey 必须为 null", first.tag.systemKey)
        assertEquals(1, first.tag.sortOrder)
        assertEquals(2, second.tag.sortOrder)
        assertEquals(listOf("课程表", "工作", "运动"), repository.listTags().map { it.name })
    }

    @Test
    fun r7_create_rejectsBlankAndDuplicateNamesCaseInsensitively() = runTest {
        listOf("", "   ", "\n\t").forEach { blank ->
            assertEquals(
                ScheduleTagCommandResult.Invalid(ScheduleTagError.BLANK_NAME),
                commands.create(blank),
            )
        }
        assertEquals("空白名不得写库", 0, repository.writeCount)

        // 与系统“课程表”重名拒绝。
        assertEquals(
            ScheduleTagCommandResult.Invalid(ScheduleTagError.DUPLICATE_NAME),
            commands.create("  课程表  "),
        )
        assertEquals(0, repository.writeCount)

        assertTrue(commands.create("Study") is ScheduleTagCommandResult.Success)
        assertEquals(
            ScheduleTagCommandResult.Invalid(ScheduleTagError.DUPLICATE_NAME),
            commands.create("  study "),
        )
        assertEquals(1, repository.writeCount)
    }

    @Test
    fun r7_rename_userTag_updatesNameAndValidatesBlankAndDuplicate() = runTest {
        val work = (commands.create("work") as ScheduleTagCommandResult.Success).tag
        commands.create("生活")

        // 改自身大小写允许（排除自身判重）。
        val recased = commands.rename(work.id, "  Work  ")
        assertTrue(recased is ScheduleTagCommandResult.Success)
        assertEquals("Work", repository.getTag(work.id)?.name)

        // 空名拒绝。
        assertEquals(
            ScheduleTagCommandResult.Invalid(ScheduleTagError.BLANK_NAME),
            commands.rename(work.id, "   "),
        )
        // 与其他标签重名拒绝。
        assertEquals(
            ScheduleTagCommandResult.Invalid(ScheduleTagError.DUPLICATE_NAME),
            commands.rename(work.id, "生活"),
        )
        assertEquals("Work", repository.getTag(work.id)?.name)

        // 不存在的标签返回 NotFound。
        assertEquals(
            ScheduleTagCommandResult.NotFound(ScheduleTagId(999L)),
            commands.rename(ScheduleTagId(999L), "新名"),
        )
    }

    @Test
    fun r7_systemCourseTag_cannotBeRenamedOrDeleted() = runTest {
        val renameResult = commands.rename(courseTag.id, "新课程表")
        assertEquals(
            ScheduleTagCommandResult.Invalid(ScheduleTagError.SYSTEM_TAG_PROTECTED),
            renameResult,
        )

        val deleteResult = commands.delete(courseTag.id)
        assertEquals(
            ScheduleTagCommandResult.Invalid(ScheduleTagError.SYSTEM_TAG_PROTECTED),
            deleteResult,
        )

        assertEquals("系统标签名称不得改变", "课程表", repository.getTag(courseTag.id)?.name)
        assertEquals("系统标签不得被删除", 1, repository.listTags().size)
        assertEquals("拒绝系统标签变更不得写库", 0, repository.writeCount)
    }

    @Test
    fun r7_delete_userTag_detachesEventsKeepingEventsAndOtherTagsIntact() = runTest {
        val work = (commands.create("工作") as ScheduleTagCommandResult.Success).tag
        val study = (commands.create("学习") as ScheduleTagCommandResult.Success).tag
        val workEvent1 = repository.insert(event(0L, "周会", work.id))
        val workEvent2 = repository.insert(event(0L, "评审", work.id))
        val studyEvent = repository.insert(event(0L, "自习", study.id))
        val untaggedEvent = repository.insert(event(0L, "无标签", null))

        assertEquals(mapOf(work.id to 2, study.id to 1), repository.observeTagEventCounts().first())

        clock.advanceSeconds(120)
        val deleted = commands.delete(work.id)
        assertEquals(ScheduleTagCommandResult.Deleted(work.id), deleted)
        assertNull("标签行应已删除", repository.getTag(work.id))

        // 关联日程必须保留且 tagId 变为 null。
        assertEquals(4, repository.snapshot.size)
        assertNull(repository.get(workEvent1)?.tagId)
        assertNull(repository.get(workEvent2)?.tagId)
        assertEquals("解绑应刷新 updatedAt", clock.now(), repository.get(workEvent1)?.updatedAt)
        // 其他标签与无标签日程不受影响。
        assertEquals(study.id, repository.get(studyEvent)?.tagId)
        assertNull(repository.get(untaggedEvent)?.tagId)
        assertEquals(mapOf(study.id to 1), repository.observeTagEventCounts().first())

        // 再次删除返回 NotFound。
        assertEquals(ScheduleTagCommandResult.NotFound(work.id), commands.delete(work.id))
    }
}
