package io.github.mazilin.nautile.verification

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.data.repository.RoomTimeCategoryRepository
import io.github.mazilin.nautile.data.repository.RoomTimeLogRepository
import io.github.mazilin.nautile.domain.model.TimeCategory
import io.github.mazilin.nautile.domain.model.TimeCategoryId
import io.github.mazilin.nautile.domain.model.TimeLogId
import io.github.mazilin.nautile.domain.usecase.TimeCategoryCommands
import io.github.mazilin.nautile.ui.screen.settings.CategoryManagementScreen
import io.github.mazilin.nautile.ui.screen.settings.CategoryManagementViewModel
import io.github.mazilin.nautile.ui.theme.NautileTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R6-G4-V2 · AC-L-10 / AC-L-18 分类管理（自动 UI）。
 *
 * AC-L-10：两级可增删改；子级行不提供新建入口；第三级由命令层拒绝并提示。
 * AC-L-18：有子级/日志时删除被拒并提示；逐条处理后删除成功；改名后历史记录仍归该分类。
 */
@RunWith(AndroidJUnit4::class)
class AcLogL10L18UiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: io.github.mazilin.nautile.data.db.NautileDatabase
    private lateinit var categoryRepository: RoomTimeCategoryRepository
    private lateinit var logRepository: RoomTimeLogRepository
    private lateinit var viewModel: CategoryManagementViewModel
    private lateinit var clock: G4Clock

    @Before
    fun setUp() {
        database = LogG4.freshDatabase(context)
        categoryRepository = RoomTimeCategoryRepository(database)
        logRepository = RoomTimeLogRepository(database)
        clock = G4Clock()
        viewModel = CategoryManagementViewModel(
            timeCategoryRepository = categoryRepository,
            timeLogRepository = logRepository,
            commands = TimeCategoryCommands(categoryRepository, clock),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Composable
    private fun Host() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        NautileTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CategoryManagementScreen(
                        state = state,
                        onBack = {},
                        onCreateTopLevel = viewModel::openCreateTopLevel,
                        onCreateChild = viewModel::openCreateChild,
                        onEdit = viewModel::openEdit,
                        onRequestDelete = viewModel::requestDelete,
                        onNameChanged = viewModel::onNameChanged,
                        onSave = viewModel::save,
                        onCloseEdit = viewModel::closeEdit,
                        onConfirmDelete = viewModel::confirmDelete,
                        onDismissDeleteConfirm = viewModel::dismissDeleteConfirm,
                        onDismissGuard = viewModel::dismissGuard,
                        onDismissMessage = viewModel::onDismissMessage,
                    )
                }
            }
        }
    }

    private fun awaitTag(tag: String, present: Boolean = true) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present
        }
        composeRule.waitForIdle()
    }

    private fun awaitText(text: String, substring: Boolean = false) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun categoryByName(name: String): TimeCategory = runBlocking {
        categoryRepository.observeAll().first().first { it.name == name }
    }

    private fun createCategoryViaUi(name: String) {
        composeRule.onNodeWithTag("category_add_top").performClick()
        awaitTag("category_edit_sheet")
        composeRule.onNodeWithTag("category_name_field").performTextReplacement(name)
        composeRule.onNodeWithTag("category_edit_save").performClick()
        awaitTag("category_edit_sheet", present = false)
        awaitText(name)
    }

    @Test
    fun AC_L_10_two_levels_create_rename_delete_and_no_third_level_entry() {
        composeRule.setContent { Host() }
        awaitTag("category_add_top")

        createCategoryViaUi("学习")
        val study = categoryByName("学习")
        awaitTag("category_row_${study.id.value}")

        // 子级创建入口只在顶级行。
        composeRule.onNodeWithTag("category_add_child_${study.id.value}").performClick()
        awaitTag("category_edit_sheet")
        composeRule.onNodeWithTag("category_name_field").performTextReplacement("数学")
        composeRule.onNodeWithTag("category_edit_save").performClick()
        awaitTag("category_edit_sheet", present = false)
        val math = categoryByName("数学")
        awaitTag("category_row_${math.id.value}")

        composeRule.onAllNodesWithTag("category_add_child_${math.id.value}").assertCountEquals(0)

        // 第三级：命令层拒绝并提示（UI 无入口，需从状态层再兜底）。
        composeRule.runOnIdle { viewModel.openCreateChild(math) }
        awaitText("最多两级分类，不能在子级下再建分类", substring = true)
        val afterThirdLevel = runBlocking { viewModel.uiState.first() }
        assertEquals("被拒后不得打开创建面板", null, afterThirdLevel.edit)

        // 改名：名称更新，子级关系保留。
        composeRule.onNodeWithTag("category_row_${study.id.value}").performClick()
        awaitTag("category_edit_sheet")
        composeRule.onNodeWithTag("category_name_field").performTextReplacement("学习（改名）")
        composeRule.onNodeWithTag("category_edit_save").performClick()
        awaitTag("category_edit_sheet", present = false)
        awaitText("学习（改名）")
        assertEquals(study.id, categoryByName("学习（改名）").id)
        assertEquals(study.id, categoryByName("数学").parentId)

        // 删除子级与父级（均无日志）。
        composeRule.onNodeWithTag("category_delete_${math.id.value}").performClick()
        awaitTag("category_delete_confirm")
        composeRule.onNodeWithTag("category_delete_confirm").performClick()
        awaitTag("category_row_${math.id.value}", present = false)

        composeRule.onNodeWithTag("category_delete_${study.id.value}").performClick()
        awaitTag("category_delete_confirm")
        composeRule.onNodeWithTag("category_delete_confirm").performClick()
        awaitTag("category_row_${study.id.value}", present = false)
        assertTrue("两级分类均可删除", runBlocking { categoryRepository.observeAll().first() }.isEmpty())
    }

    @Test
    fun AC_L_18_delete_with_child_and_log_blocked_then_allowed_after_cleanup() {
        composeRule.setContent { Host() }
        awaitTag("category_add_top")

        createCategoryViaUi("学习")
        val study = categoryByName("学习")
        composeRule.onNodeWithTag("category_add_child_${study.id.value}").performClick()
        awaitTag("category_edit_sheet")
        composeRule.onNodeWithTag("category_name_field").performTextReplacement("数学")
        composeRule.onNodeWithTag("category_edit_save").performClick()
        awaitTag("category_edit_sheet", present = false)
        val math = categoryByName("数学")
        awaitTag("category_row_${math.id.value}")

        // 父级与子级各一条日志（直接造夹具，UI 列表实时刷新）。
        runBlocking {
            LogG4.seedLog(
                logRepository,
                study.id,
                LogG4.instant(LogG4.today, 8),
                LogG4.instant(LogG4.today, 9),
            )
            LogG4.seedLog(
                logRepository,
                math.id,
                LogG4.instant(LogG4.today, 9),
                LogG4.instant(LogG4.today, 10),
            )
        }
        awaitText("1 个子分类 · 1 条日志", substring = true)

        // 删除父级：被拒并同时提示子级与日志。
        composeRule.onNodeWithTag("category_delete_${study.id.value}").performClick()
        awaitTag("category_delete_guard")
        awaitText("无法删除分类")
        awaitText("该分类下有 1 个子分类", substring = true)
        awaitText("该分类下有 1 条日志", substring = true)
        composeRule.onNodeWithTag("category_guard_ok").performClick()
        awaitTag("category_delete_guard", present = false)
        awaitTag("category_row_${study.id.value}")

        // 删除子级：有日志被拒。
        composeRule.onNodeWithTag("category_delete_${math.id.value}").performClick()
        awaitTag("category_delete_guard")
        awaitText("该分类下有 1 条日志", substring = true)
        composeRule.onNodeWithTag("category_guard_ok").performClick()
        awaitTag("category_delete_guard", present = false)

        // 逐条删除日志后，子级可删。
        runBlocking {
            val mathLog = logRepository.observeAllDescending().first()
                .first { it.categoryId == math.id }
            logRepository.delete(mathLog.id)
        }
        awaitText("0 个子分类 · 0 条日志", substring = true)
        composeRule.onNodeWithTag("category_delete_${math.id.value}").performClick()
        awaitTag("category_delete_confirm")
        composeRule.onNodeWithTag("category_delete_confirm").performClick()
        awaitTag("category_row_${math.id.value}", present = false)

        // 删除父级日志后，父级可删。
        runBlocking {
            val studyLog = logRepository.observeAllDescending().first()
                .first { it.categoryId == study.id }
            logRepository.delete(studyLog.id)
        }
        awaitText("0 个子分类 · 0 条日志", substring = true)
        composeRule.onNodeWithTag("category_delete_${study.id.value}").performClick()
        awaitTag("category_delete_confirm")
        composeRule.onNodeWithTag("category_delete_confirm").performClick()
        awaitTag("category_row_${study.id.value}", present = false)
        assertTrue(runBlocking { categoryRepository.observeAll().first() }.isEmpty())
    }

    @Test
    fun AC_L_18_rename_keeps_history_under_same_category() {
        composeRule.setContent { Host() }
        awaitTag("category_add_top")
        createCategoryViaUi("学习")
        val study = categoryByName("学习")
        val logId: TimeLogId = runBlocking {
            LogG4.seedLog(
                logRepository,
                study.id,
                LogG4.instant(LogG4.today, 8),
                LogG4.instant(LogG4.today, 9),
            )
        }
        awaitText("0 个子分类 · 1 条日志", substring = true)

        composeRule.onNodeWithTag("category_row_${study.id.value}").performClick()
        awaitTag("category_edit_sheet")
        composeRule.onNodeWithTag("category_name_field").performTextReplacement("学习（新名）")
        composeRule.onNodeWithTag("category_edit_save").performClick()
        awaitTag("category_edit_sheet", present = false)

        awaitText("学习（新名）")
        awaitText("0 个子分类 · 1 条日志", substring = true)
        val stored = runBlocking { logRepository.get(logId) }
        assertEquals("改名后日志仍归同一分类 id", study.id, stored?.categoryId)
        assertEquals("学习（新名）", runBlocking { categoryRepository.get(study.id) }?.name)
        assertEquals(study.id.value, categoryByName("学习（新名）").id.value)
    }
}
