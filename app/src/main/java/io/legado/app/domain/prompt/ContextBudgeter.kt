package io.legado.app.domain.prompt

import io.legado.app.help.ai.AiTokenEstimator

/**
 * Trims assembled block contents to respect per-block and global token budgets.
 * Drops lower-priority prefix blocks first (higher order number = lower priority for trimming).
 */
class ContextBudgeter {

    data class BudgetResult(
        val blocks: List<AssembledPromptBlock>,
        val totalTokens: Int,
    )

    fun applyBlockBudgets(
        blocks: List<AssembledPromptBlock>,
        globalBudget: Int = 0,
    ): BudgetResult {
        val trimmed = blocks.map { block ->
            val max = block.spec.maxTokens
            if (max <= 0 || block.estimatedTokens <= max) {
                block
            } else {
                val trimmedContent = truncateToTokens(block.content, max)
                block.copy(
                    content = trimmedContent,
                    estimatedTokens = estimateTokens(trimmedContent),
                    truncated = true,
                )
            }
        }.toMutableList()

        if (globalBudget <= 0) {
            return BudgetResult(trimmed, trimmed.sumOf { it.estimatedTokens })
        }

        var total = trimmed.sumOf { it.estimatedTokens }
        if (total <= globalBudget) {
            return BudgetResult(trimmed, total)
        }

        // Trim droppable blocks from lowest priority (highest order) first.
        val droppableOrder = listOf(
            PromptBlockId.WorkspacePrefetch,
            PromptBlockId.WorldCatalog,
            PromptBlockId.WorldEntries,
            PromptBlockId.MemoryTables,
            PromptBlockId.WritingStyleGuide,
            PromptBlockId.Character,
        )
        for (blockId in droppableOrder) {
            if (total <= globalBudget) break
            val index = trimmed.indexOfFirst { it.spec.id == blockId }
            if (index < 0) continue
            val removed = trimmed.removeAt(index)
            total -= removed.estimatedTokens
        }

        if (total > globalBudget) {
            // Last resort: proportionally truncate WorkspacePrefetch / WorldCatalog text.
            val prefetchIdx = trimmed.indexOfFirst { it.spec.id == PromptBlockId.WorkspacePrefetch }
            if (prefetchIdx >= 0) {
                val block = trimmed[prefetchIdx]
                val allowance = (globalBudget - (total - block.estimatedTokens)).coerceAtLeast(64)
                if (block.estimatedTokens > allowance) {
                    val newContent = truncateToTokens(block.content, allowance)
                    val newTokens = estimateTokens(newContent)
                    total -= block.estimatedTokens - newTokens
                    trimmed[prefetchIdx] = block.copy(
                        content = newContent,
                        estimatedTokens = newTokens,
                        truncated = true,
                    )
                }
            }
        }

        return BudgetResult(trimmed, trimmed.sumOf { it.estimatedTokens })
    }

    fun truncateToTokens(text: String, maxTokens: Int): String {
        if (maxTokens <= 0 || text.isBlank()) return text
        if (estimateTokens(text) <= maxTokens) return text
        var low = 0
        var high = text.length
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (estimateTokens(text.take(mid)) <= maxTokens) {
                low = mid
            } else {
                high = mid - 1
            }
        }
        val cut = text.take(low)
        return if (cut.length < text.length) "$cut\n*（内容已截断）*" else cut
    }

    fun estimateTokens(text: String): Int = AiTokenEstimator.estimateTokens(text)

    companion object {
        /**
         * Leave headroom when packing history / prompt blocks so tools schema
         * and completion tokens are less likely to overflow the real window.
         */
        const val TRIM_RESERVE_RATIO = 0.8f

        /** Full window — used as the context usage bar denominator. */
        fun displayBudget(contextWindow: Int): Int =
            if (contextWindow > 0) contextWindow else 0

        /** Reduced budget for history / block trimming (not for UI ratio). */
        fun trimBudget(contextWindow: Int): Int =
            if (contextWindow > 0) (contextWindow * TRIM_RESERVE_RATIO).toInt() else 0

        /** Alias of [displayBudget] for existing UI call sites. */
        fun inputBudget(contextWindow: Int): Int = displayBudget(contextWindow)
    }
}
