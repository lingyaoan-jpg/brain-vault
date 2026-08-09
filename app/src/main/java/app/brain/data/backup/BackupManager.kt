package app.brain.data.backup

import android.content.ContentValues
import android.content.Context
import android.util.Log
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.brain.data.db.BrainDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class BackupResult(val fileName: String, val uri: Uri?, val sizeBytes: Long, val localPath: String? = null)

sealed class RestoreResult {
    object Success : RestoreResult()
    data class Error(val message: String) : RestoreResult()
}

/**
 * 完整本地备份与恢复：
 * - 备份用 VACUUM INTO 生成一致性单文件；本地自动保留最近 7 份，也可导出到用户选择的位置。
 * - 恢复前自动安全备份当前数据；恢复失败时回滚，绝不破坏现有可用数据。
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val backupDir: File get() = File(context.filesDir, "backups").apply { mkdirs() }

    suspend fun createBackup(): BackupResult = withContext(Dispatchers.IO) {
        val bytes = buildBackupBytes()
        val name = "脑内收容所备份-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".db"

        // 本地滚动保留 7 份
        val local = File(backupDir, name)
        local.writeBytes(bytes)
        pruneLocalBackups(KEEP_LOCAL)

        var uri: Uri? = null
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val inserted = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (inserted != null) {
                context.contentResolver.openOutputStream(inserted)?.use { it.write(bytes) }
                uri = inserted
            }
        }
        BackupResult(name, uri, bytes.size.toLong(), local.absolutePath)
    }

    /** 把备份文件导出到用户选择的位置（SAF 文档 URI）。 */
    suspend fun exportBackup(destUri: Uri): BackupResult = withContext(Dispatchers.IO) {
        val bytes = buildBackupBytes()
        val name = "脑内收容所备份-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".db"
        val ok = context.contentResolver.openOutputStream(destUri)?.use { it.write(bytes) } != null
        if (!ok) throw IllegalStateException("无法写入目标位置")
        BackupResult(name, destUri, bytes.size.toLong())
    }

    /** 本机滚动保留的备份列表（最新在前）。 */
    fun listLocalBackups(): List<BackupResult> =
        backupDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".db") }
            ?.sortedByDescending { it.lastModified() }
            ?.map { BackupResult(it.name, null, it.length(), it.absolutePath) }
            ?: emptyList()

    suspend fun restore(uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext RestoreResult.Error("无法读取备份文件")
            restoreInPlace(bytes)
        } catch (e: Exception) {
            RestoreResult.Error("恢复失败，已回滚到恢复前状态：${e.message}")
        }
    }

    /** 管理端使用：从本地备份文件字节直接恢复（含安全备份与失败回滚）。 */
    /** 管理端使用：从本地备份文件字节直接恢复（同样走事务回滚保护）。*/
    suspend fun restoreFromBytes(bytes: ByteArray): Boolean {
        return restoreInPlace(bytes) is RestoreResult.Success
    }

    private suspend fun restoreInPlace(bytes: ByteArray): RestoreResult = withContext(Dispatchers.IO) {
        if (bytes.size < 16 || String(bytes, 0, 15, Charsets.US_ASCII) != "SQLite format 3") {
            return@withContext RestoreResult.Error("不是有效的备份文件")
        }
        val src = File(context.cacheDir, "restore_src.db")
        val safety = File(context.cacheDir, "pre_restore_safety.db")
        val dbPath = context.getDatabasePath(BrainDatabase.DB_NAME).absolutePath
        try {
            src.writeBytes(bytes)
            val db = BundledSQLiteDriver().open(dbPath)
            try {
                // 恢复前先安全快照，供“撤销上次恢复”使用
                if (safety.exists()) safety.delete()
                db.prepare("VACUUM INTO '" + safety.absolutePath.replace("'", "''") + "'").use { it.step() }
                db.prepare("PRAGMA busy_timeout = 10000").use { it.step() }
                db.prepare("PRAGMA foreign_keys = OFF").use { it.step() }
                db.prepare("BEGIN").use { it.step() }
                try {
                    for (table in COPY_TABLES.asReversed()) {
                        db.prepare("DELETE FROM `" + table + "`").use { it.step() }
                    }
                    db.prepare("ATTACH DATABASE '" + src.absolutePath.replace("'", "''") + "' AS backup").use { it.step() }
                    for (table in COPY_TABLES) {
                        db.prepare("INSERT INTO main.`" + table + "` SELECT * FROM backup.`" + table + "`").use { it.step() }
                    }
                    db.prepare("COMMIT").use { it.step() }
                } catch (e: Exception) {
                    try { db.prepare("ROLLBACK").use { it.step() } } catch (_: Exception) {}
                    throw e
                }
            } finally {
                db.close()
            }
            RestoreResult.Success
        } catch (e: Exception) {
            Log.w(TAG, "restore failed", e)
            RestoreResult.Error("恢复失败，已回滚到恢复前状态：${e.message}")
        } finally {
            src.delete()
        }
    }

    /** 回滚到恢复前的安全副本。*/
    private suspend fun rollbackFromSafety(): RestoreResult = withContext(Dispatchers.IO) {
        val safety = File(context.cacheDir, "pre_restore_safety.db")
        if (!safety.exists()) return@withContext RestoreResult.Error("没有可回滚的安全副本")
        try {
            restoreInPlace(safety.readBytes())
        } catch (e: Exception) {
            RestoreResult.Error("回滚失败：${e.message}")
        }
    }

    /** 用户手动撤销上一次恢复。 */
    suspend fun rollbackLastRestore(): RestoreResult {
        val result = rollbackFromSafety()
        if (result is RestoreResult.Success) {
            File(context.cacheDir, "pre_restore_safety.db").delete()
        }
        return result
    }

    private fun buildBackupBytes(): ByteArray {
        val dbPath = context.getDatabasePath(BrainDatabase.DB_NAME).absolutePath
        val tmp = File(context.cacheDir, "backup_tmp.db")
        val conn = BundledSQLiteDriver().open(dbPath)
        try {
            conn.prepare("VACUUM INTO '" + tmp.absolutePath.replace("'", "''") + "'").use { it.step() }
        } finally {
            conn.close()
        }
        val bytes = tmp.readBytes()
        tmp.delete()
        return bytes
    }

    private fun pruneLocalBackups(keep: Int) {
        val files = backupDir.listFiles()?.filter { it.isFile && it.name.endsWith(".db") }
            ?.sortedByDescending { it.lastModified() } ?: return
        files.drop(keep).forEach { it.delete() }
    }

    companion object {
        private const val KEEP_LOCAL = 7
        private const val TAG = "BackupManager"

        /** 需要完整复制的数据表（FTS 由触发器自动维护）。*/
        private val COPY_TABLES = listOf(
            "records", "comments", "categories", "record_categories",
            "drafts", "category_preferences", "category_suggestions",
            "ai_feedback", "record_links", "ai_jobs", "embeddings",
            "topic_hints", "analysis_sessions", "analysis_messages",
        )
    }
}