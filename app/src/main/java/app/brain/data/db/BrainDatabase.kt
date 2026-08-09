package app.brain.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteStatement
import app.brain.data.db.dao.AiFeedbackDao
import app.brain.data.db.dao.AiJobDao
import app.brain.data.db.dao.AnalysisMessageDao
import app.brain.data.db.dao.AnalysisSessionDao
import app.brain.data.db.dao.CategoryDao
import app.brain.data.db.dao.CategoryPreferenceDao
import app.brain.data.db.dao.CategorySuggestionDao
import app.brain.data.db.dao.CommentDao
import app.brain.data.db.dao.DraftDao
import app.brain.data.db.dao.EmbeddingDao
import app.brain.data.db.dao.RecordCategoryDao
import app.brain.data.db.dao.RecordDao
import app.brain.data.db.dao.RecordLinkDao
import app.brain.data.db.dao.TopicHintDao
import app.brain.data.db.entity.AiFeedbackEntity
import app.brain.data.db.entity.AiJobEntity
import app.brain.data.db.entity.AnalysisMessageEntity
import app.brain.data.db.entity.AnalysisSessionEntity
import app.brain.data.db.entity.CategoryEntity
import app.brain.data.db.entity.CategoryPreferenceEntity
import app.brain.data.db.entity.CategorySuggestionEntity
import app.brain.data.db.entity.CommentEntity
import app.brain.data.db.entity.DraftEntity
import app.brain.data.db.entity.EmbeddingEntity
import app.brain.data.db.entity.RecordCategoryEntity
import app.brain.data.db.entity.RecordEntity
import app.brain.data.db.entity.RecordLinkEntity
import app.brain.data.db.entity.TopicHintEntity

@Database(
    entities = [
        RecordEntity::class,
        DraftEntity::class,
        CommentEntity::class,
        CategoryEntity::class,
        CategoryPreferenceEntity::class,
        CategorySuggestionEntity::class,
        RecordCategoryEntity::class,
        AiFeedbackEntity::class,
        RecordLinkEntity::class,
        AiJobEntity::class,
        EmbeddingEntity::class,
        TopicHintEntity::class,
        AnalysisSessionEntity::class,
        AnalysisMessageEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class BrainDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun draftDao(): DraftDao
    abstract fun commentDao(): CommentDao
    abstract fun categoryDao(): CategoryDao
    abstract fun categoryPreferenceDao(): CategoryPreferenceDao
    abstract fun categorySuggestionDao(): CategorySuggestionDao
    abstract fun recordCategoryDao(): RecordCategoryDao
    abstract fun aiFeedbackDao(): AiFeedbackDao
    abstract fun recordLinkDao(): RecordLinkDao
    abstract fun aiJobDao(): AiJobDao
    abstract fun embeddingDao(): EmbeddingDao
    abstract fun topicHintDao(): TopicHintDao
    abstract fun analysisSessionDao(): AnalysisSessionDao
    abstract fun analysisMessageDao(): AnalysisMessageDao

    companion object {
        const val DB_NAME = "brain.db"

        /** v1 → v2：草稿表增加可选的标题字段 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SQLiteConnection) {
                val statement = db.prepare("ALTER TABLE drafts ADD COLUMN title TEXT")
                try {
                    (statement as BundledSQLiteStatement).step()
                } finally {
                    statement.close()
                }
            }
        }

        /** v2 → v3：分类偏好规则与历史分类调整建议 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SQLiteConnection) {
                val sql = listOf(
                    """
                    CREATE TABLE IF NOT EXISTS `category_preferences` (
                      `id` TEXT NOT NULL,
                      `dimension` TEXT NOT NULL,
                      `from_name` TEXT NOT NULL,
                      `to_name` TEXT NOT NULL,
                      `to_category_id` TEXT,
                      `count` INTEGER NOT NULL,
                      `updated_at` INTEGER NOT NULL,
                      PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                    """
                    CREATE TABLE IF NOT EXISTS `category_suggestions` (
                      `id` TEXT NOT NULL,
                      `record_id` TEXT NOT NULL,
                      `from_category_id` TEXT NOT NULL,
                      `to_category_id` TEXT NOT NULL,
                      `reason` TEXT NOT NULL,
                      `status` TEXT NOT NULL,
                      `created_at` INTEGER NOT NULL,
                      PRIMARY KEY(`id`),
                      FOREIGN KEY(`record_id`) REFERENCES `records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                    "CREATE INDEX IF NOT EXISTS `index_category_suggestions_record_id` ON `category_suggestions` (`record_id`)",
                    "CREATE INDEX IF NOT EXISTS `index_category_suggestions_status` ON `category_suggestions` (`status`)",
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_category_preferences_dimension_from_name_to_name` ON `category_preferences` (`dimension`, `from_name`, `to_name`)",
                )
                for (statement in sql) {
                    val prepared = db.prepare(statement)
                    try {
                        (prepared as BundledSQLiteStatement).step()
                    } finally {
                        prepared.close()
                    }
                }
            }
        }

        /** v3 → v4：重复主题提示、深度分析会话与消息 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SQLiteConnection) {
                val sql = listOf(
                    """
                    CREATE TABLE IF NOT EXISTS `topic_hints` (
                      `id` TEXT NOT NULL,
                      `topic` TEXT NOT NULL,
                      `category_id` TEXT,
                      `count` INTEGER NOT NULL,
                      `window_start` INTEGER NOT NULL,
                      `window_end` INTEGER NOT NULL,
                      `status` TEXT NOT NULL,
                      `created_at` INTEGER NOT NULL,
                      PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                    "CREATE INDEX IF NOT EXISTS `index_topic_hints_status` ON `topic_hints` (`status`)",
                    """
                    CREATE TABLE IF NOT EXISTS `analysis_sessions` (
                      `id` TEXT NOT NULL,
                      `title` TEXT NOT NULL,
                      `scope_json` TEXT NOT NULL,
                      `created_at` INTEGER NOT NULL,
                      `updated_at` INTEGER NOT NULL,
                      PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                    """
                    CREATE TABLE IF NOT EXISTS `analysis_messages` (
                      `id` TEXT NOT NULL,
                      `session_id` TEXT NOT NULL,
                      `role` TEXT NOT NULL,
                      `content` TEXT NOT NULL,
                      `citations_json` TEXT,
                      `created_at` INTEGER NOT NULL,
                      PRIMARY KEY(`id`),
                      FOREIGN KEY(`session_id`) REFERENCES `analysis_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                    "CREATE INDEX IF NOT EXISTS `index_analysis_messages_session_id` ON `analysis_messages` (`session_id`)",
                )
                for (statement in sql) {
                    val prepared = db.prepare(statement)
                    try {
                        (prepared as BundledSQLiteStatement).step()
                    } finally {
                        prepared.close()
                    }
                }
            }
        }

        /** v4 → v5：内容类型字段改名（观点+分析/反思合并为分析观点），收容所卡片以内容类型为主 */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SQLiteConnection) {
                val renames = listOf(
                    "灵感/念头" to "灵感想法",
                    "观点" to "分析观点",
                    "感受/情绪" to "感受",
                    "分析/反思" to "分析观点",
                    "文章/散文" to "文章",
                    "碎碎念/吐槽" to "碎碎念",
                )
                for ((old, new) in renames) {
                    val oldId = queryText(db, "SELECT id FROM categories WHERE dimension = 'type' AND name = ? LIMIT 1", old)
                        ?: continue
                    if (old == new) continue
                    val existingId = queryText(db, "SELECT id FROM categories WHERE dimension = 'type' AND name = ? LIMIT 1", new)
                    if (existingId == null || existingId == oldId) {
                        execMigration(db, "UPDATE categories SET name = ? WHERE id = ?", new, oldId)
                    } else {
                        execMigration(db, "UPDATE record_categories SET category_id = ? WHERE category_id = ?", existingId, oldId)
                        execMigration(db, "DELETE FROM categories WHERE id = ?", oldId)
                    }
                }
                // 刷新 FTS 里的分类名缓存（表不存在时跳过）
                try {
                    execMigration(
                        db,
                        """
                        UPDATE records_fts SET category_names = (
                            SELECT group_concat(c.name, ' ') FROM record_categories rc
                            JOIN categories c ON c.id = rc.category_id
                            WHERE rc.record_id = records_fts.record_id
                        )
                        """.trimIndent()
                    )
                } catch (e: Exception) {
                    // FTS 表未创建，无需刷新
                }
            }
        }

        fun build(context: Context): BrainDatabase =
            Room.databaseBuilder(context, BrainDatabase::class.java, DB_NAME)
                .setDriver(BundledSQLiteDriver())
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()

        private fun execMigration(db: SQLiteConnection, sql: String, vararg args: String) {
            val statement = db.prepare(sql)
            try {
                (statement as BundledSQLiteStatement).apply {
                    args.forEachIndexed { index, value -> bindText(index + 1, value) }
                    step()
                }
            } finally {
                statement.close()
            }
        }

        private fun queryText(db: SQLiteConnection, sql: String, arg: String): String? {
            val statement = db.prepare(sql)
            try {
                (statement as BundledSQLiteStatement).apply {
                    bindText(1, arg)
                    if (step()) return getText(0)
                }
            } finally {
                statement.close()
            }
            return null
        }
    }
}