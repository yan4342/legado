package io.legado.app.domain.prompt

import io.legado.app.data.entities.AiWorldBookEntry
import io.legado.app.domain.gateway.AiWorldBookEntryGateway
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.ui.ai.chat.AiChatMessageUi

/**
 * Scans recent chat text for world-book entry keyword matches (ST-style lore injection).
 * Splits hits into prefix (system block) vs in-chat (@D) injections based on entry [position].
 *
 * Keyword matching uses the last [scanDepth] history messages (any role) plus [extraText].
 */
class WorldEntryScanner(
    private val entryGateway: AiWorldBookEntryGateway,
) {
    data class ScanResult(
        val entries: List<AiWorldBookEntry>,
        val matchedKeys: Set<String> = emptySet(),
    ) {
        val prefixEntries: List<AiWorldBookEntry>
            get() = entries.filter {
                it.position != AiWorldBookEntry.POSITION_IN_CHAT
            }

        val inChatEntries: List<AiWorldBookEntry>
            get() = entries.filter {
                it.position == AiWorldBookEntry.POSITION_IN_CHAT
            }
    }

    suspend fun scan(
        history: List<AiChatMessageUi>,
        extraText: String = "",
        scanDepth: Int = 2,
        worldBookIds: List<String> = emptyList(),
        /**
         * When true and [worldBookIds] is empty, skip scanning (do not fall back to all enabled).
         * Used by writing mode when the workspace has no bound world books.
         */
        requireWorldBookIds: Boolean = false,
    ): ScanResult {
        if (requireWorldBookIds && worldBookIds.isEmpty()) {
            return ScanResult(emptyList())
        }
        val entries = if (worldBookIds.isEmpty()) {
            entryGateway.getAllEnabled()
        } else {
            worldBookIds.flatMap { entryGateway.getEnabledForWorldBook(it) }
        }.distinctBy { it.id }
        if (entries.isEmpty()) return ScanResult(emptyList())

        val maxScan = entries.maxOf { entry ->
            entry.scanDepth.takeIf { it > 0 } ?: scanDepth
        }.coerceAtLeast(1)
        val recentByDepth = (1..maxScan).associateWith { depth ->
            buildScanText(history, extraText, depth)
        }

        val matched = mutableListOf<AiWorldBookEntry>()
        val matchedKeys = linkedSetOf<String>()
        for (entry in entries.sortedBy { it.priority }) {
            if (entry.constant) {
                matched.add(entry)
                continue
            }
            val entryScan = entry.scanDepth.takeIf { it > 0 } ?: scanDepth
            val recentText = recentByDepth[entryScan.coerceAtLeast(1)]
                ?: buildScanText(history, extraText, entryScan)
            val keys = parseKeys(entry.keys)
            val hit = keys.firstOrNull { key ->
                key.isNotBlank() && recentText.contains(key, ignoreCase = true)
            }
            if (hit != null) {
                matched.add(entry)
                matchedKeys.add(hit)
            }
        }
        return ScanResult(matched, matchedKeys)
    }

    fun formatEntries(entries: List<AiWorldBookEntry>): String {
        if (entries.isEmpty()) return ""
        val body = entries.sortedBy { it.priority }.joinToString("\n\n") { formatEntryXml(it) }
        return "<world_entries>\n$body\n</world_entries>"
    }

    fun toInChatInjections(entries: List<AiWorldBookEntry>): List<InChatInjection> {
        return entries
            .sortedWith(compareBy({ it.insertDepth }, { it.priority }))
            .map { entry ->
                InChatInjection(
                    blockId = entry.name.ifBlank { "world_entry:${entry.id}" },
                    role = normalizeRole(entry.role),
                    content = formatEntryXml(entry),
                    depth = entry.insertDepth.coerceAtLeast(0),
                    order = entry.priority,
                )
            }
    }

    private fun formatEntryXml(entry: AiWorldBookEntry): String = buildString {
        append("<world_entry id=\"${entry.id}\"")
        if (entry.name.isNotBlank()) append(" name=\"${entry.name}\"")
        append(">\n")
        append(entry.content.trim())
        append("\n</world_entry>")
    }

    private fun normalizeRole(role: String): String = when (role.trim().lowercase()) {
        AiWorldBookEntry.ROLE_USER, "user" -> AiMessageRole.USER
        AiWorldBookEntry.ROLE_ASSISTANT, "assistant" -> AiMessageRole.ASSISTANT
        else -> AiMessageRole.SYSTEM
    }

    private fun buildScanText(
        history: List<AiChatMessageUi>,
        extraText: String,
        scanDepth: Int,
    ): String {
        val recent = history.takeLast(scanDepth.coerceAtLeast(1))
        return buildString {
            append(extraText)
            recent.forEach { msg ->
                append('\n')
                append(msg.content)
            }
        }
    }

    private fun parseKeys(raw: String): List<String> =
        raw.split(',', '\n', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
