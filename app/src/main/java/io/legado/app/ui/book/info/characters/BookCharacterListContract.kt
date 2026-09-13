package io.legado.app.ui.book.info.characters

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class BookCharacterListUiState(
    val bookUrl: String = "",
    val bookName: String = "",
    val bookAuthor: String = "",
    val isLoading: Boolean = false,
    val characters: ImmutableList<BookCharacterListItemUi> = persistentListOf(),
    val identifySheet: CharacterIdentifySheetUi? = null,
    val deleteConfirm: BookCharacterListItemUi? = null,
)

@Stable
data class BookCharacterListItemUi(
    val id: String,
    val name: String,
    val summary: String,
    val voiceGender: String = "",
    val dramaticRole: String = "",
    val avatarPath: String = "",
)

@Stable
data class CharacterIdentifySheetUi(
    val loading: Boolean = false,
    val reasoning: String = "",
    val error: String? = null,
    val candidates: ImmutableList<CharacterIdentifyCandidateUi> = persistentListOf(),
)

@Stable
data class CharacterIdentifyCandidateUi(
    val id: String,
    val name: String,
    val summary: String,
    val selected: Boolean = true,
)

sealed interface BookCharacterListIntent {
    data object Refresh : BookCharacterListIntent
    data class OpenCharacter(val characterId: String) : BookCharacterListIntent
    data object AddCharacter : BookCharacterListIntent
    data object OpenAiIdentify : BookCharacterListIntent
    data object DismissAiIdentify : BookCharacterListIntent
    data object RunAiIdentify : BookCharacterListIntent
    data class ToggleAiCandidate(val id: String) : BookCharacterListIntent
    data object SaveAiCandidates : BookCharacterListIntent
    data class RequestDeleteCharacter(val characterId: String) : BookCharacterListIntent
    data object DismissDeleteConfirm : BookCharacterListIntent
    data object ConfirmDeleteCharacter : BookCharacterListIntent
}

sealed interface BookCharacterListEffect {
    data class OpenCharacterEdit(val characterId: String?) : BookCharacterListEffect
    data class ShowToast(val message: String) : BookCharacterListEffect
}
