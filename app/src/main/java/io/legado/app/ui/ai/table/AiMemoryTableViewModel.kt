package io.legado.app.ui.ai.table

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.data.dao.BookDao
import io.legado.app.data.entities.AiMemoryTable
import io.legado.app.data.entities.AiMemoryTableExport
import io.legado.app.data.entities.AiMemoryTableRow
import io.legado.app.data.repository.AiToolRepository
import io.legado.app.domain.usecase.structured.MemoryTableMutator
import io.legado.app.domain.usecase.structured.MemoryTableRowMatcher
import io.legado.app.domain.usecase.structured.MutationSnapshotService
import io.legado.app.domain.gateway.AiChatGateway
import io.legado.app.domain.gateway.AiMemoryTableGateway
import io.legado.app.domain.gateway.AiToolConfigGateway
import io.legado.app.domain.gateway.AiToolGateway
import io.legado.app.domain.model.AiToolCall
import io.legado.app.utils.GSON
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import splitties.init.appCtx
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class AiMemoryTableViewModel(
    private val memoryTableGateway: AiMemoryTableGateway,
    private val memoryTableMutator: MemoryTableMutator,
    private val aiChatGateway: AiChatGateway,
    private val aiToolGateway: AiToolGateway,
    private val toolConfigGateway: AiToolConfigGateway,
    private val mutationSnapshotService: MutationSnapshotService,
    private val bookDao: BookDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiMemoryTableUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiMemoryTableEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    private var observeJob: Job? = null
    private var observeRowsJob: Job? = null

    init {
        observeTables()
    }

    fun onIntent(intent: AiMemoryTableIntent) {
        when (intent) {
            is AiMemoryTableIntent.SetConversationId -> setConversationId(intent.conversationId)
            AiMemoryTableIntent.RefreshList -> observeTables(_uiState.value.conversationId)
            is AiMemoryTableIntent.SelectTable -> selectTable(intent.tableId)
            AiMemoryTableIntent.BackToList -> backToList()
            is AiMemoryTableIntent.StartEditTable -> startEditTable(intent.table)
            is AiMemoryTableIntent.UpdateEditField -> updateEditField(intent.field, intent.value)
            AiMemoryTableIntent.SaveTable -> saveTable()
            AiMemoryTableIntent.CancelEdit -> cancelEdit()
            AiMemoryTableIntent.ShowBookPicker -> showBookPicker()
            AiMemoryTableIntent.DismissBookPicker -> dismissBookPicker()
            is AiMemoryTableIntent.SelectBook -> selectBook(intent.book)
            AiMemoryTableIntent.ClearBookSource -> clearBookSource()
            is AiMemoryTableIntent.DeleteTable -> deleteTable(intent.tableId)
            AiMemoryTableIntent.ToggleAllEnabled -> toggleAllEnabled()
            is AiMemoryTableIntent.RegenerateTable -> regenerateTable(intent.tableId)
            AiMemoryTableIntent.GenerateTable -> generateTable()
            is AiMemoryTableIntent.StartEditRow -> startEditRow(intent.row)
            is AiMemoryTableIntent.UpdateRowField -> updateRowField(intent.value)
            AiMemoryTableIntent.SaveRow -> saveRow()
            AiMemoryTableIntent.CancelEditRow -> cancelEditRow()
            is AiMemoryTableIntent.DeleteRow -> deleteRow(intent.rowId)
            is AiMemoryTableIntent.PatchCell -> patchCell(intent.rowId, intent.column, intent.value)
            is AiMemoryTableIntent.SetHighlightRow -> _uiState.update { it.copy(highlightRowId = intent.rowId) }
            is AiMemoryTableIntent.ResolveHighlightMatch -> resolveHighlightMatch(intent.matchHint)
            AiMemoryTableIntent.RestoreLatestSnapshot -> restoreLatestSnapshot()
            AiMemoryTableIntent.ConfirmUndoSnapshot -> confirmUndoSnapshot()
            AiMemoryTableIntent.DismissUndoSnapshot ->
                _uiState.update { it.copy(pendingUndo = null) }
            // Import / Export
            AiMemoryTableIntent.ShowExportDialog -> showExportDialog()
            AiMemoryTableIntent.DismissExportDialog -> dismissExportDialog()
            AiMemoryTableIntent.CopyExportToClipboard -> copyExportToClipboard()
            AiMemoryTableIntent.ShareExport -> shareExport()
            AiMemoryTableIntent.ShowImportDialog -> showImportDialog()
            AiMemoryTableIntent.DismissImportDialog -> dismissImportDialog()
            is AiMemoryTableIntent.UpdateImportJson -> updateImportJson(intent.json)
            AiMemoryTableIntent.ConfirmImport -> confirmImport()
            // Transfer between conversations
            AiMemoryTableIntent.ShowTransferMenu -> showTransferMenu()
            AiMemoryTableIntent.DismissTransferMenu -> dismissTransferMenu()
            AiMemoryTableIntent.ShowImportFromConversation -> showConversationPicker("import")
            AiMemoryTableIntent.ShowExportToConversation -> showConversationPicker("export")
            AiMemoryTableIntent.DismissConversationPicker -> dismissConversationPicker()
            is AiMemoryTableIntent.SelectConversationForTransfer -> selectConversationForTransfer(intent.conversationId)
            AiMemoryTableIntent.CleanupOrphans -> cleanupOrphans()
        }
    }

    private fun setConversationId(conversationId: String) {
        _uiState.update { it.copy(conversationId = conversationId) }
        // Re-observe tables scoped to this conversation + global
        observeTables(conversationId)
    }

    private fun observeTables(conversationId: String = "") {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            if (conversationId.isBlank()) {
                _uiState.update { it.copy(tables = persistentListOf(), allEnabled = true) }
                return@launch
            }
            memoryTableGateway.observeByConversation(conversationId).collect { tables ->
                _uiState.update { current ->
                    current.copy(
                        tables = tables.toImmutableList(),
                        allEnabled = tables.all { it.enabled }
                    )
                }
            }
        }
    }

    private fun selectTable(tableId: String) {
        observeRowsJob?.cancel()
        _uiState.update { it.copy(selectedTableId = tableId) }
        observeRowsJob = viewModelScope.launch {
            memoryTableGateway.observeRows(tableId).collect { rows ->
                _uiState.update { it.copy(selectedTableRows = rows.toImmutableList()) }
            }
        }
    }

    private fun backToList() {
        observeRowsJob?.cancel()
        observeRowsJob = null
        _uiState.update {
            it.copy(
                selectedTableId = null,
                selectedTableRows = persistentListOf(),
                isEditing = false,
                highlightRowId = null,
            )
        }
    }

    private fun startEditTable(table: AiMemoryTable?) {
        if (table != null) {
            _uiState.update {
                it.copy(
                    isEditing = true,
                    editingTableId = table.id,
                    editingTableName = table.name,
                    editingTableColumns = table.columns,
                    editingBookUrl = table.bookUrl,
                    editingBookName = table.bookName,
                    editingBookAuthor = table.bookAuthor,
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isEditing = true,
                    editingTableId = null,
                    editingTableName = "",
                    editingTableColumns = """["列1","列2","列3"]""",
                    editingBookUrl = "",
                    editingBookName = "",
                    editingBookAuthor = "",
                )
            }
        }
    }

    private fun updateEditField(field: String, value: String) {
        _uiState.update { current ->
            when (field) {
                "name" -> current.copy(editingTableName = value)
                "columns" -> current.copy(editingTableColumns = value)
                else -> current
            }
        }
    }

    private fun saveTable() {
        val state = _uiState.value
        if (state.editingTableName.isBlank()) {
            _effects.tryEmit(AiMemoryTableEffect.ShowMessage("Table name is required"))
            return
        }
        if (state.conversationId.isBlank()) {
            _effects.tryEmit(AiMemoryTableEffect.ShowMessage("请先打开会话后再建表"))
            return
        }
        viewModelScope.launch {
            val id = state.editingTableId ?: "memtable_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val table = io.legado.app.data.entities.AiMemoryTable(
                id = id,
                name = state.editingTableName,
                columns = state.editingTableColumns,
                conversationId = state.conversationId,
                bookUrl = state.editingBookUrl,
                bookName = state.editingBookName,
                bookAuthor = state.editingBookAuthor,
                enabled = true,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            runCatching {
                memoryTableGateway.upsertTable(table)
            }.onSuccess {
                _uiState.update { it.copy(isEditing = false, editingTableId = null) }
            }.onFailure {
                _effects.tryEmit(AiMemoryTableEffect.ShowMessage(it.message ?: "Save failed"))
            }
        }
    }

    private fun cancelEdit() {
        _uiState.update {
            it.copy(
                isEditing = false,
                editingTableId = null,
                showBookPicker = false,
            )
        }
    }

    private fun showBookPicker() {
        viewModelScope.launch {
            val books = withContext(Dispatchers.IO) {
                bookDao.all.sortedByDescending { it.durChapterTime }
            }
            _uiState.update { it.copy(showBookPicker = true, bookshelfBooks = books.toImmutableList()) }
        }
    }

    private fun dismissBookPicker() {
        _uiState.update { it.copy(showBookPicker = false) }
    }

    private fun selectBook(book: io.legado.app.data.entities.Book) {
        _uiState.update {
            it.copy(
                showBookPicker = false,
                editingBookUrl = book.bookUrl,
                editingBookName = book.name,
                editingBookAuthor = book.author,
            )
        }
    }

    private fun clearBookSource() {
        _uiState.update {
            it.copy(
                editingBookUrl = "",
                editingBookName = "",
                editingBookAuthor = "",
            )
        }
    }

    private fun deleteTable(tableId: String) {
        viewModelScope.launch {
            memoryTableGateway.deleteTable(tableId)
            if (_uiState.value.selectedTableId == tableId) {
                backToList()
            }
        }
    }

    private val memToolNames = listOf(
        "read_history_memory", "patch_history_memory",
    )

    private fun toggleAllEnabled() {
        viewModelScope.launch {
            val newState = !_uiState.value.allEnabled
            memoryTableGateway.setAllEnabled(newState)
            // Sync all memory table tools in tool management
            memToolNames.forEach { name ->
                val config = toolConfigGateway.getByToolName(name)
                if (config != null) {
                    toolConfigGateway.save(config.copy(enabled = newState))
                }
            }
            // Invalidate the tool cache so availableTools() picks up the change
            (aiToolGateway as? AiToolRepository)?.invalidateConfigCache()
        }
    }

    private fun regenerateTable(tableId: String) {
        _uiState.update {
            it.copy(
                isRegenerating = true,
                generationPreview = "",
                generationReasoning = "",
            )
        }
        viewModelScope.launch {
            try {
                val convId = _uiState.value.conversationId.takeIf { it.isNotBlank() }
                val result = memoryTableMutator.regenerateTable(
                    tableId = tableId,
                    conversationId = convId,
                    hint = null,
                    onPartial = { content, reasoning ->
                        _uiState.update {
                            it.copy(
                                generationPreview = content,
                                generationReasoning = reasoning.orEmpty(),
                            )
                        }
                    },
                )
                _effects.tryEmit(AiMemoryTableEffect.ShowMessage(result.message))
            } catch (e: Exception) {
                _effects.tryEmit(AiMemoryTableEffect.ShowMessage(e.message ?: "Regeneration failed"))
            } finally {
                _uiState.update {
                    it.copy(
                        isRegenerating = false,
                        generationPreview = "",
                        generationReasoning = "",
                    )
                }
            }
        }
    }

    private fun generateTable() {
        val convId = _uiState.value.conversationId
        if (convId.isBlank()) {
            _effects.tryEmit(AiMemoryTableEffect.ShowMessage("Open a conversation first to generate tables"))
            return
        }
        _uiState.update {
            it.copy(
                isGenerating = true,
                generationPreview = "",
                generationReasoning = "",
            )
        }
        viewModelScope.launch {
            try {
                val result = memoryTableMutator.generateTablesForConversation(
                    conversationId = convId,
                    onPartial = { content, reasoning ->
                        _uiState.update {
                            it.copy(
                                generationPreview = content,
                                generationReasoning = reasoning.orEmpty(),
                            )
                        }
                    },
                )
                _effects.tryEmit(AiMemoryTableEffect.ShowMessage(result.message))
            } catch (e: Exception) {
                _effects.tryEmit(AiMemoryTableEffect.ShowMessage(e.message ?: "Generation failed"))
            } finally {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        generationPreview = "",
                        generationReasoning = "",
                    )
                }
            }
        }
    }

    // ---- Row CRUD ----

    private fun startEditRow(row: AiMemoryTableRow?) {
        if (row != null) {
            _uiState.update {
                it.copy(showRowEditDialog = true, editingRowId = row.id, editingRowData = row.rowData)
            }
        } else {
            val tableColumns = runCatching {
                GSON.fromJson(_uiState.value.editingTableColumns, Array<String>::class.java)
            }.getOrNull()?.toList() ?: _uiState.value.tables
                .find { t -> t.id == _uiState.value.selectedTableId }?.columns
                ?.let { runCatching { GSON.fromJson(it, Array<String>::class.java).toList() }.getOrNull() }
                ?: emptyList()
            val emptyRow = tableColumns.associateWith { "" }
            _uiState.update {
                it.copy(
                    showRowEditDialog = true,
                    editingRowId = null,
                    editingRowData = GSON.toJson(emptyRow)
                )
            }
        }
    }

    private fun updateRowField(value: String) {
        _uiState.update { it.copy(editingRowData = value) }
    }

    private fun saveRow() {
        val state = _uiState.value
        val tableId = state.selectedTableId ?: return
        viewModelScope.launch {
            if (state.editingRowId != null) {
                val data = io.legado.app.utils.parseJsonStringMap(state.editingRowData)
                memoryTableMutator.patchRow(tableId, state.editingRowId, null, data)
            } else {
                val data = io.legado.app.utils.parseJsonStringMap(state.editingRowData)
                memoryTableMutator.addRow(tableId, data)
            }
            _uiState.update { it.copy(showRowEditDialog = false, editingRowId = null) }
        }
    }

    private fun cancelEditRow() {
        _uiState.update { it.copy(showRowEditDialog = false, editingRowId = null) }
    }

    private fun deleteRow(rowId: String) {
        viewModelScope.launch {
            memoryTableGateway.deleteRow(rowId)
            if (_uiState.value.highlightRowId == rowId) {
                _uiState.update { it.copy(highlightRowId = null) }
            }
        }
    }

    private fun patchCell(rowId: String, column: String, value: String) {
        val tableId = _uiState.value.selectedTableId ?: return
        viewModelScope.launch {
            memoryTableMutator.patchRow(tableId, rowId, null, mapOf(column to value))
        }
    }

    // ---- Import / Export ----

    private fun showExportDialog() {
        viewModelScope.launch {
            val convId = _uiState.value.conversationId
            if (convId.isBlank()) {
                _effects.tryEmit(AiMemoryTableEffect.ShowMessage("请先打开会话后再导出"))
                return@launch
            }
            val export = memoryTableGateway.exportForConversation(convId)
            val json = GSON.toJson(export)
            _uiState.update { it.copy(showExportDialog = true, exportJson = json) }
        }
    }

    private fun dismissExportDialog() {
        _uiState.update { it.copy(showExportDialog = false, exportJson = "") }
    }

    private fun copyExportToClipboard() {
        val json = _uiState.value.exportJson
        if (json.isNotBlank()) {
            _effects.tryEmit(AiMemoryTableEffect.CopyToClipboard(json))
            _effects.tryEmit(AiMemoryTableEffect.ShowMessage("Copied to clipboard"))
        }
    }

    private fun shareExport() {
        val json = _uiState.value.exportJson
        if (json.isNotBlank()) {
            _effects.tryEmit(AiMemoryTableEffect.ShareText(json, "Memory Tables Export"))
        }
    }

    private fun showImportDialog() {
        _uiState.update { it.copy(showImportDialog = true, importJson = "") }
    }

    private fun dismissImportDialog() {
        _uiState.update { it.copy(showImportDialog = false, importJson = "") }
    }

    private fun updateImportJson(json: String) {
        _uiState.update { it.copy(importJson = json) }
    }

    private fun confirmImport() {
        val json = _uiState.value.importJson
        if (json.isBlank()) {
            _effects.tryEmit(AiMemoryTableEffect.ShowMessage("Paste the exported JSON first"))
            return
        }
        viewModelScope.launch {
            runCatching {
                val export = GSON.fromJson(json, AiMemoryTableExport::class.java)
                // Remap conversationId on imported tables to match current scope
                val targetConvId = _uiState.value.conversationId
                if (targetConvId.isBlank()) {
                    throw IllegalStateException("请先打开会话后再导入")
                }
                val remappedTables = export.tables.map { twr ->
                    twr.copy(
                        table = twr.table.copy(conversationId = targetConvId)
                    )
                }
                val remappedExport = export.copy(
                    conversationId = targetConvId,
                    tables = remappedTables
                )
                memoryTableGateway.importFromExport(remappedExport)
            }.onSuccess {
                _uiState.update { it.copy(showImportDialog = false, importJson = "") }
                _effects.tryEmit(AiMemoryTableEffect.ShowMessage("Import successful"))
            }.onFailure {
                _effects.tryEmit(AiMemoryTableEffect.ShowMessage(it.message ?: "Import failed"))
            }
        }
    }

    // ---- Transfer between conversations ----

    private fun showTransferMenu() {
        _uiState.update { it.copy(showTransferMenu = true) }
    }

    private fun dismissTransferMenu() {
        _uiState.update { it.copy(showTransferMenu = false) }
    }

    private fun cleanupOrphans() {
        _uiState.update { it.copy(showTransferMenu = false) }
        viewModelScope.launch {
            val count = runCatching { memoryTableGateway.cleanupOrphanTables() }.getOrElse { 0 }
            _effects.tryEmit(
                AiMemoryTableEffect.ShowMessage(
                    if (count > 0) "已清理 $count 张脏数据表" else "没有需要清理的脏数据表"
                )
            )
        }
    }

    private fun showConversationPicker(mode: String) {
        _uiState.update { it.copy(showTransferMenu = false, showConversationPicker = true, conversationPickerMode = mode) }
        viewModelScope.launch {
            val currentId = _uiState.value.conversationId
            val conversations = aiChatGateway.listConversations(50)
                .filter { it.id != currentId } // exclude current conversation
            _uiState.update {
                it.copy(
                    availableConversations = conversations.toImmutableList()
                )
            }
        }
    }

    private fun dismissConversationPicker() {
        _uiState.update { it.copy(showConversationPicker = false, availableConversations = persistentListOf()) }
    }

    private fun selectConversationForTransfer(targetConversationId: String) {
        val state = _uiState.value
        val currentId = state.conversationId
        val mode = state.conversationPickerMode
        viewModelScope.launch {
            try {
                val count = if (mode == "import") {
                    memoryTableGateway.copyTablesToConversation(targetConversationId, currentId)
                } else {
                    memoryTableGateway.copyTablesToConversation(currentId, targetConversationId)
                }
                _uiState.update { it.copy(showConversationPicker = false, availableConversations = persistentListOf()) }
                _effects.tryEmit(
                    AiMemoryTableEffect.ShowMessage(
                        if (count > 0) "Transferred $count table(s)"
                        else "No tables to transfer"
                    )
                )
            } catch (e: Exception) {
                _effects.tryEmit(AiMemoryTableEffect.ShowMessage(e.message ?: "Transfer failed"))
            }
        }
    }

    private fun resolveHighlightMatch(matchHint: String) {
        val tableId = _uiState.value.selectedTableId ?: return
        val table = _uiState.value.tables.find { it.id == tableId } ?: return
        val columns = runCatching {
            GSON.fromJson(table.columns, Array<String>::class.java).toList()
        }.getOrNull() ?: emptyList()
        viewModelScope.launch {
            val match = parseMatchHint(matchHint) ?: return@launch
            val row = MemoryTableRowMatcher.findMatchingRows(
                rows = memoryTableGateway.getRows(tableId),
                match = match,
                columns = columns,
                tableName = table.name,
                parseRowData = { rowData -> io.legado.app.utils.parseJsonStringMap(rowData) },
            ).singleOrNull()
            if (row != null) {
                _uiState.update { it.copy(highlightRowId = row.id) }
            }
        }
    }

    private fun restoreLatestSnapshot() {
        val convId = _uiState.value.conversationId.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            val snapshot = mutationSnapshotService.latestMemoryTableForConversation(convId)
            if (snapshot == null) {
                _effects.tryEmit(
                    AiMemoryTableEffect.ShowMessage(
                        appCtx.getString(R.string.ai_memory_undo_empty),
                    ),
                )
                return@launch
            }
            val preview = mutationSnapshotService.previewRestore(snapshot.id)
            if (preview == null) {
                _effects.tryEmit(
                    AiMemoryTableEffect.ShowMessage(
                        appCtx.getString(R.string.ai_snapshot_not_found),
                    ),
                )
                return@launch
            }
            _uiState.update {
                it.copy(
                    pendingUndo = PendingMemoryUndoUi(
                        snapshotId = preview.snapshotId,
                        summary = preview.summary,
                        changes = preview.changes.map { fc ->
                            io.legado.app.ui.ai.chat.FieldChangeUi(fc.path, fc.oldValue, fc.newValue)
                        }.toImmutableList(),
                    ),
                )
            }
        }
    }

    private fun confirmUndoSnapshot() {
        val pending = _uiState.value.pendingUndo ?: return
        _uiState.update { it.copy(pendingUndo = null) }
        viewModelScope.launch {
            val result = mutationSnapshotService.restore(pending.snapshotId)
            val msg = if (result.success) {
                appCtx.getString(R.string.ai_memory_undo_success)
            } else {
                appCtx.getString(
                    R.string.ai_memory_undo_failed,
                    result.message,
                )
            }
            _effects.tryEmit(AiMemoryTableEffect.ShowMessage(msg))
        }
    }

    private fun parseMatchHint(matchHint: String): Map<String, String>? {
        val pairs = matchHint.split(',').mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            part.substring(0, idx).trim() to part.substring(idx + 1).trim()
        }
        return pairs.toMap().takeIf { it.isNotEmpty() }
    }

    companion object {
        private fun <T> persistentListOf(): kotlinx.collections.immutable.ImmutableList<T> =
            kotlinx.collections.immutable.persistentListOf()
    }
}
