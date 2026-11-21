package io.github.mazilin.nautile.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mazilin.nautile.data.db.dao.ProjectDao
import io.github.mazilin.nautile.data.db.entity.ProjectEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** S1-T04 ProjectDao 测试（AC-T-17 支撑）；真机未执行时按报告标注“未执行”。 */
@RunWith(AndroidJUnit4::class)
class ProjectDaoTest {

    private lateinit var database: NautileDatabase
    private lateinit var projects: ProjectDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            NautileDatabase::class.java,
        ).allowMainThreadQueries().build()
        projects = database.projectDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun project(name: String) = ProjectEntity(
        name = name,
        status = "ACTIVE",
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun AC_T_17_insert_and_observe_projects() = runBlocking {
        projects.insert(project("课程项目"))
        projects.insert(project("生活项目"))

        assertEquals(
            listOf("课程项目", "生活项目"),
            projects.observeAll().first().map { it.name },
        )
    }

    @Test
    fun AC_T_17_rename_and_change_status() = runBlocking {
        val id = projects.insert(project("课程项目"))
        val stored = requireNotNull(projects.get(id))

        projects.update(stored.copy(name = "课程项目二", status = "COMPLETED", updatedAt = 1_000L))

        val updated = requireNotNull(projects.get(id))
        assertEquals("课程项目二", updated.name)
        assertEquals("COMPLETED", updated.status)
    }

    @Test
    fun AC_T_17_delete_project() = runBlocking {
        val id = projects.insert(project("课程项目"))

        projects.delete(id)

        assertNull(projects.get(id))
    }
}
