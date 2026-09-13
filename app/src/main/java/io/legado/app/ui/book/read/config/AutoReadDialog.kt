package io.legado.app.ui.book.read.config

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import io.legado.app.R
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.lib.theme.bottomSheetBackground
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.ReadBookRouteState
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.ui.common.compose.legadoPopupPrimaryTextColor
import java.util.Locale

/** 自动翻页速度上限，与原布局 seekbar android:max 一致 */
private const val AUTO_READ_SPEED_MAX = 120

/**
 * 自动翻页速度控制面板（Compose 渲染）。阅读器底部弹出，通过
 * [ReadBookActivity.showDialogFragment] 打开，实现 [CallBack] 接收操作。
 */
class AutoReadDialog : DialogFragment() {

    private val callBack: CallBack? get() = activity as? CallBack

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = object : Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen) {}
        dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawable(requireContext().bottomSheetBackground)
            decorView.setPadding(0, 0, 0, 0)
            val attr = attributes
            attr.dimAmount = 0.0f
            attr.gravity = Gravity.BOTTOM
            attributes = attr
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
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
                    AutoReadPanel(
                        initialSpeed = if (ReadBookConfig.autoReadSpeed < 1) 1 else ReadBookConfig.autoReadSpeed,
                        onSpeedFinished = { speed ->
                            ReadBookConfig.autoReadSpeed = speed
                            upTtsSpeechRate()
                        },
                        onShowMenuBar = {
                            callBack?.showMenuBar()
                            dismissAllowingStateLoss()
                        },
                        onOpenChapterList = { callBack?.openChapterList() },
                        onPageStop = {
                            callBack?.autoPageStop()
                            dismissAllowingStateLoss()
                        },
                        onOpenPageAnimSetting = { openPageAnimSetting() },
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val bottomDialog = ReadBookRouteState.bumpBottomDialog(1)
        if (bottomDialog > 0) {
            dismiss()
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        ReadBookRouteState.bumpBottomDialog(-1)
    }

    private fun openPageAnimSetting() {
        ReadBookRouteState.controllerRef?.showPageAnimConfig {
            ReadBookRouteState.controllerRef?.upPageAnim()
            ReadBook.loadContent(false)
        }
    }

    private fun upTtsSpeechRate() {
        ReadAloud.upTtsSpeechRate(requireContext())
        if (!BaseReadAloudService.pause) {
            ReadAloud.pause(requireContext())
            ReadAloud.resume(requireContext())
        }
    }

    interface CallBack {
        fun showMenuBar()
        fun openChapterList()
        fun autoPageStop()
    }
}

/**
 * 面板内容：拖动条把手 + 自动翻页速度滑杆 + 目录/主菜单/停止/设置按钮行。
 * 文字与图标颜色统一取 [legadoPopupPrimaryTextColor]，不写死颜色值。
 */
@Composable
private fun AutoReadPanel(
    initialSpeed: Int,
    onSpeedFinished: (speed: Int) -> Unit,
    onShowMenuBar: () -> Unit,
    onOpenChapterList: () -> Unit,
    onPageStop: () -> Unit,
    onOpenPageAnimSetting: () -> Unit,
) {
    var speed by remember { mutableIntStateOf(initialSpeed) }
    val contentColor = legadoPopupPrimaryTextColor()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 8.dp)
                .size(width = 32.dp, height = 4.dp)
                .background(contentColor.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.auto_page_speed),
                color = contentColor,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = String.format(Locale.ROOT, "%ds", speed),
                color = contentColor,
                fontSize = 14.sp
            )
        }
        Slider(
            value = speed.toFloat(),
            onValueChange = { speed = it.toInt().coerceIn(1, AUTO_READ_SPEED_MAX) },
            onValueChangeFinished = { onSpeedFinished(speed.coerceIn(1, AUTO_READ_SPEED_MAX)) },
            valueRange = 1f..AUTO_READ_SPEED_MAX.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PanelButton(
                iconRes = R.drawable.ic_toc,
                label = stringResource(R.string.chapter_list),
                contentColor = contentColor,
                onClick = onOpenChapterList
            )
            Spacer(Modifier.weight(2f))
            PanelButton(
                iconRes = R.drawable.ic_menu,
                label = stringResource(R.string.main_menu),
                contentColor = contentColor,
                onClick = onShowMenuBar
            )
            Spacer(Modifier.weight(2f))
            PanelButton(
                iconRes = R.drawable.ic_auto_page_stop,
                label = stringResource(R.string.stop),
                contentColor = contentColor,
                onClick = onPageStop
            )
            Spacer(Modifier.weight(2f))
            PanelButton(
                iconRes = R.drawable.ic_settings,
                label = stringResource(R.string.setting),
                contentColor = contentColor,
                onClick = onOpenPageAnimSetting
            )
        }
    }
}

@Composable
private fun PanelButton(
    iconRes: Int,
    label: String,
    contentColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(50.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(bottom = 7.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            color = contentColor,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}
