package io.legado.app.domain.usecase

import io.legado.app.data.entities.AiCharacterCard
import io.legado.app.data.entities.AiMemoryTable
import io.legado.app.data.entities.AiMemoryTableRow
import io.legado.app.data.entities.AiOutline
import io.legado.app.data.entities.AiPromptTemplate
import io.legado.app.data.entities.AiWorldBook
import io.legado.app.data.entities.AiWorldBookEntry
import io.legado.app.data.entities.AiWorkspace
import io.legado.app.data.entities.AiWritingPrompt
import io.legado.app.domain.gateway.AiCharacterCardGateway
import io.legado.app.domain.gateway.AiMemoryTableGateway
import io.legado.app.domain.gateway.AiOutlineGateway
import io.legado.app.domain.gateway.AiPromptTemplateGateway
import io.legado.app.domain.gateway.AiWorldBookEntryGateway
import io.legado.app.domain.gateway.AiWorldBookGateway
import io.legado.app.domain.gateway.AiWritingPromptGateway
import io.legado.app.domain.prompt.ContextBudgeter
import io.legado.app.domain.usecase.structured.graph.OutlineGraph
import io.legado.app.domain.usecase.structured.graph.OutlineGraphCodec
import io.legado.app.domain.usecase.structured.graph.OutlineGraphEngine
import io.legado.app.domain.usecase.structured.graph.OutlineGraphNode
import io.legado.app.domain.usecase.structured.graph.OutlineNodeType
import io.legado.app.utils.AiIdListCodec
import io.legado.app.utils.GSON
import io.legado.app.utils.parseJsonStringMap

/**
 * Builds compact workspace index text and prefetch snippets for writing-mode prompts.
 * World-book lore entries are not listed in the writing index — they inject via keyword scan /
 * search_workspace; [buildOutlineGenerateContext] includes a short entry digest for sheet generate.
 */
class WritingWorkspaceIndexBuilder(
    private val characterCardGateway: AiCharacterCardGateway,
    private val worldBookGateway: AiWorldBookGateway,
    private val worldBookEntryGateway: AiWorldBookEntryGateway,
    private val writingPromptGateway: AiWritingPromptGateway,
    private val memoryTableGateway: AiMemoryTableGateway,
    private val outlineGateway: AiOutlineGateway,
    private val promptTemplateGateway: AiPromptTemplateGateway,
    private val contextBudgeter: ContextBudgeter = ContextBudgeter(),
) {
    companion object {
        const val PREFETCH_ROWS_PER_TABLE = 15
        private const val PREFETCH_CELL_MAX = 80
        private const val DEFAULT_PREFETCH_MAX_TOKENS = 900
        private const val SPEAKER_DESC_MAX = 300
        private const val OUTLINE_GEN_MAX_CHARS = 8000
        private const val OUTLINE_GEN_CARD_FIELD = 400
        private const val OUTLINE_GEN_WB_FIELD = 500
        private const val OUTLINE_GEN_ENTRY = 220
        private const val OUTLINE_GEN_PROMPT = 400
        private const val OUTLINE_GEN_MAX_ENTRIES_PER_BOOK = 6
        private const val OUTLINE_INDEX_CACHE_MAX = 32

        private data class OutlineIndexCacheEntry(
            val fingerprint: String,
            val lines: List<String>,
        )

        private val outlineIndexCache =
            object : LinkedHashMap<String, OutlineIndexCacheEntry>(OUTLINE_INDEX_CACHE_MAX, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, OutlineIndexCacheEntry>?,
                ): Boolean = size > OUTLINE_INDEX_CACHE_MAX
            }
    }

    data class IndexContext(
        val conversationId: String,
        val workspace: AiWorkspace?,
        val cards: List<AiCharacterCard>,
        val worldBooks: List<AiWorldBook>,
        val prompts: List<AiWritingPrompt>,
        val tables: List<AiMemoryTable>,
        val outline: AiOutline?,
    )

    suspend fun loadContext(conversationId: String, workspace: AiWorkspace?): IndexContext {
        val cardIds = parseIds(workspace?.characterCardIds)
        val cards = cardIds.mapNotNull { characterCardGateway.getById(it) }
        val wbIds = parseIds(workspace?.worldBookIds)
        val worldBooks = wbIds.mapNotNull { worldBookGateway.getById(it) }
        val promptIds = parseIds(workspace?.writingPromptIds)
        val prompts = if (promptIds.isEmpty()) {
            emptyList()
        } else {
            promptIds.mapNotNull { writingPromptGateway.getById(it) }.filter { it.enabled }
        }
        val tables = memoryTableGateway.getEnabledForConversation(conversationId)
        val outline = outlineGateway.getByConversation(conversationId)
        return IndexContext(
            conversationId = conversationId,
            workspace = workspace,
            cards = cards,
            worldBooks = worldBooks,
            prompts = prompts,
            tables = tables,
            outline = outline,
        )
    }

    suspend fun buildIndexBlock(ctx: IndexContext): String {
        val hint = promptTemplateGateway.getPrompt(AiPromptTemplate.WRITING_WORKSPACE_INDEX_HINT)
        val lines = mutableListOf<String>()
        lines.add("<workspace_index>")
        if (hint.isNotBlank()) lines.add(hint)
        if (ctx.cards.isNotEmpty()) {
            lines.add("cards:")
            ctx.cards.forEach { c ->
                lines.add("- ${c.name} (id: `${c.id}`)")
            }
        } else {
            lines.add("cards: (none)")
        }
        if (ctx.worldBooks.isNotEmpty()) {
            lines.add("world_books:")
            ctx.worldBooks.forEach { wb ->
                lines.add("- ${wb.name} (id: `${wb.id}`)")
            }
        } else {
            lines.add("world_books: (none)")
        }
        if (ctx.tables.isNotEmpty()) {
            lines.add("memory_tables:")
            ctx.tables.forEach { t ->
                val cols = runCatching {
                    GSON.fromJson(t.columns, Array<String>::class.java).toList()
                }.getOrNull()?.joinToString(", ").orEmpty()
                lines.add("- ${t.name} (id: `${t.id}`${if (cols.isNotBlank()) ", cols: $cols" else ""})")
            }
        } else {
            lines.add("memory_tables: (none)")
        }
        lines.addAll(resolveOutlineIndexLines(ctx.conversationId, ctx.outline))
        if (ctx.prompts.isNotEmpty()) {
            val tags = ctx.prompts.map { "${it.category}:${it.name}" }.joinToString(", ")
            lines.add("writing_prompts: $tags")
        }
        lines.add("</workspace_index>")
        return lines.joinToString("\n")
    }

    /**
     * Outline lines for the workspace index are keyed by a **structure fingerprint**
     * (enabled/kind/TOC titles+ids/active path/choice state). Bullet-only content edits
     * keep the same fingerprint so DeepSeek can reuse a byte-stable SYSTEM prefix.
     */
    private fun resolveOutlineIndexLines(conversationId: String, outline: AiOutline?): List<String> {
        val (fingerprint, lines) = computeOutlineIndexSection(outline)
        if (conversationId.isBlank()) return lines
        synchronized(outlineIndexCache) {
            val cached = outlineIndexCache[conversationId]
            if (cached != null && cached.fingerprint == fingerprint) {
                return cached.lines
            }
            outlineIndexCache[conversationId] = OutlineIndexCacheEntry(fingerprint, lines)
            return lines
        }
    }

    /** Visible for tests. */
    internal fun peekOutlineIndexFingerprint(conversationId: String): String? =
        synchronized(outlineIndexCache) { outlineIndexCache[conversationId]?.fingerprint }

    /** Visible for tests. */
    internal fun clearOutlineIndexCacheForTests() {
        synchronized(outlineIndexCache) { outlineIndexCache.clear() }
    }

    private fun computeOutlineIndexSection(outline: AiOutline?): Pair<String, List<String>> {
        // No outline, or outline switch off → nothing to inject (same neutral placeholder
        // as "no outline", so the model never sees outline status/content while disabled).
        if (outline == null || outline.content.isBlank() || !outline.enabled) {
            return "none" to listOf("outline: (none)")
        }
        val decoded = OutlineGraphCodec.decode(outline.content)
        val g = decoded.graph
        if (g == null) {
            val lines = listOf("outline: enabled, unsupported_format — regenerate required")
            return "unsupported|enabled" to lines
        }

        val sectionNodes = OutlineGraphEngine.writableIds(g)
            .mapNotNull { g.nodes[it] }
            .filter { it.type.name in setOf("VOLUME", "CHAPTER", "SECTION", "BRANCH") }
            .sortedWith(compareBy({ it.type.name }, { it.id }))
        val toc = sectionNodes.joinToString(" | ") { it.title }
        val sectionFp = sectionNodes.joinToString(";") { "${it.id}:${it.type.name}:${it.title}" }
        val pathFp = g.activePath.joinToString("/")
        val pending = OutlineGraphEngine.findPendingBranch(g)
        val pendingFp = pending?.let { (branch, opts) ->
            val optPart = opts.sortedBy { it.id }.joinToString(",") { "${it.id}:${it.title}" }
            "${branch.id}:${branch.title}|$optPart"
        }.orEmpty()

        val fingerprint = listOf(
            "enabled",
            g.outlineKind,
            sectionFp,
            pathFp,
            g.awaitingChoice.toString(),
            pendingFp,
        ).joinToString("|")

        val lines = mutableListOf<String>()
        lines.add("outline: enabled, format=2, kind=${g.outlineKind}")
        if (toc.isNotBlank()) lines.add("outline_sections: $toc")
        if (g.activePath.isNotEmpty()) {
            lines.add("outline_active_path: ${g.activePath.joinToString("/")}")
        }
        if (g.outlineKind == OutlineGraph.KIND_BRANCHING || pending != null) {
            if (g.awaitingChoice) {
                lines.add(
                    "outline_choice: awaiting — do not advance past fork; user picks in UI",
                )
                if (pending != null) {
                    val labels = pending.second
                        .sortedBy { it.id }
                        .joinToString(" | ") { "${it.id}:${it.title}" }
                    lines.add("outline_options: $labels")
                }
            } else {
                lines.add(
                    "outline_fork_rule: when THIS TURN reaches a decision fork, " +
                        "MUST call patch_outline(mode=graph_ops) before finishing " +
                        "(new: add_node BRANCH+OPTION; preplanted: set_anchors awaiting_choice:true). " +
                        "Do not narrate past the choice.",
                )
                if (pending != null) {
                    val labels = pending.second
                        .sortedBy { it.id }
                        .joinToString(" | ") { "${it.id}:${it.title}" }
                    lines.add(
                        "outline_pending_branch: ${pending.first.id} ${pending.first.title} ($labels)",
                    )
                }
            }
        }
        return fingerprint to lines
    }

    /**
     * Richer workspace digest for outline sheet generate/supplement (no tools).
     * Caps total size; skips current outline body (caller may attach it separately).
     */
    suspend fun buildOutlineGenerateContext(
        conversationId: String,
        workspace: AiWorkspace?,
        maxChars: Int = OUTLINE_GEN_MAX_CHARS,
    ): String {
        if (conversationId.isBlank()) return ""
        val ctx = loadContext(conversationId, workspace)
        val sb = StringBuilder()
        fun room() = maxChars - sb.length
        fun appendCap(text: String) {
            if (text.isBlank() || room() <= 0) return
            val slice = if (text.length <= room()) text else text.take(room().coerceAtLeast(0))
            sb.append(slice)
        }

        appendCap("## 工作区设定\n")
        if (ctx.cards.isNotEmpty()) {
            appendCap("### 角色卡\n")
            for (card in ctx.cards) {
                if (room() < 80) break
                appendCap("- **${card.name}** (`${card.id}`)\n")
                if (card.description.isNotBlank()) {
                    appendCap("  描述：${card.description.take(OUTLINE_GEN_CARD_FIELD)}\n")
                }
                if (card.personality.isNotBlank()) {
                    appendCap("  性格：${card.personality.take(OUTLINE_GEN_CARD_FIELD)}\n")
                }
                if (card.scenario.isNotBlank()) {
                    appendCap("  场景：${card.scenario.take(OUTLINE_GEN_CARD_FIELD)}\n")
                }
            }
            appendCap("\n")
        }
        if (ctx.worldBooks.isNotEmpty()) {
            appendCap("### 世界书\n")
            for (wb in ctx.worldBooks) {
                if (room() < 80) break
                appendCap("- **${wb.name}** (`${wb.id}`)\n")
                if (wb.plotSummary.isNotBlank()) {
                    appendCap("  剧情摘要：${wb.plotSummary.take(OUTLINE_GEN_WB_FIELD)}\n")
                }
                if (wb.writingStyle.isNotBlank()) {
                    appendCap("  文风：${wb.writingStyle.take(OUTLINE_GEN_WB_FIELD)}\n")
                }
                if (wb.grammar.isNotBlank()) {
                    appendCap("  语法/设定：${wb.grammar.take(OUTLINE_GEN_WB_FIELD)}\n")
                }
                val entries = worldBookEntryGateway.getEnabledForWorldBook(wb.id)
                    .asSequence()
                    .filter { it.enabled }
                    .sortedWith(
                        compareByDescending<AiWorldBookEntry> { it.constant }
                            .thenByDescending { it.priority },
                    )
                    .take(OUTLINE_GEN_MAX_ENTRIES_PER_BOOK)
                    .toList()
                if (entries.isNotEmpty()) {
                    appendCap("  条目：\n")
                    for (entry in entries) {
                        if (room() < 40) break
                        val label = entry.name.ifBlank { entry.keys }.ifBlank { entry.id }
                        val body = entry.content.take(OUTLINE_GEN_ENTRY)
                        appendCap("  - [$label] $body\n")
                    }
                }
            }
            appendCap("\n")
        }
        if (ctx.prompts.isNotEmpty()) {
            appendCap("### 写作提示\n")
            for (p in ctx.prompts) {
                if (room() < 40) break
                appendCap("- **${p.name}** (${p.category})：${p.content.take(OUTLINE_GEN_PROMPT)}\n")
            }
            appendCap("\n")
        }
        if (ctx.tables.isNotEmpty() && room() > 120) {
            appendCap("### 记忆表（摘要）\n")
            val tableBudget = contextBudgeter.estimateTokens("x".repeat(room().coerceAtMost(3500)))
                .coerceAtLeast(200)
            var used = 0
            for (table in ctx.tables) {
                if (used >= tableBudget || room() < 80) break
                val columns = runCatching {
                    GSON.fromJson(table.columns, Array<String>::class.java).toList()
                }.getOrNull() ?: continue
                val rows = memoryTableGateway.getRecentRows(table.id, PREFETCH_ROWS_PER_TABLE)
                if (rows.isEmpty()) continue
                val totalRows = memoryTableGateway.getRows(table.id).size
                val chunk = buildTableChunk(table, columns, rows, totalRows)
                val tokens = contextBudgeter.estimateTokens(chunk)
                if (used + tokens > tableBudget && used > 0) break
                appendCap(chunk)
                used += tokens
            }
            appendCap("\n")
        }
        return sb.toString().trim()
    }

    fun buildSpeakerAnchor(card: AiCharacterCard?): String {
        if (card == null) return ""
        val desc = card.description.take(SPEAKER_DESC_MAX)
        return buildString {
            append("\n<current_speaker>\n")
            append("角色名：${card.name}\n")
            if (desc.isNotBlank()) append("角色描述：$desc\n")
            append("</current_speaker>")
        }
    }

    /**
     * Returns sticky-cached prefetch for [ctx.conversationId], rebuilding only when stale.
     */
    suspend fun buildPrefetchBlock(ctx: IndexContext, maxTokens: Int = DEFAULT_PREFETCH_MAX_TOKENS): String {
        val tokenBudget = maxTokens.takeIf { it > 0 } ?: DEFAULT_PREFETCH_MAX_TOKENS
        return WorkspacePrefetchCache.resolve(ctx.conversationId) {
            buildPrefetchContentFresh(ctx, tokenBudget)
        }
    }

    private suspend fun buildPrefetchContentFresh(ctx: IndexContext, maxTokens: Int): String {
        if (ctx.tables.isEmpty() &&
            (ctx.outline == null || !ctx.outline.enabled || ctx.outline.content.isBlank())
        ) {
            return ""
        }
        val openTag = "<workspace_prefetch>\n"
        val closeTag = "</workspace_prefetch>"
        val body = StringBuilder()
        var usedTokens = contextBudgeter.estimateTokens(openTag + closeTag)

        for (table in ctx.tables) {
            if (usedTokens >= maxTokens) break
            val columns = runCatching {
                GSON.fromJson(table.columns, Array<String>::class.java).toList()
            }.getOrNull() ?: continue
            val rows = memoryTableGateway.getRecentRows(table.id, PREFETCH_ROWS_PER_TABLE)
            if (rows.isEmpty()) continue
            val totalRows = memoryTableGateway.getRows(table.id).size
            val chunk = buildTableChunk(table, columns, rows, totalRows)
            val chunkTokens = contextBudgeter.estimateTokens(chunk)
            if (usedTokens + chunkTokens > maxTokens) {
                if (body.isEmpty()) {
                    body.append(contextBudgeter.truncateToTokens(chunk, maxTokens - usedTokens))
                } else {
                    body.append("*（其余表格已省略，请用 read_history_memory）*\n")
                }
                break
            }
            body.append(chunk)
            usedTokens += chunkTokens
        }

        val outline = ctx.outline
        if (outline != null && outline.enabled && outline.content.isNotBlank()) {
            val outlineBody = buildOutlineSection(outline.content)
            val outlineTokens = contextBudgeter.estimateTokens(outlineBody)
            if (usedTokens + outlineTokens <= maxTokens) {
                body.append(outlineBody)
            }
        }

        if (body.isEmpty()) return ""
        return openTag + body.toString() + closeTag
    }

    private fun buildTableChunk(
        table: AiMemoryTable,
        columns: List<String>,
        rows: List<AiMemoryTableRow>,
        totalRowCount: Int,
    ): String = buildString {
        append("### ${table.name} (`${table.id}`)\n")
        append("| " + columns.joinToString(" | ") + " |\n")
        append("|" + columns.joinToString("|") { "---" } + "|\n")
        rows.forEach { row ->
            val data = parseJsonStringMap(row.rowData)
            if (data.isEmpty()) return@forEach
            append("| " + columns.joinToString(" | ") { col ->
                (data[col]?.toString() ?: "").replace("\n", " ").take(PREFETCH_CELL_MAX)
            } + " |\n")
        }
        if (totalRowCount > rows.size) {
            append("*（还有 ${totalRowCount - rows.size} 行，完整数据请用 read_history_memory）*\n")
        }
    }

    private fun buildOutlineSection(content: String): String {
        val decoded = OutlineGraphCodec.decode(content)
        val graph = decoded.graph
        if (graph == null) {
            return "\n### outline_anchor\nunsupported_format: regenerate required\n"
        }
        return buildString {
            append("\n### outline_anchor\n")
            if (graph.premise.isNotBlank()) append("premise: ${graph.premise}\n")
            if (graph.currentProgress.isNotBlank()) append("current: ${graph.currentProgress}\n")
            if (graph.nextGoal.isNotBlank()) append("next: ${graph.nextGoal}\n")
            append("in_progress: ${graph.inProgress}\n")
            append("outline_kind: ${graph.outlineKind}\n")
            if (graph.activePath.isNotEmpty()) {
                append("active_path: ${graph.activePath.joinToString("/")}\n")
            }
            val pending = OutlineGraphEngine.findPendingBranch(graph)
            append("awaiting_choice: ${graph.awaitingChoice}\n")
            if (graph.awaitingChoice) {
                append("outline_choice: awaiting\n")
                if (pending != null) {
                    pending.second.forEach { opt ->
                        append("- id:${opt.id} ${opt.title}\n")
                    }
                }
            } else if (pending != null) {
                append("outline_pending_branch: ${pending.first.id} ${pending.first.title}\n")
                append(
                    "outline_fork_rule: when plot reaches this fork, MUST patch_outline " +
                        "set_anchors awaiting_choice:true (or add_node BRANCH+OPTION for a new fork)\n",
                )
                pending.second.forEach { opt ->
                    append("- id:${opt.id} ${opt.title}\n")
                }
            }
        }
    }

    /**
     * Minimal per-turn story guide for the writing model, injected via `userPrefix`
     * (not SYSTEM) so outline updates never bust the DeepSeek/OpenAI prefix cache.
     * Returns null when there is no enabled outline for the conversation.
     */
    suspend fun buildNextSceneGuide(conversationId: String): String? {
        if (conversationId.isBlank()) return null
        val outline = outlineGateway.getByConversation(conversationId) ?: return null
        if (!outline.enabled || outline.content.isBlank()) return null
        val graph = OutlineGraphCodec.decode(outline.content).graph ?: return null
        return buildNextSceneGuideFromGraph(graph)
    }

    /** Visible for tests. */
    internal fun buildNextSceneGuideFromGraph(graph: OutlineGraph): String? {
        val lines = mutableListOf<String>()
        if (graph.currentProgress.isNotBlank()) {
            lines.add("current: ${graph.currentProgress.singleLine()}")
        }
        if (graph.nextGoal.isNotBlank()) {
            lines.add("next: ${graph.nextGoal.singleLine()}")
        }
        if (graph.awaitingChoice) {
            val pending = OutlineGraphEngine.findPendingBranch(graph)
            val choices = pending?.second
                ?.joinToString(" | ") { it.title.singleLine() }
                .orEmpty()
            lines.add("choice_pending: true（勿代用户择路）" +
                if (choices.isNotBlank()) "（选项：$choices）" else "")
        } else {
            val frontierId = graph.currentNodeId ?: graph.activePath.lastOrNull()
            nextSceneAfter(graph, frontierId)?.let { scene ->
                lines.add("next_scene: ${scene.title.singleLine()}")
            }
        }
        if (lines.isEmpty()) return null
        return buildString {
            append("<story_guide>\n")
            lines.forEach { append(it).append('\n') }
            append("</story_guide>")
        }
    }

    /**
     * Scene node after [frontierId] in narrative order: first scene descendant of the
     * frontier (volume / selected option), else the next sibling scene climbing ancestor
     * levels (volume transitions), else null when the outline has nothing planted ahead.
     */
    private fun nextSceneAfter(graph: OutlineGraph, frontierId: String?): OutlineGraphNode? {
        if (frontierId == null) return firstSceneFrom(null, graph)
        val frontier = graph.nodes[frontierId] ?: return firstSceneFrom(null, graph)
        if (frontier.type == OutlineNodeType.VOLUME ||
            frontier.type == OutlineNodeType.BRANCH ||
            frontier.type == OutlineNodeType.OPTION
        ) {
            firstSceneBelow(frontierId, graph)?.let { return it }
        }
        var cursor: String? = frontierId
        while (cursor != null) {
            val parentId = OutlineGraphEngine.findParent(graph, cursor) ?: break
            val siblings = graph.nodes[parentId]?.children.orEmpty().mapNotNull { graph.nodes[it] }
            val idx = siblings.indexOfFirst { it.id == cursor }
            if (idx >= 0) {
                siblings.drop(idx + 1).forEach { sibling ->
                    firstSceneFrom(sibling.id, graph)?.let { return it }
                }
            }
            cursor = parentId
        }
        return null
    }

    /** First CHAPTER/SECTION reachable starting at [nodeId] (inclusive). */
    private fun firstSceneFrom(nodeId: String?, graph: OutlineGraph): OutlineGraphNode? {
        val start = nodeId ?: graph.rootChildren().firstOrNull()?.id ?: return null
        val node = graph.nodes[start] ?: return null
        if (node.type == OutlineNodeType.CHAPTER || node.type == OutlineNodeType.SECTION) return node
        return firstSceneBelow(start, graph)
    }

    /** First CHAPTER/SECTION reachable below [nodeId] (nodeId itself excluded). */
    private fun firstSceneBelow(nodeId: String, graph: OutlineGraph): OutlineGraphNode? {
        val node = graph.nodes[nodeId] ?: return null
        node.children.forEach { childId ->
            val child = graph.nodes[childId] ?: return@forEach
            when (child.type) {
                OutlineNodeType.CHAPTER, OutlineNodeType.SECTION -> return child
                else -> firstSceneBelow(childId, graph)?.let { return it }
            }
        }
        return null
    }

    private fun String.singleLine(): String =
        replace('\n', ' ').replace(Regex("\\s+"), " ").trim().take(120)

    fun parseIds(raw: String?): List<String> = AiIdListCodec.parse(raw)
}
