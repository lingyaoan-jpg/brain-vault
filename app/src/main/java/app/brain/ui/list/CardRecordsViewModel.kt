package app.brain.ui.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.brain.data.db.dao.CategoryDao
import app.brain.data.db.dao.RecordCategoryDao
import app.brain.data.db.dao.RecordDao
import app.brain.data.db.entity.CategoryEntity
import app.brain.ui.common.DimensionOrder
import app.brain.ui.common.RecordListItem
import app.brain.ui.common.dimensionLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 卡片内筛选条件：只选某个一级分类，或该一级分类下的某个二级分类。 */
data class CardFilter(
    val dimension: String,
    val categoryId: String? = null,
)

/** 筛选面板里的一个二级分类。 */
data class FilterCategoryItem(
    val categoryId: String,
    val name: String,
    val count: Int,
)

/** 筛选面板里的一个一级分类（可展开看二级分类）。 */
data class FilterDimensionItem(
    val dimension: String,
    val label: String,
    val count: Int,
    val categories: List<FilterCategoryItem>,
)

/** 当前卡片可用的筛选选项。 */
data class CardFilterOptions(
    val dimensions: List<FilterDimensionItem>,
) {
    val isEmpty: Boolean get() = dimensions.all { it.count == 0 && it.categories.isEmpty() }
}

/** 收容所卡片对应的记录列表：dimension 为 "all" 时展示全部记录，否则展示该一级分类下的记录。 */
@HiltViewModel
class CardRecordsViewModel @Inject constructor(
    private val recordDao: RecordDao,
    private val recordCategoryDao: RecordCategoryDao,
    private val categoryDao: CategoryDao,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val dimension: String? =
        savedStateHandle.get<String>("dimension")?.takeIf { it != "all" && it.isNotBlank() }

    /** 从 AI 分类跳转进来时预选的二级分类（通常是内容类型卡片里的具体类型）。 */
    private val initialCategoryId: String? =
        savedStateHandle.get<String>("categoryId")?.takeIf { it.isNotBlank() }

    private val _cardName = MutableStateFlow(if (dimension == null) "全部记录" else "")
    val cardName: StateFlow<String> = _cardName.asStateFlow()

    init {
        if (dimension != null) {
            viewModelScope.launch {
                _cardName.value = if (initialCategoryId != null) {
                    categoryDao.getById(initialCategoryId)?.name ?: dimensionLabel(dimension)
                } else {
                    dimensionLabel(dimension)
                }
            }
        }
    }

    /** 当前卡片范围内的全部记录（尚未应用筛选）。 */
    private val baseItems: StateFlow<List<RecordListItem>> =
        combine(
            recordDao.observeActiveOrdered(),
            recordCategoryDao.observeAllWithCategoriesActive(),
        ) { records, links ->
            val byRecord = links.groupBy { it.recordId }
            val all = records.map { RecordListItem(it, byRecord[it.id].orEmpty()) }
            if (dimension == null) {
                all
            } else {
                all.filter { item -> item.categories.any { it.dimension == dimension } }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _filter = MutableStateFlow<CardFilter?>(
        initialCategoryId?.let { CardFilter(CategoryEntity.DIM_TYPE, it) }
    )
    val filter: StateFlow<CardFilter?> = _filter.asStateFlow()

    /** 应用筛选后的记录列表。 */
    val items: StateFlow<List<RecordListItem>> =
        combine(baseItems, _filter) { items, f ->
            if (f == null) {
                items
            } else {
                items.filter { item ->
                    if (f.categoryId != null) {
                        item.categories.any { it.categoryId == f.categoryId }
                    } else {
                        item.categories.any { it.dimension == f.dimension }
                    }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 筛选选项：一级分类卡片只列出自己的二级分类；「全部记录」列出全部一级分类（可展开）。 */
    val filterOptions: StateFlow<CardFilterOptions> =
        baseItems.map { items ->
            val dims = if (dimension != null) listOf(dimension) else DimensionOrder.FILTER_ORDER
            CardFilterOptions(
                dimensions = dims.map { dim ->
                    val dimItems = items.filter { item -> item.categories.any { it.dimension == dim } }
                    val categories = dimItems
                        .flatMap { item -> item.categories.filter { it.dimension == dim } }
                        .groupBy { it.categoryId }
                        .map { (id, list) ->
                            FilterCategoryItem(
                                categoryId = id,
                                name = list.first().name,
                                count = list.map { it.recordId }.distinct().size,
                            )
                        }
                        .sortedByDescending { it.count }
                    FilterDimensionItem(
                        dimension = dim,
                        label = dimensionLabel(dim),
                        count = dimItems.size,
                        categories = categories,
                    )
                },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardFilterOptions(emptyList()))

    fun selectFilter(dimension: String, categoryId: String? = null) {
        _filter.value = CardFilter(dimension, categoryId)
    }

    fun clearFilter() {
        _filter.value = null
    }
}
