package io.legado.app.data.repository.ai

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiBuiltinTool
import io.legado.app.domain.model.AiCapability
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerateResponse
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiToolCall
import io.legado.app.domain.model.AiToolDefinition
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.await
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.postJson
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenAiResponsesHandler : AiProtocolHandler {

    override val protocols = setOf(AiProtocol.OPENAI_RESPONSES)

    override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> =
        withContext(Dispatchers.IO) {
            runCatching { generateInternal(request) }
        }

    override suspend fun stream(
        request: AiGenerateRequest,
        emitEvent: suspend (AiStreamEvent) -> Unit
    ) {
        streamInternal(request, emitEvent)
    }

    override suspend fun fetchModels(provider: AiProviderConfig): Result<List<AiAvailableModel>> =
        withContext(Dispatchers.IO) {
            runCatching { fetchOpenAiCompatibleModels(provider) }
        }

    private suspend fun generateInternal(request: AiGenerateRequest): AiGenerateResponse {
        val provider = request.model.provider
        require(provider.baseUrl.isNotBlank() && provider.apiKey.isNotBlank() && request.model.modelId.isNotBlank()) {
            "OpenAI Responses configuration incomplete: baseUrl, apiKey, and model are required"
        }
        val keyRotator = KeyRotator(provider.apiKey)
        val params = request.params
        val nativeWebSearch = AiCapability.WEB_SEARCH in request.model.capabilities
        val body = mutableMapOf<String, Any?>(
            "model" to request.model.modelId,
            "input" to request.messages.toOpenAiResponsesInput()
        )
        val requestTools = request.tools.toOpenAiResponsesTools() +
            request.builtinTools.map { it.toResponsesTool() }
        if (requestTools.isNotEmpty()) body["tools"] = requestTools
        params.temperature?.let { body["temperature"] = it }
        params.maxOutputTokens?.let { body["max_output_tokens"] = it }
        params.topP?.let { body["top_p"] = it }
        if (hasReasoningCapability(request.model.capabilities)) {
            body["reasoning"] = buildMap<String, Any> {
                put("summary", "auto")
                if (params.reasoningLevel != AiReasoningLevel.AUTO) {
                    put("effort", params.reasoningLevel.toResponsesEffort(request.model))
                }
            }
        }
        body.mergeExtraParams(request.model.extraParams)

        return retryWithBackoff(maxAttempts = 3, keyRotator = keyRotator) {
            val response = aiOkHttpClient.newCallStrResponse {
                url(provider.baseUrl + provider.responsesPath)
                postJson(GSON.toJson(body))
                addHeaders(
                    provider.headers + provider.customHeaders + mapOf(
                        "Authorization" to "Bearer ${keyRotator.currentKey}",
                        "Content-Type" to "application/json"
                    )
                )
            }
            if (!response.isSuccessful()) {
                throw Exception(
                    response.buildHttpError() +
                        nativeSearchFailureHint(nativeWebSearch, response.code())
                )
            }
            val root = GSON.fromJson(response.body, JsonObject::class.java)
            val text = root?.getString("output_text") ?: root.extractResponsesOutputText()
            if (text.isNullOrBlank()) {
                throw Exception("Empty AI response")
            } else {
                val usage = root?.get("usage")?.asJsonObjectOrNull()?.let { usageObj ->
                    OpenAiUsage(
                        prompt_tokens = usageObj.getInt("input_tokens") ?: usageObj.getInt("prompt_tokens"),
                        completion_tokens = usageObj.getInt("output_tokens") ?: usageObj.getInt("completion_tokens"),
                        total_tokens = usageObj.getInt("total_tokens"),
                        prompt_tokens_details = null,
                        prompt_cache_hit_tokens = null,
                        prompt_cache_miss_tokens = null,
                    ).toDomain()
                }
                AiGenerateResponse(text = text, rawBody = response.body, usage = usage)
            }
        }
    }

    private suspend fun streamInternal(
        request: AiGenerateRequest,
        emitEvent: suspend (AiStreamEvent) -> Unit
    ) {
        val provider = request.model.provider
        require(provider.baseUrl.isNotBlank() && provider.apiKey.isNotBlank() && request.model.modelId.isNotBlank()) {
            "OpenAI Responses configuration incomplete: baseUrl, apiKey, and model are required"
        }
        val keyRotator = KeyRotator(provider.apiKey)
        val params = request.params
        val body = mutableMapOf<String, Any?>(
            "model" to request.model.modelId,
            "input" to request.messages.toOpenAiResponsesInput(),
            "stream" to true
        )
        val requestTools = request.tools.toOpenAiResponsesTools() +
            request.builtinTools.map { it.toResponsesTool() }
        if (requestTools.isNotEmpty()) body["tools"] = requestTools
        params.temperature?.let { body["temperature"] = it }
        params.maxOutputTokens?.let { body["max_output_tokens"] = it }
        params.topP?.let { body["top_p"] = it }
        if (hasReasoningCapability(request.model.capabilities)) {
            body["reasoning"] = buildMap<String, Any> {
                put("summary", "auto")
                if (params.reasoningLevel != AiReasoningLevel.AUTO) {
                    put("effort", params.reasoningLevel.toResponsesEffort(request.model))
                }
            }
        }
        body.mergeExtraParams(request.model.extraParams)

        // Native web search: when the model advertises WEB_SEARCH, the provider executes it
        // server-side. DeepSeek emits the search as a function_call named "web_search" (via
        // response.function_call_arguments.done) rather than a web_search_call item; normalize
        // its rawType so ToolTrace marks it builtin (visible, never gated behind approval).
        val nativeWebSearch = AiCapability.WEB_SEARCH in request.model.capabilities

        val response = retryWithBackoff(maxAttempts = 3, keyRotator = keyRotator) {
            aiOkHttpClient.newCallResponse {
                url(provider.baseUrl + provider.responsesPath)
                postJson(GSON.toJson(body))
                addHeaders(
                    provider.headers + provider.customHeaders + mapOf(
                        "Authorization" to "Bearer ${keyRotator.currentKey}",
                        "Content-Type" to "application/json"
                    )
                )
            }.also {
                if (!it.isSuccessful) {
                    // Dump the input structure to diagnose DeepSeek reasoning pass-back 400s.
                    val inputDump = request.messages.toOpenAiResponsesInput()
                        .joinToString(" | ") { it.entries.joinToString(",") { e -> "${e.key}=${e.value.toString().take(40)}" } }
                    Log.d(
                        "ResponsesBody",
                        "FAIL status=${it.code} input=[$inputDump] body=${GSON.toJson(body).take(1500)}",
                    )
                    throw Exception(
                        it.buildHttpError() +
                            nativeSearchFailureHint(nativeWebSearch, it.code)
                    )
                }
            }
        }
        // Citation fallback state: some Responses-compatible providers never populate the
        // web_search_call item's sources[] and only cite through url_citation annotations on
        // the final answer message. Collect those and, if no explicit sources arrived, emit
        // them as the builtin search's sources so the chat bubble still lists where the
        // answer came from.
        var lastBuiltinSearchId: String? = null
        var sawSearchSources = false
        var emittedCitationCount = 0
        var sawNativeSearch = false
        try {
            response.readSseData { data ->
                val root = data.toJsonObject() ?: throw Exception("Invalid OpenAI Responses stream chunk")
                root.extractApiErrorMessage()?.let { throw Exception(it) }
                when (root.getString("type")) {
                    "response.output_text.delta",
                    "response.refusal.delta" -> {
                        root.getString("delta")?.takeIf { it.isNotEmpty() }?.let {
                            emitEvent(AiStreamEvent.Content(it))
                        }
                    }
                    "response.reasoning_summary_text.delta",
                    "response.reasoning_text.delta" -> {
                        root.getString("delta")?.takeIf { it.isNotEmpty() }?.let {
                            emitEvent(AiStreamEvent.Reasoning(it))
                        }
                    }
                    "response.output_item.added",
                    "response.output_item.done" -> {
                        root.get("item")?.asJsonObjectOrNull()?.let { item ->
                            val itemType = item.getString("type").orEmpty()
                            if (itemType.contains("call") || itemType == "web_search_call") {
                                // DeepSeek may emit the native web_search builtin as a
                                // function_call/custom_tool_call named "web_search" instead of a
                                // web_search_call item. Treat any web_search-named call as builtin so
                                // it is not gated behind the approval panel (custom web_search tool is
                                // never offered when the model has the native capability).
                                val isNativeSearch = itemType == "web_search_call" ||
                                    item.getString("name") == "web_search" ||
                                    item.getString("server_label") == "web_search"
                                if (isNativeSearch || itemType.contains("web_search")) {
                                    Log.d("ResponsesBody", "output_item type=$itemType name=${item.getString("name")} label=${item.getString("server_label")} callId=${item.getString("call_id")}")
                                }
                                val searchItemData =
                                    if (isNativeSearch) extractWebSearchData(item) else null
                                if (searchItemData != null && extractSearchSources(item).isNotEmpty()) {
                                    sawSearchSources = true
                                }
                                if (isNativeSearch) {
                                    lastBuiltinSearchId =
                                        item.getString("call_id") ?: item.getString("id")
                                }
                                if (nativeWebSearch &&
                                    (isNativeSearch || itemType.contains("web_search"))
                                ) {
                                    sawNativeSearch = true
                                }
                                emitEvent(
                                    AiStreamEvent.ToolCallDelta(
                                        id = item.getString("call_id") ?: item.getString("id"),
                                        index = root.getString("output_index")?.toIntOrNull(),
                                        name = if (isNativeSearch) "web_search"
                                        else item.getString("name") ?: item.getString("server_label"),
                                        argumentsDelta = item.getString("arguments"),
                                        rawType = if (isNativeSearch) "web_search_call"
                                        else itemType,
                                        webSearchData = searchItemData,
                                    )
                                )
                            } else if (itemType == "message") {
                                // Providers that omit sources[] on the search item cite through
                                // url_citation annotations on the answer message instead. Re-emit
                                // the accumulated citations as the builtin search's sources; each
                                // emission replaces the trace's result, so the last (fullest) wins.
                                val citations = extractUrlCitations(item)
                                if (!sawSearchSources &&
                                    lastBuiltinSearchId != null &&
                                    citations.size > emittedCitationCount
                                ) {
                                    emittedCitationCount = citations.size
                                    Log.d(
                                        "ResponsesBody",
                                        "web_search sources missing; using ${citations.size} url_citation annotations as sources"
                                    )
                                    emitEvent(
                                        AiStreamEvent.ToolCallDelta(
                                            id = lastBuiltinSearchId ?: "web_search_citations",
                                            index = root.getString("output_index")?.toIntOrNull(),
                                            name = "web_search",
                                            argumentsDelta = null,
                                            rawType = "web_search_call",
                                            webSearchData = GSON.toJson(
                                                mapOf(
                                                    "queries" to emptyList<String>(),
                                                    "sources" to citations.values.toList(),
                                                )
                                            ),
                                        )
                                    )
                                }
                            }
                        }
                    }
                    "response.function_call_arguments.delta",
                    "response.mcp_call_arguments.delta",
                    "response.code_interpreter_call_code.delta",
                    "response.custom_tool_call_input.delta" -> {
                        emitEvent(
                            AiStreamEvent.ToolCallDelta(
                                id = root.getString("call_id"),
                                index = root.getString("output_index")?.toIntOrNull(),
                                name = null,
                                argumentsDelta = root.getString("delta"),
                                rawType = root.getString("type").orEmpty()
                            )
                        )
                    }
                    "response.function_call_arguments.done",
                    "response.mcp_call_arguments.done",
                    "response.code_interpreter_call_code.done",
                    "response.custom_tool_call_input.done",
                    "response.file_search_call.in_progress",
                    "response.file_search_call.searching",
                    "response.file_search_call.completed",
                    "response.web_search_call.in_progress",
                    "response.web_search_call.searching",
                    "response.web_search_call.completed" -> {
                        emitEvent(
                            AiStreamEvent.ToolCallDelta(
                                id = root.getString("item_id") ?: root.getString("call_id"),
                                index = root.getString("output_index")?.toIntOrNull(),
                                name = "web_search",
                                argumentsDelta = null,
                                // DeepSeek emits native web_search via function_call_arguments.done
                                // (rawType has no "web_search_call"); normalize so ToolTrace marks it
                                // builtin and never gates it behind approval.
                                rawType = if (nativeWebSearch) "web_search_call"
                                else root.getString("type").orEmpty(),
                                webSearchData = root.get("item")
                                    ?.asJsonObjectOrNull()
                                    ?.let { extractWebSearchData(it) },
                            )
                        )
                        if (nativeWebSearch) sawNativeSearch = true
                    }
                    "response.mcp_call.in_progress",
                    "response.mcp_call.completed",
                    "response.mcp_call.failed",
                    "response.mcp_list_tools.in_progress",
                    "response.mcp_list_tools.completed",
                    "response.mcp_list_tools.failed",
                    "response.code_interpreter_call.in_progress",
                    "response.code_interpreter_call.interpreting",
                    "response.code_interpreter_call.completed" -> {
                        emitEvent(
                            AiStreamEvent.ToolCallDelta(
                                id = root.getString("call_id"),
                                index = root.getString("output_index")?.toIntOrNull(),
                                name = null,
                                argumentsDelta = root.getString("arguments")
                                    ?: root.getString("code")
                                    ?: root.getString("input"),
                                rawType = root.getString("type").orEmpty()
                            )
                        )
                    }
                    "response.failed" -> {
                        throw Exception(root.extractResponseFailureMessage() ?: "OpenAI response failed")
                    }
                    "response.incomplete" -> {
                        throw Exception(root.extractResponseIncompleteMessage() ?: "OpenAI response incomplete")
                    }
                    "response.completed" -> {
                        root.get("response")?.asJsonObjectOrNull()?.get("usage")?.asJsonObjectOrNull()?.let { usageObj ->
                            OpenAiUsage(
                                prompt_tokens = usageObj.getInt("input_tokens") ?: usageObj.getInt("prompt_tokens"),
                                completion_tokens = usageObj.getInt("output_tokens") ?: usageObj.getInt("completion_tokens"),
                                total_tokens = usageObj.getInt("total_tokens"),
                                prompt_tokens_details = null,
                                prompt_cache_hit_tokens = null,
                                prompt_cache_miss_tokens = null,
                            ).toDomain()?.let {
                                emitEvent(AiStreamEvent.Usage(it.promptTokens, it.completionTokens, it.cacheHitTokens))
                            }
                        }
                    }
                }
            }
            if (nativeWebSearch && !sawNativeSearch) {
                Log.i(
                    "ResponsesBody",
                    "native web_search enabled but no search event arrived this turn" +
                        " — the model may not have needed to search, or the endpoint ignored the tool"
                )
            }
        } finally {
            response.close()
        }
    }

    private suspend fun fetchOpenAiCompatibleModels(provider: AiProviderConfig): List<AiAvailableModel> {
        require(provider.baseUrl.isNotBlank() && provider.apiKey.isNotBlank()) {
            "AI provider configuration incomplete: baseUrl and apiKey are required"
        }
        val keyRotator = KeyRotator(provider.apiKey)
        val modelsUrl = provider.modelsPath?.let { provider.baseUrl + it }
            ?: provider.modelsUrl
            ?: (provider.baseUrl + "/models")
        return retryWithBackoff(maxAttempts = 2, keyRotator = keyRotator) {
            val response = okHttpClient.newCallStrResponse {
                url(modelsUrl)
                addHeaders(
                    provider.headers + provider.customHeaders + mapOf(
                        "Authorization" to "Bearer ${keyRotator.currentKey}",
                        "Content-Type" to "application/json"
                    )
                )
            }
            if (!response.isSuccessful()) {
                throw Exception(response.buildHttpError())
            }
            val json = GSON.fromJson(response.body, OpenAiModelsResponse::class.java)
            json?.data.toAvailableModels()
        }
    }
}

// ---- Message & tool format converters ----

internal fun List<AiMessage>.toOpenAiResponsesInput(): List<Map<String, Any?>> {
    var idx = 0
    fun nextId() = "item_${idx++}"
    return flatMap { message ->
        when {
            message.role == AiMessageRole.TOOL -> listOf(
                mapOf(
                    "type" to "function_call_output",
                    "call_id" to message.toolCallId,
                    "output" to message.content,
                    "id" to nextId(),
                )
            )
            message.toolCalls.isNotEmpty() -> {
                // DeepSeek thinking mode requires the previous round's reasoning_text to be
                // passed back on tool-call rounds. OpenAI Responses format: `reasoning` input
                // item whose `content` is an array of reasoning_item_text blocks (a plain string
                // is rejected: "expected a sequence"). DeepSeek does not support summary/encrypted_content.
                val reasoningItem = message.reasoning.takeIf { it.isNotBlank() }?.let {
                    mapOf(
                        "type" to "reasoning",
                        "content" to listOf(
                            mapOf("type" to "reasoning_item_text", "text" to it),
                        ),
                        "id" to nextId(),
                    )
                }
                val textMessage = message.content.takeIf { it.isNotBlank() }?.let {
                    mapOf("role" to "assistant", "content" to it, "id" to nextId())
                }
                val toolCalls = message.toolCalls.map {
                    mapOf(
                        "type" to "function_call",
                        "call_id" to it.id,
                        "name" to it.name,
                        "arguments" to it.arguments,
                        "id" to nextId(),
                    )
                }
                listOfNotNull(reasoningItem, textMessage) + toolCalls
            }
            else -> listOf(
                mapOf(
                    "role" to message.role,
                    "content" to AiMessageContentSerializer.toOpenAiResponsesContent(message),
                    "id" to nextId(),
                ),
            )
        }
    }
}

internal fun List<AiToolDefinition>.toOpenAiResponsesTools(): List<Map<String, Any?>> {
    return map { tool ->
        mapOf(
            "type" to "function",
            "name" to tool.name,
            "description" to tool.description,
            "parameters" to tool.inputSchema
        )
    }
}

/** Map a builtin tool to the Responses `tools[]` entry: `type` plus optional extra fields. */
internal fun AiBuiltinTool.toResponsesTool(): Map<String, Any?> =
    mapOf("type" to type) + params

private fun JsonObject.extractResponseFailureMessage(): String? {
    val response = get("response")?.asJsonObjectOrNull() ?: return null
    val error = response.get("error")?.asJsonObjectOrNull()
    return error?.getString("message") ?: response.getString("status")
}

private fun JsonObject.extractResponseIncompleteMessage(): String? {
    val response = get("response")?.asJsonObjectOrNull() ?: return null
    val reason = response.get("incomplete_details")
        ?.asJsonObjectOrNull()
        ?.getString("reason")
    return reason?.let { "OpenAI response incomplete: $it" } ?: response.getString("status")
}

private fun JsonObject.extractResponsesOutputText(): String? {
    return get("output")
        ?.asJsonArrayOrNull()
        ?.flatMap { output ->
            output.asJsonObjectOrNull()
                ?.get("content")
                ?.asJsonArrayOrNull()
                ?.mapNotNull { content ->
                    content.asJsonObjectOrNull()
                        ?.takeIf { it.getString("type") == "output_text" }
                        ?.getString("text")
                }
                .orEmpty()
        }
        ?.joinToString("")
        ?.takeIf { it.isNotBlank() }
}

/**
 * Extract search queries + source links from a web_search_call item into compact JSON.
 * Shape: `{"query": <first>, "queries": [...], "sources": [{"title","url","publishedAt"?}]}`.
 */
internal fun extractWebSearchData(item: JsonObject): String? {
    val queries = extractSearchQueries(item)
    val sources = extractSearchSources(item)
    if (queries.isEmpty() && sources.isEmpty()) return null
    return GSON.toJson(
        mapOf(
            "query" to queries.firstOrNull(),
            "queries" to queries,
            "sources" to sources,
        )
    )
}

/**
 * All search terms of one native search: `search_queries[].text` first, then
 * `action.query`, then `action.queries[]` (string or `{text}` items).
 */
internal fun extractSearchQueries(item: JsonObject): List<String> {
    val fromDirect = jsonTextList(item.getAsJsonArray("search_queries"))
    if (fromDirect.isNotEmpty()) return fromDirect
    val action = item.get("action")?.asJsonObjectOrNull()
    action?.getString("query")?.trim()?.takeIf { it.isNotEmpty() }?.let { return listOf(it) }
    return jsonTextList(action?.getAsJsonArray("queries"))
}

/** Texts from a JSON array of strings or `{text}` objects; blank entries dropped. */
private fun jsonTextList(array: JsonArray?): List<String> {
    if (array == null) return emptyList()
    return array
        .mapNotNull { el ->
            el.asJsonObjectOrNull()?.getString("text")
                ?: el.takeIf { primitive -> primitive.isJsonPrimitive }?.asString
        }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

/** Source entries with an optional publication date when the provider supplies one. */
internal fun extractSearchSources(item: JsonObject): List<Map<String, String>> {
    return item.getAsJsonArray("sources")
        ?.mapNotNull { el ->
            val o = el.asJsonObjectOrNull() ?: return@mapNotNull null
            val url = o.getString("url").orEmpty().trim()
            if (url.isEmpty()) return@mapNotNull null
            val source = linkedMapOf(
                "title" to o.getString("title").orEmpty(),
                "url" to url,
            )
            sourceDate(o)?.let { source["publishedAt"] = it }
            source
        }
        .orEmpty()
}

/** First recognized publication-date field on a source object (Anthropic `page_age` et al.). */
private fun sourceDate(source: JsonObject): String? =
    listOf("page_age", "published_date", "publishedAt", "date")
        .firstNotNullOfOrNull { key -> source.getString(key)?.trim()?.takeIf { it.isNotEmpty() } }

/**
 * Collect `url_citation` annotations from an assistant message item's output_text parts,
 * deduped by URL. Some providers report answer citations only here and never as a
 * `sources[]` array on the web_search_call item.
 */
internal fun extractUrlCitations(item: JsonObject): LinkedHashMap<String, Map<String, String>> {
    val citations = LinkedHashMap<String, Map<String, String>>()
    val content = item.getAsJsonArray("content") ?: return citations
    for (partEl in content) {
        val part = partEl.asJsonObjectOrNull() ?: continue
        if (part.getString("type") != "output_text") continue
        val annotations = part.getAsJsonArray("annotations") ?: continue
        for (annEl in annotations) {
            val ann = annEl.asJsonObjectOrNull() ?: continue
            if (!ann.getString("type").equals("url_citation", ignoreCase = true)) continue
            val url = ann.getString("url").orEmpty().trim()
            if (url.isEmpty()) continue
            // First occurrence wins (mirrors the harness's citation-join semantics).
            if (!citations.containsKey(url)) {
                citations[url] = mapOf(
                    "title" to ann.getString("title").orEmpty(),
                    "url" to url,
                )
            }
        }
    }
    return citations
}

/** Appends actionable guidance when a native-search-capable model gets rejected mid-request. */
private fun nativeSearchFailureHint(enabled: Boolean, code: Int): String =
    if (enabled && code in 400..422) {
        "\nHint: this endpoint may not support the built-in web_search tool." +
            " Disable native web search for this model, or switch the model to the" +
            " custom web_search tool (AI settings → 联网搜索)."
    } else {
        ""
    }

private fun List<OpenAiModelItem>?.toAvailableModels(): List<AiAvailableModel> {
    return orEmpty()
        .mapNotNull { item ->
            val id = item.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            AiAvailableModel(
                id = id,
                name = item.display_name?.takeIf { it.isNotBlank() }
                    ?: item.displayName?.takeIf { it.isNotBlank() }
                    ?: item.name?.takeIf { it.isNotBlank() }
                    ?: id,
                contextWindow = item.context_window ?: item.contextWindow ?: 0,
                maxOutputTokens = item.max_tokens
                    ?: item.maxTokens
                    ?: item.max_output_tokens
                    ?: item.maxOutputTokens
                    ?: 0
            )
        }
        .distinctBy { it.id }
        .sortedBy { it.name.lowercase() }
}
