package io.legado.app.domain.model

import io.legado.app.data.entities.AiCharacterCard
import io.legado.app.data.entities.AiMemoryTable
import io.legado.app.data.entities.AiMemoryTableRow
import io.legado.app.data.repository.parseAliasesJson
import io.legado.app.domain.usecase.structured.MemoryTableRowMatcher
import io.legado.app.utils.DebugLog
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject

/**
 * Pure parser: turn book-bound memory "relation" tables into a character graph.
 *
 * Supports two row shapes (other columns such as 备注 are ignored):
 * - [角色名] + [关系]: edge targets are names mentioned in the relation text.
 * - [角色A]/[一方] + [角色B]/[另一方] + [关系]: one directed edge per row.
 */
object RelationGraphParser {

    private const val TAG = "RelationGraph"

    data class GraphNode(
        val id: String,
        val name: String,
        val characterCardId: String? = null,
    )

    data class GraphEdge(
        val id: String,
        val fromNodeId: String,
        val toNodeId: String,
        val fromName: String,
        val toName: String,
        val relationType: String,
        val summary: String,
        val tableId: String,
        val rowId: String,
    )

    data class Graph(
        val nodes: List<GraphNode>,
        val edges: List<GraphEdge>,
    )

    private fun logD(message: String) {
        runCatching { DebugLog.d(TAG, message) }
    }

    fun isRelationTable(table: AiMemoryTable): Boolean {
        val columns = parseColumns(table.columns)
        return detectNameRelationColumns(columns) != null ||
            MemoryTableRowMatcher.detectRelationshipPairKeys(table.name, columns) != null
    }

    fun build(
        tables: List<AiMemoryTable>,
        rowsByTableId: Map<String, List<AiMemoryTableRow>>,
        characterCards: List<AiCharacterCard>,
    ): Graph {
        val relationTables = tables.filter(::isRelationTable)
        logD("build: tables=${tables.size}, relationTables=${relationTables.size}, cards=${characterCards.size}")
        tables.forEach { table ->
            val columns = parseColumns(table.columns)
            val isRelation = isRelationTable(table)
            val nameCols = detectNameRelationColumns(columns)
            val pairCols = MemoryTableRowMatcher.detectRelationshipPairKeys(table.name, columns)
            val relCol = detectRelationTypeColumn(columns)
            logD(
                "table id=${table.id} name=${table.name} isRelation=$isRelation columns=$columns" +
                    when {
                        nameCols != null -> " mode=nameRelation nameCol=${nameCols.first} relCol=${nameCols.second}"
                        pairCols != null -> " mode=pair fromCol=${pairCols.first} toCol=${pairCols.second} relCol=$relCol"
                        else -> ""
                    },
            )
        }
        val cardByAlias = buildCardLookup(characterCards)
        val nodeMap = LinkedHashMap<String, GraphNode>()

        fun ensureNode(rawName: String): GraphNode {
            val name = rawName.trim()
            val key = name.lowercase()
            nodeMap[key]?.let { return it }
            val card = cardByAlias[key]
            val node = GraphNode(
                id = card?.id ?: "name:$key",
                name = card?.name ?: name,
                characterCardId = card?.id,
            )
            nodeMap[key] = node
            return node
        }

        characterCards.forEach { card ->
            val key = card.name.trim().lowercase()
            if (key.isNotEmpty()) {
                nodeMap.putIfAbsent(
                    key,
                    GraphNode(id = card.id, name = card.name, characterCardId = card.id),
                )
            }
        }

        val edges = mutableListOf<GraphEdge>()
        relationTables.forEach { table ->
            val columns = parseColumns(table.columns)
            val rows = rowsByTableId[table.id].orEmpty()
            detectNameRelationColumns(columns)?.let { (nameCol, relCol) ->
                parseNameRelationTable(
                    table = table,
                    rows = rows,
                    nameCol = nameCol,
                    relCol = relCol,
                    characterCards = characterCards,
                    ensureNode = ::ensureNode,
                    edges = edges,
                )
                return@forEach
            }
            val pair = MemoryTableRowMatcher.detectRelationshipPairKeys(table.name, columns)
                ?: return@forEach
            val (fromCol, toCol) = pair
            val relCol = detectRelationTypeColumn(columns)
            logD("parse pair table id=${table.id} rows=${rows.size} fromCol=$fromCol toCol=$toCol relCol=$relCol")
            rows.forEach { row ->
                val data = parseRowData(row.rowData)
                val fromName = data[fromCol]?.trim().orEmpty()
                val toName = data[toCol]?.trim().orEmpty()
                val relation = relCol?.let { data[it] }?.trim().orEmpty()
                val ignoredKeys = data.keys.filter { it != fromCol && it != toCol && it != relCol }
                if (fromName.isBlank() || toName.isBlank()) {
                    logD("skip row id=${row.id}: blank pair, dataKeys=${data.keys}")
                    return@forEach
                }
                if (fromName.equals(toName, ignoreCase = true)) {
                    logD("skip row id=${row.id}: same from/to=$fromName")
                    return@forEach
                }
                val from = ensureNode(fromName)
                val to = ensureNode(toName)
                logD("row id=${row.id} $fromName -> $toName relation=$relation ignoredKeys=$ignoredKeys")
                edges += GraphEdge(
                    id = row.id,
                    fromNodeId = from.id,
                    toNodeId = to.id,
                    fromName = from.name,
                    toName = to.name,
                    relationType = relation,
                    summary = "",
                    tableId = table.id,
                    rowId = row.id,
                )
            }
        }

        val dedupedEdges = edges.distinctBy {
            listOf(
                it.fromNodeId,
                it.toNodeId,
                it.relationType.lowercase(),
            ).joinToString("\u0000")
        }
        logD("result: nodes=${nodeMap.size} edges=${edges.size} dedupedEdges=${dedupedEdges.size}")
        dedupedEdges.forEach { edge ->
            logD("edge ${edge.fromName} -> ${edge.toName}: ${edge.relationType}")
        }
        return Graph(nodes = nodeMap.values.toList(), edges = dedupedEdges)
    }

    private fun parseNameRelationTable(
        table: AiMemoryTable,
        rows: List<AiMemoryTableRow>,
        nameCol: String,
        relCol: String,
        characterCards: List<AiCharacterCard>,
        ensureNode: (String) -> GraphNode,
        edges: MutableList<GraphEdge>,
    ) {
        val rowNames = rows.mapNotNull { row ->
            parseRowData(row.rowData)[nameCol]?.trim()?.takeIf { it.isNotBlank() }
        }
        val knownNames = buildKnownNames(rowNames, characterCards)
        logD("parse nameRelation table id=${table.id} rows=${rows.size} nameCol=$nameCol relCol=$relCol knownNames=$knownNames")
        rows.forEach { row ->
            val data = parseRowData(row.rowData)
            val name = data[nameCol]?.trim().orEmpty()
            val relation = data[relCol]?.trim().orEmpty()
            val ignoredKeys = data.keys.filter { it != nameCol && it != relCol }
            if (name.isBlank()) {
                logD("skip row id=${row.id}: blank name, dataKeys=${data.keys}")
                return@forEach
            }
            val from = ensureNode(name)
            val targets = namesMentionedIn(relation, knownNames, exclude = name)
            logD("row id=${row.id} name=$name relation=$relation targets=$targets ignoredKeys=$ignoredKeys")
            if (targets.isEmpty()) {
                logD("row id=${row.id}: node only, no edge")
            }
            targets.forEach { targetName ->
                val to = ensureNode(targetName)
                edges += GraphEdge(
                    id = "${row.id}:${to.id}",
                    fromNodeId = from.id,
                    toNodeId = to.id,
                    fromName = from.name,
                    toName = to.name,
                    relationType = relation,
                    summary = "",
                    tableId = table.id,
                    rowId = row.id,
                )
            }
        }
    }

    private fun detectNameRelationColumns(columns: List<String>): Pair<String, String>? {
        val nameCol = MemoryTableRowMatcher.resolveColumnKey(columns, "角色名") ?: return null
        val relCol = detectRelationTypeColumn(columns) ?: return null
        if (relCol == nameCol) return null
        return nameCol to relCol
    }

    private fun detectRelationTypeColumn(columns: List<String>): String? =
        columns.firstOrNull { col ->
            val n = normalizeCol(col)
            n == "关系" || n == "关系类型" || n == "relation" || n == "relationtype"
        }

    private fun buildKnownNames(
        rowNames: List<String>,
        characterCards: List<AiCharacterCard>,
    ): List<String> {
        val names = LinkedHashSet<String>()
        rowNames.forEach { name ->
            if (name.isNotBlank()) names += name.trim()
        }
        characterCards.forEach { card ->
            if (card.name.isNotBlank()) names += card.name.trim()
            parseAliasesJson(card.aliasesJson).forEach { alias ->
                if (alias.isNotBlank()) names += alias.trim()
            }
        }
        return names.sortedByDescending { it.length }
    }

    private fun namesMentionedIn(
        relation: String,
        candidates: List<String>,
        exclude: String,
    ): List<String> {
        if (relation.isBlank()) return emptyList()
        val excludeKey = exclude.trim().lowercase()
        val matched = LinkedHashSet<String>()
        candidates.forEach { candidate ->
            val trimmed = candidate.trim()
            val key = trimmed.lowercase()
            if (key.length < 2 || key == excludeKey) return@forEach
            if (relation.contains(trimmed, ignoreCase = true)) {
                matched += trimmed
            }
        }
        return matched.toList()
    }

    private fun buildCardLookup(cards: List<AiCharacterCard>): Map<String, AiCharacterCard> {
        val map = LinkedHashMap<String, AiCharacterCard>()
        cards.forEach { card ->
            map[card.name.trim().lowercase()] = card
            parseAliasesJson(card.aliasesJson).forEach { alias ->
                map.putIfAbsent(alias.trim().lowercase(), card)
            }
        }
        return map
    }

    private fun parseColumns(columnsJson: String): List<String> =
        runCatching {
            GSON.fromJson(columnsJson, Array<String>::class.java)?.toList()
        }.getOrNull().orEmpty().map { it.trim() }.filter { it.isNotBlank() }

    private fun parseRowData(rowData: String): Map<String, String> =
        GSON.fromJsonObject<Map<String, Any?>>(rowData).getOrNull()
            ?.mapValues { (_, v) -> v?.toString().orEmpty() }
            .orEmpty()

    private fun normalizeCol(name: String): String =
        name.trim().lowercase().replace(" ", "").replace("_", "")
}
