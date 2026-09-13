package io.legado.app.ui.ai.chat

/**
 * Shared sheet/dialog host for Compose and HTML AI-chat tracks.
 * HTML track opens the same sheets via [AiChatDialogState] flags.
 */
@androidx.compose.runtime.Composable
fun AiChatSheetHost(
    ds: AiChatDialogState,
    viewModel: AiChatViewModel,
    state: AiChatUiState,
    onConversationDeleted: (() -> Unit)? = null,
    onSaveOutlineExportFile: () -> Unit = {},
    onOpenOutlineImportFile: () -> Unit = {},
) {
    AiChatDialogs(
        ds = ds,
        viewModel = viewModel,
        state = state,
        onConversationDeleted = onConversationDeleted,
        onSaveOutlineExportFile = onSaveOutlineExportFile,
        onOpenOutlineImportFile = onOpenOutlineImportFile,
    )
}
