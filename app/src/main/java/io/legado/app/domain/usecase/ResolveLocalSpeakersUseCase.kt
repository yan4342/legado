package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.ChapterSpeechGateway
import io.legado.app.domain.gateway.ReadAloudCharacterGateway
import io.legado.app.domain.model.readaloud.CanonicalSpeechParagraph
import io.legado.app.domain.model.readaloud.ChapterSpeechAnalysisResult
import io.legado.app.domain.model.readaloud.SpeechAnalysisStatus
import io.legado.app.domain.model.readaloud.SpeechIdentity
import io.legado.app.domain.model.readaloud.SpeechResolutionSource
import io.legado.app.domain.model.readaloud.SpeechRoleType
import io.legado.app.help.readaloud.resolve.LocalCharacterSpeakerResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ResolveLocalSpeakersUseCase(
    private val readAloudCharacterGateway: ReadAloudCharacterGateway,
    private val chapterSpeechGateway: ChapterSpeechGateway,
) {

    suspend operator fun invoke(
        analysisResult: ChapterSpeechAnalysisResult,
        paragraphs: List<CanonicalSpeechParagraph>,
        now: Long = System.currentTimeMillis(),
    ): ChapterSpeechAnalysisResult {
        val bookUrl = analysisResult.analysis.bookUrl
        val characters = withContext(Dispatchers.IO) {
            readAloudCharacterGateway.getSpeakerCharacters(bookUrl)
        }
        val characterPerformances = withContext(Dispatchers.IO) {
            readAloudCharacterGateway.getPerformanceProfiles(bookUrl)
        }
        val characterRevision = buildString {
            append(LocalCharacterSpeakerResolver.VERSION)
            append(':')
            append(SpeechIdentity.characterRevision(characters))
        }
        if (analysisResult.analysis.characterRevision == characterRevision) {
            return analysisResult.copy(
                fromCache = true,
                characterPerformances = characterPerformances,
            )
        }

        val resetSegments = analysisResult.segments.map { segment ->
            if (segment.source == SpeechResolutionSource.Local && !segment.userLocked) {
                segment.copy(
                    characterId = null,
                    characterName = "",
                    confidence = minOf(segment.confidence, 0.72f),
                    source = SpeechResolutionSource.Rule,
                )
            } else {
                segment
            }
        }
        val resolved = LocalCharacterSpeakerResolver.resolve(
            paragraphs = paragraphs,
            segments = resetSegments,
            characters = characters,
        )
        val status = if (resolved.any { it.needsSpeakerResolution }) {
            SpeechAnalysisStatus.Partial
        } else {
            SpeechAnalysisStatus.Success
        }
        val analysis = analysisResult.analysis.copy(
            characterRevision = characterRevision,
            status = status,
            updatedAt = now,
        )
        chapterSpeechGateway.saveAnalysis(analysis, resolved)
        return ChapterSpeechAnalysisResult(
            analysis = analysis,
            segments = resolved,
            fromCache = false,
            characterPerformances = characterPerformances,
        )
    }

    private val io.legado.app.domain.model.readaloud.ChapterSpeechSegment.needsSpeakerResolution: Boolean
        get() = characterId == null &&
            (roleType == SpeechRoleType.Character || roleType == SpeechRoleType.Thought)
}
