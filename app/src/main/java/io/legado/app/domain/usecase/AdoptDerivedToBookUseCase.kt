package io.legado.app.domain.usecase

import io.legado.app.data.dao.BookDao
import io.legado.app.data.entities.AiBookOutline
import io.legado.app.data.entities.AiCharacterCard
import io.legado.app.data.entities.AiMemoryTable
import io.legado.app.data.entities.AiMemoryTableExport
import io.legado.app.data.entities.AiWorldBook
import io.legado.app.domain.gateway.AiBookOutlineGateway
import io.legado.app.domain.gateway.AiCharacterCardGateway
import io.legado.app.domain.gateway.AiMemoryTableGateway
import io.legado.app.domain.gateway.AiOutlineGateway
import io.legado.app.domain.gateway.AiWorldBookEntryGateway
import io.legado.app.domain.gateway.AiWorldBookGateway
import io.legado.app.domain.gateway.AiWorkspaceGateway
import io.legado.app.domain.gateway.ReadAloudCharacterGateway
import io.legado.app.domain.usecase.structured.FieldChange
import io.legado.app.domain.usecase.structured.MutationSnapshotService
import io.legado.app.domain.usecase.structured.StructuredDataDiff
import io.legado.app.utils.AiIdListCodec
import io.legado.app.utils.GSON
import java.util.UUID

/**
 * 采纳桥:衍生会话的创作结果 → 书级正典层(唯一写回通道)。
 *
 * 各类型分别采纳:
 * - 大纲:复制内容进 [AiBookOutline](PK=bookUrl)。
 * - 记忆表/关系网:逐行合并进正典表(同名正典表不存在则新建)。
 * - 角色卡:fork 卡内容 upsert 回正典源卡;非 fork 卡新建 canonical 卡并 addToCast。
 * - 世界书:合并字段与条目进同名正典世界书(不存在则新建)。
 *
 * 每次采纳前强制快照正典行(可回滚)。
 */
class AdoptDerivedToBookUseCase(
    private val workspaceGateway: AiWorkspaceGateway,
    private val outlineGateway: AiOutlineGateway,
    private val bookOutlineGateway: AiBookOutlineGateway,
    private val memoryTableGateway: AiMemoryTableGateway,
    private val characterCardGateway: AiCharacterCardGateway,
    private val worldBookGateway: AiWorldBookGateway,
    private val worldBookEntryGateway: AiWorldBookEntryGateway,
    private val readAloudCharacterGateway: ReadAloudCharacterGateway,
    private val mutationSnapshotService: MutationSnapshotService,
    private val bookDao: BookDao,
) {

    data class AdoptSection(
        val type: String,
        val label: String,
        val summary: String,
        val changes: List<FieldChange>,
    )

    data class AdoptPreview(val sections: List<AdoptSection>) {
        val totalChanges: Int get() = sections.sumOf { it.changes.size }
        val types: Set<String> get() = sections.map { it.type }.toSet()
    }

    data class AdoptResult(val success: Boolean, val message: String = "")

    companion object {
        const val TYPE_OUTLINE = "outline"
        const val TYPE_MEMORY = "memory"
        const val TYPE_CARDS = "cards"
        const val TYPE_WORLDBOOK = "worldbook"
        val ALL_TYPES = setOf(TYPE_OUTLINE, TYPE_MEMORY, TYPE_CARDS, TYPE_WORLDBOOK)
    }

    /** 计算衍生会话可采纳到正典的内容预览。未绑定书返回空。 */
    suspend fun preview(conversationId: String): AdoptPreview {
        val workspace = workspaceGateway.getByConversationId(conversationId)
            ?: return AdoptPreview(emptyList())
        val bookUrl = workspace.bookUrl
        if (bookUrl.isBlank()) return AdoptPreview(emptyList())
        val sections = mutableListOf<AdoptSection>()

        outlineGateway.getByConversation(conversationId)?.let { outline ->
            if (outline.content.isNotBlank()) {
                val canonical = bookOutlineGateway.getByBookUrl(bookUrl)
                sections += AdoptSection(
                    type = "outline",
                    label = "大纲",
                    summary = if (canonical?.content.isNullOrBlank()) "新建正典大纲" else "更新正典大纲",
                    changes = StructuredDataDiff.diffTextLines(canonical?.content.orEmpty(), outline.content),
                )
            }
        }

        val derivedTables = memoryTableGateway.getEnabledForConversation(conversationId).filter { !it.canonical }
        if (derivedTables.isNotEmpty()) {
            val canonicalTables = memoryTableGateway.getByBookUrl(bookUrl)
            val changes = derivedTables.map { table ->
                val target = canonicalTables.firstOrNull { it.canonical && it.name == table.name }
                val label = if (target != null) "合并到正典表" else "新建正典表"
                FieldChange(
                    "memory/${table.name}",
                    if (target != null) target.id else "",
                    label + "（${memoryTableGateway.getRows(table.id).size} 行）",
                )
            }
            sections += AdoptSection("memory", "记忆表/关系网", "${derivedTables.size} 张表", changes)
        }

        val derivedCards = AiIdListCodec.parse(workspace.characterCardIds)
            .mapNotNull { characterCardGateway.getById(it) }
            .filter { !it.canonical }
        if (derivedCards.isNotEmpty()) {
            val changes = derivedCards.map { card ->
                val targetId = card.forkedFromCardId?.takeIf { it.isNotBlank() }
                FieldChange(
                    "card/${card.name}",
                    targetId.orEmpty(),
                    if (targetId != null) "更新正典卡" else "新建正典卡并加入书内 cast",
                )
            }
            sections += AdoptSection("cards", "角色卡", "${derivedCards.size} 张", changes)
        }

        val derivedWbs = AiIdListCodec.parse(workspace.worldBookIds)
            .mapNotNull { worldBookGateway.getById(it) }
            .filter { !it.canonical }
        if (derivedWbs.isNotEmpty()) {
            val changes = derivedWbs.map { wb ->
                FieldChange("worldbook/${wb.name}", wb.id, "合并字段与条目进正典世界书")
            }
            sections += AdoptSection("worldbook", "世界书", "${derivedWbs.size} 本", changes)
        }

        return AdoptPreview(sections)
    }

    /** 执行采纳。未绑定书时拒绝。[types] 限定本次采纳的内容类型。 */
    suspend fun adopt(conversationId: String, types: Set<String> = ALL_TYPES): AdoptResult {
        val workspace = workspaceGateway.getByConversationId(conversationId)
            ?: return AdoptResult(false, "未找到工作区")
        val bookUrl = workspace.bookUrl
        if (bookUrl.isBlank()) {
            return AdoptResult(false, "工作区未绑定本书，无法写入")
        }
        val book = bookUrl.let { bookDao.getBook(it) }
        val adopted = mutableListOf<String>()

        // ---- 大纲 ----
        if (TYPE_OUTLINE in types) {
            outlineGateway.getByConversation(conversationId)?.let { outline ->
                if (outline.content.isNotBlank()) {
                    val before = bookOutlineGateway.getByBookUrl(bookUrl)
                    mutationSnapshotService.captureCanonicalSnapshot(
                        conversationId = conversationId,
                        resourceType = "outline",
                        resourceKey = bookUrl,
                        beforeJson = GSON.toJson(
                            before ?: AiBookOutline(bookUrl, "", true, conversationId, 0L),
                        ),
                    )
                    bookOutlineGateway.upsert(
                        AiBookOutline(
                            bookUrl = bookUrl,
                            content = outline.content,
                            enabled = before?.enabled ?: true,
                            sourceConversationId = conversationId,
                        ),
                    )
                    adopted += "大纲"
                }
            }
        }

        // ---- 记忆表 / 关系网:逐行合并进正典表 ----
        if (TYPE_MEMORY in types) {
            val derivedTables = memoryTableGateway.getEnabledForConversation(conversationId).filter { !it.canonical }
            val canonicalTables = memoryTableGateway.getByBookUrl(bookUrl)
            if (derivedTables.isNotEmpty()) {
                for (table in derivedTables) {
                var target = canonicalTables.firstOrNull { it.canonical && it.name == table.name }
                if (target == null) {
                    target = AiMemoryTable(
                        id = "memtable_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                        name = table.name,
                        columns = table.columns,
                        conversationId = conversationId,
                        bookUrl = bookUrl,
                        bookName = table.bookName.ifBlank { book?.name.orEmpty() },
                        bookAuthor = table.bookAuthor.ifBlank { book?.author.orEmpty() },
                        canonical = true,
                        enabled = table.enabled,
                    )
                    memoryTableGateway.upsertTable(target)
                }
                val rowsBefore = memoryTableGateway.getRows(target.id)
                mutationSnapshotService.captureCanonicalSnapshot(
                    conversationId = conversationId,
                    resourceType = "memory_table",
                    resourceKey = target.id,
                    beforeJson = GSON.toJson(
                        AiMemoryTableExport(
                            conversationId = conversationId,
                            tables = listOf(AiMemoryTableExport.TableWithRows(target, rowsBefore)),
                        ),
                    ),
                )
                for (row in memoryTableGateway.getRows(table.id)) {
                    memoryTableGateway.upsertRow(row.copy(tableId = target.id))
                }
            }
            adopted += "记忆表"
            }
        }

        // ---- 角色卡 ----
        if (TYPE_CARDS in types) {
            val derivedCards = AiIdListCodec.parse(workspace.characterCardIds)
                .mapNotNull { characterCardGateway.getById(it) }
                .filter { !it.canonical }
            if (derivedCards.isNotEmpty()) {
                for (card in derivedCards) {
                val sourceId = card.forkedFromCardId?.takeIf { it.isNotBlank() }
                if (sourceId != null) {
                    val source = characterCardGateway.getById(sourceId)
                    if (source != null) {
                        mutationSnapshotService.captureCanonicalSnapshot(
                            conversationId = conversationId,
                            resourceType = "character_card",
                            resourceKey = source.id,
                            beforeJson = GSON.toJson(source),
                        )
                        // fork 内容回写正典源卡。
                        characterCardGateway.upsert(
                            card.copy(
                                id = source.id,
                                canonical = true,
                                forkedFromCardId = source.forkedFromCardId,
                                createdAt = source.createdAt,
                            ),
                        )
                    }
                } else {
                    val newCard = characterCardGateway.upsert(
                        card.copy(canonical = true, forkedFromCardId = ""),
                    )
                    readAloudCharacterGateway.addToCast(bookUrl, newCard.id)
                }
            }
            adopted += "角色卡"
            }
        }

        // ---- 世界书 ----
        if (TYPE_WORLDBOOK in types) {
            val derivedWbs = AiIdListCodec.parse(workspace.worldBookIds)
                .mapNotNull { worldBookGateway.getById(it) }
                .filter { !it.canonical }
            if (derivedWbs.isNotEmpty()) {
            val canonicalWbs = worldBookGateway.getEnabled().filter { it.canonical && it.bookUrl == bookUrl }
            for (wb in derivedWbs) {
                val target = canonicalWbs.firstOrNull { it.name == wb.name }
                val targetId: String
                if (target == null) {
                    val saved = worldBookGateway.save(
                        name = wb.name,
                        bookUrl = bookUrl,
                        bookName = wb.bookName.ifBlank { book?.name.orEmpty() },
                        bookAuthor = wb.bookAuthor.ifBlank { book?.author.orEmpty() },
                        writingStyle = wb.writingStyle,
                        grammar = wb.grammar,
                        plotSummary = wb.plotSummary,
                        representativeDialogues = wb.representativeDialogues,
                        representativeProse = wb.representativeProse,
                        sourceChapterIndices = wb.sourceChapterIndices,
                        worldBookId = null,
                        enabled = wb.enabled,
                        canonical = true,
                    )
                    targetId = saved.id
                } else {
                    mutationSnapshotService.captureCanonicalSnapshot(
                        conversationId = conversationId,
                        resourceType = "world_book",
                        resourceKey = target.id,
                        beforeJson = GSON.toJson(target),
                    )
                    worldBookGateway.save(
                        name = target.name,
                        bookUrl = target.bookUrl,
                        bookName = target.bookName,
                        bookAuthor = target.bookAuthor,
                        writingStyle = if (wb.writingStyle.isNotBlank()) wb.writingStyle else target.writingStyle,
                        grammar = if (wb.grammar.isNotBlank()) wb.grammar else target.grammar,
                        plotSummary = if (wb.plotSummary.isNotBlank()) wb.plotSummary else target.plotSummary,
                        representativeDialogues = if (wb.representativeDialogues.isNotBlank()) wb.representativeDialogues else target.representativeDialogues,
                        representativeProse = if (wb.representativeProse.isNotBlank()) wb.representativeProse else target.representativeProse,
                        sourceChapterIndices = target.sourceChapterIndices,
                        worldBookId = target.id,
                        enabled = target.enabled,
                        canonical = true,
                    )
                    targetId = target.id
                }
                // 合并条目:衍生世界书条目搬进正典世界书。
                worldBookEntryGateway.getForWorldBook(wb.id).forEach { entry ->
                    worldBookEntryGateway.upsert(
                        entry.copy(
                            id = "wbe_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                            worldBookId = targetId,
                        ),
                    )
                }
            }
            adopted += "世界书"
            }
        }

        if (adopted.isEmpty()) return AdoptResult(false, "没有可采纳的衍生内容")
        return AdoptResult(true, "已写入本书：${adopted.joinToString("、")}")
    }
}
