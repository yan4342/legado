package io.legado.app.domain.usecase.structured

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.data.entities.AiMemoryTable
import io.legado.app.data.entities.AiMemoryTableRow
import io.legado.app.data.entities.AiPromptTemplate
import io.legado.app.data.entities.AiToolConfig
import io.legado.app.domain.gateway.AiChatGateway
import io.legado.app.domain.gateway.AiMemoryTableGateway
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiPromptTemplateGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.AiToolConfigGateway
import io.legado.app.domain.model.AiCallSource
import io.legado.app.domain.model.MemoryTableOp
import io.legado.app.domain.usecase.ai.AiStreamPartialCallback
import io.legado.app.domain.usecase.ai.AiStreamingTextHelper
import io.legado.app.utils.GSON
import java.util.UUID

class MemoryTableMutator(
    private val gateway: AiMemoryTableGateway,
    private val aiChatGateway: AiChatGateway,
    private val aiProfileGateway: AiProfileGateway,
    private val aiTextGateway: AiTextGateway,
    private val promptTemplateGateway: AiPromptTemplateGateway,
    private val toolConfigGateway: AiToolConfigGateway,
    private val structuredDataPreviewer: StructuredDataPreviewer,
) {
    companion object {
        private const val TAG = "MemoryTableMutator"
    }

    data class ApplyResult(
        val success: Boolean,
        val message: String,
        val changes: List<FieldChange> = emptyList(),
        val data: Map<String, Any?> = emptyMap(),
    )

    suspend fun listTables(conversationId: String?): String {
        if (conversationId.isNullOrBlank()) {
            return GSON.toJson(
                mapOf(
                    "tables" to emptyList<Any>(),
                    "hint" to "Provide conversationId to list memory tables for a session",
                )
            )
        }
        val tables = gateway.getTablesForConversation(conversationId).filter { it.enabled }
        return GSON.toJson(
            mapOf("tables" to tables.map { table ->
                val columns = parseColumns(table.columns)
                val rowCount = gateway.getRows(table.id).size
                mapOf(
                    "id" to table.id,
                    "name" to table.name,
                    "columns" to columns,
                    "rowCount" to rowCount,
                    "bookUrl" to table.bookUrl,
                    "bookName" to table.bookName,
                    "bookAuthor" to table.bookAuthor,
                )
            })
        )
    }

    suspend fun getTable(
        tableId: String,
        conversationId: String?,
        offset: Int = 0,
        limit: Int = 0,
        fields: List<String>? = null,
        rowIds: List<String>? = null,
    ): String {
        val table = gateway.getTable(tableId) ?: return """{"error":"History memory table not found: $tableId"}"""
        if (table.conversationId.isBlank()) {
            return """{"error":"History memory table is not bound to a conversation: $tableId"}"""
        }
        if (!conversationId.isNullOrBlank() && table.conversationId != conversationId) {
            return """{"error":"History memory table not accessible in this conversation: $tableId"}"""
        }
        val allRows = gateway.getRows(tableId)
        val columns = parseColumns(table.columns)
        val projectedColumns = fields?.filter { it in columns }?.takeIf { it.isNotEmpty() } ?: columns
        val filteredRows = rowIds?.takeIf { it.isNotEmpty() }?.let { ids ->
            val idSet = ids.toSet()
            allRows.filter { it.id in idSet }
        } ?: allRows
        val total = filteredRows.size
        val pageLimit = if (limit > 0) limit.coerceIn(1, 80) else total
        val safeOffset = offset.coerceIn(0, total)
        val pageRows = if (rowIds.isNullOrEmpty() && limit > 0) {
            filteredRows.drop(safeOffset).take(pageLimit)
        } else if (rowIds.isNullOrEmpty()) {
            filteredRows
        } else {
            filteredRows.drop(safeOffset).take(if (limit > 0) pageLimit else filteredRows.size)
        }
        val hasMore = if (rowIds.isNullOrEmpty() && limit > 0) {
            safeOffset + pageRows.size < total
        } else {
            false
        }
        val rowMaps = pageRows.map { row ->
            val data = parseRowData(row.rowData)
            val projected = projectedColumns.associateWith { col -> data[col]?.toString().orEmpty() }
            mapOf("id" to row.id, "rowData" to projected)
        }
        val result = mutableMapOf<String, Any?>(
            "id" to table.id,
            "name" to table.name,
            "columns" to projectedColumns,
            "bookUrl" to table.bookUrl,
            "bookName" to table.bookName,
            "bookAuthor" to table.bookAuthor,
            "rows" to rowMaps,
            "total" to total,
            "offset" to safeOffset,
            "limit" to if (limit > 0) pageLimit else total,
            "hasMore" to hasMore,
        )
        if (hasMore) {
            result["nextArgs"] = mapOf(
                "tableId" to tableId,
                "conversationId" to conversationId,
                "offset" to (safeOffset + pageRows.size),
                "limit" to pageLimit,
            )
        }
        return GSON.toJson(result)
    }

    suspend fun previewOperations(ops: List<MemoryTableOp>, conversationId: String? = null): List<MutationOpPreview> {
        return ops.mapIndexed { index, operation ->
            val fieldChanges = previewSingleOp(operation)
            val tier = StructuredDataContextParser.tierForMemoryOp(operation)
            MutationOpPreview(
                opLabel = opLabel(operation),
                fieldChanges = fieldChanges,
                deepLink = StructuredDataDeepLinkResolver.resolveForMemoryOp(operation, conversationId),
                tier = tier,
                opIndex = index,
            )
        }
    }

    suspend fun previewOperationChanges(ops: List<MemoryTableOp>): List<FieldChange> {
        return previewOperations(ops).flatMap { it.fieldChanges }
    }

    private suspend fun previewSingleOp(operation: MemoryTableOp): List<FieldChange> {
        val changes = mutableListOf<FieldChange>()
        when (operation) {
            is MemoryTableOp.PatchRow -> {
                val row = resolveRow(operation.tableId, operation.rowId, operation.match) ?: return emptyList()
                val oldData = parseRowData(row.rowData)
                val merged = StructuredDataDiff.deepMerge(oldData, operation.data)
                changes.addAll(
                    StructuredDataDiff.diffMaps(oldData, merged).map {
                        it.copy(path = "${operation.tableId}/${it.path}")
                    }
                )
            }
            is MemoryTableOp.AddRow -> {
                operation.data.forEach { (k, v) ->
                    changes.add(FieldChange("${operation.tableId}/$k", "", v?.toString().orEmpty()))
                }
            }
            is MemoryTableOp.DeleteRow -> {
                val row = resolveRow(
                    operation.tableId.orEmpty(),
                    operation.rowId,
                    operation.match
                )
                if (row != null) {
                    parseRowData(row.rowData).forEach { (k, v) ->
                        changes.add(FieldChange("${row.tableId}/$k", v?.toString().orEmpty(), ""))
                    }
                }
            }
            is MemoryTableOp.UpdateSchema -> {
                val table = gateway.getTable(operation.tableId) ?: return emptyList()
                operation.name?.takeIf { it != table.name }?.let {
                    changes.add(FieldChange("${operation.tableId}/name", table.name, it))
                }
                operation.columns?.let { cols ->
                    val oldCols = parseColumns(table.columns).joinToString(", ")
                    val newCols = cols.joinToString(", ")
                    if (oldCols != newCols) {
                        changes.add(FieldChange("${operation.tableId}/columns", oldCols, newCols))
                    }
                }
                operation.bookUrl?.takeIf { it != table.bookUrl }?.let {
                    changes.add(FieldChange("${operation.tableId}/bookUrl", table.bookUrl, it))
                }
                operation.bookName?.takeIf { it != table.bookName }?.let {
                    changes.add(FieldChange("${operation.tableId}/bookName", table.bookName, it))
                }
                operation.bookAuthor?.takeIf { it != table.bookAuthor }?.let {
                    changes.add(FieldChange("${operation.tableId}/bookAuthor", table.bookAuthor, it))
                }
            }
            is MemoryTableOp.CreateTable -> {
                changes.add(FieldChange("table/${operation.name}", "", "new table"))
                if (operation.bookUrl.isNotBlank()) {
                    changes.add(FieldChange("table/${operation.name}/bookUrl", "", operation.bookUrl))
                }
            }
            is MemoryTableOp.GenerateTables -> {
                changes.add(FieldChange("ai_generate", "", "Content will be generated by AI"))
            }
            is MemoryTableOp.RegenerateTable -> {
                changes.addAll(structuredDataPreviewer.previewRegenerateTable(operation))
            }
        }
        return changes
    }

    private fun opLabel(operation: MemoryTableOp): String = when (operation) {
        is MemoryTableOp.PatchRow -> "patch_row ${operation.tableId}"
        is MemoryTableOp.AddRow -> "add_row ${operation.tableId}"
        is MemoryTableOp.DeleteRow -> "delete_row ${operation.tableId ?: operation.rowId.orEmpty()}"
        is MemoryTableOp.UpdateSchema -> "update_schema ${operation.tableId}"
        is MemoryTableOp.CreateTable -> "create_table ${operation.name}"
        is MemoryTableOp.GenerateTables -> when {
            operation.isSingleKind -> "generate_tables ${operation.tableKind}"
            else -> "generate_tables"
        }
        is MemoryTableOp.RegenerateTable -> "regenerate_table ${operation.tableId}"
    }

    suspend fun validateRowTarget(
        tableId: String,
        rowId: String?,
        match: Map<String, Any?>?,
    ): List<ValidationIssue> {
        if (!rowId.isNullOrBlank()) {
            val row = gateway.getRow(rowId)
            if (row == null) {
                return listOf(ValidationIssue.Error("Row not found: $rowId"))
            }
            if (row.tableId != tableId) {
                return listOf(ValidationIssue.Error("rowId $rowId does not belong to table $tableId"))
            }
            return emptyList()
        }
        if (match.isNullOrEmpty()) return emptyList()
        val table = gateway.getTable(tableId)
        val columns = parseColumns(table?.columns.orEmpty())
        val matches = findRowsByMatch(tableId, match, columns, table?.name.orEmpty())
        return when {
            matches.isEmpty() -> listOf(
                ValidationIssue.Error(
                    "No row matches $match in table $tableId",
                    "Prefer rowId from the table snapshot; match only primary key columns (角色名 / 角色A+角色B / 时间点)",
                )
            )
            matches.size > 1 -> listOf(
                ValidationIssue.Error(
                    "Multiple rows (${matches.size}) match $match in table $tableId",
                    "Add more match criteria or use rowId",
                )
            )
            else -> emptyList()
        }
    }

    suspend fun applyOperations(
        ops: List<MemoryTableOp>,
        toolCallId: String? = null,
        dryRun: Boolean = false,
    ): ApplyResult {
        if (dryRun) {
            return applyOperationsLoop(ops, toolCallId, dryRun = true)
        }

        val allChanges = mutableListOf<FieldChange>()
        val results = mutableListOf<String>()
        val dbBuffer = mutableListOf<Pair<Int, MemoryTableOp>>()

        suspend fun flushDbBuffer(): ApplyResult? {
            if (dbBuffer.isEmpty()) return null
            val buffer = dbBuffer.toList()
            dbBuffer.clear()
            return try {
                gateway.withTransaction {
                    for ((opIndex, operation) in buffer) {
                        val result = applySingleOperation(operation, toolCallId, opIndex, dryRun = false)
                        if (!result.success) throw ApplyOperationException(result)
                        allChanges.addAll(result.changes)
                        results.add(result.message)
                    }
                }
                null
            } catch (e: ApplyOperationException) {
                e.result
            }
        }

        for ((opIndex, operation) in ops.withIndex()) {
            if (isAiOperation(operation)) {
                flushDbBuffer()?.let { return it }
                val result = applySingleOperation(operation, toolCallId, opIndex, dryRun = false)
                if (!result.success) return result
                allChanges.addAll(result.changes)
                results.add(result.message)
            } else {
                dbBuffer.add(opIndex to operation)
            }
        }
        flushDbBuffer()?.let { return it }

        return ApplyResult(
            success = true,
            message = results.joinToString("; ").ifBlank { "OK" },
            changes = allChanges,
        )
    }

    private suspend fun applyOperationsLoop(
        ops: List<MemoryTableOp>,
        toolCallId: String?,
        dryRun: Boolean,
    ): ApplyResult {
        val allChanges = mutableListOf<FieldChange>()
        val results = mutableListOf<String>()
        for ((opIndex, operation) in ops.withIndex()) {
            val result = applySingleOperation(operation, toolCallId, opIndex, dryRun)
            if (!result.success) return result
            allChanges.addAll(result.changes)
            results.add(result.message)
        }
        return ApplyResult(
            success = true,
            message = results.joinToString("; ").ifBlank { "OK" },
            changes = allChanges,
        )
    }

    private suspend fun applySingleOperation(
        operation: MemoryTableOp,
        toolCallId: String?,
        opIndex: Int,
        dryRun: Boolean,
    ): ApplyResult = when (operation) {
        is MemoryTableOp.CreateTable -> createTable(operation, dryRun)
        is MemoryTableOp.UpdateSchema -> updateSchema(operation, dryRun)
        is MemoryTableOp.AddRow -> addRow(operation, toolCallId, opIndex, dryRun)
        is MemoryTableOp.PatchRow -> patchRow(operation, dryRun)
        is MemoryTableOp.DeleteRow -> deleteRow(operation, dryRun)
        is MemoryTableOp.GenerateTables -> if (dryRun) {
            ApplyResult(
                true,
                "Would generate memory table(s)",
                listOf(FieldChange("ai_generate", "", "Content will be generated by AI")),
            )
        } else {
            generateTables(operation)
        }
        is MemoryTableOp.RegenerateTable -> {
            if (dryRun) {
                ApplyResult(
                    true,
                    "Would regenerate/repair table ${operation.tableId}",
                    listOf(
                        FieldChange(
                            "${operation.tableId}/regenerate",
                            "current rows",
                            "AI repair/dedupe",
                        )
                    ),
                )
            } else {
                regenerateTable(operation.tableId, operation.conversationId, operation.hint)
            }
        }
    }

    private fun isAiOperation(operation: MemoryTableOp): Boolean =
        operation is MemoryTableOp.GenerateTables ||
            operation is MemoryTableOp.RegenerateTable

    private class ApplyOperationException(val result: ApplyResult) : Exception()

    suspend fun patchRow(
        tableId: String,
        rowId: String?,
        match: Map<String, Any?>?,
        patchData: Map<String, Any?>,
        dryRun: Boolean = false,
    ): ApplyResult {
        return patchRow(MemoryTableOp.PatchRow(tableId, rowId, match, patchData), dryRun)
    }

    suspend fun addRow(
        tableId: String,
        data: Map<String, Any?>,
        toolCallId: String? = null,
        dryRun: Boolean = false,
    ): ApplyResult {
        return addRow(MemoryTableOp.AddRow(tableId, data), toolCallId, opIndex = 0, dryRun)
    }

    suspend fun regenerateTable(
        tableId: String,
        conversationId: String?,
        hint: String?,
        onPartial: AiStreamPartialCallback? = null,
    ): ApplyResult {
        val table = gateway.getTable(tableId) ?: return ApplyResult(false, "Table not found: $tableId")
        val rows = gateway.getRows(tableId)
        val chatMessages = if (!conversationId.isNullOrBlank()) {
            aiChatGateway.getMessagesForRegeneration(conversationId)
        } else emptyList()

        val repairRules = when (tableId) {
            "memtable_timeline" -> "去重规则：同一'时间点'+'事件'有多条时，保留最新那条。补全规则：如果某些行的列有空缺，根据上下文合理推断补全。"
            "memtable_character" -> "去重规则：同一'角色名'有多条时，合并所有行，每列取各行的非空值拼接（用；分隔）。补全规则：如果某角色缺少某些列（外貌/性格/能力/背景），根据已有信息合理推断补全。"
            "memtable_social" -> {
                "去重规则：同一对角色（角色A+角色B，顺序无关）只保留一行；" +
                    "关系列取最新非空短词组（如师徒、仇敌），互动摘要/备注合并去重。补全规则：空缺列根据上下文合理推断。"
            }
            else -> {
                if (table.name.contains("关系") || table.name.contains("社交")) {
                    "去重规则：同一对角色（角色A+角色B，顺序无关）只保留一行；" +
                        "关系列取最新短词组（如师徒、仇敌），互动摘要合并。补全空缺列。"
                } else {
                    "去重规则：合并所有行，去重并补全空缺。"
                }
            }
        }
        val columns = parseColumns(table.columns)
        val allRowsText = rows.map { row ->
            val data = parseRowData(row.rowData)
            columns.joinToString(" | ") { col -> data[col]?.toString().orEmpty() }
        }.joinToString("\n")
        val chatMessagesText = chatMessages.joinToString("\n\n") { msg ->
            val role = if (msg.first == "user") "用户" else "助手"
            "[$role] ${msg.second.take(2000)}"
        }
        val prompt = promptTemplateGateway.getPrompt(AiPromptTemplate.REGENERATE_MEMORY_TABLE_PROMPT)
        val system = io.legado.app.domain.usecase.ai.PromptRoleSplit.stripPlaceholders(prompt)
        val user = buildString {
            append("表名：").append(table.name).append('\n')
            append("列：").append(columns.joinToString("、")).append('\n')
            append("规则：").append(repairRules).append('\n')
            append("当前数据：\n").append(columns.joinToString(" | ")).append('\n').append(allRowsText)
            append("\n\n未入表对话：\n").append(chatMessagesText)
            if (!hint.isNullOrBlank()) append("\n\n## Extra Guidance\n").append(hint)
        }

        val config = toolConfigGateway.getByToolName("patch_history_memory")
        val result = generateWithConfig(system, user, config, onPartial)
        return result.fold(
            onSuccess = { text ->
                val cleanedRows = parseRegeneratedRows(text, tableId, columns)
                if (cleanedRows.isEmpty()) {
                    ApplyResult(false, "Failed to parse regenerated rows")
                } else {
                    gateway.replaceAllRows(tableId, cleanedRows)
                    ApplyResult(
                        success = true,
                        message = "Table '${table.name}' regenerated with ${cleanedRows.size} rows",
                        data = mapOf("rowCount" to cleanedRows.size),
                    )
                }
            },
            onFailure = { ApplyResult(false, "Regeneration failed: ${it.message}") }
        )
    }

    private suspend fun createTable(op: MemoryTableOp.CreateTable, dryRun: Boolean): ApplyResult {
        if (op.conversationId.isBlank()) {
            return ApplyResult(false, "conversationId is required to create a memory table")
        }
        val (tableName, tableColumns, tableRows) = resolveTableSchema(
            tableKind = op.tableKind,
            name = op.name,
            columns = op.columns,
            rows = op.rows,
        )
        if (tableName.isBlank()) return ApplyResult(false, "Table name is required")
        if (dryRun) {
            return ApplyResult(true, "Would create table '$tableName'", listOf(FieldChange("table", "", tableName)))
        }
        val tableId = "memtable_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val table = AiMemoryTable(
            id = tableId,
            name = tableName,
            columns = GSON.toJson(tableColumns),
            conversationId = op.conversationId,
            bookUrl = op.bookUrl,
            bookName = op.bookName,
            bookAuthor = op.bookAuthor,
            enabled = true,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        gateway.upsertTable(table)
        tableRows.forEachIndexed { index, rowData ->
            val row = AiMemoryTableRow(
                id = "memrow_${UUID.randomUUID().toString().replace("-", "")}",
                tableId = tableId,
                rowData = GSON.toJson(rowData),
                sortOrder = index,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            gateway.upsertRow(row)
        }
        return ApplyResult(
            true,
            "Created table '$tableName'. Use tableId=$tableId for add_row/patch_row (not the display name).",
            data = mapOf("tableId" to tableId, "name" to tableName),
        )
    }

    private suspend fun updateSchema(op: MemoryTableOp.UpdateSchema, dryRun: Boolean): ApplyResult {
        val table = gateway.getTable(op.tableId) ?: return ApplyResult(false, "Table not found: ${op.tableId}")
        if (op.name == null && op.columns == null &&
            op.bookUrl == null && op.bookName == null && op.bookAuthor == null
        ) {
            return ApplyResult(false, "At least one of name, columns, or book binding fields required")
        }
        val changes = mutableListOf<FieldChange>()
        op.name?.let { if (it != table.name) changes.add(FieldChange("name", table.name, it)) }
        op.columns?.let { cols ->
            val old = parseColumns(table.columns).joinToString(", ")
            val new = cols.joinToString(", ")
            if (old != new) changes.add(FieldChange("columns", old, new))
        }
        op.bookUrl?.takeIf { it != table.bookUrl }?.let {
            changes.add(FieldChange("bookUrl", table.bookUrl, it))
        }
        op.bookName?.takeIf { it != table.bookName }?.let {
            changes.add(FieldChange("bookName", table.bookName, it))
        }
        op.bookAuthor?.takeIf { it != table.bookAuthor }?.let {
            changes.add(FieldChange("bookAuthor", table.bookAuthor, it))
        }
        if (dryRun) return ApplyResult(true, "Would update schema", changes)
        val existingColumns = parseColumns(table.columns)
        val isRelation = MemoryTableRelationSchema.shouldNormalizeAsRelation(null, table.name, existingColumns)
        val resolvedName = when {
            op.name != null && isRelation -> MemoryTableRelationSchema.ensureRelationTableName(op.name)
            else -> op.name ?: table.name
        }
        val resolvedColumns = when {
            op.columns != null && isRelation -> MemoryTableRelationSchema.STANDARD_COLUMNS
            op.columns != null -> op.columns
            else -> null
        }
        val updated = table.copy(
            name = resolvedName,
            columns = resolvedColumns?.let { GSON.toJson(it) } ?: table.columns,
            bookUrl = op.bookUrl ?: table.bookUrl,
            bookName = op.bookName ?: table.bookName,
            bookAuthor = op.bookAuthor ?: table.bookAuthor,
            updatedAt = System.currentTimeMillis(),
        )
        gateway.upsertTable(updated)
        return ApplyResult(true, "Schema updated", changes)
    }

    private suspend fun addRow(
        op: MemoryTableOp.AddRow,
        toolCallId: String?,
        opIndex: Int,
        dryRun: Boolean,
    ): ApplyResult {
        val table = gateway.getTable(op.tableId) ?: return ApplyResult(false, "Table not found: ${op.tableId}")
        val columns = parseColumns(table.columns)
        val data = MemoryTableRowMatcher.filterMeaningfulData(op.data)
        if (data.isEmpty()) return ApplyResult(false, "Row data is empty")

        // Relationship / social tables: same character pair → patch, not a new row.
        val pairKeys = MemoryTableRowMatcher.detectRelationshipPairKeys(table.name, columns)
        if (pairKeys != null) {
            val (keyA, keyB) = pairKeys
            val a = data[keyA]?.toString().orEmpty()
            val b = data[keyB]?.toString().orEmpty()
            if (a.isNotBlank() && b.isNotBlank()) {
                val existing = gateway.getRows(op.tableId).find { row ->
                    val rowData = parseRowData(row.rowData)
                    MemoryTableRowMatcher.sameUnorderedPair(rowData[keyA], rowData[keyB], a, b)
                }
                if (existing != null) {
                    // Keep existing A/B order; update other fields from incoming data.
                    val patchData = data.toMutableMap().apply {
                        remove(keyA)
                        remove(keyB)
                    }
                    return patchRow(
                        MemoryTableOp.PatchRow(
                            tableId = op.tableId,
                            rowId = existing.id,
                            match = null,
                            data = patchData,
                        ),
                        dryRun,
                    ).let { result ->
                        if (result.success) {
                            result.copy(message = "Relationship row updated (same pair in '${table.name}')")
                        } else result
                    }
                }
            }
        }

        // Character sheets: same 角色名 → patch.
        val nameKey = columns.firstOrNull { it == "角色名" || it == "姓名" }
        if (nameKey != null && pairKeys == null) {
            val name = data[nameKey]?.toString().orEmpty()
            if (name.isNotBlank()) {
                val existing = gateway.getRows(op.tableId).find { row ->
                    val rowData = parseRowData(row.rowData)
                    MemoryTableRowMatcher.valuesMatch(rowData[nameKey], name)
                }
                if (existing != null) {
                    return patchRow(
                        MemoryTableOp.PatchRow(
                            tableId = op.tableId,
                            rowId = existing.id,
                            match = null,
                            data = data,
                        ),
                        dryRun,
                    ).let { result ->
                        if (result.success) {
                            result.copy(message = "Character row updated (same name in '${table.name}')")
                        } else result
                    }
                }
            }
        }

        val rowPayload = columns.associateWith { col -> data[col]?.toString().orEmpty() }
            .filterValues { it.isNotBlank() }
        if (rowPayload.isEmpty()) return ApplyResult(false, "Row data is empty")
        val changes = rowPayload.map { (k, v) -> FieldChange(k, "", v) }
        if (dryRun) return ApplyResult(true, "Would add row", changes)

        val rowDataStr = GSON.toJson(rowPayload)
        val sourceKey = toolCallId?.let { "$it:$opIndex" }
        val existing = if (sourceKey != null) {
            gateway.getRows(op.tableId).find { it.sourceMessageId == sourceKey }
        } else null
        if (existing != null) {
            gateway.upsertRow(existing.copy(rowData = rowDataStr, updatedAt = System.currentTimeMillis()))
            return ApplyResult(true, "Row updated (dedup in '${table.name}')", changes, mapOf("rowId" to existing.id))
        }
        val row = AiMemoryTableRow(
            id = "memrow_${UUID.randomUUID().toString().replace("-", "")}",
            tableId = op.tableId,
            rowData = rowDataStr,
            sourceMessageId = sourceKey,
            sortOrder = System.currentTimeMillis().toInt(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        gateway.upsertRow(row)
        return ApplyResult(true, "Row added to '${table.name}'", changes, mapOf("rowId" to row.id))
    }

    private suspend fun patchRow(op: MemoryTableOp.PatchRow, dryRun: Boolean): ApplyResult {
        val patchData = MemoryTableRowMatcher.filterMeaningfulData(op.data)
        if (patchData.isEmpty()) return ApplyResult(false, "patch_row data is empty")
        val row = resolveRow(op.tableId, op.rowId, op.match)
            ?: return ApplyResult(false, "Row not found in table ${op.tableId}")
        val oldData = parseRowData(row.rowData)
        val merged = StructuredDataDiff.deepMerge(oldData, patchData)
        val changes = StructuredDataDiff.diffMaps(oldData, merged)
        if (dryRun) return ApplyResult(true, "Would patch row", changes)
        gateway.upsertRow(
            row.copy(rowData = GSON.toJson(merged), updatedAt = System.currentTimeMillis())
        )
        return ApplyResult(true, "Row patched", changes, mapOf("rowId" to row.id))
    }

    private suspend fun deleteRow(op: MemoryTableOp.DeleteRow, dryRun: Boolean): ApplyResult {
        val row = when {
            !op.rowId.isNullOrBlank() -> gateway.getRow(op.rowId)
            !op.tableId.isNullOrBlank() -> resolveRow(op.tableId, null, op.match)
            else -> null
        } ?: return ApplyResult(false, "Row not found")
        val changes = parseRowData(row.rowData).map { (k, v) ->
            FieldChange(k, v?.toString().orEmpty(), "")
        }
        if (dryRun) return ApplyResult(true, "Would delete row", changes)
        gateway.deleteRow(row.id)
        return ApplyResult(true, "Row deleted", changes)
    }

    suspend fun generateTablesForConversation(
        conversationId: String,
        hint: String? = null,
        onPartial: AiStreamPartialCallback? = null,
    ): ApplyResult = generateTables(
        MemoryTableOp.GenerateTables(conversationId, hint),
        onPartial,
    )

    private suspend fun generateTables(
        op: MemoryTableOp.GenerateTables,
        onPartial: AiStreamPartialCallback? = null,
    ): ApplyResult {
        if (op.conversationId.isBlank()) {
            return ApplyResult(false, "conversationId is required to generate memory tables")
        }
        val chatMessages = aiChatGateway.getMessagesForRegeneration(op.conversationId)
        if (chatMessages.isEmpty()) return ApplyResult(false, "No messages found in conversation")
        val chatMessagesText = chatMessages.joinToString("\n\n") { msg ->
            val role = if (msg.first == "user") "用户" else "助手"
            "[$role] ${msg.second.take(2000)}"
        }
        val config = toolConfigGateway.getByToolName("patch_history_memory")
        return if (op.isSingleKind) {
            generateSingleKindTable(op, chatMessagesText, config, onPartial)
        } else {
            val prompt = promptTemplateGateway.getPrompt(AiPromptTemplate.GENERATE_MEMORY_TABLE_PROMPT)
            val system = io.legado.app.domain.usecase.ai.PromptRoleSplit.stripPlaceholders(prompt)
            val user = buildString {
                append(chatMessagesText)
                if (!op.hint.isNullOrBlank()) append("\n\n## Extra Guidance\n").append(op.hint)
            }
            val result = generateWithConfig(system, user, config, onPartial)
            result.fold(
                onSuccess = { text ->
                    val generated = parseGeneratedTables(
                        text,
                        op.conversationId,
                        tableKind = null,
                        op.bookUrl,
                        op.bookName,
                        op.bookAuthor,
                    )
                    if (generated.isEmpty()) {
                        ApplyResult(false, "Failed to parse generated tables")
                    } else {
                        ApplyResult(
                            success = true,
                            message = "Generated ${generated.size} table(s)",
                            data = mapOf(
                                "tableCount" to generated.size,
                                "totalRows" to generated.sumOf { it.second.size },
                            ),
                        )
                    }
                },
                onFailure = { ApplyResult(false, "Generation failed: ${it.message}") },
            )
        }
    }

    private suspend fun generateSingleKindTable(
        op: MemoryTableOp.GenerateTables,
        chatMessagesText: String,
        config: AiToolConfig?,
        onPartial: AiStreamPartialCallback?,
    ): ApplyResult {
        val isRelation = MemoryTableRelationSchema.isRelationKind(op.tableKind)
        val purpose = when {
            isRelation -> op.name ?: MemoryTableRelationSchema.DEFAULT_NAME
            else -> op.name ?: op.tableKind.orEmpty()
        }
        val columnsHint = when {
            isRelation -> {
                "必须使用且仅使用以下列（顺序固定）：${
                    GSON.toJson(MemoryTableRelationSchema.STANDARD_COLUMNS)
                }；表名必须包含「关系」"
            }
            op.columns != null -> "指定列：${GSON.toJson(op.columns)}"
            else -> "请根据表格用途自动选择3-5个合适的列"
        }
        val prompt = promptTemplateGateway.getPrompt(AiPromptTemplate.GENERATE_ONE_KIND_TABLE_PROMPT)
        val system = io.legado.app.domain.usecase.ai.PromptRoleSplit.stripPlaceholders(prompt)
        val user = buildString {
            append("用途：").append(purpose).append('\n')
            append(columnsHint)
            if (chatMessagesText.isNotBlank()) {
                append("\n\n## 对话内容\n").append(chatMessagesText)
            }
            if (!op.hint.isNullOrBlank()) append("\n\n## Extra Guidance\n").append(op.hint)
        }
        val result = generateWithConfig(system, user, config, onPartial)
        return result.fold(
            onSuccess = { text ->
                val parsed = parseSingleTableResponse(
                    text,
                    op.conversationId,
                    tableKind = op.tableKind,
                    op.bookUrl,
                    op.bookName,
                    op.bookAuthor,
                )
                if (parsed == null) ApplyResult(false, "Failed to parse AI response")
                else ApplyResult(true, "Created table ${parsed.first}", data = parsed.second)
            },
            onFailure = { ApplyResult(false, "AI generation failed: ${it.message}") },
        )
    }

    private suspend fun resolveRow(
        tableId: String,
        rowId: String?,
        match: Map<String, Any?>?,
    ): AiMemoryTableRow? {
        if (!rowId.isNullOrBlank()) return gateway.getRow(rowId)
        if (match.isNullOrEmpty()) return null
        val table = gateway.getTable(tableId) ?: return null
        val columns = parseColumns(table.columns)
        return findRowsByMatch(tableId, match, columns, table.name).singleOrNull()
    }

    private suspend fun findRowsByMatch(
        tableId: String,
        match: Map<String, Any?>,
        columns: List<String>,
        tableName: String,
    ): List<AiMemoryTableRow> = MemoryTableRowMatcher.findMatchingRows(
        rows = gateway.getRows(tableId),
        match = match,
        columns = columns,
        tableName = tableName,
        parseRowData = ::parseRowData,
    )

    private suspend fun generateWithConfig(
        systemPrompt: String,
        userPrompt: String,
        config: AiToolConfig?,
        onPartial: AiStreamPartialCallback? = null,
    ) = AiStreamingTextHelper.generateWithToolConfig(
        aiTextGateway = aiTextGateway,
        aiProfileGateway = aiProfileGateway,
        systemPrompt = systemPrompt,
        userPrompt = userPrompt,
        config = config,
        callSource = AiCallSource.MEMORY,
        onPartial = onPartial,
    )

    private suspend fun parseGeneratedTables(
        responseText: String,
        conversationId: String,
        tableKind: String? = null,
        bookUrl: String = "",
        bookName: String = "",
        bookAuthor: String = "",
    ): List<Pair<AiMemoryTable, List<AiMemoryTableRow>>> {
        val jsonStr = extractJsonArray(responseText)
        return runCatching {
            val jsonArray = JsonParser.parseString(jsonStr).asJsonArray
            jsonArray.map { element ->
                val obj = element.asJsonObject
                val rawName = obj.get("name")?.asString ?: "Unnamed Table"
                val rawColumns = obj.getAsJsonArray("columns")?.map { it.asString } ?: emptyList()
                val rawRows = parseJsonRows(obj.getAsJsonArray("rows"), rawColumns)
                val (tableName, tableColumns, tableRows) = resolveTableSchema(tableKind, rawName, rawColumns, rawRows)
                persistTable(
                    name = tableName,
                    columns = tableColumns,
                    rows = tableRows,
                    conversationId = conversationId,
                    bookUrl = bookUrl,
                    bookName = bookName,
                    bookAuthor = bookAuthor,
                )
            }
        }.getOrElse {
            Log.e(TAG, "parseGeneratedTables error: ${it.message}")
            emptyList()
        }
    }

    private suspend fun parseSingleTableResponse(
        responseText: String,
        conversationId: String,
        tableKind: String? = null,
        bookUrl: String = "",
        bookName: String = "",
        bookAuthor: String = "",
    ): Pair<String, Map<String, Any?>>? {
        val jsonStr = extractJsonObject(responseText)
        return runCatching {
            val obj = GSON.fromJson(jsonStr, JsonObject::class.java)
            val rawName = obj.get("name")?.asString ?: return@runCatching null
            val rawColumns = obj.getAsJsonArray("columns")?.map { it.asString } ?: emptyList()
            val rawRows = parseJsonRows(obj.getAsJsonArray("rows"), rawColumns)
            val (tableName, tableColumns, tableRows) = resolveTableSchema(tableKind, rawName, rawColumns, rawRows)
            val (table, rows) = persistTable(
                name = tableName,
                columns = tableColumns,
                rows = tableRows,
                conversationId = conversationId,
                bookUrl = bookUrl,
                bookName = bookName,
                bookAuthor = bookAuthor,
            )
            tableName to mapOf("tableId" to table.id, "name" to tableName, "rowCount" to rows.size)
        }.getOrNull()
    }

    private fun resolveTableSchema(
        tableKind: String?,
        name: String,
        columns: List<String>,
        rows: List<Map<String, Any?>>,
    ): Triple<String, List<String>, List<Map<String, Any?>>> {
        if (!MemoryTableRelationSchema.shouldNormalizeAsRelation(tableKind, name, columns)) {
            return Triple(name, columns, rows)
        }
        val normalized = MemoryTableRelationSchema.normalizeTable(name, columns, rows)
        return Triple(
            normalized.name,
            normalized.columns,
            normalized.rows.map { it as Map<String, Any?> },
        )
    }

    private fun parseJsonRows(rowsArray: com.google.gson.JsonArray?, columns: List<String>): List<Map<String, Any?>> {
        if (rowsArray == null) return emptyList()
        return rowsArray.map { rowElement ->
            val rowObj = rowElement.asJsonObject
            buildMap<String, Any?> {
                for (col in columns) {
                    if (rowObj.has(col)) {
                        val value = rowObj.get(col)
                        put(
                            col,
                            when {
                                value.isJsonNull -> ""
                                value.isJsonPrimitive -> value.asString
                                else -> value.toString()
                            },
                        )
                    }
                }
            }
        }
    }

    private suspend fun persistTable(
        name: String,
        columns: List<String>,
        rows: List<Map<String, Any?>>,
        conversationId: String,
        bookUrl: String,
        bookName: String,
        bookAuthor: String,
    ): Pair<AiMemoryTable, List<AiMemoryTableRow>> {
        val tableId = "memtable_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val table = AiMemoryTable(
            id = tableId,
            name = name,
            columns = GSON.toJson(columns),
            conversationId = conversationId,
            bookUrl = bookUrl,
            bookName = bookName,
            bookAuthor = bookAuthor,
            enabled = true,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        gateway.upsertTable(table)
        val persistedRows = rows.mapIndexed { index, rowData ->
            val payload = columns.associateWith { col -> rowData[col]?.toString().orEmpty() }
                .filterValues { it.isNotBlank() }
            AiMemoryTableRow(
                id = "memrow_${UUID.randomUUID().toString().replace("-", "")}",
                tableId = tableId,
                rowData = GSON.toJson(payload),
                sortOrder = index,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
        }
        if (persistedRows.isNotEmpty()) gateway.replaceAllRows(tableId, persistedRows)
        return table to persistedRows
    }

    private fun parseRegeneratedRows(responseText: String, tableId: String, columns: List<String>): List<AiMemoryTableRow> {
        val jsonStr = extractJsonArray(responseText)
        return runCatching {
            val jsonArray = JsonParser.parseString(jsonStr).asJsonArray
            jsonArray.mapIndexed { index, element ->
                val obj = element.asJsonObject
                val filteredMap = mutableMapOf<String, Any?>()
                for (col in columns) {
                    if (obj.has(col)) {
                        val value = obj.get(col)
                        filteredMap[col] = when {
                            value.isJsonNull -> null
                            value.isJsonPrimitive -> value.asString
                            else -> value.toString()
                        }
                    } else {
                        filteredMap[col] = ""
                    }
                }
                AiMemoryTableRow(
                    id = "memrow_${UUID.randomUUID().toString().replace("-", "")}",
                    tableId = tableId,
                    rowData = GSON.toJson(filteredMap),
                    sortOrder = index,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }.getOrNull() ?: emptyList()
    }

    private fun parseColumns(columnsJson: String): List<String> =
        runCatching { GSON.fromJson(columnsJson, Array<String>::class.java).toList() }.getOrNull() ?: emptyList()

    private fun parseRowData(rowData: String): Map<String, Any?> =
        io.legado.app.utils.parseJsonStringMap(rowData)

    private fun extractJsonArray(text: String): String {
        var t = text.trim().replace(Regex("""```(?:json)?\s*"""), "").replace(Regex("""\s*```"""), "").trim()
        val start = t.indexOf('[')
        val end = t.lastIndexOf(']')
        return if (start >= 0 && end > start) t.substring(start, end + 1) else t
    }

    private fun extractJsonObject(text: String): String {
        var t = text.trim().replace(Regex("""```(?:json)?\s*"""), "").replace(Regex("""\s*```"""), "").trim()
        val start = t.indexOf('{')
        var depth = 0
        var end = -1
        for (i in start until t.length) {
            when (t[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) { end = i; break }
                }
            }
        }
        return if (start >= 0 && end > start) t.substring(start, end + 1) else t
    }
}
