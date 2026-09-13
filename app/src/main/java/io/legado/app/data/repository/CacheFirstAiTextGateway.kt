package io.legado.app.data.repository

import io.legado.app.data.entities.AiArtifact
import io.legado.app.domain.gateway.AiArtifactGateway
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiCallSource
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerateResponse
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Oblivious Cache-First decorator for idempotent, prompt-invariant LLM responses.
 *
 * Keyed by (contentHash, promptHash, modelProfileId) in the existing `ai_artifacts`
 * table. Enabled only for a [cacheable] whitelist of prompt-invariant call sources
 * (sub-model / compress / outline / character) **and** only when the switch
 * [io.legado.app.help.config.AppConfig.aiResponseCacheEnabled] is on (default OFF).
 *
 * This deliberately does NOT cache chat / interactive main requests: volatile prompts
 * and tool rounds would make a local response cache stale (matching DSH's restraint of
 * "no dialogue response cache — rely on stable-prefix / provider KV cache instead").
 */
class CacheFirstAiTextGateway(
    private val delegate: AiTextGateway,
    private val artifactGateway: AiArtifactGateway,
) : AiTextGateway {

    /** Prompt-invariant call sources eligible for caching. */
    private val cacheable = setOf(
        AiCallSource.COMPRESS,
        AiCallSource.TOOL_SUBMODEL,
        AiCallSource.OUTLINE,
        AiCallSource.CHARACTER,
    )

    override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> {
        if (!shouldCache(request)) return delegate.generate(request)
        val key = artifactKey(request)
        val cached = lookup(key)
        if (cached != null) return Result.success(AiGenerateResponse(cached.output!!))
        return delegate.generate(request).also { result ->
            result.getOrNull()?.let { response ->
                if (response.text.isNotBlank()) upsert(key, AiArtifact.STATUS_SUCCESS, response.text)
            }
        }
    }

    override fun generateStream(request: AiGenerateRequest): Flow<AiStreamEvent> = flow {
        if (!shouldCache(request)) {
            delegate.generateStream(request).collect { emit(it) }
            return@flow
        }
        val key = artifactKey(request)
        val cached = lookup(key)
        if (cached != null) {
            emit(AiStreamEvent.Content(cached.output!!))
            emit(
                AiStreamEvent.Usage(
                    promptTokens = 0,
                    completionTokens = estimateCompletionTokens(cached.output.length),
                    cacheHitTokens = 0,
                )
            )
            return@flow
        }
        val fullText = StringBuilder()
        delegate.generateStream(request).collect { event ->
            when (event) {
                is AiStreamEvent.Content -> fullText.append(event.text)
                is AiStreamEvent.Reasoning -> Unit // not persisted; text-only cache
                else -> Unit
            }
            emit(event)
        }
        if (fullText.isNotBlank()) upsert(key, AiArtifact.STATUS_SUCCESS, fullText.toString())
    }

    override suspend fun fetchModels(provider: AiProviderConfig): Result<List<AiAvailableModel>> =
        delegate.fetchModels(provider)

    // ---- helpers -------------------------------------------------------------

    private fun shouldCache(request: AiGenerateRequest): Boolean =
        io.legado.app.help.config.AppConfig.aiResponseCacheEnabled &&
            request.tools.isEmpty() &&
            request.callMeta?.source in cacheable

    private suspend fun lookup(key: ArtifactKey): AiArtifact? {
        val hit = artifactGateway.getCachedArtifact(
            bookUrl = key.bookUrl,
            chapterIndex = null,
            taskType = key.taskType,
            contentHash = key.contentHash,
            promptHash = key.promptHash,
            modelProfileId = key.modelProfileId,
        )
        return hit?.takeIf { it.status == AiArtifact.STATUS_SUCCESS && !it.output.isNullOrBlank() }
    }

    private suspend fun upsert(key: ArtifactKey, status: Int, output: String) {
        val id = key.id()
        val now = System.currentTimeMillis()
        artifactGateway.upsertArtifact(
            AiArtifact(
                id = id,
                taskType = key.taskType,
                bookUrl = key.bookUrl,
                chapterIndex = null,
                contentHash = key.contentHash,
                promptHash = key.promptHash,
                modelProfileId = key.modelProfileId,
                status = status,
                output = output,
                schemaVersion = 1,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    private fun artifactKey(request: AiGenerateRequest): ArtifactKey {
        val source = request.callMeta?.source ?: AiCallSource.TOOL_SUBMODEL
        val convId = request.callMeta?.conversationId.orEmpty()
        // contentHash covers the actual request body (messages only — the parts whose
        // freeze the result depends on). promptHash covers the invariant prompt policy:
        // tools (must be empty to cache), params, and a manual version bump.
        val contentHash = MD5Utils.md5Encode(
            GSON.toJson(request.messages.map { mapOf("r" to it.role, "c" to it.content) })
        )
        val promptHash = MD5Utils.md5Encode(
            "aux_cache_v1|${GSON.toJson(request.params)}|${request.model.id}"
        )
        return ArtifactKey(
            bookUrl = convId,
            taskType = source,
            contentHash = contentHash,
            promptHash = promptHash,
            modelProfileId = request.model.id,
        )
    }
}

private data class ArtifactKey(
    val bookUrl: String,
    val taskType: String,
    val contentHash: String,
    val promptHash: String,
    val modelProfileId: String,
) {
    fun id(): String = "$taskType:$bookUrl:$contentHash:$promptHash:$modelProfileId"
}

private fun estimateCompletionTokens(chars: Int): Int = (chars * 0.6).toInt().coerceAtLeast(1)
