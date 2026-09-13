package io.legado.app.data.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.domain.model.CharacterCardPatch
import io.legado.app.domain.model.MemoryTableOp
import io.legado.app.domain.model.OutlinePatchMode
import io.legado.app.domain.model.UserCardPatch
import io.legado.app.domain.model.WorldBookEntryPatch
import io.legado.app.domain.model.WorldBookPatch
import io.legado.app.utils.GSON

internal object StructuredDataToolParser {

    fun parseMemoryOpsFromArgs(args: JsonObject): List<MemoryTableOp> {
        val opsEl = args.get("operations") ?: return emptyList()
        return when {
            opsEl.isJsonArray -> parseMemoryTableOps(JsonObject().apply { add("operations", opsEl) })
            opsEl.isJsonPrimitive && opsEl.asJsonPrimitive.isString -> {
                val parsed = runCatching { JsonParser.parseString(opsEl.asString) }.getOrNull() ?: return emptyList()
                parseMemoryTableOps(JsonObject().apply { add("operations", parsed) })
            }
            else -> emptyList()
        }
    }

    fun parseMemoryTableOps(args: JsonObject): List<MemoryTableOp> {
        val opsArray = args.getAsJsonArray("operations") ?: return emptyList()
        return opsArray.mapNotNull { element ->
            val obj = element.asJsonObject
            when (obj.get("op")?.asString) {
                "create_table" -> MemoryTableOp.CreateTable(
                    name = obj.string("name").orEmpty(),
                    columns = obj.parseStringList("columns"),
                    rows = obj.parseRowList("rows"),
                    conversationId = obj.string("conversationId").orEmpty(),
                    bookUrl = obj.string("bookUrl").orEmpty(),
                    bookName = obj.string("bookName").orEmpty(),
                    bookAuthor = obj.string("bookAuthor").orEmpty(),
                    tableKind = obj.string("tableKind") ?: obj.string("kind"),
                )
                "update_schema" -> MemoryTableOp.UpdateSchema(
                    tableId = obj.string("tableId").orEmpty(),
                    name = obj.string("name"),
                    columns = obj.get("columns")?.let {
                        if (it.isJsonArray) it.asJsonArray.map { e -> e.asString }
                        else runCatching {
                            GSON.fromJson(it.asString, Array<String>::class.java).toList()
                        }.getOrNull()
                    },
                    bookUrl = obj.string("bookUrl"),
                    bookName = obj.string("bookName"),
                    bookAuthor = obj.string("bookAuthor"),
                )
                "add_row" -> MemoryTableOp.AddRow(
                    tableId = obj.string("tableId").orEmpty(),
                    data = obj.parseRowPayload(),
                )
                "patch_row" -> MemoryTableOp.PatchRow(
                    tableId = obj.string("tableId").orEmpty(),
                    rowId = obj.string("rowId"),
                    match = obj.parseMap("match"),
                    data = obj.parseRowPayload(alsoKeys = listOf("patchData")),
                )
                "delete_row" -> MemoryTableOp.DeleteRow(
                    tableId = obj.string("tableId"),
                    rowId = obj.string("rowId"),
                    match = obj.parseMap("match"),
                )
                "generate_tables" -> parseGenerateTablesOp(obj)
                "regenerate_table", "repair_table" -> MemoryTableOp.RegenerateTable(
                    tableId = obj.string("tableId").orEmpty(),
                    conversationId = obj.string("conversationId"),
                    hint = obj.string("hint"),
                )
                else -> null
            }
        }
    }

    fun parseOutlinePatch(args: JsonObject): OutlinePatchMode? {
        val mode = args.string("action") ?: "generate"
        return when (mode) {
            "replace" -> args.string("content")?.let { OutlinePatchMode.Replace(it) }
            "append" -> args.string("content")?.let { OutlinePatchMode.Append(it) }
            "search_replace" -> {
                val search = args.string("search") ?: return null
                OutlinePatchMode.SearchReplace(
                    search = search,
                    replace = args.string("replace").orEmpty(),
                    replaceAll = args.bool("replaceAll"),
                )
            }
            "patch_section" -> {
                val title = args.string("sectionTitle") ?: args.string("section") ?: return null
                OutlinePatchMode.PatchSection(title, args.string("content").orEmpty())
            }
            "graph_ops", "graph" -> {
                val opsEl = args.get("ops") ?: args.get("operations") ?: return null
                val result = when {
                    opsEl.isJsonArray ->
                        io.legado.app.domain.usecase.structured.graph.OutlineGraphOpParser
                            .parseOps(opsEl.asJsonArray)
                    opsEl.isJsonPrimitive && opsEl.asJsonPrimitive.isString ->
                        io.legado.app.domain.usecase.structured.graph.OutlineGraphOpParser
                            .parseOpsFromRawJson(opsEl.asString)
                    else ->
                        io.legado.app.domain.usecase.structured.graph.OutlineGraphOpParser
                            .ParseResult(emptyList(), listOf("ops must be a JSON array or string"))
                }
                // Truly empty → invalid args. Any parse error must surface (never apply a partial list).
                if (result.ops.isEmpty() && result.errors.isEmpty()) {
                    null
                } else {
                    OutlinePatchMode.GraphOps(result.ops, parseErrors = result.errors)
                }
            }
            "generate" -> OutlinePatchMode.Generate(
                conversationId = args.string("conversationId").orEmpty(),
                hint = args.string("hint"),
                supplement = args.bool("supplement"),
                writingSubMode = args.string("writingSubMode").orEmpty(),
            )
            else -> null
        }
    }

    fun parseCharacterCardPatch(args: JsonObject): CharacterCardPatch = CharacterCardPatch(
        cardId = args.string("cardId"),
        name = args.string("name"),
        description = args.string("description"),
        openingLine = args.string("openingLine"),
        worldBookIds = args.string("worldBookIds"),
        personality = args.string("personality"),
        scenario = args.string("scenario"),
        exampleDialogues = args.string("exampleDialogues"),
        postHistoryInstructions = args.string("postHistoryInstructions"),
        alternateOpenings = args.string("alternateOpenings"),
        aliasesJson = parseAliasesJsonArg(args),
        voiceGender = args.string("voiceGender"),
        voiceAgeBand = args.string("voiceAgeBand"),
        dramaticRole = args.string("dramaticRole"),
        bookUrl = args.string("bookUrl"),
        bookName = args.string("bookName"),
        bookAuthor = args.string("bookAuthor"),
        conversationId = args.string("conversationId"),
        hint = args.string("hint"),
        generate = args.string("action") == "generate",
    )

    /** Accepts aliasesJson, or aliases as JSON array / comma-separated string. */
    fun parseAliasesJsonArg(args: JsonObject): String? {
        args.string("aliasesJson")?.takeIf { it.isNotBlank() }?.let { return it }
        val el = args.get("aliases")?.takeIf { !it.isJsonNull } ?: return null
        return when {
            el.isJsonArray -> GSON.toJson(el.asJsonArray)
            el.isJsonPrimitive -> {
                val raw = el.asString.trim()
                when {
                    raw.isBlank() -> null
                    raw.startsWith("[") -> raw
                    else -> GSON.toJson(
                        raw.split(',', '，', ';', '；', '\n')
                            .map { it.trim() }
                            .filter { it.isNotBlank() },
                    )
                }
            }
            else -> null
        }
    }

    fun parseUserCardPatch(args: JsonObject): UserCardPatch = UserCardPatch(
        conversationId = args.string("conversationId"),
        name = args.string("userName") ?: args.string("name"),
        description = args.string("userDescription") ?: args.string("description"),
        enabled = args.get("enabled")?.takeIf { !it.isJsonNull }?.let { runCatching { it.asBoolean }.getOrNull() },
        hint = args.string("hint"),
        generate = args.string("action") == "generate",
    )

    fun parseWorldBookPatch(args: JsonObject): WorldBookPatch? {
        val id = args.string("worldBookId")
        val name = args.string("name") ?: args.string("worldBookName")
        val generate = args.bool("generate") || args.string("action") == "generate"
        val entry = parseWorldBookEntryPatch(args)
        if (id.isNullOrBlank() && name.isNullOrBlank() && !generate && entry == null) return null
        return WorldBookPatch(
            worldBookId = id,
            name = name,
            bookUrl = args.string("bookUrl"),
            bookName = args.string("bookName"),
            bookAuthor = args.string("bookAuthor"),
            writingStyle = args.string("writingStyle"),
            grammar = args.string("grammar"),
            plotSummary = args.string("plotSummary"),
            representativeDialogues = args.string("representativeDialogues"),
            representativeProse = args.string("representativeProse"),
            sourceChapterIndices = args.string("sourceChapterIndices") ?: args.string("chapterIndices"),
            conversationId = args.string("conversationId"),
            hint = args.string("hint"),
            generate = generate,
            entry = entry,
        )
    }

    private fun parseWorldBookEntryPatch(args: JsonObject): WorldBookEntryPatch? {
        val entryOp = args.string("entryOp")?.trim()?.lowercase()
        val entryId = args.string("entryId")
        val deleteEntry = args.bool("deleteEntry") || entryOp == "delete"
        val hasFields = sequenceOf(
            "entryName", "keys", "content", "constant", "priority",
            "enabled", "position", "insertDepth", "role", "scanDepth",
        ).any { key -> args.get(key)?.takeIf { !it.isJsonNull } != null }
        if (entryOp.isNullOrBlank() && entryId.isNullOrBlank() && !hasFields && !deleteEntry) {
            return null
        }
        return WorldBookEntryPatch(
            entryId = entryId,
            op = if (deleteEntry) "delete" else (entryOp ?: "upsert"),
            name = args.string("entryName"),
            keys = args.string("keys"),
            content = args.string("content"),
            constant = args.optionalBool("constant"),
            priority = args.optionalInt("priority"),
            enabled = args.optionalBool("enabled"),
            position = args.string("position"),
            insertDepth = args.optionalInt("insertDepth"),
            role = args.string("role"),
            scanDepth = args.optionalInt("scanDepth"),
        )
    }

    fun memoryOpsToJsonArray(ops: List<io.legado.app.domain.model.MemoryTableOp>): com.google.gson.JsonArray {
        val array = com.google.gson.JsonArray()
        ops.forEach { op ->
            val obj = JsonObject()
            obj.addProperty("op", op.op)
            when (op) {
                is MemoryTableOp.CreateTable -> {
                    obj.addProperty("name", op.name)
                    obj.addProperty("conversationId", op.conversationId)
                    op.tableKind?.let { obj.addProperty("tableKind", it) }
                    if (op.bookUrl.isNotBlank()) obj.addProperty("bookUrl", op.bookUrl)
                    if (op.bookName.isNotBlank()) obj.addProperty("bookName", op.bookName)
                    if (op.bookAuthor.isNotBlank()) obj.addProperty("bookAuthor", op.bookAuthor)
                    obj.add("columns", GSON.toJsonTree(op.columns))
                    if (op.rows.isNotEmpty()) obj.add("rows", GSON.toJsonTree(op.rows))
                }
                is MemoryTableOp.UpdateSchema -> {
                    obj.addProperty("tableId", op.tableId)
                    op.name?.let { obj.addProperty("name", it) }
                    op.columns?.let { obj.add("columns", GSON.toJsonTree(it)) }
                    op.bookUrl?.let { obj.addProperty("bookUrl", it) }
                    op.bookName?.let { obj.addProperty("bookName", it) }
                    op.bookAuthor?.let { obj.addProperty("bookAuthor", it) }
                }
                is MemoryTableOp.AddRow -> {
                    obj.addProperty("tableId", op.tableId)
                    obj.add("data", GSON.toJsonTree(op.data))
                }
                is MemoryTableOp.PatchRow -> {
                    obj.addProperty("tableId", op.tableId)
                    op.rowId?.let { obj.addProperty("rowId", it) }
                    op.match?.let { obj.add("match", GSON.toJsonTree(it)) }
                    obj.add("data", GSON.toJsonTree(op.data))
                }
                is MemoryTableOp.DeleteRow -> {
                    op.tableId?.let { obj.addProperty("tableId", it) }
                    op.rowId?.let { obj.addProperty("rowId", it) }
                    op.match?.let { obj.add("match", GSON.toJsonTree(it)) }
                }
                is MemoryTableOp.GenerateTables -> {
                    obj.addProperty("conversationId", op.conversationId)
                    op.tableKind?.let { obj.addProperty("tableKind", it) }
                    op.name?.let { obj.addProperty("name", it) }
                    op.columns?.let { obj.add("columns", GSON.toJsonTree(it)) }
                    op.hint?.let { obj.addProperty("hint", it) }
                    if (op.bookUrl.isNotBlank()) obj.addProperty("bookUrl", op.bookUrl)
                    if (op.bookName.isNotBlank()) obj.addProperty("bookName", op.bookName)
                    if (op.bookAuthor.isNotBlank()) obj.addProperty("bookAuthor", op.bookAuthor)
                }
                is MemoryTableOp.RegenerateTable -> {
                    obj.addProperty("tableId", op.tableId)
                    op.conversationId?.let { obj.addProperty("conversationId", it) }
                    op.hint?.let { obj.addProperty("hint", it) }
                }
            }
            array.add(obj)
        }
        return array
    }

    private fun parseGenerateTablesOp(obj: JsonObject): MemoryTableOp.GenerateTables {
        val tableKind = obj.string("tableKind") ?: obj.string("kind")
        return MemoryTableOp.GenerateTables(
            conversationId = obj.string("conversationId").orEmpty(),
            hint = obj.string("hint"),
            tableKind = tableKind,
            name = obj.string("name"),
            columns = obj.get("columns")?.let {
                if (it.isJsonArray) it.asJsonArray.map { e -> e.asString }
                else runCatching {
                    GSON.fromJson(it.asString, Array<String>::class.java).toList()
                }.getOrNull()
            },
            bookUrl = obj.string("bookUrl").orEmpty(),
            bookName = obj.string("bookName").orEmpty(),
            bookAuthor = obj.string("bookAuthor").orEmpty(),
        )
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { !it.isJsonNull }?.asString

    private fun JsonObject.bool(name: String): Boolean =
        runCatching { get(name)?.asBoolean }.getOrNull() ?: false

    private fun JsonObject.optionalBool(name: String): Boolean? {
        val el = get(name)?.takeIf { !it.isJsonNull } ?: return null
        return runCatching { el.asBoolean }.getOrNull()
            ?: runCatching { el.asString.toBooleanStrict() }.getOrNull()
    }

    private fun JsonObject.optionalInt(name: String): Int? {
        val el = get(name)?.takeIf { !it.isJsonNull } ?: return null
        return runCatching { el.asInt }.getOrNull()
            ?: runCatching { el.asString.trim().toInt() }.getOrNull()
    }

    private fun JsonObject.parseStringList(name: String): List<String> {
        val el = get(name) ?: return emptyList()
        return if (el.isJsonArray) el.asJsonArray.map { it.asString }
        else runCatching { GSON.fromJson(el.asString, Array<String>::class.java).toList() }.getOrNull() ?: emptyList()
    }

    private fun JsonObject.parseMap(name: String): Map<String, Any?>? {
        val el = get(name) ?: return null
        return if (el.isJsonObject) {
            el.asJsonObject.entrySet().associate { (k, v) ->
                k to when {
                    v.isJsonNull -> null
                    v.isJsonPrimitive -> {
                        val p = v.asJsonPrimitive
                        when {
                            p.isBoolean -> p.asBoolean
                            p.isNumber -> p.asNumber
                            else -> p.asString
                        }
                    }
                    else -> v.toString()
                }
            }
        } else if (el.isJsonPrimitive && el.asJsonPrimitive.isString) {
            parseMapFromString(name)
        } else {
            null
        }
    }

    /**
     * Row payload for add_row / patch_row.
     * Accepts object aliases: data | values | rowData | row (+ optional patchData).
     * Also accepts parallel arrays: columns + values (or data as array).
     */
    private fun JsonObject.parseRowPayload(
        alsoKeys: List<String> = emptyList(),
    ): Map<String, Any?> {
        val objectKeys = listOf("data", "values", "rowData", "row") + alsoKeys
        for (key in objectKeys) {
            parseMap(key)?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        // columns: ["A","B"] + values/data: ["x","y"]
        val columns = parseStringList("columns")
        if (columns.isNotEmpty()) {
            val valueArray = sequenceOf("values", "data", "row")
                .mapNotNull { key -> get(key)?.takeIf { !it.isJsonNull } }
                .firstOrNull { it.isJsonArray }
                ?.asJsonArray
            if (valueArray != null) {
                val zipped = linkedMapOf<String, Any?>()
                columns.forEachIndexed { index, col ->
                    if (col.isBlank()) return@forEachIndexed
                    val cell = if (index < valueArray.size()) valueArray[index] else null
                    zipped[col] = when {
                        cell == null || cell.isJsonNull -> null
                        cell.isJsonPrimitive -> {
                            val p = cell.asJsonPrimitive
                            when {
                                p.isBoolean -> p.asBoolean
                                p.isNumber -> p.asNumber
                                else -> p.asString
                            }
                        }
                        else -> cell.toString()
                    }
                }
                if (zipped.isNotEmpty()) return zipped
            }
        }
        return emptyMap()
    }

    private fun JsonObject.parseMapFromString(name: String): Map<String, Any?>? {
        val str = string(name) ?: return null
        val map = io.legado.app.utils.parseJsonStringMap(str)
        return map.ifEmpty { null }
    }

    private fun JsonObject.parseRowList(name: String): List<Map<String, Any?>> {
        val el = get(name) ?: return emptyList()
        val array = if (el.isJsonArray) el.asJsonArray
        else runCatching { JsonParser.parseString(el.asString).asJsonArray }.getOrNull() ?: return emptyList()
        return array.map { row ->
            row.asJsonObject.entrySet().associate { (k, v) ->
                k to when {
                    v.isJsonNull -> null
                    v.isJsonPrimitive -> v.asString
                    else -> v.toString()
                }
            }
        }
    }
}
