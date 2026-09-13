package io.legado.app.ui.dict

import android.os.Build
import android.text.Html
import android.view.textclassifier.TextClassifier
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import io.legado.app.ui.common.compose.rememberLegadoBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.koin.androidx.compose.koinViewModel
import io.legado.app.R
import io.legado.app.ui.common.compose.ModalLegadoBottomSheet
import io.legado.app.ui.widget.dialog.rememberDelayedDismiss

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictSheetScreen(
    word: String,
    onDismiss: () -> Unit,
    dictSearchContext: DictSearchContext = DictSearchContext(),
    dictViewModel: DictViewModel = koinViewModel()
) {
    val sheetState = rememberLegadoBottomSheetState(skipPartiallyExpanded = false)
    val requestDismiss = rememberDelayedDismiss(sheetState, onDismiss)

    var tabs by remember { mutableStateOf<List<DictTab>>(emptyList()) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var uncachedCount by remember { mutableIntStateOf(0) }
    var fetchingNetwork by remember { mutableStateOf(false) }
    var streamingText by remember { mutableStateOf("") }
    var selectedPresetKey by remember { mutableStateOf("default") }

    val selectedTab: DictTab? = tabs.getOrNull(selectedIndex)

    fun currentPreset(): DictPromptPreset =
        DictPromptPresets.firstOrNull { it.key == selectedPresetKey } ?: DictPromptPresets.first()

    fun doSearch(index: Int, preset: DictPromptPreset, useNetwork: Boolean = false) {
        val tab = tabs.getOrNull(index) ?: return
        selectedIndex = index
        selectedPresetKey = preset.key
        isLoading = true
        error = null
        result = ""
        streamingText = ""
        uncachedCount = 0
        val onResult: (DictSearchResult) -> Unit = { r ->
            isLoading = false
            streamingText = ""
            result = r.text
            uncachedCount = r.uncachedChapterCount
        }
        dictViewModel.search(
            tab = tab,
            word = word,
            context = dictSearchContext,
            preset = preset,
            allowNetworkFetch = useNetwork,
            onPartial = { partial ->
                streamingText = partial
            },
            onFinally = onResult,
        )
    }

    fun onTabSelected(index: Int) {
        if (index == selectedIndex && !isLoading) return
        selectedIndex = index
        // 网页词典保持"点标签即搜"，AI 词典等待用户选择预设
        val tab = tabs.getOrNull(index)
        if (tab is DictTab.Web) {
            doSearch(index, currentPreset())
        } else {
            isLoading = false
            error = null
            result = ""
            streamingText = ""
            uncachedCount = 0
        }
    }

    LaunchedEffect(Unit) {
        dictViewModel.initData { loadedTabs ->
            tabs = loadedTabs
            if (loadedTabs.isNotEmpty()) {
                // 首标签若是网页词典，沿用旧行为自动搜索；AI 词典则不自动搜索
                if (loadedTabs.first() is DictTab.Web) {
                    doSearch(0, currentPreset())
                }
            }
        }
    }

    ModalLegadoBottomSheet(
        show = true,
        onDismissRequest = requestDismiss,
        sheetState = sheetState,
        title = word,
    ) {
        if (tabs.isEmpty()) {
            Text(
                text = "没有可用的词典规则",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Tab row
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(tabs.size) { index ->
                        val tab = tabs[index]
                        FilterChip(
                            selected = index == selectedIndex,
                            onClick = { onTabSelected(index) },
                            label = { Text(tab.name) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(
                                        when (tab) {
                                            is DictTab.Web -> R.drawable.ic_translate
                                            is DictTab.Ai -> R.drawable.ic_web_outline
                                        }
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }

                // AI 词典：预设选择行，点击预设才开始搜索
                if (selectedTab is DictTab.Ai) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(DictPromptPresets) { preset ->
                                FilterChip(
                                    selected = selectedPresetKey == preset.key,
                                    onClick = { doSearch(selectedIndex, preset) },
                                    label = { Text(preset.displayName) },
                                )
                            }
                        }
                        if (!isLoading && result.isBlank() && error == null) {
                            Text(
                                text = "选择预设开始查询",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // 未缓存网络章节提示 + 联网补搜
                if (uncachedCount > 0 && !fetchingNetwork) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "检测到 $uncachedCount 章未缓存",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = { doSearch(selectedIndex, currentPreset(), useNetwork = true) },
                            enabled = !isLoading,
                        ) {
                            Text("联网搜索更多")
                        }
                    }
                }

                HorizontalDivider(
                    color = LocalContentColor.current.copy(alpha = 0.15f),
                )

                // Content area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(bottom = 8.dp),
                ) {
                    val streamScrollState = rememberScrollState()
                    when {
                        isLoading && streamingText.isBlank() -> Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator()
                            if (fetchingNetwork) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "正在联网搜索未缓存章节...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        isLoading && streamingText.isNotBlank() -> Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(streamScrollState),
                        ) {
                            // 流式输出中：以轻量 markdown→html 方式渐进渲染
                            AndroidView(
                                factory = { ctx ->
                                    TextView(ctx).apply {
                                        setTextIsSelectable(true)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            setTextClassifier(TextClassifier.NO_OP)
                                        }
                                    }
                                },
                                update = { tv ->
                                    @Suppress("DEPRECATION")
                                    tv.text = Html.fromHtml(partialToHtml(streamingText), 0)
                                },
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    text = "正在生成...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        error != null -> Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = error!!,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(4.dp))
                            TextButton(onClick = { doSearch(selectedIndex, currentPreset()) }) {
                                Text("重试")
                            }
                        }

                        result.isNotBlank() -> {
                            val scrollState = rememberScrollState()
                            Column(
                                modifier = Modifier.verticalScroll(scrollState),
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        TextView(ctx).apply {
                                            setTextIsSelectable(true)
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                setTextClassifier(TextClassifier.NO_OP)
                                            }
                                        }
                                    },
                                    update = { tv ->
                                        @Suppress("DEPRECATION")
                                        tv.text = Html.fromHtml(result, 0)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 流式期间按未完成的 Markdown 渐进渲染（最终结果仍由 [AiDictRule] 统一转换）。
 */
private fun partialToHtml(text: String): String {
    return text
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
        .replace(Regex("\\*(.+?)\\*"), "<i>$1</i>")
        .replace("\n\n", "<br><br>")
        .replace("\n", "<br>")
}
