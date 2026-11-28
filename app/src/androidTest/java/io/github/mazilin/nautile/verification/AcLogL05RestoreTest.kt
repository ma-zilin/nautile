package io.github.mazilin.nautile.verification

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.data.repository.RoomTimeCategoryRepository
import io.github.mazilin.nautile.data.repository.RoomTimeLogRepository
import io.github.mazilin.nautile.domain.model.TimeCategory
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.model.TimeLog
import io.github.mazilin.nautile.domain.model.TimeLogId
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R6-G4-V2 · AC-L-05（模拟器部分）force-stop 恢复。
 *
 * 这不是一个自包含的 UI 用例：由 V2 命令序列分三步执行——
 * 1. [seedRunningTimerForForceStop]：向应用真实数据库写入一条“15 分钟前开始”的运行中记录；
 * 2. `adb shell am force-stop` → 重新启动 App → 切到日志页 → `uiautomator dump` 核对显示约 15 分钟；
 * 3. [verifyRunningTimerSurvived]：重新打开数据库，核对开始时刻未变、end_at 仍为空、记录未丢。
 *
 * 只有显式传入 instrumentation 参数 `-e l05_manual true` 时才执行；
 * 全量 connected 套件会跳过（Assume），避免污染应用真实数据库与 DataStore。
 * 只用于模拟器辅助证据；真机锁屏/划掉/设备重启仍列为“未自动化”。
 * 验证结束后清理夹具分类与记录。
 */
@RunWith(AndroidJUnit4::class)
class AcLogL05RestoreTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val prefs get() = context.getSharedPreferences("verify_l05", Context.MODE_PRIVATE)

    private fun manualSequenceEnabled(): Boolean =
        InstrumentationRegistry.getArguments().getString("l05_manual") == "true"

    @Test
    fun seedRunningTimerForForceStop() {
        Assume.assumeTrue("仅由 V2 手动 force-stop 序列执行（-e l05_manual true）", manualSequenceEnabled())
        val database = LogG4.appDatabase(context)
        val startAt = Instant.now().minusSeconds(FIFTEEN_MINUTES_SECONDS)
        try {
            val categoryRepository = RoomTimeCategoryRepository(database)
            val logRepository = RoomTimeLogRepository(database)
            runBlocking {
                val existing = categoryRepository.observeAll().first()
                    .firstOrNull { it.name == VERIFY_CATEGORY }
                val categoryId = existing?.id ?: categoryRepository.insert(
                    TimeCategory(
                        id = TimeCategoryId(0),
                        name = VERIFY_CATEGORY,
                        parentId = null,
                        sortOrder = categoryRepository.nextSortOrder(null),
                        createdAt = startAt,
                        updatedAt = startAt,
                    ),
                )
                // 清理上一次验证残留，保证只有一段运行中。
                logRepository.observeAllDescending().first()
                    .filter { it.categoryId == categoryId }
                    .forEach { logRepository.delete(it.id) }
                logRepository.insert(
                    TimeLog(
                        id = TimeLogId(0),
                        categoryId = categoryId,
                        startAt = startAt,
                        endAt = null,
                        createdAt = startAt,
                        updatedAt = startAt,
                    ),
                )
            }
            prefs.edit()
                .putLong(KEY_START_AT, startAt.toEpochMilli())
                .putString(KEY_CATEGORY, VERIFY_CATEGORY)
                .commit()
        } finally {
            database.close()
        }
    }

    @Test
    fun verifyRunningTimerSurvivedForceStop() {
        Assume.assumeTrue("仅由 V2 手动 force-stop 序列执行（-e l05_manual true）", manualSequenceEnabled())
        val startMillis = prefs.getLong(KEY_START_AT, 0L)
        Assume.assumeTrue("需要先执行 force-stop 序列的 seed 方法", startMillis > 0L)
        val expectedStart = Instant.ofEpochMilli(startMillis)
        val database = LogG4.appDatabase(context)
        val categoryRepository = RoomTimeCategoryRepository(database)
        val logRepository = RoomTimeLogRepository(database)
        try {
            val running = runBlocking { logRepository.observeRunning().first() }
            val log = running.firstOrNull { it.startAt == expectedStart }
            assertNotNull("重开后运行中记录不得丢失", log)
            assertNull("重开后不得产生结束时间或暂停态", log?.endAt)

            val elapsed = Duration.between(log!!.startAt, Instant.now())
            println("L05 force-stop restore: start=${log.startAt} elapsedSeconds=${elapsed.seconds}")
            assertTrue(
                "按开始时刻计算应不少于 15 分钟（实际 ${elapsed.seconds}s）",
                elapsed.seconds >= FIFTEEN_MINUTES_SECONDS - 5,
            )
            assertTrue(
                "开始时刻误差不超过 1 分钟（与写入值完全一致）",
                log.startAt == expectedStart,
            )
        } finally {
            // 清理夹具，避免影响后续用例。
            runBlocking {
                val log = logRepository.observeRunning().first()
                    .firstOrNull { it.startAt == expectedStart }
                log?.let { logRepository.delete(it.id) }
                val category = categoryRepository.observeAll().first()
                    .firstOrNull { it.name == VERIFY_CATEGORY }
                category?.let { categoryRepository.deleteIfUnused(it.id) }
            }
            database.close()
        }
        prefs.edit().clear().commit()
    }

    private companion object {
        const val VERIFY_CATEGORY = "验证分类（L05）"
        const val KEY_START_AT = "start_at"
        const val KEY_CATEGORY = "category"
        const val FIFTEEN_MINUTES_SECONDS = 15L * 60L
    }
}
