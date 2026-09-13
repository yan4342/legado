package io.legado.app.ui.book.read.config

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.HttpTTS
import io.legado.app.utils.showM3EditDialog
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.ui.association.ImportHttpTtsDialog
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.ui.common.compose.legadoPopupBackgroundColor
import io.legado.app.ui.common.compose.legadoPopupPrimaryTextColor
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.utils.ACache
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.startActivity
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * tts引擎管理（Compose 实现）
 */
class SpeakEngineDialog() : DialogFragment() {

    private val viewModel: SpeakEngineViewModel by viewModels()
    private val ttsUrlKey = "ttsUrlKey"
    private var ttsEngine: String? = ReadAloud.ttsEngine

    // Compose 状态
    private var engines by mutableStateOf<List<HttpTTS>>(emptyList())
    private var menuExpanded by mutableStateOf(false)

    private val callBack: CallBack? get() = parentFragment as? CallBack

    private val importDocResult = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            showDialogFragment(ImportHttpTtsDialog(uri.toString()))
        }
    }
    private val exportDirResult = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            showM3EditDialog(
                titleRes = R.string.export_success,
                initialValue = uri.toString(),
                hintRes = R.string.path,
                onConfirm = {
                    requireContext().sendToClip(uri.toString())
                },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            (requireContext().resources.displayMetrics.heightPixels * 0.9f).toInt()
        )
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            appDb.httpTTSDao.flowAll().catch {
                AppLog.put("朗读引擎界面获取数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).conflate().collect {
                engines = it
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    SpeakEngineScreen()
                }
            }
        }
    }

    private fun upTts(tts: String) {
        ttsEngine = tts
    }

    private fun isSysChecked(value: String): Boolean =
        GSON.fromJsonObject<SelectItem<String>>(ttsEngine).getOrNull()?.value == value

    private fun importAlert() {
        val aCache = ACache.get(cacheDir = false)
        val cacheUrls: MutableList<String> = aCache
            .getAsString(ttsUrlKey)
            ?.splitNotBlank(",")
            ?.toMutableList() ?: mutableListOf()
        showM3EditDialog(
            title = getString(R.string.import_on_line),
            hint = "url",
            suggestions = cacheUrls,
            onConfirm = { url ->
                if (url.isAbsUrl() && !cacheUrls.contains(url)) {
                    cacheUrls.add(0, url)
                    aCache.put(ttsUrlKey, cacheUrls.joinToString(","))
                }
                showDialogFragment(ImportHttpTtsDialog(url))
            },
        )
    }

    @Composable
    private fun SpeakEngineScreen() {
        val popupBg = legadoPopupBackgroundColor()
        val popupTextColor = legadoPopupPrimaryTextColor()

        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp)) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = popupBg,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp, start = 12.dp, end = 12.dp, bottom = 8.dp),
                ) {
                    // 顶栏：标题 + 溢出菜单
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.speak_engine),
                            style = MaterialTheme.typography.titleLarge,
                            color = popupTextColor,
                            modifier = Modifier.weight(1f).padding(start = 4.dp),
                        )
                        Box {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = null,
                                tint = popupTextColor,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable { menuExpanded = true },
                            )
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.add)) },
                                    onClick = {
                                        menuExpanded = false
                                        showDialogFragment<HttpTtsEditDialog>()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.import_default_rule)) },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.importDefault()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.import_local)) },
                                    onClick = {
                                        menuExpanded = false
                                        importDocResult.launch {
                                            mode = HandleFileContract.FILE
                                            allowExtensions = arrayOf("txt", "json")
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.import_on_line)) },
                                    onClick = {
                                        menuExpanded = false
                                        importAlert()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.export)) },
                                    onClick = {
                                        menuExpanded = false
                                        exportDirResult.launch {
                                            mode = HandleFileContract.EXPORT
                                            fileData = HandleFileContract.FileData(
                                                "httpTts.json",
                                                GSON.toJson(engines).toByteArray(),
                                                "application/json"
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(top = 4.dp),
                    ) {
                        // 系统默认头
                        item {
                            EngineRow(
                                label = "系统默认",
                                checked = ttsEngine == null || ttsEngine!!.isJsonObject()
                                        && GSON.fromJsonObject<SelectItem<String>>(ttsEngine)
                                    .getOrNull()?.value.isNullOrEmpty(),
                                showActions = false,
                                systemLabel = "SYS",
                                onSelect = { upTts(GSON.toJson(SelectItem("系统默认", ""))) },
                            )
                        }
                        // 系统引擎头
                        items(viewModel.sysEngines, key = { "sys_" + it.name }) { engine ->
                            EngineRow(
                                label = engine.label,
                                checked = isSysChecked(engine.name ?: ""),
                                showActions = false,
                                systemLabel = "SYS",
                                onSelect = {
                                    upTts(GSON.toJson(SelectItem(engine.label, engine.name)))
                                },
                            )
                        }
                        // HTTP TTS 列表
                        items(engines, key = { it.id }) { httpTTS ->
                            EngineRow(
                                label = httpTTS.name,
                                checked = httpTTS.id.toString() == ttsEngine,
                                showActions = true,
                                systemLabel = "",
                                onSelect = {
                                    val id = httpTTS.id.toString()
                                    upTts(id)
                                    if (!httpTTS.loginUrl.isNullOrBlank()
                                        && httpTTS.getLoginInfo().isNullOrBlank()
                                    ) {
                                        startActivity<SourceLoginActivity> {
                                            putExtra("type", "httpTts")
                                            putExtra("key", id)
                                        }
                                    }
                                },
                                onEdit = {
                                    showDialogFragment(HttpTtsEditDialog(httpTTS.id))
                                },
                                onDelete = {
                                    appDb.httpTTSDao.delete(httpTTS)
                                },
                            )
                        }
                    }

                    // 底部操作行
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TextButton(onClick = {
                            ReadBook.book?.setTtsEngine(ttsEngine)
                            callBack?.upSpeakEngineSummary()
                            ReadAloud.upReadAloudClass()
                            dismissAllowingStateLoss()
                        }) {
                            Text(stringResource(R.string.book), color = popupTextColor)
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { dismissAllowingStateLoss() }) {
                            Text(stringResource(R.string.cancel))
                        }
                        TextButton(onClick = {
                            ReadBook.book?.setTtsEngine(null)
                            AppConfig.ttsEngine = ttsEngine
                            callBack?.upSpeakEngineSummary()
                            ReadAloud.upReadAloudClass()
                            dismissAllowingStateLoss()
                        }) {
                            Text(stringResource(R.string.general))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun EngineRow(
        label: String,
        checked: Boolean,
        showActions: Boolean,
        systemLabel: String,
        onSelect: () -> Unit,
        onEdit: (() -> Unit)? = null,
        onDelete: (() -> Unit)? = null,
    ) {
        val textColor = legadoPopupPrimaryTextColor()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect() }
                .padding(vertical = 2.dp),
        ) {
            RadioButton(selected = checked, onClick = null)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            if (showActions) {
                Icon(
                    painter = painterResource(R.drawable.ic_edit),
                    contentDescription = stringResource(R.string.edit),
                    tint = textColor.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { onEdit?.invoke() }
                        .padding(2.dp),
                )
                Icon(
                    painter = painterResource(R.drawable.ic_clear_all),
                    contentDescription = stringResource(R.string.delete),
                    tint = textColor.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onDelete?.invoke() }
                        .padding(2.dp),
                )
            } else if (systemLabel.isNotEmpty()) {
                Text(
                    text = systemLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.5f),
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }

    interface CallBack {
        fun upSpeakEngineSummary()
    }

}
