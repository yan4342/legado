package io.legado.app.ui.file

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.common.compose.LegadoAlertDialog
import io.legado.app.ui.common.compose.legadoCardBackgroundColor
import io.legado.app.utils.FileUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FilePickerScreen(
    state: FilePickerUiState,
    onIntent: (FilePickerIntent) -> Unit,
    onBack: () -> Unit,
) {
    var showCreateFolder by remember { mutableStateOf(false) }

    val isEInk = AppConfig.isEInkMode
    val topBarContainer = if (isEInk) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary
    val topBarContent = if (isEInk) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary

    Scaffold(
        containerColor = legadoCardBackgroundColor(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (state.isSelectDir) R.string.folder_chooser else R.string.file_chooser),
                        color = topBarContent,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarContainer,
                    navigationIconContentColor = topBarContent,
                    actionIconContentColor = topBarContent,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateFolder = true }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = stringResource(R.string.create_folder))
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = topBarContainer, tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.cancel), color = topBarContent)
                    }
                    TextButton(onClick = { onIntent(FilePickerIntent.Confirm) }) {
                        Text(stringResource(R.string.ok), color = topBarContent)
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FilePickerBreadcrumb(
                dirs = state.pathDirs,
                onClick = { index -> onIntent(FilePickerIntent.NavigateBreadcrumb(index)) },
            )
            if (state.files.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.no_files),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
                ) {
                    items(state.files, key = { it.absolutePath }) { file ->
                        val isUp = file == state.parentFile
                        val isFileSelectable =
                            !state.isSelectDir && isAllowed(file, state.allowExtensions)
                        val isSelectable = isUp || file.isDirectory || isFileSelectable
                        FilePickerItemRow(
                            file = file,
                            isUp = isUp,
                            isSelectable = isSelectable,
                            isSelected = file == state.selectedFile,
                            onClick = { onIntent(FilePickerIntent.NavigateTo(file)) },
                        )
                    }
                }
            }
        }
    }

    if (showCreateFolder) {
        var name by remember { mutableStateOf("") }
        LegadoAlertDialog(
            show = true,
            onDismissRequest = { showCreateFolder = false },
            dialogTitle = stringResource(R.string.create_folder),
            confirmText = stringResource(R.string.ok),
            onConfirm = {
                if (name.isNotBlank()) onIntent(FilePickerIntent.CreateFolder(name.trim()))
                showCreateFolder = false
            },
            dismissText = stringResource(R.string.cancel),
            onDismiss = { showCreateFolder = false },
            content = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }
}

@Composable
private fun FilePickerBreadcrumb(
    dirs: kotlin.collections.List<File>,
    onClick: (index: Int) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(legadoCardBackgroundColor()),
        contentPadding = PaddingValues(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "root") {
            Row(
                Modifier
                    .clickable { onClick(0) }
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Home,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "root",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        items(dirs.size, key = { it }) { index ->
            val dir = dirs[index]
            Row(
                Modifier
                    .clickable { onClick(index + 1) }
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    dir.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FilePickerItemRow(
    file: File,
    isUp: Boolean,
    isSelectable: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val icon = when {
        isUp -> Icons.Filled.ArrowUpward
        file.isDirectory -> Icons.Filled.Folder
        else -> Icons.Filled.InsertDriveFile
    }
    val iconTint = when {
        !isSelectable -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        isUp || file.isDirectory -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val labelColor = if (isSelectable) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(enabled = isSelectable, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            legadoCardBackgroundColor()
        },
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                if (isUp) ".." else file.name,
                style = MaterialTheme.typography.bodyLarge,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (isSelected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp)
}

private fun isAllowed(file: File, allowExtensions: kotlin.collections.List<String>): Boolean {
    return allowExtensions.isEmpty() || allowExtensions.contains(FileUtils.getExtension(file.path))
}
