package io.legado.app.ui.book.read.config

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.ui.common.compose.legadoPopupBackgroundColor
import io.legado.app.ui.common.compose.legadoPopupPrimaryTextColor
import io.legado.app.utils.getPrefString
import io.legado.app.utils.hideSoftInput
import io.legado.app.utils.putPrefString
import io.legado.app.utils.setLayout

/**
 * 自定义翻页按键设置对话框（Compose 渲染），阅读器通过
 * [io.legado.app.utils.showDialogFragment] 打开。
 * 聚焦输入框后按实体键会自动填入对应 keyCode，多个按键以逗号分隔。
 */
class PageKeyDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = object : Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen) {}
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.TRANSPARENT
            attributes = attributes.apply { dimAmount = 0.6f }
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setGravity(Gravity.CENTER)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        return ComposeView(context).apply {
            setContent {
                LegadoTheme {
                    PageKeyScreen(
                        initialPrev = context.getPrefString(PreferKey.prevKeys).orEmpty(),
                        initialNext = context.getPrefString(PreferKey.nextKeys).orEmpty(),
                        onDismiss = { dismiss() },
                        onConfirm = { prev, next ->
                            context.putPrefString(PreferKey.prevKeys, prev)
                            context.putPrefString(PreferKey.nextKeys, next)
                            dismiss()
                        },
                    )
                }
            }
        }
    }

    override fun dismiss() {
        dialog?.window?.currentFocus?.hideSoftInput()
        super.dismiss()
    }
}

/**
 * 弹窗内容：标题 + 上翻/下翻输入框 + 帮助文案 + 重置/确定按钮，
 * 配色取 [legadoPopupBackgroundColor]/[legadoPopupPrimaryTextColor]，圆角与原 filletBackground 一致。
 */
@Composable
private fun PageKeyScreen(
    initialPrev: String,
    initialNext: String,
    onDismiss: () -> Unit,
    onConfirm: (prev: String, next: String) -> Unit,
) {
    var prevKeys by remember { mutableStateOf(initialPrev) }
    var nextKeys by remember { mutableStateOf(initialNext) }
    val popupTextColor = legadoPopupPrimaryTextColor()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = legadoPopupBackgroundColor(),
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.custom_page_key),
                color = popupTextColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(5.dp),
            )
            KeyInputField(
                value = prevKeys,
                onValueChange = { prevKeys = it },
                label = stringResource(R.string.prev_page_key),
                modifier = Modifier.padding(vertical = 5.dp),
            )
            KeyInputField(
                value = nextKeys,
                onValueChange = { nextKeys = it },
                label = stringResource(R.string.next_page_key),
                modifier = Modifier.padding(vertical = 5.dp),
            )
            Text(
                text = stringResource(R.string.page_key_set_help),
                color = popupTextColor.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.padding(5.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                Button(
                    onClick = {
                        prevKeys = ""
                        nextKeys = ""
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.reset))
                }
                Spacer(modifier = Modifier.width(3.dp))
                Button(
                    onClick = { onConfirm(prevKeys, nextKeys) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.ok))
                }
            }
        }
    }
}

/**
 * 按键输入框：聚焦时拦截非返回/删除的实体按键事件，把 keyCode 追加进文本，
 * 不再走普通字符插入，与原 Dialog.onKeyDown 行为一致。
 */
@Composable
private fun KeyInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(text = label) },
        modifier = modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { keyEvent ->
                val keyCode = keyEvent.nativeKeyEvent.keyCode
                if (keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_DEL) {
                    onValueChange(value.appendKeyCode(keyCode))
                    true
                } else {
                    false
                }
            },
    )
}

/** 空文本或以逗号结尾时直接追加，否则先补逗号，与原版拼接规则一致 */
private fun String.appendKeyCode(keyCode: Int): String =
    if (isEmpty() || endsWith(",")) this + keyCode else "$this,$keyCode"
