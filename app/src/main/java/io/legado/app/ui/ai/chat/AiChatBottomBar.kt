package io.legado.app.ui.ai.chat

import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import io.legado.app.R
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.WritingInputMode
import io.legado.app.domain.model.WritingInputModeSwitchState
import io.legado.app.domain.model.WritingUserInput
import io.legado.app.ui.common.compose.VerticalScrollStateScrollbar
import io.legado.app.ui.common.compose.topbar.TopBarAnimatedActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun String.toTextFieldValueAtEnd(): TextFieldValue =
    TextFieldValue(text = this, selection = TextRange(length))

@Composable
private fun CompactChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = shape,
            )
            .background(
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else Color.Transparent,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        label()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiChatBottomBar(
    state: AiChatUiState,
    viewModel: AiChatViewModel,
    dialogState: AiChatDialogState,
    inputValue: TextFieldValue,
    onInputValueChange: (TextFieldValue) -> Unit,
    inputText: String,
    inputMode: WritingInputMode,
    onInputModeChange: (WritingInputMode, TextFieldValue, WritingInputModeSwitchState) -> Unit,
    inputModeSwitchState: WritingInputModeSwitchState,
    onInputModeSwitchStateChange: (WritingInputModeSwitchState) -> Unit,
    isWritingMode: Boolean,
    writingInputHint: String,
    showAtMentionMenu: Boolean,
    atMentionCards: List<AiCharacterCardUi>,
    showSlashMenu: Boolean = false,
    slashCommands: List<AiSlashCommandUi> = emptyList(),
    galgameHudExpanded: Boolean,
    onGalgameHudExpandedChange: (Boolean) -> Unit,
    showContextBar: Boolean,
    onShowContextBarChange: (Boolean) -> Unit,
    toolsPanelOpen: Boolean,
    onToolsPanelOpenChange: (Boolean) -> Unit,
    drawerState: DrawerState,
    scope: CoroutineScope,
    onAttachClick: () -> Unit = {},
) {
    val colorScheme = MaterialTheme.colorScheme
                val imeVisible = WindowInsets.isImeVisible
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (imeVisible) Modifier.imePadding()
                            else Modifier.navigationBarsPadding()
                        ),
                ) {
                    // Galgame HUD
                    AnimatedVisibility(
                        visible = state.galgameHudHtml.isNotBlank() && !state.isSending,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        val hudCollapsedHeight = 48.dp
                        val hudExpandedHeight = 260.dp
                        val targetHeight by animateDpAsState(
                            if (galgameHudExpanded) hudExpandedHeight else hudCollapsedHeight,
                            animationSpec = tween(250),
                        )

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                            onClick = { onGalgameHudExpandedChange(!galgameHudExpanded) },
                        ) {
                            Column {
                                AndroidView(
                                    factory = { context ->
                                        object : WebView(context) {
                                            override fun onTouchEvent(event: android.view.MotionEvent): Boolean = false
                                        }.apply {
                                            settings.javaScriptEnabled = false
                                            settings.allowFileAccess = false
                                            settings.allowContentAccess = false
                                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                            isHorizontalScrollBarEnabled = false
                                            isVerticalScrollBarEnabled = false
                                            isClickable = false
                                            isFocusable = false
                                        }
                                    },
                                    update = { webView ->
                                        webView.loadDataWithBaseURL(null, state.galgameHudHtml, "text/html", "UTF-8", null)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(targetHeight)
                                        .clipToBounds(),
                                )
                                // Expand/collapse + regenerate button
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        if (galgameHudExpanded) Icons.Default.KeyboardArrowUp
                                        else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (galgameHudExpanded) "Collapse" else "Expand",
                                        modifier = Modifier.size(14.dp),
                                        tint = colorScheme.onSurfaceVariant,
                                    )
                                    if (state.galgameHudLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.onIntent(AiChatIntent.RegenerateGalgameHud)
                                                },
                                                modifier = Modifier.size(28.dp),
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_refresh_black_24dp),
                                                    contentDescription = stringResource(R.string.ai_hud_refresh),
                                                    modifier = Modifier.size(14.dp),
                                                    tint = colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    viewModel.onIntent(AiChatIntent.RecreateGalgameHud)
                                                },
                                                modifier = Modifier.size(28.dp),
                                            ) {
                                                Icon(
                                                    Icons.Default.AutoAwesome,
                                                    contentDescription = stringResource(R.string.ai_hud_recreate),
                                                    modifier = Modifier.size(14.dp),
                                                    tint = colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Suggestions bar
                    val isGalgameActive = state.galgameHudHtml.isNotBlank()
                    AnimatedVisibility(
                        visible = (state.suggestions.isNotEmpty() || isGalgameActive) && !state.isSending,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LazyRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(state.suggestions) { suggestion ->
                                    AssistChip(
                                        onClick = { viewModel.onIntent(AiChatIntent.SelectSuggestion(suggestion)) },
                                        label = {
                                            Text(
                                                suggestion,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        },
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.onIntent(AiChatIntent.DismissSuggestions) },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Dismiss suggestions",
                                    modifier = Modifier.size(14.dp),
                                    tint = colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Pending tool confirmation lives in AiChatScreen content as a bottom overlay.

                    state.outlineBranchChoice?.let { branchChoice ->
                        AnimatedVisibility(
                            visible = true,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            ChatOutlineBranchPanel(
                                choice = branchChoice,
                                onSelect = { optionId ->
                                    viewModel.onIntent(AiChatIntent.SelectOutlineBranch(optionId))
                                },
                            )
                        }
                    }

                    state.pendingUserQuestions?.let { pendingQuestions ->
                        AnimatedVisibility(
                            visible = true,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            ChatUserQuestionsPanel(
                                pending = pendingQuestions,
                                onToggleOption = { questionId, optionId ->
                                    viewModel.onIntent(
                                        AiChatIntent.ToggleQuestionOption(
                                            pendingQuestions.callId,
                                            questionId,
                                            optionId,
                                        ),
                                    )
                                },
                                onUpdateCustomText = { questionId, text ->
                                    viewModel.onIntent(
                                        AiChatIntent.UpdateQuestionCustomText(
                                            pendingQuestions.callId,
                                            questionId,
                                            text,
                                        ),
                                    )
                                },
                                onSkip = { viewModel.onIntent(AiChatIntent.DismissUserQuestions) },
                                onSubmit = {
                                    viewModel.onIntent(
                                        AiChatIntent.SubmitUserQuestions(pendingQuestions.callId),
                                    )
                                },
                            )
                        }
                    }

                    state.pendingHabitMemoryConfirm?.let { habitConfirm ->
                        AnimatedVisibility(
                            visible = true,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            ChatHabitMemoryConfirmPanel(
                                confirm = habitConfirm,
                                onRemember = { viewModel.onIntent(AiChatIntent.ConfirmHabitMemory) },
                                onSkip = { viewModel.onIntent(AiChatIntent.RejectHabitMemory) },
                            )
                        }
                    }

                    state.pendingPlan?.let { plan ->
                        AnimatedVisibility(
                            visible = true,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            PlanApprovalPanel(
                                plan = plan,
                                onAccept = { viewModel.onIntent(AiChatIntent.AcceptPlan) },
                                onReject = { viewModel.onIntent(AiChatIntent.RejectPlan) },
                                onEdit = { content ->
                                    viewModel.onIntent(AiChatIntent.EditPlan(content))
                                },
                                onCancelEdit = { viewModel.onIntent(AiChatIntent.CancelPlanEdit) },
                                onStageFeedback = { selectedText, feedback ->
                                    viewModel.onIntent(
                                        AiChatIntent.AddPlanFeedback(selectedText, feedback),
                                    )
                                },
                                onRemoveStagedFeedback = { id ->
                                    viewModel.onIntent(AiChatIntent.RemovePlanFeedback(id))
                                },
                                onSubmitAllFeedback = {
                                    viewModel.onIntent(AiChatIntent.SubmitAllPlanFeedback)
                                },
                            )
                        }
                    }

                    if (state.pendingTtsSecretFills.isNotEmpty()) {
                        AnimatedVisibility(
                            visible = true,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            ChatTtsSecretPanel(
                                fills = state.pendingTtsSecretFills,
                                onUpdate = { callId, apiKey, secretKey ->
                                    viewModel.onIntent(
                                        AiChatIntent.UpdateTtsSecretFill(callId, apiKey, secretKey),
                                    )
                                },
                            )
                        }
                    }

                    // Slash-command suggestion dropdown (leading `/…`)
                    if (showSlashMenu) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = colorScheme.surfaceContainerHigh,
                            shadowElevation = 6.dp,
                        ) {
                            Column(modifier = Modifier.padding(4.dp)) {
                                slashCommands.forEach { cmd ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val slashIdx = inputValue.text.indexOf('/')
                                                if (slashIdx >= 0) {
                                                    onInputValueChange(
                                                        (inputValue.text.substring(0, slashIdx) + cmd.insertText)
                                                            .toTextFieldValueAtEnd(),
                                                    )
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = colorScheme.primary,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(cmd.title, style = MaterialTheme.typography.bodyMedium)
                                            if (cmd.description.isNotBlank()) {
                                                Text(
                                                    cmd.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    // @mention suggestion dropdown
                    if (showAtMentionMenu) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = colorScheme.surfaceContainerHigh,
                            shadowElevation = 6.dp,
                        ) {
                            Column(modifier = Modifier.padding(4.dp)) {
                                atMentionCards.forEach { card ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val lastAt = inputText.lastIndexOf('@')
                                                if (lastAt >= 0) {
                                                    onInputValueChange(
                                                        (inputValue.text.substring(0, lastAt) + "@${card.name} ").toTextFieldValueAtEnd(),
                                                    )
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = colorScheme.primary,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(card.name, style = MaterialTheme.typography.bodyMedium)
                                        if (card.description.isNotBlank()) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                card.description.take(40),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    // Pending attachments (chat mode)
                    if (!isWritingMode && (state.pendingAttachments.isNotEmpty() || state.isProcessingAttachments)) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (state.isProcessingAttachments) {
                                item {
                                    AssistChip(
                                        onClick = {},
                                        enabled = false,
                                        label = {
                                            Text(stringResource(R.string.ai_attachment_processing))
                                        },
                                        leadingIcon = {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        },
                                    )
                                }
                            }
                            items(state.pendingAttachments, key = { it.id }) { att ->
                                val kindIcon = when (att.kind) {
                                    io.legado.app.domain.model.AiAttachmentKind.IMAGE -> Icons.Default.Image
                                    io.legado.app.domain.model.AiAttachmentKind.DOCUMENT -> Icons.Default.PictureAsPdf
                                    else -> Icons.Default.Description
                                }
                                val sizeKb = (att.sizeBytes / 1024.0).coerceAtLeast(1.0)
                                val sizeLabel = if (sizeKb < 1024) {
                                    String.format("%.0f KB", sizeKb)
                                } else {
                                    String.format("%.1f MB", sizeKb / 1024.0)
                                }
                                val kindLabel = when (att.kind) {
                                    io.legado.app.domain.model.AiAttachmentKind.IMAGE ->
                                        stringResource(R.string.ai_attachment_kind_image)
                                    io.legado.app.domain.model.AiAttachmentKind.DOCUMENT ->
                                        stringResource(R.string.ai_attachment_kind_document)
                                    io.legado.app.domain.model.AiAttachmentKind.TEXT_EXTRACT ->
                                        stringResource(R.string.ai_attachment_kind_text)
                                    else -> att.mimeType
                                }
                                AssistChip(
                                    onClick = {},
                                    label = {
                                        Column {
                                            Text(
                                                att.displayName,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                            Text(
                                                buildString {
                                                    append(kindLabel)
                                                    append(" · ")
                                                    append(sizeLabel)
                                                    if (att.truncated) {
                                                        append(" · ")
                                                        append(stringResource(R.string.ai_attachment_truncated))
                                                    }
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colorScheme.onSurfaceVariant,
                                            )
                                            att.previewText?.takeIf { it.isNotBlank() }?.let { preview ->
                                                Text(
                                                    preview,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                )
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            kindIcon,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(R.string.ai_attachment_remove),
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable {
                                                    viewModel.onIntent(AiChatIntent.RemovePendingAttachment(att.id))
                                                },
                                        )
                                    },
                                )
                            }
                        }
                    }

                    // Input area
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(28.dp),
                        color = colorScheme.surfaceContainerHigh,
                        shadowElevation = 3.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Reasoning level selector
                            var reasoningMenuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(
                                    onClick = { reasoningMenuExpanded = true },
                                    modifier = Modifier.size(35.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Lightbulb,
                                        contentDescription = stringResource(R.string.ai_thinking_mode),
                                        tint = if (state.reasoningLevel != AiReasoningLevel.OFF)
                                            colorScheme.primary else colorScheme.onSurfaceVariant,
                                    )
                                }
                                DropdownMenu(
                                    expanded = reasoningMenuExpanded,
                                    onDismissRequest = { reasoningMenuExpanded = false },
                                ) {
                                    AiReasoningLevel.entries.forEach { level ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    when (level) {
                                                        AiReasoningLevel.OFF -> stringResource(R.string.ai_thinking_off)
                                                        AiReasoningLevel.AUTO -> "Auto"
                                                        AiReasoningLevel.LOW -> "${stringResource(R.string.ai_thinking_mode)}: Low"
                                                        AiReasoningLevel.MEDIUM -> "${stringResource(R.string.ai_thinking_mode)}: Medium"
                                                        AiReasoningLevel.HIGH -> "${stringResource(R.string.ai_thinking_mode)}: High"
                                                        AiReasoningLevel.MAX -> "${stringResource(R.string.ai_thinking_mode)}: MAX"
                                                    }
                                                )
                                            },
                                            onClick = {
                                                viewModel.onIntent(AiChatIntent.UpdateReasoningLevel(level))
                                                reasoningMenuExpanded = false
                                            },
                                            leadingIcon = {
                                                if (level == state.reasoningLevel) {
                                                    Text("✓", color = colorScheme.primary)
                                                }
                                            },
                                        )
                                    }
                                }
                            }


                            // @ mention quick button for group chat
                            if (state.selectedCharacterCards.size > 1 && isWritingMode) {
                                Box(
                                    modifier = Modifier
                                        .size(35.dp)
                                        .clickable {
                                            val suffix = if (inputText.isEmpty() || inputText.endsWith(" ") || inputText.endsWith("@")) "@"
                                            else " @"
                                            onInputValueChange((inputValue.text + suffix).toTextFieldValueAtEnd())
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "@",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colorScheme.primary,
                                    )
                                }
                            }

                            // Text field with draggable resize + auto-expand
                            var textFieldMinLines by remember { mutableStateOf(1) }
                            val lineHeightDp = 20
                            Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                                // Drag handle
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(12.dp)
                                        .pointerInput(Unit) {
                                            detectVerticalDragGestures { _, dragAmount ->
                                                val newLines = textFieldMinLines - (dragAmount / 24).toInt()
                                                textFieldMinLines = newLines.coerceIn(1, 10)
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(24.dp)
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
                                    )
                                }
                                BasicTextField(
                                    value = inputValue,
                                    onValueChange = onInputValueChange,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(
                                            min = (textFieldMinLines * lineHeightDp).dp,
                                            max = (10 * lineHeightDp).dp,
                                        ),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colorScheme.onSurface),
                                    decorationBox = { innerTextField ->
                                        Box {
                                            if (inputText.isEmpty()) {
                                                if (state.isSending) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(start = 4.dp),
                                                    ) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(14.dp),
                                                            strokeWidth = 2.dp,
                                                            color = colorScheme.onSurfaceVariant,
                                                        )
                                                        Spacer(Modifier.width(8.dp))
                                                        Text(
                                                            if (isWritingMode) writingInputHint
                                                            else stringResource(R.string.ai_chat_input_hint),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                } else {
                                                    Text(
                                                        if (isWritingMode) writingInputHint
                                                        else stringResource(R.string.ai_chat_input_hint),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                            innerTextField()
                                        }
                                    },
                                    maxLines = Int.MAX_VALUE,
                                    enabled = !state.isSending,
                                )
                            }

                            // Send/stop button
                            if (state.isSending) {
                                IconButton(
                                    onClick = { viewModel.onIntent(AiChatIntent.StopGenerating) },
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = colorScheme.error)
                                }
                            } else {
                                val canSend = !state.isProcessingAttachments &&
                                    !state.isCompressing &&
                                    (inputText.isNotBlank() || state.pendingAttachments.isNotEmpty() || isWritingMode)
                                IconButton(
                                    onClick = {
                                        if (inputText.isNotBlank() || state.pendingAttachments.isNotEmpty()) {
                                            val toSend = if (isWritingMode) {
                                                WritingUserInput.normalizeDisplay(inputText, inputMode)
                                            } else {
                                                inputText
                                            }
                                            viewModel.onIntent(AiChatIntent.SendMessage(toSend))
                                            onInputValueChange(TextFieldValue(""))
                                            onInputModeSwitchStateChange(WritingInputModeSwitchState())
                                        } else if (isWritingMode) {
                                            viewModel.onIntent(AiChatIntent.ContinueWriting)
                                        }
                                    },
                                    modifier = Modifier.size(40.dp),
                                    enabled = canSend,
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = stringResource(R.string.ai_send),
                                        tint = if (canSend) colorScheme.primary else colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    // Confirm tools toggle — chat mode only (writing mode always auto-executes)
                    if (!isWritingMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Context usage ring
                            if (state.contextWindow > 0) {
                                ContextUsageRing(
                                    contextTokensUsed = state.contextTokensUsed,
                                    contextInputBudget = state.contextInputBudget,
                                    contextWindow = state.contextWindow,
                                    isCompressing = state.isCompressing,
                                    onClick = { dialogState.showContextUsageDialog = true },
                                )
                            }

                            TopBarAnimatedActionButton(
                                checked = state.webSearchArmed,
                                onCheckedChange = { checked ->
                                    if (checked == state.webSearchArmed) return@TopBarAnimatedActionButton
                                    viewModel.onIntent(AiChatIntent.ToggleWebSearch)
                                },
                                iconChecked = Icons.Default.TravelExplore,
                                iconUnchecked = Icons.Default.TravelExplore,
                                activeText = stringResource(R.string.ai_web_search_toggle_on),
                                inactiveText = stringResource(R.string.ai_web_search_toggle_off),
                                contentColor = if (state.webSearchArmed) {
                                    colorScheme.primary
                                } else {
                                    colorScheme.onSurfaceVariant
                                },
                            )
                            
                            // Attach file
                            IconButton(
                                onClick = onAttachClick,
                                enabled = !state.isSending && !state.isProcessingAttachments,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Default.AttachFile,
                                    contentDescription = stringResource(R.string.ai_attach_file),
                                    tint = colorScheme.onSurfaceVariant,
                                )
                            }

                            Spacer(Modifier.weight(1f))

                            // AI 输出审批模式：自动 / 询问 / 计划模式（每会话记忆）
                            var outputModeMenuExpanded by remember { mutableStateOf(false) }
                            Box {
                                TextButton(
                                    onClick = { outputModeMenuExpanded = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                ) {
                                    Text(
                                        when (state.outputMode) {
                                            AiOutputMode.AUTO -> "自动"
                                            AiOutputMode.ASK -> "询问"
                                            AiOutputMode.PLAN -> "计划模式"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = colorScheme.onSurfaceVariant,
                                    )
                                }
                                DropdownMenu(
                                    expanded = outputModeMenuExpanded,
                                    onDismissRequest = { outputModeMenuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Edit automatically") },
                                        onClick = {
                                            viewModel.onIntent(AiChatIntent.SetOutputMode(AiOutputMode.AUTO))
                                            outputModeMenuExpanded = false
                                        },
                                        leadingIcon = {
                                            if (state.outputMode == AiOutputMode.AUTO) Text("✓", color = colorScheme.primary)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Ask before edit") },
                                        onClick = {
                                            viewModel.onIntent(AiChatIntent.SetOutputMode(AiOutputMode.ASK))
                                            outputModeMenuExpanded = false
                                        },
                                        leadingIcon = {
                                            if (state.outputMode == AiOutputMode.ASK) Text("✓", color = colorScheme.primary)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Plan mode") },
                                        onClick = {
                                            viewModel.onIntent(AiChatIntent.SetOutputMode(AiOutputMode.PLAN))
                                            outputModeMenuExpanded = false
                                        },
                                        leadingIcon = {
                                            if (state.outputMode == AiOutputMode.PLAN) Text("✓", color = colorScheme.primary)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // Context bar for writing mode — below input
                    if (isWritingMode && showContextBar) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth(),
                                //.padding(bottom = 6.dp),
                            color = colorScheme.surfaceContainerLow,
                            shadowElevation = 1.dp,
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 15.dp, vertical = 6.dp),
                            ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Context usage ring
                                if (state.contextWindow > 0) {
                                    ContextUsageRing(
                                        contextTokensUsed = state.contextTokensUsed,
                                        contextInputBudget = state.contextInputBudget,
                                        contextWindow = state.contextWindow,
                                        isCompressing = state.isCompressing,
                                        onClick = { dialogState.showContextUsageDialog = true },
                                    )
                                }

                                // Writing mode toggles — one slot per pair, tap to cycle
                                TopBarAnimatedActionButton(
                                    checked = inputMode == WritingInputMode.ACTION,
                                    onCheckedChange = { checked ->
                                        val newMode = if (checked) {
                                            WritingInputMode.ACTION
                                        } else {
                                            WritingInputMode.DIALOGUE
                                        }
                                        if (newMode == inputMode) return@TopBarAnimatedActionButton
                                        val switchResult = WritingUserInput.onModeSwitch(
                                            input = inputValue.text,
                                            from = inputMode,
                                            to = newMode,
                                            switchState = inputModeSwitchState,
                                        )
                                        onInputModeChange(
                                            newMode,
                                            switchResult.text.toTextFieldValueAtEnd(),
                                            switchResult.state,
                                        )
                                    },
                                    iconChecked = Icons.Default.AccessibilityNew,
                                    iconUnchecked = Icons.AutoMirrored.Filled.Chat,
                                    activeText = stringResource(R.string.ai_input_mode_action),
                                    inactiveText = stringResource(R.string.ai_input_mode_dialogue),
                                )
                                TopBarAnimatedActionButton(
                                    checked = state.writingSubMode == "roleplay",
                                    onCheckedChange = { checked ->
                                        viewModel.onIntent(
                                            AiChatIntent.SetWritingSubMode(
                                                if (checked) "roleplay" else "author",
                                            ),
                                        )
                                    },
                                    iconChecked = Icons.Default.AssignmentInd,
                                    iconUnchecked = Icons.Default.Edit,
                                    activeText = stringResource(R.string.ai_writing_submode_roleplay),
                                    inactiveText = stringResource(R.string.ai_writing_submode_author),
                                )

                                CompactChip(
                                    selected = state.galgameEnabled,
                                    onClick = { viewModel.onIntent(AiChatIntent.ToggleGalgame) },
                                    label = { Text("Galgame", style = MaterialTheme.typography.labelSmall) },
                                )

                                TextButton(
                                    onClick = {
                                        viewModel.onIntent(
                                            AiChatIntent.AiHelpReply(
                                                draftText = inputText,
                                                inputMode = inputMode,
                                            ),
                                        )
                                    },
                                    enabled = !state.isSending,
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                ) {
                                    Text(stringResource(R.string.ai_help_reply), style = MaterialTheme.typography.labelSmall)
                                }

                                Spacer(Modifier.weight(1f))

                                IconButton(
                                    onClick = {
                                        if (toolsPanelOpen) {
                                            onToolsPanelOpenChange(false)
                                        } else {
                                            scope.launch { drawerState.close() }
                                            onToolsPanelOpenChange(true)
                                        }
                                    },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Default.ChevronLeft,
                                        contentDescription = "Tools",
                                        modifier = Modifier.size(18.dp),
                                        tint = if (toolsPanelOpen) colorScheme.primary else colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    }

                    // Show context bar toggle when hidden
                    if (isWritingMode && !showContextBar) {
                        TextButton(
                            onClick = { onShowContextBarChange(true) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        ) {
                            Text(
                                stringResource(R.string.ai_writing_context),
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
}

@Composable
private fun ContextUsageRing(
    contextTokensUsed: Int,
    contextInputBudget: Int,
    contextWindow: Int,
    isCompressing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val budget = contextInputBudget.takeIf { it > 0 } ?: contextWindow
    if (budget <= 0) return
    val usageRatio = (contextTokensUsed.toFloat() / budget).coerceIn(0f, 1f)
    val ringColor = when {
        usageRatio > 0.85f -> colorScheme.error
        usageRatio > 0.6f -> colorScheme.tertiary
        else -> colorScheme.primary
    }
    val displayPercent = when {
        contextTokensUsed <= 0 -> 0
        usageRatio < 0.005f -> 1
        else -> (usageRatio * 100).toInt().coerceIn(1, 100)
    }
    val ringProgress = when {
        contextTokensUsed <= 0 -> 0f
        usageRatio < 0.005f -> 0.02f
        else -> usageRatio
    }

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(enabled = !isCompressing, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isCompressing) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = ringColor,
            )
        } else {
            CircularProgressIndicator(
                progress = { ringProgress },
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp,
                color = ringColor,
                trackColor = colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
            )
            Text(
                "$displayPercent%",
                style = MaterialTheme.typography.labelSmall,
                color = ringColor,
            )
        }
    }
}
