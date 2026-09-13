package io.legado.app.domain.usecase.structured

import com.google.gson.JsonObject
import io.legado.app.data.dao.BookDao
import io.legado.app.data.entities.AiPromptTemplate
import io.legado.app.data.entities.AiToolConfig
import io.legado.app.data.entities.AiWorldBook
import io.legado.app.data.entities.AiWorldBookEntry
import io.legado.app.data.entities.Book
import io.legado.app.domain.gateway.AiChatGateway
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiPromptTemplateGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.AiToolConfigGateway
import io.legado.app.domain.gateway.AiWorldBookGateway
import io.legado.app.domain.gateway.AiWorldBookEntryGateway
import io.legado.app.domain.model.AiCallMeta
import io.legado.app.domain.model.AiCallSource
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.WorldBookEntryPatch
import io.legado.app.domain.model.WorldBookPatch
import io.legado.app.domain.usecase.ExtractWorldBookUseCase
import io.legado.app.domain.usecase.ImportWorldInfoUseCase
import io.legado.app.domain.usecase.ai.resolvedSubModelProfileId
import io.legado.app.utils.GSON
import java.util.UUID

class WorldBookMutator(
    private val gateway: AiWorldBookGateway,
    private val worldBookEntryGateway: AiWorldBookEntryGateway,
    private val bookDao: BookDao,
    private val extractWorldBookUseCase: ExtractWorldBookUseCase,
    private val importWorldInfoUseCase: ImportWorldInfoUseCase,
    private val toolConfigGateway: AiToolConfigGateway,
    private val aiChatGateway: AiChatGateway,
    private val aiProfileGateway: AiProfileGateway,
    private val aiTextGateway: AiTextGateway,
    private val promptTemplateGateway: AiPromptTemplateGateway,
) {
    data class PatchResult(
        val success: Boolean,
        val message: String,
        val worldBookId: String = "",
        val entryId: String = "",
        val changes: List<FieldChange> = emptyList(),
    )

    suspend fun read(worldBookId: String, entryId: String? = null): String {
        val wb = gateway.getById(worldBookId) ?: return """{"error":"World book not found: $worldBookId"}"""
        if (!wb.enabled) return """{"error":"World book '$worldBookId' is disabled"}"""
        val entry = entryId?.takeIf { it.isNotBlank() }?.let { id ->
            worldBookEntryGateway.getById(id)?.takeIf { it.worldBookId == worldBookId && it.enabled }
        }
        if (entryId != null && entryId.isNotBlank() && entry == null) {
            return """{"error":"World book entry not found: $entryId"}"""
        }
        if (entry != null) {
            return GSON.toJson(
                mapOf(
                    "worldBookId" to wb.id,
                    "worldBookName" to wb.name,
                    "entry" to entryToMap(entry),
                ),
            )
        }
        val entries = worldBookEntryGateway.getForWorldBook(worldBookId).sortedBy { it.priority }
        return GSON.toJson(
            mapOf(
                "id" to wb.id,
                "name" to wb.name,
                "bookName" to wb.bookName,
                "bookAuthor" to wb.bookAuthor,
                "writingStyle" to wb.writingStyle,
                "grammar" to wb.grammar,
                "plotSummary" to wb.plotSummary,
                "representativeDialogues" to wb.representativeDialogues,
                "representativeProse" to wb.representativeProse,
                "entries" to entries.map { entryCatalogMap(it) },
                "hint" to "Pass entryId to read one lore entry body. Prefer a keyword search first when many entries.",
            ),
        )
    }

    fun previewPatch(existing: AiWorldBook?, patch: WorldBookPatch): List<FieldChange> {
        if (patch.generate) {
            return listOf(FieldChange("ai_generate", "", "World book will be generated from conversation"))
        }
        val changes = mutableListOf<FieldChange>()
        if (hasBookFieldPatch(patch)) {
            val oldFields = worldBookFields(existing)
            val newFields = worldBookFields(mergePatch(existing, patch))
            changes += StructuredDataDiff.diffFields(oldFields, newFields)
        }
        patch.entry?.let { entryPatch ->
            changes += previewEntryPatch(existing?.id ?: patch.worldBookId, entryPatch)
        }
        return changes
    }

    suspend fun applyPatch(patch: WorldBookPatch): PatchResult {
        if (patch.generate) return generateFromConversation(patch)

        val entryPatch = patch.entry
        if (entryPatch != null) {
            val wbId = patch.worldBookId?.takeIf { it.isNotBlank() }
                ?: return PatchResult(false, "worldBookId is required to patch lore entries")
            gateway.getById(wbId) ?: return PatchResult(false, "World book not found: $wbId")
            val entryResult = applyEntryPatch(wbId, entryPatch)
            if (!entryResult.success) return entryResult
            if (hasBookFieldPatch(patch)) {
                val bookResult = patchFields(patch.copy(worldBookId = wbId))
                if (!bookResult.success) return bookResult
                return PatchResult(
                    success = true,
                    message = "${entryResult.message}; ${bookResult.message}",
                    worldBookId = wbId,
                    entryId = entryResult.entryId,
                    changes = entryResult.changes + bookResult.changes,
                )
            }
            return entryResult
        }

        val id = patch.worldBookId?.takeIf { it.isNotBlank() }
        return if (id != null) {
            patchFields(patch.copy(worldBookId = id))
        } else {
            createBlank(patch)
        }
    }

    suspend fun applyEntryPatch(worldBookId: String, patch: WorldBookEntryPatch): PatchResult {
        if (patch.isDelete) {
            val entryId = patch.entryId?.takeIf { it.isNotBlank() }
                ?: return PatchResult(false, "entryId is required to delete a lore entry", worldBookId)
            val existing = worldBookEntryGateway.getById(entryId)
                ?: return PatchResult(false, "World book entry not found: $entryId", worldBookId)
            if (existing.worldBookId != worldBookId) {
                return PatchResult(false, "Entry $entryId does not belong to world book $worldBookId", worldBookId)
            }
            worldBookEntryGateway.delete(entryId)
            return PatchResult(
                success = true,
                message = "Lore entry deleted",
                worldBookId = worldBookId,
                entryId = entryId,
                changes = listOf(FieldChange("entry", existing.name.ifBlank { entryId }, "(deleted)")),
            )
        }

        val existing = patch.entryId?.takeIf { it.isNotBlank() }?.let { id ->
            worldBookEntryGateway.getById(id)?.takeIf { it.worldBookId == worldBookId }
        }
        if (patch.entryId != null && patch.entryId.isNotBlank() && existing == null) {
            return PatchResult(false, "World book entry not found: ${patch.entryId}", worldBookId)
        }

        val now = System.currentTimeMillis()
        val merged = if (existing != null) {
            existing.copy(
                name = patch.name ?: existing.name,
                keys = patch.keys ?: existing.keys,
                content = patch.content ?: existing.content,
                constant = patch.constant ?: existing.constant,
                priority = patch.priority ?: existing.priority,
                enabled = patch.enabled ?: existing.enabled,
                position = normalizePosition(patch.position ?: existing.position),
                insertDepth = patch.insertDepth?.coerceAtLeast(0) ?: existing.insertDepth,
                role = normalizeRole(patch.role ?: existing.role, patch.position ?: existing.position),
                scanDepth = patch.scanDepth?.coerceAtLeast(0) ?: existing.scanDepth,
                updatedAt = now,
            )
        } else {
            val content = patch.content?.takeIf { it.isNotBlank() }
                ?: return PatchResult(false, "content is required to create a lore entry", worldBookId)
            AiWorldBookEntry(
                id = "wbe_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                worldBookId = worldBookId,
                name = patch.name.orEmpty(),
                keys = patch.keys.orEmpty(),
                content = content,
                constant = patch.constant ?: false,
                priority = patch.priority ?: 100,
                enabled = patch.enabled ?: true,
                position = normalizePosition(patch.position),
                insertDepth = patch.insertDepth?.coerceAtLeast(0) ?: 0,
                role = normalizeRole(patch.role, patch.position),
                scanDepth = patch.scanDepth?.coerceAtLeast(0) ?: 0,
                createdAt = now,
                updatedAt = now,
            )
        }
        worldBookEntryGateway.upsert(merged)
        val changes = previewEntryPatch(worldBookId, patch.copy(entryId = merged.id))
        return PatchResult(
            success = true,
            message = if (existing != null) "Lore entry updated" else "Lore entry created",
            worldBookId = worldBookId,
            entryId = merged.id,
            changes = changes.ifEmpty {
                listOf(FieldChange("entry", "", merged.name.ifBlank { merged.id }))
            },
        )
    }

    private fun previewEntryPatch(worldBookId: String?, patch: WorldBookEntryPatch): List<FieldChange> {
        if (patch.isDelete) {
            return listOf(FieldChange("entry", patch.entryId.orEmpty(), "(delete)"))
        }
        val label = patch.entryId?.takeIf { it.isNotBlank() } ?: "new"
        val fields = mutableMapOf<String, String>()
        patch.name?.let { fields["entryName"] = it }
        patch.keys?.let { fields["keys"] = it }
        patch.content?.let { fields["content"] = it.take(80) }
        patch.constant?.let { fields["constant"] = it.toString() }
        patch.priority?.let { fields["priority"] = it.toString() }
        patch.enabled?.let { fields["enabled"] = it.toString() }
        patch.position?.let { fields["position"] = it }
        patch.insertDepth?.let { fields["insertDepth"] = it.toString() }
        patch.role?.let { fields["role"] = it }
        patch.scanDepth?.let { fields["scanDepth"] = it.toString() }
        if (fields.isEmpty()) {
            return listOf(FieldChange("entry:$label", "", "(no fields)"))
        }
        return fields.map { (k, v) -> FieldChange("entry:$label.$k", "", v) }
    }

    private fun hasBookFieldPatch(patch: WorldBookPatch): Boolean =
        patch.name != null ||
            patch.bookUrl != null ||
            patch.bookName != null ||
            patch.bookAuthor != null ||
            patch.writingStyle != null ||
            patch.grammar != null ||
            patch.plotSummary != null ||
            patch.representativeDialogues != null ||
            patch.representativeProse != null ||
            patch.sourceChapterIndices != null

    private fun entryToMap(entry: AiWorldBookEntry): Map<String, Any?> = mapOf(
        "id" to entry.id,
        "name" to entry.name,
        "keys" to entry.keys,
        "content" to entry.content,
        "constant" to entry.constant,
        "priority" to entry.priority,
        "enabled" to entry.enabled,
        "position" to entry.position,
        "insertDepth" to entry.insertDepth,
        "role" to entry.role,
        "scanDepth" to entry.scanDepth,
    )

    private fun entryCatalogMap(entry: AiWorldBookEntry): Map<String, Any?> = mapOf(
        "id" to entry.id,
        "name" to entry.name,
        "keys" to entry.keys,
        "enabled" to entry.enabled,
        "constant" to entry.constant,
        "priority" to entry.priority,
        "position" to entry.position,
        "insertDepth" to entry.insertDepth,
        "role" to entry.role,
        "scanDepth" to entry.scanDepth,
    )

    private fun normalizePosition(raw: String?): String {
        val p = raw?.trim()?.lowercase().orEmpty()
        return when (p) {
            AiWorldBookEntry.POSITION_IN_CHAT, "inchat", "@d", "6" -> AiWorldBookEntry.POSITION_IN_CHAT
            else -> AiWorldBookEntry.POSITION_PREFIX
        }
    }

    private fun normalizeRole(raw: String?, position: String?): String {
        if (normalizePosition(position) != AiWorldBookEntry.POSITION_IN_CHAT) {
            return AiWorldBookEntry.ROLE_SYSTEM
        }
        return when (raw?.trim()?.lowercase()) {
            AiWorldBookEntry.ROLE_USER, "user" -> AiWorldBookEntry.ROLE_USER
            AiWorldBookEntry.ROLE_ASSISTANT, "assistant" -> AiWorldBookEntry.ROLE_ASSISTANT
            else -> AiWorldBookEntry.ROLE_SYSTEM
        }
    }

    suspend fun patchFields(patch: WorldBookPatch): PatchResult {
        val existingId = patch.worldBookId?.takeIf { it.isNotBlank() }
            ?: return PatchResult(false, "worldBookId is required to patch an existing world book")
        val existing = gateway.getById(existingId)
            ?: return PatchResult(false, "World book not found: $existingId")
        val changes = previewPatch(existing, patch)
        val merged = mergePatch(existing, patch)
        gateway.save(
            name = merged.name,
            bookUrl = merged.bookUrl,
            bookName = merged.bookName,
            bookAuthor = merged.bookAuthor,
            writingStyle = merged.writingStyle,
            grammar = merged.grammar,
            plotSummary = merged.plotSummary,
            representativeDialogues = merged.representativeDialogues,
            representativeProse = merged.representativeProse,
            sourceChapterIndices = merged.sourceChapterIndices,
            worldBookId = merged.id,
            enabled = merged.enabled,
            canonical = merged.canonical,
        )
        return PatchResult(true, "World book updated", merged.id, changes = changes)
    }

    suspend fun createBlank(patch: WorldBookPatch): PatchResult {
        val name = patch.name?.takeIf { it.isNotBlank() } ?: "新世界书"
        val blank = AiWorldBook(
            id = "",
            name = name,
            bookUrl = patch.bookUrl.orEmpty(),
            bookName = patch.bookName.orEmpty(),
            bookAuthor = patch.bookAuthor.orEmpty(),
            writingStyle = patch.writingStyle.orEmpty(),
            grammar = patch.grammar.orEmpty(),
            plotSummary = patch.plotSummary.orEmpty(),
            representativeDialogues = patch.representativeDialogues.orEmpty(),
            representativeProse = patch.representativeProse.orEmpty(),
            sourceChapterIndices = patch.sourceChapterIndices.orEmpty(),
        )
        val changes = previewPatch(null, patch)
        val saved = gateway.save(
            name = blank.name,
            bookUrl = blank.bookUrl,
            bookName = blank.bookName,
            bookAuthor = blank.bookAuthor,
            writingStyle = blank.writingStyle,
            grammar = blank.grammar,
            plotSummary = blank.plotSummary,
            representativeDialogues = blank.representativeDialogues,
            representativeProse = blank.representativeProse,
            sourceChapterIndices = blank.sourceChapterIndices,
            worldBookId = null,
            canonical = blank.canonical,
        )
        return PatchResult(true, "World book '$name' created", saved.id, changes = changes)
    }

    private suspend fun generateFromConversation(patch: WorldBookPatch): PatchResult {
        val convId = patch.conversationId?.takeIf { it.isNotBlank() }
            ?: return PatchResult(false, "conversationId is required for generate mode")
        val messagesText = aiChatGateway.getMessagesForRegeneration(convId).joinToString("\n\n") { msg ->
            "[${if (msg.first == "user") "用户" else "助手"}] ${msg.second.take(2000)}"
        }
        val existingHint = patch.worldBookId?.let { id ->
            gateway.getById(id)?.let { existing ->
                buildString {
                    append("\n\n## 当前世界书（请在此基础上更新）\n")
                    append("名称：${existing.name}\n")
                    if (existing.writingStyle.isNotBlank()) append("文风：${existing.writingStyle.take(500)}\n")
                    if (existing.plotSummary.isNotBlank()) append("情节：${existing.plotSummary.take(500)}\n")
                }
            }
        }.orEmpty()
        val prompt = promptTemplateGateway.getPrompt(AiPromptTemplate.GENERATE_WORLD_BOOK_PROMPT)
        val system = io.legado.app.domain.usecase.ai.PromptRoleSplit.stripPlaceholders(prompt)
        val user = buildString {
            if (messagesText.isNotBlank()) append(messagesText)
            if (existingHint.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append(existingHint.trimStart())
            }
            if (!patch.hint.isNullOrBlank()) {
                if (isNotEmpty()) append("\n\n")
                append("## 额外要求\n").append(patch.hint)
            }
        }.ifBlank { "（无对话记录）" }

        val config = toolConfigGateway.getByToolName("patch_world_book")
        val result = generateWithConfig(system, user, config)
        return result.fold(
            onSuccess = { response ->
                val jsonStr = extractJsonObject(response.text)
                runCatching {
                    val obj = GSON.fromJson(jsonStr, JsonObject::class.java)
                    val name = obj.get("name")?.asString?.takeIf { it.isNotBlank() }
                        ?: patch.name?.takeIf { it.isNotBlank() }
                        ?: "新世界书"
                    val saved = gateway.save(
                        name = name,
                        bookUrl = patch.bookUrl.orEmpty(),
                        bookName = patch.bookName.orEmpty(),
                        bookAuthor = patch.bookAuthor.orEmpty(),
                        writingStyle = obj.get("writingStyle")?.asString.orEmpty(),
                        grammar = obj.get("grammar")?.asString.orEmpty(),
                        plotSummary = obj.get("plotSummary")?.asString.orEmpty(),
                        representativeDialogues = obj.get("representativeDialogues")?.asString.orEmpty(),
                        representativeProse = obj.get("representativeProse")?.asString.orEmpty(),
                        sourceChapterIndices = "",
                        worldBookId = patch.worldBookId,
                    )
                    val drafts = ExtractWorldBookUseCase.parseAiEntries(obj.get("entries"))
                    if (drafts.isNotEmpty()) {
                        importWorldInfoUseCase.upsertEntryDrafts(saved.id, drafts)
                    }
                    PatchResult(
                        success = true,
                        message = "World book generated from conversation",
                        worldBookId = saved.id,
                        changes = listOf(FieldChange("name", "", name)),
                    )
                }.getOrElse { PatchResult(false, "Failed to parse AI response: ${it.message}") }
            },
            onFailure = { PatchResult(false, "AI generation failed: ${it.message}") },
        )
    }

    suspend fun extractFromChapters(
        bookUrl: String?,
        bookName: String?,
        bookAuthor: String?,
        chapterIndicesJson: String?,
        worldBookName: String?,
        writingStyle: String? = null,
        grammar: String? = null,
        plotSummary: String? = null,
        representativeDialogues: String? = null,
        representativeProse: String? = null,
        allowNetworkFetch: Boolean = false,
    ): PatchResult {
        val indices = chapterIndicesJson?.takeIf { it.isNotBlank() }?.let { json ->
            runCatching { GSON.fromJson(json, Array<Int>::class.java).toList() }.getOrNull()
        }.orEmpty()

        if (indices.isEmpty()) {
            return PatchResult(
                false,
                "chapterIndices is required for bookshelf extraction. Use patch_world_book with mode=generate to create from conversation.",
            )
        }

        val book = resolveBook(bookUrl, bookName, bookAuthor)
            ?: return PatchResult(false, "Book not found — chapter extraction requires a bookshelf book")

        val name = worldBookName?.takeIf { it.isNotBlank() } ?: "${book.name} 文风模板"
        val config = toolConfigGateway.getByToolName("extract_world_book")
        val subModelId = config.resolvedSubModelProfileId(aiProfileGateway)
        val fields = if (subModelId != null) {
            extractWorldBookUseCase.extractWithModel(
                book, indices, subModelId, allowNetworkFetch = allowNetworkFetch,
            )
        } else {
            extractWorldBookUseCase.extract(book, indices, allowNetworkFetch = allowNetworkFetch)
        }
        val saved = gateway.save(
            name = name,
            bookUrl = book.bookUrl,
            bookName = book.name,
            bookAuthor = book.author,
            writingStyle = fields.writingStyle,
            grammar = fields.grammar,
            plotSummary = fields.plotSummary,
            representativeDialogues = fields.representativeDialogues,
            representativeProse = fields.representativeProse,
            sourceChapterIndices = chapterIndicesJson.orEmpty(),
            worldBookId = null,
        )
        if (fields.entries.isNotEmpty()) {
            importWorldInfoUseCase.upsertEntryDrafts(saved.id, fields.entries)
        }
        return PatchResult(true, "World book '$name' created", saved.id)
    }

    private suspend fun generateWithConfig(
        systemPrompt: String,
        userPrompt: String,
        config: AiToolConfig?,
    ): Result<io.legado.app.domain.model.AiGenerateResponse> {
        val subModelId = config.resolvedSubModelProfileId(aiProfileGateway)
        val messages = io.legado.app.domain.usecase.ai.PromptRoleSplit.messages(systemPrompt, userPrompt)
        return if (subModelId != null) {
            val modelProfile = aiProfileGateway.getModel(subModelId)
                ?: return Result.failure(IllegalStateException("Sub-model not found"))
            val provider = aiProfileGateway.getProvider(modelProfile.providerId)
                ?: return Result.failure(IllegalStateException("Provider not found"))
            aiTextGateway.generate(
                AiGenerateRequest(
                    model = modelProfile.toModelConfig(provider),
                    messages = messages,
                    params = AiGenerationParams(),
                    callMeta = AiCallMeta(AiCallSource.WORLDBOOK),
                )
            )
        } else {
            val preset = aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
                ?: return Result.failure(IllegalStateException("No chat model configured"))
            aiTextGateway.generate(
                AiGenerateRequest(
                    model = preset.model,
                    messages = messages,
                    params = preset.params,
                    callMeta = AiCallMeta(AiCallSource.WORLDBOOK),
                )
            )
        }
    }

    private fun extractJsonObject(text: String): String {
        var t = text.trim().replace(Regex("""```(?:json)?\s*"""), "").replace(Regex("""\s*```"""), "").trim()
        val start = t.indexOf('{')
        var depth = 0
        var end = -1
        for (i in start until t.length) {
            when (t[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) { end = i; break } }
            }
        }
        return if (start >= 0 && end > start) t.substring(start, end + 1) else t
    }

    private fun mergePatch(existing: AiWorldBook?, patch: WorldBookPatch): AiWorldBook {
        val base = existing ?: AiWorldBook(id = "", name = patch.name.orEmpty())
        return base.copy(
            name = patch.name ?: base.name,
            bookUrl = patch.bookUrl ?: base.bookUrl,
            bookName = patch.bookName ?: base.bookName,
            bookAuthor = patch.bookAuthor ?: base.bookAuthor,
            writingStyle = patch.writingStyle ?: base.writingStyle,
            grammar = patch.grammar ?: base.grammar,
            plotSummary = patch.plotSummary ?: base.plotSummary,
            representativeDialogues = patch.representativeDialogues ?: base.representativeDialogues,
            representativeProse = patch.representativeProse ?: base.representativeProse,
            sourceChapterIndices = patch.sourceChapterIndices ?: base.sourceChapterIndices,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun worldBookFields(wb: AiWorldBook?): Map<String, String> = mapOf(
        "name" to (wb?.name.orEmpty()),
        "writingStyle" to (wb?.writingStyle.orEmpty()),
        "grammar" to (wb?.grammar.orEmpty()),
        "plotSummary" to (wb?.plotSummary.orEmpty()),
        "representativeDialogues" to (wb?.representativeDialogues.orEmpty()),
        "representativeProse" to (wb?.representativeProse.orEmpty()),
    )

    private fun resolveBook(bookUrl: String?, bookName: String?, bookAuthor: String?): Book? {
        bookUrl?.takeIf { it.isNotBlank() }?.let { url ->
            bookDao.getBook(url)?.let { return it }
        }
        val name = bookName?.trim().orEmpty()
        val author = bookAuthor?.trim().orEmpty()
        if (name.isNotBlank() && author.isNotBlank()) {
            bookDao.getBook(name, author)?.let { return it }
        }
        if (name.isNotBlank()) return bookDao.findByName(name).firstOrNull()
        return bookDao.lastReadBook
    }
}

private fun io.legado.app.data.entities.AiModelProfile.toModelConfig(
    provider: io.legado.app.data.entities.AiProviderProfile,
): AiModelConfig = AiModelConfig(
    id = id,
    provider = AiProviderConfig(
        id = provider.id, name = provider.name, protocol = provider.protocol,
        baseUrl = provider.baseUrl, apiKey = provider.apiKey,
        modelsUrl = provider.modelsUrl,
        chatPath = provider.chatPath ?: "/chat/completions",
        responsesPath = provider.responsesPath ?: "/responses",
        messagesPath = provider.messagesPath ?: "/v1/messages",
        modelsPath = provider.modelsPath,
    ),
    displayName = displayName,
    modelId = modelId,
    contextWindow = contextWindow,
    maxOutputTokens = maxOutputTokens,
)
