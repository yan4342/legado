package io.legado.app.help.ai

import android.content.Context
import io.legado.app.help.config.AppConfig
import splitties.init.appCtx
import java.io.File

/**
 * Spills oversized plain-text tool results to app-private storage so the model only
 * receives a bounded head/tail preview + a locator. Mirrors DSH's spill-policy:
 * keep the in-context payload small while retaining full results on disk, and let the
 * model fetch the full spill back on demand. Never used on `read_file`-style tools
 * (avoid read→spill→read loops) — enforced by callers.
 */
object ToolResultSpillStore {

    private const val DIR_NAME = "ai_tool_spill"
    private const val HEAD_CHARS = 6_000
    private const val TAIL_CHARS = 1_200

    /** Directory holding spill files; created on demand. */
    fun spillDir(context: Context = appCtx): File =
        File(context.filesDir, DIR_NAME).also { it.mkdirs() }

    /**
     * If [content] is longer than [AppConfig.aiToolSpillThreshold], persist the full
     * text under [key] and return a bounded preview carrying the spill locator; otherwise
     * returns [content] unchanged.
     */
    @JvmStatic
    fun maybeSpill(
        content: String,
        key: String,
        context: Context = appCtx,
    ): String {
        val threshold = AppConfig.aiToolSpillThreshold
        if (content.length <= threshold) return content
        val safeKey = sanitizeKey(key)
        val file = File(spillDir(context), "$safeKey.txt")
        runCatching { file.writeText(content) }
            .onFailure {
                // Persistence failed — degrade gracefully to in-context truncation.
                return content
            }
        val head = content.take(HEAD_CHARS)
        val tail = content.takeLast(TAIL_CHARS)
        return buildString {
            append(head)
            append("\n…[spilled to local spill file ")
            append(file.absolutePath)
            append("; total ")
            append(content.length)
            append(" chars; tail…]…\n")
            append(tail)
        }
    }

    /** Idempotent cleanup: delete all spill files matching a key prefix/cohort. */
    @JvmStatic
    fun prune(prefix: String = "", context: Context = appCtx, maxFiles: Int = 200) {
        val dir = spillDir(context)
        val files = dir.listFiles { _, name ->
            prefix.isBlank() || name.startsWith(prefix)
        }.orEmpty()
        // Drop oldest first to bound disk usage.
        files.sortedBy { it.lastModified() }
            .take((files.size - maxFiles).coerceAtLeast(0))
            .forEach { runCatching { it.delete() } }
    }

    private fun sanitizeKey(key: String): String =
        key.replace(Regex("[^A-Za-z0-9_\\-]"), "_").take(120)
}
