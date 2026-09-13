package io.legado.app.data.repository

import io.legado.app.data.dao.AiCharacterCardDao
import io.legado.app.data.entities.AiCharacterCard
import io.legado.app.domain.gateway.AiCharacterCardGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class AiCharacterCardRepository(
    private val dao: AiCharacterCardDao
) : AiCharacterCardGateway {

    override fun observeAll(): Flow<List<AiCharacterCard>> = dao.observeAll()

    override fun observeByBookUrl(bookUrl: String): Flow<List<AiCharacterCard>> =
        dao.observeByBookUrl(bookUrl)

    override suspend fun getAll(): List<AiCharacterCard> = withContext(Dispatchers.IO) {
        dao.all
    }

    override suspend fun getById(id: String): AiCharacterCard? = withContext(Dispatchers.IO) {
        dao.getById(id)
    }

    override suspend fun getByBookUrl(bookUrl: String): List<AiCharacterCard> =
        withContext(Dispatchers.IO) {
            dao.getByBookUrl(bookUrl)
        }

    override suspend fun save(
        name: String,
        description: String,
        openingLine: String,
        worldBookIds: String,
        cardId: String?
    ): AiCharacterCard = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val id = cardId ?: "charcard_${UUID.randomUUID().toString().replace("-", "")}"
        val existing = if (cardId != null) dao.getById(cardId) else null
        AiCharacterCard(
            id = id,
            name = name,
            description = description,
            openingLine = openingLine,
            worldBookIds = worldBookIds.takeIf { it.isNotBlank() } ?: existing?.worldBookIds ?: "",
            personality = existing?.personality.orEmpty(),
            scenario = existing?.scenario.orEmpty(),
            exampleDialogues = existing?.exampleDialogues.orEmpty(),
            postHistoryInstructions = existing?.postHistoryInstructions.orEmpty(),
            alternateOpenings = existing?.alternateOpenings ?: "[]",
            aliasesJson = existing?.aliasesJson ?: "[]",
            voiceGender = existing?.voiceGender ?: AiCharacterCard.VOICE_GENDER_UNKNOWN,
            voiceAgeBand = existing?.voiceAgeBand ?: AiCharacterCard.VOICE_AGE_UNKNOWN,
            bookUrl = existing?.bookUrl.orEmpty(),
            bookName = existing?.bookName.orEmpty(),
            bookAuthor = existing?.bookAuthor.orEmpty(),
            avatarPath = existing?.avatarPath.orEmpty(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        ).also { dao.insert(it) }
    }

    override suspend fun upsert(card: AiCharacterCard): AiCharacterCard = withContext(Dispatchers.IO) {
        val saved = card.copy(updatedAt = System.currentTimeMillis())
        dao.insert(saved)
        saved
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.delete(id)
    }
}
