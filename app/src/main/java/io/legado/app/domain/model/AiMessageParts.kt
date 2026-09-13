package io.legado.app.domain.model

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Keep
@Serializable
sealed interface AiMessagePart {

    @Keep
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : AiMessagePart

    @Keep
    @Serializable
    @SerialName("reasoning")
    data class Reasoning(val text: String) : AiMessagePart

    /**
     * Unified tool part — represents the full lifecycle of a tool call.
     * When the model requests a tool, [input] is filled and [output] is empty.
     * After execution, [output] is filled and [approvalState] reflects the outcome.
     */
    @Keep
    @Serializable
    @SerialName("tool")
    data class Tool(
        val toolCallId: String,
        val toolName: String,
        val input: String,
        val output: String = "",
        val approvalState: AiToolApprovalState = AiToolApprovalState.AUTO,
        val rawType: String = "tool_call",
        val metadata: String? = null
    ) : AiMessagePart

    @Keep
    @Serializable
    @SerialName("book_result")
    data class BookResult(
        val bookUrl: String,
        val name: String,
        val author: String,
        val origin: String? = null,
        val coverPath: String? = null,
        val latestChapterTitle: String? = null,
        val currentChapterTitle: String? = null,
        val intro: String? = null
    ) : AiMessagePart

    /**
     * Snapshot of a non-tool side effect (e.g. post-edit polish) for activity-log detail.
     * Not rendered in the chat bubble.
     */
    @Keep
    @Serializable
    @SerialName("side_effect")
    data class SideEffect(
        val source: String,
        val input: String = "",
        val output: String = "",
        val modelName: String = "",
        val totalTokens: Int = 0,
        val generatedChars: Int = 0,
        val success: Boolean = true,
    ) : AiMessagePart

    /**
     * Snapshot of prompt blocks assembled for the round that produced this assistant message.
     * Stored for execution-history UI; ignored when rebuilding model request messages.
     */
    @Keep
    @Serializable
    @SerialName("prompt_injection")
    data class PromptInjection(
        val blocks: List<PromptInjectionBlock> = emptyList(),
        val totalEstimatedTokens: Int = 0,
        val inChatCount: Int = 0,
    ) : AiMessagePart

    /**
     * User-uploaded file attachment (image, PDF, or text extracted from txt/epub).
     * Binary stays on disk; [extractedTextPath] holds plain text for TEXT_EXTRACT kind.
     */
    @Keep
    @Serializable
    @SerialName("attachment")
    data class Attachment(
        val localPath: String,
        val mimeType: String,
        val displayName: String,
        val kind: String,
        val sizeBytes: Long = 0,
        val extractedTextPath: String? = null,
        val truncated: Boolean = false,
    ) : AiMessagePart

    /**
     * AI-generated / downloaded image (PNG, JPEG, WebP, GIF, or SVG).
     * [localPath] points to a file in app-private storage.
     */
    @Keep
    @Serializable
    @SerialName("image")
    data class Image(
        val localPath: String,
        val mimeType: String,
        val remoteUrl: String? = null,
        val width: Int? = null,
        val height: Int? = null,
    ) : AiMessagePart

    /**
     * Lightweight reference to an AI-generated HTML app (game / visualization).
     * The full HTML lives in a file (`files/html_apps/{appId}.html`), metadata in
     * the `ai_html_apps` table — this part only carries the id so message partsJson
     * stays tiny even for 200KB+ HTML sources.
     */
    @Keep
    @Serializable
    @SerialName("html_app_ref")
    data class HtmlAppRef(
        val appId: String,
    ) : AiMessagePart

    // ---- Legacy parts ----

    @Deprecated("Use Tool instead — tool call and result are now a single part")
    @Keep
    @Serializable
    @SerialName("tool_call")
    data class ToolCall(
        val id: String,
        val name: String,
        val arguments: String,
        val rawType: String = "tool_call",
        val approvalState: AiToolApprovalState = AiToolApprovalState.AUTO
    ) : AiMessagePart

    @Deprecated("Use Tool instead — tool call and result are now a single part")
    @Keep
    @Serializable
    @SerialName("tool_result")
    data class ToolResult(
        val callId: String,
        val name: String,
        val content: String
    ) : AiMessagePart
}

@Keep
@Serializable
data class PromptInjectionBlock(
    val blockId: String,
    val estimatedTokens: Int = 0,
    val truncated: Boolean = false,
    val position: String = "prefix",
    /** Short preview for list rows. */
    val preview: String = "",
    /** Full injected text for detail dialog; empty on older snapshots. */
    val content: String = "",
)

@Keep
@Serializable
enum class AiToolApprovalState {
    @SerialName("auto")
    AUTO,

    @SerialName("pending")
    PENDING,

    @SerialName("approved")
    APPROVED,

    @SerialName("denied")
    DENIED,

    @SerialName("answered")
    ANSWERED;

    fun canResumeExecution(): Boolean =
        this == APPROVED || this == DENIED || this == ANSWERED
}

object AiMessagePartJson {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun encode(parts: List<AiMessagePart>): String {
        return json.encodeToString(parts)
    }

    fun decode(partsJson: String): List<AiMessagePart> {
        if (partsJson.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<AiMessagePart>>(partsJson) }
            .getOrElse { emptyList() }
            .map { it.migrateLegacy() }
    }

    /**
     * Migrate legacy ToolCall/ToolResult pairs into unified Tool parts.
     * Handles old messages stored before the unified Tool part was introduced.
     */
    private fun AiMessagePart.migrateLegacy(): AiMessagePart {
        return when (this) {
            is AiMessagePart.ToolCall -> AiMessagePart.Tool(
                toolCallId = id,
                toolName = name,
                input = arguments,
                rawType = rawType,
                approvalState = approvalState
            )
            is AiMessagePart.ToolResult -> AiMessagePart.Tool(
                toolCallId = callId,
                toolName = name,
                input = "",
                output = content,
                approvalState = AiToolApprovalState.AUTO
            )
            else -> this
        }
    }
}

// ---- Extension functions ----

fun List<AiMessagePart>.textContent(): String {
    // Join without separators so concatenated segments match the streamed fullText
    // (ContentSpan offsets). Spurious "\n\n" made saved vs streaming content differ and
    // briefly showed two assistant bubbles after tool rounds.
    return filterIsInstance<AiMessagePart.Text>()
        .joinToString("") { it.text }
        .trim()
}

fun List<AiMessagePart>.reasoningContent(): String? {
    return filterIsInstance<AiMessagePart.Reasoning>()
        .joinToString("\n\n") { it.text }
        .trim()
        .takeIf { it.isNotBlank() }
}

/** Returns all unified Tool parts (includes migrated legacy parts). */
fun List<AiMessagePart>.toolParts(): List<AiMessagePart.Tool> {
    return filterIsInstance<AiMessagePart.Tool>()
}

fun List<AiMessagePart>.promptInjection(): AiMessagePart.PromptInjection? =
    filterIsInstance<AiMessagePart.PromptInjection>().lastOrNull()

fun List<AiMessagePart>.sideEffectParts(): List<AiMessagePart.SideEffect> =
    filterIsInstance<AiMessagePart.SideEffect>()

fun List<AiMessagePart>.attachmentParts(): List<AiMessagePart.Attachment> =
    filterIsInstance<AiMessagePart.Attachment>()

fun List<AiMessagePart>.htmlAppRefParts(): List<AiMessagePart.HtmlAppRef> =
    filterIsInstance<AiMessagePart.HtmlAppRef>()

/** Build tool trace text from Tool parts. */
fun List<AiMessagePart>.toolTraceText(): String? {
    val tools = toolParts()
    if (tools.isEmpty()) return null
    return tools.joinToString("\n\n") { tool ->
        buildString {
            append("Tool: ")
            append(tool.toolName)
            append(" (")
            append(tool.toolCallId)
            append(')')
            if (tool.input.isNotBlank()) {
                append('\n')
                append("Args: ")
                append(tool.input.take(500))
            }
            if (tool.output.isNotBlank()) {
                append('\n')
                append("Result: ")
                append(tool.output.take(2000))
            }
        }
    }
}

/** Returns tool calls that are pending approval (output is empty, state is PENDING). */
fun List<AiMessagePart>.pendingToolCalls(): List<AiMessagePart.Tool> {
    return toolParts().filter {
        it.output.isBlank() && it.approvalState == AiToolApprovalState.PENDING
    }
}

/** Returns tool calls that need execution (output is empty, approval allows execution). */
fun List<AiMessagePart>.executableToolCalls(): List<AiMessagePart.Tool> {
    return toolParts().filter {
        it.output.isBlank() && it.approvalState.canResumeExecution()
    }
}
