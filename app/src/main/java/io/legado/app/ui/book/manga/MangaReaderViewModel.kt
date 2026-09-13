package io.legado.app.ui.book.manga

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.data.entities.BookProgress
import io.legado.app.data.repository.manga.MangaReaderActionRepository
import io.legado.app.domain.gateway.MangaReaderSessionFactory
import io.legado.app.domain.gateway.MangaSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.model.manga.MangaChapterState
import io.legado.app.domain.model.manga.MangaProgressState
import io.legado.app.domain.model.manga.MangaSessionCommand
import io.legado.app.domain.model.manga.MangaSessionEvent
import io.legado.app.domain.model.manga.MangaSessionState
import io.legado.app.domain.model.settings.MangaSettings
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.book.isLocal
import io.legado.app.ui.book.manga.config.MangaColorFilterConfig
import io.legado.app.ui.book.manga.config.MangaFooterConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import splitties.init.appCtx

class MangaReaderViewModel(
    private val mangaSettingsGateway: MangaSettingsGateway,
    private val otherSettingsGateway: OtherSettingsGateway,
    private val readSettingsGateway: ReadSettingsGateway,
    private val actionRepository: MangaReaderActionRepository,
    sessionFactory: MangaReaderSessionFactory,
) : ViewModel() {

    private val readerSession = sessionFactory.create()

    private var refreshContentJob: Job? = null
    private var latestMangaSettings = mangaSettingsGateway.currentSettings
    private val bookConfigWriteMutex = Mutex()
    private var pendingExplicitChapterIndex: Int? = null
    private var pagerScrollInProgress = false
    private var deferredReadySession: MangaSessionState? = null
    private var visibleItemRange: IntRange? = null
    private var launchRequest: MangaReaderIntent.Initialize? = null
    private var lastAutoCachedKey: Pair<String, Int>? = null

    private val _uiState = MutableStateFlow(MangaReaderUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<MangaReaderEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            readerSession.events.collect { event ->
                when (event) {
                    is MangaSessionEvent.ConfirmProgress -> _uiState.update {
                        it.copy(activeDialog = MangaReaderDialog.ConfirmProgress(event.progress.toBookProgress()))
                    }
                    is MangaSessionEvent.Message -> enqueueMessage(text = event.message)
                    is MangaSessionEvent.OpenPaymentUrl -> Unit // 付费章节未移植
                }
            }
        }
        viewModelScope.launch {
            readerSession.state.collect(::refreshContent)
        }
        viewModelScope.launch {
            mangaSettingsGateway.settings.collect { settings ->
                latestMangaSettings = settings
                executeSession(MangaSessionCommand.PrefetchCountChanged(settings.preDownloadNum))
                _uiState.update { state ->
                    state.copy(settings = readSettings(settings))
                }
                // 开关/章节数变化时立即尝试自动离线缓存（内部按章节去重）
                autoCacheFollowingChapters()
            }
        }
        viewModelScope.launch {
            otherSettingsGateway.settings.collect { settings ->
                _uiState.update { it.copy(confirmAddToShelf = settings.showAddToShelfAlert) }
            }
        }
    }

    fun onIntent(intent: MangaReaderIntent) {
        when (intent) {
            is MangaReaderIntent.Initialize -> initialize(intent)
            MangaReaderIntent.ResumeSession -> executeSession(MangaSessionCommand.Resume)
            MangaReaderIntent.PauseSession -> executeSession(MangaSessionCommand.Pause)
            MangaReaderIntent.NetworkAvailable -> executeSession(MangaSessionCommand.NetworkAvailable)
            MangaReaderIntent.ReloadContent -> executeSession(
                MangaSessionCommand.RetryChapter(readerSession.state.value.chapterIndex)
            )
            is MangaReaderIntent.ApplyReadingProgress -> {
                pendingExplicitChapterIndex = intent.progress.durChapterIndex
                executeSession(MangaSessionCommand.ApplyProgress(intent.progress.toMangaProgress()))
            }
            is MangaReaderIntent.OpenChapter -> openChapter(intent.chapterIndex, intent.pageIndex)
            is MangaReaderIntent.ChangeSourceBook -> launchAction {
                showLoading()
                val currentUrl = requireNotNull(readerSession.state.value.book?.bookUrl)
                actionRepository.changeSource(currentUrl, intent.book, intent.toc)
                executeSession(MangaSessionCommand.Open(intent.book.bookUrl, true, true))
            }
            MangaReaderIntent.AddCurrentBookToShelf -> launchAction {
                readerSession.state.value.book?.bookUrl?.let { actionRepository.addCurrentBookToShelf(it) }
                _effects.tryEmit(MangaReaderEffect.Finish(bookshelfChanged = true))
            }
            MangaReaderIntent.DiscardCurrentBookAndExit -> launchAction {
                readerSession.state.value.book?.bookUrl?.let { actionRepository.removeTemporaryBook(it) }
                _effects.tryEmit(MangaReaderEffect.Finish())
            }
            MangaReaderIntent.DismissDialog -> _uiState.update { it.copy(activeDialog = null) }
            MangaReaderIntent.BackPressed -> {
                when {
                    _uiState.value.activeDialog != null -> {
                        _uiState.update { it.copy(activeDialog = null) }
                    }
                    _uiState.value.activeSheet != null -> {
                        _uiState.update { it.copy(activeSheet = null, changeSourceBook = null) }
                    }
                    _uiState.value.settingsCategory != null -> closeSettings()
                    _uiState.value.menuVisible -> setMenuVisible(false)
                    else -> exitReader()
                }
            }
            MangaReaderIntent.ExitReader -> exitReader()
            MangaReaderIntent.ToggleMenu -> setMenuVisible(!_uiState.value.menuVisible)
            MangaReaderIntent.HideMenu -> setMenuVisible(false)
            MangaReaderIntent.Retry -> launchAction {
                showLoading()
                if (readerSession.state.value.book == null) {
                    // 打开失败（book==null）：RetryChapter 是 no-op，必须重发 Open，否则永久转圈
                    launchRequest?.let {
                        executeSession(
                            MangaSessionCommand.Open(it.bookUrl, it.inBookshelf, it.chapterChanged)
                        )
                    }
                } else {
                    invalidateCurrentChapter()
                }
            }
            MangaReaderIntent.PreviousChapter -> openRelativeChapter(-1)
            MangaReaderIntent.NextChapter -> openRelativeChapter(1)
            MangaReaderIntent.OpenCatalog -> showSheet(MangaReaderSheet.Catalog)
            MangaReaderIntent.OpenBookInfo -> emitAndHide(MangaReaderEffect.OpenBookInfo)
            MangaReaderIntent.OpenChapterUrl -> emitAndHide(
                MangaReaderEffect.OpenChapterUrl(readSettingsGateway.currentSettings.readUrlInBrowser)
            )
            MangaReaderIntent.ChangeSource -> {
                setMenuVisible(false)
                _uiState.update { it.copy(activeSheet = MangaReaderSheet.ChangeSource) }
                viewModelScope.launch {
                    readerSession.state.value.book?.bookUrl?.let { url ->
                        actionRepository.getBook(url)?.let { book ->
                            _uiState.update { it.copy(changeSourceBook = MangaBookSnapshot.from(book)) }
                        }
                    }
                }
            }
            MangaReaderIntent.RefreshChapter -> {
                setMenuVisible(false)
                launchAction {
                    showLoading()
                    invalidateCurrentChapter()
                }
            }
            is MangaReaderIntent.OpenSettings -> openSettings(intent.category)
            MangaReaderIntent.CloseSettings -> closeSettings()
            MangaReaderIntent.OpenClickAreaConfig -> _uiState.update {
                it.copy(activeDialog = MangaReaderDialog.ClickAreaConfig)
            }
            MangaReaderIntent.ToggleAutoRead -> _uiState.update {
                it.copy(
                    autoReadEnabled = !it.autoReadEnabled,
                    menuVisible = it.settingsCategory == MangaReaderSettingsCategory.AUTO_READ,
                )
            }
            MangaReaderIntent.ToggleDayNight -> {
                // 与文本阅读器 ReadMenu 夜间按钮一致：切主题模式并应用，主题经 ThemeManager 刷新
                AppConfig.isNightTheme = !AppConfig.isNightTheme
                ThemeConfig.applyDayNight(appCtx)
            }
            MangaReaderIntent.PlannedFeature -> enqueueMessage(resId = R.string.manga_reader_planned)
            MangaReaderIntent.DismissSheet -> _uiState.update {
                it.copy(activeSheet = null, changeSourceBook = null, settingsCategory = null)
            }
            is MangaReaderIntent.UpdateSetting -> updateSetting(intent.key, intent.value)
            is MangaReaderIntent.UpdateClickAction -> updateClickAction(intent.index, intent.action)
            is MangaReaderIntent.RetryChapter -> executeSession(MangaSessionCommand.RetryChapter(intent.chapterIndex))
            is MangaReaderIntent.PageLoadStarted -> updatePageLoadState(
                intent.key,
                MangaPageLoadState.Loading,
            )

            is MangaReaderIntent.PageLoadSucceeded -> updatePageLoadState(
                intent.key,
                MangaPageLoadState.Ready,
            )

            is MangaReaderIntent.PageLoadFailed -> updatePageLoadState(
                intent.key,
                MangaPageLoadState.Failed(intent.message),
            )

            is MangaReaderIntent.RetryPage -> retryPage(intent.key)
            is MangaReaderIntent.RetryFailedPagesInChapter -> retryFailedPages(intent.chapterIndex)
            is MangaReaderIntent.PageStep -> requestPageStep(intent.direction)
            is MangaReaderIntent.SeekToPage -> seekToPage(intent.pageIndex)
            is MangaReaderIntent.VisibleItemChanged -> updateVisibleItem(
                intent.itemIndex,
                intent.firstItemIndex,
                intent.lastItemIndex,
                intent.currentChapterVisible,
                intent.navigationId,
            )
            is MangaReaderIntent.PagerScrollChanged -> {
                pagerScrollInProgress = intent.inProgress
                if (!intent.inProgress) {
                    deferredReadySession?.let { session ->
                        deferredReadySession = null
                        refreshContent(session)
                    }
                }
            }
            is MangaReaderIntent.LongPressPage -> {
                if (_uiState.value.settings.longPressEnabled) {
                    val fallback = findSpreadCompanion(intent.pageKey)
                    showSheet(
                        MangaReaderSheet.PageActions(
                            intent.pageKey,
                            intent.companionPageKey ?: fallback?.first,
                            if (intent.companionPageKey != null) intent.companionBeforePage
                            else fallback?.second == true,
                        )
                    )
                }
            }

            is MangaReaderIntent.ExecutePageAction -> executePageAction(intent.action)
            MangaReaderIntent.OpenCacheActions -> openCacheActions()
            is MangaReaderIntent.CacheChapters -> cacheChapters(intent.selection)
            is MangaReaderIntent.MessageShown -> _uiState.update { state ->
                state.copy(
                    pendingMessages = state.pendingMessages
                        .filterNot { it.id == intent.id }
                        .toImmutableList()
                )
            }
        }
    }

    private fun initialize(intent: MangaReaderIntent.Initialize) {
        launchRequest = intent
        showLoading()
        executeSession(MangaSessionCommand.Open(intent.bookUrl, intent.inBookshelf, intent.chapterChanged))
    }

    private fun executeSession(command: MangaSessionCommand) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            readerSession.execute(command)
        }
    }

    private fun openChapter(chapterIndex: Int, pageIndex: Int) {
        val session = readerSession.state.value
        if (chapterIndex !in 0 until session.chapterCount) return
        if (pendingExplicitChapterIndex == chapterIndex) return
        pendingExplicitChapterIndex = chapterIndex
        _uiState.update {
            it.copy(
                isChapterLoading = it.pages.isNotEmpty(),
                pendingChapterIndex = chapterIndex,
                navigationId = System.nanoTime(),
            )
        }
        executeSession(MangaSessionCommand.OpenChapter(chapterIndex, pageIndex))
    }

    private fun openRelativeChapter(direction: Int) {
        val session = readerSession.state.value
        openChapter((session.chapterIndex + direction).coerceIn(0, (session.chapterCount - 1).coerceAtLeast(0)), 0)
    }

    private fun invalidateCurrentChapter() {
        val session = readerSession.state.value
        val bookUrl = session.book?.bookUrl ?: return
        // 先删内容缓存、后重载（顺序执行，避免重试读到旧缓存；fork 原版同为顺序语义）
        viewModelScope.launch {
            actionRepository.invalidateChapter(bookUrl, session.chapterIndex)
            executeSession(MangaSessionCommand.RetryChapter(session.chapterIndex))
        }
    }

    /** 打开缓存 Sheet；本地书不可离线缓存（无书源下载管线），给出提示。 */
    private fun openCacheActions() {
        setMenuVisible(false)
        viewModelScope.launch {
            val bookUrl = _uiState.value.bookUrl
            val book = bookUrl.takeIf { it.isNotEmpty() }?.let { actionRepository.getBook(it) }
            val available = book != null && !book.isLocal
            _uiState.update { it.copy(cacheAvailable = available) }
            if (available) {
                _uiState.update { it.copy(activeSheet = MangaReaderSheet.CacheActions) }
            } else {
                enqueueMessage(resId = R.string.manga_reader_cache_unavailable)
            }
        }
    }

    private fun cacheChapters(selection: MangaCacheSelection) {
        val state = _uiState.value
        if (!state.cacheAvailable || state.chapterCount <= 0) return
        if (selection == MangaCacheSelection.FOLLOWING && state.chapterIndex >= state.chapterCount - 1) {
            enqueueMessage(resId = R.string.manga_reader_no_next_chapter)
            _uiState.update { it.copy(activeSheet = null) }
            return
        }
        _uiState.update { it.copy(activeSheet = null) }
        launchAction(successMessageRes = R.string.manga_reader_cache_enqueued) {
            when (selection) {
                MangaCacheSelection.CURRENT -> actionRepository.cacheChapters(
                    state.bookUrl,
                    state.chapterIndex,
                    state.chapterIndex,
                )

                MangaCacheSelection.FOLLOWING -> actionRepository.cacheChapters(
                    state.bookUrl,
                    state.chapterIndex + 1,
                    state.chapterCount - 1,
                )

                MangaCacheSelection.ALL -> actionRepository.cacheChapters(
                    state.bookUrl,
                    0,
                    state.chapterCount - 1,
                )
            }
        }
    }

    /**
     * 自动离线缓存（MD3 46f25b873）：开启后阅读时自动把后续 N 章加入离线下载队列。
     * 以 (bookUrl, chapterIndex) 去重，每次换章只入队一次。
     */
    private fun autoCacheFollowingChapters() {
        val state = _uiState.value
        if (!state.settings.autoOfflineCache) return
        val count = state.settings.chapterPrefetchCount
        if (count <= 0 || state.chapterCount <= 0) return
        val last = state.chapterIndex
        if (lastAutoCachedKey == state.bookUrl to last) return
        lastAutoCachedKey = state.bookUrl to last
        val from = (last + 1).coerceAtMost(state.chapterCount - 1)
        val to = (last + count).coerceAtMost(state.chapterCount - 1)
        if (to < from) return
        launchAction {
            actionRepository.cacheChapters(state.bookUrl, from, to)
        }
    }

    private fun executePageAction(action: MangaPageAction) {
        val sheet = _uiState.value.activeSheet as? MangaReaderSheet.PageActions ?: return
        val selected = pageForKey(sheet.pageKey) ?: return
        val companion = sheet.companionPageKey?.let(::pageForKey)
        val pages = if (sheet.companionBeforePage) listOfNotNull(companion, selected)
        else listOfNotNull(selected, companion)
        val urls = when (action) {
            MangaPageAction.SAVE_SPREAD, MangaPageAction.SHARE_SPREAD, MangaPageAction.COPY_SPREAD ->
                pages.map { it.imageUrl }

            else -> listOf(selected.imageUrl)
        }
        _uiState.update { it.copy(activeSheet = null) }
        launchAction(
            successMessageRes = when (action) {
                MangaPageAction.SAVE, MangaPageAction.SAVE_SPREAD -> R.string.manga_reader_image_saved
                MangaPageAction.SET_COVER -> R.string.manga_reader_cover_set
                else -> null
            },
            failureMessageRes = R.string.manga_reader_action_failed,
        ) {
            val book = requireNotNull(readerSession.state.value.book)
            when (action) {
                MangaPageAction.SAVE, MangaPageAction.SAVE_SPREAD -> check(
                    actionRepository.saveImages(urls, book.bookUrl, book.sourceOrigin)
                )

                MangaPageAction.SHARE, MangaPageAction.SHARE_SPREAD -> _effects.tryEmit(
                    MangaReaderEffect.ShareImage(
                        actionRepository.prepareImageFile(
                            urls,
                            book.bookUrl,
                            book.sourceOrigin
                        ).path
                    )
                )

                MangaPageAction.COPY, MangaPageAction.COPY_SPREAD -> _effects.tryEmit(
                    MangaReaderEffect.CopyImage(
                        actionRepository.prepareImageFile(
                            urls,
                            book.bookUrl,
                            book.sourceOrigin
                        ).path
                    )
                )

                MangaPageAction.SET_COVER -> actionRepository.setBookCover(
                    book.bookUrl,
                    selected.imageUrl
                )
            }
        }
    }

    private fun pageForKey(key: String) =
        _uiState.value.pages.firstOrNull { it.key == key } as? MangaReaderItemUi.Page

    /** 双页模式下找与 [key] 配对的另一页；返回 (对方 key, 对方是否在前)。 */
    private fun findSpreadCompanion(key: String): Pair<String, Boolean>? {
        val state = _uiState.value
        if (state.settings.doublePageMode == 0) return null
        val pages = state.pages.filterIsInstance<MangaReaderItemUi.Page>()
        val index = pages.indexOfFirst { it.key == key }
        val selected = pages.getOrNull(index) ?: return null
        pages.getOrNull(index + 1)?.takeIf { it.chapterIndex == selected.chapterIndex }
            ?.let { return it.key to false }
        return pages.getOrNull(index - 1)?.takeIf { it.chapterIndex == selected.chapterIndex }
            ?.let { it.key to true }
    }

    private fun updatePageLoadState(key: String, loadState: MangaPageLoadState) {
        _uiState.update { state ->
            val index = state.pages.indexOfFirst { it.key == key }
            val page =
                state.pages.getOrNull(index) as? MangaReaderItemUi.Page ?: return@update state
            // 已就绪的页不被重新入队/预取触发 onStart 而降级回 Loading，避免已显示的图被
            // 遮罩/转圈闪一下；重试走 retryPage 显式置回 Queued，不受此限制。
            if (page.loadState == MangaPageLoadState.Ready) return@update state
            if (page.loadState == loadState) return@update state
            state.copy(
                pages = state.pages.mapIndexed { itemIndex, item ->
                    if (itemIndex == index) page.copy(loadState = loadState) else item
                }.toImmutableList(),
            )
        }
    }

    private fun retryPage(key: String) {
        _uiState.update { state ->
            val index = state.pages.indexOfFirst { it.key == key }
            val page =
                state.pages.getOrNull(index) as? MangaReaderItemUi.Page ?: return@update state
            state.copy(
                pages = state.pages.mapIndexed { itemIndex, item ->
                    if (itemIndex == index) page.copy(
                        loadState = MangaPageLoadState.Queued,
                        retryRevision = page.retryRevision + 1,
                    ) else item
                }.toImmutableList(),
            )
        }
    }

    private fun retryFailedPages(chapterIndex: Int) {
        _uiState.update { state ->
            state.copy(pages = state.pages.map { item ->
                if (item is MangaReaderItemUi.Page && item.chapterIndex == chapterIndex &&
                    item.loadState is MangaPageLoadState.Failed
                ) item.copy(
                    loadState = MangaPageLoadState.Queued,
                    retryRevision = item.retryRevision + 1
                )
                else item
            }.toImmutableList())
        }
    }

    private fun launchAction(
        @androidx.annotation.StringRes successMessageRes: Int? = null,
        @androidx.annotation.StringRes failureMessageRes: Int = R.string.manga_reader_action_failed,
        action: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { successMessageRes?.let { resId ->
                    enqueueMessage(resId = resId)
                } }
                .onFailure { error ->
                    val message = error.localizedMessage?.takeIf(String::isNotBlank)
                    if (_uiState.value.isLoading) {
                        // 加载中失败 → 整屏错误（避免永久转圈）
                        showError(message ?: appCtx.getString(failureMessageRes))
                    } else if (message != null) {
                        enqueueMessage(text = message)
                    } else {
                        enqueueMessage(resId = failureMessageRes)
                    }
                }
        }
    }

    fun refreshContent() = refreshContent(readerSession.state.value)

    private fun refreshContent(session: MangaSessionState) {
        refreshContentJob?.cancel()
        refreshContentJob = viewModelScope.launch {
            val book = session.book
            if (book == null) {
                val error = session.openError
                if (!error.isNullOrBlank()) showError(error, fallbackRes = R.string.manga_reader_init_failed)
                return@launch
            }
            val current = session.currentChapter
            if (current !is MangaChapterState.Ready) {
                when (current) {
                    is MangaChapterState.Failed -> {
                        // 显式跳章失败后立即放开门闩：否则目标页一直不可见时，
                        // 之后对该章的目录/上一章点击会被 openChapter 静默吞掉。
                        if (pendingExplicitChapterIndex == session.chapterIndex) {
                            pendingExplicitChapterIndex = null
                        }
                        showError(current.message)
                        return@launch
                    }
                    else -> Unit
                }
                if (pendingExplicitChapterIndex == session.chapterIndex && _uiState.value.pages.isNotEmpty()) {
                    _uiState.update { it.copy(isChapterLoading = true, errorMessage = null) }
                } else {
                    showLoading()
                }
                return@launch
            }
            if (pagerScrollInProgress &&
                (_uiState.value.chapterIndex != session.chapterIndex ||
                    pendingExplicitChapterIndex == session.chapterIndex)
            ) {
                deferredReadySession = session
                _uiState.update { it.copy(isChapterLoading = true, errorMessage = null) }
                return@launch
            }
            deferredReadySession = null

            val hideTitle = readSettings(latestMangaSettings).hideMangaTitle
            // 保留既有页的加载态/重试代次：内容刷新（如邻章补齐）不把已显示/已失败的页重置
            val existingPages = _uiState.value.pages
                .filterIsInstance<MangaReaderItemUi.Page>()
                .associateBy(MangaReaderItemUi.Page::key)
            val prevItems = buildAdjacentItems(
                session = session,
                chapter = session.previousChapter,
                direction = MangaChapterTransitionDirection.PREVIOUS,
                hideTitle = hideTitle,
                existingPages = existingPages,
            )
            val nextItems = buildAdjacentItems(
                session = session,
                chapter = session.nextChapter,
                direction = MangaChapterTransitionDirection.NEXT,
                hideTitle = hideTitle,
                existingPages = existingPages,
            )
            val currentItems = chapterItems(session, current.chapter, hideTitle, existingPages)
            val items = (prevItems + currentItems + nextItems).toImmutableList()
            val safePosition = (prevItems.size + session.pageIndex)
                .coerceIn(0, (items.size - 1).coerceAtLeast(0))

            val oldState = _uiState.value
            val shouldPosition = shouldForceMangaChapterPosition(
                hasPages = oldState.pages.isNotEmpty(),
                isLoading = oldState.isLoading,
                currentBookUrl = oldState.bookUrl,
                targetBookUrl = book.bookUrl,
                pendingExplicitChapterIndex = pendingExplicitChapterIndex,
                targetChapterIndex = session.chapterIndex,
            )
            // 锚点按 (章节, 页码) 定位，不依赖含图片 URL 的 item key：URL/页数变化导致 key
            // 失效后不再回退到旧裸索引（否则会把阅读器滚到错章并触发连环误切），而是按
            // 会话当前页码重算安全位置并主动复位。
            val oldCurrentItem = oldState.pages.getOrNull(oldState.currentItemIndex)
            val anchoredIndex = if (shouldPosition) null else oldCurrentItem?.let { anchor ->
                if (anchor is MangaReaderItemUi.Page) {
                    items.indexOfFirst {
                        it is MangaReaderItemUi.Page &&
                                it.chapterIndex == anchor.chapterIndex &&
                                it.pageIndex == anchor.pageIndex
                    }.takeIf { it >= 0 }
                } else null
            }
            // 旧 key 仍存在于新列表时 LazyColumn/Pager 能按 key 保住滚动位置；key 失效
            // （如图片 URL 变化）时必须主动复位到锚点，否则列表停在旧偏移上会串到邻章。
            val keyPreserved =
                oldCurrentItem?.let { old -> items.any { it.key == old.key } } == true
            val targetIndex = anchoredIndex ?: safePosition
            val positionChanged = shouldPosition || anchoredIndex == null || !keyPreserved
            _uiState.update { old ->
                old.copy(
                    bookName = book.name,
                    bookAuthor = book.author,
                    bookUrl = book.bookUrl,
                    coverUrl = book.coverUrl,
                    customCoverUrl = book.customCoverUrl,
                    chapterName = current.chapter.chapterTitle,
                    chapterUrl = current.chapter.chapterUrl,
                    sourceName = book.sourceName,
                    sourceUrl = book.sourceOrigin,
                    sourceType = book.sourceType,
                    pages = items,
                    currentItemIndex = targetIndex,
                    currentPage = if (positionChanged) session.pageIndex else old.currentPage,
                    pageCount = if (positionChanged) current.chapter.pages.size else old.pageCount,
                    chapterIndex = session.chapterIndex,
                    chapterCount = session.chapterCount,
                    isLoading = false,
                    isChapterLoading = false,
                    pendingChapterIndex = null,
                    errorMessage = null,
                    settings = readSettings(latestMangaSettings),
                    scrollRequest = if (positionChanged) {
                        MangaScrollRequest(
                            id = System.nanoTime(),
                            itemIndex = targetIndex,
                            animated = false,
                        )
                    } else old.scrollRequest,
                )
            }
            if (positionChanged && pendingExplicitChapterIndex != session.chapterIndex) {
                // 会话已持有恢复的目标页：仅通知会话以驱动邻章加载，
                // 不假装 LazyColumn 已到达目标（那会清掉 scrollRequest，让恢复滚动前
                // 到来的旧视口回调覆盖掉持久化进度）。
                executeSession(
                    MangaSessionCommand.VisiblePageChanged(session.chapterIndex, session.pageIndex)
                )
            }
            // 自动离线缓存（开启时）：换章后把后续 N 章加入离线下载队列（内部去重）
            autoCacheFollowingChapters()
        }
    }

    /** 邻章条目：前一章 → [邻章页..., 过渡卡片]；后一章 → [过渡卡片, 邻章页...]。hideTitle 时跳过全部非页项。 */
    private fun buildAdjacentItems(
        session: MangaSessionState,
        chapter: MangaChapterState,
        direction: MangaChapterTransitionDirection,
        hideTitle: Boolean,
        existingPages: Map<String, MangaReaderItemUi.Page> = emptyMap(),
    ): List<MangaReaderItemUi> {
        val currentName = (session.currentChapter as? MangaChapterState.Ready)
            ?.chapter?.chapterTitle ?: ""
        val edgePrefix = if (direction == MangaChapterTransitionDirection.PREVIOUS) "prev" else "next"
        val targetIndex = session.chapterIndex + if (direction == MangaChapterTransitionDirection.PREVIOUS) -1 else 1
        val targetExists = targetIndex in 0 until session.chapterCount
        val targetName = session.book?.chapterTitles?.getOrNull(targetIndex)
            ?: appCtx.getString(R.string.manga_reader_transition_chapter_number, targetIndex + 1)
                .takeIf { targetExists }
        val transition = buildTransition(
            chapter = chapter,
            edgePrefix = edgePrefix,
            direction = direction,
            targetIndex = targetIndex,
            targetExists = targetExists,
            currentName = currentName,
            targetName = targetName,
        )
        if (hideTitle) {
            return if (chapter is MangaChapterState.Ready) {
                chapterItems(session, chapter.chapter, hideTitle = true, existingPages = existingPages)
            } else emptyList()
        }
        val pages = if (chapter is MangaChapterState.Ready) {
            chapterItems(session, chapter.chapter, hideTitle = false, existingPages = existingPages)
        } else {
            emptyList()
        }
        return if (direction == MangaChapterTransitionDirection.PREVIOUS) pages + listOfNotNull(transition)
        else listOfNotNull(transition) + pages
    }

    private fun buildTransition(
        chapter: MangaChapterState,
        edgePrefix: String,
        direction: MangaChapterTransitionDirection,
        targetIndex: Int,
        targetExists: Boolean,
        currentName: String,
        targetName: String?,
    ): MangaReaderItemUi.ChapterTransition? {
        val key = "transition:$edgePrefix:$targetIndex"
        return when (chapter) {
            is MangaChapterState.Ready -> MangaReaderItemUi.ChapterTransition(
                key = "$key:ready",
                direction = direction,
                targetChapterIndex = targetIndex,
                currentChapterName = currentName,
                targetChapterName = chapter.chapter.chapterTitle,
                targetStatus = MangaChapterTransitionStatus.READY,
            )
            is MangaChapterState.Failed -> MangaReaderItemUi.ChapterTransition(
                key = "$key:failed",
                direction = direction,
                targetChapterIndex = targetIndex,
                currentChapterName = currentName,
                targetChapterName = targetName,
                targetStatus = MangaChapterTransitionStatus.FAILED,
                statusMessage = appCtx.getString(
                    R.string.manga_reader_adjacent_failed,
                    chapter.message.ifBlank { appCtx.getString(R.string.manga_reader_unknown_error) },
                ),
                retryChapterIndex = targetIndex,
            )
            MangaChapterState.Empty -> MangaReaderItemUi.ChapterTransition(
                key = "$key:empty",
                direction = direction,
                targetChapterIndex = targetIndex.takeIf { targetExists },
                currentChapterName = currentName,
                targetChapterName = targetName,
                targetStatus = if (targetExists) MangaChapterTransitionStatus.WAITING
                else MangaChapterTransitionStatus.UNAVAILABLE,
            )
            is MangaChapterState.Loading -> MangaReaderItemUi.ChapterTransition(
                key = "$key:loading",
                direction = direction,
                targetChapterIndex = targetIndex,
                currentChapterName = currentName,
                targetChapterName = targetName,
                targetStatus = MangaChapterTransitionStatus.LOADING,
                statusMessage = appCtx.getString(
                    if (direction == MangaChapterTransitionDirection.PREVIOUS) {
                        R.string.manga_reader_loading_prev
                    } else {
                        R.string.manga_reader_loading_next
                    },
                ),
            )
        }
    }

    private fun chapterItems(
        session: MangaSessionState,
        chapter: io.legado.app.domain.model.manga.MangaChapterContent,
        hideTitle: Boolean,
        existingPages: Map<String, MangaReaderItemUi.Page> = emptyMap(),
    ): List<MangaReaderItemUi> {
        val bookUrl = session.book?.bookUrl.orEmpty()
        return if (chapter.isVolume && chapter.pages.isEmpty()) {
            if (hideTitle) emptyList()
            else listOf(
                MangaReaderItemUi.ChapterEdge(
                    key = "volume:${chapter.chapterIndex}",
                    message = chapter.chapterTitle,
                )
            )
        } else chapter.pages.map { page ->
            val key = "page:${chapter.chapterIndex}:${page.pageIndex}:${page.imageUrl}"
            val existing = existingPages[key]
            MangaReaderItemUi.Page(
                key = key,
                imageUrl = page.imageUrl,
                bookUrl = bookUrl,
                chapterIndex = chapter.chapterIndex,
                chapterCount = session.chapterCount,
                pageIndex = page.pageIndex,
                pageCount = page.pageCount,
                chapterName = chapter.chapterTitle,
                loadState = existing?.loadState ?: MangaPageLoadState.Queued,
                retryRevision = existing?.retryRevision ?: 0,
            )
        }
    }

    fun showLoading() {
        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                pages = persistentListOf(),
                currentItemIndex = 0,
                scrollRequest = null,
            )
        }
    }

    fun showError(message: String) {
        _uiState.update {
            it.copy(isLoading = false, errorMessage = MangaReaderText.Dynamic(message))
        }
    }

    private fun showError(message: String, @androidx.annotation.StringRes fallbackRes: Int) {
        if (message.isNotBlank()) showError(message)
        else {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = MangaReaderText.Resource(
                        fallbackRes,
                        persistentListOf(""),
                    ),
                )
            }
        }
    }

    private fun enqueueMessage(
        @androidx.annotation.StringRes resId: Int? = null,
        text: String? = null,
        args: ImmutableList<String> = persistentListOf(),
    ) {
        _uiState.update { state ->
            state.copy(
                pendingMessages = (state.pendingMessages +
                    MangaReaderMessage(
                        id = System.nanoTime(),
                        content = if (resId != null) {
                            MangaReaderText.Resource(resId, args)
                        } else {
                            MangaReaderText.Dynamic(requireNotNull(text))
                        },
                    )
                ).toImmutableList()
            )
        }
    }

    override fun onCleared() {
        readerSession.close()
        super.onCleared()
    }

    private fun setMenuVisible(visible: Boolean) {
        _uiState.update {
            it.copy(
                menuVisible = visible,
                settingsCategory = if (visible) it.settingsCategory else null,
            )
        }
        _effects.tryEmit(MangaReaderEffect.SetSystemBarsVisible(visible))
    }

    private fun openSettings(category: MangaReaderSettingsCategory) {
        setMenuVisible(true)
        _uiState.update { it.copy(settingsCategory = category, activeSheet = null) }
    }

    private fun closeSettings() {
        _uiState.update { it.copy(settingsCategory = null) }
    }

    /**
     * 退出阅读器：临时书（不在书架）先走"加入书架/丢弃"确认，书架书直接 Finish。
     */
    private fun exitReader() {
        val book = readerSession.state.value.book
        when {
            book != null && !book.inBookshelf && _uiState.value.confirmAddToShelf -> {
                _uiState.update { it.copy(activeDialog = MangaReaderDialog.AddToShelf) }
            }
            book != null && !book.inBookshelf -> onIntent(
                MangaReaderIntent.DiscardCurrentBookAndExit
            )
            else -> _effects.tryEmit(MangaReaderEffect.Finish())
        }
    }

    private fun emitAndHide(effect: MangaReaderEffect) {
        setMenuVisible(false)
        _effects.tryEmit(effect)
    }

    private fun showSheet(sheet: MangaReaderSheet) {
        setMenuVisible(false)
        _uiState.update { it.copy(activeSheet = sheet) }
    }

    private fun updateClickAction(index: Int, action: Int) {
        if (index !in 0..8) return
        updateMangaPreference { settings ->
            when (index) {
                0 -> settings.copy(clickActionTL = action)
                1 -> settings.copy(clickActionTC = action)
                2 -> settings.copy(clickActionTR = action)
                3 -> settings.copy(clickActionML = action)
                4 -> settings.copy(clickActionMC = action)
                5 -> settings.copy(clickActionMR = action)
                6 -> settings.copy(clickActionBL = action)
                7 -> settings.copy(clickActionBC = action)
                8 -> settings.copy(clickActionBR = action)
                else -> settings
            }
        }
        _uiState.update { state ->
            state.copy(settings = state.settings.copy(
                clickActions = state.settings.clickActions.mapIndexed { i, old ->
                    if (i == index) action else old
                }.toImmutableList()
            ))
        }
    }

    private fun updateSetting(key: MangaReaderSettingKey, value: Int) {
        val enabled = value != 0
        when (key) {
            MangaReaderSettingKey.SCROLL_MODE -> {
                persistBookConfig { actionRepository.updateReadConfig(bookUrl()) { mangaScrollMode = value } }
                updateSettings { copy(scrollMode = value) }
            }
            MangaReaderSettingKey.SIDE_PADDING -> {
                persistBookConfig { actionRepository.updateReadConfig(bookUrl()) { webtoonSidePaddingDp = value } }
                updateSettings { copy(sidePaddingPercent = value) }
            }
            MangaReaderSettingKey.BACKGROUND_RED,
            MangaReaderSettingKey.BACKGROUND_GREEN,
            MangaReaderSettingKey.BACKGROUND_BLUE -> {
                val old = _uiState.value.settings.backgroundColor
                val red = if (key == MangaReaderSettingKey.BACKGROUND_RED) value else (old.red * 255).toInt()
                val green = if (key == MangaReaderSettingKey.BACKGROUND_GREEN) value else (old.green * 255).toInt()
                val blue = if (key == MangaReaderSettingKey.BACKGROUND_BLUE) value else (old.blue * 255).toInt()
                val color = Color(red, green, blue)
                updateMangaPreference { it.copy(background = color.toArgb()) }
                updateSettings { copy(backgroundColor = color) }
            }
            MangaReaderSettingKey.AUTO_BACKGROUND -> setAndUpdate(
                { copy(autoBackground = enabled) }, { copy(autoBackground = enabled) })
            MangaReaderSettingKey.PAGE_SCALE_TYPE -> setAndUpdate(
                { copy(pageScaleType = value) }, { copy(pageScaleType = value) })
            MangaReaderSettingKey.WIDE_PAGE_MODE -> setAndUpdate(
                { copy(widePageMode = value) }, { copy(widePageMode = value) })
            MangaReaderSettingKey.DOUBLE_PAGE_MODE -> setAndUpdate(
                { copy(doublePageMode = value) }, { copy(doublePageMode = value) })
            MangaReaderSettingKey.DOUBLE_PAGE_COVER_SINGLE -> setAndUpdate(
                { copy(doublePageCoverSingle = enabled) },
                { copy(doublePageCoverSingle = enabled) })
            MangaReaderSettingKey.DOUBLE_PAGE_INVERT -> setAndUpdate(
                { copy(doublePageInvert = enabled) }, { copy(doublePageInvert = enabled) })
            MangaReaderSettingKey.DOUBLE_PAGE_SHIFT -> setAndUpdate(
                { copy(doublePageShift = enabled) }, { copy(doublePageShift = enabled) })
            MangaReaderSettingKey.DISABLE_SCALE -> setAndUpdate(
                { copy(disableMangaScale = enabled) }, { copy(disableScale = enabled) })
            MangaReaderSettingKey.DISABLE_DOUBLE_TAP_ZOOM -> setAndUpdate(
                { copy(disableMangaDoubleTapZoom = enabled) },
                { copy(disableDoubleTapZoom = enabled) })
            MangaReaderSettingKey.DISABLE_SCROLL_ANIMATION -> setAndUpdate(
                { copy(disableMangaScrollAnimation = enabled) },
                { copy(disableScrollAnimation = enabled) })
            MangaReaderSettingKey.DISABLE_CROSS_FADE -> setAndUpdate(
                { copy(disableMangaCrossFade = enabled) }, { copy(disableCrossFade = enabled) })
            MangaReaderSettingKey.DISABLE_CLICK_SCROLL -> setAndUpdate(
                { copy(disableClickScroll = enabled) }, { copy(disableClickScroll = enabled) })
            MangaReaderSettingKey.LONG_PRESS -> setAndUpdate(
                { copy(longClick = enabled) }, { copy(longPressEnabled = enabled) })
            MangaReaderSettingKey.PRE_DOWNLOAD -> setAndUpdate(
                { copy(preDownloadNum = value) }, { copy(preDownloadCount = value) })
            MangaReaderSettingKey.CHAPTER_PREFETCH -> setAndUpdate(
                { copy(chapterPrefetchCount = value) }, { copy(chapterPrefetchCount = value) })
            MangaReaderSettingKey.AUTO_OFFLINE_CACHE -> setAndUpdate(
                { copy(autoOfflineCache = enabled) }, { copy(autoOfflineCache = enabled) })
            MangaReaderSettingKey.AUTO_READ_SPEED -> setAndUpdate(
                { copy(autoPageSpeed = value) }, { copy(autoReadSpeed = value) })
            MangaReaderSettingKey.VOLUME_KEY_PAGE -> setAndUpdate(
                { copy(volumeKeyPage = enabled) }, { copy(volumeKeyPage = enabled) })
            MangaReaderSettingKey.REVERSE_VOLUME_KEY_PAGE -> setAndUpdate(
                { copy(reverseVolumeKeyPage = enabled) },
                { copy(reverseVolumeKeyPage = enabled) })
            MangaReaderSettingKey.HIDE_MANGA_TITLE -> {
                // 保留主仓库双写：AppConfig(SP) + MangaSettings(DataStore)，兼容旧用户 SP 值；
                // 内容生成在 VM item 映射层按 hideMangaTitle 过滤。
                // atomicUpdate 同步写库后显式同步 latestMangaSettings 并直接重建条目，
                // 避免 settings 流收集器与 refreshContent 竞态导致过渡页开关不生效
                // （旧实现走 RetryChapter，会白白重载当前章）。
                AppConfig.hideMangaTitle = enabled
                viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    mangaSettingsGateway.update { it.copy(hideTitle = enabled) }
                    latestMangaSettings = mangaSettingsGateway.currentSettings
                    updateSettings { copy(hideMangaTitle = enabled) }
                    refreshContent()
                }
            }
            MangaReaderSettingKey.ENABLE_GRAY -> {
                updateMangaPreference {
                    it.copy(enableGray = enabled, enableEInk = if (enabled) false else it.enableEInk)
                }
                updateSettings { copy(enableGray = enabled, enableEInk = if (enabled) false else enableEInk) }
            }
            MangaReaderSettingKey.ENABLE_EINK -> {
                updateMangaPreference {
                    it.copy(enableEInk = enabled, enableGray = if (enabled) false else it.enableGray)
                }
                updateSettings { copy(enableEInk = enabled, enableGray = if (enabled) false else enableGray) }
            }
            MangaReaderSettingKey.EINK_THRESHOLD -> setAndUpdate(
                { copy(eInkThreshold = value) }, { copy(eInkThreshold = value) })
            MangaReaderSettingKey.FILTER_RED,
            MangaReaderSettingKey.FILTER_GREEN,
            MangaReaderSettingKey.FILTER_BLUE,
            MangaReaderSettingKey.FILTER_ALPHA,
            MangaReaderSettingKey.AUTO_BRIGHTNESS,
            MangaReaderSettingKey.BRIGHTNESS -> updateColorSetting(key, value)
            MangaReaderSettingKey.HIDE_FOOTER,
            MangaReaderSettingKey.HIDE_CHAPTER_NAME,
            MangaReaderSettingKey.HIDE_PAGE_NUMBER,
            MangaReaderSettingKey.HIDE_PAGE_NUMBER_LABEL,
            MangaReaderSettingKey.HIDE_CHAPTER,
            MangaReaderSettingKey.HIDE_CHAPTER_LABEL,
            MangaReaderSettingKey.HIDE_PROGRESS,
            MangaReaderSettingKey.HIDE_PROGRESS_LABEL,
            MangaReaderSettingKey.FOOTER_ALIGNMENT -> updateFooterSetting(key, value)
        }
    }

    private fun bookUrl(): String = readerSession.state.value.book?.bookUrl.orEmpty()

    private fun updateSettings(update: MangaReaderSettings.() -> MangaReaderSettings) {
        _uiState.update { it.copy(settings = it.settings.update()) }
    }

    private fun setAndUpdate(
        write: MangaSettings.() -> MangaSettings,
        update: MangaReaderSettings.() -> MangaReaderSettings,
    ) {
        updateMangaPreference(write)
        updateSettings(update)
    }

    private fun updateMangaPreference(transform: (MangaSettings) -> MangaSettings) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            mangaSettingsGateway.update(transform)
        }
    }

    private fun persistBookConfig(block: suspend () -> Unit) {
        viewModelScope.launch {
            bookConfigWriteMutex.withLock { block() }
        }
    }

    private fun updateColorSetting(key: MangaReaderSettingKey, value: Int) {
        val current = _uiState.value.settings
        val config = MangaColorFilterConfig(
            r = current.filterRed,
            g = current.filterGreen,
            b = current.filterBlue,
            a = current.filterAlpha,
            l = current.brightness,
            autoBrightness = current.autoBrightness,
        )
        when (key) {
            MangaReaderSettingKey.FILTER_RED -> config.r = value
            MangaReaderSettingKey.FILTER_GREEN -> config.g = value
            MangaReaderSettingKey.FILTER_BLUE -> config.b = value
            MangaReaderSettingKey.FILTER_ALPHA -> config.a = value
            MangaReaderSettingKey.AUTO_BRIGHTNESS -> config.autoBrightness = value != 0
            MangaReaderSettingKey.BRIGHTNESS -> config.l = value
            else -> Unit
        }
        updateMangaPreference { it.copy(colorFilter = config.toJson()) }
        updateSettings {
            copy(
                filterRed = config.r,
                filterGreen = config.g,
                filterBlue = config.b,
                filterAlpha = config.a,
                autoBrightness = config.autoBrightness,
                brightness = config.l,
            )
        }
        _effects.tryEmit(MangaReaderEffect.SetWindowBrightness(config.autoBrightness, config.l))
    }

    private fun updateFooterSetting(key: MangaReaderSettingKey, value: Int) {
        val current = _uiState.value.settings
        val config = MangaFooterConfig(
            hideFooter = current.hideFooter,
            hideChapterName = current.hideChapterName,
            hidePageNumber = current.hidePageNumber,
            hidePageNumberLabel = current.hidePageNumberLabel,
            hideChapter = current.hideChapter,
            hideChapterLabel = current.hideChapterLabel,
            hideProgressRatio = current.hideProgress,
            hideProgressRatioLabel = current.hideProgressLabel,
            footerOrientation = current.footerAlignment,
        )
        val enabled = value != 0
        when (key) {
            MangaReaderSettingKey.HIDE_FOOTER -> config.hideFooter = enabled
            MangaReaderSettingKey.HIDE_CHAPTER_NAME -> config.hideChapterName = enabled
            MangaReaderSettingKey.HIDE_PAGE_NUMBER -> config.hidePageNumber = enabled
            MangaReaderSettingKey.HIDE_PAGE_NUMBER_LABEL -> config.hidePageNumberLabel = enabled
            MangaReaderSettingKey.HIDE_CHAPTER -> config.hideChapter = enabled
            MangaReaderSettingKey.HIDE_CHAPTER_LABEL -> config.hideChapterLabel = enabled
            MangaReaderSettingKey.HIDE_PROGRESS -> config.hideProgressRatio = enabled
            MangaReaderSettingKey.HIDE_PROGRESS_LABEL -> config.hideProgressRatioLabel = enabled
            MangaReaderSettingKey.FOOTER_ALIGNMENT -> config.footerOrientation = value
            else -> Unit
        }
        updateMangaPreference { it.copy(footerConfig = GSON.toJson(config)) }
        updateSettings {
            copy(
                hideFooter = config.hideFooter,
                hideChapterName = config.hideChapterName,
                hidePageNumber = config.hidePageNumber,
                hidePageNumberLabel = config.hidePageNumberLabel,
                hideChapter = config.hideChapter,
                hideChapterLabel = config.hideChapterLabel,
                hideProgress = config.hideProgressRatio,
                hideProgressLabel = config.hideProgressRatioLabel,
                footerAlignment = config.footerOrientation,
            )
        }
    }

    private fun requestPageStep(direction: Int) {
        val state = _uiState.value
        val range = visibleItemRange?.takeIf { state.currentItemIndex in it }
            ?: (state.currentItemIndex..state.currentItemIndex)
        val target = nextPageItemIndex(
            items = state.pages,
            currentIndex = if (direction > 0) range.last else range.first,
            direction = direction,
        )
        if (target == null) {
            openRelativeChapter(direction)
            return
        }
        _uiState.update {
            it.copy(scrollRequest = MangaScrollRequest(
                System.nanoTime(),
                target,
                !it.settings.disableScrollAnimation,
            ))
        }
    }

    private fun seekToPage(pageIndex: Int) {
        val state = _uiState.value
        val chapterIndex = readerSession.state.value.chapterIndex
        val target = state.pages.indexOfFirst {
            it is MangaReaderItemUi.Page && it.chapterIndex == chapterIndex && it.pageIndex == pageIndex
        }
        if (target >= 0) {
            _uiState.update { it.copy(scrollRequest = MangaScrollRequest(System.nanoTime(), target, false)) }
        }
    }

    private fun updateVisibleItem(
        itemIndex: Int,
        firstItemIndex: Int,
        lastItemIndex: Int,
        currentChapterVisible: Boolean,
        navigationId: Long,
    ) {
        val state = _uiState.value
        if (navigationId != state.navigationId) return
        if (state.isLoading) return
        val item = state.pages.getOrNull(itemIndex)
        if (item !is MangaReaderItemUi.Page) {
            // 过渡页/边缘项：只维护可见区间并清掉指向它的 scrollRequest（防御性兜底，
            // PageStep 已保证目标为真实页；此处保证 seek/遗留请求落在过渡页时不卡死）。
            visibleItemRange = firstItemIndex.coerceAtMost(lastItemIndex)..
                    lastItemIndex.coerceAtLeast(firstItemIndex)
            if (state.scrollRequest?.itemIndex == itemIndex) {
                _uiState.update { it.copy(scrollRequest = null) }
            }
            return
        }
        val requestedItemIndex = state.scrollRequest?.itemIndex
        // LazyColumn 首个回调可能是恢复滚动前的旧视口（常见为第 0 页），若接受它会在
        // scrollToItem 到达前覆盖掉持久化目标。程序化导航期间只认请求指向的那一项。
        if (!acceptsMangaVisibleItem(requestedItemIndex, itemIndex)) return
        val sessionChapter = readerSession.state.value.chapterIndex
        if (pendingExplicitChapterIndex != null && item.chapterIndex != sessionChapter) return
        visibleItemRange = minOf(firstItemIndex, lastItemIndex)..maxOf(firstItemIndex, lastItemIndex)
        when (mangaChapterSwitchDecision(
            currentChapterIndex = sessionChapter,
            visibleChapterIndex = item.chapterIndex,
            currentChapterVisible = currentChapterVisible,
        )) {
            MangaChapterSwitch.NEXT, MangaChapterSwitch.PREVIOUS -> executeSession(
                MangaSessionCommand.PromoteVisibleChapter(item.chapterIndex, item.pageIndex)
            )
            MangaChapterSwitch.NONE -> Unit
        }
        executeSession(MangaSessionCommand.VisiblePageChanged(item.chapterIndex, item.pageIndex))
        _uiState.update {
            it.copy(
                currentItemIndex = itemIndex,
                currentPage = item.pageIndex,
                pageCount = item.pageCount,
                scrollRequest = if (it.scrollRequest?.itemIndex == itemIndex) null else it.scrollRequest,
            )
        }
        if (pendingExplicitChapterIndex == item.chapterIndex) {
            pendingExplicitChapterIndex = null
            _uiState.update { it.copy(navigationId = System.nanoTime()) }
        }
    }

    private fun readSettings(settings: MangaSettings): MangaReaderSettings {
        val colorFilter = GSON.fromJsonObject<MangaColorFilterConfig>(settings.colorFilter)
            .getOrNull() ?: MangaColorFilterConfig()
        val footer = GSON.fromJsonObject<MangaFooterConfig>(settings.footerConfig)
            .getOrNull() ?: MangaFooterConfig()
        val book = readerSession.state.value.book
        return MangaReaderSettings(
            scrollMode = book?.scrollMode ?: settings.scrollMode,
            sidePaddingPercent = book?.sidePaddingDp ?: settings.webtoonSidePaddingDp,
            backgroundColor = Color(settings.background),
            autoBackground = settings.autoBackground,
            pageScaleType = settings.pageScaleType,
            widePageMode = settings.widePageMode,
            doublePageMode = settings.doublePageMode,
            doublePageCoverSingle = settings.doublePageCoverSingle,
            doublePageInvert = settings.doublePageInvert,
            doublePageShift = settings.doublePageShift,
            disableScale = settings.disableMangaScale,
            disableDoubleTapZoom = settings.disableMangaDoubleTapZoom,
            disableScrollAnimation = settings.disableMangaScrollAnimation,
            disableCrossFade = settings.disableMangaCrossFade,
            disableClickScroll = settings.disableClickScroll,
            longPressEnabled = settings.longClick,
            preDownloadCount = settings.preDownloadNum,
            chapterPrefetchCount = settings.chapterPrefetchCount,
            autoOfflineCache = settings.autoOfflineCache,
            autoReadSpeed = settings.autoPageSpeed,
            volumeKeyPage = settings.volumeKeyPage,
            reverseVolumeKeyPage = settings.reverseVolumeKeyPage,
            hideMangaTitle = settings.hideTitle || AppConfig.hideMangaTitle,
            autoBrightness = colorFilter.autoBrightness,
            brightness = colorFilter.l,
            enableGray = settings.enableGray,
            enableEInk = settings.enableEInk,
            eInkThreshold = settings.eInkThreshold,
            filterRed = colorFilter.r,
            filterGreen = colorFilter.g,
            filterBlue = colorFilter.b,
            filterAlpha = colorFilter.a,
            hideFooter = footer.hideFooter,
            hideChapterName = footer.hideChapterName,
            hidePageNumber = footer.hidePageNumber,
            hidePageNumberLabel = footer.hidePageNumberLabel,
            hideChapter = footer.hideChapter,
            hideChapterLabel = footer.hideChapterLabel,
            hideProgress = footer.hideProgressRatio,
            hideProgressLabel = footer.hideProgressRatioLabel,
            footerAlignment = footer.footerOrientation,
            sourceOrigin = book?.sourceOrigin,
            clickActions = listOf(
                settings.clickActionTL,
                settings.clickActionTC,
                settings.clickActionTR,
                settings.clickActionML,
                settings.clickActionMC,
                settings.clickActionMR,
                settings.clickActionBL,
                settings.clickActionBC,
                settings.clickActionBR,
            ).toImmutableList(),
        )
    }

    private fun BookProgress.toMangaProgress() = MangaProgressState(
        bookName = name,
        bookAuthor = author,
        chapterIndex = durChapterIndex,
        pageIndex = durChapterPos,
        chapterTitle = durChapterTitle,
        updatedAt = durChapterTime,
    )

    private fun MangaProgressState.toBookProgress() = BookProgress(
        name = bookName,
        author = bookAuthor,
        durChapterIndex = chapterIndex,
        durChapterPos = pageIndex,
        durChapterTime = updatedAt,
        durChapterTitle = chapterTitle,
    )
}
