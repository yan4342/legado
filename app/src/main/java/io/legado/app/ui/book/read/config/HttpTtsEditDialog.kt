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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.data.entities.HttpTTS
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.ui.common.compose.legadoPopupBackgroundColor
import io.legado.app.ui.common.compose.legadoPopupPrimaryTextColor
import io.legado.app.ui.common.compose.rememberLegadoColorScheme
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.utils.GSON
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showHelp
import io.legado.app.utils.showLogSheet
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi

/**
 * HTTP TTS 引擎编辑（Compose 实现）
 */
class HttpTtsEditDialog() : DialogFragment() {

    constructor(id: Long) : this() {
        arguments = Bundle().apply {
            putLong("id", id)
        }
    }

    private val viewModel by viewModels<HttpTtsEditViewModel>()

    // 表单状态
    private var name by mutableStateOf("")
    private var url by mutableStateOf("")
    private var contentType by mutableStateOf("")
    private var concurrentRate by mutableStateOf("")
    private var loginUrl by mutableStateOf("")
    private var loginUi by mutableStateOf("")
    private var loginCheckJs by mutableStateOf("")
    private var headers by mutableStateOf("")

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                (resources.displayMetrics.widthPixels * 0.95f).toInt(),
                (resources.displayMetrics.heightPixels * 0.95f).toInt()
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel.initData(arguments) {
            initView(it)
        }
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    HttpTtsEditScreen()
                }
            }
        }
    }

    private fun initView(httpTTS: HttpTTS) {
        name = httpTTS.name
        url = httpTTS.url
        contentType = httpTTS.contentType ?: ""
        concurrentRate = httpTTS.concurrentRate ?: ""
        loginUrl = httpTTS.loginUrl ?: ""
        loginUi = httpTTS.loginUi ?: ""
        loginCheckJs = httpTTS.loginCheckJs ?: ""
        headers = httpTTS.header ?: ""
    }

    private fun dataFromView(): HttpTTS {
        return HttpTTS(
            id = viewModel.id ?: System.currentTimeMillis(),
            name = name,
            url = url,
            contentType = contentType.ifBlank { null },
            concurrentRate = concurrentRate.ifBlank { null },
            loginUrl = loginUrl.ifBlank { null },
            loginUi = loginUi.ifBlank { null },
            loginCheckJs = loginCheckJs.ifBlank { null },
            header = headers.ifBlank { null }
        )
    }

    @Composable
    private fun HttpTtsEditScreen() {
        val popupBg = legadoPopupBackgroundColor()
        val popupTextColor = legadoPopupPrimaryTextColor()
        var menuExpanded by remember { mutableStateOf(false) }
        var loginHeaderText by remember { mutableStateOf<String?>(null) }

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = popupBg,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶栏
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.speak_engine),
                        style = MaterialTheme.typography.titleLarge,
                        color = popupTextColor,
                        modifier = Modifier.weight(1f).padding(start = 20.dp),
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
                                text = { Text(stringResource(R.string.action_save)) },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.save(dataFromView()) {
                                        requireContext().toastOnUi("保存成功")
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.login)) },
                                onClick = {
                                    menuExpanded = false
                                    dataFromView().let { httpTts ->
                                        if (httpTts.loginUrl.isNullOrBlank()) {
                                            requireContext().toastOnUi("登录url不能为空")
                                        } else {
                                            viewModel.save(httpTts) {
                                                startActivity<SourceLoginActivity> {
                                                    putExtra("type", "httpTts")
                                                    putExtra("key", httpTts.id.toString())
                                                }
                                            }
                                        }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.show_login_header)) },
                                onClick = {
                                    menuExpanded = false
                                    loginHeaderText = dataFromView().getLoginHeader() ?: ""
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.del_login_header)) },
                                onClick = {
                                    menuExpanded = false
                                    dataFromView().removeLoginHeader()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.copy_source)) },
                                onClick = {
                                    menuExpanded = false
                                    context?.sendToClip(GSON.toJson(dataFromView()))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.paste_source)) },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.importFromClip { initView(it) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.log)) },
                                onClick = {
                                    menuExpanded = false
                                    showLogSheet()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.help)) },
                                onClick = {
                                    menuExpanded = false
                                    showHelp("httpTTSHelp")
                                },
                            )
                        }
                    }
                    Spacer(Modifier.size(12.dp))
                }

                // 表单
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    FormField(stringResource(R.string.name), name) { name = it }
                    FormField("URL", url, minLines = 2) { url = it }
                    FormField("Content-Type", contentType) { contentType = it }
                    FormField(
                        stringResource(R.string.concurrent_rate),
                        concurrentRate
                    ) { concurrentRate = it }
                    FormField(stringResource(R.string.login_url), loginUrl, minLines = 2) { loginUrl = it }
                    FormField(stringResource(R.string.login_ui), loginUi, minLines = 3) { loginUi = it }
                    FormField(
                        stringResource(R.string.login_check_js),
                        loginCheckJs,
                        minLines = 3
                    ) { loginCheckJs = it }
                    FormField(stringResource(R.string.source_http_header), headers, minLines = 3) {
                        headers = it
                    }
                }
            }
        }

        if (loginHeaderText != null) {
            MaterialExpressiveTheme(
                colorScheme = rememberLegadoColorScheme(),
                typography = Typography(),
                motionScheme = MotionScheme.expressive(),
                shapes = Shapes(),
            ) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { loginHeaderText = null },
                    title = { Text(stringResource(R.string.login_header)) },
                    text = {
                        Text(
                            text = loginHeaderText!!.ifBlank {
                                stringResource(R.string.empty)
                            }
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { loginHeaderText = null }) {
                            Text(stringResource(R.string.ok))
                        }
                    },
                )
            }
        }
    }

    @Composable
    private fun FormField(
        label: String,
        value: String,
        minLines: Int = 1,
        onValueChange: (String) -> Unit,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            minLines = minLines,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
    }

}
