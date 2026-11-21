package io.github.mazilin.nautile.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S1-T04 迁移测试骨架（06-STO-02）。
 *
 * S1 只有 version 1：先验证导出的 schema 可打开；后续版本再补迁移路径。
 * 真机无人到场时本测试不执行，报告中标注“未执行”。
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NautileDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun schema_v1_opens_without_migration() {
        helper.createDatabase(TEST_DATABASE, 1).close()
    }

    private companion object {
        const val TEST_DATABASE = "migration-test.db"
    }
}
