package io.legado.app.help.readaloud.playback

import android.content.Context
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.data.appDb
import io.legado.app.domain.model.readaloud.ReadAloudPlaybackCue
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.domain.model.readaloud.SpeechEngineRoute
import io.legado.app.domain.model.readaloud.SpeechVoiceRouter
import io.legado.app.help.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Routes a playback cue to system / HTTP / cloud synthesizers and writes audio to [output].
 */
class ReadAloudCueSynthesizer(
    context: Context,
    private val cloudSynthesizer: CloudTtsAudioSynthesizer,
    private val systemSynthesizer: SystemTtsFileSynthesizer = SystemTtsFileSynthesizer(context),
    private val httpFetcher: HttpTtsStreamFetcher = HttpTtsStreamFetcher(),
) {

    private val supportedEngineTypes = setOf(
        ReadAloudVoice.ENGINE_SYSTEM,
        ReadAloudVoice.ENGINE_HTTP,
        ReadAloudVoice.ENGINE_CLOUD,
    )

    suspend fun synthesizeToFile(
        cue: ReadAloudPlaybackCue,
        text: String,
        output: File,
        defaultRoute: SpeechEngineRoute,
        speechRate: Int = AppConfig.speechRatePlay + 5,
    ): Boolean = withContext(Dispatchers.IO) {
        val speakText = text.replace(AppPattern.notReadAloudRegex, "")
        if (speakText.isBlank()) return@withContext false
        val routed = SpeechVoiceRouter.route(cue, supportedEngineTypes, defaultRoute)
        val candidates = buildList {
            routed.voice?.let(::add)
            addAll(cue.fallbackVoices)
        }.filter { it.enabled && it.available && it.engineType in supportedEngineTypes }
            .distinctBy(ReadAloudVoice::id)
            .ifEmpty {
                listOfNotNull(routed.voice)
            }

        for (voice in candidates) {
            val ok = runCatching {
                when (voice.engineType) {
                    ReadAloudVoice.ENGINE_HTTP -> synthesizeHttp(voice, speakText, output, speechRate)
                    ReadAloudVoice.ENGINE_SYSTEM -> synthesizeSystem(voice, speakText, output, speechRate)
                    ReadAloudVoice.ENGINE_CLOUD -> cloudSynthesizer.synthesize(
                        voice = voice,
                        text = speakText,
                        output = output,
                        styleOverride = cue.emotion,
                        characterPerformance = cue.characterPerformance,
                        roleType = cue.roleType,
                    )
                    else -> false
                }
            }.onFailure {
                AppLog.put(
                    "Cue synthesize failed (${voice.engineType}/${voice.displayName}): ${it.localizedMessage}",
                    it,
                )
            }.getOrDefault(false)
            if (ok && output.exists() && output.length() > 0) return@withContext true
        }
        false
    }

    private suspend fun synthesizeHttp(
        voice: ReadAloudVoice,
        text: String,
        output: File,
        speechRate: Int,
    ): Boolean {
        val id = voice.engineId.removePrefix("http:").toLongOrNull()
            ?: voice.engineId.toLongOrNull()
            ?: return false
        val httpTts = appDb.httpTTSDao.get(id) ?: return false
        val stream = httpFetcher.fetch(httpTts, text, speechRate) ?: return false
        output.parentFile?.mkdirs()
        if (output.exists()) output.delete()
        stream.use { input -> output.outputStream().use { input.copyTo(it) } }
        return output.length() > 0
    }

    private suspend fun synthesizeSystem(
        voice: ReadAloudVoice,
        text: String,
        output: File,
        speechRate: Int,
    ): Boolean {
        val enginePackage = voice.engineId.removePrefix("system:")
        val rate = (speechRate.coerceIn(0, 50) + 5) / 10f
        return systemSynthesizer.synthesize(
            engine = enginePackage,
            voiceName = voice.speakerId,
            text = text,
            output = output,
            speechRate = rate,
        )
    }
}
