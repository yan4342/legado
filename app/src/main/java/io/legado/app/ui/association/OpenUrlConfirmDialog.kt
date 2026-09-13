package io.legado.app.ui.association

import android.app.Dialog
import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.ui.common.compose.RoundDropdownMenu
import io.legado.app.ui.common.compose.legadoPopupBackgroundColor
import io.legado.app.ui.common.compose.legadoPopupPrimaryTextColor
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx

class OpenUrlConfirmDialog() : DialogFragment() {

    constructor(
        uri: String,
        mimeType: String?,
        sourceOrigin: String? = null,
        sourceName: String? = null,
        sourceType: Int
    ) : this() {
        arguments = Bundle().apply {
            putString("uri", uri)
            putString("mimeType", mimeType)
            putString("sourceOrigin", sourceOrigin)
            putString("sourceName", sourceName)
            putInt("sourceType", sourceType)
        }
    }

    val viewModel by viewModels<OpenUrlConfirmViewModel>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = object : Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen) {}
        dialog.window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.TRANSPARENT
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val arguments = arguments ?: run {
            dismissAllowingStateLoss()
            return View(requireContext())
        }
        viewModel.initData(arguments)
        if (viewModel.uri.isBlank()) {
            dismissAllowingStateLoss()
            return View(requireContext())
        }
        val sourceName = viewModel.sourceName
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    OpenUrlConfirmContent(
                        title = stringResource(R.string.open_url_confirm_title),
                        subtitle = sourceName,
                        message = stringResource(R.string.open_url_confirm_message, sourceName),
                        onDismiss = { dismiss() },
                        onConfirm = {
                            openUrl()
                            dismiss()
                        },
                        onDisableSource = {
                            viewModel.disableSource {
                                dismiss()
                            }
                        },
                        onDeleteSource = {
                            confirmDeleteSource()
                        },
                    )
                }
            }
        }
    }

    private fun confirmDeleteSource() {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + viewModel.sourceName)
            noButton()
            yesButton {
                viewModel.deleteSource {
                    dismiss()
                }
            }
        }
    }

    private fun openUrl() {
        try {
            val uri = viewModel.uri.toUri()
            val mimeType = viewModel.mimeType
            // 创建目标 Intent 并设置类型
            val targetIntent = Intent(Intent.ACTION_VIEW).apply {
                // 同时设置 Data 和 Type
                if (!mimeType.isNullOrBlank()) {
                    setDataAndType(uri, mimeType)
                } else {
                    data = uri
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // 验证是否有应用可以处理
            if (targetIntent.resolveActivity(appCtx.packageManager) != null) {
                startActivity(targetIntent)
            } else {
                toastOnUi(R.string.can_not_open)
            }
        } catch (e: Exception) {
            AppLog.put("打开链接失败", e, true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activity?.finish()
    }

}

@Composable
private fun OpenUrlConfirmContent(
    title: String,
    subtitle: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onDisableSource: () -> Unit,
    onDeleteSource: () -> Unit,
) {
    val popupBg = legadoPopupBackgroundColor()
    val popupTextColor = legadoPopupPrimaryTextColor()
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = popupBg,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = popupTextColor,
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = popupTextColor.copy(alpha = 0.7f),
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more_vert),
                                contentDescription = null,
                                tint = popupTextColor,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        RoundDropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) { dismissMenu ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.disable_source)) },
                                onClick = {
                                    dismissMenu()
                                    onDisableSource()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_source)) },
                                onClick = {
                                    dismissMenu()
                                    onDeleteSource()
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.padding(top = 16.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = popupTextColor,
                )
                Spacer(Modifier.padding(top = 24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onConfirm) {
                        Text(stringResource(R.string.ok), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
