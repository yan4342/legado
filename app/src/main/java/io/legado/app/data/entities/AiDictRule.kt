package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.JsonObject
import io.legado.app.data.appDb
import io.legado.app.data.entities.Cache
import io.legado.app.data.repository.ai.aiOkHttpClient
import io.legado.app.data.repository.ai.readSseData
import io.legado.app.help.http.await
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.postJson
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import kotlin.coroutines.coroutineContext

/**
 * AI 字典规则
 */
@Entity(tableName = "aiDictRules")
data class AiDictRule(
    @PrimaryKey
    var name: String = "",
    var endpoint: String = "",
    var apiKey: String = "",
    var model: String = "deepseek-v4-flash",
    var systemPrompt: String = "",
    var userPromptTemplate: String = "",
    var temperature: Float = 0.7f,
    var maxTokens: Int = 512,
    @ColumnInfo(defaultValue = "1")
    var enabled: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    var sortNumber: Int = 0,
    @ColumnInfo(defaultValue = "")
    var extraJson: String = "",
) {

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + enabled.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is AiDictRule) {
            return name == other.name && enabled == other.enabled
        }
        return false
    }

    companion object {
        private const val CACHE_KEY_PREFIX = "aiDict"
        private const val CACHE_TTL_MS = 5 * 24 * 60 * 60 * 1000L // 5天
    }

    suspend fun search(word: String): String {
        // 检查缓存
        val cacheKey = "${CACHE_KEY_PREFIX}_${name}_$word"
        val now = System.currentTimeMillis()
        appDb.cacheDao.get(cacheKey, now)?.let { return it }

        val userContent = if (userPromptTemplate.isNotBlank()) {
            userPromptTemplate.replace("{{word}}", word)
        } else {
            "请解释词语: $word"
        }
        return requestAi(userContent, cacheKey, overrideSystemPrompt = null)
    }

    /**
     * 带阅读上下文的查询。相比 [search]，额外支持模板变量：
     * {{bookName}} 书名、{{chapterTitle}} 当前章节名、{{earlierMentions}} 前文出现记录、
     * {{bookMentions}} 全书出现记录（含前文/后文）。
     * 缓存键包含书名与章节，避免跨书/跨章节返回过期结果；
     * [networkExtended] 区分"联网补搜后"的结果，避免命中旧缓存导致联网白搜；
     * [presetKey] 区分不同预设生成的结果。
     */
    suspend fun searchWithContext(
        word: String,
        bookName: String? = null,
        chapterIndex: Int? = null,
        chapterTitle: String? = null,
        earlierMentions: String? = null,
        bookMentions: String? = null,
        networkExtended: Boolean = false,
        presetKey: String = "default",
        overrideSystemPrompt: String? = null,
        overrideUserPromptTemplate: String? = null,
    ): String {
        val cacheKey = buildCacheKey(word, bookName, chapterIndex, networkExtended, presetKey)
        val now = System.currentTimeMillis()
        appDb.cacheDao.get(cacheKey, now)?.let { return it }

        val userContent = buildUserContent(
            word, bookName, chapterIndex, chapterTitle, earlierMentions, bookMentions,
            template = overrideUserPromptTemplate ?: userPromptTemplate
        )
        return requestAi(userContent, cacheKey, overrideSystemPrompt)
    }

    /**
     * 流式查询：以 SSE 方式增量接收内容，[onPartial] 每次收到新文本都会携带累积的原文
     * （未转 HTML）。完成后将最终结果转 HTML 并写入缓存返回。参数含义同 [searchWithContext]。
     */
    suspend fun searchStream(
        word: String,
        onPartial: suspend (String) -> Unit,
        bookName: String? = null,
        chapterIndex: Int? = null,
        chapterTitle: String? = null,
        earlierMentions: String? = null,
        bookMentions: String? = null,
        networkExtended: Boolean = false,
        presetKey: String = "default",
        overrideSystemPrompt: String? = null,
        overrideUserPromptTemplate: String? = null,
    ): String {
        val cacheKey = buildCacheKey(word, bookName, chapterIndex, networkExtended, presetKey)
        val now = System.currentTimeMillis()
        appDb.cacheDao.get(cacheKey, now)?.let { return it }

        val userContent = buildUserContent(
            word, bookName, chapterIndex, chapterTitle, earlierMentions, bookMentions,
            template = overrideUserPromptTemplate ?: userPromptTemplate
        )
        return requestAiStream(userContent, cacheKey, overrideSystemPrompt, onPartial)
    }

    private fun buildCacheKey(
        word: String,
        bookName: String?,
        chapterIndex: Int?,
        networkExtended: Boolean,
        presetKey: String,
    ): String {
        val scope = if (networkExtended) "net" else "cached"
        return "${CACHE_KEY_PREFIX}_${name}_${presetKey}_${scope}_${bookName}_${chapterIndex}_$word"
    }

    private fun buildUserContent(
        word: String,
        bookName: String?,
        chapterIndex: Int?,
        chapterTitle: String?,
        earlierMentions: String?,
        bookMentions: String?,
        template: String,
    ): String {
        return if (template.isNotBlank()) {
            template
                .replace("{{word}}", word)
                .replace("{{bookName}}", bookName.orEmpty())
                .replace("{{chapterIndex}}", chapterIndex?.plus(1).toString())
                .replace("{{chapterTitle}}", chapterTitle.orEmpty())
                .replace("{{earlierMentions}}", earlierMentions.orEmpty())
                .replace("{{bookMentions}}", bookMentions.orEmpty())
        } else {
            "请解释词语: $word"
        }
    }

    private fun buildMessages(userContent: String, overrideSystemPrompt: String?): MutableList<Map<String, String>> {
        val messages = mutableListOf<Map<String, String>>()
        val system = overrideSystemPrompt ?: systemPrompt
        if (system.isNotBlank()) {
            messages.add(mapOf("role" to "system", "content" to system))
        }
        messages.add(mapOf("role" to "user", "content" to userContent))
        return messages
    }

    private fun buildRequestBody(messages: MutableList<Map<String, String>>, stream: Boolean): String {
        val requestBody = mutableMapOf<String, Any>(
            "model" to model,
            "messages" to messages,
            "temperature" to temperature,
            "max_tokens" to maxTokens,
            "stream" to stream,
        )
        if (extraJson.isNotBlank()) {
            @Suppress("UNCHECKED_CAST")
            val extra = GSON.fromJsonObject<Map<String, Any>>(extraJson).getOrNull()
            extra?.let { requestBody.putAll(it) }
        }
        return GSON.toJson(requestBody)
    }

    private suspend fun requestAi(userContent: String, cacheKey: String, overrideSystemPrompt: String?): String {
        val messages = buildMessages(userContent, overrideSystemPrompt)
        val jsonBody = buildRequestBody(messages, stream = false)

        val response = okHttpClient.newCallStrResponse {
            url(endpoint)
            addHeader("Authorization", "Bearer $apiKey")
            addHeader("Content-Type", "application/json")
            postJson(jsonBody)
        }
        val body = response.body
        if (body.isNullOrBlank()) {
            return "AI 接口返回为空 (HTTP ${response.raw.code})"
        }

        val analyzeRule = AnalyzeRule().setCoroutineContext(coroutineContext)
        val raw = analyzeRule.getString("$.choices[0].message.content", mContent = response.body)
        return raw.markdownToHtmlIfNeeded().also { result ->
            writeCache(cacheKey, result)
        }
    }

    private suspend fun requestAiStream(
        userContent: String,
        cacheKey: String,
        overrideSystemPrompt: String?,
        onPartial: suspend (String) -> Unit,
    ): String {
        val messages = buildMessages(userContent, overrideSystemPrompt)
        val jsonBody = buildRequestBody(messages, stream = true)

        val call = aiOkHttpClient.newCall(
            Request.Builder()
                .apply {
                    url(endpoint)
                    addHeader("Authorization", "Bearer $apiKey")
                    addHeader("Content-Type", "application/json")
                    postJson(jsonBody)
                }
                .build()
        )
        // 协程取消时同步断开连接，避免后台继续读取
        coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
        val response = call.await()
        try {
            if (!response.isSuccessful) {
                val errBody = response.body?.string().orEmpty()
                throw RuntimeException(
                    "AI 接口错误 (HTTP ${response.code}): ${errBody.take(200)}"
                )
            }
            val text = StringBuilder()
            withContext(Dispatchers.IO) {
                response.readSseData { data ->
                    coroutineContext.ensureActive()
                    val root = GSON.fromJsonObject<JsonObject>(data).getOrNull()
                    val choice = root?.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                    val content = choice?.getAsJsonObject("delta")?.get("content")
                        ?.takeIf { it.isJsonPrimitive }?.asString
                    if (!content.isNullOrEmpty()) {
                        text.append(content)
                        onPartial(text.toString())
                    }
                }
            }
            if (text.isEmpty()) {
                throw RuntimeException("AI 接口返回为空 (HTTP ${response.code})")
            }
            return text.toString().markdownToHtmlIfNeeded().also { result ->
                writeCache(cacheKey, result)
            }
        } finally {
            response.close()
        }
    }

    private suspend fun writeCache(cacheKey: String, result: String) {
        appDb.cacheDao.insert(
            Cache(
                key = cacheKey,
                value = result,
                deadline = System.currentTimeMillis() + CACHE_TTL_MS,
            )
        )
    }

    /**
     * 如果内容不含 HTML 标签，将基本 Markdown 转为 HTML，确保换行和加粗正确渲染
     */
    private fun String.markdownToHtmlIfNeeded(): String {
        if (contains(Regex("<[a-z]+[>\\s]"))) return this
        return this
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
            .replace(Regex("\\*(.+?)\\*"), "<i>$1</i>")
            .replace("\n\n", "<br><br>")
            .replace("\n", "<br>")
    }
}
