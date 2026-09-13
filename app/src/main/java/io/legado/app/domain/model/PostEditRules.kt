package io.legado.app.domain.model

/** Post-edit trigger regex ↔ polish rule, linked by [id]. */
data class PostEditRule(
    val id: String,
    val regex: String,
    /** Short pattern label shown in UI and sent to the sub-model when matched. */
    val label: String,
)

sealed class PostEditTriggerResult {
    data object Skip : PostEditTriggerResult()
    /** Blank trigger template — polish using all mapped instructions. */
    data object RunAll : PostEditTriggerResult()
    /** Trigger template without @id tags — use full prompt when any pattern matches. */
    data object LegacyMatch : PostEditTriggerResult()
    data class Matched(val ids: List<String>) : PostEditTriggerResult()
}

data class PostEditLinkageRow(
    val id: String,
    val regex: String?,
    val label: String?,
    val inTrigger: Boolean,
    val inPrompt: Boolean,
) {
    val isLinked: Boolean get() = inTrigger && inPrompt
}

data class PostEditLinkageReport(
    val rows: List<PostEditLinkageRow>,
    val legacyTrigger: Boolean,
    val warnings: List<String>,
)

data class ParsedPostEditPrompt(
    val header: String,
    val ruleIds: Set<String>,
    val overrides: Map<String, String>,
)

data class MatchedSentence(
    val range: IntRange,
    /** Full sentence text as it appears in the source. */
    val text: String,
    /** Body sent to the sub-model (speaker prefix / @mentions stripped). */
    val polishText: String,
    val prefix: String,
    val suffix: String,
    val matchedRuleIds: List<String>,
)

object PostEditRules {

    /** Visible delimiter — control chars like U+0001 are often stripped by chat models. */
    const val SENTENCE_SEPARATOR = "<<<SENT>>>"

    private const val PROMPT_HEADER = """局部润色正文，不改剧情、人称、语气、篇幅。
禁止：改剧情/视角/时态/大幅增删；禁止添加或修改角色名、[角色名]:、@提及。
仅输出修订后正文；无需修改则原样输出。"""

    /** Shared fix guidance — not repeated per rule. */
    private const val FIX_GUIDE =
        "修正方式：删繁就简，去掉命中句式（如“不是…是…”、“不是…而是…”）、破折号滥用与不必要的比喻，改为自然直述。"

    val ALL: List<PostEditRule> = listOf(
        PostEditRule("not_not", """不是[^。！？]{1,40}?不是""", "不是…不是…"),
        PostEditRule("not_just", """不是[^。！？]{1,40}?只是""", "不是…只是…"),
        PostEditRule("is_is", """是[^。！？]{1,40}?是""", "是…是…"),
        PostEditRule("not_only_more", """不仅[^。！？]{1,40}?更[是有]""", "不仅…更…"),
        PostEditRule("not_is", """不是[^。！？]{1,40}?是""", "不是…是… / 不是…而是…"),
        PostEditRule("em_dash_double", """(?:——)""", "破折号 ——"),
        PostEditRule("em_dash_triple", """(?:—).*(?:—).*(?:—)""", "句内多处 —"),
        PostEditRule("like_xiangshi", """(?:像是)""", "像是"),
        PostEditRule("like_xiang", """(?:(?<!好)像(?!似))""", "像"),
        PostEditRule("fangfu", """(?:仿佛)""", "仿佛"),
    )

    private val rulesById: Map<String, PostEditRule> = ALL.associateBy { it.id }

    fun defaultTriggerRegex(): String = buildString {
        appendLine("# 以 # 开头的行是注释。整段留空 = 每条都润色。")
        appendLine("# 格式：# @ruleId（须与 POST_EDIT_PROMPT 中 @id 一致）")
        ALL.forEach { rule ->
            appendLine("# @${rule.id}")
            appendLine(rule.regex)
        }
    }.trimEnd()

    fun defaultPrompt(): String = buildString {
        appendLine(PROMPT_HEADER)
        appendLine()
        appendLine(FIX_GUIDE)
    }.trimEnd()

    /** Shown in Prompt Template UI only; not sent to the sub-model. */
    const val PROMPT_RULE_ID_HINT =
        "规则 id 与触发正则 # @id 一一对应。Prompt 只需写润色说明；可选写 - @id：覆盖某条规则的 label。"

    fun evaluateTrigger(text: String, triggerRaw: String): PostEditTriggerResult {
        if (triggerRaw.isBlank()) {
            return if (extractMatchedSentences(text, triggerRaw).isNotEmpty()) {
                PostEditTriggerResult.RunAll
            } else {
                PostEditTriggerResult.Skip
            }
        }
        val entries = parseTriggers(triggerRaw)
        if (entries.isEmpty()) {
            return if (extractMatchedSentences(text, triggerRaw).isNotEmpty()) {
                PostEditTriggerResult.RunAll
            } else {
                PostEditTriggerResult.Skip
            }
        }
        val hasRuleIds = entries.any { it.id != null }
        if (!hasRuleIds) {
            val matched = extractMatchedSentences(text, triggerRaw)
            return if (matched.isNotEmpty()) PostEditTriggerResult.LegacyMatch else PostEditTriggerResult.Skip
        }
        val matchedIds = extractMatchedSentences(text, triggerRaw)
            .flatMap { it.matchedRuleIds }
            .distinct()
        return if (matchedIds.isEmpty()) {
            PostEditTriggerResult.Skip
        } else {
            PostEditTriggerResult.Matched(matchedIds)
        }
    }

    /** Sentence ranges in [text] whose content matches at least one trigger pattern. */
    fun extractMatchedSentences(text: String, triggerRaw: String): List<MatchedSentence> {
        val patterns = resolveTriggerPatterns(triggerRaw)
        if (patterns.isEmpty()) return emptyList()
        return sentenceRanges(text).mapNotNull { range ->
            val sentence = text.substring(range)
            if (sentence.isBlank()) return@mapNotNull null
            val (prefix, body, suffix) = splitSentenceForPolish(sentence)
            if (body.isBlank()) return@mapNotNull null
            val matchedIds = patterns.mapNotNull { (id, regex) ->
                if (regexMatches(regex, body)) id else null
            }.distinct()
            if (matchedIds.isEmpty()) {
                null
            } else {
                MatchedSentence(
                    range = range,
                    text = sentence,
                    polishText = body,
                    prefix = prefix,
                    suffix = suffix,
                    matchedRuleIds = matchedIds,
                )
            }
        }
    }

    fun buildSentencePolishInstructions(promptRaw: String, trigger: PostEditTriggerResult): String {
        return buildString {
            append(buildInstructions(promptRaw, trigger).trimEnd())
            appendLine()
            appendLine()
            appendLine("用户消息中多条待润色句子以分隔符 $SENTENCE_SEPARATOR 连接。")
            appendLine("仅润色这些句子，用相同分隔符按相同顺序输出，句数必须一致。")
            appendLine("不要解释、不要编号、不要添加角色名或 @提及、不要用空行分段代替分隔符。")
            appendLine("示例（2句）：润色后句一$SENTENCE_SEPARATOR 润色后句二")
        }
    }

    fun formatSentencePayload(sentences: List<MatchedSentence>): String =
        sentences.joinToString(SENTENCE_SEPARATOR) { it.polishText }

    fun mergePolishedSentence(sentence: MatchedSentence, polishedBody: String): String =
        sentence.prefix + polishedBody + sentence.suffix

    fun parseSentencePayload(raw: String, expectedCount: Int): List<String>? {
        val cleaned = stripCodeFence(raw.trim())
        if (cleaned.contains(SENTENCE_SEPARATOR)) {
            val parts = cleaned.split(SENTENCE_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size == expectedCount) return parts
        }
        // Single sentence: model may omit separator when only one item is expected.
        if (expectedCount == 1 && cleaned.isNotEmpty() && !cleaned.contains(SENTENCE_SEPARATOR)) {
            return listOf(cleaned)
        }
        return null
    }

    private fun stripCodeFence(text: String): String {
        val fence = Regex("""^```(?:\w+)?\s*\n?(.*?)\n?```\s*$""", RegexOption.DOT_MATCHES_ALL)
        return fence.matchEntire(text)?.groupValues?.get(1)?.trim() ?: text
    }

    fun applySentenceEdits(text: String, replacements: List<Pair<IntRange, String>>): String {
        if (replacements.isEmpty()) return text
        val sb = StringBuilder(text)
        replacements.sortedByDescending { it.first.first }.forEach { (range, newText) ->
            sb.replace(range.first, range.last + 1, newText)
        }
        return sb.toString()
    }

    internal fun sentenceRanges(text: String): List<IntRange> {
        if (text.isEmpty()) return emptyList()
        val ranges = mutableListOf<IntRange>()
        var segmentStart = 0
        var i = 0
        while (i < text.length) {
            when (text[i]) {
                '\n' -> {
                    if (i > segmentStart) ranges += segmentStart..i
                    segmentStart = i + 1
                }
                '。', '！', '？', '…' -> {
                    ranges += segmentStart..i
                    segmentStart = i + 1
                }
            }
            i++
        }
        if (segmentStart < text.length) {
            val tail = text.substring(segmentStart)
            if (tail.isNotBlank()) ranges += segmentStart..text.lastIndex
        }
        return ranges.filter { text.substring(it).isNotBlank() }
    }

    private fun resolveTriggerPatterns(triggerRaw: String): List<Pair<String?, String>> {
        if (triggerRaw.isBlank()) return ALL.map { it.id to it.regex }
        return parseTriggers(triggerRaw).map { it.id to it.regex }
    }

    private fun regexMatches(pattern: String, text: String): Boolean =
        runCatching { Regex(pattern).containsMatchIn(text) }.getOrDefault(false)

    private val SPEAKER_PREFIX = Regex("""^(\[[^\]]+\]:|[^：\n]{1,20}：)""")
    private val MENTION_SUFFIX = Regex("""(\s+@[^\s。！？…\n]+)+$""")

    private fun splitSentenceForPolish(sentence: String): Triple<String, String, String> {
        val prefix = SPEAKER_PREFIX.find(sentence)?.value.orEmpty()
        var body = sentence.removePrefix(prefix)
        val suffix = MENTION_SUFFIX.find(body)?.value.orEmpty()
        body = body.removeSuffix(suffix)
        return Triple(prefix, body, suffix)
    }

    fun buildInstructions(promptRaw: String, trigger: PostEditTriggerResult): String {
        val parsed = parsePrompt(promptRaw)
        if (trigger is PostEditTriggerResult.Skip) return promptRaw
        val header = parsed.header.ifBlank { "$PROMPT_HEADER\n\n$FIX_GUIDE" }
        val labels = when (trigger) {
            is PostEditTriggerResult.RunAll,
            is PostEditTriggerResult.LegacyMatch,
            -> allRuleLabels(parsed)
            is PostEditTriggerResult.Matched -> trigger.ids.map { ruleLabel(it, parsed) }
        }
        if (labels.isEmpty()) return promptRaw
        return buildString {
            append(header.trimEnd())
            appendLine()
            appendLine()
            append("本次命中：")
            appendLine(labels.joinToString("、"))
        }
    }

    fun linkageReport(promptRaw: String, triggerRaw: String): PostEditLinkageReport {
        val parsed = parsePrompt(promptRaw)
        val effectivePromptIds = effectivePromptRuleIds(parsed)
        val triggerEntries = parseTriggers(triggerRaw)
        val triggerRules = triggerEntries.mapNotNull { entry ->
            entry.id?.let { it to entry.regex }
        }.toMap()
        val legacyTrigger = triggerRaw.isNotBlank() &&
            triggerEntries.isNotEmpty() &&
            triggerEntries.none { it.id != null }
        val explicitPromptRules = parsed.ruleIds.isNotEmpty() || parsed.overrides.isNotEmpty()
        val allIds = (rulesById.keys + effectivePromptIds + triggerRules.keys).sorted()
        val rows = allIds.map { id ->
            PostEditLinkageRow(
                id = id,
                regex = triggerRules[id] ?: rulesById[id]?.regex,
                label = ruleLabelOrNull(id, parsed),
                inTrigger = id in triggerRules,
                inPrompt = id in effectivePromptIds,
            )
        }
        val warnings = buildList {
            if (legacyTrigger) {
                add("Trigger regex has no @id tags; any match uses the full prompt.")
            }
            if (explicitPromptRules) {
                triggerRules.keys.subtract(effectivePromptIds).forEach { id ->
                    add("@$id is in trigger regex but missing from prompt.")
                }
                effectivePromptIds.subtract(triggerRules.keys).forEach { id ->
                    add("@$id is in prompt but missing from trigger regex.")
                }
            }
        }
        return PostEditLinkageReport(rows = rows, legacyTrigger = legacyTrigger, warnings = warnings)
    }

    fun parseTriggerRuleIds(triggerRaw: String): Map<String, String> =
        parseTriggers(triggerRaw).mapNotNull { entry ->
            entry.id?.let { it to entry.regex }
        }.toMap()

    fun parsePromptRuleIds(promptRaw: String): Set<String> = parsePrompt(promptRaw).ruleIds

    fun parsePrompt(promptRaw: String): ParsedPostEditPrompt {
        val ruleIds = linkedSetOf<String>()
        val overrides = linkedMapOf<String, String>()
        val headerLines = mutableListOf<String>()
        var inRules = false
        for (line in promptRaw.lineSequence()) {
            val trimmed = line.trim()
            when {
                PROMPT_RULE_LINE.matchEntire(trimmed) != null -> {
                    inRules = true
                    val match = PROMPT_RULE_LINE.matchEntire(trimmed)!!
                    val id = match.groupValues[1]
                    ruleIds += id
                    val override = match.groupValues[2].trim()
                    if (override.isNotEmpty()) overrides[id] = override
                }
                PROMPT_ID_LINE.matchEntire(trimmed) != null -> {
                    inRules = true
                    ruleIds += PROMPT_ID_LINE.matchEntire(trimmed)!!.groupValues[1]
                }
                !inRules -> headerLines += line
            }
        }
        return ParsedPostEditPrompt(
            header = headerLines.joinToString("\n").trimEnd(),
            ruleIds = ruleIds,
            overrides = overrides,
        )
    }

    private fun allRuleLabels(parsed: ParsedPostEditPrompt): List<String> {
        val ids = effectivePromptRuleIds(parsed).toList().sorted()
        return ids.map { ruleLabel(it, parsed) }
    }

    private fun effectivePromptRuleIds(parsed: ParsedPostEditPrompt): Set<String> =
        if (parsed.ruleIds.isNotEmpty() || parsed.overrides.isNotEmpty()) {
            parsed.ruleIds + parsed.overrides.keys
        } else {
            rulesById.keys
        }

    private fun ruleLabel(id: String, parsed: ParsedPostEditPrompt): String =
        parsed.overrides[id] ?: rulesById[id]?.label ?: id

    private fun ruleLabelOrNull(id: String, parsed: ParsedPostEditPrompt): String? =
        parsed.overrides[id] ?: rulesById[id]?.label

    private data class TriggerEntry(val id: String?, val regex: String)

    private fun parseTriggers(raw: String): List<TriggerEntry> {
        val entries = mutableListOf<TriggerEntry>()
        var pendingId: String? = null
        for (line in raw.lineSequence().map { it.trim() }) {
            when {
                line.isEmpty() -> Unit
                line.startsWith("# @") -> {
                    pendingId = line.removePrefix("#").trim().removePrefix("@")
                }
                line.startsWith("#") -> Unit
                else -> {
                    entries += TriggerEntry(pendingId, line)
                    pendingId = null
                }
            }
        }
        return entries
    }

    private val PROMPT_RULE_LINE = Regex("""^- @(\w+)(?:\s*[：:]\s*(.*))?$""")
    private val PROMPT_ID_LINE = Regex("""^@(\w+)$""")
}
