package io.legado.app.data.repository

import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerateResponse
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiUsage
import io.legado.app.help.ai.AiTokenEstimator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MeteringAiTextGateway(
    private val delegate: AiTextGateway,
    private val usageRepository: AiUsageRepository,
) : AiTextGateway {

    override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> {
        val startMs = System.currentTimeMillis()
        return delegate.generate(request).also { result ->
            val durationMs = System.currentTimeMillis() - startMs
            result.fold(
                onSuccess = { response ->
                    val (usage, estimated) = resolveUsage(request, response.usage, response.text)
                    usageRepository.record(
                        request = request,
                        usage = usage,
                        generatedChars = response.text.length,
                        durationMs = durationMs,
                        success = true,
                        estimated = estimated,
                    )
                },
                onFailure = {
                    usageRepository.record(
                        request = request,
                        usage = null,
                        generatedChars = 0,
                        durationMs = durationMs,
                        success = false,
                        estimated = false,
                    )
                },
            )
        }
    }

    override fun generateStream(request: AiGenerateRequest): Flow<AiStreamEvent> = flow {
        val startMs = System.currentTimeMillis()
        var generatedChars = 0
        var usage: AiUsage? = null
        try {
            delegate.generateStream(request).collect { event ->
                when (event) {
                    is AiStreamEvent.Content -> generatedChars += event.text.length
                    is AiStreamEvent.Reasoning -> generatedChars += event.text.length
                    is AiStreamEvent.Usage -> {
                        usage = AiUsage(
                            promptTokens = event.promptTokens,
                            completionTokens = event.completionTokens,
                            totalTokens = event.promptTokens + event.completionTokens,
                            cacheHitTokens = event.cacheHitTokens,
                        )
                    }
                    else -> Unit
                }
                emit(event)
            }
            recordStream(request, usage, generatedChars, startMs, success = true)
        } catch (e: Exception) {
            recordStream(request, usage, generatedChars, startMs, success = false)
            throw e
        }
    }

    override suspend fun fetchModels(provider: AiProviderConfig): Result<List<AiAvailableModel>> =
        delegate.fetchModels(provider)

    private fun recordStream(
        request: AiGenerateRequest,
        apiUsage: AiUsage?,
        generatedChars: Int,
        startMs: Long,
        success: Boolean,
    ) {
        val durationMs = System.currentTimeMillis() - startMs
        if (!success) {
            usageRepository.record(
                request = request,
                usage = apiUsage,
                generatedChars = generatedChars,
                durationMs = durationMs,
                success = false,
                estimated = apiUsage == null,
            )
            return
        }
        val (usage, estimated) = when {
            apiUsage != null && (apiUsage.promptTokens > 0 || apiUsage.completionTokens > 0) ->
                apiUsage to false
            else -> {
                val base = AiTokenEstimator.estimateUsage(request, "")
                // No output text kept; use CJK-ish 0.6 token/char heuristic from vendor docs.
                val completion = (generatedChars * 0.6).toInt().coerceAtLeast(if (generatedChars > 0) 1 else 0)
                base.copy(
                    completionTokens = completion,
                    totalTokens = base.promptTokens + completion,
                ) to true
            }
        }
        usageRepository.record(
            request = request,
            usage = usage,
            generatedChars = generatedChars,
            durationMs = durationMs,
            success = true,
            estimated = estimated,
        )
    }

    private fun resolveUsage(
        request: AiGenerateRequest,
        apiUsage: AiUsage?,
        outputText: String,
    ): Pair<AiUsage, Boolean> {
        if (apiUsage != null && (apiUsage.promptTokens > 0 || apiUsage.completionTokens > 0 || apiUsage.totalTokens > 0)) {
            return apiUsage to false
        }
        return AiTokenEstimator.estimateUsage(request, outputText) to true
    }
}
