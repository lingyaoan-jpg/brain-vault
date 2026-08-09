package app.brain.ui.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.brain.data.analysis.AnalysisScope
import app.brain.data.analysis.DeepAnalysisService
import app.brain.data.db.dao.AnalysisSessionDao
import app.brain.data.db.dao.CategoryDao
import app.brain.data.db.dao.RecordCategoryDao
import app.brain.data.db.dao.RecordDao
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
class AnalysisViewModel @Inject constructor(
    private val sessionDao: AnalysisSessionDao,
    private val categoryDao: CategoryDao,
    private val recordDao: RecordDao,
    private val recordCategoryDao: RecordCategoryDao,
    private val service: DeepAnalysisService,
) : ViewModel() {

    val sessions: StateFlow<List<app.brain.data.db.entity.AnalysisSessionEntity>> =
        sessionDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.observeAllActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val records: StateFlow<List<RecordListItem>> =
        combine(recordDao.observeActiveOrdered(), recordCategoryDao.observeAllWithCategories()) { recs, links ->
            val byRecord = links.groupBy { it.recordId }
            recs.map { RecordListItem(it, byRecord[it.id].orEmpty()) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun createSession(title: String, scope: AnalysisScope, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                val session = service.createSession(title, scope)
                onCreated(session.id)
            } catch (e: Exception) {
                _error.value = e.message ?: "创建失败"
            } finally {
                _busy.value = false
            }
        }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            try {
                service.deleteSession(id)
            } catch (e: Exception) {
                _error.value = e.message ?: "删除失败"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}