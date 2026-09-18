package app.brain.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.brain.data.ai.AiQueueProcessor
import app.brain.data.db.dao.AnalysisSessionDao
import app.brain.data.db.dao.CategoryDao
import app.brain.data.db.dao.RecordCategoryDao
import app.brain.data.db.dao.RecordCategoryWithCategory
import app.brain.data.db.dao.RecordDao
import app.brain.data.db.entity.CategoryEntity
import app.brain.data.db.entity.RecordEntity
import app.brain.data.export.ExportFormat
import app.brain.data.export.ExportedFile
import app.brain.data.export.RecordExporter
import app.brain.data.db.dao.AiJobDao
import app.brain.data.db.dao.CommentDao
import app.brain.data.db.entity.AiJobEntity
import app.brain.data.db.entity.AnalysisSessionEntity
import app.brain.data.db.entity.CommentEntity
import app.brain.data.preference.PreferenceService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RecordDetailViewModel @Inject constructor(
    private val recordDao: RecordDao,
    private val recordCategoryDao: RecordCategoryDao,
    private val commentDao: CommentDao,
    private val aiJobDao: AiJobDao,
    private val aiQueueProcessor: AiQueueProcessor,
    private val categoryDao: CategoryDao,
    private val preferenceService: PreferenceService,
    private val exporter: RecordExporter,
    private val analysisSessionDao: AnalysisSessionDao,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val recordId: String = checkNotNull(savedStateHandle["recordId"])

    val record: StateFlow<RecordEntity?> = recordDao.observeById(recordId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val categories: StateFlow<List<RecordCategoryWithCategory>> =
        recordCategoryDao.observeByRecord(recordId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val comments: StateFlow<List<CommentEntity>> = commentDao.observeByRecord(recordId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allCategories: StateFlow<List<CategoryEntity>> = categoryDao.observeAllActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 该记录被授权进入的深度分析会话。 */
    private val _relatedSessions = MutableStateFlow<List<AnalysisSessionEntity>>(emptyList())
    val relatedSessions: StateFlow<List<AnalysisSessionEntity>> = _relatedSessions.asStateFlow()

    private val _commentInput = MutableStateFlow("")
    val commentInput: StateFlow<String> = _commentInput.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val _exportedFile = MutableStateFlow<ExportedFile?>(null)
    val exportedFile: StateFlow<ExportedFile?> = _exportedFile.asStateFlow()

    /** 编辑分类对话框内各维度选中的分类 id。 */
    private val _editSelection = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val editSelection: StateFlow<Map<String, Set<String>>> = _editSelection.asStateFlow()

    init {
        viewModelScope.launch {
            _relatedSessions.value = analysisSessionDao.findByScopeContaining(recordId)
        }
    }

    fun onCommentChange(text: String) {
        _commentInput.value = text
    }

    fun addComment() {
        val text = _commentInput.value
        if (text.isBlank()) return
        viewModelScope.launch {
            commentDao.insert(
                CommentEntity(
                    id = UUID.randomUUID().toString(),
                    recordId = recordId,
                    content = text,
                    createdAt = System.currentTimeMillis(),
                )
            )
            _commentInput.value = ""
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val record = recordDao.getById(recordId) ?: return@launch
            recordDao.setFavorite(recordId, !record.isFavorite)
        }
    }

    fun togglePinned() {
        viewModelScope.launch {
            val record = recordDao.getById(recordId) ?: return@launch
            recordDao.setPinned(recordId, !record.isPinned)
        }
    }

    fun retryOrganize() {
        viewModelScope.launch {
            val record = recordDao.getById(recordId) ?: return@launch
            val now = System.currentTimeMillis()
            aiJobDao.deleteForRecord(recordId)
            aiJobDao.insert(
                AiJobEntity(
                    id = UUID.randomUUID().toString(),
                    recordId = recordId,
                    jobType = AiJobEntity.JOB_TYPE_ORGANIZE,
                    status = AiJobEntity.STATUS_QUEUED,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            recordDao.update(
                record.copy(
                    status = RecordEntity.STATUS_PENDING,
                    aiAttempts = 0,
                    aiError = null,
                    updatedAt = now,
                )
            )
            aiQueueProcessor.kick()
        }
    }

    fun export(format: ExportFormat, includeMeta: Boolean) {
        val record = record.value ?: return
        val cats = categories.value
        val commentsList = comments.value
        viewModelScope.launch {
            _exportedFile.value = exporter.export(record, cats, commentsList, format, includeMeta)
        }
    }

    fun clearExport() {
        _exportedFile.value = null
    }

    fun delete() {
        viewModelScope.launch {
            recordDao.trash(recordId, System.currentTimeMillis())
            _deleted.value = true
        }
    }

    // ---------- 编辑分类（V1-B AI 个性化学习） ----------

    fun startEditCategories() {
        val current = categories.value
        // 内容类型是「卡片」，换卡片有单独入口，不混进标签编辑里
        _editSelection.value = current
            .filter { it.dimension != CategoryEntity.DIM_TYPE }
            .groupBy { it.dimension }
            .mapValues { (_, list) -> list.map { it.categoryId }.toSet() }
    }

    fun toggleEditCategory(dimension: String, categoryId: String) {
        val current = _editSelection.value
        val selected = current[dimension].orEmpty().toMutableSet()
        if (!selected.add(categoryId)) selected.remove(categoryId)
        _editSelection.value = current + (dimension to selected)
    }

    fun addEditCategory(dimension: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val existing = categoryDao.getByName(dimension, trimmed)
            val category = existing ?: CategoryEntity(
                id = "user_${dimension}_${UUID.randomUUID().toString().take(8)}",
                dimension = dimension,
                name = trimmed.take(20),
                createdBy = CategoryEntity.CREATED_BY_USER,
            ).also { categoryDao.insert(it) }
            val current = _editSelection.value
            val selected = (current[dimension].orEmpty() + category.id)
            _editSelection.value = current + (dimension to selected)
        }
    }

    fun saveEditCategories() {
        viewModelScope.launch {
            val selection = _editSelection.value
            for ((dimension, ids) in selection) {
                preferenceService.applyCategoryChanges(recordId, dimension, ids.toList())
            }
            _editSelection.value = emptyMap()
        }
    }

    fun cancelEditCategories() {
        _editSelection.value = emptyMap()
    }

    // ---------- 换卡片（内容类型） ----------

    /** 把这条记录挪到另一张内容类型卡片上，同时算一次人工纠正喂给 AI。 */
    fun moveToCard(categoryId: String) {
        viewModelScope.launch {
            preferenceService.applyCategoryChanges(
                recordId,
                CategoryEntity.DIM_TYPE,
                listOf(categoryId),
            )
        }
    }

    /** 新建一张卡片并把这条记录挪过去。名字规则跟收容所那边保持一致。 */
    fun createCardAndMove(rawName: String) {
        val name = rawName.trim().take(12)
        if (name.isBlank()) return
        viewModelScope.launch {
            val existing = categoryDao.getByName(CategoryEntity.DIM_TYPE, name)
            val cardId = if (existing != null) {
                existing.id
            } else {
                val card = CategoryEntity(
                    id = "user_type_${UUID.randomUUID().toString().take(8)}",
                    dimension = CategoryEntity.DIM_TYPE,
                    name = name,
                    createdBy = CategoryEntity.CREATED_BY_USER,
                )
                categoryDao.insert(card)
                card.id
            }
            preferenceService.applyCategoryChanges(
                recordId,
                CategoryEntity.DIM_TYPE,
                listOf(cardId),
            )
        }
    }
}
