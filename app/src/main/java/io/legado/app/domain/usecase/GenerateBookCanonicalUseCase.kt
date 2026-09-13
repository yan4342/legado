package io.legado.app.domain.usecase

import com.google.gson.JsonParser
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.entities.AiBookOutline
import io.legado.app.data.entities.AiMemoryTable
import io.legado.app.data.entities.AiMemoryTableRow
import io.legado.app.data.entities.AiPromptTemplate
import io.legado.app.data.entities.Book
import io.legado.app.domain.gateway.AiBookOutlineGateway
import io.legado.app.domain.gateway.AiMemoryTableGateway
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiPromptTemplateGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.AiWorldBookGateway
import io.legado.app.domain.model.AiCallMeta
import io.legado.app.domain.model.AiCallSource
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.usecase.structured.MemoryTableRelationSchema
import io.legado.app.domain.usecase.structured.graph.OutlineGraphCodec
import io.legado.app.domain.usecase.structured.graph.OutlineGraphEngine
import io.legado.app.help.book.ContentProcessor
import io.legado.app.utils.GSON
import java.util.UUID

/**
 * 书详情页正典内容一键生成(仅为空时生成)。
 *
 * - [generateOutline]: 从已缓存章节生成正典大纲,写入 ai_book_outlines。
 * - [generateRelations]: 生成正典人物关系表(角色A/角色B/关系)。
 * - [generateWorldBook]: 从章节抽取正典世界书。
 *
 * 已有正典内容时返回 [Result.AlreadyExists],不覆盖。
 */
class GenerateBookCanonicalUseCase(
    private val aiProfileGateway: AiProfileGateway,
    private val aiTextGateway: AiTextGateway,
    private val promptTemplateGateway: AiPromptTemplateGateway,
    private val bookOutlineGateway: AiBookOutlineGateway,
    private val memoryTableGateway: AiMemoryTableGateway,
    private val worldBookGateway: AiWorldBookGateway,
    private val bookDao: BookDao,
    private val bookChapterDao: BookChapterDao,
    private val resolveBookChapterContentUseCase: ResolveBookChapterContentUseCase,
    private val extractWorldBookUseCase: ExtractWorldBookUseCase,
    private val importWorldInfoUseCase: ImportWorldInfoUseCase,
) {
    sealed interface Result {
        data object AlreadyExists : Result
        data object Success : Result
        data class Failure(val message: String) : Result
    }

    companion object {
        /** 书详情页一键生成的正典记忆表统一挂在哨兵会话下(不参与任何会话索引)。 */
        private const val BOOK_CANONICAL_CONVERSATION_ID = "book_canonical"
        private const val MAX_CHARS_PER_CHAPTER = 6000
        private const val MAX_CHAPTERS = 40
    }

    // ---- 大纲 ----

    suspend fun generateOutline(bookUrl: String): Result {
        val book = bookDao.getBook(bookUrl) ?: return Result.Failure("书不存在")
        val existing = bookOutlineGateway.getByBookUrl(bookUrl)
        if (existing != null && existing.content.isNotBlank()) return Result.AlreadyExists
        val chapterTexts = loadCachedChapterTexts(book, MAX_CHAPTERS)
        if (chapterTexts.isEmpty()) return Result.Failure("没有已缓存的章节内容，请先阅读部分章节")
        val preset = aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
            ?: return Result.Failure("请先配置默认 AI 模型")
        val formatSpec = promptTemplateGateway.getPrompt(AiPromptTemplate.OUTLINE_FORMAT_SPEC)
        val body = promptTemplateGateway.getPrompt(AiPromptTemplate.GENERATE_OUTLINE_PROMPT)
            .replace("{outlineFormatSpec}", formatSpec)
            .replace("{outlineHierarchyHint}", "")
            .replace("{contextHint}", "")
            .replace("{hint}", "")
        val system = io.legado.app.domain.usecase.ai.PromptRoleSplit.stripPlaceholders(body)
        val user = "以下是小说章节正文，请生成故事大纲（outline_format:2）。\n\n${chapterTexts.joinToString("\n\n")}"
        val response = aiTextGateway.generate(
            AiGenerateRequest(
                model = preset.model,
                messages = listOf(
                    AiMessage(AiMessageRole.SYSTEM, system),
                    AiMessage(AiMessageRole.USER, user),
                ),
                params = preset.params.copy(temperature = 0f),
                callMeta = AiCallMeta(AiCallSource.OUTLINE),
            ),
        ).getOrNull()?.text.orEmpty()
        val normalized = normalizeOutline(response)
            ?: return Result.Failure("生成的大纲格式无效，请重试")
        bookOutlineGateway.upsert(
            AiBookOutline(bookUrl = bookUrl, content = normalized, enabled = true),
        )
        return Result.Success
    }

    private fun normalizeOutline(raw: String): String? {
        val cleaned = raw.trim()
            .replace(Regex("""^```(?:markdown)?\s*"""), "")
            .replace(Regex("""\s*```$"""), "")
            .trim()
        val decoded = OutlineGraphCodec.decodeAiOutput(cleaned)
        val graph = decoded.graph ?: return null
        val normalized = OutlineGraphEngine.normalizeDerivedState(graph)
        return OutlineGraphCodec.encode(normalized)
    }

    // ---- 关系网 ----

    suspend fun generateRelations(bookUrl: String): Result {
        val book = bookDao.getBook(bookUrl) ?: return Result.Failure("书不存在")
        val existing = memoryTableGateway.getByBookUrl(bookUrl)
            .filter { it.canonical && (it.name.contains("关系") || MemoryTableRelationSchema.isRelationKind(it.name)) }
        if (existing.isNotEmpty()) return Result.AlreadyExists
        val chapterTexts = loadCachedChapterTexts(book, MAX_CHAPTERS)
        if (chapterTexts.isEmpty()) return Result.Failure("没有已缓存的章节内容，请先阅读部分章节")
        val preset = aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
            ?: return Result.Failure("请先配置默认 AI 模型")
        val body = promptTemplateGateway.getPrompt(AiPromptTemplate.GENERATE_ONE_KIND_TABLE_PROMPT)
        val system = io.legado.app.domain.usecase.ai.PromptRoleSplit.stripPlaceholders(body)
        val user = buildString {
            append("用途：人物关系\n")
            append("必须使用且仅使用以下列（顺序固定）：${GSON.toJson(MemoryTableRelationSchema.STANDARD_COLUMNS)}；表名必须包含「关系」\n")
            append("\n## 对话内容\n").append(chapterTexts.joinToString("\n\n"))
        }
        val response = aiTextGateway.generate(
            AiGenerateRequest(
                model = preset.model,
                messages = listOf(
                    AiMessage(AiMessageRole.SYSTEM, system),
                    AiMessage(AiMessageRole.USER, user),
                ),
                params = preset.params.copy(temperature = 0f),
                callMeta = AiCallMeta(AiCallSource.MEMORY),
            ),
        ).getOrNull()?.text.orEmpty()
        val rows = parseRelationRows(response)
        if (rows.isEmpty()) return Result.Failure("未能解析出人物关系，请重试")
        val table = AiMemoryTable(
            id = "memtable_${UUID.randomUUID().toString().replace("-", "").take(16)}",
            name = MemoryTableRelationSchema.DEFAULT_NAME,
            columns = GSON.toJson(MemoryTableRelationSchema.STANDARD_COLUMNS),
            conversationId = BOOK_CANONICAL_CONVERSATION_ID,
            bookUrl = bookUrl,
            bookName = book.name,
            bookAuthor = book.author,
            canonical = true,
            enabled = true,
        )
        memoryTableGateway.upsertTable(table)
        rows.forEachIndexed { index, data ->
            memoryTableGateway.upsertRow(
                AiMemoryTableRow(
                    id = "memrow_${UUID.randomUUID().toString().replace("-", "")}",
                    tableId = table.id,
                    rowData = GSON.toJson(data),
                    sortOrder = index,
                ),
            )
        }
        return Result.Success
    }

    private fun parseRelationRows(text: String): List<Map<String, String>> {
        val cleaned = text.trim()
            .removeSurrounding("```json", "```")
            .removeSurrounding("```", "```")
            .trim()
        val json = runCatching { JsonParser.parseString(cleaned) }.getOrNull() ?: return emptyList()
        val rowsArray = when {
            json.isJsonArray -> json.asJsonArray
            json.isJsonObject && json.asJsonObject.has("rows") -> json.asJsonObject.getAsJsonArray("rows")
            else -> return emptyList()
        }
        return rowsArray.mapNotNull { el ->
            if (!el.isJsonObject) return@mapNotNull null
            val obj = el.asJsonObject
            val map = MemoryTableRelationSchema.STANDARD_COLUMNS.mapNotNull { col ->
                val v = obj.get(col)?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty()
                if (v.isNotBlank()) col to v else null
            }.toMap()
            if (map.containsKey("角色A") && map.containsKey("角色B")) map else null
        }
    }

    // ---- 世界书 ----

    suspend fun generateWorldBook(bookUrl: String): Result {
        val book = bookDao.getBook(bookUrl) ?: return Result.Failure("书不存在")
        val existing = worldBookGateway.getEnabled().filter { it.canonical && it.bookUrl == bookUrl }
        if (existing.isNotEmpty()) return Result.AlreadyExists
        val chapters = bookChapterDao.getChapterList(bookUrl)
        if (chapters.isEmpty()) return Result.Failure("没有章节，无法生成世界书")
        val indices = chapters.indices.take(MAX_CHAPTERS).toList()
        val fields = runCatching {
            extractWorldBookUseCase.extract(book, indices, allowNetworkFetch = false)
        }.getOrElse { return Result.Failure(it.message ?: "世界书抽取失败") }
        val saved = worldBookGateway.save(
            name = "${book.name} 世界书",
            bookUrl = bookUrl,
            bookName = book.name,
            bookAuthor = book.author,
            writingStyle = fields.writingStyle,
            grammar = fields.grammar,
            plotSummary = fields.plotSummary,
            representativeDialogues = fields.representativeDialogues,
            representativeProse = fields.representativeProse,
            sourceChapterIndices = indices.joinToString(","),
            worldBookId = null,
            enabled = true,
            canonical = true,
        )
        if (fields.entries.isNotEmpty()) {
            runCatching { importWorldInfoUseCase.upsertEntryDrafts(saved.id, fields.entries) }
        }
        return Result.Success
    }

    // ---- 章节内容 ----

    private suspend fun loadCachedChapterTexts(book: Book, maxChapters: Int): List<String> {
        val chapters = bookChapterDao.getChapterList(book.bookUrl)
        var count = 0
        return chapters.mapNotNull { chapter ->
            if (count >= maxChapters) return@mapNotNull null
            val rawContent = when (
                val result = resolveBookChapterContentUseCase.resolveRaw(
                    book = book,
                    chapter = chapter,
                    allowNetworkFetch = false,
                )
            ) {
                is ResolveBookChapterContentUseCase.Result.Success -> result.content
                is ResolveBookChapterContentUseCase.Result.Error -> return@mapNotNull null
            }
            val processed = ContentProcessor.get(book.name, book.origin)
                .getContent(book, chapter, rawContent, includeTitle = true)
                .toString()
                .take(MAX_CHARS_PER_CHAPTER)
            count++
            "--- Chapter ${chapter.index + 1}: ${chapter.title} ---\n$processed"
        }
    }
}
