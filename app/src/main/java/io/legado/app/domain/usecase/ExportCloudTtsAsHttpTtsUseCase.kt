package io.legado.app.domain.usecase

import io.legado.app.data.appDb
import io.legado.app.data.entities.HttpTTS
import io.legado.app.domain.gateway.CloudTtsEngineGateway
import io.legado.app.domain.model.readaloud.CloudTtsVoiceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExportCloudTtsAsHttpTtsResult(
    val httpTtsIds: List<Long>,
    val exportedCount: Int,
)

class ExportCloudTtsAsHttpTtsUseCase(
    private val engineGateway: CloudTtsEngineGateway,
) {

    suspend operator fun invoke(
        engineId: String,
        speakerIds: List<String> = emptyList(),
        voiceNames: Map<String, String> = emptyMap(),
        voiceConfig: CloudTtsVoiceConfig = CloudTtsVoiceConfig(),
    ): ExportCloudTtsAsHttpTtsResult = withContext(Dispatchers.IO) {
        val engine = engineGateway.get(engineId)
            ?: error("Cloud TTS engine not found: $engineId")
        require(CloudTtsHttpRuleExporter.isExportable(engine.provider)) {
            "Provider ${engine.provider} cannot be exported as HTTP TTS; use native cloud playback"
        }
        val targets = speakerIds.ifEmpty { listOf("") }
        val ids = mutableListOf<Long>()
        targets.forEachIndexed { index, speakerId ->
            val id = System.currentTimeMillis() + index
            val httpTts = CloudTtsHttpRuleExporter.export(
                engine = engine,
                speakerId = speakerId,
                voiceDisplayName = voiceNames[speakerId].orEmpty(),
                voiceConfig = voiceConfig,
                id = id,
                redactSecrets = false,
            )
            appDb.httpTTSDao.insert(httpTts)
            ids += id
        }
        ExportCloudTtsAsHttpTtsResult(httpTtsIds = ids, exportedCount = ids.size)
    }

    suspend fun preview(
        engineId: String,
        speakerId: String = "",
        voiceDisplayName: String = "",
        redactSecrets: Boolean = true,
    ): HttpTTS = withContext(Dispatchers.IO) {
        val engine = engineGateway.get(engineId)
            ?: error("Cloud TTS engine not found: $engineId")
        CloudTtsHttpRuleExporter.export(
            engine = engine,
            speakerId = speakerId,
            voiceDisplayName = voiceDisplayName,
            redactSecrets = redactSecrets,
        )
    }
}
