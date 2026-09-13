package io.legado.app.ui.association

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.databinding.DialogCustomGroupBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.association.compose.ImportListItem
import io.legado.app.ui.association.compose.ImportListScreen
import io.legado.app.ui.association.compose.ImportMenuAction
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.ui.common.compose.LegadoWaitState
import io.legado.app.ui.widget.dialog.CodeDialog
import io.legado.app.utils.GSON
import io.legado.app.utils.dpToPx
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment

/**
 * 导入书源弹出窗口（Compose 渲染）。
 *
 * 业务逻辑（解析、校验、导入、分组编辑、CodeDialog）全部留在本类内，
 * UI 由共享组件 [ImportListScreen] 承担。
 */
class ImportBookSourceDialog() : BaseDialogFragment(R.layout.dialog_recycler_view),
    CodeDialog.Callback {

    constructor(source: String, finishOnDismiss: Boolean = false) : this() {
        arguments = Bundle().apply {
            putString("source", source)
            putBoolean("finishOnDismiss", finishOnDismiss)
        }
    }

    private val viewModel by viewModels<ImportBookSourceViewModel>()

    // ---- Compose 状态（原 binding/adapter 的展示层替代）----
    private val itemsState = mutableStateOf<List<ImportListItem>>(emptyList())
    private val loadingState = mutableStateOf(true)
    private val messageState = mutableStateOf<String?>(null)
    private val footerTextState = mutableStateOf<String?>(null)
    private val groupTitleState = mutableStateOf<String?>(null)
    private val keepNameState = mutableStateOf(AppConfig.importKeepName)
    private val keepGroupState = mutableStateOf(AppConfig.importKeepGroup)
    private val keepEnableState = mutableStateOf(AppConfig.importKeepEnable)
    private val waitState = LegadoWaitState()

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
                        title = stringResource(R.string.import_book_source),
                        items = itemsState.value,
                        loading = loadingState.value,
                        message = messageState.value,
                        footerText = footerTextState.value,
                        waitState = waitState,
                        menuActions = importMenuActions(),
                        onItemClick = ::toggleItemSelect,
                        onItemOpen = ::openItemCode,
                        onFooterClick = ::toggleSelectAll,
                        onCancelClick = { dismissAllowingStateLoss() },
                        onOkClick = ::importSelected,
                    )
                }
            }
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.errorLiveData.observe(this) {
            loadingState.value = false
            messageState.value = it
        }
        viewModel.successLiveData.observe(this) {
            loadingState.value = false
            if (it > 0) {
                upListItems()
                upSelectText()
            } else {
                messageState.value = getString(R.string.wrong_format)
            }
        }
        val source = arguments?.getString("source")
        if (source.isNullOrEmpty()) {
            dismiss()
            return
        }
        viewModel.importSource(source)
    }

    /** 条目状态文案：新增/更新/已有 */
    private fun itemStateText(index: Int): String {
        val source = viewModel.allSources[index]
        val localSource = viewModel.checkSources.getOrNull(index)
        return when {
            localSource == null -> "新增"
            source.lastUpdateTime > localSource.lastUpdateTime -> "更新"
            else -> "已有"
        }
    }

    private fun upListItems() {
        itemsState.value = List(viewModel.allSources.size) { index ->
            ImportListItem(
                title = viewModel.allSources[index].bookSourceName,
                stateText = itemStateText(index),
                selected = viewModel.selectStatus.getOrElse(index) { false },
            )
        }
    }

    private fun upSelectText() {
        footerTextState.value = if (viewModel.isSelectAll) {
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

    private fun toggleItemSelect(index: Int) {
        viewModel.selectStatus[index] = !viewModel.selectStatus[index]
        upListItems()
        upSelectText()
    }

    private fun toggleSelectAll() {
        val selectAll = viewModel.isSelectAll
        viewModel.selectStatus.forEachIndexed { index, b ->
            if (b != !selectAll) {
                viewModel.selectStatus[index] = !selectAll
            }
        }
        upListItems()
        upSelectText()
    }

    private fun toggleNewSources() {
        val selectAllNew = viewModel.isSelectAllNew
        viewModel.newSourceStatus.forEachIndexed { index, b ->
            if (b) {
                viewModel.selectStatus[index] = !selectAllNew
            }
        }
        upListItems()
        upSelectText()
    }

    private fun toggleUpdateSources() {
        val selectAllUpdate = viewModel.isSelectAllUpdate
        viewModel.updateSourceStatus.forEachIndexed { index, b ->
            if (b) {
                viewModel.selectStatus[index] = !selectAllUpdate
            }
        }
        upListItems()
        upSelectText()
    }

    private fun importSelected() {
        waitState.show()
        viewModel.importSelect {
            waitState.dismiss()
            dismissAllowingStateLoss()
        }
    }

    private fun openItemCode(index: Int) {
        viewModel.allSources.getOrNull(index)?.let { source ->
            showDialogFragment(
                CodeDialog(
                    GSON.toJson(source),
                    disableEdit = false,
                    requestId = index.toString()
                )
            )
        }
    }

    /**
     * 顶栏菜单（对应 R.menu.import_source）
     */
    @Composable
    private fun importMenuActions(): List<ImportMenuAction> {
        return listOf(
            ImportMenuAction(
                title = groupTitleState.value ?: stringResource(R.string.diy_source_group),
                showInBar = true, // 原 showAsAction="always"
            ) {
                alertCustomGroup()
            },
            ImportMenuAction(stringResource(R.string.select_new_source)) {
                toggleNewSources()
            },
            ImportMenuAction(stringResource(R.string.select_update_source)) {
                toggleUpdateSources()
            },
            ImportMenuAction(
                stringResource(R.string.keep_original_name),
                isSelected = keepNameState.value
            ) {
                keepNameState.value = !keepNameState.value
                AppConfig.importKeepName = keepNameState.value
            },
            ImportMenuAction(
                stringResource(R.string.keep_group),
                isSelected = keepGroupState.value
            ) {
                keepGroupState.value = !keepGroupState.value
                AppConfig.importKeepGroup = keepGroupState.value
            },
            ImportMenuAction(
                stringResource(R.string.keep_enable),
                isSelected = keepEnableState.value
            ) {
                keepEnableState.value = !keepEnableState.value
                AppConfig.importKeepEnable = keepEnableState.value
            },
        )
    }

    @SuppressLint("InflateParams")
    private fun alertCustomGroup() {
        alert(R.string.diy_edit_source_group) {
            val alertBinding = DialogCustomGroupBinding.inflate(layoutInflater).apply {
                val groups = appDb.bookSourceDao.allGroups()
                textInputLayout.setHint(R.string.group_name)
                editView.setFilterValues(groups.toList())
                editView.dropDownHeight = 180.dpToPx()
            }
            customView {
                alertBinding.root
            }
            okButton {
                viewModel.isAddGroup = alertBinding.swAddGroup.isChecked
                viewModel.groupName = alertBinding.editView.text?.toString()
                groupTitleState.value = if (viewModel.groupName.isNullOrBlank()) {
                    getString(R.string.diy_source_group)
                } else {
                    val group = getString(R.string.diy_edit_source_group_title, viewModel.groupName)
                    if (viewModel.isAddGroup) {
                        "+$group"
                    } else {
                        group
                    }
                }
            }
            cancelButton()
        }
    }

    override fun onCodeSave(code: String, requestId: String?) {
        requestId?.toInt()?.let {
            GSON.fromJsonObject<BookSource>(code).getOrNull()?.let { source ->
                viewModel.allSources[it] = source
                upListItems()
            }
        }
    }

}
