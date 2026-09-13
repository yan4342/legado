package io.legado.app.domain.usecase.structured

/**
 * Canonical schema for character relationship memory tables.
 * Enforced on create/generate — not prompt-only.
 */
object MemoryTableRelationSchema {

    const val KIND = "relation"

    val STANDARD_COLUMNS: List<String> = listOf("角色A", "角色B", "关系")

    const val DEFAULT_NAME = "人物关系"

    fun isRelationKind(kind: String?): Boolean {
        if (kind.isNullOrBlank()) return false
        val k = kind.trim().lowercase()
        return k == KIND ||
            k == "关系" ||
            k == "社交" ||
            k.contains("relation") ||
            k.contains("relationship") ||
            k.contains("social")
    }

    fun shouldNormalizeAsRelation(tableKind: String?, name: String, columns: List<String>): Boolean {
        if (isRelationKind(tableKind)) return true
        if (name.contains("关系") || name.contains("社交")) return true
        return MemoryTableRowMatcher.detectRelationshipPairKeys(name, columns) != null
    }

    fun ensureRelationTableName(name: String): String {
        val trimmed = name.trim().ifBlank { DEFAULT_NAME }
        return if (trimmed.contains("关系")) trimmed else DEFAULT_NAME
    }

    fun normalizeRow(row: Map<String, Any?>, sourceColumns: List<String>): Map<String, String> {
        fun pickValue(keys: List<String>): String {
            for (key in keys) {
                MemoryTableRowMatcher.resolveColumnKey(sourceColumns, key)?.let { resolved ->
                    row[resolved]?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
                }
                row[key]?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
            }
            for ((k, v) in row) {
                if (keys.any { alias -> k == alias || k.contains(alias) }) {
                    v?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
                }
            }
            return ""
        }
        return buildMap {
            put("角色A", pickValue(listOf("角色A", "角色甲", "人物A", "角色1", "一方")))
            put("角色B", pickValue(listOf("角色B", "角色乙", "人物B", "角色2", "另一方")))
            put("关系", pickValue(listOf("关系", "关系类型", "relation", "relationType", "type")))
        }.filterValues { it.isNotBlank() }
    }

    data class NormalizedTable(
        val name: String,
        val columns: List<String>,
        val rows: List<Map<String, String>>,
    )

    fun normalizeTable(
        name: String,
        columns: List<String>,
        rows: List<Map<String, Any?>>,
    ): NormalizedTable = NormalizedTable(
        name = ensureRelationTableName(name),
        columns = STANDARD_COLUMNS,
        rows = rows.map { normalizeRow(it, columns) }.filter { it.isNotEmpty() },
    )
}
