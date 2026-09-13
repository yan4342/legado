package io.legado.app.data.repository.ai

import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.getProxyClient
import io.legado.app.help.http.okHttpClient
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * AI generation can legitimately spend several minutes before the next token arrives.
 * Keep the normal connect timeout from the shared client, but do not time out reads or
 * the whole call. Coroutine cancellation still cancels the underlying OkHttp call.
 *
 * All AI requests honour [AppConfig.aiChatProxy] (an explicit proxy), which on some
 * networks is required to reach the provider (e.g. emulators inherit the host VPN while
 * real devices connect directly). Clients are cached per proxy string so runtime changes
 * take effect immediately.
 */
internal val aiOkHttpClient: OkHttpClient
    get() = getAiOkHttpClient()

private val aiDirectClient: OkHttpClient by lazy {
    okHttpClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()
}

private val aiProxyClientCache: ConcurrentHashMap<String, OkHttpClient> = ConcurrentHashMap()

/** Same format as book-source `proxy`: `http://host:port` / `socks5://host:port` / `…@user@pass`. */
private val PROXY_PATTERN =
    Regex("""^(http|socks4|socks5)://[^:]+:\d{2,5}(@[^@]+@[^@]+)?$""", RegexOption.IGNORE_CASE)

internal fun getAiOkHttpClient(proxy: String? = null): OkHttpClient {
    val proxyCfg = proxy?.takeIf { it.isNotBlank() }
        ?: AppConfig.aiChatProxy.takeIf { it.isNotBlank() && AppConfig.aiChatProxyEnabled }
    if (proxyCfg.isNullOrBlank() || !PROXY_PATTERN.matches(proxyCfg)) {
        return aiDirectClient
    }
    return aiProxyClientCache.getOrPut(proxyCfg) {
        getProxyClient(proxyCfg).newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }
}
