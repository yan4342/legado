package io.legado.app.ui.ai.outline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.usecase.structured.OutlineDocument
import io.legado.app.ui.common.compose.legadoCardBackgroundColor

@Composable
fun OutlineAnchorFields(
    document: OutlineDocument,
    onDocumentChange: (OutlineDocument) -> Unit,
    readOnly: Boolean,
    writingSubMode: String = "author",
    /** Display-only titles for roleplay path; empty means none chosen yet. */
    activePathLabel: String = "",
    modifier: Modifier = Modifier,
) {
    val isRoleplay = writingSubMode == "roleplay"
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = legadoCardBackgroundColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.ai_outline_anchor_section),
                    style = MaterialTheme.typography.titleSmall,
                )
                OutlineSectionBadge(stringResource(R.string.ai_outline_anchor_badge))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            OutlineLabeledField(
                label = stringResource(R.string.ai_outline_premise),
                value = document.premise,
                onValueChange = { if (!readOnly) onDocumentChange(document.copy(premise = it)) },
                readOnly = readOnly,
                singleLine = false,
                minLines = 2,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlineLabeledField(
                    label = stringResource(R.string.ai_outline_current),
                    value = document.currentProgress,
                    onValueChange = { if (!readOnly) onDocumentChange(document.copy(currentProgress = it)) },
                    readOnly = readOnly,
                    modifier = Modifier.weight(1f),
                )
                OutlineLabeledField(
                    label = stringResource(R.string.ai_outline_next),
                    value = document.nextGoal,
                    onValueChange = { if (!readOnly) onDocumentChange(document.copy(nextGoal = it)) },
                    readOnly = readOnly,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.ai_outline_in_progress),
                    style = MaterialTheme.typography.bodySmall,
                )
                Switch(
                    checked = document.inProgress,
                    onCheckedChange = { if (!readOnly) onDocumentChange(document.copy(inProgress = it)) },
                    enabled = !readOnly,
                )
            }
            // 线性/分支切换：作者模式固定线性，其余（角色扮演/聊天）可按需选结构。
            if (writingSubMode != "author") {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text(
                    stringResource(R.string.ai_outline_kind),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = document.outlineKind != OutlineDocument.KIND_BRANCHING,
                        onClick = {
                            if (!readOnly) {
                                onDocumentChange(
                                    document.copy(
                                        outlineKind = OutlineDocument.KIND_LINEAR,
                                        awaitingChoice = false,
                                    ),
                                )
                            }
                        },
                        enabled = !readOnly,
                        label = { Text(stringResource(R.string.ai_outline_kind_linear)) },
                    )
                    FilterChip(
                        selected = document.outlineKind == OutlineDocument.KIND_BRANCHING,
                        onClick = {
                            if (!readOnly) {
                                onDocumentChange(
                                    document.copy(outlineKind = OutlineDocument.KIND_BRANCHING),
                                )
                            }
                        },
                        enabled = !readOnly,
                        label = { Text(stringResource(R.string.ai_outline_kind_branching)) },
                    )
                }
            }
            if (isRoleplay) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlineFieldLabel(stringResource(R.string.ai_outline_active_path))
                    Text(
                        text = activePathLabel.ifBlank {
                            stringResource(R.string.ai_outline_active_path_empty)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (activePathLabel.isBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        stringResource(R.string.ai_outline_active_path_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.ai_outline_awaiting_choice),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            stringResource(
                                if (document.awaitingChoice) {
                                    R.string.ai_outline_awaiting_choice_on_hint
                                } else {
                                    R.string.ai_outline_awaiting_choice_hint
                                },
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (document.awaitingChoice) {
                        // System-owned gate: user may clear a stuck wait, but cannot invent one
                        // (that would pop branch options for pre-planted future forks).
                        TextButton(
                            onClick = {
                                if (!readOnly) {
                                    onDocumentChange(document.copy(awaitingChoice = false))
                                }
                            },
                            enabled = !readOnly,
                        ) {
                            Text(stringResource(R.string.ai_outline_awaiting_choice_clear))
                        }
                    } else {
                        Text(
                            stringResource(R.string.ai_outline_awaiting_choice_off),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
