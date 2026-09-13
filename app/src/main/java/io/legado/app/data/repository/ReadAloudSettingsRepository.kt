package io.legado.app.data.repository

import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.model.PlaybackTimer
import io.legado.app.domain.model.settings.ReadAloudSettings
import io.legado.app.help.config.AppConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Read-aloud settings for legado (MD3 uses DataStore/AppConfigStore; adapted here).
 *
 * 设置流收编 Phase 5：全部字段统一经 AppConfig facade 读写（DS 快照读 + DS 写 + SP 镜像），
 * 本仓库不再有任何 SP 直连。
 */
class ReadAloudSettingsRepository : ReadAloudSettingsGateway {

    private val mutex = Mutex()
    private val _settings = MutableStateFlow(readSettings())
    override val settings: Flow<ReadAloudSettings> = _settings.asStateFlow()

    override val currentSettings: ReadAloudSettings
        get() = _settings.value

    override suspend fun update(transform: (ReadAloudSettings) -> ReadAloudSettings) {
        mutex.withLock {
            val next = transform(readSettings())
            writeSettings(next)
            _settings.value = next
        }
    }

    fun refresh() {
        _settings.value = readSettings()
    }

    companion object {
        const val DEFAULT_INTERFACE_CLASSIC = "classic"
        const val DEFAULT_INTERFACE_PLAYER = "player"
        val AVAILABLE_INTERFACES = setOf(DEFAULT_INTERFACE_CLASSIC, DEFAULT_INTERFACE_PLAYER)
    }
}

private fun readSettings(): ReadAloudSettings = ReadAloudSettings(
    ttsEngine = AppConfig.ttsEngine,
    ttsParagraphInterval = AppConfig.ttsParagraphInterval,
    audioCacheCleanTime = AppConfig.audioCacheCleanTime,
    ignoreAudioFocus = AppConfig.ignoreAudioFocus,
    mediaButtonOnExit = AppConfig.mediaButtonOnExit,
    readAloudByMediaButton = AppConfig.readAloudByMediaButton,
    pauseReadAloudWhilePhoneCalls = AppConfig.pauseReadAloudWhilePhoneCalls,
    readAloudWakeLock = AppConfig.readAloudWakeLock,
    showReadAloudCapsule = AppConfig.showReadAloudCapsule,
    capsuleAutoCollapse = AppConfig.capsuleAutoCollapse,
    capsuleOffsetX = AppConfig.capsuleOffsetX,
    capsuleOffsetY = AppConfig.capsuleOffsetY,
    mediaButtonPerNext = AppConfig.mediaButtonPerNext,
    readAloudByPage = AppConfig.readAloudByPage,
    systemMediaControlCompatibilityChange = AppConfig.systemMediaControlCompatibilityChange,
    streamReadAloudAudio = AppConfig.streamReadAloudAudio,
    ttsTimer = PlaybackTimer.normalize(AppConfig.ttsTimer),
    ttsFollowSys = AppConfig.ttsFlowSys,
    ttsSpeechRate = AppConfig.ttsSpeechRate,
    speechAnalysisMode = AppConfig.speechAnalysisMode,
    useMultiSpeaker = AppConfig.useMultiSpeaker,
    defaultInterface = AppConfig.readAloudDefaultInterface,
    contentSelectSpeakMode = AppConfig.contentSelectSpeakMode,
    audioPreDownloadNum = AppConfig.audioPreDownloadNum,
    ttsPreSynthesisConcurrency = AppConfig.ttsPreSynthesisConcurrency,
)

private fun writeSettings(settings: ReadAloudSettings) {
    AppConfig.ttsEngine = settings.ttsEngine
    AppConfig.ttsParagraphInterval = settings.ttsParagraphInterval
    AppConfig.audioCacheCleanTime = settings.audioCacheCleanTime
    AppConfig.ignoreAudioFocus = settings.ignoreAudioFocus
    AppConfig.mediaButtonOnExit = settings.mediaButtonOnExit
    AppConfig.readAloudByMediaButton = settings.readAloudByMediaButton
    AppConfig.pauseReadAloudWhilePhoneCalls = settings.pauseReadAloudWhilePhoneCalls
    AppConfig.readAloudWakeLock = settings.readAloudWakeLock
    AppConfig.showReadAloudCapsule = settings.showReadAloudCapsule
    AppConfig.capsuleAutoCollapse = settings.capsuleAutoCollapse
    AppConfig.capsuleOffsetX = settings.capsuleOffsetX
    AppConfig.capsuleOffsetY = settings.capsuleOffsetY
    AppConfig.mediaButtonPerNext = settings.mediaButtonPerNext
    AppConfig.readAloudByPage = settings.readAloudByPage
    AppConfig.systemMediaControlCompatibilityChange = settings.systemMediaControlCompatibilityChange
    AppConfig.streamReadAloudAudio = settings.streamReadAloudAudio
    AppConfig.ttsTimer = settings.ttsTimer
    AppConfig.ttsFlowSys = settings.ttsFollowSys
    AppConfig.ttsSpeechRate = settings.ttsSpeechRate
    AppConfig.speechAnalysisMode = settings.speechAnalysisMode
    AppConfig.useMultiSpeaker = settings.useMultiSpeaker
    AppConfig.readAloudDefaultInterface = settings.defaultInterface
    AppConfig.contentSelectSpeakMode = settings.contentSelectSpeakMode
    AppConfig.audioPreDownloadNum = settings.audioPreDownloadNum
    AppConfig.ttsPreSynthesisConcurrency = settings.ttsPreSynthesisConcurrency
}
