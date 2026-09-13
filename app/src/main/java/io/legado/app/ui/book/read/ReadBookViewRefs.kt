package io.legado.app.ui.book.read

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import io.legado.app.ui.book.read.page.ReadView

/**
 * 阶段 4 阅读页路由化：View 层与 Compose 壳的交接件（语义对齐 MD3 ReadBookViewRefs）。
 * ReadBookViewLayer 构建完成后回传给 ReadBookController，
 * 供旧文字选择/光标定位/导航栏占位/菜单编排逻辑按引用操作。
 */
class ReadBookViewRefs(
    val root: ViewGroup,
    val readView: ReadView,
    val textMenuPosition: View,
    val cursorLeft: ImageView,
    val cursorRight: ImageView,
    val navigationBar: View,
    val searchMenu: SearchMenu,
)
