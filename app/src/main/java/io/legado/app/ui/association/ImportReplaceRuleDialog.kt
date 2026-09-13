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
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.filletBackground
import io.legado.app.databinding.DialogCustomGroupBinding
import io.legado.app.ui.association.compose.ImportListItem
import io.legado.app.ui.association.compose.ImportListScreen
import io.legado.app.ui.association.compose.ImportMenuAction
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.ui.common.compose.LegadoWaitState
import io.legado.app.ui.widget.dialog.CodeDialog
import io.legado.app.utils.GSON
import io.legado.app.utils.dpToPx
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment

/**
 * 导入替换规则（Compose 实现）
 */
class ImportReplaceRuleDialog() : DialogFragment(),
    CodeDialog.Callback {

    constructor(source: String, finishOnDismiss: Boolean = false) : this() {
        arguments = Bundle().apply {
            putString("source", source)
            putBoolean("finishOnDismiss", finishOnDismiss)
        }
    }

    private val viewModel by viewModels<ImportReplaceRuleViewModel>()
    private val waitState = LegadoWaitState()

    private var loading by mutableStateOf(true)
    private var message by mutableStateOf<String?>(null)
    private var rules by mutableStateOf<List<ReplaceRule>>(emptyList())
    private var stateTexts by mutableStateOf<List<String?>>(emptyList())
    private var checked by mutableStateOf<List<Boolean>>(emptyList())
    private var groupLabel by mutableStateOf("")

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
            viewModel.import(source)
        }
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    ImportListScreen(
                        title = getString(R.string.import_replace_rule),
                        items = rules.mapIndexed { index, rule ->
                            ImportListItem(
                                title = if (rule.group.isNullOrBlank()) {
                                    rule.name
                                } else {
                                    "${rule.name}(${rule.group})"
                                },
                                stateText = stateTexts.getOrNull(index),
                                selected = checked.getOrElse(index) { true },
                            )
                        },
                        loading = loading,
                        message = message,
                        footerText = footerText(),
                        waitState = waitState,
                        menuActions = listOf(
                            ImportMenuAction(
                                title = groupLabel.ifBlank { getString(R.string.diy_source_group) },
                                showInBar = true,
                            ) { alertCustomGroup() }
                        ),
                        onItemClick = { index ->
                            viewModel.selectStatus[index] = !viewModel.selectStatus[index]
                            refreshChecked()
                        },
                        onItemOpen = { index ->
                            showDialogFragment(
                                CodeDialog(
                                    GSON.toJson(rules[index]),
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

    private fun alertCustomGroup() {
        alert(R.string.diy_edit_source_group) {
            val alertBinding = DialogCustomGroupBinding.inflate(layoutInflater).apply {
                val groups = appDb.replaceRuleDao.allGroups()
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
                groupLabel = if (viewModel.groupName.isNullOrBlank()) {
                    getString(R.string.diy_source_group)
                } else {
                    val group = getString(R.string.diy_edit_source_group_title, viewModel.groupName)
                    if (viewModel.isAddGroup) "+$group" else group
                }
            }
            noButton()
        }
    }

    override fun onCodeSave(code: String, requestId: String?) {
        requestId?.toInt()?.let {
            GSON.fromJsonObject<ReplaceRule>(code).getOrNull()?.let { rule ->
                viewModel.allRules[it] = rule
                snapshotItems()
            }
        }
    }

    private fun snapshotItems() {
        rules = viewModel.allRules.toList()
        stateTexts = viewModel.checkRules.mapIndexed { index, localRule ->
            val item = viewModel.allRules.getOrNull(index) ?: return@mapIndexed null
            when {
                localRule == null -> "新增"
                item.pattern != localRule.pattern
                        || item.replacement != localRule.replacement
                        || item.isRegex != localRule.isRegex
                        || item.scope != localRule.scope -> "更新"

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
            getString(R.string.select_cancel_count, viewModel.selectCount, viewModel.allRules.size)
        } else {
            getString(R.string.select_all_count, viewModel.selectCount, viewModel.allRules.size)
        }

}
