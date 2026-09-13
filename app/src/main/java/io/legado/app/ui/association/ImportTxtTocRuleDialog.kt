package io.legado.app.ui.association

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.lib.theme.filletBackground
import io.legado.app.ui.association.compose.ImportListItem
import io.legado.app.ui.association.compose.ImportListScreen
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.ui.common.compose.LegadoWaitState
import io.legado.app.ui.widget.dialog.CodeDialog
import io.legado.app.utils.GSON
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment

/**
 * 导入 TXT 目录规则（Compose 实现）
 */
class ImportTxtTocRuleDialog() : DialogFragment() {

    constructor(source: String, finishOnDismiss: Boolean = false) : this() {
        arguments = Bundle().apply {
            putString("source", source)
            putBoolean("finishOnDismiss", finishOnDismiss)
        }
    }

    private val viewModel by viewModels<ImportTxtTocRuleViewModel>()
    private val waitState = LegadoWaitState()

    private var loading by mutableStateOf(true)
    private var message by mutableStateOf<String?>(null)
    private var sources by mutableStateOf<List<TxtTocRule>>(emptyList())
    private var stateTexts by mutableStateOf<List<String?>>(emptyList())
    private var checked by mutableStateOf<List<Boolean>>(emptyList())

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawable(requireContext().filletBackground)
        setLayout(MATCH_PARENT, WRAP_CONTENT)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (arguments?.getBoolean("finishOnDismiss") == true) {
            activity?.finish()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val source = arguments?.getString("source")
        if (source.isNullOrEmpty()) {
            dismissAllowingStateLoss()
        } else {
            viewModel.errorLiveData.observe(viewLifecycleOwner) {
                loading = false
                message = it
            }
            viewModel.successLiveData.observe(viewLifecycleOwner) {
                loading = false
                if (it > 0) {
                    snapshotItems()
                } else {
                    message = getString(R.string.wrong_format)
                }
            }
            viewModel.importSource(source)
        }
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    ImportListScreen(
                        title = getString(R.string.import_txt_toc_rule),
                        items = sources.mapIndexed { index, rule ->
                            ImportListItem(
                                title = rule.name,
                                stateText = stateTexts.getOrNull(index),
                                selected = checked.getOrElse(index) { true },
                            )
                        },
                        loading = loading,
                        message = message,
                        footerText = footerText(),
                        waitState = waitState,
                        onItemClick = { index ->
                            viewModel.selectStatus[index] = !viewModel.selectStatus[index]
                            refreshChecked()
                        },
                        onItemOpen = { index ->
                            showDialogFragment(
                                CodeDialog(
                                    GSON.toJson(sources[index]),
                                    disableEdit = false,
                                    requestId = index.toString()
                                )
                            )
                        },
                        onFooterClick = {
                            val selectAll = viewModel.isSelectAll
                            viewModel.selectStatus.forEachIndexed { i, b ->
                                if (b != !selectAll) {
                                    viewModel.selectStatus[i] = !selectAll
                                }
                            }
                            refreshChecked()
                        },
                        onCancelClick = { dismissAllowingStateLoss() },
                        onOkClick = {
                            waitState.show()
                            viewModel.importSelect {
                                waitState.dismiss()
                                dismissAllowingStateLoss()
                            }
                        },
                    )
                }
            }
        }
    }

    private fun snapshotItems() {
        sources = viewModel.allSources.toList()
        stateTexts = viewModel.checkSources.mapIndexed { index, localSource ->
            val item = viewModel.allSources.getOrNull(index) ?: return@mapIndexed null
            when {
                localSource == null -> "新增"
                item != localSource -> "更新"
                else -> "已有"
            }
        }
        refreshChecked()
    }

    private fun refreshChecked() {
        checked = viewModel.selectStatus.toList()
    }

    private fun footerText(): String =
        if (viewModel.isSelectAll) {
            getString(R.string.select_cancel_count, viewModel.selectCount, viewModel.allSources.size)
        } else {
            getString(R.string.select_all_count, viewModel.selectCount, viewModel.allSources.size)
        }

}
