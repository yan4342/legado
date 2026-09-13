package io.legado.app.domain.prompt

import io.legado.app.domain.model.AiMessageRole
import io.legado.app.help.ai.AiTokenEstimator

/** Identifies a configurable prompt block in the assembly pipeline. */
enum class PromptBlockId {
    Main,
    Persona,
    Character,
    SkillsCatalog,
    WorldCatalog,
    WorldEntries,
    MemoryTables,
    CharacterCardsCatalog,
    UserMemory,
    UserCard,
    LocalTime,
    WorkspaceIndex,
    WorkspacePrefetch,
    WritingSubmode,
    WritingInputFormat,
    WritingXmlNotice,
    WritingIdentity,
    MultiCharacter,
    CurrentSpeaker,
    WritingStyleGuide,
    WritingActionContinue,
    PostHistory,
    HelpReplySystem,
    /** Chat / roleplay `<msg>` multi-bubble output protocol (template + session pref). */
    MultiBubbleProtocol,
    ;

    /**
     * True if the block's rendered content changes **every request** while a
     * conversation grows (current time / current speaker). Such blocks MUST be
     * positioned [PromptBlockPosition.InChat] so the byte-stable SYSTEM prefix
     * (DSH/DeepSeek/OpenAI prefix cache) is never rewritten on a per-turn basis.
     * Infrequent-change blocks (memory tables, keyword lore, world catalog) are
     * intentionally ordered at the *tail* of the prefix instead — they only ever
     * bust the cache when they actually change. Enforced in [PromptAssembler.assemble]
     * on debug builds.
     */
    val isVolatile: Boolean
        get() = this in setOf(
            LocalTime,
            CurrentSpeaker,
        )
}

enum class PromptPipelineMode(val value: String) {
    Chat("chat"),
    WritingRoleplay("writing_roleplay"),
    WritingAuthor("writing_author"),
    WritingHelpReply("writing_help_reply"),
    ;

    companion object {
        fun fromValue(value: String): PromptPipelineMode? =
            entries.firstOrNull { it.value == value }
    }
}

enum class PromptBlockPosition {
    /** Merged into the leading system prefix (default). */
    Prefix,
    /** Injected within chat history at [depth] from the end (0 = after last message). */
    InChat,
}

data class PromptBlockSpec(
    val id: PromptBlockId,
    val role: String = AiMessageRole.SYSTEM,
    val enabled: Boolean = true,
    val order: Int = 0,
    val position: PromptBlockPosition = PromptBlockPosition.Prefix,
    /** Used when [position] is [PromptBlockPosition.InChat]. */
    val depth: Int = 0,
    /** Max tokens for this block; 0 = unlimited. */
    val maxTokens: Int = 0,
)

data class PromptPipelinePreset(
    val id: String,
    val name: String,
    val mode: String,
    val blocks: List<PromptBlockSpec>,
    val isDefault: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class AssembledPromptBlock(
    val spec: PromptBlockSpec,
    val content: String,
    val estimatedTokens: Int,
    val truncated: Boolean = false,
)

data class AssembledPrompt(
    val systemPrompt: String,
    val inChatInjections: List<InChatInjection> = emptyList(),
    val blocks: List<AssembledPromptBlock> = emptyList(),
    /**
     * Prepended to the new user message (not SYSTEM) so per-turn instructions
     * like writing "continue" do not bust DeepSeek/OpenAI prefix cache on system.
     */
    val userPrefix: String? = null,
) {
    fun toPromptInjectionPart(
        previewChars: Int = 120,
        maxContentChars: Int = 48_000,
        /**
         * In-chat injections (world-book entries, in-chat blocks) keep a smaller content
         * snapshot so per-message storage stays bounded on long conversations.
         */
        maxInChatContentChars: Int = 2_000,
    ): io.legado.app.domain.model.AiMessagePart.PromptInjection {
        val summaries = blocks.map { block ->
            val full = block.content.trim()
            io.legado.app.domain.model.PromptInjectionBlock(
                blockId = block.spec.id.name,
                estimatedTokens = block.estimatedTokens,
                truncated = block.truncated || full.length > maxContentChars,
                position = block.spec.position.name.lowercase(),
                preview = full.take(previewChars),
                content = full.take(maxContentChars),
            )
        }
        val inChatSummaries = inChatInjections.mapIndexed { index, injection ->
            io.legado.app.domain.model.PromptInjectionBlock(
                blockId = injection.blockId.ifBlank { "inchat_${index + 1}" },
                estimatedTokens = AiTokenEstimator.estimateTokens(injection.content),
                truncated = injection.content.length > maxInChatContentChars,
                position = "inchat",
                preview = injection.content.take(previewChars),
                content = injection.content.take(maxInChatContentChars),
            )
        }
        val all = summaries + inChatSummaries
        return io.legado.app.domain.model.AiMessagePart.PromptInjection(
            blocks = all,
            totalEstimatedTokens = all.sumOf { it.estimatedTokens },
            inChatCount = inChatInjections.size,
        )
    }
}

data class InChatInjection(
    val blockId: String = "",
    val role: String,
    val content: String,
    val depth: Int,
    val order: Int,
)
