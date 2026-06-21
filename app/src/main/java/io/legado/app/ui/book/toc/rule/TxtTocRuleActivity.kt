package io.legado.app.ui.book.toc.rule

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.databinding.ActivityTxtTocRuleBinding
import io.legado.app.utils.showM3EditDialog
import io.legado.app.help.DirectLinkUpload
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.association.ImportTxtTocRuleDialog
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.widget.SelectActionBar
import io.legado.app.ui.widget.recycler.DragSelectTouchHelper
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.ACache
import io.legado.app.utils.GSON
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.launch
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.ui.common.compose.RoundDropdownMenuItem
import io.legado.app.ui.common.compose.createComposeDropdownIcon
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class TxtTocRuleActivity : VMBaseActivity<ActivityTxtTocRuleBinding, TxtTocRuleViewModel>(),
    TxtTocRuleAdapter.CallBack,
    SelectActionBar.CallBack,
    TxtTocRuleEditDialog.Callback,
    PopupMenu.OnMenuItemClickListener {

    override val viewModel by viewModels<TxtTocRuleViewModel>()
    override val binding by viewBinding(ActivityTxtTocRuleBinding::inflate)
    private val adapter: TxtTocRuleAdapter by lazy {
        TxtTocRuleAdapter(this, this)
    }
    private val importTocRuleKey = "tocRuleUrl"
    private val qrCodeResult = registerForActivityResult(QrCodeResult()) {
        it ?: return@registerForActivityResult
        showDialogFragment(ImportTxtTocRuleDialog(it))
    }
    private val importDoc = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            showDialogFragment(ImportTxtTocRuleDialog(uri.toString()))
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
        initView()
        initBottomActionBar()
        initData()
    }

    private fun initView() = binding.run {
        recyclerView.setEdgeEffectColor(primaryColor)
        recyclerView.addItemDecoration(VerticalDivider(this@TxtTocRuleActivity))
        recyclerView.adapter = adapter
        // When this page is opened, it is in selection mode
        val dragSelectTouchHelper =
            DragSelectTouchHelper(adapter.dragSelectCallback).setSlideArea(16, 50)
        dragSelectTouchHelper.attachToRecyclerView(binding.recyclerView)
        dragSelectTouchHelper.activeSlideSelect()
        // Note: need judge selection first, so add ItemTouchHelper after it.
        val itemTouchCallback = ItemTouchCallback(adapter)
        itemTouchCallback.isCanDrag = true
        ItemTouchHelper(itemTouchCallback).attachToRecyclerView(binding.recyclerView)
    }

    private fun initBottomActionBar() {
        binding.selectActionBar.setMainActionText(R.string.delete)
        binding.selectActionBar.inflateMenu(R.menu.txt_toc_rule_sel)
        binding.selectActionBar.setOnMenuItemClickListener(this)
        binding.selectActionBar.setCallBack(this)
    }

    private fun initData() {
        lifecycleScope.launch {
            appDb.txtTocRuleDao.observeAll().catch {
                AppLog.put("TXT目录规则界面获取数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).conflate().collect { tocRules ->
                adapter.setItems(tocRules, adapter.diffItemCallBack)
            }
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        // Always-visible: Add button
        menu.add(0, R.id.menu_add, 0, getString(R.string.add)).also {
            it.setIcon(R.drawable.ic_add)
            it.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        // Overflow items via Compose RoundDropdownMenu with spring bounce
        val overflowView = createComposeDropdownIcon { dismiss ->
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
                onClick = { dismiss(); showHelp("txtTocRuleHelp") },
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
            R.id.menu_add -> showDialogFragment(TxtTocRuleEditDialog())
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun del(source: TxtTocRule) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + source.name)
            noButton()
            yesButton {
                viewModel.del(source)
            }
        }
    }

    override fun edit(source: TxtTocRule) {
        showDialogFragment(TxtTocRuleEditDialog(source.id))
    }

    override fun onClickSelectBarMainAction() {
        delSourceDialog()
    }

    override fun revertSelection() {
        adapter.revertSelection()
    }

    override fun selectAll(selectAll: Boolean) {
        if (selectAll) {
            adapter.selectAll()
        } else {
            adapter.revertSelection()
        }
    }

    override fun saveTxtTocRule(txtTocRule: TxtTocRule) {
        viewModel.save(txtTocRule)
    }

    override fun update(vararg source: TxtTocRule) {
        viewModel.update(*source)
    }

    override fun toTop(source: TxtTocRule) {
        viewModel.toTop(source)
    }

    override fun toBottom(source: TxtTocRule) {
        viewModel.toBottom(source)
    }

    override fun upOrder() {
        viewModel.upOrder()
    }

    override fun upCountView() {
        binding.selectActionBar
            .upCountView(adapter.selection.size, adapter.itemCount)
    }

    private fun delSourceDialog() {
        alert(titleResource = R.string.draw, messageResource = R.string.sure_del) {
            yesButton { viewModel.del(*adapter.selection.toTypedArray()) }
            noButton()
        }
    }

    @SuppressLint("InflateParams")
    private fun showImportDialog() {
        val aCache = ACache.get(cacheDir = false)
        val defaultUrl = "https://gitee.com/fisher52/YueDuJson/raw/master/myTxtChapterRule.json"
        val cacheUrls: MutableList<String> = aCache
            .getAsString(importTocRuleKey)
            ?.splitNotBlank(",")
            ?.toMutableList()
            ?: mutableListOf()
        if (!cacheUrls.contains(defaultUrl)) {
            cacheUrls.add(0, defaultUrl)
        }
        showM3EditDialog(
            title = getString(R.string.import_on_line),
            hint = "url",
            suggestions = cacheUrls,
            onConfirm = { text ->
                if (text.isAbsUrl() && !cacheUrls.contains(text)) {
                    cacheUrls.add(0, text)
                    aCache.put(importTocRuleKey, cacheUrls.joinToString(","))
                }
                showDialogFragment(ImportTxtTocRuleDialog(text))
            },
        )
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_enable_selection -> viewModel.enableSelection(
                *adapter.selection.toTypedArray()
            )

            R.id.menu_disable_selection -> viewModel.disableSelection(
                *adapter.selection.toTypedArray()
            )

            R.id.menu_export_selection -> exportResult.launch {
                mode = HandleFileContract.EXPORT
                fileData = HandleFileContract.FileData(
                    "exportTxtTocRule.json",
                    GSON.toJson(adapter.selection).toByteArray(),
                    "application/json"
                )
            }
        }
        return true
    }

}
