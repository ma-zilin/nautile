package io.github.mazilin.nautile.verification

import androidx.lifecycle.viewModelScope
import io.github.mazilin.nautile.domain.model.ScheduleEvent
import io.github.mazilin.nautile.domain.model.ScheduleId
import io.github.mazilin.nautile.domain.model.ScheduleSource
import io.github.mazilin.nautile.domain.model.ScheduleSourceFilter
import io.github.mazilin.nautile.domain.model.ScheduleViewMode
import io.github.mazilin.nautile.domain.port.Clock
import io.github.mazilin.nautile.testing.InMemoryScheduleRepository
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleUiState
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleViewModel
import java.time.Instant
import java.time.LocalDate
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * R6-G2-V1 · AC-S-13 的纯状态部分：点日期选日期与切换视图都不改日程数据。
 *
 * 预期来自 AC-S-13（L541）“切换后日程数据不变”与 FR-S-14“点日期选日期，左右滑动不切日期”。
 * 用内存替身记录写操作次数与快照；`ScheduleViewModel` 的日期/视图/来源都只在内存。
 * 仅替换 Main 调度器，不修改业务代码；时区与时钟固定，避免波动。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AcScheduleS13DataInvarianceTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val today: LocalDate = LocalDate.of(2026, 11, 25)
    private val fixedNow: Instant = today.atTime(10, 0).atZone(zone).toInstant()

    private lateinit var repository: InMemoryScheduleRepository
    private val createdViewModels = mutableListOf<ScheduleViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = InMemoryScheduleRepository(
            initialEvents = listOf(
                event(1L, "今天的普通日程", today, 9, 10),
                event(2L, "明天的普通日程", today.plusDays(1), 9, 10),
            ),
        )
    }

    @After
    fun tearDown() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun event(id: Long, title: String, date: LocalDate, startHour: Int, endHour: Int): ScheduleEvent {
        val start = date.atTime(startHour, 0).atZone(zone).toInstant()
        val end = date.atTime(endHour, 0).atZone(zone).toInstant()
        return ScheduleEvent(
            id = ScheduleId(id),
            title = title,
            note = null,
            startAt = start,
            endAt = end,
            source = ScheduleSource.REGULAR,
            tagId = null,
            seriesId = null,
            originalStartAt = null,
            createdAt = start,
            updatedAt = start,
        )
    }

    private fun newViewModel(): ScheduleViewModel =
        ScheduleViewModel(repository, Clock { fixedNow }, zone).also { createdViewModels += it }

    private suspend fun ScheduleViewModel.await(
        description: String,
        predicate: (ScheduleUiState) -> Boolean,
    ): ScheduleUiState {
        val state = uiState.first(predicate)
        assertTrue(description, predicate(state))
        return state
    }

    @Test
    fun acS13_selectingDate_changesRangeOnlyAndWritesNothing() = runTest(dispatcher) {
        val viewModel = newViewModel()
        val snapshotBefore = repository.snapshot
        val before = viewModel.await("初始状态应加载完成") { !it.isLoading }
        assertEquals("日视图只应包含选中日的事件", 1, before.events.size)

        viewModel.onDateSelected(today.plusDays(1))
        val after = viewModel.await("选中日应切到次日") { it.selectedDate == today.plusDays(1) }

        assertEquals(today.plusDays(1), after.visibleRange.startInclusive)
        assertEquals(1, after.visibleRange.dayCount)
        assertEquals("点日期选日期不得改数据", snapshotBefore, repository.snapshot)
        assertEquals("点日期不得写库", 0, repository.writeCount)
    }

    @Test
    fun acS13_switchingViewMode_changesRangeOnlyAndWritesNothing() = runTest(dispatcher) {
        val viewModel = newViewModel()
        val snapshotBefore = repository.snapshot
        val day = viewModel.await("初始应为日视图") { !it.isLoading && it.viewMode == ScheduleViewMode.DAY }
        val dayRange = day.visibleRange
        assertEquals("日视图只显示选中日", 1, day.events.size)

        viewModel.onViewModeSelected(ScheduleViewMode.THREE_DAY)
        val threeDay = viewModel.await("应切到三天视图") { it.viewMode == ScheduleViewMode.THREE_DAY }

        viewModel.onViewModeSelected(ScheduleViewMode.WEEK)
        val week = viewModel.await("应切到周视图") { it.viewMode == ScheduleViewMode.WEEK }

        assertEquals(3, threeDay.visibleRange.dayCount)
        assertEquals(7, week.visibleRange.dayCount)
        assertEquals("周视图必须从周一开始", java.time.DayOfWeek.MONDAY, week.visibleRange.startInclusive.dayOfWeek)
        assertEquals("三天视图应同时含今天与明天", 2, threeDay.events.size)
        // 拒绝：若实现忽略视图模式，三种范围会相等，下面的断言会失败。
        assertNotEquals("日视图与三天视图范围必须不同", dayRange, threeDay.visibleRange)
        assertNotEquals("三天视图与周视图范围必须不同", threeDay.visibleRange, week.visibleRange)
        assertEquals("切换视图不得改数据", snapshotBefore, repository.snapshot)
        assertEquals("切换视图不得写库", 0, repository.writeCount)
    }

    @Test
    fun acS13_sourceFilter_doesNotChangeStoredData() = runTest(dispatcher) {
        val viewModel = newViewModel()
        val snapshotBefore = repository.snapshot
        viewModel.await("初始状态应加载完成") { !it.isLoading }

        ScheduleSourceFilter.entries.forEach { filter ->
            viewModel.onSourceFilterSelected(filter)
            val state = viewModel.await("来源筛选应生效：$filter") { it.sourceFilter == filter }
            assertEquals("来源筛选 $filter 不得改数据", snapshotBefore, repository.snapshot)
            val expectedCount = if (filter == ScheduleSourceFilter.COURSE_ONLY) 0 else 1
            assertEquals("来源筛选 $filter 的事件数不符", expectedCount, state.events.size)
        }
        assertEquals(0, repository.writeCount)
    }
}
