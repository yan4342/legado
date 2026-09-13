package io.legado.app.domain.usecase.structured

import io.legado.app.data.entities.AiOutline
import io.legado.app.domain.gateway.AiOutlineGateway
import io.legado.app.domain.usecase.WorkspacePrefetchCache
import io.legado.app.domain.usecase.structured.graph.OutlineGraphCodec
import io.legado.app.domain.usecase.structured.graph.OutlineGraphEngine
import io.legado.app.domain.usecase.structured.graph.OutlineGraphResult

/**
 * Applies a user-selected roleplay outline branch option via [OutlineGraphEngine].
 */
class OutlineBranchChoiceUseCase(
    private val outlineGateway: AiOutlineGateway,
    private val mutationSnapshotService: MutationSnapshotService,
) {
    data class Result(
        val success: Boolean,
        val message: String = "",
        val content: String = "",
    )

    suspend fun selectOption(
        conversationId: String,
        optionId: String,
        writingSubMode: String,
    ): Result {
        if (writingSubMode != "roleplay") {
            return Result(false, "author_mode_no_branch")
        }
        if (conversationId.isBlank() || optionId.isBlank()) {
            return Result(false, "invalid_args")
        }
        val existing = outlineGateway.getByConversation(conversationId)
            ?: return Result(false, "no_outline")
        val before = existing.content
        val decoded = OutlineGraphCodec.decode(before)
        val graph = decoded.graph ?: return Result(false, "unsupported_format")
        val applied = OutlineGraphEngine.selectOption(graph, optionId)
        if (applied !is OutlineGraphResult.Ok) {
            return Result(false, (applied as OutlineGraphResult.Err).message)
        }
        val after = OutlineGraphCodec.encode(applied.graph)
        outlineGateway.upsert(
            AiOutline(
                conversationId = conversationId,
                content = after,
                enabled = existing.enabled,
            ),
        )
        mutationSnapshotService.captureOutlineVersion(
            conversationId = conversationId,
            source = "outline_branch_choice",
            beforeContent = before,
            afterContent = after,
            enabled = existing.enabled,
        )
        WorkspacePrefetchCache.markStale(conversationId)
        return Result(true, "ok", after)
    }
}
