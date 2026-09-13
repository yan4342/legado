package io.legado.app.domain.usecase.structured

import io.legado.app.data.entities.AiMemoryTableRow

/**
 * Fuzzy row matching for memory tables. AI-maintained match criteria are often
 * slightly off (whitespace, nicknames, column aliases); exact equality fails too often.
 */
internal object MemoryTableRowMatcher {

    private val columnAliasGroups = listOf(
        setOf("角色名", "姓名", "名字", "名称", "人物"),
        setOf("角色A", "角色甲", "人物A", "角色1", "一方"),
        setOf("角色B", "角色乙", "人物B", "角色2", "另一方"),
        setOf("时间点", "时间", "章节", "日期"),
        setOf("事件", "情节", "摘要", "内容"),
    )

    fun normalizeValue(value: Any?): String =
        value?.toString()?.trim()?.replace(Regex("\\s+"), " ") ?: ""

    fun hasMeaningfulValue(value: Any?): Boolean = normalizeValue(value).isNotBlank()

    fun valuesMatch(stored: Any?, expected: Any?): Boolean {
        val a = normalizeValue(stored)
        val b = normalizeValue(expected)
        if (a.isEmpty() && b.isEmpty()) return true
        if (a.isEmpty() || b.isEmpty()) return false
        if (a.equals(b, ignoreCase = true)) return true
        if (a.length >= 2 && b.length >= 2 && (a.contains(b) || b.contains(a))) return true
        return false
    }

    fun resolveColumnKey(columns: Collection<String>, key: String): String? {
        val trimmed = key.trim()
        if (trimmed in columns) return trimmed
        columns.find { it.trim() == trimmed }?.let { return it }
        columnAliasGroups.forEach { group ->
            if (trimmed !in group) return@forEach
            columns.find { it in group }?.let { return it }
        }
        return columns.find { col ->
            col.contains(trimmed) || trimmed.contains(col)
        }
    }

    fun detectRelationshipPairKeys(tableName: String, columns: List<String>): Pair<String, String>? {
        fun find(candidates: List<String>): String? =
            columns.firstOrNull { col -> candidates.any { it == col } }
        val a = find(listOf("角色A", "角色甲", "人物A", "角色1", "一方"))
        val b = find(listOf("角色B", "角色乙", "人物B", "角色2", "另一方"))
        if (a != null && b != null) return a to b
        val looksLikeRelation = tableName.contains("关系") || tableName.contains("社交") ||
            tableName.contains("social", ignoreCase = true)
        if (looksLikeRelation) {
            val roleCols = columns.filter { it.startsWith("角色") && it != "角色名" }
            if (roleCols.size >= 2) return roleCols[0] to roleCols[1]
        }
        return null
    }

    fun sameUnorderedPair(leftA: Any?, leftB: Any?, rightA: String, rightB: String): Boolean {
        val a = normalizeValue(leftA)
        val b = normalizeValue(leftB)
        val ra = normalizeValue(rightA)
        val rb = normalizeValue(rightB)
        if (a.isEmpty() || b.isEmpty() || ra.isEmpty() || rb.isEmpty()) return false
        return (valuesMatch(a, ra) && valuesMatch(b, rb)) ||
            (valuesMatch(a, rb) && valuesMatch(b, ra))
    }

    fun rowMatches(
        data: Map<String, Any?>,
        match: Map<String, Any?>,
        columns: List<String>,
        tableName: String,
    ): Boolean {
        if (match.isEmpty()) return false
        val pairKeys = detectRelationshipPairKeys(tableName, columns)
        if (pairKeys != null) {
            val (keyA, keyB) = pairKeys
            val matchA = match.entries.firstOrNull { resolveColumnKey(columns, it.key) == keyA }?.value
            val matchB = match.entries.firstOrNull { resolveColumnKey(columns, it.key) == keyB }?.value
            if (matchA != null && matchB != null) {
                if (!sameUnorderedPair(data[keyA], data[keyB], matchA.toString(), matchB.toString())) {
                    return false
                }
                val otherKeys = match.keys.filter { key ->
                    val resolved = resolveColumnKey(columns, key)
                    resolved != keyA && resolved != keyB
                }
                return otherKeys.all { key ->
                    val col = resolveColumnKey(data.keys, key) ?: resolveColumnKey(columns, key) ?: key
                    valuesMatch(data[col], match[key])
                }
            }
        }
        return match.all { (key, value) ->
            val col = resolveColumnKey(data.keys, key) ?: resolveColumnKey(columns, key) ?: key
            valuesMatch(data[col], value)
        }
    }

    fun findMatchingRows(
        rows: List<AiMemoryTableRow>,
        match: Map<String, Any?>,
        columns: List<String>,
        tableName: String,
        parseRowData: (String) -> Map<String, Any?>,
    ): List<AiMemoryTableRow> = rows.filter { row ->
        rowMatches(parseRowData(row.rowData), match, columns, tableName)
    }

    fun filterMeaningfulData(data: Map<String, Any?>): Map<String, Any?> =
        data.filterValues { hasMeaningfulValue(it) }
}
