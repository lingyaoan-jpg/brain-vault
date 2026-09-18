package app.brain

import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.brain.data.settings.SettingsRepository
import app.brain.ui.appearance.AppearanceViewModel
import app.brain.ui.nav.BrainNav
import app.brain.ui.theme.BrainTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appearanceViewModel: AppearanceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val systemDark = resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val initialDark = resolveDarkTheme(appearanceViewModel.themeMode.value, systemDark)
        window.setBackgroundDrawable(ColorDrawable(if (initialDark) DARK_WINDOW else LIGHT_WINDOW))
        enableEdgeToEdge()
        val quickRecord = intent?.getBooleanExtra(EXTRA_QUICK_RECORD, false) ?: false
        setContent {
            val themeMode by appearanceViewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = resolveDarkTheme(themeMode, isSystemInDarkTheme())
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            BrainTheme(darkTheme = darkTheme) {
                BrainNav(quickRecord = quickRecord)
            }
        }
    }

    companion object {
        const val EXTRA_QUICK_RECORD = "quick_record"
        private const val LIGHT_WINDOW = 0xFFFFFFFF.toInt()
        private const val DARK_WINDOW = 0xFF141218.toInt()
    }
}

/** 把外观设置解析成「用不用深色」；跟随系统时用手机自己的设置。 */
internal fun resolveDarkTheme(mode: String, systemDark: Boolean): Boolean = when (mode) {
    SettingsRepository.THEME_LIGHT -> false
    SettingsRepository.THEME_DARK -> true
    else -> systemDark
}
