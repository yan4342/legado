package io.legado.app.ui.association

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.entities.HttpTTS
import io.legado.app.ui.association.compose.ImportListItem
import io.legado.app.ui.association.compose.ImportListScreen
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.ui.common.compose.LegadoWaitState
import io.legado.app.ui.widget.dialog.CodeDialog
import io.legado.app.utils.GSON
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment

/**
 * 导入在线朗读引擎（Compose 渲染，[ImportListScreen] 骨架）。
 * 解析校验、导入等业务逻辑留在 [ImportHttpTtsViewModel]，本类只做状态桥接。
 */
class ImportHttpTtsDialog() : BaseDialogFragment(R.layout.dialog_recycler_view),
    CodeDialog.Callback {

    constructor(source: String, finishOnDismiss: Boolean = false) : this() {
        arguments = Bundle().apply {
            putString("source", source)
            putBoolean("finishOnDismiss", finishOnDismiss)
        }
    }

    private val viewModel by viewModels<ImportHttpTtsViewModel>()
    private val waitState = LegadoWaitState()

    // 原 binding 各控件的 Compose 状态镜像
    private val items = mutableStateListOf<ImportListItem>()
    private var loading by mutableStateOf(true)
    private var message by mutableStateOf<String?>(null)
    private var footerText by mutableStateOf<String?>(null)

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
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
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    ImportListScreen(
                        title = stringResource(R.string.import_tts),
                        items = items,
                        loading = loading,
                        message = message,
                        footerText = footerText,
                        waitState = waitState,
                        onItemClick = ::toggleSelect,
                        onItemOpen = ::openSource,
                        onFooterClick = ::selectOrCancelAll,
                        onCancelClick = { dismissAllowingStateLoss() },
                        onOkClick = ::importSelect,
                    )
                }
            }
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.errorLiveData.observe(this) {
            loading = false
            message = it
        }
        viewModel.successLiveData.observe(this) {
            loading = false
            if (it > 0) {
                refreshItems()
                upSelectText()
            } else {
                message = getString(R.string.wrong_format)
            }
        }
        val source = arguments?.getString("source")
        if (source.isNullOrEmpty()) {
            dismiss()
            return
        }
        viewModel.importSource(source)
    }

    private fun refreshItems() {
        items.clear()
        viewModel.allSources.forEachIndexed { index, source ->
            val localSource = viewModel.checkSources[index]
            items.add(
                ImportListItem(
                    title = source.name,
                    stateText = when {
                        localSource == null -> "新增"
                        source.lastUpdateTime > localSource.lastUpdateTime -> "更新"
                        else -> "已有"
                    },
                    selected = viewModel.selectStatus[index],
                )
            )
        }
    }

    private fun upSelectText() {
        footerText = if (viewModel.isSelectAll) {
            getString(
                R.string.select_cancel_count,
                viewModel.selectCount,
                viewModel.allSources.size
            )
        } else {
            getString(
                R.string.select_all_count,
                viewModel.selectCount,
                viewModel.allSources.size
            )
        }
    }

    private fun toggleSelect(index: Int) {
        viewModel.selectStatus[index] = !viewModel.selectStatus[index]
        items[index] = items[index].copy(selected = viewModel.selectStatus[index])
        upSelectText()
    }

    private fun selectOrCancelAll() {
        val selectAll = viewModel.isSelectAll
        viewModel.selectStatus.forEachIndexed { index, b ->
            if (b != !selectAll) {
                viewModel.selectStatus[index] = !selectAll
            }
        }
        refreshItems()
        upSelectText()
    }

    private fun importSelect() {
        waitState.show()
        viewModel.importSelect {
            waitState.dismiss()
            dismissAllowingStateLoss()
        }
    }

    private fun openSource(index: Int) {
        val source = viewModel.allSources[index]
        showDialogFragment(
            CodeDialog(
                GSON.toJson(source),
                disableEdit = false,
                requestId = index.toString()
            )
        )
    }

    override fun onCodeSave(code: String, requestId: String?) {
        requestId?.toInt()?.let {
            HttpTTS.fromJson(code).getOrNull()?.let { source ->
                viewModel.allSources[it] = source
                if (it < items.size) {
                    items[it] = items[it].copy(title = source.name)
                }
            }
        }
    }
}
