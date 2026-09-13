package io.legado.app.ui.book.read

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isLocal
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.ReadBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.ui.common.compose.LegadoTheme
import io.legado.app.utils.showM3EditDialog
import io.legado.app.utils.sendToClip
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 内容编辑（Compose 实现，全屏编辑器）
 */
class ContentEditDialog : DialogFragment() {

    val viewModel by viewModels<ContentEditViewModel>()

    private var title by mutableStateOf("")
    private var contentText by mutableStateOf("")
    private var loading by mutableStateOf(false)

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        title = ReadBook.curTextChapter?.title ?: ""
        viewModel.loadStateLiveData.observe(viewLifecycleOwner) {
            loading = it
        }
        viewModel.initContent { content ->
            contentText = content
        }
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    ContentEditScreen()
                }
            }
        }
    }

    override fun onCancel(dialog: android.content.DialogInterface) {
        super.onCancel(dialog)
        save()
    }

    private fun save() {
        val content = contentText
        Coroutine.async {
            val book = ReadBook.book ?: return@async
            val chapter = appDb.bookChapterDao
                .getChapter(book.bookUrl, ReadBook.durChapterIndex)
                ?: return@async
            BookHelp.saveText(book, chapter, content)
            ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
        }
    }

    @Composable
    private fun ContentEditScreen() {
        val textColor = MaterialTheme.colorScheme.onSurface
        val bgColor = MaterialTheme.colorScheme.surfaceContainerLowest
        var menuExpanded by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor),
        ) {
            // 顶栏：标题（点击改章节名）+ 菜单
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { editTitle() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
                Icon(
                    painter = painterResource(R.drawable.ic_save),
                    contentDescription = stringResource(R.string.action_save),
                    tint = textColor,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable {
                            save()
                            dismiss()
                        },
                )
                Box {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { menuExpanded = true },
                    )
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reset)) },
                            onClick = {
                                menuExpanded = false
                                viewModel.initContent(true) { content ->
                                    contentText = content
                                    ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy_all)) },
                            onClick = {
                                menuExpanded = false
                                requireContext().sendToClip("$title\n$contentText")
                            },
                        )
                    }
                }
                Spacer(Modifier.size(12.dp))
            }

            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = contentText,
                    onValueChange = { contentText = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                )
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(36.dp),
                    )
                }
            }
        }
    }

    private fun editTitle() {
        lifecycleScope.launch {
            val book = ReadBook.book ?: return@launch
            val chapter = withContext(IO) {
                appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
            } ?: return@launch
            showM3EditDialog(
                titleRes = R.string.edit,
                initialValue = chapter.title,
                onConfirm = { value ->
                    chapter.title = value
                    lifecycleScope.launch {
                        withContext(IO) {
                            appDb.bookChapterDao.update(chapter)
                        }
                        title = chapter.getDisplayTitle()
                        ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
                    }
                },
            )
        }
    }

    class ContentEditViewModel(application: Application) : BaseViewModel(application) {
        val loadStateLiveData = MutableLiveData<Boolean>()
        var content: String? = null

        fun initContent(reset: Boolean = false, success: (String) -> Unit) {
            execute {
                val book = ReadBook.book ?: return@execute null
                val chapter = appDb.bookChapterDao
                    .getChapter(book.bookUrl, ReadBook.durChapterIndex)
                    ?: return@execute null
                if (reset) {
                    content = null
                    BookHelp.delContent(book, chapter)
                    if (!book.isLocal) ReadBook.bookSource?.let { bookSource ->
                        WebBook.getContentAwait(bookSource, book, chapter)
                    }
                }
                return@execute content ?: let {
                    val contentProcessor = ContentProcessor.get(book.name, book.origin)
                    val content = BookHelp.getContent(book, chapter) ?: return@let null
                    contentProcessor.getContent(book, chapter, content, includeTitle = false)
                        .toString()
                }
            }.onStart {
                loadStateLiveData.postValue(true)
            }.onSuccess {
                content = it
                success.invoke(it ?: "")
            }.onFinally {
                loadStateLiveData.postValue(false)
            }
        }

    }

}
