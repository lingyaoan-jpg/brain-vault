package app.brain.ui.record

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.brain.data.ai.AiQueueProcessor
import app.brain.data.db.dao.AiJobDao
import app.brain.data.db.dao.DraftDao
import app.brain.data.db.dao.RecordCategoryDao
import app.brain.data.db.dao.RecordDao
import app.brain.data.db.entity.AiJobEntity
import app.brain.data.db.entity.CategoryEntity
import app.brain.data.db.entity.DraftEntity
import app.brain.data.db.entity.RecordEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RecordEditorViewModel @Inject constructor(
    private val recordDao: RecordDao,
    private val recordCategoryDao: RecordCategoryDao,
    private val draftDao: DraftDao,
    private val aiJobDao: AiJobDao,
    private val aiQueueProcessor: AiQueueProcessor,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val recordId: String? = savedStateHandle["recordId"]

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    /** 新建记录保存后，AI 正在后台分类（等待收容）。 */
    private val _organizing = MutableStateFlow(false)
    val organizing: StateFlow<Boolean> = _organizing.asStateFlow()

    /** 新记录分类完成后命中的内容类型卡片 id；null 表示超时或失败（兜底去「全部记录」）。 */
    private val _organizedCategoryId = MutableStateFlow<String?>(null)
    val organizedCategoryId: StateFlow<String?> = _organizedCategoryId.asStateFlow()

    val isEdit: Boolean = recordId != null

    private var draftCreatedAt: Long = System.currentTimeMillis()

    init {
        viewModelScope.launch {
            if (recordId != null) {
                val existing = recordDao.getById(recordId)
                _title.value = existing?.title.orEmpty()
                _content.value = existing?.content.orEmpty()
            } else {
                val draft = draftDao.getLatest()
                if (draft != null) {
                    draftCreatedAt = draft.createdAt
                    _title.value = draft.title.orEmpty()
                    _content.value = draft.content
                }
            }

            // 标题或正文变化时，500ms 防抖自动保存草稿（仅新建模式）
            launch {
                combine(_title, _content) { t, c -> t to c }
                    .drop(1)
                    .debounce(500)
                    .distinctUntilChanged()
                    .collect { (t, c) ->
                        if (recordId != null) return@collect
                        if (c.isBlank() && t.isBlank()) {
                            draftDao.delete(DRAFT_ID)
                        } else {
                            draftDao.upsert(
                                DraftEntity(
                                    id = DRAFT_ID,
                                    content = c,
                                    title = t.trim().ifEmpty { null },
                                    createdAt = draftCreatedAt,
                                    updatedAt = System.currentTimeMillis(),
                                )
                            )
                        }
                    }
            }
        }
    }

    fun onContentChange(text: String) {
        _content.value = text
    }

    fun onTitleChange(text: String) {
        _title.value = text
    }

    fun save() {
        val text = _content.value
        if (text.isBlank() || _saving.value) return
        viewModelScope.launch {
            _saving.value = true
            val now = System.currentTimeMillis()
            val title = _title.value.trim().ifEmpty { null }
            if (recordId != null) {
                val existing = recordDao.getById(recordId)
                if (existing != null) {
                    // A08：正文更新，首次记录时间不变，不生成历史版本
                    recordDao.update(
                        existing.copy(
                            content = text,
                            title = title,
                            titleSource = if (title != null) RecordEntity.TITLE_SOURCE_MANUAL else RecordEntity.TITLE_SOURCE_AI,
                            updatedAt = now,
                        )
                    )
                }
            } else {
                val newId = UUID.randomUUID().toString()
                recordDao.insert(
                    RecordEntity(
                        id = newId,
                        content = text,
                        title = title,
                        titleSource = if (title != null) RecordEntity.TITLE_SOURCE_MANUAL else RecordEntity.TITLE_SOURCE_AI,
                        createdAt = now,
                        updatedAt = now,
                        status = RecordEntity.STATUS_PENDING,
                    )
                )
                aiJobDao.insert(
                    AiJobEntity(
                        id = UUID.randomUUID().toString(),
                        recordId = newId,
                        jobType = AiJobEntity.JOB_TYPE_ORGANIZE,
                        status = AiJobEntity.STATUS_QUEUED,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
                aiQueueProcessor.kick()
                draftDao.delete(DRAFT_ID)
                startOrganizeWatch(newId)
            }
            _saved.value = true
            _saving.value = false
        }
    }

    /** 轮询等待 AI 分类结果：命中内容类型后跳转对应卡片，超时则兜底。 */
    private fun startOrganizeWatch(recordId: String) {
        _organizing.value = true
        viewModelScope.launch {
            val deadline = System.currentTimeMillis() + ORGANIZE_WAIT_MS
            var found: String? = null
            while (System.currentTimeMillis() < deadline && found == null) {
                delay(400)
                val typeLink = recordCategoryDao.getByRecord(recordId)
                    .firstOrNull { it.dimension == CategoryEntity.DIM_TYPE }
                if (typeLink != null) found = typeLink.categoryId
            }
            _organizedCategoryId.value = found
            _organizing.value = false
        }
    }

    companion object {
        const val DRAFT_ID = "draft_main"
        const val ORGANIZE_WAIT_MS = 10_000L
    }
}