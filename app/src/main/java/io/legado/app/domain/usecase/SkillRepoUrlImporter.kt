package io.legado.app.domain.usecase

import io.legado.app.domain.model.AiToolProgressCallback
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.getProxyClient
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.utils.Utf8BomUtils
import io.legado.app.utils.isAbsUrl
import okhttp3.OkHttpClient
import okio.Buffer
import java.nio.charset.Charset

/**
 * Resolve a git-hosting / raw URL to candidate `SKILL.md` download URLs and fetch the first that works.
 *
 * Supported inputs:
 * - Direct raw / `.md` URL
 * - `github.com/owner/repo` (+ optional `/tree|blob/ref/path`)
 * - `gitee.com/owner/repo` (+ `/tree|blob/ref/path` or `/raw/ref/path`)
 * - `gitlab.com/owner/repo` (+ `/-/tree|blob/raw/ref/path`)
 * - Already-raw hosts (`raw.githubusercontent.com`, `cdn.jsdelivr.net/gh/...`)
 */
object SkillRepoUrlImporter {

    /** Same format as book-source `proxy` header: `http://host:port` / `socks5://host:port` / `…@user@pass`. */
    private val PROXY_PATTERN =
        Regex("""^(http|socks4|socks5)://[^:]+:\d{2,5}(@[^@]+@[^@]+)?$""", RegexOption.IGNORE_CASE)

    data class FetchedSkill(
        val markdown: String,
        /** Companion `.md` resources fetched from the same directory as SKILL.md. */
        val companions: Map<String, String> = emptyMap(),
        val sourceUrl: String,
    )

    suspend fun fetch(
        url: String,
        onProgress: AiToolProgressCallback? = null,
    ): Result<FetchedSkill> {
        val trimmed = url.trim()
        if (!trimmed.isAbsUrl()) {
            return Result.failure(IllegalArgumentException("Not an absolute URL"))
        }
        onProgress?.onProgress(0.05f, "Resolving candidates")
        val candidates = candidateSkillMdUrls(trimmed)
        if (candidates.isEmpty()) {
            return Result.failure(IllegalArgumentException("Cannot resolve SKILL.md from URL"))
        }
        var lastError: Throwable? = null
        val n = candidates.size.coerceAtLeast(1)
        for ((index, candidate) in candidates.withIndex()) {
            val slotStart = 0.08f + 0.55f * (index.toFloat() / n)
            val slotEnd = 0.08f + 0.55f * ((index + 1).toFloat() / n)
            onProgress?.onProgress(slotStart, "Fetching SKILL.md (${index + 1}/$n)")
            val body = runCatching {
                var lastEmitted = -1f
                httpGetText(candidate) { read, total ->
                    val local = if (total > 0L) {
                        (read.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0.5f
                    }
                    val shouldEmit = total <= 0L ||
                        local >= 1f ||
                        lastEmitted < 0f ||
                        local - lastEmitted >= 0.03f
                    if (!shouldEmit) return@httpGetText
                    lastEmitted = local
                    val fraction = slotStart + (slotEnd - slotStart) * 0.85f * local
                    val label = if (total > 0L) {
                        "Downloading SKILL.md ${formatBytes(read)} / ${formatBytes(total)}"
                    } else {
                        "Downloading SKILL.md ${formatBytes(read)}"
                    }
                    onProgress?.onProgress(fraction, label)
                }
            }.getOrElse {
                lastError = it
                null
            } ?: continue
            if (!looksLikeSkillMarkdown(body)) {
                lastError = IllegalArgumentException("Not a SKILL.md at $candidate")
                continue
            }
            onProgress?.onProgress(slotEnd.coerceAtMost(0.72f), "Loading resources")
            val companions = fetchCompanions(candidate, body, onProgress)
            onProgress?.onProgress(0.86f, "Download complete")
            return Result.success(
                FetchedSkill(
                    markdown = body,
                    companions = companions,
                    sourceUrl = candidate,
                ),
            )
        }
        return Result.failure(
            lastError ?: IllegalArgumentException("No SKILL.md found for $trimmed"),
        )
    }

    fun candidateSkillMdUrls(url: String): List<String> {
        val u = url.trim().trimEnd('/')
        val out = linkedSetOf<String>()

        when {
            u.contains("raw.githubusercontent.com/", ignoreCase = true) -> {
                addGithubRawCandidates(out, u)
            }
            u.contains("cdn.jsdelivr.net/gh/", ignoreCase = true) -> {
                addJsDelivrCandidates(out, u)
            }
            u.contains("github.com/", ignoreCase = true) -> {
                addGithubWebCandidates(out, u)
            }
            u.contains("gitee.com/", ignoreCase = true) -> {
                addGiteeCandidates(out, u)
            }
            u.contains("gitlab.com/", ignoreCase = true) -> {
                addGitlabCandidates(out, u)
            }
            else -> {
                // Generic: treat as file or directory.
                if (u.endsWith(".md", ignoreCase = true)) {
                    out += u
                } else {
                    out += "$u/SKILL.md"
                    out += "$u/skill.md"
                }
            }
        }
        return out.toList()
    }

    private fun addGithubWebCandidates(out: MutableSet<String>, url: String) {
        // github.com/owner/repo[/tree|blob/ref[/path]]
        val m = Regex(
            """https?://github\.com/([^/]+)/([^/#?]+)(?:/(tree|blob)/([^/]+)(?:/(.*))?)?""",
            RegexOption.IGNORE_CASE,
        ).matchEntire(url.trimEnd('/')) ?: return
        val owner = m.groupValues[1]
        val repo = m.groupValues[2].removeSuffix(".git")
        val kind = m.groupValues[3].lowercase()
        val ref = m.groupValues[4].ifBlank { "main" }
        val path = m.groupValues[5].trim('/')

        val refs = if (m.groupValues[4].isBlank()) listOf("main", "master") else listOf(ref)
        for (r in refs) {
            when {
                kind == "blob" && path.isNotBlank() -> {
                    out += "https://raw.githubusercontent.com/$owner/$repo/$r/$path"
                    if (!path.endsWith(".md", ignoreCase = true)) {
                        out += "https://raw.githubusercontent.com/$owner/$repo/$r/$path/SKILL.md"
                    }
                }
                path.isNotBlank() -> {
                    // tree or path under ref
                    if (path.endsWith(".md", ignoreCase = true)) {
                        out += "https://raw.githubusercontent.com/$owner/$repo/$r/$path"
                    } else {
                        out += "https://raw.githubusercontent.com/$owner/$repo/$r/$path/SKILL.md"
                        out += "https://raw.githubusercontent.com/$owner/$repo/$r/$path/skill.md"
                    }
                }
                else -> {
                    out += "https://raw.githubusercontent.com/$owner/$repo/$r/SKILL.md"
                    out += "https://raw.githubusercontent.com/$owner/$repo/$r/skills/SKILL.md"
                }
            }
        }
    }

    private fun addGithubRawCandidates(out: MutableSet<String>, url: String) {
        if (url.endsWith(".md", ignoreCase = true)) {
            out += url
        } else {
            out += "${url.trimEnd('/')}/SKILL.md"
        }
    }

    private fun addJsDelivrCandidates(out: MutableSet<String>, url: String) {
        // cdn.jsdelivr.net/gh/owner/repo@version/path
        val base = url.trimEnd('/')
        if (base.endsWith(".md", ignoreCase = true)) {
            out += base
        } else {
            out += "$base/SKILL.md"
        }
    }

    private fun addGiteeCandidates(out: MutableSet<String>, url: String) {
        // gitee.com/owner/repo[/blob|tree|raw/ref/path]
        val raw = Regex(
            """https?://gitee\.com/([^/]+)/([^/#?]+)/raw/([^/]+)/(.*)""",
            RegexOption.IGNORE_CASE,
        ).matchEntire(url.trimEnd('/'))
        if (raw != null) {
            val path = raw.groupValues[4]
            val base = "https://gitee.com/${raw.groupValues[1]}/${raw.groupValues[2].removeSuffix(".git")}/raw/${raw.groupValues[3]}"
            if (path.endsWith(".md", ignoreCase = true)) out += "$base/$path"
            else {
                out += "$base/$path/SKILL.md"
                out += "$base/${path.trimEnd('/')}/SKILL.md"
            }
            return
        }
        val m = Regex(
            """https?://gitee\.com/([^/]+)/([^/#?]+)(?:/(tree|blob)/([^/]+)(?:/(.*))?)?""",
            RegexOption.IGNORE_CASE,
        ).matchEntire(url.trimEnd('/')) ?: return
        val owner = m.groupValues[1]
        val repo = m.groupValues[2].removeSuffix(".git")
        val ref = m.groupValues[4].ifBlank { "master" }
        val path = m.groupValues[5].trim('/')
        val refs = if (m.groupValues[4].isBlank()) listOf("master", "main") else listOf(ref)
        for (r in refs) {
            val base = "https://gitee.com/$owner/$repo/raw/$r"
            when {
                path.endsWith(".md", ignoreCase = true) -> out += "$base/$path"
                path.isNotBlank() -> out += "$base/$path/SKILL.md"
                else -> {
                    out += "$base/SKILL.md"
                    out += "$base/skills/SKILL.md"
                }
            }
        }
    }

    private fun addGitlabCandidates(out: MutableSet<String>, url: String) {
        // gitlab.com/owner/repo/-/blob|tree|raw/ref/path
        val raw = Regex(
            """https?://gitlab\.com/(.+?)/-/raw/([^/]+)/(.*)""",
            RegexOption.IGNORE_CASE,
        ).matchEntire(url.trimEnd('/'))
        if (raw != null) {
            val project = raw.groupValues[1]
            val ref = raw.groupValues[2]
            val path = raw.groupValues[3]
            val base = "https://gitlab.com/$project/-/raw/$ref"
            if (path.endsWith(".md", ignoreCase = true)) out += "$base/$path"
            else out += "$base/${path.trimEnd('/')}/SKILL.md"
            return
        }
        val m = Regex(
            """https?://gitlab\.com/(.+?)(?:/-/(tree|blob)/([^/]+)(?:/(.*))?)?""",
            RegexOption.IGNORE_CASE,
        ).matchEntire(url.trimEnd('/')) ?: return
        val project = m.groupValues[1].removeSuffix(".git")
        val ref = m.groupValues[3].ifBlank { "main" }
        val path = m.groupValues[4].trim('/')
        val refs = if (m.groupValues[3].isBlank()) listOf("main", "master") else listOf(ref)
        for (r in refs) {
            val base = "https://gitlab.com/$project/-/raw/$r"
            when {
                path.endsWith(".md", ignoreCase = true) -> out += "$base/$path"
                path.isNotBlank() -> out += "$base/$path/SKILL.md"
                else -> {
                    out += "$base/SKILL.md"
                    out += "$base/skills/SKILL.md"
                }
            }
        }
    }

    private fun looksLikeSkillMarkdown(text: String): Boolean {
        val t = text.trim().removePrefix("\uFEFF")
        if (!t.startsWith("---")) return false
        return when (val parsed = SkillPackageCodec.parse(t)) {
            is SkillPackageCodec.ParseResult.Success -> true
            is SkillPackageCodec.ParseResult.Error -> false
        }
    }

    private suspend fun fetchCompanions(
        skillMdUrl: String,
        markdown: String,
        onProgress: AiToolProgressCallback?,
    ): Map<String, String> {
        val parsed = when (val result = SkillPackageCodec.parse(markdown)) {
            is SkillPackageCodec.ParseResult.Success -> result.pkg.skill
            is SkillPackageCodec.ParseResult.Error -> return emptyMap()
        }
        val resources = parsed.docIds().filter { it != SkillPackageCodec.DOC_ID_SKILL }
        if (resources.isEmpty()) return emptyMap()
        val dir = skillMdUrl.substringBeforeLast('/').trimEnd('/')
        if (dir.isBlank() || dir == skillMdUrl) return emptyMap()
        val out = linkedMapOf<String, String>()
        val total = resources.size.coerceAtLeast(1)
        for ((index, id) in resources.withIndex()) {
            val fraction = 0.72f + 0.14f * ((index + 1).toFloat() / total)
            onProgress?.onProgress(fraction, "Resource ${index + 1}/$total: $id")
            val companionUrl = "$dir/$id.md"
            val text = runCatching { httpGetText(companionUrl) }.getOrNull() ?: continue
            if (text.isNotBlank()) out[id] = text
        }
        return out
    }

    private suspend fun httpGetText(
        url: String,
        onBytes: (suspend (read: Long, total: Long) -> Unit)? = null,
    ): String {
        val client = skillHttpClient()
        val response = client.newCallResponse {
            url(url)
        }
        if (!response.isSuccessful) {
            throw IllegalStateException("HTTP ${response.code} for $url")
        }
        val body = response.body.decompressed()
        val total = body.contentLength()
        if (onBytes == null) {
            return body.text()
        }
        val source = body.source()
        val buffer = Buffer()
        var readTotal = 0L
        val chunk = ByteArray(16 * 1024)
        while (true) {
            val read = source.read(chunk)
            if (read == -1) break
            buffer.write(chunk, 0, read)
            readTotal += read
            onBytes(readTotal, total)
        }
        val bytes = Utf8BomUtils.removeUTF8BOM(buffer.readByteArray())
        val charset = body.contentType()?.charset() ?: Charset.forName("UTF-8")
        return String(bytes, charset)
    }

    private fun skillHttpClient(): OkHttpClient {
        val proxy = AppConfig.aiSkillDownloadProxy.trim()
        if (proxy.isBlank()) return okHttpClient
        if (!PROXY_PATTERN.matches(proxy)) {
            throw IllegalArgumentException(
                "Invalid skill download proxy. Use http://host:port or socks5://host:port " +
                    "(optional auth: http://host:port@user@pass)",
            )
        }
        return getProxyClient(proxy)
    }

    fun isValidProxy(proxy: String): Boolean {
        val trimmed = proxy.trim()
        return trimmed.isEmpty() || PROXY_PATTERN.matches(trimmed)
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> String.format("%.1fKB", bytes / 1024.0)
        else -> String.format("%.1fMB", bytes / (1024.0 * 1024.0))
    }
}
