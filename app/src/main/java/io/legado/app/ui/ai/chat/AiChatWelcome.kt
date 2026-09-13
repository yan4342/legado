package io.legado.app.ui.ai.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.usecase.ToolTraceBuilder
import java.time.ZonedDateTime
import kotlin.random.Random
import kotlinx.coroutines.delay

/** Preferred global habit keys for chat mode (reply style only; persona/用户描述 is writing-RP). */
object ChatHabitKeys {
    const val REPLY_STYLE = "reply_style"
}

private data class WelcomeChipSpec(
    val labelRes: Int,
    val fillRes: Int,
)

private val welcomeChipPool = listOf(
    WelcomeChipSpec(R.string.ai_chat_chip_find_book, R.string.ai_chat_chip_find_book_fill),
    WelcomeChipSpec(R.string.ai_chat_chip_check_source, R.string.ai_chat_chip_check_source_fill),
    WelcomeChipSpec(R.string.ai_chat_chip_catgirl, R.string.ai_chat_chip_catgirl_fill),
    WelcomeChipSpec(R.string.ai_chat_chip_search_sources, R.string.ai_chat_chip_search_sources_fill),
    WelcomeChipSpec(R.string.ai_chat_chip_bookshelf, R.string.ai_chat_chip_bookshelf_fill),
    WelcomeChipSpec(R.string.ai_chat_chip_write_source, R.string.ai_chat_chip_write_source_fill),
)

fun chatWelcomeGreetingRes(hour: Int): Int = when (hour) {
    in 5..10 -> R.string.ai_chat_welcome_morning
    in 11..13 -> R.string.ai_chat_welcome_noon
    in 14..17 -> R.string.ai_chat_welcome_afternoon
    in 18..22 -> R.string.ai_chat_welcome_evening
    else -> R.string.ai_chat_welcome_night
}

fun chatWelcomeGreetingRes(now: ZonedDateTime = ZonedDateTime.now()): Int =
    chatWelcomeGreetingRes(now.hour)

/**
 * Derive a lightweight TopBar status from the in-flight streaming message.
 * Prefers the latest tool that is still executing.
 */
fun generationStatusFromStreaming(streaming: AiChatMessageUi?): String? {
    val parts = streaming?.parts.orEmpty()
    val executing = parts.asReversed().firstOrNull { part ->
        part is AiMessagePart.Tool && (
            part.output.isBlank() ||
                part.output == ToolTraceBuilder.EXECUTING_PLACEHOLDER
            )
    } as? AiMessagePart.Tool
    return executing?.toolName
}

private val firstPacketWaitPhraseRes = listOf(
    R.string.ai_chat_wait_thinking,
    R.string.ai_chat_wait_bookshelf,
    R.string.ai_chat_wait_sources,
    R.string.ai_chat_wait_organizing,
)

fun isAwaitingFirstPacket(streaming: AiChatMessageUi?, isSending: Boolean): Boolean {
    if (!isSending || streaming == null) return false
    if (streaming.content.isNotBlank()) return false
    if (streaming.reasoning.orEmpty().isNotBlank()) return false
    if (generationStatusFromStreaming(streaming) != null) return false
    return true
}

@Composable
fun rememberFirstPacketWaitLabel(active: Boolean): String {
    val phrases = firstPacketWaitPhraseRes
    var index by remember { mutableIntStateOf(Random.nextInt(phrases.size)) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        index = Random.nextInt(phrases.size)
        while (true) {
            delay(2200)
            index = (index + 1) % phrases.size
        }
    }
    return stringResource(phrases[index])
}

@Composable
fun generationStatusLabel(
    streaming: AiChatMessageUi?,
    isSending: Boolean,
): String {
    val toolName = generationStatusFromStreaming(streaming)
    if (toolName != null) return toolStatusLabel(toolName)
    if (isAwaitingFirstPacket(streaming, isSending)) {
        return rememberFirstPacketWaitLabel(active = true)
    }
    return stringResource(R.string.ai_replying)
}

@Composable
fun toolStatusLabel(toolName: String?): String {
    if (toolName.isNullOrBlank()) return stringResource(R.string.ai_replying)
    val res = when (toolName) {
        "check_book_source" -> R.string.ai_status_check_book_source
        "debug_book_source" -> R.string.ai_status_debug_book_source
        "fetch_page_snippet" -> R.string.ai_status_fetch_page_snippet
        "patch_book_source" -> R.string.ai_status_patch_book_source
        "write_book_source_from_url" -> R.string.ai_status_write_book_source
        "search_books" -> R.string.ai_status_search_books
        "search_book_sources" -> R.string.ai_status_search_book_sources
        "add_book_to_bookshelf" -> R.string.ai_status_add_book
        "get_chapter_content" -> R.string.ai_status_get_chapter
        "search_book_content" -> R.string.ai_status_search_book_content
        "patch_user_memory" -> R.string.ai_status_patch_user_memory
        "read_user_memory" -> R.string.ai_status_read_user_memory
        "web_search" -> R.string.ai_status_web_search
        "read_web_page" -> R.string.ai_status_read_web_page
        "ask_user_questions" -> R.string.ai_status_ask_user
        "read_tts_config", "read_cloud_tts_engine", "read_http_tts" -> R.string.ai_status_read_tts_config
        "patch_cloud_tts_engine" -> R.string.ai_status_patch_cloud_tts
        "list_cloud_tts_voices" -> R.string.ai_status_list_cloud_tts_voices
        "patch_http_tts" -> R.string.ai_status_patch_http_tts
        "test_tts" -> R.string.ai_status_test_tts
        "set_default_tts_engine" -> R.string.ai_status_set_default_tts
        "export_cloud_tts_as_http_tts" -> R.string.ai_status_export_cloud_tts
        else -> R.string.ai_status_calling_tool
    }
    return stringResource(res)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatWelcomePanel(
    onChipFill: (String) -> Unit,
    conversationId: String? = null,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val greeting = stringResource(chatWelcomeGreetingRes())
    val clock = remember {
        java.time.format.DateTimeFormatter.ofPattern("HH:mm")
            .format(ZonedDateTime.now())
    }
    val bubbleText = buildString {
        append(greeting)
            append("\n\n")
            append(stringResource(R.string.ai_chat_welcome_subtitle))       
    }

    val chipSpecs = remember(conversationId) {
        val count = Random.nextInt(2, 4) // 2 or 3
        welcomeChipPool.shuffled(Random).take(count)
    }
    val chipLabels = chipSpecs.map { stringResource(it.labelRes) }
    val chipFills = chipSpecs.map { stringResource(it.fillRes) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_ai_chat),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            colorFilter = ColorFilter.tint(colorScheme.onSurfaceVariant),
        )
        Spacer(modifier = Modifier.height(16.dp))
        ChatWelcomeBubble(text = bubbleText)
        Spacer(modifier = Modifier.height(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            chipLabels.forEachIndexed { index, label ->
                val fill = chipFills[index]
                AssistChip(
                    onClick = { onChipFill(fill) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
fun ChatWelcomeBubble(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.widthIn(max = 360.dp),
        shape = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 4.dp,
            bottomEnd = 16.dp,
        ),
        color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurface,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}
