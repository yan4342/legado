@file:OptIn(ExperimentalMaterial3Api::class)

package io.legado.app.ui.book.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.common.compose.ModalLegadoBottomSheet

/**
 * 漫画阅读器操作弹层（移植自 MD3 c4cd6afdf，样式跟从本项目 MangaSettingsSheet）：
 * - 缓存 Sheet：本章 / 后续章节 / 整部漫画（FAB「离线缓存」入口，本地书不可用）
 * - 单页操作 Sheet：长按页面弹出，保存 / 分享 / 复制 / 设为封面 / 双页合成图 / 重试本章失败页
 */
@Composable
internal fun MangaReaderSheetsHost(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    if (state.activeSheet == MangaReaderSheet.CacheActions) {
        MangaReaderCacheActionsSheet(state, onIntent)
    }
    (state.activeSheet as? MangaReaderSheet.PageActions)?.let { sheet ->
        MangaReaderPageActionsSheet(
            hasCompanion = sheet.companionPageKey != null,
            chapterIndex = state.pages.firstOrNull { it.key == sheet.pageKey }
                .let { (it as? MangaReaderItemUi.Page)?.chapterIndex },
            onIntent = onIntent,
        )
    }
}

/** 缓存 Sheet：本章 / 后续 / 全部 */
@Composable
private fun MangaReaderCacheActionsSheet(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    ModalLegadoBottomSheet(
        show = true,
        onDismissRequest = { onIntent(MangaReaderIntent.DismissSheet) },
        title = stringResource(R.string.manga_reader_offline_cache),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            MangaSheetActionRow(
                icon = Icons.Filled.Download,
                label = stringResource(R.string.manga_reader_cache_current),
            ) {
                onIntent(MangaReaderIntent.CacheChapters(MangaCacheSelection.CURRENT))
            }
            MangaSheetActionRow(
                icon = Icons.Filled.CloudDownload,
                label = stringResource(R.string.manga_reader_cache_following),
            ) {
                onIntent(MangaReaderIntent.CacheChapters(MangaCacheSelection.FOLLOWING))
            }
            MangaSheetActionRow(
                icon = Icons.Filled.DownloadForOffline,
                label = stringResource(R.string.manga_reader_cache_all),
            ) {
                onIntent(MangaReaderIntent.CacheChapters(MangaCacheSelection.ALL))
            }
        }
    }
}

/** 单页操作 Sheet：长按页面弹出；双页模式下附合成图动作与重试本章失败页 */
@Composable
private fun MangaReaderPageActionsSheet(
    hasCompanion: Boolean,
    chapterIndex: Int?,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    ModalLegadoBottomSheet(
        show = true,
        onDismissRequest = { onIntent(MangaReaderIntent.DismissSheet) },
        title = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            MangaSheetActionRow(
                icon = Icons.Filled.Save,
                label = stringResource(R.string.save_image),
            ) {
                onIntent(MangaReaderIntent.ExecutePageAction(MangaPageAction.SAVE))
            }
            MangaSheetActionRow(
                icon = Icons.Filled.Share,
                label = stringResource(R.string.share),
            ) {
                onIntent(MangaReaderIntent.ExecutePageAction(MangaPageAction.SHARE))
            }
            MangaSheetActionRow(
                icon = Icons.Filled.ContentCopy,
                label = stringResource(R.string.copy_text),
            ) {
                onIntent(MangaReaderIntent.ExecutePageAction(MangaPageAction.COPY))
            }
            MangaSheetActionRow(
                icon = Icons.Filled.Image,
                label = stringResource(R.string.manga_reader_set_cover),
            ) {
                onIntent(MangaReaderIntent.ExecutePageAction(MangaPageAction.SET_COVER))
            }
            if (hasCompanion) {
                MangaSheetActionRow(
                    icon = Icons.Filled.Save,
                    label = stringResource(R.string.manga_reader_save_spread),
                ) {
                    onIntent(MangaReaderIntent.ExecutePageAction(MangaPageAction.SAVE_SPREAD))
                }
                MangaSheetActionRow(
                    icon = Icons.Filled.Share,
                    label = stringResource(R.string.manga_reader_share_spread),
                ) {
                    onIntent(MangaReaderIntent.ExecutePageAction(MangaPageAction.SHARE_SPREAD))
                }
                MangaSheetActionRow(
                    icon = Icons.Filled.ContentCopy,
                    label = stringResource(R.string.manga_reader_copy_spread),
                ) {
                    onIntent(MangaReaderIntent.ExecutePageAction(MangaPageAction.COPY_SPREAD))
                }
            }
            if (chapterIndex != null) {
                MangaSheetActionRow(
                    icon = Icons.AutoMirrored.Filled.Redo,
                    label = stringResource(R.string.manga_reader_retry_failed_chapter),
                ) {
                    onIntent(MangaReaderIntent.RetryFailedPagesInChapter(chapterIndex))
                }
            }
        }
    }
}

@Composable
private fun MangaSheetActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Spacer(Modifier.height(2.dp))
}
