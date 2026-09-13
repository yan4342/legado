package io.legado.app.domain.usecase.structured

import com.google.gson.JsonParser
import io.legado.app.data.dao.BookDao
import io.legado.app.data.repository.AiToolRepository
import io.legado.app.domain.gateway.AiCharacterCardGateway
import io.legado.app.domain.gateway.AiMemoryTableGateway
import io.legado.app.domain.gateway.AiOutlineGateway
import io.legado.app.domain.model.MemoryTableOp
import io.legado.app.domain.model.OutlinePatchMode

class StructuredDataValidator(
    private val memoryTableGateway: AiMemoryTableGateway,
    private val memoryTableMutator: MemoryTableMutator,
    private val outlineGateway: AiOutlineGateway,
    private val characterCardGateway: AiCharacterCardGateway,
    private val bookDao: BookDao,
) {

    suspend fun validate(ctx: StructuredMutationContext): ValidationResult {
        val issues = when (ctx.toolName) {
            AiToolRepository.TOOL_PATCH_HISTORY_MEMORY -> validateMemoryOps(ctx)
            AiToolRepository.TOOL_PATCH_OUTLINE -> validateOutline(ctx)
            AiToolRepository.TOOL_PATCH_CHARACTER_CARD -> validateCharacterCard(ctx)
            AiToolRepository.TOOL_PATCH_USER_CARD -> validateUserCard(ctx)
            AiToolRepository.TOOL_PATCH_WORLD_BOOK -> validateWorldBook(ctx)
            AiToolRepository.TOOL_EXTRACT_WORLD_BOOK -> validateExtractWorldBook(ctx)
            else -> emptyList()
        }
        return ValidationResult(issues)
    }

    private suspend fun validateMemoryOps(ctx: StructuredMutationContext): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val ops = ctx.memoryOps
        if (ops.isEmpty()) {
            issues.add(ValidationIssue.Error("operations array is required", "Provide a JSON array of memory table operations"))
            return issues
        }
        val missingTableIdHint =
            "tableId is the id field from read_history_memory (e.g. memtable_…), NOT the display name used in create_table."
        for (op in ops) {
            when (op) {
                is MemoryTableOp.PatchRow -> {
                    if (op.tableId.isBlank()) {
                        issues.add(ValidationIssue.Error("patch_row requires tableId", missingTableIdHint))
                    } else if (memoryTableGateway.getTable(op.tableId) == null) {
                        issues.add(tableNotFoundError(op.tableId, ctx.conversationId))
                    }
                    if (op.rowId.isNullOrBlank() && op.match.isNullOrEmpty()) {
                        issues.add(
                            ValidationIssue.Error(
                                "patch_row requires rowId or match",
                                "Provide rowId or match:{column:value}",
                            )
                        )
                    } else if (!op.rowId.isNullOrBlank() || !op.match.isNullOrEmpty()) {
                        issues.addAll(memoryTableMutator.validateRowTarget(op.tableId, op.rowId, op.match))
                    }
                    if (op.data.isEmpty()) {
                        issues.add(ValidationIssue.Error("patch_row data is empty"))
                    }
                }
                is MemoryTableOp.AddRow -> {
                    if (op.tableId.isBlank()) {
                        issues.add(ValidationIssue.Error("add_row requires tableId", missingTableIdHint))
                    } else if (memoryTableGateway.getTable(op.tableId) == null) {
                        issues.add(tableNotFoundError(op.tableId, ctx.conversationId))
                    }
                    if (op.data.isEmpty()) {
                        issues.add(ValidationIssue.Error("add_row data is empty"))
                    }
                }
                is MemoryTableOp.DeleteRow -> {
                    val tableId = op.tableId.orEmpty()
                    if (op.rowId.isNullOrBlank() && op.match.isNullOrEmpty()) {
                        issues.add(ValidationIssue.Error("delete_row requires rowId or match"))
                    } else if (!op.rowId.isNullOrBlank() || !op.match.isNullOrEmpty()) {
                        if (tableId.isNotBlank()) {
                            if (memoryTableGateway.getTable(tableId) == null) {
                                issues.add(tableNotFoundError(tableId, ctx.conversationId))
                            } else {
                                issues.addAll(memoryTableMutator.validateRowTarget(tableId, op.rowId, op.match))
                            }
                        }
                    }
                }
                is MemoryTableOp.RegenerateTable -> {
                    if (op.tableId.isBlank()) {
                        issues.add(ValidationIssue.Error("regenerate_table requires tableId", missingTableIdHint))
                    } else if (memoryTableGateway.getTable(op.tableId) == null) {
                        issues.add(tableNotFoundError(op.tableId, ctx.conversationId))
                    }
                }
                is MemoryTableOp.CreateTable -> {
                    if (op.name.isBlank() && !MemoryTableRelationSchema.isRelationKind(op.tableKind)) {
                        issues.add(ValidationIssue.Error("create_table requires name"))
                    }
                    if (MemoryTableRelationSchema.isRelationKind(op.tableKind)) {
                        issues.add(
                            ValidationIssue.Warning(
                                "Relation table will use canonical schema: name contains 关系, columns 角色A/角色B/关系",
                            ),
                        )
                    }
                }
                is MemoryTableOp.UpdateSchema -> {
                    if (op.tableId.isBlank()) {
                        issues.add(ValidationIssue.Error("update_schema requires tableId", missingTableIdHint))
                    } else if (memoryTableGateway.getTable(op.tableId) == null) {
                        issues.add(tableNotFoundError(op.tableId, ctx.conversationId))
                    }
                }
                is MemoryTableOp.GenerateTables -> {
                    val convId = op.conversationId.ifBlank { ctx.conversationId.orEmpty() }
                    if (convId.isNotBlank() && memoryTableGateway.getTablesForConversation(convId).isNotEmpty()) {
                        issues.add(ValidationIssue.Warning("Conversation already has memory tables; generate may add duplicates"))
                    }
                }
                else -> Unit
            }
        }
        return issues
    }

    /**
     * Explain tableId vs display name and list ids the model can retry with.
     * create_table uses `name`; add_row/patch_row require `tableId` from read_history_memory.
     */
    private suspend fun tableNotFoundError(
        tableIdOrName: String,
        conversationId: String?,
    ): ValidationIssue.Error {
        val tables = if (!conversationId.isNullOrBlank()) {
            memoryTableGateway.getTablesForConversation(conversationId)
        } else {
            emptyList()
        }
        val nameMatches = tables.filter { it.name.equals(tableIdOrName, ignoreCase = true) }
        val suggestion = buildString {
            append("tableId must be the id from read_history_memory (memtable_…), ")
            append("not the display name from create_table. ")
            when {
                nameMatches.size == 1 ->
                    append("Did you mean tableId=\"${nameMatches.first().id}\" for name \"${nameMatches.first().name}\"? ")
                nameMatches.size > 1 ->
                    append(
                        "Multiple tables named \"$tableIdOrName\": " +
                            nameMatches.joinToString { it.id } + ". Pick one id. ",
                    )
            }
            if (tables.isNotEmpty()) {
                append(
                    "Available: " +
                        tables.take(20).joinToString("; ") { "\"${it.name}\"→${it.id}" },
                )
            } else if (conversationId.isNullOrBlank()) {
                append("Pass conversationId, then call read_history_memory without tableId to list tables.")
            } else {
                append("No tables in this conversation; create_table or generate_tables first.")
            }
        }
        return ValidationIssue.Error("Table not found: $tableIdOrName", suggestion)
    }

    private suspend fun validateOutline(ctx: StructuredMutationContext): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val convId = ctx.conversationId.orEmpty()
        val mode = ctx.outlineMode ?: return listOf(ValidationIssue.Error("Invalid patch_outline args"))
        when (mode) {
            is OutlinePatchMode.SearchReplace -> {
                val current = outlineGateway.getByConversation(convId)?.content.orEmpty()
                if (!current.contains(mode.search)) {
                    issues.add(
                        ValidationIssue.Error(
                            "search text not found in outline",
                            "Verify the search string matches the current outline content",
                        )
                    )
                }
                val preview = if (mode.replaceAll) {
                    current.replace(mode.search, mode.replace)
                } else {
                    current.replaceFirst(mode.search, mode.replace)
                }
                if (preview.isNotBlank() &&
                    io.legado.app.domain.usecase.structured.graph.OutlineGraphCodec
                        .decodeAiOutput(preview).graph == null
                ) {
                    issues.add(
                        ValidationIssue.Error(
                            "search_replace would produce invalid outline_format:2",
                            "Use mode=graph_ops or keep YAML+id headings+checkbox branches",
                        ),
                    )
                }
            }
            is OutlinePatchMode.Replace -> {
                if (mode.content.isNotBlank() &&
                    io.legado.app.domain.usecase.structured.graph.OutlineGraphCodec
                        .decodeAiOutput(mode.content).graph == null
                ) {
                    issues.add(
                        ValidationIssue.Error(
                            "replace content is not valid outline_format:2",
                            "Use mode=generate or mode=graph_ops; do not paste story bible / ASCII trees",
                        ),
                    )
                }
            }
            is OutlinePatchMode.Append -> {
                val current = outlineGateway.getByConversation(convId)?.content.orEmpty()
                val preview = current + mode.content
                if (preview.isNotBlank() &&
                    io.legado.app.domain.usecase.structured.graph.OutlineGraphCodec
                        .decodeAiOutput(preview).graph == null
                ) {
                    issues.add(
                        ValidationIssue.Error(
                            "append would produce invalid outline_format:2",
                            "Use mode=graph_ops add_node instead of freeform markdown append",
                        ),
                    )
                }
            }
            is OutlinePatchMode.GraphOps -> {
                if (mode.ops.isEmpty()) {
                    issues.add(ValidationIssue.Error("graph_ops is empty"))
                }
                if (mode.parseErrors.isNotEmpty()) {
                    issues.add(
                        ValidationIssue.Error(
                            "graph_ops invalid",
                            "Rejected ops: ${mode.parseErrors.joinToString("; ")}",
                        ),
                    )
                }
                if (mode.ops.any { it is io.legado.app.domain.usecase.structured.graph.OutlineGraphOp.SelectOption }) {
                    issues.add(
                        ValidationIssue.Error(
                            "select_option is user-only",
                            "Do not auto-pick branches; wait for the user",
                        ),
                    )
                }
            }
            else -> Unit
        }
        return issues
    }

    private fun validateCharacterCard(ctx: StructuredMutationContext): List<ValidationIssue> {
        val patch = ctx.characterPatch ?: return emptyList()
        if (patch.generate) return emptyList()
        val issues = mutableListOf<ValidationIssue>()
        if (patch.cardId.isNullOrBlank() && patch.name.isNullOrBlank()) {
            issues.add(ValidationIssue.Error("cardId or name is required for patch mode"))
        }
        val hasField = listOf(patch.name, patch.description, patch.openingLine, patch.worldBookIds)
            .any { !it.isNullOrBlank() }
        if (!hasField) {
            issues.add(ValidationIssue.Error("No fields to patch"))
        }
        return issues
    }

    private fun validateUserCard(ctx: StructuredMutationContext): List<ValidationIssue> {
        val patch = ctx.userCardPatch ?: return emptyList()
        if (patch.generate) {
            if (patch.conversationId.isNullOrBlank() && ctx.conversationId.isNullOrBlank()) {
                return listOf(ValidationIssue.Error("conversationId is required for generate mode"))
            }
            return emptyList()
        }
        val issues = mutableListOf<ValidationIssue>()
        val convId = patch.conversationId?.takeIf { it.isNotBlank() } ?: ctx.conversationId
        if (convId.isNullOrBlank()) {
            issues.add(ValidationIssue.Error("conversationId is required"))
        }
        val hasField = patch.name != null || patch.description != null || patch.enabled != null
        if (!hasField) {
            issues.add(ValidationIssue.Error("No fields to patch"))
        }
        return issues
    }

    private fun validateWorldBook(ctx: StructuredMutationContext): List<ValidationIssue> {
        val patch = ctx.worldBookPatch ?: return listOf(ValidationIssue.Error("Invalid world book patch args"))
        if (patch.generate) return emptyList()
        val issues = mutableListOf<ValidationIssue>()
        val entry = patch.entry
        if (entry != null) {
            if (patch.worldBookId.isNullOrBlank()) {
                issues.add(ValidationIssue.Error("worldBookId is required to patch lore entries"))
            }
            if (entry.isDelete && entry.entryId.isNullOrBlank()) {
                issues.add(ValidationIssue.Error("entryId is required to delete a lore entry"))
            }
            if (!entry.isDelete &&
                entry.entryId.isNullOrBlank() &&
                entry.content.isNullOrBlank()
            ) {
                issues.add(ValidationIssue.Error("content is required to create a lore entry"))
            }
            return issues
        }
        if (patch.worldBookId.isNullOrBlank() && patch.name.isNullOrBlank()) {
            issues.add(ValidationIssue.Error("worldBookId or name is required for patch mode"))
        }
        return issues
    }

    private fun validateExtractWorldBook(ctx: StructuredMutationContext): List<ValidationIssue> {
        val args = ctx.args
        val issues = mutableListOf<ValidationIssue>()
        val indices = args.get("chapterIndices")?.takeIf { !it.isJsonNull }?.asString
        if (indices.isNullOrBlank()) {
            issues.add(ValidationIssue.Error("chapterIndices is required", "Provide a JSON array e.g. [0,1,2]"))
        } else {
            val parsed = runCatching { JsonParser.parseString(indices) }.getOrNull()
            if (parsed == null || !parsed.isJsonArray || parsed.asJsonArray.isEmpty()) {
                issues.add(ValidationIssue.Error("chapterIndices must be a non-empty JSON array"))
            }
        }
        val bookUrl = args.get("bookUrl")?.takeIf { !it.isJsonNull }?.asString
        val bookName = args.get("bookName")?.takeIf { !it.isJsonNull }?.asString?.trim()
        val bookAuthor = args.get("bookAuthor")?.takeIf { !it.isJsonNull }?.asString?.trim()
        val book = when {
            !bookUrl.isNullOrBlank() -> bookDao.getBook(bookUrl)
            !bookName.isNullOrBlank() && !bookAuthor.isNullOrBlank() -> bookDao.getBook(bookName, bookAuthor)
            !bookName.isNullOrBlank() -> bookDao.findByName(bookName).firstOrNull()
            else -> bookDao.lastReadBook
        }
        if (book == null) {
            issues.add(ValidationIssue.Error("Book not found", "Provide bookUrl or bookName from search_books"))
        }
        return issues
    }
}
