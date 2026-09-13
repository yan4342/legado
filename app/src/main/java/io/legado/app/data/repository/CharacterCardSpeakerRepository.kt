package io.legado.app.data.repository

import io.legado.app.data.dao.AiCharacterCardDao
import io.legado.app.data.dao.BookCharacterCastDao
import io.legado.app.data.entities.AiCharacterCard
import io.legado.app.data.entities.BookCharacterCast
import io.legado.app.domain.gateway.ReadAloudCharacterGateway
import io.legado.app.domain.model.DramaticRole
import io.legado.app.domain.model.readaloud.CharacterPerformanceProfile
import io.legado.app.domain.model.readaloud.SpeakerCharacter
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CharacterCardSpeakerRepository(
    private val castDao: BookCharacterCastDao,
    private val characterCardDao: AiCharacterCardDao,
) : ReadAloudCharacterGateway {

    override fun observeSpeakerCharacters(bookUrl: String): Flow<List<SpeakerCharacter>> =
        combine(
            castDao.observeCast(bookUrl),
            observeCharacterCards(bookUrl),
        ) { castRows, cards ->
            val roles = castRows.associate { it.characterCardId to it.dramaticRole }
            cards.map { it.toSpeakerCharacter(roles[it.id].orEmpty()) }
        }

    override fun observeCharacterCards(bookUrl: String): Flow<List<AiCharacterCard>> =
        combine(
            castDao.observeCast(bookUrl),
            castDao.observeCharactersForBook(bookUrl),
            characterCardDao.observeByBookUrl(bookUrl),
        ) { castRows, castCards, sourceCards ->
            sortCastCards(mergeCards(castCards, sourceCards), castRows)
        }

    override suspend fun getSpeakerCharacters(bookUrl: String): List<SpeakerCharacter> =
        withContext(Dispatchers.IO) {
            val roles = getCastRoles(bookUrl)
            getCharacterCards(bookUrl).map { card ->
                card.toSpeakerCharacter(roles[card.id].orEmpty())
            }
        }

    override suspend fun getCharacterCards(bookUrl: String): List<AiCharacterCard> =
        withContext(Dispatchers.IO) {
            sortCastCards(
                mergeCards(
                    castDao.getCharactersForBook(bookUrl),
                    characterCardDao.getByBookUrl(bookUrl),
                ),
                castDao.getCast(bookUrl),
            )
        }

    override suspend fun getPerformanceProfiles(bookUrl: String): List<CharacterPerformanceProfile> =
        withContext(Dispatchers.IO) {
            val roles = getCastRoles(bookUrl)
            getCharacterCards(bookUrl).map { card ->
                card.toPerformanceProfile(roles[card.id].orEmpty())
            }
        }

    override suspend fun getCastRoles(bookUrl: String): Map<String, String> =
        withContext(Dispatchers.IO) {
            castDao.getCast(bookUrl).associate { it.characterCardId to it.dramaticRole }
        }

    override suspend fun getDramaticRole(bookUrl: String, characterCardId: String): String =
        withContext(Dispatchers.IO) {
            castDao.getDramaticRole(bookUrl, characterCardId).orEmpty()
        }

    override suspend fun updateDramaticRole(
        bookUrl: String,
        characterCardId: String,
        dramaticRole: String,
    ) = withContext(Dispatchers.IO) {
        if (bookUrl.isBlank() || characterCardId.isBlank()) return@withContext
        val normalized = DramaticRole.normalize(dramaticRole)
        val existing = castDao.getCast(bookUrl)
        val row = existing.firstOrNull { it.characterCardId == characterCardId }
        if (row == null) {
            castDao.insert(
                BookCharacterCast(
                    bookUrl = bookUrl,
                    characterCardId = characterCardId,
                    sortOrder = existing.size,
                    dramaticRole = normalized,
                )
            )
        } else {
            castDao.updateDramaticRole(bookUrl, characterCardId, normalized)
        }
    }

    override suspend fun setCast(bookUrl: String, characterCardIds: List<String>) =
        withContext(Dispatchers.IO) {
            castDao.setCast(bookUrl, characterCardIds.distinct())
        }

    override suspend fun addToCast(bookUrl: String, characterCardId: String) =
        withContext(Dispatchers.IO) {
            val existing = castDao.getCast(bookUrl)
            if (existing.any { it.characterCardId == characterCardId }) return@withContext
            castDao.insert(
                BookCharacterCast(
                    bookUrl = bookUrl,
                    characterCardId = characterCardId,
                    sortOrder = existing.size,
                    dramaticRole = "",
                )
            )
        }

    override suspend fun removeFromCast(bookUrl: String, characterCardId: String) =
        withContext(Dispatchers.IO) {
            castDao.delete(bookUrl, characterCardId)
        }

    private fun mergeCards(
        castCards: List<AiCharacterCard>,
        sourceCards: List<AiCharacterCard>,
    ): List<AiCharacterCard> {
        val byId = LinkedHashMap<String, AiCharacterCard>()
        castCards.forEach { byId[it.id] = it }
        sourceCards.forEach { byId.putIfAbsent(it.id, it) }
        return byId.values.toList()
    }

    private fun sortCastCards(
        cards: List<AiCharacterCard>,
        castRows: List<BookCharacterCast>,
    ): List<AiCharacterCard> {
        if (cards.isEmpty()) return cards
        val castByCardId = castRows.associateBy { it.characterCardId }
        return cards.sortedWith(
            compareBy<AiCharacterCard> { card ->
                DramaticRole.sortKey(castByCardId[card.id]?.dramaticRole.orEmpty())
            }.thenBy { card ->
                castByCardId[card.id]?.sortOrder ?: Int.MAX_VALUE
            }.thenBy { it.name.lowercase() },
        )
    }
}

fun AiCharacterCard.toSpeakerCharacter(dramaticRole: String = ""): SpeakerCharacter = SpeakerCharacter(
    id = id,
    name = name,
    aliases = parseAliasesJson(aliasesJson),
    role = dramaticRole,
    voiceGender = voiceGender,
    voiceAgeBand = voiceAgeBand,
    updatedAt = updatedAt,
)

fun AiCharacterCard.toPerformanceProfile(dramaticRole: String = ""): CharacterPerformanceProfile =
    CharacterPerformanceProfile(
        characterId = id,
        role = dramaticRole,
        voiceGender = voiceGender,
        voiceAgeBand = voiceAgeBand,
        personality = personality.ifBlank { description },
        updatedAt = updatedAt,
    )

fun parseAliasesJson(aliasesJson: String): List<String> =
    GSON.fromJsonArray<String>(aliasesJson).getOrNull().orEmpty()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

/** Compact map for AI tool read/list responses. */
fun AiCharacterCard.toCharacterCardToolMap(
    dramaticRole: String = "",
    includePerformanceFields: Boolean = true,
): Map<String, Any?> {
    val base = mutableMapOf<String, Any?>(
        "cardId" to id,
        "name" to name,
        "description" to description,
        "openingLine" to openingLine,
        "worldBookIds" to worldBookIds,
        "personality" to personality,
        "scenario" to scenario,
        "exampleDialogues" to exampleDialogues,
        "postHistoryInstructions" to postHistoryInstructions,
        "alternateOpenings" to alternateOpenings,
        "bookUrl" to bookUrl,
        "bookName" to bookName,
        "bookAuthor" to bookAuthor,
    )
    if (!includePerformanceFields) return base
    base["aliases"] = parseAliasesJson(aliasesJson)
    base["aliasesJson"] = aliasesJson
    base["voiceGender"] = voiceGender
    base["voiceAgeBand"] = voiceAgeBand
    if (dramaticRole.isNotBlank()) {
        base["dramaticRole"] = dramaticRole
    }
    return base
}
