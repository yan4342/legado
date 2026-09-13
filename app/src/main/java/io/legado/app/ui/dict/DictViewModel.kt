package io.legado.app.ui.dict

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.AiDictRule
import io.legado.app.data.entities.DictRule
import io.legado.app.domain.usecase.ChapterContentWindow
import io.legado.app.domain.usecase.SearchBookContentUseCase
import io.legado.app.help.coroutine.Coroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class DictTab {
    abstract val name: String
    abstract val enabled: Boolean
    abstract val sortNumber: Int

    data class Web(val rule: DictRule) : DictTab() {
        override val name get() = rule.name
        override val enabled get() = rule.enabled
        override val sortNumber get() = rule.sortNumber
    }

    data class Ai(val rule: AiDictRule) : DictTab() {
        override val name get() = rule.name
        override val enabled get() = rule.enabled
        override val sortNumber get() = rule.sortNumber
    }
}

/**
 * 一次词典查询的结果。[uncachedChapterCount] 仅 AI 词典且模板使用上下文变量时非零，
 * 表示预搜索跳过的未缓存网络章节数，用于提示用户是否联网补搜。
 */
data class DictSearchResult(
    val text: String,
    val uncachedChapterCount: Int = 0,
)

class DictViewModel(
    application: Application,
    private val searchBookContentUseCase: SearchBookContentUseCase,
) : BaseViewModel(application) {

    private var dictJob: Coroutine<DictSearchResult>? = null

    fun initData(onSuccess: (List<DictTab>) -> Unit) {
        execute {
            val webRules = appDb.dictRuleDao.enabled.map { DictTab.Web(it) }
            val aiRules = appDb.aiDictRuleDao.enabled.map { DictTab.Ai(it) }
            (webRules + aiRules).sortedBy { it.sortNumber }
        }.onSuccess {
            onSuccess.invoke(it)
        }
    }

    /**
     * 查询词典。AI 规则支持流式输出（[onPartial] 每收到增量文本回调一次，主线程执行）；
     * [preset] 选中预设，覆盖规则自带 prompt；[allowNetworkFetch] 允许联网补搜未缓存章节。
     */
    fun search(
        tab: DictTab,
        word: String,
        context: DictSearchContext = DictSearchContext(),
        preset: DictPromptPreset = DictPromptPresets.first(),
        allowNetworkFetch: Boolean = false,
        onPartial: (String) -> Unit = {},
        onFinally: (DictSearchResult) -> Unit,
    ) {
        dictJob?.cancel()
        dictJob = execute {
            when (tab) {
                is DictTab.Web -> DictSearchResult(tab.rule.search(word))
                is DictTab.Ai -> runAiSearch(
                    rule = tab.rule,
                    word = word,
                    context = context,
                    preset = preset,
                    allowNetworkFetch = allowNetworkFetch,
                    onPartial = onPartial,
                )
            }
        }.onSuccess {
            onFinally.invoke(it)
        }.onError {
            onFinally.invoke(DictSearchResult(it.localizedMessage ?: "ERROR"))
        }
    }

    private suspend fun runAiSearch(
        rule: AiDictRule,
        word: String,
        context: DictSearchContext,
        preset: DictPromptPreset,
        allowNetworkFetch: Boolean,
        onPartial: (String) -> Unit,
    ): DictSearchResult {
        val effectiveTemplate = preset.userPromptTemplate ?: rule.userPromptTemplate
        val outcome = if (needsContextSearch(effectiveTemplate)) {
            runPreSearch(word, context, effectiveTemplate, allowNetworkFetch)
        } else {
            null
        }
        val mentions = outcome?.let { formatMentions(it, context.chapterIndex) }
        val text = rule.searchStream(
            word = word,
            onPartial = { partial ->
                withContext(Dispatchers.Main) { onPartial(partial) }
            },
            bookName = context.bookName,
            chapterIndex = context.chapterIndex,
            chapterTitle = context.chapterTitle,
            earlierMentions = mentions,
            bookMentions = mentions,
            networkExtended = allowNetworkFetch,
            presetKey = preset.key,
            overrideSystemPrompt = preset.systemPrompt,
            overrideUserPromptTemplate = preset.userPromptTemplate,
        )
        return DictSearchResult(
            text = text,
            uncachedChapterCount = outcome?.skippedUncached ?: 0,
        )
    }

    /** 仅当模板引用上下文变量时才做预搜索，避免无谓的全文扫描。 */
    private fun needsContextSearch(template: String): Boolean =
        template.contains("{{bookName}}") ||
            template.contains("{{chapterTitle}}") ||
            template.contains("{{chapterIndex}}") ||
            template.contains("{{earlierMentions}}") ||
            template.contains("{{bookMentions}}")

    private suspend fun runPreSearch(
        word: String,
        context: DictSearchContext,
        template: String,
        allowNetworkFetch: Boolean,
    ): SearchBookContentUseCase.SearchOutcome? {
        val bookUrl = context.bookUrl ?: return null
        val book = appDb.bookDao.getBook(bookUrl) ?: return null
        // 全本搜索（{{bookMentions}}，前文+后文）不限制章节；
        // 否则只扫当前章节及之前，避免扫到阅读进度之后的内容泄露剧情。
        // chapterIndex 是 0 起章节索引，chapterLimit 是自 chapterStart 起的扫描章数。
        val fullBook = template.contains("{{bookMentions}}")
        return searchBookContentUseCase.searchHits(
            book = book,
            query = word,
            chapterStart = if (fullBook) null else 0,
            chapterLimit = if (fullBook) null else context.chapterIndex?.plus(1),
            matchMode = ChapterContentWindow.MatchMode.OR,
            hitsPerChapter = 1,
            allowNetworkFetch = allowNetworkFetch,
        )
    }

    /**
     * 分层格式化命中：
     * 第 1 层——相关度最高的 [MAX_SNIPPET_HITS] 条完整片段（按 [currentChapterIndex] 标注
     * 前文/本章/后文，无阅读进度时只列章节号）；
     * 第 2 层——剩余命中只列章节号汇总，避免大词命中几百章时撑爆 prompt。
     */
    private fun formatMentions(
        outcome: SearchBookContentUseCase.SearchOutcome,
        currentChapterIndex: Int?,
    ): String {
        val hits = outcome.hits
        if (hits.isEmpty()) return ""
        val top = hits.take(MAX_SNIPPET_HITS).joinToString("\n") { hit ->
            "- ${chapterTag(hit.chapterIndex, currentChapterIndex)}第${hit.chapterIndex + 1}章《${hit.chapterTitle}》: ${hit.snippet.take(SNIPPET_CAP)}"
        }
        val rest = hits.drop(MAX_SNIPPET_HITS)
        val tail = if (rest.isNotEmpty()) {
            val shown = rest.take(MAX_CHAPTER_LIST)
                .joinToString("、") { (it.chapterIndex + 1).toString() }
            val more = if (rest.size > MAX_CHAPTER_LIST) "…等" else ""
            "\n\n另出现在第${shown}章$more（共${outcome.matchedChapterCount}章命中）"
        } else {
            ""
        }
        return top + tail
    }

    private fun chapterTag(index: Int, currentChapterIndex: Int?): String = when (currentChapterIndex) {
        null -> ""
        else -> when {
            index < currentChapterIndex -> "【前文】"
            index == currentChapterIndex -> "【本章】"
            else -> "【后文】"
        }
    }

    companion object {
        private const val MAX_SNIPPET_HITS = 20
        private const val SNIPPET_CAP = 150
        private const val MAX_CHAPTER_LIST = 60
    }
}
