package io.legado.app.ui.book.info.characters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.domain.gateway.AiCharacterCardGateway
import io.legado.app.domain.gateway.ReadAloudCharacterGateway
import io.legado.app.domain.usecase.IdentifyBookCharactersUseCase
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

class BookCharacterListViewModel(
    private val bookUrl: String,
    private val readAloudCharacterGateway: ReadAloudCharacterGateway,
    private val identifyBookCharacters: IdentifyBookCharactersUseCase,
    private val characterCardGateway: AiCharacterCardGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BookCharacterListUiState(bookUrl = bookUrl, isLoading = true),
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<BookCharacterListEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    private var identifiedCandidates: List<IdentifyBookCharactersUseCase.Candidate> = emptyList()
    private var loadJob: Job? = null
    private var identifyJob: Job? = null

    init {
        loadBookMeta()
        load()
    }

    fun onIntent(intent: BookCharacterListIntent) {
        when (intent) {
            BookCharacterListIntent.Refresh -> load()
            is BookCharacterListIntent.OpenCharacter -> {
                _effects.tryEmit(BookCharacterListEffect.OpenCharacterEdit(intent.characterId))
            }
            BookCharacterListIntent.AddCharacter -> {
                _effects.tryEmit(BookCharacterListEffect.OpenCharacterEdit(null))
            }
            BookCharacterListIntent.OpenAiIdentify -> openAiIdentify()
            BookCharacterListIntent.DismissAiIdentify -> dismissAiIdentify()
            BookCharacterListIntent.RunAiIdentify -> runAiIdentify()
            is BookCharacterListIntent.ToggleAiCandidate -> toggleAiCandidate(intent.id)
            BookCharacterListIntent.SaveAiCandidates -> saveAiCandidates()
            is BookCharacterListIntent.RequestDeleteCharacter -> requestDeleteCharacter(intent.characterId)
            BookCharacterListIntent.DismissDeleteConfirm -> dismissDeleteConfirm()
            BookCharacterListIntent.ConfirmDeleteCharacter -> confirmDeleteCharacter()
        }
    }

    private fun requestDeleteCharacter(characterId: String) {
        val character = _uiState.value.characters.firstOrNull { it.id == characterId } ?: return
        _uiState.update { it.copy(deleteConfirm = character) }
    }

    private fun dismissDeleteConfirm() {
        _uiState.update { it.copy(deleteConfirm = null) }
    }

    private fun confirmDeleteCharacter() {
        val target = _uiState.value.deleteConfirm ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(deleteConfirm = null) }
            try {
                withContext(Dispatchers.IO) {
                    readAloudCharacterGateway.removeFromCast(bookUrl, target.id)
                    characterCardGateway.delete(target.id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _effects.tryEmit(
                    BookCharacterListEffect.ShowToast(
                        e.localizedMessage ?: appCtx.getString(R.string.load_failed),
                    ),
                )
            }
        }
    }

    private fun loadBookMeta() {
        viewModelScope.launch {
            val book = withContext(Dispatchers.IO) { appDb.bookDao.getBook(bookUrl) }
            if (book != null) {
                _uiState.update {
                    it.copy(bookName = book.name, bookAuthor = book.author)
                }
            }
        }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                combine(
                    readAloudCharacterGateway.observeCharacterCards(bookUrl),
                    readAloudCharacterGateway.observeSpeakerCharacters(bookUrl),
                ) { cards, speakers ->
                    val roles = speakers.associate { it.id to it.role }
                    cards to roles
                }.collect { (cards, roles) ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            characters = cards.map { card ->
                                BookCharacterListItemUi(
                                    id = card.id,
                                    name = card.name,
                                    summary = card.personality.ifBlank { card.description },
                                    voiceGender = card.voiceGender,
                                    dramaticRole = roles[card.id].orEmpty(),
                                    avatarPath = card.avatarPath,
                                )
                            }.toImmutableList(),
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoading = false) }
                _effects.tryEmit(
                    BookCharacterListEffect.ShowToast(
                        e.localizedMessage ?: appCtx.getString(R.string.load_failed),
                    ),
                )
            }
        }
    }

    private fun openAiIdentify() {
        viewModelScope.launch {
            val cached = runCatching { identifyBookCharacters.loadLatest(bookUrl) }
                .getOrDefault(emptyList())
            identifiedCandidates = cached
            _uiState.update {
                it.copy(
                    identifySheet = CharacterIdentifySheetUi(
                        candidates = cached.toCandidateUi(),
                    ),
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
                it.copy(identifySheet = CharacterIdentifySheetUi(loading = true))
            }
            try {
                identifyBookCharacters.identifyStream(bookUrl).collect { progress ->
                    when (progress) {
                        is IdentifyBookCharactersUseCase.Progress.Reasoning -> {
                            _uiState.update { state ->
                                val sheet = state.identifySheet
                                    ?: CharacterIdentifySheetUi(loading = true)
                                state.copy(
                                    identifySheet = sheet.copy(
                                        loading = true,
                                        reasoning = (sheet.reasoning + progress.text).takeLast(2000),
                                        error = null,
                                    ),
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
                                    ),
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
                            error = e.localizedMessage ?: appCtx.getString(R.string.load_failed),
                        ),
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
                    }.toImmutableList(),
                ),
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
                BookCharacterListEffect.ShowToast(
                    appCtx.getString(R.string.ai_identify_characters_none_selected),
                ),
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
                    BookCharacterListEffect.ShowToast(
                        appCtx.getString(R.string.ai_identify_characters_saved, selected.size),
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        identifySheet = sheet.copy(
                            loading = false,
                            error = e.localizedMessage ?: appCtx.getString(R.string.load_failed),
                        ),
                    )
                }
            }
        }
    }
}

private fun List<IdentifyBookCharactersUseCase.Candidate>.toCandidateUi() =
    mapIndexed { index, candidate ->
        CharacterIdentifyCandidateUi(
            id = index.toString(),
            name = candidate.name,
            summary = candidate.summary.ifBlank { candidate.personality },
            selected = true,
        )
    }.toImmutableList()
