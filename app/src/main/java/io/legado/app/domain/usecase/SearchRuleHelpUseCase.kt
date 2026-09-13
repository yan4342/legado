package io.legado.app.domain.usecase

import android.content.res.AssetManager
import io.legado.app.domain.gateway.AiSkillGateway
import io.legado.app.utils.GSON
import kotlinx.coroutines.runBlocking
import splitties.init.appCtx

/**
 * Keyword search over help markdown under assets/web/help/md and skill docs.
 * Book-source scopes: rule/js/regex/xpath/debug/all.
 * TTS scope: mimoTtsHelp + httpTTSHelp.
 * Skill scope: skill:{skillId}.
 *
 * Progressive disclosure for skills:
 * - Catalog injects metadata only.
 * - `doc=SKILL` fetches core instructions (on demand).
 * - Other `doc=` ids fetch supporting resources (very on demand).
 * Results are for on-demand tool use — never inject into system prompts.
 */
class SearchRuleHelpUseCase(
    private val skillGateway: AiSkillGateway,
) {

    data class Hit(
        val resource: String,
        val score: Int,
        val title: String,
        val snippet: String,
        val doc: String,
        val line: Int? = null,
    )

    fun search(
        query: String,
        scope: String = "all",
        limit: Int = DEFAULT_LIMIT,
        doc: String? = null,
    ): String {
        val docId = doc?.trim()?.takeIf { it.isNotBlank() }
        if (docId != null) {
            return fetchDoc(scope = scope, docId = docId, query = query.trim())
        }
        val key = query.trim()
        if (key.isBlank()) return """{"error":"query is required (or pass doc= to load a full document)"}"""
        val resolved = resolveDocs(scope)
            ?: return """{"error":"Invalid scope: use rule, js, regex, xpath, debug, tts, skill:{id}, or all"}"""
        val hits = mutableListOf<Hit>()
        val needle = key.lowercase()
        for ((id, text, resource) in resolved) {
            val lines = text.lines()
            for ((index, line) in lines.withIndex()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                val lower = trimmed.lowercase()
                if (!lower.contains(needle)) continue
                val score = when {
                    trimmed.startsWith("#") && lower.contains(needle) -> 12
                    lower.startsWith(needle) -> 10
                    else -> 6
                }
                hits += Hit(
                    resource = resource,
                    score = score,
                    title = headingNear(lines, index) ?: id,
                    snippet = snippetAround(lines, index),
                    doc = id,
                    line = index + 1,
                )
            }
            if (id.lowercase().contains(needle)) {
                hits += Hit(
                    resource = resource,
                    score = 8,
                    title = id,
                    snippet = lines.take(4).joinToString("\n").take(240),
                    doc = id,
                    line = 1,
                )
            }
        }
        val cap = limit.coerceIn(1, MAX_LIMIT)
        val sorted = hits
            .distinctBy { "${it.doc}:${it.line}:${it.snippet.take(40)}" }
            .sortedByDescending { it.score }
            .take(cap)
        val scopeNorm = scope.trim().lowercase().ifBlank { "all" }
        return GSON.toJson(
            mapOf(
                "query" to key,
                "scope" to scopeNorm,
                "count" to sorted.size,
                "hits" to sorted.map {
                    mapOf(
                        "resource" to it.resource,
                        "score" to it.score,
                        "title" to it.title,
                        "snippet" to it.snippet,
                        "doc" to it.doc,
                        "line" to it.line,
                    )
                },
                "workflow" to when {
                    scopeNorm == "tts" || scopeNorm.startsWith("skill:tts") ->
                        "Use snippets for cloud/Http TTS setup. Do not paste full help docs into prompts."
                    scopeNorm.startsWith("skill:") ->
                        "Keyword hits only. To load core instructions use doc=SKILL; other docs are supporting resources."
                    else ->
                        "Use snippets to craft/fix book source rules. Do not paste full help docs into prompts."
                },
            ),
        )
    }

    /**
     * Full-document fetch for progressive disclosure levels 2–3.
     * Caps content length; optional [query] filters to matching sections when set.
     */
    private fun fetchDoc(scope: String, docId: String, query: String): String {
        val normalized = scope.trim().lowercase().ifBlank { "all" }
        val text: String
        val resource: String
        when {
            normalized.startsWith("skill:") -> {
                val skillId = normalized.removePrefix("skill:").trim()
                if (skillId.isBlank()) {
                    return """{"error":"Invalid skill scope"}"""
                }
                val md = runBlocking { skillGateway.readDocMarkdown(skillId, docId) }
                    ?: return """{"error":"Document not found: skill:$skillId doc=$docId"}"""
                text = md
                resource = "skill:$skillId"
            }
            else -> {
                val resolved = resolveDocs(normalized)
                    ?: return """{"error":"Invalid scope for doc fetch"}"""
                val match = resolved.firstOrNull { it.id.equals(docId, ignoreCase = true) }
                    ?: return """{"error":"Document not found: $docId (scope=$normalized)"}"""
                text = match.text
                resource = match.resource
            }
        }
        val layer = when {
            docId.equals(SkillPackageCodec.DOC_ID_SKILL, ignoreCase = true) -> "core"
            resource.startsWith("skill:") -> "resource"
            else -> "help"
        }
        val body = if (query.isBlank()) {
            text.take(MAX_FULL_DOC_CHARS)
        } else {
            excerptMatching(text, query).take(MAX_FULL_DOC_CHARS)
        }
        return GSON.toJson(
            mapOf(
                "scope" to normalized,
                "doc" to docId,
                "resource" to resource,
                "layer" to layer,
                "truncated" to (body.length < text.length),
                "content" to body,
                "workflow" to when (layer) {
                    "core" -> "Core skill instructions loaded. Follow when relevant; load resources only if needed."
                    "resource" -> "Supporting resource loaded. Use sparingly; do not paste into user replies."
                    else -> "Help doc loaded on demand. Do not paste full docs into prompts."
                },
            ),
        )
    }

    private data class DocText(val id: String, val text: String, val resource: String)

    private fun resolveDocs(scope: String): List<DocText>? {
        val normalized = scope.trim().lowercase().ifBlank { "all" }
        if (normalized.startsWith("skill:")) {
            val skillId = normalized.removePrefix("skill:").trim()
            if (skillId.isBlank()) return null
            return runBlocking {
                val skill = skillGateway.getById(skillId) ?: return@runBlocking emptyList()
                skillGateway.listDocMetas(skill).mapNotNull { meta ->
                    val md = skillGateway.readDocMarkdown(skillId, meta.id) ?: return@mapNotNull null
                    DocText(meta.id, md, "skill:$skillId")
                }
            }
        }
        val assetNames = when (normalized) {
            "all" -> HELP_DOCS
            "rule" -> listOf("ruleHelp")
            "js" -> listOf("jsHelp")
            "regex" -> listOf("regexHelp")
            "xpath" -> listOf("xpathHelp")
            "debug" -> listOf("debugHelp")
            "tts" -> TTS_HELP_DOCS
            else -> return null
        }
        val assets: AssetManager = appCtx.assets
        return assetNames.mapNotNull { doc ->
            val text = runCatching {
                assets.open("$ASSET_DIR$doc.md").bufferedReader().use { it.readText() }
            }.getOrNull() ?: return@mapNotNull null
            DocText(doc, text, "rule_help")
        }
    }

    private fun excerptMatching(text: String, query: String): String {
        val needle = query.lowercase()
        val lines = text.lines()
        val indices = lines.mapIndexedNotNull { i, line ->
            i.takeIf { line.lowercase().contains(needle) }
        }
        if (indices.isEmpty()) return text.take(800)
        val chunks = indices.take(8).map { index ->
            val start = (index - 2).coerceAtLeast(0)
            val end = (index + 6).coerceAtMost(lines.lastIndex)
            lines.subList(start, end + 1).joinToString("\n")
        }
        return chunks.joinToString("\n\n---\n\n")
    }

    private fun headingNear(lines: List<String>, index: Int): String? {
        for (i in index downTo 0) {
            val line = lines[i].trim()
            if (line.startsWith("#")) return line.trimStart('#').trim()
        }
        return null
    }

    private fun snippetAround(lines: List<String>, index: Int, radius: Int = 2): String {
        val start = (index - radius).coerceAtLeast(0)
        val end = (index + radius).coerceAtMost(lines.lastIndex)
        return lines.subList(start, end + 1).joinToString("\n").take(400)
    }

    companion object {
        private const val ASSET_DIR = "web/help/md/"
        private const val DEFAULT_LIMIT = 12
        private const val MAX_LIMIT = 30
        private const val MAX_FULL_DOC_CHARS = 12_000
        val HELP_DOCS = listOf("ruleHelp", "jsHelp", "regexHelp", "xpathHelp", "debugHelp")
        val TTS_HELP_DOCS = listOf("mimoTtsHelp", "httpTTSHelp")
    }
}
