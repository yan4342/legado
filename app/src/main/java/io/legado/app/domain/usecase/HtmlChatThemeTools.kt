package io.legado.app.domain.usecase

import com.google.gson.JsonObject
import io.legado.app.ui.ai.chat.html.AiChatHtmlThemeStore
import io.legado.app.utils.GSON
import splitties.init.appCtx

/** HTML chat theme tools: path read + edits/writes/deletePaths. */
class HtmlChatThemeTools(
    private val store: AiChatHtmlThemeStore = AiChatHtmlThemeStore(appCtx),
) {
    fun list(args: JsonObject): String {
        val themes = store.listThemes()
        return GSON.toJson(
            mapOf(
                "success" to true,
                "count" to themes.size,
                "themes" to themes.map {
                    mapOf(
                        "id" to it.id,
                        "name" to it.name,
                        "version" to it.version,
                        "builtin" to it.builtin,
                        "selected" to it.selected,
                        "dirty" to it.dirty,
                    )
                },
            ),
        )
    }

    fun read(args: JsonObject): String {
        val id = args.stringOrNull("themeId")?.trim().orEmpty()
            .ifBlank { args.stringOrNull("id")?.trim().orEmpty() }
        if (id.isBlank() || !store.exists(id)) {
            return errorJson("Theme not found. Pass themeId from list_html_chat_themes.")
        }
        val path = args.stringOrNull("path")?.trim().orEmpty()
        val manifest = store.readManifest(id)
        val themeMeta = mapOf(
            "id" to id,
            "name" to (manifest?.name ?: id),
            "version" to (manifest?.version ?: 1),
            "builtin" to !store.isUserTheme(id),
            "userOverlay" to store.isUserTheme(id),
            "paths" to store.listPackPaths(id),
            "allowedPaths" to listOf(
                "styles.css",
                "theme.json",
                "index.html",
                "app.js",
                "fragments/{slot}.html",
                "media/{file}",
            ),
            "allowedSlots" to AiChatHtmlThemeStore.KNOWN_SLOTS.toList(),
            "requiredIdsBySlot" to AiChatHtmlThemeStore.REQUIRED_IDS_BY_SLOT.mapValues { it.value.toList() },
            "noteIds" to "requiredIdsBySlot are default-shell expectations (warnings on patch, not hard fails).",
            "hasUserIndexHtml" to store.hasUserIndexHtml(id),
            "hasUserAppJs" to store.hasUserAppJs(id),
            "starters" to listOf(AiChatHtmlThemeStore.STARTER_BLANK),
        )
        if (path.isBlank()) {
            return GSON.toJson(
                mapOf(
                    "success" to true,
                    "theme" to themeMeta,
                    "note" to "Pass path to read file contents. Use offset (1-based line) + limit (default 400). " +
                        "Pass grep=<regex> with path to search within a file (case-insensitive). " +
                        "baseThemeId=blank seeds the blank starter. Binary media: zip only.",
                ),
            )
        }
        val grep = args.stringOrNull("grep")?.trim()?.takeIf { it.isNotBlank() }
        if (grep != null) {
            val maxResults = args.intOrNull("limit")?.coerceIn(1, 200) ?: 80
            return store.grepPackFile(id, path, grep, maxResults).fold(
                onSuccess = { result ->
                    GSON.toJson(
                        mapOf(
                            "success" to true,
                            "theme" to themeMeta,
                        ) + result,
                    )
                },
                onFailure = { errorJson(it.message ?: "grep failed") },
            )
        }
        val offset = args.intOrNull("offset") ?: 1
        val limit = args.intOrNull("limit") ?: 400
        return store.readPackFileLines(id, path, offset, limit).fold(
            onSuccess = { slice ->
                GSON.toJson(
                    mapOf(
                        "success" to true,
                        "theme" to themeMeta,
                        "path" to slice.path,
                        "content" to slice.content,
                        "totalLines" to slice.totalLines,
                        "offset" to slice.offset,
                        "limit" to slice.limit,
                        "truncated" to slice.truncated,
                        "note" to if (slice.truncated) {
                            "Truncated. Re-read with offset=${slice.offset + slice.content.lines().size} to continue."
                        } else {
                            ""
                        },
                    ),
                )
            },
            onFailure = { errorJson(it.message ?: "read failed") },
        )
    }

    fun edit(args: JsonObject): String {
        val id = args.stringOrNull("themeId")?.trim().orEmpty()
            .ifBlank { args.stringOrNull("id")?.trim().orEmpty() }
        if (id.isBlank()) return errorJson("themeId is required")
        val path = args.stringOrNull("path")?.trim()?.takeIf { it.isNotBlank() }
            ?: return errorJson("path is required")
        val oldString = args.stringOrNull("old_string")?.trimEnd()
            ?: args.stringOrNull("oldString")?.trimEnd()
        val newString = args.stringOrNull("new_string")
            ?: args.stringOrNull("newString")
            ?: ""
        if (oldString.isNullOrEmpty()) return errorJson("old_string must not be empty")
        val replaceAll = args.booleanOrNull("replace_all")
            ?: args.booleanOrNull("replaceAll")
            ?: false
        val skipShellValidation = args.booleanOrNull("skipShellValidation") == true
        return store.editFile(id, path, oldString, newString, replaceAll, skipShellValidation).fold(
            onSuccess = { result ->
                GSON.toJson(mapOf("success" to true) + result)
            },
            onFailure = { failureJson(it) },
        )
    }

    fun write(args: JsonObject): String {
        val id = args.stringOrNull("themeId")?.trim().orEmpty()
            .ifBlank { args.stringOrNull("id")?.trim().orEmpty() }
        if (id.isBlank()) return errorJson("themeId is required")
        val path = args.stringOrNull("path")?.trim()?.takeIf { it.isNotBlank() }
            ?: return errorJson("path is required")
        val contents = args.stringOrNull("contents")
            ?: args.stringOrNull("content")
            ?: return errorJson("contents is required")
        val baseThemeId = args.stringOrNull("baseThemeId")
            ?: args.stringOrNull("fromThemeId")
            ?: args.stringOrNull("templateId")
        val skipShellValidation = args.booleanOrNull("skipShellValidation") == true
        return store.writeUserFile(id, path, contents, baseThemeId, skipShellValidation).fold(
            onSuccess = { result ->
                GSON.toJson(mapOf("success" to true) + result)
            },
            onFailure = { errorJson(it.message ?: "write failed") },
        )
    }

    fun deleteFile(args: JsonObject): String {
        val id = args.stringOrNull("themeId")?.trim().orEmpty()
            .ifBlank { args.stringOrNull("id")?.trim().orEmpty() }
        if (id.isBlank()) return errorJson("themeId is required")
        val path = args.stringOrNull("path")?.trim()?.takeIf { it.isNotBlank() }
            ?: return errorJson("path is required")
        return store.deleteUserFile(id, path).fold(
            onSuccess = { result ->
                GSON.toJson(mapOf("success" to true) + result)
            },
            onFailure = { errorJson(it.message ?: "delete failed") },
        )
    }

    fun diff(args: JsonObject): String {
        val idA = args.stringOrNull("themeIdA")?.trim().orEmpty()
            .ifBlank { args.stringOrNull("idA")?.trim().orEmpty() }
        val idB = args.stringOrNull("themeIdB")?.trim().orEmpty()
            .ifBlank { args.stringOrNull("idB")?.trim().orEmpty() }
        if (idA.isBlank() || idB.isBlank()) return errorJson("themeIdA and themeIdB are required")
        if (!store.exists(idA)) return errorJson("Theme not found: $idA")
        if (!store.exists(idB)) return errorJson("Theme not found: $idB")
        val path = args.stringOrNull("path")?.trim()?.takeIf { it.isNotBlank() } ?: "styles.css"
        val versionA = args.intOrNull("versionA")
        val versionB = args.intOrNull("versionB")
        val manifestA = store.readManifest(idA)
        val manifestB = store.readManifest(idB)
        return store.diffPackFile(idA, idB, path, versionA, versionB).fold(
            onSuccess = { diff ->
                GSON.toJson(
                    mapOf(
                        "success" to true,
                        "themeA" to mapOf(
                            "id" to idA,
                            "name" to (manifestA?.name ?: idA),
                            "version" to (manifestA?.version ?: 1),
                        ),
                        "themeB" to mapOf(
                            "id" to idB,
                            "name" to (manifestB?.name ?: idB),
                            "version" to (manifestB?.version ?: 1),
                        ),
                    ) + diff,
                )
            },
            onFailure = { errorJson(it.message ?: "diff failed") },
        )
    }

    fun listVersions(args: JsonObject): String {
        val id = args.stringOrNull("themeId")?.trim().orEmpty()
            .ifBlank { args.stringOrNull("id")?.trim().orEmpty() }
        if (id.isBlank() || !store.exists(id)) {
            return errorJson("Theme not found. Pass themeId from list_html_chat_themes.")
        }
        val commits = store.listCommits(id)
        val manifest = store.readManifest(id)
        return GSON.toJson(
            mapOf(
                "success" to true,
                "themeId" to id,
                "currentVersion" to (manifest?.version ?: 1),
                "dirty" to (manifest?.dirty == true),
                "versions" to commits.map {
                    mapOf(
                        "version" to it.version,
                        "message" to it.message,
                        "timestamp" to it.timestamp,
                    )
                },
            ),
        )
    }

    fun manualCommit(args: JsonObject): String {
        val id = args.stringOrNull("themeId")?.trim().orEmpty()
            .ifBlank { args.stringOrNull("id")?.trim().orEmpty() }
        if (id.isBlank()) return errorJson("themeId is required")
        val message = args.stringOrNull("message") ?: args.stringOrNull("commitMessage")
        return store.commitTheme(id, message = message).fold(
            onSuccess = { info ->
                GSON.toJson(
                    mapOf(
                        "success" to true,
                        "theme" to mapOf(
                            "id" to info.id,
                            "name" to info.name,
                            "version" to info.version,
                            "selected" to info.selected,
                            "dirty" to info.dirty,
                        ),
                        "note" to "Committed v${info.version}. Files snapshotted, working tree clean.",
                    ),
                )
            },
            onFailure = { errorJson(it.message ?: "commit failed") },
        )
    }

    fun rollback(args: JsonObject): String {
        val id = args.stringOrNull("themeId")?.trim().orEmpty()
            .ifBlank { args.stringOrNull("id")?.trim().orEmpty() }
        if (id.isBlank()) return errorJson("themeId is required")
        val targetVersion = args.intOrNull("targetVersion")
            ?: return errorJson("targetVersion is required. Use list_html_chat_theme_versions first.")
        val message = args.stringOrNull("message")
        return store.rollbackToVersion(id, targetVersion, message).fold(
            onSuccess = { info ->
                GSON.toJson(
                    mapOf(
                        "success" to true,
                        "theme" to mapOf(
                            "id" to info.id,
                            "name" to info.name,
                            "version" to info.version,
                            "selected" to info.selected,
                            "dirty" to info.dirty,
                        ),
                        "note" to "Rolled back to v$targetVersion. Committed as v${info.version}.",
                    ),
                )
            },
            onFailure = { errorJson(it.message ?: "rollback failed") },
        )
    }

    fun setActive(args: JsonObject): String {
        val id = args.stringOrNull("themeId")?.trim().orEmpty()
            .ifBlank { args.stringOrNull("id")?.trim().orEmpty() }
        if (id.isBlank()) return errorJson("themeId is required")
        if (!store.setTheme(id)) return errorJson("Theme not found: $id")
        return GSON.toJson(mapOf("success" to true, "themeId" to id))
    }

    private fun failureJson(error: Throwable): String {
        val pe = error as? AiChatHtmlThemeStore.PatchOpException
        return if (pe != null) GSON.toJson(pe.toErrorMap()) else errorJson(error.message ?: "patch failed")
    }

    private fun errorJson(message: String): String =
        GSON.toJson(mapOf("success" to false, "error" to message))

    private fun JsonObject.jsonArrayOrNull(key: String): com.google.gson.JsonArray? {
        val el = get(key)?.takeIf { !it.isJsonNull } ?: return null
        if (el.isJsonArray) return el.asJsonArray
        if (el.isJsonPrimitive && el.asJsonPrimitive.isString) {
            return runCatching {
                com.google.gson.JsonParser.parseString(el.asString).asJsonArray
            }.getOrNull()
        }
        return null
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        get(key)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString

    private fun JsonObject.booleanOrNull(key: String): Boolean? {
        val el = get(key)?.takeIf { !it.isJsonNull && it.isJsonPrimitive } ?: return null
        return when {
            el.asJsonPrimitive.isBoolean -> el.asBoolean
            el.asJsonPrimitive.isString -> el.asString.equals("true", ignoreCase = true)
            else -> null
        }
    }

    private fun JsonObject.intOrNull(key: String): Int? {
        val el = get(key)?.takeIf { !it.isJsonNull && it.isJsonPrimitive } ?: return null
        return runCatching { el.asInt }.getOrNull()
    }
}
