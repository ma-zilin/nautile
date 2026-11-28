package io.github.mazilin.nautile.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.data.db.NautileDatabase
import io.github.mazilin.nautile.data.db.entity.ProjectEntity
import io.github.mazilin.nautile.data.db.entity.TaskEntity
import io.github.mazilin.nautile.domain.model.AiErrorCode
import io.github.mazilin.nautile.domain.model.AiMessageStatus
import io.github.mazilin.nautile.domain.model.AiRole
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room AI 会话仓库事务（架构 §3.2；AC-AN-02 持久化、AC-AN-03 清除隔离）。
 *
 * 使用 v4 in-memory 数据库，不写真实用户数据库；时间用固定 Instant 断言，
 * 不依赖系统时钟。清除会话后断言 tasks/projects 的数量与内容不变。
 */
@RunWith(AndroidJUnit4::class)
class AiConversationRepositoryTest {

    private lateinit var database: NautileDatabase
    private lateinit var repository: RoomAiConversationRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, NautileDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomAiConversationRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun beginTurnCreatesSingleSessionAndOrderedUserAssistantPair() = runBlocking {
        val at = Instant.ofEpochMilli(1_700_000_000_000)
        val ids = repository.beginTurn("你好", at)

        val messages = repository.observeMessages().first()
        assertEquals(2, messages.size)
        assertEquals(AiRole.USER, messages[0].role)
        assertEquals(AiMessageStatus.COMPLETE, messages[0].status)
        assertEquals("你好", messages[0].content)
        assertEquals(AiRole.ASSISTANT, messages[1].role)
        assertEquals(AiMessageStatus.STREAMING, messages[1].status)
        assertEquals("占位助手内容应为空串", "", messages[1].content)
        assertEquals("用户消息与助手占位使用同一发送时刻", at, messages[0].createdAt)
        assertEquals(at, messages[1].createdAt)
        assertEquals(ids.userMessageId.value, messages[0].id.value)
        assertEquals(ids.assistantMessageId.value, messages[1].id.value)
        assertEquals(ids.sessionId.value, messages[0].sessionId.value)
        assertTrue(messages[0].id.value < messages[1].id.value)
    }

    @Test
    fun repeatedBeginTurnReusesSingleSessionAndKeepsIdOrder() = runBlocking {
        repository.beginTurn("第一问", Instant.ofEpochMilli(1_000))
        repository.beginTurn("第二问", Instant.ofEpochMilli(2_000))

        val sessions = database.query("SELECT COUNT(*) FROM ai_sessions", null)
        sessions.use {
            assertTrue(it.moveToFirst())
            assertEquals("单会话：ai_sessions 应只有一行", 1, it.getInt(0))
        }

        val messages = repository.observeMessages().first()
        assertEquals(4, messages.size)
        assertEquals(listOf("第一问", "", "第二问", ""), messages.map { it.content })
        assertEquals(messages.map { it.id.value }, messages.map { it.id.value }.sorted())
    }

    @Test
    fun finishAssistantWritesContentStatusAndErrorAndTouchesSession() = runBlocking {
        val beginAt = Instant.ofEpochMilli(1_000)
        val ids = repository.beginTurn("问题", beginAt)
        val finishAt = Instant.ofEpochMilli(9_000)

        repository.finishAssistant(
            messageId = ids.assistantMessageId,
            content = "完整回答",
            status = AiMessageStatus.COMPLETE,
            errorCode = null,
            at = finishAt,
        )

        val messages = repository.observeMessages().first()
        assertEquals("完整回答", messages[1].content)
        assertEquals(AiMessageStatus.COMPLETE, messages[1].status)

        database.query(
            "SELECT updated_at FROM ai_sessions WHERE id = ${ids.sessionId.value}",
            null,
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(9_000L, cursor.getLong(0))
        }
    }

    @Test
    fun failedAssistantKeepsErrorCodeOnlyShortCode() = runBlocking {
        val ids = repository.beginTurn("问题", Instant.ofEpochMilli(1_000))

        repository.finishAssistant(
            messageId = ids.assistantMessageId,
            content = "部分内容",
            status = AiMessageStatus.FAILED,
            errorCode = AiErrorCode.NETWORK,
            at = Instant.ofEpochMilli(2_000),
        )

        val assistant = repository.observeMessages().first()[1]
        assertEquals(AiMessageStatus.FAILED, assistant.status)
        assertEquals(AiErrorCode.NETWORK, assistant.errorCode)
        assertEquals("部分内容", assistant.content)
    }

    @Test
    fun finishAssistantAfterClearIsNoOpAndDoesNotResurrect() = runBlocking {
        val ids = repository.beginTurn("问题", Instant.ofEpochMilli(1_000))
        repository.clearConversation()

        // 已删除的行：UPDATE 命中 0 行，属安全无操作。
        repository.finishAssistant(
            messageId = ids.assistantMessageId,
            content = "迟到终态",
            status = AiMessageStatus.COMPLETE,
            errorCode = null,
            at = Instant.ofEpochMilli(2_000),
        )

        assertTrue("清除后不应复活任何消息", repository.observeMessages().first().isEmpty())
    }

    @Test
    fun clearConversationDeletesAiOnlyAndLeavesTasksAndProjectsIntact() = runBlocking {
        val projectId = database.projectDao().insert(
            ProjectEntity(name = "项目甲", status = "ACTIVE", createdAt = 10, updatedAt = 20),
        )
        val taskId = database.taskDao().insert(
            TaskEntity(
                title = "待办甲",
                detail = "详情",
                projectId = projectId,
                position = "INBOX",
                priority = "NONE",
                createdAt = 10,
                updatedAt = 20,
            ),
        )
        val projectsBefore = database.projectDao().observeAll().first()
        val tasksBefore = database.taskDao().observeAll().first()

        repository.beginTurn("会前问题", Instant.ofEpochMilli(1_000))
        repository.finishAssistant(
            messageId = repository.observeMessages().first()[1].id,
            content = "会前回答",
            status = AiMessageStatus.COMPLETE,
            errorCode = null,
            at = Instant.ofEpochMilli(2_000),
        )

        repository.clearConversation()

        assertTrue("AI 消息应清空", repository.observeMessages().first().isEmpty())
        database.query("SELECT COUNT(*) FROM ai_sessions", null).use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }

        val projectsAfter = database.projectDao().observeAll().first()
        val tasksAfter = database.taskDao().observeAll().first()
        assertEquals("清除 AI 会话不得改动项目数量", projectsBefore.size, projectsAfter.size)
        assertEquals("清除 AI 会话不得改动待办数量", tasksBefore.size, tasksAfter.size)
        assertEquals(projectsBefore.map { it.id to it.name }, projectsAfter.map { it.id to it.name })
        assertEquals(tasksBefore.map { it.id to it.title }, tasksAfter.map { it.id to it.title })
        assertEquals(projectId, projectsAfter.single().id)
        assertEquals(taskId, tasksAfter.single().id)
        assertEquals("详情", tasksAfter.single().detail)
    }

    @Test
    fun conversationSurvivesFileDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_PERSIST_DATABASE)

        var fileDatabase = Room.databaseBuilder(
            context,
            NautileDatabase::class.java,
            TEST_PERSIST_DATABASE,
        ).build()
        try {
            val fileRepository = RoomAiConversationRepository(fileDatabase)
            val ids = fileRepository.beginTurn("持久化问题", Instant.ofEpochMilli(1_000))
            fileRepository.finishAssistant(
                messageId = ids.assistantMessageId,
                content = "持久化回答",
                status = AiMessageStatus.COMPLETE,
                errorCode = null,
                at = Instant.ofEpochMilli(2_000),
            )
        } finally {
            fileDatabase.close()
        }

        fileDatabase = Room.databaseBuilder(
            context,
            NautileDatabase::class.java,
            TEST_PERSIST_DATABASE,
        ).build()
        try {
            val reopened = RoomAiConversationRepository(fileDatabase).observeMessages().first()
            assertEquals("重开数据库后消息数应保持", 2, reopened.size)
            assertEquals(listOf("持久化问题", "持久化回答"), reopened.map { it.content })
            assertEquals(AiRole.USER, reopened[0].role)
            assertEquals(AiRole.ASSISTANT, reopened[1].role)
            assertEquals(AiMessageStatus.COMPLETE, reopened[1].status)
            assertEquals(Instant.ofEpochMilli(1_000), reopened[0].createdAt)
        } finally {
            fileDatabase.close()
            context.deleteDatabase(TEST_PERSIST_DATABASE)
        }
    }

    @Test
    fun markInterruptedAsStoppedConvertsOnlyStreamingAssistantRows() = runBlocking {
        val first = repository.beginTurn("问题一", Instant.ofEpochMilli(1_000))
        repository.finishAssistant(
            messageId = first.assistantMessageId,
            content = "已完成",
            status = AiMessageStatus.COMPLETE,
            errorCode = null,
            at = Instant.ofEpochMilli(1_500),
        )
        val second = repository.beginTurn("问题二", Instant.ofEpochMilli(2_000))

        repository.markInterruptedAsStopped()

        val messages = repository.observeMessages().first()
        assertEquals(4, messages.size)
        assertEquals("已完成消息不得被改成 STOPPED", AiMessageStatus.COMPLETE, messages[1].status)
        assertEquals(AiMessageStatus.STOPPED, messages[3].status)
        assertEquals(second.assistantMessageId.value, messages[3].id.value)
        assertEquals("用户消息只写 COMPLETE", AiMessageStatus.COMPLETE, messages[2].status)
    }

    private companion object {
        const val TEST_PERSIST_DATABASE = "ai-g2-persist-test.db"
    }
}
