package io.legado.app.domain.model

import android.util.Base64
import android.util.Log
import io.legado.app.data.entities.AiCharacterCard
import io.legado.app.utils.GSON
import java.util.UUID

data class ParsedCharacterImport(
    val card: AiCharacterCard,
    val characterBook: SillyTavernWorldInfoImporter.ParsedWorldBook? = null,
    /** Embedded portrait bytes (PNG card image or JSON base64 avatar). */
    val avatarImageBytes: ByteArray? = null,
)

object SillyTavernCharacterCardImporter {
    private const val TAG = "StCardImport"

    private fun logI(msg: String) = runCatching { Log.i(TAG, msg) }
    private fun logD(msg: String) = runCatching { Log.d(TAG, msg) }

    fun parse(json: String): AiCharacterCard = parseFull(json).card

    fun parseFull(json: String, avatarImageBytes: ByteArray? = null): ParsedCharacterImport {
        val root = StImportJson.parse(json)
        val data = when {
            root.isJsonObject && root.asJsonObject.has("data") -> root.asJsonObject.getAsJsonObject("data")
            root.isJsonObject -> root.asJsonObject
            else -> error("Invalid character card JSON")
        }
        fun field(vararg keys: String): String {
            for (key in keys) {
                val el = data.get(key) ?: continue
                if (el.isJsonPrimitive) return el.asString
            }
            return ""
        }
        val name = field("name", "char_name").ifBlank { "Imported Character" }
        val description = field("description", "char_persona")
        val personality = field("personality")
        val scenario = field("scenario", "world_scenario")
        val openingLine = field("first_mes", "char_greeting")
        val exampleDialogues = field("mes_example", "example_dialogue")
        val postHistory = field("post_history_instructions")
        val alternates = mutableListOf<String>()
        data.getAsJsonArray("alternate_greetings")?.forEach { el ->
            if (el.isJsonPrimitive) alternates.add(el.asString)
        }
        val card = if (openingLine.isBlank() && alternates.isNotEmpty()) {
            val first = alternates.removeAt(0)
            AiCharacterCard(
                id = UUID.randomUUID().toString(),
                name = name,
                description = description,
                personality = personality.ifBlank { description },
                scenario = scenario,
                openingLine = first,
                exampleDialogues = exampleDialogues,
                postHistoryInstructions = postHistory,
                alternateOpenings = GSON.toJson(alternates),
            )
        } else {
            AiCharacterCard(
                id = UUID.randomUUID().toString(),
                name = name,
                description = description,
                personality = personality,
                scenario = scenario,
                openingLine = openingLine,
                exampleDialogues = exampleDialogues,
                postHistoryInstructions = postHistory,
                alternateOpenings = GSON.toJson(alternates),
            )
        }
        val bookElRaw = data.get("character_book")
            ?: root.takeIf { it.isJsonObject }?.asJsonObject?.get("character_book")
        val bookEl = when {
            bookElRaw == null || bookElRaw.isJsonNull -> null
            bookElRaw.isJsonObject -> bookElRaw
            bookElRaw.isJsonPrimitive && bookElRaw.asJsonPrimitive.isString -> {
                val s = bookElRaw.asString.trim()
                if (s.startsWith('{') || s.startsWith('[')) {
                    runCatching { StImportJson.parse(s) }.getOrNull()
                } else null
            }
            else -> bookElRaw
        }
        val characterBook = SillyTavernWorldInfoImporter.parseCharacterBook(
            bookEl,
            fallbackName = "$name 世界书",
        )
        val embeddedAvatar = avatarImageBytes ?: decodeEmbeddedAvatar(data)
        logD(
            "parsed card name=$name hasBook=${characterBook != null} entries=${characterBook?.entries?.size ?: 0} " +
                "hasAvatar=${embeddedAvatar != null}",
        )
        return ParsedCharacterImport(
            card = card,
            characterBook = characterBook,
            avatarImageBytes = embeddedAvatar,
        )
    }

    fun parseBytes(bytes: ByteArray): ParsedCharacterImport {
        val isPng = PngCharaDecoder.isPng(bytes)
        logI(
            "parseBytes size=${bytes.size} isPng=$isPng magic=${bytes.take(8).joinToString(" ") { "%02x".format(it) }}",
        )
        return if (isPng) {
            // ST character PNGs use the image itself as the portrait.
            parseFull(PngCharaDecoder.decodeJson(bytes), avatarImageBytes = bytes)
        } else {
            val json = StImportJson.decodeText(bytes).also {
                logI("treating as text jsonLen=${it.length}")
            }
            parseFull(json)
        }
    }

    /** Prefer data-URI / raw base64 in `avatar`; ignore short filename placeholders. */
    private fun decodeEmbeddedAvatar(data: com.google.gson.JsonObject): ByteArray? {
        val raw = data.get("avatar")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
        if (raw.length < 64) return null
        val b64 = when {
            "base64," in raw -> raw.substringAfter("base64,")
            raw.startsWith("data:") -> return null
            else -> raw
        }
        return runCatching {
            Base64.decode(b64, Base64.DEFAULT)
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }
}
