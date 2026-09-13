package io.legado.app.ui.book.read.config

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.ReadBookRouteState
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.ui.common.compose.legadoPopupBackgroundColor
import io.legado.app.ui.common.compose.legadoPopupPrimaryTextColor
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.observeEvent
import io.legado.app.utils.toastOnUi

/**
 * 朗读面板（底部弹层，Compose 实现）。
 */
class ReadAloudDialog : DialogFragment() {

    private val callBack: CallBack? get() = activity as? CallBack

    private var isPaused by mutableStateOf(false)
    private var timerProgress by mutableStateOf(0)
    private var ttsFollowSys by mutableStateOf(true)
    private var speechRate by mutableStateOf(0)
    private var showTimePicker by mutableStateOf(false)

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawableResource(android.R.color.transparent)
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
        speechRate = AppConfig.ttsSpeechRate
        ttsFollowSys = requireContext().getPrefBoolean("ttsFollowSys", true)
        timerProgress = if (BaseReadAloudService.timeMinute > 0) {
            BaseReadAloudService.timeMinute
        } else {
            AppConfig.ttsTimer
        }
        isPaused = BaseReadAloudService.pause
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    ReadAloudSheetContent()
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val bottomDialog = ReadBookRouteState.bumpBottomDialog(1)
        if (bottomDialog > 0) {
            dismissAllowingStateLoss()
            return
        }
        observeEvent<Int>(EventBus.ALOUD_STATE) { isPaused = BaseReadAloudService.pause }
        observeEvent<Int>(EventBus.READ_ALOUD_DS) { timerProgress = it }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        ReadBookRouteState.bumpBottomDialog(-1)
    }

    private fun upTtsSpeechRate() {
        ReadAloud.upTtsSpeechRate(requireContext())
        if (!BaseReadAloudService.pause) {
            ReadAloud.pause(requireContext())
            ReadAloud.resume(requireContext())
        }
    }

    @Composable
    private fun ReadAloudSheetContent() {
        val context = LocalContext.current
        val bg = context.bottomBackground
        val textColor = Color(context.getPrimaryTextColor(ColorUtils.isColorLight(bg)))
        val accentColor = MaterialTheme.colorScheme.primary

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(bg))
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            // 拖动手柄
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .width(32.dp)
                        .height(4.dp)
                        .background(textColor.copy(alpha = 0.2f), RoundedCornerShape(2.dp)),
                )
            }

            Text(
                text = stringResource(R.string.switch_to_read_aloud_player),
                color = accentColor,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        dismissAllowingStateLoss()
                        callBack?.showReadAloudPlayer()
                    }
                    .padding(vertical = 10.dp),
                textAlign = TextAlign.Center,
            )

            // 章节 + 句子控制
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.previous_chapter),
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .clickable { ReadBook.moveToPrevChapter(upContent = true, toLast = false) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                )
                Spacer(Modifier.weight(1f))
                SheetIcon(R.drawable.ic_skip_previous, stringResource(R.string.prev_sentence), textColor) {
                    ReadAloud.prevParagraph(requireContext())
                }
                SheetIcon(
                    if (isPaused) R.drawable.ic_play_24dp else R.drawable.ic_pause_24dp,
                    if (isPaused) stringResource(R.string.audio_play) else stringResource(R.string.pause),
                    textColor,
                ) { callBack?.onClickReadAloud() }
                SheetIcon(R.drawable.ic_stop_black_24dp, stringResource(R.string.stop), textColor) {
                    ReadAloud.stop(requireContext())
                    dismissAllowingStateLoss()
                }
                SheetIcon(R.drawable.ic_skip_next, stringResource(R.string.next_sentence), textColor) {
                    ReadAloud.nextParagraph(requireContext())
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.next_chapter),
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .clickable { ReadBook.moveToNextChapter(true) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                )
            }

            // 定时
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SheetIcon(R.drawable.ic_time_add_24dp, stringResource(R.string.set_timer), textColor) {
                    AppConfig.ttsTimer = timerProgress
                    requireContext().toastOnUi("保存设定时间成功！")
                }
                Slider(
                    value = timerProgress.toFloat(),
                    onValueChange = { timerProgress = it.toInt() },
                    onValueChangeFinished = { ReadAloud.setTimer(requireContext(), timerProgress) },
                    valueRange = 0f..180f,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(
                        R.string.timer_m,
                        if (timerProgress < 0) 0 else timerProgress
                    ),
                    color = textColor,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable { showTimePicker = true },
                )
            }

            // 语速：标签 + 跟随系统开关
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.read_aloud_speed),
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = ((speechRate + 5) / 10f).toString(),
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 3.dp),
                )
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.flow_sys),
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = ttsFollowSys,
                        onCheckedChange = {
                            ttsFollowSys = it
                            AppConfig.ttsFlowSys = it
                            upTtsSpeechRate()
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            // 语速调节
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SheetIcon(R.drawable.ic_reduce, stringResource(R.string.tts_speech_reduce), textColor,
                    enabled = !ttsFollowSys) {
                    speechRate -= 1
                    AppConfig.ttsSpeechRate = speechRate
                    upTtsSpeechRate()
                }
                Slider(
                    value = speechRate.toFloat(),
                    onValueChange = { speechRate = it.toInt() },
                    onValueChangeFinished = {
                        AppConfig.ttsSpeechRate = speechRate
                        upTtsSpeechRate()
                    },
                    valueRange = 0f..45f,
                    enabled = !ttsFollowSys,
                    modifier = Modifier.weight(1f),
                )
                SheetIcon(R.drawable.ic_add, stringResource(R.string.tts_speech_add), textColor,
                    enabled = !ttsFollowSys) {
                    speechRate += 1
                    AppConfig.ttsSpeechRate = speechRate
                    upTtsSpeechRate()
                }
            }

            // 底部功能行：目录 / 主菜单 / 后台 / 设置
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SheetAction(R.drawable.ic_toc, stringResource(R.string.chapter_list), textColor) {
                    callBack?.openChapterList()
                }
                SheetAction(R.drawable.ic_menu, stringResource(R.string.main_menu), textColor) {
                    callBack?.showMenuBar()
                    dismissAllowingStateLoss()
                }
                SheetAction(R.drawable.ic_visibility_off, stringResource(R.string.to_backstage), textColor) {
                    callBack?.finish()
                }
                SettingAction(textColor)
            }
        }

        if (showTimePicker) {
            TimePickerSheet(
                onDismiss = { showTimePicker = false },
                onSelect = { minutes ->
                    showTimePicker = false
                    ReadAloud.setTimer(requireContext(), minutes)
                },
            )
        }
    }

    @Composable
    private fun SheetIcon(
        iconRes: Int,
        contentDescription: String,
        tint: Color,
        enabled: Boolean = true,
        onClick: () -> Unit,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
            modifier = Modifier
                .size(30.dp)
                .clickable(enabled = enabled) { onClick() }
                .padding(2.dp),
        )
    }

    @Composable
    private fun SheetAction(
        iconRes: Int,
        label: String,
        tint: Color,
        onClick: () -> Unit,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(60.dp)
                .clickable { onClick() }
                .padding(bottom = 7.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = label,
                color = tint,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun SettingAction(tint: Color) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(60.dp)
                .combinedClickable(
                    onClick = {
                        ReadAloudConfigDialog().show(childFragmentManager, "readAloudConfigDialog")
                    },
                    onLongClick = {
                        // 通过主页导航打开多说话人播放器/投音入口
                        val bookUrl = ReadBook.book?.bookUrl
                        if (bookUrl != null) {
                            requireContext().startActivity(
                                io.legado.app.ui.main.MainIntent.createBookVoiceCastingIntent(
                                    requireContext(),
                                    bookUrl,
                                )
                            )
                        }
                    },
                )
                .padding(bottom = 7.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = stringResource(R.string.setting),
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = stringResource(R.string.setting),
                color = tint,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }

    @Composable
    private fun TimePickerSheet(
        onDismiss: () -> Unit,
        onSelect: (Int) -> Unit,
    ) {
        val times = remember { intArrayOf(0, 5, 10, 15, 30, 60, 90, 180) }
        androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = legadoPopupBackgroundColor(),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.set_timer),
                        style = MaterialTheme.typography.headlineSmall,
                        color = legadoPopupPrimaryTextColor(),
                    )
                    times.forEach { minutes ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(minutes)
                                }
                                .padding(vertical = 6.dp),
                        ) {
                            RadioButton(selected = false, onClick = null)
                            Text(
                                text = "$minutes 分钟",
                                style = MaterialTheme.typography.bodyLarge,
                                color = legadoPopupPrimaryTextColor(),
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    interface CallBack {
        fun showMenuBar()
        fun openChapterList()
        fun onClickReadAloud()
        fun finish()
        fun showReadAloudPlayer()
    }
}
