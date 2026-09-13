package io.legado.app.domain.usecase.structured

import com.google.gson.JsonObject
import io.legado.app.data.repository.AiToolRepository
import io.legado.app.data.repository.StructuredDataToolParser
import io.legado.app.domain.model.CharacterCardPatch
import io.legado.app.domain.model.MemoryTableOp
import io.legado.app.domain.model.OutlinePatchMode
import io.legado.app.domain.model.StructuredDataDeepLink
import io.legado.app.domain.model.UserCardPatch
import io.legado.app.domain.model.WorldBookPatch

data class StructuredMutationContext(
    val toolName: String,
    val args: JsonObject,
    val conversationId: String?,
    val memoryOps: List<MemoryTableOp> = emptyList(),
    val outlineMode: OutlinePatchMode? = null,
    val characterPatch: CharacterCardPatch? = null,
    val userCardPatch: UserCardPatch? = null,
    val worldBookPatch: WorldBookPatch? = null,
)

enum class StructuredMutationTier {
    INCREMENTAL,
    CREATIVE,
    DESTRUCTIVE,
}

data class MutationOpPreview(
    val opLabel: String,
    val fieldChanges: List<FieldChange>,
    val deepLink: StructuredDataDeepLink?,
    val tier: StructuredMutationTier,
    val validationIssues: List<ValidationIssue> = emptyList(),
    val opIndex: Int = 0,
)

object StructuredDataContextParser {

    fun parse(toolName: String, args: JsonObject, conversationId: String?): StructuredMutationContext {
        val convId = args.get("conversationId")?.takeIf { !it.isJsonNull }?.asString ?: conversationId
        return when (toolName) {
            AiToolRepository.TOOL_PATCH_HISTORY_MEMORY -> StructuredMutationContext(
                toolName = toolName,
                args = args,
                conversationId = convId,
                memoryOps = StructuredDataToolParser.parseMemoryOpsFromArgs(args),
            )
            AiToolRepository.TOOL_PATCH_OUTLINE -> StructuredMutationContext(
                toolName = toolName,
                args = args,
                conversationId = convId,
                outlineMode = StructuredDataToolParser.parseOutlinePatch(args)
                    ?: OutlinePatchMode.Generate(convId.orEmpty()),
            )
            AiToolRepository.TOOL_PATCH_CHARACTER_CARD -> StructuredMutationContext(
                toolName = toolName,
                args = args,
                conversationId = convId,
                characterPatch = StructuredDataToolParser.parseCharacterCardPatch(args),
            )
            AiToolRepository.TOOL_PATCH_USER_CARD -> StructuredMutationContext(
                toolName = toolName,
                args = args,
                conversationId = convId,
                userCardPatch = StructuredDataToolParser.parseUserCardPatch(args),
            )
            AiToolRepository.TOOL_PATCH_WORLD_BOOK -> StructuredMutationContext(
                toolName = toolName,
                args = args,
                conversationId = convId,
                worldBookPatch = StructuredDataToolParser.parseWorldBookPatch(args),
            )
            AiToolRepository.TOOL_EXTRACT_WORLD_BOOK -> StructuredMutationContext(
                toolName = toolName,
                args = args,
                conversationId = convId,
            )
            else -> StructuredMutationContext(
                toolName = toolName,
                args = args,
                conversationId = convId,
            )
        }
    }

    fun tier(context: StructuredMutationContext): StructuredMutationTier = when (context.toolName) {
        AiToolRepository.TOOL_PATCH_HISTORY_MEMORY -> memoryOpsTier(context.memoryOps)
        AiToolRepository.TOOL_PATCH_OUTLINE -> outlineTier(context.outlineMode)
        AiToolRepository.TOOL_PATCH_CHARACTER_CARD -> {
            if (context.characterPatch?.generate == true) StructuredMutationTier.CREATIVE
            else StructuredMutationTier.INCREMENTAL
        }
        AiToolRepository.TOOL_PATCH_USER_CARD -> {
            if (context.userCardPatch?.generate == true) StructuredMutationTier.CREATIVE
            else StructuredMutationTier.INCREMENTAL
        }
        AiToolRepository.TOOL_PATCH_WORLD_BOOK -> {
            if (context.worldBookPatch?.generate == true) StructuredMutationTier.CREATIVE
            else StructuredMutationTier.INCREMENTAL
        }
        AiToolRepository.TOOL_EXTRACT_WORLD_BOOK -> StructuredMutationTier.CREATIVE
        else -> StructuredMutationTier.INCREMENTAL
    }

    fun tierForMemoryOp(op: MemoryTableOp): StructuredMutationTier = when (op) {
        is MemoryTableOp.PatchRow,
        is MemoryTableOp.AddRow,
        is MemoryTableOp.DeleteRow,
        is MemoryTableOp.UpdateSchema,
        is MemoryTableOp.CreateTable,
        -> StructuredMutationTier.INCREMENTAL
        is MemoryTableOp.GenerateTables,
        -> StructuredMutationTier.CREATIVE
        is MemoryTableOp.RegenerateTable -> StructuredMutationTier.DESTRUCTIVE
    }

    private fun memoryOpsTier(ops: List<MemoryTableOp>): StructuredMutationTier {
        if (ops.isEmpty()) return StructuredMutationTier.INCREMENTAL
        return ops.maxOf { tierForMemoryOp(it) }
    }

    private fun outlineTier(mode: OutlinePatchMode?): StructuredMutationTier = when (mode) {
        is OutlinePatchMode.Replace -> StructuredMutationTier.DESTRUCTIVE
        is OutlinePatchMode.Generate -> StructuredMutationTier.CREATIVE
        is OutlinePatchMode.SearchReplace,
        is OutlinePatchMode.Append,
        is OutlinePatchMode.PatchSection,
        is OutlinePatchMode.GraphOps,
        -> StructuredMutationTier.INCREMENTAL
        null -> StructuredMutationTier.CREATIVE
    }
}
