package io.github.mazilin.nautile.domain.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** T-02：模型默认值（S1-T02 步骤 4）。 */
class DomainModelDefaultsTest {

    private val now: Instant = Instant.parse("2026-11-17T00:00:00Z")

    @Test
    fun task_defaults_use_none_priority_and_null_optional_fields() {
        val task = Task(
            id = TaskId(1L),
            title = "写周报",
            position = GtdPosition.INBOX,
            createdAt = now,
            updatedAt = now,
        )

        assertEquals(Priority.NONE, task.priority)
        assertNull(task.projectId)
        assertNull(task.positionBeforeClose)
        assertNull(task.dueDate)
        assertNull(task.dueTime)
        assertNull(task.closedOutcome)
        assertNull(task.closedAt)
        assertNull(task.closedOn)
    }

    @Test
    fun project_defaults_to_active_status() {
        val project = Project(
            id = ProjectId(1L),
            name = "课程项目",
            createdAt = now,
            updatedAt = now,
        )

        assertEquals(ProjectStatus.ACTIVE, project.status)
    }

    @Test
    fun user_settings_default_to_todo_and_predictive_back_enabled() {
        val settings = UserSettings()

        assertEquals(AppPage.TODO, settings.defaultPage)
        assertNull(settings.lastPage)
        assertNull(settings.lastGtdPosition)
        assertTrue(settings.predictiveBackEnabled)
        assertTrue(settings.perModuleViewOptions.isEmpty())
    }
}
