package io.legado.app.ui.ai.chat.html

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import com.google.gson.JsonObject
import io.legado.app.domain.model.WritingInputMode
import io.legado.app.domain.model.WritingInputModeSwitchState
import io.legado.app.domain.model.WritingUserInput
import io.legado.app.ui.ai.chat.AiChatIntent
import io.legado.app.ui.ai.chat.AiOutputMode
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject

/**
 * JS → Native bridge for the HTML AI chat shell.
 * Only whitelisted [AiChatIntent] types are accepted for chat actions.
 */
internal class AiChatHtmlBridge(
    private val onIntent: (AiChatIntent) -> Unit,
    private val onReady: () -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onBack: () -> Unit,
    private val onListThemes: () -> String = { "[]" },
    private val onGetThemeId: () -> String = { AiChatHtmlThemeStore.DEFAULT_THEME_ID },
    private val onSetThemeId: (String) -> Boolean = { false },
    private val onOpenSheet: (sheet: String, id: String?) -> Unit = { _, _ -> },
    private val onGetFragments: () -> String = { "{}" },
    private val onCopyText: (String) -> Unit = {},
    private val onPickAttachments: () -> Unit = {},
    private val onMatchSlashCommands: (String) -> String = { "[]" },
    private val onSelectionChanged: (String, String, Int, Int) -> Unit = { _, _, _, _ -> },
    private val onSaveImage: (String) -> Unit = {},
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun selectionChanged(path: String, text: String, startLine: Int, endLine: Int) {
        mainHandler.post { onSelectionChanged(path, text, startLine, endLine) }
    }

    @JavascriptInterface
    fun ready() {
        mainHandler.post { onReady() }
    }

    @JavascriptInterface
    fun openSettings() {
        mainHandler.post { onOpenSettings() }
    }

    @JavascriptInterface
    fun goBack() {
        mainHandler.post { onBack() }
    }

    @JavascriptInterface
    fun listThemes(): String = onListThemes()

    @JavascriptInterface
    fun getThemeId(): String = onGetThemeId()

    @JavascriptInterface
    fun setThemeId(id: String?): Boolean {
        val themeId = id?.trim().orEmpty()
        if (themeId.isEmpty()) return false
        mainHandler.post { onSetThemeId(themeId) }
        return true
    }

    @JavascriptInterface
    fun getFragments(): String = onGetFragments()

    @JavascriptInterface
    fun openSheet(json: String?) {
        if (json.isNullOrBlank()) return
        mainHandler.post {
            val obj = GSON.fromJsonObject<JsonObject>(json).getOrNull() ?: return@post
            val sheet = obj.get("sheet")?.asString?.trim().orEmpty()
            val id = obj.get("id")?.asString?.trim()
            if (sheet.isNotEmpty()) onOpenSheet(sheet, id)
        }
    }

    @JavascriptInterface
    fun copyText(text: String?) {
        val value = text.orEmpty()
        if (value.isEmpty()) return
        mainHandler.post { onCopyText(value) }
    }

    @JavascriptInterface
    fun saveImage(localPath: String?) {
        val path = localPath.orEmpty()
        android.util.Log.d("svg", "Bridge.saveImage: path=${path.take(80)} len=${path.length}")
        if (path.isEmpty()) return
        mainHandler.post { onSaveImage(path) }
    }

    @JavascriptInterface
    fun saveSvgCode(svgCode: String?) {
        val code = svgCode.orEmpty()
        android.util.Log.d("svg", "Bridge.saveSvgCode: codeLen=${code.length} startsSvg=${code.take(80)}")
        if (code.isEmpty()) return
        mainHandler.post { onSaveImage(code) }
    }

    @JavascriptInterface
    fun pickAttachments() {
        mainHandler.post { onPickAttachments() }
    }

    @JavascriptInterface
    fun matchSlashCommands(partial: String?): String =
        onMatchSlashCommands(partial.orEmpty())

    @JavascriptInterface
    fun normalizeWritingInput(text: String?, mode: String?): String {
        val inputMode = parseWritingMode(mode)
        return WritingUserInput.normalizeDisplay(text.orEmpty(), inputMode)
    }

    @JavascriptInterface
    fun switchWritingInputMode(json: String?): String {
        val obj = GSON.fromJsonObject<JsonObject>(json.orEmpty()).getOrNull()
            ?: return """{"text":"","mode":"DIALOGUE"}"""
        val text = obj.get("text")?.asString.orEmpty()
        val from = parseWritingMode(obj.get("from")?.asString)
        val to = parseWritingMode(obj.get("to")?.asString)
        val switchState = WritingInputModeSwitchState(
            textBeforeSwitch = obj.get("textBeforeSwitch")?.asString.orEmpty(),
            textAfterSwitch = obj.get("textAfterSwitch")?.asString.orEmpty(),
            modeBeforeSwitch = obj.get("modeBeforeSwitch")?.asString?.let { parseWritingMode(it) },
            modeAfterSwitch = obj.get("modeAfterSwitch")?.asString?.let { parseWritingMode(it) },
        )
        val result = WritingUserInput.onModeSwitch(text, from, to, switchState)
        return GSON.toJson(
            mapOf(
                "text" to result.text,
                "mode" to to.name,
                "textBeforeSwitch" to result.state.textBeforeSwitch,
                "textAfterSwitch" to result.state.textAfterSwitch,
                "modeBeforeSwitch" to result.state.modeBeforeSwitch?.name,
                "modeAfterSwitch" to result.state.modeAfterSwitch?.name,
            ),
        )
    }

    @JavascriptInterface
    fun postIntent(json: String?) {
        if (json.isNullOrBlank()) return
        mainHandler.post {
            val intent = parseIntent(json) ?: return@post
            onIntent(intent)
        }
    }

    companion object {
        private fun parseWritingMode(raw: String?): WritingInputMode =
            runCatching { WritingInputMode.valueOf(raw.orEmpty().uppercase()) }
                .getOrDefault(WritingInputMode.DIALOGUE)

        internal fun parseIntent(json: String): AiChatIntent? {
            val obj = GSON.fromJsonObject<JsonObject>(json).getOrNull() ?: return null
            val type = obj.get("type")?.asString ?: return null
            return try {
                when (type) {
                    "NewConversation" -> AiChatIntent.NewConversation
                    "SelectConversation" -> AiChatIntent.SelectConversation(
                        id = obj.requireString("id"),
                    )
                    "SendMessage" -> AiChatIntent.SendMessage(
                        content = obj.requireString("content"),
                    )
                    "StopGenerating" -> AiChatIntent.StopGenerating
                    "LoadMoreMessages" -> AiChatIntent.LoadMoreMessages
                    "RegenerateMessage" -> AiChatIntent.RegenerateMessage(
                        messageId = obj.requireString("messageId"),
                    )
                    "SwitchBranch" -> AiChatIntent.SwitchBranch(
                        messageId = obj.requireString("messageId"),
                        direction = obj.requireInt("direction"),
                    )
                    "EditMessage" -> AiChatIntent.EditMessage(
                        messageId = obj.requireString("messageId"),
                        newContent = obj.requireString("newContent"),
                    )
                    "DeleteMessage" -> AiChatIntent.DeleteMessage(
                        messageId = obj.requireString("messageId"),
                    )
                    "DeleteConversation" -> AiChatIntent.DeleteConversation(
                        id = obj.requireString("id"),
                    )
                    "RenameConversation" -> AiChatIntent.RenameConversation(
                        id = obj.requireString("id"),
                        title = obj.requireString("title"),
                    )
                    "SwitchMode" -> AiChatIntent.SwitchMode(
                        type = obj.optString("mode").ifBlank { "chat" },
                    )
                    "SetWritingSubMode" -> AiChatIntent.SetWritingSubMode(
                        subMode = obj.requireString("subMode"),
                    )
                    "ToggleConfirmTools" -> AiChatIntent.SetOutputMode(
                        mode = if (obj.get("checked")?.asBoolean == true) AiOutputMode.ASK else AiOutputMode.AUTO,
                    )
                    "SetOutputMode" -> AiChatIntent.SetOutputMode(
                        mode = runCatching {
                            AiOutputMode.valueOf(obj.optString("mode").uppercase())
                        }.getOrDefault(AiOutputMode.ASK),
                    )
                    "AcceptPlan" -> AiChatIntent.AcceptPlan
                    "RejectPlan" -> AiChatIntent.RejectPlan
                    "EditPlan" -> AiChatIntent.EditPlan(
                        content = obj.requireString("content"),
                    )
                    "CancelPlanEdit" -> AiChatIntent.CancelPlanEdit
                    "PlanRevisionFeedback" -> AiChatIntent.PlanRevisionFeedback(
                        selectedText = obj.optString("selectedText"),
                        feedback = obj.optString("feedback"),
                    )
                    "AddPlanFeedback" -> AiChatIntent.AddPlanFeedback(
                        selectedText = obj.optString("selectedText"),
                        feedback = obj.optString("feedback"),
                    )
                    "RemovePlanFeedback" -> AiChatIntent.RemovePlanFeedback(
                        feedbackId = obj.requireString("feedbackId"),
                    )
                    "SubmitAllPlanFeedback" -> AiChatIntent.SubmitAllPlanFeedback
                    "OpenPlan" -> AiChatIntent.OpenPlan(planId = obj.requireString("planId"))
                    "DismissPlanDetail" -> AiChatIntent.DismissPlanDetail
                    "ToggleWebSearch" -> AiChatIntent.ToggleWebSearch
                    "ToggleGalgame" -> AiChatIntent.ToggleGalgame
                    "ToggleInterCharacterChat" -> AiChatIntent.ToggleInterCharacterChat
                    "ToggleDialogueHighlight" -> AiChatIntent.ToggleDialogueHighlight
                    "ToggleRoleplayDialogueBubble" -> AiChatIntent.ToggleRoleplayDialogueBubble
                    "TogglePostEdit" -> AiChatIntent.TogglePostEdit
                    "SetStructuredAutoMaintain" -> AiChatIntent.SetStructuredAutoMaintain(
                        enabled = obj.get("enabled")?.asBoolean == true,
                    )
                    "ToggleToolApproval" -> AiChatIntent.ToggleToolApproval(
                        callId = obj.requireString("callId"),
                    )
                    "ToggleToolSubItemApproval" -> AiChatIntent.ToggleToolSubItemApproval(
                        callId = obj.requireString("callId"),
                        subItemId = obj.requireString("subItemId"),
                    )
                    "UpdateToolFeedback" -> AiChatIntent.UpdateToolFeedback(
                        callId = obj.requireString("callId"),
                        feedback = obj.optString("feedback"),
                    )
                    "UpdateToolBatchFeedback" -> AiChatIntent.UpdateToolBatchFeedback(
                        feedback = obj.optString("feedback"),
                    )
                    "ConfirmPendingTools" -> AiChatIntent.ConfirmPendingTools
                    "RejectPendingTools" -> AiChatIntent.RejectPendingTools
                    "UpdateDraftInput" -> AiChatIntent.UpdateDraftInput(
                        text = obj.optString("text"),
                    )
                    "ShowWorkspaceSheet" -> AiChatIntent.ShowWorkspaceSheet
                    "ContinueWriting" -> AiChatIntent.ContinueWriting
                    "UpdateReasoningLevel" -> {
                        val levelName = obj.optString("level").ifBlank { obj.optString("reasoningLevel") }
                        val level = runCatching {
                            io.legado.app.domain.model.AiReasoningLevel.valueOf(levelName.uppercase())
                        }.getOrElse { io.legado.app.domain.model.AiReasoningLevel.AUTO }
                        AiChatIntent.UpdateReasoningLevel(level)
                    }
                    "SelectSuggestion" -> AiChatIntent.SelectSuggestion(
                        text = obj.requireString("text"),
                    )
                    "DismissSuggestions" -> AiChatIntent.DismissSuggestions
                    "ToggleUserCardEnabled" -> AiChatIntent.ToggleUserCardEnabled
                    "ToggleOutlineEnabled" -> AiChatIntent.ToggleOutlineEnabled
                    "CompressContext" -> AiChatIntent.CompressContext
                    "RemovePendingAttachment" -> AiChatIntent.RemovePendingAttachment(
                        id = obj.requireString("id"),
                    )
                    "ForkConversation" -> AiChatIntent.ForkConversation(
                        messageId = obj.requireString("messageId"),
                    )
                    "RegenerateFromUserMessage" -> AiChatIntent.RegenerateFromUserMessage(
                        userMessageId = obj.optString("userMessageId")
                            .ifBlank { obj.optString("messageId") }
                            .ifBlank { error("Missing string field: userMessageId") },
                    )
                    "RegenerateGalgameHud" -> AiChatIntent.RegenerateGalgameHud
                    "RecreateGalgameHud" -> AiChatIntent.RecreateGalgameHud
                    "SelectOutlineBranch" -> AiChatIntent.SelectOutlineBranch(
                        optionId = obj.requireString("optionId"),
                    )
                    "ToggleQuestionOption" -> AiChatIntent.ToggleQuestionOption(
                        callId = obj.requireString("callId"),
                        questionId = obj.requireString("questionId"),
                        optionId = obj.requireString("optionId"),
                    )
                    "UpdateQuestionCustomText" -> AiChatIntent.UpdateQuestionCustomText(
                        callId = obj.requireString("callId"),
                        questionId = obj.requireString("questionId"),
                        text = obj.optString("text"),
                    )
                    "SubmitUserQuestions" -> AiChatIntent.SubmitUserQuestions(
                        callId = obj.requireString("callId"),
                    )
                    "DismissUserQuestions" -> AiChatIntent.DismissUserQuestions
                    "ConfirmHabitMemory" -> AiChatIntent.ConfirmHabitMemory
                    "RejectHabitMemory" -> AiChatIntent.RejectHabitMemory
                    "UpdateTtsSecretFill" -> AiChatIntent.UpdateTtsSecretFill(
                        callId = obj.requireString("callId"),
                        apiKey = obj.get("apiKey")?.takeIf { !it.isJsonNull }?.asString,
                        secretKey = obj.get("secretKey")?.takeIf { !it.isJsonNull }?.asString,
                    )
                    "AiHelpReply" -> AiChatIntent.AiHelpReply(
                        draftText = obj.optString("draftText").ifBlank { obj.optString("text") },
                        inputMode = parseWritingMode(obj.optString("inputMode").ifBlank { obj.optString("mode") }),
                    )
                    "RestoreSnapshot" -> AiChatIntent.RestoreSnapshot(
                        snapshotId = obj.requireString("snapshotId"),
                    )
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun JsonObject.requireString(key: String): String {
            if (!has(key) || get(key)?.isJsonNull == true) {
                error("Missing string field: $key")
            }
            return get(key)?.asString.orEmpty()
        }

        private fun JsonObject.requireInt(key: String): Int {
            if (!has(key) || get(key)?.isJsonNull == true) {
                error("Missing int field: $key")
            }
            return get(key).asInt
        }

        private fun JsonObject.optString(key: String): String =
            get(key)?.asString.orEmpty()
    }
}
