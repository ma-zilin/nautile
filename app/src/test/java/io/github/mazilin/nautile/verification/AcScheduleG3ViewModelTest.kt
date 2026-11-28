package io.github.mazilin.nautile.verification

import androidx.lifecycle.viewModelScope
import io.github.mazilin.nautile.domain.model.ScheduleEvent
import io.github.mazilin.nautile.domain.model.ScheduleId
import io.github.mazilin.nautile.domain.model.ScheduleSource
import io.github.mazilin.nautile.domain.model.ScheduleSourceFilter
import io.github.mazilin.nautile.domain.model.ScheduleTag
import io.github.mazilin.nautile.domain.model.ScheduleTagId
import io.github.mazilin.nautile.domain.port.Clock
import io.github.mazilin.nautile.domain.rule.ScheduleRules
import io.github.mazilin.nautile.testing.InMemoryScheduleRepository
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleEditorField
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleEditorMode
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleEditorUiState
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleUiState
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * R6-G3-V1 · 日程新建/详情编辑器与 ViewModel 写库口径（AC-D-06/07/08、AC-S-04）。
 *
 * 预期来自基线 v2.16 与 05 §2.3/§3.2：新建只有“创建”落库、取消不生成；创建成功原位插入、
 * 不跳页、不改筛选；详情字段编辑即保存；无效值不写库且阻止关闭；标签不改变来源。
 * 内存仓库替身记录写次数与快照；时钟、时区固定，避免波动。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AcScheduleG3ViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val today: LocalDate = LocalDate.of(2026, 11, 25)
    private val fixedNow: Instant = today.atTime(10, 0).atZone(zone).toInstant()
    private val courseTag = ScheduleTag(ScheduleTagId(1L), "课程表", "COURSE", 0)

    private lateinit var repository: InMemoryScheduleRepository
    private val viewModels = mutableListOf<ScheduleViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = InMemoryScheduleRepository(
            initialEvents = listOf(event(1L, "原日程", today, 9, 10)),
            initialTags = listOf(courseTag),
        )
    }

    @After
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun event(
        id: Long,
        title: String,
        date: LocalDate,
        startHour: Int,
        endHour: Int,
        tagId: ScheduleTagId? = null,
    ): ScheduleEvent {
        val start = date.atTime(startHour, 0).atZone(zone).toInstant()
        val end = date.atTime(endHour, 0).atZone(zone).toInstant()
        return ScheduleEvent(
            id = ScheduleId(id),
            title = title,
            note = null,
            startAt = start,
            endAt = end,
            source = ScheduleSource.MANUAL,
            tagId = tagId,
            seriesId = null,
            originalStartAt = null,
            createdAt = start,
            updatedAt = start,
        )
    }

    private fun newViewModel(): ScheduleViewModel =
        ScheduleViewModel(repository, Clock { fixedNow }, zone).also { viewModels += it }

    private suspend fun ScheduleViewModel.await(
        description: String,
        predicate: (ScheduleUiState) -> Boolean,
    ): ScheduleUiState {
        val state = uiState.first(predicate)
        assertTrue(description, predicate(state))
        return state
    }

    private fun ScheduleViewModel.editorOrFail(): ScheduleEditorUiState {
        val state = editor.value
        assertNotNull("编辑器应已打开", state)
        return state!!
    }

    @Test
    fun acD07_openNewThenCancel_writesNothing() = runTest(dispatcher) {
        val viewModel = newViewModel()

        viewModel.onOpenNew()
        val opened = viewModel.editorOrFail()
        assertEquals(ScheduleEditorMode.CREATE, opened.mode)
        assertEquals("新建默认来源手动创建", ScheduleSource.MANUAL, opened.source)
        assertEquals("新建默认无标签", null, opened.tagId)

        viewModel.closeEditor()

        assertNull("取消后面板应关闭", viewModel.editor.value)
        assertEquals("取消不得生成半成品（AC-D-07）", 0, repository.writeCount)
        assertEquals(1, repository.snapshot.size)
    }

    @Test
    fun acD07_createWithBlankTitle_staysOpenWithoutWrite() = runTest(dispatcher) {
        val viewModel = newViewModel()
        var created = false
        viewModel.onOpenNew()
        viewModel.onEditorTitleChanged("   ")

        viewModel.createNew { created = true }

        val state = viewModel.editorOrFail()
        assertFalse("无效标题不得关闭面板", state.canDismiss)
        assertNotNull("应内联提示标题为空", state.saveError)
        assertFalse("不得回调创建成功", created)
        assertEquals("无效输入不得写库（AC-D-07）", 0, repository.writeCount)
    }

    @Test
    fun acD07_createWithInvalidRange_staysOpen_andNoIllegalIntermediate() = runTest(dispatcher) {
        val viewModel = newViewModel()
        var created = false
        viewModel.onOpenNew()
        viewModel.onEditorTitleChanged("等长时间")
        // 今天 10:00 新建的默认起止是 10:00–11:00；清除结束时间后不得生成。
        viewModel.onEditorTimeCleared(ScheduleEditorField.END)

        val missing = viewModel.editorOrFail()
        assertFalse("结束时间为空时应阻止提交", missing.canDismiss)
        viewModel.createNew { created = true }
        assertFalse(created)
        assertEquals(0, repository.writeCount)

        // 结束与开始相等：仍拒绝，不静默改成 +1 小时。
        viewModel.onEditorTimeConfirmed(ScheduleEditorField.END, LocalTime.of(10, 0))
        val equal = viewModel.editorOrFail()
        assertEquals("结束时间必须晚于开始时间", equal.rangeError)
        viewModel.createNew { created = true }
        assertFalse(created)
        assertEquals("相等起止不得写库", 0, repository.writeCount)

        // 修正后才允许创建。
        viewModel.onEditorTimeConfirmed(ScheduleEditorField.END, LocalTime.of(10, 30))
        viewModel.createNew { created = true }
        assertTrue("修正后应创建成功（AC-D-08）", created)
        assertNull(viewModel.editor.value)
        assertEquals(2, repository.snapshot.size)
    }

    @Test
    fun acD07_createSuccess_insertsInPlace_keepingDateAndFilter() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.await("初始状态应加载完成") { !it.isLoading }
        viewModel.onSourceFilterSelected(ScheduleSourceFilter.REGULAR_ONLY)
        viewModel.onDateSelected(today)
        viewModel.onOpenNew()
        viewModel.onEditorTitleChanged("原位插入的日程")
        val placeholder = viewModel.editorOrFail()

        viewModel.createNew {}

        val after = viewModel.await("新日程应原位出现在当前筛选与选中日") {
            it.sourceFilter == ScheduleSourceFilter.REGULAR_ONLY && it.events.any { block ->
                block.event.title == "原位插入的日程"
            }
        }
        assertEquals("创建不得跳页（选中日期不变）", today, after.selectedDate)
        assertEquals("创建不得改来源筛选（AC-D-07）", ScheduleSourceFilter.REGULAR_ONLY, after.sourceFilter)
        assertEquals("新建日期应沿用打开面板时的选中日", placeholder.startDate, today)
        val created = repository.snapshot.first { it.title == "原位插入的日程" }
        assertEquals("标签不改变来源（AC-S-04）", ScheduleSource.MANUAL, created.source)
        assertEquals(2, repository.snapshot.size)
    }

    @Test
    fun acD06_editTitleAndNote_persistPerField_withoutClosing() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.onOpenDetail(ScheduleId(1L))
        viewModel.editorOrFail()

        viewModel.onEditorTitleChanged("改后标题")
        viewModel.commitEditorTitle()
        assertEquals("切换字段即保存标题（AC-D-06）", "改后标题", repository.snapshot.first().title)
        assertNotNull("详情应仍打开（编辑即保存，无整页保存按钮）", viewModel.editor.value)

        viewModel.onEditorNoteChanged("备注草稿")
        viewModel.commitEditorNote()
        assertEquals("备注编辑即保存", "备注草稿", repository.snapshot.first().note)
    }

    @Test
    fun acD06_blankTitleEdit_doesNotWrite_andBlocksDismiss() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.onOpenDetail(ScheduleId(1L))
        viewModel.editorOrFail()
        val writesBefore = repository.writeCount

        viewModel.onEditorTitleChanged("   ")
        viewModel.commitEditorTitle()

        assertEquals("空标题不得写库", writesBefore, repository.writeCount)
        assertEquals("原值应保留", "原日程", repository.snapshot.first().title)
        assertFalse("无效字段应阻止关闭（AC-D-06）", viewModel.dismissEditor())
        assertNotNull(viewModel.editor.value)
    }

    @Test
    fun acD08_twoDismissAttemptsBothBlocked_thenCorrectionAllowsLeave() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.onOpenDetail(ScheduleId(1L))
        viewModel.editorOrFail()
        viewModel.onEditorTitleChanged("")
        viewModel.commitEditorTitle()

        assertFalse("第一次关闭应被阻止", viewModel.dismissEditor())
        assertFalse("第二次关闭仍应被阻止（AC-D-08）", viewModel.dismissEditor())
        assertNotNull("面板应保留", viewModel.editor.value)

        viewModel.onEditorTitleChanged("修正后标题")
        viewModel.commitEditorTitle()
        assertTrue("修正后应可离开（AC-D-08）", viewModel.dismissEditor())
        assertNull(viewModel.editor.value)
        assertEquals("修正值应落库", "修正后标题", repository.snapshot.first().title)
    }

    @Test
    fun acD06_dateRangeEdit_writesOnlyWhenValid() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.onOpenDetail(ScheduleId(1L))
        viewModel.editorOrFail()
        val writesBefore = repository.writeCount

        viewModel.onEditorStartDateSelected(today.plusDays(1))
        assertEquals("开始晚于结束不得写库（不产生非法中间状态）", writesBefore, repository.writeCount)

        viewModel.onEditorEndDateSelected(today.plusDays(1))
        assertEquals("起止同时合法才写一次", writesBefore + 1, repository.writeCount)
        val stored = repository.snapshot.first()
        assertEquals(today.plusDays(1), stored.startAt.atZone(zone).toLocalDate())
        assertEquals(today.plusDays(1), stored.endAt.atZone(zone).toLocalDate())
        assertEquals(LocalTime.of(9, 0), stored.startAt.atZone(zone).toLocalTime())
    }

    @Test
    fun acS04_tagSelection_neverChangesSource() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.onOpenDetail(ScheduleId(1L))
        val opened = viewModel.editorOrFail()
        assertEquals("详情来源只读显示手动创建", ScheduleSource.MANUAL, opened.source)

        viewModel.onCourseTagSelected()
        val tagged = repository.snapshot.first()
        assertEquals("手动标课程表不得改来源（AC-S-04）", ScheduleSource.MANUAL, tagged.source)
        assertEquals(courseTag.id, tagged.tagId)
        assertTrue(viewModel.editorOrFail().isCourseTagSelected)

        viewModel.onNoTagSelected()
        assertNull("切回无标签应清空 tagId", repository.snapshot.first().tagId)
        assertEquals(ScheduleSource.MANUAL, repository.snapshot.first().source)
    }

    @Test
    fun acD06_delete_removesRecordAndClosesEditor() = runTest(dispatcher) {
        val viewModel = newViewModel()
        var deleted = false
        viewModel.onOpenDetail(ScheduleId(1L))
        viewModel.editorOrFail()

        viewModel.deleteSchedule { deleted = true }

        assertTrue(deleted)
        assertNull(viewModel.editor.value)
        assertTrue("删除应移除记录", repository.snapshot.isEmpty())
    }

    @Test
    fun acD06_openUnknownDetail_keepsEditorClosed() = runTest(dispatcher) {
        val viewModel = newViewModel()

        viewModel.onOpenDetail(ScheduleId(404L))

        assertNull("不存在的日程不得产生空面板", viewModel.editor.value)
        assertEquals(0, repository.writeCount)
    }

    @Test
    fun acM01_clearTimeBlocksClose_andNoWrite() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.onOpenDetail(ScheduleId(1L))
        viewModel.editorOrFail()
        val writesBefore = repository.writeCount

        viewModel.onEditorTimeCleared(ScheduleEditorField.START)

        assertFalse("清除开始时间后不得关闭", viewModel.dismissEditor())
        assertEquals("未设置时间不得写库", writesBefore, repository.writeCount)
        assertNotNull(viewModel.editor.value)
    }

    @Test
    fun acM01_timeTextRules_doNotAllowLooseInput() {
        // 与 UI 内联报错共用同一规则；此处补充 ViewModel 依赖的严格口径。
        assertNull(ScheduleRules.parseTimeText("25:00"))
        assertNull(ScheduleRules.parseTimeText("12:60"))
        assertNull(ScheduleRules.parseTimeText("非数字"))
        assertNotNull(ScheduleRules.parseTimeText("10:30"))
    }
}
