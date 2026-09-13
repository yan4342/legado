package io.legado.app.ui.book.info.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R

@Composable
fun GraphControlBar(
    simulationEnabled: Boolean,
    showSimulationToggle: Boolean,
    onFit: () -> Unit,
    onToggleSimulation: () -> Unit,
    onCenterHub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            IconButton(onClick = onFit) {
                Icon(
                    Icons.Default.FitScreen,
                    contentDescription = stringResource(R.string.character_network_fit),
                )
            }
            if (showSimulationToggle) {
                IconButton(onClick = onToggleSimulation) {
                    Icon(
                        if (simulationEnabled) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(
                            if (simulationEnabled) {
                                R.string.character_network_pause_sim
                            } else {
                                R.string.character_network_resume_sim
                            },
                        ),
                    )
                }
            }
            IconButton(onClick = onCenterHub) {
                Icon(
                    Icons.Default.CenterFocusStrong,
                    contentDescription = stringResource(R.string.character_network_center_hub),
                )
            }
        }
    }
}
