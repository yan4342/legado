package io.legado.app.ui.book.read.config

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.help.IntentHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.model.ReadAloud
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.ui.common.compose.M3NumberPickerDialog
import io.legado.app.ui.common.compose.legadoPopupBackgroundColor
import io.legado.app.ui.common.compose.legadoPopupPrimaryTextColor
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.utils.GSON
import io.legado.app.utils.StringUtils
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.postEvent
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment

/**
 * 朗读设置（原内嵌 PreferenceFragment 的 Compose 重写）。
 * 行→控件映射：
 *  ignoreAudioFocus / pauseReadAloudWhilePhoneCalls(联动禁用) / readAloudWakeLock /
 *  systemMediaControlCompatibilityChange / mediaButtonPerNext / readAloudByPage /
 *  streamReadAloudAudio / showReadAloudCapsule → TinySwitchSettingItem
 *  ttsParagraphInterval → TinyClickableSettingItem + M3NumberPickerDialog
 *  readAloudDefaultInterface → TinyClickableSettingItem + 单选弹层
 *  appTtsEngine → TinyClickableSettingItem + SpeakEngineDialog
 *  sysTtsConfig → TinyClickableSettingItem + 系统设置页
 */
class ReadAloudConfigDialog : DialogFragment() {

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        setLayout(0.9f, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    ReadAloudConfigScreen()
                }
            }
        }
    }

    private fun speakEngineSummary(context: Context): String {
        val ttsEngine = ReadAloud.ttsEngine ?: return context.getString(R.string.system_tts)
        return if (StringUtils.isNumeric(ttsEngine)) {
            appDb.httpTTSDao.getName(ttsEngine.toLong())
                ?: context.getString(R.string.system_tts)
        } else {
            GSON.fromJsonObject<SelectItem<String>>(ttsEngine).getOrNull()?.title
                ?: context.getString(R.string.system_tts)
        }
    }

    @Composable
    private fun ReadAloudConfigScreen() {
        val context = LocalContext.current

        // 从引擎选择等子对话框返回时刷新摘要
        val refreshTick = remember { mutableIntStateOf(0) }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) refreshTick.intValue++
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        var ignoreAudioFocus by remember {
            mutableStateOf(AppConfig.ignoreAudioFocus)
        }
        var pauseWhilePhoneCalls by remember {
            mutableStateOf(AppConfig.pauseReadAloudWhilePhoneCalls)
        }
        var wakeLock by remember {
            mutableStateOf(AppConfig.readAloudWakeLock)
        }
        var mediaCompatChange by remember {
            mutableStateOf(AppConfig.systemMediaControlCompatibilityChange)
        }
        var mediaButtonPerNext by remember {
            mutableStateOf(AppConfig.mediaButtonPerNext)
        }
        var readAloudByPage by remember {
            mutableStateOf(AppConfig.readAloudByPage)
        }
        var streamReadAloudAudio by remember {
            mutableStateOf(AppConfig.streamReadAloudAudio)
        }
        var showCapsule by remember {
            mutableStateOf(AppConfig.showReadAloudCapsule)
        }
        var paragraphInterval by remember {
            mutableStateOf(AppConfig.ttsParagraphInterval)
        }
        var defaultInterface by remember {
            mutableStateOf(AppConfig.readAloudDefaultInterface)
        }
        var showInterfacePicker by remember { mutableStateOf(false) }

        val popupBg = legadoPopupBackgroundColor()
        val popupTextColor = legadoPopupPrimaryTextColor()

        fun resetMediaButtonIfRunning() {
            if (BaseReadAloudService.isRun) {
                postEvent(EventBus.MEDIA_BUTTON, false)
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = popupBg,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .padding(top = 24.dp, start = 12.dp, end = 12.dp, bottom = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.aloud_config),
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                        color = popupTextColor,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                    )
                    Column(
                        modifier = Modifier
                            .heightIn(max = 520.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        TinySwitchSettingItem(
                            title = stringResource(R.string.ignore_audio_focus_title),
                            description = stringResource(R.string.ignore_audio_focus_summary),
                            checked = ignoreAudioFocus,
                            onCheckedChange = {
                                ignoreAudioFocus = it
                                AppConfig.ignoreAudioFocus = it
                            },
                        )
                        TinySwitchSettingItem(
                            title = stringResource(R.string.pause_read_aloud_while_phone_calls_title),
                            description = stringResource(R.string.pause_read_aloud_while_phone_calls_summary),
                            checked = pauseWhilePhoneCalls,
                            enabled = ignoreAudioFocus,
                            onCheckedChange = {
                                pauseWhilePhoneCalls = it
                                AppConfig.pauseReadAloudWhilePhoneCalls = it
                            },
                        )
                        TinySwitchSettingItem(
                            title = stringResource(R.string.read_aloud_wake_lock),
                            description = stringResource(R.string.read_aloud_wake_lock_summary),
                            checked = wakeLock,
                            onCheckedChange = {
                                wakeLock = it
                                AppConfig.readAloudWakeLock = it
                            },
                        )
                        TinySwitchSettingItem(
                            title = stringResource(R.string.system_media_control_compatibility_change),
                            description = stringResource(R.string.system_media_control_compatibility_change_summary),
                            checked = mediaCompatChange,
                            onCheckedChange = {
                                mediaCompatChange = it
                                AppConfig.systemMediaControlCompatibilityChange = it
                            },
                        )
                        TinySwitchSettingItem(
                            title = stringResource(R.string.pref_media_button_per_next),
                            description = stringResource(R.string.pref_media_button_per_next_summary),
                            checked = mediaButtonPerNext,
                            onCheckedChange = {
                                mediaButtonPerNext = it
                                AppConfig.mediaButtonPerNext = it
                            },
                        )
                        TinySwitchSettingItem(
                            title = stringResource(R.string.read_aloud_by_page),
                            description = stringResource(R.string.read_aloud_by_page_summary),
                            checked = readAloudByPage,
                            onCheckedChange = {
                                readAloudByPage = it
                                AppConfig.readAloudByPage = it
                                resetMediaButtonIfRunning()
                            },
                        )
                        TinySwitchSettingItem(
                            title = stringResource(R.string.stream_read_aloud_audio),
                            description = stringResource(R.string.stream_read_aloud_audio_summary),
                            checked = streamReadAloudAudio,
                            onCheckedChange = {
                                streamReadAloudAudio = it
                                AppConfig.streamReadAloudAudio = it
                                resetMediaButtonIfRunning()
                            },
                        )
                        TinyClickableSettingItem(
                            title = stringResource(R.string.tts_paragraph_interval),
                            description = stringResource(
                                R.string.tts_paragraph_interval_summary,
                                paragraphInterval
                            ),
                            onClick = {
                                showDialogFragment(
                                    M3NumberPickerDialog.create(
                                        title = getString(R.string.tts_paragraph_interval),
                                        value = AppConfig.ttsParagraphInterval,
                                        minValue = 0,
                                        maxValue = 5000,
                                        onConfirm = {
                                            AppConfig.ttsParagraphInterval = it
                                            paragraphInterval = it
                                        }
                                    )
                                )
                            },
                        )
                        TinyClickableSettingItem(
                            title = stringResource(R.string.default_read_aloud_interface),
                            description = stringResource(
                                R.string.default_read_aloud_interface_summary,
                                if (defaultInterface == "player") {
                                    stringResource(R.string.read_aloud_interface_player)
                                } else {
                                    stringResource(R.string.read_aloud_interface_classic)
                                }
                            ),
                            onClick = { showInterfacePicker = true },
                        )
                        TinySwitchSettingItem(
                            title = stringResource(R.string.show_read_aloud_capsule),
                            description = stringResource(R.string.show_read_aloud_capsule_summary),
                            checked = showCapsule,
                            onCheckedChange = {
                                showCapsule = it
                                AppConfig.showReadAloudCapsule = it
                            },
                        )
                        TinyClickableSettingItem(
                            title = stringResource(R.string.speak_engine),
                            description = "TTS",
                            trailingContent = {
                                Text(
                                    text = speakEngineSummary(context),
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = legadoPopupPrimaryTextColor().copy(alpha = 0.7f),
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                            },
                            onClick = {
                                showDialogFragment(SpeakEngineDialog())
                            },
                        )
                        TinyClickableSettingItem(
                            title = stringResource(R.string.sys_tts_config),
                            description = stringResource(R.string.sys_tts_config_summary),
                            onClick = { IntentHelp.openTTSSetting() },
                        )
                    }
                }
            }
        }

        if (showInterfacePicker) {
            InterfacePickerSheet(
                current = defaultInterface,
                onSelect = { value ->
                    defaultInterface = value
                    AppConfig.readAloudDefaultInterface = value
                    showInterfacePicker = false
                },
                onDismiss = { showInterfacePicker = false },
            )
        }
    }

    @Composable
    private fun InterfacePickerSheet(
        current: String,
        onSelect: (String) -> Unit,
        onDismiss: () -> Unit,
    ) {
        val values = remember { arrayOf("classic", "player") }
        val labels = listOf(
            stringResource(R.string.read_aloud_interface_classic),
            stringResource(R.string.read_aloud_interface_player),
        )
        androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = legadoPopupBackgroundColor(),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.default_read_aloud_interface),
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                        color = legadoPopupPrimaryTextColor(),
                    )
                    labels.forEachIndexed { index, label ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(values[index]) }
                                .padding(vertical = 6.dp),
                        ) {
                            RadioButton(selected = values[index] == current, onClick = null)
                            Text(
                                text = label,
                                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                                color = legadoPopupPrimaryTextColor(),
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
