package io.legado.app.domain.usecase

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.data.entities.Book

object BookshelfChapterIndices {

    const val MAX_CHAPTERS_PER_READ = 10

    sealed class ParseResult {
        data class Success(val indices: List<Int>) : ParseResult()
        data class Error(val message: String) : ParseResult()
    }

    fun parse(args: JsonObject, defaultChapterIndex: Int): ParseResult {
        val fromArray = args.get("chapterIndices")?.takeIf { !it.isJsonNull }?.let { element ->
            when {
                element.isJsonArray -> element.asJsonArray
                element.isJsonPrimitive -> runCatching {
                    JsonParser.parseString(element.asString).asJsonArray
                }.getOrNull()
                else -> null
            }
        }
        val indices = if (fromArray != null && fromArray.size() > 0) {
            buildList {
                for (i in 0 until fromArray.size()) {
                    val item = fromArray.get(i)
                    if (!item.isJsonPrimitive || !item.asJsonPrimitive.isNumber) {
                        return ParseResult.Error("chapterIndices must be a JSON array of integers")
                    }
                    add(item.asInt.coerceAtLeast(0))
                }
            }.distinct().sorted()
        } else {
            listOf(args.int("chapterIndex", defaultChapterIndex).coerceAtLeast(0))
        }
        if (indices.isEmpty()) {
            return ParseResult.Error("At least one chapter index is required")
        }
        if (indices.size > MAX_CHAPTERS_PER_READ) {
            return ParseResult.Error("At most $MAX_CHAPTERS_PER_READ chapters per read")
        }
        return ParseResult.Success(indices)
    }

    private fun JsonObject.int(name: String, defaultValue: Int): Int {
        return runCatching { get(name)?.takeIf { !it.isJsonNull }?.asInt }.getOrNull() ?: defaultValue
    }

    fun formatRange(indices: List<Int>): String {
        if (indices.isEmpty()) return ""
        if (indices.size == 1) return "chapter ${indices.first() + 1}"
        return "chapters ${indices.first() + 1}–${indices.last() + 1} (${indices.size} total)"
    }
}
