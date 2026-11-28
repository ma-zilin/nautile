package io.github.mazilin.nautile.verification

import androidx.lifecycle.viewModelScope
import io.github.mazilin.nautile.domain.model.ScheduleEvent
import io.github.mazilin.nautile.domain.model.ScheduleId
import io.github.mazilin.nautile.domain.model.ScheduleSource
import io.github.mazilin.nautile.domain.model.ScheduleSourceFilter
import io.github.mazilin.nautile.domain.model.ScheduleTag
import io.github.mazilin.nautile.domain.model.ScheduleTagId
import io.github.mazilin.nautile.domain.model.TimeRange
import io.github.mazilin.nautile.domain.port.Clock
import io.github.mazilin.nautile.domain.repository.ScheduleRepository
import io.github.mazilin.nautile.testing.InMemoryScheduleRepository
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleEditorField
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleEditorUiState
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleUiState
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleViewModel
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * R6-G4-V1 · G3 遗留异常修复回归（G4-T0）。
 *
 * 注入抛异常的内存替身，验证 `ScheduleViewModel` 在创建/删除/保存时：
 * 转为失败提示、不崩溃、不显示成功、不关闭面板、不写非法中间状态。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AcScheduleG4ErrorRegressionTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val today: LocalDate = LocalDate.of(2026, 11, 28)
    private val fixedNow: Instant = today.atTime(10, 0).atZone(zone).toInstant()
    private val courseTag = ScheduleTag(ScheduleTagId(1L), "课程表", "COURSE", 0)

    private lateinit var delegate: InMemoryScheduleRepository
    private lateinit var failing: FailingScheduleRepository
    private val viewModels = mutableListOf<ScheduleViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        delegate = InMemoryScheduleRepository(
            initialEvents = listOf(event(1L, "原日程", today, 9, 10)),
            initialTags = listOf(courseTag),
        )
        failing = FailingScheduleRepository(delegate)
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
    ): ScheduleEvent {
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
        ScheduleViewModel(failing, Clock { fixedNow }, zone).also { viewModels += it }

    private suspend fun ScheduleViewModel.awaitState(
        description: String,
        predicate: (ScheduleUiState) -> Boolean,
    ): ScheduleUiState {
        val state = uiState.first(predicate)
        assertFalse(description, predicate(state).not())
        return state
    }

    @Test
    fun G4_regression_create_repository_failure_shows_error_without_success_or_crash() =
        runTest(dispatcher) {
            val viewModel = newViewModel()
            var created = false
            viewModel.onOpenNew()
            viewModel.onEditorTitleChanged("异常日程")
            failing.failWrites = true

            viewModel.createNew { created = true }

            val editor: ScheduleEditorUiState = viewModel.editor.first { it?.saveError != null }!!
            assertEquals("创建失败，请重试", editor.saveError)
            assertFalse("不得回调创建成功", created)
            assertFalse("不得停留在保存中", editor.isSaving)
            assertNotNull("失败后应保留面板", viewModel.editor.value)
            assertEquals("不得写入任何日程", 1, delegate.snapshot.size)
        }

    @Test
    fun G4_regression_delete_repository_failure_shows_error_and_keeps_record() =
        runTest(dispatcher) {
            val viewModel = newViewModel()
            var deleted = false
            viewModel.onOpenDetail(ScheduleId(1L))
            viewModel.editor.first { it != null }
            failing.failWrites = true

            viewModel.deleteSchedule { deleted = true }

            val editor = viewModel.editor.first { it?.saveError != null }!!
            assertEquals("删除失败，请重试", editor.saveError)
            assertFalse("不得回调删除成功", deleted)
            assertNotNull("失败后应保留详情", viewModel.editor.value)
            assertEquals("记录不得被删除", 1, delegate.snapshot.size)
        }

    @Test
    fun G4_regression_update_repository_failure_shows_error_and_keeps_original() =
        runTest(dispatcher) {
            val viewModel = newViewModel()
            viewModel.onOpenDetail(ScheduleId(1L))
            viewModel.editor.first { it != null }
            failing.failWrites = true

            viewModel.onEditorTimeConfirmed(ScheduleEditorField.END, LocalTime.of(10, 30))

            val editor = viewModel.editor.first { it?.saveError != null }!!
            assertEquals("保存失败，请重试", editor.saveError)
            assertEquals("原值不得被改写", LocalTime.of(10, 0), delegate.snapshot.single().endAt.atZone(zone).toLocalTime())
            assertNotNull("失败后应保留详情", viewModel.editor.value)
        }

    /** 只让写操作抛异常；读取与标签查询保持正常，避免初始化与加载被干扰。 */
    private class FailingScheduleRepository(
        private val delegate: ScheduleRepository,
    ) : ScheduleRepository by delegate {

        var failWrites: Boolean = false

        override suspend fun insert(event: ScheduleEvent): ScheduleId {
            if (failWrites) throw IOException("磁盘写入失败")
            return delegate.insert(event)
        }

        override suspend fun update(event: ScheduleEvent) {
            if (failWrites) throw IOException("磁盘写入失败")
            delegate.update(event)
        }

        override suspend fun delete(id: ScheduleId) {
            if (failWrites) throw IOException("磁盘写入失败")
            delegate.delete(id)
        }

        override fun observeTags(): Flow<List<ScheduleTag>> = delegate.observeTags()

        override fun observeInRange(
            range: TimeRange,
            source: ScheduleSourceFilter,
        ): Flow<List<ScheduleEvent>> = delegate.observeInRange(range, source)
    }
}
