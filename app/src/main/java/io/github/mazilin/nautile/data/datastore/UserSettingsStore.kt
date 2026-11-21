package io.github.mazilin.nautile.data.datastore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.mazilin.nautile.domain.model.AppPage
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.UserSettings

/**
 * 设置键与默认值（S1-T05 步骤 2；06-STO-03）。
 *
 * 设置只存 DataStore，不进入 Room；键名固定，不随 UI 文案变化。
 */
object UserSettingsStore {

    val defaultPage = stringPreferencesKey("default_page")
    val lastPage = stringPreferencesKey("last_page")
    val lastGtdPosition = stringPreferencesKey("last_gtd_position")
    val predictiveBackEnabled = booleanPreferencesKey("predictive_back_enabled")

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
}
