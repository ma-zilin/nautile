package io.github.mazilin.nautile.verification

import io.github.mazilin.nautile.domain.model.AppPage
import io.github.mazilin.nautile.domain.model.GtdPosition
import io.github.mazilin.nautile.domain.model.UserSettings
import io.github.mazilin.nautile.domain.repository.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * G9 测试用设置仓库替身：真实保存 perModuleViewOptions，可注入写入失败。
 *
 * 只替代 DataStore 持久化层；ViewOptions 编解码与 ViewModel 回退逻辑仍走生产实现。
 * DataStore 真持久化由 G9ViewOptionsPersistenceTest 单独覆盖。
 */
internal class G9MemorySettingsRepository(
    initial: UserSettings = UserSettings(lastGtdPosition = GtdPosition.INBOX),
) : SettingsRepository {

    private val state = MutableStateFlow(initial)

    /** 每次 setViewOptions 的调用记录（含失败前已尝试的值）。 */
    val viewOptionsWrites = mutableListOf<Pair<GtdPosition, String>>()

    var failViewOptionsWrites: Boolean = false

    override val settings: Flow<UserSettings> = state

    override suspend fun setDefaultPage(page: AppPage) = Unit

    override suspend fun setLastPage(page: AppPage) = Unit

    override suspend fun setLastGtdPosition(position: GtdPosition) {
        state.value = state.value.copy(lastGtdPosition = position)
    }

    override suspend fun setPredictiveBackEnabled(enabled: Boolean) = Unit

    override suspend fun setViewOptions(position: GtdPosition, options: String) {
        viewOptionsWrites += position to options
        if (failViewOptionsWrites) throw IOException("模拟视图选项写入失败")
        state.value = state.value.copy(
            perModuleViewOptions = state.value.perModuleViewOptions + (position to options),
        )
    }
}
