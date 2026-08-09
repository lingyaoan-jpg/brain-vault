package app.brain.ui.suggestions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.brain.data.db.dao.CategorySuggestionDao
import app.brain.data.db.dao.SuggestionWithRecord
import app.brain.data.preference.PreferenceService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SuggestionViewModel @Inject constructor(
    private val suggestionDao: CategorySuggestionDao,
    private val preferenceService: PreferenceService,
) : ViewModel() {

    val suggestions: StateFlow<List<SuggestionWithRecord>> =
        suggestionDao.observeByStatus(STATUS_PENDING)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun accept(item: SuggestionWithRecord) {
        viewModelScope.launch {
            preferenceService.acceptSuggestion(item.recordId, item.fromCategoryId, item.toCategoryId, item.suggestionId)
        }
    }

    fun ignore(item: SuggestionWithRecord) {
        viewModelScope.launch {
            preferenceService.ignoreSuggestion(item.suggestionId)
        }
    }

    fun acceptAll() {
        viewModelScope.launch {
            preferenceService.acceptAllSuggestions()
        }
    }

    companion object {
        private const val STATUS_PENDING = "pending"
    }
}