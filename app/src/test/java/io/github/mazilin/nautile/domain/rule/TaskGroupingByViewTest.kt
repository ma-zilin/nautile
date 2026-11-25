package io.github.mazilin.nautile.domain.rule

import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Priority
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.model.ViewClassification
import io.github.mazilin.nautile.domain.model.ViewOptions
import io.github.mazilin.nautile.domain.model.ViewSort
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G9 视图分组（CMP-39、FR-V-01/03、AC-T-08）。
 *
 * 预期依据 `docs/需求基线.md` v2.2 与用户 2026-11-25 的 G9 口径：
 * - 先按排序得到组内顺序，再按分类分组；
 * - 项目分类保留空项目组（非搜索态），未归属任务进末尾“单独任务”；
 * - 优先级组高→中→低→无，只输出非空组；
 * - 截止日期组按日期由近到远，无日期组置末；
 * - 搜索态调用方传 includeEmptyProjects=false，不输出 0 结果组。
 */
class TaskGroupingByViewTest {

    private val now: Instant = Instant.parse("2026-11-25T00:00:00Z")

    private fun project(id: Long, name: String) = Project(
        id = ProjectId(id),
        name = name,
        createdAt = now,
        updatedAt = now,
    )

    private fun task(
        id: Long,
        projectId: ProjectId? = null,
        priority: Priority = Priority.NONE,
        dueDate: LocalDate? = null,
        manualOrder: Long? = null,
    ) = Task(
        id = TaskId(id),
        title = "任务 $id",
        projectId = projectId,
        position = GtdPosition.NEXT_ACTION,
        priority = priority,
        dueDate = dueDate,
        createdAt = now.plusSeconds(id),
        updatedAt = now,
        manualOrder = manualOrder,
    )

    private fun ids(tasks: List<Task>): List<Long> = tasks.map { it.id.value }

    @Test
    fun project_classification_keeps_empty_projects_and_standalone_group() {
        val course = project(1L, "课程")
        val empty = project(2L, "空项目")
        val tasks = listOf(
            task(11L, projectId = course.id, manualOrder = 1L),
            task(12L, projectId = course.id, manualOrder = 0L),
            task(13L, projectId = null, manualOrder = 2L),
            task(14L, projectId = ProjectId(99L), manualOrder = 3L),
        )

        val groups = TaskGrouping.byView(
            tasks = tasks,
            projects = listOf(course, empty),
            options = ViewOptions(ViewClassification.PROJECT, ViewSort.MANUAL),
            includeEmptyProjects = true,
        )

        assertEquals("课程/空项目/单独任务", 3, groups.size)
        assertEquals("课程", groups[0].headerTitle)
        assertEquals("项目组内按 manualOrder 升序", listOf(12L, 11L), ids(groups[0].tasks))
        assertEquals("空项目", groups[1].headerTitle)
        assertTrue("空项目组应保留", groups[1].tasks.isEmpty())
        assertTrue("末尾为单独任务组", groups[2].isUngrouped)
        assertEquals("未知项目归入单独任务", listOf(13L, 14L), ids(groups[2].tasks))
        assertEquals("project_1", groups[0].key)
    }

    @Test
    fun project_classification_with_search_hides_empty_projects() {
        val course = project(1L, "课程")
        val empty = project(2L, "空项目")
        val tasks = listOf(task(11L, projectId = course.id))

        val groups = TaskGrouping.byView(
            tasks = tasks,
            projects = listOf(course, empty),
            options = ViewOptions.Default,
            includeEmptyProjects = false,
        )

        assertEquals(1, groups.size)
        assertEquals("课程", groups[0].headerTitle)
        assertFalse(groups.any { it.tasks.isEmpty() })
    }

    @Test
    fun priority_classification_outputs_non_empty_groups_in_fixed_order() {
        val tasks = listOf(
            task(1L, priority = Priority.NONE),
            task(2L, priority = Priority.LOW),
            task(3L, priority = Priority.HIGH),
            task(4L, priority = Priority.MEDIUM),
            task(5L, priority = Priority.HIGH),
        )

        val groups = TaskGrouping.byView(
            tasks = tasks,
            projects = emptyList(),
            options = ViewOptions(ViewClassification.PRIORITY, ViewSort.CREATED),
            includeEmptyProjects = true,
        )

        assertEquals(
            listOf("高优先级", "中优先级", "低优先级", "无优先级"),
            groups.map { it.headerTitle },
        )
        assertEquals("高优先级组应含两条", setOf(3L, 5L), ids(groups[0].tasks).toSet())
        assertTrue(groups[0].tasks.all { it.priority == Priority.HIGH })
        assertEquals(listOf(4L), ids(groups[1].tasks))
        assertEquals(listOf(2L), ids(groups[2].tasks))
        assertEquals(listOf(1L), ids(groups[3].tasks))
        assertEquals("priority_HIGH", groups[0].key)
    }

    @Test
    fun priority_classification_omits_empty_levels() {
        val groups = TaskGrouping.byView(
            tasks = listOf(task(1L, priority = Priority.MEDIUM)),
            projects = emptyList(),
            options = ViewOptions(ViewClassification.PRIORITY, ViewSort.MANUAL),
            includeEmptyProjects = true,
        )

        assertEquals(listOf("中优先级"), groups.map { it.headerTitle })
    }

    @Test
    fun due_classification_sorts_dates_and_puts_undated_last() {
        val tasks = listOf(
            task(1L, dueDate = null),
            task(2L, dueDate = LocalDate.parse("2026-12-02")),
            task(3L, dueDate = LocalDate.parse("2026-12-01")),
            task(4L, dueDate = LocalDate.parse("2026-12-02")),
        )

        val groups = TaskGrouping.byView(
            tasks = tasks,
            projects = emptyList(),
            options = ViewOptions(ViewClassification.DUE, ViewSort.CREATED),
            includeEmptyProjects = true,
        )

        assertEquals(
            listOf("12月1日", "12月2日", "无截止日期"),
            groups.map { it.headerTitle },
        )
        assertEquals(listOf(3L), ids(groups[0].tasks))
        assertEquals("同日期保持输入排序", listOf(4L, 2L), ids(groups[1].tasks))
        assertEquals(listOf(1L), ids(groups[2].tasks))
        assertEquals("due_none", groups[2].key)
    }

    @Test
    fun due_classification_without_undated_has_no_undated_group() {
        val groups = TaskGrouping.byView(
            tasks = listOf(task(1L, dueDate = LocalDate.parse("2026-12-01"))),
            projects = emptyList(),
            options = ViewOptions(ViewClassification.DUE, ViewSort.MANUAL),
            includeEmptyProjects = true,
        )

        assertEquals(1, groups.size)
        assertEquals("12月1日", groups[0].headerTitle)
    }

    @Test
    fun sort_is_applied_before_grouping() {
        val day = LocalDate.parse("2026-12-01")
        val tasks = listOf(
            task(1L, dueDate = day, priority = Priority.LOW),
            task(2L, dueDate = day, priority = Priority.HIGH),
            task(3L, dueDate = day, priority = Priority.MEDIUM),
        )

        val groups = TaskGrouping.byView(
            tasks = tasks,
            projects = emptyList(),
            // 分类=截止日期、排序=优先级：同一日期组内按优先级。
            options = ViewOptions(ViewClassification.DUE, ViewSort.PRIORITY),
            includeEmptyProjects = true,
        )

        assertEquals(1, groups.size)
        assertEquals(listOf(2L, 3L, 1L), ids(groups[0].tasks))
    }
}
