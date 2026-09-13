package io.legado.app.ui.ai.chat

import androidx.compose.runtime.Stable
import io.legado.app.data.entities.AiWorldBook
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.WritingInputMode
import io.legado.app.domain.usecase.structured.MutationSnapshotService
import io.legado.app.domain.usecase.structured.OutlineDocument
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** AI 输出审批模式：Edit automatically / Ask before edit / Plan mode。每会话记忆。 */
enum class AiOutputMode {
    /** 自动编辑：写工具直接执行，无需审批。 */
    AUTO,
    /** 编辑前询问：写工具逐项确认（原有 confirmToolsBeforeExecute=true 行为）。 */
    ASK,
    /** 计划模式：先输出设计计划，批准后才进入第二轮最终生成（批准后切 AUTO）。 */
    PLAN,
}


@Stable
data class AiChatUiState(
    val conversations: ImmutableList<AiChatConversationUi> = persistentListOf(),
    val messages: ImmutableList<AiChatMessageUi> = persistentListOf(),
    val streamingMessage: AiChatMessageUi? = null,
    val pendingToolConfs: ImmutableList<PendingToolCallUi> = persistentListOf(),
    val pendingToolBatchFeedback: String = "",
    val pendingUserQuestions: PendingUserQuestionsUi? = null,
    val pendingHabitMemoryConfirm: PendingHabitMemoryConfirmUi? = null,
    val pendingTtsSecretFills: ImmutableList<PendingTtsSecretFillUi> = persistentListOf(),
    val reasoningLevel: AiReasoningLevel = AiReasoningLevel.AUTO,
    val isSending: Boolean = false,
    val currentConversationId: String? = null,
    // Writing mode
    val conversationType: String = "chat",
    val characterCards: ImmutableList<AiCharacterCardUi> = persistentListOf(),
    val writingPrompts: ImmutableList<AiWritingPromptUi> = persistentListOf(),
    val selectedCharacterCard: AiCharacterCardUi? = null,
    val selectedCharacterCards: ImmutableList<AiCharacterCardUi> = persistentListOf(),
    val userName: String = "",
    val userDescription: String = "",
    val userCardEnabled: Boolean = false,
    val continueActionPrompt: String = "",
    val writingSubMode: String = "roleplay",  // "roleplay" | "author"
    // Pagination
    val displayLimit: Int = DEFAULT_DISPLAY_LIMIT,
    val isLoadingHistory: Boolean = false,
    val hasMoreHistory: Boolean = true,
    /** False until the first message snapshot for the current conversation arrives (avoids empty-state flash). */
    val messagesReady: Boolean = false,
    // Model config & context
    val contextWindow: Int = 0,
    val contextInputBudget: Int = 0,
    val contextTokensUsed: Int = 0,
    val contextTokensSource: String = CONTEXT_SOURCE_ESTIMATE,
    val contextCalibrationScale: Float = 1f,
    val contextCacheHitTokens: Int = 0,
    /** Sliding token-weighted ratio: Σ cacheHitTokens / Σ promptTokens (recent requests). */
    val cacheHitRatio: Float = 0f,
    val contextUsageSlices: ImmutableList<AiContextUsageSliceUi> = persistentListOf(),
    val isCompressing: Boolean = false,
    val compressedSummary: String = "",
    val enabledWorldBooks: ImmutableList<AiWorldBook> = persistentListOf(),
    // Non-null while regenerating: ID of the old assistant message being replaced
    val regeneratingMessageId: String? = null,
    val writingRoundCount: Long = 0,
    val memoryTableContent: String = "",
    val outlineContent: String = "",
    val outlineDocument: OutlineDocument = OutlineDocument(),
    val outlineEnabled: Boolean = false,
    /** Roleplay: outline has pending branch options for the user. */
    val outlineBranchChoice: OutlineBranchChoiceUi? = null,
    // Outline import/export
    val showOutlineExportDialog: Boolean = false,
    val outlineExportJson: String = "",
    val showOutlineImportDialog: Boolean = false,
    val outlineImportJson: String = "",
    val showOutlineConversationPicker: Boolean = false,
    val outlineConversationPickerMode: String = "",  // "import" | "export"
    val outlinePickerConversations: ImmutableList<AiChatConversationUi> = persistentListOf(),
    val showOutlineOverwriteConfirm: Boolean = false,
    val outlineOverwritePendingSourceId: String? = null,
    val suggestions: ImmutableList<String> = persistentListOf(),
    val suggestionsLoading: Boolean = false,
    val outlineGenerationPreview: String = "",
    val outlineGenerationReasoning: String = "",
    val showOutlineVersionHistory: Boolean = false,
    val outlineVersions: ImmutableList<MutationSnapshotService.OutlineSnapshotSummary> = persistentListOf(),
    val outlineBookUrl: String = "",
    val outlineBookName: String = "",
    val outlineBookAuthor: String = "",
    val showOutlineBookPicker: Boolean = false,
    val outlineBookshelfBooks: ImmutableList<io.legado.app.data.entities.Book> = persistentListOf(),
    val galgameHudHtml: String = "",
    val galgameHudLoading: Boolean = false,
    val galgameEnabled: Boolean = false,
    /** 每会话 AI 输出审批模式（AUTO/ASK/PLAN）。ASK 即原 confirmToolsBeforeExecute=true。 */
    val outputMode: AiOutputMode = AiOutputMode.ASK,
    /** Plan mode：第一轮计划落库后等待审批。 */
    val pendingPlan: PendingPlanUi? = null,
    /** 已结束计划（批准/拒绝/作废）的只读详情视图。 */
    val planDetailView: PlanDetailUi? = null,
    /**
     * Cached from prompt pipeline: whether MultiBubbleProtocol is enabled for the current mode.
     * Controls `<msg>` strip/split; not a user chat toggle.
     */
    val multiBubbleProtocolEnabled: Boolean = true,
    /** Writing roleplay: show accent bubbles on dialogue segments. */
    val roleplayDialogueBubbleEnabled: Boolean = true,
    /** Chat bottom-bar: user armed public web search for this conversation session. */
    val webSearchArmed: Boolean = false,
    val interCharacterChatEnabled: Boolean = true,
    val dialogueHighlightEnabled: Boolean = true,
    val postEditEnabled: Boolean = false,
    val isPostEditing: Boolean = false,
    // Workspace
    val workspaceId: String? = null,
    val workspaceName: String = "",
    val workspaceCharacterCardIds: String = "",
    val workspaceWorldBookIds: String = "",
    val workspaceBookUrl: String = "",
    val workspaceExportJson: String = "",
    val workspaceImportJson: String = "",
    val showWorkspaceSheet: Boolean = false,
    val showWorkspaceClonePicker: Boolean = false,
    val workspaceCloneSources: ImmutableList<AiChatConversationUi> = persistentListOf(),
    val showWorkspaceBookPicker: Boolean = false,
    val workspaceBookshelfBooks: ImmutableList<io.legado.app.data.entities.Book> = persistentListOf(),
    /** 采纳桥:衍生会话"写入本书"预览(仅衍生会话显示)。 */
    val adoptPreview: io.legado.app.domain.usecase.AdoptDerivedToBookUseCase.AdoptPreview? = null,
    val adoptPreviewLoading: Boolean = false,
    /** 采纳桥:本次要写入的类型(outline/memory/cards/worldbook 子集)。 */
    val adoptSelectedTypes: Set<String> = emptySet(),
    val isMaintainingStructuredData: Boolean = false,
    /** Global: auto memory-table / outline maintain after writing turns. */
    val structuredAutoMaintainEnabled: Boolean = true,
    val pendingSnapshotUndo: PendingSnapshotUndoUi? = null,
    /** Global habit: reply_style (e.g. catgirl tone). Chat empty-state only. */
    val habitReplyStyle: String = "",
    val pendingAttachments: ImmutableList<AiChatPendingAttachmentUi> = persistentListOf(),
    val isProcessingAttachments: Boolean = false,
    /** Globally available skills matching current conversation mode (for select sheet). */
    val availableSkills: ImmutableList<AiConversationSkillUi> = persistentListOf(),
    /**
     * CSV allowlist of skillIds for this conversation.
     * null = all globally-enabled skills for the mode.
     */
    val conversationSkillIds: String? = null,
    // ---- HTML App Player（AI 生成的游戏/HTML 应用） ----
    /** 非 null 时全屏播放器打开。 */
    val activeHtmlApp: HtmlAppPlayerState? = null,
    /** 最新 AI 回复末尾夹带的 `__game_update__` JSON，下行推给运行中的游戏。 */
    val gameUpdatePayload: String? = null,
    /** 游戏经 GameBridge.notifyContext 注入的静默上下文，注入下轮 AI 请求后清空。 */
    val silentGameContext: String = "",
) {
    companion object {
        const val DEFAULT_DISPLAY_LIMIT = 12
        const val CONTEXT_SOURCE_API = "api"
        const val CONTEXT_SOURCE_ESTIMATE = "estimate"
    }
}

@Stable
data class AiChatConversationUi(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val isSelected: Boolean,
    val providerName: String,
    val modelName: String,
    val type: String = "chat",
    val characterCardId: String? = null,
    val promptIds: String? = null,
)

enum class AiContextUsageCategoryUi {
    SYSTEM,
    RULES,
    CHARACTER,
    KNOWLEDGE,
    HISTORY,
    DRAFT,
    TOOLS,
}

@Stable
data class AiContextUsageSliceUi(
    val category: AiContextUsageCategoryUi,
    val tokens: Int,
    val detail: String = "",
)

@Stable
data class AiChatMessageUi(
    val id: String,
    val role: String,
    val parts: ImmutableList<AiMessagePart> = persistentListOf(),
    val content: String = "",
    val reasoning: String? = null,
    val toolTrace: String? = null,
    val createdAt: Long,
    val thinkingDuration: Int = 0,
    val bookResults: ImmutableList<AiChatBookResultUi> = persistentListOf(),
    val branchIndex: Int = 0,
    val totalBranches: Int = 1,
    val parentMessageId: String? = null,
    val speakerCardId: String? = null,
    val speakerName: String = "",
    val speakerColorIndex: Int = 0,
    /** Director note (`/temp`): shown in UI, omitted from later model context. */
    val excludeFromContext: Boolean = false,
    /** 消息里引用的 AI 生成 HTML 应用（game/可视化），用于渲染"运行"卡片。 */
    val htmlApps: ImmutableList<AiChatHtmlAppUi> = persistentListOf(),
    /** 计划工件消息：时间线中以计划卡片呈现；planFileId 指向其计划文件。 */
    val isPlanMessage: Boolean = false,
    /** 计划行 id（plan_xxx），用于时间线链接与重开。 */
    val planFileId: String? = null,
    /** 计划卡片正文：当前 plan.md 内容（消息加载/计划变更时填充）。 */
    val planContent: String? = null,
    /** 计划卡片状态：AiPlan.STATUS_*（pending/approved/rejected/superseded）。 */
    val planStatus: String? = null,
    /** 计划修订次数（卡片标识行 + pop 动画触发用）。 */
    val planRevision: Int? = null,
)

@Stable
data class AiChatBookResultUi(
    val bookUrl: String,
    val name: String,
    val author: String,
    val origin: String?,
    val coverPath: String?,
    val latestChapterTitle: String?,
    val currentChapterTitle: String?,
    val intro: String?,
)

/** HTML App 播放器当前实例：appId + 标题 + index.html 的绝对路径（file:// 加载）。 */
@Stable
data class HtmlAppPlayerState(
    val appId: String,
    val title: String,
    val entryPath: String,
)

/** 消息里引用的一个 HTML App（来自 AiMessagePart.HtmlAppRef → AiHtmlApp 元数据）。 */
@Stable
data class AiChatHtmlAppUi(
    val appId: String,
    val title: String,
)

@Stable
data class AiChatPendingAttachmentUi(
    val id: String,
    val localPath: String,
    val mimeType: String,
    val displayName: String,
    val kind: String,
    val sizeBytes: Long = 0,
    val extractedTextPath: String? = null,
    val truncated: Boolean = false,
    val previewText: String? = null,
    val isProcessing: Boolean = false,
)

@Stable
data class AiToolConfirmationUi(
    val title: String,
    val description: String,
)

@Stable
data class FieldChangeUi(
    val path: String,
    val oldValue: String,
    val newValue: String,
    val checked: Boolean = true,
)

@Stable
data class PendingSnapshotUndoUi(
    val snapshotId: String,
    val resourceType: String,
    val summary: String,
    val changes: ImmutableList<FieldChangeUi> = persistentListOf(),
)

enum class AiExecutionStatus {
    RUNNING,
    EXECUTING,
    DONE,
    FAILED,
    CANCELLED,
    REJECTED,
    INCOMPLETE,
}

enum class AiExecutionKind {
    TOOL,
    SIDE_EFFECT,
    PROMPT_INJECTION,
}

@Stable
data class AiExecutionInjectionBlockUi(
    val blockId: String,
    val estimatedTokens: Int = 0,
    val truncated: Boolean = false,
    val position: String = "prefix",
    val preview: String = "",
    /** Full text for detail dialog; falls back to preview when empty. */
    val content: String = "",
)

@Stable
data class AiExecutionHistoryItemUi(
    val id: String,
    val kind: AiExecutionKind = AiExecutionKind.TOOL,
    val toolCallId: String = "",
    val messageId: String = "",
    val toolName: String = "",
    /** Usage source for SIDE_EFFECT, or empty. */
    val source: String = "",
    val operationPreview: String = "",
    val status: AiExecutionStatus = AiExecutionStatus.DONE,
    val timestamp: Long = 0L,
    val input: String = "",
    val output: String = "",
    val snapshotId: String? = null,
    val totalTokens: Int = 0,
    val generatedChars: Int = 0,
    val modelName: String = "",
    val injectionBlocks: ImmutableList<AiExecutionInjectionBlockUi> = persistentListOf(),
    val injectionTotalTokens: Int = 0,
    val inChatCount: Int = 0,
)

@Stable
data class PendingToolSubItem(
    val id: String,
    val opLabel: String,
    val fieldChanges: kotlinx.collections.immutable.ImmutableList<FieldChangeUi> = persistentListOf(),
    val deepLink: io.legado.app.domain.model.StructuredDataDeepLink? = null,
    val checked: Boolean = true,
    val validationError: String? = null,
    val tier: String = "INCREMENTAL",
    val opIndex: Int = 0,
)

@Stable
data class PendingToolCallUi(
    val callId: String,
    val toolName: String,
    val displayName: String,
    val summary: String,
    val previewDetail: String = "",
    val feedback: String = "",
    val checked: Boolean = true,
    val validationError: String? = null,
    val validationWarnings: kotlinx.collections.immutable.ImmutableList<String> = persistentListOf(),
    val fieldChanges: kotlinx.collections.immutable.ImmutableList<FieldChangeUi> = persistentListOf(),
    val deepLink: io.legado.app.domain.model.StructuredDataDeepLink? = null,
    val subItems: kotlinx.collections.immutable.ImmutableList<PendingToolSubItem> = persistentListOf(),
    val editableArgsJson: String = "",
    val argsValidationError: String? = null,
    val tier: String = "INCREMENTAL",
    val allowFieldLevelApproval: Boolean = false,
    /** Cloud TTS create/import: must fill secrets in the dedicated panel before confirm. */
    val requiresLocalSecret: Boolean = false,
    val localSecretSatisfied: Boolean = true,
)

/** Whether this pending tool call can be included when the user taps Confirm. */
fun PendingToolCallUi.isApprovable(): Boolean {
    if (argsValidationError != null) return false
    if (requiresLocalSecret && !localSecretSatisfied) return false
    if (subItems.isNotEmpty()) {
        return subItems.any { it.checked && it.validationError == null }
    }
    return checked && validationError == null
}

/** Whether the parent approval checkbox should accept toggles. */
fun PendingToolCallUi.canToggleApproval(): Boolean {
    if (argsValidationError != null) return false
    if (subItems.isEmpty()) return validationError == null
    return subItems.any { it.tier == "INCREMENTAL" && it.validationError == null }
}

fun PendingToolSubItem.isApprovable(): Boolean =
    checked && validationError == null

/** Parent checkbox state when sub-items carry per-op validation. */
fun PendingToolCallUi.hasApprovableSubSelection(): Boolean =
    subItems.any { it.isApprovable() }

@Stable
data class UserQuestionOptionUi(
    val id: String,
    val label: String,
)

@Stable
data class UserQuestionUi(
    val id: String,
    val prompt: String,
    val options: ImmutableList<UserQuestionOptionUi>,
    val allowMultiple: Boolean,
    val selectedIds: Set<String> = emptySet(),
    val customText: String = "",
)

@Stable
data class PendingUserQuestionsUi(
    val callId: String,
    val questions: ImmutableList<UserQuestionUi>,
)

fun PendingUserQuestionsUi.canSubmit(): Boolean =
    questions.all { question ->
        question.selectedIds.isNotEmpty() || question.customText.isNotBlank()
    }

@Stable
data class OutlineBranchOptionUi(
    val id: String,
    val label: String,
)

@Stable
data class OutlineBranchChoiceUi(
    val title: String,
    val options: ImmutableList<OutlineBranchOptionUi>,
)

@Stable
data class HabitMemoryConfirmLineUi(
    val op: String,
    val key: String,
    val value: String = "",
)

@Stable
data class PendingHabitMemoryConfirmUi(
    val callId: String,
    val lines: kotlinx.collections.immutable.ImmutableList<HabitMemoryConfirmLineUi>,
)

@Stable
data class PendingTtsSecretFillUi(
    val callId: String,
    val action: String,
    val provider: String? = null,
    val engineLabel: String? = null,
    val apiKeyLabel: String = "API Key",
    val secretKeyLabel: String = "Secret Key",
    val needsApiKey: Boolean = true,
    val needsSecretKey: Boolean = false,
    val apiKey: String = "",
    val secretKey: String = "",
) {
    fun isSatisfied(): Boolean =
        (!needsApiKey || apiKey.isNotBlank()) && (!needsSecretKey || secretKey.isNotBlank())
}

/** Plan mode：第一轮计划落库后，独立面板等待用户审批/编辑/拒绝。 */
@Stable
data class PendingPlanUi(
    /** 计划消息在对话中的 id（计划作为 assistant 消息落库，同 id 更新跟随修订）。 */
    val messageId: String,
    val planContent: String,
    val planReasoning: String? = null,
    /** 用户编辑中的计划文本；批准时用编辑后的内容。 */
    val isEditing: Boolean = false,
    val editedPlanContent: String = "",
    /** 暂存的段落反馈：逐条选中暂存，然后一次提交全部修订。 */
    val stagedFeedback: ImmutableList<StagedFeedbackUi> = persistentListOf(),
) {
    /** 批准时使用的最终计划内容（编辑态优先）。 */
    val approvedContent: String
        get() = if (isEditing) editedPlanContent else planContent
}

/** 暂存的段落反馈项：选中计划中一段文字，并附修改意见。
 *  startLine/endLine 为选中文本在 plan.md 中的 1 基行号区间（定位失败时为空）。 */
@Stable
data class StagedFeedbackUi(
    val id: String,
    val selectedText: String,
    val feedback: String,
    val startLine: Int? = null,
    val endLine: Int? = null,
)

/** 已结束计划的只读详情（内容从 plan_<id>.md 文件读取）。 */
@Stable
data class PlanDetailUi(
    val planId: String,
    val messageId: String,
    val content: String,
    val status: String,
    val revision: Int,
    val updatedAt: Long,
)


@Stable
data class AiCharacterCardUi(
    val id: String,
    val name: String,
    val description: String,
    val openingLine: String,
    val worldBookIds: String = "",
    val personality: String = "",
    val scenario: String = "",
    val exampleDialogues: String = "",
    val postHistoryInstructions: String = "",
    val alternateOpenings: String = "[]",
    val aliasesJson: String = "[]",
    val voiceGender: String = "unknown",
    val voiceAgeBand: String = "unknown",
    val bookUrl: String = "",
    val bookName: String = "",
    val bookAuthor: String = "",
    val dramaticRole: String = "",
    val avatarPath: String = "",
    val conversationCount: Int = 0,
)

fun io.legado.app.data.entities.AiCharacterCard.toUi(
    conversationCount: Int = 0,
    dramaticRole: String = "",
): AiCharacterCardUi =
    AiCharacterCardUi(
        id = id,
        name = name,
        description = description,
        openingLine = openingLine,
        worldBookIds = worldBookIds,
        personality = personality,
        scenario = scenario,
        exampleDialogues = exampleDialogues,
        postHistoryInstructions = postHistoryInstructions,
        alternateOpenings = alternateOpenings,
        aliasesJson = aliasesJson,
        voiceGender = voiceGender,
        voiceAgeBand = voiceAgeBand,
        bookUrl = bookUrl,
        bookName = bookName,
        bookAuthor = bookAuthor,
        dramaticRole = dramaticRole,
        avatarPath = avatarPath,
        conversationCount = conversationCount,
    )

@Stable
data class AiWritingPromptUi(
    val id: String,
    val name: String,
    val content: String,
    val category: String,
    val enabled: Boolean = true,
)

@Stable
data class AiConversationSkillUi(
    val skillId: String,
    val name: String,
    val description: String,
    val mode: String,
    val globallyEnabled: Boolean,
)

@Stable
data class AiSlashCommandUi(
    val id: String,
    val primary: String,
    val insertText: String,
    val title: String,
    val description: String,
)

sealed interface AiChatIntent {
    data object NewConversation : AiChatIntent
    data class SelectConversation(val id: String) : AiChatIntent
    data class SendMessage(val content: String) : AiChatIntent
    data class AddAttachmentsFromUris(val uris: List<android.net.Uri>) : AiChatIntent
    data class RemovePendingAttachment(val id: String) : AiChatIntent
    data object StopGenerating : AiChatIntent
    data class ToggleToolApproval(val callId: String) : AiChatIntent
    data class ToggleToolSubItemApproval(val callId: String, val subItemId: String) : AiChatIntent
    data class UpdateToolFeedback(val callId: String, val feedback: String) : AiChatIntent
    data class UpdateToolBatchFeedback(val feedback: String) : AiChatIntent
    data object ConfirmPendingTools : AiChatIntent
    data object RejectPendingTools : AiChatIntent
    data class SetOutputMode(val mode: AiOutputMode) : AiChatIntent
    data object ToggleWebSearch : AiChatIntent
    data class UpdateReasoningLevel(val level: AiReasoningLevel) : AiChatIntent
    data class RegenerateMessage(val messageId: String) : AiChatIntent
    data class SwitchBranch(val messageId: String, val direction: Int) : AiChatIntent
    data class DeleteConversation(val id: String) : AiChatIntent
    data class RenameConversation(val id: String, val title: String) : AiChatIntent
    // Writing mode
    data class SwitchMode(val type: String) : AiChatIntent
    data class CreateConversationWithCharacter(val characterCardId: String) : AiChatIntent
    data class UpdateConversationCharacter(val characterCardId: String?) : AiChatIntent
    data class UpdateConversationCharacters(val cardIds: Set<String>) : AiChatIntent
    data class UpdateActionPrompt(val category: String, val content: String) : AiChatIntent
    data class SaveCharacterCard(val name: String, val description: String, val openingLine: String, val worldBookIds: String = "", val cardId: String? = null, val personality: String = "", val scenario: String = "", val exampleDialogues: String = "", val postHistoryInstructions: String = "", val alternateOpenings: String = "[]", val aliasesJson: String = "[]", val voiceGender: String = "unknown", val voiceAgeBand: String = "unknown", val bookUrl: String = "", val bookName: String = "", val bookAuthor: String = "", val dramaticRole: String = "", val avatarPath: String = "") : AiChatIntent
    data class ImportCharacterCardJson(val json: String) : AiChatIntent
    data class ImportCharacterCardBytes(val bytes: ByteArray) : AiChatIntent
    data class SaveUserCard(val name: String, val description: String, val enabled: Boolean) : AiChatIntent
    data object ToggleUserCardEnabled : AiChatIntent
    data class DeleteCharacterCard(val cardId: String) : AiChatIntent
    data class SaveWritingPrompt(val name: String, val content: String, val category: String, val promptId: String? = null) : AiChatIntent
    data class DeleteWritingPrompt(val promptId: String) : AiChatIntent
    data class TogglePromptEnabled(val promptId: String) : AiChatIntent
    data class ToggleConversationSkill(val skillId: String) : AiChatIntent
    data object ResetConversationSkills : AiChatIntent
    data object ContinueWriting : AiChatIntent
    data class AiHelpReply(
        val draftText: String,
        val inputMode: WritingInputMode = WritingInputMode.DIALOGUE,
    ) : AiChatIntent
    // Message actions
    data class DeleteMessage(val messageId: String) : AiChatIntent
    data class EditMessage(val messageId: String, val newContent: String) : AiChatIntent
    data class RegenerateFromUserMessage(val userMessageId: String) : AiChatIntent
    data class ForkConversation(val messageId: String) : AiChatIntent
    data class ForkConversationToSingleChat(val characterCardId: String) : AiChatIntent
    // Writing sub-mode
    data class SetWritingSubMode(val subMode: String) : AiChatIntent  // "roleplay" | "author"
    // Pagination
    data object LoadMoreMessages : AiChatIntent
    // Context
    data class UpdateDraftInput(val text: String) : AiChatIntent
    data object CompressContext : AiChatIntent
    // Suggestions
    data class SelectSuggestion(val text: String) : AiChatIntent
    data object DismissSuggestions : AiChatIntent
    data object ToggleGalgame : AiChatIntent
    data object RegenerateGalgameHud : AiChatIntent
    data object RecreateGalgameHud : AiChatIntent
    data class ImportHudHtml(val html: String) : AiChatIntent
    data object ToggleInterCharacterChat : AiChatIntent
    data object ToggleDialogueHighlight : AiChatIntent
    data object ToggleRoleplayDialogueBubble : AiChatIntent
    // HTML App Player（AI 生成的游戏/HTML 应用）
    /** 点击消息卡片"运行"：按 messageId 查 HtmlAppRef → 打开全屏播放器。 */
    data class LaunchHtmlApp(val messageId: String) : AiChatIntent
    data object DismissHtmlApp : AiChatIntent
    /** 游戏经 GameBridge.sendToChat：作为用户消息插入对话（自动关闭播放器）。 */
    data class GameSendToChat(val text: String) : AiChatIntent
    /** 游戏经 GameBridge.notifyContext：静默注入下轮 AI 请求上下文。 */
    data class GameNotifyContext(val json: String) : AiChatIntent
    data class SetStructuredAutoMaintain(val enabled: Boolean) : AiChatIntent
    data object TogglePostEdit : AiChatIntent
    // Plan mode（审批）
    data object AcceptPlan : AiChatIntent
    data object RejectPlan : AiChatIntent
    data class EditPlan(val content: String) : AiChatIntent
    data object CancelPlanEdit : AiChatIntent
    /** 选中计划部分文字提交反馈，让 AI 修订计划（selectedText 为空=作用于整篇）。 */
    data class PlanRevisionFeedback(val selectedText: String, val feedback: String) : AiChatIntent
    /** 拒绝后的计划卡片上提交反馈修订意见。 */
    data class RejectedPlanFeedback(val planFileId: String, val feedback: String) : AiChatIntent
    /** 选中计划部分文字提交反馈，暂存到队列（不立即发起修订）。 */
    data class AddPlanFeedback(val selectedText: String, val feedback: String) : AiChatIntent
    /** 移除暂存队列中的一条反馈。 */
    data class RemovePlanFeedback(val feedbackId: String) : AiChatIntent
    /** 提交暂存队列中的全部反馈，发起一次修订轮。 */
    data object SubmitAllPlanFeedback : AiChatIntent
    /** 点击时间线计划链接卡：pending 重开审批面板，已结束打开只读详情。 */
    data class OpenPlan(val planId: String) : AiChatIntent
    data object DismissPlanDetail : AiChatIntent
    // Outline
    data class SaveOutline(val content: String, val enabled: Boolean) : AiChatIntent
    data object ToggleOutlineEnabled : AiChatIntent
    data class GenerateOutline(val outlineKind: String) : AiChatIntent  // AI generate from scratch; kind = linear|branching
    data class SupplementOutline(val content: String, val outlineKind: String) : AiChatIntent  // AI supplement current sheet draft/content
    data object DeleteOutline : AiChatIntent
    data object CleanupOrphanOutlines : AiChatIntent
    data class SelectOutlineBranch(val optionId: String) : AiChatIntent
    // Outline import/export
    data object ShowOutlineExportDialog : AiChatIntent
    data object DismissOutlineExportDialog : AiChatIntent
    data object CopyOutlineExport : AiChatIntent
    data object ShareOutlineExport : AiChatIntent
    data object ShowOutlineImportDialog : AiChatIntent
    data object DismissOutlineImportDialog : AiChatIntent
    data class UpdateOutlineImportJson(val json: String) : AiChatIntent
    data object ConfirmOutlineImport : AiChatIntent
    data object ShowOutlineImportFromConversation : AiChatIntent
    data object ShowOutlineExportToConversation : AiChatIntent
    data object DismissOutlineConversationPicker : AiChatIntent
    data class SelectConversationForOutlineTransfer(val conversationId: String) : AiChatIntent
    data object ConfirmOutlineOverwrite : AiChatIntent
    data object DismissOutlineOverwriteConfirm : AiChatIntent
    data object ShowOutlineBookPicker : AiChatIntent
    data object DismissOutlineBookPicker : AiChatIntent
    data class SelectOutlineBook(val book: io.legado.app.data.entities.Book) : AiChatIntent
    data object ClearOutlineBookSource : AiChatIntent
    data class RestoreSnapshot(val snapshotId: String) : AiChatIntent
    data object ConfirmSnapshotUndo : AiChatIntent
    data object DismissSnapshotUndo : AiChatIntent
    data class UpdateToolArgs(val callId: String, val json: String) : AiChatIntent
    data class ToggleFieldChangeApproval(val callId: String, val path: String) : AiChatIntent
    data class ToggleQuestionOption(val callId: String, val questionId: String, val optionId: String) : AiChatIntent
    data class UpdateQuestionCustomText(val callId: String, val questionId: String, val text: String) : AiChatIntent
    data class SubmitUserQuestions(val callId: String) : AiChatIntent
    data object DismissUserQuestions : AiChatIntent
    data object ConfirmHabitMemory : AiChatIntent
    data object RejectHabitMemory : AiChatIntent
    data class UpdateTtsSecretFill(
        val callId: String,
        val apiKey: String? = null,
        val secretKey: String? = null,
    ) : AiChatIntent
    // Workspace
    data object ShowWorkspaceSheet : AiChatIntent
    data object DismissWorkspaceSheet : AiChatIntent
    data object ExportWorkspace : AiChatIntent
    data object ShowWorkspaceClonePicker : AiChatIntent
    data object DismissWorkspaceClonePicker : AiChatIntent
    data class CloneWorkspaceFromConversation(val sourceConversationId: String) : AiChatIntent
    data class UpdateWorkspaceImportJson(val json: String) : AiChatIntent
    data object ImportWorkspaceJson : AiChatIntent
    data class UpdateWorkspaceWorldBookIds(val worldBookIds: String) : AiChatIntent
    // 工作区绑定本书(采纳桥目标)
    data object ShowWorkspaceBookPicker : AiChatIntent
    data object DismissWorkspaceBookPicker : AiChatIntent
    data class SelectWorkspaceBook(val book: io.legado.app.data.entities.Book) : AiChatIntent
    // 采纳桥:衍生会话 → 写入本书(正典)
    data object ShowAdoptPreview : AiChatIntent
    data object DismissAdoptPreview : AiChatIntent
    data object ConfirmAdoptToBook : AiChatIntent
    data class ToggleAdoptType(val type: String) : AiChatIntent
}

sealed interface AiChatEffect {
    data class ShowMessage(val message: String) : AiChatEffect
    data class SetInputText(val text: String, val forConversationId: String? = null) : AiChatEffect
    data class CopyToClipboard(val text: String) : AiChatEffect
    data class ShareText(val text: String, val title: String) : AiChatEffect
}
