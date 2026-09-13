package io.legado.app.ui.ai.chat

import android.util.Log
import io.legado.app.R
import io.legado.app.utils.putPrefBoolean
import splitties.init.appCtx
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.AiToolConfig
import io.legado.app.data.entities.AiPlan
import io.legado.app.data.entities.AiPromptTemplate
import io.legado.app.domain.model.AiCallMeta
import io.legado.app.domain.model.AiCallSource
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.PostEditRules
import io.legado.app.domain.model.PostEditTriggerResult
import io.legado.app.domain.usecase.ToolTraceBuilder
import io.legado.app.domain.usecase.ai.resolvedSubModelProfileId
import io.legado.app.help.ai.PlanFileStore
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.config.ai.AiAbilityManagementViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// =============================================================================
// Plan line-number anchoring
// =============================================================================

/**
 * 在计划 markdown 里定位选中文本的 1 基行号区间。
 * 先做精确子串查找（选中文本与 plan.md 原文一致时最准）；失败则剥离行首 markdown
 * 符号后逐行匹配（覆盖渲染与原文不一致的场景），取命中的首末行。
 * 返回 null 表示无法定位——调用方省略行号，不强行报错。
 */
internal fun locatePlanLines(planContent: String, selectedText: String): IntRange? {
    val query = selectedText.trim()
    if (query.isEmpty() || planContent.isEmpty()) return null

    // 1) 精确子串：把起止字符下标换算成行号。
    val idx = planContent.indexOf(query)
    if (idx >= 0) {
        val startLine = lineNumberAt(planContent, idx)
        val endLine = lineNumberAt(planContent, idx + query.length)
        return startLine..endLine
    }

    // 2) 剥离行首符号后逐行匹配（渲染换行造成的多行选中此处大概率失败，尽力而为）。
    val lines = planContent.split('\n')
    val normQuery = query.lowercase()
    val hits = lines.indices.filter { i ->
        stripPlanLinePrefix(lines[i]).lowercase().contains(normQuery)
    }
    if (hits.isEmpty()) return null
    return (hits.first() + 1)..(hits.last() + 1)
}

/** 字符在整篇文本中的下标 → 1 基行号。 */
private fun lineNumberAt(content: String, charIndex: Int): Int =
    1 + content.substring(0, charIndex.coerceIn(0, content.length)).count { it == '\n' }

/** 剥离行首常见 markdown 符号（标题/列表/引用/数字列表），供渲染与原文不一致时的兜底匹配。 */
private fun stripPlanLinePrefix(line: String): String {
    var s = line.trimStart()
    s = s.removePrefix("### ").removePrefix("## ").removePrefix("# ")
        .removePrefix("> ").removePrefix("- ").removePrefix("* ")
        .trimStart()
    return s.replaceFirst(Regex("^\\d+\\.\\s*"), "")
}

// =============================================================================
// Output mode
// =============================================================================

internal fun AiChatViewModel.setOutputMode(mode: AiOutputMode) {
    val cid = currentConversationId.value ?: return
    _uiState.update { it.copy(outputMode = mode, pendingPlan = null) }
    viewModelScope.launch {
        aiChatGateway.updateConversationOutputMode(cid, mode.name.lowercase())
    }
}

// =============================================================================
// Plan mode (approval)
// =============================================================================

/** 把当前会话的 pending 计划行标记为指定状态（有内容才写文件；状态一定落库）。 */
internal fun AiChatViewModel.markPlanStatus(status: String, content: String? = null) {
    val cid = currentConversationId.value ?: return
    viewModelScope.launch {
        val row = aiPlanGateway.getPendingByConversation(cid) ?: return@launch
        val finalContent = content ?: PlanFileStore.read(appCtx, cid, row.id)
        if (finalContent != null) {
            PlanFileStore.write(appCtx, cid, row.id, finalContent)
        }
        // 状态必须落库：即使计划文件缺失，拒绝/批准也不能失效。
        aiPlanGateway.upsert(row.copy(status = status))
        // 时间线计划卡片状态/内容同步。
        row.messageId.takeIf { it.isNotBlank() }?.let { mid ->
            planCardByMessageId[mid] = PlanCardMeta(
                content = finalContent ?: PlanFileStore.read(appCtx, cid, row.id).orEmpty(),
                status = status,
                revision = row.revision,
            )
        }
    }
}

/** 会话切换后恢复 pending 计划（跨会话审批面板复活）并标记时间线计划消息。 */
internal fun AiChatViewModel.restorePendingPlanAndPlanMessages(conversationId: String) {
    viewModelScope.launch {
        val plans = aiPlanGateway.getByConversation(conversationId)
        if (plans.isEmpty()) {
            Log.d("AiPlan", "restore: conv=$conversationId no plan rows")
            return@launch
        }
        planFileIdByMessageId.clear()
        planCardByMessageId.clear()
        // 跳过空 messageId（write_file("plan://…") 与 refreshPlanState 之间的崩溃窗口留下的行），
        // 避免把 "" 映射进 map 污染时间线标记。
        plans.forEach { planRow ->
            if (planRow.messageId.isNotBlank()) {
                planFileIdByMessageId[planRow.messageId] = planRow.id
                planCardByMessageId[planRow.messageId] = PlanCardMeta(
                    content = PlanFileStore.read(appCtx, conversationId, planRow.id).orEmpty(),
                    status = planRow.status,
                    revision = planRow.revision,
                )
            }
        }
        Log.d(
            "AiPlan",
            "restore: conv=$conversationId rows=${plans.size} map=${planFileIdByMessageId.size} " +
                "pending=${plans.firstOrNull { it.status == AiPlan.STATUS_PENDING }?.let { "${it.id}/msg=${it.messageId}" } ?: "none"}",
        )
        val pending = plans.firstOrNull { it.status == AiPlan.STATUS_PENDING }
        _uiState.update { current ->
            val restored = pending?.let { p ->
                PendingPlanUi(
                    messageId = p.messageId,
                    planContent = PlanFileStore.read(appCtx, conversationId, p.id).orEmpty(),
                    planReasoning = null,
                )
            }
            current.copy(pendingPlan = restored ?: current.pendingPlan)
        }
        // 立即重投消息，让「📋 计划」链接卡呈现。
        _uiState.update { it.copy(messages = allLoadedMessages.toImmutableList()) }
    }
}

internal fun AiChatViewModel.acceptPlan() {
    val plan = _uiState.value.pendingPlan ?: return
    // 批准后切 AUTO（写工具直接执行），与 Claude Code 批准 plan 后进入执行模式一致。
    // setOutputMode 已把 pendingPlan 清为 null → 面板立即关闭，执行在时间线静默可见。
    setOutputMode(AiOutputMode.AUTO)
    // 落库批准态 + 写计划文件（编辑过则用编辑后的内容）。
    markPlanStatus(AiPlan.STATUS_APPROVED, plan.approvedContent)
    // 无缝续跑：不伪造用户消息、不 read_file 迂回，直接注入已批准计划继续执行。
    continueApprovedPlan(plan.approvedContent)
}

/**
 * 批准后无缝续跑：把已批准计划直接注入下一轮请求（不落库为时间线气泡），
 * 在 AUTO 模式下继续生成最终结果。用户视角一轮：一条消息 → 计划 📋 → 批准 → 最终结果，
 * 全程无需再打字，也没有"用户批准"的假气泡。
 */
internal fun AiChatViewModel.continueApprovedPlan(approvedContent: String) {
    val convId = currentConversationId.value ?: return
    streamingJob?.cancel()
    streamingJob = viewModelScope.launch {
        try {
            generationStopRequested = false
            refreshMultiBubbleProtocolEnabled()
            _uiState.update { it.copy(isSending = true) }
            val history = getContextUiMessages()
            setStreamingPlaceholder()
            val fullText = StringBuilder()
            val fullReasoning = StringBuilder()
            val toolTrace = ToolTraceBuilder()
            // 直接注入已批准计划，模型无需 read_file 拿最新内容。
            val userContent = "计划已批准。不要重新规划，严格按以下已批准计划执行并生成最终结果。\n\n" +
                "【已批准计划】\n$approvedContent"
            var completed = false
            try {
                val request = buildGenerationRequest(
                    userContent = userContent,
                    history = history,
                )
                collectStream(request, fullText, fullReasoning, toolTrace)
                continueToolRounds(convId, request, fullText, fullReasoning, toolTrace)
                completed = true
            } catch (_: CancellationException) {
                // 用户中途停止：保存已流出的部分，不再走完成保存（避免双写）。
                _uiState.update { it.copy(streamingMessage = null) }
                savePartialAssistantOnStop(convId, fullText, fullReasoning, toolTrace)
            } catch (e: Exception) {
                if (fullText.isNotEmpty()) {
                    _effects.tryEmit(AiChatEffect.ShowMessage(e.message ?: "Failed"))
                }
            }
            if (completed && fullText.isNotBlank()) {
                val finalText = maybePostEdit(fullText.toString(), fullReasoning.toString(), toolTrace)
                val speakerId = _uiState.value.streamingMessage?.speakerCardId
                    ?: resolveSpeakerCardId(finalText)
                persistAssistantBubbles(
                    convId = convId,
                    parentMessageId = null,
                    rawText = finalText,
                    reasoning = fullReasoning.toString(),
                    toolTrace = toolTrace,
                    wasCancelled = false,
                    speakerId = speakerId,
                    speakerName = speakerNameForPersist(finalText, speakerId),
                    duration = _uiState.value.streamingMessage?.thinkingDuration ?: 0,
                )
            } else {
                _uiState.update { it.copy(streamingMessage = null) }
            }
        } finally {
            withContext(NonCancellable) {
                _uiState.update { it.copy(pendingPlan = null, streamingMessage = null, isSending = false) }
                refreshContextUsageEstimate(getContextUiMessages())
            }
            if (streamingJob == currentCoroutineContext()[Job]) streamingJob = null
        }
    }
}

/**
 * 修订轮静默续跑：把修订反馈直接注入下一轮请求（不落库为时间线气泡）。
 * outputMode 保持 PLAN（编辑计划文件），修订指令靠 planRevisionRound 标志注入。
 * 修订完成后：edit_file 已改文件则直接用；模型直接输出完整修订计划则剥围栏写回文件，
 * 再 refreshPlanState 重弹审批面板展示新计划。全程不新增时间线消息。
 */
internal fun AiChatViewModel.continuePlanRevision(feedbackContent: String) {
    val convId = currentConversationId.value ?: return
    streamingJob?.cancel()
    streamingJob = viewModelScope.launch {
        try {
            generationStopRequested = false
            refreshMultiBubbleProtocolEnabled()
            _uiState.update { it.copy(isSending = true) }
            val history = getContextUiMessages()
            setStreamingPlaceholder()
            val fullText = StringBuilder()
            val fullReasoning = StringBuilder()
            val toolTrace = ToolTraceBuilder()
            // 修订指令标志：buildGenerationRequest 读取并消费 → PLAN_REVISION_DIRECTIVE。
            planRevisionRound = true
            val userContent = "计划修订意见：\n\n$feedbackContent"
            var completed = false
            try {
                val request = buildGenerationRequest(
                    userContent = userContent,
                    history = history,
                )
                collectStream(request, fullText, fullReasoning, toolTrace)
                continueToolRounds(convId, request, fullText, fullReasoning, toolTrace)
                completed = true
            } catch (_: CancellationException) {
                _uiState.update { it.copy(streamingMessage = null) }
            } catch (e: Exception) {
                if (fullText.isNotEmpty()) {
                    _effects.tryEmit(AiChatEffect.ShowMessage(e.message ?: "Failed"))
                }
            }
            if (completed) {
                val text = fullText.toString()
                val planFileUpdated = toolTrace.completedPlanFileCalls()
                if (planFileUpdated || text.isNotBlank()) {
                    withContext(NonCancellable) {
                        val row = aiPlanGateway.getPendingByConversation(convId)
                        if (row != null) {
                            // 递增修订号 → 卡片 pop 动画触发，提示计划已更新。
                            aiPlanGateway.upsert(row.copy(revision = row.revision + 1))
                            if (!planFileUpdated) {
                                // 模型直接输出完整修订计划：剥掉代码围栏再写回，避免 ```markdown 污染文件。
                                val clean = text.trim()
                                    .removePrefix("```markdown\n").removePrefix("```md\n").removePrefix("```\n")
                                    .removeSuffix("\n```").trim()
                                PlanFileStore.write(appCtx, convId, row.id, clean)
                            }
                            val planMsgId = aiChatGateway.getContextMessages(convId, 1).firstOrNull()?.id
                            refreshPlanState(convId, planMsgId, fullReasoning.toString())
                        }
                    }
                } else {
                    _uiState.update { it.copy(streamingMessage = null) }
                }
            }
        } finally {
            // 注意：不清 pendingPlan —— 修订成功时 refreshPlanState 已重建面板，失败时保持关闭。
            withContext(NonCancellable) {
                _uiState.update { it.copy(streamingMessage = null, isSending = false) }
                refreshContextUsageEstimate(getContextUiMessages())
            }
            if (streamingJob == currentCoroutineContext()[Job]) streamingJob = null
        }
    }
}

internal fun AiChatViewModel.rejectPlan() {
    if (_uiState.value.pendingPlan == null) return
    // 仅收起审批面板,保留计划消息本身:拒绝只是不采纳,计划文件留在磁盘供用户回顾。
    markPlanStatus(AiPlan.STATUS_REJECTED)
    _uiState.update { it.copy(pendingPlan = null) }
}

internal fun AiChatViewModel.editPlan(content: String) {
    val plan = _uiState.value.pendingPlan ?: return
    _uiState.update {
        it.copy(pendingPlan = plan.copy(isEditing = true, editedPlanContent = content))
    }
}

internal fun AiChatViewModel.cancelPlanEdit() {
    val plan = _uiState.value.pendingPlan ?: return
    _uiState.update {
        it.copy(pendingPlan = plan.copy(isEditing = false, editedPlanContent = ""))
    }
}

/** 选区反馈：发起计划修订轮（静默续跑，不落时间线）。selectedText 为空表示作用于整篇计划。 */
internal fun AiChatViewModel.revisePlan(selectedText: String, feedback: String) {
    if (feedback.isBlank()) {
        _effects.tryEmit(AiChatEffect.ShowMessage(appCtx.getString(R.string.ai_plan_revision_feedback_empty)))
        return
    }
    val plan = _uiState.value.pendingPlan ?: return
    val range = if (selectedText.isNotBlank()) locatePlanLines(plan.planContent, selectedText) else null
    // 只带反馈本体；操作指令由系统级 PLAN_REVISION_DIRECTIVE 提供，本轮不落时间线。
    val feedbackContent = buildString {
        append("【反馈 1】")
        range?.let { r ->
            append("（位置：第 ${r.first} 行")
            if (r.first != r.last) append("-${r.last}")
            append("）")
        }
        if (selectedText.isNotBlank()) {
            append("\n选中内容：\n```\n$selectedText\n```\n")
        }
        append("修改意见：\n$feedback\n")
    }
    startPlanRevision(feedbackContent)
}

// =============================================================================
// Suggestions
// =============================================================================

internal fun AiChatViewModel.selectSuggestion(text: String) {
    if (_uiState.value.conversationType == "writing") {
        _effects.tryEmit(AiChatEffect.SetInputText(text))
    } else {
        onIntent(AiChatIntent.SendMessage(text))
    }
}

internal fun AiChatViewModel.dismissSuggestions() {
    _uiState.update { it.copy(suggestions = persistentListOf()) }
}

internal fun AiChatViewModel.generateSuggestions() {
    val convId = currentConversationId.value ?: return
    viewModelScope.launch {
        val config = aiToolConfigGateway.getByToolName("suggested_replies") ?: return@launch
        if (!config.enabled) return@launch
        if (!config.useSubModel) return@launch // inline mode handled during streaming
        generateSuggestionsViaSubModel(config, convId)
    }
}

internal suspend fun AiChatViewModel.generateSuggestionsViaSubModel(config: AiToolConfig, convId: String) {
    val state = _uiState.value
    _uiState.update { it.copy(suggestionsLoading = true) }

    try {
        val recentMsgs = allLoadedMessages.takeLast(2)
        val contextText = recentMsgs.joinToString("\n\n") { "[${it.role}] ${it.content}" }
        val prompt = promptTemplateGateway.getPrompt(AiPromptTemplate.SUGGESTIONS_PROMPT)
        val isWriting = state.conversationType == "writing"
        val userMessage = if (isWriting) {
            val template = promptTemplateGateway.getPrompt(AiPromptTemplate.GALGAME_SUGGESTIONS_PROMPT)
            "$template\n\n对话：\n$contextText"
        } else {
            "Conversation:\n$contextText\n\nGenerate 3 suggested replies based on the above."
        }

        val request = buildSuggestionsRequest(config, prompt, userMessage, AiCallSource.SUGGESTION, convId) ?: run {
            _uiState.update { it.copy(suggestionsLoading = false) }
            return
        }

        val result = aiTextGateway.generate(request)
        if (currentConversationId.value != convId) return

        result.onSuccess { response ->
            val suggestions = parseSuggestionsJson(response.text)
            _uiState.update { it.copy(suggestions = suggestions.toImmutableList(), suggestionsLoading = false) }
        }.onFailure {
            _uiState.update { it.copy(suggestionsLoading = false) }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        _uiState.update { it.copy(suggestionsLoading = false) }
    }
}

internal suspend fun AiChatViewModel.buildSuggestionsRequest(
    config: AiToolConfig,
    prompt: String,
    userMessage: String,
    source: String,
    conversationId: String? = currentConversationId.value,
): AiGenerateRequest? {
    val subModelId = config.resolvedSubModelProfileId(aiProfileGateway)
    if (subModelId != null) {
        val modelProfile = aiProfileGateway.getModel(subModelId) ?: return null
        val provider = aiProfileGateway.getProvider(modelProfile.providerId) ?: return null
        return AiGenerateRequest(
            model = AiModelConfig(
                id = modelProfile.id,
                provider = AiProviderConfig(
                    id = provider.id,
                    name = provider.name,
                    protocol = provider.protocol,
                    baseUrl = provider.baseUrl,
                    apiKey = provider.apiKey,
                    modelsUrl = provider.modelsUrl,
                    headers = emptyMap(),
                    chatPath = provider.chatPath ?: "/chat/completions",
                    responsesPath = provider.responsesPath ?: "/responses",
                    messagesPath = provider.messagesPath ?: "/v1/messages",
                    modelsPath = provider.modelsPath,
                    customHeaders = emptyMap()
                ),
                displayName = modelProfile.displayName,
                modelId = modelProfile.modelId,
                contextWindow = modelProfile.contextWindow,
                maxOutputTokens = modelProfile.maxOutputTokens
            ),
            messages = listOf(
                AiMessage(AiMessageRole.SYSTEM, prompt),
                AiMessage(AiMessageRole.USER, userMessage)
            ),
            params = AiGenerationParams(reasoningLevel = AiReasoningLevel.OFF),
            callMeta = AiCallMeta(source, conversationId),
        )
    }
    // Fall back to default preset model
    val preset = aiProfileGateway.getTaskPreset(AiTaskType.CHAT) ?: return null
    return AiGenerateRequest(
        model = preset.model,
        messages = listOf(
            AiMessage(AiMessageRole.SYSTEM, prompt),
            AiMessage(AiMessageRole.USER, userMessage)
        ),
        params = preset.params.copy(reasoningLevel = AiReasoningLevel.OFF),
        callMeta = AiCallMeta(source, conversationId),
    )
}

// =============================================================================
// Post-edit (writing mode style correction)
// =============================================================================

internal fun AiChatViewModel.observePostEditConfig() {
    viewModelScope.launch {
        aiToolConfigGateway.observeAll().collect { configs ->
            val postEditConfig = configs.find { it.toolName == "post_edit" }
            _uiState.update { it.copy(postEditEnabled = postEditConfig?.enabled == true) }
        }
    }
}

internal fun AiChatViewModel.togglePostEdit() {
    viewModelScope.launch {
        val config = aiToolConfigGateway.getByToolName("post_edit")
            ?: AiToolConfig(toolName = "post_edit", enabled = false,
                subModelPrompt = AiAbilityManagementViewModel.defaultPrompt("post_edit"))
        aiToolConfigGateway.save(config.copy(enabled = !config.enabled))
    }
}

/**
 * Writing-mode post-edit gate: polish pure-text output via sub-model before saving.
 * Skips chat mode, disabled config, empty text, and replies that contain tool calls
 * (tool insertions are positioned by character offset and would misalign if the text changed).
 */
internal suspend fun AiChatViewModel.maybePostEdit(text: String, reasoning: String, toolTrace: ToolTraceBuilder): String {
    if (_uiState.value.conversationType != "writing") {
        Log.d("PostEdit", "skip: not writing mode (type=${_uiState.value.conversationType})")
        return text
    }
    if (!_uiState.value.postEditEnabled) {
        Log.d("PostEdit", "skip: postEditEnabled=false (toggle off)")
        return text
    }
    if (text.isBlank()) {
        Log.d("PostEdit", "skip: blank text")
        return text
    }
    val hasTools = generationUseCase.buildAssistantParts(text, "", toolTrace)
        .any { it is AiMessagePart.Tool }
    if (hasTools) {
        Log.d("PostEdit", "skip: reply contains tool calls")
        return text
    }
    // Regex pre-check: only spend a sub-model call when a target phrasing is actually present.
    val triggerRaw = promptTemplateGateway.getPrompt(AiPromptTemplate.POST_EDIT_TRIGGER_REGEX)
    val trigger = PostEditRules.evaluateTrigger(text, triggerRaw)
    if (trigger is PostEditTriggerResult.Skip) {
        Log.d("PostEdit", "skip: no trigger regex matched (textLen=${text.length})")
        return text
    }
    Log.d("PostEdit", "triggered: calling sub-model (textLen=${text.length}, trigger=$trigger)")
    _uiState.update { it.copy(isPostEditing = true) }
    val edited = try {
        runPostEdit(text, trigger)
    } finally {
        _uiState.update { it.copy(isPostEditing = false) }
    }
    val finalText = edited ?: text
    Log.d("PostEdit", "done: changed=${finalText != text} finalLen=${finalText.length}")
    // Sync the streaming placeholder to the polished text so it matches the saved message
    // (observeStreamingCleanup clears the placeholder by content equality).
    if (finalText != text) {
        syncStreamingMessage(finalText, reasoning, toolTrace)
    }
    return finalText
}

/** Run sub-model post-edit on raw writing output. Returns polished text, or null to fall back to the original. */
internal suspend fun AiChatViewModel.runPostEdit(rawText: String, trigger: PostEditTriggerResult): String? {
    return try {
        val config = aiToolConfigGateway.getByToolName("post_edit") ?: run {
            Log.d("PostEdit", "runPostEdit: no post_edit config"); return null
        }
        if (!config.enabled) { Log.d("PostEdit", "runPostEdit: config disabled"); return null }
        val triggerRaw = promptTemplateGateway.getPrompt(AiPromptTemplate.POST_EDIT_TRIGGER_REGEX)
        val matchedSentences = PostEditRules.extractMatchedSentences(rawText, triggerRaw)
        if (matchedSentences.isEmpty()) {
            Log.d("PostEdit", "runPostEdit: no matched sentences"); return null
        }
        val promptRaw = promptTemplateGateway.getPrompt(AiPromptTemplate.POST_EDIT_PROMPT)
        val sentenceTrigger = when (trigger) {
            is PostEditTriggerResult.Matched -> trigger
            else -> PostEditTriggerResult.Matched(
                matchedSentences.flatMap { it.matchedRuleIds }.distinct()
            )
        }
        val instructions = PostEditRules.buildSentencePolishInstructions(promptRaw, sentenceTrigger)
        val payload = PostEditRules.formatSentencePayload(matchedSentences)
        val request = buildSuggestionsRequest(config, instructions, payload, AiCallSource.POST_EDIT) ?: run {
            Log.d("PostEdit", "runPostEdit: no sub-model profile and no default CHAT preset"); return null
        }
        Log.d(
            "PostEdit",
            "runPostEdit: calling model=${request.model.modelId}, sentences=${matchedSentences.size}",
        )
        val result = aiTextGateway.generate(request)
        result.exceptionOrNull()?.let { Log.e("PostEdit", "runPostEdit: generate failed: ${it.message}") }
        val generated = result.getOrNull()
        val rawReply = generated?.text
        val polished = rawReply?.let {
            PostEditRules.parseSentencePayload(it, matchedSentences.size)
        }
        if (polished == null) {
            val preview = rawReply.orEmpty().replace("\n", "\\n").take(200)
            Log.w(
                "PostEdit",
                "runPostEdit: sentence payload parse failed, keeping original; " +
                    "expected=${matchedSentences.size} replyLen=${rawReply?.length ?: 0} preview=$preview",
            )
            return null
        }
        val replacements = matchedSentences.mapIndexed { index, sentence ->
            sentence.range to PostEditRules.mergePolishedSentence(sentence, polished[index])
        }
        val usage = generated?.usage
        pendingSideEffects += AiMessagePart.SideEffect(
            source = AiCallSource.POST_EDIT,
            input = payload,
            output = polished.joinToString(PostEditRules.SENTENCE_SEPARATOR),
            modelName = request.model.displayName.ifBlank { request.model.modelId },
            totalTokens = usage?.totalTokens
                ?: ((usage?.promptTokens ?: 0) + (usage?.completionTokens ?: 0)),
            generatedChars = polished.sumOf { it.length },
            success = true,
        )
        PostEditRules.applySentenceEdits(rawText, replacements)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("PostEdit", "runPostEdit failed: ${e.message}")
        null
    }
}

// =============================================================================
// Web search
// =============================================================================

internal fun AiChatViewModel.toggleWebSearch() {
    if (_uiState.value.conversationType != "chat") return
    if (!_uiState.value.webSearchArmed && !AppConfig.aiWebSearchConfigured) {
        _effects.tryEmit(
            AiChatEffect.ShowMessage(appCtx.getString(webSearchConfigureHintRes())),
        )
        return
    }
    _uiState.update { it.copy(webSearchArmed = !it.webSearchArmed) }
}

// =============================================================================
// HUD toggles
// =============================================================================

internal fun AiChatViewModel.toggleInterCharacterChat() {
    val newValue = !_uiState.value.interCharacterChatEnabled
    _uiState.update { it.copy(interCharacterChatEnabled = newValue) }
    appCtx.putPrefBoolean(
        io.legado.app.constant.PreferKey.interCharacterChatEnabled, newValue
    )
}

internal fun AiChatViewModel.toggleDialogueHighlight() {
    val newValue = !_uiState.value.dialogueHighlightEnabled
    _uiState.update { it.copy(dialogueHighlightEnabled = newValue) }
    appCtx.putPrefBoolean(
        io.legado.app.constant.PreferKey.dialogueHighlightEnabled, newValue
    )
}

internal fun AiChatViewModel.toggleRoleplayDialogueBubble() {
    val newValue = !_uiState.value.roleplayDialogueBubbleEnabled
    _uiState.update { it.copy(roleplayDialogueBubbleEnabled = newValue) }
    appCtx.putPrefBoolean(
        io.legado.app.constant.PreferKey.aiRoleplayDialogueBubbleEnabled, newValue
    )
}

internal fun AiChatViewModel.setStructuredAutoMaintain(enabled: Boolean) {
    AppConfig.aiStructuredAutoMaintain = enabled
    _uiState.update { it.copy(structuredAutoMaintainEnabled = enabled) }
}
