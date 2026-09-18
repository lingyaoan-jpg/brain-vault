package app.brain.ui.appearance

import androidx.lifecycle.ViewModel
import app.brain.data.settings.AppearanceState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val appearance: AppearanceState,
) : ViewModel() {

    val themeMode: StateFlow<String> = appearance.themeMode

    fun select(mode: String) {
        appearance.set(mode)
    }
}
