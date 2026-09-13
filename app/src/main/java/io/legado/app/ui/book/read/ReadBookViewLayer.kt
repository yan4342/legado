package io.legado.app.ui.book.read

import android.view.LayoutInflater
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.databinding.ActivityBookReadBinding
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.ReadView

/**
 * 阶段 4 阅读页路由化：AndroidView 承载现有 View 渲染层。
 * 复用 activity_book_read.xml（read_view + read_menu + search_menu + 光标 + 导航栏占位），
 * 渲染逻辑零改动；仅把各 View 的回调宿主从 activity 强切换为 [ReadBookController] 注入
 * （callBackOverride，Activity 形态保留原回退）。菜单 Compose 化（M3）后此处的
 * read_menu/search_menu 将由 Compose 菜单替代。
 */
@Composable
internal fun ReadBookViewLayer(
    modifier: Modifier = Modifier,
    readViewCallBack: ReadView.CallBack,
    contentTextCallBack: ContentTextView.CallBack,
    searchCallBack: SearchMenu.CallBack,
    onRefsReady: (ReadBookViewRefs) -> Unit,
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            val binding = ActivityBookReadBinding.inflate(LayoutInflater.from(context))
            binding.readView.callBackOverride = readViewCallBack
            binding.searchMenu.callBackOverride = searchCallBack
            // ReadView 内三个 PageView 惰性创建，此处提前实例化并注入页内回调
            listOf(
                binding.readView.prevPage,
                binding.readView.curPage,
                binding.readView.nextPage,
            ).forEach { it.callBackOverride = contentTextCallBack }
            // 构造期回调未注入时 upContent 被守卫跳过，此处补跑首帧内容装配
            binding.readView.upContent()
            onRefsReady(
                ReadBookViewRefs(
                    root = binding.root,
                    readView = binding.readView,
                    textMenuPosition = binding.textMenuPosition,
                    cursorLeft = binding.cursorLeft,
                    cursorRight = binding.cursorRight,
                    navigationBar = binding.navigationBar,
                    searchMenu = binding.searchMenu,
                )
            )
            binding.root
        },
    )
}
