package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.domain.model.ClosedOutcome
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import io.github.mazilin.nautile.domain.rule.ProjectRules
import io.github.mazilin.nautile.domain.rule.TaskGrouping
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * R7-G1 · 待办项目可见性筛选与跨清单总进度计算验证（#24、#26、架构 §7）。
 *
 * 覆盖：
 * - TaskGrouping.visibleProjectsFor：非 ENDED 仅活跃项目；ENDED 返回已结束项目（含空）∪ 有已结束子任务的活跃项目，不含活跃空项目；
 * - ProjectRules.progress：跨 GTD 全量任务、COMPLETED 计分子、ABANDONED 计分母不计分子、0 子任务 percent == null。
 */
class TaskGroupingAndProgressR7Test {

    private val now: Instant = Instant.parse("2026-11-28T08:00:00Z")

    private fun project(id: Long, name: String, status: ProjectStatus): Project = Project(
        id = ProjectId(id),
        name = name,
        status = status,
        createdAt = now,
        updatedAt = now,
    )

    private fun task(
        id: Long,
        projectId: ProjectId?,
        position: GtdPosition,
        outcome: ClosedOutcome? = null,
    ): Task = Task(
        id = TaskId(id),
        title = "任务$id",
        projectId = projectId,
        position = position,
        closedOutcome = outcome,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun r7_visibleProjectsFor_nonEndedPositionsReturnOnlyActiveProjects() {
        val activeEmpty = project(1L, "活跃空项目", ProjectStatus.ACTIVE)
        val completed = project(2L, "已完成项目", ProjectStatus.COMPLETED)
        val activeBusy = project(3L, "活跃项目", ProjectStatus.ACTIVE)
        val abandoned = project(4L, "已放弃项目", ProjectStatus.ABANDONED)
        val allProjects = listOf(activeEmpty, completed, activeBusy, abandoned)

        listOf(
            GtdPosition.INBOX,
            GtdPosition.NEXT_ACTION,
            GtdPosition.WAITING,
            GtdPosition.SOMEDAY,
        ).forEach { position ->
            val tasksInPos = listOf(
                task(10L, activeBusy.id, position),
                // 即使异常数据让已结项项目挂了任务，也不应把已结束项目暴露到未结束清单。
                task(11L, completed.id, position),
            )
            val visible = TaskGrouping.visibleProjectsFor(position, allProjects, tasksInPos)
            assertEquals(
                "$position 清单只应返回活跃项目且保持原序",
                listOf(activeEmpty.id, activeBusy.id),
                visible.map { it.id },
            )
        }
    }

    @Test
    fun r7_visibleProjectsFor_endedIncludesClosedEmptyAndActiveWithEndedTasks_excludesActiveEmpty() {
        val activeEmpty = project(1L, "活跃空项目", ProjectStatus.ACTIVE)
        val completedEmpty = project(2L, "已完成空项目", ProjectStatus.COMPLETED)
        val activeWithEndedChild = project(3L, "活跃有已结子任务项目", ProjectStatus.ACTIVE)
        val abandonedEmpty = project(4L, "已放弃空项目", ProjectStatus.ABANDONED)
        val activeWithoutEndedChild = project(5L, "活跃仅进行中子任务项目", ProjectStatus.ACTIVE)
        val allProjects = listOf(
            activeEmpty,
            completedEmpty,
            activeWithEndedChild,
            abandonedEmpty,
            activeWithoutEndedChild,
        )

        val endedTasks = listOf(
            task(100L, activeWithEndedChild.id, GtdPosition.ENDED, ClosedOutcome.COMPLETED),
            task(101L, null, GtdPosition.ENDED, ClosedOutcome.ABANDONED),
        )

        val visible = TaskGrouping.visibleProjectsFor(GtdPosition.ENDED, allProjects, endedTasks)

        assertEquals(
            "已结束清单应返回：已结束项目（含空）∪ 有已结束子任务的活跃项目，保持原序且绝不含活跃空项目",
            listOf(completedEmpty.id, activeWithEndedChild.id, abandonedEmpty.id),
            visible.map { it.id },
        )
    }

    @Test
    fun r7_projectProgress_countsAcrossAllGtdPositions_completedInNumerator_abandonedOnlyInDenominator() {
        val p1 = ProjectId(1L)
        val p2 = ProjectId(2L)
        val emptyProject = ProjectId(3L)

        val allTasks = listOf(
            // p1 跨全部 5 个清单：2 COMPLETED + 1 ABANDONED + 3 未结束 = 共 6 个任务，完成率 2*100/6 = 33%
            task(1L, p1, GtdPosition.INBOX, null),
            task(2L, p1, GtdPosition.NEXT_ACTION, null),
            task(3L, p1, GtdPosition.WAITING, null),
            task(4L, p1, GtdPosition.ENDED, ClosedOutcome.COMPLETED),
            task(5L, p1, GtdPosition.ENDED, ClosedOutcome.COMPLETED),
            task(6L, p1, GtdPosition.ENDED, ClosedOutcome.ABANDONED),
            // 干扰：其他项目与无项目任务不应计入 p1
            task(7L, p2, GtdPosition.ENDED, ClosedOutcome.COMPLETED),
            task(8L, null, GtdPosition.ENDED, ClosedOutcome.COMPLETED),
        )

        val p1Progress = ProjectRules.progress(allTasks, p1)
        assertEquals(2, p1Progress.completedCount)
        assertEquals(6, p1Progress.totalCount)
        assertEquals(33, p1Progress.percent)
        assertEquals("参数顺序重载必须一致", p1Progress, ProjectRules.progress(p1, allTasks))

        val p2Progress = ProjectRules.progress(allTasks, p2)
        assertEquals(1, p2Progress.completedCount)
        assertEquals(1, p2Progress.totalCount)
        assertEquals(100, p2Progress.percent)

        // 0 子任务 percent 必须为 null（不得伪造 0%）。
        val emptyProgress = ProjectRules.progress(allTasks, emptyProject)
        assertEquals(0, emptyProgress.completedCount)
        assertEquals(0, emptyProgress.totalCount)
        assertNull("0 子任务项目 percent 必须为 null", emptyProgress.percent)

        // 有子任务但全放弃或全未完成时：percent 应为 0（而非 null）。
        val zeroPercentTasks = listOf(
            task(20L, emptyProject, GtdPosition.SOMEDAY, null),
            task(21L, emptyProject, GtdPosition.ENDED, ClosedOutcome.ABANDONED),
        )
        val zeroPercent = ProjectRules.progress(zeroPercentTasks, emptyProject)
        assertEquals(0, zeroPercent.completedCount)
        assertEquals(2, zeroPercent.totalCount)
        assertEquals(0, zeroPercent.percent)
    }
}
