package io.legado.app.domain.usecase.structured.graph

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Parses AI/tool JSON into [OutlineGraphOp] list.
 *
 * Canonical field names (taught to the model via tool description + prompt examples):
 * - add_node{parent,node|compact: type/title/bullets/index}
 * - update_node{id,title/bullets/selected}
 * - delete_node{id}
 * - move_node{id,parent,index}
 * - select_option{optionId}
 * - set_current_node{nodeId} (blank/null clears the pointer; missing rejects the op)
 * - set_anchors{premise,current,next,in_progress,outline_kind,awaiting_choice}
 *
 * The parser is intentionally lenient: it also accepts common aliases the model may emit
 * (parentId, nested patch:{…}, camelCase, abbreviated node types) and normalizes to canonical.
 * Rejected ops are NOT silently dropped — they are collected in [ParseResult.errors] so callers
 * can surface a clear error instead of applying a partial op list.
 */
object OutlineGraphOpParser {

    /** One parsed op, or the reason it was rejected. */
    data class OpResult(val op: OutlineGraphOp? = null, val error: String? = null)

    /** Collected ops plus per-op rejection reasons — invalid ops are never silently dropped. */
    data class ParseResult(val ops: List<OutlineGraphOp>, val errors: List<String>) {
        val isValid: Boolean get() = errors.isEmpty()
    }

    fun parseOps(opsArray: JsonArray): ParseResult {
        val ops = mutableListOf<OutlineGraphOp>()
        val errors = mutableListOf<String>()
        opsArray.forEachIndexed { index, el ->
            if (!el.isJsonObject) {
                errors.add("ops[$index]: not an object")
                return@forEachIndexed
            }
            val r = parseOp(el.asJsonObject)
            if (r.op != null) ops.add(r.op) else errors.add("ops[$index]: ${r.error ?: "invalid op"}")
        }
        return ParseResult(ops, errors)
    }

    fun parseOp(obj: JsonObject): OpResult {
        val op = obj.get("op")?.asString?.trim()?.lowercase()
        if (op.isNullOrBlank()) return OpResult(error = "missing op")
        return when (op) {
            "add_node" -> {
                val parent = obj.string("parent") ?: obj.string("parentId")
                if (parent == null) return OpResult(error = "add_node: parent missing")
                val nodeObj = obj.getAsJsonObject("node")
                val node = when {
                    nodeObj != null -> parseNode(nodeObj)
                    // Compact form: {"op":"add_node","parent":"vol_1","type":"CHAPTER","title":"…"}
                    else -> parseNode(obj)
                }
                if (node == null) return OpResult(error = "add_node: invalid node (need type/title)")
                OpResult(
                    OutlineGraphOp.AddNode(
                        parentId = parent,
                        node = node,
                        index = obj.get("index")?.takeIf { it.isJsonPrimitive }?.asInt,
                    ),
                )
            }
            "update_node" -> {
                val id = obj.string("id") ?: return OpResult(error = "update_node: id missing")
                val patch = obj.getAsJsonObject("patch")
                OpResult(
                    OutlineGraphOp.UpdateNode(
                        id = id,
                        title = patch?.string("title") ?: obj.string("title"),
                        bullets = patch?.stringList("bullets") ?: obj.stringList("bullets"),
                        selected = patch?.boolOrNull("selected") ?: obj.boolOrNull("selected"),
                    ),
                )
            }
            "delete_node" -> obj.string("id")?.let { OpResult(OutlineGraphOp.DeleteNode(it)) }
                ?: OpResult(error = "delete_node: id missing")
            "move_node" -> {
                val id = obj.string("id") ?: return OpResult(error = "move_node: id missing")
                val parent = obj.string("parent") ?: obj.string("newParent") ?: obj.string("newParentId")
                if (parent == null) return OpResult(error = "move_node: parent missing")
                OpResult(
                    OutlineGraphOp.MoveNode(
                        id = id,
                        newParentId = parent,
                        index = obj.get("index")?.takeIf { it.isJsonPrimitive }?.asInt,
                    ),
                )
            }
            "select_option" -> obj.string("optionId")?.let { OpResult(OutlineGraphOp.SelectOption(it)) }
                ?: obj.string("id")?.let { OpResult(OutlineGraphOp.SelectOption(it)) }
                ?: OpResult(error = "select_option: optionId missing")
            "set_current_node" -> {
                val el = obj.get("nodeId") ?: obj.get("id")
                when {
                    el == null -> OpResult(error = "set_current_node: nodeId missing")
                    el.isJsonNull -> OpResult(OutlineGraphOp.SetCurrentNode(null)) // blank clears the pointer
                    el.isJsonPrimitive -> OpResult(OutlineGraphOp.SetCurrentNode(el.asString.ifBlank { null }))
                    else -> OpResult(error = "set_current_node: nodeId must be a string")
                }
            }
            "set_anchors" -> {
                val patch = obj.getAsJsonObject("patch") ?: obj
                OpResult(
                    OutlineGraphOp.SetAnchors(
                        premise = patch.string("premise"),
                        currentProgress = patch.string("current") ?: patch.string("currentProgress"),
                        nextGoal = patch.string("next") ?: patch.string("nextGoal"),
                        inProgress = patch.boolOrNull("in_progress") ?: patch.boolOrNull("inProgress"),
                        outlineKind = patch.string("outline_kind") ?: patch.string("outlineKind"),
                        awaitingChoice = patch.boolOrNull("awaiting_choice")
                            ?: patch.boolOrNull("awaitingChoice"),
                    ),
                )
            }
            else -> OpResult(error = "unknown op '$op'")
        }
    }

    fun parseNode(obj: JsonObject): OutlineGraphNode? {
        // id optional — Engine assigns a unique id when blank/duplicate
        val id = obj.string("id").orEmpty()
        val typeStr = obj.string("type")?.uppercase() ?: "CHAPTER"
        val type = runCatching { OutlineNodeType.valueOf(typeStr) }.getOrElse {
            when (typeStr.lowercase()) {
                "volume", "vol" -> OutlineNodeType.VOLUME
                "chapter", "ch" -> OutlineNodeType.CHAPTER
                "section", "sec" -> OutlineNodeType.SECTION
                "branch" -> OutlineNodeType.BRANCH
                "option", "opt" -> OutlineNodeType.OPTION
                else -> OutlineNodeType.CHAPTER
            }
        }
        val title = obj.string("title").orEmpty()
        if (id.isBlank() && title.isBlank() && type != OutlineNodeType.BRANCH) {
            return null
        }
        return OutlineGraphNode(
            id = id,
            type = type,
            title = title.ifBlank {
                when (type) {
                    OutlineNodeType.BRANCH -> "分支：路口"
                    OutlineNodeType.OPTION -> "选项"
                    OutlineNodeType.VOLUME -> "新卷"
                    OutlineNodeType.CHAPTER -> "新章"
                    OutlineNodeType.SECTION -> "新节"
                    OutlineNodeType.ROOT -> "root"
                }
            },
            bullets = obj.stringList("bullets").orEmpty(),
            selected = obj.boolOrNull("selected"),
            children = obj.stringList("children").orEmpty(),
        )
    }

    fun parseOpsFromRawJson(raw: String): ParseResult {
        val cleaned = raw.trim()
            .removeSurrounding("```json", "```")
            .removeSurrounding("```", "```")
            .trim()
        val parsed = runCatching { JsonParser.parseString(cleaned) }.getOrNull()
        return when {
            parsed == null -> ParseResult(emptyList(), listOf("not valid JSON"))
            parsed.isJsonArray -> parseOps(parsed.asJsonArray)
            parsed.isJsonObject -> {
                val obj = parsed.asJsonObject
                val ops = obj.getAsJsonArray("ops") ?: obj.getAsJsonArray("operations")
                if (ops != null) {
                    parseOps(ops)
                } else {
                    val r = parseOp(obj)
                    ParseResult(
                        ops = listOfNotNull(r.op),
                        errors = r.error?.let { listOf(it) } ?: emptyList(),
                    )
                }
            }
            else -> ParseResult(emptyList(), listOf("unsupported JSON shape"))
        }
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    private fun JsonObject.boolOrNull(key: String): Boolean? {
        val el = get(key) ?: return null
        return when {
            el.isJsonPrimitive && el.asJsonPrimitive.isBoolean -> el.asBoolean
            el.isJsonPrimitive && el.asJsonPrimitive.isString ->
                el.asString.toBooleanStrictOrNull()
            else -> null
        }
    }

    private fun JsonObject.stringList(key: String): List<String>? {
        val el = get(key) ?: return null
        return when {
            el.isJsonArray -> el.asJsonArray.mapNotNull {
                if (it.isJsonPrimitive) it.asString else null
            }
            else -> null
        }
    }
}
