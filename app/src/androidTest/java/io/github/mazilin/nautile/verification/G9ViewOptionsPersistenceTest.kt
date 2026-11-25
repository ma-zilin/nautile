package io.github.mazilin.nautile.verification

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.data.datastore.DataStoreSettingsRepository
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.ViewClassification
import io.github.mazilin.nautile.domain.model.ViewOptions
import io.github.mazilin.nautile.domain.model.ViewSort
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G9 视图选项 DataStore 持久化（CMP-41、AC-T-08）。
 *
 * 预期依据 `docs/需求基线.md` v2.2 与用户 2026-11-25 的 G9 口径：
 * - 每个 GTD 模块独立保存，切换清单不串值；
 * - 重启（重建 DataStore 实例）后仍能读回；
 * - 写入失败时保留旧值，由调用方回退并提示。
 *
 * 使用真实 Preferences DataStore + 临时文件，不连接应用日常设置目录。
 */
@RunWith(AndroidJUnit4::class)
class G9ViewOptionsPersistenceTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var file: File
    private var scope: CoroutineScope? = null

    @Before
    fun setUp() {
        file = File(context.cacheDir, "g9_view_options_${UUID.randomUUID()}.preferences_pb")
        file.delete()
    }

    @After
    fun tearDown() {
        runBlocking { stopScope() }
        file.delete()
    }

    private fun newScope(): CoroutineScope =
        CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scope = it }

    private fun newDataStore(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    private suspend fun stopScope() {
        val current = scope ?: return
        scope = null
        current.coroutineContext[Job]?.cancelAndJoin()
    }

    @Test
    fun cmp41_modules_are_saved_independently_and_survive_restart() = runBlocking {
        val firstScope = newScope()
        val firstRepository = DataStoreSettingsRepository(newDataStore(firstScope))

        val nextAction = ViewOptions(ViewClassification.PRIORITY, ViewSort.CREATED)
        val waiting = ViewOptions(ViewClassification.DUE, ViewSort.DUE)
        firstRepository.setViewOptions(GtdPosition.NEXT_ACTION, nextAction.encode())
        firstRepository.setViewOptions(GtdPosition.WAITING, waiting.encode())

        val afterWrite = firstRepository.settings.first()
        assertEquals(nextAction, ViewOptions.fromSettings(afterWrite.perModuleViewOptions, GtdPosition.NEXT_ACTION))
        assertEquals(waiting, ViewOptions.fromSettings(afterWrite.perModuleViewOptions, GtdPosition.WAITING))
        // 未写入的模块不出现，也不串值。
        assertFalse(GtdPosition.INBOX in afterWrite.perModuleViewOptions)
        assertEquals(
            ViewOptions.Default,
            ViewOptions.fromSettings(afterWrite.perModuleViewOptions, GtdPosition.INBOX),
        )

        // 模拟重启：关闭第一个 DataStore 实例，用同一文件重建。
        stopScope()
        val secondScope = newScope()
        val restarted = DataStoreSettingsRepository(newDataStore(secondScope)).settings.first()

        assertEquals(
            "重启后下一步行动选项保持",
            nextAction,
            ViewOptions.fromSettings(restarted.perModuleViewOptions, GtdPosition.NEXT_ACTION),
        )
        assertEquals(
            "重启后等待中选项保持",
            waiting,
            ViewOptions.fromSettings(restarted.perModuleViewOptions, GtdPosition.WAITING),
        )
        assertEquals(
            "两模块互不串值",
            2,
            restarted.perModuleViewOptions.size,
        )
    }

    @Test
    fun cmp41_write_failure_keeps_previous_value() = runBlocking {
        val realScope = newScope()
        val realStore = newDataStore(realScope)
        val repository = DataStoreSettingsRepository(realStore)
        val original = ViewOptions(ViewClassification.PROJECT, ViewSort.MANUAL)
        repository.setViewOptions(GtdPosition.INBOX, original.encode())

        val failing = DataStoreSettingsRepository(FailingDataStore(realStore))
        try {
            failing.setViewOptions(
                GtdPosition.INBOX,
                ViewOptions(ViewClassification.PRIORITY, ViewSort.DUE).encode(),
            )
            fail("写入失败应向上抛出，供 ViewModel 回退")
        } catch (expected: IOException) {
            assertTrue(expected.message.orEmpty().contains("模拟写入失败"))
        }

        val stored = repository.settings.first()
        assertEquals(
            "失败后必须保留旧值",
            original,
            ViewOptions.fromSettings(stored.perModuleViewOptions, GtdPosition.INBOX),
        )
    }

    /** 读取委托真实 DataStore；写入一律抛 IOException，用于构造失败路径。 */
    private class FailingDataStore(
        private val delegate: DataStore<Preferences>,
    ) : DataStore<Preferences> {
        override val data = delegate.data

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = throw IOException("模拟写入失败")
    }
}
