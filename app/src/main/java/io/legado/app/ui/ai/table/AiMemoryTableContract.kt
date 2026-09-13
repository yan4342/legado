package io.legado.app.ui.ai.table

import androidx.compose.runtime.Stable
import io.legado.app.data.entities.AiChatConversation
import io.legado.app.data.entities.AiMemoryTable
import io.legado.app.data.entities.AiMemoryTableRow
import io.legado.app.ui.ai.chat.FieldChangeUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class AiMemoryTableUiState(
    val tables: ImmutableList<AiMemoryTable> = persistentListOf(),
    val allEnabled: Boolean = true,
    val conversationId: String = "",
    val selectedTableId: String? = null,
    val selectedTableRows: ImmutableList<AiMemoryTableRow> = persistentListOf(),
    val isEditing: Boolean = false,
    val editingTableId: String? = null,
    val editingTableName: String = "",
    val editingTableColumns: String = "",
    val editingBookUrl: String = "",
    val editingBookName: String = "",
    val editingBookAuthor: String = "",
    val showBookPicker: Boolean = false,
    val bookshelfBooks: kotlinx.collections.immutable.ImmutableList<io.legado.app.data.entities.Book> = persistentListOf(),
    val editingRowId: String? = null,
    val editingRowData: String = "",
    val showRowEditDialog: Boolean = false,
    val isRegenerating: Boolean = false,
    // AI generation
    val isGenerating: Boolean = false,
    val generationPreview: String = "",
    val generationReasoning: String = "",
    // JSON export/import
    val showExportDialog: Boolean = false,
    val exportJson: String = "",
    val showImportDialog: Boolean = false,
    val importJson: String = "",
    // Transfer between conversations
    val showTransferMenu: Boolean = false,
    val showConversationPicker: Boolean = false,
    val conversationPickerMode: String = "",  // "import" | "export"
    val availableConversations: ImmutableList<AiChatConversation> = persistentListOf(),
    val highlightRowId: String? = null,
    val pendingUndo: PendingMemoryUndoUi? = null,
)

@Stable
data class PendingMemoryUndoUi(
    val snapshotId: String,
    val summary: String,
    val changes: ImmutableList<FieldChangeUi> = persistentListOf(),
)

sealed interface AiMemoryTableIntent {
    data class SetConversationId(val conversationId: String) : AiMemoryTableIntent
    data object RefreshList : AiMemoryTableIntent
    data class SelectTable(val tableId: String) : AiMemoryTableIntent
    data object BackToList : AiMemoryTableIntent
    data class StartEditTable(val table: AiMemoryTable?) : AiMemoryTableIntent
    data class UpdateEditField(val field: String, val value: String) : AiMemoryTableIntent
    data object SaveTable : AiMemoryTableIntent
    data object CancelEdit : AiMemoryTableIntent
    data object ShowBookPicker : AiMemoryTableIntent
    data object DismissBookPicker : AiMemoryTableIntent
    data class SelectBook(val book: io.legado.app.data.entities.Book) : AiMemoryTableIntent
    data object ClearBookSource : AiMemoryTableIntent
    data class DeleteTable(val tableId: String) : AiMemoryTableIntent
    data object ToggleAllEnabled : AiMemoryTableIntent
    data class RegenerateTable(val tableId: String) : AiMemoryTableIntent
    data object GenerateTable : AiMemoryTableIntent
    // Row CRUD
    data class StartEditRow(val row: AiMemoryTableRow?) : AiMemoryTableIntent
    data class UpdateRowField(val value: String) : AiMemoryTableIntent
    data object SaveRow : AiMemoryTableIntent
    data object CancelEditRow : AiMemoryTableIntent
    data class DeleteRow(val rowId: String) : AiMemoryTableIntent
    data class PatchCell(val rowId: String, val column: String, val value: String) : AiMemoryTableIntent
    data class SetHighlightRow(val rowId: String?) : AiMemoryTableIntent
    data class ResolveHighlightMatch(val matchHint: String) : AiMemoryTableIntent
    data object RestoreLatestSnapshot : AiMemoryTableIntent
    data object ConfirmUndoSnapshot : AiMemoryTableIntent
    data object DismissUndoSnapshot : AiMemoryTableIntent
    // Import / Export
    data object ShowExportDialog : AiMemoryTableIntent
    data object DismissExportDialog : AiMemoryTableIntent
    data object CopyExportToClipboard : AiMemoryTableIntent
    data object ShareExport : AiMemoryTableIntent
    data object ShowImportDialog : AiMemoryTableIntent
    data object DismissImportDialog : AiMemoryTableIntent
    data class UpdateImportJson(val json: String) : AiMemoryTableIntent
    data object ConfirmImport : AiMemoryTableIntent
    // Transfer between conversations
    data object ShowTransferMenu : AiMemoryTableIntent
    data object DismissTransferMenu : AiMemoryTableIntent
    data object ShowImportFromConversation : AiMemoryTableIntent
    data object ShowExportToConversation : AiMemoryTableIntent
    data object DismissConversationPicker : AiMemoryTableIntent
    data class SelectConversationForTransfer(val conversationId: String) : AiMemoryTableIntent
    // Maintenance
    data object CleanupOrphans : AiMemoryTableIntent
}

sealed interface AiMemoryTableEffect {
    data class ShowMessage(val message: String) : AiMemoryTableEffect
    data class ShareText(val text: String, val title: String) : AiMemoryTableEffect
    data class CopyToClipboard(val text: String) : AiMemoryTableEffect
}
