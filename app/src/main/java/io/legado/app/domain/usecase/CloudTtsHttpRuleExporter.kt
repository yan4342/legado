package io.legado.app.domain.usecase

import io.legado.app.data.entities.HttpTTS
import io.legado.app.domain.model.readaloud.CloudTtsEngine
import io.legado.app.domain.model.readaloud.CloudTtsProviderType
import io.legado.app.domain.model.readaloud.CloudTtsVoiceConfig
import io.legado.app.domain.model.readaloud.profile
import io.legado.app.utils.GSON

/**
 * Converts a structured [CloudTtsEngine] into an [HttpTTS] rule that
 * [io.legado.app.service.HttpReadAloudService] can already play.
 *
 * Providers that need SigV4 or SSE reassembly are marked non-exportable.
 */
object CloudTtsHttpRuleExporter {

    fun isExportable(provider: CloudTtsProviderType): Boolean = when (provider) {
        // Only providers that return raw audio bytes (ExoPlayer-ready).
        CloudTtsProviderType.OpenAiSpeech,
        CloudTtsProviderType.AzureSpeech -> true
        // MiMo / Alibaba: SSE streams. Volcengine / Gemini: JSON+base64.
        // AWS Polly: SigV4 signing. Use Track 2 native cloud playback instead.
        CloudTtsProviderType.Mimo,
        CloudTtsProviderType.AlibabaCloud,
        CloudTtsProviderType.AwsPolly,
        CloudTtsProviderType.Volcengine,
        CloudTtsProviderType.GeminiTts -> false
    }

    fun export(
        engine: CloudTtsEngine,
        speakerId: String = "",
        voiceDisplayName: String = "",
        voiceConfig: CloudTtsVoiceConfig = CloudTtsVoiceConfig(),
        id: Long = System.currentTimeMillis(),
        redactSecrets: Boolean = false,
    ): HttpTTS {
        require(isExportable(engine.provider)) {
            "Provider ${engine.provider.profile.displayName} cannot be exported as HTTP TTS"
        }
        val voice = speakerId.ifBlank { defaultVoice(engine.provider) }
        val display = voiceDisplayName.ifBlank { voice }
        val name = buildString {
            append("[cloud:${engine.id}] ")
            append(engine.name)
            if (display.isNotBlank()) append(" · ").append(display)
        }
        val apiKey = if (redactSecrets) "***" else engine.apiKey
        val rule = when (engine.provider) {
            CloudTtsProviderType.OpenAiSpeech -> openAiRule(engine, voice, voiceConfig, apiKey)
            CloudTtsProviderType.AzureSpeech -> azureRule(engine, voice, voiceConfig, apiKey)
            else -> error("Unsupported export provider: ${engine.provider}")
        }
        return HttpTTS(
            id = id,
            name = name,
            url = rule.url,
            contentType = rule.contentType,
            header = rule.header,
            jsLib = GSON.toJson(
                mapOf(
                    "cloudEngineId" to engine.id,
                    "cloudProvider" to engine.provider.storageValue,
                    "speakerId" to voice,
                )
            ),
            lastUpdateTime = System.currentTimeMillis(),
        )
    }

    private fun defaultVoice(provider: CloudTtsProviderType): String = when (provider) {
        CloudTtsProviderType.OpenAiSpeech -> "alloy"
        CloudTtsProviderType.AzureSpeech -> "zh-CN-XiaoxiaoNeural"
        else -> ""
    }

    private fun openAiRule(
        engine: CloudTtsEngine,
        voice: String,
        config: CloudTtsVoiceConfig,
        apiKey: String,
    ): ExportedRule {
        val base = engine.baseUrl.ifBlank { "https://api.openai.com/v1" }.trimEnd('/')
        val endpoint = if (base.endsWith("/audio/speech")) base else "$base/audio/speech"
        val model = engine.model.ifBlank { engine.provider.profile.defaultModel }
        val format = config.format.ifBlank { "mp3" }
        val speedExpr = "Math.max(0.25, Math.min(4, (speakSpeed + 5) / 10))"
        val body = buildString {
            append("{")
            append("\"model\":\"$model\",")
            append("\"input\":\"{{speakText}}\",")
            append("\"voice\":\"$voice\",")
            append("\"response_format\":\"$format\",")
            append("\"speed\":{{String($speedExpr)}}")
            append("}")
        }
        val url = "$endpoint,{\"method\":\"POST\",\"body\":$body}"
        val header = GSON.toJson(
            mapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json",
            )
        )
        return ExportedRule(url, contentTypeFor(format), header)
    }

    private fun azureRule(
        engine: CloudTtsEngine,
        voice: String,
        config: CloudTtsVoiceConfig,
        apiKey: String,
    ): ExportedRule {
        val endpoint = engine.baseUrl.trim().trimEnd('/').ifBlank {
            require(engine.region.isNotBlank()) { "Azure region is required for export" }
            "https://${engine.region}.tts.speech.microsoft.com"
        }
        val locale = config.locale.ifBlank {
            voice.split('-').take(2).joinToString("-").ifBlank { "zh-CN" }
        }
        val format = config.format.ifBlank { "mp3" }
        val outputFormat = when (format.lowercase()) {
            "wav" -> "riff-24khz-16bit-mono-pcm"
            "ogg", "opus" -> "ogg-24khz-16bit-mono-opus"
            else -> "audio-24khz-48kbitrate-mono-mp3"
        }
        val rateExpr = "((speakSpeed + 5) / 10)"
        // SSML body as a JS string so speakText can be XML-escaped at request time.
        val bodyJs = buildString {
            append("\"<speak version=\\\"1.0\\\" xmlns=\\\"http://www.w3.org/2001/10/synthesis\\\" ")
            append("xmlns:mstts=\\\"https://www.w3.org/2001/mstts\\\" xml:lang=\\\"$locale\\\">")
            append("<voice name=\\\"$voice\\\">")
            append("<prosody rate=\\\"\" + $rateExpr + \"\\\">\" + speakText")
            append(".replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;') + \"")
            append("</prosody></voice></speak>\"")
        }
        val url = "$endpoint/cognitiveservices/v1,{\"method\":\"POST\",\"body\":$bodyJs,\"type\":\"xml\"}"
        val header = GSON.toJson(
            mapOf(
                "Ocp-Apim-Subscription-Key" to apiKey,
                "X-Microsoft-OutputFormat" to outputFormat,
                "Content-Type" to "application/ssml+xml",
                "User-Agent" to "Legado",
            )
        )
        return ExportedRule(url, contentTypeFor(format), header)
    }

    private fun contentTypeFor(format: String): String = when (format.lowercase()) {
        "wav", "pcm" -> "audio/wav"
        "ogg", "opus", "ogg_opus", "ogg_vorbis" -> "audio/ogg"
        "aac" -> "audio/aac"
        "flac" -> "audio/flac"
        else -> "audio/mpeg"
    }

    private data class ExportedRule(
        val url: String,
        val contentType: String,
        val header: String,
    )
}
