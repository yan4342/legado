package io.legado.app.ui.ai.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.os.Build
import android.text.Spanned
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import io.legado.app.ui.common.compose.TooltipIconButton
import android.view.textclassifier.TextClassifier
import android.widget.TextView
import io.legado.app.R
import io.legado.app.data.entities.AiPlan
import io.legado.app.help.coil.CoilImagesPlugin
import io.legado.app.ui.common.compose.VerticalScrollStateScrollbar
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.toArgb
import kotlinx.collections.immutable.ImmutableList

@Composable
private fun ChatElevatedPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f), shape),
        shape = shape,
        color = colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun QuestionOptionCard(
    label: String,
    selected: Boolean,
    allowMultiple: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val borderColor = if (selected) {
        colorScheme.primary
    } else {
        colorScheme.outline.copy(alpha = 0.35f)
    }
    val backgroundColor = if (selected) {
        colorScheme.primary.copy(alpha = 0.08f)
    } else {
        Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .background(backgroundColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (allowMultiple) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() },
                modifier = Modifier.size(20.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) colorScheme.primary
                        else colorScheme.outline.copy(alpha = 0.4f),
                    ),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurface,
        )
    }
}

@Composable
internal fun ChatUserQuestionsPanel(
    pending: PendingUserQuestionsUi,
    onToggleOption: (questionId: String, optionId: String) -> Unit,
    onUpdateCustomText: (questionId: String, text: String) -> Unit,
    onSkip: () -> Unit,
    onSubmit: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val canSubmit = pending.canSubmit()
    ChatElevatedPanel {
        val questionsScrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .clipToBounds(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 10.dp)
                    .heightIn(max = 280.dp)
                    .verticalScroll(questionsScrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                pending.questions.forEach { question ->
                    key(question.id) {
                        Text(
                            question.prompt,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            question.options.forEach { option ->
                                QuestionOptionCard(
                                    label = option.label,
                                    selected = option.id in question.selectedIds,
                                    allowMultiple = question.allowMultiple,
                                    onClick = {
                                        onToggleOption(question.id, option.id)
                                    },
                                )
                            }
                        }
                        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.6f))
                        Text(
                            stringResource(R.string.ai_user_questions_custom_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = question.customText,
                            onValueChange = { onUpdateCustomText(question.id, it) },
                            modifier = Modifier.fillMaxWidth()
                                .heightIn(min = 40.dp, max = 80.dp),
                            placeholder = {
                                Text(stringResource(R.string.ai_user_questions_custom_placeholder))
                            },
                            textStyle = MaterialTheme.typography.labelSmall,
                            singleLine = false,
                            maxLines = 3,
                        )
                    }
                }
            }
            VerticalScrollStateScrollbar(
                scrollState = questionsScrollState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .matchParentSize(),
                fadeWhenIdle = false,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.ai_user_questions_skip))
            }
            Button(
                onClick = onSubmit,
                enabled = canSubmit,
            ) {
                Text(stringResource(R.string.ai_user_questions_submit))
            }
        }
    }
}

@Composable
private fun habitMemoryHumanSummary(line: HabitMemoryConfirmLineUi): String? = when (line.op) {
    "delete" -> null
    else -> when (line.key) {
        "reply_style" -> stringResource(
            R.string.ai_habit_memory_confirm_reply_style_hint,
            line.value.take(80),
        )
        else -> line.value.take(80).takeIf { it.isNotBlank() }
    }
}

@Composable
internal fun ChatHabitMemoryConfirmPanel(
    confirm: PendingHabitMemoryConfirmUi,
    onRemember: () -> Unit,
    onSkip: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val primaryLine = confirm.lines.firstOrNull()
    ChatElevatedPanel {
        Text(
            stringResource(R.string.ai_habit_memory_confirm_title),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
        )
        if (primaryLine != null) {
            val summary = habitMemoryHumanSummary(primaryLine)
            if (summary != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            } else if (primaryLine.op == "delete") {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.ai_habit_memory_confirm_delete, primaryLine.key),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
            if (confirm.lines.size > 1) {
                Spacer(Modifier.height(6.dp))
                confirm.lines.drop(1).forEach { line ->
                    val lineText = when (line.op) {
                        "delete" -> stringResource(R.string.ai_habit_memory_confirm_delete, line.key)
                        else -> stringResource(
                            R.string.ai_habit_memory_confirm_set,
                            line.key,
                            line.value.take(80),
                        )
                    }
                    Text(
                        lineText,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.ai_habit_memory_confirm_skip))
            }
            Button(
                onClick = onRemember,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.ai_habit_memory_confirm_remember))
            }
        }
    }
}

@Composable
internal fun ChatTtsSecretPanel(
    fills: ImmutableList<PendingTtsSecretFillUi>,
    onUpdate: (callId: String, apiKey: String?, secretKey: String?) -> Unit,
) {
    if (fills.isEmpty()) return
    val colorScheme = MaterialTheme.colorScheme
    ChatElevatedPanel {
        Text(
            stringResource(R.string.ai_tts_secret_fill_title),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.ai_tts_secret_fill_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
        )
        fills.forEach { fill ->
            key(fill.callId) {
                Spacer(Modifier.height(10.dp))
                val subtitle = buildString {
                    fill.engineLabel?.takeIf { it.isNotBlank() }?.let { append(it) }
                    fill.provider?.takeIf { it.isNotBlank() }?.let {
                        if (isNotEmpty()) append(" · ")
                        append(it)
                    }
                    if (isEmpty()) append(fill.action)
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                )
                if (fill.needsApiKey) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = fill.apiKey,
                        onValueChange = { onUpdate(fill.callId, it, null) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(fill.apiKeyLabel) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                }
                if (fill.needsSecretKey) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = fill.secretKey,
                        onValueChange = { onUpdate(fill.callId, null, it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(fill.secretKeyLabel) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
fun ChatOutlineBranchPanel(
    choice: OutlineBranchChoiceUi,
    onSelect: (optionId: String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    ChatElevatedPanel {
        Text(
            stringResource(R.string.ai_outline_branch_choose_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            choice.title,
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.ai_outline_branch_choose_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        choice.options.forEach { option ->
            key(option.id) {
                QuestionOptionCard(
                    label = option.label,
                    selected = false,
                    allowMultiple = false,
                    onClick = { onSelect(option.id) },
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

/** Plan mode：第一轮计划待审批面板。批准 → 第二轮生成；编辑 → 修改计划文本；拒绝 → 撤销计划。 */
@Composable
internal fun PlanApprovalPanel(
    plan: PendingPlanUi,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onEdit: (content: String) -> Unit,
    onCancelEdit: () -> Unit,
    onStageFeedback: (selectedText: String, feedback: String) -> Unit,
    onRemoveStagedFeedback: (feedbackId: String) -> Unit,
    onSubmitAllFeedback: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    var editBuffer by remember { mutableStateOf(plan.planContent) }
    var fullscreen by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var feedbackSelectedText by remember { mutableStateOf("") }
    val stagedItems = plan.stagedFeedback
    ChatElevatedPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "计划审批",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TooltipIconButton(
                onClick = { fullscreen = true },
                label = "全屏",
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Default.Fullscreen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "AI 已输出设计计划。选中文字可在菜单里「反馈这段」暂存修改意见，全部标注完后一次提交修订。",
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        if (!fullscreen) {
            PlanBody(
                plan = plan,
                editBuffer = editBuffer,
                onEditBufferChange = { content ->
                    editBuffer = content
                    onEdit(content)
                },
                maxHeight = 320.dp,
                onRequestFeedback = { selectedText ->
                    feedbackSelectedText = selectedText
                    showFeedbackDialog = true
                },
            )
        }
        // 已暂存的反馈列表（限高滚动区，避免把底部操作按钮挤出面板）
        if (stagedItems.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "已暂存 ${stagedItems.size} 条反馈",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            StagedFeedbackList(items = stagedItems, onRemove = onRemoveStagedFeedback)
            // 提交反馈独立成行，不挤占 [拒绝][编辑][批准] 主操作行。
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onSubmitAllFeedback,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("提交 ${stagedItems.size} 条反馈，让 AI 修改")
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlanActionButtons(
                plan = plan,
                onAccept = onAccept,
                onReject = onReject,
                onEnterEdit = { onEdit(editBuffer) },
                onCancelEdit = {
                    onCancelEdit()
                    editBuffer = plan.planContent
                },
            )
        }
    }
    if (fullscreen) {
        PlanFullscreenDialog(
            plan = plan,
            editBuffer = editBuffer,
            onEditBufferChange = { content ->
                editBuffer = content
                onEdit(content)
            },
            onAccept = onAccept,
            onReject = onReject,
            onStageFeedback = onStageFeedback,
            onRemoveStagedFeedback = onRemoveStagedFeedback,
            onSubmitAllFeedback = onSubmitAllFeedback,
            onCancelEdit = {
                onCancelEdit()
                editBuffer = plan.planContent
            },
            onDismiss = { fullscreen = false },
        )
    }
    if (showFeedbackDialog) {
        PlanRevisionFeedbackDialog(
            selectedText = feedbackSelectedText,
            onDismiss = { showFeedbackDialog = false },
            onSubmit = { feedback ->
                showFeedbackDialog = false
                onStageFeedback(feedbackSelectedText, feedback)
            },
        )
    }
}

/** 计划正文：编辑态显示输入框；展示态用可选中 Markwon 渲染 markdown（选择菜单带「反馈这段」）。 */
@Composable
private fun PlanBody(
    plan: PendingPlanUi,
    editBuffer: String,
    onEditBufferChange: (String) -> Unit,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
    onTextViewCreated: ((TextView?) -> Unit)? = null,
    onRequestFeedback: ((selectedText: String) -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    if (plan.isEditing) {
        OutlinedTextField(
            value = editBuffer,
            onValueChange = onEditBufferChange,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = maxHeight),
            textStyle = MaterialTheme.typography.bodySmall,
        )
    } else {
        val ctx = LocalContext.current
        val markwon = remember {
            Markwon.builder(ctx)
                .usePlugin(CoilImagesPlugin.create(ctx))
                .usePlugin(HtmlPlugin.create())
                .usePlugin(TablePlugin.create(ctx))
                .build()
        }
        var markdown by remember(plan.planContent) { mutableStateOf<Spanned?>(null) }
        LaunchedEffect(plan.planContent) {
            markdown = withContext(Dispatchers.IO) { markwon.toMarkdown(plan.planContent) }
        }
        Box(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(colorScheme.surfaceContainerLow),
        ) {
            val textColor = colorScheme.onSurface
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    AndroidView(
                        factory = { c ->
                            TextView(c).apply {
                                setTextColor(textColor.toArgb())
                                textSize = 15f
                                setLineSpacing(4f, 1f)
                                setTextIsSelectable(true)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    setTextClassifier(TextClassifier.NO_OP)
                                }
                                // 选中文字后，选择菜单追加「反馈这段」→ 读选区交给反馈对话框。
                                customSelectionActionModeCallback = object : ActionMode.Callback {
                                    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                                        menu.add(0, R.id.plan_feedback_action, 0, "反馈这段")
                                        return true
                                    }
                                    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false
                                    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                                        if (item.itemId == R.id.plan_feedback_action) {
                                            val s = selectionStart.coerceAtLeast(0)
                                            val e = selectionEnd.coerceAtLeast(s)
                                            val sel = if (e > s) text.toString().substring(s, e).trim() else ""
                                            onRequestFeedback?.invoke(sel)
                                            mode.finish()
                                            return true
                                        }
                                        return false
                                    }
                                    override fun onDestroyActionMode(mode: ActionMode) {}
                                }
                            }
                        },
                        update = { tv ->
                            markdown?.let { markwon.setParsedMarkdown(tv, it) }
                            onTextViewCreated?.invoke(tv)
                        },
                    )
                }
            }
        }
    }
}

/** 计划消息卡片：头行（📋 计划 + 状态 chip + 查看）+ 当前 plan.md 内容预览。 */
@Composable
internal fun PlanCard(
    planFileId: String?,
    status: String?,
    revision: Int,
    onOpen: (() -> Unit)?,
    onFeedback: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    var showFeedbackDialog by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val trigger = "$status.$revision"
    val pop = remember(trigger) { Animatable(0.92f) }
    LaunchedEffect(trigger) {
        pop.animateTo(1f, animationSpec = tween(260, easing = FastOutSlowInEasing))
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
            .then(if (onOpen != null) Modifier.clickable { onOpen() } else Modifier),
        shape = shape,
        color = colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "📋 计划",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                status?.let { s ->
                    val statusColor = planStatusColor(s, colorScheme)
                    Text(
                        planStatusLabel(s),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusColor.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (onOpen != null) {
                    TooltipIconButton(
                        onClick = onOpen,
                        label = "查看",
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Default.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                if (status == "rejected" && onFeedback != null) {
                    TooltipIconButton(
                        onClick = { showFeedbackDialog = true },
                        label = "反馈",
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Default.Feedback,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            val label = if (planFileId != null) "$planFileId.md · 修订 #$revision" else "修订 #$revision"
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showFeedbackDialog) {
            PlanRevisionFeedbackDialog(
                selectedText = "",
                onDismiss = { showFeedbackDialog = false },
                onSubmit = { fb ->
                    showFeedbackDialog = false
                    onFeedback?.invoke(fb)
                },
            )
        }
    }
}

/** AiPlan.STATUS_* → 中文标签。 */
internal fun planStatusLabel(status: String): String = when (status) {
    AiPlan.STATUS_APPROVED -> "已批准"
    AiPlan.STATUS_REJECTED -> "已拒绝"
    AiPlan.STATUS_SUPERSEDED -> "已更新"
    else -> "待审批"
}

/** AiPlan.STATUS_* → 状态 chip 颜色。 */
internal fun planStatusColor(
    status: String,
    scheme: androidx.compose.material3.ColorScheme,
): Color = when (status) {
    AiPlan.STATUS_APPROVED -> Color(0xFF43A047)
    AiPlan.STATUS_REJECTED -> scheme.error
    else -> scheme.primary
}

/** 已暂存反馈列表（限高可滚动），供面板与全屏对话框复用，避免把底部操作按钮挤出。 */
@Composable
private fun StagedFeedbackList(
    items: List<StagedFeedbackUi>,
    onRemove: (feedbackId: String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 140.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.Top,
            ) {
                IconButton(
                    onClick = { onRemove(item.id) },
                    modifier = Modifier.size(22.dp),
                ) {
                    Text("✕", style = MaterialTheme.typography.labelSmall, color = colorScheme.error)
                }
                Column(modifier = Modifier.weight(1f)) {
                    if (item.startLine != null) {
                        Text(
                            "第 ${item.startLine}" +
                                if (item.endLine != null && item.endLine != item.startLine) "-${item.endLine}" else "" +
                                " 行",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.primary,
                        )
                    }
                    if (item.selectedText.isNotBlank()) {
                        Text(
                            item.selectedText.take(80),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        item.feedback,
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

/** 计划审批主操作按钮行：拒绝 / 编辑|取消编辑 / 批准（提交反馈独立成行，不挤占此行）。 */
@Composable
private fun RowScope.PlanActionButtons(
    plan: PendingPlanUi,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onEnterEdit: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    OutlinedButton(
        onClick = onReject,
        modifier = Modifier.weight(1f),
    ) {
        Text("拒绝")
    }
    if (plan.isEditing) {
        OutlinedButton(
            onClick = onCancelEdit,
            modifier = Modifier.weight(1f),
        ) {
            Text("取消编辑")
        }
    } else {
        OutlinedButton(
            onClick = onEnterEdit,
            modifier = Modifier.weight(1f),
        ) {
            Text("编辑")
        }
    }
    Button(
        onClick = onAccept,
        modifier = Modifier.weight(1f),
    ) {
        Text("批准")
    }
}

/** 计划全屏查看/编辑对话框：正文占满剩余高度，底部仍可直接审批。 */
@Composable
private fun PlanFullscreenDialog(
    plan: PendingPlanUi,
    editBuffer: String,
    onEditBufferChange: (String) -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onStageFeedback: (selectedText: String, feedback: String) -> Unit,
    onRemoveStagedFeedback: (feedbackId: String) -> Unit,
    onSubmitAllFeedback: () -> Unit,
    onCancelEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val stagedItems = plan.stagedFeedback
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var feedbackSelectedText by remember { mutableStateOf("") }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "计划审批（全屏）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
                Spacer(Modifier.height(10.dp))
                PlanBody(
                    plan = plan,
                    editBuffer = editBuffer,
                    onEditBufferChange = onEditBufferChange,
                    maxHeight = Dp.Infinity,
                    modifier = Modifier.weight(1f),
                    onRequestFeedback = { selectedText ->
                        feedbackSelectedText = selectedText
                        showFeedbackDialog = true
                    },
                )
                if (stagedItems.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "已暂存 ${stagedItems.size} 条反馈",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    StagedFeedbackList(items = stagedItems, onRemove = onRemoveStagedFeedback)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onSubmitAllFeedback,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("提交 ${stagedItems.size} 条反馈，让 AI 修改")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlanActionButtons(
                        plan = plan,
                        onAccept = onAccept,
                        onReject = onReject,
                        onEnterEdit = { onEditBufferChange(editBuffer) },
                        onCancelEdit = onCancelEdit,
                    )
                }
            }
        }
    }
    if (showFeedbackDialog) {
        PlanRevisionFeedbackDialog(
            selectedText = feedbackSelectedText,
            onDismiss = { showFeedbackDialog = false },
            onSubmit = { feedback ->
                showFeedbackDialog = false
                onStageFeedback(feedbackSelectedText, feedback)
            },
        )
    }
}

/** 选区反馈对话框：展示选中内容（如有），填修改意见并提交。 */
@Composable
private fun PlanRevisionFeedbackDialog(
    selectedText: String,
    onDismiss: () -> Unit,
    onSubmit: (feedback: String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    var feedback by remember { mutableStateOf("") }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(16.dp),
            color = colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "对选中内容提修改意见",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                )
                if (selectedText.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "选中内容：",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Text(
                        selectedText,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurface,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colorScheme.surfaceContainerLow)
                            .padding(10.dp)
                            .heightIn(max = 140.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                } else {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "未选中文字，意见将作用于整篇计划。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = feedback,
                    onValueChange = { feedback = it },
                    placeholder = { Text("填写修改意见…") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { if (feedback.isNotBlank()) onSubmit(feedback) },
                        enabled = feedback.isNotBlank(),
                    ) { Text("提交") }
                }
            }
        }
    }
}

/** 已结束计划（批准/拒绝/作废）的只读详情：markdown 查看 + 状态/修订号。 */
@Composable
internal fun PlanDetailDialog(planDetail: PlanDetailUi, onDismiss: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "计划 · ${planDetail.planId}.md",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
                Text(
                    "状态：${planDetail.status} · 修订 #${planDetail.revision}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                PlanDetailMarkdownBody(content = planDetail.content, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** 只读 markdown 正文（与 PlanBody 展示态同一 Markwon 管线）。 */
@Composable
private fun PlanDetailMarkdownBody(content: String, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val markwon = remember {
        Markwon.builder(ctx)
            .usePlugin(CoilImagesPlugin.create(ctx))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(TablePlugin.create(ctx))
            .build()
    }
    var markdown by remember(content) { mutableStateOf<Spanned?>(null) }
    LaunchedEffect(content) {
        markdown = withContext(Dispatchers.IO) { markwon.toMarkdown(content) }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colorScheme.surfaceContainerLow),
    ) {
        val textColor = colorScheme.onSurface
        SelectionContainer {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                AndroidView(
                    factory = { c ->
                        TextView(c).apply {
                            setTextColor(textColor.toArgb())
                            textSize = 15f
                            setLineSpacing(4f, 1f)
                            setTextIsSelectable(true)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                setTextClassifier(TextClassifier.NO_OP)
                            }
                        }
                    },
                    update = { tv ->
                        markdown?.let { markwon.setParsedMarkdown(tv, it) }
                    },
                )
            }
        }
    }
}
