package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.domain.model.ScheduleSource
import io.github.mazilin.nautile.domain.rule.ScheduleRules
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleEditorMode
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleEditorUiState
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R6-G3-V1 · 日报编辑器状态与严格 24h 校验（AC-D-06/08、AC-M-01 辅助核对）。
 *
 * 预期来自需求基线 v2.16：AC-D-06（字段编辑即保存、无效阻止并提示、不产生非法中间结果）、
 * AC-D-08（无效两次被阻止、修正后可离开）、AC-D-07（非法 24h 内联报错、阻止提交、不静默修正）。
 * 只用纯 JVM：不依赖模拟器与真实数据库。
 */
class AcScheduleG3EditorStateTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 25)

    private fun state(
        title: String = "概率论",
        startTime: LocalTime? = LocalTime.of(9, 0),
        endTime: LocalTime? = LocalTime.of(10, 30),
        startDate: LocalDate = today,
        endDate: LocalDate = today,
        isSaving: Boolean = false,
    ): ScheduleEditorUiState = ScheduleEditorUiState(
        mode = ScheduleEditorMode.EDIT,
        scheduleId = null,
        title = title,
        note = "",
        startDate = startDate,
        startTime = startTime,
        endDate = endDate,
        endTime = endTime,
        source = ScheduleSource.REGULAR,
        tagId = null,
        isSaving = isSaving,
    )

    @Test
    fun acD06_editor_blankTitle_isInvalid_andBlocksDismiss() {
        val blank = state(title = "   ")

        assertEquals("标题不能为空", blank.titleError)
        assertFalse("空标题不得允许关闭（AC-D-06）", blank.canDismiss)
        assertNotNull("空标题应给出提示", blank.validationMessage)
    }

    @Test
    fun acD06_editor_endEqualStart_isRejected() {
        val equal = state(
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(9, 0),
        )

        assertEquals("结束时间必须晚于开始时间", equal.rangeError)
        assertFalse(equal.canDismiss)
        assertNotNull("结束不晚于开始应阻止关闭", equal.validationMessage)
    }

    @Test
    fun acD06_editor_endBeforeStart_isRejected() {
        val reversed = state(
            startTime = LocalTime.of(10, 0),
            endTime = LocalTime.of(9, 30),
        )

        assertEquals("结束时间必须晚于开始时间", reversed.rangeError)
        assertFalse(reversed.isValid)
    }

    @Test
    fun acD06_editor_crossDayEnd_isValid() {
        val crossDay = state(
            startDate = today,
            startTime = LocalTime.of(23, 0),
            endDate = today.plusDays(1),
            endTime = LocalTime.of(1, 0),
        )

        assertNull("跨天且结束晚于开始应合法", crossDay.validationMessage)
        assertTrue(crossDay.isValid)
        assertTrue(crossDay.canDismiss)
    }

    @Test
    fun acD06_editor_missingTime_isInvalid_andBlocksDismiss() {
        val noStart = state(startTime = null)
        val noEnd = state(endTime = null)

        assertNotNull("未设置开始时间应阻止提交", noStart.validationMessage)
        assertNotNull("未设置结束时间应阻止提交", noEnd.validationMessage)
        assertFalse(noStart.canDismiss)
        assertFalse(noEnd.canDismiss)
    }

    @Test
    fun acD06_editor_savingBlockDismiss() {
        val saving = state(isSaving = true)

        assertTrue("字段本身有效", saving.isValid)
        assertFalse("提交中不得关闭，避免已发出写入却关闭面板", saving.canDismiss)
    }

    @Test
    fun acD08_editor_invalidThenCorrected_becomesValid() {
        val invalid = state(endTime = LocalTime.of(8, 0))
        assertFalse("无效值后应阻止离开（AC-D-08）", invalid.canDismiss)
        assertNotNull(invalid.validationMessage)

        val corrected = invalid.copy(endTime = LocalTime.of(10, 30))
        assertTrue("修正后可保存并离开（AC-D-08）", corrected.canDismiss)
        assertNull(corrected.validationMessage)
    }

    @Test
    fun acM01_strictTimeText_acceptsOnlyTwoDigit24h() {
        listOf("00:00", "09:05", "12:00", "23:59").forEach { text ->
            assertNotNull("严格 HH:mm 应接受：$text", ScheduleRules.parseTimeText(text))
            assertNull("合法文本不应报错：$text", ScheduleRules.timeTextError(text))
        }
    }

    @Test
    fun acM01_strictTimeText_rejectsOutOfRangeNonNumericAndLooseFormats() {
        // AC-M-01 明确列出 25:00、12:60、非数字；另有 24:00、单位数小时等越界/非严格格式。
        listOf("25:00", "12:60", "24:00", "abc", "12", "9:05", "12:0", "12:000", "-1:00").forEach { text ->
            assertNull("非法 24h 文本不得被解析：$text", ScheduleRules.parseTimeText(text))
            assertEquals(
                "非法 24h 文本应内联报错：$text",
                ScheduleRules.TIME_FORMAT_ERROR,
                ScheduleRules.timeTextError(text),
            )
        }
    }

    @Test
    fun acM01_invalidTime_isNotSilentlyCorrected() {
        // “不静默修正”：25:00 不得被夹到 23:59、12:60 不得被进位为 13:00。
        assertNull(ScheduleRules.parseTimeText("25:00"))
        assertNull(ScheduleRules.parseTimeText("12:60"))
        assertNotNull(ScheduleRules.timeTextError("25:00"))
        assertNotNull(ScheduleRules.timeTextError("12:60"))
        // 合法值保持原值，不做额外变换。
        assertEquals(LocalTime.of(23, 59), ScheduleRules.parseTimeText("23:59"))
        assertEquals(LocalTime.of(0, 0), ScheduleRules.parseTimeText("00:00"))
    }

    @Test
    fun acS04_editor_source_isAlwaysRegularThisPhase() {
        val editor = state()

        assertEquals("本轮新建/详情来源固定普通（用户 2026-11-28 确认只读）", ScheduleSource.REGULAR, editor.source)
        assertEquals("普通", editor.sourceLabel)
        assertNull("默认无标签", editor.tagId)
        assertFalse("默认未选中课程表标签", editor.isCourseTagSelected)
    }
}
