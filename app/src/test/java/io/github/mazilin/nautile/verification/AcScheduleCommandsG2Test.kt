package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.domain.model.ScheduleId
import io.github.mazilin.nautile.domain.model.ScheduleSource
import io.github.mazilin.nautile.domain.model.ScheduleTag
import io.github.mazilin.nautile.domain.model.ScheduleTagId
import io.github.mazilin.nautile.domain.usecase.ScheduleCommandResult
import io.github.mazilin.nautile.domain.usecase.ScheduleCommands
import io.github.mazilin.nautile.domain.usecase.ScheduleError
import io.github.mazilin.nautile.domain.usecase.ScheduleFieldUpdate
import io.github.mazilin.nautile.testing.FakeClock
import io.github.mazilin.nautile.testing.InMemoryScheduleRepository
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * R6-G2-V1 · 日程命令拒绝分支（AC-S-03/04 写入前置、AC-D-06 的“不产生非法中间结果”）。
 *
 * 预期：空标题、结束不晚于开始、课程来源、未知标签都不写库；失败后仓库内容与写入计数不变。
 * 非法时间文本（25:00、12:60、非数字）在 G2 无文本入口（`ScheduleCommands` 只接收 `Instant`），
 * 该分支属 G3-T2 的 24h 输入面板（AC-M-01 辅助核对），本测试不伪造通过。
 */
class AcScheduleCommandsG2Test {

    private val now: Instant = Instant.parse("2026-11-25T02:00:00Z")
    private val start = Instant.parse("2026-11-25T01:00:00Z")
    private val end = Instant.parse("2026-11-25T02:30:00Z")

    private lateinit var repository: InMemoryScheduleRepository
    private lateinit var commands: ScheduleCommands

    @Before
    fun setUp() {
        repository = InMemoryScheduleRepository(
            initialTags = listOf(ScheduleTag(ScheduleTagId(1L), "课程表", "COURSE", 0)),
        )
        commands = ScheduleCommands(repository, FakeClock(now))
    }

    private fun invalidError(result: ScheduleCommandResult): ScheduleError {
        assertTrue("预期 Invalid，实际=$result", result is ScheduleCommandResult.Invalid)
        return (result as ScheduleCommandResult.Invalid).error
    }

    @Test
    fun g2_create_validRegularEvent_persistsWithTrimmedTitleAndRegularSource() = runTest {
        val result = commands.create(
            title = "  概率论  ",
            note = null,
            startAt = start,
            endAt = end,
            tagId = null,
        )

        assertTrue("预期 Success，实际=$result", result is ScheduleCommandResult.Success)
        val stored = (result as ScheduleCommandResult.Success).event
        assertTrue("新日程 id 应大于 0", stored.id.value > 0L)
        assertEquals(ScheduleSource.REGULAR, repository.get(stored.id)?.source)
        assertEquals("概率论", repository.get(stored.id)?.title)
        assertEquals(1, repository.writeCount)
    }

    @Test
    fun g2_create_blankTitle_isRejectedWithoutWriting() = runTest {
        listOf("", "   ", "\n\t").forEach { blank ->
            val result = commands.create(
                title = blank,
                note = null,
                startAt = start,
                endAt = end,
                tagId = null,
            )
            assertEquals(ScheduleError.BLANK_TITLE, invalidError(result))
        }

        assertEquals("拒绝分支不得写库", 0, repository.writeCount)
        assertTrue(repository.snapshot.isEmpty())
    }

    @Test
    fun g2_create_endNotAfterStart_isRejectedWithoutWriting() = runTest {
        val sameInstant = commands.create("等长", null, start, start, null)
        assertEquals(ScheduleError.END_NOT_AFTER_START, invalidError(sameInstant))

        val reversed = commands.create("倒序", null, end, start, null)
        assertEquals(ScheduleError.END_NOT_AFTER_START, invalidError(reversed))

        assertEquals("拒绝分支不得写库", 0, repository.writeCount)
        assertTrue(repository.snapshot.isEmpty())
    }

    @Test
    fun g2_create_courseSourceIsRejectedThisPhase() = runTest {
        val result = commands.create(
            title = "课程来源",
            note = null,
            startAt = start,
            endAt = end,
            tagId = null,
            source = ScheduleSource.COURSE,
        )

        assertEquals(ScheduleError.UNSUPPORTED_SOURCE, invalidError(result))
        assertEquals(0, repository.writeCount)
    }

    @Test
    fun g2_create_unknownTagIsRejectedWithoutWriting() = runTest {
        val result = commands.create("未知标签", null, start, end, ScheduleTagId(999L))

        assertEquals(ScheduleError.TAG_NOT_FOUND, invalidError(result))
        assertEquals(0, repository.writeCount)
    }

    @Test
    fun g2_create_knownCourseTagKeepsRegularSource() = runTest {
        val result = commands.create("打课程表标签的普通日程", null, start, end, ScheduleTagId(1L))

        val stored = (result as ScheduleCommandResult.Success).event
        assertEquals("标签不改变来源（AC-S-04）", ScheduleSource.REGULAR, stored.source)
        assertEquals(ScheduleTagId(1L), stored.tagId)
    }

    @Test
    fun g2_update_blankTitleIsRejectedAndKeepsStoredValue() = runTest {
        val created = (commands.create("原标题", null, start, end, null)
            as ScheduleCommandResult.Success).event

        val result = commands.updateField(created.id, ScheduleFieldUpdate.Title("   "))

        assertEquals(ScheduleError.BLANK_TITLE, invalidError(result))
        assertEquals("原标题", repository.get(created.id)?.title)
        assertEquals("拒绝更新不得写库", 1, repository.writeCount)
    }

    @Test
    fun g2_update_endBeforeStartIsRejectedAndKeepsStoredValue() = runTest {
        val created = (commands.create("跨天", null, start, end, null)
            as ScheduleCommandResult.Success).event

        val result = commands.updateField(
            created.id,
            ScheduleFieldUpdate.End(start.minusSeconds(60)),
        )

        assertEquals(ScheduleError.END_NOT_AFTER_START, invalidError(result))
        assertEquals(end, repository.get(created.id)?.endAt)
        assertEquals(1, repository.writeCount)
    }

    @Test
    fun g2_update_existingEvent_persistsSingleField() = runTest {
        val created = (commands.create("改标题", null, start, end, null)
            as ScheduleCommandResult.Success).event

        val result = commands.updateField(created.id, ScheduleFieldUpdate.Title("新标题"))

        assertTrue(result is ScheduleCommandResult.Success)
        assertEquals("新标题", repository.get(created.id)?.title)
    }

    @Test
    fun g2_update_missingEventReturnsNotFound() = runTest {
        val result = commands.updateField(ScheduleId(404L), ScheduleFieldUpdate.Title("不存在"))

        assertTrue("预期 NotFound，实际=$result", result is ScheduleCommandResult.NotFound)
        assertEquals(0, repository.writeCount)
    }

    @Test
    fun g2_delete_existingRemovesAndMissingReturnsNotFound() = runTest {
        val created = (commands.create("待删除", null, start, end, null)
            as ScheduleCommandResult.Success).event

        val deleted = commands.delete(created.id)
        assertTrue("预期 Deleted，实际=$deleted", deleted is ScheduleCommandResult.Deleted)
        assertNull(repository.get(created.id))

        val again = commands.delete(created.id)
        assertTrue("重复删除应为 NotFound，实际=$again", again is ScheduleCommandResult.NotFound)
    }
}
