package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.appcompat.view.SupportMenuInflater
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.core.view.isVisible
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.ItemTextBinding
import io.legado.app.databinding.PopupActionMenuBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.widget.ThemedPopupWindow
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.gone
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.visible

@SuppressLint("RestrictedApi")
class TextActionMenu(
    private val context: Context,
    private val callBack: CallBack
) : ThemedPopupWindow(context) {

    companion object {
        /** 默认可见菜单项数量，超出部分折叠到"更多"中 */
        const val MAX_VISIBLE_ITEMS = 5

        /** 空间充足的像素阈值（用于判断浮窗显示在上方还是下方） */
        private const val SPACE_THRESHOLD = 500
    }

    private val binding = PopupActionMenuBinding.inflate(LayoutInflater.from(context))
    private val adapter = Adapter(context).apply { setHasStableIds(true) }

    /** 所有菜单项（可见 + 更多） */
    private val allMenuItems: List<MenuItemImpl>

    /** 是否处于"更多"展开状态 */
    private var isMoreExpanded = false

    private val expandTextMenu get() = context.getPrefBoolean(PreferKey.expandTextMenu)

    init {
        setThemedContentView(binding.root)
        isOutsideTouchable = false
        isFocusable = false

        // 膨胀菜单
        val myMenu = MenuBuilder(context).also {
            SupportMenuInflater(context).inflate(R.menu.content_select_action, it)
        }
        val otherMenu = MenuBuilder(context).also {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                onInitializeMenu(it)
            }
        }
        allMenuItems = myMenu.visibleItems + otherMenu.visibleItems

        // RecyclerView 设置
        binding.recyclerView.adapter = adapter
        binding.recyclerViewMore.adapter = adapter

        // "更多"按钮点击
        binding.ivMenuMore.setOnClickListener { toggleMoreMenu() }

        // 关闭时重置状态
        setOnDismissListener { resetMenuState() }

        // 初始菜单状态
        upMenu()
    }

    /** 刷新菜单显示状态 */
    fun upMenu() {
        if (expandTextMenu) {
            adapter.setItems(allMenuItems)
            binding.ivMenuMore.gone()
        } else {
            binding.ivMenuMore.visible()
            adapter.setItems(allMenuItems.take(MAX_VISIBLE_ITEMS))
        }
    }

    /** 切换"更多"展开/收起 */
    private fun toggleMoreMenu() {
        isMoreExpanded = !isMoreExpanded
        if (isMoreExpanded) {
            binding.ivMenuMore.setImageResource(R.drawable.ic_arrow_back)
            adapter.setItems(allMenuItems.drop(MAX_VISIBLE_ITEMS))
            binding.recyclerView.gone()
            binding.recyclerViewMore.visible()
        } else {
            binding.ivMenuMore.setImageResource(R.drawable.ic_more_vert)
            binding.recyclerViewMore.gone()
            adapter.setItems(allMenuItems.take(MAX_VISIBLE_ITEMS))
            binding.recyclerView.visible()
        }
    }

    /** 关闭浮窗时收起"更多"（除非全局展开模式） */
    private fun resetMenuState() {
        if (!expandTextMenu) {
            isMoreExpanded = false
            binding.ivMenuMore.setImageResource(R.drawable.ic_more_vert)
            binding.recyclerViewMore.gone()
            adapter.setItems(allMenuItems.take(MAX_VISIBLE_ITEMS))
            binding.recyclerView.visible()
        }
    }

    /**
     * 在文本选择位置附近显示浮窗。
     *
     * @param view 锚点视图
     * @param windowHeight 窗口总高度（含导航栏）
     * @param startX 选择起始 X
     * @param startTopY 选择起始顶部 Y
     * @param startBottomY 选择起始底部 Y
     * @param endX 选择结束 X
     * @param endBottomY 选择结束底部 Y
     */
    fun show(
        view: View,
        windowHeight: Int,
        startX: Int,
        startTopY: Int,
        startBottomY: Int,
        endX: Int,
        endBottomY: Int
    ) {
        if (expandTextMenu) {
            showExpanded(view, windowHeight, startX, startTopY, startBottomY, endX, endBottomY)
        } else {
            showCollapsed(view, startX, startTopY, startBottomY, endX, endBottomY)
        }
    }

    /** 全局展开模式：直接定位 */
    private fun showExpanded(
        view: View, windowHeight: Int,
        startX: Int, startTopY: Int, startBottomY: Int,
        endX: Int, endBottomY: Int
    ) {
        when {
            startTopY > SPACE_THRESHOLD -> showAtLocation(
                view, Gravity.BOTTOM or Gravity.START, startX, windowHeight - startTopY
            )
            endBottomY - startBottomY > SPACE_THRESHOLD -> showAtLocation(
                view, Gravity.TOP or Gravity.START, startX, startBottomY
            )
            else -> showAtLocation(
                view, Gravity.TOP or Gravity.START, endX, endBottomY
            )
        }
    }

    /** 折叠模式：预测量高度后定位 */
    private fun showCollapsed(
        view: View,
        startX: Int, startTopY: Int, startBottomY: Int,
        endX: Int, endBottomY: Int
    ) {
        contentView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupHeight = contentView.measuredHeight
        when {
            startBottomY > SPACE_THRESHOLD -> showAtLocation(
                view, Gravity.TOP or Gravity.START, startX, startTopY - popupHeight
            )
            endBottomY - startBottomY > SPACE_THRESHOLD -> showAtLocation(
                view, Gravity.TOP or Gravity.START, startX, startBottomY
            )
            else -> showAtLocation(
                view, Gravity.TOP or Gravity.START, endX, endBottomY
            )
        }
    }

    // ======================== Adapter ========================

    inner class Adapter(context: Context) :
        RecyclerAdapter<MenuItemImpl, ItemTextBinding>(context) {

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getViewBinding(parent: ViewGroup): ItemTextBinding =
            ItemTextBinding.inflate(inflater, parent, false)

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemTextBinding,
            item: MenuItemImpl,
            payloads: MutableList<Any>
        ) {
            binding.textView.text = item.title
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemTextBinding) {
            holder.itemView.setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    if (!callBack.onMenuItemSelected(it.itemId)) {
                        onMenuItemSelected(it)
                    }
                }
                callBack.onMenuActionFinally()
            }
            holder.itemView.setOnLongClickListener {
                toggleSpeakMode()
                true
            }
        }
    }

    private fun toggleSpeakMode() {
        if (AppConfig.contentSelectSpeakMod == 0) {
            AppConfig.contentSelectSpeakMod = 1
            context.toastOnUi("切换为从选择的地方开始一直朗读")
        } else {
            AppConfig.contentSelectSpeakMod = 0
            context.toastOnUi("切换为朗读选择内容")
        }
    }

    // ======================== 菜单分发 ========================

    private fun onMenuItemSelected(item: MenuItemImpl) {
        when (item.itemId) {
            R.id.menu_copy -> context.sendToClip(callBack.selectedText)
            R.id.menu_share_str -> context.share(callBack.selectedText)
            R.id.menu_browser -> openInBrowser()
            else -> item.intent?.let { intent ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    kotlin.runCatching {
                        intent.putExtra(Intent.EXTRA_PROCESS_TEXT, callBack.selectedText)
                        context.startActivity(intent)
                    }.onFailure { e ->
                        AppLog.put("执行文本菜单操作出错\n$e", e, true)
                    }
                }
            }
        }
    }

    private fun openInBrowser() {
        kotlin.runCatching {
            val intent = if (callBack.selectedText.isAbsUrl()) {
                Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(callBack.selectedText) }
            } else {
                Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra(SearchManager.QUERY, callBack.selectedText)
                }
            }
            context.startActivity(intent)
        }.onFailure {
            it.printOnDebug()
            context.toastOnUi(it.localizedMessage ?: "ERROR")
        }
    }

    // ======================== PROCESS_TEXT 支持 ========================

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createProcessTextIntent(): Intent =
        Intent().setAction(Intent.ACTION_PROCESS_TEXT).setType("text/plain")

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getSupportedActivities(): List<ResolveInfo> =
        context.packageManager.queryIntentActivities(createProcessTextIntent(), 0)

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createProcessTextIntentForResolveInfo(info: ResolveInfo): Intent =
        createProcessTextIntent()
            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
            .setClassName(info.activityInfo.packageName, info.activityInfo.name)

    @RequiresApi(Build.VERSION_CODES.M)
    private fun onInitializeMenu(menu: Menu) {
        kotlin.runCatching {
            var menuItemOrder = 100
            for (resolveInfo in getSupportedActivities()) {
                menu.add(
                    Menu.NONE, Menu.NONE,
                    menuItemOrder++,
                    resolveInfo.loadLabel(context.packageManager)
                ).intent = createProcessTextIntentForResolveInfo(resolveInfo)
            }
        }.onFailure {
            context.toastOnUi("获取文字操作菜单出错:${it.localizedMessage}")
        }
    }

    // ======================== Callback ========================

    interface CallBack {
        val selectedText: String
        fun onMenuItemSelected(itemId: Int): Boolean
        fun onMenuActionFinally()
    }
}