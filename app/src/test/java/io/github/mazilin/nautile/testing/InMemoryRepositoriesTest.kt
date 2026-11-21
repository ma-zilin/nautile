package io.github.mazilin.nautile.testing

import io.github.mazilin.nautile.domain.model.AppPage
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.Project
import io.github.mazilin.nautile.domain.model.ProjectId
import io.github.mazilin.nautile.domain.model.ProjectStatus
import io.github.mazilin.nautile.domain.model.Task
import io.github.mazilin.nautile.domain.model.TaskId
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** T-02：替身的最小行为，保证 T03/T06 引用前可用。 */
class InMemoryRepositoriesTest {

    private val now: Instant = Instant.parse("2026-11-17T00:00:00Z")

    private fun newTask(title: String, position: GtdPosition = GtdPosition.INBOX) = Task(
        id = TaskId(0L),
        title = title,
        position = position,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun task_insert_assigns_id_and_filters_by_position() = runTest {
        val repository = InMemoryTaskRepository()

        val inboxId = repository.insert(newTask("写周报"))
        repository.insert(newTask("等回复", position = GtdPosition.WAITING))

        assertTrue(inboxId.value > 0L)
        assertEquals(
            listOf("写周报"),
            repository.observeByPosition(GtdPosition.INBOX).first().map { it.title },
        )
        assertEquals(2, repository.observeAll().first().size)
    }

    @Test
    fun task_update_and_delete_all_are_visible_through_flow() = runTest {
        val repository = InMemoryTaskRepository()
        val firstId = repository.insert(newTask("写周报"))
        val secondId = repository.insert(newTask("写月报"))
        val stored = requireNotNull(repository.get(firstId))

        repository.update(stored.copy(title = "写季报"))
        assertEquals("写季报", repository.get(firstId)?.title)

        repository.deleteAll(listOf(firstId, secondId))
        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun project_insert_update_and_delete() = runTest {
        val repository = InMemoryProjectRepository()
        val id = repository.insert(
            Project(
                id = ProjectId(0L),
                name = "课程项目",
                createdAt = now,
                updatedAt = now,
            ),
        )

        assertTrue(id.value > 0L)
        assertEquals(ProjectStatus.ACTIVE, repository.get(id)?.status)

        repository.update(requireNotNull(repository.get(id)).copy(status = ProjectStatus.COMPLETED))
        assertEquals(ProjectStatus.COMPLETED, repository.get(id)?.status)

        repository.delete(id)
        assertNull(repository.get(id))
        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun settings_setters_update_flow() = runTest {
        val repository = FakeSettingsRepository()

        repository.setLastPage(AppPage.TODO)
        repository.setLastGtdPosition(GtdPosition.NEXT_ACTION)
        repository.setPredictiveBackEnabled(false)
        repository.setViewOptions(GtdPosition.INBOX, "default")

        val settings = repository.settings.first()
        assertEquals(AppPage.TODO, settings.defaultPage)
        assertEquals(AppPage.TODO, settings.lastPage)
        assertEquals(GtdPosition.NEXT_ACTION, settings.lastGtdPosition)
        assertTrue(!settings.predictiveBackEnabled)
        assertEquals("default", settings.perModuleViewOptions[GtdPosition.INBOX])
    }
}
