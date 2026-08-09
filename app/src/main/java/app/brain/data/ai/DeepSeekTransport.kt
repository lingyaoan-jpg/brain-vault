package app.brain.data.ai

import app.brain.data.db.entity.CategoryEntity
import app.brain.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App 直连 DeepSeek 的实现。密钥来自设置页（Keystore 加密存储），
 * 不写死在代码或安装包里。只发送当前记录原文、分类清单和用户偏好。
 */
@Singleton
class DeepSeekTransport @Inject constructor(
    private val settings: SettingsRepository,
) : AiTransport {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun organize(request: OrganizeRequest): OrganizeResult = withContext(Dispatchers.IO) {
        val key = settings.apiKey
        if (key.isBlank()) throw AiApiException("未配置 API 密钥，请先在设置页填写。")

        val payload = DeepSeekChatRequest(
            model = settings.model,
            messages = listOf(
                DeepSeekMessage("system", buildSystemPrompt(request.categories, request.preferences)),
                DeepSeekMessage("user", buildUserPrompt(request)),
            ),
            temperature = 0.2,
            responseFormat = DeepSeekResponseFormat("json_object"),
        )

        val content = postChat(payload, key)
        parseOrganizeResult(content)
    }

    override suspend fun chat(systemPrompt: String, messages: List<ChatTurn>): String = withContext(Dispatchers.IO) {
        val key = settings.apiKey
        if (key.isBlank()) throw AiApiException("未配置 API 密钥，请先在设置页填写。")

        val payload = DeepSeekChatRequest(
            model = settings.model,
            messages = listOf(DeepSeekMessage("system", systemPrompt)) +
                messages.map { DeepSeekMessage(it.role, it.content) },
            temperature = 0.7,
        )
        postChat(payload, key)
    }

    private fun postChat(payload: DeepSeekChatRequest, key: String): String {
        val body = json.encodeToString(DeepSeekChatRequest.serializer(), payload)
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val httpRequest = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .post(body)
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw AiApiException("HTTP ${response.code}：${text.take(200)}")
            }
            val chatResponse = try {
                json.decodeFromString(DeepSeekChatResponse.serializer(), text)
            } catch (e: Exception) {
                throw AiApiException("响应解析失败：${e.message}")
            }
            return chatResponse.choices.firstOrNull()?.message?.content
                ?: throw AiApiException("AI 返回了空响应")
        }
    }

    private fun buildSystemPrompt(categories: List<CategoryInfo>, preferences: List<CategoryPreferenceInfo>): String {
        val grouped = categories.groupBy { it.dimension }
        fun section(label: String, dimension: String) = buildString {
            append(label).append("：")
            append(grouped[dimension].orEmpty().joinToString("、") { it.name })
        }
        return """
你是"脑内收容所"的自动整理助手。你只负责为一条原始记录生成整理信息，绝不改写、合并或总结替换原文。
以下是可用分类（维度：名称）：
${section("内容类型", CategoryEntity.DIM_TYPE)}
${section("人生课题", CategoryEntity.DIM_TOPIC)}
${section("紧急程度", CategoryEntity.DIM_URGENCY)}
${section("重要程度", CategoryEntity.DIM_IMPORTANCE)}
${section("情绪", CategoryEntity.DIM_EMOTION)}
规则：
1. title：生成不超过 20 字的短标题，概括记录主题；原文已有明确标题时可沿用。
2. types：只选 1 个"内容类型"，这是收容所卡片的主分类，必须选择；优先复用上面已有的名称，现有类型都不合适且确实属于新的内容形式时，可给出 1 个简短新类型名（2-6 字）；但"散步""会议""朋友"这类日常场景词必须归入现有类型，不得新建。
3. topics：从"人生课题"选 0-2 个，优先复用已有名称；内容明显属于新课题且现有都不合适时，可给出一个新课题的简短名称。
4. urgency：从"紧急程度"选 1 个；没有时间含义时选"无时限"。
5. importance：从"重要程度"选 1 个。
6. emotions：只在文字明确表达情绪时选 0-3 个，否则留空数组。
7. 不要生成任何自由关键词标签（如"散步""会议""朋友"这类日常词），分类以内容类型为主，结合人生课题、紧急程度、重要程度和情绪。
${preferenceSection(preferences)}
只输出一个 JSON 对象，不要输出任何其他文字。格式：
{"title":"...","types":["..."],"topics":["..."],"urgency":"...","importance":"...","emotions":["..."]}
""".trimIndent()
    }

    private fun preferenceSection(preferences: List<CategoryPreferenceInfo>): String {
        if (preferences.isEmpty()) return ""
        val lines = preferences.joinToString("\n") { "    - ${it.from} -> ${it.to}（${it.count} 次）" }
        val first = preferences.first()
        return """
用户的历史修改习惯（AI 原分类 -> 用户改为的分类，次数）：
${lines}
新记录分类请优先遵循这些习惯：例如用户多次把「${first.from}」改为「${first.to}」，说明用户更认可「${first.to}」这一分类；遇到同类内容时直接选用用户认可的分类，不再给出「${first.from}」。
""".trimIndent()
    }

    private fun buildUserPrompt(request: OrganizeRequest): String = buildString {
        append("原文：\n").append(request.content)
        request.title?.takeIf { it.isNotBlank() }?.let { append("\n\n已有标题：").append(it) }
    }

    private fun parseOrganizeResult(content: String): OrganizeResult {
        val fence = "\u0060\u0060\u0060"
        val cleaned = content
            .trim()
            .removePrefix(fence + "json")
            .removePrefix(fence)
            .removeSuffix(fence)
            .removePrefix("~~~json")
            .removePrefix("~~~")
            .removeSuffix("~~~")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) throw AiApiException("AI 未返回 JSON 结果")
        val raw = cleaned.substring(start, end + 1)
        return try {
            json.decodeFromString(OrganizeResult.serializer(), raw)
        } catch (e: Exception) {
            throw AiApiException("结果解析失败：${e.message}")
        }
    }
}