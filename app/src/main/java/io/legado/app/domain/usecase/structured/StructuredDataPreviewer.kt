package io.legado.app.domain.usecase.structured

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.entities.Book
import io.legado.app.domain.gateway.AiCharacterCardGateway
import io.legado.app.domain.gateway.AiChatGateway
import io.legado.app.domain.gateway.AiMemoryTableGateway
import io.legado.app.domain.model.CharacterCardPatch
import io.legado.app.domain.model.MemoryTableOp
import io.legado.app.domain.model.OutlinePatchMode
import io.legado.app.domain.model.UserCardPatch
import io.legado.app.domain.model.WorldBookPatch

class StructuredDataPreviewer(
    private val aiChatGateway: AiChatGateway,
    private val characterCardGateway: AiCharacterCardGateway,
    private val memoryTableGateway: AiMemoryTableGateway,
    private val bookDao: BookDao,
    private val bookChapterDao: BookChapterDao,
) {

    suspend fun previewRegenerateTable(op: MemoryTableOp.RegenerateTable): List<FieldChange> {
        val table = memoryTableGateway.getTable(op.tableId) ?: return listOf(
            FieldChange("${op.tableId}/regenerate", "", "Table not found")
        )
        val columns = runCatching {
            io.legado.app.utils.GSON.fromJson(table.columns, Array<String>::class.java).toList()
        }.getOrNull().orEmpty()
        val rowCount = memoryTableGateway.getRows(op.tableId).size
        return listOf(
            FieldChange(
                "${op.tableId}/regenerate",
                "${table.name}: $rowCount rows, columns [${columns.joinToString(", ")}]",
                "整表 AI 重建 (repair/dedupe)",
            )
        )
    }

    suspend fun previewOutlineGenerate(
        conversationId: String,
        mode: OutlinePatchMode.Generate,
        currentContent: String,
    ): List<FieldChange> {
        val messages = if (conversationId.isNotBlank()) {
            aiChatGateway.getMessagesForRegeneration(conversationId)
        } else emptyList()
        val summary = buildString {
            append("现有大纲 ${currentContent.length} 字")
            if (mode.supplement && currentContent.isNotBlank()) {
                append("；将在现有基础上补充")
            }
            append("；会话消息 ${messages.size} 条")
            messages.take(3).forEachIndexed { i, msg ->
                append("\n  [${i + 1}] ${msg.first}: ${msg.second.take(100)}")
            }
            mode.hint?.takeIf { it.isNotBlank() }?.let { append("\n  hint: $it") }
        }
        return listOf(FieldChange("outline/generate", currentContent.take(120), summary))
    }

    suspend fun previewCharacterCardGenerate(patch: CharacterCardPatch): List<FieldChange> {
        val convId = patch.conversationId.orEmpty()
        val messages = if (convId.isNotBlank()) {
            aiChatGateway.getMessagesForRegeneration(convId)
        } else emptyList()
        val existing = patch.cardId?.let { characterCardGateway.getById(it) }
        val summary = buildString {
            append("将根据会话生成角色卡（${messages.size} 条消息）")
            existing?.let {
                append("\n  现有: ${it.name} — ${it.description.take(80)}")
            }
            patch.hint?.takeIf { it.isNotBlank() }?.let { append("\n  hint: $it") }
        }
        return listOf(FieldChange("character_card/generate", existing?.name.orEmpty(), summary))
    }

    suspend fun previewUserCardGenerate(patch: UserCardPatch): List<FieldChange> {
        val convId = patch.conversationId.orEmpty()
        val messages = if (convId.isNotBlank()) {
            aiChatGateway.getMessagesForRegeneration(convId)
        } else emptyList()
        val existing = convId.takeIf { it.isNotBlank() }?.let { aiChatGateway.getConversation(it) }
        val summary = buildString {
            append("将根据会话生成用户描述（${messages.size} 条消息）")
            existing?.let {
                if (it.userName.isNotBlank() || it.userDescription.isNotBlank()) {
                    append("\n  现有: ${it.userName} — ${it.userDescription.take(80)}")
                }
            }
            patch.hint?.takeIf { it.isNotBlank() }?.let { append("\n  hint: $it") }
        }
        return listOf(FieldChange("user_card/generate", existing?.userName.orEmpty(), summary))
    }

    fun previewWorldBookGenerate(patch: WorldBookPatch): List<FieldChange> {
        val summary = buildString {
            append("将根据会话生成世界书")
            patch.name?.takeIf { it.isNotBlank() }?.let { append(" ($it)") }
            patch.hint?.takeIf { it.isNotBlank() }?.let { append("\n  hint: $it") }
        }
        return listOf(FieldChange("world_book/generate", "", summary))
    }

    fun previewExtractWorldBook(args: JsonObject, book: Book?): List<FieldChange> {
        val bookLabel = book?.let { "${it.name} / ${it.author}" } ?: "未找到书籍"
        val indices = parseChapterIndices(args.string("chapterIndices"))
        val chapterTitles = if (book != null && indices.isNotEmpty()) {
            indices.mapNotNull { idx ->
                bookChapterDao.getChapterList(book.bookUrl, idx, idx).firstOrNull()?.title
                    ?.let { title -> "#$idx $title" }
            }
        } else emptyList()
        val summary = buildString {
            append(bookLabel)
            if (indices.isNotEmpty()) {
                append("\n  章节: ${indices.joinToString(", ")}")
            }
            if (chapterTitles.isNotEmpty()) {
                append("\n  ")
                append(chapterTitles.joinToString("\n  "))
            }
            args.string("worldBookName")?.takeIf { it.isNotBlank() }?.let {
                append("\n  世界书名: $it")
            }
        }
        return listOf(FieldChange("extract_world_book", "", summary))
    }

    private fun parseChapterIndices(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val el = JsonParser.parseString(raw)
            when {
                el.isJsonArray -> el.asJsonArray.mapNotNull { runCatching { it.asInt }.getOrNull() }
                else -> raw.split(',', ' ', '[', ']')
                    .mapNotNull { it.trim().toIntOrNull() }
            }
        }.getOrElse { emptyList() }
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { !it.isJsonNull }?.asString
}
