package io.legado.app.domain.model.readaloud

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.utils.GSON
import java.util.UUID

/** Serialize / parse the full cloud TTS engine document for advanced JSON editing. */
object CloudTtsEngineJsonCodec {

    fun toPrettyJson(engine: CloudTtsEngine): String {
        val options = parseOptionsElement(engine.optionsJson)
        val root = JsonObject().apply {
            addProperty("id", engine.id)
            addProperty("name", engine.name)
            addProperty("provider", engine.provider.storageValue)
            addProperty("baseUrl", engine.baseUrl)
            addProperty("apiKey", engine.apiKey)
            addProperty("secretKey", engine.secretKey)
            addProperty("region", engine.region)
            addProperty("appId", engine.appId)
            addProperty("model", engine.model)
            add("options", options)
            addProperty("enabled", engine.enabled)
        }
        return GSON.toJson(root)
    }

    fun toPrettyJson(
        id: String?,
        name: String,
        provider: String,
        baseUrl: String,
        apiKey: String,
        secretKey: String,
        region: String,
        appId: String,
        model: String,
        optionsJson: String,
        enabled: Boolean,
    ): String {
        val draft = CloudTtsEngine(
            id = id ?: "",
            name = name,
            provider = CloudTtsProviderType.fromStorage(provider),
            baseUrl = baseUrl,
            apiKey = apiKey,
            secretKey = secretKey,
            region = region,
            appId = appId,
            model = model,
            optionsJson = optionsJson.ifBlank { "{}" },
            enabled = enabled,
        )
        return toPrettyJson(draft)
    }

    fun parse(raw: String): Result<ParsedEngineJson> = runCatching {
        val trimmed = raw.trim()
        require(trimmed.isNotBlank()) { "JSON 不能为空" }
        val element = JsonParser.parseString(trimmed)
        require(element.isJsonObject) { "根节点必须是 JSON 对象" }
        val obj = element.asJsonObject
        fun str(key: String, default: String = ""): String =
            obj.get(key)?.takeUnless { it.isJsonNull }?.asString?.trim() ?: default

        val optionsElement = obj.get("options")
        val optionsJson = when {
            optionsElement == null || optionsElement.isJsonNull -> "{}"
            optionsElement.isJsonObject -> GSON.toJson(optionsElement)
            optionsElement.isJsonPrimitive -> optionsElement.asString.ifBlank { "{}" }
            else -> error("options 必须是对象或 JSON 字符串")
        }
        require(parseOptionsElement(optionsJson).isJsonObject) {
            "options 不是合法 JSON 对象"
        }
        val provider = str("provider")
        require(provider.isNotBlank()) { "provider 不能为空" }
        require(CloudTtsProviderType.entries.any { it.storageValue == provider }) {
            "不支持的 provider：$provider"
        }
        val name = str("name")
        require(name.isNotBlank()) { "name 不能为空" }
        val apiKey = str("apiKey")
        require(apiKey.isNotBlank()) { "apiKey 不能为空" }
        val enabled = obj.get("enabled")?.takeUnless { it.isJsonNull }?.asBoolean ?: true
        ParsedEngineJson(
            id = str("id").ifBlank { null },
            name = name,
            provider = provider,
            baseUrl = str("baseUrl"),
            apiKey = apiKey,
            secretKey = str("secretKey"),
            region = str("region"),
            appId = str("appId"),
            model = str("model"),
            optionsJson = optionsJson,
            enabled = enabled,
        )
    }

    fun newId(): String = UUID.randomUUID().toString()

    private fun parseOptionsElement(optionsJson: String): JsonObject {
        val raw = optionsJson.ifBlank { "{}" }
        val element = runCatching { JsonParser.parseString(raw) }.getOrElse {
            return JsonObject()
        }
        return when {
            element.isJsonObject -> element.asJsonObject
            else -> JsonObject()
        }
    }

    data class ParsedEngineJson(
        val id: String?,
        val name: String,
        val provider: String,
        val baseUrl: String,
        val apiKey: String,
        val secretKey: String,
        val region: String,
        val appId: String,
        val model: String,
        val optionsJson: String,
        val enabled: Boolean,
    )
}
