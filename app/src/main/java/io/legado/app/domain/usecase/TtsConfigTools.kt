package io.legado.app.domain.usecase

import com.google.gson.JsonObject
import io.legado.app.data.dao.HttpTTSDao
import io.legado.app.data.entities.HttpTTS
import io.legado.app.domain.gateway.CloudTtsEngineGateway
import io.legado.app.domain.model.readaloud.CloudTtsEngine
import io.legado.app.domain.model.readaloud.CloudTtsEngineJsonCodec
import io.legado.app.domain.model.readaloud.CloudTtsProviderType
import io.legado.app.domain.model.readaloud.CloudTtsSynthesisRequest
import io.legado.app.domain.model.readaloud.profile
import io.legado.app.help.config.AppConfig
import io.legado.app.help.readaloud.playback.CloudTtsAudioSynthesizer
import io.legado.app.help.readaloud.playback.HttpTtsStreamFetcher
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.model.ReadAloud
import io.legado.app.utils.GSON
import io.legado.app.utils.StringUtils
import io.legado.app.utils.fromJsonObject
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import splitties.init.appCtx

/**
 * Chat-mode tools for configuring Cloud TTS engines and HttpTTS rules.
 * Secrets are redacted on read; `***` / blank on update keeps existing credentials.
 */
class TtsConfigTools(
    private val cloudEngineGateway: CloudTtsEngineGateway,
    private val cloudSynthesizer: CloudTtsAudioSynthesizer,
    private val exportHttpTts: ExportCloudTtsAsHttpTtsUseCase,
    private val syncVoices: SyncVoicesFromAllEnginesUseCase,
    private val httpTtsDao: HttpTTSDao,
    private val httpFetcher: HttpTtsStreamFetcher = HttpTtsStreamFetcher(),
) {

    companion object {
        const val REDACTED = "***"
        const val MAX_TEST_TEXT_CHARS = 50
        private val PROVIDER_VALUES = CloudTtsProviderType.entries.map { it.storageValue }

        fun isRedactedOrBlank(value: String?): Boolean {
            val v = value?.trim().orEmpty()
            return v.isEmpty() || v == REDACTED
        }

        fun mergeSecret(incoming: String?, existing: String): String {
            return if (isRedactedOrBlank(incoming)) existing else incoming!!.trim()
        }

        fun redactSecret(value: String): String =
            if (value.isBlank()) "" else REDACTED

        /** Local-only secret fill for chat approval (never leave real keys in editable tool args). */
        data class LocalSecretFill(
            val needsApiKey: Boolean,
            val needsSecretKey: Boolean,
            val apiKeyLabel: String,
            val secretKeyLabel: String = "Secret Key",
            val provider: String?,
            val action: String,
            val engineLabel: String?,
            val prefilledApiKey: String = "",
            val prefilledSecretKey: String = "",
        ) {
            fun isSatisfied(apiKey: String, secretKey: String): Boolean =
                (!needsApiKey || apiKey.isNotBlank()) && (!needsSecretKey || secretKey.isNotBlank())
        }

        data class CloudPatchSecretPrep(
            val redactedArgs: JsonObject,
            val fill: LocalSecretFill,
        )

        fun resolveCloudPatchAction(args: JsonObject): String =
            args.stringOrNull("action")?.trim()?.lowercase().orEmpty().ifBlank {
                when {
                    args.has("json") && !args.get("json").isJsonNull -> "import_json"
                    listOf(
                        "name", "provider", "baseUrl", "apiKey", "secretKey", "region",
                        "appId", "model", "optionsJson", "options", "enabled", "engineId",
                    ).any { args.has(it) && !args.get(it).isJsonNull } -> "update"
                    else -> ""
                }
            }

        /**
         * For create / import_json (and update when model sent real secrets): strip secrets from args
         * into a local fill draft. Returns null when no dedicated secret UI is needed.
         */
        fun prepareCloudPatchSecretFill(args: JsonObject): CloudPatchSecretPrep? {
            val action = resolveCloudPatchAction(args)
            return when (action) {
                "create" -> prepareCreateSecretFill(args)
                "import_json" -> prepareImportSecretFill(args)
                "update" -> prepareUpdateSecretFill(args)
                else -> null
            }
        }

        fun injectCloudPatchSecrets(
            args: JsonObject,
            apiKey: String?,
            secretKey: String?,
        ): JsonObject {
            val out = args.deepCopy()
            if (!apiKey.isNullOrBlank()) out.addProperty("apiKey", apiKey.trim())
            if (!secretKey.isNullOrBlank()) out.addProperty("secretKey", secretKey.trim())
            if (resolveCloudPatchAction(out) == "import_json") {
                val raw = out.stringOrNull("json").orEmpty()
                if (raw.isNotBlank()) {
                    out.addProperty("json", patchEngineJsonSecrets(raw, apiKey, secretKey))
                }
            }
            return out
        }

        private fun prepareCreateSecretFill(args: JsonObject): CloudPatchSecretPrep {
            val providerRaw = args.stringOrNull("provider")?.trim().orEmpty()
            val provider = CloudTtsProviderType.entries.firstOrNull { it.storageValue == providerRaw }
            val profile = provider?.profile
            val apiIn = args.stringOrNull("apiKey")
            val secIn = args.stringOrNull("secretKey")
            val preApi = apiIn?.trim().orEmpty().takeUnless { isRedactedOrBlank(it) }.orEmpty()
            val preSec = secIn?.trim().orEmpty().takeUnless { isRedactedOrBlank(it) }.orEmpty()
            val needsSecret = provider == CloudTtsProviderType.AwsPolly
            val redacted = args.deepCopy().apply {
                if (has("apiKey")) addProperty("apiKey", if (preApi.isNotEmpty()) REDACTED else "")
                if (has("secretKey") || needsSecret) {
                    addProperty("secretKey", if (preSec.isNotEmpty()) REDACTED else "")
                }
            }
            return CloudPatchSecretPrep(
                redactedArgs = redacted,
                fill = LocalSecretFill(
                    needsApiKey = true,
                    needsSecretKey = needsSecret,
                    apiKeyLabel = profile?.apiKeyLabel ?: "API Key",
                    secretKeyLabel = if (needsSecret) "Secret Access Key" else "Secret Key",
                    provider = providerRaw.ifBlank { null },
                    action = "create",
                    engineLabel = args.stringOrNull("name")?.trim()?.ifBlank { null },
                    prefilledApiKey = preApi,
                    prefilledSecretKey = preSec,
                ),
            )
        }

        private fun prepareImportSecretFill(args: JsonObject): CloudPatchSecretPrep? {
            val raw = args.stringOrNull("json")?.trim().orEmpty()
            if (raw.isBlank()) return null
            val parsed = runCatching {
                com.google.gson.JsonParser.parseString(raw).asJsonObject
            }.getOrNull()
            val apiIn = parsed?.stringOrNull("apiKey") ?: args.stringOrNull("apiKey")
            val secIn = parsed?.stringOrNull("secretKey") ?: args.stringOrNull("secretKey")
            val preApi = apiIn?.trim().orEmpty().takeUnless { isRedactedOrBlank(it) }.orEmpty()
            val preSec = secIn?.trim().orEmpty().takeUnless { isRedactedOrBlank(it) }.orEmpty()
            val providerRaw = parsed?.stringOrNull("provider")?.trim()
                ?: args.stringOrNull("provider")?.trim().orEmpty()
            val provider = CloudTtsProviderType.entries.firstOrNull { it.storageValue == providerRaw }
            val needsSecret = provider == CloudTtsProviderType.AwsPolly
            val redactedJson = if (parsed != null) {
                parsed.deepCopy().apply {
                    addProperty("apiKey", if (preApi.isNotEmpty()) REDACTED else "")
                    if (has("secretKey") || needsSecret) {
                        addProperty("secretKey", if (preSec.isNotEmpty()) REDACTED else "")
                    }
                }.toString()
            } else {
                raw
            }
            val redacted = args.deepCopy().apply {
                addProperty("json", redactedJson)
                if (has("apiKey")) addProperty("apiKey", if (preApi.isNotEmpty()) REDACTED else "")
                if (has("secretKey")) addProperty("secretKey", if (preSec.isNotEmpty()) REDACTED else "")
            }
            return CloudPatchSecretPrep(
                redactedArgs = redacted,
                fill = LocalSecretFill(
                    needsApiKey = true,
                    needsSecretKey = needsSecret,
                    apiKeyLabel = provider?.profile?.apiKeyLabel ?: "API Key",
                    secretKeyLabel = if (needsSecret) "Secret Access Key" else "Secret Key",
                    provider = providerRaw.ifBlank { null },
                    action = "import_json",
                    engineLabel = parsed?.stringOrNull("name")?.trim()?.ifBlank { null }
                        ?: args.stringOrNull("name")?.trim()?.ifBlank { null },
                    prefilledApiKey = preApi,
                    prefilledSecretKey = preSec,
                ),
            )
        }

        private fun prepareUpdateSecretFill(args: JsonObject): CloudPatchSecretPrep? {
            val apiIn = args.stringOrNull("apiKey")
            val secIn = args.stringOrNull("secretKey")
            val preApi = apiIn?.trim().orEmpty().takeUnless { isRedactedOrBlank(it) }.orEmpty()
            val preSec = secIn?.trim().orEmpty().takeUnless { isRedactedOrBlank(it) }.orEmpty()
            if (preApi.isEmpty() && preSec.isEmpty()) return null
            val providerRaw = args.stringOrNull("provider")?.trim().orEmpty()
            val provider = CloudTtsProviderType.entries.firstOrNull { it.storageValue == providerRaw }
            val redacted = args.deepCopy().apply {
                if (preApi.isNotEmpty()) addProperty("apiKey", REDACTED)
                if (preSec.isNotEmpty()) addProperty("secretKey", REDACTED)
            }
            return CloudPatchSecretPrep(
                redactedArgs = redacted,
                fill = LocalSecretFill(
                    needsApiKey = preApi.isNotEmpty(),
                    needsSecretKey = preSec.isNotEmpty(),
                    apiKeyLabel = provider?.profile?.apiKeyLabel ?: "API Key",
                    secretKeyLabel = "Secret Key",
                    provider = providerRaw.ifBlank { null },
                    action = "update",
                    engineLabel = args.stringOrNull("name")?.trim()?.ifBlank { null }
                        ?: args.stringOrNull("engineId")?.trim()?.take(8),
                    prefilledApiKey = preApi,
                    prefilledSecretKey = preSec,
                ),
            )
        }

        private fun patchEngineJsonSecrets(
            raw: String,
            apiKey: String?,
            secretKey: String?,
        ): String {
            val obj = runCatching {
                com.google.gson.JsonParser.parseString(raw).asJsonObject
            }.getOrNull() ?: return raw
            if (!apiKey.isNullOrBlank()) obj.addProperty("apiKey", apiKey.trim())
            if (!secretKey.isNullOrBlank()) obj.addProperty("secretKey", secretKey.trim())
            return obj.toString()
        }

        fun parseDefaultEngine(raw: String?): Map<String, Any?> {
            if (raw.isNullOrBlank()) {
                return mapOf(
                    "kind" to "system",
                    "id" to "",
                    "label" to "系统默认",
                    "raw" to null,
                )
            }
            if (StringUtils.isNumeric(raw)) {
                return mapOf(
                    "kind" to "http",
                    "id" to raw,
                    "label" to "HttpTTS:$raw",
                    "raw" to raw,
                )
            }
            val item = GSON.fromJsonObject<SelectItem<String>>(raw).getOrNull()
            return if (item != null) {
                mapOf(
                    "kind" to if (item.value.isBlank()) "system" else "system_engine",
                    "id" to item.value,
                    "label" to item.title,
                    "raw" to raw,
                )
            } else {
                mapOf(
                    "kind" to "unknown",
                    "id" to "",
                    "label" to raw.take(40),
                    "raw" to raw,
                )
            }
        }

        fun cloudEngineSummaryMap(engine: CloudTtsEngine): Map<String, Any?> = mapOf(
            "id" to engine.id,
            "name" to engine.name,
            "provider" to engine.provider.storageValue,
            "providerLabel" to engine.provider.profile.displayName,
            "model" to engine.model,
            "region" to engine.region,
            "appId" to engine.appId.takeIf { it.isNotBlank() },
            "baseUrl" to engine.baseUrl.takeIf { it.isNotBlank() },
            "enabled" to engine.enabled,
            "exportable" to CloudTtsHttpRuleExporter.isExportable(engine.provider),
            "hasApiKey" to engine.apiKey.isNotBlank(),
            "hasSecretKey" to engine.secretKey.isNotBlank(),
        )

        fun cloudEngineDetailMap(engine: CloudTtsEngine): Map<String, Any?> =
            cloudEngineSummaryMap(engine) + mapOf(
                "apiKey" to redactSecret(engine.apiKey),
                "secretKey" to redactSecret(engine.secretKey),
                "optionsJson" to engine.optionsJson,
            )

        fun httpTtsSummaryMap(tts: HttpTTS): Map<String, Any?> = mapOf(
            "id" to tts.id,
            "name" to tts.name,
            "url" to tts.url.take(120),
            "contentType" to tts.contentType,
            "hasLogin" to !tts.loginUrl.isNullOrBlank(),
            "hasHeader" to !tts.header.isNullOrBlank(),
        )

        fun httpTtsDetailMap(tts: HttpTTS): Map<String, Any?> = mapOf(
            "id" to tts.id,
            "name" to tts.name,
            "url" to tts.url,
            "contentType" to tts.contentType,
            "concurrentRate" to tts.concurrentRate,
            "loginUrl" to tts.loginUrl,
            "loginUi" to tts.loginUi?.take(200),
            "header" to tts.header?.take(500),
            "loginCheckJs" to tts.loginCheckJs?.take(200),
            "enabledCookieJar" to tts.enabledCookieJar,
            "lastUpdateTime" to tts.lastUpdateTime,
        )

        fun summaryCloudPatch(args: JsonObject): String {
            val action = args.stringOrNull("action")?.trim()?.lowercase().orEmpty().ifBlank {
                if (args.has("json")) "import_json" else "update"
            }
            val target = args.stringOrNull("name")
                ?: args.stringOrNull("engineId")?.take(8)
                ?: args.stringOrNull("provider")
                ?: "?"
            return "Cloud TTS: $action ($target)"
        }

        fun summaryHttpPatch(args: JsonObject): String {
            val action = args.stringOrNull("action")?.trim()?.lowercase().orEmpty().ifBlank {
                if (args.has("json")) "import_json" else "update"
            }
            val target = args.stringOrNull("name")
                ?: args.longOrNull("httpTtsId")?.toString()
                ?: "?"
            return "HttpTTS: $action ($target)"
        }

        fun summarySetDefault(args: JsonObject): String {
            val kind = args.stringOrNull("kind") ?: "?"
            return "Set default TTS: $kind"
        }

        fun summaryExport(args: JsonObject): String {
            val target = args.stringOrNull("name")
                ?: args.stringOrNull("engineId")?.take(8)
                ?: "?"
            return "Export cloud TTS → HttpTTS ($target)"
        }

        fun previewDetailCloud(args: JsonObject): String = summaryCloudPatch(args)
        fun previewDetailHttp(args: JsonObject): String = summaryHttpPatch(args)
        fun previewDetailSetDefault(args: JsonObject): String = summarySetDefault(args)
        fun previewDetailExport(args: JsonObject): String = summaryExport(args)
    }

    suspend fun readTtsConfig(args: JsonObject = JsonObject()): String = withContext(Dispatchers.IO) {
        val cloud = cloudEngineGateway.getAll()
        val httpList = httpTtsDao.all
        GSON.toJson(
            buildMap {
                put("success", true)
                put("defaultEngine", parseDefaultEngine(AppConfig.ttsEngine))
                put("cloudEngines", cloud.map(::cloudEngineSummaryMap))
                put("httpTts", httpList.map(::httpTtsSummaryMap))
                put(
                    "exportableProviders",
                    CloudTtsProviderType.entries
                        .filter { CloudTtsHttpRuleExporter.isExportable(it) }
                        .map { it.storageValue },
                )
                put("providers", PROVIDER_VALUES)
                args.stringOrNull("engineId")?.let { id ->
                    cloud.firstOrNull { it.id == id }?.let { put("engine", cloudEngineDetailMap(it)) }
                }
            },
        )
    }

    suspend fun readCloudTtsEngine(args: JsonObject): String = withContext(Dispatchers.IO) {
        val engine = resolveCloudEngine(args)
            ?: return@withContext """{"error":"Cloud TTS engine not found; pass engineId or name"}"""
        GSON.toJson(
            mapOf(
                "success" to true,
                "engine" to cloudEngineDetailMap(engine),
            ),
        )
    }

    suspend fun patchCloudTtsEngine(args: JsonObject): String = withContext(Dispatchers.IO) {
        val action = args.stringOrNull("action")?.trim()?.lowercase().orEmpty().ifBlank {
            when {
                args.has("json") && !args.get("json").isJsonNull -> "import_json"
                hasCloudFieldPatch(args) -> "update"
                else -> ""
            }
        }
        when (action) {
            "create" -> createCloudEngine(args)
            "update" -> updateCloudEngine(args)
            "delete" -> deleteCloudEngine(args)
            "import_json" -> importCloudEngineJson(args)
            else -> """{"error":"Unknown action: $action. Use create|update|delete|import_json"}"""
        }
    }

    suspend fun listCloudTtsVoices(args: JsonObject): String = withContext(Dispatchers.IO) {
        val engine = resolveCloudEngine(args)
            ?: return@withContext """{"error":"Cloud TTS engine not found; pass engineId or name"}"""
        runCatching {
            val voices = cloudSynthesizer.fetchVoices(engine)
            GSON.toJson(
                mapOf(
                    "success" to true,
                    "engineId" to engine.id,
                    "engineName" to engine.name,
                    "count" to voices.size,
                    "voices" to voices.map {
                        mapOf(
                            "id" to it.id,
                            "displayName" to it.displayName,
                            "locale" to it.locale,
                            "gender" to it.gender,
                            "styles" to it.styles,
                            "sampleRate" to it.sampleRate,
                        )
                    },
                ),
            )
        }.getOrElse { """{"error":"${escapeJson(it.message ?: "list voices failed")}"}""" }
    }

    suspend fun readHttpTts(args: JsonObject): String = withContext(Dispatchers.IO) {
        val id = args.longOrNull("httpTtsId")
        val name = args.stringOrNull("name")
        if (id != null || !name.isNullOrBlank()) {
            val tts = resolveHttpTts(args)
                ?: return@withContext """{"error":"HttpTTS not found"}"""
            return@withContext GSON.toJson(
                mapOf("success" to true, "httpTts" to httpTtsDetailMap(tts)),
            )
        }
        val list = httpTtsDao.all
        GSON.toJson(
            mapOf(
                "success" to true,
                "count" to list.size,
                "httpTts" to list.map(::httpTtsSummaryMap),
            ),
        )
    }

    suspend fun patchHttpTts(args: JsonObject): String = withContext(Dispatchers.IO) {
        val action = args.stringOrNull("action")?.trim()?.lowercase().orEmpty().ifBlank {
            when {
                args.has("json") && !args.get("json").isJsonNull -> "import_json"
                hasHttpFieldPatch(args) -> "update"
                else -> ""
            }
        }
        when (action) {
            "create" -> createHttpTts(args)
            "update" -> updateHttpTts(args)
            "delete" -> deleteHttpTts(args)
            "import_json" -> importHttpTtsJson(args)
            else -> """{"error":"Unknown action: $action. Use create|update|delete|import_json"}"""
        }
    }

    suspend fun testTts(args: JsonObject): String = withContext(Dispatchers.IO) {
        val kind = args.stringOrNull("kind")?.trim()?.lowercase().orEmpty()
        val text = (args.stringOrNull("text") ?: "朗读测试").trim()
            .take(MAX_TEST_TEXT_CHARS)
            .ifBlank { "朗读测试" }
        when (kind) {
            "cloud" -> testCloud(args, text)
            "http" -> testHttp(args, text)
            else -> """{"error":"kind must be cloud or http"}"""
        }
    }

    fun setDefaultTtsEngine(args: JsonObject): String {
        val kind = args.stringOrNull("kind")?.trim()?.lowercase().orEmpty()
        val next = when (kind) {
            "system" -> {
                GSON.toJson(SelectItem("系统默认", ""))
            }
            "system_engine" -> {
                val id = args.stringOrNull("engineId")?.trim().orEmpty()
                if (id.isBlank()) return """{"error":"system_engine requires engineId (Android TTS package name)"}"""
                val label = args.stringOrNull("label")?.trim().orEmpty().ifBlank { id }
                GSON.toJson(SelectItem(label, id))
            }
            "http" -> {
                val id = args.longOrNull("httpTtsId")
                    ?: args.stringOrNull("httpTtsId")?.toLongOrNull()
                    ?: return """{"error":"http requires httpTtsId"}"""
                httpTtsDao.get(id)
                    ?: return """{"error":"HttpTTS $id not found"}"""
                id.toString()
            }
            else -> return """{"error":"kind must be system|system_engine|http"}"""
        }
        AppConfig.ttsEngine = next
        runCatching { ReadAloud.upReadAloudClass() }
        return GSON.toJson(
            mapOf(
                "success" to true,
                "defaultEngine" to parseDefaultEngine(AppConfig.ttsEngine),
            ),
        )
    }

    suspend fun exportCloudTtsAsHttpTts(args: JsonObject): String = withContext(Dispatchers.IO) {
        val engine = resolveCloudEngine(args)
            ?: return@withContext """{"error":"Cloud TTS engine not found; pass engineId or name"}"""
        if (!CloudTtsHttpRuleExporter.isExportable(engine.provider)) {
            return@withContext """{"error":"Provider ${engine.provider.storageValue} cannot be exported as HttpTTS; use native cloud playback"}"""
        }
        val speakerIds = args.stringListOrNull("speakerIds")
            ?: args.stringOrNull("speakerId")?.let { listOf(it) }
            ?: emptyList()
        runCatching {
            val result = exportHttpTts(
                engineId = engine.id,
                speakerIds = speakerIds,
                voiceNames = emptyMap(),
            )
            GSON.toJson(
                mapOf(
                    "success" to true,
                    "engineId" to engine.id,
                    "exportedCount" to result.exportedCount,
                    "httpTtsIds" to result.httpTtsIds,
                ),
            )
        }.getOrElse { """{"error":"${escapeJson(it.message ?: "export failed")}"}""" }
    }

    // ---- cloud internals ----

    private suspend fun createCloudEngine(args: JsonObject): String {
        val name = args.stringOrNull("name")?.trim().orEmpty()
        if (name.isBlank()) return """{"error":"create requires name"}"""
        val providerRaw = args.stringOrNull("provider")?.trim().orEmpty()
        if (providerRaw.isBlank()) return """{"error":"create requires provider (${PROVIDER_VALUES.joinToString()})"}"""
        val provider = CloudTtsProviderType.entries.firstOrNull { it.storageValue == providerRaw }
            ?: return """{"error":"Unsupported provider: $providerRaw"}"""
        val apiKey = args.stringOrNull("apiKey")?.trim().orEmpty()
        if (apiKey.isBlank() || apiKey == REDACTED) {
            return """{"error":"create requires apiKey"}"""
        }
        val now = System.currentTimeMillis()
        val engine = CloudTtsEngine(
            id = args.stringOrNull("engineId")?.trim()?.ifBlank { null } ?: UUID.randomUUID().toString(),
            name = name,
            provider = provider,
            baseUrl = args.stringOrNull("baseUrl")?.trim().orEmpty(),
            apiKey = apiKey,
            secretKey = args.stringOrNull("secretKey")?.trim().orEmpty().takeUnless { it == REDACTED }.orEmpty(),
            region = args.stringOrNull("region")?.trim().orEmpty(),
            appId = args.stringOrNull("appId")?.trim().orEmpty(),
            model = args.stringOrNull("model")?.trim().orEmpty().ifBlank { provider.profile.defaultModel },
            optionsJson = args.optionsJsonOrDefault(),
            enabled = args.booleanOrNull("enabled") ?: true,
            createdAt = now,
            updatedAt = now,
        )
        cloudEngineGateway.upsert(engine)
        val syncNote = runCatching {
            syncVoices(cloudEngineId = engine.id)
            "voices synced"
        }.getOrElse { "voice sync skipped: ${it.message}" }
        return GSON.toJson(
            mapOf(
                "success" to true,
                "action" to "create",
                "engine" to cloudEngineDetailMap(engine),
                "note" to syncNote,
            ),
        )
    }

    private suspend fun updateCloudEngine(args: JsonObject): String {
        val existing = resolveCloudEngine(args)
            ?: return """{"error":"update requires existing engineId or name"}"""
        val providerRaw = args.stringOrNull("provider")?.trim()
        val provider = when {
            providerRaw.isNullOrBlank() -> existing.provider
            else -> CloudTtsProviderType.entries.firstOrNull { it.storageValue == providerRaw }
                ?: return """{"error":"Unsupported provider: $providerRaw"}"""
        }
        val optionsJson = when {
            args.has("optionsJson") && !args.get("optionsJson").isJsonNull ->
                args.stringOrNull("optionsJson")?.trim()?.ifBlank { "{}" } ?: "{}"
            args.has("options") && args.get("options").isJsonObject ->
                GSON.toJson(args.getAsJsonObject("options"))
            else -> existing.optionsJson
        }
        if (!isValidOptionsJson(optionsJson)) {
            return """{"error":"optionsJson must be a JSON object"}"""
        }
        val updated = existing.copy(
            name = args.stringOrNull("name")?.trim()?.ifBlank { null } ?: existing.name,
            provider = provider,
            baseUrl = args.stringOrNull("baseUrl")?.trim() ?: existing.baseUrl,
            apiKey = mergeSecret(args.stringOrNull("apiKey"), existing.apiKey),
            secretKey = mergeSecret(args.stringOrNull("secretKey"), existing.secretKey),
            region = args.stringOrNull("region")?.trim() ?: existing.region,
            appId = args.stringOrNull("appId")?.trim() ?: existing.appId,
            model = args.stringOrNull("model")?.trim()?.ifBlank { null }
                ?: existing.model.ifBlank { provider.profile.defaultModel },
            optionsJson = optionsJson,
            enabled = args.booleanOrNull("enabled") ?: existing.enabled,
            updatedAt = System.currentTimeMillis(),
        )
        if (updated.apiKey.isBlank()) return """{"error":"apiKey cannot be empty"}"""
        cloudEngineGateway.upsert(updated)
        val syncNote = runCatching {
            syncVoices(cloudEngineId = updated.id)
            "voices synced"
        }.getOrElse { "voice sync skipped: ${it.message}" }
        return GSON.toJson(
            mapOf(
                "success" to true,
                "action" to "update",
                "engine" to cloudEngineDetailMap(updated),
                "note" to syncNote,
            ),
        )
    }

    private suspend fun deleteCloudEngine(args: JsonObject): String {
        val existing = resolveCloudEngine(args)
            ?: return """{"error":"delete requires engineId or name"}"""
        cloudEngineGateway.delete(existing)
        return GSON.toJson(
            mapOf(
                "success" to true,
                "action" to "delete",
                "engineId" to existing.id,
                "name" to existing.name,
            ),
        )
    }

    private suspend fun importCloudEngineJson(args: JsonObject): String {
        val json = args.stringOrNull("json")?.trim().orEmpty()
        if (json.isBlank()) return """{"error":"import_json requires json"}"""
        val parsed = CloudTtsEngineJsonCodec.parse(json).getOrElse {
            return """{"error":"${escapeJson(it.message ?: "invalid json")}"}"""
        }
        val existing = parsed.id?.let { cloudEngineGateway.get(it) }
        val apiKey = mergeSecret(parsed.apiKey, existing?.apiKey.orEmpty())
        val secretKey = mergeSecret(parsed.secretKey, existing?.secretKey.orEmpty())
        if (apiKey.isBlank()) return """{"error":"apiKey cannot be empty"}"""
        val provider = CloudTtsProviderType.fromStorage(parsed.provider)
        val now = System.currentTimeMillis()
        val engine = CloudTtsEngine(
            id = existing?.id ?: parsed.id ?: CloudTtsEngineJsonCodec.newId(),
            name = parsed.name,
            provider = provider,
            baseUrl = parsed.baseUrl,
            apiKey = apiKey,
            secretKey = secretKey,
            region = parsed.region,
            appId = parsed.appId,
            model = parsed.model.ifBlank { provider.profile.defaultModel },
            optionsJson = parsed.optionsJson,
            enabled = parsed.enabled,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        cloudEngineGateway.upsert(engine)
        val syncNote = runCatching {
            syncVoices(cloudEngineId = engine.id)
            "voices synced"
        }.getOrElse { "voice sync skipped: ${it.message}" }
        return GSON.toJson(
            mapOf(
                "success" to true,
                "action" to if (existing == null) "create" else "update",
                "engine" to cloudEngineDetailMap(engine),
                "note" to syncNote,
            ),
        )
    }

    private suspend fun resolveCloudEngine(args: JsonObject): CloudTtsEngine? {
        args.stringOrNull("engineId")?.trim()?.takeIf { it.isNotBlank() }?.let { id ->
            cloudEngineGateway.get(id)?.let { return it }
        }
        args.stringOrNull("name")?.trim()?.takeIf { it.isNotBlank() }?.let { name ->
            cloudEngineGateway.getAll().firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?.let { return it }
        }
        return null
    }

    private fun hasCloudFieldPatch(args: JsonObject): Boolean =
        listOf(
            "name", "provider", "baseUrl", "apiKey", "secretKey", "region",
            "appId", "model", "optionsJson", "options", "enabled", "engineId",
        ).any { args.has(it) && !args.get(it).isJsonNull }

    // ---- http internals ----

    private fun createHttpTts(args: JsonObject): String {
        val name = args.stringOrNull("name")?.trim().orEmpty()
        val url = args.stringOrNull("url")?.trim().orEmpty()
        if (name.isBlank()) return """{"error":"create requires name"}"""
        if (url.isBlank()) return """{"error":"create requires url"}"""
        val tts = HttpTTS(
            id = args.longOrNull("httpTtsId") ?: System.currentTimeMillis(),
            name = name,
            url = url,
            contentType = args.stringOrNull("contentType"),
            concurrentRate = args.stringOrNull("concurrentRate") ?: "0",
            loginUrl = args.stringOrNull("loginUrl"),
            loginUi = args.stringOrNull("loginUi"),
            header = args.stringOrNull("header"),
            loginCheckJs = args.stringOrNull("loginCheckJs"),
            enabledCookieJar = args.booleanOrNull("enabledCookieJar") ?: false,
            lastUpdateTime = System.currentTimeMillis(),
        )
        httpTtsDao.insert(tts)
        return GSON.toJson(
            mapOf(
                "success" to true,
                "action" to "create",
                "httpTts" to httpTtsDetailMap(tts),
            ),
        )
    }

    private fun updateHttpTts(args: JsonObject): String {
        val existing = resolveHttpTts(args)
            ?: return """{"error":"update requires existing id or name"}"""
        val name = args.stringOrNull("name")?.trim()?.ifBlank { null } ?: existing.name
        val url = args.stringOrNull("url")?.trim()?.ifBlank { null } ?: existing.url
        if (name.isBlank() || url.isBlank()) {
            return """{"error":"name and url cannot be empty"}"""
        }
        val updated = existing.copy(
            name = name,
            url = url,
            contentType = if (args.has("contentType")) args.stringOrNull("contentType") else existing.contentType,
            concurrentRate = args.stringOrNull("concurrentRate") ?: existing.concurrentRate,
            loginUrl = if (args.has("loginUrl")) args.stringOrNull("loginUrl") else existing.loginUrl,
            loginUi = if (args.has("loginUi")) args.stringOrNull("loginUi") else existing.loginUi,
            header = if (args.has("header")) args.stringOrNull("header") else existing.header,
            loginCheckJs = if (args.has("loginCheckJs")) args.stringOrNull("loginCheckJs") else existing.loginCheckJs,
            enabledCookieJar = args.booleanOrNull("enabledCookieJar") ?: existing.enabledCookieJar,
            lastUpdateTime = System.currentTimeMillis(),
        )
        httpTtsDao.insert(updated)
        if (AppConfig.ttsEngine == updated.id.toString()) {
            runCatching { ReadAloud.upReadAloudClass() }
        }
        return GSON.toJson(
            mapOf(
                "success" to true,
                "action" to "update",
                "httpTts" to httpTtsDetailMap(updated),
            ),
        )
    }

    private fun deleteHttpTts(args: JsonObject): String {
        val existing = resolveHttpTts(args)
            ?: return """{"error":"delete requires id or name"}"""
        httpTtsDao.delete(existing)
        return GSON.toJson(
            mapOf(
                "success" to true,
                "action" to "delete",
                "id" to existing.id,
                "name" to existing.name,
            ),
        )
    }

    private fun importHttpTtsJson(args: JsonObject): String {
        val json = args.stringOrNull("json")?.trim().orEmpty()
        if (json.isBlank()) return """{"error":"import_json requires json"}"""
        val tts = HttpTTS.fromJson(json).getOrElse {
            // try array first element
            HttpTTS.fromJsonArray(json).getOrElse {
                return """{"error":"${escapeJson(it.message ?: "invalid HttpTTS json")}"}"""
            }.firstOrNull() ?: return """{"error":"empty HttpTTS array"}"""
        }
        if (tts.name.isBlank() || tts.url.isBlank()) {
            return """{"error":"imported HttpTTS requires name and url"}"""
        }
        val toSave = tts.copy(lastUpdateTime = System.currentTimeMillis())
        httpTtsDao.insert(toSave)
        return GSON.toJson(
            mapOf(
                "success" to true,
                "action" to "import_json",
                "httpTts" to httpTtsDetailMap(toSave),
            ),
        )
    }

    private fun resolveHttpTts(args: JsonObject): HttpTTS? {
        args.longOrNull("httpTtsId")?.let { id ->
            httpTtsDao.get(id)?.let { return it }
        }
        args.stringOrNull("name")?.trim()?.takeIf { it.isNotBlank() }?.let { name ->
            httpTtsDao.all.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return it }
        }
        return null
    }

    private fun hasHttpFieldPatch(args: JsonObject): Boolean =
        listOf(
            "id", "name", "url", "contentType", "concurrentRate", "loginUrl",
            "loginUi", "header", "loginCheckJs", "enabledCookieJar",
        ).any { args.has(it) && !args.get(it).isJsonNull }

    // ---- test ----

    private suspend fun testCloud(args: JsonObject, text: String): String {
        val engine = resolveCloudEngine(args)
            ?: return """{"error":"Cloud TTS engine not found; pass engineId or name"}"""
        val voiceId = args.stringOrNull("voiceId")?.trim().orEmpty().ifBlank {
            runCatching { cloudSynthesizer.fetchVoices(engine).firstOrNull()?.id }.getOrNull().orEmpty()
        }
        if (voiceId.isBlank()) {
            return """{"error":"voiceId required (or engine has no voices)"}"""
        }
        val tmp = File(appCtx.cacheDir, "ai_tts_test_${System.currentTimeMillis()}.bin")
        return try {
            val ok = cloudSynthesizer.synthesize(
                engine = engine,
                request = CloudTtsSynthesisRequest(text = text, voiceId = voiceId),
                output = tmp,
            )
            val bytes = if (tmp.exists()) tmp.length() else 0L
            GSON.toJson(
                mapOf(
                    "success" to ok,
                    "kind" to "cloud",
                    "engineId" to engine.id,
                    "voiceId" to voiceId,
                    "bytes" to bytes,
                    "text" to text,
                    "error" to if (ok) null else "synthesis returned empty audio",
                ),
            )
        } catch (e: Exception) {
            GSON.toJson(
                mapOf(
                    "success" to false,
                    "kind" to "cloud",
                    "engineId" to engine.id,
                    "voiceId" to voiceId,
                    "error" to (e.message ?: "synthesis failed"),
                ),
            )
        } finally {
            tmp.delete()
        }
    }

    private suspend fun testHttp(args: JsonObject, text: String): String {
        val tts = resolveHttpTts(args)
            ?: return """{"error":"HttpTTS not found; pass id or name"}"""
        val speechRate = args.intOrNull("speechRate") ?: 5
        return try {
            val stream = httpFetcher.fetch(tts, text, speechRate)
                ?: return GSON.toJson(
                    mapOf(
                        "success" to false,
                        "kind" to "http",
                        "id" to tts.id,
                        "error" to "fetch returned null",
                    ),
                )
            stream.use { input ->
                var total = 0
                val buf = ByteArray(8 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    total += n
                    if (total >= 256 * 1024) break
                }
                GSON.toJson(
                    mapOf(
                        "success" to (total > 0),
                        "kind" to "http",
                        "id" to tts.id,
                        "name" to tts.name,
                        "bytes" to total,
                        "text" to text,
                        "error" to if (total > 0) null else "empty audio stream",
                    ),
                )
            }
        } catch (e: Exception) {
            GSON.toJson(
                mapOf(
                    "success" to false,
                    "kind" to "http",
                    "id" to tts.id,
                    "error" to (e.message ?: "http tts fetch failed"),
                ),
            )
        }
    }

    private fun JsonObject.optionsJsonOrDefault(): String {
        if (has("options") && get("options").isJsonObject) {
            return GSON.toJson(getAsJsonObject("options"))
        }
        return stringOrNull("optionsJson")?.trim()?.ifBlank { "{}" } ?: "{}"
    }

    private fun isValidOptionsJson(raw: String): Boolean =
        runCatching {
            com.google.gson.JsonParser.parseString(raw.ifBlank { "{}" }).isJsonObject
        }.getOrDefault(false)

    private fun escapeJson(msg: String): String =
        msg.replace("\\", "\\\\").replace("\"", "\\\"")
}

private fun JsonObject.stringOrNull(key: String): String? {
    val el = get(key) ?: return null
    if (el.isJsonNull) return null
    return el.asString
}

private fun JsonObject.intOrNull(key: String): Int? {
    val el = get(key) ?: return null
    if (el.isJsonNull) return null
    return when {
        el.isJsonPrimitive && el.asJsonPrimitive.isNumber -> el.asInt
        else -> el.asString.toIntOrNull()
    }
}

private fun JsonObject.longOrNull(key: String): Long? {
    val el = get(key) ?: return null
    if (el.isJsonNull) return null
    return when {
        el.isJsonPrimitive && el.asJsonPrimitive.isNumber -> el.asLong
        else -> el.asString.toLongOrNull()
    }
}

private fun JsonObject.booleanOrNull(key: String): Boolean? {
    val el = get(key) ?: return null
    if (el.isJsonNull) return null
    return when {
        el.isJsonPrimitive && el.asJsonPrimitive.isBoolean -> el.asBoolean
        else -> el.asString.toBooleanStrictOrNull()
    }
}

private fun JsonObject.stringListOrNull(key: String): List<String>? {
    val el = get(key) ?: return null
    if (el.isJsonNull) return null
    if (!el.isJsonArray) return null
    return el.asJsonArray.mapNotNull {
        if (it.isJsonPrimitive) it.asString.trim().takeIf { s -> s.isNotBlank() } else null
    }
}
