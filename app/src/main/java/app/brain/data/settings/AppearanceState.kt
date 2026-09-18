package app.brain.data.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 外观模式的进程内唯一来源。Activity 和「外观」页面各自持有自己的 ViewModel，
 * 只有共用同一份状态，切换后界面才会立刻跟着变。
 */
@Singleton
class AppearanceState @Inject constructor(
    private val settings: SettingsRepository,
) {
    private val _themeMode = MutableStateFlow(settings.themeMode)
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun set(mode: String) {
        settings.themeMode = mode
        _themeMode.value = settings.themeMode
    }
}
