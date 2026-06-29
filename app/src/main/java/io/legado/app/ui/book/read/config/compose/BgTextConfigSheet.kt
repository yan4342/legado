@file:OptIn(ExperimentalMaterial3Api::class)

package io.legado.app.ui.book.read.config.compose

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toColorInt
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.help.DefaultData
import io.legado.app.help.book.isImage
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.selector
import io.legado.app.model.ReadBook
import io.legado.app.ui.common.compose.ModalLegadoBottomSheet
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.createFileIfNotExist
import io.legado.app.utils.createFileReplace
import io.legado.app.utils.createFolderReplace
import io.legado.app.utils.delete
import io.legado.app.utils.externalCache
import io.legado.app.utils.externalFiles
import io.legado.app.utils.find
import io.legado.app.utils.getFile
import io.legado.app.utils.inputStream
import io.legado.app.utils.openInputStream
import io.legado.app.utils.openOutputStream
import io.legado.app.utils.outputStream
import io.legado.app.utils.postEvent
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.readBytes
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.GSON
import splitties.init.appCtx
import java.io.File
import java.io.FileOutputStream

@Composable
fun BgTextConfigSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    onTextColorClick: (Int) -> Unit,
    onBgColorClick: (Int) -> Unit,
) {
    if (!show) return

    val context = LocalContext.current
    val durConfig = remember { ReadBookConfig.durConfig }

    var bgAlpha by remember { mutableFloatStateOf(durConfig.bgAlpha.toFloat()) }
    var underLine by remember { mutableStateOf(durConfig.underline) }
    var darkStatusIcon by remember { mutableStateOf(durConfig.curStatusIconDark()) }

    val bgAssets = remember {
        runCatching { context.assets.list("bg")?.toList().orEmpty() }.getOrDefault(emptyList())
    }

    val selectBgImage = rememberLauncherForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            kotlin.runCatching {
                val inputStream = uri.inputStream(context).getOrThrow()
                val suffix = uri.path?.substringAfterLast(".") ?: "jpg"
                val fileName = inputStream.use { stream ->
                    MD5Utils.md5Encode(stream) + ".$suffix"
                }
                val freshStream = uri.inputStream(context).getOrThrow()
                var file = context.externalFiles
                file = FileUtils.createFileIfNotExist(file, "bg", fileName)
                FileOutputStream(file).use { outputStream ->
                    freshStream.copyTo(outputStream)
                }
                freshStream.close()
                ReadBookConfig.durConfig.setCurBg(2, fileName)
                postEvent(EventBus.UP_CONFIG, listOf(1))
            }.onFailure {
                context.toastOnUi(it.localizedMessage)
            }
        }
    }

    val selectExportDir = rememberLauncherForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            exportConfig(context, uri)
        }
    }

    val selectImportDoc = rememberLauncherForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            if (uri.path == "/网络导入") {
                context.toastOnUi("网络导入暂不支持")
            } else {
                val bytes = uri.readBytes(context)
                importConfig(bytes)
            }
        }
    }

    val isImageBook = ReadBook.book?.isImage == true

    ModalLegadoBottomSheet(
        show = show,
        onDismissRequest = {
            ReadBookConfig.save()
            onDismiss()
        },
        title = "",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            // ── Name row ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${stringResource(R.string.style_name)}:",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = durConfig.name.ifBlank { "Aa" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.restore),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable {
                            val defaultConfigs = DefaultData.readConfigs
                            val layoutNames = defaultConfigs.map { it.name }
                            context.selector("选择预设布局", layoutNames as List<CharSequence>) { _, i ->
                                if (i >= 0) {
                                    ReadBookConfig.durConfig = defaultConfigs[i].copy()
                                    bgAlpha = ReadBookConfig.durConfig.bgAlpha.toFloat()
                                    underLine = ReadBookConfig.durConfig.underline
                                    darkStatusIcon = ReadBookConfig.durConfig.curStatusIconDark()
                                    postEvent(EventBus.UP_CONFIG, listOf(1, 2, 5))
                                }
                            }
                        }
                        .padding(4.dp),
                )
            }

            // ── Toggle: Dark status icon ──
            SwitchRow(
                label = stringResource(R.string.dark_status_icon),
                checked = darkStatusIcon,
                onCheckedChange = {
                    darkStatusIcon = it
                    durConfig.setCurStatusIconDark(it)
                },
            )

            // ── Toggle: Underline (hidden for image books) ──
            if (!isImageBook) {
                SwitchRow(
                    label = stringResource(R.string.text_underline),
                    checked = underLine,
                    onCheckedChange = {
                        underLine = it
                        ReadBookConfig.underline = it
                        postEvent(EventBus.UP_CONFIG, listOf(6, 9, 11))
                    },
                )
            }

            // ── Row: Text color | Bg color | Import | Export | Delete ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.text_color),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .weight(2f)
                        .clickable { onTextColorClick(durConfig.curTextColor()) }
                        .padding(vertical = 6.dp, horizontal = 6.dp),
                )
                Text(
                    text = stringResource(R.string.bg_color),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .weight(2f)
                        .clickable { onBgColorClick(durConfig.curBgStr().toColorInt()) }
                        .padding(vertical = 6.dp, horizontal = 6.dp),
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable {
                            selectImportDoc.launch {
                                mode = HandleFileContract.FILE
                                title = context.getString(R.string.import_str)
                                allowExtensions = arrayOf("zip")
                                otherActions = arrayListOf(SelectItem("网络导入", -1))
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_import),
                        contentDescription = stringResource(R.string.import_str),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable {
                            selectExportDir.launch { title = context.getString(R.string.export_str) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_export),
                        contentDescription = stringResource(R.string.export_str),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable {
                            if (ReadBookConfig.deleteDur()) {
                                postEvent(EventBus.UP_CONFIG, listOf(1, 2, 12, 5))
                                onDismiss()
                            } else {
                                context.toastOnUi("数量已是最少,不能删除.")
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_clear_all),
                        contentDescription = stringResource(R.string.delete),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // ── Background alpha ──
            Text(
                text = stringResource(R.string.bg_alpha),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Slider(
                    value = bgAlpha,
                    onValueChange = {
                        bgAlpha = it
                        ReadBookConfig.bgAlpha = it.toInt()
                        postEvent(EventBus.UP_CONFIG, listOf(3))
                    },
                    valueRange = 0f..255f,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${bgAlpha.toInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(32.dp),
                )
            }

            // ── Background images ──
            Text(
                text = stringResource(R.string.bg_image),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(76.dp),
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(LocalContentColor.current.copy(alpha = 0.1f))
                            .clickable {
                                selectBgImage.launch { mode = HandleFileContract.IMAGE }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_image),
                            contentDescription = stringResource(R.string.select_image),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                items(bgAssets) { assetName ->
                    val bitmap = remember(assetName) {
                        runCatching {
                            context.assets.open("bg/$assetName").use { stream ->
                                android.graphics.BitmapFactory.decodeStream(stream)
                            }
                        }.getOrNull()
                    }
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(LocalContentColor.current.copy(alpha = 0.1f))
                            .clickable {
                                kotlin.runCatching {
                                    val inputStream = context.assets.open("bg/$assetName")
                                    var file = context.externalFiles
                                    file = FileUtils.createFileIfNotExist(file, "bg", assetName)
                                    FileOutputStream(file).use { out ->
                                        inputStream.copyTo(out)
                                    }
                                    inputStream.close()
                                    ReadBookConfig.durConfig.setCurBg(2, assetName)
                                    postEvent(EventBus.UP_CONFIG, listOf(1))
                                }.onFailure {
                                    context.toastOnUi(it.localizedMessage)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = assetName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(76.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun exportConfig(context: android.content.Context, uri: Uri) {
    val configFileName = "readConfig.zip"
    Coroutine.async {
        val exportFiles = arrayListOf<File>()
        val configDir = context.externalCache.getFile("readConfig")
        configDir.createFolderReplace()
        val configFile = configDir.getFile("readConfig.json")
        configFile.createFileReplace()
        val config = ReadBookConfig.getExportConfig()
        val fontPath = ReadBookConfig.textFont
        if (fontPath.isNotEmpty()) {
            val fontDoc = FileDoc.fromFile(fontPath)
            val fontName = fontDoc.name
            val fontInputStream = fontDoc.openInputStream().getOrNull()
            if (fontInputStream != null) {
                val fontExportFile = FileUtils.createFileIfNotExist(configDir, fontName)
                val out = fontExportFile.outputStream()
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fontInputStream.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                }
                out.close()
                fontInputStream.close()
                config.textFont = fontName
                exportFiles.add(fontExportFile)
            }
        }
        configFile.writeText(GSON.toJson(config))
        exportFiles.add(configFile)
        repeat(3) {
            val path = ReadBookConfig.durConfig.getBgPath(it) ?: return@repeat
            val bgFile = File(path)
            if (bgFile.exists()) {
                val bgName = FileUtils.getName(path)
                val bgExportFile = File(FileUtils.getPath(configDir, bgName))
                if (!bgExportFile.exists()) {
                    bgFile.copyTo(bgExportFile)
                }
                exportFiles.add(bgExportFile)
            }
        }
        val configZipPath = FileUtils.getPath(context.externalCache, configFileName)
        if (ZipUtils.zipFiles(exportFiles, File(configZipPath))) {
            val exportFileName = if (ReadBookConfig.config.name.isBlank()) configFileName
            else "${ReadBookConfig.config.name}.zip"
            val exportDir = FileDoc.fromDir(uri)
            exportDir.find(exportFileName)?.delete()
            val exportFileDoc = exportDir.createFileIfNotExist(exportFileName)
            exportFileDoc.openOutputStream().getOrThrow().use { out ->
                File(configZipPath).inputStream().use { it.copyTo(out) }
            }
        }
    }.onSuccess {
        context.toastOnUi("导出成功")
    }.onError {
        it.printOnDebug()
        context.toastOnUi("导出失败:${it.localizedMessage}")
    }
}

private fun importConfig(byteArray: ByteArray) {
    Coroutine.async {
        ReadBookConfig.import(byteArray)
    }.onSuccess {
        ReadBookConfig.durConfig = it
        postEvent(EventBus.UP_CONFIG, listOf(1, 2, 5))
        appCtx.toastOnUi("导入成功")
    }.onError {
        it.printOnDebug()
        appCtx.toastOnUi("导入失败:${it.localizedMessage}")
    }
}
