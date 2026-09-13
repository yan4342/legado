@file:Suppress("DEPRECATION")

package io.legado.app.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.annotation.CallSuper
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import io.legado.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.constant.NotificationId
import io.legado.app.constant.PreferKey
import io.legado.app.constant.Status
import io.legado.app.domain.model.readaloud.ReadAloudPlaybackCursor
import io.legado.app.domain.model.readaloud.ReadAloudPlaybackInfo
import io.legado.app.domain.model.readaloud.ReadAloudPlaybackQueue
import io.legado.app.domain.model.readaloud.ReadAloudSessionStatus
import io.legado.app.domain.model.readaloud.SpeechAnalysisMode
import io.legado.app.domain.model.readaloud.SpeechPlanItem
import io.legado.app.domain.usecase.PrepareChapterSpeechPlanUseCase
import io.legado.app.help.MediaHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.readaloud.segment.toCanonicalSpeechParagraphs
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.model.BookCover
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadAloudSessionStore
import io.legado.app.model.ReadBook
import io.legado.app.receiver.MediaButtonReceiver
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.utils.LogUtils
import io.legado.app.utils.activityPendingIntent
import io.legado.app.utils.broadcastPendingIntent
import io.legado.app.utils.observeEvent
import io.legado.app.utils.observeSharedPreferences
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get
import splitties.init.appCtx
import splitties.systemservices.audioManager
import splitties.systemservices.notificationManager
import splitties.systemservices.powerManager
import splitties.systemservices.telephonyManager
import splitties.systemservices.wifiManager

/**
 * 朗读服务
 */
abstract class BaseReadAloudService : BaseService(),
    AudioManager.OnAudioFocusChangeListener {

    companion object {
        @JvmStatic
        var isRun = false
            private set

        @JvmStatic
        var pause = true
            private set

        @JvmStatic
        var timeMinute: Int = 0
            private set

        fun isPlay(): Boolean {
            return isRun && !pause
        }

        private const val TAG = "BaseReadAloudService"

        /**
         * 语速 1.0x 时的每秒朗读字数估算值, 用于把字符进度换算成媒体播放器时间轴
         */
        private const val ESTIMATED_CHARS_PER_SECOND = 4f

        /** 当前朗读章节序号, 无活动章节时为 -1 */
        @JvmStatic
        var currentChapterIndex: Int = -1
            private set

        /** 当前朗读的章内字符位置 */
        @JvmStatic
        var currentProgress: Int = 0
            private set

        /** 朗读服务自身驱动的页面移动进行中（用于区分手动翻页脱离朗读位置） */
        @JvmStatic
        var speechDrivingNavigation: Boolean = false
            private set

        /** 在朗读驱动的页面移动/同步外侧调用，期间 [speechDrivingNavigation] 为 true。 */
        @JvmStatic
        fun <R> withSpeechNavigation(block: () -> R): R {
            speechDrivingNavigation = true
            try {
                return block()
            } finally {
                speechDrivingNavigation = false
            }
        }

    }

    private val useWakeLock = AppConfig.readAloudWakeLock
    private val wakeLock by lazy {
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "legado:ReadAloudService")
            .apply {
                this.setReferenceCounted(false)
            }
    }
    private val wifiLock by lazy {
        @Suppress("DEPRECATION")
        wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "legado:AudioPlayService")
            ?.apply {
                setReferenceCounted(false)
            }
    }
    private val mFocusRequest: AudioFocusRequestCompat by lazy {
        MediaHelp.buildAudioFocusRequestCompat(this)
    }
    private val mediaSessionCompat: MediaSessionCompat by lazy {
        MediaSessionCompat(this, "readAloud")
    }
    private val phoneStateListener by lazy {
        ReadAloudPhoneStateListener()
    }
    internal var contentList = emptyList<String>()
    internal var speechPlan = emptyList<SpeechPlanItem>()
    internal var playbackQueue = ReadAloudPlaybackQueue.Empty
    internal var playbackCursor: ReadAloudPlaybackCursor? = null
    internal var nowSpeak: Int = 0
    internal var readAloudNumber: Int = 0
    internal var textChapter: TextChapter? = null
    internal var pageIndex = 0
    private var needResumeOnAudioFocusGain = false
    private var needResumeOnCallStateIdle = false
    private var registeredPhoneStateListener = false
    private var dsJob: Job? = null
    private var upNotificationJob: Coroutine<*>? = null
    private var prepareReadAloudJob: Coroutine<*>? = null
    private var prepareReadAloudGeneration = 0L
    private var upMediaProgressJob: Job? = null
    private var lastMediaSessionState = PlaybackStateCompat.STATE_NONE
    private var lastMediaSessionPositionMs = -1L
    private var lastMediaSessionUpdateElapsedMs = 0L
    private var currentCharProgress = 0
    private var cover: Bitmap =
        BitmapFactory.decodeResource(appCtx.resources, R.drawable.icon_read_book)
    var pageChanged = false
    private var toLast = false
    var paragraphStartPos = 0
    var readAloudByPage = false
        private set
    protected open val useSpeechPlaybackQueue: Boolean = false
    protected val hasSpeechPlaybackQueue: Boolean
        get() = useSpeechPlaybackQueue && !playbackQueue.isEmpty

    /** 当前朗读倍速, 用于把字符进度估算成媒体播放器时间轴 */
    protected open val currentSpeechRate: Float
        get() = 1f
    private var waitNewReadAloud = true
    private val sessionStore: ReadAloudSessionStore by lazy {
        get(ReadAloudSessionStore::class.java)
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) {
                pauseReadAloud()
            }
        }
    }

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        isRun = true
        pause = false
        observeLiveBus()
        initMediaSession()
        initBroadcastReceiver()
        initPhoneStateListener()
        upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING)
        setTimer(AppConfig.ttsTimer)
        if (AppConfig.ttsTimer > 0) {
            toastOnUi("朗读定时 ${AppConfig.ttsTimer} 分钟")
        }
        execute {
            BookCover.executeCoverBitmap(
                context = this@BaseReadAloudService,
                path = ReadBook.book?.getDisplayCover(),
                sizePx = 256,
            )
        }.onSuccess { bitmap ->
            if (bitmap != null && bitmap.width > 16 && bitmap.height > 16) {
                cover = bitmap
                upReadAloudNotification()
            }
        }
    }

    fun observeLiveBus() {
        observeEvent<Bundle>(EventBus.READ_ALOUD_PLAY) {
            val play = it.getBoolean("play")
            val pageIndex = it.getInt("pageIndex")
            val startPos = it.getInt("startPos")
            newReadAloud(play, pageIndex, startPos)
        }
        observeSharedPreferences { _, key ->
            when (key) {
                PreferKey.ignoreAudioFocus,
                PreferKey.pauseReadAloudWhilePhoneCalls -> {
                    initPhoneStateListener()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (useWakeLock) {
            wakeLock.release()
            wifiLock?.release()
        }
        prepareReadAloudGeneration++
        prepareReadAloudJob?.cancel()
        isRun = false
        pause = true
        sessionStore.stop()
        abandonFocus()
        unregisterReceiver(broadcastReceiver)
        postEvent(EventBus.ALOUD_STATE, Status.STOP)
        notificationManager.cancel(NotificationId.ReadAloudService)
        upMediaSessionPlaybackState(PlaybackStateCompat.STATE_STOPPED)
        mediaSessionCompat.release()
        ReadBook.uploadProgress()
        unregisterPhoneStateListener(phoneStateListener)
        upNotificationJob?.invokeOnCompletion {
            notificationManager.cancel(NotificationId.ReadAloudService)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            IntentAction.play -> newReadAloud(
                intent.getBooleanExtra("play", true),
                intent.getIntExtra("pageIndex", ReadBook.durPageIndex),
                intent.getIntExtra("startPos", 0)
            )

            IntentAction.pause -> pauseReadAloud()
            IntentAction.resume -> resumeReadAloud()
            IntentAction.upTtsSpeechRate -> upSpeechRate(true)
            IntentAction.prevParagraph -> prevP()
            IntentAction.nextParagraph -> nextP()
            IntentAction.prev -> prevChapter()
            IntentAction.next -> nextChapter()
            IntentAction.addTimer -> addTimer()
            IntentAction.setTimer -> setTimer(intent.getIntExtra("minute", 0))
            IntentAction.stop -> stopSelf()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun newReadAloud(play: Boolean, pageIndex: Int, startPos: Int) {
        val generation = ++prepareReadAloudGeneration
        prepareReadAloudJob?.cancel()
        prepareReadAloudJob = execute(executeContext = IO) {
            val preparedChapter = ReadBook.curTextChapter ?: return@execute
            if (!preparedChapter.isCompleted) {
                return@execute
            }
            val preparedReadAloudByPage = AppConfig.readAloudByPage
            var preparedReadAloudNumber = preparedChapter.getReadLength(pageIndex) + startPos
            var preparedContentList = preparedChapter.getNeedReadAloud(0, preparedReadAloudByPage, 0)
                .split("\n")
                .filter { it.isNotEmpty() }
            val preparedSpeechPlan = buildSpeechPlan(
                bookUrl = ReadBook.book?.bookUrl.orEmpty(),
                chapterIndex = ReadBook.durChapterIndex,
                textChapter = preparedChapter,
            )
            if (generation != prepareReadAloudGeneration) return@execute
            val preparedPlaybackQueue = runCatching {
                ReadAloudPlaybackQueue.from(preparedSpeechPlan)
            }.onFailure {
                AppLog.put("创建多角色播放队列失败，使用原朗读方式\n${it.localizedMessage}", it)
            }.getOrDefault(ReadAloudPlaybackQueue.Empty)
            var preparedPlaybackCursor = preparedPlaybackQueue.cursorAt(preparedReadAloudNumber)
            var pos = startPos
            val page = preparedChapter.getPage(pageIndex) ?: return@execute
            if (pos > 0) {
                for (paragraph in page.paragraphs) {
                    val tmp = pos - paragraph.length - 1
                    if (tmp < 0) break
                    pos = tmp
                }
            }
            var preparedNowSpeak = preparedChapter.getParagraphNum(
                preparedReadAloudNumber + 1,
                preparedReadAloudByPage,
            ) - 1
            val moveToLast = toLast
            if (!preparedReadAloudByPage && startPos == 0 && !moveToLast) {
                pos = page.chapterPosition -
                        preparedChapter.paragraphs[preparedNowSpeak].chapterPosition
            }
            if (moveToLast) {
                preparedReadAloudNumber = preparedChapter.getLastParagraphPosition()
                preparedNowSpeak = preparedContentList.lastIndex
                if (page.paragraphs.size == 1) {
                    pos = page.chapterPosition -
                            preparedChapter.paragraphs[preparedNowSpeak].chapterPosition
                }
            }
            var preparedParagraphStartPos = pos
            if (useSpeechPlaybackQueue && !preparedPlaybackQueue.isEmpty) {
                preparedPlaybackQueue.cursorAt(preparedReadAloudNumber)?.let { cursor ->
                    preparedPlaybackCursor = cursor
                    preparedContentList = preparedPlaybackQueue.cues.map { it.text }
                    preparedNowSpeak = cursor.cueIndex
                    preparedParagraphStartPos = cursor.offset
                    preparedReadAloudNumber = preparedPlaybackQueue.cues[cursor.cueIndex].chapterStart
                }
            }
            if (generation != prepareReadAloudGeneration) return@execute
            this@BaseReadAloudService.pageIndex = pageIndex
            textChapter = preparedChapter
            readAloudByPage = preparedReadAloudByPage
            contentList = preparedContentList
            speechPlan = preparedSpeechPlan
            playbackQueue = preparedPlaybackQueue
            playbackCursor = preparedPlaybackCursor
            nowSpeak = preparedNowSpeak
            readAloudNumber = preparedReadAloudNumber
            paragraphStartPos = preparedParagraphStartPos
            waitNewReadAloud = false
            if (moveToLast) toLast = false
            launch(Main) {
                if (generation != prepareReadAloudGeneration) return@launch
                if (play) play() else pageChanged = true
            }
        }.onError {
            AppLog.put("启动朗读出错\n${it.localizedMessage}", it, true)
        }
    }

    protected suspend fun buildSpeechPlan(
        bookUrl: String,
        chapterIndex: Int,
        textChapter: TextChapter,
    ): List<SpeechPlanItem> {
        if (bookUrl.isEmpty() || !AppConfig.useMultiSpeaker) return emptyList()
        val prepareSpeechPlan: PrepareChapterSpeechPlanUseCase =
            get(PrepareChapterSpeechPlanUseCase::class.java)
        return runCatching {
            prepareSpeechPlan(
                bookUrl = bookUrl,
                chapterIndex = chapterIndex,
                paragraphs = textChapter.toCanonicalSpeechParagraphs(),
                analysisMode = SpeechAnalysisMode.fromStorage(AppConfig.speechAnalysisMode),
                useMultiSpeaker = AppConfig.useMultiSpeaker,
            )
        }.onFailure {
            AppLog.put("生成多角色朗读计划失败，使用原朗读方式\n${it.localizedMessage}", it)
        }.getOrDefault(emptyList())
    }

    @SuppressLint("WakelockTimeout")
    open fun play() {
        if (useWakeLock) {
            wakeLock.acquire()
            wifiLock?.acquire()
        }
        isRun = true
        pause = false
        needResumeOnAudioFocusGain = false
        needResumeOnCallStateIdle = false
        upReadAloudNotification()
        upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING)
        upMediaMetadata()
        upMediaProgress()
        sessionStore.setStatus(ReadAloudSessionStatus.Playing)
        postEvent(EventBus.ALOUD_STATE, Status.PLAY)
    }

    abstract fun playStop()

    @CallSuper
    open fun pauseReadAloud(abandonFocus: Boolean = true) {
        if (useWakeLock) {
            wakeLock.release()
            wifiLock?.release()
        }
        pause = true
        if (abandonFocus) {
            abandonFocus()
        }
        upMediaProgressJob?.cancel()
        upReadAloudNotification()
        upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PAUSED)
        sessionStore.setStatus(ReadAloudSessionStatus.Paused)
        postEvent(EventBus.ALOUD_STATE, Status.PAUSE)
        ReadBook.uploadProgress()
        doDs()
    }

    @SuppressLint("WakelockTimeout")
    @CallSuper
    open fun resumeReadAloud() {
        resumeReadAloudInternal()
    }

    private fun resumeReadAloudInternal() {
        pause = false
        needResumeOnAudioFocusGain = false
        needResumeOnCallStateIdle = false
        upReadAloudNotification()
        upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING)
        upMediaProgress()
        sessionStore.setStatus(ReadAloudSessionStatus.Playing)
        postEvent(EventBus.ALOUD_STATE, Status.PLAY)
    }

    abstract fun upSpeechRate(reset: Boolean = false)

    fun upTtsProgress(progress: Int) {
        val chapterPosition = progress.coerceAtLeast(0)
        if (chapterPosition < currentCharProgress) {
            // 进度回退(上一段/上一章等), 重置媒体进度锚点, 允许进度条跟随回退
            lastMediaSessionPositionMs = -1L
        }
        currentCharProgress = chapterPosition
        currentProgress = chapterPosition
        currentChapterIndex = textChapter?.chapter?.index ?: currentChapterIndex
        if (hasSpeechPlaybackQueue) {
            playbackQueue.cursorAt(chapterPosition)?.let { cursor ->
                val cue = playbackQueue.cues[cursor.cueIndex]
                sessionStore.updatePlayback(
                    ReadAloudPlaybackInfo(
                        chapterPosition = cue.chapterStart + cursor.offset,
                        chapterLength = playbackQueue.cues.lastOrNull()?.chapterEnd
                            ?: cue.chapterEnd,
                        text = cue.text,
                        engineName = cue.voice?.displayName.orEmpty(),
                        characterName = cue.characterId.orEmpty(),
                        roleType = cue.roleType,
                    )
                )
            }
        } else {
            val chapterLength = textChapter?.paragraphs?.lastOrNull()?.let { paragraph ->
                paragraph.chapterPosition + paragraph.text.length
            } ?: chapterPosition
            sessionStore.updatePlayback(
                ReadAloudPlaybackInfo(
                    chapterPosition = chapterPosition,
                    chapterLength = chapterLength.coerceAtLeast(1),
                    text = contentList.getOrNull(nowSpeak).orEmpty(),
                )
            )
        }
        postEvent(EventBus.TTS_PROGRESS, progress)
    }

    private fun prevP() {
        if (waitNewReadAloud) {
            return
        }
        if (hasSpeechPlaybackQueue) {
            val current = playbackCursor ?: ReadAloudPlaybackCursor(nowSpeak, paragraphStartPos)
            playbackQueue.previous(current)?.let { previous ->
                playStop()
                moveToPlaybackCursor(previous)
                play()
            } ?: run {
                toLast = true
                waitNewReadAloud = true
                ReadBook.moveToPrevChapter(true)
            }
            return
        }
        if (nowSpeak > 0) {
            playStop()
            do {
                nowSpeak--
                readAloudNumber -= contentList[nowSpeak].length + 1 + paragraphStartPos
                paragraphStartPos = 0
            } while (contentList[nowSpeak].matches(AppPattern.notReadAloudRegex))
            textChapter?.let {
                if (readAloudByPage) {
                    val paragraphs = it.getParagraphs(true)
                    if (!paragraphs[nowSpeak].isParagraphEnd) readAloudNumber++
                }
                if (readAloudNumber < it.getReadLength(pageIndex)) {
                    pageIndex--
                    if (sessionStore.state.value.followReadAloudPosition) {
                        withSpeechNavigation { ReadBook.moveToPrevPage() }
                    }
                }
            }
            upTtsProgress(readAloudNumber + 1)
            play()
        } else {
            toLast = true
            waitNewReadAloud = true
            ReadBook.moveToPrevChapter(true)
        }
    }

    private fun nextP() {
        if (waitNewReadAloud) {
            return
        }
        if (hasSpeechPlaybackQueue) {
            val current = playbackCursor ?: ReadAloudPlaybackCursor(nowSpeak, paragraphStartPos)
            playbackQueue.next(current)?.let { next ->
                playStop()
                moveToPlaybackCursor(next)
                play()
            } ?: run {
                waitNewReadAloud = true
                nextChapter()
            }
            return
        }
        if (nowSpeak < contentList.size - 1) {
            playStop()
            readAloudNumber += contentList[nowSpeak].length.plus(1) - paragraphStartPos
            paragraphStartPos = 0
            nowSpeak++
            textChapter?.let {
                if (readAloudByPage) {
                    val paragraphs = it.getParagraphs(true)
                    if (!paragraphs[nowSpeak].isParagraphEnd) readAloudNumber--
                }
                if (pageIndex + 1 < it.pageSize
                    && readAloudNumber >= it.getReadLength(pageIndex + 1)
                ) {
                    pageIndex++
                    if (sessionStore.state.value.followReadAloudPosition) {
                        withSpeechNavigation { ReadBook.moveToNextPage() }
                    }
                }
            }
            upTtsProgress(readAloudNumber + 1)
            play()
        } else {
            waitNewReadAloud = true
            nextChapter()
        }
    }

    private fun setTimer(minute: Int) {
        timeMinute = minute
        doDs()
    }

    private fun addTimer() {
        if (timeMinute == 180) {
            timeMinute = 0
        } else {
            timeMinute += 10
            if (timeMinute > 180) timeMinute = 180
        }
        doDs()
    }

    protected fun moveToPlaybackCursor(cursor: ReadAloudPlaybackCursor) {
        val cue = playbackQueue.cues[cursor.cueIndex]
        playbackCursor = cursor
        nowSpeak = cursor.cueIndex
        paragraphStartPos = cursor.offset
        readAloudNumber = cue.chapterStart
        textChapter?.let { chapter ->
            val targetPosition = cue.chapterStart + cursor.offset
            val targetPage = chapter.getPageIndexByCharIndex(targetPosition)
            // 页面脱离朗读位置（用户手动翻页）后不再驱动可见页面，仅推进朗读内部页游标
            val follow = sessionStore.state.value.followReadAloudPosition
            withSpeechNavigation {
                while (pageIndex < targetPage) {
                    pageIndex++
                    if (follow) ReadBook.moveToNextPage()
                }
                while (pageIndex > targetPage) {
                    pageIndex--
                    if (follow) ReadBook.moveToPrevPage()
                }
            }
            upTtsProgress(targetPosition + 1)
        }
        runCatching {
            val chapter = textChapter
            sessionStore.updatePlayback(
                ReadAloudPlaybackInfo(
                    chapterPosition = cue.chapterStart + cursor.offset,
                    chapterLength = chapter?.getContent()?.length ?: cue.chapterEnd,
                    text = cue.text,
                    engineName = cue.voice?.displayName.orEmpty(),
                    characterName = cue.characterId.orEmpty(),
                    roleType = cue.roleType,
                )
            )
        }
    }

    /**
     * 定时
     */
    @Synchronized
    private fun doDs() {
        sessionStore.updateTimer(timeMinute)
        postEvent(EventBus.READ_ALOUD_DS, timeMinute)
        upReadAloudNotification()
        dsJob?.cancel()
        dsJob = lifecycleScope.launch {
            while (isActive) {
                delay(60000)
                if (!pause) {
                    if (timeMinute >= 0) {
                        timeMinute--
                    }
                    if (timeMinute == 0) {
                        ReadAloud.stop(this@BaseReadAloudService)
                        sessionStore.updateTimer(timeMinute)
                        postEvent(EventBus.READ_ALOUD_DS, timeMinute)
                        break
                    }
                }
                sessionStore.updateTimer(timeMinute)
                postEvent(EventBus.READ_ALOUD_DS, timeMinute)
                upReadAloudNotification()
            }
        }
    }

    /**
     * 请求音频焦点
     * @return 音频焦点
     */
    fun requestFocus(): Boolean {
        if (AppConfig.ignoreAudioFocus) {
            return true
        }
        val requestFocus = MediaHelp.requestFocus(mFocusRequest)
        if (!requestFocus) {
            pauseReadAloud(false)
            toastOnUi("未获取到音频焦点")
        }
        return requestFocus
    }

    /**
     * 放弃音频焦点
     */
    private fun abandonFocus() {
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, mFocusRequest)
    }

    /**
     * 更新媒体状态
     */
    private fun upMediaSessionPlaybackState(state: Int) {
        val now = SystemClock.elapsedRealtime()
        val position = nextMediaSessionPositionMs(
            state = state,
            lastState = lastMediaSessionState,
            estimate = mediaProgressPositionMs(),
            lastPosition = lastMediaSessionPositionMs,
            nowElapsedRealtime = now,
            lastUpdateElapsedRealtime = lastMediaSessionUpdateElapsedMs,
        )
        if (state == lastMediaSessionState && position == lastMediaSessionPositionMs) {
            return
        }
        lastMediaSessionState = state
        lastMediaSessionPositionMs = position
        lastMediaSessionUpdateElapsedMs = now
        mediaSessionCompat.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(MediaHelp.MEDIA_SESSION_ACTIONS)
                // TTS 没有真实时间轴, 位置按字符进度与语速估算为毫秒时间
                .setState(state, position, if (state == PlaybackStateCompat.STATE_PLAYING) 1f else 0f)
                // 为系统媒体控件添加定时按钮
                .addCustomAction(
                    PlaybackStateCompat.CustomAction.Builder(
                        "ACTION_ADD_TIMER",
                        getString(R.string.set_timer),
                        R.drawable.ic_time_add_24dp
                    ).build()
                )
                .build()
        )
    }

    /**
     * 播放时每秒推送一次媒体进度, 系统媒体播放器进度条随朗读推进
     */
    private fun upMediaProgress() {
        upMediaProgressJob?.cancel()
        upMediaProgressJob = lifecycleScope.launch {
            while (isActive) {
                refreshMediaSessionPlaybackState()
                delay(1000)
            }
        }
    }

    private fun refreshMediaSessionPlaybackState() {
        if (pause) {
            upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PAUSED)
        } else {
            upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING)
        }
    }

    private fun mediaProgressPositionMs(): Long {
        val chapterLength = currentChapterLength()
        val charsPerSecond = currentCharsPerSecond()
        if (chapterLength <= 0 || charsPerSecond <= 0f) return 0L
        return estimatedReadAloudTimeMs(
            currentCharProgress.coerceIn(0, chapterLength),
            charsPerSecond,
        )
    }

    /**
     * 当前章节按语速估算的朗读总时长, TTS 没有真实时间轴
     */
    private fun mediaProgressDurationMs(): Long {
        val chapterLength = currentChapterLength()
        val charsPerSecond = currentCharsPerSecond()
        if (chapterLength <= 0 || charsPerSecond <= 0f) return 0L
        return estimatedReadAloudTimeMs(chapterLength, charsPerSecond)
    }

    private fun currentChapterLength(): Int =
        textChapter?.paragraphs?.lastOrNull()?.let { paragraph ->
            paragraph.chapterPosition + paragraph.text.length
        } ?: 0

    private fun currentCharsPerSecond(): Float =
        (ESTIMATED_CHARS_PER_SECOND * currentSpeechRate).coerceAtLeast(0.1f)

    /**
     * 更新媒体元数据, 用于车机蓝牙显示与系统进度条时间轴
     */
    protected fun upMediaMetadata() {
        val metadata = MediaMetadataCompat.Builder()
            .putText(MediaMetadataCompat.METADATA_KEY_TITLE, ReadBook.curTextChapter?.title ?: ReadBook.book?.name ?: "")
            .putText(MediaMetadataCompat.METADATA_KEY_ARTIST, ReadBook.book?.name ?: "")
            .putText(MediaMetadataCompat.METADATA_KEY_ALBUM, ReadBook.book?.author ?: "")
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, cover)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaProgressDurationMs())
            .build()
        mediaSessionCompat.setMetadata(metadata)
    }

    /**
     * 初始化MediaSession, 注册多媒体按钮
     */
    @SuppressLint("UnspecifiedImmutableFlag")
    private fun initMediaSession() {
        if (AppConfig.systemMediaControlCompatibilityChange) {
            mediaSessionCompat.setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    resumeReadAloud()
                }

                override fun onPause() {
                    pauseReadAloud()
                }

                override fun onSkipToNext() {
                    if (AppConfig.mediaButtonPerNext) {
                        nextChapter()
                    } else {
                        nextP()
                    }
                }

                override fun onSkipToPrevious() {
                    if (AppConfig.mediaButtonPerNext) {
                        prevChapter()
                    } else {
                        prevP()
                    }
                }

                override fun onStop() {
                    stopSelf()
                }

                override fun onCustomAction(action: String, extras: Bundle?) {
                    if (action == "ACTION_ADD_TIMER") addTimer()
                }

                override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                    return MediaButtonReceiver.handleIntent(
                        this@BaseReadAloudService, mediaButtonEvent
                    )
                }
            })
        } else {
            mediaSessionCompat.setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    resumeReadAloud()
                }

                override fun onPause() {
                    pauseReadAloud()
                }

                override fun onSkipToNext() {
                    if (AppConfig.mediaButtonPerNext) {
                        nextChapter()
                    } else {
                        nextP()
                    }
                }

                override fun onSkipToPrevious() {
                    if (AppConfig.mediaButtonPerNext) {
                        prevChapter()
                    } else {
                        prevP()
                    }
                }

                override fun onStop() {
                    stopSelf()
                }

                override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                    return MediaButtonReceiver.handleIntent(
                        this@BaseReadAloudService, mediaButtonEvent
                    )
                }
            })
        }
        mediaSessionCompat.setMediaButtonReceiver(
            broadcastPendingIntent<MediaButtonReceiver>(Intent.ACTION_MEDIA_BUTTON)
        )
        mediaSessionCompat.isActive = true
    }

    /**
     * 注册多媒体按钮监听
     */
    private fun initBroadcastReceiver() {
        val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(broadcastReceiver, intentFilter)
    }

    /**
     * 音频焦点变化
     */
    override fun onAudioFocusChange(focusChange: Int) {
        if (AppConfig.ignoreAudioFocus) {
            AppLog.put("忽略音频焦点处理(TTS)")
            return
        }
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (needResumeOnAudioFocusGain) {
                    AppLog.put("音频焦点获得,继续朗读")
                    resumeReadAloud()
                } else {
                    AppLog.put("音频焦点获得")
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                AppLog.put("音频焦点丢失,暂停朗读")
                pauseReadAloud()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                AppLog.put("音频焦点暂时丢失并会很快再次获得,暂停朗读")
                if (!pause) {
                    needResumeOnAudioFocusGain = true
                    pauseReadAloud(false)
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // 短暂丢失焦点，这种情况是被其他应用申请了短暂的焦点希望其他声音能压低音量（或者关闭声音）凸显这个声音（比如短信提示音），
                AppLog.put("音频焦点短暂丢失,不做处理")
            }
        }
    }

    private fun upReadAloudNotification() {
        upNotificationJob = execute {
            try {
                val notification = createNotification()
                notificationManager.notify(NotificationId.ReadAloudService, notification.build())
            } catch (e: Exception) {
                AppLog.put("创建朗读通知出错,${e.localizedMessage}", e, true)
            }
        }
    }

    private fun choiceMediaStyle(): androidx.media.app.NotificationCompat.MediaStyle {
        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(1, 2, 4)
        if (AppConfig.systemMediaControlCompatibilityChange) {
            //fix #4090 android 14 can not show play control in lock screen
            mediaStyle.setMediaSession(mediaSessionCompat.sessionToken)
        }
        return mediaStyle
    }

    private fun createNotification(): NotificationCompat.Builder {
        var nTitle: String = when {
            pause -> getString(R.string.read_aloud_pause)
            timeMinute > 0 -> getString(
                R.string.read_aloud_timer,
                timeMinute
            )

            else -> getString(R.string.read_aloud_t)
        }
        nTitle += ": ${ReadBook.book?.name}"
        var nSubtitle = ReadBook.curTextChapter?.title
        if (nSubtitle.isNullOrBlank())
            nSubtitle = getString(R.string.read_aloud_s)
        val builder = NotificationCompat
            .Builder(this, AppConst.channelIdReadAloud)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setSmallIcon(R.drawable.ic_volume_up)
            .setSubText(getString(R.string.read_aloud))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(nTitle)
            .setContentText(nSubtitle)
            .setContentIntent(
                activityPendingIntent<ReadBookActivity>("activity")
            )
            .setVibrate(null)
            .setSound(null)
            .setLights(0, 0, 0)
        builder.setLargeIcon(cover)
        // 按钮定义：上一章、播放、停止、下一章、定时
        builder.addAction(
            R.drawable.ic_skip_previous,
            getString(R.string.previous_chapter),
            aloudServicePendingIntent(IntentAction.prev)
        )
        if (pause) {
            builder.addAction(
                R.drawable.ic_play_24dp,
                getString(R.string.resume),
                aloudServicePendingIntent(IntentAction.resume)
            )
        } else {
            builder.addAction(
                R.drawable.ic_pause_24dp,
                getString(R.string.pause),
                aloudServicePendingIntent(IntentAction.pause)
            )
        }
        builder.addAction(
            R.drawable.ic_stop_black_24dp,
            getString(R.string.stop),
            aloudServicePendingIntent(IntentAction.stop)
        )
        builder.addAction(
            R.drawable.ic_skip_next,
            getString(R.string.next_chapter),
            aloudServicePendingIntent(IntentAction.next)
        )
        builder.addAction(
            R.drawable.ic_time_add_24dp,
            getString(R.string.set_timer),
            aloudServicePendingIntent(IntentAction.addTimer)
        )
        builder.setStyle(choiceMediaStyle())
        return builder
    }

    /**
     * 更新通知
     */
    override fun startForegroundNotification() {
        execute {
            try {
                val notification = createNotification()
                startForeground(NotificationId.ReadAloudService, notification.build())
            } catch (e: Exception) {
                AppLog.put("创建朗读通知出错,${e.localizedMessage}", e, true)
                //创建通知出错不结束服务就会崩溃,服务必须绑定通知
                stopSelf()
            }
        }
    }

    abstract fun aloudServicePendingIntent(actionStr: String): PendingIntent?

    open fun prevChapter() {
        toLast = false
        resumeReadAloudInternal()
        withSpeechNavigation { ReadBook.moveToPrevChapter(true, toLast = false) }
    }

    open fun nextChapter() {
        ReadBook.upReadTime()
        AppLog.putDebug("${ReadBook.curTextChapter?.chapter?.title} 朗读结束跳转下一章并朗读")
        resumeReadAloudInternal()
        if (!withSpeechNavigation { ReadBook.moveToNextChapter(true) }) {
            stopSelf()
        }
    }

    private fun initPhoneStateListener() {
        val needRegister = AppConfig.ignoreAudioFocus && AppConfig.pauseReadAloudWhilePhoneCalls
        if (needRegister && registeredPhoneStateListener) {
            return
        }
        if (needRegister) {
            registerPhoneStateListener(phoneStateListener)
        } else {
            unregisterPhoneStateListener(phoneStateListener)
        }
    }

    private fun unregisterPhoneStateListener(l: PhoneStateListener) {
        if (registeredPhoneStateListener) {
            withReadPhoneStatePermission {
                telephonyManager.listen(l, PhoneStateListener.LISTEN_NONE)
                registeredPhoneStateListener = false
            }
        }
    }

    private fun registerPhoneStateListener(l: PhoneStateListener) {
        withReadPhoneStatePermission {
            telephonyManager.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
            registeredPhoneStateListener = true
        }
    }

    private fun withReadPhoneStatePermission(block: () -> Unit) {
        try {
            block.invoke()
        } catch (_: SecurityException) {
            PermissionsCompat.Builder()
                .addPermissions(Permissions.READ_PHONE_STATE)
                .rationale(R.string.read_aloud_read_phone_state_permission_rationale)
                .onGranted {
                    try {
                        block.invoke()
                    } catch (_: SecurityException) {
                        LogUtils.d(TAG, "Grant read phone state permission fail.")
                    }
                }
                .request()
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    inner class ReadAloudPhoneStateListener : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            super.onCallStateChanged(state, phoneNumber)
            when (state) {
                TelephonyManager.CALL_STATE_IDLE -> {
                    if (needResumeOnCallStateIdle) {
                        AppLog.put("来电结束,继续朗读")
                        resumeReadAloud()
                    } else {
                        AppLog.put("来电结束")
                    }
                }

                TelephonyManager.CALL_STATE_RINGING -> {
                    if (!pause) {
                        AppLog.put("来电响铃,暂停朗读")
                        needResumeOnCallStateIdle = true
                        pauseReadAloud()
                    } else {
                        AppLog.put("来电响铃")
                    }
                }

                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    AppLog.put("来电接听,不做处理")
                }
            }
        }
    }

}

/**
 * 把已朗读字符数按每秒朗读字数估算为媒体播放器时间轴毫秒值
 */
internal fun estimatedReadAloudTimeMs(
    chars: Int,
    charsPerSecond: Float,
): Long {
    if (chars <= 0 || charsPerSecond <= 0f) return 0L
    return (chars * 1000.0 / charsPerSecond).toLong()
}

/**
 * 计算推送给系统媒体播放器的进度位置。
 * 播放中保持单调不后退: 字符估算值领先时跟随估算值, 落后时按墙钟推进,
 * 避免每秒重复推送同一估算值导致进度条来回跳变;
 * 暂停时冻结在系统插值的显示位置, 恢复时从冻结位置继续, 不跳变。
 */
internal fun nextMediaSessionPositionMs(
    state: Int,
    lastState: Int,
    estimate: Long,
    lastPosition: Long,
    nowElapsedRealtime: Long,
    lastUpdateElapsedRealtime: Long,
): Long {
    val playing = PlaybackStateCompat.STATE_PLAYING
    val paused = PlaybackStateCompat.STATE_PAUSED
    return when {
        state == paused && lastState == playing ->
            if (lastPosition < 0) estimate
            else lastPosition + (nowElapsedRealtime - lastUpdateElapsedRealtime)

        state == paused -> lastPosition.coerceAtLeast(0)

        state == playing && lastState == paused ->
            if (lastPosition < 0) estimate else lastPosition

        state == playing ->
            if (lastPosition < 0) estimate
            else maxOf(estimate, lastPosition + (nowElapsedRealtime - lastUpdateElapsedRealtime))

        else -> estimate
    }
}
