package io.legado.app.domain.model

import android.util.Log
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.zip.InflaterInputStream

/**
 * Extracts SillyTavern character-card JSON embedded in PNG tEXt / zTXt / iTXt chunks
 * (`chara` or `ccv3` keywords).
 */
object PngCharaDecoder {

    private const val TAG = "PngChara"

    private fun logI(msg: String) = runCatching { Log.i(TAG, msg) }
    private fun logD(msg: String) = runCatching { Log.d(TAG, msg) }
    private fun logW(msg: String) = runCatching { Log.w(TAG, msg) }
    private fun logE(msg: String) = runCatching { Log.e(TAG, msg) }

    private val PNG_SIG = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    fun isPng(bytes: ByteArray): Boolean =
        bytes.size >= 8 && PNG_SIG.indices.all { bytes[it] == PNG_SIG[it] }

    fun decodeJson(bytes: ByteArray): String {
        require(isPng(bytes)) { "Not a PNG file" }
        logI("decode start: bytes=${bytes.size}")
        var offset = 8
        var charaB64: String? = null
        var ccv3B64: String? = null
        var charaChunk: String? = null
        var ccv3Chunk: String? = null
        var textChunks = 0
        while (offset + 12 <= bytes.size) {
            val length = readInt(bytes, offset)
            val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
            val dataStart = offset + 8
            val dataEnd = dataStart + length
            if (dataEnd + 4 > bytes.size) {
                logW("chunk truncated type=$type length=$length offset=$offset total=${bytes.size}")
                break
            }
            when (type) {
                "tEXt" -> parseTextChunk(bytes, dataStart, length)?.let { (key, value) ->
                    textChunks++
                    logD("tEXt key=$key valueLen=${value.length}")
                    when (key) {
                        "chara" -> {
                            charaB64 = value
                            charaChunk = "tEXt"
                        }
                        "ccv3" -> {
                            ccv3B64 = value
                            ccv3Chunk = "tEXt"
                        }
                    }
                }
                "zTXt" -> parseZtxtChunk(bytes, dataStart, length)?.let { (key, value) ->
                    textChunks++
                    logD("zTXt key=$key valueLen=${value.length}")
                    when (key) {
                        "chara" -> {
                            charaB64 = value
                            charaChunk = "zTXt"
                        }
                        "ccv3" -> {
                            ccv3B64 = value
                            ccv3Chunk = "zTXt"
                        }
                    }
                }
                "iTXt" -> parseItxtChunk(bytes, dataStart, length)?.let { (key, value) ->
                    textChunks++
                    logD("iTXt key=$key valueLen=${value.length}")
                    when (key) {
                        "chara" -> {
                            charaB64 = value
                            charaChunk = "iTXt"
                        }
                        "ccv3" -> {
                            ccv3B64 = value
                            ccv3Chunk = "iTXt"
                        }
                    }
                }
                "IEND" -> break
            }
            offset = dataEnd + 4 // skip CRC
        }
        val preferCcv3 = ccv3B64 != null
        val b64 = ccv3B64 ?: charaB64
        if (b64 == null) {
            logE("no chara/ccv3 chunk; textChunks=$textChunks")
            error("No SillyTavern chara/ccv3 chunk in PNG")
        }
        val usedKey = if (preferCcv3) "ccv3" else "chara"
        val usedChunk = if (preferCcv3) ccv3Chunk else charaChunk
        logI("found $usedKey in $usedChunk, b64Len=${b64.length} (alsoChara=${charaB64 != null}, alsoCcv3=${ccv3B64 != null})")
        val json = decodeBase64Utf8(b64)
        logI("decoded jsonLen=${json.length} head=${json.take(80).replace('\n', ' ')}")
        return json
    }

    /** JVM Base64 so unit tests work without Android runtime. */
    internal fun decodeBase64Utf8(b64: String): String {
        val cleaned = b64.trim().replace("\n", "").replace("\r", "")
        val decoded = Base64.getDecoder().decode(cleaned)
        return String(decoded, Charsets.UTF_8)
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int

    private fun parseTextChunk(bytes: ByteArray, start: Int, length: Int): Pair<String, String>? {
        if (length <= 1) return null
        val nullIdx = (start until start + length).firstOrNull { bytes[it] == 0.toByte() } ?: return null
        val key = String(bytes, start, nullIdx - start, Charsets.ISO_8859_1)
        val value = String(bytes, nullIdx + 1, start + length - nullIdx - 1, Charsets.ISO_8859_1)
        return key to value
    }

    private fun parseZtxtChunk(bytes: ByteArray, start: Int, length: Int): Pair<String, String>? {
        if (length <= 2) return null
        val nullIdx = (start until start + length).firstOrNull { bytes[it] == 0.toByte() } ?: return null
        val key = String(bytes, start, nullIdx - start, Charsets.ISO_8859_1)
        // compression method byte at nullIdx+1, then zlib data
        val dataStart = nullIdx + 2
        if (dataStart >= start + length) return null
        val compressed = bytes.copyOfRange(dataStart, start + length)
        val value = inflate(compressed)?.toString(Charsets.ISO_8859_1) ?: return null
        return key to value
    }

    private fun parseItxtChunk(bytes: ByteArray, start: Int, length: Int): Pair<String, String>? {
        if (length <= 5) return null
        val end = start + length
        var p = start
        fun readCString(): String? {
            val n = (p until end).firstOrNull { bytes[it] == 0.toByte() } ?: return null
            val s = String(bytes, p, n - p, Charsets.ISO_8859_1)
            p = n + 1
            return s
        }
        val key = readCString() ?: return null
        if (p + 2 > end) return null
        val compressionFlag = bytes[p].toInt() and 0xff
        p += 2 // flag + method
        readCString() // language tag
        // translated keyword: UTF-8 null-terminated
        val n = (p until end).firstOrNull { bytes[it] == 0.toByte() } ?: return null
        p = n + 1
        val textBytes = bytes.copyOfRange(p, end)
        val text = if (compressionFlag == 1) {
            inflate(textBytes)?.toString(Charsets.UTF_8) ?: return null
        } else {
            textBytes.toString(Charsets.UTF_8)
        }
        return key to text
    }

    private fun inflate(data: ByteArray): ByteArray? = runCatching {
        InflaterInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
    }.getOrNull()
}
