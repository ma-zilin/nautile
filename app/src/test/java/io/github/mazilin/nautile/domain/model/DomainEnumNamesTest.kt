package io.github.mazilin.nautile.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T-02：枚举以文本名持久化，名称即存储值；改名等同迁移，须另立决定。
 */
class DomainEnumNamesTest {

    @Test
    fun gtd_position_names_are_stable() {
        assertEquals(
            listOf("INBOX", "NEXT_ACTION", "WAITING", "SOMEDAY", "ENDED"),
            GtdPosition.values().map { it.name },
        )
        assertEquals(GtdPosition.ENDED, GtdPosition.valueOf("ENDED"))
    }

    @Test
    fun priority_names_are_stable() {
        assertEquals(
            listOf("HIGH", "MEDIUM", "LOW", "NONE"),
            Priority.values().map { it.name },
        )
        assertEquals(Priority.NONE, Priority.valueOf("NONE"))
    }

    @Test
    fun closed_outcome_names_are_stable() {
        assertEquals(
            listOf("COMPLETED", "ABANDONED"),
            ClosedOutcome.values().map { it.name },
        )
        assertEquals(ClosedOutcome.COMPLETED, ClosedOutcome.valueOf("COMPLETED"))
    }

    @Test
    fun project_status_names_are_stable() {
        assertEquals(
            listOf("ACTIVE", "COMPLETED", "ABANDONED"),
            ProjectStatus.values().map { it.name },
        )
        assertEquals(ProjectStatus.ACTIVE, ProjectStatus.valueOf("ACTIVE"))
    }

    @Test
    fun app_page_names_are_stable() {
        assertEquals(
            listOf("TODO", "SCHEDULE", "LOG", "AI"),
            AppPage.values().map { it.name },
        )
        assertEquals(AppPage.TODO, AppPage.valueOf("TODO"))
    }
}
