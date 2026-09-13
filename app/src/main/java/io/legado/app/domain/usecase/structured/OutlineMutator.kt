package io.legado.app.domain.usecase.structured

import io.legado.app.data.entities.AiOutline
import io.legado.app.data.entities.AiPromptTemplate
import io.legado.app.domain.gateway.AiChatGateway
import io.legado.app.domain.gateway.AiOutlineGateway
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiPromptTemplateGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.AiToolConfigGateway
import io.legado.app.domain.gateway.AiWorkspaceGateway
import io.legado.app.domain.model.AiCallSource
import io.legado.app.domain.model.OutlinePatchMode
import io.legado.app.domain.usecase.WritingWorkspaceIndexBuilder
import io.legado.app.domain.usecase.ai.AiStreamPartialCallback
import io.legado.app.domain.usecase.ai.AiStreamingTextHelper

class OutlineMutator(
    private val gateway: AiOutlineGateway,
    private val aiChatGateway: AiChatGateway,
    private val aiProfileGateway: AiProfileGateway,
    private val aiTextGateway: AiTextGateway,
    private val promptTemplateGateway: AiPromptTemplateGateway,
    private val toolConfigGateway: AiToolConfigGateway,
    private val structuredDataPreviewer: StructuredDataPreviewer,
    private val workspaceGateway: AiWorkspaceGateway,
    private val workspaceIndexBuilder: WritingWorkspaceIndexBuilder,
) {
    companion object {
        private const val TAG = "OutlineMutator"
        private const val DEFAULT_OFFSET_PAGE_LIMIT = 2000
        /** Recent dialogue kept verbatim (same page size as gateway default). */
        private const val RECENT_PAGE_SIZE = 30
        private const val MSG_CHAR_CAP = 2000
        /** Budget for the recent-dialogue block so early-plot digests keep their share. */
        private const val RECENT_DIALOGUE_CHAR_CAP = 12000
        /** Older pages summarized one round each (oldest → newer), not dumped raw. */
        private const val MAX_DIGEST_ROUNDS = 4

        /**
         * Apply [ops] to outline markdown. On engine failure returns [Result.failure]
         * (does not silently keep the previous content).
         */
        fun applyGraphOpsContent(
            current: String,
            ops: List<io.legado.app.domain.usecase.structured.graph.OutlineGraphOp>,
        ): Result<String> {
            val decoded = io.legado.app.domain.usecase.structured.graph.OutlineGraphCodec.decode(current)
            val graph = decoded.graph
                ?: io.legado.app.domain.usecase.structured.graph.OutlineGraph.empty()
            return when (
                val result = io.legado.app.domain.usecase.structured.graph.OutlineGraphEngine.applyAll(
                    graph = graph,
                    ops = ops,
                    allowSelect = false,
                )
            ) {
                is io.legado.app.domain.usecase.structured.graph.OutlineGraphResult.Ok -> {
                    val encoded = io.legado.app.domain.usecase.structured.graph.OutlineGraphCodec.encode(
                        io.legado.app.domain.usecase.structured.graph.OutlineGraphEngine
                            .normalizeDerivedState(result.graph),
                    )
                    Result.success(encoded)
                }
                is io.legado.app.domain.usecase.structured.graph.OutlineGraphResult.Err ->
                    Result.failure(IllegalStateException(result.message))
            }
        }
    }

    data class PatchResult(
        val success: Boolean,
        val message: String,
        val content: String = "",
        val changes: List<FieldChange> = emptyList(),
        val saved: Boolean = false,
    )

    suspend fun getContent(conversationId: String): String =
        gateway.getByConversation(conversationId)?.content.orEmpty()

    suspend fun getSnapshot(conversationId: String): Pair<String, Boolean> {
        val outline = gateway.getByConversation(conversationId)
        return outline?.content.orEmpty() to (outline?.enabled == true)
    }

    suspend fun read(
        conversationId: String,
        offset: Int = 0,
        limit: Int = 0,
    ): String {
        val outline = gateway.getByConversation(conversationId)
        val content = outline?.content.orEmpty()
        val enabled = outline?.enabled == true
        val bookMeta = mapOf(
            "bookUrl" to outline?.bookUrl.orEmpty(),
            "bookName" to outline?.bookName.orEmpty(),
            "bookAuthor" to outline?.bookAuthor.orEmpty(),
        )
        val parsed = OutlineParser.parse(content)
        // current_node is graph-only; the rest come from the shared YAML parse above.
        val currentNode = io.legado.app.domain.usecase.structured.graph.OutlineGraphCodec
            .decode(content).graph?.currentNodeId.orEmpty()
        val anchors = mapOf(
            "premise" to parsed.premise,
            "current" to parsed.currentProgress,
            "next" to parsed.nextGoal,
            "inProgress" to parsed.inProgress,
            "current_node" to currentNode,
            "awaiting_choice" to parsed.awaitingChoice,
            "active_path" to parsed.activePath,
        )
        val sections = parsed.sections.map { mapOf("title" to it.title, "offset" to it.offset, "level" to it.level) }
        val meta = mapOf(
            "anchors" to anchors,
            "sections" to sections,
            "hierarchyDepth" to parsed.hierarchyDepth,
        )
        val totalChars = content.length
        val safeOffset = offset.coerceIn(0, totalChars)
        val effectiveLimit = when {
            limit > 0 -> limit
            safeOffset > 0 -> DEFAULT_OFFSET_PAGE_LIMIT
            else -> 0
        }
        if (effectiveLimit <= 0) {
            return io.legado.app.utils.GSON.toJson(
                mapOf("content" to content, "enabled" to enabled) + meta + bookMeta,
            )
        }
        val safeLimit = effectiveLimit.coerceIn(1, 8000)
        val slice = content.substring(safeOffset, (safeOffset + safeLimit).coerceAtMost(totalChars))
        val hasMore = safeOffset + slice.length < totalChars
        val nextArgs = if (hasMore) {
            mapOf(
                "conversationId" to conversationId,
                "offset" to (safeOffset + slice.length),
                "limit" to safeLimit,
            )
        } else null
        return io.legado.app.utils.GSON.toJson(
            mapOf(
                "content" to slice,
                "enabled" to enabled,
                "totalChars" to totalChars,
                "offset" to safeOffset,
                "limit" to safeLimit,
                "hasMore" to hasMore,
                "nextArgs" to nextArgs,
            ) + meta + bookMeta,
        )
    }

    suspend fun previewPatch(conversationId: String, mode: OutlinePatchMode): List<FieldChange> {
        val current = gateway.getByConversation(conversationId)?.content.orEmpty()
        if (mode is OutlinePatchMode.Generate) {
            return structuredDataPreviewer.previewOutlineGenerate(conversationId, mode, current)
        }
        val newContent = computeNewContent(current, mode, dryRunGenerate = true)
        return when (mode) {
            is OutlinePatchMode.SearchReplace -> StructuredDataDiff.diffSearchReplaceContext(
                current, mode.search, mode.replace, mode.replaceAll,
            )
            is OutlinePatchMode.Replace, is OutlinePatchMode.Append ->
                StructuredDataDiff.diffTextLines(current, newContent)
            is OutlinePatchMode.PatchSection -> {
                val sectionContent = patchSection(current, mode.sectionTitle, mode.content)
                StructuredDataDiff.diffTextLines(current, sectionContent)
            }
            is OutlinePatchMode.GraphOps ->
                StructuredDataDiff.diffTextLines(current, newContent)
        }
    }

    suspend fun applyPatch(
        conversationId: String,
        mode: OutlinePatchMode,
        bookUrl: String? = null,
        bookName: String? = null,
        bookAuthor: String? = null,
    ): PatchResult {
        val existing = gateway.getByConversation(conversationId)
        val current = existing?.content.orEmpty()
        val enabled = existing?.enabled == true
        val newContent = when (mode) {
            is OutlinePatchMode.Generate -> {
                val sourceId = mode.conversationId.ifBlank { conversationId }
                val resolvedSubMode = resolveWritingSubMode(mode.writingSubMode)
                val generated = generateFromConversation(
                    conversationId = sourceId,
                    hint = mode.hint,
                    supplement = mode.supplement,
                    existingContent = if (mode.supplement) current else null,
                    writingSubMode = resolvedSubMode,
                )
                if (!generated.success) return generated
                normalizeStoredOutline(generated.content)
                    ?: return PatchResult(
                        false,
                        invalidOutlineFormatMessage(
                            io.legado.app.domain.usecase.structured.graph.OutlineGraphCodec
                                .decodeAiOutput(generated.content).error,
                        ),
                    )
            }
            is OutlinePatchMode.GraphOps -> {
                if (mode.parseErrors.isNotEmpty()) {
                    return PatchResult(
                        false,
                        "graph_ops invalid: ${mode.parseErrors.joinToString("; ")}",
                    )
                }
                val raw = applyGraphOpsContent(current, mode.ops).getOrElse { err ->
                    return PatchResult(
                        false,
                        "graph_ops failed: ${err.message ?: "unknown"}",
                    )
                }
                normalizeStoredOutline(raw)
                    ?: return PatchResult(false, invalidOutlineFormatMessage("graph_ops_encode_failed"))
            }
            is OutlinePatchMode.Replace,
            is OutlinePatchMode.Append,
            is OutlinePatchMode.SearchReplace,
            is OutlinePatchMode.PatchSection,
            -> {
                val raw = computeNewContent(current, mode, dryRunGenerate = false)
                normalizeStoredOutline(raw)
                    ?: return PatchResult(
                        false,
                        invalidOutlineFormatMessage(
                            io.legado.app.domain.usecase.structured.graph.OutlineGraphCodec
                                .decodeAiOutput(raw).error,
                        ) + " Prefer mode=generate or mode=graph_ops.",
                    )
            }
        }
        val changes = StructuredDataDiff.diffText(current, newContent, "outline")
        if (conversationId.isBlank()) {
            return PatchResult(true, "Outline not saved: no conversationId", newContent, changes, saved = false)
        }
        // Always persist content. Preserve the user's switch — do not silently enable.
        gateway.upsert(
            AiOutline(
                conversationId = conversationId,
                content = newContent,
                enabled = enabled,
                bookUrl = bookUrl?.takeIf { it.isNotBlank() } ?: existing?.bookUrl.orEmpty(),
                bookName = bookName?.takeIf { it.isNotBlank() } ?: existing?.bookName.orEmpty(),
                bookAuthor = bookAuthor?.takeIf { it.isNotBlank() } ?: existing?.bookAuthor.orEmpty(),
            ),
        )
        return PatchResult(true, "Outline updated", newContent, changes, saved = true)
    }

    /** Decode → normalizeDerivedState → encode; null if not valid outline_format:2. */
    private fun normalizeStoredOutline(raw: String): String? {
        val decoded = io.legado.app.domain.usecase.structured.graph.OutlineGraphCodec.decodeAiOutput(raw)
        val graph = decoded.graph ?: return null
        val normalized = io.legado.app.domain.usecase.structured.graph.OutlineGraphEngine
            .normalizeDerivedState(graph)
        return io.legado.app.domain.usecase.structured.graph.OutlineGraphCodec.encode(normalized)
    }

    /** Validate + normalize model-generated outline content; null when not a valid outline_format:2 doc. */
    fun normalizeGeneratedOutline(raw: String): String? = normalizeStoredOutline(raw)

    private fun invalidOutlineFormatMessage(error: String?): String =
        "Outline format invalid (${error ?: "parse_failed"}). " +
            "Need outline_format:2, closed YAML ---, headings id:…, " +
            "branches as ### id:… 分支：… with - [ ] id:… options."


    suspend fun generateFromConversation(
        conversationId: String,
        hint: String? = null,
        supplement: Boolean = false,
        existingContent: String? = null,
        writingSubMode: String = "",
        outlineKind: String = "",
        onPartial: AiStreamPartialCallback? = null,
    ): PatchResult {
        val contextHint = buildOutlineContextHint(
            conversationId = conversationId,
            supplement = supplement,
            existingContent = existingContent,
            writingSubMode = writingSubMode,
        )
        // 用户在大纲面板选了「线性/分支」时按选中结构出；未选则退回按写作模式决定。
        val templateKey = when {
            outlineKind == io.legado.app.domain.usecase.structured.graph.OutlineGraph.KIND_BRANCHING ->
                AiPromptTemplate.GENERATE_OUTLINE_ROLEPLAY
            outlineKind == io.legado.app.domain.usecase.structured.graph.OutlineGraph.KIND_LINEAR ->
                AiPromptTemplate.GENERATE_OUTLINE_AUTHOR
            writingSubMode == "roleplay" -> AiPromptTemplate.GENERATE_OUTLINE_ROLEPLAY
            writingSubMode == "author" -> AiPromptTemplate.GENERATE_OUTLINE_AUTHOR
            else -> AiPromptTemplate.GENERATE_OUTLINE_PROMPT
        }
        var promptBody = promptTemplateGateway.getPrompt(templateKey)
        if (promptBody.isBlank()) {
            promptBody = promptTemplateGateway.getPrompt(AiPromptTemplate.GENERATE_OUTLINE_PROMPT)
        }
        val formatSpec = promptTemplateGateway.getPrompt(AiPromptTemplate.OUTLINE_FORMAT_SPEC)
        val hierarchyHint = promptTemplateGateway.getPrompt(AiPromptTemplate.OUTLINE_HIERARCHY_HINT_TWO_LEVEL)
        // 强制结构：杜绝「线性+分支」混排，严格按用户点击的结构输出。
        val kindDirective = when (outlineKind) {
            io.legado.app.domain.usecase.structured.graph.OutlineGraph.KIND_BRANCHING ->
                "【结构】必须为分支粗纲：outline_kind: branching；可含「### 分支：」路口与「- [ ]」选项，但整体不得退回纯线性堆叠。"
            io.legado.app.domain.usecase.structured.graph.OutlineGraph.KIND_LINEAR ->
                "【结构】必须为线性粗纲：outline_kind: linear；仅卷/章与要点，禁止出现「分支：」「- [ ]」等任何分支元素。"
            else -> ""
        }
        val system = io.legado.app.domain.usecase.ai.PromptRoleSplit.stripPlaceholders(
            promptBody
                .replace("{outlineFormatSpec}", formatSpec)
                .replace("{outlineHierarchyHint}", hierarchyHint)
                .replace("{contextHint}", "")
                .replace("{hint}", "") +
                if (kindDirective.isNotBlank()) "\n\n$kindDirective" else "",
        )
        val user = buildString {
            append(contextHint.trim())
            if (!hint.isNullOrBlank()) {
                if (isNotEmpty()) append("\n\n")
                append("## Extra Guidance\n").append(hint)
            }
        }.ifBlank { "（无上下文，请根据创作知识生成大纲。）" }

        val config = toolConfigGateway.getByToolName("patch_outline")
        val result = AiStreamingTextHelper.generateWithToolConfig(
            aiTextGateway = aiTextGateway,
            aiProfileGateway = aiProfileGateway,
            systemPrompt = system,
            userPrompt = user,
            config = config,
            callSource = AiCallSource.OUTLINE,
            onPartial = onPartial,
        )
        return result.fold(
            onSuccess = { text ->
                val content = text.trim()
                    .replace(Regex("""^```(?:markdown)?\s*"""), "")
                    .replace(Regex("""\s*```$"""), "")
                    .trim()
                PatchResult(true, "Generated outline", content, listOf(FieldChange("outline", "", content.take(200))))
            },
            onFailure = { PatchResult(false, "AI generation failed: ${it.message}") }
        )
    }

    /**
     * Recent page kept full; older pages compressed via sequential digest rounds
     * (oldest→newer) so long sessions keep early plot without raising raw message limit.
     * Supplement always includes dialogue context alongside the current outline.
     */
    private suspend fun buildOutlineContextHint(
        conversationId: String,
        supplement: Boolean,
        existingContent: String?,
        writingSubMode: String,
    ): String {
        val pages = loadMessagePages(conversationId)
        val recent = pages.firstOrNull().orEmpty()
        val olderNewestFirst = pages.drop(1)
        val digests = mutableListOf<String>()
        if (olderNewestFirst.isNotEmpty()) {
            var prior = ""
            for (page in olderNewestFirst.asReversed()) {
                val digest = digestPlotChunk(page, prior).orEmpty().trim()
                if (digest.isNotEmpty()) {
                    digests.add(digest)
                    prior = digest
                }
            }
        }

        return buildString {
            val workspace = workspaceGateway.getByConversationId(conversationId)
            val workspaceCtx = workspaceIndexBuilder.buildOutlineGenerateContext(
                conversationId = conversationId,
                workspace = workspace,
            )
            if (workspaceCtx.isNotBlank()) {
                append(workspaceCtx)
                append("\n\n请结合以上工作区设定生成/更新大纲（勿照抄记忆表原文作章节）。\n\n")
            }
            if (supplement && !existingContent.isNullOrBlank()) {
                append("## 当前大纲（草稿或现有内容）\n")
                append(existingContent)
                append("\n\n请把以上内容整理并扩写为完整、规范的 outline_format:2 大纲：")
                append("保留原有章节标题与 id、分支选项与勾选状态；")
                append("若当前只是未完成的草稿，请补全为符合格式要求的完整大纲。")
                append("\n\n")
            }
            if (digests.isNotEmpty()) {
                append("## 前期剧情摘要（由早到晚）\n")
                digests.forEachIndexed { index, digest ->
                    append("### 阶段 ").append(index + 1).append('\n')
                    append(digest).append("\n\n")
                }
            }
            if (recent.isNotEmpty()) {
                val (dialogue, truncated) = formatDialogueBounded(recent, RECENT_DIALOGUE_CHAR_CAP)
                if (dialogue.isNotBlank()) {
                    append("## 近期对话\n")
                    if (truncated) {
                        append("（对话较长，以下只保留最近的部分；更早的内容见上方阶段摘要）\n")
                    }
                    append(dialogue)
                }
            } else if (digests.isEmpty() && workspaceCtx.isBlank() && isEmpty()) {
                append("（无对话记录与工作区设定，请根据你的创作知识构思一个故事大纲。）")
            }
        }
    }

    /** Pages[0] = newest [RECENT_PAGE_SIZE]; later pages are older windows. */
    private suspend fun loadMessagePages(conversationId: String): List<List<Pair<String, String>>> {
        if (conversationId.isBlank()) return emptyList()
        val pages = mutableListOf<List<Pair<String, String>>>()
        var offset = 0
        val maxPages = 1 + MAX_DIGEST_ROUNDS
        while (pages.size < maxPages) {
            val batch = aiChatGateway.getMessagesForRegeneration(
                conversationId,
                limit = RECENT_PAGE_SIZE,
                offset = offset,
            )
            if (batch.isEmpty()) break
            pages.add(batch)
            if (batch.size < RECENT_PAGE_SIZE) break
            offset += RECENT_PAGE_SIZE
        }
        return pages
    }

    private fun formatDialogue(messages: List<Pair<String, String>>): String = buildString {
        messages.forEach { msg ->
            val role = if (msg.first == "user") "用户" else "助手"
            append('[').append(role).append("] ")
            append(msg.second.take(MSG_CHAR_CAP))
            append("\n\n")
        }
    }

    /**
     * Recent dialogue keeps the newest messages when over budget: `next`/`current` anchors
     * stay grounded in the latest turns while early-plot digests keep their share of the prompt.
     * Returns (formatted old→new, whether older recent messages were dropped).
     */
    private fun formatDialogueBounded(
        messages: List<Pair<String, String>>,
        maxChars: Int,
    ): Pair<String, Boolean> {
        if (messages.isEmpty()) return "" to false
        val kept = mutableListOf<Pair<String, String>>()
        var used = 0
        for (msg in messages.asReversed()) {
            val text = msg.second.take(MSG_CHAR_CAP)
            val cost = text.length + 8
            if (kept.isNotEmpty() && used + cost > maxChars) break
            kept.add(msg.first to text)
            used += cost
        }
        val truncated = kept.size < messages.size
        val body = kept.asReversed().joinToString("\n\n") { (role, text) ->
            "[${if (role == "user") "用户" else "助手"}] $text"
        }
        return body to truncated
    }

    private suspend fun digestPlotChunk(
        chunk: List<Pair<String, String>>,
        priorDigest: String,
    ): String? {
        if (chunk.isEmpty()) return null
        var body = promptTemplateGateway.getPrompt(AiPromptTemplate.OUTLINE_PLOT_DIGEST_PROMPT)
        if (body.isBlank()) {
            body = AiPromptTemplate.DEFAULTS[AiPromptTemplate.OUTLINE_PLOT_DIGEST_PROMPT].orEmpty()
        }
        val priorBlock = if (priorDigest.isBlank()) {
            ""
        } else {
            "## 此前摘要（请衔接，勿重复啰嗦）\n$priorDigest\n"
        }
        val filled = body
            .replace("{priorDigest}", priorBlock)
            .replace("{chunk}", formatDialogue(chunk).trim())
        val system = "你是剧情摘要助手。只输出摘要正文。"
        val config = toolConfigGateway.getByToolName("patch_outline")
        return AiStreamingTextHelper.generateWithToolConfig(
            aiTextGateway = aiTextGateway,
            aiProfileGateway = aiProfileGateway,
            systemPrompt = system,
            userPrompt = filled,
            config = config,
            callSource = AiCallSource.OUTLINE,
            onPartial = null,
        ).getOrNull()?.trim()?.takeIf { it.isNotBlank() }
    }

    /**
     * Prefer explicit tool arg only.
     * Chat omit → blank → [GENERATE_OUTLINE_PROMPT] (branching follows user request).
     * Writing sessions may inject via enrichToolCallsWithWritingSubMode for sheet templates.
     * Do not infer from the target conversation's saved author/roleplay sub-mode.
     */
    private fun resolveWritingSubMode(explicit: String): String {
        return when (explicit.trim()) {
            "author" -> "author"
            "roleplay" -> "roleplay"
            else -> ""
        }
    }

    private suspend fun computeNewContent(
        current: String,
        mode: OutlinePatchMode,
        dryRunGenerate: Boolean,
    ): String = when (mode) {
        is OutlinePatchMode.Replace -> mode.content
        is OutlinePatchMode.Append -> current + mode.content
        is OutlinePatchMode.SearchReplace -> {
            if (mode.replaceAll) current.replace(mode.search, mode.replace)
            else current.replaceFirst(mode.search, mode.replace)
        }
        is OutlinePatchMode.PatchSection -> patchSection(current, mode.sectionTitle, mode.content)
        is OutlinePatchMode.GraphOps ->
            // Preview only: failed ops → unchanged content (empty diff), not a fake success write.
            applyGraphOpsContent(current, mode.ops).getOrDefault(current)
        is OutlinePatchMode.Generate -> if (dryRunGenerate) current else current
    }

    private fun patchSection(content: String, sectionTitle: String, newBody: String): String {
        val header = if (sectionTitle.startsWith("#")) sectionTitle else "## $sectionTitle"
        val pattern = Regex("""(?m)^${Regex.escape(header)}\s*$""")
        val match = pattern.find(content)
        if (match == null) {
            return if (content.isBlank()) "$header\n$newBody" else "$content\n\n$header\n$newBody"
        }
        val start = match.range.last + 1
        val nextHeader = Regex("""(?m)^#{1,6}\s+""").find(content, start)
        val end = nextHeader?.range?.first ?: content.length
        return content.substring(0, start) + "\n" + newBody.trim() + "\n" + content.substring(end).trimStart()
    }

}
