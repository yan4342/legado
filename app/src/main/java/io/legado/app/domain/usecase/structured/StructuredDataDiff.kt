package io.legado.app.domain.usecase.structured

data class FieldChange(
    val path: String,
    val oldValue: String,
    val newValue: String,
    val checked: Boolean = true,
)

object StructuredDataDiff {

    private const val MAX_DIFF_LINES = 80

    fun diffMaps(old: Map<String, Any?>, new: Map<String, Any?>): List<FieldChange> {
        val keys = (old.keys + new.keys).toSet()
        return keys.mapNotNull { key ->
            val oldStr = old[key]?.toString().orEmpty()
            val newStr = new[key]?.toString().orEmpty()
            if (oldStr != newStr) FieldChange(key, oldStr, newStr) else null
        }
    }

    fun diffFields(old: Map<String, String>, new: Map<String, String>): List<FieldChange> {
        val keys = (old.keys + new.keys).toSet()
        return keys.mapNotNull { key ->
            val oldStr = old[key].orEmpty()
            val newStr = new[key].orEmpty()
            if (oldStr != newStr) FieldChange(key, oldStr, newStr) else null
        }
    }

    fun diffText(old: String, new: String, path: String = "content"): List<FieldChange> {
        if (old == new) return emptyList()
        return listOf(FieldChange(path, old, new))
    }

    fun diffTextLines(old: String, new: String, pathPrefix: String = "outline"): List<FieldChange> {
        if (old == new) return emptyList()
        val oldLines = old.lines()
        val newLines = new.lines()
        val changes = mutableListOf<FieldChange>()
        val maxLen = maxOf(oldLines.size, newLines.size)
        var diffCount = 0
        for (i in 0 until maxLen) {
            val oldLine = oldLines.getOrNull(i).orEmpty()
            val newLine = newLines.getOrNull(i).orEmpty()
            if (oldLine != newLine) {
                diffCount++
                if (diffCount <= MAX_DIFF_LINES) {
                    changes.add(FieldChange("$pathPrefix:L${i + 1}", oldLine, newLine))
                }
            }
        }
        val extra = diffCount - MAX_DIFF_LINES
        if (extra > 0) {
            changes.add(FieldChange("$pathPrefix:...", "", "... +$extra more changed lines"))
        }
        return changes
    }

    /** Diff only lines around the first [search] match, ±[contextLines]. */
    fun diffSearchReplaceContext(
        old: String,
        search: String,
        replace: String,
        replaceAll: Boolean,
        pathPrefix: String = "outline",
        contextLines: Int = 3,
    ): List<FieldChange> {
        if (search.isBlank()) return diffTextLines(old, old, pathPrefix)
        val newContent = if (replaceAll) old.replace(search, replace) else old.replaceFirst(search, replace)
        if (old == newContent) {
            return listOf(FieldChange("$pathPrefix:search", search.take(200), "(no match found)"))
        }
        val oldLines = old.lines()
        val newLines = newContent.lines()
        val matchLine = oldLines.indexOfFirst { it.contains(search) }.takeIf { it >= 0 }
            ?: return diffTextLines(old, newContent, pathPrefix)
        val start = (matchLine - contextLines).coerceAtLeast(0)
        val end = (matchLine + contextLines).coerceAtMost(maxOf(oldLines.lastIndex, newLines.lastIndex))
        val changes = mutableListOf<FieldChange>()
        for (i in start..end) {
            val oldLine = oldLines.getOrNull(i).orEmpty()
            val newLine = newLines.getOrNull(i).orEmpty()
            if (oldLine != newLine) {
                changes.add(FieldChange("$pathPrefix:L${i + 1}", oldLine, newLine))
            }
        }
        return changes.ifEmpty { diffTextLines(old, newContent, pathPrefix) }
    }

    fun formatDiffForPreview(changes: List<FieldChange>): String {
        if (changes.isEmpty()) return ""
        return changes.joinToString("\n") { change ->
            buildString {
                append(change.path)
                append(":\n")
                if (change.oldValue.isNotBlank()) {
                    append("  - ")
                    append(change.oldValue.take(200))
                    append('\n')
                }
                if (change.newValue.isNotBlank()) {
                    append("  + ")
                    append(change.newValue.take(200))
                }
            }
        }
    }

    fun deepMerge(base: Map<String, Any?>, patch: Map<String, Any?>): Map<String, Any?> {
        val result = base.toMutableMap()
        patch.forEach { (key, value) ->
            if (value != null) result[key] = value
        }
        return result
    }
}
