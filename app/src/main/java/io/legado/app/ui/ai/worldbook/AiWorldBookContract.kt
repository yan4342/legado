package io.legado.app.ui.ai.worldbook

import androidx.compose.runtime.Stable
import io.legado.app.data.entities.AiWorldBook
import io.legado.app.data.entities.AiWorldBookEntry
import io.legado.app.data.entities.Book
import io.legado.app.domain.model.SillyTavernWorldInfoImporter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class AiWorldBookUiState(
    val worldBooks: ImmutableList<AiWorldBook> = persistentListOf(),
    val isEditing: Boolean = false,
    val editingExistingId: String? = null,
    val editFields: WorldBookEditFields = WorldBookEditFields(),
    val extractionInProgress: Boolean = false,
    val extractionPreview: String = "",
    val extractionReasoning: String = "",
    val showBookPicker: Boolean = false,
    val bookshelfBooks: ImmutableList<Book> = persistentListOf(),
    val selectedBook: Book? = null,
    val chapterStartIndex: Int = 0,
    val chapterEndIndex: Int = 0,
    val maxChapterIndex: Int = 0,
    val entries: ImmutableList<AiWorldBookEntry> = persistentListOf(),
    val editingEntry: AiWorldBookEntry? = null,
    val showEntryEditor: Boolean = false,
    /** Lore drafts from AI extraction; written on Save. */
    val pendingEntryDrafts: ImmutableList<SillyTavernWorldInfoImporter.EntryDraft> = persistentListOf(),
)

@Stable
data class WorldBookEditFields(
    val name: String = "",
    val bookUrl: String = "",
    val bookName: String = "",
    val bookAuthor: String = "",
    val writingStyle: String = "",
    val grammar: String = "",
    val plotSummary: String = "",
    val representativeDialogues: String = "",
    val representativeProse: String = "",
    val sourceChapterIndices: String = "",
)

sealed interface AiWorldBookIntent {
    data object LoadList : AiWorldBookIntent
    data class StartEdit(val existing: AiWorldBook?) : AiWorldBookIntent
    data class UpdateField(val field: String, val value: String) : AiWorldBookIntent
    data object Save : AiWorldBookIntent
    data class Delete(val id: String) : AiWorldBookIntent
    data object CancelEdit : AiWorldBookIntent
    data class ToggleEnabled(val id: String) : AiWorldBookIntent
    // Extraction
    data object ShowBookPicker : AiWorldBookIntent
    data object DismissBookPicker : AiWorldBookIntent
    data class SelectBook(val book: Book) : AiWorldBookIntent
    data class SetChapterRange(val start: Int, val end: Int) : AiWorldBookIntent
    data object StartExtraction : AiWorldBookIntent
    // Export
    data class ExportStyleToPrompts(val worldBookId: String) : AiWorldBookIntent
    data class ExportGrammarToPrompts(val worldBookId: String) : AiWorldBookIntent
    data class StartEntryEdit(val entry: AiWorldBookEntry?) : AiWorldBookIntent
    data class UpdateEntryField(val field: String, val value: String) : AiWorldBookIntent
    data object SaveEntry : AiWorldBookIntent
    data object CancelEntryEdit : AiWorldBookIntent
    data class DeleteEntry(val id: String) : AiWorldBookIntent
    data class ToggleEntryEnabled(val id: String) : AiWorldBookIntent
    data class ImportWorldInfoJson(val json: String, val fallbackName: String = "Imported Lorebook") : AiWorldBookIntent
    data class ImportWorldInfoBytes(val bytes: ByteArray, val fallbackName: String = "Imported Lorebook") : AiWorldBookIntent
    /** Append lore entries from ST/AI JSON into the world book currently being edited. */
    data class ImportEntriesIntoCurrent(val json: String) : AiWorldBookIntent
    data class ImportEntriesBytesIntoCurrent(val bytes: ByteArray) : AiWorldBookIntent
}

sealed interface AiWorldBookEffect {
    data class ShowMessage(val message: String) : AiWorldBookEffect
}
