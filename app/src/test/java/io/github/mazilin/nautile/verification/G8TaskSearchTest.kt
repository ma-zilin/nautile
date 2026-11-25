package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.rule.TaskSearch
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G8 闸门：待办搜索纯函数（CMP-38、FR-N-14/15、BR-76、用户 2026-11-25 决定）。
 *
 * 预期从需求与用户决定派生，不照抄实现：
 * - 空串/全空白 → 原列表原样返回（不新建、不改顺序）；
 * - 标题或所属项目名包含匹配，大小写不敏感，忽略首尾空白；
 * - 保持入参顺序、返回原任务对象、不修改输入；
 * - 不搜标签（当前 Task 无标签字段）、不搜详情等非标题/项目文本；
 * - 未知项目 ID 不命中项目名。
 */
class G8TaskSearchTest {

    private val now: Instant = Instant.parse("2026-11-25T12:00:00Z")

    private fun project(id: Long, name: String) = Project(
        id = ProjectId(id),
        name = name,
        createdAt = now,
        updatedAt = now,
    )

    private fun task(
        id: Long,
        title: String,
        projectId: ProjectId? = null,
        detail: String? = null,
    ) = Task(
        id = TaskId(id),
        title = title,
        projectId = projectId,
        position = GtdPosition.INBOX,
        createdAt = now,
        updatedAt = now,
        detail = detail,
    )

    @Test
    fun empty_and_blank_query_return_input_list_unchanged() {
        val tasks = listOf(task(1L, "概率论刷题"), task(2L, "买牛奶"))

        assertSame("空串应原样返回入参列表", tasks, TaskSearch.filter(tasks, emptyList(), ""))
        assertSame(
            "全空白应原样返回入参列表",
            tasks,
            TaskSearch.filter(tasks, emptyList(), "   "),
        )
    }

    @Test
    fun matches_title_case_insensitively_and_trims_query() {
        val tasks = listOf(
            task(1L, "Read BOOK"),
            task(2L, "英语单词"),
        )

        val hits = TaskSearch.filter(tasks, emptyList(), "  read book  ")

        assertEquals(listOf(TaskId(1L)), hits.map { it.id })
    }

    @Test
    fun matches_project_name_case_insensitively() {
        val project = project(10L, "Deep Work")
        val tasks = listOf(
            task(1L, "整理资料", projectId = project.id),
            task(2L, "其他任务"),
        )

        val hits = TaskSearch.filter(tasks, listOf(project), "deep")

        assertEquals(listOf(TaskId(1L)), hits.map { it.id })
    }

    @Test
    fun mixed_title_and_project_hits_keep_input_order() {
        val project = project(10L, "Alpha 项目")
        val tasks = listOf(
            task(1L, "无关任务", projectId = project.id),
            task(2L, "alpha 报告"),
            task(3L, "Beta 任务"),
            task(4L, "Alpha 复盘"),
        )

        val hits = TaskSearch.filter(tasks, listOf(project), "ALPHA")

        assertEquals(listOf(TaskId(1L), TaskId(2L), TaskId(4L)), hits.map { it.id })
    }

    @Test
    fun returns_empty_when_nothing_matches() {
        val project = project(10L, "课程")
        val tasks = listOf(task(1L, "概率论刷题", projectId = project.id), task(2L, "买牛奶"))

        assertTrue(TaskSearch.filter(tasks, listOf(project), "不存在的关键词").isEmpty())
    }

    @Test
    fun does_not_search_detail_or_other_non_title_fields() {
        val tasks = listOf(task(1L, "普通任务", detail = "概率论复习笔记"))

        assertTrue(
            "搜索只命中标题或项目名，详情文本不应命中",
            TaskSearch.filter(tasks, emptyList(), "概率论").isEmpty(),
        )
    }

    @Test
    fun unknown_project_id_does_not_match_project_name() {
        val project = project(10L, "课程")
        val tasks = listOf(task(1L, "无关任务", projectId = ProjectId(99L)))

        assertTrue(
            "项目不在给定列表时不得按项目名命中",
            TaskSearch.filter(tasks, listOf(project), "课程").isEmpty(),
        )
    }

    @Test
    fun returns_original_task_objects_without_modifying_input() {
        val project = project(10L, "课程")
        val first = task(1L, "课程笔记")
        val second = task(2L, "无关标题", projectId = project.id)
        val tasks = listOf(first, second, task(3L, "买牛奶"))
        val snapshot = tasks.toList()

        val hits = TaskSearch.filter(tasks, listOf(project), "课程")

        assertEquals(listOf(first, second), hits)
        assertSame("应返回原任务对象，不复制", first, hits[0])
        assertSame("应返回原任务对象，不复制", second, hits[1])
        assertEquals("输入列表内容与顺序不得改变", snapshot, tasks)
        assertEquals("输入列表元素顺序不得改变", listOf(TaskId(1L), TaskId(2L), TaskId(3L)), tasks.map { it.id })
    }
}
