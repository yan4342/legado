package io.legado.app.ui.book.bookmark

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import io.legado.app.data.appDb
import io.legado.app.data.entities.Bookmark
import io.legado.app.ui.common.compose.LegadoTheme
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 书签编辑对话框（Compose 渲染）。阅读器通过 [showDialogFragment] 打开，
 * 内部复用 [BookmarkEditDialog]，与"所有书签"页保持一致。
 *
 * @param showDelete 为 true 时显示删除按钮。
 */
class BookmarkDialog() : DialogFragment() {

    constructor(bookmark: Bookmark, showDelete: Boolean = false) : this() {
        arguments = Bundle().apply {
            putBoolean("showDelete", showDelete)
            putParcelable("bookmark", bookmark)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = object : Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen) {}
        dialog.window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.TRANSPARENT
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        @Suppress("DEPRECATION")
        val bookmark = arguments?.getParcelable<Bookmark>("bookmark")
        val showDelete = arguments?.getBoolean("showDelete", false) ?: false
        if (bookmark == null) {
            dismiss()
            return View(requireContext())
        }
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    BookmarkEditDialog(
                        bookmark = bookmark,
                        onDismiss = { dismiss() },
                        onSave = { bookText, content ->
                            lifecycleScope.launch {
                                withContext(IO) {
                                    appDb.bookmarkDao.insert(bookmark.copy(bookText = bookText, content = content))
                                }
                                dismiss()
                            }
                        },
                        onDelete = if (showDelete) {
                            {
                                lifecycleScope.launch {
                                    withContext(IO) {
                                        appDb.bookmarkDao.delete(bookmark)
                                    }
                                    dismiss()
                                }
                            }
                        } else null,
                    )
                }
            }
        }
    }
}
