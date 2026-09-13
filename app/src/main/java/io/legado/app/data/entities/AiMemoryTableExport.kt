package io.legado.app.data.entities

/**
 * Lightweight export/import container for memory tables and their rows.
 */
data class AiMemoryTableExport(
    val version: Int = 1,
    val conversationId: String,
    val tables: List<TableWithRows>
) {
    data class TableWithRows(
        val table: AiMemoryTable,
        val rows: List<AiMemoryTableRow>
    )
}
