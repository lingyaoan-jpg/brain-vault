package app.brain.ui.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.brain.data.db.dao.CategoryDao
import app.brain.data.db.dao.RecordCategoryDao
import app.brain.data.db.dao.RecordDao
import app.brain.data.db.entity.CategoryEntity
import app.brain.data.search.SearchRepository
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

/** 卡片内筛选条件：只选某个辅助一级分类，或该一级分类下的某个二级分类。 */
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

/** 卡片内筛选只列辅助维度：人生课题 / 情绪 / 重要程度 / 紧急程度（不再出现「内容类型」）。 */
private val AUX_DIMENSIONS = DimensionOrder.FILTER_ORDER.filter { it != CategoryEntity.DIM_TYPE }

/**
 * 收容所卡片对应的记录列表：cardKey 为 "all" 时展示全部卡片（全部记录），
 * 否则展示该内容类型卡片下的记录。
 */
@HiltViewModel
class CardRecordsViewModel @Inject constructor(
    private val recordDao: RecordDao,
    private val recordCategoryDao: RecordCategoryDao,
    private val categoryDao: CategoryDao,
    private val searchRepository: SearchRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** "all" 或内容类型分类 id。 */
    private val cardKey: String? =
        savedStateHandle.get<String>("cardKey")?.takeIf { it != "all" && it.isNotBlank() }

    private val _cardName = MutableStateFlow("全部卡片")
    val cardName: StateFlow<String> = _cardName.asStateFlow()

    /** 重命名错误提示（名称重复等）。 */
    private val _renameMessage = MutableStateFlow<String?>(null)
    val renameMessage: StateFlow<String?> = _renameMessage.asStateFlow()

    /** 重命名成功标志，用于关闭弹窗。 */
    private val _renameDone = MutableStateFlow(false)
    val renameDone: StateFlow<Boolean> = _renameDone.asStateFlow()

    init {
        if (cardKey != null) {
            viewModelScope.launch {
                _cardName.value = categoryDao.getById(cardKey)?.name ?: "全部卡片"
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
            if (cardKey == null) {
                all
            } else {
                all.filter { item -> item.categories.any { it.categoryId == cardKey } }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _filter = MutableStateFlow<CardFilter?>(null)
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

    /** 筛选选项：只列辅助维度，每个一级可展开选二级，也可只选一级。 */
    val filterOptions: StateFlow<CardFilterOptions> =
        baseItems.map { items ->
            CardFilterOptions(
                dimensions = AUX_DIMENSIONS.map { dim ->
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

    /** 重命名当前内容类型卡片。名称重复或为空白时给出错误提示。 */
    fun renameCard(newNameRaw: String) {
        val cardId = cardKey ?: return
        val newName = newNameRaw.trim()
        if (newName.isEmpty()) {
            _renameMessage.value = "名称不能为空"
            return
        }
        viewModelScope.launch {
            val current = categoryDao.getById(cardId) ?: return@launch
            if (current.name == newName) {
                _renameMessage.value = null
                _renameDone.value = true
                return@launch
            }
            val existing = categoryDao.getByName(CategoryEntity.DIM_TYPE, newName)
            if (existing != null && existing.id != cardId) {
                _renameMessage.value = "已有同名卡片：$newName"
                return@launch
            }
            categoryDao.update(current.copy(name = newName))
            _cardName.value = newName
            searchRepository.refreshCategoryNames()
            _renameMessage.value = null
            _renameDone.value = true
        }
    }

    /** 打开重命名弹窗前重置重命名状态。 */
    fun resetRenameState() {
        _renameMessage.value = null
        _renameDone.value = false
    }
}
