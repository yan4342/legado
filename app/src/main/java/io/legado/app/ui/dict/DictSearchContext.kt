package io.legado.app.ui.dict

/**
 * 词典查询时的上下文信息，由阅读页在唤起词典弹窗时传入。
 * [earlierMentions] 在 ViewModel 预搜索前文后回填，仅 AI 词典使用。
 */
data class DictSearchContext(
    val bookUrl: String? = null,
    val bookName: String? = null,
    val chapterIndex: Int? = null,
    val chapterTitle: String? = null,
    val earlierMentions: String? = null,
)
