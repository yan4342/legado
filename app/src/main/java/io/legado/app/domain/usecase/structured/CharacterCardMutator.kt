package io.legado.app.domain.usecase.structured

import com.google.gson.JsonObject
import io.legado.app.data.entities.AiCharacterCard
import io.legado.app.data.entities.AiPromptTemplate
import io.legado.app.data.entities.AiToolConfig
import io.legado.app.help.ai.CharacterCardPerformancePolicy
import io.legado.app.data.repository.parseAliasesJson
import io.legado.app.domain.gateway.AiCharacterCardGateway
import io.legado.app.domain.gateway.AiChatGateway
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiPromptTemplateGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.AiToolConfigGateway
import io.legado.app.domain.gateway.AiWorkspaceGateway
import io.legado.app.domain.model.AiCallMeta
import io.legado.app.domain.model.AiCallSource
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.CharacterCardPatch
import io.legado.app.domain.usecase.ai.resolvedSubModelProfileId
import io.legado.app.utils.AiIdListCodec
import io.legado.app.utils.GSON

class CharacterCardMutator(
    private val gateway: AiCharacterCardGateway,
    private val aiChatGateway: AiChatGateway,
    private val aiProfileGateway: AiProfileGateway,
    private val aiTextGateway: AiTextGateway,
    private val promptTemplateGateway: AiPromptTemplateGateway,
    private val toolConfigGateway: AiToolConfigGateway,
    private val workspaceGateway: AiWorkspaceGateway,
) {
    data class PatchResult(
        val success: Boolean,
        val message: String,
        val cardId: String = "",
        val changes: List<FieldChange> = emptyList(),
        val data: Map<String, Any?> = emptyMap(),
    )

    suspend fun read(cardId: String?, includePerformanceFields: Boolean = true): String {
        if (cardId.isNullOrBlank()) {
            val cards = gateway.observeAll()
            // observeAll is Flow - for tool we need list; use getById pattern
            return """{"error":"cardId is required"}"""
        }
        val card = gateway.getById(cardId) ?: return """{"error":"Character card not found: $cardId"}"""
        val data = linkedMapOf<String, Any?>(
            "cardId" to card.id,
            "name" to card.name,
            "description" to card.description,
            "openingLine" to card.openingLine,
            "worldBookIds" to card.worldBookIds,
            "personality" to card.personality,
            "scenario" to card.scenario,
            "exampleDialogues" to card.exampleDialogues,
            "postHistoryInstructions" to card.postHistoryInstructions,
            "alternateOpenings" to card.alternateOpenings,
            "bookUrl" to card.bookUrl,
            "bookName" to card.bookName,
            "bookAuthor" to card.bookAuthor,
        )
        if (includePerformanceFields) {
            data["aliasesJson"] = card.aliasesJson
            data["voiceGender"] = card.voiceGender
            data["voiceAgeBand"] = card.voiceAgeBand
            // avatarPath is UI-only — never returned to AI tools
        }
        return GSON.toJson(data)
    }

    /**
     * Compact catalog of character cards for `list_character_cards`.
     * Filters are optional; omit bookUrl/bookName to list across the whole library.
     * Performance/cast fields (aliases, voice hints) are omitted in writing mode;
     * [AiCharacterCard.avatarPath] is UI-only and never returned.
     */
    suspend fun readAll(
        includePerformanceFields: Boolean = true,
        query: String? = null,
        bookUrl: String? = null,
        bookName: String? = null,
        bookAuthor: String? = null,
        limit: Int = 100,
    ): String {
        val q = query?.trim()?.takeIf { it.isNotBlank() }
        val cards = gateway.getAll()
            .asSequence()
            .filter { card ->
                (q == null ||
                    card.name.contains(q, ignoreCase = true) ||
                    card.description.contains(q, ignoreCase = true) ||
                    card.bookName.contains(q, ignoreCase = true)) &&
                    (bookUrl.isNullOrBlank() || card.bookUrl == bookUrl) &&
                    (bookName.isNullOrBlank() || card.bookName.equals(bookName, ignoreCase = true)) &&
                    (bookAuthor.isNullOrBlank() || card.bookAuthor.equals(bookAuthor, ignoreCase = true))
            }
            .sortedByDescending { it.updatedAt }
            .take(limit)
            .toList()
        val list = cards.map { card ->
            linkedMapOf<String, Any?>(
                "cardId" to card.id,
                "name" to card.name,
                "description" to card.description.replace('\n', ' ').take(160),
                "bookUrl" to card.bookUrl,
                "bookName" to card.bookName,
                "bookAuthor" to card.bookAuthor,
                "updatedAt" to card.updatedAt,
            ).also { m ->
                if (includePerformanceFields) {
                    m["aliases"] = parseAliasesJson(card.aliasesJson)
                    m["voiceGender"] = card.voiceGender
                    m["voiceAgeBand"] = card.voiceAgeBand
                }
            }
        }
        return GSON.toJson(mapOf("count" to list.size, "characterCards" to list))
    }

    suspend fun previewPatch(
        patch: CharacterCardPatch,
        includePerformanceFields: Boolean = true,
        canonical: Boolean = false,
    ): List<FieldChange> {
        if (patch.generate) {
            return listOf(FieldChange("ai_generate", "", "Character card will be generated by AI"))
        }
        val (targetPatch, _) = resolvePatchTarget(patch, canonical)
        val existing = targetPatch.cardId?.let { gateway.getById(it) }
        val oldFields = buildFieldSnapshot(existing, includePerformanceFields)
        val newFields = buildFieldSnapshotFromPatch(targetPatch, existing, includePerformanceFields)
        return StructuredDataDiff.diffFields(oldFields, newFields)
    }

    /**
     * 正典卡保护:衍生会话(canonical=false)对正典卡的 patch/generate 一律先 fork 成衍生卡
     * (forkedFromCardId=正典卡 id、canonical=0),再应用修改,绝不覆盖正典卡。
     * 返回 (应用到的 patch, 若发生 fork 则为其正典源卡 id)。
     */
    private suspend fun resolvePatchTarget(
        patch: CharacterCardPatch,
        canonical: Boolean,
    ): Pair<CharacterCardPatch, String?> {
        if (canonical) return patch to null
        val sourceId = patch.cardId?.takeIf { it.isNotBlank() } ?: return patch to null
        val source = gateway.getById(sourceId) ?: return patch to null
        if (!source.canonical) return patch to null
        return patch.copy(cardId = null) to source.id
    }

    /** 把新建/派生的卡加入会话与 workspace.characterCardIds(正典卡也在正典会话内可见)。 */
    private suspend fun addCardToSessionScope(conversationId: String?, cardId: String) {
        val convId = conversationId?.takeIf { it.isNotBlank() } ?: return
        val conv = aiChatGateway.getConversation(convId) ?: return
        val convIds = AiIdListCodec.parse(conv.characterCardIds).toMutableList()
        if (cardId !in convIds) {
            convIds.add(cardId)
            aiChatGateway.updateConversationCharacters(convId, AiIdListCodec.toJsonArray(convIds))
        }
        if (conv.characterCardId.isNullOrBlank()) {
            aiChatGateway.updateConversationCharacter(convId, cardId)
        }
        workspaceGateway.getByConversationId(convId)?.let { ws ->
            val wsIds = AiIdListCodec.parse(ws.characterCardIds).toMutableList()
            if (cardId !in wsIds) {
                wsIds.add(cardId)
                workspaceGateway.updateRefs(ws.id, characterCardIds = AiIdListCodec.toCsv(wsIds))
            }
        }
    }

    suspend fun applyPatch(
        patch: CharacterCardPatch,
        includePerformanceFields: Boolean = true,
        canonical: Boolean = false,
    ): PatchResult {
        // 正典卡保护:衍生会话先 fork。
        val sourceCard = patch.cardId?.let { gateway.getById(it) }
        val (targetPatch, forkedFromCardId) = if (!canonical && sourceCard?.canonical == true) {
            patch.copy(cardId = null) to sourceCard.id
        } else {
            patch to null
        }
        if (targetPatch.generate) {
            return generateFromConversation(
                targetPatch, includePerformanceFields, canonical, forkedFromCardId,
            )
        }
        val effectivePatch = if (includePerformanceFields) targetPatch else CharacterCardPerformancePolicy.stripPatch(targetPatch)
        val existing = effectivePatch.cardId?.let { gateway.getById(it) }
        // fork 时字段回退到正典源卡,否则回退到目标卡。
        val fallback = existing ?: sourceCard
        val changes = previewPatch(effectivePatch, includePerformanceFields, canonical)
        val card = gateway.save(
            name = effectivePatch.name ?: fallback?.name ?: return PatchResult(false, "name is required for new card"),
            description = effectivePatch.description ?: fallback?.description.orEmpty(),
            openingLine = effectivePatch.openingLine ?: fallback?.openingLine.orEmpty(),
            worldBookIds = effectivePatch.worldBookIds ?: fallback?.worldBookIds.orEmpty(),
            cardId = effectivePatch.cardId,
        )
        val enriched = gateway.upsert(
            card.copy(
                personality = effectivePatch.personality ?: fallback?.personality.orEmpty(),
                scenario = effectivePatch.scenario ?: fallback?.scenario.orEmpty(),
                exampleDialogues = effectivePatch.exampleDialogues ?: fallback?.exampleDialogues.orEmpty(),
                postHistoryInstructions = effectivePatch.postHistoryInstructions ?: fallback?.postHistoryInstructions.orEmpty(),
                alternateOpenings = effectivePatch.alternateOpenings ?: fallback?.alternateOpenings ?: "[]",
                aliasesJson = if (includePerformanceFields) {
                    effectivePatch.aliasesJson ?: fallback?.aliasesJson ?: "[]"
                } else {
                    fallback?.aliasesJson ?: "[]"
                },
                voiceGender = if (includePerformanceFields) {
                    effectivePatch.voiceGender ?: fallback?.voiceGender
                        ?: io.legado.app.data.entities.AiCharacterCard.VOICE_GENDER_UNKNOWN
                } else {
                    fallback?.voiceGender ?: io.legado.app.data.entities.AiCharacterCard.VOICE_GENDER_UNKNOWN
                },
                voiceAgeBand = if (includePerformanceFields) {
                    effectivePatch.voiceAgeBand ?: fallback?.voiceAgeBand
                        ?: io.legado.app.data.entities.AiCharacterCard.VOICE_AGE_UNKNOWN
                } else {
                    fallback?.voiceAgeBand ?: io.legado.app.data.entities.AiCharacterCard.VOICE_AGE_UNKNOWN
                },
                avatarPath = if (includePerformanceFields) {
                    effectivePatch.avatarPath ?: fallback?.avatarPath.orEmpty()
                } else {
                    fallback?.avatarPath.orEmpty()
                },
                bookUrl = effectivePatch.bookUrl ?: fallback?.bookUrl.orEmpty(),
                bookName = effectivePatch.bookName ?: fallback?.bookName.orEmpty(),
                bookAuthor = effectivePatch.bookAuthor ?: fallback?.bookAuthor.orEmpty(),
                canonical = canonical,
                forkedFromCardId = forkedFromCardId ?: fallback?.forkedFromCardId.orEmpty(),
            )
        )
        // fork 出的新卡加入会话作用域;正典会话新建卡同理。
        if (forkedFromCardId != null || effectivePatch.cardId == null) {
            addCardToSessionScope(effectivePatch.conversationId, enriched.id)
        }
        return PatchResult(
            success = true,
            message = when {
                forkedFromCardId != null -> "Character card created (forked from canonical card)"
                existing != null -> "Character card updated"
                else -> "Character card created"
            },
            cardId = enriched.id,
            changes = changes,
            data = buildResultData(enriched, includePerformanceFields),
        )
    }

    /** Re-merge card-derived world books into workspace when a referenced card changes bindings. */
    suspend fun syncWorkspaceWorldBooks(cardId: String, conversationId: String?) {
        val convId = conversationId?.takeIf { it.isNotBlank() } ?: return
        val workspace = workspaceGateway.getByConversationId(convId) ?: return
        val cardIds = AiIdListCodec.parse(workspace.characterCardIds)
        if (cardId !in cardIds) return
        val cardDerived = cardIds.flatMap { id ->
            AiIdListCodec.parse(gateway.getById(id)?.worldBookIds)
        }
        val merged = (AiIdListCodec.parse(workspace.worldBookIds) + cardDerived).distinct()
        workspaceGateway.updateRefs(
            workspaceId = workspace.id,
            worldBookIds = AiIdListCodec.toCsv(merged),
        )
    }

    private suspend fun generateFromConversation(
        patch: CharacterCardPatch,
        includePerformanceFields: Boolean = true,
        canonical: Boolean = false,
        forkedFromCardId: String? = null,
    ): PatchResult {
        val messagesText = patch.conversationId?.let { convId ->
            aiChatGateway.getMessagesForRegeneration(convId).joinToString("\n\n") { msg ->
                "[${if (msg.first == "user") "用户" else "助手"}] ${msg.second.take(2000)}"
            }
        }.orEmpty()
        val existingCardHint = patch.cardId?.let { id ->
            gateway.getById(id)?.let { existing ->
                buildString {
                    append("\n\n## 当前角色卡信息（请在此基础上更新）")
                    append("\n角色名：${existing.name}")
                    append("\n描述：${existing.description}")
                    append("\n开场白：${existing.openingLine}")
                    if (existing.personality.isNotBlank()) append("\n性格：${existing.personality}")
                    if (existing.scenario.isNotBlank()) append("\n场景：${existing.scenario}")
                    if (existing.exampleDialogues.isNotBlank()) append("\n示例对话：${existing.exampleDialogues}")
                    if (existing.postHistoryInstructions.isNotBlank()) {
                        append("\n历史后指令：${existing.postHistoryInstructions}")
                    }
                    val aliases = parseAliasesJson(existing.aliasesJson)
                    if (includePerformanceFields && aliases.isNotEmpty()) {
                        append("\n别名：${aliases.joinToString("、")}")
                    }
                    if (includePerformanceFields &&
                        existing.voiceGender != AiCharacterCard.VOICE_GENDER_UNKNOWN
                    ) {
                        append("\n配音性别：${existing.voiceGender}")
                    }
                    if (includePerformanceFields &&
                        existing.voiceAgeBand != AiCharacterCard.VOICE_AGE_UNKNOWN
                    ) {
                        append("\n配音年龄段：${existing.voiceAgeBand}")
                    }
                }
            }
        }.orEmpty()
        val prompt = promptTemplateGateway.getPrompt(AiPromptTemplate.GENERATE_CHARACTER_CARD_PROMPT)
        val system = io.legado.app.domain.usecase.ai.PromptRoleSplit.stripPlaceholders(prompt)
        val user = buildString {
            if (messagesText.isNotBlank()) append(messagesText)
            if (existingCardHint.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append(existingCardHint.trimStart())
            }
            if (!patch.hint.isNullOrBlank()) {
                if (isNotEmpty()) append("\n\n")
                append("## Extra Guidance\n").append(patch.hint)
            }
        }.ifBlank { "（无对话记录）" }

        val config = toolConfigGateway.getByToolName("patch_character_card")
        val result = generateWithConfig(system, user, config)
        return result.fold(
            onSuccess = { response ->
                val jsonStr = extractJsonObject(response.text)
                runCatching {
                    val obj = GSON.fromJson(jsonStr, JsonObject::class.java)
                    val name = obj.get("name")?.asString?.takeIf { it.isNotBlank() }
                        ?: return@runCatching PatchResult(false, "AI response missing 'name' field")
                    val description = obj.get("description")?.asString.orEmpty()
                    val openingLine = obj.get("openingLine")?.asString.orEmpty()
                    val personality = obj.get("personality")?.asString.orEmpty()
                    val scenario = obj.get("scenario")?.asString.orEmpty()
                    val exampleDialogues = obj.get("exampleDialogues")?.asString.orEmpty()
                    val postHistoryInstructions = obj.get("postHistoryInstructions")?.asString.orEmpty()
                    val alternateOpenings = obj.get("alternateOpenings")?.let { el ->
                        if (el.isJsonArray) GSON.toJson(el.asJsonArray) else el.asString
                    } ?: "[]"
                    val existing = patch.cardId?.let { gateway.getById(it) }
                    // fork 时字段回退到正典源卡,否则回退到目标卡。
                    val fallback = existing ?: forkedFromCardId?.let { gateway.getById(it) }
                    val aliasesJson = if (includePerformanceFields) {
                        parseAliasesFromAiResponse(obj)
                    } else {
                        fallback?.aliasesJson ?: "[]"
                    }
                    val voiceGender = if (includePerformanceFields) {
                        obj.get("voiceGender")?.asString?.takeIf { it.isNotBlank() }
                            ?: AiCharacterCard.VOICE_GENDER_UNKNOWN
                    } else {
                        fallback?.voiceGender ?: AiCharacterCard.VOICE_GENDER_UNKNOWN
                    }
                    val voiceAgeBand = if (includePerformanceFields) {
                        obj.get("voiceAgeBand")?.asString?.takeIf { it.isNotBlank() }
                            ?: AiCharacterCard.VOICE_AGE_UNKNOWN
                    } else {
                        fallback?.voiceAgeBand ?: AiCharacterCard.VOICE_AGE_UNKNOWN
                    }
                    val card = gateway.save(name, description, openingLine, "", patch.cardId)
                    val enriched = gateway.upsert(
                        card.copy(
                            personality = personality,
                            scenario = scenario,
                            exampleDialogues = exampleDialogues,
                            postHistoryInstructions = postHistoryInstructions,
                            alternateOpenings = alternateOpenings,
                            aliasesJson = aliasesJson,
                            voiceGender = voiceGender,
                            voiceAgeBand = voiceAgeBand,
                            avatarPath = fallback?.avatarPath.orEmpty(),
                            bookUrl = patch.bookUrl?.takeIf { it.isNotBlank() }
                                ?: fallback?.bookUrl.orEmpty(),
                            bookName = patch.bookName?.takeIf { it.isNotBlank() }
                                ?: fallback?.bookName.orEmpty(),
                            bookAuthor = patch.bookAuthor?.takeIf { it.isNotBlank() }
                                ?: fallback?.bookAuthor.orEmpty(),
                            canonical = canonical,
                            forkedFromCardId = forkedFromCardId ?: fallback?.forkedFromCardId.orEmpty(),
                        )
                    )
                    if (forkedFromCardId != null || patch.cardId == null) {
                        addCardToSessionScope(patch.conversationId, enriched.id)
                    }
                    PatchResult(
                        success = true,
                        message = "Character card generated",
                        cardId = enriched.id,
                        changes = listOf(FieldChange("name", "", name)),
                        data = buildResultData(enriched, includePerformanceFields),
                    )
                }.getOrElse { PatchResult(false, "Failed to parse AI response: ${it.message}") }
            },
            onFailure = { PatchResult(false, "AI generation failed: ${it.message}") }
        )
    }

    private fun buildFieldSnapshot(
        existing: AiCharacterCard?,
        includePerformanceFields: Boolean,
    ): Map<String, String> = buildMap {
        put("name", existing?.name.orEmpty())
        put("description", existing?.description.orEmpty())
        put("openingLine", existing?.openingLine.orEmpty())
        put("worldBookIds", existing?.worldBookIds.orEmpty())
        put("personality", existing?.personality.orEmpty())
        put("scenario", existing?.scenario.orEmpty())
        put("exampleDialogues", existing?.exampleDialogues.orEmpty())
        put("postHistoryInstructions", existing?.postHistoryInstructions.orEmpty())
        put("alternateOpenings", existing?.alternateOpenings.orEmpty())
        put("bookUrl", existing?.bookUrl.orEmpty())
        put("bookName", existing?.bookName.orEmpty())
        put("bookAuthor", existing?.bookAuthor.orEmpty())
        if (includePerformanceFields) {
            put("aliasesJson", existing?.aliasesJson.orEmpty())
            put("voiceGender", existing?.voiceGender.orEmpty())
            put("voiceAgeBand", existing?.voiceAgeBand.orEmpty())
        }
    }

    private fun buildFieldSnapshotFromPatch(
        patch: CharacterCardPatch,
        existing: AiCharacterCard?,
        includePerformanceFields: Boolean,
    ): Map<String, String> {
        val oldFields = buildFieldSnapshot(existing, includePerformanceFields)
        return buildMap {
            put("name", patch.name ?: oldFields.getValue("name"))
            put("description", patch.description ?: oldFields.getValue("description"))
            put("openingLine", patch.openingLine ?: oldFields.getValue("openingLine"))
            put("worldBookIds", patch.worldBookIds ?: oldFields.getValue("worldBookIds"))
            put("personality", patch.personality ?: oldFields.getValue("personality"))
            put("scenario", patch.scenario ?: oldFields.getValue("scenario"))
            put("exampleDialogues", patch.exampleDialogues ?: oldFields.getValue("exampleDialogues"))
            put("postHistoryInstructions", patch.postHistoryInstructions ?: oldFields.getValue("postHistoryInstructions"))
            put("alternateOpenings", patch.alternateOpenings ?: oldFields.getValue("alternateOpenings"))
            put("bookUrl", patch.bookUrl ?: oldFields.getValue("bookUrl"))
            put("bookName", patch.bookName ?: oldFields.getValue("bookName"))
            put("bookAuthor", patch.bookAuthor ?: oldFields.getValue("bookAuthor"))
            if (includePerformanceFields) {
                put("aliasesJson", patch.aliasesJson ?: oldFields.getValue("aliasesJson"))
                put("voiceGender", patch.voiceGender ?: oldFields.getValue("voiceGender"))
                put("voiceAgeBand", patch.voiceAgeBand ?: oldFields.getValue("voiceAgeBand"))
            }
        }
    }

    private fun buildResultData(
        enriched: AiCharacterCard,
        includePerformanceFields: Boolean,
    ): Map<String, Any?> {
        val data = linkedMapOf<String, Any?>(
            "cardId" to enriched.id,
            "name" to enriched.name,
            "description" to enriched.description,
            "openingLine" to enriched.openingLine,
            "worldBookIds" to enriched.worldBookIds,
            "personality" to enriched.personality,
            "scenario" to enriched.scenario,
            "exampleDialogues" to enriched.exampleDialogues,
            "postHistoryInstructions" to enriched.postHistoryInstructions,
            "alternateOpenings" to enriched.alternateOpenings,
            "bookUrl" to enriched.bookUrl,
            "bookName" to enriched.bookName,
            "bookAuthor" to enriched.bookAuthor,
        )
        if (includePerformanceFields) {
            data["aliasesJson"] = enriched.aliasesJson
            data["voiceGender"] = enriched.voiceGender
            data["voiceAgeBand"] = enriched.voiceAgeBand
            // avatarPath is UI-only — never returned to AI tools
        }
        return data
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
                    callMeta = AiCallMeta(AiCallSource.CHARACTER),
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
                    callMeta = AiCallMeta(AiCallSource.CHARACTER),
                )
            )
        }
    }

    private fun parseAliasesFromAiResponse(obj: JsonObject): String {
        obj.get("aliasesJson")?.takeIf { !it.isJsonNull }?.let { el ->
            return if (el.isJsonArray) GSON.toJson(el.asJsonArray) else el.asString
        }
        obj.get("aliases")?.takeIf { !it.isJsonNull }?.let { el ->
            return when {
                el.isJsonArray -> GSON.toJson(el.asJsonArray)
                el.isJsonPrimitive -> {
                    val raw = el.asString.trim()
                    when {
                        raw.isBlank() -> "[]"
                        raw.startsWith("[") -> raw
                        else -> GSON.toJson(
                            raw.split(',', '，', ';', '；', '\n')
                                .map { it.trim() }
                                .filter { it.isNotBlank() },
                        )
                    }
                }
                else -> "[]"
            }
        }
        return "[]"
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

    private fun io.legado.app.data.entities.AiModelProfile.toModelConfig(provider: io.legado.app.data.entities.AiProviderProfile) =
        AiModelConfig(
            id = id,
            provider = AiProviderConfig(
                id = provider.id, name = provider.name, protocol = provider.protocol,
                baseUrl = provider.baseUrl, apiKey = provider.apiKey, modelsUrl = provider.modelsUrl,
                chatPath = provider.chatPath ?: "/chat/completions",
                responsesPath = provider.responsesPath ?: "/responses",
                messagesPath = provider.messagesPath ?: "/v1/messages",
                modelsPath = provider.modelsPath,
                headers = emptyMap(), customHeaders = emptyMap(),
            ),
            displayName = displayName, modelId = modelId,
            contextWindow = contextWindow, maxOutputTokens = maxOutputTokens,
        )
}
