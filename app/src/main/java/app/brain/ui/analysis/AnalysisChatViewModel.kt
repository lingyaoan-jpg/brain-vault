package app.brain.ui.analysis

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.brain.data.analysis.AnalysisScope
import app.brain.data.analysis.DeepAnalysisService
import app.brain.data.db.dao.AnalysisMessageDao
import app.brain.data.db.dao.AnalysisSessionDao
import app.brain.data.db.dao.CategoryDao
import app.brain.data.db.dao.RecordCategoryDao
import app.brain.data.db.dao.RecordDao
import app.brain.data.db.entity.AnalysisMessageEntity
import app.brain.data.db.entity.AnalysisSessionEntity
import app.brain.data.db.entity.CategoryEntity
import app.brain.ui.common.RecordListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnalysisChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionDao: AnalysisSessionDao,
    private val messageDao: AnalysisMessageDao,
    private val categoryDao: CategoryDao,
    private val recordDao: RecordDao,
    private val recordCategoryDao: RecordCategoryDao,
    private val service: DeepAnalysisService,
) : ViewModel() {

    private val sessionId: String = savedStateHandle.get<String>(ARG_SESSION_ID).orEmpty()

    val session: StateFlow<AnalysisSessionEntity?> =
        sessionDao.observeById(sessionId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val messages: StateFlow<List<AnalysisMessageEntity>> =
        messageDao.observeBySession(sessionId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.observeAllActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val records: StateFlow<List<RecordListItem>> =
        combine(recordDao.observeActiveOrdered(), recordCategoryDao.observeAllWithCategories()) { recs, links ->
            val byRecord = links.groupBy { it.recordId }
            recs.map { RecordListItem(it, byRecord[it.id].orEmpty()) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun send(text: String) {
        if (_sending.value) return
        val clean = text.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            _sending.value = true
            _error.value = null
            try {
                service.sendMessage(sessionId, clean)
            } catch (e: Exception) {
                _error.value = e.message ?: "发送失败"
            } finally {
                _sending.value = false
            }
        }
    }

    fun updateScope(scope: AnalysisScope) {
        viewModelScope.launch {
            _error.value = null
            try {
                service.updateScope(sessionId, scope)
            } catch (e: Exception) {
                _error.value = e.message ?: "更新范围失败"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    companion object {
        const val ARG_SESSION_ID = "sessionId"
    }
}