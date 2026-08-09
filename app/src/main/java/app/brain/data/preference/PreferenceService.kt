package app.brain.data.preference

import android.util.Log
import app.brain.data.db.dao.AiFeedbackDao
import app.brain.data.db.dao.CategoryDao
import app.brain.data.db.dao.CategoryPreferenceDao
import app.brain.data.db.dao.CategorySuggestionDao
import app.brain.data.db.dao.RecordCategoryDao
import app.brain.data.db.entity.AiFeedbackEntity
import app.brain.data.db.entity.CategoryPreferenceEntity
import app.brain.data.db.entity.CategorySuggestionEntity
import app.brain.data.db.entity.RecordCategoryEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 个性化分类学习（V1-B）：
 * 1. 用户手动改分类后，把「AI 原分类 → 用户新分类」记为偏好信号；
 * 2. 偏好只影响未来 AI 判断（提示词携带），不自动改历史；
 * 3. 对可能分类错误的历史内容生成「分类调整建议」，由用户接受或忽略。
 */
@Singleton
class PreferenceService @Inject constructor(
    private val categoryDao: CategoryDao,
    private val recordCategoryDao: RecordCategoryDao,
    private val aiFeedbackDao: AiFeedbackDao,
    private val preferenceDao: CategoryPreferenceDao,
    private val suggestionDao: CategorySuggestionDao,
) {

    /**
     * 详情页保存分类编辑：维度内用 newCategoryIds 整体替换。
     * 若移除了 AI 来源分类且新增了分类，记一条「AI 原分类 → 用户新分类」偏好。
     */
    suspend fun applyCategoryChanges(recordId: String, dimension: String, newCategoryIds: List<String>) {
        val now = System.currentTimeMillis()
        val oldLinks = recordCategoryDao.getByRecord(recordId).filter { it.dimension == dimension }
        val oldIds = oldLinks.map { it.categoryId }.toSet()
        val newSet = newCategoryIds.toSet()

        val removed = oldIds - newSet
        val added = newSet - oldIds

        // 从「移除的 AI 来源分类」里取一个作为 from，记偏好（只取第一组配对，避免规则爆炸）
        val removedAi = removed.firstOrNull { id ->
            oldLinks.firstOrNull { it.categoryId == id }?.source == RecordCategoryEntity.SOURCE_AI
        }
        val from = removedAi?.let { categoryDao.getById(it) }
        val to = added.firstOrNull()?.let { categoryDao.getById(it) }

        if (from != null && to != null) {
            val fromKey = normalizeName(from.name)
            val toKey = normalizeName(to.name)
            if (fromKey.isNotBlank() && fromKey != toKey) {
                val bumped = preferenceDao.bump(dimension, fromKey, toKey, to.id, now)
                if (bumped == 0) {
                    preferenceDao.insert(
                        CategoryPreferenceEntity(
                            id = UUID.randomUUID().toString(),
                            dimension = dimension,
                            fromName = fromKey,
                            toName = toKey,
                            toCategoryId = to.id,
                            count = 1,
                            updatedAt = now,
                        )
                    )
                }
            }
            aiFeedbackDao.insert(
                AiFeedbackEntity(
                    id = UUID.randomUUID().toString(),
                    recordId = recordId,
                    categoryId = to.id,
                    action = AiFeedbackEntity.ACTION_CHANGE_CATEGORY,
                    oldValue = from.name,
                    newValue = to.name,
                    createdAt = now,
                )
            )
        }

        for (id in removed) {
            if (id == removedAi) continue
            categoryDao.getById(id)?.let { cat ->
                aiFeedbackDao.insert(
                    AiFeedbackEntity(
                        id = UUID.randomUUID().toString(),
                        recordId = recordId,
                        categoryId = id,
                        action = AiFeedbackEntity.ACTION_REMOVE_CATEGORY,
                        oldValue = cat.name,
                        createdAt = now,
                    )
                )
            }
        }
        for (id in added) {
            if (id == to?.id) continue
            categoryDao.getById(id)?.let { cat ->
                aiFeedbackDao.insert(
                    AiFeedbackEntity(
                        id = UUID.randomUUID().toString(),
                        recordId = recordId,
                        categoryId = id,
                        action = AiFeedbackEntity.ACTION_ADD_CATEGORY,
                        newValue = cat.name,
                        createdAt = now,
                    )
                )
            }
        }

        for (id in removed) {
            recordCategoryDao.remove(recordId, id)
        }
        for (id in added) {
            recordCategoryDao.insert(
                RecordCategoryEntity(
                    recordId = recordId,
                    categoryId = id,
                    source = RecordCategoryEntity.SOURCE_USER,
                    createdAt = now,
                )
            )
        }
    }

    /** 接受一条历史调整建议：把记录的分类从 from 换成 to（不动其他分类）。 */
    suspend fun acceptSuggestion(recordId: String, fromCategoryId: String, toCategoryId: String, suggestionId: String) {
        val now = System.currentTimeMillis()
        if (recordCategoryDao.exists(recordId, fromCategoryId)) {
            recordCategoryDao.remove(recordId, fromCategoryId)
            if (!recordCategoryDao.exists(recordId, toCategoryId)) {
                recordCategoryDao.insert(
                    RecordCategoryEntity(
                        recordId = recordId,
                        categoryId = toCategoryId,
                        source = RecordCategoryEntity.SOURCE_USER,
                        createdAt = now,
                    )
                )
            }
            val from = categoryDao.getById(fromCategoryId)
            val to = categoryDao.getById(toCategoryId)
            if (from != null && to != null) {
                aiFeedbackDao.insert(
                    AiFeedbackEntity(
                        id = UUID.randomUUID().toString(),
                        recordId = recordId,
                        categoryId = toCategoryId,
                        action = AiFeedbackEntity.ACTION_CHANGE_CATEGORY,
                        oldValue = from.name,
                        newValue = to.name,
                        createdAt = now,
                    )
                )
            }
        }
        suggestionDao.setStatus(suggestionId, CategorySuggestionEntity.STATUS_ACCEPTED)
    }

    suspend fun ignoreSuggestion(suggestionId: String) {
        suggestionDao.setStatus(suggestionId, CategorySuggestionEntity.STATUS_IGNORED)
    }

    /** 全部接受：对每一条待处理建议执行接受逻辑。 */
    suspend fun acceptAllSuggestions() {
        val pending = suggestionDao.getPendingSnapshot()
        for (item in pending) {
            acceptSuggestion(item.recordId, item.fromCategoryId, item.toCategoryId, item.id)
        }
    }

    /**
     * 扫描历史记录，按 count>=2 的偏好规则生成待处理建议（幂等：同记录同目标已存在则跳过）。
     */
    suspend fun generateSuggestions() {
        if (suggestionDao.pendingCount() >= MAX_PENDING) return
        val rules = preferenceDao.getStrong(MIN_RULE_COUNT)
        if (rules.isEmpty()) return
        val best = rules
            .groupBy { it.dimension to it.fromName }
            .mapValues { (_, list) -> list.maxByOrNull { it.count } ?: list.first() }

        val byRecord = recordCategoryDao.getAllWithCategories().groupBy { it.recordId }
        val now = System.currentTimeMillis()
        val candidates = mutableListOf<CategorySuggestionEntity>()

        outer@ for ((recordId, links) in byRecord) {
            for (link in links) {
                if (link.source != RecordCategoryEntity.SOURCE_AI) continue
                val rule = best[link.dimension to normalizeName(link.name)] ?: continue
                val toId = rule.toCategoryId ?: continue
                if (toId == link.categoryId) continue
                if (links.any { it.categoryId == toId }) continue
                if (suggestionDao.existsOpen(recordId, toId) > 0) continue
                val to = categoryDao.getById(toId) ?: continue
                candidates += CategorySuggestionEntity(
                    id = UUID.randomUUID().toString(),
                    recordId = recordId,
                    fromCategoryId = link.categoryId,
                    toCategoryId = toId,
                    reason = "你常把「" + link.name + "」改为「" + to.name + "」，这条可能也适合归入「" + to.name + "」。",
                    status = CategorySuggestionEntity.STATUS_PENDING,
                    createdAt = now,
                )
                if (candidates.size >= MAX_GENERATE) break@outer
            }
        }
        if (candidates.isNotEmpty()) {
            suggestionDao.insertAll(candidates)
            Log.i(TAG, "generated " + candidates.size + " category suggestions")
        }
    }

    /** 归一化分类名：去空白与常见标点、全角转半角、转小写。 */
    private fun normalizeName(name: String): String {
        val sb = StringBuilder()
        for (ch in name) {
            val code = ch.code
            if (code == ' '.code || code == '\t'.code || code == 0x3000) continue
            if (code in 0xFF01..0xFF5E) {
                sb.append((code - 0xFEE0).toChar())
                continue
            }
            if (ch in PUNCT_TO_STRIP) continue
            sb.append(ch)
        }
        return sb.toString().lowercase()
    }

    companion object {
        private const val TAG = "Preference"
        const val MIN_RULE_COUNT = 2
        private const val MAX_PENDING = 50
        private const val MAX_GENERATE = 30
        val PUNCT_TO_STRIP = setOf(
            '，', '。', '！', '？', '、', '；', '：', '“', '”', '‘', '’',
            '（', '）', '【', '】', '《', '》', '〈', '〉', '·', '—', '…', '～',
            ',', '.', '!', '?', ';', ':', '"', '\'', '(', ')', '[', ']', '{', '}',
        )
    }
}