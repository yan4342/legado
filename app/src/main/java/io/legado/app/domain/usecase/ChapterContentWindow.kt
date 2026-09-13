package io.legado.app.domain.usecase

import io.legado.app.utils.ChineseUtils

/**
 * Pure helpers for chapter text windowing and keyword snippets (AI tools / tests).
 */
object ChapterContentWindow {

    enum class MatchMode {
        OR,
        AND,
        PHRASE,
        ;

        companion object {
            fun parse(raw: String?): MatchMode = when (raw?.trim()?.lowercase()) {
                "and" -> AND
                "phrase" -> PHRASE
                else -> OR
            }
        }
    }

    data class Slice(
        val content: String,
        val offset: Int,
        val contentLength: Int,
        val truncatedStart: Boolean,
        val truncatedEnd: Boolean,
    ) {
        val truncated: Boolean get() = truncatedStart || truncatedEnd
    }

    /** One query term or quoted phrase. */
    data class QueryTerm(
        val text: String,
        val isPhrase: Boolean = false,
    )

    data class ParsedQuery(
        val terms: List<QueryTerm>,
        val matchMode: MatchMode,
    ) {
        val keywords: List<String> get() = terms.map { it.text }
        val isEmpty: Boolean get() = terms.isEmpty()
    }

    data class MultiMatch(
        /** Earliest offset among all keyword hits (for get_chapter_content). */
        val charOffset: Int,
        /** Total raw occurrences of all keywords. */
        val matchCount: Int,
        /** Keywords that appear at least once. */
        val matchedKeywords: List<String>,
        /**
         * Higher = better. AND (all terms present) ranks above partial OR;
         * more distinct terms and more occurrences bump the score.
         */
        val score: Int,
        /** Prefer snippet around the last matched keyword for multi-term queries. */
        val snippetQuery: String,
    )

    /** One occurrence hit within a chapter (flat search results). */
    data class OccurrenceHit(
        val charOffset: Int,
        val snippet: String,
        val snippetQuery: String,
        val matchedKeywords: List<String>,
        val score: Int,
        val resultCountWithinChapter: Int,
    )

    data class ChapterMatchResult(
        val matchCount: Int,
        val matchedKeywords: List<String>,
        val score: Int,
        val occurrences: List<OccurrenceHit>,
    )

    fun slice(text: String, offset: Int, maxChars: Int): Slice {
        val len = text.length
        val start = offset.coerceIn(0, len)
        val max = maxChars.coerceAtLeast(0)
        val end = (start + max).coerceAtMost(len)
        return Slice(
            content = text.substring(start, end),
            offset = start,
            contentLength = len,
            truncatedStart = start > 0,
            truncatedEnd = end < len,
        )
    }

    /** Split on whitespace; keep order, drop blanks, cap count. */
    fun parseKeywords(query: String, maxTerms: Int = MAX_KEYWORDS): List<String> =
        parseQuery(query, MatchMode.OR, maxTerms).keywords

    /**
     * Parse query into terms/phrases.
     * Quoted `"..."` segments are phrases; remaining whitespace tokens are terms.
     * [matchMode] PHRASE treats the whole query (quotes stripped) as one phrase.
     */
    fun parseQuery(
        query: String,
        matchMode: MatchMode = MatchMode.OR,
        maxTerms: Int = MAX_KEYWORDS,
    ): ParsedQuery {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return ParsedQuery(emptyList(), matchMode)
        if (matchMode == MatchMode.PHRASE) {
            val phrase = trimmed.replace("\"", "").trim()
            return if (phrase.isEmpty()) {
                ParsedQuery(emptyList(), matchMode)
            } else {
                ParsedQuery(listOf(QueryTerm(phrase, isPhrase = true)), matchMode)
            }
        }
        val terms = mutableListOf<QueryTerm>()
        val seen = linkedSetOf<String>()
        var i = 0
        while (i < trimmed.length && terms.size < maxTerms) {
            while (i < trimmed.length && trimmed[i].isWhitespace()) i++
            if (i >= trimmed.length) break
            if (trimmed[i] == '"') {
                val end = trimmed.indexOf('"', i + 1)
                val phrase = if (end > i) {
                    trimmed.substring(i + 1, end).trim()
                } else {
                    trimmed.substring(i + 1).trim()
                }
                i = if (end > i) end + 1 else trimmed.length
                if (phrase.isNotEmpty() && seen.add(phrase)) {
                    terms.add(QueryTerm(phrase, isPhrase = true))
                }
            } else {
                val start = i
                while (i < trimmed.length && !trimmed[i].isWhitespace() && trimmed[i] != '"') i++
                val token = trimmed.substring(start, i).trim()
                if (token.isNotEmpty() && seen.add(token)) {
                    terms.add(QueryTerm(token, isPhrase = false))
                }
            }
        }
        return ParsedQuery(terms, matchMode)
    }

    fun findOffsets(content: String, query: String): List<Int> {
        val key = query.trim()
        if (key.isEmpty() || content.isEmpty()) return emptyList()
        val positions = mutableListOf<Int>()
        var index = content.indexOf(key, ignoreCase = true)
        while (index >= 0) {
            positions.add(index)
            index = content.indexOf(key, index + key.length, ignoreCase = true)
        }
        return positions
    }

    /**
     * Find offsets with 繁简 variants: raw / t2s / s2t query against content;
     * when chinese converter is off, also try length-preserving t2s(content).
     */
    fun findOffsetsNormalized(
        content: String,
        query: String,
        chineseConverterType: Int = 0,
    ): List<Int> {
        val key = query.trim()
        if (key.isEmpty() || content.isEmpty()) return emptyList()
        val variants = queryVariants(key)
        val fromContent = linkedSetOf<Int>()
        for (v in variants) {
            fromContent.addAll(findOffsets(content, v))
        }
        if (fromContent.isNotEmpty()) return fromContent.toList().sorted()

        if (chineseConverterType == 0) {
            val t2sContent = runCatching { ChineseUtils.t2s(content) }.getOrNull() ?: return emptyList()
            if (t2sContent.length != content.length) return emptyList()
            val t2sKey = runCatching { ChineseUtils.t2s(key) }.getOrDefault(key)
            val mapped = findOffsets(t2sContent, t2sKey)
            if (mapped.isNotEmpty()) return mapped
        }
        return emptyList()
    }

    fun queryVariants(query: String): List<String> {
        val key = query.trim()
        if (key.isEmpty()) return emptyList()
        val out = linkedSetOf(key)
        runCatching { ChineseUtils.t2s(key) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        runCatching { ChineseUtils.s2t(key) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        return out.toList()
    }

    /**
     * OR match: chapter hits if any keyword appears.
     * Score boosts chapters that contain more distinct keywords (AND-ish ranking).
     */
    fun matchKeywords(
        content: String,
        keywords: List<String>,
        chapterTitle: String = "",
    ): MultiMatch? {
        if (keywords.isEmpty() || content.isEmpty()) return null
        val parsed = ParsedQuery(keywords.map { QueryTerm(it) }, MatchMode.OR)
        val chapter = matchChapter(content, parsed, chapterTitle, hitsPerChapter = 1) ?: return null
        val first = chapter.occurrences.firstOrNull()
        return MultiMatch(
            charOffset = first?.charOffset ?: 0,
            matchCount = chapter.matchCount,
            matchedKeywords = chapter.matchedKeywords,
            score = chapter.score,
            snippetQuery = first?.snippetQuery ?: chapter.matchedKeywords.last(),
        )
    }

    /**
     * Aho-Corasick 批量匹配：单遍扫描 content，返回每个 term 的命中 offset 列表。
     * 语义与逐 term 调用 [findOffsetsNormalized] 等价：
     * 1) 每个 term 的所有繁简变体（原词/t2s/s2t）命中即计入；
     * 2) 变体全部未命中的 term，在 [chineseConverterType]==0 时回退到对内容做
     *    长度安全的 t2s 后再扫一次（与旧逻辑的 fallback 一致）。
     * 总体复杂度由 O(词数 × 章节长度) 降为 O(章节长度 + 命中数)。
     */
    fun matchTermOffsets(
        content: String,
        terms: List<QueryTerm>,
        chineseConverterType: Int = 0,
    ): Map<String, List<Int>> {
        if (content.isEmpty() || terms.isEmpty()) return emptyMap()

        // 与 findOffsetsNormalized 保持一致的大小写不敏感语义：
        // 内容与 pattern 均小写化后扫描；小写化必须保持长度，否则 offset 错位。
        val lower = content.lowercase()
        val contentScannable = lower.length == content.length

        val matcher = AhoCorasick()
        terms.forEachIndexed { index, term ->
            for (v in queryVariants(term.text)) {
                val lv = v.lowercase()
                // 小写化后长度变化的变体无法正确换算 offset，跳过，由逐词 fallback 兜底
                if (lv.isNotEmpty() && lv.length == v.length) {
                    matcher.addPattern(lv, tag = index)
                }
            }
        }
        matcher.build()

        val result = mutableMapOf<String, MutableList<Int>>()
        if (contentScannable) {
            for ((tag, offset) in matcher.scan(lower)) {
                result.getOrPut(terms[tag].text) { mutableListOf() }.add(offset)
            }
        } else {
            // 罕见的大小写折叠场景（如某些扩展字符小写后变长），回退逐词扫描保证 offset 正确
            for (term in terms) {
                val offsets = findOffsetsNormalized(content, term.text, chineseConverterType)
                if (offsets.isNotEmpty()) result[term.text] = offsets.toMutableList()
            }
            return result.mapValues { (_, offsets) -> offsets.sorted().distinct() }
        }

        // 变体未命中且允许回退的 term：对内容做 t2s 后补扫
        val missing = terms.filter { result[it.text].isNullOrEmpty() }
        if (missing.isNotEmpty() && chineseConverterType == 0) {
            val t2sContent = runCatching { ChineseUtils.t2s(content) }.getOrNull()
            if (t2sContent != null && t2sContent.length == content.length) {
                val t2sLower = t2sContent.lowercase()
                if (t2sLower.length == t2sContent.length) {
                    val matcher2 = AhoCorasick()
                    missing.forEachIndexed { index, term ->
                        val t2sKey = runCatching { ChineseUtils.t2s(term.text) }.getOrDefault(term.text)
                        val t2sKeyLower = t2sKey.lowercase()
                        if (t2sKeyLower.isNotBlank() && t2sKeyLower.length == t2sKey.length) {
                            matcher2.addPattern(t2sKeyLower, tag = index)
                        }
                    }
                    matcher2.build()
                    for ((tag, offset) in matcher2.scan(t2sLower)) {
                        result.getOrPut(missing[tag].text) { mutableListOf() }.add(offset)
                    }
                }
            }
        }
        return result.mapValues { (_, offsets) -> offsets.sorted().distinct() }
    }

    /**
     * Match a chapter and return up to [hitsPerChapter] occurrence hits.
     */
    fun matchChapter(
        content: String,
        parsed: ParsedQuery,
        chapterTitle: String = "",
        hitsPerChapter: Int = DEFAULT_HITS_PER_CHAPTER,
        /** AppConfig.chineseConverterType; 0 enables length-safe t2s(content) fallback. */
        chineseConverterType: Int = 0,
    ): ChapterMatchResult? {
        if (parsed.isEmpty || content.isEmpty()) return null
        val cap = hitsPerChapter.coerceIn(1, MAX_HITS_PER_CHAPTER)
        val terms = parsed.terms
        val mode = parsed.matchMode

        data class TermHit(val term: QueryTerm, val offsets: List<Int>)

        val offsetsByTerm = matchTermOffsets(content, terms, chineseConverterType)
        val termHits = terms.map { term ->
            TermHit(term, offsetsByTerm[term.text].orEmpty())
        }
        val matched = termHits.filter { it.offsets.isNotEmpty() }
        if (matched.isEmpty()) return null

        val allMatched = matched.size == terms.size
        when (mode) {
            MatchMode.AND, MatchMode.PHRASE -> if (!allMatched) return null
            MatchMode.OR -> Unit
        }

        var titleHits = 0
        for (term in terms) {
            if (findOffsetsNormalized(chapterTitle, term.text, chineseConverterType).isNotEmpty() ||
                chapterTitle.contains(term.text, ignoreCase = true)
            ) {
                titleHits++
            }
        }

        val totalCount = matched.sumOf { it.offsets.size }
        val distinct = matched.size
        val score =
            (if (allMatched && terms.size > 1) AND_BONUS else if (allMatched && mode != MatchMode.OR) AND_BONUS / 2 else 0) +
                distinct * DISTINCT_WEIGHT +
                totalCount +
                titleHits * TITLE_WEIGHT

        val matchedKeywords = matched.map { it.term.text }
        val primary = matched.first()
        val occurrenceOffsets = linkedMapOf<Int, String>() // offset -> snippetQuery

        fun addOffsets(termHit: TermHit) {
            val surface = surfaceFormAt(content, termHit.offsets.firstOrNull() ?: return, termHit.term.text)
                ?: termHit.term.text
            for (off in termHit.offsets) {
                if (occurrenceOffsets.size >= cap) return
                occurrenceOffsets.putIfAbsent(off, surfaceFormAt(content, off, termHit.term.text) ?: surface)
            }
        }

        addOffsets(primary)
        if (mode == MatchMode.OR && occurrenceOffsets.size < cap) {
            for (th in matched.drop(1)) {
                addOffsets(th)
                if (occurrenceOffsets.size >= cap) break
            }
        }

        val occurrences = occurrenceOffsets.entries.mapIndexed { index, (offset, snippetQuery) ->
            OccurrenceHit(
                charOffset = offset,
                snippet = snippetAroundOffset(content, offset, snippetQuery.length),
                snippetQuery = snippetQuery,
                matchedKeywords = matchedKeywords,
                score = score,
                resultCountWithinChapter = index,
            )
        }
        if (occurrences.isEmpty()) return null

        return ChapterMatchResult(
            matchCount = totalCount,
            matchedKeywords = matchedKeywords,
            score = score,
            occurrences = occurrences,
        )
    }

    fun snippetAround(text: String, query: String, maxLen: Int = SNIPPET_MAX): String {
        val compact = text.replace("\n", " ").trim()
        if (compact.isBlank()) return ""
        val key = query.trim()
        if (key.isEmpty()) return compact.take(maxLen)
        val idx = compact.indexOf(key, ignoreCase = true)
        if (idx < 0) return compact.take(maxLen)
        return windowAround(compact, idx, key.length, maxLen)
    }

    fun snippetAroundOffset(
        text: String,
        offset: Int,
        matchLen: Int,
        maxLen: Int = SNIPPET_MAX,
    ): String {
        val compact = text.replace("\n", " ")
        if (compact.isBlank()) return ""
        val idx = offset.coerceIn(0, compact.length)
        val len = matchLen.coerceAtLeast(1).coerceAtMost((compact.length - idx).coerceAtLeast(1))
        return windowAround(compact, idx, len, maxLen)
    }

    private fun windowAround(compact: String, idx: Int, matchLen: Int, maxLen: Int): String {
        val half = (maxLen - matchLen).coerceAtLeast(0) / 2
        val start = (idx - half).coerceAtLeast(0)
        val end = (start + maxLen).coerceAtMost(compact.length)
        val slice = compact.substring(start, end).trim()
        return buildString {
            if (start > 0) append('…')
            append(slice)
            if (end < compact.length) append('…')
        }
    }

    private fun surfaceFormAt(content: String, offset: Int, query: String): String? {
        val variants = queryVariants(query)
        for (v in variants) {
            if (offset + v.length <= content.length) {
                val slice = content.substring(offset, offset + v.length)
                if (slice.equals(v, ignoreCase = true)) return slice
            }
        }
        // Fallback: longest variant that matches ignore-case at offset via content scan
        val fromOffsets = findOffsetsNormalized(content, query)
        if (offset !in fromOffsets) return query
        for (v in variants.sortedByDescending { it.length }) {
            if (offset + v.length <= content.length) {
                val slice = content.substring(offset, offset + v.length)
                if (slice.equals(v, ignoreCase = true)) return slice
            }
        }
        return query
    }

    const val SNIPPET_MAX = 800
    const val MAX_KEYWORDS = 8
    const val DEFAULT_HITS_PER_CHAPTER = 3
    const val MAX_HITS_PER_CHAPTER = 10
    private const val AND_BONUS = 10_000
    private const val DISTINCT_WEIGHT = 100
    private const val TITLE_WEIGHT = 20
}
