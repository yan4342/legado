package io.legado.app.ui.association

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import io.legado.app.R
import io.legado.app.help.coil.LegadoFetcher
import io.legado.app.help.source.SourceVerificationHelp
import io.legado.app.lib.dialogs.alert
import io.legado.app.model.ImageProvider
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.ui.common.compose.RoundDropdownMenu
import io.legado.app.ui.common.compose.legadoPopupBackgroundColor
import io.legado.app.ui.common.compose.legadoPopupPrimaryTextColor
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.utils.showDialogFragment

/**
 * 图片验证码对话框（Compose 渲染）
 * 结果保存在内存中
 * val key = "${sourceOrigin ?: ""}_verificationResult"
 * CacheManager.get(key)
 */
class VerificationCodeDialog() : DialogFragment() {

    constructor(
        imageUrl: String,
        sourceOrigin: String? = null,
        sourceName: String? = null,
        sourceType: Int
    ) : this() {
        arguments = Bundle().apply {
            putString("imageUrl", imageUrl)
            putString("sourceOrigin", sourceOrigin)
            putString("sourceName", sourceName)
            putInt("sourceType", sourceType)
        }
    }

    val viewModel by viewModels<VerificationCodeViewModel>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = object : Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen) {}
        dialog.window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.TRANSPARENT
            // 软键盘适配：窗口不自行平移/缩放，由 Compose imePadding 抬升卡片
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        return dialog
    }

    private var sourceOrigin: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val arguments = arguments ?: run {
            dismissAllowingStateLoss()
            return View(requireContext())
        }
        viewModel.initData(arguments)
        sourceOrigin = arguments.getString("sourceOrigin")
        val imageUrl = arguments.getString("imageUrl") ?: run {
            dismissAllowingStateLoss()
            return View(requireContext())
        }
        ImageProvider.remove(imageUrl)
        var bitmap by mutableStateOf<Bitmap?>(null)
        var loadFailed by mutableStateOf(false)
        loadImage(
            url = imageUrl,
            sourceUrl = sourceOrigin,
            onSuccess = { bitmap = it },
            onError = { loadFailed = true },
        )
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    VerificationCodeContent(
                        title = stringResource(R.string.input_verification_code),
                        subtitle = arguments.getString("sourceName"),
                        bitmap = bitmap,
                        loadFailed = loadFailed,
                        onImageClick = {
                            showDialogFragment(PhotoDialog(imageUrl, sourceOrigin))
                        },
                        onOk = { code ->
                            sourceOrigin?.let { origin ->
                                SourceVerificationHelp.setResult(origin, code)
                            }
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

    private fun loadImage(url: String, sourceUrl: String?, onSuccess: (Bitmap) -> Unit, onError: () -> Unit) {
        val request = ImageRequest.Builder(requireContext())
            .data(url)
            .apply {
                if (sourceUrl != null) {
                    extras[LegadoFetcher.sourceOriginKey] = sourceUrl
                }
            }
            .diskCachePolicy(CachePolicy.DISABLED)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .target(
                onSuccess = { result ->
                    val drawable = result.asDrawable(resources)
                    val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        val copiedBitmap = bitmap.copy(bitmap.config!!, true)
                        ImageProvider.put(url, copiedBitmap)
                        onSuccess(copiedBitmap)
                    } else {
                        onError()
                    }
                },
                onError = { _ ->
                    onError()
                }
            )
            .build()
        SingletonImageLoader.get(requireContext()).enqueue(request)
    }

    @Suppress("DEPRECATION")
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

    override fun onDestroy() {
        sourceOrigin?.let { SourceVerificationHelp.checkResult(it) }
        super.onDestroy()
        activity?.finish()
    }

}

@Composable
private fun VerificationCodeContent(
    title: String,
    subtitle: String?,
    bitmap: Bitmap?,
    loadFailed: Boolean,
    onImageClick: () -> Unit,
    onOk: (String) -> Unit,
    onDisableSource: () -> Unit,
    onDeleteSource: () -> Unit,
) {
    val popupBg = legadoPopupBackgroundColor()
    val popupTextColor = legadoPopupPrimaryTextColor()
    var menuExpanded by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
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
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = popupTextColor.copy(alpha = 0.7f),
                            )
                        }
                    }
                    IconButton(onClick = { onOk(code) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = stringResource(R.string.ok),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
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
                Spacer(Modifier.padding(top = 8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onImageClick),
                    contentAlignment = Alignment.Center,
                ) {
                    val currentBitmap = bitmap
                    when {
                        currentBitmap != null -> Image(
                            bitmap = currentBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.verification_code),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )

                        loadFailed -> Image(
                            painter = painterResource(R.drawable.image_loading_error),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )

                        else -> CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.5.dp,
                        )
                    }
                }
                Spacer(Modifier.padding(top = 12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.verification_code)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
