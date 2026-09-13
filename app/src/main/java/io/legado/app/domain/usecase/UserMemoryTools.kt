package io.legado.app.domain.usecase

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.legado.app.data.entities.AiMemory
import io.legado.app.domain.gateway.AiMemoryGateway

/**
 * Cross-conversation habit memory (global AiMemory with conversationId "").
 * Chat/Agent only — Writing must not inject or write.
 */
object UserMemoryTools {

    const val MAX_VALUE_CHARS = 500
    const val MAX_GLOBAL_ENTRIES = 50
    /** Writing-RP user description; excluded from chat habit prompt injection. */
    const val KEY_PERSONA = "persona"
    private val KEY_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_.]{0,63}$")

    suspend fun buildPromptBlock(
        conversationType: String,
        conversationId: String?,
        gateway: AiMemoryGateway,
    ): String? {
        if (conversationType == "writing") return null
        if (conversationId.isNullOrBlank()) return null
        val memories = gateway.getForPrompt(conversationId)
            .filterNot { it.key.equals(KEY_PERSONA, ignoreCase = true) }
        if (memories.isEmpty()) return null
        val memoryBlock = memories.joinToString("\n") { "- ${it.key}: ${it.value}" }
        val memoryHash = memoryBlock.hashCode().toString(16)
        return "\n<user_memory hash=\"$memoryHash\">\n$memoryBlock\n</user_memory>"
    }

    suspend fun read(gateway: AiMemoryGateway, key: String? = null): String {
        val global = gateway.getGlobal()
        val filtered = if (key.isNullOrBlank()) {
            global
        } else {
            global.filter { it.key == key }
        }
        val entries = JsonArray()
        filtered.forEach { mem ->
            entries.add(JsonObject().apply {
                addProperty("key", mem.key)
                addProperty("value", mem.value)
            })
        }
        return JsonObject().apply {
            addProperty("success", true)
            addProperty("count", filtered.size)
            add("entries", entries)
        }.toString()
    }

    /**
     * Apply set/delete ops to global habit memory only.
     * @return JSON result; never writes when [conversationType] is writing.
     */
    suspend fun patch(
        gateway: AiMemoryGateway,
        args: JsonObject,
        conversationType: String?,
    ): String {
        if (conversationType == "writing") {
            return """{"error":"User habit memory is not available in writing mode"}"""
        }
        val ops = parseOps(args)
        if (ops.isEmpty()) {
            return """{"error":"operations required: [{\"op\":\"set|delete\",\"key\":\"...\",\"value\":\"...\"}]"}"""
        }
        val applied = JsonArray()
        for (op in ops) {
            when (op.op) {
                "set" -> {
                    validateKey(op.key)?.let { return it }
                    val value = (op.value ?: "").take(MAX_VALUE_CHARS)
                    if (value.isBlank()) {
                        return """{"error":"value is required for set"}"""
                    }
                    val existing = gateway.getGlobal()
                    val isUpdate = existing.any { it.key == op.key }
                    if (!isUpdate && existing.size >= MAX_GLOBAL_ENTRIES) {
                        return """{"error":"Global habit memory limit ($MAX_GLOBAL_ENTRIES) reached; delete unused keys first"}"""
                    }
                    gateway.upsert(
                        AiMemory(
                            conversationId = "",
                            key = op.key,
                            value = value,
                        )
                    )
                    applied.add(JsonObject().apply {
                        addProperty("op", "set")
                        addProperty("key", op.key)
                        addProperty("value", value)
                    })
                }
                "delete" -> {
                    validateKey(op.key)?.let { return it }
                    gateway.delete("", op.key)
                    applied.add(JsonObject().apply {
                        addProperty("op", "delete")
                        addProperty("key", op.key)
                    })
                }
                else -> return """{"error":"Unknown op: ${op.op}. Use set or delete"}"""
            }
        }
        return JsonObject().apply {
            addProperty("success", true)
            add("applied", applied)
        }.toString()
    }

    fun summary(args: JsonObject): String {
        val ops = parseOps(args)
        if (ops.isEmpty()) return "Patch user habit memory"
        return ops.joinToString("; ") { op ->
            when (op.op) {
                "set" -> "Remember: ${op.key} = ${(op.value ?: "").take(40)}"
                "delete" -> "Delete memory: ${op.key}"
                else -> "${op.op}: ${op.key}"
            }
        }
    }

    fun previewDetail(args: JsonObject): String = summary(args)

    fun operationPreview(args: JsonObject): String {
        val ops = parseOps(args)
        if (ops.isEmpty()) return ""
        return ops.map { it.op }.distinct().joinToString(", ")
    }

    data class Op(val op: String, val key: String, val value: String?)

    fun parseOps(args: JsonObject): List<Op> {
        val opsElement = args.get("operations")
        val fromArray: JsonArray? = when {
            opsElement == null || opsElement.isJsonNull -> null
            opsElement.isJsonArray -> opsElement.asJsonArray
            opsElement.isJsonPrimitive -> runCatching {
                com.google.gson.JsonParser.parseString(opsElement.asString).asJsonArray
            }.getOrNull()
            else -> null
        }
        if (fromArray != null && fromArray.size() > 0) {
            return fromArray.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val obj = el.asJsonObject
                val op = obj.get("op")?.asString?.trim().orEmpty()
                val key = obj.get("key")?.asString?.trim().orEmpty()
                if (op.isBlank() || key.isBlank()) return@mapNotNull null
                Op(op = op, key = key, value = obj.get("value")?.asString)
            }
        }
        val op = args.get("action")?.asString?.trim().orEmpty()
        val key = args.get("key")?.asString?.trim().orEmpty()
        if (op.isBlank() || key.isBlank()) return emptyList()
        return listOf(Op(op = op, key = key, value = args.get("value")?.asString))
    }

    private fun validateKey(key: String): String? {
        if (!KEY_REGEX.matches(key)) {
            return """{"error":"Invalid key \"$key\". Use short ASCII/dot keys e.g. pref.reply_style"}"""
        }
        return null
    }

    fun isValidKey(key: String): Boolean = KEY_REGEX.matches(key)
}
