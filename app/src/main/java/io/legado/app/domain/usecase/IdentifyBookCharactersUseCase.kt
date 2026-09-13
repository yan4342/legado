package io.legado.app.domain.usecase

import com.google.gson.JsonParser
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.entities.AiArtifact
import io.legado.app.data.entities.AiCharacterCard
import io.legado.app.data.entities.Book
import io.legado.app.domain.gateway.AiArtifactGateway
import io.legado.app.domain.gateway.AiCharacterCardGateway
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.ReadAloudCharacterGateway
import io.legado.app.domain.model.AiCallMeta
import io.legado.app.domain.model.AiCallSource
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiTaskType
import io.legado.app.help.book.ContentProcessor
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.fromJsonArray
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.util.UUID

class IdentifyBookCharactersUseCase(
    private val aiProfileGateway: AiProfileGateway,
    private val aiTextGateway: AiTextGateway,
    private val aiArtifactGateway: AiArtifactGateway,
    private val aiCharacterCardGateway: AiCharacterCardGateway,
    private val readAloudCharacterGateway: ReadAloudCharacterGateway,
    private val bookDao: BookDao,
    private val bookChapterDao: BookChapterDao,
    private val resolveBookChapterContentUseCase: ResolveBookChapterContentUseCase,
) {
    data class Candidate(
        val name: String,
        val aliases: List<String>,
        val voiceGender: String,
        val voiceAgeBand: String,
        val role: String,
        val personality: String,
        val summary: String,
        val evidence: String,
        val confidence: Float,
    )

    sealed interface Progress {
        data class Reasoning(val text: String) : Progress
        data class ToolCall(val name: String) : Progress
        data class Done(val candidates: List<Candidate>) : Progress
    }

    suspend fun identify(bookUrl: String): List<Candidate> {
        var candidates = emptyList<Candidate>()
        identifyStream(bookUrl).collect { progress ->
            if (progress is Progress.Done) candidates = progress.candidates
        }
        return candidates
    }

    suspend fun loadLatest(bookUrl: String): List<Candidate> {
        val artifact =
            aiArtifactGateway.observeBookArtifacts(bookUrl, AiTaskType.IDENTIFY_CHARACTERS)
                .first()
                .firstOrNull { it.status == AiArtifact.STATUS_SUCCESS && !it.output.isNullOrBlank() }
                ?: return emptyList()
        return decodeCandidates(artifact.output)
    }

    fun decodeCandidates(output: String?): List<Candidate> =
        output?.let { GSON.fromJsonArray<Candidate>(it).getOrNull() }.orEmpty()

    fun identifyStream(bookUrl: String): Flow<Progress> = flow {
        val book = bookDao.getBook(bookUrl) ?: error("Book not found")
        val chapterTexts = loadCachedChapterTexts(book)
        if (chapterTexts.isEmpty()) {
            error("No cached chapter content available for character identification")
        }

        val identifyPreset = aiProfileGateway.getTaskPreset(AiTaskType.IDENTIFY_CHARACTERS)
        val preset = identifyPreset
            ?: aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
            ?: error("No AI model configured for character identification")
        val prompt = identifyPreset?.promptTemplate?.takeIf(String::isNotBlank) ?: DEFAULT_PROMPT
        val contentHash = buildContentHash(bookUrl, chapterTexts)
        val promptHash = MD5Utils.md5Encode(prompt + PROMPT_VERSION)

        aiArtifactGateway.getCachedArtifact(
            bookUrl = bookUrl,
            chapterIndex = null,
            taskType = AiTaskType.IDENTIFY_CHARACTERS,
            contentHash = contentHash,
            promptHash = promptHash,
            modelProfileId = preset.model.id,
        )?.takeIf { it.status == AiArtifact.STATUS_SUCCESS && !it.output.isNullOrBlank() }
            ?.let { cached ->
                emit(Progress.Done(decodeCandidates(cached.output)))
                return@flow
            }

        val existingCards = readAloudCharacterGateway.getCharacterCards(bookUrl)
        val existingNames = existingCards.flatMap { card ->
            listOf(card.name) + parseAliases(card.aliasesJson)
        }.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val existingHint = if (existingNames.isEmpty()) {
            ""
        } else {
            "\n\nExisting characters (do not duplicate): ${existingNames.joinToString(", ")}"
        }
        val userMessage = buildString {
            append("Identify stable fictional characters from these downloaded chapter excerpts.")
            append(existingHint)
            append("\n\n")
            append(chapterTexts.joinToString("\n\n"))
        }

        val response = StringBuilder()
        aiTextGateway.generateStream(
            AiGenerateRequest(
                model = preset.model,
                messages = listOf(
                    AiMessage(AiMessageRole.SYSTEM, prompt),
                    AiMessage(AiMessageRole.USER, userMessage),
                ),
                params = preset.params.copy(temperature = 0f),
                callMeta = AiCallMeta(AiCallSource.CHARACTER),
            )
        ).collect { event ->
            when (event) {
                is AiStreamEvent.Content -> response.append(event.text)
                is AiStreamEvent.Reasoning -> emit(Progress.Reasoning(event.text))
                else -> Unit
            }
        }

        val candidates = parseCandidates(response.toString())
        val now = System.currentTimeMillis()
        aiArtifactGateway.upsertArtifact(
            AiArtifact(
                id = "${contentHash}_${AiTaskType.IDENTIFY_CHARACTERS}_${promptHash}_${preset.model.id}",
                taskType = AiTaskType.IDENTIFY_CHARACTERS,
                bookUrl = bookUrl,
                contentHash = contentHash,
                promptHash = promptHash,
                modelProfileId = preset.model.id,
                status = AiArtifact.STATUS_SUCCESS,
                output = GSON.toJson(candidates),
                createdAt = now,
                updatedAt = now,
            )
        )
        emit(Progress.Done(candidates))
    }

    suspend fun save(bookUrl: String, candidates: List<Candidate>) {
        val book = bookDao.getBook(bookUrl)
        candidates.forEach { candidate ->
            val existing = findExistingCard(candidate.name)
            val mergedAliases = mergeAliases(existing, candidate.aliases, candidate.name)
            val cardId = existing?.id ?: newCardId()
            val now = System.currentTimeMillis()
            val card = AiCharacterCard(
                id = cardId,
                name = candidate.name,
                description = candidate.summary.ifBlank { existing?.description.orEmpty() },
                openingLine = existing?.openingLine.orEmpty(),
                worldBookIds = existing?.worldBookIds.orEmpty(),
                personality = candidate.personality.ifBlank { existing?.personality.orEmpty() },
                scenario = existing?.scenario.orEmpty(),
                exampleDialogues = existing?.exampleDialogues.orEmpty(),
                postHistoryInstructions = existing?.postHistoryInstructions.orEmpty(),
                alternateOpenings = existing?.alternateOpenings ?: "[]",
                aliasesJson = GSON.toJson(mergedAliases),
                voiceGender = candidate.voiceGender.ifBlank {
                    existing?.voiceGender ?: AiCharacterCard.VOICE_GENDER_UNKNOWN
                },
                voiceAgeBand = candidate.voiceAgeBand.ifBlank {
                    existing?.voiceAgeBand ?: AiCharacterCard.VOICE_AGE_UNKNOWN
                },
                bookUrl = bookUrl,
                bookName = book?.name.orEmpty().ifBlank { existing?.bookName.orEmpty() },
                bookAuthor = book?.author.orEmpty().ifBlank { existing?.bookAuthor.orEmpty() },
                // AI 识别自本书正文 → 书级正典层。
                canonical = true,
                avatarPath = existing?.avatarPath.orEmpty(),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
            aiCharacterCardGateway.upsert(card)
            readAloudCharacterGateway.addToCast(bookUrl, card.id)
            if (candidate.role.isNotBlank()) {
                readAloudCharacterGateway.updateDramaticRole(bookUrl, card.id, candidate.role)
            }
        }
    }

    private suspend fun loadCachedChapterTexts(book: Book): List<String> {
        val chapters = bookChapterDao.getChapterList(book.bookUrl)
        return chapters.mapNotNull { chapter ->
            val rawContent = when (
                val result = resolveBookChapterContentUseCase.resolveRaw(
                    book = book,
                    chapter = chapter,
                    allowNetworkFetch = false,
                )
            ) {
                is ResolveBookChapterContentUseCase.Result.Success -> result.content
                is ResolveBookChapterContentUseCase.Result.Error -> return@mapNotNull null
            }
            val processed = ContentProcessor.get(book.name, book.origin)
                .getContent(book, chapter, rawContent, includeTitle = true)
                .toString()
                .take(MAX_CHARS_PER_CHAPTER)
            "--- Chapter ${chapter.index + 1}: ${chapter.title} ---\n$processed"
        }
    }

    private fun parseCandidates(response: String): List<Candidate> {
        val jsonStart = response.indexOf('{')
        val jsonEnd = response.lastIndexOf('}')
        require(jsonStart >= 0 && jsonEnd > jsonStart) {
            "Character identification did not return a JSON object"
        }
        val root = JsonParser.parseString(response.substring(jsonStart, jsonEnd + 1)).asJsonObject
        return root.getAsJsonArray("characters").map { element ->
            val item = element.asJsonObject
            Candidate(
                name = item.get("name")?.asString?.trim().orEmpty(),
                aliases = item.getAsJsonArray("aliases")?.map { it.asString.trim() }.orEmpty(),
                voiceGender = item.get("voiceGender")?.asString ?: AiCharacterCard.VOICE_GENDER_UNKNOWN,
                voiceAgeBand = item.get("voiceAgeBand")?.asString ?: AiCharacterCard.VOICE_AGE_UNKNOWN,
                role = item.get("role")?.asString.orEmpty(),
                personality = item.get("personality")?.asString.orEmpty(),
                summary = item.get("summary")?.asString.orEmpty(),
                evidence = item.get("evidence")?.asString.orEmpty(),
                confidence = item.get("confidence")?.asFloat?.coerceIn(0f, 1f) ?: 0f,
            )
        }.filter { it.name.isNotBlank() && it.confidence >= MIN_CONFIDENCE }
            .distinctBy { it.name.lowercase() }
    }

    private suspend fun findExistingCard(name: String): AiCharacterCard? {
        val normalized = name.trim()
        if (normalized.isBlank()) return null
        return aiCharacterCardGateway.observeAll().first().firstOrNull { card ->
            card.name.equals(normalized, ignoreCase = true) ||
                parseAliases(card.aliasesJson).any { it.equals(normalized, ignoreCase = true) }
        }
    }

    private fun mergeAliases(
        existing: AiCharacterCard?,
        incoming: List<String>,
        primaryName: String,
    ): List<String> = (
        parseAliases(existing?.aliasesJson.orEmpty()) +
            incoming +
            listOfNotNull(existing?.name?.takeIf { !it.equals(primaryName, ignoreCase = true) })
        )
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase() }

    private fun parseAliases(aliasesJson: String): List<String> =
        GSON.fromJsonArray<String>(aliasesJson).getOrNull().orEmpty()
            .map(String::trim)
            .filter(String::isNotBlank)

    private fun buildContentHash(bookUrl: String, chapterTexts: List<String>): String =
        MD5Utils.md5Encode(bookUrl + chapterTexts.joinToString("\n"))

    private fun newCardId(): String =
        "charcard_${UUID.randomUUID().toString().replace("-", "")}"

    companion object {
        private const val MIN_CONFIDENCE = 0.65f
        private const val MAX_CHARS_PER_CHAPTER = 6000
        private const val PROMPT_VERSION = "identify_characters_v1"
        const val DEFAULT_PROMPT =
            """You identify stable fictional characters from downloaded local chapter excerpts provided in the user message. Return JSON only: {\"characters\":[{\"name\":string,\"aliases\":[string],\"voiceGender\":\"male|female|unknown\",\"voiceAgeBand\":\"child|teen|young_adult|adult|elderly|unknown\",\"role\":\"male_lead|female_lead|male_supporting|female_supporting|\",\"personality\":string,\"summary\":string,\"evidence\":string,\"confidence\":number}]}. Do not include pronouns, generic titles, or one-off passers-by. Use unknown instead of guessing age or gender. Do not create duplicates of existing names or aliases."""
    }
}
