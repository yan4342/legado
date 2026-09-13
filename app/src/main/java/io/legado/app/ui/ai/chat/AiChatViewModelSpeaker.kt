package io.legado.app.ui.ai.chat

import android.util.Log
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.data.entities.AiPromptTemplate
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.CharacterCardPatch
import io.legado.app.domain.model.PersonaMacro
import io.legado.app.domain.model.PersonaMacroContext
import io.legado.app.domain.model.SillyTavernCharacterCardImporter
import io.legado.app.domain.model.WritingUserInput
import io.legado.app.domain.usecase.ToolTraceBuilder
import io.legado.app.help.ai.CharacterCardPerformancePolicy
import io.legado.app.utils.AiIdListCodec
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import splitties.init.appCtx

// ---- Regex patterns for parsing speaker prefixes from model output ----

internal val LEADING_BRACKET_SPEAKER = Regex("""^\[([^\]\n]{1,40})\]\s*[:：]\s*""")
internal val LEADING_PLAIN_SPEAKER = Regex("""^([^：:\n\[\]]{1,20})[:：]\s*""")

// =====================================================================
// Speaker helpers
// =====================================================================

/** Match the start of the assistant response against known character card names.
 *  Expects format: [角色名]: or 角色名： */
internal fun AiChatViewModel.resolveSpeakerCardId(text: String): String? {
    val lead = extractLeadingSpeaker(text)
    if (lead != null) {
        findCardIdByName(lead.first)?.let { return it }
    }
    val cards = speakerLookupCards()
    if (cards.isEmpty()) return null
    val trimmed = text.trimStart()
    for (card in cards) {
        val prefixes = listOf("[${card.name}]:", "${card.name}：", "${card.name}:", "[${card.name}]：")
        for (prefix in prefixes) {
            if (trimmed.startsWith(prefix)) return card.id
        }
    }
    // Fallback: if only one selected card, attribute to it
    val selected = selectedCharacterCards()
    if (selected.size == 1) return selected.first().id
    return null
}

internal fun AiChatViewModel.selectedCharacterCards(): List<AiCharacterCardUi> =
    _uiState.value.selectedCharacterCards.ifEmpty {
        _uiState.value.selectedCharacterCard?.let { listOf(it) }.orEmpty()
    }

/** Writing mode keeps workspace.characterCardIds in sync with session selection. */
internal fun AiChatViewModel.speakerLookupCards(): List<AiCharacterCardUi> = selectedCharacterCards()

internal fun AiChatViewModel.findCardIdByName(name: String): String? {
    val n = name.trim()
    if (n.isBlank()) return null
    val cards = speakerLookupCards()
    return cards.firstOrNull { it.name == n }?.id
        ?: cards.firstOrNull { it.name.equals(n, ignoreCase = true) }?.id
}

/**
 * Parse leading `[角色名]:` / `角色名：` from model output.
 * @return name + remainder without the prefix
 */
internal fun AiChatViewModel.extractLeadingSpeaker(text: String): Pair<String, String>? {
    val trimmed = text.trimStart()
    if (trimmed.isEmpty()) return null
    // Error messages (saved as "Error: HTTP ...") are not speaker-prefixed content.
    // Treating "Error:" or the following "HTTP xxx:" as a speaker prefix hides the
    // HTTP status code — keep such text intact so it remains visible.
    if (trimmed.startsWith("Error:", ignoreCase = true) || trimmed.startsWith("Error：")) return null
    val bracket = LEADING_BRACKET_SPEAKER.find(trimmed)
    if (bracket != null) {
        val name = bracket.groupValues[1].trim()
        if (name.isNotBlank()) {
            return name to trimmed.removePrefix(bracket.value).trimStart()
        }
    }
    val plain = LEADING_PLAIN_SPEAKER.find(trimmed)
    if (plain != null) {
        val name = plain.groupValues[1].trim()
        // Avoid treating narration like "门外：" as speaker if too short/generic — keep as-is
        if (name.isNotBlank() && name.length <= 20) {
            return name to trimmed.removePrefix(plain.value).trimStart()
        }
    }
    return null
}

/** Strip leading `[角色名]:` for bubble display (UI label shows the name). */
internal fun AiChatViewModel.cleanSpeakerPrefix(text: String, speakerName: String): String {
    // Always strip a leading [角色名]: / 角色名： so it never duplicates the speaker label.
    extractLeadingSpeaker(text)?.let { return it.second }
    if (speakerName.isBlank()) return text
    val prefixes = listOf(
        "[${speakerName}]:",
        "[${speakerName}]：",
        "${speakerName}：",
        "${speakerName}:",
    )
    val trimmed = text.trimStart()
    for (prefix in prefixes) {
        if (trimmed.startsWith(prefix)) return trimmed.removePrefix(prefix).trimStart()
    }
    return text
}

internal fun AiChatViewModel.stripLeadingSpeakerPrefix(text: String): String =
    extractLeadingSpeaker(text)?.second ?: text

/** Writing UI renders [parts], not [content] — keep Text parts in sync with cleaned body. */
internal fun AiChatViewModel.partsWithStrippedSpeaker(
    parts: List<AiMessagePart>,
    speakerName: String,
): List<AiMessagePart> {
    var strippedLead = false
    return parts.map { part ->
        if (part is AiMessagePart.Text && part.text.isNotBlank() && !strippedLead) {
            val cleaned = cleanSpeakerPrefix(part.text, speakerName)
            if (cleaned != part.text) strippedLead = true
            part.copy(text = cleaned)
        } else {
            part
        }
    }
}

/**
 * Resolve speaker from raw model text, then strip leading `[名]:` for storage/display.
 * [historyForModel] re-adds the prefix when sending context to the model.
 */
internal fun AiChatViewModel.finalizeAssistantBody(
    text: String,
    preferredSpeakerId: String? = null,
): Pair<String, String?> {
    val speakerId = preferredSpeakerId ?: resolveSpeakerCardId(text)
    return stripLeadingSpeakerPrefix(text) to speakerId
}

/**
 * Build persistable parts from the raw streamed [rawText] (ContentSpan offsets must match),
 * then strip the leading speaker prefix from Text parts — same order as [syncStreamingMessage].
 * Never rebuild spans against already-stripped text (that duplicates / mis-slices body).
 */
internal fun AiChatViewModel.assembleAssistantPartsForPersist(
    rawText: String,
    reasoning: String,
    toolTrace: ToolTraceBuilder,
    wasCancelled: Boolean,
    speakerName: String,
): List<AiMessagePart> {
    val parts = buildFinalAssistantParts(rawText, reasoning, toolTrace, wasCancelled)
    return partsWithStrippedSpeaker(parts, speakerName)
}

internal fun AiChatViewModel.speakerNameForPersist(rawText: String, speakerId: String?): String {
    val fromStream = _uiState.value.streamingMessage?.speakerName.orEmpty()
    if (fromStream.isNotBlank()) return fromStream
    val fromCard = speakerId?.let { sid ->
        speakerLookupCards().find { it.id == sid }?.name
    }.orEmpty()
    if (fromCard.isNotBlank()) return fromCard
    return extractLeadingSpeaker(rawText)?.first.orEmpty()
}

/** Resolve speaker id/name and cleaned body for UI from DB fields + raw text. */
internal suspend fun AiChatViewModel.speakerUiFields(
    speakerCardId: String?,
    rawText: String,
): Triple<String?, String, String> {
    val lead = extractLeadingSpeaker(rawText)
    val resolvedId = speakerCardId
        ?: lead?.first?.let { findCardIdByName(it) }
        ?: resolveSpeakerCardId(rawText)
    val nameFromCard = resolvedId?.let { sid ->
        aiCharacterCardGateway.getById(sid)?.name
            ?: speakerLookupCards().find { it.id == sid }?.name
    }.orEmpty()
    val speakerName = nameFromCard.ifBlank { lead?.first.orEmpty() }
    val content = cleanSpeakerPrefix(rawText, speakerName)
    return Triple(resolvedId, speakerName, content)
}

/** Find @角色名 mentions in text order (longest name match first at each @). */
internal fun AiChatViewModel.findMentionedSpeakers(content: String): List<String> {
    val cards = selectedCharacterCards()
    if (cards.isEmpty()) return emptyList()
    val sorted = cards.sortedByDescending { it.name.length }
    val found = mutableListOf<String>()
    val foundIds = mutableSetOf<String>()
    var searchFrom = 0
    while (searchFrom < content.length) {
        val at = content.indexOf('@', searchFrom)
        if (at < 0) break
        val afterAt = content.substring(at + 1)
        val matched = sorted.firstOrNull { card ->
            card.name.isNotBlank() && afterAt.startsWith(card.name) && card.id !in foundIds
        }
        if (matched != null) {
            found.add(matched.id)
            foundIds.add(matched.id)
            searchFrom = at + 1 + matched.name.length
        } else {
            searchFrom = at + 1
        }
    }
    return found
}

// =====================================================================
// Card CRUD
// =====================================================================

internal fun AiChatViewModel.createConversationWithCharacter(characterCardId: String) {
    viewModelScope.launch {
        val card = aiCharacterCardGateway.getById(characterCardId) ?: return@launch
        runCatching {
            aiChatGateway.createConversation(title = card.name)
        }.onSuccess { conv ->
            aiChatGateway.updateConversationType(conv.id, "writing")
            aiChatGateway.updateConversationCharacter(conv.id, characterCardId)
            aiChatGateway.updateConversationCharacters(
                conv.id,
                AiIdListCodec.toJsonArray(listOf(characterCardId)),
            )
            aiWorkspaceGateway.ensureForConversation(
                conversationId = conv.id,
                name = card.name,
                characterCardIds = characterCardId,
                worldBookIds = AiIdListCodec.toCsv(card.worldBookIds),
                writingPromptIds = AiIdListCodec.toCsv(
                    _uiState.value.writingPrompts.filter { it.enabled }.map { it.id },
                ),
            )
            if (card.openingLine.isNotBlank()) {
                aiChatGateway.saveMessage(
                    conversationId = conv.id,
                    role = AiMessageRole.ASSISTANT,
                    parts = listOf(AiMessagePart.Text(card.openingLine)),
                    parentMessageId = null,
                    speakerCardId = characterCardId,
                )
            }
            selectConversation(conv.id)
        }.onFailure {
            _effects.tryEmit(AiChatEffect.ShowMessage(it.message ?: "Failed"))
        }
    }
}

internal fun AiChatViewModel.updateConversationCharacter(characterCardId: String?) {
    val cid = currentConversationId.value ?: return
    viewModelScope.launch {
        aiChatGateway.updateConversationCharacter(cid, characterCardId)
        val ids = listOfNotNull(characterCardId)
        aiChatGateway.updateConversationCharacters(cid, AiIdListCodec.toJsonArray(ids))
        val card = characterCardId?.let { aiCharacterCardGateway.getById(it) }
        val count = characterCardId?.let { aiChatGateway.countConversationsByCharacter(it) } ?: 0
        _uiState.update { current ->
            current.copy(
                selectedCharacterCard = card?.toUi(count),
                selectedCharacterCards = listOfNotNull(card?.toUi()).toImmutableList(),
            )
        }
        // Update title if it's still default
        card?.let {
            val conv = aiChatGateway.getConversation(cid)
            if (conv != null && (conv.title == "New Chat" || conv.title.isBlank())) {
                aiChatGateway.updateConversationTitle(cid, it.name)
            }
        }
        if (_uiState.value.conversationType == "writing") {
            syncWorkspaceCardRefs(cid, ids.toSet())
        }
    }
}

internal fun AiChatViewModel.updateConversationCharacters(cardIds: Set<String>) {
    val cid = currentConversationId.value ?: return
    val idsJson = AiIdListCodec.toJsonArray(cardIds)
    viewModelScope.launch {
        aiChatGateway.updateConversationCharacters(cid, idsJson)
        // Backward compat: also set characterCardId for single-character mode
        if (cardIds.size == 1) {
            aiChatGateway.updateConversationCharacter(cid, cardIds.first())
        } else {
            aiChatGateway.updateConversationCharacter(cid, null)
        }
        val cards = cardIds.mapNotNull { id ->
            aiCharacterCardGateway.getById(id)?.toUi()
        }
        val singleCard = cards.singleOrNull()?.let { card ->
            val count = aiChatGateway.countConversationsByCharacter(card.id)
            card.copy(conversationCount = count)
        }
        // Save opening line for new characters if conversation is empty
        val existingMsgs = aiChatGateway.getContextMessages(cid, 1)
        if (existingMsgs.isEmpty()) {
            cards.forEach { card ->
                if (card.openingLine.isNotBlank()) {
                    aiChatGateway.saveMessage(
                        conversationId = cid,
                        role = AiMessageRole.ASSISTANT,
                        parts = listOf(AiMessagePart.Text(card.openingLine)),
                        speakerCardId = card.id,
                    )
                }
            }
        }
        _uiState.update { current ->
            current.copy(
                selectedCharacterCard = singleCard,
                selectedCharacterCards = cards.toImmutableList(),
            )
        }
        syncWorkspaceCardRefs(cid, cardIds)
    }
}

internal fun AiChatViewModel.updateActionPrompt(category: String, content: String) {
    _uiState.update { current ->
        when (category) {
            "action_continue" -> current.copy(continueActionPrompt = content)
            else -> current
        }
    }
}

internal fun AiChatViewModel.saveCharacterCard(
    name: String,
    description: String,
    openingLine: String,
    worldBookIds: String,
    cardId: String?,
    personality: String = "",
    scenario: String = "",
    exampleDialogues: String = "",
    postHistoryInstructions: String = "",
    alternateOpenings: String = "[]",
    aliasesJson: String = "[]",
    voiceGender: String = "unknown",
    voiceAgeBand: String = "unknown",
    bookUrl: String = "",
    bookName: String = "",
    bookAuthor: String = "",
    dramaticRole: String = "",
    avatarPath: String = "",
) {
    viewModelScope.launch {
        val includePerformance = _uiState.value.conversationType != "writing"
        runCatching {
            val patch = CharacterCardPatch(
                cardId = cardId,
                name = name,
                description = description,
                openingLine = openingLine,
                worldBookIds = AiIdListCodec.toCsv(worldBookIds),
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
            )
            characterCardMutator.applyPatch(
                if (includePerformance) patch else CharacterCardPerformancePolicy.stripPatch(patch),
                includePerformanceFields = includePerformance,
            )
        }.onSuccess { result ->
            if (!result.success) {
                _effects.tryEmit(AiChatEffect.ShowMessage(result.message))
                return@onSuccess
            }
            val savedId = result.cardId.ifBlank { cardId.orEmpty() }
            if (includePerformance && savedId.isNotBlank() && bookUrl.isNotBlank()) {
                readAloudCharacterGateway.updateDramaticRole(bookUrl, savedId, dramaticRole)
            }
            // Card ↔ first opening message: sync when this card is bound to the session
            // (single selectedCharacterCard OR multi selectedCharacterCards).
            if (savedId.isNotBlank() && isCardBoundToCurrentConversation(savedId)) {
                syncOpeningMessage(savedId, openingLine)
            }
            // Refresh bound selection immediately (Flow may lag a frame).
            if (savedId.isNotBlank()) {
                val saved = aiCharacterCardGateway.getById(savedId)?.toUi(
                    _uiState.value.selectedCharacterCard?.conversationCount
                        ?: _uiState.value.selectedCharacterCards.find { it.id == savedId }?.conversationCount
                        ?: 0,
                )
                if (saved != null) {
                    _uiState.update { current ->
                        val nextSelected = current.selectedCharacterCards.map {
                            if (it.id == savedId) saved else it
                        }
                        current.copy(
                            selectedCharacterCards = nextSelected.toImmutableList(),
                            selectedCharacterCard = when {
                                current.selectedCharacterCard?.id == savedId -> saved
                                else -> current.selectedCharacterCard
                            },
                        )
                    }
                }
            }
            // Card→world-book binding seeds workspace; merge when this card is in use.
            val cid = currentConversationId.value
            val selectedIds = _uiState.value.selectedCharacterCards.map { it.id }
                .ifEmpty { listOfNotNull(_uiState.value.selectedCharacterCard?.id) }
                .toSet()
            if (cid != null && savedId.isNotBlank() && savedId in selectedIds) {
                runCatching { syncWorkspaceCardRefs(cid, selectedIds) }
                    .onFailure {
                        Log.w("AiChat", "syncWorkspaceCardRefs after save failed: ${it.message}")
                    }
            }
        }.onFailure {
            _effects.tryEmit(AiChatEffect.ShowMessage(it.message ?: "Failed"))
        }
    }
}

internal fun AiChatViewModel.saveUserCard(name: String, description: String, enabled: Boolean) {
    val cid = currentConversationId.value ?: return
    val trimmedName = name.trim()
    val trimmedDescription = description.trim()
    _uiState.update {
        it.copy(
            userName = trimmedName,
            userDescription = trimmedDescription,
            userCardEnabled = enabled,
        )
    }
    viewModelScope.launch {
        aiChatGateway.updateConversationUserCard(cid, trimmedName, trimmedDescription, enabled)
    }
}

internal fun AiChatViewModel.toggleUserCardEnabled() {
    val cid = currentConversationId.value ?: return
    val enabled = !_uiState.value.userCardEnabled
    _uiState.update { it.copy(userCardEnabled = enabled) }
    viewModelScope.launch {
        aiChatGateway.updateConversationUserCardEnabled(cid, enabled)
    }
}

internal suspend fun AiChatViewModel.refreshUserCardState(conversationId: String?) {
    val cid = conversationId ?: return
    val conv = aiChatGateway.getConversation(cid) ?: return
    _uiState.update {
        it.copy(
            userName = conv.userName,
            userDescription = conv.userDescription,
            userCardEnabled = conv.userCardEnabled,
        )
    }
}

internal fun AiChatViewModel.boundCharacterCardIds(): Set<String> {
    val state = _uiState.value
    return state.selectedCharacterCards.map { it.id }
        .ifEmpty { listOfNotNull(state.selectedCharacterCard?.id) }
        .toSet()
}

internal fun AiChatViewModel.isCardBoundToCurrentConversation(cardId: String): Boolean =
    cardId in boundCharacterCardIds()

/**
 * Root assistant bubbles seeded from character openings (no parent user turn).
 * Multi-character sessions keep one per [speakerCardId].
 */
internal fun AiChatViewModel.findOpeningMessageForCard(cardId: String): AiChatMessageUi? {
    val openings = allLoadedMessages.filter {
        it.role == AiMessageRole.ASSISTANT && it.parentMessageId == null
    }
    openings.firstOrNull { it.speakerCardId == cardId }?.let { return it }
    // Legacy single-card openings may omit speakerCardId.
    val bound = boundCharacterCardIds()
    if (bound.size == 1 && bound.single() == cardId) {
        return openings.singleOrNull { it.speakerCardId.isNullOrBlank() }
            ?: openings.singleOrNull()
    }
    return null
}

/** Card openingLine → conversation first message (matched by speaker when multi-char). */
internal suspend fun AiChatViewModel.syncOpeningMessage(cardId: String, openingLine: String) {
    val cid = currentConversationId.value ?: return
    val openingMsg = findOpeningMessageForCard(cardId)
    if (openingMsg != null) {
        val parts = listOf(AiMessagePart.Text(openingLine.ifBlank { " " }))
        aiChatGateway.updateMessageParts(openingMsg.id, parts)
    } else if (openingLine.isNotBlank()) {
        aiChatGateway.saveMessage(
            conversationId = cid,
            role = AiMessageRole.ASSISTANT,
            parts = listOf(AiMessagePart.Text(openingLine)),
            parentMessageId = null,
            speakerCardId = cardId,
        )
    }
}

/** Edited first message → card openingLine (and in-memory selection). */
internal suspend fun AiChatViewModel.syncOpeningLineFromEditedMessage(messageId: String, newContent: String) {
    val msg = allLoadedMessages.find { it.id == messageId }
        ?: aiChatGateway.getMessage(messageId)?.let { entity ->
            // Minimal shape for opening detection when UI list is stale.
            AiChatMessageUi(
                id = entity.id,
                role = entity.role,
                createdAt = entity.createdAt,
                parentMessageId = entity.parentMessageId,
                speakerCardId = entity.speakerCardId,
            )
        }
        ?: return
    if (msg.role != AiMessageRole.ASSISTANT || msg.parentMessageId != null) return
    val cardId = msg.speakerCardId
        ?: boundCharacterCardIds().singleOrNull()
        ?: return
    if (!isCardBoundToCurrentConversation(cardId)) return
    val existing = aiCharacterCardGateway.getById(cardId) ?: return
    if (existing.openingLine == newContent) return
    aiCharacterCardGateway.upsert(existing.copy(openingLine = newContent))
    _uiState.update { current ->
        fun AiCharacterCardUi.withOpening() = copy(openingLine = newContent)
        current.copy(
            selectedCharacterCards = current.selectedCharacterCards.map {
                if (it.id == cardId) it.withOpening() else it
            }.toImmutableList(),
            selectedCharacterCard = current.selectedCharacterCard
                ?.takeIf { it.id == cardId }?.withOpening()
                ?: current.selectedCharacterCard,
        )
    }
}

internal fun AiChatViewModel.deleteCharacterCard(cardId: String) {
    viewModelScope.launch {
        // If currently selected character is being deleted, clear it
        if (_uiState.value.selectedCharacterCard?.id == cardId) {
            val cid = currentConversationId.value
            if (cid != null) {
                aiChatGateway.updateConversationCharacter(cid, null)
            }
            _uiState.update { it.copy(selectedCharacterCard = null) }
        }
        aiCharacterCardGateway.delete(cardId)
    }
}

internal fun AiChatViewModel.importCharacterCardJson(json: String) {
    viewModelScope.launch {
        runCatching {
            importCharacterParsed(SillyTavernCharacterCardImporter.parseFull(json))
        }.onSuccess { msg ->
            _effects.tryEmit(AiChatEffect.ShowMessage(msg))
        }.onFailure {
            _effects.tryEmit(AiChatEffect.ShowMessage(it.message ?: appCtx.getString(R.string.ai_character_import_failed)))
        }
    }
}

internal fun AiChatViewModel.importCharacterCardBytes(bytes: ByteArray) {
    viewModelScope.launch {
        Log.i("StCardImport", "importCharacterCardBytes size=${bytes.size}")
        runCatching {
            importCharacterParsed(SillyTavernCharacterCardImporter.parseBytes(bytes))
        }.onSuccess { msg ->
            Log.i("StCardImport", "import ok: $msg")
            _effects.tryEmit(AiChatEffect.ShowMessage(msg))
        }.onFailure {
            Log.e("StCardImport", "import failed: ${it.message}", it)
            _effects.tryEmit(AiChatEffect.ShowMessage(it.message ?: appCtx.getString(R.string.ai_character_import_failed)))
        }
    }
}

internal suspend fun AiChatViewModel.importCharacterParsed(parsed: io.legado.app.domain.model.ParsedCharacterImport): String {
    val card = importWorldInfoUseCase.upsertCardWithAvatar(parsed)
    val book = parsed.characterBook
    return if (book != null) {
        val result = importWorldInfoUseCase.importParsed(book, bindToCharacterCardId = card.id)
        buildString {
            append(appCtx.getString(R.string.ai_character_imported))
            append(" · ")
            append(appCtx.getString(R.string.ai_world_book_import_entries, result.entryCount))
            if (result.ignoredAdvanced) {
                append(" · ")
                append(appCtx.getString(R.string.ai_world_book_import_advanced_ignored))
            }
        }
    } else {
        appCtx.getString(R.string.ai_character_imported)
    }
}

// =====================================================================
// Writing prompts & skills
// =====================================================================

internal fun AiChatViewModel.saveWritingPrompt(name: String, content: String, category: String, promptId: String?) {
    viewModelScope.launch {
        runCatching {
            aiWritingPromptGateway.save(name, content, category, promptId)
        }.onFailure {
            _effects.tryEmit(AiChatEffect.ShowMessage(it.message ?: "Failed"))
        }
    }
}

internal fun AiChatViewModel.deleteWritingPrompt(promptId: String) {
    viewModelScope.launch {
        aiWritingPromptGateway.delete(promptId)
    }
}

internal fun AiChatViewModel.togglePromptEnabled(promptId: String) {
    viewModelScope.launch {
        val prompt = aiWritingPromptGateway.getById(promptId) ?: return@launch
        val newEnabled = !prompt.enabled
        aiWritingPromptGateway.updateEnabled(promptId, newEnabled)
        val cid = currentConversationId.value ?: return@launch
        if (_uiState.value.conversationType == "writing") {
            val enabledIds = AiIdListCodec.toCsv(
                _uiState.value.writingPrompts
                    .map { if (it.id == promptId) it.copy(enabled = newEnabled) else it }
                    .filter { it.enabled }
                    .map { it.id },
            )
            syncWorkspaceRefs(cid, writingPromptIds = enabledIds)
            aiChatGateway.updateConversationPrompts(cid, enabledIds.ifBlank { null })
        }
    }
}

internal fun AiChatViewModel.resolveWritingPromptContent(promptId: String, templateFallbackKey: String): String {
    val prompt = _uiState.value.writingPrompts.firstOrNull { it.id == promptId }
    return when {
        prompt != null && prompt.enabled -> prompt.content
        prompt != null && !prompt.enabled -> ""
        else -> AiPromptTemplate.DEFAULTS[templateFallbackKey].orEmpty()
    }
}

internal fun AiChatViewModel.toggleConversationSkill(skillId: String) {
    viewModelScope.launch {
        val cid = currentConversationId.value ?: return@launch
        val state = _uiState.value
        val type = state.conversationType
        val eligible = state.availableSkills
            .filter { it.globallyEnabled && skillMatchesMode(it.mode, type) }
            .map { it.skillId }
        if (skillId !in eligible) return@launch
        val selected = when (val raw = state.conversationSkillIds) {
            null -> eligible.toMutableSet()
            else -> AiIdListCodec.parse(raw).toMutableSet().apply {
                retainAll(eligible.toSet())
            }
        }
        if (skillId in selected) selected.remove(skillId) else selected.add(skillId)
        val next = when {
            selected.size == eligible.size && eligible.all { it in selected } -> null
            else -> AiIdListCodec.toCsv(selected)
        }
        aiChatGateway.updateConversationSkills(cid, next)
        _uiState.update { it.copy(conversationSkillIds = next) }
    }
}

internal fun AiChatViewModel.resetConversationSkills() {
    viewModelScope.launch {
        val cid = currentConversationId.value ?: return@launch
        aiChatGateway.updateConversationSkills(cid, null)
        _uiState.update { it.copy(conversationSkillIds = null) }
    }
}

internal fun AiChatViewModel.skillMatchesMode(skillMode: String, conversationType: String): Boolean = when (skillMode) {
    io.legado.app.data.entities.AiSkill.MODE_BOTH -> true
    io.legado.app.data.entities.AiSkill.MODE_CHAT -> conversationType == "chat"
    io.legado.app.data.entities.AiSkill.MODE_WRITING -> conversationType == "writing"
    else -> conversationType == skillMode
}

internal fun AiChatViewModel.savePromptTemplate(key: String, content: String) {
    viewModelScope.launch {
        runCatching {
            promptTemplateGateway.upsert(
                AiPromptTemplate(promptKey = key, content = content)
            )
        }.onFailure {
            _effects.tryEmit(AiChatEffect.ShowMessage(it.message ?: "Failed"))
        }
    }
}

internal fun AiChatViewModel.resetPromptTemplate(key: String) {
    viewModelScope.launch {
        promptTemplateGateway.delete(key)
    }
}

// =====================================================================
// Writing helpers (card-related)
// =====================================================================

internal fun AiChatViewModel.writingCharacterCards(): List<AiCharacterCardUi> {
    val state = _uiState.value
    return state.selectedCharacterCards.ifEmpty {
        state.selectedCharacterCard?.let { listOf(it) }.orEmpty()
    }
}

internal fun AiChatViewModel.buildPersonaMacroContext(): PersonaMacroContext? {
    val state = _uiState.value
    if (state.conversationType != "writing" || state.writingSubMode != "roleplay") return null
    return PersonaMacroContext.from(
        userName = state.userName,
        userCardEnabled = state.userCardEnabled,
        characterNames = writingCharacterCards().map { it.name },
    )
}

internal fun AiChatViewModel.expandInputMacros(text: String): String {
    val ctx = buildPersonaMacroContext() ?: return text
    return PersonaMacro.expand(text, ctx)
}

internal fun AiChatViewModel.contentForModel(display: String, isWriting: Boolean): String {
    val expanded = expandInputMacros(display)
    return if (isWriting) WritingUserInput.toModelContent(expanded) else expanded
}

internal fun AiChatViewModel.appendCharacterCards(
    parts: MutableList<String>,
    cards: List<AiCharacterCardUi>,
    macroCtx: PersonaMacroContext? = null,
) {
    cards.forEach { card ->
        val cardLines = mutableListOf<String>()
        cardLines.add("角色名：${card.name}")
        if (card.description.isNotBlank()) {
            val description = macroCtx?.let { PersonaMacro.expand(card.description, it) }
                ?: card.description
            cardLines.add("角色描述：$description")
        }
        if (card.openingLine.isNotBlank()) {
            cardLines.add("开场白示例：${card.openingLine}")
        }
        if (card.worldBookIds.isNotBlank()) {
            cardLines.add("绑定的世界书ID: ${card.worldBookIds}")
        }
        parts.add("\n<character_card>\n${cardLines.joinToString("\n")}\n</character_card>")
    }
}
