package io.legado.app.domain.usecase

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.constant.BookSourceType
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
import io.legado.app.model.Debug
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.GSON
import io.legado.app.utils.isAbsUrl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CopyOnWriteArrayList

/**
 * AI chat tools for book-source versioning, check/debug/fix/write.
 * Keeps [io.legado.app.data.repository.AiToolRepository] thin.
 */
class BookSourceAgentTools(
    private val versionService: BookSourceVersionService,
    private val checkUseCase: CheckBookSourceUseCase,
    private val searchRuleHelpUseCase: SearchRuleHelpUseCase,
    private val writeUseCase: WriteBookSourceFromUrlUseCase,
) {

    fun searchRuleHelp(args: JsonObject): String {
        val query = args.string("query").orEmpty()
        val scope = args.string("scope") ?: "all"
        val limit = args.int("limit", 12)
        val doc = args.string("doc")
        return searchRuleHelpUseCase.search(query, scope, limit, doc)
    }

    suspend fun listVersions(args: JsonObject): String {
        val source = when (val resolved = resolveArgs(args)) {
            is ResolveOk -> resolved.source
            is ResolveErr -> return resolved.json
        }
        val url = source.bookSourceUrl
        val limit = args.int("limit", BookSourceVersionService.VERSION_KEEP)
        val versions = versionService.list(url, limit)
        return GSON.toJson(
            mapOf(
                "bookSourceUrl" to url,
                "bookSourceName" to source.bookSourceName,
                "count" to versions.size,
                "versions" to versions.map {
                    mapOf(
                        "id" to it.id,
                        "source" to it.source,
                        "diffSummary" to it.diffSummary,
                        "createdAt" to it.createdAt,
                        "conversationId" to it.conversationId,
                    )
                },
            ),
        )
    }

    suspend fun diffVersion(args: JsonObject): String {
        val id = args.string("versionId").orEmpty().trim()
        if (id.isBlank()) return """{"error":"versionId is required"}"""
        val diff = versionService.diff(id) ?: return """{"error":"Version not found: $id"}"""
        return GSON.toJson(
            mapOf(
                "versionId" to diff.versionId,
                "bookSourceUrl" to diff.bookSourceUrl,
                "source" to diff.source,
                "createdAt" to diff.createdAt,
                "summary" to diff.summary,
                "changes" to diff.changes.map {
                    mapOf("path" to it.path, "oldValue" to it.oldValue.take(200), "newValue" to it.newValue.take(200))
                },
            ),
        )
    }

    suspend fun restoreVersion(
        args: JsonObject,
        toolCallId: String?,
        batchId: String?,
        conversationId: String?,
    ): String {
        val id = args.string("versionId").orEmpty().trim()
        if (id.isBlank()) return """{"error":"versionId is required"}"""
        val result = versionService.restore(
            id,
            BookSourceVersionService.CaptureMeta(
                toolCallId = toolCallId,
                batchId = batchId,
                conversationId = conversationId,
            ),
        )
        return GSON.toJson(
            mapOf(
                "success" to result.success,
                "message" to result.message,
                "snapshotId" to result.snapshotId,
                "bookSourceUrl" to result.bookSourceUrl,
            ),
        )
    }

    suspend fun readBookSource(args: JsonObject): String {
        val source = when (val resolved = resolveArgs(args)) {
            is ResolveOk -> resolved.source
            is ResolveErr -> return resolved.json
        }
        val stage = args.string("stage")?.trim()?.lowercase().orEmpty()
        val out = linkedMapOf<String, Any?>(
            "success" to true,
            "stage" to stage.ifBlank { "meta" },
            "bookSourceUrl" to source.bookSourceUrl,
        )
        when (stage) {
            "", "meta" -> {
                out["bookSourceName"] = source.bookSourceName
                out["bookSourceGroup"] = source.bookSourceGroup
                out["bookSourceType"] = source.bookSourceType
                if (source.bookSourceType == BookSourceType.image) {
                    out["bookSourceNote"] =
                        "Manga source (bookSourceType=2): ruleContent.content must yield <img> HTML " +
                        "(e.g. @css:img or @css:.wrap img — NOT @css:img@src which returns a bare URL list and extracts 0 images). " +
                        "The content check requires ≥1 extracted <img> and probes the first two image URLs for HTTP 2xx. " +
                        "Image src may carry a ,{\"headers\":{...}} suffix for per-image headers. Set header (Referer/UA) for anti-hotlink. " +
                        "If chapter images are produced by page JS (encrypted vars / lazy load), ruleToc.chapterUrl must emit " +
                        ",{\"webView\":true} (optionally ,\"webViewDelayTime\":3000) so the chapter renders before parsing — " +
                        "otherwise the content check extracts 0 images."
                }
                out["enabled"] = source.enabled
                out["bookUrlPattern"] = source.bookUrlPattern
                out["searchUrl"] = source.searchUrl
                out["exploreUrl"] = source.exploreUrl
                out["header"] = source.header
                out["loginUrl"] = source.loginUrl
                out["bookSourceComment"] = source.bookSourceComment
            }
            "search" -> {
                out["searchUrl"] = source.searchUrl
                out["ruleSearch"] = source.ruleSearch
            }
            "info" -> {
                out["bookUrlPattern"] = source.bookUrlPattern
                out["ruleBookInfo"] = source.ruleBookInfo
            }
            "toc" -> out["ruleToc"] = source.ruleToc
            "content" -> out["ruleContent"] = source.ruleContent
            "explore" -> {
                out["exploreUrl"] = source.exploreUrl
                out["ruleExplore"] = source.ruleExplore
            }
            "all" -> out["bookSource"] = source
            else -> return """{"error":"Invalid stage: use meta|search|info|toc|content|explore|all"}"""
        }
        return GSON.toJson(out)
    }

    suspend fun fetchPageSnippet(
        args: JsonObject,
        bookshelfAccessApproved: Boolean,
        configMaxChars: Int? = null,
    ): String {
        if (!AppConfig.aiAllowBookSourceFetch) {
            return """{"error":"Book-source network fetch is disabled in AI ability settings"}"""
        }
        if (!bookshelfAccessApproved) {
            return """{"error":"User confirmation required for network fetch"}"""
        }
        val url = args.string("url").orEmpty().trim()
        if (url.isBlank() || !url.isAbsUrl()) {
            return """{"error":"url must be an absolute http(s) URL"}"""
        }
        val sourceUrl = args.string("bookSourceUrl")?.trim()
        val source = sourceUrl?.takeIf { it.isNotBlank() }?.let { appDb.bookSourceDao.getBookSource(it) }
        // configMaxChars 是工具级上限（能力管理里的"最大字符数"）：AI 传参可更小但不能超过它。
        // raw 模式返回原始 HTML，默认抓满上限；摘要模式保持 4000 默认。
        val raw = args.bool("raw", false)
        val defaultMax = if (raw) (configMaxChars ?: 12_000) else 4000
        val maxChars = args.int("maxChars", defaultMax).coerceIn(500, configMaxChars ?: 12_000)
        // JS 渲染页面（加密变量 / 懒加载图片，典型如漫画章节页）需 WebView 渲染后再取 HTML。
        // 复用 URL 选项机制（url,{"webView":true}，与阅读器规则同一条路径），由 paramPattern 剥离。
        val useWebView = args.bool("useWebView", false)
        val webViewDelayMs = args.int("webViewDelayMs", 0).coerceIn(0, 10_000)
        val alreadyHasOption = AnalyzeUrl.paramPattern.matcher(url).find()
        val mUrl = when {
            !useWebView || alreadyHasOption -> url
            webViewDelayMs > 0 -> "$url,{\"webView\":true,\"webViewDelayTime\":$webViewDelayMs}"
            else -> "$url,{\"webView\":true}"
        }
        return runCatching {
            val response = AnalyzeUrl(
                mUrl = mUrl,
                source = source,
                baseUrl = source?.bookSourceUrl.orEmpty(),
            ).getStrResponseAwait(useWebView = true)
            val body = response.body.orEmpty()
            GSON.toJson(
                mapOf(
                    "success" to true,
                    "url" to (response.url.takeIf { it.isNotBlank() } ?: url),
                    "renderedByWebView" to useWebView,
                    "raw" to raw,
                    "chars" to body.length,
                    "snippet" to body.take(maxChars),
                    "truncated" to (body.length > maxChars),
                ),
            )
        }.getOrElse {
            """{"error":"${escape(it.localizedMessage ?: it.toString())}"}"""
        }
    }

    suspend fun checkBookSource(
        args: JsonObject,
        toolCallId: String?,
        batchId: String?,
        conversationId: String?,
        bookshelfAccessApproved: Boolean,
    ): String {
        if (!AppConfig.aiAllowBookSourceFetch) {
            return """{"error":"Book-source network fetch is disabled in AI ability settings"}"""
        }
        if (!bookshelfAccessApproved) {
            return """{"error":"User confirmation required for network check"}"""
        }
        val resolved = resolveArgs(args)
        val source = when (resolved) {
            is ResolveOk -> resolved.source
            is ResolveErr -> return resolved.json
        }
        val url = source.bookSourceUrl
        val requireSearch = args.bool("requireSearch", false) ||
            args.string("action")?.equals("mvp", true) == true
        val timeoutMs = args.int("timeoutMs", 120_000).toLong().coerceIn(5_000L, 180_000L)
        val stopOnFailure = args.bool("stopOnFailure", true)
        val defaultDiscovery = !source.exploreUrl.isNullOrBlank()
        val options = if (requireSearch) {
            CheckBookSourceUseCase.mvpWriteOptions(
                keyword = args.string("keyword"),
                persist = args.bool("persist", true),
            ).copy(
                timeoutMs = timeoutMs,
                stopOnFailure = stopOnFailure,
                checkDiscovery = args.bool("checkDiscovery", defaultDiscovery),
                checkImages = args.bool("checkImages", true),
            )
        } else {
            CheckBookSourceUseCase.Options(
                checkSearch = args.bool("checkSearch", true),
                checkDiscovery = args.bool("checkDiscovery", defaultDiscovery),
                checkInfo = args.bool("checkInfo", true),
                checkCategory = args.bool("checkCategory", true),
                checkContent = args.bool("checkContent", true),
                keyword = args.string("keyword"),
                timeoutMs = timeoutMs,
                persist = args.bool("persist", true),
                requireSearch = false,
                stopOnFailure = stopOnFailure,
                checkImages = args.bool("checkImages", true),
            )
        }
        val snapshotId = versionService.captureBeforeChange(
            url,
            BookSourceVersionService.SOURCE_CHECK,
            BookSourceVersionService.CaptureMeta(toolCallId, batchId, conversationId),
        )
        val result = checkUseCase.check(source, options)
        val obj = JsonParser.parseString(result.toJson()).asJsonObject
        if (snapshotId != null) obj.addProperty("snapshotId", snapshotId)
        obj.addProperty("resolvedBy", "local_search")
        obj.addProperty("resolvedName", source.bookSourceName)
        obj.addProperty("bookSourceType", source.bookSourceType)
        return obj.toString()
    }

    suspend fun debugBookSource(
        args: JsonObject,
        bookshelfAccessApproved: Boolean,
        configMaxChars: Int? = null,
    ): String {
        if (!AppConfig.aiAllowBookSourceFetch) {
            return """{"error":"Book-source network fetch is disabled in AI ability settings"}"""
        }
        if (!bookshelfAccessApproved) {
            return """{"error":"User confirmation required for network debug"}"""
        }
        val source = when (val resolved = resolveArgs(args)) {
            is ResolveOk -> resolved.source
            is ResolveErr -> return resolved.json
        }
        val url = source.bookSourceUrl
        val key = args.string("key").orEmpty().trim()
        if (key.isBlank()) {
            return """{"error":"key is required. Search keyword, absolute detail URL, ++tocUrl, --contentUrl, or name::exploreUrl"}"""
        }
        val maxLogs = args.int("maxLogs", 80).coerceIn(10, 200)
        val logs = CopyOnWriteArrayList<String>()
        val finished = CompletableDeferred<Int>()
        val previous = Debug.callback
        Debug.callback = object : Debug.Callback {
            override fun printLog(state: Int, msg: String) {
                logs += msg
                if (state == 1000 || state == -1) {
                    finished.complete(state)
                }
            }
        }
        return try {
            coroutineScope {
                Debug.startDebug(this, source, key)
                val state = withTimeout(args.int("timeoutMs", 120_000).toLong().coerceIn(5_000L, 180_000L)) {
                    finished.await()
                }
                GSON.toJson(
                    mapOf(
                        "success" to (state == 1000),
                        "bookSourceUrl" to url,
                        "bookSourceName" to source.bookSourceName,
                        "key" to key,
                        "state" to state,
                        "logTail" to cappedLogTail(logs, maxLogs, configMaxChars),
                    ),
                )
            }
        } catch (e: Exception) {
            GSON.toJson(
                mapOf(
                    "success" to false,
                    "bookSourceUrl" to url,
                    "bookSourceName" to source.bookSourceName,
                    "key" to key,
                    "error" to (e.localizedMessage ?: e.toString()),
                    "logTail" to cappedLogTail(logs, maxLogs, configMaxChars),
                ),
            )
        } finally {
            Debug.cancelDebug(destroy = false)
            Debug.callback = previous
        }
    }

    /**
     * logTail 按行数（maxLogs）再按字符预算（configMaxChars，工具级"最大字符数"上限）截断。
     * budget 为 null 时维持纯行数截断，与旧行为一致。
     */
    private fun cappedLogTail(lines: List<String>, maxLogs: Int, budget: Int?): List<String> {
        val tail = lines.takeLast(maxLogs)
        if (budget == null || budget <= 0) return tail
        val out = mutableListOf<String>()
        var used = 0
        for (line in tail) {
            if (used + line.length > budget && out.isNotEmpty()) break
            out += line
            used += line.length
        }
        if (out.size < tail.size) {
            out += "[...truncated ${tail.size - out.size} lines, ${tail.sumOf { it.length } - used} chars]"
        }
        return out
    }

    suspend fun patchBookSource(
        args: JsonObject,
        toolCallId: String?,
        batchId: String?,
        conversationId: String?,
        bookshelfAccessApproved: Boolean,
    ): String {
        val source = when (val resolved = resolveArgs(args)) {
            is ResolveOk -> resolved.source.copy()
            is ResolveErr -> return resolved.json
        }
        val opsRaw = args.string("operations") ?: args.get("operations")?.toString()
        if (opsRaw.isNullOrBlank()) return """{"error":"operations JSON array is required"}"""
        val ops = runCatching {
            JsonParser.parseString(opsRaw).asJsonArray
        }.getOrElse {
            return """{"error":"operations must be a JSON array of {path,value}"}"""
        }
        val before = source.copy()
        val oldUrl = source.bookSourceUrl
        for (el in ops) {
            val op = el.asJsonObject
            val path = op.get("path")?.asString?.trim().orEmpty()
            if (path.isBlank()) continue
            val value = op.get("value")
            applyField(source, path, value)
        }
        val newUrl = source.bookSourceUrl.trim()
        if (newUrl.isBlank()) {
            return """{"error":"bookSourceUrl cannot be blank"}"""
        }
        if (oldUrl != newUrl) {
            val conflict = appDb.bookSourceDao.getBookSource(newUrl)
            if (conflict != null) {
                return """{"error":"Cannot rename bookSourceUrl: target already exists: $newUrl"}"""
            }
        }
        val snapshotId = versionService.captureBeforeChange(
            oldUrl,
            BookSourceVersionService.SOURCE_PATCH,
            BookSourceVersionService.CaptureMeta(
                toolCallId = toolCallId,
                batchId = batchId,
                conversationId = conversationId,
                diffSummary = if (oldUrl != newUrl) {
                    "rename $oldUrl → $newUrl + patch ${ops.size()} field(s)"
                } else {
                    "patch ${ops.size()} field(s)"
                },
            ),
        )
        source.lastUpdateTime = System.currentTimeMillis()
        if (oldUrl != newUrl) {
            // Primary key change: delete old row then insert (same as BookSourceEditViewModel.save).
            SourceHelp.deleteBookSource(oldUrl)
            SourceHelp.insertBookSource(source)
            versionService.migrateUrl(oldUrl, newUrl)
        } else {
            SourceHelp.insertBookSource(source)
        }
        if (before.exploreUrl != source.exploreUrl) {
            before.clearExploreKindsCache()
        }
        val recheck = args.bool("recheck", true)
        var checkJson: String? = null
        if (recheck) {
            if (!AppConfig.aiAllowBookSourceFetch) {
                checkJson = """{"skipped":true,"reason":"fetch disabled"}"""
            } else if (!bookshelfAccessApproved) {
                checkJson = """{"skipped":true,"reason":"network confirm required for recheck"}"""
            } else {
                val touchExplore = ops.any { el ->
                    val path = el.asJsonObject.get("path")?.asString.orEmpty()
                    path == "exploreUrl" || path == "exploreScreen" || path == "enabledExplore" ||
                        path == "ruleExplore" || path.startsWith("ruleExplore.")
                }
                val checkDiscovery = args.bool(
                    "checkDiscovery",
                    touchExplore || !source.exploreUrl.isNullOrBlank(),
                )
                val checkResult = checkUseCase.check(
                    source,
                    CheckBookSourceUseCase.mvpWriteOptions(persist = true)
                        .copy(checkDiscovery = checkDiscovery),
                )
                checkJson = checkResult.toJson()
            }
        }
        val changes = BookSourceVersionService.diffBookSources(before, source)
        return GSON.toJson(
            mapOf(
                "success" to true,
                "bookSourceUrl" to source.bookSourceUrl,
                "previousBookSourceUrl" to oldUrl.takeIf { it != newUrl },
                "renamed" to (oldUrl != newUrl),
                "snapshotId" to snapshotId,
                "changedFields" to changes.map { it.path },
                "check" to checkJson?.let { JsonParser.parseString(it) },
            ),
        )
    }

    /**
     * Dry-run patch operations against the current source for mutation approval UI.
     * Returns empty list when the source/ops cannot be resolved.
     */
    fun previewPatch(args: JsonObject): List<io.legado.app.domain.usecase.structured.FieldChange> {
        val source = when (val resolved = resolveArgs(args)) {
            is ResolveOk -> resolved.source.copy()
            is ResolveErr -> return emptyList()
        }
        val opsRaw = args.string("operations") ?: args.get("operations")?.toString()
        if (opsRaw.isNullOrBlank()) return emptyList()
        val ops = runCatching { JsonParser.parseString(opsRaw).asJsonArray }.getOrNull()
            ?: return emptyList()
        val before = source.copy()
        for (el in ops) {
            val op = el.asJsonObject
            val path = op.get("path")?.asString?.trim().orEmpty()
            if (path.isBlank()) continue
            applyField(source, path, op.get("value"))
        }
        return BookSourceVersionService.diffBookSources(before, source)
    }

    suspend fun writeFromUrl(
        args: JsonObject,
        toolCallId: String?,
        batchId: String?,
        conversationId: String?,
        bookshelfAccessApproved: Boolean,
    ): String {
        return writeUseCase.execute(
            args = args,
            toolCallId = toolCallId,
            batchId = batchId,
            conversationId = conversationId,
            bookshelfAccessApproved = bookshelfAccessApproved,
        )
    }

    private sealed class ResolveResult
    private data class ResolveOk(val source: BookSource) : ResolveResult()
    private data class ResolveErr(val json: String) : ResolveResult()

    private fun resolveArgs(args: JsonObject): ResolveResult {
        return when (
            val resolved = LocalBookSourceResolver.resolveFromDb(
                bookSourceUrl = args.string("bookSourceUrl"),
                query = args.string("query"),
                bookSourceName = args.string("bookSourceName"),
                enabledOnly = args.bool("enabledOnly", false),
                limit = args.int("limit", 20),
            )
        ) {
            is LocalBookSourceResolver.Outcome.Unique -> ResolveOk(resolved.source)
            is LocalBookSourceResolver.Outcome.Ambiguous ->
                ResolveErr(LocalBookSourceResolver.ambiguousJson(resolved.query, resolved.candidates))
            is LocalBookSourceResolver.Outcome.NotFound ->
                ResolveErr(LocalBookSourceResolver.notFoundJson(resolved.query))
            is LocalBookSourceResolver.Outcome.MissingQuery ->
                ResolveErr("""{"error":"${resolved.message}"}""")
        }
    }

    private fun applyField(source: BookSource, path: String, value: com.google.gson.JsonElement?) {
        val text = when {
            value == null || value.isJsonNull -> null
            value.isJsonPrimitive -> value.asString
            else -> value.toString()
        }
        when (path) {
            "bookSourceUrl" -> source.bookSourceUrl = text.orEmpty().trim()
            "bookSourceName" -> source.bookSourceName = text.orEmpty()
            "bookSourceGroup" -> source.bookSourceGroup = text
            "bookSourceType" -> source.bookSourceType = text?.toIntOrNull() ?: source.bookSourceType
            "bookUrlPattern" -> source.bookUrlPattern = text
            "enabled" -> source.enabled = text?.toBooleanStrictOrNull() ?: (text != "0")
            "enabledExplore" -> source.enabledExplore = text?.toBooleanStrictOrNull() ?: source.enabledExplore
            "searchUrl" -> source.searchUrl = text
            "exploreUrl" -> source.exploreUrl = text
            "exploreScreen" -> source.exploreScreen = text
            "header" -> source.header = text
            "loginUrl" -> source.loginUrl = text
            "jsLib" -> source.jsLib = text
            "bookSourceComment" -> source.bookSourceComment = text
            "concurrentRate" -> source.concurrentRate = text
            "ruleSearch" -> source.ruleSearch = parseRule(text, SearchRule::class.java) ?: source.ruleSearch
            "ruleExplore" -> source.ruleExplore = parseRule(text, ExploreRule::class.java) ?: source.ruleExplore
            "ruleBookInfo" -> source.ruleBookInfo = parseRule(text, BookInfoRule::class.java) ?: source.ruleBookInfo
            "ruleToc" -> source.ruleToc = parseRule(text, TocRule::class.java) ?: source.ruleToc
            "ruleContent" -> source.ruleContent = parseRule(text, ContentRule::class.java) ?: source.ruleContent
            else -> {
                // Nested rule field: ruleSearch.bookList
                val parts = path.split('.', limit = 2)
                if (parts.size == 2) {
                    when (parts[0]) {
                        "ruleSearch" -> {
                            val rule = source.getSearchRule()
                            setBeanField(rule, parts[1], text)
                            source.ruleSearch = rule
                        }
                        "ruleExplore" -> {
                            val rule = source.getExploreRule()
                            setBeanField(rule, parts[1], text)
                            source.ruleExplore = rule
                        }
                        "ruleBookInfo" -> {
                            val rule = source.getBookInfoRule()
                            setBeanField(rule, parts[1], text)
                            source.ruleBookInfo = rule
                        }
                        "ruleToc" -> {
                            val rule = source.getTocRule()
                            setBeanField(rule, parts[1], text)
                            source.ruleToc = rule
                        }
                        "ruleContent" -> {
                            val rule = source.getContentRule()
                            setBeanField(rule, parts[1], text)
                            source.ruleContent = rule
                        }
                    }
                }
            }
        }
    }

    private fun <T> parseRule(text: String?, clazz: Class<T>): T? {
        if (text.isNullOrBlank()) return null
        return runCatching { GSON.fromJson(text, clazz) }.getOrNull()
    }

    private fun setBeanField(target: Any, field: String, value: String?) {
        runCatching {
            val f = target.javaClass.getDeclaredField(field)
            f.isAccessible = true
            f.set(target, value)
        }
    }

    companion object {
        fun JsonObject.string(name: String): String? =
            get(name)?.takeIf { !it.isJsonNull }?.asString

        fun JsonObject.int(name: String, defaultValue: Int): Int =
            runCatching { get(name)?.takeIf { !it.isJsonNull }?.asInt }.getOrNull() ?: defaultValue

        fun JsonObject.bool(name: String, defaultValue: Boolean): Boolean {
            val el = get(name)?.takeIf { !it.isJsonNull } ?: return defaultValue
            return runCatching {
                when {
                    el.isJsonPrimitive && el.asJsonPrimitive.isBoolean -> el.asBoolean
                    else -> el.asString.toBooleanStrictOrNull() ?: defaultValue
                }
            }.getOrDefault(defaultValue)
        }

        private fun escape(text: String): String =
            text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    }
}
