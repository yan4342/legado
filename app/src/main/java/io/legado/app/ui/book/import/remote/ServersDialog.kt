package io.legado.app.ui.book.import.remote

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import io.legado.app.constant.AppConst.DEFAULT_WEBDAV_ID
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Server
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.common.compose.LegadoAlertDialog
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.utils.showDialogFragment
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * 服务器配置（Compose 渲染）。WebDav 列表：选择默认服务器、新建、编辑、删除。
 * 原共享布局 dialog_recycler_view 保留，仅 UI 迁移至 Compose。
 */
class ServersDialog : DialogFragment() {

    val viewModel by viewModels<ServersViewModel>()

    private val callback get() = (activity as? Callback)

    private var servers by mutableStateOf<List<Server>>(emptyList())
    private var selectServerId by mutableStateOf(AppConfig.remoteServerId)
    private var deleteCandidate by mutableStateOf<Server?>(null)

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

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            setBackgroundDrawableResource(R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        initData()
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    ServersScreen(
                        servers = servers,
                        selectServerId = selectServerId,
                        onSelect = { id -> selectServerId = id },
                        onAdd = { showDialogFragment(ServerConfigDialog()) },
                        onEdit = { server -> showDialogFragment(ServerConfigDialog(server.id)) },
                        onDeleteRequest = { server -> deleteCandidate = server },
                        onDefault = {
                            AppConfig.remoteServerId = DEFAULT_WEBDAV_ID
                            dismissAllowingStateLoss()
                        },
                        onCancel = { dismissAllowingStateLoss() },
                        onOk = {
                            AppConfig.remoteServerId = selectServerId
                            dismissAllowingStateLoss()
                        },
                    )
                    LegadoAlertDialog(
                        show = deleteCandidate != null,
                        onDismissRequest = { deleteCandidate = null },
                        dialogTitle = stringResource(R.string.draw),
                        text = stringResource(R.string.sure_del),
                        confirmText = stringResource(R.string.ok),
                        onConfirm = {
                            deleteCandidate?.let { viewModel.delete(it) }
                            deleteCandidate = null
                        },
                        dismissText = stringResource(R.string.cancel),
                        onDismiss = { deleteCandidate = null },
                    )
                }
            }
        }
    }

    private fun initData() {
        lifecycleScope.launch {
            appDb.serverDao.observeAll().catch {
                AppLog.put("服务器配置界面获取数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).collect {
                servers = it
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        callback?.onDialogDismiss("serversDialog")
    }

    interface Callback {

        fun onDialogDismiss(tag: String)

    }

}

/**
 * 全屏列表内容：顶部标题栏（含新建）+ 服务器列表 + 底部（默认/取消/确定）。
 */
@Composable
private fun ServersScreen(
    servers: List<Server>,
    selectServerId: Long,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Server) -> Unit,
    onDeleteRequest: (Server) -> Unit,
    onDefault: () -> Unit,
    onCancel: () -> Unit,
    onOk: () -> Unit,
) {
    val backgroundColor = MaterialTheme.colorScheme.background

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.server_config),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onAdd) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.create),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(servers, key = { it.id }) { server ->
                ServerItem(
                    server = server,
                    selected = server.id == selectServerId,
                    onSelect = onSelect,
                    onEdit = onEdit,
                    onDeleteRequest = onDeleteRequest,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDefault) {
                Text(text = stringResource(R.string.text_default), color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) {
                Text(text = stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onOk) {
                Text(text = stringResource(R.string.ok), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ServerItem(
    server: Server,
    selected: Boolean,
    onSelect: (Long) -> Unit,
    onEdit: (Server) -> Unit,
    onDeleteRequest: (Server) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(server.id) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = { onSelect(server.id) })
            Column(
                modifier = Modifier.weight(1f).padding(vertical = 6.dp),
            ) {
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                if (!server.getWebDavConfig()?.url.isNullOrEmpty()) {
                    Text(
                        text = server.getWebDavConfig()?.url.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = { onEdit(server) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_edit),
                    contentDescription = stringResource(R.string.edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(onClick = { onDeleteRequest(server) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_outline_delete),
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}
