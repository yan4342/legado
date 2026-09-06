package io.legado.app.ui.book.read

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.fragment.app.DialogFragment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.help.config.AppConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.common.compose.LegadoAlertDialog
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.utils.setLayout
import io.legado.app.ui.common.compose.legadoPopupBackgroundColor
import io.legado.app.ui.common.compose.legadoPopupPrimaryTextColor
import io.legado.app.ui.common.compose.rememberLegadoColorScheme
import io.legado.app.ui.replace.ReplaceEditRoute
import io.legado.app.ui.replace.ReplaceRuleActivity

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 起效的替换规则（Compose 实现）
 */
class EffectiveReplacesDialog : DialogFragment() {

    private val viewModel by activityViewModels<ReadBookViewModel>()
    private val chineseConvert by lazy { ReplaceRule(0, "繁简转换") }

    private var isEdit = false

    private val editActivity =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == AppCompatActivity.RESULT_OK) {
                isEdit = true
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val effectiveReplaceRules = ReadBook.curTextChapter?.effectiveReplaceRules ?: emptyList()
        val rules = if (AppConfig.chineseConverterType > 0) {
            effectiveReplaceRules + chineseConvert
        } else {
            effectiveReplaceRules
        }
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    EffectiveReplacesScreen(
                        rules = remember { rules },
                        isChineseConvertItem = { it == chineseConvert },
                        onItemClick = { item ->
                            if (item == chineseConvert) {
                                // 繁简转换模式选择由 Screen 内部状态弹层处理
                            } else {
                                editActivity.launch(
                                    ReplaceRuleActivity.startIntent(
                                        requireContext(),
                                        ReplaceEditRoute(id = item.id)
                                    )
                                )
                            }
                        },
                        onChineseConvertClick = {
                            AppConfig.chineseConverterType = it
                            isEdit = true
                        },
                        onRuleDeleted = { rule ->
                            isEdit = true
                            lifecycleScope.launch(Dispatchers.IO) {
                                appDb.replaceRuleDao.delete(rule)
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 窗口透明，仅显示居中圆角卡片（对应旧版 filletBackground 圆角窗）
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        setLayout(0.9f, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (isEdit) {
            viewModel.replaceRuleChanged()
        }
    }

}

@Composable
private fun EffectiveReplacesScreen(
    rules: List<ReplaceRule>,
    isChineseConvertItem: (ReplaceRule) -> Boolean,
    onItemClick: (ReplaceRule) -> Unit,
    onChineseConvertClick: (Int) -> Unit,
    onRuleDeleted: (ReplaceRule) -> Unit,
) {
    val popupBg = legadoPopupBackgroundColor()
    val popupTextColor = legadoPopupPrimaryTextColor()

    var showChineseConvertDialog by remember { mutableStateOf(false) }
    val ruleItems = remember { mutableStateListOf<ReplaceRule>().apply { addAll(rules) } }
    var ruleToDelete by remember { mutableStateOf<ReplaceRule?>(null) }

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
                modifier = Modifier.padding(top = 24.dp, start = 24.dp, end = 24.dp, bottom = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.effective_replaces),
                    style = MaterialTheme.typography.headlineSmall,
                    color = popupTextColor,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .padding(top = 8.dp),
                ) {
                    items(ruleItems, key = { it.id }) { item ->
                        val canDelete = !isChineseConvertItem(item)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = popupTextColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (isChineseConvertItem(item)) {
                                            showChineseConvertDialog = true
                                        } else {
                                            onItemClick(item)
                                        }
                                    }
                                    .padding(vertical = 14.dp, horizontal = 4.dp),
                            )
                            if (canDelete) {
                                IconButton(onClick = { ruleToDelete = item }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.delete),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    ruleToDelete?.let { rule ->
        LegadoAlertDialog(
            show = true,
            onDismissRequest = { ruleToDelete = null },
            dialogTitle = stringResource(R.string.delete),
            text = stringResource(R.string.sure_del),
            onConfirm = {
                ruleItems.remove(rule)
                onRuleDeleted(rule)
            },
            onDismiss = { ruleToDelete = null },
        )
    }

    if (showChineseConvertDialog) {
        val modes = stringArrayResource(R.array.chinese_mode)
        androidx.compose.ui.window.Dialog(onDismissRequest = { showChineseConvertDialog = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = legadoPopupBackgroundColor(),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.chinese_converter),
                        style = MaterialTheme.typography.headlineSmall,
                        color = legadoPopupPrimaryTextColor(),
                    )
                    modes.forEachIndexed { index, mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showChineseConvertDialog = false
                                    onChineseConvertClick(index)
                                }
                                .padding(vertical = 6.dp),
                        ) {
                            RadioButton(
                                selected = AppConfig.chineseConverterType == index,
                                onClick = null,
                            )
                            Text(
                                text = mode,
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
}
