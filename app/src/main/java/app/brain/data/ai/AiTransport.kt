package app.brain.data.ai

/** AI 传输层：本轮实现为 App 直连 DeepSeek；将来可加服务器代理实现而不改动上层。 */
interface AiTransport {
    suspend fun organize(request: OrganizeRequest): OrganizeResult

    /** 自由对话（用于深度分析）；systemPrompt 与历史消息由调用方组装。 */
    suspend fun chat(systemPrompt: String, messages: List<ChatTurn>): String
}