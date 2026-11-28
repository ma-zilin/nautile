package io.github.mazilin.nautile.data.datastore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.mazilin.nautile.domain.model.AppPage
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.ScheduleViewMode
import io.github.mazilin.nautile.domain.model.TimerMode
import io.github.mazilin.nautile.domain.model.UserSettings

/**
 * 设置键与默认值（S1-T05 步骤 2；06-STO-03；R6-G4 新增日志偏好）。
 *
 * 设置只存 DataStore，不进入 Room；键名固定，不随 UI 文案变化。
 * 未知存储值一律回落默认，不抛异常（架构 §4.2）。
 */
object UserSettingsStore {

    val defaultPage = stringPreferencesKey("default_page")
    val lastPage = stringPreferencesKey("last_page")
    val lastGtdPosition = stringPreferencesKey("last_gtd_position")
    val predictiveBackEnabled = booleanPreferencesKey("predictive_back_enabled")

    /** 计时模式：PARALLEL / SINGLE；默认并行，未知值回落并行。 */
    val timerMode = stringPreferencesKey("timer_mode")

    /** 日程视图：DAY / THREE_DAY / WEEK；默认日视图，未知值回落日视图。 */
    val scheduleViewMode = stringPreferencesKey("schedule_view_mode")

    /** `view_options_<module>`：按 GTD 模块保存的视图选项文本。 */
    fun viewOptionsKey(module: GtdPosition) = stringPreferencesKey("view_options_${module.name}")

    private const val VIEW_OPTIONS_PREFIX = "view_options_"

    /** 读取设置；缺省时默认页 TODO、预测返回开启（AC-N-14、AC-N-21）。 */
    fun toSettings(preferences: Preferences): UserSettings = UserSettings(
        defaultPage = preferences[defaultPage].toAppPageOrNull() ?: AppPage.TODO,
        lastPage = preferences[lastPage].toAppPageOrNull(),
        lastGtdPosition = preferences[lastGtdPosition].toGtdPositionOrNull(),
        predictiveBackEnabled = preferences[predictiveBackEnabled] ?: true,
        perModuleViewOptions = readViewOptions(preferences),
        timerMode = preferences[timerMode].toTimerMode(),
        scheduleViewMode = preferences[scheduleViewMode].toScheduleViewMode(),
    )

    private fun readViewOptions(preferences: Preferences): Map<GtdPosition, String> =
        preferences.asMap().entries.mapNotNull { (key, value) ->
            val name = key.name
            val options = value as? String ?: return@mapNotNull null
            if (!name.startsWith(VIEW_OPTIONS_PREFIX)) return@mapNotNull null
            val module = name.removePrefix(VIEW_OPTIONS_PREFIX).toGtdPositionOrNull() ?: return@mapNotNull null
            module to options
        }.toMap()

    private fun String?.toAppPageOrNull(): AppPage? =
        this?.let { stored -> AppPage.entries.firstOrNull { it.name == stored } }

    private fun String?.toGtdPositionOrNull(): GtdPosition? =
        this?.let { stored -> GtdPosition.entries.firstOrNull { it.name == stored } }

    private fun String?.toTimerMode(): TimerMode =
        this?.let { stored -> TimerMode.entries.firstOrNull { it.name == stored } }
            ?: TimerMode.PARALLEL

    private fun String?.toScheduleViewMode(): ScheduleViewMode =
        this?.let { stored -> ScheduleViewMode.entries.firstOrNull { it.name == stored } }
            ?: ScheduleViewMode.DAY
}
