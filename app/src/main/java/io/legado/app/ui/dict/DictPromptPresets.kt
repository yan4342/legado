package io.legado.app.ui.dict

/**
 * AI 词典的内置预设。
 * 在阅读界面的词典弹窗与规则编辑页中复用；
 * [systemPrompt]/[userPromptTemplate] 为 null 时表示使用规则自身配置。
 */
data class DictPromptPreset(
    val key: String,
    val displayName: String,
    val systemPrompt: String? = null,
    val userPromptTemplate: String? = null,
)

val DictPromptPresets = listOf(
    DictPromptPreset("default", "默认"),
    DictPromptPreset(
        key = "detail",
        displayName = "详细",
        systemPrompt = "你是一个词典助手。请用Markdown格式回复：**加粗**标注关键词、空行分隔段落。不使用代码块或表格。",
        userPromptTemplate = "请详细解释'{{word}}'的含义，包括读音、词源、用法示例和同义词。",
    ),
    DictPromptPreset(
        key = "translate",
        displayName = "翻译",
        systemPrompt = "你是一个词典助手。请用Markdown格式回复：**加粗**标注关键词、空行分隔段落。不使用代码块或表格。",
        userPromptTemplate = "请将'{{word}}'翻译并给出释义。",
    ),
    DictPromptPreset(
        key = "foreshadow",
        displayName = "前文搜索",
        systemPrompt = "你是这本小说的读者助手。请用Markdown格式回复：**加粗**标注关键词、空行分隔段落。",
        userPromptTemplate = "在《{{bookName}}》第{{chapterIndex}}章《{{chapterTitle}}》中，解释'{{word}}'是谁/是什么，并结合以下前文出现记录总结关键情节（如首次出场、重要转折、伏笔）：\n{{earlierMentions}}",
    ),
    DictPromptPreset(
        key = "fullbook",
        displayName = "全本搜索",
        systemPrompt = "你是这本小说的读者助手。请用Markdown格式回复：**加粗**标注关键词、空行分隔段落。",
        userPromptTemplate = "在《{{bookName}}》第{{chapterIndex}}章《{{chapterTitle}}》中，解释'{{word}}'是谁/是什么，并结合以下全书出现记录（每处已标注【前文】/【本章】/【后文】）总结关键情节（如首次出场、重要转折、伏笔、后续发展）：\n{{bookMentions}}",
    ),
)
