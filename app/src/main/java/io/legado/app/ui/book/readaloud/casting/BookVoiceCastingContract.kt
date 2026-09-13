package io.legado.app.ui.book.readaloud.casting

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class BookVoiceCastingUiState(
    val bookUrl: String,
    val isLoading: Boolean = true,
    val isSyncingVoices: Boolean = false,
    val items: ImmutableList<VoiceCastingItemUi> = persistentListOf(),
    val voices: ImmutableList<VoiceOptionUi> = persistentListOf(),
    val picker: VoicePickerUi? = null,
    val identifySheet: CharacterIdentifySheetUi? = null,
)

@Stable
data class VoiceCastingItemUi(
    val subjectType: String,
    val subjectId: String,
    val kind: CastingSubjectKind,
    val name: String,
    val description: String = "",
    val avatarUri: String? = null,
    val hasBinding: Boolean = false,
    val voiceName: String = "",
    val voiceAvailable: Boolean = false,
)

@Stable
data class VoiceOptionUi(
    val id: String,
    val name: String,
    val engineType: String,
    val engineName: String,
    val selectable: Boolean,
)

@Stable
data class VoicePickerUi(
    val subjectType: String,
    val subjectId: String,
    val kind: CastingSubjectKind,
    val name: String,
    val selectedVoiceId: String?,
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

enum class CastingSubjectKind {
    Narrator,
    UnknownMale,
    UnknownFemale,
    Unknown,
    Character,
}

sealed interface BookVoiceCastingIntent {
    data object Refresh : BookVoiceCastingIntent
    data object SyncVoices : BookVoiceCastingIntent
    data class OpenVoicePicker(val subjectType: String, val subjectId: String) :
        BookVoiceCastingIntent
    data object DismissVoicePicker : BookVoiceCastingIntent
    data class AssignVoice(val voiceId: String) : BookVoiceCastingIntent
    data object ClearBinding : BookVoiceCastingIntent
    data object OpenAiIdentify : BookVoiceCastingIntent
    data object DismissAiIdentify : BookVoiceCastingIntent
    data object RunAiIdentify : BookVoiceCastingIntent
    data class ToggleAiCandidate(val id: String) : BookVoiceCastingIntent
    data object SaveAiCandidates : BookVoiceCastingIntent
}

sealed interface BookVoiceCastingEffect {
    data class ShowToast(val message: String) : BookVoiceCastingEffect
}
