package io.github.mazilin.nautile.verification

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.data.repository.RoomScheduleRepository
import io.github.mazilin.nautile.domain.model.ScheduleEvent
import io.github.mazilin.nautile.domain.model.ScheduleId
import io.github.mazilin.nautile.domain.model.ScheduleSource
import io.github.mazilin.nautile.domain.port.Clock
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleScreen
import io.github.mazilin.nautile.ui.screen.schedule.ScheduleViewModel
import io.github.mazilin.nautile.ui.theme.NautileTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking

/**
 * R6-G2-V2 共享夹具：模拟器（emulator-5554）上的日程页 UI 测试与截图。
 *
 * 时钟与周视图日期固定，避免跨天/跨时区波动；数据库使用内存 Room，不污染实际数据。
 * 课程标签种子在测试库内显式写入，模拟迁移种子，不依赖真实安装包状态。
 */
internal object ScheduleG2TestSupport {

    val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 迁移种子“课程表”标签 id（与 MIGRATION_3_4 一致）。 */
    const val COURSE_TAG_ID: Long = 1L

    fun instant(date: LocalDate, hour: Int, minute: Int = 0): Instant =
        date.atTime(hour, minute).atZone(ZONE).toInstant()

    fun clockAt(date: LocalDate, hour: Int = 10): Clock = Clock { instant(date, hour) }

    fun event(
        id: Long,
        title: String,
        date: LocalDate,
        startHour: Int,
        endHour: Int,
        source: ScheduleSource = ScheduleSource.MANUAL,
        tagId: Long? = null,
    ): ScheduleEvent {
        val start = instant(date, startHour)
        val end = instant(date, endHour)
        return ScheduleEvent(
            id = ScheduleId(id),
            title = title,
            note = null,
            startAt = start,
            endAt = end,
            source = source,
            tagId = tagId?.let { io.github.mazilin.nautile.domain.model.ScheduleTagId(it) },
            seriesId = null,
            originalStartAt = null,
            createdAt = start,
            updatedAt = start,
        )
    }

    fun createDatabase(): NautileDatabase = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        NautileDatabase::class.java,
    ).allowMainThreadQueries().build()

    fun seedCourseTag(database: NautileDatabase) {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO schedule_tags (id, name, system_key, sort_order, created_at, updated_at) " +
                "VALUES ($COURSE_TAG_ID, '课程表', 'COURSE', 0, 0, 0)",
        )
    }

    fun newViewModel(database: NautileDatabase, today: LocalDate): ScheduleViewModel =
        ScheduleViewModel(
            repository = RoomScheduleRepository(database),
            clock = clockAt(today),
            zoneId = ZONE,
        )

    fun seed(database: NautileDatabase, events: List<ScheduleEvent>) {
        val repository = RoomScheduleRepository(database)
        runBlocking { events.forEach { repository.insert(it) } }
    }
}

/** 把真实 ViewModel 接到日程页；测试与截图共用同一装配。 */
@Composable
internal fun ScheduleG2Screen(
    viewModel: ScheduleViewModel,
    dark: Boolean = false,
    onOpenNew: () -> Unit = {},
) {
    NautileTheme(darkTheme = dark) {
        val state by viewModel.uiState.collectAsState()
        ScheduleScreen(
            state = state,
            onDateSelected = viewModel::onDateSelected,
            onRangeStep = viewModel::onRangeStep,
            onToday = viewModel::onToday,
            onViewModeSelected = viewModel::onViewModeSelected,
            onSourceFilterSelected = viewModel::onSourceFilterSelected,
            onOpenNew = onOpenNew,
            onDismissMessage = viewModel::onDismissMessage,
        )
    }
}
