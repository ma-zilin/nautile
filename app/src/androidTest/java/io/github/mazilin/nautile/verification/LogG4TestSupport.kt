package io.github.mazilin.nautile.verification

import android.content.Context
import androidx.room.Room
import io.github.mazilin.nautile.data.db.MIGRATION_1_2
import io.github.mazilin.nautile.data.db.MIGRATION_2_3
import io.github.mazilin.nautile.data.db.MIGRATION_3_4
import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.domain.model.TimeCategory
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.model.TimeLog
import io.github.mazilin.nautile.domain.model.TimeLogId
import io.github.mazilin.nautile.domain.port.Clock
import io.github.mazilin.nautile.domain.repository.TimeCategoryRepository
import io.github.mazilin.nautile.domain.repository.TimeLogRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * R6-G4-V2 设备层测试支撑（独立验证，不属于业务代码）。
 *
 * 固定时钟与时区，使 UI 断言可重复；数据库一律使用内存库（AC-L-05 例外，单独说明）。
 */
internal object LogG4 {

    val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    val today: LocalDate = LocalDate.of(2026, 11, 28)

    val now: Instant = today.atTime(10, 0).atZone(zone).toInstant()

    fun instant(date: LocalDate, hour: Int, minute: Int = 0): Instant =
        date.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant()

    fun freshDatabase(context: Context): NautileDatabase =
        Room.inMemoryDatabaseBuilder(context, NautileDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    /** 真实应用数据库（仅 AC-L-05 force-stop 恢复场景使用）。 */
    fun appDatabase(context: Context): NautileDatabase =
        Room.databaseBuilder(context, NautileDatabase::class.java, "nautile.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

    suspend fun seedCategory(
        categories: TimeCategoryRepository,
        name: String,
        createdAt: Instant = now,
        parentId: TimeCategoryId? = null,
    ): TimeCategoryId = categories.insert(
        TimeCategory(
            id = TimeCategoryId(0),
            name = name,
            parentId = parentId,
            sortOrder = categories.nextSortOrder(parentId),
            createdAt = createdAt,
            updatedAt = createdAt,
        ),
    )

    suspend fun seedLog(
        logs: TimeLogRepository,
        categoryId: TimeCategoryId,
        startAt: Instant,
        endAt: Instant?,
    ): TimeLogId = logs.insert(
        TimeLog(
            id = TimeLogId(0),
            categoryId = categoryId,
            startAt = startAt,
            endAt = endAt,
            createdAt = startAt,
            updatedAt = startAt,
        ),
    )
}

/** 可推进的固定时钟。 */
internal class G4Clock(private var current: Instant = LogG4.now) : Clock {

    override fun now(): Instant = current

    fun advanceSeconds(seconds: Long) {
        current = current.plusSeconds(seconds)
    }
}
