package io.legado.app.ui.book.readRecord

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import io.legado.app.R
import io.legado.app.domain.model.AiCallSource

enum class RecordTab { READING, AI }

enum class DisplayMode(val label: String) { SUMMARY("汇总"), LATEST("最近阅读"), BY_TIME("阅读时长") }

enum class AiUsageDisplayMode(@StringRes val labelRes: Int) {
    BY_MODEL(R.string.ai_usage_display_by_model),
    BY_SOURCE(R.string.ai_usage_display_by_source),
    RECENT(R.string.ai_usage_display_recent),
}

@Stable
data class ReadRecordUiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val books: List<BookReadRecordItem> = emptyList(),
    val totalReadTime: Long = 0L,
    val todayReadTime: Long = 0L,
    val consecutiveDays: Int = 0,
    val totalBooks: Int = 0,
    val searchKey: String? = null,
    val displayMode: DisplayMode = DisplayMode.BY_TIME,
    val enableRecord: Boolean = true,
    val summaryCovers: List<BookReadRecordItem> = emptyList(),
    val hasMore: Boolean = false,
    val selectedTab: RecordTab = RecordTab.READING,
    val aiSummary: AiUsageSummaryUi? = null,
    val aiItems: List<AiUsageItemUi> = emptyList(),
    val aiDisplayMode: AiUsageDisplayMode = AiUsageDisplayMode.BY_MODEL,
    val showClearAiConfirm: Boolean = false,
)

@Stable
data class AiUsageSourceSliceUi(
    val source: String,
    val tokens: Long,
)

@Stable
data class AiUsageSummaryUi(
    val todayTokens: Long = 0,
    val totalTokens: Long = 0,
    val todayChars: Long = 0,
    val totalChars: Long = 0,
    val consecutiveDays: Int = 0,
    val callCount: Long = 0,
    val sourceSlices: List<AiUsageSourceSliceUi> = emptyList(),
    val hasEstimated: Boolean = false,
)

@Stable
data class AiUsageItemUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val totalTokens: Long,
    val generatedChars: Long,
    val timestamp: Long = 0L,
    val source: String = "",
    val estimated: Boolean = false,
)

@Stable
data class BookReadRecordItem(
    val bookName: String, val author: String = "",
    val readTime: Long = 0L, val lastRead: Long = 0L,
    val coverPath: String? = null,
    val durChapterIndex: Int = 0,
    val durChapterTitle: String? = null,
)

sealed interface ReadRecordIntent {
    data object Load : ReadRecordIntent
    data object Refresh : ReadRecordIntent
    data class Search(val key: String?) : ReadRecordIntent
    data class SetMode(val mode: DisplayMode) : ReadRecordIntent
    data class DeleteBook(val bookName: String) : ReadRecordIntent
    data class ClickBook(val bookName: String, val author: String) : ReadRecordIntent
    data object ToggleEnableRecord : ReadRecordIntent
    data object LoadMore : ReadRecordIntent
    data class SetTab(val tab: RecordTab) : ReadRecordIntent
    data class SetAiDisplayMode(val mode: AiUsageDisplayMode) : ReadRecordIntent
    data object RequestClearAiUsage : ReadRecordIntent
    data object ConfirmClearAiUsage : ReadRecordIntent
    data object DismissClearAiConfirm : ReadRecordIntent
}

sealed interface ReadRecordEffect {
    data class ShowToast(val message: String) : ReadRecordEffect
    data class NavigateToBook(val bookName: String, val author: String) : ReadRecordEffect
    data class OpenSearch(val bookName: String) : ReadRecordEffect
}

fun aiSourceDisplayName(source: String): String = when (source) {
    AiCallSource.CHAT -> "聊天"
    AiCallSource.TITLE -> "标题生成"
    AiCallSource.COMPRESS -> "压缩上下文"
    AiCallSource.SUGGESTION -> "建议回复"
    AiCallSource.GALGAME -> "Galgame HUD"
    AiCallSource.POST_EDIT -> "后处理润色"
    AiCallSource.HELP_REPLY -> "帮写回复"
    AiCallSource.TOOL_SUBMODEL -> "工具子模型"
    AiCallSource.OUTLINE -> "大纲"
    AiCallSource.MEMORY -> "记忆表"
    AiCallSource.CHARACTER -> "角色卡"
    AiCallSource.WORLDBOOK -> "世界书"
    AiCallSource.STRUCTURED_MAINTAIN -> "结构化维护"
    else -> source.ifBlank { "未知" }
}
