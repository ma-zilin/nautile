// AGP 9 默认启用内置 Kotlin（不再应用 org.jetbrains.kotlin.android）。
// 按官方 AGP 9 说明，用 buildscript classpath 把 KGP 升级到与 libs.versions.toml 的 kotlin 一致。
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
