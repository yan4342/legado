package io.legado.app.ui.rss.source.manage

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssSource
import io.legado.app.databinding.ActivityRssSourceBinding
import io.legado.app.utils.showM3EditDialog
import io.legado.app.help.DirectLinkUpload
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.ui.association.ImportRssSourceDialog
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.ui.widget.SelectActionBar
import io.legado.app.ui.widget.recycler.DragSelectTouchHelper
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.ui.common.compose.RoundDropdownMenuItem
import io.legado.app.ui.common.compose.createComposeDropdownIcon
import io.legado.app.utils.ACache
import io.legado.app.utils.applyTint
import io.legado.app.utils.dpToPx
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.launch
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.share
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.startActivity
import io.legado.app.utils.transaction
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * 订阅源管理
 */
class RssSourceActivity : VMBaseActivity<ActivityRssSourceBinding, RssSourceViewModel>(),
    PopupMenu.OnMenuItemClickListener,
    SelectActionBar.CallBack,
    RssSourceAdapter.CallBack {

    override val binding by viewBinding(ActivityRssSourceBinding::inflate)
    override val viewModel by viewModels<RssSourceViewModel>()
    private val importRecordKey = "rssSourceRecordKey"
    private val adapter by lazy { RssSourceAdapter(this, this) }
    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }
    private var sourceFlowJob: Job? = null
    private var groups = arrayListOf<String>()
    private var groupMenu: SubMenu? = null
    private val qrCodeResult = registerForActivityResult(QrCodeResult()) {
        it ?: return@registerForActivityResult
        showDialogFragment(ImportRssSourceDialog(it))
    }
    private val importDoc = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            showDialogFragment(ImportRssSourceDialog(uri.toString()))
        }
    }
    private val exportResult = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            showM3EditDialog(
                titleRes = R.string.export_success,
                initialValue = uri.toString(),
                hintRes = R.string.path,
                onConfirm = {
                    sendToClip(uri.toString())
                },
            )
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initRecyclerView()
        initSearchView()
        initGroupFlow()
        upSourceFlow()
        initSelectActionBar()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        // Group icon with dynamic submenu via Compose RoundDropdownMenu (spring bounce)
        val groupView = createComposeDropdownIcon { dismiss ->
            RoundDropdownMenuItem(
                text = getString(R.string.group_manage),
                onClick = { dismiss(); showDialogFragment<GroupManageDialog>() },
            )
            RoundDropdownMenuItem(
                text = getString(R.string.enabled),
                onClick = { dismiss(); searchView.setQuery(getString(R.string.enabled), true) },
            )
            RoundDropdownMenuItem(
                text = getString(R.string.disabled),
                onClick = { dismiss(); searchView.setQuery(getString(R.string.disabled), true) },
            )
            RoundDropdownMenuItem(
                text = getString(R.string.need_login),
                onClick = { dismiss(); searchView.setQuery(getString(R.string.need_login), true) },
            )
            RoundDropdownMenuItem(
                text = getString(R.string.no_group),
                onClick = { dismiss(); searchView.setQuery(getString(R.string.no_group), true) },
            )
            groups.forEach { groupName ->
                RoundDropdownMenuItem(
                    text = groupName,
                    onClick = { dismiss(); searchView.setQuery("group:$groupName", true) },
                )
            }
        }
        val groupId = View.generateViewId()
        menu.add(0, groupId, 0, getString(R.string.menu_action_group)).also {
            it.setIcon(R.drawable.ic_groups)
            it.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            it.actionView = groupView
        }
        // Overflow items via Compose RoundDropdownMenu
        val overflowView = createComposeDropdownIcon { dismiss ->
            RoundDropdownMenuItem(
                text = getString(R.string.add_rss_source),
                onClick = { dismiss(); startActivity<RssSourceEditActivity>() },
            )
            RoundDropdownMenuItem(
                text = getString(R.string.import_local),
                onClick = {
                    dismiss()
                    importDoc.launch {
                        mode = HandleFileContract.FILE
                        allowExtensions = arrayOf("txt", "json")
                    }
                },
            )
            RoundDropdownMenuItem(
                text = getString(R.string.import_on_line),
                onClick = { dismiss(); showImportDialog() },
            )
            RoundDropdownMenuItem(
                text = getString(R.string.import_by_qr_code),
                onClick = { dismiss(); qrCodeResult.launch() },
            )
            RoundDropdownMenuItem(
                text = getString(R.string.import_default_rule),
                onClick = { dismiss(); viewModel.importDefault() },
            )
            RoundDropdownMenuItem(
                text = getString(R.string.help),
                onClick = { dismiss(); showHelp("SourceMRssHelp") },
            )
        }
        val overflowId = View.generateViewId()
        menu.add(0, overflowId, 99, "").also {
            it.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            it.actionView = overflowView
        }
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // No longer needed — group menu handled by ComposeView
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_add -> startActivity<RssSourceEditActivity>()
            R.id.menu_import_local -> importDoc.launch {
                mode = HandleFileContract.FILE
                allowExtensions = arrayOf("txt", "json")
            }

            R.id.menu_import_onLine -> showImportDialog()
            R.id.menu_import_qr -> qrCodeResult.launch()
            R.id.menu_group_manage -> showDialogFragment<GroupManageDialog>()
            R.id.menu_import_default -> viewModel.importDefault()
            R.id.menu_enabled_group -> {
                searchView.setQuery(getString(R.string.enabled), true)
            }

            R.id.menu_disabled_group -> {
                searchView.setQuery(getString(R.string.disabled), true)
            }

            R.id.menu_group_login -> {
                searchView.setQuery(getString(R.string.need_login), true)
            }

            R.id.menu_group_null -> {
                searchView.setQuery(getString(R.string.no_group), true)
            }

            R.id.menu_help -> showHelp("SourceMRssHelp")
            else -> if (item.groupId == R.id.source_group) {
                searchView.setQuery("group:${item.title}", true)
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_enable_selection -> viewModel.enableSelection(adapter.selection)
            R.id.menu_disable_selection -> viewModel.disableSelection(adapter.selection)
            R.id.menu_add_group -> selectionAddToGroups()
            R.id.menu_remove_group -> selectionRemoveFromGroups()
            R.id.menu_top_sel -> viewModel.topSource(*adapter.selection.toTypedArray())
            R.id.menu_bottom_sel -> viewModel.bottomSource(*adapter.selection.toTypedArray())
            R.id.menu_export_selection -> viewModel.saveToFile(adapter.selection) { file ->
                exportResult.launch {
                    mode = HandleFileContract.EXPORT
                    fileData = HandleFileContract.FileData(
                        "exportRssSource.json", file, "application/json"
                    )
                }
            }

            R.id.menu_share_source -> viewModel.saveToFile(adapter.selection) {
                share(it)
            }

            R.id.menu_check_selected_interval -> adapter.checkSelectedInterval()
        }
        return true
    }

    private fun initRecyclerView() {
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.addItemDecoration(VerticalDivider(this))
        binding.recyclerView.adapter = adapter
        // When this page is opened, it is in selection mode
        val dragSelectTouchHelper: DragSelectTouchHelper =
            DragSelectTouchHelper(adapter.dragSelectCallback).setSlideArea(16, 50)
        dragSelectTouchHelper.attachToRecyclerView(binding.recyclerView)
        dragSelectTouchHelper.activeSlideSelect()
        // Note: need judge selection first, so add ItemTouchHelper after it.
        val itemTouchCallback = ItemTouchCallback(adapter)
        itemTouchCallback.isCanDrag = true
        ItemTouchHelper(itemTouchCallback).attachToRecyclerView(binding.recyclerView)
    }

    private fun initSearchView() {
        binding.titleBar.findViewById<SearchView>(R.id.search_view).let {
            it.applyTint(primaryTextColor)
            it.onActionViewExpanded()
            it.queryHint = getString(R.string.search_rss_source)
            it.clearFocus()
            it.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    return false
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    upSourceFlow(newText)
                    return false
                }
            })
        }
    }

    private fun initSelectActionBar() {
        binding.selectActionBar.setMainActionText(R.string.delete)
        binding.selectActionBar.inflateMenu(R.menu.rss_source_sel)
        binding.selectActionBar.setOnMenuItemClickListener(this)
        binding.selectActionBar.setCallBack(this)
    }

    private fun initGroupFlow() {
        lifecycleScope.launch {
            appDb.rssSourceDao.flowGroups().conflate().collect {
                groups.clear()
                groups.addAll(it)
                upGroupMenu()
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun selectionAddToGroups() {
        showM3EditDialog(
            titleRes = R.string.add_group,
            hintRes = R.string.group_name,
            suggestions = groups.toList(),
            onConfirm = { value ->
                if (value.isNotEmpty()) {
                    viewModel.selectionAddToGroups(adapter.selection, value)
                }
            },
        )
    }

    @SuppressLint("InflateParams")
    private fun selectionRemoveFromGroups() {
        showM3EditDialog(
            titleRes = R.string.remove_group,
            hintRes = R.string.group_name,
            suggestions = groups.toList(),
            onConfirm = { value ->
                if (value.isNotEmpty()) {
                    viewModel.selectionRemoveFromGroups(adapter.selection, value)
                }
            },
        )
    }

    override fun selectAll(selectAll: Boolean) {
        if (selectAll) {
            adapter.selectAll()
        } else {
            adapter.revertSelection()
        }
    }

    override fun revertSelection() {
        adapter.revertSelection()
    }

    override fun onClickSelectBarMainAction() {
        delSourceDialog()
    }

    private fun delSourceDialog() {
        alert(titleResource = R.string.draw, messageResource = R.string.sure_del) {
            yesButton { viewModel.del(*adapter.selection.toTypedArray()) }
            noButton()
        }
    }

    private fun upGroupMenu() = groupMenu?.transaction { menu ->
        menu.removeGroup(R.id.source_group)
        groups.forEach {
            menu.add(R.id.source_group, Menu.NONE, Menu.NONE, it)
        }
    }

    private fun upSourceFlow(searchKey: String? = null) {
        sourceFlowJob?.cancel()
        sourceFlowJob = lifecycleScope.launch {
            when {
                searchKey.isNullOrBlank() -> {
                    appDb.rssSourceDao.flowAll()
                }

                searchKey == getString(R.string.enabled) -> {
                    appDb.rssSourceDao.flowEnabled()
                }

                searchKey == getString(R.string.disabled) -> {
                    appDb.rssSourceDao.flowDisabled()
                }

                searchKey == getString(R.string.need_login) -> {
                    appDb.rssSourceDao.flowLogin()
                }

                searchKey == getString(R.string.no_group) -> {
                    appDb.rssSourceDao.flowNoGroup()
                }

                searchKey.startsWith("group:") -> {
                    val key = searchKey.substringAfter("group:")
                    appDb.rssSourceDao.flowGroupSearch(key)
                }

                else -> {
                    appDb.rssSourceDao.flowSearch(searchKey)
                }
            }.catch {
                AppLog.put("订阅源管理界面更新数据出错", it)
            }.flowOn(IO).conflate().collect {
                adapter.setItems(it, adapter.diffItemCallback)
                delay(100)
            }
        }
    }

    override fun upCountView() {
        binding.selectActionBar.upCountView(
            adapter.selection.size,
            adapter.itemCount
        )
    }

    @SuppressLint("InflateParams")
    private fun showImportDialog() {
        val aCache = ACache.get(cacheDir = false)
        val cacheUrls: MutableList<String> = aCache
            .getAsString(importRecordKey)
            ?.splitNotBlank(",")
            ?.toMutableList() ?: mutableListOf()
        showM3EditDialog(
            title = getString(R.string.import_on_line),
            hint = "url",
            suggestions = cacheUrls,
            onConfirm = { text ->
                if (text.isAbsUrl() && !cacheUrls.contains(text)) {
                    cacheUrls.add(0, text)
                    aCache.put(importRecordKey, cacheUrls.joinToString(","))
                }
                showDialogFragment(ImportRssSourceDialog(text))
            },
        )
    }

    override fun del(source: RssSource) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + source.sourceName)
            noButton()
            yesButton {
                viewModel.del(source)
            }
        }
    }

    override fun edit(source: RssSource) {
        startActivity<RssSourceEditActivity> {
            putExtra("sourceUrl", source.sourceUrl)
        }
    }

    override fun update(vararg source: RssSource) {
        viewModel.update(*source)
    }

    override fun toTop(source: RssSource) {
        viewModel.topSource(source)
    }

    override fun toBottom(source: RssSource) {
        viewModel.bottomSource(source)
    }

    override fun upOrder() {
        viewModel.upOrder()
    }

}