package io.github.mazilin.nautile.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 架构边界静态扫描（T-051；07 §2.2、§5）。
 *
 * 只扫描 `src/main/java` 的 import 行，不执行被扫描代码。
 * 规则来自 07 §2.2：domain 不依赖 Android/外层；data 不依赖 ui/interaction/platform；
 * ui 不直接依赖数据实现；platform 不依赖 ui/interaction/data。
 */
class PackageBoundaryTest {

    private val mainRoot: File = listOf(
        File("src/main/java"),
        File("app/src/main/java"),
    ).firstOrNull { it.isDirectory }
        ?: error("找不到 src/main/java；当前工作目录=${File(".").absolutePath}")

    @Test
    fun AC_G_01_domain_has_no_android_or_androidx_imports() {
        assertNoForbiddenImports(
            relativeDir = "io/github/mazilin/nautile/domain",
            forbidden = listOf(
                "import android.",
                "import androidx.",
            ),
        )
    }

    @Test
    fun AC_G_01_domain_does_not_depend_on_outer_layers() {
        assertNoForbiddenImports(
            relativeDir = "io/github/mazilin/nautile/domain",
            forbidden = listOf(
                "import io.github.mazilin.nautile.data.",
                "import io.github.mazilin.nautile.ui.",
                "import io.github.mazilin.nautile.interaction.",
                "import io.github.mazilin.nautile.platform.",
            ),
        )
    }

    @Test
    fun AC_G_01_data_does_not_depend_on_ui_interaction_or_platform() {
        assertNoForbiddenImports(
            relativeDir = "io/github/mazilin/nautile/data",
            forbidden = listOf(
                "import io.github.mazilin.nautile.ui.",
                "import io.github.mazilin.nautile.interaction.",
                "import io.github.mazilin.nautile.platform.",
            ),
        )
    }

    @Test
    fun AC_G_01_ui_does_not_depend_on_data_implementations() {
        assertNoForbiddenImports(
            relativeDir = "io/github/mazilin/nautile/ui",
            forbidden = listOf(
                "import io.github.mazilin.nautile.data.",
                "import io.github.mazilin.nautile.platform.",
                "import androidx.room.",
                "import androidx.datastore.",
            ),
        )
    }

    @Test
    fun AC_G_01_platform_does_not_depend_on_ui_interaction_or_data() {
        assertNoForbiddenImports(
            relativeDir = "io/github/mazilin/nautile/platform",
            forbidden = listOf(
                "import io.github.mazilin.nautile.ui.",
                "import io.github.mazilin.nautile.interaction.",
                "import io.github.mazilin.nautile.data.",
            ),
        )
    }

    private fun assertNoForbiddenImports(relativeDir: String, forbidden: List<String>) {
        val dir = File(mainRoot, relativeDir)
        assertTrue("目录不存在：${dir.absolutePath}", dir.isDirectory)

        val violations = dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().withIndex().mapNotNull { (index, line) ->
                    val trimmed = line.trimStart()
                    if (!trimmed.startsWith("import ")) return@mapNotNull null
                    if (forbidden.any { trimmed.contains(it) }) {
                        "${file.relativeTo(mainRoot).path}:${index + 1}: $trimmed"
                    } else {
                        null
                    }
                }
            }
            .toList()

        assertTrue(
            buildString {
                append("发现包边界违规（")
                append(relativeDir)
                append("）：\n")
                append(violations.joinToString("\n"))
            },
            violations.isEmpty(),
        )
    }
}
