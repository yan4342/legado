package io.legado.app.domain.model

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import java.io.StringReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Lenient JSON parsing for SillyTavern exports.
 *
 * Gson 2.11+ [JsonParser.parseString] restores [Strictness.LEGACY_STRICT] before checking
 * end-of-document, so trailing junk / a second top-level value throws
 * `MalformedJsonException: Use JsonReader.setStrictness(...)`. Object trailing commas
 * (`{"a":1,}`) also fail even in LENIENT mode — strip them first.
 */
object StImportJson {

    fun parse(raw: String): JsonElement {
        var prepared = prepare(raw)
        // Unwrap double-encoded JSON strings: "\"{...}\"" → {...}
        for (i in 0 until 3) {
            val reader = JsonReader(StringReader(prepared)).apply {
                setStrictness(Strictness.LENIENT)
            }
            val el = JsonParser.parseReader(reader)
            if (el.isJsonObject || el.isJsonArray) return el
            if (el.isJsonPrimitive && el.asJsonPrimitive.isString) {
                val inner = el.asString.trim()
                if (inner.startsWith('{') || inner.startsWith('[') || inner.startsWith('"')) {
                    prepared = prepare(inner)
                    continue
                }
            }
            return el
        }
        val reader = JsonReader(StringReader(prepared)).apply {
            setStrictness(Strictness.LENIENT)
        }
        return JsonParser.parseReader(reader)
    }

    fun decodeText(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val charset = detectCharset(bytes)
        var text = String(bytes, charset)
        if (text.isNotEmpty() && text[0] == '\uFEFF') {
            text = text.substring(1)
        }
        return text
    }

    fun prepare(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("\uFEFF")) s = s.substring(1).trim()
        // Strip markdown fences if a user pasted / exported wrapped JSON.
        if (s.startsWith("```")) {
            s = s.removePrefix("```json").removePrefix("```JSON").removePrefix("```")
                .trim()
            val fence = s.lastIndexOf("```")
            if (fence >= 0) s = s.substring(0, fence).trim()
        }
        return stripTrailingCommas(s)
    }

    private fun detectCharset(bytes: ByteArray): Charset {
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xff
            val b1 = bytes[1].toInt() and 0xff
            if (b0 == 0xfe && b1 == 0xff) return StandardCharsets.UTF_16BE
            if (b0 == 0xff && b1 == 0xfe) return StandardCharsets.UTF_16LE
        }
        if (bytes.size >= 3 &&
            (bytes[0].toInt() and 0xff) == 0xef &&
            (bytes[1].toInt() and 0xff) == 0xbb &&
            (bytes[2].toInt() and 0xff) == 0xbf
        ) {
            return StandardCharsets.UTF_8
        }
        // UTF-16 without BOM: many NULs in even/odd positions
        if (bytes.size >= 4) {
            var evenNul = 0
            var oddNul = 0
            val n = minOf(bytes.size, 64)
            for (i in 0 until n) {
                if (bytes[i] == 0.toByte()) {
                    if (i % 2 == 0) evenNul++ else oddNul++
                }
            }
            if (oddNul > n / 4 && evenNul < n / 10) return StandardCharsets.UTF_16LE
            if (evenNul > n / 4 && oddNul < n / 10) return StandardCharsets.UTF_16BE
        }
        return StandardCharsets.UTF_8
    }

    /**
     * Remove trailing commas before `}` / `]`, skipping string contents.
     * Gson LENIENT still rejects `{"a":1,}`.
     */
    internal fun stripTrailingCommas(json: String): String {
        val out = StringBuilder(json.length)
        var i = 0
        var inString = false
        var escape = false
        while (i < json.length) {
            val c = json[i]
            if (inString) {
                out.append(c)
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
                i++
                continue
            }
            when (c) {
                '"' -> {
                    inString = true
                    out.append(c)
                    i++
                }
                ',' -> {
                    var j = i + 1
                    while (j < json.length && json[j].isWhitespace()) j++
                    if (j < json.length && (json[j] == '}' || json[j] == ']')) {
                        // drop trailing comma
                        i++
                    } else {
                        out.append(c)
                        i++
                    }
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }
}
