package app.brain.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.brain.data.db.dao.CategoryDao
import app.brain.data.db.dao.RecordCategoryDao
import app.brain.data.db.dao.RecordDao
import app.brain.data.db.entity.CategoryEntity
import app.brain.data.search.SearchRepository
import app.brain.ui.common.RecordListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 收容所首页里的一个内容类型卡片。 */
data class TypeCardItem(
    val categoryId: String,
    val name: String,
    val count: Int,
)

/** 收容所页：全部记录 + 按内容类型分组的方块卡片；搜索时展示搜索结果。 */
@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val recordDao: RecordDao,
    private val categoryDao: CategoryDao,
    private val recordCategoryDao: RecordCategoryDao,
    private val searchRepository: SearchRepository,
) : ViewModel() {

    val totalCount: StateFlow<Int> = recordDao.observeActiveCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

/** 已有记录的内容类型卡片，按记录数从多到少排序。 */
    val typeCards: StateFlow<List<TypeCardItem>> =
        combine(
            categoryDao.observeByDimension(CategoryEntity.DIM_TYPE),
            recordCategoryDao.observeAllWithCategoriesActive(),
        ) { types, links ->
            val byCategory = links.filter { it.dimension == CategoryEntity.DIM_TYPE }
                .groupBy { it.categoryId }
            types.mapNotNull { cat ->
                val count = byCategory[cat.id].orEmpty().size
                if (count > 0) TypeCardItem(cat.id, cat.name, count) else null
            }.sortedByDescending { it.count }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

/** null 表示当前没有进行中的搜索。 */
    private val searchResults = MutableStateFlow<List<RecordListItem>?>(null)
    val results: StateFlow<List<RecordListItem>?> = searchResults.asStateFlow()

    private var searchJob: Job? = null

    fun onSearchChange(text: String) {
        _searchQuery.value = text
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            val term = text.trim()
            if (term.isEmpty()) {
                searchResults.value = null
                return@launch
            }
            val ids = if (term.length >= 3) {
                searchRepository.searchRecordIds(term)
            } else {
                recordDao.searchSubstring(term).first().map { it.id }
            }
            val records = if (ids.isEmpty()) emptyList() else recordDao.getActiveByIds(ids)
            val links = recordCategoryDao.getAllWithCategories()
            val byRecord = links.groupBy { it.recordId }
            val byId = records.associateBy { it.id }
            searchResults.value = ids
                .mapNotNull { byId[it] }
                .sortedByDescending { it.createdAt }
                .map { RecordListItem(it, byRecord[it.id].orEmpty()) }
        }
    }
}
