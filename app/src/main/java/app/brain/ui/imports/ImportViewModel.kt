package app.brain.ui.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.brain.data.ai.AiQueueProcessor
import app.brain.data.db.dao.AiJobDao
import app.brain.data.db.dao.RecordDao
import app.brain.data.db.entity.AiJobEntity
import app.brain.data.db.entity.RecordEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val recordDao: RecordDao,
    private val aiJobDao: AiJobDao,
    private val aiQueueProcessor: AiQueueProcessor,
) : ViewModel() {

    private val _preview = MutableStateFlow<List<String>>(emptyList())
    val preview: StateFlow<List<String>> = _preview.asStateFlow()

    private val _imported = MutableStateFlow<Int?>(null)
    val imported: StateFlow<Int?> = _imported.asStateFlow()

    /** 把粘贴文本按空行切分为多条，只切分、不改写原文。 */
    fun parse(text: String) {
        val items = text
            .replace("\r\n", "\n")
            .split(Regex("\n\\s*\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(MAX_IMPORT)
        _preview.value = items
    }

    fun removeAt(index: Int) {
        val current = _preview.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _preview.value = current
        }
    }

    fun importAll() {
        val items = _preview.value
        if (items.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val jobs = mutableListOf<AiJobEntity>()
            for (content in items) {
                val id = UUID.randomUUID().toString()
                recordDao.insert(
                    RecordEntity(
                        id = id,
                        content = content,
                        titleSource = RecordEntity.TITLE_SOURCE_AI,
                        createdAt = now,
                        updatedAt = now,
                        status = RecordEntity.STATUS_PENDING,
                    )
                )
                jobs += AiJobEntity(
                    id = UUID.randomUUID().toString(),
                    recordId = id,
                    jobType = AiJobEntity.JOB_TYPE_ORGANIZE,
                    status = AiJobEntity.STATUS_QUEUED,
                    createdAt = now,
                    updatedAt = now,
                )
            }
            aiJobDao.insertAll(jobs)
            aiQueueProcessor.kick()
            _imported.value = items.size
            _preview.value = emptyList()
        }
    }

    fun clearImported() {
        _imported.value = null
    }

    companion object {
        const val MAX_IMPORT = 200
    }
}