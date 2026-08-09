package app.brain.data.admin

import android.content.Context
import android.util.Log
import app.brain.data.ai.AiQueueProcessor
import app.brain.data.backup.BackupManager
import app.brain.data.db.dao.AiJobDao
import app.brain.data.db.dao.CategoryDao
import app.brain.data.db.dao.RecordCategoryDao
import app.brain.data.db.dao.RecordDao
import app.brain.data.db.entity.AiJobEntity
import app.brain.data.db.entity.CategoryEntity
import app.brain.data.db.entity.RecordCategoryEntity
import app.brain.data.db.entity.RecordEntity
import app.brain.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 简易电脑管理端：手机内嵌轻量 Web 服务器，仅局域网访问。
 * 登录使用用户设置的密码（随机令牌会话）；功能：查看/搜索、批量导入、批量修改、导出、备份与恢复。
 * 与手机共用同一个本地数据库，数据天然一致，恢复走安全备份，不会静默覆盖。
 */
@Singleton
class AdminServer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val recordDao: RecordDao,
    private val recordCategoryDao: RecordCategoryDao,
    private val categoryDao: CategoryDao,
    private val aiJobDao: AiJobDao,
    private val aiQueueProcessor: AiQueueProcessor,
    private val backupManager: BackupManager,
) {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var server: NanoHTTPD? = null

    @Volatile
    private var activeTokens: Set<String> = emptySet()

    val isRunning: Boolean get() = server?.isAlive == true

    fun start(port: Int, password: String) {
        settings.adminPort = port
        settings.adminPassword = password
        stop()
        val handler = Handler(port, password)
        try {
            handler.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            server = handler
            Log.i(TAG, "admin server started on $port")
        } catch (e: Exception) {
            Log.w(TAG, "admin server start failed", e)
            throw e
        }
    }

    fun stop() {
        try {
            server?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "admin server stop failed", e)
        }
        server = null
        activeTokens = emptySet()
    }

    fun localAddress(): String {
        val port = settings.adminPort
        return "http://${localIpV4()}:$port"
    }

    private fun localIpV4(): String {
        try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().forEach { nif ->
                if (!nif.isUp || nif.isLoopback) return@forEach
                nif.inetAddresses.toList().forEach { addr ->
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        return addr.hostAddress ?: ""
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "get ip failed", e)
        }
        return "127.0.0.1"
    }

    private fun serveAdminPage(): NanoHTTPD.Response {
        val bytes = runCatching {
            context.assets.open(ASSET_ADMIN_HTML).use { it.readBytes() }
        }.getOrNull()
        val page = if (bytes == null) {
            "<html><body><h1>脑内收容所管理端</h1><p>页面资源缺失</p></body></html>".toByteArray()
        } else {
            bytes
        }
        return NanoHTTPD.newFixedLengthResponse(Status.OK, "text/html; charset=utf-8", java.io.ByteArrayInputStream(page), page.size.toLong())
    }

    private inner class Handler(private val port: Int, private val password: String) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): NanoHTTPD.Response {
            return try {
                route(session)
            } catch (e: Exception) {
                Log.w(TAG, "admin route error: ${session.uri}", e)
                jsonError("服务器错误：${e.message}")
            }
        }

        private fun route(session: IHTTPSession): NanoHTTPD.Response {
            val uri = session.uri.trimEnd('/')
            val method = session.method
            return when {
                method == Method.GET && uri.isEmpty() -> serveAdminPage()
                method == Method.POST && uri == "/api/login" -> login(session)
                method == Method.POST && uri == "/api/logout" -> logout(session)
                method == Method.GET && uri == "/api/status" -> if (authorized(session)) status() else unauthorized()
                method == Method.GET && uri == "/api/records" -> if (authorized(session)) records(session) else unauthorized()
                method == Method.POST && uri == "/api/import" -> if (authorized(session)) importRecords(session) else unauthorized()
                method == Method.POST && uri == "/api/batch" -> if (authorized(session)) batchModify(session) else unauthorized()
                method == Method.GET && uri == "/api/export" -> if (authorized(session)) exportRecords(session) else unauthorized()
                method == Method.POST && uri == "/api/backup/create" -> if (authorized(session)) createBackup() else unauthorized()
                method == Method.GET && uri == "/api/backups" -> if (authorized(session)) listBackups() else unauthorized()
                method == Method.GET && uri == "/api/backup/download" -> if (authorized(session)) downloadBackup(session) else unauthorized()
                method == Method.POST && uri == "/api/backup/restore" -> if (authorized(session)) restoreBackup(session) else unauthorized()
                else -> jsonError("接口不存在：$method $uri", Status.NOT_FOUND)
            }
        }

        private fun authorized(session: IHTTPSession): Boolean {
            val queryToken = session.parms["token"]
            val header = session.headers["authorization"]
            val bearer = header?.removePrefix("Bearer ")?.trim()
            val token = queryToken ?: bearer ?: return false
            return token in activeTokens
        }

        private fun login(session: IHTTPSession): NanoHTTPD.Response {
            val req = parseBody<LoginRequest>(session) ?: return jsonError("参数错误")
            if (req.password.isBlank() || req.password != password) {
                return jsonError("密码错误", Status.FORBIDDEN)
            }
            val token = UUID.randomUUID().toString().replace("-", "")
            activeTokens = activeTokens + token
            return json(mapOf("token" to token, "address" to localAddress()))
        }

        private fun logout(session: IHTTPSession): NanoHTTPD.Response {
            val token = session.parms["token"]
            if (token != null) activeTokens = activeTokens - token
            return json(mapOf("ok" to true))
        }

        private fun status(): NanoHTTPD.Response = runBlocking {
            val recordCount = recordDao.observeActiveCount().first()
            val backups = backupManager.listLocalBackups().size
            json(
                mapOf(
                    "running" to true,
                    "port" to port,
                    "recordCount" to recordCount,
                    "backupCount" to backups,
                )
            )
        }

        private fun records(session: IHTTPSession): NanoHTTPD.Response = runBlocking {
            withContext(Dispatchers.IO) {
                val q = session.parms["q"]?.trim().orEmpty()
                val dimension = session.parms["dimension"]
                val categoryId = session.parms["categoryId"]
                val base = if (q.isNotEmpty()) {
                    recordDao.searchSubstring(q).first()
                } else {
                    recordDao.observeActiveOrdered().first()
                }
                val links = recordCategoryDao.getAllWithCategories().groupBy { it.recordId }
                val filtered = base.filter { rec ->
                    val cats = links[rec.id].orEmpty()
                    (dimension == null || cats.any { it.dimension == dimension }) &&
                        (categoryId == null || cats.any { it.categoryId == categoryId })
                }
                val apiRecords = filtered.map { rec ->
                    ApiRecord(
                        id = rec.id,
                        title = rec.title ?: "",
                        content = rec.content,
                        createdAt = rec.createdAt,
                        isFavorite = rec.isFavorite,
                        isPinned = rec.isPinned,
                        categories = links[rec.id].orEmpty().map { ApiCategory(it.dimension, it.name) },
                    )
                }
                json(mapOf("records" to apiRecords))
            }
        }

        private fun importRecords(session: IHTTPSession): NanoHTTPD.Response {
            val req = parseBody<ImportRequest>(session) ?: return jsonError("参数错误")
            val items = req.text
                .replace("\r\n", "\n")
                .split(Regex("\n\\s*\n+"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .take(200)
            if (items.isEmpty()) return json(mapOf("imported" to 0))
            runBlocking {
                withContext(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    val jobs = mutableListOf<AiJobEntity>()
                    for (content in items) {
                        val id = UUID.randomUUID().toString()
                        recordDao.insert(
                            RecordEntity(
                                id = id,
                                content = content,
                                titleSource = RecordEntity.TITLE_SOURCE_AI,
                                createdAt = now,
                                updatedAt = now,
                                status = RecordEntity.STATUS_PENDING,
                            )
                        )
                        jobs += AiJobEntity(
                            id = UUID.randomUUID().toString(),
                            recordId = id,
                            jobType = AiJobEntity.JOB_TYPE_ORGANIZE,
                            status = AiJobEntity.STATUS_QUEUED,
                            createdAt = now,
                            updatedAt = now,
                        )
                    }
                    aiJobDao.insertAll(jobs)
                }
            }
            aiQueueProcessor.kick()
            return json(mapOf("imported" to items.size))
        }

        private fun batchModify(session: IHTTPSession): NanoHTTPD.Response {
            val req = parseBody<BatchRequest>(session) ?: return jsonError("参数错误")
            if (req.ids.isEmpty()) return jsonError("未选择记录")
            runBlocking {
                withContext(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    req.title?.trim()?.takeIf { it.isNotEmpty() }?.let { title ->
                        recordDao.batchSetTitle(req.ids, title.take(40), now)
                    }
                    req.createdAt?.let { time ->
                        recordDao.batchSetTime(req.ids, time, now)
                    }
                    if (!req.dimension.isNullOrBlank() && !req.value.isNullOrBlank()) {
                        val clean = req.value.trim().take(20)
                        val existing = categoryDao.getByName(req.dimension, clean)
                        val category = existing ?: CategoryEntity(
                            id = "user_${req.dimension}_${UUID.randomUUID().toString().take(8)}",
                            dimension = req.dimension,
                            name = clean,
                            createdBy = CategoryEntity.CREATED_BY_USER,
                        ).also { categoryDao.insert(it) }
                        recordCategoryDao.removeDimensionForRecords(req.ids, req.dimension)
                        recordCategoryDao.insertAll(
                            req.ids.map {
                                RecordCategoryEntity(
                                    recordId = it,
                                    categoryId = category.id,
                                    source = RecordCategoryEntity.SOURCE_USER,
                                    createdAt = now,
                                )
                            }
                        )
                    }
                }
            }
            return json(mapOf("updated" to req.ids.size))
        }

        private fun exportRecords(session: IHTTPSession): NanoHTTPD.Response = runBlocking {
            val ids = session.parms["ids"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            val records = if (ids.isEmpty()) {
                recordDao.observeActiveOrdered().first()
            } else {
                recordDao.getActiveByIds(ids)
            }
            val links = recordCategoryDao.getAllWithCategories().groupBy { it.recordId }
            val sb = StringBuilder()
            records.forEachIndexed { index, rec ->
                if (index > 0) sb.append("\n\n==========\n\n")
                rec.title?.takeIf { it.isNotBlank() }?.let { sb.append(it).append("\n\n") }
                val cats = links[rec.id].orEmpty()
                if (cats.isNotEmpty()) {
                    val dims = LinkedHashMap<String, MutableList<String>>()
                    cats.forEach { dims.getOrPut(it.dimension) { mutableListOf() }.add(it.name) }
                    sb.append(dims.map { (d, names) -> "${d}：${names.joinToString("、")}" }.joinToString("；")).append("\n\n")
                }
                sb.append(rec.content)
            }
            val bytes = sb.toString().toByteArray(Charsets.UTF_8)
            NanoHTTPD.newFixedLengthResponse(
                Status.OK,
                "text/plain; charset=utf-8",
                java.io.ByteArrayInputStream(bytes),
                bytes.size.toLong(),
            )
        }

        private fun listBackups(): NanoHTTPD.Response {
            val backups = backupManager.listLocalBackups().map { mapOf("fileName" to it.fileName, "sizeBytes" to it.sizeBytes) }
            return json(mapOf("backups" to backups))
        }

        private fun createBackup(): NanoHTTPD.Response = runBlocking {
            val result = backupManager.createBackup()
            json(mapOf("fileName" to result.fileName, "sizeBytes" to result.sizeBytes))
        }

        private fun downloadBackup(session: IHTTPSession): NanoHTTPD.Response {
            val name = session.parms["file"] ?: return jsonError("缺少文件名")
            val file = java.io.File(context.filesDir, "backups").resolve(name)
            if (!file.exists() || !file.canonicalPath.startsWith(java.io.File(context.filesDir, "backups").canonicalPath)) {
                return jsonError("备份不存在", Status.NOT_FOUND)
            }
            val bytes = file.readBytes()
            return NanoHTTPD.newFixedLengthResponse(
                Status.OK,
                "application/octet-stream",
                java.io.ByteArrayInputStream(bytes),
                bytes.size.toLong(),
            )
        }

        private fun restoreBackup(session: IHTTPSession): NanoHTTPD.Response {
            val req = parseBody<RestoreRequest>(session) ?: return jsonError("参数错误")
            val file = java.io.File(context.filesDir, "backups").resolve(req.fileName)
            if (!file.exists() || !file.canonicalPath.startsWith(java.io.File(context.filesDir, "backups").canonicalPath)) {
                return jsonError("备份不存在", Status.NOT_FOUND)
            }
            val bytes = file.readBytes()
            val result = runBlocking { backupManager.restoreFromBytes(bytes) }
            return if (result) json(mapOf("ok" to true)) else jsonError("恢复失败")
        }

        // ---------- helpers ----------

        private inline fun <reified T> parseBody(session: IHTTPSession): T? {
            return try {
                val length = session.headers["content-length"]?.toIntOrNull()
                val raw = if (length != null && length > 0) {
                    val buf = ByteArray(length)
                    var off = 0
                    while (off < length) {
                        val r = session.inputStream.read(buf, off, length - off)
                        if (r < 0) break
                        off += r
                    }
                    String(buf, 0, off, Charsets.UTF_8)
                } else {
                    null
                }
                if (raw.isNullOrBlank()) null else json.decodeFromString<T>(raw)
            } catch (e: Exception) {
                Log.w(TAG, "parse body failed", e)
                null
            }
        }

        private fun json(obj: Any): NanoHTTPD.Response =
            NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json; charset=utf-8", json.stringifySafe(obj))

        private fun jsonError(message: String, status: Status = Status.BAD_REQUEST): NanoHTTPD.Response =
            NanoHTTPD.newFixedLengthResponse(
                status,
                "application/json; charset=utf-8",
                """{"error":"${message.replace("\"", "'")}"}""",
            )

        private fun unauthorized(): NanoHTTPD.Response = jsonError("未登录或会话已过期", Status.FORBIDDEN)
    }

    companion object {
        private const val TAG = "AdminServer"
        private const val ASSET_ADMIN_HTML = "admin.html"
    }
}

@Serializable
private data class LoginRequest(val password: String = "")

@Serializable
private data class ImportRequest(val text: String = "")

@Serializable
private data class BatchRequest(
    val ids: List<String> = emptyList(),
    val title: String? = null,
    val createdAt: Long? = null,
    val dimension: String? = null,
    val value: String? = null,
)

@Serializable
private data class RestoreRequest(val fileName: String = "")

@Serializable
private data class ApiCategory(val dimension: String, val name: String)

@Serializable
private data class ApiRecord(
    val id: String,
    val title: String,
    val content: String,
    @SerialName("createdAt") val createdAt: Long,
    @SerialName("isFavorite") val isFavorite: Boolean,
    @SerialName("isPinned") val isPinned: Boolean,
    val categories: List<ApiCategory>,
)

private fun Json.stringifySafe(value: Any): String = when (value) {
    is Map<*, *> -> {
        val sb = StringBuilder("{")
        var first = true
        for ((k, v) in value) {
            if (!first) sb.append(",")
            first = false
            sb.append("\"").append(k).append("\":").append(encodeValue(v))
        }
        sb.append("}")
        sb.toString()
    }
    is List<*> -> value.joinToString(prefix = "[", postfix = "]") { encodeValue(it) }
    else -> encodeValue(value)
}

private fun encodeValue(value: Any?): String = when (value) {
    null -> "null"
    is String -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
    is Boolean -> value.toString()
    is Int, is Long -> value.toString()
    is List<*> -> value.joinToString(prefix = "[", postfix = "]") { encodeValue(it) }
    is ApiRecord -> {
        val sb = StringBuilder("{")
        sb.append("\"id\":\"").append(value.id).append("\",")
        sb.append("\"title\":").append(encodeValue(value.title)).append(",")
        sb.append("\"content\":").append(encodeValue(value.content)).append(",")
        sb.append("\"createdAt\":").append(value.createdAt).append(",")
        sb.append("\"isFavorite\":").append(value.isFavorite).append(",")
        sb.append("\"isPinned\":").append(value.isPinned).append(",")
        sb.append("\"categories\":").append(value.categories.joinToString(prefix = "[", postfix = "]") {
            "{\"dimension\":${encodeValue(it.dimension)},\"name\":${encodeValue(it.name)}}"
        })
        sb.append("}")
        sb.toString()
    }
    else -> "\"" + value.toString().replace("\"", "\\\"") + "\""
}