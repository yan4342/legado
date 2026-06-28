package io.legado.app.ui.book.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import io.legado.app.R
import io.legado.app.ui.book.info.compose.BookInfoRouteScreen
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.utils.showLogSheet
import io.legado.app.utils.startActivity

class SearchActivity : AppCompatActivity() {

    private val viewModel by viewModels<SearchViewModel>()

    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.run {
            decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = android.graphics.Color.TRANSPARENT
        }

        val initKey = intent.getStringExtra("key")
        val initScope = intent.getStringExtra("searchScope")
        viewModel.onIntent(SearchIntent.Initialize(initKey, initScope))

        setContent {
            LegadoTheme {
                SharedTransitionLayout {
                    var bookInfoOverlay by remember { mutableStateOf<BookInfoOverlayParams?>(null) }

                    Box(modifier = Modifier.fillMaxSize()) {
                        SearchScreen(
                            viewModel = viewModel,
                            onBack = {
                                if (bookInfoOverlay != null) bookInfoOverlay = null
                                else if (hasWindowFocus()) {
                                    viewModel.onIntent(SearchIntent.StopSearch)
                                    viewModel.onIntent(SearchIntent.UpdateQuery(""))
                                    finish()
                                }
                            },
                            onOpenBookInfo = { name, author, bookUrl, origin, coverPath, sharedCoverKey ->
                                bookInfoOverlay = BookInfoOverlayParams(
                                    name, author, bookUrl, origin, coverPath, sharedCoverKey
                                )
                            },
                            onOpenSourceManage = {
                                startActivity<BookSourceActivity>()
                            },
                            onShowLog = {
                                showLogSheet()
                            },
                            sharedTransitionScope = this@SharedTransitionLayout,
                        )

                        AnimatedVisibility(
                            visible = bookInfoOverlay != null,
                            enter = fadeIn(tween(300)),
                            exit = fadeOut(tween(250)),
                        ) {
                            bookInfoOverlay?.let { params ->
                                BookInfoRouteScreen(
                                    bookUrl = params.bookUrl,
                                    name = params.name,
                                    author = params.author,
                                    coverPath = params.coverPath,
                                    origin = params.origin,
                                    onBack = { bookInfoOverlay = null },
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@AnimatedVisibility,
                                    sharedCoverKey = params.sharedCoverKey,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val initKey = intent.getStringExtra("key")
        val initScope = intent.getStringExtra("searchScope")
        viewModel.onIntent(SearchIntent.Initialize(initKey, initScope))
    }

    private data class BookInfoOverlayParams(
        val name: String,
        val author: String,
        val bookUrl: String,
        val origin: String?,
        val coverPath: String?,
        val sharedCoverKey: String?,
    )

    companion object {
        fun start(context: Context, key: String?) {
            context.startActivity<SearchActivity> {
                putExtra("key", key)
            }
        }
    }
}
