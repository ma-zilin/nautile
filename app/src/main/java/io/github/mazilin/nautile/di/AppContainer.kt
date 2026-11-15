package io.github.mazilin.nautile.di

import android.content.Context

/**
 * 手工装配容器：后续切片在此创建 Room/DataStore/平台实现。
 * 增量 1 不装配任何具体实现，避免提前实现业务。
 */
class AppContainer(private val context: Context)
