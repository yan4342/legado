@file:OptIn(ExperimentalCoroutinesApi::class)

package io.legado.app.ui.ai.chat

// =============================================================================
// Extension functions extracted from AiChatViewModel — Tool Approval & User Prompts
// =============================================================================

import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.data.repository.AiToolRepository
import io.legado.app.data.repository.StructuredDataToolParser
import io.legado.app.domain.model.AiToolCall
import io.legado.app.domain.usecase.ToolApprovalDecision
import io.legado.app.domain.usecase.ToolTraceBuilder
import io.legado.app.domain.usecase.TtsConfigTools
import io.legado.app.domain.usecase.UserMemoryTools
import io.legado.app.domain.usecase.UserQuestionAnswer
import io.legado.app.domain.usecase.UserQuestionsToolParser
import io.legado.app.domain.usecase.structured.StructuredDataContextParser
import io.legado.app.domain.usecase.structured.StructuredDataDeepLinkResolver
import io.legado.app.domain.usecase.structured.StructuredDataDiff
import io.legado.app.domain.usecase.structured.ValidationIssue
import io.legado.app.domain.usecase.structured.formattedFeedback
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.isAbsUrl
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import splitties.init.appCtx

// =============================================================================
// User Questions — pending question UI lifecycle
// =============================================================================

internal fun AiChatViewModel.dismissUserQuestions() {
    cancelPendingUserQuestions(cancelled = true)
}

internal fun AiChatViewModel.cancelPendingUserQuestions(cancelled: Boolean) {
    val deferred = userQuestionsDeferred
    userQuestionsDeferred = null
    if (deferred != null && cancelled) {
        deferred.complete(ToolTraceBuilder.RESULT_CANCELLED)
    }
    _uiState.update { it.copy(pendingUserQuestions = null) }
}

internal fun AiChatViewModel.toggleQuestionOption(callId: String, questionId: String, optionId: String) {
    _uiState.update { state ->
        val pending = state.pendingUserQuestions ?: return@update state
        if (pending.callId != callId) return@update state
        state.copy(
            pendingUserQuestions = pending.copy(
                questions = pending.questions.map { question ->
                    if (question.id != questionId) return@map question
                    val selected = if (question.allowMultiple) {
                        if (optionId in question.selectedIds) {
                            question.selectedIds - optionId
                        } else {
                            question.selectedIds + optionId
                        }
                    } else {
                        setOf(optionId)
                    }
                    question.copy(selectedIds = selected)
                }.toImmutableList(),
            ),
        )
    }
}

internal fun AiChatViewModel.updateQuestionCustomText(callId: String, questionId: String, text: String) {
    _uiState.update { state ->
        val pending = state.pendingUserQuestions ?: return@update state
        if (pending.callId != callId) return@update state
        state.copy(
            pendingUserQuestions = pending.copy(
                questions = pending.questions.map { question ->
                    if (question.id == questionId) question.copy(customText = text) else question
                }.toImmutableList(),
            ),
        )
    }
}

internal fun AiChatViewModel.submitUserQuestions(callId: String) {
    val pending = _uiState.value.pendingUserQuestions ?: return
    if (pending.callId != callId || !pending.canSubmit()) return
    val answers = pending.questions.map { question ->
        UserQuestionAnswer(
            questionId = question.id,
            selectedOptionIds = question.selectedIds.toList(),
            customText = question.customText.takeIf { it.isNotBlank() },
        )
    }
    val json = UserQuestionsToolParser.formatAnswersJson(answers)
    userQuestionsDeferred?.complete(json)
    userQuestionsDeferred = null
    _uiState.update { it.copy(pendingUserQuestions = null) }
}

// =============================================================================
// Habit Memory — confirmation UI
// =============================================================================

internal suspend fun AiChatViewModel.awaitHabitMemoryConfirm(call: AiToolCall): Boolean {
    val args = runCatching {
        com.google.gson.JsonParser.parseString(call.arguments).asJsonObject
    }.getOrNull() ?: return false
    val ops = UserMemoryTools.parseOps(args)
    if (ops.isEmpty()) return false
    val lines = ops.map { op ->
        HabitMemoryConfirmLineUi(
            op = op.op,
            key = op.key,
            value = op.value.orEmpty(),
        )
    }.toImmutableList()
    _uiState.update {
        it.copy(
            pendingHabitMemoryConfirm = PendingHabitMemoryConfirmUi(
                callId = call.id,
                lines = lines,
            ),
        )
    }
    val deferred = CompletableDeferred<Boolean>()
    habitMemoryConfirmDeferred = deferred
    return try {
        deferred.await()
    } finally {
        habitMemoryConfirmDeferred = null
        _uiState.update { it.copy(pendingHabitMemoryConfirm = null) }
    }
}

internal fun AiChatViewModel.confirmHabitMemory() {
    if (_uiState.value.pendingHabitMemoryConfirm == null) return
    _effects.tryEmit(
        AiChatEffect.ShowMessage(appCtx.getString(R.string.ai_habit_memory_remembered)),
    )
    habitMemoryConfirmDeferred?.complete(true)
}

internal fun AiChatViewModel.rejectHabitMemory() {
    habitMemoryConfirmDeferred?.complete(false)
}

internal fun AiChatViewModel.cancelPendingHabitMemory(confirmed: Boolean) {
    habitMemoryConfirmDeferred?.complete(confirmed)
    habitMemoryConfirmDeferred = null
    _uiState.update { it.copy(pendingHabitMemoryConfirm = null) }
}

// =============================================================================
// TTS Secret Fill — local secret injection UI
// =============================================================================

internal fun AiChatViewModel.updateTtsSecretFill(callId: String, apiKey: String?, secretKey: String?) {
    _uiState.update { s ->
        val fills = s.pendingTtsSecretFills.map { fill ->
            if (fill.callId != callId) fill
            else fill.copy(
                apiKey = apiKey ?: fill.apiKey,
                secretKey = secretKey ?: fill.secretKey,
            )
        }.toImmutableList()
        val satisfied = fills.firstOrNull { it.callId == callId }?.isSatisfied() == true
        s.copy(
            pendingTtsSecretFills = fills,
            pendingToolConfs = s.pendingToolConfs.map { item ->
                if (item.callId != callId) item
                else item.copy(localSecretSatisfied = !item.requiresLocalSecret || satisfied)
            }.toImmutableList(),
        )
    }
}

// =============================================================================
// Tool Approval — toggling individual/batch approval
// =============================================================================

internal fun AiChatViewModel.clearPendingToolApprovalUi() {
    _uiState.update {
        it.copy(
            pendingToolConfs = persistentListOf(),
            pendingToolBatchFeedback = "",
            pendingTtsSecretFills = persistentListOf(),
        )
    }
}

internal fun AiChatViewModel.toggleToolApproval(callId: String) {
    _uiState.update { s ->
        s.copy(pendingToolConfs = s.pendingToolConfs.map { item ->
            if (item.callId != callId) return@map item
            if (!item.canToggleApproval()) return@map item
            val newChecked = !item.checked
            item.copy(
                checked = newChecked,
                subItems = item.subItems.map { sub ->
                    sub.copy(checked = if (sub.tier == "INCREMENTAL") newChecked && sub.validationError == null else newChecked)
                }.toImmutableList(),
            ).let { updated ->
                if (updated.subItems.isEmpty()) updated
                else updated.copy(checked = updated.subItems.any { it.isApprovable() })
            }
        }.toImmutableList())
    }
}

internal fun AiChatViewModel.toggleToolSubItemApproval(callId: String, subItemId: String) {
    _uiState.update { s ->
        s.copy(pendingToolConfs = s.pendingToolConfs.map { item ->
            if (item.callId != callId) return@map item
            val updatedSubs = item.subItems.map { sub ->
                if (sub.id != subItemId || sub.tier != "INCREMENTAL" || sub.validationError != null) sub
                else sub.copy(checked = !sub.checked)
            }.toImmutableList()
            item.copy(subItems = updatedSubs, checked = updatedSubs.any { it.isApprovable() })
        }.toImmutableList())
    }
}

internal fun AiChatViewModel.updateToolFeedback(callId: String, feedback: String) {
    _uiState.update { s ->
        s.copy(pendingToolConfs = s.pendingToolConfs.map {
            if (it.callId == callId) it.copy(feedback = feedback) else it
        }.toImmutableList())
    }
}

internal fun AiChatViewModel.updateToolBatchFeedback(feedback: String) {
    _uiState.update { it.copy(pendingToolBatchFeedback = feedback) }
}

internal fun AiChatViewModel.updateToolArgs(callId: String, json: String) {
    val call = approvedToolCalls.find { it.id == callId } ?: return
    val parseError = formatArgsValidationError(json)
    if (parseError != null) {
        _uiState.update { s ->
            s.copy(pendingToolConfs = s.pendingToolConfs.map {
                if (it.callId == callId) {
                    it.copy(
                        editableArgsJson = json,
                        argsValidationError = parseError,
                        validationError = parseError,
                        checked = false,
                        summary = appCtx.getString(R.string.ai_tool_args_json_invalid_summary),
                        subItems = persistentListOf(),
                    )
                } else it
            }.toImmutableList())
        }
        return
    }
    viewModelScope.launch {
        val args = com.google.gson.JsonParser.parseString(json).asJsonObject
        val rebuilt = buildPendingToolCallUi(call, args, currentConversationId.value, json)
        _uiState.update { s ->
            val fills = s.pendingTtsSecretFills.toMutableList()
            fills.removeAll { it.callId == callId }
            rebuilt.secretFill?.let { fills.add(it) }
            s.copy(
                pendingToolConfs = s.pendingToolConfs.map {
                    if (it.callId == callId) rebuilt.ui.copy(feedback = it.feedback) else it
                }.toImmutableList(),
                pendingTtsSecretFills = fills.toImmutableList(),
            )
        }
    }
}

// =============================================================================
// PendingToolBuild — result of building approval UI for a tool call
// =============================================================================

internal data class PendingToolBuild(
    val ui: PendingToolCallUi,
    val secretFill: PendingTtsSecretFillUi? = null,
)

// =============================================================================
// Field-level approval — toggling individual field changes
// =============================================================================

internal fun AiChatViewModel.toggleFieldChangeApproval(callId: String, path: String) {
    _uiState.update { s ->
        s.copy(pendingToolConfs = s.pendingToolConfs.map { item ->
            if (item.callId != callId) return@map item
            item.copy(
                subItems = item.subItems.map { sub ->
                    sub.copy(
                        fieldChanges = sub.fieldChanges.map { fc ->
                            if (fc.path == path) fc.copy(checked = !fc.checked) else fc
                        }.toImmutableList(),
                    )
                }.toImmutableList(),
            )
        }.toImmutableList())
    }
}

// =============================================================================
// Build tool call approval UI
// =============================================================================

internal suspend fun AiChatViewModel.buildPendingToolCallUi(
    call: AiToolCall,
    args: com.google.gson.JsonObject,
    convId: String?,
    editableArgsJson: String = call.arguments,
): PendingToolBuild {
    val displayName = io.legado.app.ui.config.ai.resolvedToolDisplayName(call.name)
    var workingArgs = args
    var workingEditableJson = editableArgsJson
    var secretFill: PendingTtsSecretFillUi? = null
    if (call.name == AiToolRepository.TOOL_PATCH_CLOUD_TTS_ENGINE) {
        val prep = TtsConfigTools.prepareCloudPatchSecretFill(args)
        if (prep != null) {
            workingArgs = prep.redactedArgs
            workingEditableJson = prep.redactedArgs.toString()
            secretFill = PendingTtsSecretFillUi(
                callId = call.id,
                action = prep.fill.action,
                provider = prep.fill.provider,
                engineLabel = prep.fill.engineLabel,
                apiKeyLabel = prep.fill.apiKeyLabel,
                secretKeyLabel = prep.fill.secretKeyLabel,
                needsApiKey = prep.fill.needsApiKey,
                needsSecretKey = prep.fill.needsSecretKey,
                apiKey = prep.fill.prefilledApiKey,
                secretKey = prep.fill.prefilledSecretKey,
            )
        }
    }
    val argsError = formatArgsValidationError(workingEditableJson)
    if (argsError != null) {
        return PendingToolBuild(
            ui = PendingToolCallUi(
                callId = call.id,
                toolName = call.name,
                displayName = displayName,
                summary = appCtx.getString(R.string.ai_tool_args_json_invalid_summary),
                previewDetail = workingEditableJson.take(800),
                checked = false,
                validationError = argsError,
                editableArgsJson = workingEditableJson,
                argsValidationError = argsError,
                tier = "INCREMENTAL",
                requiresLocalSecret = secretFill != null,
                localSecretSatisfied = secretFill?.isSatisfied() ?: true,
            ),
            secretFill = secretFill,
        )
    }

    val repo = aiToolGateway as? AiToolRepository
    val opPreviews = repo?.let {
        runCatching { it.previewMutationOps(call.name, workingArgs) }.getOrElse { emptyList() }
    }.orEmpty()
    val validation = structuredDataValidator.validate(
        StructuredDataContextParser.parse(call.name, workingArgs, convId)
    )
    val fieldChanges = opPreviews.flatMap { it.fieldChanges }
    val isBookshelfGated = AiToolRepository.isBookshelfAccessConfirmTool(call.name)
    var summary = AiToolRepository.toolSummary(call.name, workingArgs)
    var previewText = if (fieldChanges.isNotEmpty()) {
        StructuredDataDiff.formatDiffForPreview(fieldChanges)
    } else {
        AiToolRepository.previewDetail(call.name, workingArgs)
    }
    var blockingError = validation.blocking?.formattedFeedback()
    var tier = StructuredDataContextParser.tier(
        StructuredDataContextParser.parse(call.name, workingArgs, convId)
    ).name
    var chapterArgsError: String? = null

    if (isBookshelfGated) {
        tier = "READ"
        when (call.name) {
            AiToolRepository.TOOL_SEARCH_BOOKS -> {
                summary = bookshelfAccessPreviewer.previewSearch(workingArgs)
                previewText = summary
            }
            AiToolRepository.TOOL_SEARCH_BOOK_SOURCES -> {
                summary = bookshelfAccessPreviewer.previewSourceSearch(workingArgs)
                previewText = summary
                if (!io.legado.app.help.config.AppConfig.aiAllowBookSourceFetch) {
                    blockingError = "Book-source search is disabled in AI ability settings"
                } else if (workingArgs.get("query")?.takeIf { !it.isJsonNull }?.asString.orEmpty().trim().isBlank()) {
                    blockingError = "query is required"
                }
            }
            AiToolRepository.TOOL_ADD_BOOK_TO_BOOKSHELF -> {
                summary = bookshelfAccessPreviewer.previewAddBook(workingArgs)
                previewText = summary
                if (!io.legado.app.help.config.AppConfig.aiAllowBookSourceFetch) {
                    blockingError = "Book-source fetch is disabled in AI ability settings"
                } else {
                    val bookUrl = workingArgs.get("bookUrl")?.takeIf { !it.isJsonNull }?.asString.orEmpty().trim()
                    val bookName = workingArgs.get("bookName")?.takeIf { !it.isJsonNull }?.asString.orEmpty().trim()
                    if (bookUrl.isBlank() && bookName.isBlank()) {
                        blockingError = "bookUrl or bookName is required"
                    }
                }
            }
            AiToolRepository.TOOL_GET_CHAPTER_CONTENT -> {
                val preview = bookshelfAccessPreviewer.previewChapterRead(workingArgs)
                summary = bookshelfAccessPreviewer.formatChapterReadSummary(preview)
                previewText = bookshelfAccessPreviewer.formatChapterReadPreview(preview)
                preview.validationError?.let { blockingError = it }
                if (preview.validationError != null) {
                    chapterArgsError = preview.validationError
                }
            }
            AiToolRepository.TOOL_SEARCH_BOOK_CONTENT -> {
                val preview = bookshelfAccessPreviewer.previewContentSearch(workingArgs)
                summary = bookshelfAccessPreviewer.formatContentSearchSummary(preview)
                previewText = bookshelfAccessPreviewer.formatContentSearchPreview(preview)
                preview.validationError?.let { blockingError = it }
            }
            else -> {
                summary = AiToolRepository.toolSummary(call.name, workingArgs)
                previewText = summary
                if (!io.legado.app.help.config.AppConfig.aiAllowBookSourceFetch) {
                    blockingError = appCtx.getString(R.string.ai_tool_error_book_source_fetch_disabled)
                }
            }
        }
    } else if (AiToolRepository.isWebSearchConfirmTool(call.name)) {
        tier = "READ"
        summary = AiToolRepository.toolSummary(call.name, workingArgs)
        previewText = summary
        when (call.name) {
            AiToolRepository.TOOL_WEB_SEARCH -> {
                if (!AppConfig.aiWebSearchConfigured) {
                    blockingError = appCtx.getString(webSearchConfigureHintRes())
                } else if (workingArgs.get("query")?.takeIf { !it.isJsonNull }?.asString.orEmpty().trim().isBlank()) {
                    blockingError = "query is required"
                } else if (!_uiState.value.webSearchArmed) {
                    blockingError = appCtx.getString(R.string.ai_web_search_not_armed)
                }
            }
            AiToolRepository.TOOL_READ_WEB_PAGE -> {
                val url = workingArgs.get("url")?.takeIf { !it.isJsonNull }?.asString.orEmpty().trim()
                if (url.isBlank() || !url.isAbsUrl()) {
                    blockingError = "url must be an absolute http(s) URL"
                } else if (!_uiState.value.webSearchArmed) {
                    blockingError = appCtx.getString(R.string.ai_web_search_not_armed)
                }
            }
            else -> {
                if (!_uiState.value.webSearchArmed) {
                    blockingError = appCtx.getString(R.string.ai_web_search_not_armed)
                }
            }
        }
    }
    val subItems = opPreviews.map { preview ->
        val subError = preview.validationIssues
            .filterIsInstance<ValidationIssue.Error>()
            .firstOrNull()
            ?.formattedFeedback()
        PendingToolSubItem(
            id = "${call.id}:${preview.opIndex}",
            opLabel = preview.opLabel,
            fieldChanges = preview.fieldChanges.map {
                FieldChangeUi(it.path, it.oldValue, it.newValue)
            }.toImmutableList(),
            deepLink = preview.deepLink,
            checked = blockingError == null && subError == null,
            validationError = subError,
            tier = preview.tier.name,
            opIndex = preview.opIndex,
        )
    }.toImmutableList()
    val allowFieldLevel = call.name == AiToolRepository.TOOL_PATCH_HISTORY_MEMORY
        && subItems.size == 1
        && subItems[0].opLabel.startsWith("patch_row")
        && subItems[0].tier == "INCREMENTAL"
    val secretOk = secretFill?.isSatisfied() ?: true
    return PendingToolBuild(
        ui = PendingToolCallUi(
            callId = call.id,
            toolName = call.name,
            displayName = displayName,
            summary = summary,
            previewDetail = previewText,
            checked = when {
                chapterArgsError != null -> false
                subItems.isNotEmpty() -> subItems.any { it.checked && it.validationError == null }
                else -> blockingError == null
            },
            validationError = if (subItems.isNotEmpty() && opPreviews.isNotEmpty()) null else blockingError,
            validationWarnings = buildList {
                addAll(validation.warnings.map { it.message })
                if (secretFill != null && !secretOk) {
                    add(appCtx.getString(R.string.ai_tts_secret_fill_required))
                }
            }.toImmutableList(),
            fieldChanges = fieldChanges.map {
                FieldChangeUi(it.path, it.oldValue, it.newValue)
            }.toImmutableList(),
            deepLink = StructuredDataDeepLinkResolver.resolve(call.name, workingArgs, convId),
            subItems = subItems,
            editableArgsJson = workingEditableJson,
            argsValidationError = chapterArgsError,
            tier = tier,
            allowFieldLevelApproval = allowFieldLevel,
            requiresLocalSecret = secretFill != null,
            localSecretSatisfied = secretOk,
        ),
        secretFill = secretFill,
    )
}

// =============================================================================
// Args validation — JSON parse error formatting
// =============================================================================

internal fun AiChatViewModel.formatArgsValidationError(json: String): String? {
    val ex = runCatching {
        com.google.gson.JsonParser.parseString(json).asJsonObject
    }.exceptionOrNull() ?: return null
    val detail = ex.message
        ?.lineSequence()
        ?.firstOrNull()
        ?.trim()
        .orEmpty()
    return if (detail.isBlank()) {
        appCtx.getString(R.string.ai_tool_args_json_invalid)
    } else {
        appCtx.getString(R.string.ai_tool_args_json_invalid_detail, detail)
    }
}

// =============================================================================
// Confirm / reject all pending tools
// =============================================================================

internal fun AiChatViewModel.confirmPendingTools() {
    val deferred = confirmationDeferred ?: return
    val items = _uiState.value.pendingToolConfs
    val batchFeedback = _uiState.value.pendingToolBatchFeedback
    val secretByCall = _uiState.value.pendingTtsSecretFills.associateBy { it.callId }
    clearPendingToolApprovalUi()
    val approved = items.filter { it.isApprovable() }.map { item ->
        val original = approvedToolCalls.find { it.id == item.callId } ?: return@map null
        val argsJson = item.editableArgsJson.ifBlank { original.arguments }
        var args = runCatching { com.google.gson.JsonParser.parseString(argsJson).asJsonObject }
            .getOrNull() ?: return@map null
        var executionWarning: String? = null
        if (item.subItems.isNotEmpty() && original.name == AiToolRepository.TOOL_PATCH_HISTORY_MEMORY) {
            val checkedIndices = item.subItems.filter { it.checked }.map { it.opIndex }.toSet()
            val ctx = StructuredDataContextParser.parse(original.name, args, currentConversationId.value)
            val skippedOpLabels = item.subItems
                .filter { !it.checked || it.validationError != null }
                .map { sub ->
                    sub.validationError?.let { err -> "${sub.opLabel}: $err" } ?: sub.opLabel
                }
            var filteredOps = ctx.memoryOps.filterIndexed { index, _ -> checkedIndices.contains(index) }
            if (item.allowFieldLevelApproval && item.subItems.size == 1) {
                val sub = item.subItems.first()
                val beforeKeys = (filteredOps.firstOrNull() as? io.legado.app.domain.model.MemoryTableOp.PatchRow)
                    ?.data?.keys.orEmpty()
                filteredOps = filteredOps.map { op ->
                    if (op is io.legado.app.domain.model.MemoryTableOp.PatchRow) {
                        val approvedData = op.data.filterKeys { key ->
                            sub.fieldChanges.any { fc -> fc.checked && fc.path.endsWith("/$key") }
                        }
                        op.copy(data = approvedData)
                    } else op
                }.filter { op ->
                    op !is io.legado.app.domain.model.MemoryTableOp.PatchRow || op.data.isNotEmpty()
                }
                val afterKeys = (filteredOps.firstOrNull() as? io.legado.app.domain.model.MemoryTableOp.PatchRow)
                    ?.data?.keys.orEmpty()
                val skippedFields = beforeKeys - afterKeys
                if (skippedFields.isNotEmpty()) {
                    executionWarning = appCtx.getString(
                        R.string.ai_tool_partial_fields_skipped,
                        skippedFields.joinToString(", "),
                    )
                }
            }
            if (skippedOpLabels.isNotEmpty()) {
                val opsMsg = appCtx.getString(
                    R.string.ai_tool_partial_ops_skipped,
                    skippedOpLabels.joinToString("; "),
                )
                executionWarning = if (executionWarning.isNullOrBlank()) opsMsg
                else "$executionWarning | $opsMsg"
            }
            args = args.deepCopy().apply {
                add("operations", StructuredDataToolParser.memoryOpsToJsonArray(filteredOps))
            }
        }
        if (item.feedback.isNotBlank()) {
            args = args.deepCopy().apply { addProperty("hint", item.feedback) }
        }
        if (original.name == AiToolRepository.TOOL_PATCH_CLOUD_TTS_ENGINE) {
            val fill = secretByCall[item.callId]
            if (fill != null) {
                args = TtsConfigTools.injectCloudPatchSecrets(
                    args,
                    fill.apiKey.takeIf { it.isNotBlank() },
                    fill.secretKey.takeIf { it.isNotBlank() },
                )
            }
        }
        val needsBookshelfAccess = when (original.name) {
            AiToolRepository.TOOL_EXTRACT_WORLD_BOOK -> true
            AiToolRepository.TOOL_PATCH_BOOK_SOURCE -> {
                val recheck = args.get("recheck")?.takeIf { !it.isJsonNull }?.let { el ->
                    runCatching {
                        when {
                            el.isJsonPrimitive && el.asJsonPrimitive.isBoolean -> el.asBoolean
                            else -> el.asString.toBooleanStrictOrNull()
                        }
                    }.getOrNull()
                } ?: true
                AppConfig.aiAllowBookSourceFetch && recheck
            }
            AiToolRepository.TOOL_WRITE_BOOK_SOURCE_FROM_URL ->
                AppConfig.aiAllowBookSourceFetch &&
                    bookshelfAccessPreviewer.writeNeedsNetwork(args)
            else -> AiToolRepository.isBookshelfAccessConfirmTool(original.name)
        }
        original.copy(
            arguments = args.toString(),
            bookshelfAccessApproved = needsBookshelfAccess,
            webSearchApproved = AiToolRepository.isWebSearchConfirmTool(original.name),
            executionWarning = executionWarning ?: original.executionWarning,
        )
    }.filterNotNull()

    val approvedIds = approved.map { it.id }.toSet()
    val multiPending = items.size > 1
    val skippedFeedback = linkedMapOf<String, String>()
    for (item in items) {
        if (item.callId in approvedIds) continue
        val raw = buildSkippedToolFeedback(item, batchFeedback)
        skippedFeedback[item.callId] = if (multiPending) {
            "${item.displayName}: $raw"
        } else {
            raw
        }
    }
    for (call in approvedToolCalls) {
        if (call.id !in approvedIds && call.id !in skippedFeedback) {
            val fallback = batchFeedback.trim().ifBlank {
                appCtx.getString(R.string.ai_tool_partial_not_approved)
            }
            val name = io.legado.app.ui.config.ai.resolvedToolDisplayName(call.name)
            skippedFeedback[call.id] = if (multiPending) "$name: $fallback" else fallback
        }
    }

    pendingRejectionFeedback = if (approved.isEmpty()) {
        buildToolRejectionFeedback(items, batchFeedback)
            ?: skippedFeedback.values.joinToString("\n").takeIf { it.isNotBlank() }
    } else {
        null
    }
    deferred.complete(
        ToolApprovalDecision(
            approved = approved,
            skippedFeedback = skippedFeedback,
        ),
    )
    // Record timestamps so the same tool family skips confirmation within the trust window
    val now = System.currentTimeMillis()
    for (item in approved) {
        item?.let { recentApprovals[it.name] = now }
    }
    confirmationDeferred = null
}

internal fun AiChatViewModel.buildSkippedToolFeedback(item: PendingToolCallUi, batchFeedback: String): String {
    val parts = buildList {
        item.argsValidationError?.takeIf { it.isNotBlank() }?.let { add(it) }
        item.validationError?.takeIf { it.isNotBlank() && it != item.argsValidationError }?.let { add(it) }
        item.subItems.forEach { sub ->
            when {
                sub.validationError != null -> add("${sub.opLabel}: ${sub.validationError}")
                !sub.checked -> add(appCtx.getString(R.string.ai_tool_partial_op_unchecked, sub.opLabel))
            }
        }
        item.feedback.takeIf { it.isNotBlank() }?.let { add(it) }
        if (isEmpty() && batchFeedback.isNotBlank()) add(batchFeedback.trim())
        if (isEmpty()) add(appCtx.getString(R.string.ai_tool_partial_not_approved))
    }
    return parts.joinToString("; ")
}

internal fun AiChatViewModel.rejectPendingTools() {
    val deferred = confirmationDeferred ?: return
    val items = _uiState.value.pendingToolConfs
    pendingRejectionFeedback = buildToolRejectionFeedback(items, _uiState.value.pendingToolBatchFeedback)
    clearPendingToolApprovalUi()
    deferred.complete(ToolApprovalDecision())
    confirmationDeferred = null
}

// =============================================================================
// JsonObject utility — deep copy
// =============================================================================

internal fun com.google.gson.JsonObject.deepCopy(): com.google.gson.JsonObject =
    com.google.gson.JsonParser.parseString(toString()).asJsonObject

// =============================================================================
// Snapshot undo — preview & confirm restore of mutation snapshots
// =============================================================================

internal fun AiChatViewModel.prepareSnapshotUndo(snapshotId: String) {
    viewModelScope.launch {
        val preview = mutationSnapshotService.previewRestore(snapshotId)
        if (preview == null) {
            _effects.tryEmit(
                AiChatEffect.ShowMessage(appCtx.getString(R.string.ai_snapshot_not_found)),
            )
            return@launch
        }
        _uiState.update {
            it.copy(
                pendingSnapshotUndo = PendingSnapshotUndoUi(
                    snapshotId = preview.snapshotId,
                    resourceType = preview.resourceType,
                    summary = preview.summary,
                    changes = preview.changes.map { fc ->
                        FieldChangeUi(fc.path, fc.oldValue, fc.newValue)
                    }.toImmutableList(),
                ),
            )
        }
    }
}

internal fun AiChatViewModel.confirmSnapshotUndo() {
    val pending = _uiState.value.pendingSnapshotUndo ?: return
    _uiState.update { it.copy(pendingSnapshotUndo = null) }
    viewModelScope.launch {
        val result = mutationSnapshotService.restore(pending.snapshotId)
        refreshUserCardState(currentConversationId.value)
        val msg = if (result.success) {
            snapshotUndoSuccessMessage(result.resourceType.ifBlank { pending.resourceType })
        } else {
            appCtx.getString(R.string.ai_memory_undo_failed, result.message)
        }
        _effects.tryEmit(AiChatEffect.ShowMessage(msg))
    }
}

internal fun AiChatViewModel.snapshotUndoSuccessMessage(resourceType: String): String = when (resourceType) {
    "memory_table" -> appCtx.getString(R.string.ai_memory_undo_success)
    "outline" -> appCtx.getString(R.string.ai_snapshot_undo_success_outline)
    "character_card" -> appCtx.getString(R.string.ai_snapshot_undo_success_character)
    "user_card" -> appCtx.getString(R.string.ai_snapshot_undo_success_user)
    "world_book" -> appCtx.getString(R.string.ai_snapshot_undo_success_world_book)
    else -> appCtx.getString(R.string.ai_snapshot_undo_success_generic)
}

// =============================================================================
// Web search — configure hint resource lookup (used by buildPendingToolCallUi)
// =============================================================================

internal fun AiChatViewModel.webSearchConfigureHintRes(): Int =
    if (AppConfig.aiWebSearchMode == "page") {
        R.string.ai_web_search_configure_page_first
    } else {
        R.string.ai_web_search_configure_first
    }
