package app.brain.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.brain.data.db.dao.CategoryDao
import app.brain.data.db.dao.CommentDao
import app.brain.data.db.dao.RecordCategoryDao
import app.brain.data.db.dao.RecordDao
import app.brain.data.db.entity.CategoryEntity
import app.brain.data.db.entity.RecordCategoryEntity
import app.brain.data.export.BatchExportEntry
import app.brain.data.export.ExportFormat
import app.brain.data.export.ExportedFile
import app.brain.data.export.RecordExporter
import app.brain.ui.common.RecordListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class RecordsListMode { TIMELINE, FAVORITES }

@HiltViewModel
class RecordsListViewModel @Inject constructor(
    private val recordDao: RecordDao,
    private val recordCategoryDao: RecordCategoryDao,
    private val categoryDao: CategoryDao,
    private val commentDao: CommentDao,
    private val exporter: RecordExporter,
) : ViewModel() {

    val timeline: StateFlow<List<RecordListItem>> =
        combine(recordDao.observeActiveOrdered(), recordCategoryDao.observeAllWithCategories()) { records, links ->
            val byRecord = links.groupBy { it.recordId }
            records.map { RecordListItem(it, byRecord[it.id].orEmpty()) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites: StateFlow<List<RecordListItem>> =
        combine(recordDao.observeFavorites(), recordCategoryDao.observeAllWithCategories()) { records, links ->
            val byRecord = links.groupBy { it.recordId }
            records.map { RecordListItem(it, byRecord[it.id].orEmpty()) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _exportedFile = MutableStateFlow<ExportedFile?>(null)
    val exportedFile: StateFlow<ExportedFile?> = _exportedFile.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun toggleSelect(id: String) {
        val current = _selectedIds.value.toMutableSet()
        if (!current.add(id)) current.remove(id)
        _selectedIds.value = current
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun clearMessage() {
        _message.value = null
    }

    fun exportSelected(format: ExportFormat, includeMeta: Boolean) {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val records = recordDao.getActiveByIds(ids)
            val allLinks = recordCategoryDao.getAllWithCategories().groupBy { it.recordId }
            val commentsByRecord = commentDao.getByRecords(ids).groupBy { it.recordId }
            val entries = records.map { record ->
                BatchExportEntry(
                    record = record,
                    categories = allLinks[record.id].orEmpty(),
                    comments = commentsByRecord[record.id].orEmpty(),
                )
            }
            _exportedFile.value = exporter.exportBatch(entries, format, includeMeta)
        }
    }

    fun clearExport() {
        _exportedFile.value = null
    }

    /** 批量修改标题：为空或过长时忽略。 */
    fun batchSetTitle(title: String) {
        val clean = title.trim().take(40)
        if (clean.isEmpty()) {
            _message.value = "标题不能为空"
            return
        }
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            recordDao.batchSetTitle(ids, clean, System.currentTimeMillis())
            _message.value = "已更新 ${ids.size} 条标题"
        }
    }

    /** 批量修改首次记录时间。 */
    fun batchSetTime(timeMillis: Long) {
        if (timeMillis <= 0) {
            _message.value = "请先填写时间"
            return
        }
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            recordDao.batchSetTime(ids, timeMillis, System.currentTimeMillis())
            _message.value = "已更新 ${ids.size} 条记录时间"
        }
    }

    /**
     * 批量把所选记录的某个维度分类改为指定值（覆盖该维度原有分类）。
     * 分类不存在时自动新建（用户创建）。
     */
    fun batchSetDimension(dimension: String, value: String) {
        val clean = value.trim().take(20)
        if (clean.isEmpty()) {
            _message.value = "分类不能为空"
            return
        }
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val existing = categoryDao.getByName(dimension, clean)
            val category = existing ?: CategoryEntity(
                id = "user_${dimension}_${UUID.randomUUID().toString().take(8)}",
                dimension = dimension,
                name = clean,
                createdBy = CategoryEntity.CREATED_BY_USER,
            ).also { categoryDao.insert(it) }
            recordCategoryDao.removeDimensionForRecords(ids, dimension)
            val now = System.currentTimeMillis()
            recordCategoryDao.insertAll(
                ids.map {
                    RecordCategoryEntity(
                        recordId = it,
                        categoryId = category.id,
                        source = RecordCategoryEntity.SOURCE_USER,
                        createdAt = now,
                    )
                }
            )
            _message.value = "已把 ${ids.size} 条记录的分类改为「$clean」"
        }
    }
}