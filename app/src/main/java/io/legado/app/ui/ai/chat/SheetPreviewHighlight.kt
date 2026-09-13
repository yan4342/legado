package io.legado.app.ui.ai.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R

fun fieldChangesByLeaf(changes: List<FieldChangeUi>, tableId: String? = null): Map<String, FieldChangeUi> {
    if (changes.isEmpty()) return emptyMap()
    return buildMap {
        changes.forEach { change ->
            val leaf = when {
                tableId != null && change.path.startsWith("$tableId/") ->
                    change.path.removePrefix("$tableId/")
                change.path.contains('/') -> change.path.substringAfterLast('/')
                else -> change.path
            }
            if (leaf.isNotBlank()) put(leaf, change)
        }
    }
}

fun outlineLineChanges(changes: List<FieldChangeUi>): List<Pair<Int, FieldChangeUi>> =
    changes.mapNotNull { change ->
        val num = Regex("""outline:L(\d+)""").find(change.path)?.groupValues?.getOrNull(1)?.toIntOrNull()
        num?.let { it to change }
    }.sortedBy { it.first }

fun canInlineGitHighlightMemoryTable(highlightRowId: String?, changes: List<FieldChangeUi>): Boolean {
    if (highlightRowId.isNullOrBlank() || changes.isEmpty()) return false
    return changes.any { it.oldValue.isNotBlank() || it.newValue.isNotBlank() }
}

fun canInlineGitHighlightOutline(
    content: String,
    highlightSection: String?,
    changes: List<FieldChangeUi>,
): Boolean {
    if (changes.isEmpty()) return false
    val lineChanges = outlineLineChanges(changes)
    if (lineChanges.isNotEmpty()) {
        val lineCount = content.lines().size.coerceAtLeast(1)
        return lineChanges.any { (lineNum, _) -> lineNum in 1..lineCount }
    }
    if (!highlightSection.isNullOrBlank()) {
        val markers = listOf("## $highlightSection", "# $highlightSection", highlightSection)
        return markers.any { content.contains(it, ignoreCase = true) }
    }
    return false
}

fun canInlineGitHighlightUserFields(changes: List<FieldChangeUi>): Boolean {
    val fields = setOf("name", "userName", "description", "userDescription", "openingLine")
    return changes.any { fieldChangesByLeaf(listOf(it)).keys.any { k -> k in fields } }
}

fun canInlineGitHighlightCharacterFields(changes: List<FieldChangeUi>): Boolean {
    val fields = setOf("name", "description", "openingLine")
    return changes.any { fieldChangesByLeaf(listOf(it)).keys.any { k -> k in fields } }
}

fun shouldShowSheetPreviewBanner(
    changes: List<FieldChangeUi>,
    useInlineGit: Boolean,
): Boolean = changes.isNotEmpty() && !useInlineGit

@Composable
fun SheetPreviewBanner(
    changes: List<FieldChangeUi>,
    modifier: Modifier = Modifier,
    maxHeightDp: Int = 160,
) {
    if (changes.isEmpty()) return
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colorScheme.surfaceVariant.copy(alpha = 0.65f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                stringResource(R.string.ai_sheet_proposed_changes),
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            FieldChangeDiffList(changes = changes, maxHeightDp = maxHeightDp)
        }
    }
}

/** Git-style cell: old struck through (red), new below (green/primary). */
@Composable
fun GitStyleCellPreview(
    change: FieldChangeUi?,
    currentValue: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    if (change == null || (change.oldValue.isBlank() && change.newValue.isBlank())) {
        Text(
            currentValue.ifBlank { "-" },
            style = MaterialTheme.typography.bodySmall,
            modifier = modifier,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    Column(modifier = modifier) {
        if (change.oldValue.isNotBlank()) {
            Text(
                change.oldValue,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.error,
                textDecoration = TextDecoration.LineThrough,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (change.newValue.isNotBlank()) {
            Text(
                change.newValue,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else if (change.oldValue.isNotBlank() && currentValue.isNotBlank()) {
            Text(
                currentValue,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Inline git diff for outline line-level changes (shown inside sheet, not top banner). */
@Composable
fun GitStyleOutlineLinePanel(
    content: String,
    changes: List<FieldChangeUi>,
    highlightSection: String?,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val lineChanges = outlineLineChanges(changes)
    val lines = content.lines()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colorScheme.primaryContainer.copy(alpha = 0.25f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                stringResource(R.string.ai_sheet_proposed_changes),
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            if (lineChanges.isNotEmpty()) {
                lineChanges.forEach { (lineNum, change) ->
                    val ctx = lines.getOrNull(lineNum - 1).orEmpty()
                    Text(
                        "L$lineNum",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                    if (change.oldValue.isNotBlank()) {
                        Text(
                            "- ${change.oldValue.ifBlank { ctx }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.error,
                        )
                    } else if (ctx.isNotBlank()) {
                        Text(
                            "- $ctx",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.error,
                            textDecoration = TextDecoration.LineThrough,
                        )
                    }
                    if (change.newValue.isNotBlank()) {
                        Text(
                            "+ ${change.newValue}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.primary,
                        )
                    }
                }
            } else if (!highlightSection.isNullOrBlank()) {
                Text(
                    "§ $highlightSection",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.tertiary,
                )
                changes.forEach { change ->
                    if (change.oldValue.isNotBlank()) {
                        Text(
                            "- ${change.oldValue.take(300)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.error,
                        )
                    }
                    if (change.newValue.isNotBlank()) {
                        Text(
                            "+ ${change.newValue.take(300)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GitStyleFieldPreview(
    label: String,
    change: FieldChangeUi?,
    modifier: Modifier = Modifier,
) {
    if (change == null) return
    val colorScheme = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
        )
        if (change.oldValue.isNotBlank()) {
            Text(
                "- ${change.oldValue}",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.error,
                textDecoration = TextDecoration.LineThrough,
            )
        }
        if (change.newValue.isNotBlank()) {
            Text(
                "+ ${change.newValue}",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.primary,
            )
        }
    }
}
