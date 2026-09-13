package io.legado.app.ui.book.import.remote

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.data.entities.Server
import io.legado.app.data.entities.rule.RowUi
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.ui.common.compose.legadoPopupBackgroundColor
import io.legado.app.ui.common.compose.legadoPopupPrimaryTextColor
import io.legado.app.utils.GSON

/**
 * WebDav 服务器编辑表单（Compose 渲染，M3 居中卡片样式）。
 * 原布局 dialog_webdav_server 已无其它使用者并随迁移删除。
 */
class ServerConfigDialog() : DialogFragment() {

    constructor(id: Long) : this() {
        arguments = Bundle().apply {
            putLong("id", id)
        }
    }

    private val viewModel by viewModels<ServerConfigViewModel>()

    private val webDavServerUi = listOf(
        RowUi("url"),
        RowUi("username"),
        RowUi("password", RowUi.Type.password)
    )

    private var name by mutableStateOf("")
    private var url by mutableStateOf("")
    private var username by mutableStateOf("")
    private var password by mutableStateOf("")

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = object : Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen) {}
        dialog.window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewModel.init(arguments?.getLong("id")) {
            upConfigView(viewModel.mServer)
        }
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    ServerConfigContent(
                        typeLabel = remember {
                            resources.getStringArray(R.array.server_type).firstOrNull() ?: Server.TYPE.WEBDAV.name
                        },
                        name = name,
                        onNameChange = { name = it },
                        url = url,
                        onUrlChange = { url = it },
                        username = username,
                        onUsernameChange = { username = it },
                        password = password,
                        onPasswordChange = { password = it },
                        onCancel = { dismissAllowingStateLoss() },
                        onSave = {
                            viewModel.save(getServer()) {
                                dismissAllowingStateLoss()
                            }
                        },
                    )
                }
            }
        }
    }

    private fun upConfigView(server: Server?) {
        name = server?.name ?: ""
        val config = server?.getConfigJsonObject()
        webDavServerUi.forEach { rowUi ->
            val value = runCatching { config?.getString(rowUi.name) }.getOrNull().orEmpty()
            when (rowUi.name) {
                "url" -> url = value
                "username" -> username = value
                "password" -> password = value
            }
        }
    }

    private fun getServer(): Server {
        val server = viewModel.mServer?.copy() ?: Server()
        server.name = name
        server.type = when (server.type) {
            else -> Server.TYPE.WEBDAV
        }
        server.config = when (server.type) {
            else -> GSON.toJson(getWebDavConfig())
        }
        return server
    }

    private fun getWebDavConfig(): HashMap<String, String> {
        val data = hashMapOf<String, String>()
        webDavServerUi.forEach { rowUi ->
            val value = when (rowUi.name) {
                "url" -> url
                "username" -> username
                "password" -> password
                else -> ""
            }
            data[rowUi.name] = value
        }
        return data
    }

}

/**
 * M3EditInputDialog 式居中表单：透明全屏壳 + 居中 Surface 卡片。
 * imePadding + verticalScroll 适配软键盘。
 */
@Composable
private fun ServerConfigContent(
    typeLabel: String,
    name: String,
    onNameChange: (String) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val popupBg = legadoPopupBackgroundColor()
    val popupTextColor = legadoPopupPrimaryTextColor()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = popupBg,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
            ) {
                Text(
                    text = stringResource(R.string.server_config),
                    style = MaterialTheme.typography.headlineSmall,
                    color = popupTextColor,
                )
                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.name)) },
                )
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "TYPE",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = popupTextColor,
                    )
                }
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("url") },
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("username") },
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("password") },
                    visualTransformation = PasswordVisualTransformation(),
                )
                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onSave) {
                        Text(stringResource(R.string.action_save), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
