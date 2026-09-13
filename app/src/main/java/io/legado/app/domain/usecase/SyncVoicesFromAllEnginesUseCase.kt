package io.legado.app.domain.usecase

import io.legado.app.constant.AppLog
import io.legado.app.domain.gateway.CloudTtsEngineGateway
import io.legado.app.domain.gateway.HttpTtsEngineGateway
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.domain.model.readaloud.VoiceCatalogEntry
import io.legado.app.help.readaloud.playback.CloudTtsAudioSynthesizer
import io.legado.app.help.readaloud.playback.SystemTtsVoiceCatalog
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class SyncVoicesFromAllEnginesUseCase(
    private val cloudEngineGateway: CloudTtsEngineGateway,
    private val httpTtsEngineGateway: HttpTtsEngineGateway,
    private val cloudSynthesizer: CloudTtsAudioSynthesizer,
    private val systemCatalog: SystemTtsVoiceCatalog,
    private val syncVoices: SyncReadAloudVoicesUseCase,
) {

    suspend operator fun invoke(
        cloudEngineId: String? = null,
    ): SyncReadAloudVoicesResult = withContext(Dispatchers.IO) {
        val entries = mutableListOf<VoiceCatalogEntry>()
        entries += collectSystemVoices()
        entries += collectHttpVoices()
        entries += collectCloudVoices(cloudEngineId)
        syncVoices(
            entries = entries,
            managedSources = setOf(
                ReadAloudVoice.MANAGED_BY_CONFIGURED_TTS,
            ),
            removeMissingEngineTypes = if (cloudEngineId == null) {
                setOf(
                    ReadAloudVoice.ENGINE_SYSTEM,
                    ReadAloudVoice.ENGINE_HTTP,
                    ReadAloudVoice.ENGINE_CLOUD,
                )
            } else {
                emptySet()
            },
        )
    }

    /**
     * Match [io.legado.app.ui.book.read.config.SpeakEngineDialog]: one row per installed
     * system TTS engine (plus a "system default"), not every locale voice.
     */
    private suspend fun collectSystemVoices(): List<VoiceCatalogEntry> {
        return runCatching {
            buildList {
                add(
                    VoiceCatalogEntry(
                        engineType = ReadAloudVoice.ENGINE_SYSTEM,
                        engineId = "",
                        speakerId = "",
                        displayName = "系统默认",
                        managedBy = ReadAloudVoice.MANAGED_BY_CONFIGURED_TTS,
                    )
                )
                systemCatalog.getEngines().forEach { engine ->
                    add(
                        VoiceCatalogEntry(
                            engineType = ReadAloudVoice.ENGINE_SYSTEM,
                            engineId = engine.id,
                            speakerId = "",
                            displayName = engine.displayName,
                            traitsJson = GSON.toJson(
                                mapOf("sourceId" to engine.sourceId)
                            ),
                            managedBy = ReadAloudVoice.MANAGED_BY_CONFIGURED_TTS,
                        )
                    )
                }
            }
        }.onFailure {
            AppLog.put("同步系统 TTS 音色失败\n${it.localizedMessage}", it)
        }.getOrDefault(emptyList())
    }

    private suspend fun collectHttpVoices(): List<VoiceCatalogEntry> {
        return runCatching {
            httpTtsEngineGateway.observeAll().first().map { engine ->
                VoiceCatalogEntry(
                    engineType = ReadAloudVoice.ENGINE_HTTP,
                    engineId = engine.sourceId,
                    speakerId = "",
                    displayName = engine.displayName,
                    managedBy = ReadAloudVoice.MANAGED_BY_CONFIGURED_TTS,
                )
            }
        }.onFailure {
            AppLog.put("同步 HTTP TTS 音色失败\n${it.localizedMessage}", it)
        }.getOrDefault(emptyList())
    }

    private suspend fun collectCloudVoices(cloudEngineId: String?): List<VoiceCatalogEntry> {
        val engines = if (cloudEngineId != null) {
            listOfNotNull(cloudEngineGateway.get(cloudEngineId))
        } else {
            cloudEngineGateway.getAll().filter { it.enabled }
        }
        return engines.flatMap { engine ->
            runCatching {
                cloudSynthesizer.fetchVoices(engine).map { voice ->
                    VoiceCatalogEntry(
                        engineType = ReadAloudVoice.ENGINE_CLOUD,
                        engineId = engine.id,
                        speakerId = voice.id,
                        displayName = "${engine.name} · ${voice.displayName}",
                        traitsJson = GSON.toJson(
                            mapOf(
                                "locale" to voice.locale,
                                "gender" to voice.gender,
                                "sampleRate" to voice.sampleRate,
                            )
                        ),
                        emotionCatalogJson = GSON.toJson(voice.styles),
                        managedBy = ReadAloudVoice.MANAGED_BY_CONFIGURED_TTS,
                    )
                }
            }.onFailure {
                AppLog.put(
                    "同步云 TTS 音色失败 (${engine.name})\n${it.localizedMessage}",
                    it,
                )
            }.getOrDefault(emptyList())
        }
    }
}
