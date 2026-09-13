package io.legado.app.ui.file

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.utils.toastOnUi
import java.io.File

/**
 * 应用内文件选择器（Compose 渲染）。由 [HandleFileActivity] 通过 [show] 打开，
 * 选择结果通过 [CallBack.onResult] 回传。
 */
class FilePickerDialog : DialogFragment() {

    private val viewModel: FilePickerViewModel by viewModels()

    companion object {
        const val tag = "FileChooserDialog"

        fun show(
            manager: FragmentManager,
            mode: Int = HandleFileContract.FILE,
            title: String? = null,
            initPath: String? = null,
            isShowHideDir: Boolean = false,
            allowExtensions: Array<String>? = null,
        ) {
            FilePickerDialog().apply {
                val bundle = Bundle()
                bundle.putInt("mode", mode)
                bundle.putString("title", title)
                bundle.putBoolean("isShowHideDir", isShowHideDir)
                bundle.putString("initPath", initPath)
                bundle.putStringArray("allowExtensions", allowExtensions)
                arguments = bundle
            }.show(manager, tag)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = object : Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen) {}
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.TRANSPARENT
            val lp = attributes
            lp.dimAmount = 0.5f
            attributes = lp
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        val window = dialog?.window ?: return
        val metrics = resources.displayMetrics
        window.setLayout(
            (metrics.widthPixels * 0.9f).toInt(),
            (metrics.heightPixels * 0.8f).toInt(),
        )
        window.setGravity(android.view.Gravity.CENTER)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    val context = LocalContext.current
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))) {
                        val state by viewModel.uiState.collectAsStateWithLifecycle()

                        LaunchedEffect(Unit) { viewModel.initData(arguments) }

                        LaunchedEffect(Unit) {
                            viewModel.effects.collect { effect ->
                                when (effect) {
                                    is FilePickerEffect.Confirm -> {
                                        val data = Intent().setData(Uri.fromFile(File(effect.path)))
                                        (parentFragment as? CallBack)?.onResult(data)
                                        (activity as? CallBack)?.onResult(data)
                                        dismissAllowingStateLoss()
                                    }

                                    is FilePickerEffect.ShowToast -> context.toastOnUi(effect.message)
                                }
                            }
                        }

                        FilePickerScreen(
                            state = state,
                            onIntent = viewModel::onIntent,
                            onBack = { dismissAllowingStateLoss() },
                        )
                    }
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        activity?.finish()
    }

    interface CallBack {
        fun onResult(data: Intent)
    }
}
