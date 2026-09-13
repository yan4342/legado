package io.legado.app.ui.book.read.config

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.selector
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.common.compose.LegadoTheme

/** 3×3 网格下标到点击动作配置的映射，顺序：上排、中排、下排（经 AppConfig facade 写入 DS+SP） */
private val clickActionProps = listOf(
    AppConfig::clickActionTL,
    AppConfig::clickActionTC,
    AppConfig::clickActionTR,
    AppConfig::clickActionML,
    AppConfig::clickActionMC,
    AppConfig::clickActionMR,
    AppConfig::clickActionBL,
    AppConfig::clickActionBC,
    AppConfig::clickActionBR,
)

/**
 * 点击区域设置（Compose 渲染）。全屏半透明 3×3 遮罩，点按格子弹出动作选择器，
 * 选择后写入对应点击动作配置，关闭时由 [AppConfig.detectClickArea] 生效。
 */
class ClickActionConfigDialog : DialogFragment() {

    private val actions by lazy {
        linkedMapOf(
            Pair(-1, getString(R.string.non_action)),
            Pair(0, getString(R.string.menu)),
            Pair(1, getString(R.string.next_page)),
            Pair(2, getString(R.string.prev_page)),
            Pair(3, getString(R.string.next_chapter)),
            Pair(4, getString(R.string.previous_chapter)),
            Pair(5, getString(R.string.read_aloud_prev_paragraph)),
            Pair(6, getString(R.string.read_aloud_next_paragraph)),
            Pair(7, getString(R.string.bookmark_add)),
            Pair(8, getString(R.string.edit_content)),
            Pair(9, getString(R.string.replace_state_change)),
            Pair(10, getString(R.string.chapter_list)),
            Pair(11, getString(R.string.search_content)),
            Pair(12, getString(R.string.sync_book_progress_t)),
            Pair(13, getString(R.string.read_aloud_pause_resume))
        )
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = object : Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen) {}
        dialog.window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            setBackgroundDrawableResource(R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
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
                    ClickActionConfigScreen(
                        actions = actions,
                        onChooseAction = { index, apply ->
                            selectAction { action ->
                                clickActionProps[index].set(action)
                                apply(action)
                            }
                        },
                        onClose = { dismissAllowingStateLoss() },
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as ReadBookActivity).bottomDialog++
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (activity as ReadBookActivity).bottomDialog--
    }

    private fun selectAction(success: (action: Int) -> Unit) {
        context?.selector(
            getString(R.string.select_action),
            actions.values.toList()
        ) { _, index ->
            success.invoke(actions.keys.toList()[index])
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AppConfig.detectClickArea()
    }

}

/**
 * 全屏遮罩内容：顶部标题栏（含关闭按钮）+ 3×3 点击区域网格。
 * 格子底色沿用原 shape_translucent_card 的 R.color.translucent，文字保持白色。
 */
@Composable
private fun ClickActionConfigScreen(
    actions: Map<Int, String>,
    onChooseAction: (index: Int, apply: (action: Int) -> Unit) -> Unit,
    onClose: () -> Unit,
) {
    var values by remember {
        mutableStateOf(
            listOf(
                AppConfig.clickActionTL,
                AppConfig.clickActionTC,
                AppConfig.clickActionTR,
                AppConfig.clickActionML,
                AppConfig.clickActionMC,
                AppConfig.clickActionMR,
                AppConfig.clickActionBL,
                AppConfig.clickActionBC,
                AppConfig.clickActionBR,
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.translucent))
            .padding(3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colorResource(R.color.translucent))
                .padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 6.dp)
        ) {
            Text(
                text = stringResource(R.string.click_regional_config),
                color = colorResource(R.color.white),
                modifier = Modifier.weight(1f)
            )
            Icon(
                painter = painterResource(R.drawable.ic_baseline_close),
                contentDescription = stringResource(R.string.close),
                tint = colorResource(R.color.white),
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = onClose)
            )
        }
        for (row in 0 until 3) {
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                for (col in 0 until 3) {
                    val index = row * 3 + col
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(3.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(colorResource(R.color.translucent))
                            .clickable {
                                onChooseAction(index) { action ->
                                    values = values.toMutableList().also { it[index] = action }
                                }
                            }
                    ) {
                        Text(
                            text = actions[values[index]].orEmpty(),
                            color = colorResource(R.color.white)
                        )
                    }
                }
            }
        }
    }
}
