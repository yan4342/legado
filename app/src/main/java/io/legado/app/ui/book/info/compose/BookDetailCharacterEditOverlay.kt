package io.legado.app.ui.book.info.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.legado.app.domain.gateway.AiCharacterCardGateway
import io.legado.app.domain.gateway.ReadAloudCharacterGateway
import io.legado.app.domain.model.CharacterCardPatch
import io.legado.app.domain.usecase.structured.CharacterCardMutator
import io.legado.app.ui.ai.chat.AiCharacterCardUi
import io.legado.app.ui.ai.chat.CharacterEditDialog
import io.legado.app.ui.ai.chat.toUi
import io.legado.app.utils.AiIdListCodec
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * Character edit sheet for book detail cast section.
 * Dismiss does not auto-save (unlike chat / character list flows).
 */
@Composable
fun BookDetailCharacterEditOverlay(
    characterId: String?,
    bookUrl: String,
    bookName: String,
    bookAuthor: String,
    onDismiss: () -> Unit,
) {
    if (characterId.isNullOrBlank()) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val characterCardGateway: AiCharacterCardGateway = koinInject()
    val readAloudCharacterGateway: ReadAloudCharacterGateway = koinInject()
    val characterCardMutator: CharacterCardMutator = koinInject()
    var editingCharacter by remember(characterId) { mutableStateOf<AiCharacterCardUi?>(null) }

    LaunchedEffect(characterId) {
        val card = withContext(Dispatchers.IO) {
            characterCardGateway.getById(characterId)
        } ?: run {
            onDismiss()
            return@LaunchedEffect
        }
        val dramaticRole = withContext(Dispatchers.IO) {
            readAloudCharacterGateway.getDramaticRole(bookUrl, characterId)
        }
        editingCharacter = card.toUi(dramaticRole = dramaticRole)
    }

    editingCharacter?.let { draft ->
        CharacterEditDialog(
            existing = draft,
            saveOnDismiss = false,
            onDismiss = onDismiss,
            onSave = { name, desc, opening, worldBookIds, personality, scenario,
                exampleDialogues, postHistory, alternateOpenings, aliasesJson,
                voiceGender, voiceAgeBand, boundBookUrl, boundBookName, boundBookAuthor, dramaticRole, avatarPath ->
                scope.launch(Dispatchers.IO) {
                    val resolvedBookUrl = boundBookUrl.ifBlank { bookUrl }
                    val result = characterCardMutator.applyPatch(
                        CharacterCardPatch(
                            cardId = draft.id,
                            name = name,
                            description = desc,
                            openingLine = opening,
                            worldBookIds = AiIdListCodec.toCsv(worldBookIds),
                            personality = personality,
                            scenario = scenario,
                            exampleDialogues = exampleDialogues,
                            postHistoryInstructions = postHistory,
                            alternateOpenings = alternateOpenings,
                            aliasesJson = aliasesJson,
                            voiceGender = voiceGender,
                            voiceAgeBand = voiceAgeBand,
                            bookUrl = resolvedBookUrl,
                            bookName = boundBookName.ifBlank { bookName },
                            bookAuthor = boundBookAuthor.ifBlank { bookAuthor },
                            avatarPath = avatarPath,
                        ),
                        // 书详情页编辑的就是正典卡(不 fork)。
                        canonical = true,
                    )
                    if (result.success) {
                        // Cast role is scoped to the book being edited, not the card's bound bookUrl.
                        readAloudCharacterGateway.updateDramaticRole(
                            bookUrl = bookUrl,
                            characterCardId = result.cardId.ifBlank { draft.id },
                            dramaticRole = dramaticRole,
                        )
                    }
                    withContext(Dispatchers.Main) {
                        if (!result.success) {
                            context.toastOnUi(result.message)
                        } else {
                            onDismiss()
                        }
                    }
                }
            },
        )
    }
}
