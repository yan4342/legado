package io.legado.app.ui.book.info.network

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.DramaticRole
import kotlinx.collections.immutable.ImmutableList

@Composable
fun GroupedRelationList(
    nodes: ImmutableList<NetworkNodeUi>,
    edges: ImmutableList<NetworkEdgeUi>,
    selectedNodeId: String?,
    onSelectNode: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = remember(nodes, edges, selectedNodeId) {
        buildRelationGroups(nodes, edges, selectedNodeId)
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        groups.forEach { group ->
            item(key = "header_${group.nodeId}") {
                val roleRes = DramaticRole.labelRes(group.dramaticRole)
                Text(
                    text = buildString {
                        append(group.name)
                        if (roleRes != null) {
                            append(" · ")
                            append(stringResource(roleRes))
                        }
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (group.nodeId == selectedNodeId) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectNode(group.nodeId) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            // Prefix with nodeId: the same undirected edge appears under both endpoints.
            items(group.relations, key = { "${group.nodeId}_${it.edgeId}" }) { line ->
                ListItem(
                    modifier = Modifier.clickable {
                        onSelectNode(line.counterpartNodeId)
                    },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "—",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = line.label.ifBlank { "·" },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "—",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = line.counterpartName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            if (group.relations.isEmpty()) {
                item(key = "empty_${group.nodeId}") {
                    Text(
                        text = stringResource(R.string.character_network_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}
