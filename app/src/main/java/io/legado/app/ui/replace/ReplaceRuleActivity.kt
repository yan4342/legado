package io.legado.app.ui.replace

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.databinding.ActivityReplaceRuleBinding
import io.legado.app.utils.showM3EditDialog
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.ui.association.ImportReplaceRuleDialog
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.replace.edit.ReplaceEditActivity
import io.legado.app.ui.widget.SelectActionBar
import io.legado.app.ui.widget.recycler.DragSelectTouchHelper
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.ACache
import io.legado.app.utils.GSON
import io.legado.app.utils.applyTint
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.launch
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.splitNotBlank
import io.legado.app.ui.common.compose.RoundDropdownMenuItem
import io.legado.app.ui.common.compose.createComposeDropdownIcon
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
 * 替换规则管理
 */
class ReplaceRuleActivity : VMBaseActivity<ActivityReplaceRuleBinding, ReplaceRuleViewModel>(),
    SearchView.OnQueryTextListener,
    PopupMenu.OnMenuItemClickListener,
    SelectActionBar.CallBack,
    ReplaceRuleAdapter.CallBack {
    override val binding by viewBinding(ActivityReplaceRuleBinding::inflate)
    override val viewModel by viewModels<ReplaceRuleViewModel>()
    private val importRecordKey = "replaceRuleRecordKey"
    private val adapter by lazy { ReplaceRuleAdapter(this, this) }
    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }
    private var groups = arrayListOf<String>()
    private var groupMenu: SubMenu? = null
    private var replaceRuleFlowJob: Job? = null
    private var dataInit = false
    private val qrCodeResult = registerForActivityResult(QrCodeResult()) {
        it ?: return@registerForActivityResult
        showDialogFragment(ImportReplaceRuleDialog(it))
    }
    private val editActivity =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                setResult(RESULT_OK)
            }
        }
    private val importDoc = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            showDialogFragment(ImportReplaceRuleDialog(uri.toString()))
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
        initSelectActionView()
        observeReplaceRuleData()
        observeGroupData()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        // Group icon with dynamic submenu via Compose RoundDropdownMenu (spring bounce)
        val groupView = createComposeDropdownIcon { dismiss ->
            RoundDropdownMenuItem(
                text = getString(R.string.group_manage),
                onClick = { dismiss(); showDialogFragment<GroupManageDialog>() },
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
                text = getString(R.string.add_replace_rule),
                onClick = { dismiss(); editActivity.launch(ReplaceEditActivity.startIntent(this@ReplaceRuleActivity)) },
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
                text = getString(R.string.help),
                onClick = { dismiss(); showHelp("replaceRuleHelp") },
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

    private fun initRecyclerView() {
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.addItemDecoration(VerticalDivider(this))
        val itemTouchCallback = ItemTouchCallback(adapter)
        itemTouchCallback.isCanDrag = true
        val dragSelectTouchHelper: DragSelectTouchHelper =
            DragSelectTouchHelper(adapter.dragSelectCallback).setSlideArea(16, 50)
        dragSelectTouchHelper.attachToRecyclerView(binding.recyclerView)
        // When this page is opened, it is in selection mode
        dragSelectTouchHelper.activeSlideSelect()

        // Note: need judge selection first, so add ItemTouchHelper after it.
        ItemTouchHelper(itemTouchCallback).attachToRecyclerView(binding.recyclerView)
    }

    private fun initSearchView() {
        searchView.applyTint(primaryTextColor)
        searchView.queryHint = getString(R.string.replace_purify_search)
        searchView.setOnQueryTextListener(this)
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
        alert(titleResource = R.string.draw, messageResource = R.string.sure_del) {
            yesButton { viewModel.delSelection(adapter.selection) }
            noButton()
        }
    }

    private fun initSelectActionView() {
        binding.selectActionBar.setMainActionText(R.string.delete)
        binding.selectActionBar.inflateMenu(R.menu.replace_rule_sel)
        binding.selectActionBar.setOnMenuItemClickListener(this)
        binding.selectActionBar.setCallBack(this)
    }

    private fun observeReplaceRuleData(searchKey: String? = null) {
        dataInit = false
        replaceRuleFlowJob?.cancel()
        replaceRuleFlowJob = lifecycleScope.launch {
            when {
                searchKey.isNullOrEmpty() -> {
                    appDb.replaceRuleDao.flowAll()
                }

                searchKey == getString(R.string.no_group) -> {
                    appDb.replaceRuleDao.flowNoGroup()
                }

                searchKey.startsWith("group:") -> {
                    val key = searchKey.substringAfter("group:")
                    appDb.replaceRuleDao.flowGroupSearch("%$key%")
                }

                else -> {
                    appDb.replaceRuleDao.flowSearch("%$searchKey%")
                }
            }.catch {
                AppLog.put("替换规则管理界面更新数据出错", it)
            }.flowOn(IO).conflate().collect {
                if (dataInit) {
                    setResult(Activity.RESULT_OK)
                }
                adapter.setItems(it, adapter.diffItemCallBack)
                dataInit = true
                delay(100)
            }
        }
    }

    private fun observeGroupData() {
        lifecycleScope.launch {
            appDb.replaceRuleDao.flowGroups().collect {
                groups.clear()
                groups.addAll(it)
                upGroupMenu()
            }
        }
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_add_replace_rule ->
                editActivity.launch(ReplaceEditActivity.startIntent(this))

            R.id.menu_group_manage -> showDialogFragment<GroupManageDialog>()
            R.id.menu_del_selection -> viewModel.delSelection(adapter.selection)
            R.id.menu_import_onLine -> showImportDialog()
            R.id.menu_import_local -> importDoc.launch {
                mode = HandleFileContract.FILE
                allowExtensions = arrayOf("txt", "json")
            }

            R.id.menu_import_qr -> qrCodeResult.launch()
            R.id.menu_help -> showHelp("replaceRuleHelp")
            R.id.menu_group_null -> {
                searchView.setQuery(getString(R.string.no_group), true)
            }

            else -> if (item.groupId == R.id.replace_group) {
                searchView.setQuery("group:${item.title}", true)
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_enable_selection -> viewModel.enableSelection(adapter.selection)
            R.id.menu_disable_selection -> viewModel.disableSelection(adapter.selection)
            R.id.menu_top_sel -> viewModel.topSelect(adapter.selection)
            R.id.menu_bottom_sel -> viewModel.bottomSelect(adapter.selection)
            R.id.menu_export_selection -> exportResult.launch {
                mode = HandleFileContract.EXPORT
                fileData = HandleFileContract.FileData(
                    "exportReplaceRule.json",
                    GSON.toJson(adapter.selection).toByteArray(),
                    "application/json"
                )
            }
        }
        return false
    }

    private fun upGroupMenu() = groupMenu?.transaction { menu ->
        menu.removeGroup(R.id.replace_group)
        groups.forEach {
            menu.add(R.id.replace_group, Menu.NONE, Menu.NONE, it)
        }
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
                showDialogFragment(ImportReplaceRuleDialog(text))
            },
        )
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        observeReplaceRuleData(newText)
        return false
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        Coroutine.async { ContentProcessor.upReplaceRules() }
    }

    override fun upCountView() {
        binding.selectActionBar.upCountView(
            adapter.selection.size,
            adapter.itemCount
        )
    }

    override fun update(vararg rule: ReplaceRule) {
        setResult(RESULT_OK)
        viewModel.update(*rule)
    }

    override fun delete(rule: ReplaceRule) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + rule.name)
            noButton()
            yesButton {
                setResult(RESULT_OK)
                viewModel.delete(rule)
            }
        }
    }

    override fun edit(rule: ReplaceRule) {
        setResult(RESULT_OK)
        editActivity.launch(ReplaceEditActivity.startIntent(this, rule.id))
    }

    override fun toTop(rule: ReplaceRule) {
        setResult(RESULT_OK)
        viewModel.toTop(rule)
    }

    override fun toBottom(rule: ReplaceRule) {
        setResult(RESULT_OK)
        viewModel.toBottom(rule)
    }

    override fun upOrder() {
        setResult(RESULT_OK)
        viewModel.upOrder()
    }
}