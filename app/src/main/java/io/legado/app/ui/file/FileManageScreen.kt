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
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import io.legado.app.ui.common.compose.LegadoSearchBar
import io.legado.app.ui.common.compose.legadoCardBackgroundColor
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileManageScreen(
    state: FileManageUiState,
    onIntent: (FileManageIntent) -> Unit,
    onBack: () -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<File?>(null) }

    val isEInk = AppConfig.isEInkMode
    val topBarContainer = if (isEInk) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary
    val topBarContent = if (isEInk) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary

    val displayFiles = remember(state.files, state.searchQuery, state.parentFile) {
        if (state.searchQuery.isBlank()) state.files
        else state.files.filter {
            it == state.parentFile || it.name.contains(state.searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    LegadoSearchBar(
                        value = state.searchQuery,
                        onValueChange = { onIntent(FileManageIntent.Search(it)) },
                        placeholder = stringResource(R.string.screen),
                        tint = topBarContent,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarContainer,
                    navigationIconContentColor = topBarContent,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PathBreadcrumb(
                dirs = state.pathDirs,
                onClick = { index -> onIntent(FileManageIntent.NavigateBreadcrumb(index)) },
            )
            if (displayFiles.isEmpty()) {
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
                    items(displayFiles, key = { it.absolutePath }) { file ->
                        val isUp = file == state.parentFile
                        FileItemRow(
                            file = file,
                            isUp = isUp,
                            onClick = { onIntent(FileManageIntent.NavigateTo(file)) },
                            onLongClick = if (isUp) null else { { deleteTarget = file } },
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { file ->
        LegadoAlertDialog(
            show = true,
            onDismissRequest = { deleteTarget = null },
            dialogTitle = stringResource(R.string.del_file),
            text = stringResource(R.string.sure_del_any, file.name),
            confirmText = stringResource(R.string.delete),
            onConfirm = {
                onIntent(FileManageIntent.DeleteFile(file))
                deleteTarget = null
            },
            dismissText = stringResource(R.string.cancel),
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun PathBreadcrumb(
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
private fun FileItemRow(
    file: File,
    isUp: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val icon = when {
        isUp -> Icons.Filled.ArrowUpward
        file.isDirectory -> Icons.Filled.Folder
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
    val iconTint = if (file.isDirectory || isUp) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        color = legadoCardBackgroundColor(),
        tonalElevation = 1.dp,
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
