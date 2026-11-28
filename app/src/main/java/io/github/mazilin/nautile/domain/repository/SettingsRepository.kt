package io.github.mazilin.nautile.domain.repository

import io.github.mazilin.nautile.domain.model.AppPage
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.ScheduleViewMode
import io.github.mazilin.nautile.domain.model.TimerMode
import io.github.mazilin.nautile.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

/**
 * 设置仓库（AC-N-14、AC-N-21；R6-G4 增设计时/视图偏好）。
 *
 * 设置只在 DataStore 持久化，不写 Room；写入均为整体键值更新，由实现串行化。
 * [setTimerMode]、[setScheduleViewMode] 的默认空实现只用于兼容既有测试替身；
 * 生产实现 [io.github.mazilin.nautile.data.datastore.DataStoreSettingsRepository] 必须覆盖。
 */
interface SettingsRepository {

    val settings: Flow<UserSettings>

    suspend fun setDefaultPage(page: AppPage)

    suspend fun setLastPage(page: AppPage)

    suspend fun setLastGtdPosition(position: GtdPosition)

    suspend fun setPredictiveBackEnabled(enabled: Boolean)

    /** 保存单个 GTD 模块的视图选项；内容为不透明文本，S1 只用默认值（D-10）。 */
    suspend fun setViewOptions(position: GtdPosition, options: String)

    /** 保存计时模式；本轮不暴露切换 UI，只有默认并行（架构 §4.2）。 */
    suspend fun setTimerMode(mode: TimerMode) = Unit

    /** 保存日程视图偏好；未知值回落日视图。 */
    suspend fun setScheduleViewMode(mode: ScheduleViewMode) = Unit
}
