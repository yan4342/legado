package io.legado.app.ui.book.read

import android.content.Context
import android.content.Intent
import android.os.Build
import io.legado.app.R
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefString
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.putPrefString

/**
 * 文字选择菜单的数据模型与菜单项构建。
 */

/** 文字选择菜单浮窗的状态(选中位置 + 选中文本 + 菜单项) */
data class TextMenuState(
    val selectedText: String,
    val startX: Int,
    val startTopY: Int,
    val startBottomY: Int,
    val endX: Int,
    val endBottomY: Int,
    val items: List<ActionMenuItem>,
)

/** 文字选择菜单项 */
data class ActionMenuItem(
    val id: Int,
    val title: String,
    val iconDrawable: android.graphics.drawable.Drawable? = null,
    val intent: Intent? = null,
    val showState: Int = 0, // 0: 一级, 1: 折叠
) {
    val enabled: Boolean
        get() = showState == 0

    val uniqueId: String
        get() = if (intent != null) {
            val comp = intent.component
            if (comp != null) "${comp.packageName}/${comp.className}" else title
        } else {
            when (id) {
                R.id.menu_copy -> "menu_copy"
                R.id.menu_share_str -> "menu_share_str"
                R.id.menu_browser -> "menu_browser"
                R.id.menu_aloud -> "menu_aloud"
                R.id.menu_bookmark -> "menu_bookmark"
                R.id.menu_dict -> "menu_dict"
                R.id.menu_replace -> "menu_replace"
                R.id.menu_search_content -> "menu_search_content"
                R.id.menu_split_chapter -> "menu_split_chapter"
                else -> id.toString()
            }
        }
}

/** 一级菜单默认可见数量,超出部分折叠到"更多" */
const val MAX_VISIBLE_TEXT_MENU_ITEMS = 5

/** 文字选择菜单项配置的持久化 key(保存排序与显示状态) */
const val TEXT_MENU_CONFIG_KEY = "textSelectMenuConfig"

/** 文字选择菜单项的持久化配置项 */
data class SelectionMenuConfigItem(
    val id: String,
    val showState: Int, // 0: 一级, 1: 折叠, 2: 隐藏
)

/**
 * 构建文字选择菜单项列表。
 * 未配置时:前 [MAX_VISIBLE_TEXT_MENU_ITEMS] 项作为一级菜单,其余折叠到"更多"。
 * 用户通过"编辑菜单项"保存配置后:按配置排序并应用显示状态。
 */
fun buildTextActionMenuItems(
    context: Context,
    showSplitChapter: Boolean,
): List<ActionMenuItem> {
    val items = mutableListOf<ActionMenuItem>()
    items.add(ActionMenuItem(R.id.menu_replace, context.getString(R.string.replace)))
    items.add(ActionMenuItem(R.id.menu_copy, context.getString(android.R.string.copy)))
    items.add(ActionMenuItem(R.id.menu_bookmark, context.getString(R.string.bookmark)))
    items.add(ActionMenuItem(R.id.menu_aloud, context.getString(R.string.read_aloud)))
    items.add(ActionMenuItem(R.id.menu_dict, context.getString(R.string.dict)))
    items.add(ActionMenuItem(R.id.menu_search_content, context.getString(R.string.search_content)))
    items.add(ActionMenuItem(R.id.menu_browser, context.getString(R.string.browser)))
    items.add(ActionMenuItem(R.id.menu_share_str, context.getString(R.string.share)))
    if (showSplitChapter) {
        items.add(ActionMenuItem(R.id.menu_split_chapter, context.getString(R.string.split_chapter_here)))
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        runCatching {
            val pm = context.packageManager
            val intent = Intent().setAction(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            for (resolveInfo in resolveInfos) {
                val processIntent = Intent()
                    .setAction(Intent.ACTION_PROCESS_TEXT)
                    .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
                    .setClassName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name)
                val title = resolveInfo.loadLabel(pm).toString()
                val icon = runCatching { resolveInfo.loadIcon(pm) }.getOrNull()
                items.add(
                    ActionMenuItem(id = -1, title = title, iconDrawable = icon, intent = processIntent)
                )
            }
        }.onFailure {
            it.printOnDebug()
        }
    }
    // 默认:前 MAX_VISIBLE_TEXT_MENU_ITEMS 项作为一级,其余折叠
    val baseItems = items.mapIndexed { index, item ->
        if (index < MAX_VISIBLE_TEXT_MENU_ITEMS) item else item.copy(showState = 1)
    }
    // 应用用户保存的配置(排序 + 显示状态)
    val configs = loadTextMenuConfig(context) ?: return baseItems
    if (configs.isEmpty()) return baseItems
    val configMap = configs.associateBy { it.id }
    val ordered = mutableListOf<ActionMenuItem>()
    for (config in configs) {
        val found = baseItems.find { it.uniqueId == config.id } ?: continue
        ordered.add(found.copy(showState = config.showState))
    }
    for (item in baseItems) {
        if (!configMap.containsKey(item.uniqueId)) ordered.add(item)
    }
    return ordered
}

/** 读取文字选择菜单项配置;无配置返回 null */
fun loadTextMenuConfig(context: Context): List<SelectionMenuConfigItem>? {
    val json = context.getPrefString(TEXT_MENU_CONFIG_KEY) ?: return null
    return runCatching {
        GSON.fromJsonObject<List<SelectionMenuConfigItem>>(json).getOrNull()
    }.getOrNull()
}

/** 保存文字选择菜单项配置(排序 + 显示状态) */
fun saveTextMenuConfig(context: Context, items: List<ActionMenuItem>) {
    val configs = items.map { SelectionMenuConfigItem(it.uniqueId, it.showState) }
    context.putPrefString(TEXT_MENU_CONFIG_KEY, GSON.toJson(configs))
}
