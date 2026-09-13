package io.legado.app.help.readaloud.playback

import com.script.ScriptException
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.HttpTTS
import io.legado.app.exception.NoStackTraceException
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.printOnDebug
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Response
import org.mozilla.javascript.WrappedException
import java.io.InputStream
import java.net.ConnectException
import java.net.SocketTimeoutException

/**
 * Shared HTTP TTS audio fetcher used by [io.legado.app.service.HttpReadAloudService]
 * and [ReadAloudCueSynthesizer].
 */
class HttpTtsStreamFetcher {

    suspend fun fetch(
        httpTts: HttpTTS,
        speakText: String,
        speechRate: Int,
        onTransientError: (Exception) -> Boolean = { false },
    ): InputStream? {
        var errorNo = 0
        while (true) {
            try {
                val analyzeUrl = AnalyzeUrl(
                    httpTts.url,
                    speakText = speakText,
                    speakSpeed = speechRate,
                    source = httpTts,
                    readTimeout = 300 * 1000L,
                    coroutineContext = currentCoroutineContext(),
                )
                var response = analyzeUrl.getResponseAwait()
                currentCoroutineContext().ensureActive()
                val checkJs = httpTts.loginCheckJs
                if (checkJs?.isNotBlank() == true) {
                    response = analyzeUrl.evalJS(checkJs, response) as Response
                }
                response.headers["Content-Type"]?.let { rawType ->
                    val contentType = rawType.substringBefore(";")
                    val ct = httpTts.contentType
                    if (contentType == "application/json" || contentType.startsWith("text/")) {
                        throw NoStackTraceException(response.body.string())
                    } else if (ct?.isNotBlank() == true) {
                        if (!contentType.matches(ct.toRegex())) {
                            throw NoStackTraceException(
                                "TTS服务器返回错误：" + response.body.string()
                            )
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                return response.body.byteStream()
            } catch (e: Exception) {
                when (e) {
                    is CancellationException -> throw e
                    is ScriptException, is WrappedException -> {
                        AppLog.put("js错误\n${e.localizedMessage}", e, true)
                        e.printOnDebug()
                        throw e
                    }
                    is SocketTimeoutException, is ConnectException -> {
                        errorNo++
                        if (errorNo > 5) {
                            AppLog.put(
                                "tts超时或连接错误超过5次\n${e.localizedMessage}",
                                e,
                                true,
                            )
                            throw e
                        }
                    }
                    else -> {
                        errorNo++
                        AppLog.put("tts下载错误\n${e.localizedMessage}", e)
                        e.printOnDebug()
                        if (errorNo > 5 || onTransientError(e)) {
                            AppLog.put("TTS服务器连续错误，已暂停阅读。", e, true)
                            throw e
                        }
                        AppLog.put("TTS下载音频出错，使用无声音频代替。\n朗读文本：$speakText")
                        return null
                    }
                }
            }
        }
    }
}
