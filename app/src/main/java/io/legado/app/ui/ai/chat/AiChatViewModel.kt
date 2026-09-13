package io.legado.app.ui.ai.chat

import android.util.Log
import io.legado.app.R
import io.legado.app.utils.getPrefBoolean
import splitties.init.appCtx
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.AiPlan
import io.legado.app.domain.gateway.AiCharacterCardGateway
import io.legado.app.domain.gateway.AiChatGateway
import io.legado.app.domain.gateway.AiMemoryGateway
import io.legado.app.domain.gateway.AiHtmlAppGateway
import io.legado.app.domain.gateway.AiOutlineGateway
import io.legado.app.domain.gateway.AiPlanGateway
import io.legado.app.domain.gateway.AiTodoGateway
import io.legado.app.domain.gateway.AiMemoryTableGateway
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiPromptPipelineGateway
import io.legado.app.domain.gateway.AiPromptTemplateGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.AiToolConfigGateway
import io.legado.app.domain.gateway.ReadAloudCharacterGateway
import io.legado.app.domain.gateway.AiWorkspaceGateway
import io.legado.app.domain.gateway.AiToolGateway
import io.legado.app.domain.gateway.AiWorldBookGateway
import io.legado.app.domain.gateway.AiWritingPromptGateway
import io.legado.app.domain.gateway.AiSkillGateway
import io.legado.app.domain.prompt.PromptAssembler
import io.legado.app.domain.usecase.ImportWorldInfoUseCase
import io.legado.app.domain.usecase.AiChatGenerationUseCase
import io.legado.app.domain.usecase.GenerationOrchestrator
import io.legado.app.domain.usecase.WritingStructuredMaintainUseCase
import io.legado.app.domain.usecase.AdoptDerivedToBookUseCase
import io.legado.app.domain.usecase.WritingWorkspaceIndexBuilder
import io.legado.app.help.ai.PlanFileStore
import io.legado.app.help.config.AppConfig
import io.legado.app.domain.usecase.structured.CharacterCardMutator
import io.legado.app.domain.usecase.structured.MutationSnapshotService
import io.legado.app.domain.usecase.structured.StructuredDataValidator
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.model.AiMessagePartJson
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.AiToolCall
import io.legado.app.domain.model.AiToolGroupState
import io.legado.app.domain.model.reasoningContent
import io.legado.app.domain.model.textContent
import io.legado.app.domain.model.htmlAppRefParts
import io.legado.app.domain.model.toolTraceText
import io.legado.app.domain.usecase.structured.OutlineBranchChoiceUseCase
import io.legado.app.domain.usecase.structured.OutlineMarkdownCodec
import io.legado.app.domain.usecase.structured.OutlineMutator
import io.legado.app.domain.usecase.BookshelfAccessPreviewer
import io.legado.app.domain.usecase.ToolApprovalDecision
import io.legado.app.utils.AiIdListCodec
import io.legado.app.utils.GSON
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/** 计划卡片元数据：内容（当前 plan.md）+ 状态（AiPlan.STATUS_*）。 */
internal data class PlanCardMeta(
    val content: String,
    val status: String,
    val revision: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AiChatViewModel(
    internal val aiChatGateway: AiChatGateway,
    internal val aiProfileGateway: AiProfileGateway,
    internal val aiCharacterCardGateway: AiCharacterCardGateway,
    internal val aiWritingPromptGateway: AiWritingPromptGateway,
    internal val aiSkillGateway: AiSkillGateway,
    internal val aiWorldBookGateway: AiWorldBookGateway,
    internal val aiMemoryTableGateway: AiMemoryTableGateway,
    internal val aiMemoryGateway: AiMemoryGateway,
    internal val promptTemplateGateway: AiPromptTemplateGateway,
    internal val generationUseCase: AiChatGenerationUseCase,
    internal val aiOutlineGateway: AiOutlineGateway,
    internal val aiToolConfigGateway: AiToolConfigGateway,
    internal val aiTextGateway: AiTextGateway,
    internal val aiToolGateway: AiToolGateway,
    internal val outlineMutator: OutlineMutator,
    internal val outlineBranchChoiceUseCase: OutlineBranchChoiceUseCase,
    internal val characterCardMutator: CharacterCardMutator,
    internal val readAloudCharacterGateway: ReadAloudCharacterGateway,
    internal val structuredDataValidator: StructuredDataValidator,
    internal val mutationSnapshotService: MutationSnapshotService,
    internal val aiWorkspaceGateway: AiWorkspaceGateway,
    internal val workspaceIndexBuilder: WritingWorkspaceIndexBuilder,
    internal val structuredMaintainUseCase: WritingStructuredMaintainUseCase,
    internal val promptAssembler: PromptAssembler,
    internal val promptPipelineGateway: AiPromptPipelineGateway,
    internal val generationOrchestrator: GenerationOrchestrator,
    internal val bookshelfAccessPreviewer: BookshelfAccessPreviewer,
    internal val importWorldInfoUseCase: ImportWorldInfoUseCase,
    internal val adoptUseCase: AdoptDerivedToBookUseCase,
    internal val aiPlanGateway: AiPlanGateway,
    internal val aiTodoGateway: AiTodoGateway,
    internal val aiHtmlAppGateway: AiHtmlAppGateway,
) : ViewModel() {

    internal val currentConversationId = MutableStateFlow<String?>(null)
    internal var recentMessages: List<AiChatMessageUi> = emptyList()
    internal var olderMessages: List<AiChatMessageUi> = emptyList()
    /** Optimistic delete guard: Room Flow may re-emit before delete finishes. */
    internal val pendingDeletedMessageIds = mutableSetOf<String>()
    /** 当前会话中计划消息 id → 计划行 id（时间线「📋 计划」链接标记）。 */
    internal val planFileIdByMessageId = mutableMapOf<String, String>()
    /** 计划消息 id → 卡片元数据（当前 plan.md 内容 + 状态），消息加载/计划事件时填充。 */
    internal val planCardByMessageId = mutableMapOf<String, PlanCardMeta>()
    internal val allLoadedMessages: List<AiChatMessageUi>
        get() {
            val base = visibleMessages((olderMessages + recentMessages).distinctBy { it.id })
            if (planFileIdByMessageId.isEmpty()) return base
            return base.map { msg ->
                val planId = planFileIdByMessageId[msg.id]
                if (planId != null) {
                    val meta = planCardByMessageId[msg.id]
                    msg.copy(
                        isPlanMessage = true,
                        planFileId = planId,
                        planContent = meta?.content,
                        planStatus = meta?.status,
                        planRevision = meta?.revision,
                    )
                } else msg
            }
        }
    internal var streamingJob: Job? = null
    /** Background memory/outline maintain; must not block [isSending]. */
    internal var maintainJob: Job? = null
    internal val maintainMutex = Mutex()
    internal var workspaceWorldBookSaveJob: Job? = null
    internal var confirmationDeferred: CompletableDeferred<ToolApprovalDecision>? = null
    internal var userQuestionsDeferred: CompletableDeferred<String>? = null
    internal var habitMemoryConfirmDeferred: CompletableDeferred<Boolean>? = null
    internal var pendingRejectionFeedback: String? = null
    internal var generationStopRequested = false
    /** 下一次计划轮按「修订轮」处理：提交意见后 pendingPlan 已清空（面板关闭），修订指令靠此标志保持。 */
    internal var planRevisionRound = false
    /** Timestamp (ms) of the last user approval for each tool name. */
    internal val recentApprovals = mutableMapOf<String, Long>()
    /** Chat-only: active tool groups cached per conversation. */
    internal val conversationToolGroupStates = mutableMapOf<String, AiToolGroupState>()
    /** Chat-only: active tool groups for the in-flight generation (set_tool_groups mutates this). */
    internal var generationToolGroupState: io.legado.app.domain.model.AiToolGroupState? = null
    internal var thinkingStartTime: Long = 0L
    /** Raw token estimate for the request about to be sent; used to calibrate against API prompt. */
    internal var pendingRawPromptEstimate: Int = 0
    internal var draftContextEstimateJob: Job? = null
    internal var draftPersistJob: Job? = null
    /** Latest composer text for the current conversation (text-only draft; no attachments). */
    internal var currentDraftText: String = ""
    /** Prompt blocks assembled for the in-flight generation round. */
    internal var pendingPromptInjection: AiMessagePart.PromptInjection? = null
    internal var pendingSideEffects: MutableList<AiMessagePart.SideEffect> = mutableListOf()
    internal var pendingStreamImages: MutableList<AiMessagePart.Image> = mutableListOf()

    internal val _uiState = MutableStateFlow(AiChatUiState())
    val uiState = _uiState.asStateFlow()

    /** Get list of cards matching a partial @mention (for dropdown filtering). */
    fun matchAtMention(partial: String): List<AiCharacterCardUi> {
        val cards = selectedCharacterCards()
        if (partial.isBlank()) return cards.toList()
        return cards.filter { it.name.contains(partial, ignoreCase = true) }
    }

    /** Slash-command autocomplete while typing a leading `/…` token. */
    fun matchSlashCommands(partial: String): List<AiSlashCommandUi> {
        return io.legado.app.domain.usecase.ai.SlashCommandCatalog.match(partial).map { entry ->
            when (entry.id) {
                "temp" -> AiSlashCommandUi(
                    id = entry.id,
                    primary = entry.primary,
                    insertText = entry.insertText,
                    title = "/${entry.primary}",
                    description = appCtx.getString(R.string.ai_slash_temp_desc),
                )
                else -> AiSlashCommandUi(
                    id = entry.id,
                    primary = entry.primary,
                    insertText = entry.insertText,
                    title = "/${entry.primary}",
                    description = "",
                )
            }
        }
    }

    internal val _effects = MutableSharedFlow<AiChatEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        val savedInterChat = appCtx.getPrefBoolean(
            io.legado.app.constant.PreferKey.interCharacterChatEnabled, true
        )
        val savedDialogueHighlight = appCtx.getPrefBoolean(
            io.legado.app.constant.PreferKey.dialogueHighlightEnabled, true
        )
        val savedRoleplayDialogueBubble = appCtx.getPrefBoolean(
            io.legado.app.constant.PreferKey.aiRoleplayDialogueBubbleEnabled, true
        )
        val savedStructuredAutoMaintain = AppConfig.aiStructuredAutoMaintain
        _uiState.update {
            it.copy(
                interCharacterChatEnabled = savedInterChat,
                dialogueHighlightEnabled = savedDialogueHighlight,
                roleplayDialogueBubbleEnabled = savedRoleplayDialogueBubble,
                structuredAutoMaintainEnabled = savedStructuredAutoMaintain,
            )
        }
        observeConversations()
        observeCurrentMessages()
        observeStreamingCleanup()
        observeCharacterCards()
        observeWritingPrompts()
        observeAvailableSkills()
        observeWorldBooks()
        observeMemoryTableData()
        observeOutline()
        observeGalgameConfig()
        observePostEditConfig()
        viewModelScope.launch { refreshMultiBubbleProtocolEnabled() }
    }

    /** Clear [streamingMessage] after generation ends and the saved message is confirmed in [messages]. */
    private fun observeStreamingCleanup() {
        viewModelScope.launch {
            _uiState.collect { state ->
                val streamMsg = state.streamingMessage ?: return@collect
                if (state.isSending) return@collect
                // Streaming has ended. Clear when saved message matches, or when content is empty (cancelled).
                val shouldClear = streamMsg.content.isBlank() ||
                    state.messages.lastOrNull()?.let { last ->
                        last.role == AiMessageRole.ASSISTANT && (last.content == streamMsg.content || last.parts.isNotEmpty() && last.parts == streamMsg.parts)
                    } == true
                if (shouldClear) {
                    _uiState.update { it.copy(streamingMessage = null, regeneratingMessageId = null) }
                }
            }
        }
    }

    fun onIntent(intent: AiChatIntent) {
        when (intent) {
            AiChatIntent.NewConversation -> createConversation()
            is AiChatIntent.SelectConversation -> selectConversation(intent.id)
            is AiChatIntent.SendMessage -> sendMessage(intent.content)
            is AiChatIntent.AddAttachmentsFromUris -> addAttachmentsFromUris(intent.uris)
            is AiChatIntent.RemovePendingAttachment -> removePendingAttachment(intent.id)
            AiChatIntent.StopGenerating -> stopGenerating()
            is AiChatIntent.ToggleToolApproval -> toggleToolApproval(intent.callId)
            is AiChatIntent.ToggleToolSubItemApproval -> toggleToolSubItemApproval(intent.callId, intent.subItemId)
            is AiChatIntent.UpdateToolFeedback -> updateToolFeedback(intent.callId, intent.feedback)
            is AiChatIntent.UpdateToolBatchFeedback -> updateToolBatchFeedback(intent.feedback)
            is AiChatIntent.UpdateToolArgs -> updateToolArgs(intent.callId, intent.json)
            is AiChatIntent.ToggleFieldChangeApproval -> toggleFieldChangeApproval(intent.callId, intent.path)
            is AiChatIntent.ToggleQuestionOption -> toggleQuestionOption(intent.callId, intent.questionId, intent.optionId)
            is AiChatIntent.UpdateQuestionCustomText -> updateQuestionCustomText(intent.callId, intent.questionId, intent.text)
            is AiChatIntent.SubmitUserQuestions -> submitUserQuestions(intent.callId)
            AiChatIntent.DismissUserQuestions -> dismissUserQuestions()
            AiChatIntent.ConfirmHabitMemory -> confirmHabitMemory()
            AiChatIntent.RejectHabitMemory -> rejectHabitMemory()
            is AiChatIntent.UpdateTtsSecretFill -> updateTtsSecretFill(
                intent.callId,
                intent.apiKey,
                intent.secretKey,
            )
            AiChatIntent.ConfirmPendingTools -> confirmPendingTools()
            AiChatIntent.RejectPendingTools -> rejectPendingTools()
            is AiChatIntent.SetOutputMode -> setOutputMode(intent.mode)
            AiChatIntent.AcceptPlan -> acceptPlan()
            AiChatIntent.RejectPlan -> rejectPlan()
            is AiChatIntent.EditPlan -> editPlan(intent.content)
            AiChatIntent.CancelPlanEdit -> cancelPlanEdit()
            is AiChatIntent.PlanRevisionFeedback -> revisePlan(intent.selectedText, intent.feedback)
            is AiChatIntent.RejectedPlanFeedback -> feedbackRejectedPlan(intent.planFileId, intent.feedback)
            is AiChatIntent.AddPlanFeedback -> addPlanFeedback(intent.selectedText, intent.feedback)
            is AiChatIntent.RemovePlanFeedback -> removePlanFeedback(intent.feedbackId)
            AiChatIntent.SubmitAllPlanFeedback -> submitAllPlanFeedback()
            is AiChatIntent.OpenPlan -> openPlan(intent.planId)
            AiChatIntent.DismissPlanDetail -> dismissPlanDetail()
            AiChatIntent.ToggleWebSearch -> toggleWebSearch()
            is AiChatIntent.UpdateReasoningLevel -> updateReasoningLevel(intent.level)
            is AiChatIntent.RegenerateMessage -> regenerateMessage(intent.messageId)
            is AiChatIntent.SwitchBranch -> switchBranch(intent.messageId, intent.direction)
            is AiChatIntent.DeleteConversation -> deleteConversation(intent.id)
            is AiChatIntent.RenameConversation -> renameConversation(intent.id, intent.title)
            // Writing mode
            is AiChatIntent.SwitchMode -> switchMode(intent.type)
            is AiChatIntent.CreateConversationWithCharacter -> createConversationWithCharacter(intent.characterCardId)
            is AiChatIntent.UpdateConversationCharacter -> updateConversationCharacter(intent.characterCardId)
            is AiChatIntent.UpdateConversationCharacters -> updateConversationCharacters(intent.cardIds)
            is AiChatIntent.UpdateActionPrompt -> updateActionPrompt(intent.category, intent.content)
            is AiChatIntent.SaveCharacterCard -> saveCharacterCard(
                intent.name, intent.description, intent.openingLine, intent.worldBookIds, intent.cardId,
                intent.personality, intent.scenario, intent.exampleDialogues, intent.postHistoryInstructions, intent.alternateOpenings,
                intent.aliasesJson, intent.voiceGender, intent.voiceAgeBand,
                intent.bookUrl, intent.bookName, intent.bookAuthor, intent.dramaticRole, intent.avatarPath,
            )
            is AiChatIntent.ImportCharacterCardJson -> importCharacterCardJson(intent.json)
            is AiChatIntent.ImportCharacterCardBytes -> importCharacterCardBytes(intent.bytes)
            is AiChatIntent.SaveUserCard -> saveUserCard(intent.name, intent.description, intent.enabled)
            AiChatIntent.ToggleUserCardEnabled -> toggleUserCardEnabled()
            is AiChatIntent.DeleteCharacterCard -> deleteCharacterCard(intent.cardId)
            is AiChatIntent.SaveWritingPrompt -> saveWritingPrompt(intent.name, intent.content, intent.category, intent.promptId)
            is AiChatIntent.DeleteWritingPrompt -> deleteWritingPrompt(intent.promptId)
            is AiChatIntent.TogglePromptEnabled -> togglePromptEnabled(intent.promptId)
            is AiChatIntent.ToggleConversationSkill -> toggleConversationSkill(intent.skillId)
            AiChatIntent.ResetConversationSkills -> resetConversationSkills()
            AiChatIntent.ContinueWriting -> continueWriting()
            is AiChatIntent.AiHelpReply -> aiHelpReply(intent.draftText, intent.inputMode)
            is AiChatIntent.DeleteMessage -> deleteMessage(intent.messageId)
            is AiChatIntent.EditMessage -> editMessage(intent.messageId, intent.newContent)
            is AiChatIntent.RegenerateFromUserMessage -> regenerateFromUserMessage(intent.userMessageId)
            is AiChatIntent.ForkConversation -> forkConversation(intent.messageId)
            is AiChatIntent.ForkConversationToSingleChat -> forkConversationToSingleChat(intent.characterCardId)
            is AiChatIntent.SetWritingSubMode -> setWritingSubMode(intent.subMode)
            AiChatIntent.LoadMoreMessages -> loadMoreMessages()
            is AiChatIntent.UpdateDraftInput -> updateDraftInput(intent.text)
            AiChatIntent.CompressContext -> compressContext()
            is AiChatIntent.SelectSuggestion -> selectSuggestion(intent.text)
            AiChatIntent.DismissSuggestions -> dismissSuggestions()
            AiChatIntent.ToggleGalgame -> toggleGalgame()
            AiChatIntent.RegenerateGalgameHud -> generateGalgameHud()
            AiChatIntent.RecreateGalgameHud -> recreateGalgameHud()
            is AiChatIntent.ImportHudHtml -> importHudHtml(intent.html)
            AiChatIntent.ToggleInterCharacterChat -> toggleInterCharacterChat()
            AiChatIntent.ToggleDialogueHighlight -> toggleDialogueHighlight()
            AiChatIntent.ToggleRoleplayDialogueBubble -> toggleRoleplayDialogueBubble()
            is AiChatIntent.SetStructuredAutoMaintain -> setStructuredAutoMaintain(intent.enabled)
            AiChatIntent.TogglePostEdit -> togglePostEdit()
            is AiChatIntent.SaveOutline -> saveOutline(intent.content, intent.enabled)
            AiChatIntent.ToggleOutlineEnabled -> toggleOutlineEnabled()
            is AiChatIntent.GenerateOutline -> generateOutlineFromSheet(supplement = false, outlineKind = intent.outlineKind)
            is AiChatIntent.SupplementOutline -> generateOutlineFromSheet(
                supplement = true,
                explicitContent = intent.content,
                outlineKind = intent.outlineKind,
            )
            AiChatIntent.DeleteOutline -> deleteOutline()
            AiChatIntent.CleanupOrphanOutlines -> cleanupOrphanOutlines()
            is AiChatIntent.SelectOutlineBranch -> selectOutlineBranch(intent.optionId)
            AiChatIntent.ShowOutlineExportDialog -> showOutlineExportDialog()
            AiChatIntent.DismissOutlineExportDialog -> dismissOutlineExportDialog()
            AiChatIntent.CopyOutlineExport -> copyOutlineExport()
            AiChatIntent.ShareOutlineExport -> shareOutlineExport()
            AiChatIntent.ShowOutlineImportDialog -> showOutlineImportDialog()
            AiChatIntent.DismissOutlineImportDialog -> dismissOutlineImportDialog()
            is AiChatIntent.UpdateOutlineImportJson -> updateOutlineImportJson(intent.json)
            AiChatIntent.ConfirmOutlineImport -> confirmOutlineImport()
            AiChatIntent.ShowOutlineImportFromConversation -> showOutlineConversationPicker("import")
            AiChatIntent.ShowOutlineExportToConversation -> showOutlineConversationPicker("export")
            AiChatIntent.DismissOutlineConversationPicker -> dismissOutlineConversationPicker()
            is AiChatIntent.SelectConversationForOutlineTransfer -> selectConversationForOutlineTransfer(intent.conversationId)
            AiChatIntent.ConfirmOutlineOverwrite -> confirmOutlineOverwrite()
            AiChatIntent.DismissOutlineOverwriteConfirm -> dismissOutlineOverwriteConfirm()
            AiChatIntent.ShowOutlineBookPicker -> showOutlineBookPicker()
            AiChatIntent.DismissOutlineBookPicker -> dismissOutlineBookPicker()
            is AiChatIntent.SelectOutlineBook -> selectOutlineBook(intent.book)
            AiChatIntent.ClearOutlineBookSource -> clearOutlineBookSource()
            is AiChatIntent.RestoreSnapshot -> prepareSnapshotUndo(intent.snapshotId)
            AiChatIntent.ConfirmSnapshotUndo -> confirmSnapshotUndo()
            AiChatIntent.DismissSnapshotUndo ->
                _uiState.update { it.copy(pendingSnapshotUndo = null) }
            AiChatIntent.ShowWorkspaceSheet -> showWorkspaceSheet()
            AiChatIntent.DismissWorkspaceSheet -> _uiState.update { it.copy(showWorkspaceSheet = false) }
            AiChatIntent.ExportWorkspace -> exportWorkspace()
            AiChatIntent.ShowWorkspaceClonePicker -> showWorkspaceClonePicker()
            AiChatIntent.DismissWorkspaceClonePicker -> _uiState.update { it.copy(showWorkspaceClonePicker = false) }
            is AiChatIntent.CloneWorkspaceFromConversation -> cloneWorkspaceFromConversation(intent.sourceConversationId)
            is AiChatIntent.UpdateWorkspaceImportJson -> _uiState.update { it.copy(workspaceImportJson = intent.json) }
            AiChatIntent.ImportWorkspaceJson -> importWorkspaceJson()
            is AiChatIntent.UpdateWorkspaceWorldBookIds -> updateWorkspaceWorldBookIds(intent.worldBookIds)
            AiChatIntent.ShowAdoptPreview -> showAdoptPreview()
            AiChatIntent.DismissAdoptPreview -> _uiState.update { it.copy(adoptPreview = null, adoptPreviewLoading = false, adoptSelectedTypes = emptySet()) }
            AiChatIntent.ConfirmAdoptToBook -> confirmAdoptToBook()
            is AiChatIntent.ToggleAdoptType -> toggleAdoptType(intent.type)
            AiChatIntent.ShowWorkspaceBookPicker -> showWorkspaceBookPicker()
            AiChatIntent.DismissWorkspaceBookPicker -> _uiState.update { it.copy(showWorkspaceBookPicker = false) }
            is AiChatIntent.SelectWorkspaceBook -> selectWorkspaceBook(intent.book)
            // HTML App Player
            is AiChatIntent.LaunchHtmlApp -> launchHtmlApp(intent.messageId)
            AiChatIntent.DismissHtmlApp -> dismissHtmlApp()
            is AiChatIntent.GameSendToChat -> gameSendToChat(intent.text)
            is AiChatIntent.GameNotifyContext -> gameNotifyContext(intent.json)
            else -> {}
        }
    }

    // ---- Conversation lifecycle ----

    private fun observeConversations() {
        viewModelScope.launch {
            aiChatGateway.observeConversations().collect { conversations ->
                val selectedId = currentConversationId.value
                if (selectedId != null && conversations.none { it.id == selectedId }) {
                    currentConversationId.value = null
                }
                _uiState.update { current ->
                    val previousConversations = current.conversations.associateBy { it.id }
                    current.copy(
                        conversations = conversations.map {
                            val previous = previousConversations[it.id]
                            AiChatConversationUi(
                                id = it.id, title = it.title, updatedAt = it.updatedAt,
                                isSelected = it.id == (currentConversationId.value ?: current.currentConversationId),
                                providerName = previous?.providerName.orEmpty(),
                                modelName = previous?.modelName.orEmpty(),
                                type = it.type,
                                characterCardId = it.characterCardId,
                                promptIds = it.promptIds,
                            )
                        }.toImmutableList()
                    )
                }
                if (currentConversationId.value == null) {
                    conversations.firstOrNull()?.let { selectConversation(it.id) }
                        ?: createConversation()
                }
            }
        }
    }

    private fun observeCurrentMessages() {
        viewModelScope.launch {
            currentConversationId
                .filterNotNull()
                .flatMapLatest { cid ->
                    aiChatGateway.observeRecentSelectedMessages(cid, 30)
                }
                .collect { messages ->
                    val cid = currentConversationId.value
                    val dbIds = messages.map { it.id }.toSet()
                    pendingDeletedMessageIds.removeAll { it !in dbIds }
                    recentMessages = visibleMessages(messages.map { msg ->
                        messageEntityToUi(msg).copy(totalBranches = 1)
                    })
                    if (cid != null) {
                        val branchCounts = runCatching { aiChatGateway.getBranchCounts(cid) }.getOrNull()
                        recentMessages = recentMessages.map { msg ->
                            if (msg.role == AiMessageRole.ASSISTANT && msg.parentMessageId != null) {
                                msg.copy(totalBranches = branchCounts?.get(msg.parentMessageId) ?: 1)
                            } else msg
                        }
                    }
                    // Trim olderMessages to avoid overlap with recentMessages
                    if (recentMessages.isNotEmpty() && olderMessages.isNotEmpty()) {
                        val recentIds = recentMessages.map { it.id }.toSet()
                        olderMessages = olderMessages.filter { it.id !in recentIds }
                    }
                    val allMsgs = allLoadedMessages
                    _uiState.update { current ->
                        val newLimit = if (allMsgs.size > current.displayLimit) allMsgs.size
                            else current.displayLimit
                        val mayHaveMore = messages.size >= 30 || olderMessages.isNotEmpty()
                        current.copy(
                            messages = allMsgs.toImmutableList(),
                            displayLimit = newLimit,
                            hasMoreHistory = current.hasMoreHistory && mayHaveMore,
                            messagesReady = true,
                        )
                    }
                    refreshContextUsageEstimate(getContextUiMessages())
                }
        }
    }

    private fun observeCharacterCards() {
        viewModelScope.launch {
            aiCharacterCardGateway.observeAll().collect { cards ->
                Log.d("AiTool", "CharacterCards: Flow emitted ${cards.size} cards")
                val uiById = cards.associate { card ->
                    val count = runCatching {
                        aiChatGateway.countConversationsByCharacter(card.id)
                    }.getOrNull() ?: 0
                    card.id to card.toUi(count)
                }
                _uiState.update { current ->
                    val refreshedSelected = current.selectedCharacterCards.mapNotNull { uiById[it.id] }
                    val refreshedPrimary = current.selectedCharacterCard?.id?.let { uiById[it] }
                        ?: refreshedSelected.singleOrNull()
                    current.copy(
                        characterCards = uiById.values.toList().toImmutableList(),
                        selectedCharacterCards = refreshedSelected.toImmutableList(),
                        selectedCharacterCard = refreshedPrimary,
                    )
                }
            }
        }
    }

    private fun observeWritingPrompts() {
        viewModelScope.launch {
            aiWritingPromptGateway.observeAll().collect { prompts ->
                Log.d("AiTool", "observeWritingPrompts: loaded ${prompts.size} prompts: ${prompts.joinToString { "${it.name}(${it.category}) enabled=${it.enabled}" }}")
                _uiState.update { current ->
                    current.copy(
                        writingPrompts = prompts.map { prompt ->
                            AiWritingPromptUi(
                                id = prompt.id,
                                name = prompt.name,
                                content = prompt.content,
                                category = prompt.category,
                                enabled = prompt.enabled,
                            )
                        }.toImmutableList()
                    )
                }
            }
        }
    }

    private fun observeAvailableSkills() {
        viewModelScope.launch {
            aiSkillGateway.ensureSeeded()
            aiSkillGateway.observeAll().collect { skills ->
                _uiState.update { current ->
                    current.copy(
                        availableSkills = skills
                            .sortedBy { it.sortOrder }
                            .map { skill ->
                                AiConversationSkillUi(
                                    skillId = skill.skillId,
                                    name = skill.name,
                                    description = skill.description.ifBlank { skill.hint },
                                    mode = skill.mode,
                                    globallyEnabled = skill.enabled,
                                )
                            }
                            .toImmutableList(),
                    )
                }
            }
        }
    }

    private fun observeWorldBooks() {
        viewModelScope.launch {
            aiWorldBookGateway.observeAll().collect { books ->
                _uiState.update { it.copy(enabledWorldBooks = books.filter { wb -> wb.enabled }.toImmutableList()) }
            }
        }
    }

    private fun observeMemoryTableData() {
        viewModelScope.launch {
            combine(
                aiMemoryTableGateway.observeAll(),
                currentConversationId
            ) { tables, convId -> tables to convId }.collect { (tables, convId) ->
                // Writing-mode injection: scope to this conversation's tables only.
                val enabledTables = tables.filter {
                    it.enabled && it.conversationId.isNotBlank() && it.conversationId == convId
                }
                if (enabledTables.isEmpty()) {
                    _uiState.update { it.copy(memoryTableContent = "") }
                    return@collect
                }
                // Compact table preview for UI sheets; prompt prefetch uses WritingWorkspaceIndexBuilder
                val content = buildString {
                    enabledTables.forEach { table ->
                        val columns = runCatching {
                            io.legado.app.utils.GSON.fromJson(table.columns, Array<String>::class.java).toList()
                        }.getOrNull() ?: return@forEach
                        try {
                            val rows = aiMemoryTableGateway.getRows(table.id)
                            if (rows.isEmpty()) return@forEach
                            append("\n### ${table.name}\n")
                            append("| " + columns.joinToString(" | ") + " |\n")
                            append("|" + columns.joinToString("|") { "---" } + "|\n")
                            rows.take(20).forEach { row ->
                                val data = io.legado.app.utils.parseJsonStringMap(row.rowData)
                                if (data.isEmpty()) return@forEach
                                append("| " + columns.joinToString(" | ") { col ->
                                    (data[col]?.toString() ?: "").replace("\n", " ").take(100)
                                } + " |\n")
                            }
                            if (rows.size > 20) {
                                append("*（还有 ${rows.size - 20} 行未显示）*\n")
                            }
                        } catch (_: Exception) {}
                    }
                }
                _uiState.update { it.copy(memoryTableContent = content) }
            }
        }
    }

    private fun observeOutline() {
        viewModelScope.launch {
            currentConversationId.filterNotNull().flatMapLatest { convId ->
                aiOutlineGateway.observeByConversation(convId)
            }.collect { outline ->
                val content = outline?.content.orEmpty()
                val doc = OutlineMarkdownCodec.decode(content)
                _uiState.update {
                    it.copy(
                        outlineContent = content,
                        outlineDocument = doc,
                        outlineEnabled = outline?.enabled == true,
                        outlineBookUrl = outline?.bookUrl.orEmpty(),
                        outlineBookName = outline?.bookName.orEmpty(),
                        outlineBookAuthor = outline?.bookAuthor.orEmpty(),
                        outlineBranchChoice = resolveOutlineBranchChoiceUi(
                            content = content,
                            enabled = outline?.enabled == true,
                            writingSubMode = it.writingSubMode,
                        ),
                    )
                }
            }
        }
    }

    private fun createConversation() {
        viewModelScope.launch {
            runCatching {
                val conv = aiChatGateway.createConversation()
                val lastType = if (appCtx.getPrefBoolean(
                        io.legado.app.constant.PreferKey.lastConversationType, false
                    )) "writing" else "chat"
                aiChatGateway.updateConversationType(conv.id, lastType)
                conv
            }.onSuccess { selectConversation(it.id) }
                .onFailure { _effects.tryEmit(AiChatEffect.ShowMessage(it.message ?: "Failed")) }
        }
    }

    internal fun selectConversation(id: String) {
        Log.d("AiChatSwitch", "selectConversation called: newId=$id oldId=${currentConversationId.value}")
        streamingJob?.cancel()
        // Do not cancel maintainJob: let DB writes finish; only clear UI flag for the old chat.
        val previousId = currentConversationId.value
        val draftSnapshot = currentDraftText
        draftPersistJob?.cancel()
        draftPersistJob = null
        _uiState.update {
            it.copy(
                isMaintainingStructuredData = false,
                messagesReady = false,
                pendingAttachments = persistentListOf(),
                isProcessingAttachments = false,
                isCompressing = false,
                compressedSummary = "",
            )
        }
        recentMessages = emptyList()
        olderMessages = emptyList()
        pendingDeletedMessageIds.clear()
        planFileIdByMessageId.clear()
        planCardByMessageId.clear()
        currentConversationId.value = id
        Log.d("AiChatSwitch", "selectConversation: currentConversationId set to $id")
        viewModelScope.launch {
            if (previousId != null && previousId != id) {
                runCatching { aiChatGateway.updateConversationDraft(previousId, draftSnapshot) }
            }
            val conversation = aiChatGateway.getConversation(id)
            val draft = conversation?.draftText.orEmpty()
            currentDraftText = draft
            val preset = aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
                ?: aiProfileGateway.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER)
            val providerName = preset?.model?.provider?.name.orEmpty()
            val modelName = preset?.model?.displayName.orEmpty()
            val contextWindow = preset?.model?.contextWindow ?: 0

            // Load character card info
            val charCardId = conversation?.characterCardId
            val charCard = charCardId?.let { aiCharacterCardGateway.getById(it) }
            val selectedCharCardUi = charCard?.let { card ->
                val count = aiChatGateway.countConversationsByCharacter(card.id)
                card.toUi(count)
            }

            // Load multi-character cards from characterCardIds (JSON or CSV)
            val cardIds = AiIdListCodec.parse(conversation?.characterCardIds)
            val selectedCards = cardIds.mapNotNull { id ->
                aiCharacterCardGateway.getById(id)?.toUi()
            }
            // Single character via multi-select: also populate selectedCharacterCard for backward compat
            val resolvedCharCardUi = selectedCharCardUi
                ?: selectedCards.singleOrNull()?.let { card ->
                    val count = aiChatGateway.countConversationsByCharacter(card.id)
                    card.copy(conversationCount = count)
                }

                _uiState.update { current ->
                    val subMode = normalizeWritingSubMode(conversation?.writingSubMode)
                    val convOutputMode = runCatching {
                        AiOutputMode.valueOf(
                            conversation?.outputMode?.uppercase().orEmpty(),
                        )
                    }.getOrDefault(AiOutputMode.ASK)
                    current.copy(
                        currentConversationId = id,
                        writingRoundCount = 0,
                        conversationType = conversation?.type ?: "chat",
                        writingSubMode = subMode,
                        outputMode = convOutputMode,
                        pendingPlan = null,
                        compressedSummary = conversation?.compressedSummary.orEmpty(),
                    reasoningLevel = conversation?.reasoningLevel?.let {
                        runCatching { AiReasoningLevel.valueOf(it.uppercase()) }.getOrNull()
                    } ?: current.reasoningLevel,
                    streamingMessage = null, isSending = false,
                    selectedCharacterCard = resolvedCharCardUi,
                    selectedCharacterCards = selectedCards.toImmutableList(),
                    userName = conversation?.userName.orEmpty(),
                    userDescription = conversation?.userDescription.orEmpty(),
                    userCardEnabled = conversation?.userCardEnabled == true,
                    displayLimit = AiChatUiState.DEFAULT_DISPLAY_LIMIT,
                    isLoadingHistory = false,
                    hasMoreHistory = true,
                    contextWindow = contextWindow,
                    contextInputBudget = generationUseCase.contextInputBudget(contextWindow),
                    contextTokensUsed = conversation?.contextPromptTokens ?: 0,
                    contextTokensSource = conversation?.contextTokensSource
                        ?: AiChatUiState.CONTEXT_SOURCE_ESTIMATE,
                    contextCalibrationScale = conversation?.contextCalibrationScale ?: 1f,
                    suggestions = persistentListOf(),
                    galgameHudHtml = conversation?.galgameHudHtml ?: "",
                    webSearchArmed = false,
                    conversationSkillIds = conversation?.skillIds,
                    workspaceId = conversation?.workspaceId,
                    workspaceName = "",
                    workspaceCharacterCardIds = "",
                    workspaceWorldBookIds = "",
                    outlineBranchChoice = resolveOutlineBranchChoiceUi(
                        content = current.outlineContent,
                        enabled = current.outlineEnabled,
                        writingSubMode = subMode,
                    ),
                    conversations = current.conversations.map {
                        if (it.id == id) it.copy(isSelected = true, providerName = providerName, modelName = modelName)
                        else it.copy(isSelected = false)
                    }.toImmutableList()
                )
            }
            // 恢复该会话的 pending 计划（跨会话审批面板可复活）+ 标记计划消息。
            restorePendingPlanAndPlanMessages(id)
            _effects.emit(AiChatEffect.SetInputText(draft, forConversationId = id))
            if (conversation?.type == "writing") {
                runCatching { ensureWorkspaceForCurrent(id) }
                val userTurns = countWritingUserTurns(id)
                _uiState.update { it.copy(writingRoundCount = userTurns) }
            }
            refreshHabitMemories()
            refreshContextUsageEstimate(getContextUiMessages())
            refreshMultiBubbleProtocolEnabled()
        }
    }

    internal fun refreshHabitMemories() {
        viewModelScope.launch {
            val global = runCatching { aiMemoryGateway.getGlobal() }.getOrDefault(emptyList())
            val replyStyle = global.firstOrNull { it.key == ChatHabitKeys.REPLY_STYLE }?.value.orEmpty()
            _uiState.update {
                it.copy(habitReplyStyle = replyStyle)
            }
        }
    }

    // ---- Plan mode（审批） ----

    /** Plan mode 轮收尾：工具已写文件（write_file("plan://…") / edit_file("plan://…")），读文件刷新 pendingPlan。 */
    internal suspend fun refreshPlanState(convId: String, messageId: String?, reasoning: String) {
        val row = aiPlanGateway.getPendingByConversation(convId) ?: return
        val isNewPlan = row.messageId.isBlank() && messageId != null
        // 新计划轮：工具建行时 messageId 为空，这里回填。
        if (isNewPlan) {
            aiPlanGateway.upsert(row.copy(messageId = messageId))
            planFileIdByMessageId[messageId] = row.id
        }
        Log.d(
            "AiPlan",
            "refreshPlanState conv=$convId row=${row.id} msg=$messageId isNew=$isNewPlan " +
                "rowMsg=${row.messageId} mapSize=${planFileIdByMessageId.size}",
        )
        val content = PlanFileStore.read(appCtx, convId, row.id).orEmpty()
        val current = _uiState.value.pendingPlan
        val planReasoning = if (isNewPlan) {
            reasoning.takeIf { r -> r.isNotBlank() }
        } else {
            // 修订轮：保留首轮的 planReasoning，不用本轮推理覆盖。
            current?.planReasoning
        }
        _uiState.update {
            it.copy(
                pendingPlan = PendingPlanUi(
                    messageId = row.messageId.ifBlank { messageId ?: "" },
                    planContent = content,
                    planReasoning = planReasoning,
                ),
            )
        }
        // 同步时间线计划卡片元数据（最新内容 + 状态）。
        val cardMsgId = row.messageId.ifBlank { messageId ?: "" }
        if (cardMsgId.isNotBlank()) {
            planCardByMessageId[cardMsgId] = PlanCardMeta(content = content, status = row.status, revision = row.revision)
        }
        // 刚生成的计划消息在 persistAssistantBubbles 时已换入 recentMessages，
        // 但那时 planFileIdByMessageId 尚未填充，需重投消息让「📋 计划」链接卡渲染。
        val reloaded = allLoadedMessages.toImmutableList()
        Log.d(
            "AiPlan",
            "refreshPlanState emit: recentIds=${recentMessages.map { it.id }} " +
                "map=${planFileIdByMessageId} marked=${reloaded.count { it.isPlanMessage }}",
        )
        _uiState.update { it.copy(messages = reloaded) }
    }

    /** 暂存一条段落反馈（不立即发起修订）。 */
    private fun addPlanFeedback(selectedText: String, feedback: String) {
        val plan = _uiState.value.pendingPlan ?: return
        if (feedback.isBlank()) return
        // 暂存时算好行号区间（锚定 plan.md 原文），供消息构建与面板展示复用。
        val range = if (selectedText.isNotBlank()) locatePlanLines(plan.planContent, selectedText) else null
        _uiState.update {
            it.copy(
                pendingPlan = plan.copy(
                    stagedFeedback = (plan.stagedFeedback + StagedFeedbackUi(
                        id = "fb_${java.util.UUID.randomUUID().toString().replace("-", "")}",
                        selectedText = selectedText,
                        feedback = feedback,
                        startLine = range?.first,
                        endLine = range?.last,
                    )).toImmutableList(),
                ),
            )
        }
    }

    /** 移除暂存队列中的一条反馈。 */
    private fun removePlanFeedback(feedbackId: String) {
        val plan = _uiState.value.pendingPlan ?: return
        _uiState.update {
            it.copy(
                pendingPlan = plan.copy(
                    stagedFeedback = plan.stagedFeedback.filter { f -> f.id != feedbackId }.toImmutableList(),
                ),
            )
        }
    }

    /** 拒绝后的计划卡片上提交反馈修订意见：标记回 pending → 设 pendingPlan → 发起修订轮。 */
    private fun feedbackRejectedPlan(planFileId: String, feedback: String) {
        if (feedback.isBlank()) return
        if (_uiState.value.isSending) {
            _effects.tryEmit(AiChatEffect.ShowMessage("请等待当前操作完成"))
            return
        }
        viewModelScope.launch {
            val row = aiPlanGateway.getById(planFileId) ?: return@launch
            if (row.status != AiPlan.STATUS_REJECTED) return@launch
            // 重新标记为 pending，卡片状态即时刷新。
            aiPlanGateway.upsert(row.copy(status = AiPlan.STATUS_PENDING))
            planCardByMessageId[row.messageId]?.let {
                planCardByMessageId[row.messageId] = it.copy(status = AiPlan.STATUS_PENDING)
            }
            _uiState.update { it.copy(messages = allLoadedMessages.toImmutableList()) }
            val content = PlanFileStore.read(appCtx, row.conversationId, row.id).orEmpty()
            // 设 pendingPlan 以通过 startPlanRevision 守卫。
            _uiState.update {
                it.copy(pendingPlan = PendingPlanUi(messageId = row.messageId, planContent = content))
            }
            val feedbackContent = buildString {
                append("【反馈】（对计划整体）\n")
                append("修改意见：\n$feedback\n")
            }
            startPlanRevision(feedbackContent)
        }
    }

    /** 发起计划修订轮：静默续跑，修订反馈与修订过程不落时间线。 */
    internal fun startPlanRevision(feedbackContent: String) {
        if (_uiState.value.pendingPlan == null) return
        if (_uiState.value.isSending) {
            _effects.tryEmit(AiChatEffect.ShowMessage(appCtx.getString(R.string.ai_plan_revision_busy)))
            return
        }
        if (feedbackContent.isBlank()) return
        // 提交意见后立即收起审批面板，静默跑修订轮；新计划好了由 refreshPlanState 重弹面板。
        _uiState.update { it.copy(pendingPlan = null) }
        continuePlanRevision(feedbackContent)
    }

    /** 提交暂存队列中全部反馈，发起一次修订轮（消息已整体组装，不再二次包框架）。 */
    private fun submitAllPlanFeedback() {
        val plan = _uiState.value.pendingPlan ?: return
        val items = plan.stagedFeedback
        if (items.isEmpty()) return
        // busy 守卫先于清空队列，避免暂存反馈丢失。
        if (_uiState.value.isSending) {
            _effects.tryEmit(AiChatEffect.ShowMessage(appCtx.getString(R.string.ai_plan_revision_busy)))
            return
        }
        // 只带反馈本体；操作指令由系统级 PLAN_REVISION_DIRECTIVE 提供，本轮不落时间线。
        val combinedMsg = buildString {
            items.forEachIndexed { i, fb ->
                append("【反馈 ${i + 1}】")
                if (fb.startLine != null) {
                    append("（位置：第 ${fb.startLine} 行")
                    if (fb.endLine != null && fb.endLine != fb.startLine) append("-${fb.endLine}")
                    append("）")
                }
                if (fb.selectedText.isNotBlank()) {
                    append("\n选中内容：\n```\n${fb.selectedText}\n```\n")
                }
                append("修改意见：\n${fb.feedback}\n\n")
            }
        }
        // 清空暂存队列后再发起修订。
        _uiState.update { it.copy(pendingPlan = plan.copy(stagedFeedback = persistentListOf())) }
        startPlanRevision(combinedMsg)
    }

    /** 点击时间线计划链接卡：pending 重开审批面板，已结束打开只读详情。 */
    private fun openPlan(planId: String) {
        viewModelScope.launch {
            val row = aiPlanGateway.getById(planId) ?: return@launch
            val content = PlanFileStore.read(appCtx, row.conversationId, row.id).orEmpty()
            if (row.status == AiPlan.STATUS_PENDING) {
                _uiState.update {
                    it.copy(pendingPlan = PendingPlanUi(messageId = row.messageId, planContent = content))
                }
            } else {
                _uiState.update {
                    it.copy(
                        planDetailView = PlanDetailUi(
                            planId = row.id,
                            messageId = row.messageId,
                            content = content,
                            status = row.status,
                            revision = row.revision,
                            updatedAt = row.updatedAt,
                        ),
                    )
                }
            }
        }
    }

    private fun dismissPlanDetail() {
        _uiState.update { it.copy(planDetailView = null) }
    }

    // Mutable cache of the current batch of mutation tool calls awaiting approval
    internal var approvedToolCalls: List<AiToolCall> = emptyList()

    private fun updateReasoningLevel(level: AiReasoningLevel) {
        _uiState.update { it.copy(reasoningLevel = level) }
        val cid = currentConversationId.value ?: return
        viewModelScope.launch {
            aiChatGateway.updateReasoningLevel(cid, level.name.lowercase())
        }
    }

    private fun deleteConversation(conversationId: String) {
        val isCurrent = currentConversationId.value == conversationId
        conversationToolGroupStates.remove(conversationId)
        if (isCurrent) {
            currentConversationId.value = null
            _uiState.update {
                it.copy(
                    messages = persistentListOf(),
                    currentConversationId = null,
                    streamingMessage = null,
                    messagesReady = false,
                )
            }
        }
        viewModelScope.launch {
            aiChatGateway.deleteConversation(conversationId)
            // 清理会话下全部 HTML App（实体 + 文件）。
            runCatching { aiHtmlAppGateway.deleteByConversation(conversationId) }
        }
    }

    private fun renameConversation(conversationId: String, title: String) {
        viewModelScope.launch { aiChatGateway.updateConversationTitle(conversationId, title) }
    }

    internal fun generateConversationTitle(conversationId: String, userContent: String, assistantContent: String) {
        viewModelScope.launch {
            try {
                val title = generationUseCase.generateTitle(userContent, assistantContent, _uiState.value.reasoningLevel)
                if (title.isNotBlank()) aiChatGateway.updateConversationTitle(conversationId, title)
            } catch (_: Exception) {
                aiChatGateway.updateConversationTitle(conversationId, userContent.take(24))
            }
        }
    }

    internal suspend fun messageEntityToUi(msg: io.legado.app.data.entities.AiChatMessage): AiChatMessageUi {
        val parts = AiMessagePartJson.decode(msg.partsJson)
        val rawText = parts.textContent()
        val (speakerId, speakerName, displayContent) = speakerUiFields(msg.speakerCardId, rawText)
        val speakerColorIndex = speakerId?.hashCode()?.let {
            kotlin.math.abs(it) % 8
        } ?: speakerName.hashCode().let { kotlin.math.abs(it) % 8 }
        val htmlApps = parts.htmlAppRefParts().mapNotNull { ref ->
            aiHtmlAppGateway.getById(ref.appId)?.let { AiChatHtmlAppUi(appId = it.id, title = it.title) }
        }.toImmutableList()
        return AiChatMessageUi(
            id = msg.id, role = msg.role,
            parts = partsWithStrippedSpeaker(parts, speakerName).toImmutableList(),
            content = displayContent,
            reasoning = parts.reasoningContent(),
            toolTrace = parts.toolTraceText(),
            createdAt = msg.createdAt,
            thinkingDuration = msg.thinkingDuration,
            bookResults = parts.extractBookResults().toImmutableList(),
            htmlApps = htmlApps,
            branchIndex = msg.branchIndex,
            parentMessageId = msg.parentMessageId,
            speakerCardId = speakerId,
            speakerName = speakerName,
            speakerColorIndex = speakerColorIndex,
            excludeFromContext = msg.excludeFromContext,
        )
    }

    internal fun AiChatPendingAttachmentUi.toAttachmentPart(): AiMessagePart.Attachment =
        AiMessagePart.Attachment(
            localPath = localPath,
            mimeType = mimeType,
            displayName = displayName,
            kind = kind,
            sizeBytes = sizeBytes,
            extractedTextPath = extractedTextPath,
            truncated = truncated,
        )

    suspend fun getOutlineForConversation(convId: String): Pair<String, Boolean> {
        val outline = aiOutlineGateway.getByConversation(convId)
        return outline?.content.orEmpty() to (outline?.enabled != false)
    }

    suspend fun getUserCardForConversation(convId: String): Triple<String, String, Boolean> {
        val conv = aiChatGateway.getConversation(convId)
            ?: return Triple("", "", false)
        return Triple(conv.userName, conv.userDescription, conv.userCardEnabled)
    }

}
