package io.legado.app.domain.usecase.structured

import io.legado.app.data.dao.AiStructuredDataSnapshotDao
import io.legado.app.data.entities.AiBookOutline
import io.legado.app.data.entities.AiCharacterCard
import io.legado.app.data.entities.AiMemoryTableExport
import io.legado.app.data.entities.AiOutline
import io.legado.app.data.entities.AiOutlineSnapshotPayload
import io.legado.app.data.entities.AiStructuredDataSnapshot
import io.legado.app.data.entities.AiUserCardSnapshotPayload
import io.legado.app.data.entities.AiWorldBook
import io.legado.app.data.repository.AiToolRepository
import io.legado.app.domain.gateway.AiBookOutlineGateway
import io.legado.app.domain.gateway.AiCharacterCardGateway
import io.legado.app.domain.gateway.AiChatGateway
import io.legado.app.domain.gateway.AiMemoryTableGateway
import io.legado.app.domain.gateway.AiOutlineGateway
import io.legado.app.domain.gateway.AiWorldBookGateway
import io.legado.app.domain.model.MemoryTableOp
import io.legado.app.utils.GSON
import java.util.UUID

class MutationSnapshotService(
    private val snapshotDao: AiStructuredDataSnapshotDao,
    private val memoryTableGateway: AiMemoryTableGateway,
    private val outlineGateway: AiOutlineGateway,
    private val characterCardGateway: AiCharacterCardGateway,
    private val aiChatGateway: AiChatGateway,
    private val worldBookGateway: AiWorldBookGateway,
    private val bookOutlineGateway: AiBookOutlineGateway,
) {

    data class RestoreResult(val success: Boolean, val message: String, val resourceType: String = "")

    data class OutlineSnapshotSummary(
        val id: String,
        val createdAt: Long,
        val source: String?,
        val diffSummary: String?,
    )

    /** Preview of applying a snapshot (current → after restore). */
    data class RestorePreview(
        val snapshotId: String,
        val resourceType: String,
        val summary: String,
        val changes: List<FieldChange>,
    )

    suspend fun captureOutlineVersion(
        conversationId: String,
        source: String,
        beforeContent: String,
        afterContent: String,
        enabled: Boolean,
    ): String? {
        if (conversationId.isBlank()) return null
        if (beforeContent == afterContent && source != "before_restore") return null
        val diffSummary = OutlineDiffSummarizer.summarize(beforeContent, afterContent)
        val snapshotId = "snap_${UUID.randomUUID().toString().replace("-", "")}"
        snapshotDao.insert(
            AiStructuredDataSnapshot(
                id = snapshotId,
                conversationId = conversationId,
                resourceType = "outline",
                resourceKey = conversationId,
                payloadJson = GSON.toJson(
                    AiOutlineSnapshotPayload(
                        content = beforeContent,
                        enabled = enabled,
                        source = source,
                        diffSummary = diffSummary,
                    ),
                ),
                toolCallId = null,
                batchId = null,
                toolName = source,
                createdAt = System.currentTimeMillis(),
            ),
        )
        pruneOutlineSnapshots(conversationId, OUTLINE_VERSION_KEEP)
        return snapshotId
    }

    suspend fun listOutlineSnapshots(conversationId: String, limit: Int = OUTLINE_VERSION_KEEP): List<OutlineSnapshotSummary> {
        return snapshotDao.recentOutlineByConversation(conversationId, limit).mapNotNull { snap ->
            val payload = runCatching {
                GSON.fromJson(snap.payloadJson, AiOutlineSnapshotPayload::class.java)
            }.getOrNull() ?: return@mapNotNull null
            OutlineSnapshotSummary(
                id = snap.id,
                createdAt = snap.createdAt,
                source = payload.source ?: snap.toolName,
                diffSummary = payload.diffSummary,
            )
        }
    }

    suspend fun pruneOutlineSnapshots(conversationId: String, keep: Int = OUTLINE_VERSION_KEEP) {
        snapshotDao.deleteOlderOutlineThan(conversationId, keep)
    }

    suspend fun snapshotContent(snapshotId: String): AiOutlineSnapshotPayload? {
        val snap = snapshotDao.getById(snapshotId) ?: return null
        return runCatching {
            GSON.fromJson(snap.payloadJson, AiOutlineSnapshotPayload::class.java)
        }.getOrNull()
    }

    companion object {
        private const val OUTLINE_VERSION_KEEP = 5
    }

    suspend fun capture(
        ctx: StructuredMutationContext,
        toolCallId: String?,
        batchId: String?,
    ): String? {
        val payload = buildPayload(ctx) ?: return null
        val snapshotId = "snap_${UUID.randomUUID().toString().replace("-", "")}"
        val (resourceType, resourceKey) = resourceInfo(ctx)
        snapshotDao.insert(
            AiStructuredDataSnapshot(
                id = snapshotId,
                conversationId = ctx.conversationId,
                resourceType = resourceType,
                resourceKey = resourceKey,
                payloadJson = payload,
                toolCallId = toolCallId,
                batchId = batchId,
                toolName = ctx.toolName,
                createdAt = System.currentTimeMillis(),
            )
        )
        prune(ctx.conversationId)
        return snapshotId
    }

    suspend fun restore(snapshotId: String): RestoreResult {
        val snapshot = snapshotDao.getById(snapshotId)
            ?: return RestoreResult(false, "Snapshot not found")
        return when (snapshot.resourceType) {
            "memory_table" -> restoreMemoryTable(snapshot.payloadJson)
            "outline" -> restoreOutline(snapshot.payloadJson, snapshot.resourceKey)
            "character_card" -> restoreCharacterCard(snapshot.payloadJson)
            "user_card" -> restoreUserCard(snapshot.payloadJson, snapshot.resourceKey)
            "world_book" -> restoreWorldBook(snapshot.payloadJson)
            else -> RestoreResult(false, "Unknown resource type: ${snapshot.resourceType}")
        }
    }

    /**
     * 采纳桥:采纳前快照正典行的当前状态,采纳后保留快照可回滚。
     * 复用标准 resourceType(outline/memory_table/character_card/world_book),resourceKey 为 canonical 主键。
     */
    suspend fun captureCanonicalSnapshot(
        conversationId: String,
        resourceType: String,
        resourceKey: String,
        beforeJson: String,
    ): String? {
        if (conversationId.isBlank() || beforeJson.isBlank()) return null
        val snapshotId = "snap_${UUID.randomUUID().toString().replace("-", "")}"
        snapshotDao.insert(
            AiStructuredDataSnapshot(
                id = snapshotId,
                conversationId = conversationId,
                resourceType = resourceType,
                resourceKey = resourceKey,
                payloadJson = beforeJson,
                toolCallId = null,
                batchId = null,
                toolName = "adopt_to_book",
                createdAt = System.currentTimeMillis(),
            )
        )
        return snapshotId
    }

    /** 采纳桥回滚:把正典行恢复到采纳前状态。 */
    suspend fun restoreCanonicalSnapshot(snapshotId: String): RestoreResult {
        val snapshot = snapshotDao.getById(snapshotId)
            ?: return RestoreResult(false, "Snapshot not found")
        return when (snapshot.resourceType) {
            "outline" -> {
                val outline = runCatching {
                    GSON.fromJson(snapshot.payloadJson, AiBookOutline::class.java)
                }.getOrNull() ?: return RestoreResult(false, "Invalid outline snapshot", "outline")
                bookOutlineGateway.upsert(outline)
                RestoreResult(true, "正典大纲已回滚", "outline")
            }
            "memory_table" -> restoreMemoryTable(snapshot.payloadJson)
            "character_card" -> {
                val card = runCatching {
                    GSON.fromJson(snapshot.payloadJson, AiCharacterCard::class.java)
                }.getOrNull() ?: return RestoreResult(false, "Invalid character snapshot", "character_card")
                characterCardGateway.upsert(card)
                RestoreResult(true, "正典角色卡已回滚", "character_card")
            }
            "world_book" -> restoreWorldBook(snapshot.payloadJson)
            else -> RestoreResult(false, "Unknown resource type: ${snapshot.resourceType}")
        }
    }

    suspend fun prune(conversationId: String?, keep: Int = 10) {
        snapshotDao.deleteOlderThan(conversationId, keep)
    }

    suspend fun latestForConversation(conversationId: String?): AiStructuredDataSnapshot? {
        return snapshotDao.recentByConversation(conversationId, 1).firstOrNull()
    }

    suspend fun latestMemoryTableForConversation(conversationId: String?): AiStructuredDataSnapshot? {
        return snapshotDao.recentByConversationAndType(conversationId, "memory_table", 1).firstOrNull()
    }

    suspend fun previewRestore(snapshotId: String): RestorePreview? {
        val snapshot = snapshotDao.getById(snapshotId) ?: return null
        return when (snapshot.resourceType) {
            "memory_table" -> previewMemoryTableRestore(snapshot)
            "outline" -> {
                val payload = runCatching {
                    GSON.fromJson(snapshot.payloadJson, AiOutlineSnapshotPayload::class.java)
                }.getOrNull()
                val current = outlineGateway.getByConversation(snapshot.resourceKey)?.content.orEmpty()
                val before = payload?.content.orEmpty()
                RestorePreview(
                    snapshotId = snapshot.id,
                    resourceType = "outline",
                    summary = payload?.diffSummary?.takeIf { it.isNotBlank() } ?: "outline",
                    changes = StructuredDataDiff.diffTextLines(current, before),
                )
            }
            else -> RestorePreview(
                snapshotId = snapshot.id,
                resourceType = snapshot.resourceType,
                summary = snapshot.resourceType,
                changes = emptyList(),
            )
        }
    }

    private suspend fun previewMemoryTableRestore(snapshot: AiStructuredDataSnapshot): RestorePreview {
        val export = runCatching {
            GSON.fromJson(snapshot.payloadJson, AiMemoryTableExport::class.java)
        }.getOrNull()
        if (export == null || export.tables.isEmpty()) {
            return RestorePreview(snapshot.id, "memory_table", "memory_table", emptyList())
        }
        val changes = mutableListOf<FieldChange>()
        val names = mutableListOf<String>()
        for (twr in export.tables) {
            val table = twr.table
            names += table.name.ifBlank { table.id }
            val currentRows = memoryTableGateway.getRows(table.id).associateBy { it.id }
            val snapRows = twr.rows.associateBy { it.id }
            val allIds = (currentRows.keys + snapRows.keys)
            for (rowId in allIds) {
                val cur = currentRows[rowId]
                val snap = snapRows[rowId]
                val curMap = cur?.let { parseRowData(it.rowData) }.orEmpty()
                val snapMap = snap?.let { parseRowData(it.rowData) }.orEmpty()
                when {
                    cur != null && snap == null -> {
                        curMap.forEach { (k, v) ->
                            val vs = v?.toString().orEmpty()
                            if (vs.isNotBlank()) {
                                changes += FieldChange("${table.name}/$rowId/$k", vs, "")
                            }
                        }
                    }
                    cur == null && snap != null -> {
                        snapMap.forEach { (k, v) ->
                            val vs = v?.toString().orEmpty()
                            if (vs.isNotBlank()) {
                                changes += FieldChange("${table.name}/$rowId/$k", "", vs)
                            }
                        }
                    }
                    cur != null && snap != null -> {
                        StructuredDataDiff.diffMaps(curMap, snapMap).forEach { fc ->
                            changes += fc.copy(path = "${table.name}/$rowId/${fc.path}")
                        }
                    }
                }
            }
            val currentTable = memoryTableGateway.getTable(table.id)
            if (currentTable != null && currentTable.name != table.name) {
                changes += FieldChange("${table.id}/name", currentTable.name, table.name)
            }
        }
        val capped = if (changes.size > 60) {
            changes.take(60) + FieldChange("…", "", "+${changes.size - 60} more")
        } else changes
        return RestorePreview(
            snapshotId = snapshot.id,
            resourceType = "memory_table",
            summary = names.joinToString(", "),
            changes = capped,
        )
    }

    private fun parseRowData(rowData: String): Map<String, Any?> =
        runCatching {
            @Suppress("UNCHECKED_CAST")
            GSON.fromJson(rowData, Map::class.java) as? Map<String, Any?>
        }.getOrNull().orEmpty()

    private suspend fun buildPayload(ctx: StructuredMutationContext): String? = when (ctx.toolName) {
        AiToolRepository.TOOL_PATCH_HISTORY_MEMORY -> {
            val tableIds = ctx.memoryOps.mapNotNull { op ->
                when (op) {
                    is MemoryTableOp.PatchRow -> op.tableId
                    is MemoryTableOp.AddRow -> op.tableId
                    is MemoryTableOp.DeleteRow -> op.tableId
                    is MemoryTableOp.UpdateSchema -> op.tableId
                    is MemoryTableOp.RegenerateTable -> op.tableId
                    else -> null
                }
            }.distinct()
            if (tableIds.isEmpty()) return null
            val tables = tableIds.mapNotNull { tableId ->
                val table = memoryTableGateway.getTable(tableId) ?: return@mapNotNull null
                val rows = memoryTableGateway.getRows(tableId)
                AiMemoryTableExport.TableWithRows(table, rows)
            }
            if (tables.isEmpty()) return null
            GSON.toJson(
                AiMemoryTableExport(
                    conversationId = ctx.conversationId.orEmpty(),
                    tables = tables,
                )
            )
        }
        AiToolRepository.TOOL_PATCH_OUTLINE -> {
            val convId = ctx.conversationId ?: return null
            val outline = outlineGateway.getByConversation(convId) ?: return null
            GSON.toJson(
                AiOutlineSnapshotPayload(
                    content = outline.content,
                    enabled = outline.enabled,
                )
            )
        }
        AiToolRepository.TOOL_PATCH_CHARACTER_CARD -> {
            val cardId = ctx.characterPatch?.cardId ?: return null
            val card = characterCardGateway.getById(cardId) ?: return null
            GSON.toJson(card)
        }
        AiToolRepository.TOOL_PATCH_USER_CARD -> {
            val convId = ctx.conversationId ?: ctx.userCardPatch?.conversationId ?: return null
            val conv = aiChatGateway.getConversation(convId) ?: return null
            GSON.toJson(
                AiUserCardSnapshotPayload(
                    conversationId = conv.id,
                    userName = conv.userName,
                    userDescription = conv.userDescription,
                    enabled = conv.userCardEnabled,
                )
            )
        }
        AiToolRepository.TOOL_PATCH_WORLD_BOOK -> {
            val wbId = ctx.worldBookPatch?.worldBookId ?: return null
            val wb = worldBookGateway.getById(wbId) ?: return null
            GSON.toJson(wb)
        }
        AiToolRepository.TOOL_EXTRACT_WORLD_BOOK -> null
        else -> null
    }

    private fun resourceInfo(ctx: StructuredMutationContext): Pair<String, String> = when (ctx.toolName) {
        AiToolRepository.TOOL_PATCH_HISTORY_MEMORY -> {
            val tableId = ctx.memoryOps.firstNotNullOfOrNull { op ->
                when (op) {
                    is MemoryTableOp.PatchRow -> op.tableId
                    is MemoryTableOp.AddRow -> op.tableId
                    is MemoryTableOp.RegenerateTable -> op.tableId
                    is MemoryTableOp.UpdateSchema -> op.tableId
                    else -> null
                }
            }.orEmpty()
            "memory_table" to tableId
        }
        AiToolRepository.TOOL_PATCH_OUTLINE -> "outline" to ctx.conversationId.orEmpty()
        AiToolRepository.TOOL_PATCH_CHARACTER_CARD -> "character_card" to ctx.characterPatch?.cardId.orEmpty()
        AiToolRepository.TOOL_PATCH_USER_CARD -> "user_card" to ctx.conversationId.orEmpty()
        AiToolRepository.TOOL_PATCH_WORLD_BOOK -> "world_book" to ctx.worldBookPatch?.worldBookId.orEmpty()
        AiToolRepository.TOOL_EXTRACT_WORLD_BOOK -> "world_book" to ""
        else -> "unknown" to ""
    }

    private suspend fun restoreMemoryTable(payloadJson: String): RestoreResult {
        return runCatching {
            val export = GSON.fromJson(payloadJson, AiMemoryTableExport::class.java)
                ?: return@runCatching RestoreResult(false, "Invalid memory snapshot", "memory_table")
            for (tableWithRows in export.tables) {
                memoryTableGateway.upsertTable(tableWithRows.table)
                memoryTableGateway.replaceAllRows(tableWithRows.table.id, tableWithRows.rows)
            }
            RestoreResult(true, "Memory table restored", "memory_table")
        }.getOrElse { RestoreResult(false, it.message ?: "Restore failed", "memory_table") }
    }

    private suspend fun restoreOutline(payloadJson: String, conversationId: String): RestoreResult {
        return runCatching {
            val data = GSON.fromJson(payloadJson, AiOutlineSnapshotPayload::class.java)
                ?: return@runCatching RestoreResult(false, "Invalid outline snapshot", "outline")
            outlineGateway.upsert(
                AiOutline(
                    conversationId = conversationId,
                    content = data.content,
                    enabled = data.enabled,
                )
            )
            RestoreResult(true, "Outline restored", "outline")
        }.getOrElse { RestoreResult(false, it.message ?: "Restore failed", "outline") }
    }

    private suspend fun restoreCharacterCard(payloadJson: String): RestoreResult {
        return runCatching {
            val card = GSON.fromJson(payloadJson, AiCharacterCard::class.java)
                ?: return@runCatching RestoreResult(false, "Invalid character snapshot", "character_card")
            characterCardGateway.save(
                name = card.name,
                description = card.description,
                openingLine = card.openingLine,
                worldBookIds = card.worldBookIds,
                cardId = card.id,
            )
            RestoreResult(true, "Character card restored", "character_card")
        }.getOrElse { RestoreResult(false, it.message ?: "Restore failed", "character_card") }
    }

    private suspend fun restoreUserCard(payloadJson: String, conversationId: String): RestoreResult {
        return runCatching {
            val data = GSON.fromJson(payloadJson, AiUserCardSnapshotPayload::class.java)
                ?: return@runCatching RestoreResult(false, "Invalid user card snapshot", "user_card")
            val convId = data.conversationId.ifBlank { conversationId }
            aiChatGateway.updateConversationUserCard(
                convId,
                data.userName,
                data.userDescription,
                data.enabled,
            )
            RestoreResult(true, "User persona restored", "user_card")
        }.getOrElse { RestoreResult(false, it.message ?: "Restore failed", "user_card") }
    }

    private suspend fun restoreWorldBook(payloadJson: String): RestoreResult {
        return runCatching {
            val wb = GSON.fromJson(payloadJson, AiWorldBook::class.java)
                ?: return@runCatching RestoreResult(false, "Invalid world book snapshot", "world_book")
            worldBookGateway.save(
                name = wb.name,
                bookUrl = wb.bookUrl,
                bookName = wb.bookName,
                bookAuthor = wb.bookAuthor,
                writingStyle = wb.writingStyle,
                grammar = wb.grammar,
                plotSummary = wb.plotSummary,
                representativeDialogues = wb.representativeDialogues,
                representativeProse = wb.representativeProse,
                sourceChapterIndices = wb.sourceChapterIndices,
                worldBookId = wb.id,
                enabled = wb.enabled,
            )
            RestoreResult(true, "World book restored", "world_book")
        }.getOrElse { RestoreResult(false, it.message ?: "Restore failed", "world_book") }
    }
}
