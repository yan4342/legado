package io.legado.app.ui.book.read

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 阶段 4 阅读页路由化：阅读路由与外部（BookInfoRouteScreen 等）的结果回传 holder，
 * 模式同 TocRouteState（pending-holder）。在 MainNavHost remember 后分发给
 * ReadBookController 与 BookInfoEntry。
 */
class ReadBookRouteState {

    /** 退出阅读器时置位：BookInfo 消费后清空（刷新书籍/在架状态） */
    var pendingExit: Boolean? by mutableStateOf(null)

    /** 阅读器内加入书架成功（原 setResult(RESULT_OK)） */
    var addedToShelf: Boolean by mutableStateOf(false)

    /** 书籍被删除（原 RESULT_DELETED，BookInfo 收到后应 onBack） */
    var bookDeleted: Boolean by mutableStateOf(false)

    /** 阅读器内请求打开书籍详情（原 bookInfoActivity launcher 的入参路由） */
    var pendingBookInfoUrl: String? by mutableStateOf(null)

    fun consumeExit(): Boolean? = pendingExit.also { pendingExit = null }

    companion object {

        /** 原 ReadBookActivity.RESULT_DELETED：阅读器通知调用方书籍已删除 */
        const val RESULT_DELETED = 100

        /**
         * 过渡桥（M2⑤ 弹窗硬转点修复用，M3 弹窗 Compose 化后删除）：
         * 路由形态的 Controller 供 View 弹窗访问 bottomDialog/upPageAnim；
         * Activity 形态为 null，弹窗回退 activity as ReadBookActivity。
         */
        var controllerRef: ReadBookController? = null

        /** 弹层系统栏计数（语义同后置自增/自减，返回变更前的值） */
        fun bumpBottomDialog(delta: Int): Int {
            val ref = controllerRef ?: return 0
            val v = ref.bottomDialog
            ref.bottomDialog = v + delta
            return v
        }
    }
}


/** 阅读菜单面板的 UI 状态快照（原 ReadMenu 的命令式刷新转为状态） */
data class ReadMenuUiState(
    val bookName: String? = null,
    val chapterName: String? = null,
    val chapterUrl: String? = null,
    val isLocalBook: Boolean = true,
    val preEnabled: Boolean = false,
    val nextEnabled: Boolean = false,
    val seekMax: Int = 0,
    val seekProgress: Int = 0,
    val autoPageActive: Boolean = false,
    val brightnessAuto: Boolean = true,
)
