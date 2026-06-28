package io.legado.app.ui.main.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.koin.androidx.compose.koinViewModel
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.config.AppConfig
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.help.source.exploreKinds
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.common.compose.EmptyStateView
import io.legado.app.ui.common.compose.RoundDropdownMenu
import io.legado.app.ui.common.compose.RoundDropdownMenuItem
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.main.MainRoute
import io.legado.app.ui.main.MainRouteExploreShow
import io.legado.app.utils.flowWithLifecycleAndDatabaseChange
import io.legado.app.utils.startActivity
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun ExploreScreen(
    onNavigateToRoute: (MainRoute) -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel: ExploreViewModel = koinViewModel()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val sources = remember { mutableStateListOf<BookSourcePart>() }
    val groups = remember { mutableStateListOf<String>() }
    var searchKey by remember { mutableStateOf("") }
    var showGroupMenu by remember { mutableStateOf(false) }
    var expandedIndex by remember { mutableIntStateOf(-1) }
    var contextMenuSource by remember { mutableStateOf<BookSourcePart?>(null) }
    val categoriesMap = remember { mutableMapOf<String, List<ExploreKind>>() }
    var loadingCategoriesForUrl by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val onSurfaceColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onPrimary

    // 更新发现数据流
    LaunchedEffect(searchKey) {
        when {
            searchKey.isBlank() -> appDb.bookSourceDao.flowExplore()
            searchKey.startsWith("group:") -> {
                val key = searchKey.substringAfter("group:")
                appDb.bookSourceDao.flowGroupExplore(key)
            }
            else -> appDb.bookSourceDao.flowExplore(searchKey)
        }.flowWithLifecycleAndDatabaseChange(
            lifecycleOwner.lifecycle,
            androidx.lifecycle.Lifecycle.State.RESUMED,
            AppDatabase.BOOK_SOURCE_TABLE_NAME,
        ).catch {
            AppLog.put("发现界面更新数据出错", it)
        }.conflate().flowOn(IO).collect {
            sources.clear()
            sources.addAll(it)
        }
    }

    // 监听分组数据
    LaunchedEffect(Unit) {
        appDb.bookSourceDao.flowExploreGroups()
            .flowWithLifecycleAndDatabaseChange(
                lifecycleOwner.lifecycle,
                androidx.lifecycle.Lifecycle.State.RESUMED,
                AppDatabase.BOOK_SOURCE_TABLE_NAME,
            ).catch {
                AppLog.put("发现界面获取分组数据失败\n${it.localizedMessage}", it)
            }.conflate().collect {
                groups.clear()
                groups.addAll(it)
            }
    }

    // 加载展开的分类
    LaunchedEffect(expandedIndex) {
        if (expandedIndex < 0 || expandedIndex >= sources.size) return@LaunchedEffect
        val source = sources[expandedIndex]
        if (categoriesMap.containsKey(source.bookSourceUrl)) return@LaunchedEffect
        loadingCategoriesForUrl = source.bookSourceUrl
        try {
            val kinds = source.exploreKinds()
            categoriesMap[source.bookSourceUrl] = kinds
        } catch (e: Exception) {
            AppLog.put("发现界面加载分类失败\n${e.localizedMessage}", e)
        } finally {
            loadingCategoriesForUrl = null
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                modifier = Modifier.height(64.dp),
                windowInsets = WindowInsets(0.dp),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.primary,
                    titleContentColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onPrimary,
                ),
                title = {
                    TextField(
                        value = searchKey.replace("group:", ""),
                        onValueChange = { searchKey = it },
                        placeholder = { Text(stringResource(R.string.screen_find)) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        shape = RoundedCornerShape(16.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = onSurfaceColor.copy(alpha = 0.12f),
                            unfocusedContainerColor = onSurfaceColor.copy(alpha = 0.08f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = onSurfaceColor,
                            unfocusedTextColor = onSurfaceColor,
                            cursorColor = onSurfaceColor,
                            focusedLeadingIconColor = onSurfaceColor,
                            unfocusedLeadingIconColor = onSurfaceColor.copy(alpha = 0.7f),
                            focusedPlaceholderColor = onSurfaceColor.copy(alpha = 0.5f),
                            unfocusedPlaceholderColor = onSurfaceColor.copy(alpha = 0.4f),
                        ),
                        modifier = Modifier.fillMaxWidth().height(42.dp).padding(bottom = 6.dp),
                    )
                },
                actions = {
                    Box {
                        IconButton(onClick = { showGroupMenu = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_groups),
                                contentDescription = stringResource(R.string.group),
                            )
                        }
                        DropdownMenu(
                            expanded = showGroupMenu,
                            onDismissRequest = { showGroupMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.all)) },
                                onClick = {
                                    searchKey = ""
                                    showGroupMenu = false
                                },
                            )
                            groups.forEach { group ->
                                DropdownMenuItem(
                                    text = { Text(group) },
                                    onClick = {
                                        searchKey = "group:$group"
                                        showGroupMenu = false
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (sources.isEmpty()) {
            EmptyStateView(
                modifier = Modifier.fillMaxSize().padding(padding),
                message = stringResource(R.string.explore_empty),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                itemsIndexed(
                    items = sources,
                    key = { _, item -> item.bookSourceUrl },
                ) { index, source ->
                    val isExpanded = expandedIndex == index
                    val isLoading = loadingCategoriesForUrl == source.bookSourceUrl
                    val kinds = categoriesMap[source.bookSourceUrl]

                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        // 头部行
                        Box {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                expandedIndex = if (isExpanded) -1 else index
                                            },
                                            onLongClick = { contextMenuSource = source },
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = source.bookSourceName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                                Icon(
                                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown
                                    else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            } // Surface

                            // 长按上下文菜单
                            RoundDropdownMenu(
                                expanded = contextMenuSource == source,
                                onDismissRequest = { contextMenuSource = null },
                            ) { dismiss ->
                                RoundDropdownMenuItem(
                                    text = context.getString(R.string.edit),
                                    onClick = {
                                        dismiss()
                                        context.startActivity<BookSourceEditActivity> {
                                            putExtra("sourceUrl", source.bookSourceUrl)
                                        }
                                    },
                                )
                                RoundDropdownMenuItem(
                                    text = context.getString(R.string.to_top),
                                    onClick = { dismiss(); viewModel.topSource(source) },
                                )
                                if (source.hasLoginUrl) {
                                    RoundDropdownMenuItem(
                                        text = context.getString(R.string.login),
                                        onClick = {
                                            dismiss()
                                            context.startActivity<SourceLoginActivity> {
                                                putExtra("type", "bookSource")
                                                putExtra("key", source.bookSourceUrl)
                                            }
                                        },
                                    )
                                }
                                RoundDropdownMenuItem(
                                    text = context.getString(R.string.search),
                                    onClick = {
                                        dismiss()
                                        (context as? MainActivity)?.navigateToSearch(
                                            scopeRaw = SearchScope(source).toString()
                                        )
                                    },
                                )
                                RoundDropdownMenuItem(
                                    text = context.getString(R.string.refresh),
                                    onClick = {
                                        dismiss()
                                        categoriesMap.remove(source.bookSourceUrl)
                                        scope.launch {
                                            try {
                                                source.clearExploreKindsCache()
                                                if (isExpanded) {
                                                    loadingCategoriesForUrl = source.bookSourceUrl
                                                    val kinds = source.exploreKinds()
                                                    categoriesMap[source.bookSourceUrl] = kinds
                                                }
                                            } catch (_: Exception) {}
                                            loadingCategoriesForUrl = null
                                        }
                                    },
                                )
                                RoundDropdownMenuItem(
                                    text = context.getString(R.string.delete),
                                    onClick = {
                                        dismiss()
                                        context.alert(titleResource = R.string.draw) {
                                            setMessage(context.getString(R.string.sure_del) + "\n" + source.bookSourceName)
                                            noButton()
                                            yesButton { viewModel.deleteSource(source) }
                                        }
                                    },
                                )
                            }
                        }

                        // 展开的分类标签
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            if (kinds != null && kinds.isNotEmpty()) {
                                BoxWithConstraints(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                ) {
                                    val density = LocalDensity.current
                                    val spacing = 8.dp
                                    val itemPadding = 16.dp
                                    val minItemWidth = 72.dp

                                    val maxRowWidthPx = with(density) {
                                        (maxWidth - itemPadding * 2).toPx()
                                    }
                                    val itemWidthPx = with(density) { minItemWidth.toPx() }
                                    val spacingPx = with(density) { spacing.toPx() }

                                    val rows = remember(kinds, maxRowWidthPx) {
                                        buildList {
                                            var row = mutableListOf<ExploreKind>()
                                            var width = 0f

                                            kinds.forEach { kind ->
                                                val s = kind.style()

                                                if (s.layout_flexBasisPercent >= 1f || s.layout_wrapBefore) {
                                                    if (row.isNotEmpty()) {
                                                        add(row)
                                                        row = mutableListOf()
                                                        width = 0f
                                                    }
                                                    add(mutableListOf(kind))
                                                    return@forEach
                                                }

                                                val next = if (row.isEmpty()) itemWidthPx
                                                else width + spacingPx + itemWidthPx

                                                if (next > maxRowWidthPx) {
                                                    add(row)
                                                    row = mutableListOf(kind)
                                                    width = itemWidthPx
                                                } else {
                                                    row += kind
                                                    width = next
                                                }
                                            }

                                            if (row.isNotEmpty()) add(row)
                                        }
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        rows.forEach { row ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                if (row.size == 1) {
                                                    Tag(
                                                        kind = row.first(),
                                                        sourceUrl = source.bookSourceUrl,
                                                        onNavigateToRoute = onNavigateToRoute,
                                                        modifier = Modifier.fillMaxWidth(),
                                                    )
                                                } else {
                                                    row.forEach { kind ->
                                                        Tag(
                                                            kind = kind,
                                                            sourceUrl = source.bookSourceUrl,
                                                            onNavigateToRoute = onNavigateToRoute,
                                                            modifier = Modifier.weight(1f),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.Tag(
    kind: ExploreKind,
    sourceUrl: String,
    onNavigateToRoute: (MainRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = {
            if (kind.url.isNullOrBlank()) return@Surface
            if (kind.title.startsWith("ERROR:")) return@Surface
            onNavigateToRoute(MainRouteExploreShow(
                title = kind.title,
                sourceUrl = sourceUrl,
                exploreUrl = kind.url,
            ))
        },
        shape = RoundedCornerShape(16.dp),
        color = Color(0x63ACACAC),
        modifier = modifier,
    ) {
        Text(
            text = kind.title,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}
