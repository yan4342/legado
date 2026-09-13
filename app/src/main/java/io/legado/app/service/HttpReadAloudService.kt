package io.legado.app.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.Downloader
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.script.ScriptException
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.data.entities.HttpTTS
import io.legado.app.domain.model.readaloud.ReadAloudPlaybackCue
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.domain.model.readaloud.SpeechEngineRoute
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.exoplayer.InputStreamDataSource
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.readaloud.playback.HttpTtsStreamFetcher
import io.legado.app.help.readaloud.playback.ReadAloudCueSynthesizer
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.toastOnUi
import io.legado.app.lib.dialogs.SelectItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.Response
import org.koin.android.ext.android.inject
import org.mozilla.javascript.WrappedException
import splitties.init.appCtx
import java.io.File
import java.io.InputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlin.time.Duration.Companion.seconds

/**
 * 在线朗读
 */
@SuppressLint("UnsafeOptInUsageError")
class HttpReadAloudService : BaseReadAloudService(),
    Player.Listener {

    override val useSpeechPlaybackQueue: Boolean = true

    private val cueSynthesizer: ReadAloudCueSynthesizer by inject()
    private val httpFetcher = HttpTtsStreamFetcher()
    private val synthSemaphore by lazy {
        Semaphore(AppConfig.ttsPreSynthesisConcurrency.coerceIn(1, 8))
    }

    private val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(this).setLoadControl(
            DefaultLoadControl.Builder().setBufferDurationsMs(
                1800_000_000,
                1800_000_000,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            ).build()
        ).build()
    }
    private val ttsFolderPath: String by lazy {
        cacheDir.absolutePath + File.separator + "httpTTS" + File.separator
    }
    private val cache by lazy {
        SimpleCache(
            File(cacheDir, "httpTTS_cache"),
            LeastRecentlyUsedCacheEvictor(128 * 1024 * 1024),
            StandaloneDatabaseProvider(appCtx)
        )
    }
    private val cacheDataSinkFactory by lazy {
        CacheDataSink.Factory()
            .setCache(cache)
    }
    private val loadErrorHandlingPolicy by lazy {
        CustomLoadErrorHandlingPolicy()
    }
    private var speechRate: Int = AppConfig.speechRatePlay + 5
    private var downloadTask: Coroutine<*>? = null
    private var playIndexJob: Job? = null
    private var downloadErrorNo: Int = 0
    private var playErrorNo = 0
    private val downloadTaskActiveLock = Mutex()
    private val loadingState = MutableStateFlow(false)

    protected override val currentSpeechRate: Float
        get() = speechRate / 10f

    override fun onCreate() {
        super.onCreate()
        exoPlayer.addListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadTask?.cancel()
        exoPlayer.release()
        cache.release()
        Coroutine.async {
            removeCacheFile()
        }
    }

    override fun play() {
        pageChanged = false
        exoPlayer.stop()
        if (!requestFocus()) return
        if (contentList.isEmpty()) {
            AppLog.putDebug("朗读列表为空")
            ReadBook.readAloud()
        } else {
            super.play()
            // Multi-engine cue playback always uses file synthesis (ExoPlayer playlist).
            if (hasSpeechPlaybackQueue || !AppConfig.streamReadAloudAudio) {
                downloadAndPlayAudios()
            } else {
                downloadAndPlayAudiosStream()
            }
        }
    }

    override fun playStop() {
        exoPlayer.stop()
        playIndexJob?.cancel()
    }

    private fun updateNextPos() {
        if (hasSpeechPlaybackQueue) {
            val current = playbackCursor ?: io.legado.app.domain.model.readaloud.ReadAloudPlaybackCursor(
                nowSpeak,
                paragraphStartPos,
            )
            playbackQueue.next(current)?.let { next ->
                moveToPlaybackCursor(next)
            } ?: nextChapter()
            return
        }
        readAloudNumber += contentList[nowSpeak].length + 1 - paragraphStartPos
        paragraphStartPos = 0
        if (nowSpeak < contentList.lastIndex) {
            nowSpeak++
        } else {
            nextChapter()
        }
    }

    private fun defaultSpeechRoute(): SpeechEngineRoute {
        val http = ReadAloud.httpTTS
        if (http != null) {
            return SpeechEngineRoute(
                engineType = ReadAloudVoice.ENGINE_HTTP,
                engineId = http.id.toString(),
            )
        }
        val enginePackage = runCatching {
            GSON.fromJsonObject<SelectItem<String>>(ReadAloud.ttsEngine).getOrNull()?.value.orEmpty()
        }.getOrDefault("")
        return SpeechEngineRoute(
            engineType = ReadAloudVoice.ENGINE_SYSTEM,
            engineId = if (enginePackage.isBlank()) "" else "system:$enginePackage",
            speakerId = "",
        )
    }

    private fun downloadAndPlayAudios() {
        exoPlayer.clearMediaItems()
        downloadTask?.cancel()
        downloadTask = execute {
            downloadTaskActiveLock.withLock {
                ensureActive()
                if (hasSpeechPlaybackQueue) {
                    downloadAndPlayCueAudios()
                } else {
                    downloadAndPlayLegacyHttpAudios()
                }
            }
        }.onError {
            AppLog.put("朗读下载出错\n${it.localizedMessage}", it, true)
        }
    }

    private suspend fun CoroutineScope.downloadAndPlayCueAudios() {
        val route = defaultSpeechRoute()
        val cues = playbackQueue.cues
        for (index in nowSpeak until cues.size) {
            ensureActive()
            val cue = cues[index]
            var text = cue.text
            if (paragraphStartPos > 0 && index == nowSpeak) {
                text = text.substring(paragraphStartPos.coerceAtMost(text.length))
            }
            val fileName = md5SpeakFileName(text, cue = cue)
            val speakText = text.replace(AppPattern.notReadAloudRegex, "")
            if (speakText.isEmpty()) {
                createSilentSound(fileName)
            } else if (!hasSpeakFile(fileName)) {
                synthSemaphore.withPermit {
                    ensureActive()
                    val file = getSpeakFileAsMd5(fileName)
                    val ok = cueSynthesizer.synthesizeToFile(
                        cue = cue,
                        text = text,
                        output = file,
                        defaultRoute = route,
                        speechRate = speechRate,
                    )
                    if (!ok) createSilentSound(fileName)
                }
            }
            val file = getSpeakFileAsMd5(fileName)
            val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
            launch(Main) {
                if (AppConfig.ttsParagraphInterval > 0) {
                    if (index == nowSpeak && exoPlayer.mediaItemCount == 0) {
                        exoPlayer.setMediaItem(mediaItem)
                    }
                } else if (exoPlayer.mediaItemCount == 0) {
                    exoPlayer.setMediaItem(mediaItem)
                } else {
                    exoPlayer.addMediaItem(mediaItem)
                }
            }
        }
        preDownloadCueAudios()
    }

    private suspend fun CoroutineScope.downloadAndPlayLegacyHttpAudios() {
        val httpTts = ReadAloud.httpTTS
        contentList.forEachIndexed { index, content ->
            ensureActive()
            if (index < nowSpeak) return@forEachIndexed
            var text = content
            if (paragraphStartPos > 0 && index == nowSpeak) {
                text = text.substring(paragraphStartPos)
            }
            val fileName = md5SpeakFileName(text)
            val speakText = text.replace(AppPattern.notReadAloudRegex, "")
            if (speakText.isEmpty()) {
                AppLog.put("阅读段落内容为空，使用无声音频代替。\n朗读文本：$text")
                createSilentSound(fileName)
            } else if (!hasSpeakFile(fileName)) {
                runCatching {
                    if (httpTts != null) {
                        val inputStream = getSpeakStream(httpTts, speakText)
                        if (inputStream != null) {
                            createSpeakFile(fileName, inputStream)
                        } else {
                            createSilentSound(fileName)
                        }
                    } else {
                        // Multi-speaker mode with system default engine: synthesize to file.
                        val route = defaultSpeechRoute()
                        val cue = ReadAloudPlaybackCue(
                            text = text,
                            chapterStart = 0,
                            chapterEnd = text.length,
                            paragraphIndex = index,
                            voice = null,
                            fallbackVoices = emptyList(),
                            roleType = io.legado.app.domain.model.readaloud.SpeechRoleType.Narrator,
                            characterId = null,
                        )
                        val file = getSpeakFileAsMd5(fileName)
                        val ok = cueSynthesizer.synthesizeToFile(
                            cue = cue,
                            text = text,
                            output = file,
                            defaultRoute = route,
                            speechRate = speechRate,
                        )
                        if (!ok) createSilentSound(fileName)
                    }
                }.onFailure {
                    when (it) {
                        is CancellationException -> Unit
                        else -> pauseReadAloud()
                    }
                    return
                }
            }
            val file = getSpeakFileAsMd5(fileName)
            val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
            launch(Main) {
                if (AppConfig.ttsParagraphInterval > 0) {
                    if (index == nowSpeak && exoPlayer.mediaItemCount == 0) {
                        exoPlayer.setMediaItem(mediaItem)
                    }
                } else if (exoPlayer.mediaItemCount == 0) {
                    exoPlayer.setMediaItem(mediaItem)
                } else {
                    exoPlayer.addMediaItem(mediaItem)
                }
            }
        }
        if (httpTts != null) {
            preDownloadAudios(httpTts)
        }
    }

    private suspend fun preDownloadCueAudios() {
        val textChapter = ReadBook.nextTextChapter ?: return
        val bookUrl = ReadBook.book?.bookUrl.orEmpty()
        if (bookUrl.isEmpty()) return
        val plan = buildSpeechPlan(
            bookUrl = bookUrl,
            chapterIndex = ReadBook.durChapterIndex + 1,
            textChapter = textChapter,
        )
        val queue = runCatching {
            io.legado.app.domain.model.readaloud.ReadAloudPlaybackQueue.from(plan)
        }.getOrDefault(io.legado.app.domain.model.readaloud.ReadAloudPlaybackQueue.Empty)
        if (queue.isEmpty) return
        val route = defaultSpeechRoute()
        val limit = AppConfig.audioPreDownloadNum.coerceIn(1, 30)
        queue.cues.take(limit).forEach { cue ->
            currentCoroutineContext().ensureActive()
            val fileName = md5SpeakFileName(cue.text, cue = cue, textChapter = textChapter)
            val speakText = cue.text.replace(AppPattern.notReadAloudRegex, "")
            if (speakText.isEmpty()) {
                createSilentSound(fileName)
            } else if (!hasSpeakFile(fileName)) {
                synthSemaphore.withPermit {
                    val file = getSpeakFileAsMd5(fileName)
                    cueSynthesizer.synthesizeToFile(
                        cue = cue,
                        text = cue.text,
                        output = file,
                        defaultRoute = route,
                        speechRate = speechRate,
                    )
                }
            }
        }
    }

    private suspend fun preDownloadAudios(httpTts: HttpTTS) {
        val textChapter = ReadBook.nextTextChapter ?: return
        val contentList = textChapter.getNeedReadAloud(0, readAloudByPage, 0, 1)
            .splitToSequence("\n")
            .filter { it.isNotEmpty() }
            .take(10)
            .toList()
        contentList.forEach { content ->
            currentCoroutineContext().ensureActive()
            val fileName = md5SpeakFileName(content, textChapter)
            val speakText = content.replace(AppPattern.notReadAloudRegex, "")
            if (speakText.isEmpty()) {
                createSilentSound(fileName)
            } else if (!hasSpeakFile(fileName)) {
                runCatching {
                    val inputStream = getSpeakStream(httpTts, speakText)
                    if (inputStream != null) {
                        createSpeakFile(fileName, inputStream)
                    } else {
                        createSilentSound(fileName)
                    }
                }
            }
        }
    }

    private fun downloadAndPlayAudiosStream() {
        exoPlayer.clearMediaItems()
        downloadTask?.cancel()
        downloadTask = execute {
            downloadTaskActiveLock.withLock {
                ensureActive()
                val httpTts = ReadAloud.httpTTS ?: throw NoStackTraceException("tts is null")
                val downloaderChannel = Channel<Downloader>()
                launch {
                    for (downloader in downloaderChannel) {
                        downloader.download(null)
                    }
                }
                contentList.forEachIndexed { index, content ->
                    ensureActive()
                    if (index < nowSpeak) return@forEachIndexed
                    var text = content
                    if (paragraphStartPos > 0 && index == nowSpeak) {
                        text = text.substring(paragraphStartPos)
                    }
                    val speakText = text.replace(AppPattern.notReadAloudRegex, "")
                    if (speakText.isEmpty()) {
                        AppLog.put("阅读段落内容为空，使用无声音频代替。\n朗读文本：$speakText")
                    }
                    val fileName = md5SpeakFileName(text)
                    val dataSourceFactory = createDataSourceFactory(httpTts, speakText)
                    val mediaSource = createMediaSource(dataSourceFactory, fileName)
                    launch(Main) {
                        if (AppConfig.ttsParagraphInterval > 0) {
                            if (index == nowSpeak && exoPlayer.mediaItemCount == 0) {
                                exoPlayer.setMediaSource(mediaSource)
                            }
                        } else if (exoPlayer.mediaItemCount == 0) {
                            exoPlayer.setMediaSource(mediaSource)
                        } else {
                            exoPlayer.addMediaSource(mediaSource)
                        }
                    }
                }
                preDownloadAudiosStream(httpTts, downloaderChannel)
            }
        }.onError {
            AppLog.put("朗读下载出错\n${it.localizedMessage}", it, true)
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun preDownloadAudiosStream(
        httpTts: HttpTTS,
        downloaderChannel: Channel<Downloader>
    ) {
        val textChapter = ReadBook.nextTextChapter ?: return
        val contentList = textChapter.getNeedReadAloud(0, readAloudByPage, 0, 1)
            .splitToSequence("\n")
            .filter { it.isNotEmpty() }
            .toList()
        val flow = loadingState.debounce(1.seconds)
        contentList.forEach { content ->
            currentCoroutineContext().ensureActive()
            val fileName = md5SpeakFileName(content, textChapter)
            val speakText = content.replace(AppPattern.notReadAloudRegex, "")
            val dataSourceFactory = createDataSourceFactory(httpTts, speakText)
            val downloader = createDownloader(dataSourceFactory, fileName)
            downloaderChannel.send(downloader)
            flow.first { !it }
        }
    }

    private fun createDataSourceFactory(
        httpTts: HttpTTS,
        speakText: String
    ): CacheDataSource.Factory {
        val upstreamFactory = DataSource.Factory {
            InputStreamDataSource {
                if (speakText.isEmpty()) {
                    null
                } else {
                    kotlin.runCatching {
                        runBlocking(lifecycleScope.coroutineContext[Job]!!) {
                            getSpeakStream(httpTts, speakText)
                        }
                    }.onFailure {
                        when (it) {
                            is InterruptedException,
                            is CancellationException -> Unit

                            else -> pauseReadAloud()
                        }
                    }.getOrThrow()
                } ?: resources.openRawResource(R.raw.silent_sound)
            }
        }
        val factory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheWriteDataSinkFactory(cacheDataSinkFactory)
        return factory
    }

    private fun createDownloader(factory: CacheDataSource.Factory, fileName: String): Downloader {
        val uri = fileName.toUri()
        val request = DownloadRequest.Builder(fileName, uri).build()
        return DefaultDownloaderFactory(factory, okHttpClient.dispatcher.executorService)
            .createDownloader(request)
    }

    private fun createMediaSource(factory: DataSource.Factory, fileName: String): MediaSource {
        return DefaultMediaSourceFactory(this)
            .setDataSourceFactory(factory)
            .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
            .createMediaSource(MediaItem.fromUri(fileName))
    }

    private suspend fun getSpeakStream(
        httpTts: HttpTTS,
        speakText: String
    ): InputStream? {
        return httpFetcher.fetch(
            httpTts = httpTts,
            speakText = speakText,
            speechRate = speechRate,
            onTransientError = {
                downloadErrorNo++
                downloadErrorNo > 5
            },
        ).also {
            if (it != null) downloadErrorNo = 0
        }
    }

    private fun md5SpeakFileName(
        content: String,
        textChapter: TextChapter? = this.textChapter,
        cue: ReadAloudPlaybackCue? = null,
    ): String {
        val voiceKey = cue?.voice?.let { voice ->
            "${voice.engineType}:|:${voice.engineId}:|:${voice.speakerId}:|:${voice.id}"
        } ?: "${ReadAloud.httpTTS?.url}"
        return MD5Utils.md5Encode16(textChapter?.title ?: "") + "_" +
                MD5Utils.md5Encode16("$voiceKey-|-$speechRate-|-$content")
    }

    private fun createSilentSound(fileName: String) {
        val file = createSpeakFile(fileName)
        file.writeBytes(resources.openRawResource(R.raw.silent_sound).readBytes())
    }

    private fun hasSpeakFile(name: String): Boolean {
        return FileUtils.exist("${ttsFolderPath}$name.mp3")
    }

    private fun getSpeakFileAsMd5(name: String): File {
        return File("${ttsFolderPath}$name.mp3")
    }

    private fun createSpeakFile(name: String): File {
        return FileUtils.createFileIfNotExist("${ttsFolderPath}$name.mp3")
    }

    private fun createSpeakFile(name: String, inputStream: InputStream) {
        FileUtils.createFileIfNotExist("${ttsFolderPath}$name.mp3").outputStream().use { out ->
            inputStream.use {
                it.copyTo(out)
            }
        }
    }

    /**
     * 移除缓存文件
     */
    private fun removeCacheFile() {
        val titleMd5 = MD5Utils.md5Encode16(textChapter?.title ?: "")
        FileUtils.listDirsAndFiles(ttsFolderPath)?.forEach {
            val isSilentSound = it.length() == 2160L
            if ((!it.name.startsWith(titleMd5)
                        && System.currentTimeMillis() - it.lastModified() > 600000)
                || isSilentSound
            ) {
                FileUtils.delete(it.absolutePath)
            }
        }
    }


    override fun pauseReadAloud(abandonFocus: Boolean) {
        super.pauseReadAloud(abandonFocus)
        kotlin.runCatching {
            playIndexJob?.cancel()
            exoPlayer.pause()
        }
    }

    override fun resumeReadAloud() {
        super.resumeReadAloud()
        kotlin.runCatching {
            if (pageChanged) {
                play()
            } else {
                exoPlayer.play()
                upPlayPos()
            }
        }
    }

    private fun upPlayPos() {
        playIndexJob?.cancel()
        val textChapter = textChapter ?: return
        playIndexJob = lifecycleScope.launch {
            upTtsProgress(readAloudNumber + 1)
            if (exoPlayer.duration <= 0) {
                return@launch
            }
            val speakTextLength = contentList[nowSpeak].length
            if (speakTextLength <= 0) {
                return@launch
            }
            val sleep = exoPlayer.duration / speakTextLength
            val start = speakTextLength * exoPlayer.currentPosition / exoPlayer.duration
            for (i in start..contentList[nowSpeak].length) {
                if (pageIndex + 1 < textChapter.pageSize
                    && readAloudNumber + i > textChapter.getReadLength(pageIndex + 1)
                ) {
                    pageIndex++
                    ReadBook.moveToNextPage()
                    upTtsProgress(readAloudNumber + i.toInt())
                }
                delay(sleep)
            }
        }
    }

    /**
     * 更新朗读速度
     */
    override fun upSpeechRate(reset: Boolean) {
        downloadTask?.cancel()
        exoPlayer.stop()
        speechRate = AppConfig.speechRatePlay + 5
        upMediaMetadata()
        if (AppConfig.streamReadAloudAudio) {
            downloadAndPlayAudiosStream()
        } else {
            downloadAndPlayAudios()
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)
        when (playbackState) {
            Player.STATE_IDLE -> {
                // 空闲
            }

            Player.STATE_BUFFERING -> {
                // 缓冲中
            }

            Player.STATE_READY -> {
                // 准备好
                if (pause) return
                exoPlayer.play()
                upPlayPos()
            }

            Player.STATE_ENDED -> {
                // 结束
                playErrorNo = 0
                val interval = AppConfig.ttsParagraphInterval.toLong()
                if (interval > 0) {
                    val isLastParagraph = nowSpeak >= contentList.lastIndex
                    updateNextPos()
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                    if (!pause && !isLastParagraph) {
                        execute {
                            delay(interval)
                            if (!pause) {
                                launch(Main) {
                                    if (!pause) {
                                        play()
                                    }
                                }
                            }
                        }
                    }
                } else {
                    updateNextPos()
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                }
            }
        }
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        when (reason) {
            Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED -> {
                if (!timeline.isEmpty && exoPlayer.playbackState == Player.STATE_IDLE) {
                    exoPlayer.prepare()
                }
            }

            else -> {}
        }
    }

    override fun onIsLoadingChanged(isLoading: Boolean) {
        loadingState.value = isLoading
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) return
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            playErrorNo = 0
        }
        updateNextPos()
        upPlayPos()
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        AppLog.put("朗读错误\n${contentList[nowSpeak]}", error)
        deleteCurrentSpeakFile()
        playErrorNo++
        if (playErrorNo >= 5) {
            toastOnUi("朗读连续5次错误, 最后一次错误代码(${error.localizedMessage})")
            AppLog.put("朗读连续5次错误, 最后一次错误代码(${error.localizedMessage})", error)
            pauseReadAloud()
        } else {
            if (exoPlayer.hasNextMediaItem()) {
                exoPlayer.seekToNextMediaItem()
                exoPlayer.prepare()
            } else {
                exoPlayer.clearMediaItems()
                updateNextPos()
            }
        }
    }

    private fun deleteCurrentSpeakFile() {
        if (AppConfig.streamReadAloudAudio) {
            return
        }
        val mediaItem = exoPlayer.currentMediaItem ?: return
        val filePath = mediaItem.localConfiguration!!.uri.path!!
        File(filePath).delete()
    }

    override fun aloudServicePendingIntent(actionStr: String): PendingIntent? {
        return servicePendingIntent<HttpReadAloudService>(actionStr)
    }

    class CustomLoadErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy(0) {
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            return C.TIME_UNSET
        }
    }

}
