package app.brain.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.brain.data.db.dao.CategorySuggestionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** 主界面页签容器的轻量 ViewModel：目前只提供侧边抽屉里的「整理建议」数量角标。 */
@HiltViewModel
class MainTabsViewModel @Inject constructor(
    suggestionDao: CategorySuggestionDao,
) : ViewModel() {
    val suggestionCount: StateFlow<Int> = suggestionDao.observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
