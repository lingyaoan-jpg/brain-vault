package app.brain.data.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 发送给整理 AI 的请求内容（仅当前记录 + 分类清单）。 */
@Serializable
data class OrganizeRequest(
    val content: String,
    val title: String? = null,
    val categories: List<CategoryInfo> = emptyList(),
    val preferences: List<CategoryPreferenceInfo> = emptyList(),
)

@Serializable
data class CategoryInfo(
    val id: String,
    val dimension: String,
    val name: String,
)

/** 个性化偏好：用户曾把分类 from 改为 to（count 次）。 */
@Serializable
data class CategoryPreferenceInfo(
    val dimension: String,
    val from: String,
    val to: String,
    val count: Int,
)

/** AI 整理结果：分类返回名称（非 id），处理器负责映射或新建。 */
@Serializable
data class OrganizeResult(
    val title: String? = null,
    val types: List<String> = emptyList(),
    val topics: List<String> = emptyList(),
    val urgency: String? = null,
    val importance: String? = null,
    val emotions: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)

/** 深度分析对话中的一轮消息。 */
data class ChatTurn(
    val role: String,
    val content: String,
) {
    companion object {
        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}

/** DeepSeek chat completions 请求。 */
@Serializable
data class DeepSeekChatRequest(
    val model: String,
    val messages: List<DeepSeekMessage>,
    val temperature: Double = 0.2,
    @SerialName("response_format") val responseFormat: DeepSeekResponseFormat = DeepSeekResponseFormat("text"),
)

@Serializable
data class DeepSeekMessage(
    val role: String,
    val content: String,
)

@Serializable
data class DeepSeekResponseFormat(
    val type: String,
)

@Serializable
data class DeepSeekChatResponse(
    val choices: List<DeepSeekChoice> = emptyList(),
)

@Serializable
data class DeepSeekChoice(
    val message: DeepSeekMessage,
)

/** API 调用失败。 */
class AiApiException(message: String) : Exception(message)