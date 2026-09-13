package io.legado.app.domain.model

/** Patch operations for history memory tables. */
sealed interface MemoryTableOp {
    val op: String

    data class CreateTable(
        val name: String,
        val columns: List<String>,
        val rows: List<Map<String, Any?>> = emptyList(),
        val conversationId: String = "",
        val bookUrl: String = "",
        val bookName: String = "",
        val bookAuthor: String = "",
        /** When "relation", name/columns are normalized to the canonical relationship schema. */
        val tableKind: String? = null,
    ) : MemoryTableOp {
        override val op = "create_table"
    }

    data class UpdateSchema(
        val tableId: String,
        val name: String? = null,
        val columns: List<String>? = null,
        val bookUrl: String? = null,
        val bookName: String? = null,
        val bookAuthor: String? = null,
    ) : MemoryTableOp {
        override val op = "update_schema"
    }

    data class AddRow(
        val tableId: String,
        val data: Map<String, Any?>,
    ) : MemoryTableOp {
        override val op = "add_row"
    }

    data class PatchRow(
        val tableId: String,
        val rowId: String? = null,
        val match: Map<String, Any?>? = null,
        val data: Map<String, Any?>,
    ) : MemoryTableOp {
        override val op = "patch_row"
    }

    data class DeleteRow(
        val tableId: String? = null,
        val rowId: String? = null,
        val match: Map<String, Any?>? = null,
    ) : MemoryTableOp {
        override val op = "delete_row"
    }

    /**
     * AI-generate memory table(s).
     * - [tableKind] null/blank: generate 2–4 common tables (timeline, character, relation, …).
     * - [tableKind] set (e.g. "relation"): generate one table of that kind with enforced schema.
     */
    data class GenerateTables(
        val conversationId: String,
        val hint: String? = null,
        val tableKind: String? = null,
        val name: String? = null,
        val columns: List<String>? = null,
        val bookUrl: String = "",
        val bookName: String = "",
        val bookAuthor: String = "",
    ) : MemoryTableOp {
        override val op = "generate_tables"

        val isSingleKind: Boolean get() = !tableKind.isNullOrBlank()
    }

    /** Full-table repair/dedupe. Only when user asks to 整理/去重/修复. */
    data class RegenerateTable(
        val tableId: String,
        val conversationId: String? = null,
        val hint: String? = null,
    ) : MemoryTableOp {
        override val op = "regenerate_table"
    }
}

/** Patch modes for story outline. */
sealed interface OutlinePatchMode {
    data class Replace(val content: String) : OutlinePatchMode
    data class Append(val content: String) : OutlinePatchMode
    data class SearchReplace(
        val search: String,
        val replace: String,
        val replaceAll: Boolean = false,
    ) : OutlinePatchMode

    data class PatchSection(val sectionTitle: String, val content: String) : OutlinePatchMode
    data class Generate(
        val conversationId: String,
        val hint: String? = null,
        val supplement: Boolean = false,
        /** "roleplay" | "author"; blank uses legacy generate prompt. */
        val writingSubMode: String = "",
    ) : OutlinePatchMode

    /** Structured graph mutations (runtime source of truth). */
    data class GraphOps(
        val ops: List<io.legado.app.domain.usecase.structured.graph.OutlineGraphOp>,
        /** Rejected ops while parsing — non-empty means the call MUST fail, not apply a partial list. */
        val parseErrors: List<String> = emptyList(),
    ) : OutlinePatchMode
}

/** Field-level patch for character cards. */
data class CharacterCardPatch(
    val cardId: String? = null,
    val name: String? = null,
    val description: String? = null,
    val openingLine: String? = null,
    val worldBookIds: String? = null,
    val personality: String? = null,
    val scenario: String? = null,
    val exampleDialogues: String? = null,
    val postHistoryInstructions: String? = null,
    val alternateOpenings: String? = null,
    val aliasesJson: String? = null,
    val voiceGender: String? = null,
    val voiceAgeBand: String? = null,
    val dramaticRole: String? = null,
    val avatarPath: String? = null,
    val bookUrl: String? = null,
    val bookName: String? = null,
    val bookAuthor: String? = null,
    val conversationId: String? = null,
    val hint: String? = null,
    val generate: Boolean = false,
)

/** Field-level patch for the current conversation's user persona (chat mode). */
data class UserCardPatch(
    val conversationId: String? = null,
    val name: String? = null,
    val description: String? = null,
    val enabled: Boolean? = null,
    val hint: String? = null,
    val generate: Boolean = false,
)

/** Field-level patch for a single lore entry under a world book. */
data class WorldBookEntryPatch(
    val entryId: String? = null,
    /** upsert (default) or delete */
    val op: String = "upsert",
    val name: String? = null,
    val keys: String? = null,
    val content: String? = null,
    val constant: Boolean? = null,
    val priority: Int? = null,
    val enabled: Boolean? = null,
    val position: String? = null,
    val insertDepth: Int? = null,
    val role: String? = null,
    val scanDepth: Int? = null,
) {
    val isDelete: Boolean get() = op.equals("delete", ignoreCase = true)
}

/** Field-level patch for world books. Use generate=true to AI-summarize from conversation; omit worldBookId to create. */
data class WorldBookPatch(
    val worldBookId: String? = null,
    val name: String? = null,
    val bookUrl: String? = null,
    val bookName: String? = null,
    val bookAuthor: String? = null,
    val writingStyle: String? = null,
    val grammar: String? = null,
    val plotSummary: String? = null,
    val representativeDialogues: String? = null,
    val representativeProse: String? = null,
    val sourceChapterIndices: String? = null,
    val conversationId: String? = null,
    val hint: String? = null,
    val generate: Boolean = false,
    /** Optional lore-entry create/update/delete (requires [worldBookId]). */
    val entry: WorldBookEntryPatch? = null,
)

/** Deep link target for structured data sheets. */
data class StructuredDataDeepLink(
    val resourceType: String,
    val tableId: String? = null,
    val rowId: String? = null,
    val matchHint: String? = null,
    val outlineSection: String? = null,
    val cardId: String? = null,
    val worldBookId: String? = null,
    val conversationId: String? = null,
    val openTableList: Boolean = false,
)
