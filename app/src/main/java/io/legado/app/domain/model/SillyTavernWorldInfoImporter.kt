package io.legado.app.domain.model

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.legado.app.data.entities.AiWorldBookEntry

/**
 * Parses SillyTavern World Info / Lorebook JSON (standalone export or character_book).
 * Lossy mapping into [ParsedWorldBook] drafts — see plan field table.
 */
object SillyTavernWorldInfoImporter {

    private const val TAG = "StWorldImport"

    private fun logI(msg: String) {
        runCatching { Log.i(TAG, msg) }
    }

    private fun logE(msg: String, t: Throwable? = null) {
        runCatching {
            if (t != null) Log.e(TAG, msg, t) else Log.e(TAG, msg)
        }
    }

    data class EntryDraft(
        val name: String,
        val keys: String,
        val content: String,
        val constant: Boolean = false,
        val priority: Int = 100,
        val enabled: Boolean = true,
        val position: String = AiWorldBookEntry.POSITION_PREFIX,
        val insertDepth: Int = 0,
        val role: String = AiWorldBookEntry.ROLE_SYSTEM,
        val scanDepth: Int = 0,
    )

    data class ImportStats(
        val entryCount: Int = 0,
        val skippedEmpty: Int = 0,
        val ignoredAdvanced: Boolean = false,
    )

    data class ParsedWorldBook(
        val name: String,
        val description: String = "",
        val entries: List<EntryDraft> = emptyList(),
        val stats: ImportStats = ImportStats(),
    )

    fun parse(json: String, fallbackName: String = "Imported Lorebook"): ParsedWorldBook {
        logI("parse jsonLen=${json.length} fallback=$fallbackName head=${json.take(60).replace('\n', ' ')}")
        return parseFlexible(json, fallbackName, fromPng = false)
    }

    /**
     * JSON text, or SillyTavern PNG character card (`chara`/`ccv3` → extract `character_book`).
     */
    fun parseBytes(bytes: ByteArray, fallbackName: String = "Imported Lorebook"): ParsedWorldBook {
        val isPng = PngCharaDecoder.isPng(bytes)
        logI(
            "parseBytes size=${bytes.size} isPng=$isPng magic=${bytes.take(8).joinToString(" ") { "%02x".format(it) }}",
        )
        val json = if (isPng) {
            PngCharaDecoder.decodeJson(bytes)
        } else {
            StImportJson.decodeText(bytes).also {
                logI("text decode jsonLen=${it.length} head=${it.take(60).replace('\n', ' ')}")
            }
        }
        return parseFlexible(json, fallbackName, fromPng = isPng)
    }

    /**
     * Prefer embedded `character_book` (card V2/V3); otherwise treat root as lorebook.
     */
    fun parseFlexible(
        json: String,
        fallbackName: String = "Imported Lorebook",
        fromPng: Boolean = false,
    ): ParsedWorldBook {
        logI("parseFlexible jsonLen=${json.length} fromPng=$fromPng fallback=$fallbackName")
        val root = try {
            StImportJson.parse(json)
        } catch (e: Exception) {
            logE("JSON parse failed: ${e.message}", e)
            throw e
        }
        logI("root type=${rootKind(root)} isObj=${root.isJsonObject} isArr=${root.isJsonArray}")
        if (root.isJsonObject) {
            val obj = root.asJsonObject
            val bookEl = unwrapMaybeStringJson(
                obj.get("character_book")
                    ?: obj.getAsJsonObject("data")?.get("character_book"),
            )
            val cardFallback = when {
                fromPng || looksLikeCharacterCard(obj) -> cardNameFallback(obj, fallbackName)
                else -> fallbackName
            }
            val fromCard = parseCharacterBook(bookEl, fallbackName = cardFallback)
            if (fromCard != null) {
                logI(
                    "using character_book name=${fromCard.name} entries=${fromCard.entries.size} skipped=${fromCard.stats.skippedEmpty} fromPng=$fromPng",
                )
                return fromCard
            }
            // Nested lorebook keys used by some ST exports
            val nested = unwrapMaybeStringJson(
                obj.get("worldInfo")
                    ?: obj.get("worldinfo")
                    ?: obj.get("lorebook")
                    ?: obj.get("book"),
            )
            if (nested != null) {
                val nestedBook = when {
                    nested.isJsonObject || nested.isJsonArray -> parseRoot(nested, fallbackName)
                    else -> null
                }
                if (nestedBook != null && (nestedBook.entries.isNotEmpty() || nested.isJsonObject)) {
                    logI("using nested lorebook name=${nestedBook.name} entries=${nestedBook.entries.size}")
                    return nestedBook
                }
            }
            if (fromPng || (looksLikeCharacterCard(obj) && !obj.has("entries"))) {
                logE("character card JSON has no usable character_book/entries")
                error("角色卡 JSON 中没有 character_book / 世界书条目")
            }
        }
        val parsed = parseRoot(root, fallbackName)
        logI(
            "standalone lorebook name=${parsed.name} entries=${parsed.entries.size} skipped=${parsed.stats.skippedEmpty} advanced=${parsed.stats.ignoredAdvanced}",
        )
        return parsed
    }

    private fun unwrapMaybeStringJson(el: JsonElement?): JsonElement? {
        if (el == null || el.isJsonNull) return null
        if (el.isJsonObject || el.isJsonArray) return el
        if (el.isJsonPrimitive && el.asJsonPrimitive.isString) {
            val s = el.asString.trim()
            if (s.startsWith('{') || s.startsWith('[')) {
                return runCatching { StImportJson.parse(s) }.getOrNull()
            }
        }
        return el
    }

    private fun looksLikeCharacterCard(obj: JsonObject): Boolean {
        if (obj.has("spec") || obj.has("first_mes") || obj.has("char_name")) return true
        val data = obj.getAsJsonObject("data") ?: return false
        return data.has("first_mes") || data.has("name") || data.has("character_book")
    }

    private fun rootKind(el: JsonElement): String = when {
        el.isJsonObject -> "object"
        el.isJsonArray -> "array"
        el.isJsonNull -> "null"
        el.isJsonPrimitive -> "primitive:${el.asString.take(40)}"
        else -> "unknown"
    }

    private fun cardNameFallback(root: JsonObject, fallbackName: String): String {
        val data = root.getAsJsonObject("data") ?: root
        val name = data.get("name")?.takeIf { it.isJsonPrimitive }?.asString
            ?: data.get("char_name")?.takeIf { it.isJsonPrimitive }?.asString
        return if (!name.isNullOrBlank()) "$name 世界书" else fallbackName
    }

    private fun parseRoot(root: JsonElement, fallbackName: String): ParsedWorldBook {
        return when {
            root.isJsonArray -> parseEntriesRoot(root.asJsonArray, fallbackName)
            root.isJsonObject -> parseObjectRoot(root.asJsonObject, fallbackName)
            else -> {
                logE("parseRoot rejected type=${rootKind(root)}")
                error("无效的世界信息 JSON（需要对象或数组，当前为 ${rootKind(root)}）")
            }
        }
    }

    /** Prefer [characterBook] element; also accepts a root that already is character_book. */
    fun parseCharacterBook(element: JsonElement?, fallbackName: String): ParsedWorldBook? {
        if (element == null || !element.isJsonObject) return null
        val obj = element.asJsonObject
        if (!obj.has("entries") && !looksLikeEntryMap(obj)) return null
        return parseObjectRoot(obj, fallbackName)
    }

    private fun parseObjectRoot(obj: JsonObject, fallbackName: String): ParsedWorldBook {
        val name = stringField(obj, "name").ifBlank { fallbackName }
        val description = stringField(obj, "description")
        val entriesEl = obj.get("entries")
        val (drafts, skipped, advanced) = when {
            entriesEl == null && looksLikeEntryMap(obj) -> parseEntryMap(obj)
            entriesEl != null && entriesEl.isJsonArray -> parseEntryArray(entriesEl.asJsonArray)
            entriesEl != null && entriesEl.isJsonObject -> parseEntryMap(entriesEl.asJsonObject)
            else -> Triple(emptyList(), 0, false)
        }
        return ParsedWorldBook(
            name = name,
            description = description,
            entries = drafts,
            stats = ImportStats(
                entryCount = drafts.size,
                skippedEmpty = skipped,
                ignoredAdvanced = advanced || hasBookAdvanced(obj),
            ),
        )
    }

    private fun parseEntriesRoot(arr: JsonArray, fallbackName: String): ParsedWorldBook {
        val (drafts, skipped, advanced) = parseEntryArray(arr)
        return ParsedWorldBook(
            name = fallbackName,
            entries = drafts,
            stats = ImportStats(entryCount = drafts.size, skippedEmpty = skipped, ignoredAdvanced = advanced),
        )
    }

    private fun looksLikeEntryMap(obj: JsonObject): Boolean {
        if (obj.has("entries") || obj.has("name") || obj.has("description")) return false
        val first = obj.entrySet().firstOrNull()?.value ?: return false
        return first.isJsonObject && (
            first.asJsonObject.has("content") ||
                first.asJsonObject.has("key") ||
                first.asJsonObject.has("keys")
            )
    }

    private fun parseEntryArray(arr: JsonArray): Triple<List<EntryDraft>, Int, Boolean> {
        val out = mutableListOf<EntryDraft>()
        var skipped = 0
        var advanced = false
        for (el in arr) {
            if (!el.isJsonObject) continue
            val obj = el.asJsonObject
            val draft = parseEntry(obj)
            if (draft == null) {
                skipped++
                continue
            }
            if (entryHasAdvanced(obj)) advanced = true
            out.add(draft)
        }
        return Triple(out, skipped, advanced)
    }

    private fun parseEntryMap(map: JsonObject): Triple<List<EntryDraft>, Int, Boolean> {
        val out = mutableListOf<EntryDraft>()
        var skipped = 0
        var advanced = false
        for ((uid, el) in map.entrySet()) {
            if (!el.isJsonObject) continue
            val obj = el.asJsonObject
            val draft = parseEntry(obj, uidFallback = uid)
            if (draft == null) {
                skipped++
                continue
            }
            if (entryHasAdvanced(obj)) advanced = true
            out.add(draft)
        }
        return Triple(out, skipped, advanced)
    }

    private fun parseEntry(obj: JsonObject, uidFallback: String? = null): EntryDraft? {
        val content = stringField(obj, "content").trim()
        if (content.isEmpty()) return null

        val name = stringField(obj, "comment")
            .ifBlank { stringField(obj, "name") }
            .ifBlank { stringField(obj, "memo") }
            .ifBlank {
                when {
                    obj.has("uid") -> obj.get("uid").toString().trim('"')
                    !uidFallback.isNullOrBlank() -> uidFallback
                    else -> ""
                }
            }

        val primary = normalizeKeys(obj.get("key") ?: obj.get("keys"))
        val secondary = normalizeKeys(obj.get("keysecondary") ?: obj.get("secondary_keys"))
        val keys = (primary + secondary).distinct().joinToString(",")

        val enabled = when {
            obj.has("disable") && obj.get("disable").asBooleanOrFalse() -> false
            obj.has("enabled") -> obj.get("enabled").asBooleanOrTrue()
            else -> true
        }

        val priority = intField(obj, "insertion_order")
            ?: intField(obj, "order")
            ?: intField(obj, "priority")
            ?: 100

        val position = mapPosition(obj.get("position"))
        val insertDepth = if (position == AiWorldBookEntry.POSITION_IN_CHAT) {
            intField(obj, "depth") ?: intField(obj, "insertDepth") ?: 0
        } else {
            0
        }
        val role = mapRole(obj.get("role"), position)
        val scanDepth = intField(obj, "scanDepth")
            ?: intField(obj, "scan_depth")
            ?: 0

        return EntryDraft(
            name = name,
            keys = keys,
            content = content,
            constant = obj.get("constant")?.asBooleanOrFalse() == true,
            priority = priority,
            enabled = enabled,
            position = position,
            insertDepth = insertDepth.coerceAtLeast(0),
            role = role,
            scanDepth = scanDepth.coerceAtLeast(0),
        )
    }

    /**
     * ST insertion positions (numeric): 0–5 / 7 → prefix; 6 (@D) → in_chat.
     * Also accepts string labels containing "depth" / "@d" / "in_chat".
     */
    fun mapPosition(raw: JsonElement?): String {
        if (raw == null || raw.isJsonNull) return AiWorldBookEntry.POSITION_PREFIX
        if (raw.isJsonPrimitive) {
            val p = raw.asJsonPrimitive
            if (p.isNumber) {
                // SillyTavern: 6 = at depth (@D)
                return if (p.asInt == 6) AiWorldBookEntry.POSITION_IN_CHAT
                else AiWorldBookEntry.POSITION_PREFIX
            }
            val s = p.asString.trim().lowercase()
            if (s.contains("depth") || s == "@d" || s == "at_depth" ||
                s == "in_chat" || s == "inchat" || s == "chat"
            ) {
                return AiWorldBookEntry.POSITION_IN_CHAT
            }
        }
        return AiWorldBookEntry.POSITION_PREFIX
    }

    fun mapRole(raw: JsonElement?, position: String): String {
        if (position != AiWorldBookEntry.POSITION_IN_CHAT) return AiWorldBookEntry.ROLE_SYSTEM
        if (raw == null || raw.isJsonNull) return AiWorldBookEntry.ROLE_SYSTEM
        if (!raw.isJsonPrimitive) return AiWorldBookEntry.ROLE_SYSTEM
        val p = raw.asJsonPrimitive
        if (p.isNumber) {
            return when (p.asInt) {
                1 -> AiWorldBookEntry.ROLE_USER
                2 -> AiWorldBookEntry.ROLE_ASSISTANT
                else -> AiWorldBookEntry.ROLE_SYSTEM
            }
        }
        return when (p.asString.trim().lowercase()) {
            "user", "👤" -> AiWorldBookEntry.ROLE_USER
            "assistant", "bot", "🤖" -> AiWorldBookEntry.ROLE_ASSISTANT
            else -> AiWorldBookEntry.ROLE_SYSTEM
        }
    }

    fun normalizeKeys(raw: JsonElement?): List<String> {
        if (raw == null || raw.isJsonNull) return emptyList()
        return when {
            raw.isJsonArray -> raw.asJsonArray.mapNotNull {
                if (it.isJsonPrimitive) it.asString.trim().takeIf { s -> s.isNotEmpty() } else null
            }
            raw.isJsonPrimitive -> raw.asString
                .split(',', '\n', ';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            else -> emptyList()
        }
    }

    private fun hasBookAdvanced(obj: JsonObject): Boolean =
        obj.has("scan_depth") || obj.has("token_budget") || obj.has("recursive_scanning")

    private fun entryHasAdvanced(obj: JsonObject): Boolean {
        if (listOf(
                "probability", "useProbability", "weight",
                "case_sensitive", "useRegex", "selective", "selectiveLogic", "sticky", "cooldown",
                "delay", "extensions",
            ).any { obj.has(it) }
        ) {
            return true
        }
        // Non-@D ST positions (AN / example / outlet) collapse to prefix — still warn.
        val pos = obj.get("position") ?: return false
        if (!pos.isJsonPrimitive) return false
        val p = pos.asJsonPrimitive
        if (p.isNumber) {
            val n = p.asInt
            return n !in listOf(0, 1, 6) // 0/1 char defs → prefix; 6 = @D supported
        }
        val s = p.asString.trim().lowercase()
        if (s.isEmpty() || s == "prefix" || s.contains("char")) return false
        return mapPosition(pos) == AiWorldBookEntry.POSITION_PREFIX
    }

    private fun stringField(obj: JsonObject, key: String): String {
        val el = obj.get(key) ?: return ""
        return if (el.isJsonPrimitive) el.asString else ""
    }

    private fun intField(obj: JsonObject, key: String): Int? {
        val el = obj.get(key) ?: return null
        return runCatching {
            when {
                el.isJsonPrimitive && el.asJsonPrimitive.isNumber -> el.asInt
                el.isJsonPrimitive -> el.asString.toIntOrNull()
                else -> null
            }
        }.getOrNull()
    }

    private fun JsonElement.asBooleanOrFalse(): Boolean =
        runCatching {
            when {
                isJsonPrimitive && asJsonPrimitive.isBoolean -> asBoolean
                isJsonPrimitive -> asString.equals("true", ignoreCase = true)
                else -> false
            }
        }.getOrDefault(false)

    private fun JsonElement.asBooleanOrTrue(): Boolean =
        runCatching {
            when {
                isJsonPrimitive && asJsonPrimitive.isBoolean -> asBoolean
                isJsonPrimitive -> !asString.equals("false", ignoreCase = true)
                else -> true
            }
        }.getOrDefault(true)
}
