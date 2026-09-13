package io.legado.app.data.repository

import android.util.Log
import io.legado.app.data.repository.ai.AiProviderRegistry
import io.legado.app.data.repository.ai.AnthropicHandler
import io.legado.app.data.repository.ai.OpenAiChatHandler
import io.legado.app.data.repository.ai.OpenAiResponsesHandler
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerateResponse
import io.legado.app.domain.model.AiProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class AiTextRepositoryImpl : AiTextGateway {

    companion object {
        private const val TAG = "AiText"
    }

    private val registry = AiProviderRegistry(
        handlers = listOf(
            OpenAiChatHandler(),
            OpenAiResponsesHandler(),
            AnthropicHandler()
        )
    )

    override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                registry.handlerFor(request.model.provider.protocol).generate(request)
            }.onFailure {
                Log.e(TAG, "generate failed: ${it.message}", it)
            }.mapCatching { it.getOrThrow() }
        }

    override fun generateStream(request: AiGenerateRequest): Flow<AiStreamEvent> = flow {
        registry.handlerFor(request.model.provider.protocol).stream(request) { emit(it) }
    }.catch { e ->
        Log.e(TAG, "generateStream failed: ${e.message}", e)
        throw e
    }.flowOn(Dispatchers.IO)

    override suspend fun fetchModels(provider: AiProviderConfig): Result<List<AiAvailableModel>> =
        withContext(Dispatchers.IO) {
            runCatching {
                registry.handlerFor(provider.protocol).fetchModels(provider).getOrThrow()
            }
        }
}
