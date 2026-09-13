package io.legado.app.domain.gateway

import io.legado.app.domain.model.AiToolCall
import io.legado.app.domain.model.AiToolDefinition
import io.legado.app.domain.model.AiToolProgressCallback
import io.legado.app.domain.model.AiToolResult

interface AiToolGateway {
    fun availableTools(
        conversationType: String = "chat",
        webSearchArmed: Boolean = false,
        /** Chat-only active group ids; null skips group filter (writing / legacy). */
        activeToolGroups: Set<String>? = null,
        /**
         * Model capabilities. When [io.legado.app.domain.model.AiCapability.WEB_SEARCH] is present,
         * the custom [web_search tool][io.legado.app.data.repository.AiToolRepository.TOOL_WEB_SEARCH]
         * is suppressed so the provider's native web search is used instead.
         */
        modelCapabilities: Set<String> = emptySet(),
        /** AI 输出审批模式："auto" | "ask" | "plan"。决定写工具描述是否标注需确认。 */
        outputMode: String = "ask",
        /** 会话是否已有计划工件：有则 read_file("plan://…") 可用（计划最终轮/修订轮读文件）。 */
        hasActivePlan: Boolean = false,
    ): List<AiToolDefinition>

    suspend fun execute(
        call: AiToolCall,
        onProgress: AiToolProgressCallback? = null,
        toolGroupState: io.legado.app.domain.model.AiToolGroupState? = null,
    ): AiToolResult
}
