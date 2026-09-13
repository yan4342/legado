package io.legado.app.domain.usecase.structured

import com.google.gson.JsonObject
import io.legado.app.data.repository.AiToolRepository
import io.legado.app.domain.model.MemoryTableOp
import io.legado.app.domain.model.OutlinePatchMode
import io.legado.app.domain.model.StructuredDataDeepLink

object StructuredDataDeepLinkResolver {

    fun resolve(toolName: String, args: JsonObject, conversationId: String? = null): StructuredDataDeepLink? {
        val ctx = StructuredDataContextParser.parse(toolName, args, conversationId)
        return resolve(ctx)
    }

    fun resolve(ctx: StructuredMutationContext): StructuredDataDeepLink? = when (ctx.toolName) {
        AiToolRepository.TOOL_READ_HISTORY_MEMORY -> StructuredDataDeepLink(
            resourceType = "memory_table",
            tableId = ctx.args.string("tableId"),
            conversationId = ctx.conversationId,
            openTableList = ctx.args.string("tableId").isNullOrBlank(),
        )
        AiToolRepository.TOOL_PATCH_HISTORY_MEMORY -> resolveMemoryTable(ctx)
        AiToolRepository.TOOL_READ_OUTLINE -> resolveOutline(ctx)
        AiToolRepository.TOOL_PATCH_OUTLINE -> resolveOutline(ctx)
        AiToolRepository.TOOL_READ_CHARACTER_CARD -> StructuredDataDeepLink(
            resourceType = "character_card",
            cardId = ctx.args.string("cardId"),
            conversationId = ctx.conversationId,
        )
        AiToolRepository.TOOL_PATCH_CHARACTER_CARD -> StructuredDataDeepLink(
            resourceType = "character_card",
            cardId = ctx.characterPatch?.cardId ?: ctx.args.string("cardId"),
            conversationId = ctx.conversationId,
        )
        AiToolRepository.TOOL_READ_USER_CARD -> StructuredDataDeepLink(
            resourceType = "user_card",
            conversationId = ctx.conversationId,
        )
        AiToolRepository.TOOL_PATCH_USER_CARD -> StructuredDataDeepLink(
            resourceType = "user_card",
            conversationId = ctx.conversationId ?: ctx.userCardPatch?.conversationId,
        )
        AiToolRepository.TOOL_READ_WORLD_BOOK -> StructuredDataDeepLink(
            resourceType = "world_book",
            worldBookId = ctx.args.string("worldBookId"),
            conversationId = ctx.conversationId,
            openTableList = ctx.args.string("worldBookId").isNullOrBlank(),
        )
        AiToolRepository.TOOL_PATCH_WORLD_BOOK -> StructuredDataDeepLink(
            resourceType = "world_book",
            worldBookId = ctx.worldBookPatch?.worldBookId ?: ctx.args.string("worldBookId"),
            conversationId = ctx.conversationId,
            openTableList = (ctx.worldBookPatch?.worldBookId ?: ctx.args.string("worldBookId")).isNullOrBlank(),
        )
        AiToolRepository.TOOL_EXTRACT_WORLD_BOOK -> StructuredDataDeepLink(
            resourceType = "world_book",
            conversationId = ctx.conversationId,
            openTableList = true,
        )
        else -> null
    }

    /** Open the best-effort editor target when full resolve returns null. */
    fun fallback(toolName: String, conversationId: String?): StructuredDataDeepLink? =
        when (io.legado.app.domain.model.AiStructuredToolNames.structuredResource(toolName)) {
            io.legado.app.domain.model.AiStructuredToolNames.StructuredResource.MEMORY_TABLE ->
                StructuredDataDeepLink(resourceType = "memory_table", conversationId = conversationId, openTableList = true)
            io.legado.app.domain.model.AiStructuredToolNames.StructuredResource.OUTLINE ->
                StructuredDataDeepLink(resourceType = "outline", conversationId = conversationId)
            io.legado.app.domain.model.AiStructuredToolNames.StructuredResource.CHARACTER ->
                StructuredDataDeepLink(resourceType = "character_card", conversationId = conversationId)
            io.legado.app.domain.model.AiStructuredToolNames.StructuredResource.USER ->
                StructuredDataDeepLink(resourceType = "user_card", conversationId = conversationId)
            io.legado.app.domain.model.AiStructuredToolNames.StructuredResource.USER_MEMORY -> null
            io.legado.app.domain.model.AiStructuredToolNames.StructuredResource.WORLD_BOOK ->
                StructuredDataDeepLink(resourceType = "world_book", conversationId = conversationId, openTableList = true)
            null -> null
        }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    fun resolveForMemoryOp(op: MemoryTableOp, conversationId: String?): StructuredDataDeepLink? = when (op) {
        is MemoryTableOp.PatchRow,
        is MemoryTableOp.DeleteRow,
        is MemoryTableOp.AddRow,
        -> {
            val tableId = when (op) {
                is MemoryTableOp.PatchRow -> op.tableId
                is MemoryTableOp.DeleteRow -> op.tableId
                is MemoryTableOp.AddRow -> op.tableId
                else -> null
            }
            val rowId = when (op) {
                is MemoryTableOp.PatchRow -> op.rowId
                is MemoryTableOp.DeleteRow -> op.rowId
                else -> null
            }
            val match = when (op) {
                is MemoryTableOp.PatchRow -> op.match
                is MemoryTableOp.DeleteRow -> op.match
                else -> null
            }
            StructuredDataDeepLink(
                resourceType = "memory_table",
                tableId = tableId,
                rowId = rowId,
                matchHint = match?.entries?.joinToString { "${it.key}=${it.value}" },
                conversationId = conversationId,
                openTableList = tableId.isNullOrBlank(),
            )
        }
        is MemoryTableOp.RegenerateTable,
        is MemoryTableOp.UpdateSchema,
        -> StructuredDataDeepLink(
            resourceType = "memory_table",
            tableId = when (op) {
                is MemoryTableOp.RegenerateTable -> op.tableId
                is MemoryTableOp.UpdateSchema -> op.tableId
                else -> null
            },
            conversationId = conversationId,
            openTableList = false,
        )
        is MemoryTableOp.CreateTable,
        is MemoryTableOp.GenerateTables,
        -> StructuredDataDeepLink(
            resourceType = "memory_table",
            conversationId = conversationId,
            openTableList = true,
        )
    }

    private fun resolveMemoryTable(ctx: StructuredMutationContext): StructuredDataDeepLink? {
        val targetOp = ctx.memoryOps.firstOrNull { op ->
            op is MemoryTableOp.PatchRow ||
                op is MemoryTableOp.DeleteRow ||
                op is MemoryTableOp.AddRow ||
                (op is MemoryTableOp.RegenerateTable && op.tableId.isNotBlank()) ||
                (op is MemoryTableOp.UpdateSchema && op.tableId.isNotBlank())
        }
        return targetOp?.let { resolveForMemoryOp(it, ctx.conversationId) }
            ?: StructuredDataDeepLink(
                resourceType = "memory_table",
                conversationId = ctx.conversationId,
                openTableList = true,
            )
    }

    private fun resolveOutline(ctx: StructuredMutationContext): StructuredDataDeepLink {
        val section = when (val mode = ctx.outlineMode) {
            is OutlinePatchMode.PatchSection -> mode.sectionTitle
            else -> null
        }
        return StructuredDataDeepLink(
            resourceType = "outline",
            outlineSection = section,
            conversationId = ctx.conversationId,
        )
    }
}
