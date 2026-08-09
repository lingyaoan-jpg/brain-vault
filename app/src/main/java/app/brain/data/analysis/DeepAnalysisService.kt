package app.brain.data.analysis

import android.util.Log
import app.brain.data.ai.AiTransport
import app.brain.data.ai.ChatTurn
import app.brain.data.db.dao.AnalysisMessageDao
import app.brain.data.db.dao.AnalysisSessionDao
import app.brain.data.db.dao.RecordCategoryDao
import app.brain.data.db.dao.RecordDao
import app.brain.data.db.entity.AnalysisMessageEntity
import app.brain.data.db.entity.AnalysisSessionEntity
import app.brain.ui.common.displayTitle
import app.brain.ui.common.formatTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** 深度分析的授权范围。所有条件组合后只读取命中记录，绝不越界。 */
@Serializable
data class AnalysisScope(
    val topics: List<String> = emptyList(),
    val categoryIds: List<String> = emptyList(),
    val dateFrom: Long? = null,
    val dateTo: Long? = null,
    val recordIds: List<String> = emptyList(),
)

/**
 * 多会话深度分析：会话与对话记录独立存放；每次提问只发送授权范围内的原文。
 * 分析结论要求 AI 引用原文片段和首次记录时间，便于用户核对。
 */
@Singleton
class DeepAnalysisService @Inject constructor(
    private val sessionDao: AnalysisSessionDao,
    private val messageDao: AnalysisMessageDao,
    private val recordDao: RecordDao,
    private val recordCategoryDao: RecordCategoryDao,
    private val transport: AiTransport,
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun createSession(title: String, scope: AnalysisScope): AnalysisSessionEntity = withContext(Dispatchers.IO) {
        require(scopeHasCriterion(scope)) { "请至少选择一个分析范围" }
        val resolved = resolveScope(scope)
        val now = System.currentTimeMillis()
        val session = AnalysisSessionEntity(
            id = UUID.randomUUID().toString(),
            title = title.trim().ifEmpty { "深度分析 ${formatTime(now)}" },
            scopeJson = json.encodeToString(AnalysisScope.serializer(), resolved),
            createdAt = now,
            updatedAt = now,
        )
        sessionDao.insert(session)
        session
    }

    suspend fun updateScope(sessionId: String, scope: AnalysisScope) = withContext(Dispatchers.IO) {
        val session = sessionDao.getById(sessionId) ?: return@withContext
        require(scopeHasCriterion(scope)) { "请至少选择一个分析范围" }
        val resolved = resolveScope(scope)
        sessionDao.update(
            session.copy(
                scopeJson = json.encodeToString(AnalysisScope.serializer(), resolved),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        messageDao.deleteForSession(sessionId)
        deleteSessionRow(sessionId)
    }

    private suspend fun deleteSessionRow(sessionId: String) {
        sessionDao.delete(sessionId)
    }

    /** 发送一条用户消息并返回 AI 回复（已持久化）。 */
    suspend fun sendMessage(sessionId: String, text: String): AnalysisMessageEntity = withContext(Dispatchers.IO) {
        val session = sessionDao.getById(sessionId)
            ?: throw IllegalStateException("会话不存在")
        val userText = text.trim()
        if (userText.isEmpty()) throw IllegalStateException("消息不能为空")

        val scope = json.decodeFromString(AnalysisScope.serializer(), session.scopeJson)
        val history = messageDao.getBySession(sessionId)
        val now = System.currentTimeMillis()

        val userMessage = AnalysisMessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = AnalysisMessageEntity.ROLE_USER,
            content = userText,
            createdAt = now,
        )
        messageDao.insert(userMessage)

        val systemPrompt = buildSystemPrompt(session.title, scope)
        val turns = history.takeLast(MAX_HISTORY_MESSAGES)
            .filter { it.role == AnalysisMessageEntity.ROLE_USER || it.role == AnalysisMessageEntity.ROLE_ASSISTANT }
            .map { ChatTurn(it.role, it.content) } + ChatTurn(ChatTurn.ROLE_USER, userText)

        val reply = transport.chat(systemPrompt, turns)

        val assistantMessage = AnalysisMessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = AnalysisMessageEntity.ROLE_ASSISTANT,
            content = reply,
            createdAt = System.currentTimeMillis(),
        )
        messageDao.insert(assistantMessage)
        sessionDao.update(session.copy(updatedAt = System.currentTimeMillis()))
        assistantMessage
    }

    private suspend fun resolveScope(scope: AnalysisScope): AnalysisScope {
        val now = System.currentTimeMillis()
        val records = if (scope.recordIds.isNotEmpty()) {
            recordDao.getActiveByIds(scope.recordIds)
        } else {
            val all = recordDao.getActiveBetween(
                scope.dateFrom ?: 0L,
                scope.dateTo ?: now,
            )
            if (scope.categoryIds.isEmpty()) all else {
                val links = recordCategoryDao.getAllWithCategories().groupBy { it.recordId }
                val allowed = scope.categoryIds.toSet()
                all.filter { rec -> links[rec.id].orEmpty().any { it.categoryId in allowed } }
            }
        }
        return scope.copy(recordIds = records.take(MAX_SCOPE_RECORDS).map { it.id })
    }

    /** 组装 AI 看到的原文上下文（只含授权范围，截断过长内容）。 */
    private suspend fun buildContextText(scope: AnalysisScope): String {
        if (scope.recordIds.isEmpty()) return "（范围内没有记录）"
        val records = recordDao.getActiveByIds(scope.recordIds)
        val links = recordCategoryDao.getAllWithCategories().groupBy { it.recordId }
        val sb = StringBuilder()
        records.sortedBy { it.createdAt }.forEachIndexed { index, rec ->
            val tags = links[rec.id].orEmpty().joinToString("、") { it.name }
            sb.append("【记录 ").append(index + 1).append("】")
                .append("时间：").append(formatTime(rec.createdAt)).append("；")
                .append("标题：").append(displayTitle(rec))
                .append(if (tags.isNotBlank()) "；分类：$tags" else "")
                .append("\n原文：").append(rec.content.take(MAX_RECORD_CHARS))
                .append("\n\n")
        }
        return sb.toString().take(MAX_CONTEXT_CHARS)
    }

    private suspend fun buildSystemPrompt(title: String, scope: AnalysisScope): String {
        val parts = mutableListOf<String>()
        if (scope.topics.isNotEmpty()) parts.add("人生课题：" + scope.topics.joinToString("、"))
        if (scope.dateFrom != null || scope.dateTo != null) {
            val from = scope.dateFrom?.let { formatTime(it) } ?: "不限"
            val to = scope.dateTo?.let { formatTime(it) } ?: "不限"
            parts.add("时间范围：$from 至 $to")
        }
        parts.add("记录数量：${scope.recordIds.size} 条")
        val context = buildContextText(scope)
        return """
你是"脑内收容所"的深度分析助手。下面是用户授权的分析范围：
${parts.joinToString("\n")}

范围内的原始记录如下（原文不允许改写）：
$context

分析要求：
1. 只基于上面给出的记录内容分析，不猜测范围外信息。
2. 可帮助识别反复出现的模式、心态/情绪/观点变化、已发生的成长，以及可能的盲点或矛盾。
3. 重要结论必须引用对应原文片段并注明首次记录时间，方便用户核对，格式如：『原文片段』（2026-08-09 12:00）。
4. 行动建议可选，且明确区分"事实"与"建议"。
5. 如果范围内信息不足，请直接说明，不要编造。
6. 对话围绕会话主题「${title}」展开，回答使用简体中文。
""".trimIndent()
    }

    private fun scopeHasCriterion(scope: AnalysisScope): Boolean =
        scope.topics.isNotEmpty() ||
            scope.categoryIds.isNotEmpty() ||
            scope.dateFrom != null ||
            scope.dateTo != null ||
            scope.recordIds.isNotEmpty()

    companion object {
        private const val TAG = "DeepAnalysis"
        private const val MAX_HISTORY_MESSAGES = 12
        private const val MAX_SCOPE_RECORDS = 60
        private const val MAX_RECORD_CHARS = 500
        private const val MAX_CONTEXT_CHARS = 12_000
    }
}