package io.legado.app.ui.ai.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.legado.app.R
import io.legado.app.ui.common.compose.VerticalScrollStateScrollbar
import kotlinx.collections.immutable.ImmutableList

/**
 * Tool approval panel as a content overlay so translucent/rounded edges
 * reveal the chat list behind (not Scaffold bottomBar background).
 */
@Composable
fun AiToolApprovalOverlay(
    items: ImmutableList<PendingToolCallUi>,
    batchFeedback: String,
    characterCards: List<AiCharacterCardUi>,
    selectedCharacterCards: List<AiCharacterCardUi>,
    dialogState: AiChatDialogState,
    viewModel: AiChatViewModel,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = items.isNotEmpty(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        AiToolApprovalPanel(
            items = items,
            batchFeedback = batchFeedback,
            characterCards = characterCards,
            selectedCharacterCards = selectedCharacterCards,
            dialogState = dialogState,
            viewModel = viewModel,
        )
    }
}

@Composable
fun AiToolApprovalPanel(
    items: ImmutableList<PendingToolCallUi>,
    batchFeedback: String,
    characterCards: List<AiCharacterCardUi>,
    selectedCharacterCards: List<AiCharacterCardUi>,
    dialogState: AiChatDialogState,
    viewModel: AiChatViewModel,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val panelFamily = resolvePanelVisualFamily(items)
    val panelColors = toolApprovalColors(panelFamily)
    // Outermost Box is transparent so chat shows through side gaps;
    // panelColors apply only to the inner rounded Surface.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = panelColors.panelContainer,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
            ) {
                Box(
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(
                            panelColors.accentStripe,
                            RoundedCornerShape(
                                topStart = 12.dp,
                                bottomStart = 12.dp,
                            ),
                        ),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(12.dp),
                ) {
                    Text(
                        stringResource(pendingToolConfirmTitleRes(items)),
                        style = MaterialTheme.typography.labelMedium,
                        color = panelColors.panelOnContainer,
                    )
                    pendingToolConfirmSubtitle(items)?.let { subtitle ->
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = panelColors.panelSubtitle,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Spacer(modifier.height(8.dp))
                    val previewMaxHeight = pendingToolPreviewMaxHeight(items)
                    val toolPreviewScrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(animationSpec = tween(200))
                            .heightIn(max = previewMaxHeight)
                            .clip(RoundedCornerShape(8.dp))
                            .clipToBounds(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 10.dp)
                                .verticalScroll(toolPreviewScrollState),
                        ) {
                            items.forEach { item ->
                                key(item.callId) {
                                    val itemColors = toolItemApprovalColors(item.toolName, item.tier)
                                    var expanded by remember(item.callId) {
                                        mutableStateOf(shouldAutoExpandToolPreview(item))
                                    }
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = itemColors.itemContainer,
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { expanded = !expanded }
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Checkbox(
                                                    checked = item.checked,
                                                    onCheckedChange = {
                                                        viewModel.onIntent(AiChatIntent.ToggleToolApproval(item.callId))
                                                    },
                                                    enabled = item.canToggleApproval(),
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        ToolApprovalBadgesRow(
                                                            toolName = item.toolName,
                                                            tier = item.tier,
                                                        )
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(
                                                            item.displayName,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = itemColors.itemOnContainer,
                                                            modifier = Modifier.weight(1f),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                    Text(
                                                        item.summary,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = colorScheme.onSurface,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                    val rowError = item.argsValidationError ?: item.validationError
                                                    rowError?.let { err ->
                                                        Text(
                                                            err,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = colorScheme.error,
                                                            maxLines = 4,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                    item.validationWarnings.forEach { warning ->
                                                        Text(
                                                            warning,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = colorScheme.onSurfaceVariant,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                }
                                                if (
                                                    item.previewDetail.isNotBlank() ||
                                                    item.feedback.isNotBlank() ||
                                                    item.fieldChanges.isNotEmpty() ||
                                                    item.argsValidationError != null ||
                                                    item.editableArgsJson.isNotBlank()
                                                ) {
                                                    Icon(
                                                        if (expanded) {
                                                            Icons.Default.KeyboardArrowUp
                                                        } else {
                                                            Icons.Default.KeyboardArrowDown
                                                        },
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                        tint = itemColors.accent.copy(alpha = 0.7f),
                                                    )
                                                }
                                            }
                                            AnimatedVisibility(visible = expanded) {
                                                Column(modifier = Modifier.padding(start = 32.dp, bottom = 4.dp)) {
                                                    if (item.subItems.isNotEmpty()) {
                                                        item.subItems.forEach { sub ->
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                            ) {
                                                                Checkbox(
                                                                    checked = sub.checked,
                                                                    onCheckedChange = {
                                                                        viewModel.onIntent(
                                                                            AiChatIntent.ToggleToolSubItemApproval(
                                                                                item.callId,
                                                                                sub.id,
                                                                            ),
                                                                        )
                                                                    },
                                                                    enabled = sub.tier == "INCREMENTAL" &&
                                                                        sub.validationError == null,
                                                                )
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    Text(
                                                                        sub.opLabel,
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = colorScheme.onSurface,
                                                                    )
                                                                    sub.validationError?.let {
                                                                        Text(
                                                                            it,
                                                                            style = MaterialTheme.typography.labelSmall,
                                                                            color = colorScheme.error,
                                                                        )
                                                                    }
                                                                    FieldChangeDiffList(
                                                                        changes = sub.fieldChanges,
                                                                        maxHeightDp = 160,
                                                                        showCheckboxes = item.allowFieldLevelApproval &&
                                                                            item.subItems.size == 1,
                                                                        onToggleField = if (item.allowFieldLevelApproval) {
                                                                            { path ->
                                                                                viewModel.onIntent(
                                                                                    AiChatIntent.ToggleFieldChangeApproval(
                                                                                        item.callId,
                                                                                        path,
                                                                                    ),
                                                                                )
                                                                            }
                                                                        } else {
                                                                            null
                                                                        },
                                                                    )
                                                                }
                                                                sub.deepLink?.let { link ->
                                                                    TextButton(onClick = {
                                                                        openStructuredDeepLink(
                                                                            dialogState,
                                                                            link,
                                                                            characterCards,
                                                                            selectedCharacterCards,
                                                                            previewChanges = sub.fieldChanges,
                                                                        )
                                                                    }) {
                                                                        Text(
                                                                            stringResource(R.string.ai_tool_locate_op),
                                                                            style = MaterialTheme.typography.labelSmall,
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        Spacer(Modifier.height(4.dp))
                                                    } else if (item.fieldChanges.isNotEmpty()) {
                                                        FieldChangeDiffList(
                                                            changes = item.fieldChanges,
                                                            maxHeightDp = 160,
                                                        )
                                                        Spacer(Modifier.height(4.dp))
                                                    } else if (item.previewDetail.isNotBlank()) {
                                                        Text(
                                                            item.previewDetail,
                                                            style = MaterialTheme.typography.bodySmall.copy(
                                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                            ),
                                                            color = colorScheme.onSurfaceVariant,
                                                        )
                                                        Spacer(Modifier.height(4.dp))
                                                    }
                                                    item.deepLink?.let { link ->
                                                        TextButton(onClick = {
                                                            openStructuredDeepLink(
                                                                dialogState,
                                                                link,
                                                                characterCards,
                                                                selectedCharacterCards,
                                                                previewChanges = item.fieldChanges,
                                                            )
                                                        }) {
                                                            Text(
                                                                stringResource(R.string.ai_tool_open_editor),
                                                                style = MaterialTheme.typography.labelSmall,
                                                            )
                                                        }
                                                    }
                                                    val showAdvancedArgs = remember(item.callId) {
                                                        mutableStateOf(item.argsValidationError != null)
                                                    }
                                                    TextButton(
                                                        onClick = { showAdvancedArgs.value = !showAdvancedArgs.value },
                                                    ) {
                                                        Text(
                                                            if (showAdvancedArgs.value) {
                                                                "Hide advanced args"
                                                            } else {
                                                                "Advanced args"
                                                            },
                                                            style = MaterialTheme.typography.labelSmall,
                                                        )
                                                    }
                                                    if (showAdvancedArgs.value || item.argsValidationError != null) {
                                                        OutlinedTextField(
                                                            value = item.editableArgsJson.ifBlank { "{}" },
                                                            onValueChange = {
                                                                viewModel.onIntent(
                                                                    AiChatIntent.UpdateToolArgs(item.callId, it),
                                                                )
                                                            },
                                                            modifier = Modifier.fillMaxWidth(),
                                                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                            ),
                                                            isError = item.argsValidationError != null,
                                                            supportingText = item.argsValidationError?.let { err ->
                                                                {
                                                                    Text(
                                                                        err,
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                    )
                                                                }
                                                            },
                                                            singleLine = false,
                                                            maxLines = 8,
                                                        )
                                                    }
                                                    OutlinedTextField(
                                                        value = item.feedback,
                                                        onValueChange = {
                                                            viewModel.onIntent(
                                                                AiChatIntent.UpdateToolFeedback(item.callId, it),
                                                            )
                                                        },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        placeholder = {
                                                            Text(
                                                                "Note for this tool if you confirm it",
                                                                style = MaterialTheme.typography.labelSmall,
                                                            )
                                                        },
                                                        textStyle = MaterialTheme.typography.bodySmall,
                                                        singleLine = false,
                                                        maxLines = 2,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        VerticalScrollStateScrollbar(
                            scrollState = toolPreviewScrollState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .matchParentSize(),
                            fadeWhenIdle = false,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = batchFeedback,
                        onValueChange = { viewModel.onIntent(AiChatIntent.UpdateToolBatchFeedback(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "Tell the AI what to change instead (sent when you reject)",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        textStyle = MaterialTheme.typography.bodySmall,
                        singleLine = false,
                        maxLines = 3,
                    )
                    Spacer(Modifier.height(8.dp))
                    val approvableItems = items.filter { it.isApprovable() }
                    val approvableCount = approvableItems.size
                    val destructive = approvableItems.any { it.tier == "DESTRUCTIVE" }
                    val hasRejectFeedback = batchFeedback.isNotBlank() ||
                        items.any { it.feedback.isNotBlank() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(1f),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { viewModel.onIntent(AiChatIntent.RejectPendingTools) }) {
                            Text(
                                if (hasRejectFeedback) "Send feedback & reject" else "Reject all",
                                color = colorScheme.error,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = { viewModel.onIntent(AiChatIntent.ConfirmPendingTools) },
                            enabled = approvableCount > 0,
                        ) {
                            Text(
                                if (destructive) {
                                    "Confirm destructive change ($approvableCount)"
                                } else {
                                    "Confirm ($approvableCount)"
                                },
                                color = when {
                                    approvableCount == 0 -> colorScheme.onSurface.copy(alpha = 0.38f)
                                    destructive -> colorScheme.error
                                    else -> panelColors.accent
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
