package io.legado.app.ui.book.read

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.readaloud.ReadAloudSessionStatus
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.model.ReadAloudSessionStore
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.common.compose.LegadoTheme
import org.koin.java.KoinJavaComponent.get

private val readAloudSessionStore: ReadAloudSessionStore by lazy {
    get(ReadAloudSessionStore::class.java)
}

/**
 * 阅读界面底部悬浮胶囊组（移植自 MD3 ReadBookFloatingActionBar，适配 View 架构）：
 * - 阅读锚点：跳走后可「返回原阅读位置」或主动丢弃
 * - 朗读脱离提示：翻页脱离朗读位置时「回到朗读位置」或「从此处朗读」
 *
 * 各胶囊条件独立、可并存；都不可见时整组隐藏。
 */
@Composable
fun ReadingFloatingCapsulesContent() {
    val anchorAvailable by ReadBook.readingAnchorState.collectAsState()
    val session by readAloudSessionStore.state.collectAsState()

    val readAloudRunning = session.status != ReadAloudSessionStatus.Idle
    val readAloudDetached = readAloudRunning &&
        !session.followReadAloudPosition &&
        ReadBookConfig.readAloudDetachReminderEnabled
    val anchorVisible = anchorAvailable

    AnimatedVisibility(
        visible = anchorVisible || readAloudDetached,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (readAloudDetached) {
                CapsuleButton(stringResource(R.string.back_to_speaking_position)) {
                    backToSpeakingPosition()
                }
                CapsuleButton(stringResource(R.string.read_aloud_from_here)) {
                    readAloudSessionStore.restoreReadAloudFollow()
                    ReadBook.readAloud()
                }
            }
            if (anchorVisible) {
                CapsuleButton(stringResource(R.string.return_reading_position)) {
                    ReadBook.restoreLastBookProgress()
                }
                CapsuleButton(stringResource(R.string.dismiss_reading_anchor)) {
                    ReadBook.discardReadingAnchor()
                }
            }
        }
    }
}

@Composable
private fun CapsuleButton(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        tonalElevation = 6.dp,
        //shadowElevation = 6.dp,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

/**
 * 「回到朗读位置」：跳回正在朗读的章节与字符位置并绘制高亮。
 * 属于朗读相关的页面移动，不触发手动脱离。
 */
fun backToSpeakingPosition() {
    readAloudSessionStore.restoreReadAloudFollow()
    val speakingChapterIndex = BaseReadAloudService.currentChapterIndex
    val chapterStart = BaseReadAloudService.currentProgress.coerceAtLeast(0)
    if (speakingChapterIndex < 0) return
    if (speakingChapterIndex != ReadBook.durChapterIndex) {
        BaseReadAloudService.withSpeechNavigation {
            ReadBook.openChapter(speakingChapterIndex, chapterStart) {
                ReadBook.upTextChapterAloudSpan(chapterStart)
            }
        }
    } else {
        val pageIndex = ReadBook.curTextChapter?.getPageIndexByCharIndex(chapterStart)
            ?: return
        ReadBook.setPageIndex(pageIndex)
        readAloudSessionStore.restoreReadAloudFollow()
        ReadBook.upTextChapterAloudSpan(chapterStart)
    }
}

/**
 * 把悬浮胶囊组挂到阅读界面：附着在 Activity decorView 底部居中，
 * 窗口销毁时自动移除。
 */
fun attachReadingFloatingCapsules(activity: Activity) {
    val decorView = activity.window.decorView as? ViewGroup ?: return
    if (decorView.findViewWithTag<View>(READING_CAPSULE_TAG) != null) return
    val capsuleView = ComposeView(activity).apply {
        tag = READING_CAPSULE_TAG
        setContent {
            LegadoTheme {
                ReadingFloatingCapsulesContent()
            }
        }
    }
    decorView.addView(
        capsuleView,
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL,
        ).apply {
            bottomMargin = (88 * activity.resources.displayMetrics.density).toInt()
        },
    )
}

private const val READING_CAPSULE_TAG = "reading_floating_capsules"
