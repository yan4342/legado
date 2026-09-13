package io.legado.app.ui.config.compose

import android.net.Uri
import androidx.compose.runtime.Composable
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.lib.dialogs.selector
import io.legado.app.model.BookCover
import io.legado.app.ui.config.CoverRuleConfigDialog
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.externalFiles
import io.legado.app.help.config.AppConfigStore
import io.legado.app.utils.inputStream
import io.legado.app.utils.readUri
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx
import java.io.FileOutputStream

class CoverConfigComposeFragment : ConfigComposeFragment() {

    private var pendingCoverIsNight: Boolean = false

    private val selectCoverImage = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            val key = if (pendingCoverIsNight) PreferKey.defaultCoverDark else PreferKey.defaultCover
            setCoverFromUri(key, uri)
        }
    }

    @Composable
    override fun ConfigContent() {
        CoverConfigScreen(
            onBackClick = { activity?.finish() },
            onCoverRuleClick = {
                showDialogFragment(CoverRuleConfigDialog())
            },
            onDefaultCoverClick = { isNight ->
                pendingCoverIsNight = isNight
                val key = if (isNight) PreferKey.defaultCoverDark else PreferKey.defaultCover
                if (AppConfigStore.getString(key).isNullOrEmpty()) {
                    launchCoverPicker(isNight)
                } else {
                    context?.selector(
                        items = arrayListOf(
                            getString(R.string.delete),
                            getString(R.string.select_image),
                        )
                    ) { _, i ->
                        if (i == 0) {
                            AppConfigStore.remove(key)
                            BookCover.upDefaultCover()
                        } else {
                            launchCoverPicker(isNight)
                        }
                    }
                }
            },
        )
    }

    private fun launchCoverPicker(isNight: Boolean) {
        pendingCoverIsNight = isNight
        selectCoverImage.launch {
            requestCode = if (isNight) 112 else 111
            mode = HandleFileContract.IMAGE
        }
    }

    private fun setCoverFromUri(preferenceKey: String, uri: Uri) {
        readUri(uri) { fileDoc, inputStream ->
            kotlin.runCatching {
                var file = requireContext().externalFiles
                val suffix = fileDoc.name.substringAfterLast(".")
                val fileName = uri.inputStream(requireContext()).getOrThrow().use {
                    MD5Utils.md5Encode(it) + ".$suffix"
                }
                file = FileUtils.createFileIfNotExist(file, "covers", fileName)
                FileOutputStream(file).use {
                    inputStream.copyTo(it)
                }
                AppConfigStore.putString(preferenceKey, file.absolutePath)
                BookCover.upDefaultCover()
            }.onFailure {
                appCtx.toastOnUi(it.localizedMessage)
            }
        }
    }
}
