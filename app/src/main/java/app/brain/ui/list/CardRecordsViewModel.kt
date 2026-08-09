package app.brain.ui.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.brain.data.db.dao.CategoryDao
import app.brain.data.db.dao.RecordCategoryDao
import app.brain.data.db.dao.RecordDao
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

/** 收容所卡片对应的记录列表：categoryId 为 "all" 时展示全部记录。 */
@HiltViewModel
class CardRecordsViewModel @Inject constructor(
    private val recordDao: RecordDao,
    private val recordCategoryDao: RecordCategoryDao,
    private val categoryDao: CategoryDao,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val categoryId: String? =
        savedStateHandle.get<String>("categoryId")?.takeIf { it != "all" && it.isNotBlank() }

    private val _cardName = MutableStateFlow(if (categoryId == null) "全部记录" else "")
    val cardName: StateFlow<String> = _cardName.asStateFlow()

    init {
        if (categoryId != null) {
            viewModelScope.launch {
                _cardName.value = categoryDao.getById(categoryId)?.name ?: "记录"
            }
        }
    }

    private fun buildItems(records: List<app.brain.data.db.entity.RecordEntity>, links: List<app.brain.data.db.dao.RecordCategoryWithCategory>): List<RecordListItem> {
        val byRecord = links.groupBy { it.recordId }
        return records.map { RecordListItem(it, byRecord[it.id].orEmpty()) }
    }

    val items: StateFlow<List<RecordListItem>> =
        if (categoryId != null) {
            combine(
                recordDao.observeActiveByCategory(categoryId),
                recordCategoryDao.observeAllWithCategoriesActive(),
            ) { recs, links ->
                buildItems(recs, links)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        } else {
            combine(
                recordDao.observeActiveOrdered(),
                recordCategoryDao.observeAllWithCategoriesActive(),
            ) { recs, links ->
                buildItems(recs, links)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }
}
