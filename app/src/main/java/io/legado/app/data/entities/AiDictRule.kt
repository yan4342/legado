package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Cache
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.postJson
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
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
        val cacheKey = "${CACHE_KEY_PREFIX}_${name}_${word}"
        val now = System.currentTimeMillis()
        appDb.cacheDao.get(cacheKey, now)?.let { return it }

        val messages = mutableListOf<Map<String, String>>()
        if (systemPrompt.isNotBlank()) {
            messages.add(mapOf("role" to "system", "content" to systemPrompt))
        }
        val userContent = if (userPromptTemplate.isNotBlank()) {
            userPromptTemplate.replace("{{word}}", word)
        } else {
            "请解释词语: $word"
        }
        messages.add(mapOf("role" to "user", "content" to userContent))

        val requestBody = mutableMapOf<String, Any>(
            "model" to model,
            "messages" to messages,
            "temperature" to temperature,
            "max_tokens" to maxTokens
        )
        if (extraJson.isNotBlank()) {
            @Suppress("UNCHECKED_CAST")
            val extra = GSON.fromJsonObject<Map<String, Any>>(extraJson).getOrNull()
            extra?.let { requestBody.putAll(it) }
        }
        val jsonBody = GSON.toJson(requestBody)

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
        val result = raw.markdownToHtmlIfNeeded()
        // 写入缓存
        appDb.cacheDao.insert(Cache(key = cacheKey, value = result, deadline = now + CACHE_TTL_MS))
        return result
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
