package app.brain.data.preference

import android.util.Log
import app.brain.data.db.dao.RecordCategoryDao
import app.brain.data.db.dao.TopicHintDao
import app.brain.data.db.entity.TopicHintEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 重复主题提示：统计最近 30 天内每个"人生课题"分类下的记录数，
 * 达到阈值后生成低打扰提示（pending），用户可忽略或跳转深度分析。
 * 只做统计提示，不修改任何记录和原文。
 */
@Singleton
class TopicHintService @Inject constructor(
    private val topicHintDao: TopicHintDao,
    private val recordCategoryDao: RecordCategoryDao,
) {

    suspend fun generate() = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val windowStart = now - WINDOW_MS
            val counts = recordCategoryDao.topicCountsSince(windowStart, MIN_COUNT)
            for (topic in counts) {
                if (topicHintDao.existsPendingForTopic(topic.topic)) continue
                topicHintDao.insert(
                    TopicHintEntity(
                        id = UUID.randomUUID().toString(),
                        topic = topic.topic,
                        categoryId = topic.categoryId,
                        count = topic.cnt,
                        windowStart = windowStart,
                        windowEnd = now,
                        status = TopicHintEntity.STATUS_PENDING,
                        createdAt = now,
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "topic hint generation failed", e)
        }
    }

    companion object {
        private const val TAG = "TopicHint"
        private const val WINDOW_MS = 30L * 24 * 60 * 60 * 1000
        private const val MIN_COUNT = 3
    }
}