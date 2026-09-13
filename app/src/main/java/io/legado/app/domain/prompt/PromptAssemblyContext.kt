package io.legado.app.domain.prompt

import io.legado.app.data.entities.AiWorkspace
import io.legado.app.data.entities.AiWritingPrompt
import io.legado.app.ui.ai.chat.AiCharacterCardUi
import io.legado.app.ui.ai.chat.AiChatMessageUi
import io.legado.app.ui.ai.chat.AiWritingPromptUi

/** All inputs needed to render prompt blocks for one generation request. */
data class PromptAssemblyContext(
    val pipelineMode: PromptPipelineMode,
    val conversationId: String? = null,
    val conversationType: String = "chat",
    val conversationTitle: String = "",
    val history: List<AiChatMessageUi> = emptyList(),
    val newUserContent: String = "",
    val contextWindow: Int = 0,
    val writingSubMode: String = "roleplay",
    val directedSpeakerCardId: String? = null,
    val characterCards: List<AiCharacterCardUi> = emptyList(),
    val userName: String = "",
    val userDescription: String = "",
    val userCardEnabled: Boolean = false,
    val writingPrompts: List<AiWritingPromptUi> = emptyList(),
    val workspace: AiWorkspace? = null,
    val postHistoryInstructions: String = "",
    val worldEntryScanDepth: Int = 2,
    /** Chat-only: bottom-bar web search toggle armed for this generation. */
    val webSearchArmed: Boolean = false,
    /** CSV allowlist of skill ids; null = all globally-enabled skills for this mode. */
    val skillIds: String? = null,
    /**
     * Chat-only: mutable active tool groups for this generation.
     * Default [io.legado.app.domain.model.AiToolGroup.DEFAULT_ACTIVE_IDS]; writing ignores.
     */
    val toolGroupState: io.legado.app.domain.model.AiToolGroupState? = null,
    /** AI 输出审批模式："auto" | "ask" | "plan"。决定写工具是否需确认。 */
    val outputMode: String = "ask",
    /** 会话是否已有计划工件（决定 read_file("plan://…") 是否可用）。 */
    val hasActivePlan: Boolean = false,
    /**
     * 当前任务清单渲染文本（update_todos 维护）。非空时追加到最后一个 user 消息尾部，
     * 不动 SYSTEM 前缀（保持 DeepSeek/OpenAI 前缀缓存稳定）。仅 UI 会话生成路径设置。
     */
    val todosText: String? = null,
)
