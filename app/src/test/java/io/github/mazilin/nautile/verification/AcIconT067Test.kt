package io.github.mazilin.nautile.verification

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T-067 图标接入的可执行核对（T-072）。
 *
 * 只验证 Manifest/资源/APK 打包，不判定桌面外观。
 */
class AcIconT067Test {

    private fun rootFile(relative: String): File? =
        listOf(File(relative), File("app/$relative"))
            .firstOrNull { it.isFile }

    @Test
    fun T_067_manifest_declares_default_and_round_icon() {
        val manifest = rootFile("src/main/AndroidManifest.xml")
            ?: error("找不到 AndroidManifest.xml")

        val text = manifest.readText()
        assertTrue(text.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue(text.contains("android:roundIcon=\"@mipmap/ic_launcher_round\""))
    }

    @Test
    fun T_067_adaptive_and_raster_resources_exist() {
        listOf(
            "src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
            "src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
            "src/main/res/drawable/ic_launcher_background.png",
            "src/main/res/drawable/ic_launcher_foreground.png",
            "src/main/res/drawable/ic_launcher_monochrome.png",
            "src/main/res/mipmap-mdpi/ic_launcher.png",
            "src/main/res/mipmap-xxxhdpi/ic_launcher.png",
            "src/main/res/mipmap-xxxhdpi/ic_launcher_round.png",
        ).forEach { path ->
            assertTrue("缺少图标资源 $path", rootFile(path) != null)
        }
    }

    @Test
    fun T_067_debug_apk_contains_launcher_resources() {
        val apk = listOf(
            File("build/outputs/apk/debug/app-debug.apk"),
            File("app/build/outputs/apk/debug/app-debug.apk"),
        ).firstOrNull { it.isFile }
            ?: error("找不到 debug APK；应先执行 assembleDebug")

        ZipFile(apk).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            assertTrue(names.any { it.contains("ic_launcher") })
            assertTrue(names.any { it.contains("ic_launcher_round") })
            assertFalse("不应打入其他配色资产", names.any { it.contains("deep-blue") || it.contains("light-blue") })
        }
    }
}
