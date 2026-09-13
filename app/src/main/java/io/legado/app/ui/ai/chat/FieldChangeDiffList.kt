package io.legado.app.ui.ai.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FieldChangeDiffList(
    changes: List<FieldChangeUi>,
    modifier: Modifier = Modifier,
    maxHeightDp: Int = 200,
    showCheckboxes: Boolean = false,
    onToggleField: ((String) -> Unit)? = null,
) {
    if (changes.isEmpty()) return
    val scrollState = rememberScrollState()
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeightDp.dp)
            .verticalScroll(scrollState),
    ) {
        changes.forEach { change ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                if (showCheckboxes && onToggleField != null) {
                    Checkbox(
                        checked = change.checked,
                        onCheckedChange = { onToggleField(change.path) },
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    if (change.oldValue.isNotBlank()) {
                        Text(
                            "- ${change.path}: ${change.oldValue}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.error,
                        )
                    }
                    if (change.newValue.isNotBlank()) {
                        Text(
                            "+ ${change.path}: ${change.newValue}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
