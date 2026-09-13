package io.legado.app.domain.usecase

import com.google.gson.JsonObject
import com.script.ScriptBindings
import com.script.rhino.RhinoScriptEngine
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

/**
 * Limited expression-only JS eval for AI tools.
 * Bindings: only `result` / `input` (strings). No java/source/cookie/cache.
 * Removes Packages / JavaImporter from the scope to block Java interop.
 */
class AiEvalJsUseCase {

    suspend fun evaluate(args: JsonObject): String = withContext(Dispatchers.Default) {
        try {
            val code = args.stringOrNull("code")?.trim().orEmpty()
            if (code.isBlank()) return@withContext """{"error":"code is required"}"""
            if (code.length > MAX_CODE_CHARS) {
                return@withContext """{"error":"code exceeds $MAX_CODE_CHARS characters"}"""
            }
            val input = args.stringOrNull("input")
                ?: args.stringOrNull("result")
                ?: ""
            val timeoutMs = (args.intOrNull("timeoutMs") ?: DEFAULT_TIMEOUT_MS)
                .coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)

            val evaluated = withTimeoutOrNull(timeoutMs.toLong()) {
                val bindings = ScriptBindings()
                bindings["result"] = input
                bindings["input"] = input
                val scope = RhinoScriptEngine.getRuntimeScope(bindings)
                sealAgainstJava(scope)
                RhinoScriptEngine.eval(code, scope, currentCoroutineContext())
            }

            if (evaluated == null) {
                """{"error":"JS evaluation timed out after ${timeoutMs}ms"}"""
            } else {
                val text = evaluated.toString()
                GSON.toJson(
                    mapOf(
                        "success" to true,
                        "result" to text.take(MAX_OUTPUT_CHARS),
                        "truncated" to (text.length > MAX_OUTPUT_CHARS),
                    ),
                )
            }
        } catch (e: Exception) {
            """{"error":"${escapeJson(e.message ?: e.javaClass.simpleName)}"}"""
        }
    }

    private fun sealAgainstJava(scope: Scriptable) {
        val names = listOf(
            "Packages", "JavaImporter", "JavaAdapter",
            "java", "javax", "android", "org", "com", "io", "okhttp3",
            "getClass", "importClass", "importPackage",
        )
        for (name in names) {
            runCatching { ScriptableObject.putProperty(scope, name, Undefined.instance) }
        }
        var proto = scope.prototype
        var depth = 0
        while (proto != null && depth++ < 4) {
            for (name in names) {
                runCatching { ScriptableObject.putProperty(proto, name, Undefined.instance) }
            }
            proto = proto.prototype
        }
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        val el = get(key) ?: return null
        if (el.isJsonNull) return null
        return runCatching { el.asString }.getOrNull()
    }

    private fun JsonObject.intOrNull(key: String): Int? {
        val el = get(key) ?: return null
        if (el.isJsonNull || !el.isJsonPrimitive) return null
        return runCatching { el.asInt }.getOrNull()
            ?: runCatching { el.asString.toIntOrNull() }.getOrNull()
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")

    companion object {
        const val DEFAULT_TIMEOUT_MS = 5_000
        const val MIN_TIMEOUT_MS = 500
        const val MAX_TIMEOUT_MS = 15_000
        const val MAX_CODE_CHARS = 8_000
        const val MAX_OUTPUT_CHARS = 8_000
    }
}
