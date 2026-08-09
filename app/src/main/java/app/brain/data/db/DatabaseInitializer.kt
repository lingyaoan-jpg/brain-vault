package app.brain.data.db

import android.content.Context
import android.util.Log
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteStatement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DatabaseInitializer {
    private const val TAG = "BrainDb"

    /**
     * 幂等初始化：确保 FTS 索引与种子分类存在。
     * 先触发 Room 建表（惰性打开），再通过独立 BundledSQLite 连接执行 DDL。
     */
    suspend fun initialize(context: Context, database: BrainDatabase) = withContext(Dispatchers.IO) {
        // 1) 触发 Room 打开数据库并创建表结构
        database.categoryDao().getAllActive()

        // 2) 独立连接创建 FTS 与种子数据
        val path = context.getDatabasePath(BrainDatabase.DB_NAME).absolutePath
        val connection = BundledSQLiteDriver().open(path)
        try {
            ensureFts(connection)
            seedCategoriesIfEmpty(connection)
        } finally {
            connection.close()
        }
    }

    private fun ensureFts(connection: SQLiteConnection) {
        val hasFts = queryLong(
            connection,
            "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'records_fts'"
        ) > 0
        if (hasFts) return

        Log.i(TAG, "creating records_fts")
        try {
            exec(connection, FTS_SQL_TRIGRAM)
        } catch (e: Exception) {
            Log.w(TAG, "trigram tokenizer unavailable, falling back to unicode61", e)
            exec(connection, FTS_SQL_UNICODE61)
        }
        FTS_TRIGGERS.forEach { exec(connection, it) }
    }

    private fun seedCategoriesIfEmpty(connection: SQLiteConnection) {
        val count = queryLong(connection, "SELECT COUNT(*) FROM categories")
        if (count > 0) return

        Log.i(TAG, "seeding ${SeedCategories.all.size} categories")
        SeedCategories.all.forEachIndexed { index, seed ->
            val statement = connection.prepare(
                "INSERT OR IGNORE INTO categories (id, dimension, name, created_by, is_active, sort_order) VALUES (?, ?, ?, 'seed', 1, ?)"
            )
            try {
                (statement as BundledSQLiteStatement).apply {
                    bindText(1, SeedCategories.seedId(seed.dimension, index))
                    bindText(2, seed.dimension)
                    bindText(3, seed.name)
                    bindLong(4, seed.sortOrder.toLong())
                    step()
                }
            } finally {
                statement.close()
            }
        }
    }

    private fun exec(connection: SQLiteConnection, sql: String) {
        val statement = connection.prepare(sql)
        try {
            (statement as BundledSQLiteStatement).step()
        } finally {
            statement.close()
        }
    }

    private fun queryLong(connection: SQLiteConnection, sql: String): Long {
        val statement = connection.prepare(sql)
        try {
            val s = statement as BundledSQLiteStatement
            return if (s.step()) s.getLong(0) else 0L
        } finally {
            statement.close()
        }
    }

    private const val FTS_COLUMNS = "record_id UNINDEXED, content, title, comments, category_names"

    private const val FTS_SQL_TRIGRAM =
        "CREATE VIRTUAL TABLE IF NOT EXISTS records_fts USING fts5($FTS_COLUMNS, tokenize = 'trigram')"

    private const val FTS_SQL_UNICODE61 =
        "CREATE VIRTUAL TABLE IF NOT EXISTS records_fts USING fts5($FTS_COLUMNS, tokenize = 'unicode61')"

    private val FTS_TRIGGERS = listOf(
        """
        CREATE TRIGGER IF NOT EXISTS fts_records_insert AFTER INSERT ON records BEGIN
            INSERT INTO records_fts(record_id, content, title)
            VALUES (new.id, new.content, COALESCE(new.title, ''));
        END
        """.trimIndent(),
        """
        CREATE TRIGGER IF NOT EXISTS fts_records_update AFTER UPDATE ON records BEGIN
            UPDATE records_fts SET content = new.content, title = COALESCE(new.title, '')
            WHERE record_id = new.id;
        END
        """.trimIndent(),
        """
        CREATE TRIGGER IF NOT EXISTS fts_records_delete AFTER DELETE ON records BEGIN
            DELETE FROM records_fts WHERE record_id = old.id;
        END
        """.trimIndent(),
        """
        CREATE TRIGGER IF NOT EXISTS fts_comments_insert AFTER INSERT ON comments BEGIN
            UPDATE records_fts SET comments = (
                SELECT group_concat(content, ' ') FROM comments WHERE record_id = new.record_id
            ) WHERE record_id = new.record_id;
        END
        """.trimIndent(),
        """
        CREATE TRIGGER IF NOT EXISTS fts_comments_update AFTER UPDATE OF content ON comments BEGIN
            UPDATE records_fts SET comments = (
                SELECT group_concat(content, ' ') FROM comments WHERE record_id = new.record_id
            ) WHERE record_id = new.record_id;
        END
        """.trimIndent(),
        """
        CREATE TRIGGER IF NOT EXISTS fts_comments_delete AFTER DELETE ON comments BEGIN
            UPDATE records_fts SET comments = (
                SELECT group_concat(content, ' ') FROM comments WHERE record_id = old.record_id
            ) WHERE record_id = old.record_id;
        END
        """.trimIndent(),
        """
        CREATE TRIGGER IF NOT EXISTS fts_record_categories_insert AFTER INSERT ON record_categories BEGIN
            UPDATE records_fts SET category_names = (
                SELECT group_concat(c.name, ' ') FROM record_categories rc
                JOIN categories c ON c.id = rc.category_id
                WHERE rc.record_id = new.record_id
            ) WHERE record_id = new.record_id;
        END
        """.trimIndent(),
        """
        CREATE TRIGGER IF NOT EXISTS fts_record_categories_delete AFTER DELETE ON record_categories BEGIN
            UPDATE records_fts SET category_names = (
                SELECT group_concat(c.name, ' ') FROM record_categories rc
                JOIN categories c ON c.id = rc.category_id
                WHERE rc.record_id = old.record_id
            ) WHERE record_id = old.record_id;
        END
        """.trimIndent(),
    )
}