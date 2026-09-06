package io.legado.app.ui.book.toc

import android.content.Intent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import io.legado.app.base.BaseComposeActivity

/**
 * 目录页 Activity 薄壳：界面内容与选章结果逻辑统一在 [TocScreen]（主栈 TocEntry 亦复用）。
 * 本类只负责 intent extras 读取与 Activity 形态的结果回传（TocActivityResult 契约）。
 */
class TocActivity : BaseComposeActivity() {

    val viewModel by viewModels<TocViewModel>()

    @Composable
    override fun Content() {
        val bookUrl = intent.getStringExtra("bookUrl")
        val initialPage = intent.getIntExtra("initialPage", 0)
        TocScreen(
            bookUrl = bookUrl,
            initialPage = initialPage,
            viewModel = viewModel,
            launchScope = lifecycleScope,
            onExit = { result ->
                if (result is TocRouteResult.Selection) {
                    setResult(RESULT_OK, Intent().apply {
                        putExtra("index", result.index)
                        putExtra("chapterPos", result.pos)
                        putExtra("readerLaunched", result.readerLaunched)
                    })
                }
                finish()
            },
        )
    }
}
