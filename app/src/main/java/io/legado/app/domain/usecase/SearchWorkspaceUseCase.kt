package io.legado.app.domain.usecase

import io.legado.app.data.entities.AiMemoryTable
import io.legado.app.data.entities.AiMemoryTableRow
import io.legado.app.data.entities.AiWorldBook
import io.legado.app.data.entities.AiWorldBookEntry
import io.legado.app.domain.gateway.AiMemoryTableGateway
import io.legado.app.domain.gateway.AiOutlineGateway
import io.legado.app.domain.gateway.AiWorldBookEntryGateway
import io.legado.app.domain.gateway.AiWorldBookGateway
import io.legado.app.domain.gateway.AiWorkspaceGateway
import io.legado.app.domain.usecase.structured.OutlineParser
import io.legado.app.utils.AiIdListCodec
import io.legado.app.utils.GSON
import io.legado.app.utils.parseJsonStringMap

class SearchWorkspaceUseCase(
    private val memoryTableGateway: AiMemoryTableGateway,
    private val outlineGateway: AiOutlineGateway,
    private val worldBookGateway: AiWorldBookGateway,
    private val worldBookEntryGateway: AiWorldBookEntryGateway,
    private val workspaceGateway: AiWorkspaceGateway,
) {

    data class Hit(
        val resource: String,
        val score: Int,
        val title: String,
        val snippet: String,
        val tableId: String? = null,
        val rowId: String? = null,
        val outlineOffset: Int? = null,
        val worldBookId: String? = null,
        val entryId: String? = null,
    )

    suspend fun search(
        query: String,
        conversationId: String?,
        scope: String = SCOPE_ALL,
        limit: Int = DEFAULT_LIMIT,
        conversationType: String? = null,
    ): String {
        val key = query.trim()
        if (key.isBlank()) {
            return """{"error":"query is required"}"""
        }
        val convId = conversationId?.trim().orEmpty()
        if (convId.isBlank()) {
            return """{"error":"conversationId is required"}"""
        }
        // 写作模式沙盒:world_book 检索限于工作区绑定;聊天模式无工作区,保留全量回退。
        val writing = conversationType == "writing"
        val normalizedScope = scope.trim().lowercase().ifBlank { SCOPE_ALL }
        val cap = limit.coerceIn(1, MAX_LIMIT)
        val hits = mutableListOf<Hit>()
        when (normalizedScope) {
            SCOPE_MEMORY -> searchMemory(convId, key, hits, cap)
            SCOPE_OUTLINE -> searchOutline(convId, key, hits, cap)
            SCOPE_WORLD_BOOK -> searchWorldBooks(convId, key, hits, cap, writing)
            SCOPE_ALL -> {
                searchMemory(convId, key, hits, collectCap = null)
                searchOutline(convId, key, hits, collectCap = null)
                searchWorldBooks(convId, key, hits, collectCap = null, writing)
            }
            else -> return """{"error":"Invalid scope: use memory, outline, world_book, or all"}"""
        }
        val sorted = hits.sortedByDescending { it.score }.take(cap)
        val workflow = if (sorted.isEmpty()) {
            "No matches. Try a shorter keyword or a different scope."
        } else {
            "Use read_history_memory (tableId/rowIds), read_outline (outlineOffset), or read_world_book (worldBookId/entryId) for full text."
        }
        return GSON.toJson(
            mapOf(
                "query" to key,
                "scope" to normalizedScope,
                "count" to sorted.size,
                "hits" to sorted.map { it.toMap() },
                "workflow" to workflow,
            ),
        )
    }

    private suspend fun searchMemory(
        convId: String,
        key: String,
        hits: MutableList<Hit>,
        collectCap: Int?,
    ) {
        if (collectCap != null && hits.size >= collectCap) return
        val tables = memoryTableGateway.getEnabledForConversation(convId)
        val needle = key.lowercase()
        for (table in tables) {
            if (collectCap != null && hits.size >= collectCap) break
            val tableNameHit = table.name.contains(key, ignoreCase = true)
            val rows = memoryTableGateway.getRows(table.id)
            for (row in rows) {
                if (collectCap != null && hits.size >= collectCap) break
                val hit = matchMemoryRow(table, row, needle, key, tableNameHit) ?: continue
                hits.add(hit)
            }
        }
    }

    private fun matchMemoryRow(
        table: AiMemoryTable,
        row: AiMemoryTableRow,
        needle: String,
        rawQuery: String,
        tableNameHit: Boolean,
    ): Hit? {
        val data = parseJsonStringMap(row.rowData)
        val textParts = buildList {
            addAll(data.values.mapNotNull { it?.toString() })
        }
        val joined = textParts.joinToString(" ")
        val nameValues = textParts.filter { it.contains(rawQuery, ignoreCase = true) }
        val bodyHit = joined.contains(needle)
        if (nameValues.isEmpty() && !bodyHit) return null
        val score = when {
            tableNameHit && nameValues.isNotEmpty() -> 12
            nameValues.isNotEmpty() -> 10
            tableNameHit && bodyHit -> 9
            else -> 5
        }
        val title = buildString {
            append(table.name)
            nameValues.firstOrNull()?.let { append(" · ").append(it.take(40)) }
        }
        val snippetSource = nameValues.firstOrNull() ?: joined.ifBlank { table.name }
        return Hit(
            resource = "memory_row",
            score = score,
            title = title,
            snippet = snippetAround(snippetSource, rawQuery),
            tableId = table.id,
            rowId = row.id,
        )
    }

    private suspend fun searchOutline(
        convId: String,
        key: String,
        hits: MutableList<Hit>,
        collectCap: Int?,
    ) {
        if (collectCap != null && hits.size >= collectCap) return
        val outline = outlineGateway.getByConversation(convId) ?: return
        val content = outline.content
        if (content.isBlank()) return
        val parsed = OutlineParser.parse(content)
        val body = parsed.body.ifBlank { content }
        val bodyBase = parsed.bodyStartOffset
        val needle = key.lowercase()
        val sections = parsed.sections.filter {
            it.title !in setOf("整体走向", "当前进度", "下一目标")
        }
        if (sections.isEmpty()) {
            searchOutlineChunks(body, bodyBase, key, needle, hits, collectCap)
            return
        }
        for (i in sections.indices) {
            if (collectCap != null && hits.size >= collectCap) break
            val section = sections[i]
            val nextOffset = sections.getOrNull(i + 1)?.offset ?: content.length
            val relStart = (section.offset - bodyBase).coerceIn(0, body.length)
            val relEnd = (nextOffset - bodyBase).coerceIn(relStart, body.length)
            val sectionText = body.substring(relStart, relEnd)
            if (!sectionText.lowercase().contains(needle)) continue
            if (sectionText.length > SECTION_SPLIT_THRESHOLD) {
                searchOutlineChunks(sectionText, section.offset, key, needle, hits, collectCap)
            } else {
                val score = if (section.title.contains(key, ignoreCase = true)) 10 else 7
                hits.add(
                    Hit(
                        resource = "outline",
                        score = score,
                        title = section.title,
                        snippet = snippetAround(sectionText, key),
                        outlineOffset = section.offset,
                    ),
                )
            }
        }
    }

    private fun searchOutlineChunks(
        text: String,
        baseOffset: Int,
        rawQuery: String,
        needle: String,
        hits: MutableList<Hit>,
        collectCap: Int?,
    ) {
        val chunkSize = OUTLINE_CHUNK_SIZE
        var offset = 0
        while (offset < text.length && (collectCap == null || hits.size < collectCap)) {
            val end = (offset + chunkSize).coerceAtMost(text.length)
            val chunk = text.substring(offset, end)
            if (chunk.lowercase().contains(needle)) {
                val titleLine = chunk.lineSequence().firstOrNull { it.isNotBlank() }?.take(60)
                    ?: "outline @ ${baseOffset + offset}"
                val score = if (titleLine.contains(rawQuery, ignoreCase = true)) 10 else 6
                hits.add(
                    Hit(
                        resource = "outline",
                        score = score,
                        title = titleLine,
                        snippet = snippetAround(chunk, rawQuery),
                        outlineOffset = baseOffset + offset,
                    ),
                )
            }
            if (end >= text.length) break
            offset += chunkSize - OUTLINE_CHUNK_OVERLAP
        }
    }

    private suspend fun searchWorldBooks(
        convId: String,
        key: String,
        hits: MutableList<Hit>,
        collectCap: Int?,
        writing: Boolean,
    ) {
        if (collectCap != null && hits.size >= collectCap) return
        val books = resolveWorldBooks(convId, writing)
        val needle = key.lowercase()
        for (book in books) {
            if (collectCap != null && hits.size >= collectCap) break
            matchWorldBook(book, key, needle)?.let { hits.add(it) }
            if (collectCap != null && hits.size >= collectCap) break
            val entries = worldBookEntryGateway.getEnabledForWorldBook(book.id)
            for (entry in entries) {
                if (collectCap != null && hits.size >= collectCap) break
                matchWorldBookEntry(book, entry, key, needle)?.let { hits.add(it) }
            }
        }
    }

    private suspend fun resolveWorldBooks(conversationId: String, writing: Boolean): List<AiWorldBook> {
        val workspace = workspaceGateway.getByConversationId(conversationId)
        val ids = AiIdListCodec.parse(workspace?.worldBookIds)
        if (ids.isNotEmpty()) {
            return ids.mapNotNull { worldBookGateway.getById(it) }.filter { it.enabled }
        }
        // 写作模式:工作区未绑定世界书时绝不回退到全部(与 WorldEntryScanner.requireWorldBookIds 一致)。
        // 聊天模式无工作区,保留全量目录。
        if (writing) return emptyList()
        return worldBookGateway.getEnabled()
    }

    /** 写作模式沙盒:worldBookId 是否属于该会话工作区绑定。未绑定的读工具应拒绝。 */
    suspend fun isWorldBookInWorkspace(worldBookId: String, conversationId: String?): Boolean {
        val cid = conversationId?.takeIf { it.isNotBlank() } ?: return false
        val workspace = workspaceGateway.getByConversationId(cid) ?: return false
        return worldBookId in AiIdListCodec.parse(workspace.worldBookIds)
    }

    private fun matchWorldBook(book: AiWorldBook, rawQuery: String, needle: String): Hit? {
        val fields = listOf(
            book.name to 10,
            book.plotSummary to 6,
            book.writingStyle to 6,
            book.grammar to 5,
            book.representativeDialogues to 5,
            book.representativeProse to 5,
        )
        var bestScore = 0
        var bestText = ""
        for ((text, weight) in fields) {
            if (text.isBlank()) continue
            if (!text.lowercase().contains(needle)) continue
            if (weight > bestScore) {
                bestScore = weight
                bestText = text
            }
        }
        if (bestScore == 0) return null
        return Hit(
            resource = "world_book",
            score = bestScore,
            title = book.name,
            snippet = snippetAround(bestText, rawQuery),
            worldBookId = book.id,
        )
    }

    private fun matchWorldBookEntry(
        book: AiWorldBook,
        entry: AiWorldBookEntry,
        rawQuery: String,
        needle: String,
    ): Hit? {
        val keysHit = entry.keys.contains(rawQuery, ignoreCase = true)
        val nameHit = entry.name.contains(rawQuery, ignoreCase = true)
        val contentHit = entry.content.contains(needle)
        if (!keysHit && !nameHit && !contentHit) return null
        val score = when {
            keysHit || nameHit -> 10
            else -> 6
        }
        val source = when {
            nameHit -> entry.name
            keysHit -> entry.keys
            else -> entry.content
        }
        return Hit(
            resource = "world_book_entry",
            score = score,
            title = "${book.name} / ${entry.name.ifBlank { entry.keys.take(40) }}",
            snippet = snippetAround(source.ifBlank { entry.content }, rawQuery),
            worldBookId = book.id,
            entryId = entry.id,
        )
    }

    private fun snippetAround(text: String, query: String, maxLen: Int = SNIPPET_MAX): String {
        val compact = text.replace("\n", " ").trim()
        if (compact.isBlank()) return ""
        val idx = compact.indexOf(query, ignoreCase = true)
        if (idx < 0) return compact.take(maxLen)
        val half = maxLen / 2
        val start = (idx - half).coerceAtLeast(0)
        val end = (start + maxLen).coerceAtMost(compact.length)
        val slice = compact.substring(start, end).trim()
        return buildString {
            if (start > 0) append('…')
            append(slice)
            if (end < compact.length) append('…')
        }
    }

    private fun Hit.toMap(): Map<String, Any?> = buildMap {
        put("resource", resource)
        put("score", score)
        put("title", title)
        put("snippet", snippet)
        tableId?.let { put("tableId", it) }
        rowId?.let { put("rowId", it) }
        outlineOffset?.let { put("outlineOffset", it) }
        worldBookId?.let { put("worldBookId", it) }
        entryId?.let { put("entryId", it) }
    }

    companion object {
        const val SCOPE_ALL = "all"
        const val SCOPE_MEMORY = "memory"
        const val SCOPE_OUTLINE = "outline"
        const val SCOPE_WORLD_BOOK = "world_book"
        private const val DEFAULT_LIMIT = 15
        private const val MAX_LIMIT = 30
        private const val SNIPPET_MAX = 120
        private const val OUTLINE_CHUNK_SIZE = 400
        private const val OUTLINE_CHUNK_OVERLAP = 80
        private const val SECTION_SPLIT_THRESHOLD = 800
    }
}
