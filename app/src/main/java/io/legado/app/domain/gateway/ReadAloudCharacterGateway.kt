package io.legado.app.domain.gateway

import io.legado.app.data.entities.AiCharacterCard
import io.legado.app.domain.model.readaloud.CharacterPerformanceProfile
import io.legado.app.domain.model.readaloud.SpeakerCharacter
import kotlinx.coroutines.flow.Flow

/**
 * Character source for multi-speaker read-aloud.
 * Backed by [AiCharacterCard] + [io.legado.app.data.entities.BookCharacterCast],
 * not MD3 BookKnowledge profiles.
 */
interface ReadAloudCharacterGateway {
    fun observeSpeakerCharacters(bookUrl: String): Flow<List<SpeakerCharacter>>
    fun observeCharacterCards(bookUrl: String): Flow<List<AiCharacterCard>>
    suspend fun getSpeakerCharacters(bookUrl: String): List<SpeakerCharacter>
    suspend fun getCharacterCards(bookUrl: String): List<AiCharacterCard>
    suspend fun getPerformanceProfiles(bookUrl: String): List<CharacterPerformanceProfile>
    suspend fun getCastRoles(bookUrl: String): Map<String, String>
    suspend fun getDramaticRole(bookUrl: String, characterCardId: String): String
    suspend fun updateDramaticRole(bookUrl: String, characterCardId: String, dramaticRole: String)
    suspend fun setCast(bookUrl: String, characterCardIds: List<String>)
    suspend fun addToCast(bookUrl: String, characterCardId: String)
    suspend fun removeFromCast(bookUrl: String, characterCardId: String)
}
