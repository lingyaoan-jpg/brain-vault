package app.brain.data.ai

import android.util.Log
import app.brain.data.db.dao.AiJobDao
import app.brain.data.db.dao.CategoryPreferenceDao
import app.brain.data.db.dao.CategoryDao
import app.brain.data.db.dao.RecordCategoryDao
import app.brain.data.db.dao.RecordDao
import app.brain.data.db.entity.AiJobEntity
import app.brain.data.db.entity.CategoryEntity
import app.brain.data.db.entity.RecordCategoryEntity
import app.brain.data.db.entity.RecordEntity
import app.brain.data.preference.PreferenceService
import app.brain.data.preference.TopicHintService
import app.brain.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 后台 AI 整理队列：轮询 ai_jobs，调用传输层整理记录，写回标题与多维分类。
 * 断网/无密钥时任务停留在队列，联网或配置密钥后自动继续。
 */
@Singleton
class AiQueueProcessor @Inject constructor(
    private val jobDao: AiJobDao,
    private val recordDao: RecordDao,
    private val categoryDao: CategoryDao,
    private val recordCategoryDao: RecordCategoryDao,
    private val preferenceDao: CategoryPreferenceDao,
    private val preferenceService: PreferenceService,
    private val topicHintService: TopicHintService,
    private val transport: AiTransport,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wake = Channel<Unit>(Channel.CONFLATED)

    fun start() {
        scope.launch {
            try {
                mergeDuplicateCategories()
            } catch (e: Exception) {
                Log.w(TAG, "merge categories failed", e)
            }
            try {
                removeAiKeywordTags()
            } catch (e: Exception) {
                Log.w(TAG, "remove keyword tags failed", e)
            }
            try {
                backfillPendingJobs()
            } catch (e: Exception) {
                Log.w(TAG, "backfill failed", e)
            }
            try {
                preferenceService.generateSuggestions()
            } catch (e: Exception) {
                Log.w(TAG, "suggestion generation failed", e)
            }
            try {
                topicHintService.generate()
            } catch (e: Exception) {
                Log.w(TAG, "topic hint generation failed", e)
            }
            while (isActive) {
                try {
                    processDueJobs()
                } catch (e: Exception) {
                    Log.w(TAG, "queue processing error", e)
                }
                withTimeoutOrNull(POLL_INTERVAL_MS) { wake.receive() }
            }
        }
    }

    /** 为历史待整理记录补建队列任务（幂等：已有活动任务则跳过）。 */
    private suspend fun backfillPendingJobs() {
        val records = recordDao.getUntreated()
        val now = System.currentTimeMillis()
        for (record in records) {
            if (jobDao.findActive(record.id, AiJobEntity.JOB_TYPE_ORGANIZE) == null) {
                jobDao.insert(
                    AiJobEntity(
                        id = UUID.randomUUID().toString(),
                        recordId = record.id,
                        jobType = AiJobEntity.JOB_TYPE_ORGANIZE,
                        status = AiJobEntity.STATUS_QUEUED,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }
        }
    }

    /** 保存记录后立即唤醒队列。 */
    fun kick() {
        wake.trySend(Unit)
        scope.launch {
            try {
                preferenceService.generateSuggestions()
            } catch (e: Exception) {
                Log.w(TAG, "suggestion generation failed", e)
            }
            try {
                topicHintService.generate()
            } catch (e: Exception) {
                Log.w(TAG, "topic hint generation failed", e)
            }
        }
    }

    private suspend fun processDueJobs() {
        if (!settings.hasApiKey) return
        val jobs = jobDao.nextDue(System.currentTimeMillis(), MAX_BATCH)
        for (job in jobs) {
            try {
                process(job)
            } catch (e: Exception) {
                Log.w(TAG, "job ${job.id} failed: ${e.message}", e)
            }
        }
    }

    private suspend fun process(job: AiJobEntity) {
        val now = System.currentTimeMillis()
        val record = recordDao.getById(job.recordId)
        if (record == null || record.deletedAt != null) {
            jobDao.update(job.copy(status = AiJobEntity.STATUS_DONE, updatedAt = now))
            return
        }

        jobDao.update(job.copy(status = AiJobEntity.STATUS_RUNNING, updatedAt = now))
        recordDao.update(record.copy(status = RecordEntity.STATUS_PROCESSING, aiError = null, updatedAt = now))

        try {
            val categories = categoryDao.getAllActive()
            val preferences = preferenceDao.getStrong(PreferenceService.MIN_RULE_COUNT)
            val request = OrganizeRequest(
                content = record.content,
                title = record.title?.takeIf { record.titleSource == RecordEntity.TITLE_SOURCE_MANUAL },
                categories = categories.map { CategoryInfo(it.id, it.dimension, it.name) },
                preferences = preferences.map { CategoryPreferenceInfo(it.dimension, it.fromName, it.toName, it.count) },
            )
            val result = transport.organize(request)
            applyResult(record, result)

            // 重新读取记录，避免旧快照覆盖用户手动修改的标题
            val fresh = recordDao.getById(job.recordId) ?: return
            jobDao.update(job.copy(status = AiJobEntity.STATUS_DONE, updatedAt = System.currentTimeMillis()))
            recordDao.update(
                fresh.copy(
                    status = RecordEntity.STATUS_ORGANIZED,
                    aiProcessedAt = System.currentTimeMillis(),
                    aiAttempts = 0,
                    aiError = null,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        } catch (e: Exception) {
            val attempts = job.attempts + 1
            val failed = attempts >= job.maxAttempts
            jobDao.update(
                job.copy(
                    attempts = attempts,
                    status = if (failed) AiJobEntity.STATUS_FAILED else AiJobEntity.STATUS_QUEUED,
                    nextRetryAt = if (failed) null else System.currentTimeMillis() + backoffMs(attempts),
                    lastError = e.message?.take(300),
                    updatedAt = System.currentTimeMillis(),
                )
            )
            val current = recordDao.getById(job.recordId) ?: return
            recordDao.update(
                current.copy(
                    status = if (failed) RecordEntity.STATUS_FAILED else RecordEntity.STATUS_PENDING,
                    aiAttempts = attempts,
                    aiError = e.message?.take(300),
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    private suspend fun applyResult(record: RecordEntity, result: OrganizeResult) {
        val now = System.currentTimeMillis()

        // 标题：只在用户未手动设置标题时写入
        if (record.titleSource == RecordEntity.TITLE_SOURCE_AI) {
            result.title?.trim()?.takeIf { it.isNotBlank() }?.let { aiTitle ->
                if (record.title.isNullOrBlank()) {
                    recordDao.update(record.copy(title = aiTitle.take(40), updatedAt = now))
                }
            }
        }

        val existing = categoryDao.getAllActive()
        val byKey = mutableMapOf<String, CategoryEntity>()
        existing.forEach { byKey[it.dimension + "|" + normalizeName(it.name)] = it }
        val links = mutableListOf<RecordCategoryEntity>()

        suspend fun add(dimension: String, names: List<String>, max: Int) {
            names.take(max).forEach { raw ->
                val name = raw.trim()
                if (name.isBlank()) return@forEach
                val key = dimension + "|" + normalizeName(name)
                val category = byKey[key] ?: createCategory(dimension, name).also { byKey[key] = it }
                links += RecordCategoryEntity(
                    recordId = record.id,
                    categoryId = category.id,
                    source = RecordCategoryEntity.SOURCE_AI,
                    createdAt = now,
                )
            }
        }

        // 内容类型只保留一个，作为收容所卡片的主分类
        add(CategoryEntity.DIM_TYPE, result.types, 1)
        add(CategoryEntity.DIM_TOPIC, result.topics, 2)
        result.urgency?.let { add(CategoryEntity.DIM_URGENCY, listOf(it), 1) }
        result.importance?.let { add(CategoryEntity.DIM_IMPORTANCE, listOf(it), 1) }
        add(CategoryEntity.DIM_EMOTION, result.emotions, 3)

        if (links.isNotEmpty()) {
            recordCategoryDao.removeAllAiForRecord(record.id)
            recordCategoryDao.insertAll(links)
        }
    }

    /** 合并归一化后重复的分类（种子优先、保留最早），迁移记录关联后删除重复项。 */
    private suspend fun mergeDuplicateCategories() {
        val all = categoryDao.getAllActive()
        val groups = all.groupBy { it.dimension to normalizeName(it.name) }
        for (group in groups.values) {
            if (group.size <= 1) continue
            val keeper = group.sortedWith(compareBy({ rankSource(it.createdBy) }, { it.id })).first()
            for (dupe in group) {
                if (dupe.id == keeper.id) continue
                recordCategoryDao.linksByCategory(dupe.id).forEach { link ->
                    if (recordCategoryDao.exists(link.recordId, keeper.id)) {
                        recordCategoryDao.remove(link.recordId, dupe.id)
                    } else {
                        recordCategoryDao.repoint(link.recordId, dupe.id, keeper.id)
                    }
                }
                categoryDao.delete(dupe.id)
            }
        }
    }

    /** 清理 AI 生成的自由关键词标签（这类日常词不再需要）。 */
    private suspend fun removeAiKeywordTags() {
        categoryDao.deleteByDimensionAndCreator(CategoryEntity.DIM_TAG, CategoryEntity.CREATED_BY_AI)
    }

    private fun rankSource(source: String): Int = when (source) {
        CategoryEntity.CREATED_BY_SEED -> 0
        CategoryEntity.CREATED_BY_USER -> 1
        else -> 2
    }

    /** 归一化分类名：去空白与常见标点、全角转半角、转小写，用于近似重复识别。 */
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

    private suspend fun createCategory(dimension: String, name: String): CategoryEntity {
        val category = CategoryEntity(
            id = "ai_${dimension}_${UUID.randomUUID().toString().take(8)}",
            dimension = dimension,
            name = name.take(20),
            createdBy = CategoryEntity.CREATED_BY_AI,
        )
        categoryDao.insert(category)
        return categoryDao.getByName(dimension, name) ?: category
    }

    private fun backoffMs(attempts: Int): Long =
        listOf(30_000L, 120_000L, 600_000L, 3_600_000L)
            .getOrElse(attempts - 1) { 3_600_000L }

    companion object {
        val PUNCT_TO_STRIP = setOf(
            '，', '。', '！', '？', '、', '；', '：', '“', '”', '‘', '’',
            '（', '）', '【', '】', '《', '》', '〈', '〉', '·', '—', '…', '～',
            ',', '.', '!', '?', ';', ':', '"', '\'', '(', ')', '[', ']', '{', '}',
        )
        private const val TAG = "AiQueue"
        private const val POLL_INTERVAL_MS = 30_000L
        private const val MAX_BATCH = 5
    }
}