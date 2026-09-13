package io.legado.app.domain.usecase.ai

import io.legado.app.data.entities.AiModelProfile
import io.legado.app.data.entities.AiProviderProfile
import io.legado.app.data.entities.AiToolConfig
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.model.AiCallMeta
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiTaskType
import kotlin.coroutines.cancellation.CancellationException

typealias AiStreamPartialCallback = suspend (content: String, reasoning: String?) -> Unit

/** Sub-model profile id when delegation is enabled and not equal to the main chat model
 *  (intentional second-pass tools still resolve when equal). */
suspend fun AiToolConfig?.resolvedSubModelProfileId(
    aiProfileGateway: AiProfileGateway,
): String? {
    if (this == null) return null
    val mainId = aiProfileGateway.getTaskPreset(AiTaskType.CHAT)?.model?.id
    if (!shouldDelegateToSubModel(mainId)) return null
    return subModelProfileId
}

object AiStreamingTextHelper {

    suspend fun streamGenerate(
        aiTextGateway: AiTextGateway,
        request: AiGenerateRequest,
        onPartial: AiStreamPartialCallback? = null,
    ): Result<String> {
        val content = StringBuilder()
        val reasoning = StringBuilder()
        return try {
            aiTextGateway.generateStream(request).collect { event ->
                when (event) {
                    is AiStreamEvent.Content -> {
                        content.append(event.text)
                        onPartial?.invoke(content.toString(), reasoning.toString().ifBlank { null })
                    }
                    is AiStreamEvent.Reasoning -> {
                        reasoning.append(event.text)
                        onPartial?.invoke(content.toString(), reasoning.toString())
                    }
                    else -> Unit
                }
            }
            Result.success(content.toString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateWithToolConfig(
        aiTextGateway: AiTextGateway,
        aiProfileGateway: AiProfileGateway,
        userPrompt: String,
        config: AiToolConfig?,
        callSource: String,
        systemPrompt: String = "",
        onPartial: AiStreamPartialCallback? = null,
    ): Result<String> {
        val request = buildRequest(
            aiProfileGateway = aiProfileGateway,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            config = config,
            callSource = callSource,
        ) ?: return Result.failure(IllegalStateException("No chat model configured"))
        return streamGenerate(aiTextGateway, request, onPartial)
    }

    private suspend fun buildRequest(
        aiProfileGateway: AiProfileGateway,
        systemPrompt: String,
        userPrompt: String,
        config: AiToolConfig?,
        callSource: String,
    ): AiGenerateRequest? {
        val subModelId = config.resolvedSubModelProfileId(aiProfileGateway)
        if (subModelId != null) {
            val modelProfile = aiProfileGateway.getModel(subModelId) ?: return null
            val provider = aiProfileGateway.getProvider(modelProfile.providerId) ?: return null
            val maxChars = modelProfile.contextWindow.coerceAtLeast(4000)
            return AiGenerateRequest(
                model = modelProfile.toModelConfig(provider),
                messages = PromptRoleSplit.messages(systemPrompt, userPrompt, maxChars),
                params = AiGenerationParams(),
                callMeta = AiCallMeta(callSource),
            )
        }
        val preset = aiProfileGateway.getTaskPreset(AiTaskType.CHAT) ?: return null
        return AiGenerateRequest(
            model = preset.model,
            messages = PromptRoleSplit.messages(systemPrompt, userPrompt),
            params = preset.params,
            callMeta = AiCallMeta(callSource),
        )
    }

    private fun AiModelProfile.toModelConfig(provider: AiProviderProfile) = AiModelConfig(
        id = id,
        provider = AiProviderConfig(
            id = provider.id,
            name = provider.name,
            protocol = provider.protocol,
            baseUrl = provider.baseUrl,
            apiKey = provider.apiKey,
            modelsUrl = provider.modelsUrl,
            chatPath = provider.chatPath ?: "/chat/completions",
            responsesPath = provider.responsesPath ?: "/responses",
            messagesPath = provider.messagesPath ?: "/v1/messages",
            modelsPath = provider.modelsPath,
            headers = emptyMap(),
            customHeaders = emptyMap(),
        ),
        displayName = displayName,
        modelId = modelId,
        contextWindow = contextWindow,
        maxOutputTokens = maxOutputTokens,
    )
}
