package io.legado.app.ui.book.readaloud.casting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.data.entities.AiCharacterCard
import io.legado.app.domain.gateway.ReadAloudCharacterGateway
import io.legado.app.domain.gateway.ReadAloudVoiceGateway
import io.legado.app.domain.model.readaloud.BookVoiceBinding
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.domain.usecase.IdentifyBookCharactersUseCase
import io.legado.app.domain.usecase.SyncVoicesFromAllEnginesUseCase
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import splitties.init.appCtx

class BookVoiceCastingViewModel(
    private val bookUrl: String,
    private val readAloudCharacterGateway: ReadAloudCharacterGateway,
    private val voiceGateway: ReadAloudVoiceGateway,
    private val identifyBookCharacters: IdentifyBookCharactersUseCase,
    private val syncVoicesFromAllEngines: SyncVoicesFromAllEnginesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookVoiceCastingUiState(bookUrl = bookUrl))
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<BookVoiceCastingEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    private var characterCards: List<AiCharacterCard> = emptyList()
    private var voices: List<ReadAloudVoice> = emptyList()
    private var bindings: List<BookVoiceBinding> = emptyList()
    private var identifiedCandidates: List<IdentifyBookCharactersUseCase.Candidate> = emptyList()
    private var loadJob: Job? = null
    private var identifyJob: Job? = null

    init {
        load()
    }

    fun onIntent(intent: BookVoiceCastingIntent) {
        when (intent) {
            BookVoiceCastingIntent.Refresh -> load()
            BookVoiceCastingIntent.SyncVoices -> syncVoices()
            is BookVoiceCastingIntent.OpenVoicePicker -> openVoicePicker(intent)
            BookVoiceCastingIntent.DismissVoicePicker -> {
                _uiState.update { it.copy(picker = null) }
            }
            is BookVoiceCastingIntent.AssignVoice -> assignVoice(intent.voiceId)
            BookVoiceCastingIntent.ClearBinding -> clearBinding()
            BookVoiceCastingIntent.OpenAiIdentify -> openAiIdentify()
            BookVoiceCastingIntent.DismissAiIdentify -> dismissAiIdentify()
            BookVoiceCastingIntent.RunAiIdentify -> runAiIdentify()
            is BookVoiceCastingIntent.ToggleAiCandidate -> toggleAiCandidate(intent.id)
            BookVoiceCastingIntent.SaveAiCandidates -> saveAiCandidates()
        }
    }

    private fun syncVoices() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingVoices = true) }
            runCatching { syncVoicesFromAllEngines() }
                .onSuccess { result ->
                    _effects.tryEmit(
                        BookVoiceCastingEffect.ShowToast(
                            appCtx.getString(
                                R.string.cloud_tts_voices_synced,
                                result.inserted + result.updated,
                            )
                        )
                    )
                }
                .onFailure { e ->
                    _effects.tryEmit(
                        BookVoiceCastingEffect.ShowToast(
                            e.localizedMessage ?: appCtx.getString(R.string.error)
                        )
                    )
                }
            _uiState.update { it.copy(isSyncingVoices = false) }
        }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                combine(
                    readAloudCharacterGateway.observeCharacterCards(bookUrl),
                    voiceGateway.observeVoices(),
                    voiceGateway.observeBindings(bookUrl),
                ) { cards, latestVoices, latestBindings ->
                    Triple(cards, latestVoices, latestBindings)
                }.collect { (cards, latestVoices, latestBindings) ->
                    characterCards = cards
                    voices = latestVoices
                    bindings = latestBindings
                    publishState()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoading = false) }
                _effects.tryEmit(
                    BookVoiceCastingEffect.ShowToast(
                        e.localizedMessage ?: appCtx.getString(R.string.load_failed)
                    )
                )
            }
        }
    }

    private fun publishState() {
        val voicesById = voices.associateBy(ReadAloudVoice::id)
        val bindingsBySubject = bindings.associateBy { it.subjectType to it.subjectId }
        val specialItems = listOf(
            specialItem(BookVoiceBinding.SUBJECT_NARRATOR, CastingSubjectKind.Narrator),
            specialItem(BookVoiceBinding.SUBJECT_UNKNOWN_MALE, CastingSubjectKind.UnknownMale),
            specialItem(BookVoiceBinding.SUBJECT_UNKNOWN_FEMALE, CastingSubjectKind.UnknownFemale),
            specialItem(BookVoiceBinding.SUBJECT_UNKNOWN, CastingSubjectKind.Unknown),
        )
        val characterItems = characterCards.map { card ->
            VoiceCastingItemUi(
                subjectType = BookVoiceBinding.SUBJECT_CHARACTER,
                subjectId = card.id,
                kind = CastingSubjectKind.Character,
                name = card.name,
                description = card.personality.ifBlank { card.description },
                avatarUri = card.avatarPath.takeIf { it.isNotBlank() },
            )
        }
        val items = (specialItems + characterItems).map { item ->
            val binding = bindingsBySubject[item.subjectType to item.subjectId]
            val voice = binding?.voiceId?.let(voicesById::get)
            item.copy(
                hasBinding = binding != null,
                voiceName = voice?.displayName.orEmpty(),
                voiceAvailable = voice?.let { it.available && it.enabled } == true,
            )
        }
        val voiceOptions = voices.map { voice ->
            VoiceOptionUi(
                id = voice.id,
                name = voice.displayName,
                engineType = voice.engineType,
                engineName = voice.engineId,
                selectable = voice.available && voice.enabled,
            )
        }.sortedWith(
            compareByDescending<VoiceOptionUi> { it.selectable }
                .thenBy { it.engineType }
                .thenBy { it.name.lowercase() }
        )
        val currentPicker = _uiState.value.picker?.let { picker ->
            val item = items.firstOrNull {
                it.subjectType == picker.subjectType && it.subjectId == picker.subjectId
            } ?: return@let null
            val selectedVoiceId = bindingsBySubject[item.subjectType to item.subjectId]?.voiceId
            picker.copy(selectedVoiceId = selectedVoiceId)
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                items = items.toImmutableList(),
                voices = voiceOptions.toImmutableList(),
                picker = currentPicker,
            )
        }
    }

    private fun specialItem(subject: String, kind: CastingSubjectKind) = VoiceCastingItemUi(
        subjectType = subject,
        subjectId = subject,
        kind = kind,
        name = "",
    )

    private fun openVoicePicker(intent: BookVoiceCastingIntent.OpenVoicePicker) {
        val item = _uiState.value.items.firstOrNull {
            it.subjectType == intent.subjectType && it.subjectId == intent.subjectId
        } ?: return
        val selectedVoiceId = bindings.firstOrNull {
            it.subjectType == item.subjectType && it.subjectId == item.subjectId
        }?.voiceId
        _uiState.update {
            it.copy(
                picker = VoicePickerUi(
                    subjectType = item.subjectType,
                    subjectId = item.subjectId,
                    kind = item.kind,
                    name = item.name,
                    selectedVoiceId = selectedVoiceId,
                )
            )
        }
    }

    private fun assignVoice(voiceId: String) {
        val picker = _uiState.value.picker ?: return
        val voice = voices.firstOrNull { it.id == voiceId && it.available && it.enabled } ?: return
        val old = bindings.firstOrNull {
            it.subjectType == picker.subjectType && it.subjectId == picker.subjectId
        }
        viewModelScope.launch {
            try {
                voiceGateway.upsertBinding(
                    BookVoiceBinding(
                        bookUrl = bookUrl,
                        subjectType = picker.subjectType,
                        subjectId = picker.subjectId,
                        voiceId = voice.id,
                        locked = true,
                        source = BookVoiceBinding.SOURCE_USER,
                        confidence = 1f,
                        createdAt = old?.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                _uiState.update { it.copy(picker = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                showSaveError(e)
            }
        }
    }

    private fun clearBinding() {
        val picker = _uiState.value.picker ?: return
        val binding = bindings.firstOrNull {
            it.subjectType == picker.subjectType && it.subjectId == picker.subjectId
        } ?: run {
            _uiState.update { it.copy(picker = null) }
            return
        }
        viewModelScope.launch {
            try {
                voiceGateway.deleteBinding(binding)
                _uiState.update { it.copy(picker = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                showSaveError(e)
            }
        }
    }

    private fun openAiIdentify() {
        viewModelScope.launch {
            val cached = runCatching { identifyBookCharacters.loadLatest(bookUrl) }.getOrDefault(emptyList())
            identifiedCandidates = cached
            _uiState.update {
                it.copy(
                    identifySheet = CharacterIdentifySheetUi(
                        candidates = cached.toCandidateUi(),
                    )
                )
            }
        }
    }

    private fun dismissAiIdentify() {
        identifyJob?.cancel()
        identifyJob = null
        identifiedCandidates = emptyList()
        _uiState.update { it.copy(identifySheet = null) }
    }

    private fun runAiIdentify() {
        identifyJob?.cancel()
        identifyJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    identifySheet = CharacterIdentifySheetUi(loading = true)
                )
            }
            try {
                identifyBookCharacters.identifyStream(bookUrl).collect { progress ->
                    when (progress) {
                        is IdentifyBookCharactersUseCase.Progress.Reasoning -> {
                            _uiState.update { state ->
                                val sheet = state.identifySheet ?: CharacterIdentifySheetUi(loading = true)
                                state.copy(
                                    identifySheet = sheet.copy(
                                        loading = true,
                                        reasoning = (sheet.reasoning + progress.text).takeLast(2000),
                                        error = null,
                                    )
                                )
                            }
                        }
                        is IdentifyBookCharactersUseCase.Progress.ToolCall -> Unit
                        is IdentifyBookCharactersUseCase.Progress.Done -> {
                            identifiedCandidates = progress.candidates
                            _uiState.update {
                                it.copy(
                                    identifySheet = CharacterIdentifySheetUi(
                                        candidates = progress.candidates.toCandidateUi(),
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        identifySheet = CharacterIdentifySheetUi(
                            error = e.localizedMessage ?: appCtx.getString(R.string.load_failed)
                        )
                    )
                }
            }
        }
    }

    private fun toggleAiCandidate(id: String) {
        _uiState.update { state ->
            val sheet = state.identifySheet ?: return@update state
            state.copy(
                identifySheet = sheet.copy(
                    candidates = sheet.candidates.map { candidate ->
                        if (candidate.id == id) candidate.copy(selected = !candidate.selected)
                        else candidate
                    }.toImmutableList()
                )
            )
        }
    }

    private fun saveAiCandidates() {
        val sheet = _uiState.value.identifySheet ?: return
        val selected = sheet.candidates.filter { it.selected }.mapNotNull { item ->
            identifiedCandidates.getOrNull(item.id.toIntOrNull() ?: -1)
        }
        if (selected.isEmpty()) {
            _effects.tryEmit(
                BookVoiceCastingEffect.ShowToast(
                    appCtx.getString(R.string.ai_identify_characters_none_selected)
                )
            )
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(identifySheet = sheet.copy(loading = true, error = null))
            }
            try {
                identifyBookCharacters.save(bookUrl, selected)
                identifiedCandidates = emptyList()
                _uiState.update { it.copy(identifySheet = null) }
                _effects.tryEmit(
                    BookVoiceCastingEffect.ShowToast(
                        appCtx.getString(R.string.ai_identify_characters_saved, selected.size)
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        identifySheet = sheet.copy(
                            loading = false,
                            error = e.localizedMessage ?: appCtx.getString(R.string.save_error)
                        )
                    )
                }
            }
        }
    }

    private fun showSaveError(error: Throwable) {
        _effects.tryEmit(
            BookVoiceCastingEffect.ShowToast(
                error.localizedMessage ?: appCtx.getString(R.string.save_error)
            )
        )
    }
}

private fun List<IdentifyBookCharactersUseCase.Candidate>.toCandidateUi() =
    mapIndexed { index, candidate ->
        CharacterIdentifyCandidateUi(
            id = index.toString(),
            name = candidate.name,
            summary = listOf(candidate.summary, candidate.evidence)
                .filter(String::isNotBlank)
                .joinToString("\n"),
        )
    }.toImmutableList()
