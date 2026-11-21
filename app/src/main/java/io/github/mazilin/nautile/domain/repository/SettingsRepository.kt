package io.github.mazilin.nautile.domain.repository

import io.github.mazilin.nautile.domain.model.AppPage
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

/**
 * 设置仓库（AC-N-14、AC-N-21）。
 *
 * 设置只在 DataStore 持久化，不写 Room；写入均为整体键值更新，由实现串行化。
 */
interface SettingsRepository {

    val settings: Flow<UserSettings>

    suspend fun setDefaultPage(page: AppPage)

    suspend fun setLastPage(page: AppPage)

    suspend fun setLastGtdPosition(position: GtdPosition)

    suspend fun setPredictiveBackEnabled(enabled: Boolean)

    /** 保存单个 GTD 模块的视图选项；内容为不透明文本，S1 只用默认值（D-10）。 */
    suspend fun setViewOptions(position: GtdPosition, options: String)
}
