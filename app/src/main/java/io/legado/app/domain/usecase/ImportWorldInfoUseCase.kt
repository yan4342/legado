package io.legado.app.domain.usecase

import android.util.Log
import io.legado.app.data.entities.AiWorldBookEntry
import io.legado.app.domain.gateway.AiCharacterCardGateway
import io.legado.app.domain.gateway.AiWorldBookEntryGateway
import io.legado.app.domain.gateway.AiWorldBookGateway
import io.legado.app.domain.model.ImportWorldInfoResult
import io.legado.app.domain.model.ParsedCharacterImport
import io.legado.app.domain.model.PngCharaDecoder
import io.legado.app.domain.model.SillyTavernCharacterCardImporter
import io.legado.app.domain.model.SillyTavernWorldInfoImporter
import io.legado.app.domain.model.StImportJson
import io.legado.app.help.ai.CharacterAvatarStore
import io.legado.app.utils.AiIdListCodec
import java.util.UUID

class ImportWorldInfoUseCase(
    private val worldBookGateway: AiWorldBookGateway,
    private val worldBookEntryGateway: AiWorldBookEntryGateway,
    private val characterCardGateway: AiCharacterCardGateway,
) {
    private companion object {
        const val TAG = "StWorldImport"
    }

    private fun logI(msg: String) = runCatching { Log.i(TAG, msg) }

    suspend fun importParsed(
        parsed: SillyTavernWorldInfoImporter.ParsedWorldBook,
        bindToCharacterCardId: String? = null,
        characterCardId: String? = null,
        characterCardName: String? = null,
    ): ImportWorldInfoResult {
        val saved = worldBookGateway.save(
            name = parsed.name.ifBlank { "Imported Lorebook" },
            bookUrl = "",
            bookName = "",
            bookAuthor = "",
            writingStyle = "",
            grammar = "",
            plotSummary = parsed.description,
            representativeDialogues = "",
            representativeProse = "",
            sourceChapterIndices = "",
            worldBookId = null,
            enabled = true,
        )
        val now = System.currentTimeMillis()
        for (draft in parsed.entries) {
            worldBookEntryGateway.upsert(
                AiWorldBookEntry(
                    id = UUID.randomUUID().toString(),
                    worldBookId = saved.id,
                    name = draft.name,
                    keys = draft.keys,
                    content = draft.content,
                    constant = draft.constant,
                    priority = draft.priority,
                    enabled = draft.enabled,
                    position = draft.position,
                    insertDepth = draft.insertDepth,
                    role = draft.role,
                    scanDepth = draft.scanDepth,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        if (!bindToCharacterCardId.isNullOrBlank()) {
            val card = characterCardGateway.getById(bindToCharacterCardId)
            if (card != null) {
                val ids = AiIdListCodec.parse(card.worldBookIds).toMutableList()
                if (saved.id !in ids) ids.add(saved.id)
                characterCardGateway.upsert(
                    card.copy(worldBookIds = AiIdListCodec.toCsv(ids), updatedAt = System.currentTimeMillis()),
                )
            }
        }
        return ImportWorldInfoResult(
            worldBookId = saved.id,
            worldBookName = saved.name,
            entryCount = parsed.stats.entryCount,
            skippedEmpty = parsed.stats.skippedEmpty,
            ignoredAdvanced = parsed.stats.ignoredAdvanced,
            characterCardId = characterCardId ?: bindToCharacterCardId,
            characterCardName = characterCardName,
        )
    }

    suspend fun importJson(
        json: String,
        fallbackName: String = "Imported Lorebook",
        bindToCharacterCardId: String? = null,
    ): ImportWorldInfoResult {
        if (looksLikeCharacterCardJson(json)) {
            logI("importJson: character card bundle detected")
            return importCardBundle(
                SillyTavernCharacterCardImporter.parseFull(json),
                fallbackName,
            )
        }
        val parsed = SillyTavernWorldInfoImporter.parseFlexible(json, fallbackName)
        return importParsed(parsed, bindToCharacterCardId)
    }

    /**
     * Standalone lorebook JSON/PNG, or ST character-card PNG/JSON that embeds `character_book`.
     * Card bundles import **both** the character card and the world book (bound via worldBookIds).
     */
    suspend fun importBytes(
        bytes: ByteArray,
        fallbackName: String = "Imported Lorebook",
        bindToCharacterCardId: String? = null,
    ): ImportWorldInfoResult {
        if (PngCharaDecoder.isPng(bytes)) {
            logI("importBytes: PNG character card bundle")
            return importCardBundle(
                SillyTavernCharacterCardImporter.parseBytes(bytes),
                fallbackName,
            )
        }
        val text = StImportJson.decodeText(bytes)
        if (looksLikeCharacterCardJson(text)) {
            logI("importBytes: character card JSON bundle")
            return importCardBundle(
                SillyTavernCharacterCardImporter.parseFull(text),
                fallbackName,
            )
        }
        val parsed = SillyTavernWorldInfoImporter.parseBytes(bytes, fallbackName)
        return importParsed(parsed, bindToCharacterCardId)
    }

    suspend fun parseBytesToDrafts(
        bytes: ByteArray,
        fallbackName: String = "Imported Lorebook",
    ): SillyTavernWorldInfoImporter.ParsedWorldBook =
        SillyTavernWorldInfoImporter.parseBytes(bytes, fallbackName)

    /**
     * Upsert character card + lorebook from a ST card parse; always binds the new book to the card.
     */
    suspend fun importCardBundle(
        parsed: ParsedCharacterImport,
        fallbackName: String = "Imported Lorebook",
    ): ImportWorldInfoResult {
        val card = upsertCardWithAvatar(parsed)
        logI("importCardBundle: card=${card.name} id=${card.id} hasBook=${parsed.characterBook != null}")
        val book = parsed.characterBook
            ?: error("角色卡中没有 character_book / 世界书条目")
        val bookWithName = if (book.name.isBlank() || book.name == "Imported Lorebook") {
            book.copy(name = fallbackName.takeIf { it.isNotBlank() && it != "Imported Lorebook" }
                ?: "${card.name} 世界书")
        } else {
            book
        }
        return importParsed(
            bookWithName,
            bindToCharacterCardId = card.id,
            characterCardId = card.id,
            characterCardName = card.name,
        )
    }

    /** Persist card and import portrait bytes when present. */
    suspend fun upsertCardWithAvatar(parsed: ParsedCharacterImport): io.legado.app.data.entities.AiCharacterCard {
        val card = characterCardGateway.upsert(parsed.card)
        val bytes = parsed.avatarImageBytes ?: return card
        val path = runCatching {
            CharacterAvatarStore.copyFromBytes(card.id, bytes)
        }.onFailure {
            logI("avatar import failed for ${card.id}: ${it.message}")
        }.getOrNull() ?: return card
        return characterCardGateway.upsert(card.copy(avatarPath = path))
    }

    private fun looksLikeCharacterCardJson(json: String): Boolean {
        val root = runCatching { StImportJson.parse(json) }.getOrNull() ?: return false
        if (!root.isJsonObject) return false
        val obj = root.asJsonObject
        if (obj.has("spec") || obj.has("first_mes") || obj.has("char_name")) return true
        val data = obj.getAsJsonObject("data") ?: return false
        return data.has("first_mes") || data.has("character_book") ||
            (data.has("name") && (data.has("description") || data.has("personality")))
    }

    suspend fun upsertEntryDrafts(
        worldBookId: String,
        drafts: List<SillyTavernWorldInfoImporter.EntryDraft>,
        replaceExisting: Boolean = false,
    ) {
        if (replaceExisting) {
            worldBookEntryGateway.getForWorldBook(worldBookId).forEach {
                worldBookEntryGateway.delete(it.id)
            }
        }
        val now = System.currentTimeMillis()
        for (draft in drafts) {
            if (draft.content.isBlank()) continue
            worldBookEntryGateway.upsert(
                AiWorldBookEntry(
                    id = UUID.randomUUID().toString(),
                    worldBookId = worldBookId,
                    name = draft.name,
                    keys = draft.keys,
                    content = draft.content,
                    constant = draft.constant,
                    priority = draft.priority,
                    enabled = draft.enabled,
                    position = draft.position,
                    insertDepth = draft.insertDepth,
                    role = draft.role,
                    scanDepth = draft.scanDepth,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }
}
