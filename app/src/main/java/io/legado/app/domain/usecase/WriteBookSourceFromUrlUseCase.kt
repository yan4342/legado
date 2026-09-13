package io.legado.app.domain.usecase

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.BookInfoRule
import io.legado.app.data.entities.rule.ContentRule
import io.legado.app.data.entities.rule.ExploreRule
import io.legado.app.data.entities.rule.SearchRule
import io.legado.app.data.entities.rule.TocRule
import io.legado.app.help.config.AppConfig
import io.legado.app.help.source.SourceHelp
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isAbsUrl
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Write-source flow: MVP requires search → info → toc → content.
 * Explore (discovery) is optional but fully supported when exploreUrl / ruleExplore are provided.
 */
class WriteBookSourceFromUrlUseCase(
    private val versionService: BookSourceVersionService,
    private val checkUseCase: CheckBookSourceUseCase,
) {

    suspend fun execute(
        args: JsonObject,
        toolCallId: String?,
        batchId: String?,
        conversationId: String?,
        bookshelfAccessApproved: Boolean,
    ): String {
        val action = args.string("action")?.trim()?.lowercase().orEmpty().ifBlank { "upsert" }
        return when (action) {
            "upsert", "draft" -> upsertDraft(args, toolCallId, batchId, conversationId, bookshelfAccessApproved)
            "check" -> {
                networkGate(bookshelfAccessApproved)?.let { return it }
                checkDraft(args)
            }
            "commit" -> {
                networkGate(bookshelfAccessApproved)?.let { return it }
                commit(args, toolCallId, batchId, conversationId)
            }
            else -> """{"error":"Invalid action: use upsert|check|commit"}"""
        }
    }

    private fun networkGate(bookshelfAccessApproved: Boolean): String? {
        if (!AppConfig.aiAllowBookSourceFetch) {
            return """{"error":"Book-source network fetch is disabled in AI ability settings"}"""
        }
        if (!bookshelfAccessApproved) {
            return """{"error":"User confirmation required for network write-source"}"""
        }
        return null
    }

    private suspend fun upsertDraft(
        args: JsonObject,
        toolCallId: String?,
        batchId: String?,
        conversationId: String?,
        bookshelfAccessApproved: Boolean,
    ): String {
        val detailUrl = args.string("detailUrl")?.trim().orEmpty()
        val searchUrlArg = args.string("searchUrl")?.trim().orEmpty()
        val searchResultUrl = args.string("searchResultUrl")?.trim().orEmpty()
        val bookSourceUrl = args.string("bookSourceUrl")?.trim().orEmpty()
            .ifBlank { inferBaseUrl(detailUrl.ifBlank { searchUrlArg.ifBlank { searchResultUrl } }) }

        if (bookSourceUrl.isBlank()) {
            return """{"error":"bookSourceUrl or detailUrl/searchUrl is required"}"""
        }
        if (detailUrl.isBlank() && searchUrlArg.isBlank() && searchResultUrl.isBlank() &&
            args.string("ruleSearch").isNullOrBlank() &&
            args.string("ruleBookInfo").isNullOrBlank()
        ) {
            return """{"error":"Provide detailUrl plus searchUrl or searchResultUrl (or rule patches)"}"""
        }

        val existing = appDb.bookSourceDao.getBookSource(bookSourceUrl)
        val source = existing?.copy() ?: BookSource(
            bookSourceUrl = bookSourceUrl,
            bookSourceName = args.string("bookSourceName")?.trim().orEmpty()
                .ifBlank { hostName(bookSourceUrl) },
            enabled = false,
            enabledExplore = false,
        )

        args.string("bookSourceName")?.trim()?.takeIf { it.isNotBlank() }?.let {
            source.bookSourceName = it
        }
        if (args.get("bookSourceType") != null) {
            source.bookSourceType = args.int("bookSourceType", source.bookSourceType).coerceIn(0, 3)
        }

        if (detailUrl.isNotBlank()) {
            if (source.bookUrlPattern.isNullOrBlank()) {
                source.bookUrlPattern = guessBookUrlPattern(detailUrl)
            }
        }
        args.string("bookUrlPattern")?.trim()?.takeIf { it.isNotBlank() }?.let {
            source.bookUrlPattern = it
        }

        val inferredSearch = when {
            searchUrlArg.isNotBlank() -> searchUrlArg
            searchResultUrl.isNotBlank() -> inferSearchUrlTemplate(searchResultUrl)
            else -> null
        }
        if (!inferredSearch.isNullOrBlank()) {
            source.searchUrl = inferredSearch
        }
        args.string("searchUrl")?.trim()?.takeIf { it.isNotBlank() }?.let {
            source.searchUrl = it
        }

        applyRuleJson<SearchRule>(args, "ruleSearch") { source.ruleSearch = it }
        applyRuleJson<ExploreRule>(args, "ruleExplore") { source.ruleExplore = it }
        applyRuleJson<BookInfoRule>(args, "ruleBookInfo") { source.ruleBookInfo = it }
        applyRuleJson<TocRule>(args, "ruleToc") { source.ruleToc = it }
        applyRuleJson<ContentRule>(args, "ruleContent") { source.ruleContent = it }
        args.string("header")?.let { source.header = it }
        args.string("exploreUrl")?.trim()?.takeIf { it.isNotBlank() }?.let {
            if (existing?.exploreUrl != it) {
                existing?.clearExploreKindsCache()
            }
            source.exploreUrl = it
        }
        args.string("exploreScreen")?.let { source.exploreScreen = it }
        args.string("enabledExplore")?.let {
            source.enabledExplore = it.toBooleanStrictOrNull() ?: (it != "0")
        }

        // Nested shorthand fields
        applyNested(args, source)

        val stage = args.string("stage")?.trim()?.lowercase()
        val missing = missingForStage(source, stage)
        if (missing.isNotEmpty() && stage in setOf("search", "explore", "info", "toc", "content", "all")) {
            return GSON.toJson(
                mapOf(
                    "success" to false,
                    "error" to "Missing fields for stage=$stage",
                    "missing" to missing,
                    "hint" to "Use ask_user_questions if search clues are unclear. MVP requires searchUrl + ruleSearch.",
                    "draft" to draftSummary(source),
                ),
            )
        }

        val snapshotId = versionService.captureBeforeChange(
            source.bookSourceUrl,
            BookSourceVersionService.SOURCE_WRITE,
            BookSourceVersionService.CaptureMeta(toolCallId, batchId, conversationId, "write upsert"),
        )
        source.lastUpdateTime = System.currentTimeMillis()
        // Keep drafts disabled until commit
        if (existing == null) source.enabled = false
        SourceHelp.insertBookSource(source)

        val autoCheck = args.bool("check", stage == "all")
        val checkResult = if (autoCheck) {
            networkGate(bookshelfAccessApproved)?.let { return it }
            val withDiscovery = args.bool("checkDiscovery", !source.exploreUrl.isNullOrBlank())
            checkUseCase.check(
                source.bookSourceUrl,
                CheckBookSourceUseCase.mvpWriteOptions(persist = true)
                    .copy(checkDiscovery = withDiscovery),
            )
        } else null

        return GSON.toJson(
            mapOf(
                "success" to true,
                "action" to "upsert",
                "bookSourceUrl" to source.bookSourceUrl,
                "snapshotId" to snapshotId,
                "draft" to draftSummary(source),
                "missingForMvp" to missingForMvp(source),
                "check" to checkResult?.let { JsonParser.parseString(it.toJson()) },
                "next" to nextHint(source, checkResult),
            ),
        )
    }

    private suspend fun checkDraft(args: JsonObject): String {
        val url = args.string("bookSourceUrl").orEmpty().trim()
        if (url.isBlank()) return """{"error":"bookSourceUrl is required"}"""
        val source = appDb.bookSourceDao.getBookSource(url)
        val withDiscovery = args.bool("checkDiscovery", !source?.exploreUrl.isNullOrBlank())
        val result = checkUseCase.check(
            url,
            CheckBookSourceUseCase.mvpWriteOptions(persist = true).copy(checkDiscovery = withDiscovery),
        )
        return GSON.toJson(
            mapOf(
                "success" to result.success,
                "action" to "check",
                "canCommit" to (
                    source != null &&
                        missingForMvp(source).isEmpty() &&
                        result.allRequiredOk(requireSearch = true)
                    ),
                "missingForMvp" to (source?.let { missingForMvp(it) } ?: listOf("source missing")),
                "missingForExplore" to (source?.let { missingForExplore(it) } ?: emptyList()),
                "check" to JsonParser.parseString(result.toJson()),
            ),
        )
    }

    private suspend fun commit(
        args: JsonObject,
        toolCallId: String?,
        batchId: String?,
        conversationId: String?,
    ): String {
        val url = args.string("bookSourceUrl").orEmpty().trim()
        if (url.isBlank()) return """{"error":"bookSourceUrl is required"}"""
        val source = appDb.bookSourceDao.getBookSource(url)
            ?: return """{"error":"Book source not found: $url"}"""
        val missing = missingForMvp(source)
        if (missing.isNotEmpty()) {
            return GSON.toJson(
                mapOf(
                    "success" to false,
                    "error" to "Cannot commit: MVP requires search→info→toc→content rules",
                    "missingForMvp" to missing,
                ),
            )
        }
        val check = checkUseCase.check(
            url,
            // Discovery is optional and must not block MVP commit when explore is broken.
            CheckBookSourceUseCase.mvpWriteOptions(persist = true).copy(
                checkDiscovery = args.bool("checkDiscovery", false),
            ),
        )
        if (!check.allRequiredOk(requireSearch = true)) {
            return GSON.toJson(
                mapOf(
                    "success" to false,
                    "error" to "Cannot commit until search/info/toc/content all pass",
                    "check" to JsonParser.parseString(check.toJson()),
                ),
            )
        }
        val snapshotId = versionService.captureBeforeChange(
            url,
            BookSourceVersionService.SOURCE_COMMIT,
            BookSourceVersionService.CaptureMeta(toolCallId, batchId, conversationId, "commit"),
        )
        // Default enabled=false unless explicitly requested
        source.enabled = args.bool("enabled", false)
        source.lastUpdateTime = System.currentTimeMillis()
        SourceHelp.insertBookSource(source)
        return GSON.toJson(
            mapOf(
                "success" to true,
                "action" to "commit",
                "bookSourceUrl" to source.bookSourceUrl,
                "enabled" to source.enabled,
                "snapshotId" to snapshotId,
                "check" to JsonParser.parseString(check.toJson()),
                "message" to "Committed book source (enabled=${source.enabled})",
            ),
        )
    }

    private inline fun <reified T> applyRuleJson(
        args: JsonObject,
        key: String,
        set: (T) -> Unit,
    ) {
        val raw = args.string(key) ?: args.get(key)?.takeIf { it.isJsonObject }?.toString()
        if (raw.isNullOrBlank()) return
        GSON.fromJsonObject<T>(raw).getOrNull()?.let(set)
    }

    private fun applyNested(args: JsonObject, source: BookSource) {
        // Convenience: allow ruleSearchBookList etc. via nested object fields already covered.
        args.string("searchBookList")?.let {
            source.getSearchRule().bookList = it
        }
        args.string("searchName")?.let { source.getSearchRule().name = it }
        args.string("searchAuthor")?.let { source.getSearchRule().author = it }
        args.string("searchBookUrl")?.let { source.getSearchRule().bookUrl = it }
        args.string("exploreBookList")?.let { source.getExploreRule().bookList = it }
        args.string("exploreName")?.let { source.getExploreRule().name = it }
        args.string("exploreAuthor")?.let { source.getExploreRule().author = it }
        args.string("exploreBookUrl")?.let { source.getExploreRule().bookUrl = it }
        args.string("infoName")?.let { source.getBookInfoRule().name = it }
        args.string("infoAuthor")?.let { source.getBookInfoRule().author = it }
        args.string("infoTocUrl")?.let { source.getBookInfoRule().tocUrl = it }
        args.string("tocChapterList")?.let { source.getTocRule().chapterList = it }
        args.string("tocChapterName")?.let { source.getTocRule().chapterName = it }
        args.string("tocChapterUrl")?.let { source.getTocRule().chapterUrl = it }
        args.string("contentRule")?.let { source.getContentRule().content = it }
    }

    private fun missingForStage(source: BookSource, stage: String?): List<String> {
        return when (stage) {
            "search" -> buildList {
                if (source.searchUrl.isNullOrBlank()) add("searchUrl")
                if (source.ruleSearch?.bookList.isNullOrBlank()) add("ruleSearch.bookList")
            }
            "explore" -> missingForExplore(source)
            "info" -> buildList {
                if (!hasUsableBookInfoRule(source.ruleBookInfo)) add("ruleBookInfo")
            }
            "toc" -> buildList {
                if (source.ruleToc?.chapterList.isNullOrBlank()) add("ruleToc.chapterList")
            }
            "content" -> buildList {
                if (source.ruleContent?.content.isNullOrBlank()) add("ruleContent.content")
            }
            "all" -> missingForMvp(source)
            else -> emptyList()
        }
    }

    private fun missingForExplore(source: BookSource): List<String> = buildList {
        if (source.exploreUrl.isNullOrBlank()) add("exploreUrl")
        if (source.ruleExplore?.bookList.isNullOrBlank() &&
            source.ruleSearch?.bookList.isNullOrBlank()
        ) {
            // explore falls back to search bookList when ruleExplore.bookList blank
            add("ruleExplore.bookList (or ruleSearch.bookList fallback)")
        }
    }

    private fun missingForMvp(source: BookSource): List<String> = buildList {
        if (source.searchUrl.isNullOrBlank()) add("searchUrl")
        if (source.ruleSearch?.bookList.isNullOrBlank()) add("ruleSearch.bookList")
        if (!hasUsableBookInfoRule(source.ruleBookInfo)) add("ruleBookInfo")
        if (source.ruleToc?.chapterList.isNullOrBlank()) add("ruleToc.chapterList")
        if (source.ruleContent?.content.isNullOrBlank()) add("ruleContent.content")
    }

    private fun draftSummary(source: BookSource): Map<String, Any?> = mapOf(
        "bookSourceUrl" to source.bookSourceUrl,
        "bookSourceName" to source.bookSourceName,
        "bookSourceType" to source.bookSourceType,
        "enabled" to source.enabled,
        "enabledExplore" to source.enabledExplore,
        "searchUrl" to source.searchUrl,
        "exploreUrl" to source.exploreUrl,
        "bookUrlPattern" to source.bookUrlPattern,
        "hasRuleSearch" to (source.ruleSearch != null),
        "hasRuleExplore" to (source.ruleExplore != null),
        "hasRuleBookInfo" to hasUsableBookInfoRule(source.ruleBookInfo),
        "hasRuleToc" to (source.ruleToc != null),
        "hasRuleContent" to (source.ruleContent != null),
        "missingForExplore" to missingForExplore(source),
    )

    private fun nextHint(source: BookSource, check: CheckBookSourceUseCase.Result?): String {
        val missing = missingForMvp(source)
        if (missing.isNotEmpty()) {
            return "Fill missing: ${missing.joinToString()}. Then action=check, then action=commit."
        }
        if (check == null) return "Call action=check (four stages). If all green, action=commit."
        if (check.success) return "All stages green. Call action=commit (enabled defaults to false)."
        return "Fix failing stages via patch_book_source / fetch_page_snippet / search_rule_help, then re-check."
    }

    companion object {
        /** Empty `{}` is not enough — need at least one usable info field (or init). */
        fun hasUsableBookInfoRule(rule: BookInfoRule?): Boolean {
            if (rule == null) return false
            return !rule.init.isNullOrBlank() ||
                !rule.name.isNullOrBlank() ||
                !rule.author.isNullOrBlank() ||
                !rule.tocUrl.isNullOrBlank() ||
                !rule.intro.isNullOrBlank() ||
                !rule.coverUrl.isNullOrBlank() ||
                !rule.kind.isNullOrBlank() ||
                !rule.lastChapter.isNullOrBlank()
        }

        fun inferBaseUrl(url: String): String {
            if (url.isBlank()) return ""
            return runCatching {
                val u = URL(url)
                "${u.protocol}://${u.host}"
            }.getOrDefault(url)
        }

        fun hostName(url: String): String =
            runCatching { URL(url).host }.getOrDefault(url).take(40)

        fun guessBookUrlPattern(detailUrl: String): String {
            return runCatching {
                val u = URL(detailUrl)
                val path = u.path
                // Replace trailing numeric/id segment with regex
                val patterned = path.replace(Regex("/\\d+(/)?$"), "/\\\\d+\$1")
                    .replace(Regex("/[A-Za-z0-9_-]{6,}(/)?$")) { "/[\\\\w-]+${it.groupValues.getOrNull(1).orEmpty()}" }
                "^${Regex.escape(u.protocol + "://" + u.host)}$patterned"
                    .replace("\\Q", "").replace("\\E", "")
                    .let { ".*${u.host}.*" } // keep simple & usable
            }.getOrDefault(".*")
        }

        /**
         * Reverse a search-result URL that already contains the keyword into a {{key}} template.
         */
        fun inferSearchUrlTemplate(resultUrl: String): String? {
            if (!resultUrl.isAbsUrl()) return null
            return runCatching {
                val u = URL(resultUrl)
                val query = u.query.orEmpty()
                if (query.isNotBlank()) {
                    val pairs = query.split('&').mapNotNull { part ->
                        val idx = part.indexOf('=')
                        if (idx <= 0) return@mapNotNull null
                        val k = part.substring(0, idx)
                        val v = URLDecoder.decode(part.substring(idx + 1), StandardCharsets.UTF_8)
                        k to v
                    }
                    // Prefer common search param names with non-blank values
                    val keyParam = listOf("q", "key", "keyword", "search", "wd", "searchkey", "searchKey")
                        .firstOrNull { name -> pairs.any { it.first.equals(name, true) && it.second.isNotBlank() } }
                        ?: pairs.firstOrNull { it.second.isNotBlank() && it.second.length in 1..40 }?.first
                    if (keyParam != null) {
                        val rebuilt = pairs.joinToString("&") { (k, v) ->
                            if (k.equals(keyParam, true)) "$k={{key}}" else "$k=$v"
                        }
                        return@runCatching "${u.protocol}://${u.host}${u.path}?$rebuilt"
                    }
                }
                // Path-embedded keyword — leave as-is with a hint placeholder at end
                val path = u.path
                val last = path.substringAfterLast('/')
                if (last.isNotBlank() && last.length in 1..40 && !last.all { it.isDigit() }) {
                    val base = path.substringBeforeLast('/')
                    return@runCatching "${u.protocol}://${u.host}$base/{{key}}"
                }
                resultUrl
            }.getOrNull()
        }

        private fun JsonObject.string(name: String): String? =
            get(name)?.takeIf { !it.isJsonNull }?.asString

        private fun JsonObject.int(name: String, defaultValue: Int): Int =
            runCatching { get(name)?.takeIf { !it.isJsonNull }?.asInt }.getOrNull() ?: defaultValue

        private fun JsonObject.bool(name: String, defaultValue: Boolean): Boolean {
            val el = get(name)?.takeIf { !it.isJsonNull } ?: return defaultValue
            return runCatching {
                when {
                    el.isJsonPrimitive && el.asJsonPrimitive.isBoolean -> el.asBoolean
                    else -> el.asString.toBooleanStrictOrNull() ?: defaultValue
                }
            }.getOrDefault(defaultValue)
        }
    }
}
