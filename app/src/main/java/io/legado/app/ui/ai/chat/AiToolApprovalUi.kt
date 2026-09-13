package io.legado.app.ui.ai.chat

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.repository.AiToolRepository
import io.legado.app.lib.theme.LocalAiChatSemanticColors
import kotlinx.collections.immutable.ImmutableList

@StringRes
fun pendingToolConfirmTitleRes(items: ImmutableList<PendingToolCallUi>): Int {
    val hasMutation = items.any { AiToolRepository.isMutationTool(it.toolName) }
    val hasChapterRead = items.any { AiToolRepository.isBookshelfGatedTool(it.toolName) }
    val hasSourceSearch = items.any { AiToolRepository.isBookSourceSearchTool(it.toolName) }
    val hasAddBook = items.any { AiToolRepository.isAddBookToBookshelfTool(it.toolName) }
    val hasBookSourceAgent = items.any { AiToolRepository.isBookSourceAgentTool(it.toolName) }
    val hasNonBookSourceMutation = items.any {
        AiToolRepository.isMutationTool(it.toolName) && !AiToolRepository.isBookSourceAgentTool(it.toolName)
    }
    val bookshelfAccessKinds = listOf(hasChapterRead, hasSourceSearch, hasAddBook).count { it }
    return when {
        hasBookSourceAgent &&
            !hasChapterRead && !hasSourceSearch && !hasAddBook && !hasNonBookSourceMutation ->
            R.string.ai_tool_confirm_book_source_title
        hasMutation && (hasChapterRead || hasSourceSearch || hasAddBook) ->
            R.string.ai_tool_confirm_mixed_title
        bookshelfAccessKinds > 1 -> R.string.ai_tool_confirm_mixed_title
        hasSourceSearch -> R.string.ai_tool_confirm_source_search_title
        hasAddBook -> R.string.ai_tool_confirm_add_bookshelf_title
        hasChapterRead -> R.string.ai_bookshelf_access_confirm_title
        hasMutation -> R.string.ai_tool_confirm_mutation_title
        else -> R.string.ai_tool_confirm_mixed_title
    }
}

@Composable
fun pendingToolConfirmSubtitle(items: ImmutableList<PendingToolCallUi>): String? {
    val chapterCount = items.count { AiToolRepository.isBookshelfGatedTool(it.toolName) }
    val sourceSearchCount = items.count { AiToolRepository.isBookSourceSearchTool(it.toolName) }
    val addBookCount = items.count { AiToolRepository.isAddBookToBookshelfTool(it.toolName) }
    val bookSourceAgentCount = items.count { AiToolRepository.isBookSourceAgentTool(it.toolName) }
    val mutationCount = items.count {
        AiToolRepository.isMutationTool(it.toolName) && !AiToolRepository.isBookSourceAgentTool(it.toolName)
    }
    val bookshelfCount = chapterCount + sourceSearchCount + addBookCount
    if (bookshelfCount == 0 && mutationCount == 0 && bookSourceAgentCount == 0) return null
    if (bookSourceAgentCount > 0 && bookshelfCount == 0 && mutationCount == 0) {
        return if (bookSourceAgentCount > 1) {
            stringResource(R.string.ai_tool_confirm_batch_subtitle_book_source, bookSourceAgentCount)
        } else {
            null
        }
    }
    if (bookshelfCount > 0 && (mutationCount > 0 || bookSourceAgentCount > 0)) {
        return stringResource(
            R.string.ai_tool_confirm_batch_subtitle_mixed,
            bookshelfCount,
            mutationCount + bookSourceAgentCount,
        )
    }
    if (bookshelfCount > 1) {
        return stringResource(R.string.ai_tool_confirm_batch_subtitle_bookshelf, bookshelfCount)
    }
    if (mutationCount + bookSourceAgentCount > 1) {
        return stringResource(
            R.string.ai_tool_confirm_batch_subtitle_mutation,
            mutationCount + bookSourceAgentCount,
        )
    }
    return null
}

fun pendingToolPreviewMaxHeight(items: ImmutableList<PendingToolCallUi>): Dp {
    val hasLongChapterPreview = items.any { item ->
        item.toolName == AiToolRepository.TOOL_GET_CHAPTER_CONTENT &&
            item.previewDetail.lines().size > 4
    }
    return if (hasLongChapterPreview) 360.dp else 280.dp
}

fun shouldAutoExpandToolPreview(item: PendingToolCallUi): Boolean =
    item.argsValidationError != null ||
        item.validationError != null ||
        (
            item.toolName == AiToolRepository.TOOL_GET_CHAPTER_CONTENT &&
                item.previewDetail.isNotBlank()
            )

/** Visual family shared by panel, item rows, badges, and message bubbles. */
@Immutable
enum class ToolVisualFamily {
    Search,
    Read,
    Extract,
    Memory,
    Mutation,
    Mixed,
    Destructive,
    Neutral,
}

enum class ToolBubbleVisualState {
    AwaitingApproval,
    Running,
    Executing,
    Done,
    TerminalFailure,
}

@Stable
data class ToolApprovalColors(
    val panelContainer: Color,
    val panelOnContainer: Color,
    val panelSubtitle: Color,
    val itemContainer: Color,
    val itemOnContainer: Color,
    val accent: Color,
    val iconTint: Color,
    val statusTint: Color,
    val accentStripe: Color,
)

private fun tintedApprovalPanel(surface: Color, accent: Color, fraction: Float = 0.12f): Color =
    lerp(surface, accent, fraction)

fun resolvePanelVisualFamily(items: ImmutableList<PendingToolCallUi>): ToolVisualFamily {
    if (items.any { it.tier == "DESTRUCTIVE" }) return ToolVisualFamily.Destructive
    val hasMemory = items.any { AiToolRepository.isMemoryTableTool(it.toolName) }
    val hasMutation = items.any {
        AiToolRepository.isMutationTool(it.toolName) && !AiToolRepository.isMemoryTableTool(it.toolName)
    }
    val hasBookshelf = items.any { AiToolRepository.isBookshelfAccessConfirmTool(it.toolName) }
    return when {
        hasMemory && (hasMutation || hasBookshelf) -> ToolVisualFamily.Mixed
        hasMemory -> ToolVisualFamily.Memory
        hasMutation && hasBookshelf -> ToolVisualFamily.Mixed
        hasMutation -> ToolVisualFamily.Mutation
        hasBookshelf -> ToolVisualFamily.Read
        else -> ToolVisualFamily.Neutral
    }
}

fun resolveToolVisualFamily(toolName: String, tier: String? = null): ToolVisualFamily {
    if (tier == "DESTRUCTIVE") return ToolVisualFamily.Destructive
    return when (toolName) {
        AiToolRepository.TOOL_SEARCH_BOOKS -> ToolVisualFamily.Search
        AiToolRepository.TOOL_SEARCH_BOOK_SOURCES -> ToolVisualFamily.Search
        AiToolRepository.TOOL_SEARCH_BOOK_CONTENT -> ToolVisualFamily.Search
        AiToolRepository.TOOL_WEB_SEARCH -> ToolVisualFamily.Search
        AiToolRepository.TOOL_READ_WEB_PAGE -> ToolVisualFamily.Read
        AiToolRepository.TOOL_ADD_BOOK_TO_BOOKSHELF -> ToolVisualFamily.Search
        AiToolRepository.TOOL_GET_CHAPTER_CONTENT -> ToolVisualFamily.Read
        AiToolRepository.TOOL_EXTRACT_WORLD_BOOK -> ToolVisualFamily.Extract
        AiToolRepository.TOOL_READ_HISTORY_MEMORY,
        AiToolRepository.TOOL_PATCH_HISTORY_MEMORY,
            -> ToolVisualFamily.Memory
        else -> when {
            AiToolRepository.isBookSourceAgentTool(toolName) &&
                AiToolRepository.isMutationTool(toolName) -> ToolVisualFamily.Mutation
            AiToolRepository.isBookSourceAgentTool(toolName) -> ToolVisualFamily.Read
            AiToolRepository.isMutationTool(toolName) -> ToolVisualFamily.Mutation
            AiToolRepository.isBookshelfAccessConfirmTool(toolName) -> ToolVisualFamily.Read
            else -> ToolVisualFamily.Neutral
        }
    }
}

@Composable
fun toolApprovalColors(family: ToolVisualFamily): ToolApprovalColors {
    val cs = MaterialTheme.colorScheme
    val semantic = LocalAiChatSemanticColors.current
    val base = when (family) {
        ToolVisualFamily.Search -> ToolApprovalColors(
            panelContainer = cs.primaryContainer.copy(alpha = 0.92f),
            panelOnContainer = cs.onPrimaryContainer,
            panelSubtitle = cs.onPrimaryContainer.copy(alpha = 0.72f),
            itemContainer = cs.primary.copy(alpha = 0.14f),
            itemOnContainer = cs.onSurface,
            accent = cs.primary,
            iconTint = cs.primary,
            statusTint = cs.primary,
            accentStripe = cs.primary,
        )
        ToolVisualFamily.Read -> ToolApprovalColors(
            panelContainer = cs.secondaryContainer.copy(alpha = 0.92f),
            panelOnContainer = cs.onSecondaryContainer,
            panelSubtitle = cs.onSecondaryContainer.copy(alpha = 0.72f),
            itemContainer = cs.secondary.copy(alpha = 0.14f),
            itemOnContainer = cs.onSurface,
            accent = cs.secondary,
            iconTint = cs.secondary,
            statusTint = cs.secondary,
            accentStripe = cs.secondary,
        )
        ToolVisualFamily.Extract -> ToolApprovalColors(
            panelContainer = cs.tertiaryContainer.copy(alpha = 0.92f),
            panelOnContainer = cs.onTertiaryContainer,
            panelSubtitle = cs.onTertiaryContainer.copy(alpha = 0.72f),
            itemContainer = cs.tertiary.copy(alpha = 0.14f),
            itemOnContainer = cs.onSurface,
            accent = cs.tertiary,
            iconTint = cs.tertiary,
            statusTint = cs.tertiary,
            accentStripe = cs.tertiary,
        )
        ToolVisualFamily.Memory -> {
            val memoryAccent = Color(0xFF7E57C2)
            ToolApprovalColors(
                panelContainer = tintedApprovalPanel(cs.surface, memoryAccent, 0.14f).copy(alpha = 0.92f),
                panelOnContainer = cs.onSurface,
                panelSubtitle = cs.onSurfaceVariant,
                itemContainer = memoryAccent.copy(alpha = 0.14f),
                itemOnContainer = cs.onSurface,
                accent = memoryAccent,
                iconTint = memoryAccent,
                statusTint = memoryAccent,
                accentStripe = memoryAccent,
            )
        }
        ToolVisualFamily.Mutation -> ToolApprovalColors(
            panelContainer = cs.surfaceContainerHigh.copy(alpha = 0.92f),
            panelOnContainer = cs.onSurface,
            panelSubtitle = cs.onSurfaceVariant,
            itemContainer = cs.primary.copy(alpha = 0.08f),
            itemOnContainer = cs.onSurface,
            accent = cs.primary,
            iconTint = cs.primary,
            statusTint = cs.primary,
            accentStripe = cs.primary,
        )
        ToolVisualFamily.Mixed -> ToolApprovalColors(
            panelContainer = tintedApprovalPanel(cs.surfaceContainerHigh, cs.tertiary, 0.18f).copy(alpha = 0.92f),
            panelOnContainer = cs.onSurface,
            panelSubtitle = cs.onSurfaceVariant,
            itemContainer = cs.surfaceContainerHighest,
            itemOnContainer = cs.onSurface,
            accent = cs.tertiary,
            iconTint = cs.tertiary,
            statusTint = cs.tertiary,
            accentStripe = cs.secondary,
        )
        ToolVisualFamily.Destructive -> ToolApprovalColors(
            panelContainer = cs.errorContainer.copy(alpha = 0.92f),
            panelOnContainer = cs.onErrorContainer,
            panelSubtitle = cs.onErrorContainer.copy(alpha = 0.72f),
            itemContainer = cs.error.copy(alpha = 0.08f),
            itemOnContainer = cs.onSurface,
            accent = cs.error,
            iconTint = cs.error,
            statusTint = cs.error,
            accentStripe = cs.error,
        )
        ToolVisualFamily.Neutral -> ToolApprovalColors(
            panelContainer = cs.surfaceContainerHigh.copy(alpha = 0.92f),
            panelOnContainer = cs.onSurface,
            panelSubtitle = cs.onSurfaceVariant,
            itemContainer = cs.surfaceContainerHighest,
            itemOnContainer = cs.onSurface,
            accent = cs.onSurfaceVariant,
            iconTint = cs.onSurfaceVariant,
            statusTint = cs.onSurfaceVariant,
            accentStripe = cs.onSurfaceVariant,
        )
    }
    return base.applySemanticOverrides(family, semantic, cs.surface)
}

private fun ToolApprovalColors.applySemanticOverrides(
    family: ToolVisualFamily,
    semantic: io.legado.app.lib.theme.AiChatSemanticColors,
    surface: Color,
): ToolApprovalColors {
    return when (family) {
        ToolVisualFamily.Search -> semantic.toolSearch?.let { accent ->
            copy(
                accent = accent,
                iconTint = accent,
                statusTint = accent,
                accentStripe = accent,
                itemContainer = accent.copy(alpha = 0.14f),
            )
        } ?: this
        ToolVisualFamily.Read -> semantic.toolRead?.let { accent ->
            copy(
                accent = accent,
                iconTint = accent,
                statusTint = accent,
                accentStripe = accent,
                itemContainer = accent.copy(alpha = 0.14f),
            )
        } ?: this
        ToolVisualFamily.Extract -> semantic.toolExtract?.let { accent ->
            copy(
                accent = accent,
                iconTint = accent,
                statusTint = accent,
                accentStripe = accent,
                itemContainer = accent.copy(alpha = 0.14f),
                panelContainer = tintedApprovalPanel(surface, accent, 0.12f),
            )
        } ?: this
        ToolVisualFamily.Memory -> semantic.toolMemory?.let { accent ->
            copy(
                accent = accent,
                iconTint = accent,
                statusTint = accent,
                accentStripe = accent,
                itemContainer = accent.copy(alpha = 0.14f),
                panelContainer = tintedApprovalPanel(surface, accent, 0.14f),
            )
        } ?: this
        ToolVisualFamily.Mutation -> semantic.toolMutation?.let { accent ->
            copy(
                accent = accent,
                iconTint = accent,
                statusTint = accent,
                accentStripe = accent,
                itemContainer = accent.copy(alpha = 0.14f),
                panelContainer = tintedApprovalPanel(surface, accent, 0.12f),
            )
        } ?: this
        ToolVisualFamily.Mixed -> {
            val accent = semantic.toolExtract ?: accent
            val stripe = semantic.toolRead ?: accentStripe
            if (semantic.toolExtract != null || semantic.toolRead != null) {
                copy(
                    accent = accent,
                    iconTint = accent,
                    statusTint = accent,
                    accentStripe = stripe,
                )
            } else {
                this
            }
        }
        ToolVisualFamily.Destructive -> semantic.toolDestructive?.let { accent ->
            copy(
                accent = accent,
                iconTint = accent,
                statusTint = accent,
                accentStripe = accent,
                itemContainer = accent.copy(alpha = 0.08f),
            )
        } ?: this
        ToolVisualFamily.Neutral -> this
    }
}

@Composable
fun toolBubbleColors(
    toolName: String,
    tier: String?,
    state: ToolBubbleVisualState,
): ToolApprovalColors {
    val base = toolApprovalColors(resolveToolVisualFamily(toolName, tier))
    val cs = MaterialTheme.colorScheme
    return when (state) {
        ToolBubbleVisualState.AwaitingApproval -> base.copy(
            statusTint = base.accent,
        )
        ToolBubbleVisualState.Running -> base.copy(
            panelContainer = cs.surfaceVariant.copy(alpha = 0.38f),
            iconTint = cs.onSurfaceVariant,
            statusTint = cs.onSurfaceVariant,
            accentStripe = cs.outline,
        )
        ToolBubbleVisualState.Executing -> base.copy(
            panelContainer = base.accent.copy(alpha = 0.18f),
            statusTint = cs.onSurface,
            iconTint = base.accent,
            panelOnContainer = cs.onSurface,
            panelSubtitle = cs.onSurfaceVariant,
            itemOnContainer = cs.onSurface,
        )
        ToolBubbleVisualState.Done -> base.copy(
            panelContainer = base.accent.copy(alpha = 0.14f),
            statusTint = base.accent,
            iconTint = base.accent.copy(alpha = 0.88f),
        )
        ToolBubbleVisualState.TerminalFailure -> base.copy(
            panelContainer = cs.surfaceVariant.copy(alpha = 0.32f),
            iconTint = cs.onSurfaceVariant,
            statusTint = cs.onSurfaceVariant,
            accentStripe = cs.outline,
        )
    }
}

@Immutable
enum class ToolApprovalBadgeStyle {
    Search,
    Read,
    Extract,
    Memory,
    Patch,
    Generate,
    Destructive,
}

@Immutable
data class ResolvedToolApprovalBadge(
    @StringRes val labelRes: Int,
    val style: ToolApprovalBadgeStyle,
)

private val PATCH_TOOLS = setOf(
    AiToolRepository.TOOL_PATCH_HISTORY_MEMORY,
    AiToolRepository.TOOL_PATCH_OUTLINE,
    AiToolRepository.TOOL_PATCH_CHARACTER_CARD,
    AiToolRepository.TOOL_PATCH_USER_CARD,
    AiToolRepository.TOOL_PATCH_USER_MEMORY,
    AiToolRepository.TOOL_PATCH_WORLD_BOOK,
    AiToolRepository.TOOL_PATCH_BOOK_SOURCE,
)

fun resolveToolApprovalBadges(toolName: String, tier: String? = null): List<ResolvedToolApprovalBadge> {
    val badges = mutableListOf<ResolvedToolApprovalBadge>()
    toolDomainBadge(toolName)?.let { badges.add(it) }
    when (tier) {
        "CREATIVE" -> {
            if (toolName in PATCH_TOOLS) {
                badges.add(
                    ResolvedToolApprovalBadge(
                        R.string.ai_tool_badge_generate,
                        ToolApprovalBadgeStyle.Generate,
                    ),
                )
            }
        }
        "DESTRUCTIVE" -> {
            badges.add(
                ResolvedToolApprovalBadge(
                    R.string.ai_tool_badge_destructive,
                    ToolApprovalBadgeStyle.Destructive,
                ),
            )
        }
    }
    if (badges.isEmpty()) {
        when {
            isBookshelfReadTool(toolName) || AiToolRepository.isBookshelfAccessConfirmTool(toolName) ->
                badges.add(ResolvedToolApprovalBadge(R.string.ai_tool_badge_read, ToolApprovalBadgeStyle.Read))
            AiToolRepository.isMutationTool(toolName) ->
                badges.add(ResolvedToolApprovalBadge(R.string.ai_tool_badge_patch, ToolApprovalBadgeStyle.Patch))
            toolName.startsWith("read_") ->
                badges.add(ResolvedToolApprovalBadge(R.string.ai_tool_badge_read, ToolApprovalBadgeStyle.Read))
        }
    }
    return badges
}

private fun isBookshelfReadTool(toolName: String): Boolean = toolName in setOf(
    AiToolRepository.TOOL_GET_BOOK_DETAIL,
    AiToolRepository.TOOL_LIST_BOOK_CHAPTERS,
)

private fun toolDomainBadge(toolName: String): ResolvedToolApprovalBadge? = when (toolName) {
    AiToolRepository.TOOL_SEARCH_BOOKS ->
        ResolvedToolApprovalBadge(R.string.ai_tool_badge_search, ToolApprovalBadgeStyle.Search)
    AiToolRepository.TOOL_SEARCH_BOOK_CONTENT ->
        ResolvedToolApprovalBadge(R.string.ai_tool_badge_search, ToolApprovalBadgeStyle.Search)
    AiToolRepository.TOOL_SEARCH_BOOK_SOURCES ->
        ResolvedToolApprovalBadge(R.string.ai_tool_badge_source_search, ToolApprovalBadgeStyle.Search)
    AiToolRepository.TOOL_WEB_SEARCH ->
        ResolvedToolApprovalBadge(R.string.ai_tool_badge_web_search, ToolApprovalBadgeStyle.Search)
    AiToolRepository.TOOL_READ_WEB_PAGE ->
        ResolvedToolApprovalBadge(R.string.ai_tool_badge_read_web_page, ToolApprovalBadgeStyle.Read)
    AiToolRepository.TOOL_ADD_BOOK_TO_BOOKSHELF ->
        ResolvedToolApprovalBadge(R.string.ai_tool_badge_bookshelf, ToolApprovalBadgeStyle.Search)
    AiToolRepository.TOOL_GET_BOOK_DETAIL,
    AiToolRepository.TOOL_LIST_BOOK_CHAPTERS,
    AiToolRepository.TOOL_GET_CHAPTER_CONTENT,
        -> ResolvedToolApprovalBadge(R.string.ai_tool_badge_read, ToolApprovalBadgeStyle.Read)
    AiToolRepository.TOOL_LIST_CONVERSATIONS,
    AiToolRepository.TOOL_READ_CONVERSATION,
        -> ResolvedToolApprovalBadge(R.string.ai_tool_badge_conversation, ToolApprovalBadgeStyle.Read)
    AiToolRepository.TOOL_SEARCH_WORKSPACE ->
        ResolvedToolApprovalBadge(R.string.ai_tool_badge_workspace, ToolApprovalBadgeStyle.Search)
    AiToolRepository.TOOL_ASK_USER_QUESTIONS ->
        ResolvedToolApprovalBadge(R.string.ai_tool_badge_questions, ToolApprovalBadgeStyle.Read)
    AiToolRepository.TOOL_EXTRACT_WORLD_BOOK ->
        ResolvedToolApprovalBadge(R.string.ai_tool_badge_extract, ToolApprovalBadgeStyle.Extract)
    AiToolRepository.TOOL_PATCH_HISTORY_MEMORY ->
        ResolvedToolApprovalBadge(R.string.ai_tool_badge_memory, ToolApprovalBadgeStyle.Memory)
    AiToolRepository.TOOL_READ_HISTORY_MEMORY ->
        ResolvedToolApprovalBadge(R.string.ai_tool_badge_memory, ToolApprovalBadgeStyle.Memory)
    AiToolRepository.TOOL_READ_OUTLINE ->
        ResolvedToolApprovalBadge(R.string.ai_tool_badge_outline, ToolApprovalBadgeStyle.Read)
    AiToolRepository.TOOL_PATCH_OUTLINE ->
        ResolvedToolApprovalBadge(R.string.ai_tool_badge_outline, ToolApprovalBadgeStyle.Patch)
    AiToolRepository.TOOL_READ_CHARACTER_CARD ->
        ResolvedToolApprovalBadge(R.string.ai_tool_badge_character, ToolApprovalBadgeStyle.Read)
    AiToolRepository.TOOL_PATCH_CHARACTER_CARD ->
        ResolvedToolApprovalBadge(R.string.ai_tool_badge_character, ToolApprovalBadgeStyle.Patch)
    AiToolRepository.TOOL_READ_USER_CARD ->
        ResolvedToolApprovalBadge(R.string.ai_tool_badge_user, ToolApprovalBadgeStyle.Read)
    AiToolRepository.TOOL_PATCH_USER_CARD ->
        ResolvedToolApprovalBadge(R.string.ai_tool_badge_user, ToolApprovalBadgeStyle.Patch)
    AiToolRepository.TOOL_READ_USER_MEMORY ->
        ResolvedToolApprovalBadge(R.string.ai_tool_badge_memory, ToolApprovalBadgeStyle.Memory)
    AiToolRepository.TOOL_PATCH_USER_MEMORY ->
        ResolvedToolApprovalBadge(R.string.ai_tool_badge_memory, ToolApprovalBadgeStyle.Memory)
    AiToolRepository.TOOL_READ_WORLD_BOOK ->
        ResolvedToolApprovalBadge(R.string.ai_tool_badge_worldbook, ToolApprovalBadgeStyle.Read)
    AiToolRepository.TOOL_PATCH_WORLD_BOOK ->
        ResolvedToolApprovalBadge(R.string.ai_tool_badge_worldbook, ToolApprovalBadgeStyle.Patch)
    else -> when {
        AiToolRepository.isBookSourceAgentTool(toolName) -> ResolvedToolApprovalBadge(
            R.string.ai_tool_badge_book_source,
            if (AiToolRepository.isMutationTool(toolName)) {
                ToolApprovalBadgeStyle.Patch
            } else {
                ToolApprovalBadgeStyle.Read
            },
        )
        else -> null
    }
}

@Composable
private fun badgeColors(style: ToolApprovalBadgeStyle): Pair<Color, Color> {
    val family = when (style) {
        ToolApprovalBadgeStyle.Search -> ToolVisualFamily.Search
        ToolApprovalBadgeStyle.Read -> ToolVisualFamily.Read
        ToolApprovalBadgeStyle.Extract -> ToolVisualFamily.Extract
        ToolApprovalBadgeStyle.Memory -> ToolVisualFamily.Memory
        ToolApprovalBadgeStyle.Patch -> ToolVisualFamily.Mutation
        ToolApprovalBadgeStyle.Generate -> ToolVisualFamily.Search
        ToolApprovalBadgeStyle.Destructive -> ToolVisualFamily.Destructive
    }
    val colors = toolApprovalColors(family)
    return when (style) {
        ToolApprovalBadgeStyle.Generate ->
            colors.accent.copy(alpha = 0.16f) to colors.accent
        ToolApprovalBadgeStyle.Memory,
        ToolApprovalBadgeStyle.Patch,
        ToolApprovalBadgeStyle.Search,
        ToolApprovalBadgeStyle.Read,
        ToolApprovalBadgeStyle.Extract,
        ToolApprovalBadgeStyle.Destructive,
            -> colors.accent.copy(alpha = 0.14f) to colors.accent
    }
}

@Composable
fun ToolApprovalBadge(
    badge: ResolvedToolApprovalBadge,
    modifier: Modifier = Modifier,
) {
    val (background, foreground) = badgeColors(badge.style)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = background,
    ) {
        Text(
            stringResource(badge.labelRes),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
        )
    }
}

@Composable
fun ToolApprovalBadgesRow(
    toolName: String,
    tier: String? = null,
    modifier: Modifier = Modifier,
) {
    val badges = resolveToolApprovalBadges(toolName, tier)
    if (badges.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        badges.forEach { badge ->
            ToolApprovalBadge(badge)
        }
    }
}

@Composable
fun toolItemApprovalColors(toolName: String, tier: String?): ToolApprovalColors =
    toolApprovalColors(resolveToolVisualFamily(toolName, tier))
