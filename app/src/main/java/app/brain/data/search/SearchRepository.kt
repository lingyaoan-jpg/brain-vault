package app.brain.data.search

import android.content.Context
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteStatement
import app.brain.data.db.BrainDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FTS5 关键词搜索。records_fts 虚拟表由 DatabaseInitializer 手工创建，
 * Room 编译期无法感知，因此这里用独立 BundledSQLite 连接查询，返回命中的 record id。
 */
@Singleton
class SearchRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun searchRecordIds(rawTerm: String): List<String> = withContext(Dispatchers.IO) {
        val path = context.getDatabasePath(BrainDatabase.DB_NAME).absolutePath
        val connection = BundledSQLiteDriver().open(path)
        try {
            val query = "\"" + rawTerm.replace("\"", "\"\"") + "\""
            val statement = connection.prepare(
                "SELECT record_id FROM records_fts WHERE records_fts MATCH ? ORDER BY rowid DESC"
            )
            try {
                (statement as BundledSQLiteStatement).bindText(1, query)
                val ids = mutableListOf<String>()
                while (statement.step()) {
                    ids.add(statement.getText(0))
                }
                ids
            } finally {
                statement.close()
            }
        } finally {
            connection.close()
        }
    }

    companion object {
        fun escapeFts(input: String): String = "\"" + input.replace("\"", "\"\"") + "\""
    }
}