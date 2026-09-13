package io.legado.app.ui.ai.worldbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import io.legado.app.data.dao.BookDao
import io.legado.app.data.entities.AiWorldBook
import io.legado.app.data.entities.Book
import io.legado.app.domain.gateway.AiWorldBookEntryGateway
import io.legado.app.domain.gateway.AiWorldBookGateway
import io.legado.app.domain.gateway.AiWritingPromptGateway
import io.legado.app.domain.usecase.ExtractWorldBookUseCase
import io.legado.app.domain.usecase.ImportWorldInfoUseCase
import io.legado.app.domain.model.WorldBookPatch
import io.legado.app.domain.usecase.structured.WorldBookMutator
import io.legado.app.R
import splitties.init.appCtx
import io.legado.app.utils.GSON
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiWorldBookViewModel(
    private val worldBookGateway: AiWorldBookGateway,
    private val worldBookEntryGateway: AiWorldBookEntryGateway,
    private val worldBookMutator: WorldBookMutator,
    private val extractUseCase: ExtractWorldBookUseCase,
    private val importWorldInfoUseCase: ImportWorldInfoUseCase,
    private val writingPromptGateway: AiWritingPromptGateway,
    private val bookDao: BookDao
) : ViewModel() {

    private companion object {
        const val TAG = "StWorldImport"
    }

    private val _uiState = MutableStateFlow(AiWorldBookUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiWorldBookEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    private var entriesJob: Job? = null

    init {
        observeWorldBooks()
    }

    fun onIntent(intent: AiWorldBookIntent) {
        when (intent) {
            is AiWorldBookIntent.LoadList -> observeWorldBooks()
            is AiWorldBookIntent.StartEdit -> startEdit(intent.existing)
            is AiWorldBookIntent.UpdateField -> updateField(intent.field, intent.value)
            AiWorldBookIntent.Save -> save()
            is AiWorldBookIntent.Delete -> delete(intent.id)
            AiWorldBookIntent.CancelEdit -> cancelEdit()
            is AiWorldBookIntent.ToggleEnabled -> toggleEnabled(intent.id)
            AiWorldBookIntent.ShowBookPicker -> showBookPicker()
            AiWorldBookIntent.DismissBookPicker -> dismissBookPicker()
            is AiWorldBookIntent.SelectBook -> selectBook(intent.book)
            is AiWorldBookIntent.SetChapterRange -> setChapterRange(intent.start, intent.end)
            AiWorldBookIntent.StartExtraction -> startExtraction()
            is AiWorldBookIntent.ExportStyleToPrompts -> exportToPrompts(intent.worldBookId, "writingStyle")
            is AiWorldBookIntent.ExportGrammarToPrompts -> exportToPrompts(intent.worldBookId, "grammar")
            is AiWorldBookIntent.StartEntryEdit -> startEntryEdit(intent.entry)
            is AiWorldBookIntent.UpdateEntryField -> updateEntryField(intent.field, intent.value)
            AiWorldBookIntent.SaveEntry -> saveEntry()
            AiWorldBookIntent.CancelEntryEdit -> _uiState.update { it.copy(showEntryEditor = false, editingEntry = null) }
            is AiWorldBookIntent.DeleteEntry -> deleteEntry(intent.id)
            is AiWorldBookIntent.ToggleEntryEnabled -> toggleEntry(intent.id)
            is AiWorldBookIntent.ImportWorldInfoJson -> importWorldInfo(intent.json, intent.fallbackName)
            is AiWorldBookIntent.ImportWorldInfoBytes -> importWorldInfoBytes(intent.bytes, intent.fallbackName)
            is AiWorldBookIntent.ImportEntriesIntoCurrent -> importEntriesIntoCurrent(intent.json)
            is AiWorldBookIntent.ImportEntriesBytesIntoCurrent -> importEntriesBytesIntoCurrent(intent.bytes)
        }
    }

    private fun importEntriesIntoCurrent(json: String) {
        val worldBookId = _uiState.value.editingExistingId ?: run {
            _effects.tryEmit(AiWorldBookEffect.ShowMessage(appCtx.getString(R.string.ai_world_book_import_need_save_first)))
            return
        }
        viewModelScope.launch {
            Log.i(TAG, "importEntriesIntoCurrent jsonLen=${json.length} worldBookId=$worldBookId")
            runCatching {
                val parsed = io.legado.app.domain.model.SillyTavernWorldInfoImporter.parseFlexible(json)
                importWorldInfoUseCase.upsertEntryDrafts(worldBookId, parsed.entries)
                parsed
            }.onSuccess { parsed ->
                Log.i(TAG, "importEntriesIntoCurrent ok entries=${parsed.entries.size}")
                val msg = buildString {
                    append(appCtx.getString(R.string.ai_world_book_import_entries, parsed.entries.size))
                    if (parsed.stats.ignoredAdvanced) {
                        append(" · ")
                        append(appCtx.getString(R.string.ai_world_book_import_advanced_ignored))
                    }
                }
                _effects.tryEmit(AiWorldBookEffect.ShowMessage(msg))
            }.onFailure {
                Log.e(TAG, "importEntriesIntoCurrent failed: ${it.message}", it)
                _effects.tryEmit(
                    AiWorldBookEffect.ShowMessage(it.message ?: appCtx.getString(R.string.ai_world_book_import_failed)),
                )
            }
        }
    }

    private fun importEntriesBytesIntoCurrent(bytes: ByteArray) {
        val worldBookId = _uiState.value.editingExistingId ?: run {
            _effects.tryEmit(AiWorldBookEffect.ShowMessage(appCtx.getString(R.string.ai_world_book_import_need_save_first)))
            return
        }
        viewModelScope.launch {
            Log.i(TAG, "importEntriesBytesIntoCurrent size=${bytes.size} worldBookId=$worldBookId")
            runCatching {
                val parsed = importWorldInfoUseCase.parseBytesToDrafts(bytes)
                importWorldInfoUseCase.upsertEntryDrafts(worldBookId, parsed.entries)
                parsed
            }.onSuccess { parsed ->
                Log.i(TAG, "importEntriesBytesIntoCurrent ok entries=${parsed.entries.size}")
                val msg = buildString {
                    append(appCtx.getString(R.string.ai_world_book_import_entries, parsed.entries.size))
                    if (parsed.stats.ignoredAdvanced) {
                        append(" · ")
                        append(appCtx.getString(R.string.ai_world_book_import_advanced_ignored))
                    }
                }
                _effects.tryEmit(AiWorldBookEffect.ShowMessage(msg))
            }.onFailure {
                Log.e(TAG, "importEntriesBytesIntoCurrent failed: ${it.message}", it)
                _effects.tryEmit(
                    AiWorldBookEffect.ShowMessage(it.message ?: appCtx.getString(R.string.ai_world_book_import_failed)),
                )
            }
        }
    }

    private fun importWorldInfo(json: String, fallbackName: String) {
        viewModelScope.launch {
            Log.i(TAG, "importWorldInfo jsonLen=${json.length} fallback=$fallbackName")
            runCatching {
                importWorldInfoUseCase.importJson(json, fallbackName)
            }.onSuccess { result ->
                Log.i(
                    TAG,
                    "importWorldInfo ok name=${result.worldBookName} entries=${result.entryCount} card=${result.characterCardName}",
                )
                _effects.tryEmit(AiWorldBookEffect.ShowMessage(formatImportResultMessage(result)))
            }.onFailure {
                Log.e(TAG, "importWorldInfo failed: ${it.message}", it)
                _effects.tryEmit(
                    AiWorldBookEffect.ShowMessage(it.message ?: appCtx.getString(R.string.ai_world_book_import_failed)),
                )
            }
        }
    }

    private fun importWorldInfoBytes(bytes: ByteArray, fallbackName: String) {
        viewModelScope.launch {
            Log.i(TAG, "importWorldInfoBytes size=${bytes.size} fallback=$fallbackName")
            runCatching {
                importWorldInfoUseCase.importBytes(bytes, fallbackName)
            }.onSuccess { result ->
                Log.i(
                    TAG,
                    "importWorldInfoBytes ok name=${result.worldBookName} entries=${result.entryCount} card=${result.characterCardName}",
                )
                _effects.tryEmit(AiWorldBookEffect.ShowMessage(formatImportResultMessage(result)))
            }.onFailure {
                Log.e(TAG, "importWorldInfoBytes failed: ${it.message}", it)
                _effects.tryEmit(
                    AiWorldBookEffect.ShowMessage(it.message ?: appCtx.getString(R.string.ai_world_book_import_failed)),
                )
            }
        }
    }

    private fun formatImportResultMessage(result: io.legado.app.domain.model.ImportWorldInfoResult): String {
        return buildString {
            if (!result.characterCardName.isNullOrBlank()) {
                append(
                    appCtx.getString(
                        R.string.ai_world_book_import_with_card,
                        result.characterCardName,
                        result.worldBookName,
                        result.entryCount,
                    ),
                )
            } else {
                append(
                    appCtx.getString(
                        R.string.ai_world_book_import_success,
                        result.worldBookName,
                        result.entryCount,
                    ),
                )
            }
            if (result.ignoredAdvanced) {
                append(" · ")
                append(appCtx.getString(R.string.ai_world_book_import_advanced_ignored))
            }
        }
    }

    private fun observeEntries(worldBookId: String?) {
        entriesJob?.cancel()
        entriesJob = null
        if (worldBookId.isNullOrBlank()) {
            _uiState.update { it.copy(entries = persistentListOf()) }
            return
        }
        entriesJob = viewModelScope.launch {
            worldBookEntryGateway.observeForWorldBook(worldBookId).collect { entries ->
                _uiState.update { it.copy(entries = entries.toImmutableList()) }
            }
        }
    }

    private fun startEntryEdit(entry: io.legado.app.data.entities.AiWorldBookEntry?) {
        val worldBookId = _uiState.value.editingExistingId ?: return
        _uiState.update {
            it.copy(
                showEntryEditor = true,
                editingEntry = entry ?: io.legado.app.data.entities.AiWorldBookEntry(
                    id = "wbe_${System.currentTimeMillis()}",
                    worldBookId = worldBookId,
                ),
            )
        }
    }

    private fun updateEntryField(field: String, value: String) {
        val entry = _uiState.value.editingEntry ?: return
        _uiState.update {
            it.copy(
                editingEntry = when (field) {
                    "name" -> entry.copy(name = value)
                    "keys" -> entry.copy(keys = value)
                    "content" -> entry.copy(content = value)
                    "priority" -> entry.copy(priority = value.toIntOrNull() ?: entry.priority)
                    "constant" -> entry.copy(constant = value.equals("true", ignoreCase = true))
                    "enabled" -> entry.copy(enabled = value.equals("true", ignoreCase = true))
                    "position" -> entry.copy(position = value)
                    "insertDepth" -> entry.copy(insertDepth = value.toIntOrNull()?.coerceAtLeast(0) ?: entry.insertDepth)
                    "role" -> entry.copy(role = value)
                    "scanDepth" -> entry.copy(scanDepth = value.toIntOrNull()?.coerceAtLeast(0) ?: entry.scanDepth)
                    else -> entry
                },
            )
        }
    }

    private fun saveEntry() {
        val entry = _uiState.value.editingEntry ?: return
        if (entry.content.isBlank()) {
            _effects.tryEmit(AiWorldBookEffect.ShowMessage("Entry content is required"))
            return
        }
        viewModelScope.launch {
            worldBookEntryGateway.upsert(entry)
            _uiState.update { it.copy(showEntryEditor = false, editingEntry = null) }
        }
    }

    private fun deleteEntry(id: String) {
        viewModelScope.launch { worldBookEntryGateway.delete(id) }
    }

    private fun toggleEntry(id: String) {
        viewModelScope.launch {
            val entry = worldBookEntryGateway.getById(id) ?: return@launch
            worldBookEntryGateway.upsert(
                entry.copy(
                    enabled = !entry.enabled,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun observeWorldBooks() {
        viewModelScope.launch {
            worldBookGateway.observeAll().collect { books ->
                _uiState.update { it.copy(worldBooks = books.toImmutableList()) }
            }
        }
    }

    private fun startEdit(existing: AiWorldBook?) {
        if (existing != null) {
            observeEntries(existing.id)
            _uiState.update { current ->
                current.copy(
                    isEditing = true,
                    editingExistingId = existing.id,
                    pendingEntryDrafts = persistentListOf(),
                    editFields = WorldBookEditFields(
                        name = existing.name,
                        bookUrl = existing.bookUrl,
                        bookName = existing.bookName,
                        bookAuthor = existing.bookAuthor,
                        writingStyle = existing.writingStyle,
                        grammar = existing.grammar,
                        plotSummary = existing.plotSummary,
                        representativeDialogues = existing.representativeDialogues,
                        representativeProse = existing.representativeProse,
                        sourceChapterIndices = existing.sourceChapterIndices,
                    )
                )
            }
        } else {
            observeEntries(null)
            _uiState.update {
                it.copy(
                    isEditing = true,
                    editingExistingId = null,
                    editFields = WorldBookEditFields(),
                    pendingEntryDrafts = persistentListOf(),
                )
            }
        }
    }

    private fun updateField(field: String, value: String) {
        _uiState.update { current ->
            val fields = current.editFields
            current.copy(editFields = when (field) {
                "name" -> fields.copy(name = value)
                "bookUrl" -> fields.copy(bookUrl = value)
                "bookName" -> fields.copy(bookName = value)
                "bookAuthor" -> fields.copy(bookAuthor = value)
                "writingStyle" -> fields.copy(writingStyle = value)
                "grammar" -> fields.copy(grammar = value)
                "plotSummary" -> fields.copy(plotSummary = value)
                "representativeDialogues" -> fields.copy(representativeDialogues = value)
                "representativeProse" -> fields.copy(representativeProse = value)
                "sourceChapterIndices" -> fields.copy(sourceChapterIndices = value)
                else -> fields
            })
        }
    }

    private fun save() {
        val state = _uiState.value
        val fields = state.editFields
        if (fields.name.isBlank()) {
            _effects.tryEmit(AiWorldBookEffect.ShowMessage("Name is required"))
            return
        }
        viewModelScope.launch {
            runCatching {
                val existingId = state.editingExistingId
                val worldBookId = if (existingId != null) {
                    val result = worldBookMutator.patchFields(
                        WorldBookPatch(
                            worldBookId = existingId,
                            name = fields.name,
                            writingStyle = fields.writingStyle,
                            grammar = fields.grammar,
                            plotSummary = fields.plotSummary,
                            representativeDialogues = fields.representativeDialogues,
                            representativeProse = fields.representativeProse,
                        )
                    )
                    if (!result.success) error(result.message)
                    existingId
                } else {
                    val result = worldBookMutator.applyPatch(
                        WorldBookPatch(
                            name = fields.name,
                            bookUrl = fields.bookUrl,
                            bookName = fields.bookName,
                            bookAuthor = fields.bookAuthor,
                            writingStyle = fields.writingStyle,
                            grammar = fields.grammar,
                            plotSummary = fields.plotSummary,
                            representativeDialogues = fields.representativeDialogues,
                            representativeProse = fields.representativeProse,
                            sourceChapterIndices = fields.sourceChapterIndices,
                        )
                    )
                    if (!result.success) error(result.message)
                    result.worldBookId
                }
                val drafts = state.pendingEntryDrafts
                if (drafts.isNotEmpty() && worldBookId.isNotBlank()) {
                    importWorldInfoUseCase.upsertEntryDrafts(worldBookId, drafts)
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isEditing = false,
                        editingExistingId = null,
                        editFields = WorldBookEditFields(),
                        pendingEntryDrafts = persistentListOf(),
                    )
                }
            }.onFailure {
                _effects.tryEmit(AiWorldBookEffect.ShowMessage(it.message ?: "Save failed"))
            }
        }
    }

    private fun delete(id: String) {
        viewModelScope.launch {
            worldBookGateway.delete(id)
            if (_uiState.value.editingExistingId == id) {
                _uiState.update {
                    it.copy(
                        isEditing = false,
                        editingExistingId = null,
                        editFields = WorldBookEditFields(),
                        pendingEntryDrafts = persistentListOf(),
                    )
                }
            }
        }
    }

    private fun cancelEdit() {
        _uiState.update {
            it.copy(
                isEditing = false,
                editingExistingId = null,
                editFields = WorldBookEditFields(),
                pendingEntryDrafts = persistentListOf(),
            )
        }
    }

    private fun toggleEnabled(id: String) {
        viewModelScope.launch {
            worldBookGateway.toggleEnabled(id)
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

    private fun selectBook(book: Book) {
        _uiState.update { current ->
            val maxChapter = book.totalChapterNum.coerceAtLeast(1)
            val endIdx = (book.durChapterIndex + 4).coerceIn(0, maxChapter - 1)
            val startIdx = book.durChapterIndex.coerceIn(0, endIdx)
            current.copy(
                showBookPicker = false,
                selectedBook = book,
                chapterStartIndex = startIdx,
                chapterEndIndex = endIdx,
                maxChapterIndex = maxChapter - 1,
                editFields = current.editFields.copy(
                    name = current.editFields.name.ifBlank { "${book.name} 文风模板" },
                    bookUrl = book.bookUrl,
                    bookName = book.name,
                    bookAuthor = book.author
                )
            )
        }
    }

    private fun setChapterRange(start: Int, end: Int) {
        _uiState.update { it.copy(chapterStartIndex = start, chapterEndIndex = end) }
    }

    private fun startExtraction() {
        val state = _uiState.value
        val book = state.selectedBook ?: run {
            _effects.tryEmit(AiWorldBookEffect.ShowMessage("Please select a book first"))
            return
        }
        val start = state.chapterStartIndex
        val end = state.chapterEndIndex
        if (start > end) {
            _effects.tryEmit(AiWorldBookEffect.ShowMessage("Invalid chapter range"))
            return
        }

        _uiState.update {
            it.copy(
                extractionInProgress = true,
                extractionPreview = "",
                extractionReasoning = "",
            )
        }
        viewModelScope.launch {
            runCatching {
                val indices = (start..end).toList()
                extractUseCase.extract(
                    book = book,
                    chapterIndices = indices,
                    onPartial = { content, reasoning ->
                        _uiState.update {
                            it.copy(
                                extractionPreview = content,
                                extractionReasoning = reasoning.orEmpty(),
                            )
                        }
                    },
                    allowNetworkFetch = true,
                )
            }.onSuccess { fields ->
                _uiState.update { current ->
                    current.copy(
                        extractionInProgress = false,
                        extractionPreview = "",
                        extractionReasoning = "",
                        pendingEntryDrafts = fields.entries.toImmutableList(),
                        editFields = current.editFields.copy(
                            writingStyle = fields.writingStyle,
                            grammar = fields.grammar,
                            plotSummary = fields.plotSummary,
                            representativeDialogues = fields.representativeDialogues,
                            representativeProse = fields.representativeProse,
                            sourceChapterIndices = GSON.toJson((start..end).toList())
                        )
                    )
                }
                val entryHint = if (fields.entries.isNotEmpty()) {
                    " · ${appCtx.getString(R.string.ai_world_book_import_entries, fields.entries.size)}"
                } else ""
                _effects.tryEmit(AiWorldBookEffect.ShowMessage("Extraction complete$entryHint"))
            }.onFailure {
                _uiState.update {
                    it.copy(
                        extractionInProgress = false,
                        extractionPreview = "",
                        extractionReasoning = "",
                    )
                }
                _effects.tryEmit(AiWorldBookEffect.ShowMessage(it.message ?: "Extraction failed"))
            }
        }
    }

    private fun exportToPrompts(worldBookId: String, field: String) {
        viewModelScope.launch {
            val wb = worldBookGateway.getById(worldBookId) ?: run {
                _effects.tryEmit(AiWorldBookEffect.ShowMessage("World book not found"))
                return@launch
            }
            val (label, content, category) = when (field) {
                "writingStyle" -> Triple("文风", wb.writingStyle, "style")
                "grammar" -> Triple("语法", wb.grammar, "approach")
                else -> return@launch
            }
            if (content.isBlank()) {
                _effects.tryEmit(AiWorldBookEffect.ShowMessage("$label is empty"))
                return@launch
            }
            runCatching {
                writingPromptGateway.save(
                    name = "${wb.name} $label [来源: ${wb.bookName}]",
                    content = content,
                    category = category
                )
            }.onSuccess {
                _effects.tryEmit(AiWorldBookEffect.ShowMessage("$label exported to prompts"))
            }.onFailure {
                _effects.tryEmit(AiWorldBookEffect.ShowMessage(it.message ?: "Export failed"))
            }
        }
    }
}
