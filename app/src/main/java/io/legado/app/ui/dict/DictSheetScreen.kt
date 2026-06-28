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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
    dictViewModel: DictViewModel = koinViewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val requestDismiss = rememberDelayedDismiss(sheetState, onDismiss)

    var tabs by remember { mutableStateOf<List<DictTab>>(emptyList()) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun doSearch(index: Int) {
        if (index !in tabs.indices) return
        selectedIndex = index
        isLoading = true
        error = null
        result = ""
        dictViewModel.search(tabs[index], word) { r ->
            isLoading = false
            result = r
        }
    }

    LaunchedEffect(Unit) {
        dictViewModel.initData { loadedTabs ->
            tabs = loadedTabs
            if (loadedTabs.isNotEmpty()) {
                doSearch(0)
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
            // Tab row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tabs.size) { index ->
                    val tab = tabs[index]
                    FilterChip(
                        selected = index == selectedIndex,
                        onClick = { doSearch(index) },
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

            Spacer(Modifier.height(12.dp))

            // Content area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(horizontal = 16.dp),
            ) {
                when {
                    isLoading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )

                    error != null -> Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = error!!,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        TextButton(onClick = { doSearch(selectedIndex) }) {
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

        Spacer(Modifier.height(16.dp))
    }
}
