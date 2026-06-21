package io.legado.app.ui.book.import.local

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.utils.showM3EditDialog
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.ui.book.import.BaseImportBookActivity
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.widget.SelectActionBar
import io.legado.app.utils.ArchiveUtils
import io.legado.app.utils.FileDoc
import io.legado.app.utils.gone
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.isUri
import io.legado.app.utils.launch
import io.legado.app.utils.putPrefInt
import io.legado.app.ui.common.compose.RoundDropdownMenuItem
import io.legado.app.ui.common.compose.createComposeDropdownIcon
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 导入本地书籍界面
 */
class ImportBookActivity : BaseImportBookActivity<ImportBookViewModel>(),
    PopupMenu.OnMenuItemClickListener,
    ImportBookAdapter.CallBack,
    SelectActionBar.CallBack {

    override val viewModel by viewModels<ImportBookViewModel>()
    private val adapter by lazy { ImportBookAdapter(this, this) }
    private var scanDocJob: Job? = null

    private val selectFolder = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            AppConfig.importBookPath = uri.toString()
            initRootDoc(true)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        searchView.queryHint = getString(R.string.screen) + " • " + getString(R.string.local_book)
        onBackPressedDispatcher.addCallback(this) {
            if (!goBackDir()) {
                finish()
            }
        }
        lifecycleScope.launch {
            initView()
            initEvent()
            if (setBookStorage() && AppConfig.importBookPath.isNullOrBlank()) {
                AppConfig.importBookPath = AppConfig.defaultBookTreeUri
            }
            initData()
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        // Always-visible: select folder button
        menu.add(0, R.id.menu_select_folder, 0, getString(R.string.select_folder)).also {
            it.setIcon(R.drawable.ic_folder_open)
            it.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        // Sort icon with Compose RoundDropdownMenu (spring bounce)
        val sortView = createComposeDropdownIcon { dismiss ->
            RoundDropdownMenuItem(
                text = getString(R.string.sort_by_name),
                onClick = { dismiss(); upSort(0) },
                isSelected = viewModel.sort == 0,
            )
            RoundDropdownMenuItem(
                text = getString(R.string.sort_by_size),
                onClick = { dismiss(); upSort(1) },
                isSelected = viewModel.sort == 1,
            )
            RoundDropdownMenuItem(
                text = getString(R.string.sort_by_time),
                onClick = { dismiss(); upSort(2) },
                isSelected = viewModel.sort == 2,
            )
        }
        val sortId = View.generateViewId()
        menu.add(0, sortId, 1, getString(R.string.sort)).also {
            it.setIcon(R.drawable.ic_sort)
            it.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            it.actionView = sortView
        }
        // Overflow: other items via Compose RoundDropdownMenu
        val overflowView = createComposeDropdownIcon { dismiss ->
            RoundDropdownMenuItem(
                text = getString(R.string.scan_folder),
                onClick = { dismiss(); scanFolder() },
            )
            RoundDropdownMenuItem(
                text = getString(R.string.import_file_name),
                onClick = { dismiss(); alertImportFileName() },
            )
        }
        val overflowId = View.generateViewId()
        menu.add(0, overflowId, 99, "").also {
            it.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            it.actionView = overflowView
        }
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_select_folder -> selectFolder.launch()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_del_selection -> viewModel.deleteDoc(adapter.selected) {
                adapter.removeSelection()
            }
        }
        return false
    }

    override fun selectAll(selectAll: Boolean) {
        adapter.selectAll(selectAll)
    }

    override fun revertSelection() {
        adapter.revertSelection()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onClickSelectBarMainAction() {
        viewModel.addToBookshelf(adapter.selected) {
            adapter.selected.forEach {
                it.isOnBookShelf = true
            }
            adapter.selected.clear()
            adapter.notifyDataSetChanged()
        }
    }

    private fun initView() {
        binding.layTop.setBackgroundColor(backgroundColor)
        binding.tvEmptyMsg.setText(R.string.empty_msg_import_book)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.recycledViewPool.setMaxRecycledViews(0, 15)
        binding.selectActionBar.setMainActionText(R.string.add_to_bookshelf)
        binding.selectActionBar.inflateMenu(R.menu.import_book_sel)
        binding.selectActionBar.setOnMenuItemClickListener(this)
        binding.selectActionBar.setCallBack(this)
    }

    private fun initEvent() {
        binding.tvGoBack.setOnClickListener {
            goBackDir()
        }
    }

    private fun initData() {
        viewModel.dataFlowStart = {
            initRootDoc()
        }
        lifecycleScope.launch {
            viewModel.dataFlow.conflate().collect { docs ->
                adapter.setItems(docs)
            }
        }
    }

    private fun initRootDoc(changedFolder: Boolean = false) {
        if (viewModel.rootDoc != null && !changedFolder) {
            upPath()
        } else {
            val lastPath = AppConfig.importBookPath
            if (lastPath.isNullOrBlank()) {
                binding.tvEmptyMsg.visible()
                selectFolder.launch()
            } else {
                val rootUri = if (lastPath.isUri()) {
                    lastPath.toUri()
                } else {
                    Uri.fromFile(File(lastPath))
                }
                when {
                    rootUri.isContentScheme() -> initRootPath(rootUri)
                    else -> initRootPath(rootUri.path!!)
                }
            }
        }
    }

    private fun initRootPath(rootUri: Uri) {
        kotlin.runCatching {
            val doc = DocumentFile.fromTreeUri(this, rootUri)
            if (doc == null || doc.name.isNullOrEmpty() || !doc.isDirectory) {
                binding.tvEmptyMsg.visible()
                selectFolder.launch()
            } else {
                viewModel.subDocs.clear()
                viewModel.rootDoc = FileDoc.fromDocumentFile(doc)
                upPath()
            }
        }.onFailure {
            binding.tvEmptyMsg.visible()
            selectFolder.launch()
        }
    }

    private fun initRootPath(path: String) {
        binding.tvEmptyMsg.visible()
        PermissionsCompat.Builder()
            .addPermissions(*Permissions.Group.STORAGE)
            .rationale(R.string.tip_perm_request_storage)
            .onGranted {
                kotlin.runCatching {
                    val file = File(path)
                    if (!file.isDirectory) {
                        binding.tvEmptyMsg.visible()
                        selectFolder.launch()
                    } else {
                        viewModel.subDocs.clear()
                        viewModel.rootDoc = FileDoc.fromFile(file)
                        upPath()
                    }
                }.onFailure {
                    binding.tvEmptyMsg.visible()
                    selectFolder.launch()
                }
            }
            .request()
    }

    private fun upSort(sort: Int) {
        viewModel.sort = sort
        putPrefInt(PreferKey.localBookImportSort, sort)
        if (scanDocJob?.isActive != true) {
            viewModel.dataCallback?.upAdapter()
        }
    }

    @Synchronized
    private fun upPath() {
        binding.tvGoBack.isEnabled = viewModel.subDocs.isNotEmpty()
        viewModel.rootDoc?.let {
            scanDocJob?.cancel()
            upDocs(it)
        }
    }

    private fun upDocs(rootDoc: FileDoc) {
        binding.tvEmptyMsg.gone()
        var path = rootDoc.name + File.separator
        var lastDoc = rootDoc
        for (doc in viewModel.subDocs) {
            lastDoc = doc
            path = path + doc.name + File.separator
        }
        binding.tvPath.text = path
        adapter.selected.clear()
        adapter.clearItems()
        viewModel.loadDoc(lastDoc)
    }

    /**
     * 扫描当前文件夹及所有子文件夹
     */
    private fun scanFolder() {
        viewModel.rootDoc?.let { doc ->
            adapter.clearItems()
            val lastDoc = viewModel.subDocs.lastOrNull() ?: doc
            binding.refreshProgressBar.isAutoLoading = true
            scanDocJob?.cancel()
            scanDocJob = lifecycleScope.launch(IO) {
                viewModel.scanDoc(lastDoc)
                withContext(Main) {
                    binding.refreshProgressBar.isAutoLoading = false
                }
            }
        }
    }

    private fun alertImportFileName() {
        showM3EditDialog(
            title = getString(R.string.import_file_name),
            initialValue = AppConfig.bookImportFileName ?: "",
            hint = "js",
            onConfirm = { value ->
                AppConfig.bookImportFileName = value
            },
        )
    }

    @Synchronized
    override fun nextDoc(fileDoc: FileDoc) {
        viewModel.subDocs.add(fileDoc)
        upPath()
    }

    @Synchronized
    private fun goBackDir(): Boolean {
        return if (viewModel.subDocs.isNotEmpty()) {
            viewModel.subDocs.removeAt(viewModel.subDocs.lastIndex)
            upPath()
            true
        } else {
            false
        }
    }

    override fun onSearchTextChange(newText: String?) {
        viewModel.updateCallBackFlow(newText)
    }

    override fun upCountView() {
        binding.selectActionBar.upCountView(adapter.selected.size, adapter.checkableCount)
    }

    override fun startRead(fileDoc: FileDoc) {
        if (!ArchiveUtils.isArchive(fileDoc.name)) {
            appDb.bookDao.getBookByFileName(fileDoc.name)?.let {
                val filePath = fileDoc.toString()
                if (it.bookUrl != filePath) {
                    it.bookUrl = filePath
                    appDb.bookDao.insert(it)
                }
                startReadBook(it)
            }
        } else {
            onArchiveFileClick(fileDoc)
        }
    }

}
