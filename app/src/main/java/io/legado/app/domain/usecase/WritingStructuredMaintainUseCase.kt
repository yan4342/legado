package io.legado.app.domain.usecase

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.data.entities.AiPromptTemplate
import io.legado.app.data.repository.StructuredDataToolParser
import io.legado.app.domain.gateway.AiChatGateway
import io.legado.app.domain.gateway.AiMemoryTableGateway
import io.legado.app.domain.gateway.AiOutlineGateway
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiPromptTemplateGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.AiToolConfigGateway
import io.legado.app.domain.model.AiCallMeta
import io.legado.app.domain.model.AiCallSource
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.MemoryTableOp
import io.legado.app.domain.model.OutlinePatchMode
import io.legado.app.domain.usecase.WorkspacePrefetchCache
import io.legado.app.domain.usecase.ai.resolvedSubModelProfileId
import io.legado.app.domain.usecase.structured.MemoryTableMutator
import io.legado.app.domain.usecase.structured.MemoryTableRowMatcher
import io.legado.app.domain.usecase.structured.MutationSnapshotService
import io.legado.app.domain.usecase.structured.OutlineAwaitingChoicePolicy
import io.legado.app.domain.usecase.structured.OutlineMaintainSnapshotBuilder
import io.legado.app.domain.usecase.structured.OutlineMutator
import io.legado.app.utils.GSON

/**
 * App-side automatic maintenance of memory tables and outline for writing mode.
 * Memory: every 3 user turns. Outline: every 9 user turns
 * (empty → generate once; non-empty → add/edit/delete only).
 * Outline prompts differ by [writingSubMode] (author vs roleplay).
 */
class WritingStructuredMaintainUseCase(
    private val memoryTableGateway: AiMemoryTableGateway,
    private val outlineGateway: AiOutlineGateway,
    private val memoryTableMutator: MemoryTableMutator,
    private val outlineMutator: OutlineMutator,
    private val mutationSnapshotService: MutationSnapshotService,
    private val aiChatGateway: AiChatGateway,
    private val aiProfileGateway: AiProfileGateway,
    private val aiTextGateway: AiTextGateway,
    private val promptTemplateGateway: AiPromptTemplateGateway,
    private val toolConfigGateway: AiToolConfigGateway,
) {
    companion object {
        private const val TAG = "StructuredMaintain"
        const val MEMORY_INTERVAL = 3L
        const val OUTLINE_INTERVAL = 9L

        /** @deprecated Use [MEMORY_INTERVAL]. Kept for call-site compatibility. */
        const val MAINTAIN_INTERVAL = MEMORY_INTERVAL

        private const val OUTLINE_COOLDOWN_MAX = 64

        /**
         * After the main model [patch_outline]s, defer auto outline maintain until
         * [writingRoundCount] reaches this value (due requires `round >= cooledUntil`).
         */
        private val outlineCooldownUntilRound =
            object : LinkedHashMap<String, Long>(OUTLINE_COOLDOWN_MAX, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
                    size > OUTLINE_COOLDOWN_MAX
            }

        /** Visible for tests. */
        internal fun clearOutlineCooldownForTests() {
            synchronized(outlineCooldownUntilRound) { outlineCooldownUntilRound.clear() }
        }

        /** Visible for tests / shared due-check. */
        internal fun isOutlineDue(
            force: Boolean,
            outlineEnabled: Boolean,
            writingRoundCount: Long,
            cooledUntil: Long,
        ): Boolean = force || (
            outlineEnabled &&
                writingRoundCount % OUTLINE_INTERVAL == 0L &&
                writingRoundCount >= cooledUntil
            )

        /** Visible for tests. */
        internal fun cooldownUntilAfterMainPatch(writingRoundCount: Long): Long =
            writingRoundCount + OUTLINE_INTERVAL
    }

    data class Result(
        val memoryUpdated: Boolean = false,
        val outlineUpdated: Boolean = false,
        val message: String = "",
    )

    /**
     * @param writingRoundCount user-message count for this conversation
     * @param writingSubMode "roleplay" | "author"
     * @param skipMemory if main model already patched memory this turn
     * @param skipOutline if main model already patched outline this turn —
     *   also starts a full [OUTLINE_INTERVAL] cooldown before the next auto outline maintain
     */
    suspend fun maintainIfNeeded(
        conversationId: String,
        writingRoundCount: Long,
        outlineEnabled: Boolean,
        writingSubMode: String = "roleplay",
        skipMemory: Boolean = false,
        skipOutline: Boolean = false,
        force: Boolean = false,
    ): Result {
        if (skipOutline && !force) {
            // Main model already updated outline; skip this turn and defer the next auto pass.
            val until = cooldownUntilAfterMainPatch(writingRoundCount)
            synchronized(outlineCooldownUntilRound) {
                outlineCooldownUntilRound[conversationId] = until
            }
            Log.d(TAG, "outline cooldown until round=$until (main patched)")
        }

        val cooledUntil = synchronized(outlineCooldownUntilRound) {
            outlineCooldownUntilRound[conversationId] ?: 0L
        }
        val dueMemory = force || writingRoundCount % MEMORY_INTERVAL == 0L
        val dueOutline = isOutlineDue(force, outlineEnabled, writingRoundCount, cooledUntil)
        if (!dueMemory && !dueOutline) {
            Log.d(
                TAG,
                "skip: round=$writingRoundCount memory%=$MEMORY_INTERVAL outline%=$OUTLINE_INTERVAL " +
                    "outlineCoolUntil=$cooledUntil",
            )
            return Result(message = "skipped_interval")
        }

        var memoryUpdated = false
        var outlineUpdated = false
        val errors = mutableListOf<String>()

        if (dueMemory) {
            if (!skipMemory) {
                runCatching { maintainMemory(conversationId) }
                    .onSuccess { memoryUpdated = it }
                    .onFailure {
                        Log.w(TAG, "memory maintain failed: ${it.message}")
                        errors.add("memory: ${it.message}")
                    }
            } else {
                Log.d(TAG, "skip memory: already patched by main model")
            }
        }

        if (dueOutline && outlineEnabled) {
            if (!skipOutline) {
                runCatching { maintainOutline(conversationId, writingSubMode) }
                    .onSuccess { updated ->
                        outlineUpdated = updated
                        if (updated) {
                            synchronized(outlineCooldownUntilRound) {
                                outlineCooldownUntilRound.remove(conversationId)
                            }
                        }
                    }
                    .onFailure {
                        Log.w(TAG, "outline maintain failed: ${it.message}")
                        errors.add("outline: ${it.message}")
                    }
            } else {
                Log.d(TAG, "skip outline: already patched by main model")
            }
        }

        return Result(
            memoryUpdated = memoryUpdated,
            outlineUpdated = outlineUpdated,
            message = errors.joinToString("; ").ifBlank { "ok" },
        )
    }

    private suspend fun maintainMemory(conversationId: String): Boolean {
        val tables = memoryTableGateway.getEnabledForConversation(conversationId)
        if (tables.isEmpty()) {
            Log.d(TAG, "skip memory: no tables (will not auto-generate)")
            return false
        }

        val recentMessages = aiChatGateway.getMessagesForRegeneration(conversationId, limit = 24)
        if (recentMessages.isEmpty()) return false

        val tablesSnapshot = buildString {
            tables.forEach { table ->
                val columns = runCatching {
                    GSON.fromJson(table.columns, Array<String>::class.java).toList()
                }.getOrNull() ?: return@forEach
                val rows = memoryTableGateway.getRows(table.id).takeLast(12)
                append("### ${table.name} (tableId=`${table.id}`)\n")
                append("columns: ${columns.joinToString(", ")}\n")
                rows.forEach { row ->
                    append("- rowId=${row.id}: ${row.rowData.take(200)}\n")
                }
                append("\n")
            }
        }
        val chatText = recentMessages.takeLast(18).joinToString("\n") { (role, content) ->
            "[${if (role == "user") "用户" else "助手"}] ${content.take(800)}"
        }
        val system = toMaintainSystemPrompt(
            promptTemplateGateway.getPrompt(AiPromptTemplate.STRUCTURED_MAINTAIN_MEMORY_PROMPT),
        )
        val user = buildString {
            append("当前表格：\n")
            append(tablesSnapshot.trim())
            append("\n\n最近对话：\n")
            append(chatText)
        }

        val response = generateWithToolConfig(system, user, "patch_history_memory", conversationId)
            ?: return false
        val ops = parseMaintainMemoryOps(response, conversationId)
        if (ops.isEmpty()) {
            Log.d(TAG, "memory maintain: no ops parsed")
            return false
        }
        val result = memoryTableMutator.applyOperations(ops)
        Log.d(TAG, "memory patch: success=${result.success} ops=${ops.size}")
        return result.success
    }

    /**
     * 空大纲：首次 generate 建稿；非空：仅增删改（append / search_replace / patch_section），
     * 禁止整篇 replace / 再次 generate。按 writingSubMode 选用作者/扮演模板。
     */
    private suspend fun maintainOutline(conversationId: String, writingSubMode: String): Boolean {
        val existing = outlineGateway.getByConversation(conversationId)
        val current = existing?.content.orEmpty()
        if (current.isBlank()) {
            Log.d(TAG, "outline empty: generate initial draft")
            val hint = if (writingSubMode == "roleplay") {
                "根据最近对话生成角色扮演粗纲：outline_format:2 + 带 id 的卷章骨架；每章 1-2 条推进要点；若已到抉择点可预埋 BRANCH/OPTION (awaiting_choice)。"
            } else {
                "根据最近对话生成线性粗纲：outline_format:2 + 带 id 的卷章标题骨架；每章最多 1-2 条推进要点，保持线性。"
            }
            val result = outlineMutator.applyPatch(
                conversationId,
                OutlinePatchMode.Generate(
                    conversationId = conversationId,
                    hint = hint,
                    supplement = false,
                    writingSubMode = writingSubMode,
                ),
            )
            if (result.success && existing != null && !existing.enabled) {
                outlineGateway.upsert(existing.copy(content = result.content, enabled = false))
            }
            Log.d(TAG, "outline generate: success=${result.success}")
            if (result.success) WorkspacePrefetchCache.markStale(conversationId)
            return result.success
        }

        val isRoleplay = writingSubMode == "roleplay"
        val awaiting = isRoleplay && OutlineAwaitingChoicePolicy.isAwaiting(current)
        val templateKey = if (isRoleplay) {
            AiPromptTemplate.STRUCTURED_MAINTAIN_OUTLINE_ROLEPLAY
        } else {
            AiPromptTemplate.STRUCTURED_MAINTAIN_OUTLINE_AUTHOR
        }
        var prompt = promptTemplateGateway.getPrompt(templateKey)
        if (prompt.isBlank()) {
            prompt = promptTemplateGateway.getPrompt(AiPromptTemplate.STRUCTURED_MAINTAIN_OUTLINE_PROMPT)
        }

        val recentMessages = aiChatGateway.getMessagesForRegeneration(conversationId, limit = 30)
        if (recentMessages.isEmpty()) return false
        val chatText = recentMessages.takeLast(24).joinToString("\n") { (role, content) ->
            "[${if (role == "user") "用户" else "助手"}] ${content.take(600)}"
        }
        val snapshot = OutlineMaintainSnapshotBuilder.build(current)
        val system = toMaintainSystemPrompt(prompt)
        val user = buildString {
            append("大纲快照：\n")
            append(snapshot.trim())
            append("\n\n对话：\n")
            append(chatText)
        }

        val response = generateWithToolConfig(system, user, "patch_outline", conversationId) ?: return false
        val mode = parseMaintainOutlinePatch(response) ?: run {
            Log.d(TAG, "outline maintain: no add/edit/delete patch parsed")
            return false
        }
        when (mode) {
            is OutlinePatchMode.GraphOps -> {
                if (mode.parseErrors.isNotEmpty()) {
                    Log.w(TAG, "outline maintain: invalid graph_ops ${mode.parseErrors.joinToString("; ")}")
                    return false
                }
                if (mode.ops.isEmpty()) {
                    Log.d(TAG, "outline maintain: empty graph_ops")
                    return false
                }
            }
            is OutlinePatchMode.Append -> if (mode.content.isBlank()) {
                Log.d(TAG, "outline maintain: empty append (no plot change)")
                return false
            }
            is OutlinePatchMode.SearchReplace -> if (mode.search.isBlank() || !current.contains(mode.search)) {
                Log.d(TAG, "outline maintain: search_replace miss")
                return false
            }
            is OutlinePatchMode.PatchSection -> if (mode.sectionTitle.isBlank()) {
                Log.d(TAG, "outline maintain: blank patch_section title")
                return false
            }
            else -> Unit
        }
        if (awaiting && !OutlineAwaitingChoicePolicy.isAllowedWhileAwaiting(mode, current)) {
            Log.d(TAG, "outline maintain: blocked while awaiting_choice mode=${mode::class.simpleName}")
            return false
        }
        val result = outlineMutator.applyPatch(conversationId, mode)
        if (result.success) {
            var after = result.content
            if (isRoleplay) {
                val normalized = OutlineAwaitingChoicePolicy.normalizeAwaitingFrontMatter(after)
                if (normalized != after && existing != null) {
                    outlineGateway.upsert(existing.copy(content = normalized, enabled = existing.enabled))
                    after = normalized
                }
            }
            mutationSnapshotService.captureOutlineVersion(
                conversationId = conversationId,
                source = "structured_maintain",
                beforeContent = current,
                afterContent = after,
                enabled = existing?.enabled == true,
            )
            if (existing != null && !existing.enabled) {
                outlineGateway.upsert(existing.copy(content = after, enabled = false))
            }
            WorkspacePrefetchCache.markStale(conversationId)
        }
        Log.d(TAG, "outline maintain: success=${result.success} mode=${mode::class.simpleName}")
        return result.success
    }

    private fun parseMaintainMemoryOps(raw: String, conversationId: String): List<MemoryTableOp> {
        val cleaned = raw.trim()
            .removeSurrounding("```json", "```")
            .removeSurrounding("```", "```")
            .trim()
        val parsed = runCatching { JsonParser.parseString(cleaned) }.getOrNull() ?: return emptyList()
        val opsArray = when {
            parsed.isJsonArray -> parsed.asJsonArray
            parsed.isJsonObject -> parsed.asJsonObject.getAsJsonArray("operations")
            else -> null
        } ?: return emptyList()
        val args = JsonObject().apply { add("operations", opsArray) }
        return StructuredDataToolParser.parseMemoryTableOps(args).map { op ->
            when (op) {
                is MemoryTableOp.GenerateTables -> op.copy(conversationId = conversationId)
                is MemoryTableOp.CreateTable ->
                    if (op.conversationId.isBlank()) op.copy(conversationId = conversationId) else op
                else -> op
            }
        }.filter {
            it !is MemoryTableOp.RegenerateTable &&
                it !is MemoryTableOp.GenerateTables
        }.filter { op ->
            when (op) {
                is MemoryTableOp.AddRow ->
                    MemoryTableRowMatcher.filterMeaningfulData(op.data).isNotEmpty()
                is MemoryTableOp.PatchRow ->
                    MemoryTableRowMatcher.filterMeaningfulData(op.data).isNotEmpty()
                else -> true
            }
        }
    }

    /** Only allow add/edit/delete outline modes; reject generate/replace. */
    private fun parseMaintainOutlinePatch(raw: String): OutlinePatchMode? {
        val cleaned = raw.trim()
            .removeSurrounding("```json", "```")
            .removeSurrounding("```", "```")
            .trim()
        val parsed = runCatching { JsonParser.parseString(cleaned) }.getOrNull() ?: return null
        val obj = when {
            parsed.isJsonObject -> parsed.asJsonObject
            parsed.isJsonArray && parsed.asJsonArray.size() > 0 && parsed.asJsonArray[0].isJsonObject ->
                parsed.asJsonArray[0].asJsonObject
            else -> return null
        }
        val mode = StructuredDataToolParser.parseOutlinePatch(obj) ?: return null
        return when (mode) {
            is OutlinePatchMode.GraphOps -> mode
            // Legacy string modes no longer accepted for auto-maintain
            is OutlinePatchMode.Append,
            is OutlinePatchMode.SearchReplace,
            is OutlinePatchMode.PatchSection,
            is OutlinePatchMode.Generate,
            is OutlinePatchMode.Replace -> {
                Log.d(TAG, "outline maintain: rejected ${mode::class.simpleName}")
                null
            }
        }
    }

    /**
     * Strip legacy placeholders from custom templates so they can be used as a stable SYSTEM prefix.
     */
    private fun toMaintainSystemPrompt(template: String): String =
        io.legado.app.domain.usecase.ai.PromptRoleSplit.stripPlaceholders(template)

    private suspend fun generateWithToolConfig(
        systemPrompt: String,
        userPrompt: String,
        toolName: String,
        conversationId: String,
    ): String? {
        val config = toolConfigGateway.getByToolName(toolName)
        val subModelId = config.resolvedSubModelProfileId(aiProfileGateway)
        val messages = io.legado.app.domain.usecase.ai.PromptRoleSplit.messages(systemPrompt, userPrompt)
        return if (subModelId != null) {
            val modelProfile = aiProfileGateway.getModel(subModelId) ?: return null
            val provider = aiProfileGateway.getProvider(modelProfile.providerId) ?: return null
            val maxChars = modelProfile.contextWindow.coerceAtLeast(4000)
            val cappedMessages = io.legado.app.domain.usecase.ai.PromptRoleSplit.messages(
                systemPrompt,
                userPrompt,
                maxChars,
            )
            aiTextGateway.generate(
                AiGenerateRequest(
                    model = AiModelConfig(
                        id = modelProfile.id,
                        provider = AiProviderConfig(
                            id = provider.id,
                            name = provider.name,
                            protocol = provider.protocol,
                            baseUrl = provider.baseUrl,
                            apiKey = provider.apiKey,
                            modelsUrl = provider.modelsUrl,
                            chatPath = provider.chatPath ?: "/chat/completions",
                            responsesPath = provider.responsesPath ?: "/responses",
                            messagesPath = provider.messagesPath ?: "/v1/messages",
                            modelsPath = provider.modelsPath,
                            headers = emptyMap(),
                            customHeaders = emptyMap(),
                        ),
                        displayName = modelProfile.displayName,
                        modelId = modelProfile.modelId,
                        contextWindow = modelProfile.contextWindow,
                        maxOutputTokens = modelProfile.maxOutputTokens,
                    ),
                    messages = cappedMessages,
                    params = AiGenerationParams(),
                    callMeta = AiCallMeta(AiCallSource.STRUCTURED_MAINTAIN, conversationId),
                ),
            ).getOrNull()?.text
        } else {
            val preset = aiProfileGateway.getTaskPreset(AiTaskType.CHAT) ?: return null
            aiTextGateway.generate(
                AiGenerateRequest(
                    model = preset.model,
                    messages = messages,
                    params = preset.params,
                    callMeta = AiCallMeta(AiCallSource.STRUCTURED_MAINTAIN, conversationId),
                ),
            ).getOrNull()?.text
        }
    }
}
