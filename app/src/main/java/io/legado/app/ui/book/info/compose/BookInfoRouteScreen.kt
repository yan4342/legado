package io.legado.app.ui.book.info.compose

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.preference.PreferenceManager
import io.legado.app.R
import io.legado.app.ui.common.compose.TransparentTopAppBarStatusBar
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.help.AppWebDav
import io.legado.app.help.book.addType
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isImage
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isWebFile
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.ui.book.audio.AudioPlayActivity
import io.legado.app.ui.book.changesource.ChangeBookSourceDialog
import io.legado.app.ui.book.info.BookInfoViewModel
import io.legado.app.ui.book.info.edit.BookInfoEditActivity
import io.legado.app.ui.book.manga.ReadMangaActivity
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.ui.book.toc.TocRouteResult
import io.legado.app.ui.book.toc.TocRouteState
import io.legado.app.ui.common.compose.LegadoAlertDialog
import io.legado.app.ui.common.compose.ModalLegadoBottomSheet
import io.legado.app.ui.common.compose.rememberLegadoColorScheme
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.widget.dialog.LegadoLogListContent
import io.legado.app.utils.GSON
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.openFileUri
import io.legado.app.utils.sendToClip
import io.legado.app.utils.shareWithQr
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.ui.ai.worldbook.AiWorldBookSheet
import io.legado.app.domain.gateway.ReadAloudCharacterGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * 完整功能版 — BookInfoComposeActivity 全部能力迁移。
 * UI 保持 BookDetailScreen 不变。
 */
private const val GENERATE_RELATIONS = 1
private const val GENERATE_WORLD_BOOK = 2
private const val GENERATE_OUTLINE = 3

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BookInfoRouteScreen(
    bookUrl: String?,
    name: String?,
    author: String?,
    coverPath: String? = null,
    origin: String? = null,
    onBack: () -> Unit,
    onReadBook: (String, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onNavigateToVoiceCasting: (String) -> Unit = {},
    onNavigateToCloudTts: (String) -> Unit = {},
    onNavigateToCharacterNetwork: (bookUrl: String, focusCharacterId: String?) -> Unit = { _, _ -> },
    onNavigateToCharacterList: (String) -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
    // 主栈形态：目录走 MainRouteToc 路由，结果经 tocRouteState 回传；null 时保持 ActivityResult 旧路径。
    tocRouteState: TocRouteState? = null,
    onOpenTocRoute: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as? AppCompatActivity
    val fragActivity = context as? FragmentActivity
    val vm: BookInfoViewModel = viewModel(context as androidx.lifecycle.ViewModelStoreOwner)
    val characterGateway: ReadAloudCharacterGateway = koinInject()
    val generateCanonicalUseCase: io.legado.app.domain.usecase.GenerateBookCanonicalUseCase = koinInject()
    var showWorldBookSheet by remember { mutableStateOf(false) }
    var worldBookHighlightId by remember { mutableStateOf<String?>(null) }
    var showOutlineSheet by remember { mutableStateOf(false) }
    var outlineSheetContent by remember { mutableStateOf("") }
    var editingCharacterId by remember { mutableStateOf<String?>(null) }

    // ── Eager book from route params (cover renders from frame 1 of transition) ──
    val eagerBook = remember(bookUrl, name, author, coverPath) {
        if (bookUrl != null) Book(
            name = name ?: "",
            author = author ?: "",
            bookUrl = bookUrl,
            origin = origin ?: "",
            customCoverUrl = coverPath,
        ) else null
    }
    var book by remember { mutableStateOf(eagerBook, neverEqualPolicy()) }
    var chapters by remember { mutableStateOf<List<BookChapter>?>(null) }
    var inShelf by remember { mutableStateOf(false) }
    // Track the active book URL — starts from route params, updates after source change
    var activeBookUrl by remember { mutableStateOf(bookUrl) }

    // ── ActivityResult launchers ──
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val tocLauncher = rememberLauncherForActivityResult(TocActivityResult()) { result ->
        result?.let { (i, p, readerLaunched) ->
            // TocActivity 已在文本书详情页直达时启动阅读器,跳过重复启动
            if (!readerLaunched) {
                vm.getBook(false)?.let { b ->
                    fragActivity?.lifecycleScope?.launch {
                        withContext(IO) { b.durChapterIndex = i; b.durChapterPos = p; appDb.bookDao.update(b) }
                        activity?.startActivity(makeReadIntent(activity, vm, b))
                    }
                }
            }
        } ?: run { if (!vm.inBookshelf) vm.delBook() }
    }

    // 主栈目录路由的结果回传（pending-holder）：TocEntry 写入后回退，此处消费并清空。
    // Selection.readerLaunched = true 时 TocScreen 已直接启动阅读器，跳过重复启动；
    // Cancelled（未选章节返回）对应旧路径的 null result —— 清理未入库的临时书。
    tocRouteState?.let { state ->
        LaunchedEffect(state.pendingResult) {
            val result = state.pendingResult ?: return@LaunchedEffect
            state.pendingResult = null
            when (result) {
                is TocRouteResult.Selection -> {
                    val book = vm.getBook(false)
                    if (!result.readerLaunched && book != null && book.bookUrl == result.bookUrl) {
                        fragActivity?.lifecycleScope?.launch {
                            withContext(IO) {
                                book.durChapterIndex = result.index
                                book.durChapterPos = result.pos
                                appDb.bookDao.update(book)
                            }
                            activity?.startActivity(makeReadIntent(activity, vm, book))
                        }
                    }
                }
                TocRouteResult.Cancelled -> if (!vm.inBookshelf) vm.delBook()
            }
        }
    }
    val readLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.upBook(Intent())
        if (it.resultCode == Activity.RESULT_OK) vm.inBookshelf = true
        if (it.resultCode == ReadBookActivity.RESULT_DELETED) onBack()
    }
    val editLauncher = rememberLauncherForActivityResult(StartActivityContract(BookInfoEditActivity::class.java)) {
        if (it.resultCode == Activity.RESULT_OK) refreshTrigger++
    }
    val srcLauncher = rememberLauncherForActivityResult(StartActivityContract(BookSourceEditActivity::class.java)) {
        if (it.resultCode != Activity.RESULT_CANCELED) vm.getBook()?.let { b ->
            vm.bookSource = appDb.bookSourceDao.getBookSource(b.origin); vm.refreshBook(b); refreshTrigger++
        }
    }

    val coroutineScope = rememberCoroutineScope()

    // ── Dialog/sheet states ──
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteIsLocal by remember { mutableStateOf(false) }
    var showGroupSheet by remember { mutableStateOf(false) }
    var currentGroupId by remember { mutableLongStateOf(0L) }
    var showAppLogSheet by remember { mutableStateOf(false) }
    var showChangeSourceAlert by remember { mutableStateOf(false) }
    // Variable dialog state
    var showVariableDialog by remember { mutableStateOf(false) }
    var variableTitle by remember { mutableStateOf("") }
    var variableKey by remember { mutableStateOf("") }
    var variableValue by remember { mutableStateOf<String?>(null) }
    var variableComment by remember { mutableStateOf("") }
    // Web file sheet state
    var showWebFileSheet by remember { mutableStateOf(false) }
    var webFileCb by remember { mutableStateOf<((Book) -> Unit)?>(null) }
    // Loading state (replaces WaitDialog)
    var isLoading by remember { mutableStateOf(false) }
    var loadingText by remember { mutableStateOf("Loading.....") }

    // 状态栏透明，透出底层背景图
    TransparentTopAppBarStatusBar()

    LaunchedEffect(bookUrl, name, author) {
        // Init full data from DB (eager book already rendered with cover)
        vm.initData(Intent().apply {
            name?.let { putExtra("name", it) }; author?.let { putExtra("author", it) }
            bookUrl?.let { putExtra("bookUrl", it) }
        })
    }

    DisposableEffect(Unit) {
        val bo = Observer<Book?> { received ->
            // 过滤旧书籍的残留 LiveData 值（bookUrl 不匹配则丢弃）
            // 换源后 activeBookUrl 会同步更新，确保新书籍的更新能通过
            if (received != null) {
                if (received.bookUrl != activeBookUrl) return@Observer
                book = received
                inShelf = vm.inBookshelf
            } else {
                book = eagerBook
                inShelf = false
            }
        }
        val co = Observer<List<BookChapter>?> { chapters = it }
        vm.bookData.observeForever(bo); vm.chapterListData.observeForever(co)
        onDispose { vm.bookData.removeObserver(bo); vm.chapterListData.removeObserver(co) }
    }
    DisposableEffect(Unit) {
        val p = PreferenceManager.getDefaultSharedPreferences(context)
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (k == PreferKey.useDefaultCover) AppConfig.useDefaultCover = p.getBoolean(PreferKey.useDefaultCover, false)
        }
        p.registerOnSharedPreferenceChangeListener(l); onDispose { p.unregisterOnSharedPreferenceChangeListener(l) }
    }
    DisposableEffect(Unit) {
        val wo = Observer<Boolean> { show ->
            isLoading = show
        }
        vm.waitDialogData.observeForever(wo); onDispose { vm.waitDialogData.removeObserver(wo) }
    }
    // Resume refresh: mimics BookInfoComposeActivity.onResume() behavior
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Centralized refresh: reloads from DAO into both LiveData and Compose state.
    // Triggered by launcher callbacks (edit/source-edit return) or resume events.
    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger == 0) return@LaunchedEffect
        vm.upEditBook()
        val url = book?.bookUrl ?: bookUrl ?: return@LaunchedEffect
        withContext(IO) {
            appDb.bookDao.getBook(url)
        }?.let { fresh -> book = fresh }
    }

    if (book != null) {
        val b = book!!
        var upd by remember(b) { mutableStateOf(b.canUpdate) }
        var spl by remember(b) { mutableStateOf(b.getSplitLongChapter()) }
        // 正典内容为空时点击"关系网/世界书/大纲"→ 确认后用 AI 生成
        var pendingGenerate by remember(b.bookUrl) { mutableStateOf<Int?>(null) }
        val castCharacters by remember(b.bookUrl) {
            combine(
                characterGateway.observeCharacterCards(b.bookUrl),
                characterGateway.observeSpeakerCharacters(b.bookUrl),
            ) { cards, speakers ->
                val roles = speakers.associate { it.id to it.role }
                cards.map { card ->
                    BookDetailCharacterUi(
                        id = card.id,
                        name = card.name,
                        subtitle = card.personality.ifBlank { card.description }.take(40),
                        dramaticRole = roles[card.id].orEmpty(),
                        avatarPath = card.avatarPath,
                    )
                }
            }
        }.collectAsStateWithLifecycle(initialValue = emptyList())

        // 正典内容一键生成(仅为空时生成):识别关系网 / 世界书 / 大纲
        val runBookGenerate: (Int) -> Unit = { type ->
            activity?.toastOnUi(R.string.generate_ai_running)
            coroutineScope.launch(IO) {
                val result = runCatching {
                    when (type) {
                        GENERATE_RELATIONS -> generateCanonicalUseCase.generateRelations(b.bookUrl)
                        GENERATE_WORLD_BOOK -> generateCanonicalUseCase.generateWorldBook(b.bookUrl)
                        GENERATE_OUTLINE -> generateCanonicalUseCase.generateOutline(b.bookUrl)
                        else -> io.legado.app.domain.usecase.GenerateBookCanonicalUseCase.Result.Failure("未知类型")
                    }
                }.getOrElse {
                    io.legado.app.domain.usecase.GenerateBookCanonicalUseCase.Result.Failure(it.message ?: "生成失败")
                }
                val msg = when (result) {
                    is io.legado.app.domain.usecase.GenerateBookCanonicalUseCase.Result.Success ->
                        activity?.getString(R.string.generate_ai_done).orEmpty()
                    is io.legado.app.domain.usecase.GenerateBookCanonicalUseCase.Result.AlreadyExists ->
                        activity?.getString(R.string.generate_ai_exists).orEmpty()
                    is io.legado.app.domain.usecase.GenerateBookCanonicalUseCase.Result.Failure ->
                        result.message
                }
                withContext(Dispatchers.Main) { activity?.toastOnUi(msg) }
            }
        }

        BookDetailScreen(
            book = b, latestChapterTitle = b.latestChapterTitle, totalChapterNum = b.totalChapterNum,
            onBack = onBack,
            onReadClick = {
                val ctx = activity ?: return@BookDetailScreen
                if (b.isWebFile) showWebAlert(vm, fragActivity) { bk ->
                    if (!vm.inBookshelf) {
                        bk.addType(BookType.notShelf)
                        vm.saveBook(bk) { vm.saveChapterList { readLauncher.launch(makeReadIntent(ctx, vm, bk)) } }
                    } else readLauncher.launch(makeReadIntent(ctx, vm, bk))
                }
                else {
                    if (!vm.inBookshelf) {
                        b.addType(BookType.notShelf)
                        vm.saveBook(b) { vm.saveChapterList { readLauncher.launch(makeReadIntent(ctx, vm, b)) } }
                    } else readLauncher.launch(makeReadIntent(ctx, vm, b))
                }
            },
            onShelfClick = {
                if (b.isWebFile) showWebAlert(vm, fragActivity)
                else vm.addToBookshelf { inShelf = true }
            },
            inBookshelf = inShelf,
            onTocClick = {
                if (chapters.isNullOrEmpty()) activity?.toastOnUi(R.string.chapter_list_empty)
                else vm.getBook()?.let { b ->
                    vm.prepareOpenChapterList {
                        if (tocRouteState != null) onOpenTocRoute(b.bookUrl)
                        else tocLauncher.launch(b.bookUrl)
                    }
                }
            },
            onEditClick = { editLauncher.launch { putExtra("bookUrl", b.bookUrl) } },
            onMenuAction = { act ->
                if (act == MENU_CAN_UPDATE) { b.canUpdate = !b.canUpdate; upd = b.canUpdate }
                if (act == MENU_SPLIT_LONG_CHAPTER) { b.setSplitLongChapter(!b.getSplitLongChapter()); spl = b.getSplitLongChapter() }
                when (act) {
                    MENU_EDIT -> editLauncher.launch { putExtra("bookUrl", b.bookUrl) }
                    MENU_SHARE -> activity?.shareWithQr("${b.bookUrl}#${GSON.toJson(b)}", b.name)
                    MENU_REFRESH -> vm.getBook()?.let { vm.refreshBook(it) }
                    MENU_LOGIN -> vm.bookSource?.let {
                        activity?.startActivity<SourceLoginActivity> { putExtra("type","bookSource"); putExtra("key",it.bookSourceUrl) }
                    }
                    MENU_TOP -> vm.topBook()
                    MENU_COPY_BOOK_URL -> activity?.sendToClip(b.bookUrl)
                    MENU_COPY_TOC_URL -> b.tocUrl?.let { activity?.sendToClip(it) }
                    MENU_CAN_UPDATE -> { if (vm.inBookshelf) vm.saveBook(b) }
                    MENU_SPLIT_LONG_CHAPTER -> vm.loadBookInfo(b, false)
                    MENU_CLEAR_CACHE -> vm.clearCache()
                    MENU_VOICE_CASTING -> onNavigateToVoiceCasting(b.bookUrl)
                    MENU_CLOUD_TTS -> onNavigateToCloudTts(b.bookUrl)
                    MENU_UPLOAD -> doUpload(b, vm, activity, coroutineScope)
                    MENU_DELETE -> {
                        deleteIsLocal = b.isLocal
                        showDeleteDialog = true
                    }
                    MENU_CHANGE_SOURCE -> showChangeSourceAlert = true
                    MENU_GROUP -> {
                        currentGroupId = b.group
                        showGroupSheet = true
                    }
                    MENU_LOG -> showAppLogSheet = true
                    MENU_SET_SOURCE_VARIABLE -> {
                        coroutineScope.launch(IO) {
                            val s = vm.bookSource ?: run {
                                withContext(Dispatchers.Main) { activity?.toastOnUi("书源不存在") }; return@launch
                            }
                            variableTitle = activity?.getString(R.string.set_source_variable) ?: ""
                            variableKey = s.getKey()
                            variableValue = s.getVariable()
                            variableComment = s.getDisplayVariableComment("源变量可在js中通过source.getVariable()获取")
                            showVariableDialog = true
                        }
                    }
                    MENU_SET_BOOK_VARIABLE -> {
                        coroutineScope.launch(IO) {
                            val s = vm.bookSource ?: run {
                                withContext(Dispatchers.Main) { activity?.toastOnUi("书源不存在") }; return@launch
                            }
                            val bk = vm.getBook() ?: return@launch
                            variableTitle = activity?.getString(R.string.set_book_variable) ?: ""
                            variableKey = bk.bookUrl
                            variableValue = bk.getCustomVariable()
                            variableComment = s.getDisplayVariableComment("""书籍变量可在js中通过book.getVariable("custom")获取""")
                            showVariableDialog = true
                        }
                    }
                }
            },
            canUpdate = upd, splitLongChapter = spl,
            isLoginVisible = !vm.bookSource?.loginUrl.isNullOrBlank(),
            isSourceVariableVisible = vm.bookSource != null,
            isBookVariableVisible = vm.bookSource != null,
            coverTransitionName = sharedCoverKey,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            sharedCoverKey = sharedCoverKey,
            characters = castCharacters,
            onCharacterClick = { characterId ->
                editingCharacterId = characterId
            },
            onOpenVoiceCasting = { onNavigateToVoiceCasting(b.bookUrl) },
            onOpenCharacterList = {
                onNavigateToCharacterList(b.bookUrl)
            },
            onOpenNetwork = {
                // 关系网:正典有内容 → 查看;为空 → 确认后用 AI 生成
                coroutineScope.launch(IO) {
                    val hasRelation = appDb.aiMemoryTableDao.getByBookUrl(b.bookUrl)
                        .any { it.canonical && it.name.contains("关系") }
                    withContext(Dispatchers.Main) {
                        if (hasRelation) {
                            val hubId = castCharacters
                                .firstOrNull { it.dramaticRole == io.legado.app.domain.model.DramaticRole.MALE_LEAD }?.id
                                ?: castCharacters
                                    .firstOrNull { it.dramaticRole == io.legado.app.domain.model.DramaticRole.FEMALE_LEAD }?.id
                            onNavigateToCharacterNetwork(b.bookUrl, hubId)
                        } else {
                            pendingGenerate = GENERATE_RELATIONS
                        }
                    }
                }
            },
            onOpenWorldBook = {
                // 世界书:正典有内容 → 查看;为空 → 确认后生成
                coroutineScope.launch(IO) {
                    val linked = appDb.aiWorldBookDao.all.firstOrNull {
                        it.bookUrl == b.bookUrl && it.canonical
                    }
                    withContext(Dispatchers.Main) {
                        if (linked != null) {
                            worldBookHighlightId = linked.id
                            showWorldBookSheet = true
                        } else {
                            pendingGenerate = GENERATE_WORLD_BOOK
                        }
                    }
                }
            },
            onOpenOutline = {
                // 大纲:正典有内容 → 查看;为空 → 确认后生成
                coroutineScope.launch(IO) {
                    val outline = appDb.aiBookOutlineDao.getByBookUrl(b.bookUrl)
                    withContext(Dispatchers.Main) {
                        if (outline != null && outline.content.isNotBlank()) {
                            outlineSheetContent = outline.content
                            showOutlineSheet = true
                        } else {
                            pendingGenerate = GENERATE_OUTLINE
                        }
                    }
                }
            },
        )

        if (showWorldBookSheet) {
            AiWorldBookSheet(
                onDismiss = {
                    showWorldBookSheet = false
                    worldBookHighlightId = null
                },
                highlightWorldBookId = worldBookHighlightId,
            )
        }

        if (showOutlineSheet) {
            ModalLegadoBottomSheet(
                show = true,
                onDismissRequest = { showOutlineSheet = false },
                title = stringResource(R.string.book_outline),
            ) {
                Text(
                    text = outlineSheetContent,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        BookDetailCharacterEditOverlay(
            characterId = editingCharacterId,
            bookUrl = b.bookUrl,
            bookName = b.name,
            bookAuthor = b.author,
            onDismiss = { editingCharacterId = null },
        )

        // 正典内容为空时确认 AI 生成
        pendingGenerate?.let { type ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { pendingGenerate = null },
                title = { androidx.compose.material3.Text(stringResource(R.string.book_generate_confirm_title)) },
                text = {
                    androidx.compose.material3.Text(
                        stringResource(
                            when (type) {
                                GENERATE_RELATIONS -> R.string.book_generate_relations_confirm
                                GENERATE_WORLD_BOOK -> R.string.book_generate_world_book_confirm
                                else -> R.string.book_generate_outline_confirm
                            },
                        ),
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            pendingGenerate = null
                            runBookGenerate(type)
                        },
                    ) {
                        androidx.compose.material3.Text(stringResource(R.string.generate))
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { pendingGenerate = null }) {
                        androidx.compose.material3.Text(
                            androidx.compose.ui.res.stringResource(android.R.string.cancel),
                        )
                    }
                },
            )
        }

        // ═══════════════════════ Compose Dialogs & Sheets ═══════════════════════

        // 删除确认
        if (showDeleteDialog) {
            val delIsLocal = deleteIsLocal
            val deleteAlert = LocalConfig.bookInfoDeleteAlert
            if (deleteAlert) {
                var deleteOriginal by remember { mutableStateOf(LocalConfig.deleteBookOriginal) }
                LegadoAlertDialog(
                    show = showDeleteDialog,
                    onDismissRequest = { showDeleteDialog = false },
                    dialogTitle = stringResource(R.string.draw),
                    content = {
                        if (delIsLocal) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = deleteOriginal,
                                    onCheckedChange = { deleteOriginal = it },
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.delete_book_file))
                            }
                        }
                    },
                    confirmText = stringResource(R.string.ok),
                    onConfirm = {
                        if (delIsLocal) LocalConfig.deleteBookOriginal = deleteOriginal
                        vm.delBook(LocalConfig.deleteBookOriginal)
                        showDeleteDialog = false
                    },
                    dismissText = stringResource(R.string.cancel),
                    onDismiss = { showDeleteDialog = false },
                )
            } else {
                // 无确认弹窗，直接删除
                LaunchedEffect(Unit) {
                    vm.delBook(LocalConfig.deleteBookOriginal)
                    showDeleteDialog = false
                }
            }
        }

        // 换源预确认弹窗
        if (showChangeSourceAlert) {
            val sourceName = appDb.bookSourceDao.getBookSource(b.origin)?.bookSourceName ?: b.originName
            val hasSource = !b.isLocal && appDb.bookSourceDao.has(b.origin)
            LegadoAlertDialog(
                show = showChangeSourceAlert,
                onDismissRequest = { showChangeSourceAlert = false },
                dialogTitle = stringResource(R.string.change_origin),
                text = sourceName.takeIf { it.isNotBlank() },
                content = {
                    if (hasSource) {
                        FilledTonalButton(
                            onClick = {
                                showChangeSourceAlert = false
                                srcLauncher.launch { putExtra("sourceUrl", b.origin) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.view_source))
                        }
                    }
                },
                confirmText = stringResource(R.string.change_origin),
                onConfirm = {
                    showChangeSourceAlert = false
                    val dialog = ChangeBookSourceDialog(b.name, b.author)
                    dialog.changeSourceCallback = object : ChangeBookSourceDialog.CallBack {
                        override val oldBook: Book? = vm.bookData.value
                        override fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
                            activeBookUrl = book.bookUrl
                            vm.changeTo(source, book, toc)
                        }
                    }
                    activity?.showDialogFragment(dialog)
                },
                dismissText = stringResource(R.string.cancel),
                onDismiss = { showChangeSourceAlert = false },
            )
        }

        // 分组选择
        if (showGroupSheet && fragActivity != null) {
            GroupSelectBottomSheet(
                show = showGroupSheet,
                initialGroupId = currentGroupId,
                activity = fragActivity,
                onDismissRequest = { showGroupSheet = false },
                onConfirm = { newGroupId ->
                    b.group = newGroupId
                    vm.saveBook(b)
                },
            )
        }

        // 应用日志
        if (showAppLogSheet) {
            LegadoLogListContent(
                onDismiss = { showAppLogSheet = false },
            )
        }

        // 变量编辑
        if (showVariableDialog) {
            var editText by remember(variableKey, showVariableDialog) { mutableStateOf(variableValue ?: "") }
            LegadoAlertDialog(
                show = showVariableDialog,
                onDismissRequest = { showVariableDialog = false },
                dialogTitle = variableTitle,
                content = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (variableComment.isNotBlank()) {
                            Text(
                                text = variableComment,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        OutlinedTextField(
                            value = editText,
                            onValueChange = { editText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("变量值") },
                            singleLine = false,
                            maxLines = 6,
                        )
                    }
                },
                confirmText = stringResource(R.string.ok),
                onConfirm = {
                    (activity as? io.legado.app.ui.widget.dialog.VariableDialog.Callback)?.setVariable(variableKey, editText)
                    showVariableDialog = false
                },
                dismissText = stringResource(R.string.cancel),
                onDismiss = { showVariableDialog = false },
            )
        }
    }

    // 加载指示器（替代 WaitDialog）
    if (isLoading) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(loadingText, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ═══════════════════════ 保留的辅助函数 ═══════════════════════

private fun makeReadIntent(ctx: android.content.Context, vm: BookInfoViewModel, b: Book): Intent {
    val cls: Class<*> = when {
        b.isAudio -> AudioPlayActivity::class.java
        !b.isLocal && b.isImage && AppConfig.showMangaUi -> ReadMangaActivity::class.java
        else -> ReadBookActivity::class.java
    }
    return Intent(ctx, cls).apply { putExtra("bookUrl", b.bookUrl); putExtra("inBookshelf", vm.inBookshelf) }
}

private fun doUpload(b: Book, vm: BookInfoViewModel, act: AppCompatActivity?, scope: kotlinx.coroutines.CoroutineScope) {
    val c = act ?: return
    scope.launch {
        vm.waitDialogData.postValue(true)
        try { AppWebDav.defaultBookWebDav?.upload(b) ?: c.toastOnUi("未配置webDav"); b.lastCheckTime = System.currentTimeMillis(); vm.saveBook(b) }
        catch (e: Exception) { c.toastOnUi(e.localizedMessage) }
        finally { vm.waitDialogData.postValue(false) }
    }
}

private fun showWebAlert(vm: BookInfoViewModel, fa: FragmentActivity?, cb: ((Book) -> Unit)? = null) {
    val a = fa ?: return; val fs = vm.webFiles
    if (fs.isEmpty()) { a.toastOnUi("Unexpected webFileData"); return }
    a.selector(R.string.download_and_import_file, fs) { _, wf, _ -> when {
        wf.isSupported -> vm.importOrDownloadWebFile<Book>(wf) { cb?.invoke(it) }
        wf.isSupportDecompress -> vm.importOrDownloadWebFile<android.net.Uri>(wf) { uri -> vm.getArchiveFilesName(uri) { ns ->
            if (ns.size == 1) vm.importArchiveBook(uri, ns[0]) { cb?.invoke(it) }
            else a.selector(R.string.import_select_book, ns) { _, n, _ -> vm.importArchiveBook(uri, n) { cb?.invoke(it) } }
        } }
        else -> a.alert(title = a.getString(R.string.draw), message = a.getString(R.string.file_not_supported, wf.name)) {
            neutralButton(R.string.open_fun) { vm.importOrDownloadWebFile<android.net.Uri>(wf) { a.openFileUri(it, "*/*") } }; noButton()
        }
    } }
}
