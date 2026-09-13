@file:Suppress("DEPRECATION")

package io.legado.app.ui.ai.chat.html

import io.legado.app.domain.model.AiMessagePart
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.ai.chat.AiChatMessageUi
import io.legado.app.ui.ai.chat.AiChatUiState
import io.legado.app.ui.ai.chat.FieldChangeUi
import io.legado.app.ui.ai.chat.PendingToolCallUi
import io.legado.app.ui.ai.chat.PendingToolSubItem
import io.legado.app.utils.GSON
import java.io.File
import kotlin.text.Charsets

/**
 * Slim projection of [AiChatUiState] for the HTML render track.
 * Omits outline / workspace heavy payloads to keep WebView updates light.
 */
internal object AiChatHtmlStateMapper {

    private const val COMPRESSED_SUMMARY_MAX = 2000
    private const val USER_DESCRIPTION_MAX = 500

    fun toJson(state: AiChatUiState, themeStore: AiChatHtmlThemeStore? = null): String =
        GSON.toJson(project(state, themeStore))

    fun project(
        state: AiChatUiState,
        themeStore: AiChatHtmlThemeStore? = null,
    ): Map<String, Any?> {
        val themeId = AppConfig.aiChatHtmlThemeId.ifBlank { AiChatHtmlThemeStore.DEFAULT_THEME_ID }
        val manifest = themeStore?.readManifest(themeId)
        return mapOf(
            "currentConversationId" to state.currentConversationId,
            "conversationType" to state.conversationType,
            "isSending" to state.isSending,
            "messagesReady" to state.messagesReady,
            "hasMoreHistory" to state.hasMoreHistory,
            "isLoadingHistory" to state.isLoadingHistory,
            "pendingToolBatchFeedback" to state.pendingToolBatchFeedback,
            "conversations" to state.conversations.map { c ->
                mapOf(
                    "id" to c.id,
                    "title" to c.title,
                    "updatedAt" to c.updatedAt,
                    "isSelected" to c.isSelected,
                    "providerName" to c.providerName,
                    "modelName" to c.modelName,
                    "type" to c.type,
                    "characterCardId" to c.characterCardId,
                    "promptIds" to c.promptIds,
                )
            },
            "messages" to state.messages.map { projectMessage(it) },
            "streamingMessage" to state.streamingMessage?.let { projectMessage(it) },
            "regeneratingMessageId" to state.regeneratingMessageId,
            "pendingToolConfs" to state.pendingToolConfs.map { projectTool(it) },
            "writingSubMode" to state.writingSubMode,
            "outputMode" to state.outputMode.name,
            "webSearchArmed" to state.webSearchArmed,
            "galgameEnabled" to state.galgameEnabled,
            "interCharacterChatEnabled" to state.interCharacterChatEnabled,
            "dialogueHighlightEnabled" to state.dialogueHighlightEnabled,
            "roleplayDialogueBubbleEnabled" to state.roleplayDialogueBubbleEnabled,
            "structuredAutoMaintainEnabled" to state.structuredAutoMaintainEnabled,
            "outlineEnabled" to state.outlineEnabled,
            "workspaceId" to state.workspaceId,
            "workspaceName" to state.workspaceName,
            "selectedCharacterName" to (
                state.selectedCharacterCards.firstOrNull()?.name
                    ?: state.selectedCharacterCard?.name
                    ?: ""
            ),
            "userName" to state.userName,
            "userDescription" to state.userDescription.take(USER_DESCRIPTION_MAX),
            "userCardEnabled" to state.userCardEnabled,
            "continueActionPrompt" to state.continueActionPrompt,
            "writingRoundCount" to state.writingRoundCount,
            "reasoningLevel" to state.reasoningLevel.name,
            "suggestions" to state.suggestions.toList(),
            "suggestionsLoading" to state.suggestionsLoading,
            "galgameHudHtml" to state.galgameHudHtml,
            "galgameHudLoading" to state.galgameHudLoading,
            "pendingAttachments" to state.pendingAttachments.map { a ->
                mapOf(
                    "id" to a.id,
                    "displayName" to a.displayName,
                    "mimeType" to a.mimeType,
                    "kind" to a.kind,
                    "isProcessing" to a.isProcessing,
                    "truncated" to a.truncated,
                )
            },
            "isProcessingAttachments" to state.isProcessingAttachments,
            "postEditEnabled" to state.postEditEnabled,
            "isPostEditing" to state.isPostEditing,
            "multiBubbleProtocolEnabled" to state.multiBubbleProtocolEnabled,
            "isMaintainingStructuredData" to state.isMaintainingStructuredData,
            "habitReplyStyle" to state.habitReplyStyle,
            "contextTokensUsed" to state.contextTokensUsed,
            "contextInputBudget" to state.contextInputBudget,
            "contextWindow" to state.contextWindow,
            "isCompressing" to state.isCompressing,
            "cacheHitRatio" to state.cacheHitRatio,
            "contextTokensSource" to state.contextTokensSource,
            "contextCalibrationScale" to state.contextCalibrationScale,
            "contextCacheHitTokens" to state.contextCacheHitTokens,
            "contextUsageSlices" to state.contextUsageSlices.map { slice ->
                mapOf(
                    "category" to slice.category.name,
                    "tokens" to slice.tokens,
                    "detail" to slice.detail,
                )
            },
            "compressedSummary" to state.compressedSummary.take(COMPRESSED_SUMMARY_MAX),
            "selectedCharacterNames" to state.selectedCharacterCards.map { it.name },
            "selectedCharacterCount" to state.selectedCharacterCards.size,
            "selectedCharacterCards" to state.selectedCharacterCards.map { card ->
                mapOf(
                    "id" to card.id,
                    "name" to card.name,
                    "avatarPath" to card.avatarPath,
                    "dramaticRole" to card.dramaticRole,
                )
            },
            "availableSkills" to state.availableSkills.map { skill ->
                mapOf(
                    "skillId" to skill.skillId,
                    "name" to skill.name,
                    "description" to skill.description,
                    "mode" to skill.mode,
                    "globallyEnabled" to skill.globallyEnabled,
                )
            },
            "conversationSkillIds" to state.conversationSkillIds,
            "pendingSnapshotUndo" to state.pendingSnapshotUndo?.let { undo ->
                mapOf(
                    "snapshotId" to undo.snapshotId,
                    "resourceType" to undo.resourceType,
                    "summary" to undo.summary,
                )
            },
            "outlineBranchChoice" to state.outlineBranchChoice?.let { choice ->
                mapOf(
                    "title" to choice.title,
                    "options" to choice.options.map { o ->
                        mapOf("id" to o.id, "label" to o.label)
                    },
                )
            },
            "pendingUserQuestions" to state.pendingUserQuestions?.let { pending ->
                mapOf(
                    "callId" to pending.callId,
                    "questions" to pending.questions.map { q ->
                        mapOf(
                            "id" to q.id,
                            "prompt" to q.prompt,
                            "allowMultiple" to q.allowMultiple,
                            "selectedIds" to q.selectedIds.toList(),
                            "customText" to q.customText,
                            "options" to q.options.map { o ->
                                mapOf("id" to o.id, "label" to o.label)
                            },
                        )
                    },
                )
            },
            "pendingHabitMemoryConfirm" to state.pendingHabitMemoryConfirm?.let { habit ->
                mapOf(
                    "callId" to habit.callId,
                    "lines" to habit.lines.map { line ->
                        mapOf(
                            "op" to line.op,
                            "key" to line.key,
                            "value" to line.value,
                        )
                    },
                )
            },
            "pendingPlan" to state.pendingPlan?.let { plan ->
                mapOf(
                    "messageId" to plan.messageId,
                    "planContent" to plan.planContent,
                    "planReasoning" to plan.planReasoning,
                    "isEditing" to plan.isEditing,
                    "editedPlanContent" to plan.editedPlanContent,
                    "stagedFeedback" to plan.stagedFeedback.map { f ->
                        mapOf(
                            "id" to f.id,
                            "selectedText" to f.selectedText,
                            "feedback" to f.feedback,
                            "startLine" to f.startLine,
                            "endLine" to f.endLine,
                        )
                    },
                )
            },
            "planDetailView" to state.planDetailView?.let { detail ->
                mapOf(
                    "planId" to detail.planId,
                    "messageId" to detail.messageId,
                    "content" to detail.content,
                    "status" to detail.status,
                    "revision" to detail.revision,
                    "updatedAt" to detail.updatedAt,
                )
            },
            "pendingTtsSecretFills" to state.pendingTtsSecretFills.map { fill ->
                mapOf(
                    "callId" to fill.callId,
                    "action" to fill.action,
                    "provider" to fill.provider,
                    "engineLabel" to fill.engineLabel,
                    "apiKeyLabel" to fill.apiKeyLabel,
                    "secretKeyLabel" to fill.secretKeyLabel,
                    "needsApiKey" to fill.needsApiKey,
                    "needsSecretKey" to fill.needsSecretKey,
                    "apiKey" to fill.apiKey,
                    "secretKey" to fill.secretKey,
                )
            },
            "themeId" to themeId,
            "themeVersion" to (manifest?.version ?: 1),
            "themeDirty" to (manifest?.dirty == true),
        )
    }

    /** Full StreamingUpdate effect JSON for the lightweight per-token channel. */
    fun streamingUpdateJson(state: AiChatUiState): String =
        GSON.toJson(streamingUpdateMap(state))

    fun streamingUpdateMap(state: AiChatUiState): Map<String, Any?> {
        val streamMsg = state.streamingMessage
        return mapOf(
            "type" to "StreamingUpdate",
            "message" to (streamMsg?.let { streamingProjection(it) } ?: emptyMap<String, Any?>()),
            "flags" to mapOf(
                "isSending" to state.isSending,
                "regeneratingMessageId" to state.regeneratingMessageId,
                "isProcessingAttachments" to state.isProcessingAttachments,
                "reasoningLevel" to state.reasoningLevel.name,
                "hasMoreHistory" to state.hasMoreHistory,
                "isLoadingHistory" to state.isLoadingHistory,
                "outputMode" to state.outputMode.name,
                "webSearchArmed" to state.webSearchArmed,
                "contextTokensUsed" to state.contextTokensUsed,
                "contextInputBudget" to state.contextInputBudget,
                "contextWindow" to state.contextWindow,
                "isCompressing" to state.isCompressing,
                "cacheHitRatio" to state.cacheHitRatio,
            ),
        )
    }

    /** Slim message projection for the streaming channel — only fields that change per token. */
    fun streamingProjection(msg: AiChatMessageUi): Map<String, Any?> = mapOf(
        "id" to msg.id,
        "role" to msg.role,
        "content" to msg.content,
        "reasoning" to msg.reasoning,
        "speakerName" to msg.speakerName,
        "speakerColorIndex" to msg.speakerColorIndex,
        "thinkingDuration" to msg.thinkingDuration,
        "parts" to msg.parts.map { projectPart(it) },
    )

    private fun projectMessage(msg: AiChatMessageUi): Map<String, Any?> = mapOf(
        "id" to msg.id,
        "role" to msg.role,
        "content" to msg.content,
        "reasoning" to msg.reasoning,
        "createdAt" to msg.createdAt,
        "thinkingDuration" to msg.thinkingDuration,
        "branchIndex" to msg.branchIndex,
        "totalBranches" to msg.totalBranches,
        "parentMessageId" to msg.parentMessageId,
        "speakerName" to msg.speakerName,
        "speakerColorIndex" to msg.speakerColorIndex,
        "speakerCardId" to msg.speakerCardId,
        "excludeFromContext" to msg.excludeFromContext,
        "isPlanMessage" to msg.isPlanMessage,
        "planFileId" to msg.planFileId,
        "parts" to msg.parts.map { projectPart(it) },
        "bookResults" to msg.bookResults.map { b ->
            mapOf(
                "bookUrl" to b.bookUrl,
                "name" to b.name,
                "author" to b.author,
                "origin" to b.origin,
                "coverPath" to b.coverPath,
                "latestChapterTitle" to b.latestChapterTitle,
                "currentChapterTitle" to b.currentChapterTitle,
                "intro" to b.intro,
            )
        },
    )

    private fun projectPart(part: AiMessagePart): Map<String, Any?> = when (part) {
        is AiMessagePart.Text -> mapOf(
            "kind" to "text",
            "text" to part.text,
        )
        is AiMessagePart.Reasoning -> mapOf(
            "kind" to "reasoning",
            "text" to part.text,
        )
        is AiMessagePart.Tool -> mapOf(
            "kind" to "tool",
            "toolCallId" to part.toolCallId,
            "toolName" to part.toolName,
            "input" to part.input,
            "output" to part.output,
            "approvalState" to part.approvalState.name,
        )
        is AiMessagePart.BookResult -> mapOf(
            "kind" to "book_result",
            "bookUrl" to part.bookUrl,
            "name" to part.name,
            "author" to part.author,
            "origin" to part.origin,
            "coverPath" to part.coverPath,
        )
        is AiMessagePart.Attachment -> mapOf(
            "kind" to "attachment",
            "displayName" to part.displayName,
            "mimeType" to part.mimeType,
            "kindType" to part.kind,
            "sizeBytes" to part.sizeBytes,
        )






        is AiMessagePart.Image -> {
            val isSvg = part.mimeType.contains("svg", ignoreCase = true)
            val svgText = if (isSvg) {
                runCatching {
                    val file = File(part.localPath)
                    if (file.isFile) {
                        io.legado.app.help.ai.AiChatAttachmentStore.sanitizeSvg(
                            file.readText(Charsets.UTF_8)
                        )
                    } else null
                }.getOrNull()
            } else null
            mapOf(
                "kind" to "image",
                "localPath" to part.localPath,
                "mimeType" to part.mimeType,
                "remoteUrl" to part.remoteUrl,
                "width" to part.width,
                "height" to part.height,
                "svgText" to svgText,
            )
        }
        is AiMessagePart.SideEffect -> mapOf(
            "kind" to "side_effect",
            "source" to part.source,
        )
        is AiMessagePart.HtmlAppRef -> mapOf(
            "kind" to "html_app_ref",
            "appId" to part.appId,
        )
        is AiMessagePart.PromptInjection -> mapOf(
            "kind" to "prompt_injection",
            "totalEstimatedTokens" to part.totalEstimatedTokens,
        )
        is AiMessagePart.ToolCall -> mapOf(
            "kind" to "tool",
            "toolCallId" to part.id,
            "toolName" to part.name,
            "input" to part.arguments,
            "output" to "",
            "approvalState" to part.approvalState.name,
        )
        is AiMessagePart.ToolResult -> mapOf(
            "kind" to "tool",
            "toolCallId" to part.callId,
            "toolName" to part.name,
            "input" to "",
            "output" to part.content,
            "approvalState" to "AUTO",
        )
    }

    private fun projectTool(tool: PendingToolCallUi): Map<String, Any?> = mapOf(
        "callId" to tool.callId,
        "toolName" to tool.toolName,
        "displayName" to tool.displayName,
        "summary" to tool.summary,
        "previewDetail" to tool.previewDetail,
        "feedback" to tool.feedback,
        "checked" to tool.checked,
        "validationError" to tool.validationError,
        "validationWarnings" to tool.validationWarnings.toList(),
        "fieldChanges" to tool.fieldChanges.map { projectFieldChange(it) },
        "subItems" to tool.subItems.map { projectSubItem(it) },
        "argsValidationError" to tool.argsValidationError,
        "requiresLocalSecret" to tool.requiresLocalSecret,
        "localSecretSatisfied" to tool.localSecretSatisfied,
        "tier" to tool.tier,
    )

    private fun projectSubItem(item: PendingToolSubItem): Map<String, Any?> = mapOf(
        "id" to item.id,
        "opLabel" to item.opLabel,
        "checked" to item.checked,
        "validationError" to item.validationError,
        "tier" to item.tier,
        "fieldChanges" to item.fieldChanges.map { projectFieldChange(it) },
    )

    private fun projectFieldChange(change: FieldChangeUi): Map<String, Any?> = mapOf(
        "path" to change.path,
        "oldValue" to change.oldValue,
        "newValue" to change.newValue,
        "checked" to change.checked,
    )
}
