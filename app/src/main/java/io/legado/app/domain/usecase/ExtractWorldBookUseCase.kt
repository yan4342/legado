package io.legado.app.domain.usecase

import com.google.gson.JsonParser
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.entities.AiModelProfile
import io.legado.app.data.entities.AiProviderProfile
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.AiPromptTemplate
import io.legado.app.domain.gateway.AiPromptTemplateGateway
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.model.AiCallMeta
import io.legado.app.domain.model.AiCallSource
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.SillyTavernWorldInfoImporter
import io.legado.app.domain.usecase.ai.AiStreamPartialCallback
import io.legado.app.domain.usecase.ai.AiStreamingTextHelper
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.config.AppConfig

class ExtractWorldBookUseCase(
    private val bookChapterDao: BookChapterDao,
    private val aiTextGateway: AiTextGateway,
    private val aiProfileGateway: AiProfileGateway,
    private val promptTemplateGateway: AiPromptTemplateGateway,
    private val resolveBookChapterContentUseCase: ResolveBookChapterContentUseCase,
) {
    data class Fields(
        val writingStyle: String = "",
        val grammar: String = "",
        val plotSummary: String = "",
        val representativeDialogues: String = "",
        val representativeProse: String = "",
        val entries: List<SillyTavernWorldInfoImporter.EntryDraft> = emptyList(),
    )

    suspend fun extract(
        book: Book,
        chapterIndices: List<Int>,
        onPartial: AiStreamPartialCallback? = null,
        allowNetworkFetch: Boolean = false,
    ): Fields {
        val preset = aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER)
            ?: error("Please configure a default AI model first")
        return extractWithModel(book, chapterIndices, preset.model, onPartial, allowNetworkFetch)
    }

    suspend fun extractWithModel(
        book: Book,
        chapterIndices: List<Int>,
        modelProfileId: String,
        onPartial: AiStreamPartialCallback? = null,
        allowNetworkFetch: Boolean = false,
    ): Fields {
        val modelProfile = aiProfileGateway.getModel(modelProfileId)
            ?: error("Model not found: $modelProfileId")
        val provider = aiProfileGateway.getProvider(modelProfile.providerId)
            ?: error("Provider not found for model")
        val modelConfig = AiModelConfig(
            id = modelProfile.id,
            provider = AiProviderConfig(
                id = provider.id, name = provider.name, protocol = provider.protocol,
                baseUrl = provider.baseUrl, apiKey = provider.apiKey,
                modelsUrl = provider.modelsUrl,
                chatPath = provider.chatPath ?: "/chat/completions",
                responsesPath = provider.responsesPath ?: "/responses",
                messagesPath = provider.messagesPath ?: "/v1/messages",
                modelsPath = provider.modelsPath,
            ),
            displayName = modelProfile.displayName,
            modelId = modelProfile.modelId,
            contextWindow = modelProfile.contextWindow,
            maxOutputTokens = modelProfile.maxOutputTokens
        )
        return extractWithModel(book, chapterIndices, modelConfig, onPartial, allowNetworkFetch)
    }

    private suspend fun extractWithModel(
        book: Book,
        chapterIndices: List<Int>,
        modelConfig: AiModelConfig,
        onPartial: AiStreamPartialCallback? = null,
        allowNetworkFetch: Boolean = false,
    ): Fields {

        var fetchError: String? = null
        val chapters = chapterIndices.mapNotNull { index ->
            val chapter = bookChapterDao.getChapter(book.bookUrl, index) ?: return@mapNotNull null
            val rawContent = when (val result = resolveBookChapterContentUseCase.resolveRaw(
                book, chapter, allowNetworkFetch,
            )) {
                is ResolveBookChapterContentUseCase.Result.Success -> result.content
                is ResolveBookChapterContentUseCase.Result.Error -> {
                    fetchError = result.message
                    return@mapNotNull null
                }
            }
            val processed = ContentProcessor.get(book.name, book.origin)
                .getContent(book, chapter, rawContent, includeTitle = true)
                .toString()
                .take(MAX_CHARS_PER_CHAPTER)
            "--- Chapter ${index + 1}: ${chapter.title} ---\n$processed"
        }

        if (chapters.isEmpty()) {
            val hint = if (!AppConfig.aiAllowBookSourceFetch) {
                " Enable book source fetch in AI ability settings to download uncached chapters."
            } else if (!allowNetworkFetch) {
                " Confirm the extraction to download uncached chapters."
            } else ""
            error(
                (fetchError ?: "No chapter content available for selected chapters.") + hint,
            )
        }

        val system = io.legado.app.domain.usecase.ai.PromptRoleSplit.stripPlaceholders(
            promptTemplateGateway.getPrompt(AiPromptTemplate.WORLD_BOOK_EXTRACTION_PROMPT),
        )
        val user = chapters.joinToString("\n\n")

        val request = AiGenerateRequest(
            model = modelConfig,
            messages = io.legado.app.domain.usecase.ai.PromptRoleSplit.messages(system, user),
            params = modelConfig.defaultParams,
            callMeta = AiCallMeta(AiCallSource.WORLDBOOK),
        )

        val result = AiStreamingTextHelper.streamGenerate(aiTextGateway, request, onPartial)
        val text = result.getOrThrow()
        return parseResponse(text)
    }

    private fun parseResponse(text: String): Fields {
        // Robust JSON extraction: find the first '{' and last '}', strip markdown fences
        val jsonStr = extractJsonObject(text)
        return try {
            val obj = JsonParser.parseString(jsonStr).asJsonObject
            Fields(
                writingStyle = obj.string("writingStyle"),
                grammar = obj.string("grammar"),
                plotSummary = obj.string("plotSummary"),
                representativeDialogues = obj.string("representativeDialogues"),
                representativeProse = obj.string("representativeProse"),
                entries = parseAiEntries(obj.get("entries")),
            )
        } catch (_: Exception) {
            // If JSON parsing fails, try to extract fields with regex
            Fields(
                writingStyle = extractField(text, "writingStyle"),
                grammar = extractField(text, "grammar"),
                plotSummary = extractField(text, "plotSummary"),
                representativeDialogues = extractField(text, "representativeDialogues"),
                representativeProse = extractField(text, "representativeProse")
            ).let { fields ->
                if (fields.writingStyle.isBlank() && fields.grammar.isBlank() &&
                    fields.plotSummary.isBlank() && fields.representativeDialogues.isBlank() &&
                    fields.representativeProse.isBlank()
                ) {
                    // Complete fallback: put everything in plotSummary
                    Fields(plotSummary = text)
                } else fields
            }
        }
    }

    /** Extract JSON object from text, handling markdown fences and surrounding text. */
    private fun extractJsonObject(text: String): String {
        var t = text.trim()
        t = t.replace(Regex("""```(?:json)?\s*"""), "")
            .replace(Regex("""\s*```"""), "")
            .trim()
        val start = t.indexOf('{')
        val end = t.lastIndexOf('}')
        return if (start >= 0 && end > start) {
            t.substring(start, end + 1)
        } else t
    }

    private fun extractField(text: String, fieldName: String): String {
        val pattern = Regex(""""$fieldName"\s*:\s*"((?:[^"\\]|\\.)*)"""", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(text)?.groupValues?.getOrNull(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\n", "\n")
            .orEmpty()
    }

    companion object {
        private const val MAX_CHARS_PER_CHAPTER = 6000

        fun parseAiEntries(raw: com.google.gson.JsonElement?): List<SillyTavernWorldInfoImporter.EntryDraft> {
            if (raw == null || !raw.isJsonArray) return emptyList()
            val out = mutableListOf<SillyTavernWorldInfoImporter.EntryDraft>()
            for (el in raw.asJsonArray) {
                if (!el.isJsonObject) continue
                val obj = el.asJsonObject
                val content = obj.string("content").trim()
                if (content.isEmpty()) continue
                val keys = SillyTavernWorldInfoImporter.normalizeKeys(obj.get("keys") ?: obj.get("key"))
                    .joinToString(",")
                val name = obj.string("name").ifBlank { obj.string("comment") }.ifBlank {
                    keys.split(',').firstOrNull().orEmpty()
                }
                val constant = obj.get("constant")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
                out.add(
                    SillyTavernWorldInfoImporter.EntryDraft(
                        name = name,
                        keys = keys,
                        content = content,
                        constant = constant,
                        priority = 100,
                        enabled = true,
                    ),
                )
            }
            return out
        }
    }
}

private fun com.google.gson.JsonObject.string(name: String): String =
    get(name)?.takeIf { !it.isJsonNull }?.asString.orEmpty()
