package io.legado.app.ui.main.rss

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.koin.androidx.compose.koinViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssSource
import io.legado.app.help.coil.LegadoFetcher
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.common.compose.EmptyStateView
import io.legado.app.ui.common.compose.RoundDropdownMenu
import io.legado.app.ui.common.compose.RoundDropdownMenuItem
import io.legado.app.ui.main.MainRoute
import io.legado.app.ui.rss.article.RssSortActivity
import io.legado.app.ui.rss.favorites.RssFavoritesActivity
import io.legado.app.ui.rss.read.ReadRssActivity
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.ui.rss.source.manage.RssSourceActivity
import io.legado.app.ui.rss.subscription.RuleSubActivity
import io.legado.app.utils.flowWithLifecycleAndDatabaseChange
import io.legado.app.utils.openUrl
import io.legado.app.utils.startActivity
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RssScreen(
    onNavigateToRoute: (MainRoute) -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel: RssViewModel = koinViewModel()
    val lifecycleOwner = LocalLifecycleOwner.current

    val sources = remember { mutableStateListOf<RssSource>() }
    val groups = remember { mutableStateListOf<String>() }
    var searchKey by remember { mutableStateOf("") }
    var showGroupMenu by remember { mutableStateOf(false) }
    var contextMenuSource by remember { mutableStateOf<RssSource?>(null) }

    val onSurfaceColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onPrimary

    // 更新RSS数据流
    LaunchedEffect(searchKey) {
        when {
            searchKey.isBlank() -> appDb.rssSourceDao.flowEnabled()
            searchKey.startsWith("group:") -> {
                val key = searchKey.substringAfter("group:")
                appDb.rssSourceDao.flowEnabledByGroup(key)
            }
            else -> appDb.rssSourceDao.flowEnabled(searchKey)
        }.flowWithLifecycleAndDatabaseChange(
            lifecycleOwner.lifecycle,
            androidx.lifecycle.Lifecycle.State.RESUMED,
            AppDatabase.RSS_SOURCE_TABLE_NAME,
        ).catch {
            AppLog.put("订阅界面更新数据出错", it)
        }.flowOn(IO).conflate().collect {
            sources.clear()
            sources.addAll(it)
        }
    }

    // 监听分组数据
    LaunchedEffect(Unit) {
        appDb.rssSourceDao.flowEnabledGroups()
            .flowWithLifecycleAndDatabaseChange(
                lifecycleOwner.lifecycle,
                androidx.lifecycle.Lifecycle.State.RESUMED,
                AppDatabase.RSS_SOURCE_TABLE_NAME,
            ).catch {
                AppLog.put("订阅界面获取分组数据失败\n${it.localizedMessage}", it)
            }.conflate().collect {
                groups.clear()
                groups.addAll(it)
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
                    titleContentColor = onSurfaceColor,
                    actionIconContentColor = onSurfaceColor,
                ),
                title = {
                    TextField(
                        value = searchKey.replace("group:", ""),
                        onValueChange = { searchKey = it },
                        placeholder = { Text(stringResource(R.string.rss)) },
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
                    // 收藏夹
                    IconButton(onClick = { context.startActivity<RssFavoritesActivity>() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_star),
                            contentDescription = stringResource(R.string.favorite),
                        )
                    }
                    // 分组筛选
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
                    // 设置
                    IconButton(onClick = { context.startActivity<RssSourceActivity>() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.setting),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (sources.isEmpty()) {
            EmptyStateView(
                modifier = Modifier.fillMaxSize().padding(padding),
                message = stringResource(R.string.rss_source_empty),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 头部：规则订阅
                item(span = { GridItemSpan(4) }) {
                    Card(
                        onClick = { context.startActivity<RuleSubActivity>() },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.rule_subscription),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }

                items(sources, key = { it.sourceUrl }) { source ->
                    Box {
                        Card(
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    if (source.singleUrl) {
                                        viewModel.getSingleUrl(source) { url ->
                                            if (url.startsWith("http", true)) {
                                                context.startActivity<ReadRssActivity> {
                                                    putExtra("title", source.sourceName)
                                                    putExtra("origin", url)
                                                }
                                            } else {
                                                context.openUrl(url)
                                            }
                                        }
                                    } else {
                                        context.startActivity<RssSortActivity> {
                                            putExtra("url", source.sourceUrl)
                                        }
                                    }
                                },
                                onLongClick = { contextMenuSource = source },
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (AppConfig.isEInkMode) MaterialTheme.colorScheme.surface
                                else MaterialTheme.colorScheme.surfaceContainer,
                            ),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(source.sourceIcon)
                                        .apply {
                                            extras[LegadoFetcher.sourceOriginKey] = source.sourceUrl
                                        }
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.size(50.dp),
                                    placeholder = painterResource(R.drawable.image_rss),
                                    error = painterResource(R.drawable.image_rss),
                                )
                                Text(
                                    text = source.sourceName,
                                    style = MaterialTheme.typography.labelSmall,
                                    minLines = 2,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }

                        RoundDropdownMenu(
                            expanded = contextMenuSource == source,
                            onDismissRequest = { contextMenuSource = null },
                        ) { dismiss ->
                            RoundDropdownMenuItem(
                                text = context.getString(R.string.to_top),
                                onClick = { dismiss(); viewModel.topSource(source) },
                            )
                            RoundDropdownMenuItem(
                                text = context.getString(R.string.edit),
                                onClick = {
                                    dismiss()
                                    context.startActivity<RssSourceEditActivity> {
                                        putExtra("sourceUrl", source.sourceUrl)
                                    }
                                },
                            )
                            RoundDropdownMenuItem(
                                text = context.getString(R.string.disable_source),
                                onClick = { dismiss(); viewModel.disable(source) },
                            )
                            RoundDropdownMenuItem(
                                text = context.getString(R.string.delete),
                                onClick = {
                                    dismiss()
                                    context.alert(titleResource = R.string.draw) {
                                        setMessage(context.getString(R.string.sure_del) + "\n" + source.sourceName)
                                        noButton()
                                        yesButton { viewModel.del(source) }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
