package io.legado.app.ui.config.compose

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import com.jaredrummler.android.colorpicker.ColorShape
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.DialogImageBlurringBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.prefs.ColorPreference
import io.legado.app.ui.config.ConfigTag
import io.legado.app.ui.config.ThemeListDialog
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.postEvent
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.inputStream
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import io.legado.app.utils.readUri
import io.legado.app.utils.removePref
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx
import java.io.FileOutputStream

class ThemeConfigComposeFragment : ConfigComposeFragment(), ColorPickerDialogListener {

    private var pendingColorCallback: ((Color) -> Unit)? = null
    private var currentDialogTag: String? = null
    private var pendingBgIsNight: Boolean = false
    private var pendingBgChange: (() -> Unit)? = null

    private val selectBgImage = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            val bgKey = if (pendingBgIsNight) PreferKey.bgImageN else PreferKey.bgImage
            setBgFromUri(uri, bgKey) {
                pendingBgChange?.invoke()
            }
        }
    }

    @Composable
    override fun ConfigContent() {
        ThemeConfigScreen(
            onBackClick = { activity?.finish() },
            onRequestColorPicker = { title, currentColor, onChange ->
                pendingColorCallback = onChange
                currentDialogTag = "color_$title"
                val colorInt = if (currentColor != Color.Unspecified) {
                    val r = (currentColor.red * 255).toInt()
                    val g = (currentColor.green * 255).toInt()
                    val b = (currentColor.blue * 255).toInt()
                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                } else {
                    android.graphics.Color.GRAY
                }
                val dialog = ColorPreference.ColorPickerDialogCompat.newBuilder()
                    .setDialogType(ColorPickerDialog.TYPE_PRESETS)
                    .setDialogTitle(0)
                    .setColorShape(ColorShape.CIRCLE)
                    .setPresets(ColorPickerDialog.MATERIAL_COLORS)
                    .setAllowPresets(true)
                    .setAllowCustom(true)
                    .setShowAlphaSlider(false)
                    .setShowColorShades(true)
                    .setColor(colorInt)
                    .create()
                dialog.setColorPickerDialogListener(this@ThemeConfigComposeFragment)
                childFragmentManager
                    .beginTransaction()
                    .add(dialog, currentDialogTag!!)
                    .commitAllowingStateLoss()
            },
            onThemeListClick = {
                ThemeListDialog().show(childFragmentManager, "themeList")
            },
            onBgImageClick = { isNight ->
                pendingBgIsNight = isNight
                pendingBgChange = {
                    // Trigger theme update for the active theme
                    if (isNight) {
                        ThemeConfig.applyTheme(requireContext())
                    } else {
                        ThemeConfig.applyTheme(requireContext())
                    }
                }
                selectBgAction(isNight)
            },
            onThemeModeToggle = {
                AppConfig.isNightTheme = !AppConfig.isNightTheme
                ThemeConfig.applyDayNight(requireContext())
            },
            onWelcomeStyleClick = {
                activity?.let { a ->
                    a.intent.putExtra("configTag", ConfigTag.WELCOME_CONFIG)
                    a.supportFragmentManager.beginTransaction()
                        .replace(R.id.configFrameLayout, WelcomeConfigComposeFragment(), ConfigTag.WELCOME_CONFIG)
                        .commit()
                }
            },
            onCoverConfigClick = {
                activity?.let { a ->
                    a.intent.putExtra("configTag", ConfigTag.COVER_CONFIG)
                    a.supportFragmentManager.beginTransaction()
                        .replace(R.id.configFrameLayout, CoverConfigComposeFragment(), ConfigTag.COVER_CONFIG)
                        .commit()
                }
            },
        )
    }

    override fun onResume() {
        super.onResume()
        currentDialogTag?.let { tag ->
            childFragmentManager.findFragmentByTag(tag)?.let { f ->
                if (f is ColorPickerDialog) {
                    f.setColorPickerDialogListener(this@ThemeConfigComposeFragment)
                    if (pendingColorCallback == null) {
                        f.dismissAllowingStateLoss()
                    }
                }
            }
        }
    }

    override fun onColorSelected(dialogId: Int, color: Int) {
        val callback = pendingColorCallback
        pendingColorCallback = null
        currentDialogTag?.let { tag ->
            childFragmentManager.findFragmentByTag(tag)?.let { f ->
                if (f is ColorPickerDialog) {
                    f.dismissAllowingStateLoss()
                }
            }
        }
        callback?.invoke(Color(color))
    }

    override fun onDialogDismissed(dialogId: Int) {
        pendingColorCallback = null
    }

    // --- Background image ---

    private fun selectBgAction(isNight: Boolean) {
        val bgKey = if (isNight) PreferKey.bgImageN else PreferKey.bgImage
        val blurringKey = if (isNight) PreferKey.bgImageNBlurring else PreferKey.bgImageBlurring
        val actions = arrayListOf(
            getString(R.string.background_image_blurring),
            getString(R.string.select_image),
        )
        if (!getPrefString(bgKey).isNullOrEmpty()) {
            actions.add(getString(R.string.delete))
        }
        context?.selector(items = actions) { _, i ->
            when (i) {
                0 -> alertImageBlurring(blurringKey) {
                    upTheme(isNight)
                }
                1 -> {
                    selectBgImage.launch {
                        this.requestCode = if (isNight) 122 else 121
                        this.mode = HandleFileContract.IMAGE
                    }
                }
                2 -> {
                    removePref(bgKey)
                    upTheme(isNight)
                }
            }
        }
    }

    private fun alertImageBlurring(preferKey: String, success: () -> Unit) {
        alert(R.string.background_image_blurring) {
            val alertBinding = DialogImageBlurringBinding.inflate(layoutInflater).apply {
                getPrefInt(preferKey, 0).let {
                    seekBar.progress = it
                    textViewValue.text = it.toString()
                }
                seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
                        textViewValue.text = progress.toString()
                    }
                    override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
                })
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.seekBar.progress.let {
                    putPrefInt(preferKey, it)
                    success.invoke()
                }
            }
            cancelButton()
        }
    }

    private fun upTheme(isNightTheme: Boolean) {
        if (AppConfig.isNightTheme == isNightTheme) {
            ThemeConfig.applyTheme(requireContext())
            view?.post { postEvent(EventBus.RECREATE, "") }
        }
    }

    private fun setBgFromUri(uri: Uri, preferenceKey: String, success: () -> Unit) {
        readUri(uri) { fileDoc, inputStream ->
            kotlin.runCatching {
                var file = requireContext().externalFiles
                val suffix = fileDoc.name.substringAfterLast(".")
                val fileName = uri.inputStream(requireContext()).getOrThrow().use {
                    MD5Utils.md5Encode(it) + ".$suffix"
                }
                file = FileUtils.createFileIfNotExist(file, preferenceKey, fileName)
                FileOutputStream(file).use {
                    inputStream.copyTo(it)
                }
                putPrefString(preferenceKey, file.absolutePath)
                success()
            }.onFailure {
                appCtx.toastOnUi(it.localizedMessage)
            }
        }
    }
}
