package io.legado.app.domain.usecase

import com.google.gson.JsonObject
import io.legado.app.domain.gateway.AiHtmlAppGateway
import io.legado.app.domain.gateway.Either
import io.legado.app.utils.GSON
import kotlinx.coroutines.flow.first

/**
 * HTML App 工具。源码编辑统一走 `read_file` / `edit_file` / `write_file` / `delete_file`
 * 的 `htmlapp://{appId}[/index.html]` 路径（经 FileToolRouter 分派），版本控制走独立工具：
 *
 * - `publish_html_app`    —— 创建（省略 appId）或整体重写+自动提交（传 appId）
 * - `commit_html_app`     —— 提交工作树（write/edit 后），升版本+快照
 * - `list_html_app_versions` —— 提交历史
 * - `rollback_html_app`   —— 从快照恢复
 *
 * 版本控制语义同主题包：工作树 = live（write/edit 立即生效），commit 才升版本。
 */
class HtmlAppTools(
    private val htmlAppGateway: AiHtmlAppGateway,
) {

    /** 创建或整体重写（自动提交）HTML App。返回 `{status, appId, title, version, updated}`。 */
    suspend fun publish(conversationId: String, args: JsonObject): String {
        if (conversationId.isBlank()) return errorJson("conversationId is required")
        val title = args.stringOrNull("title").orEmpty().trim().take(120)
        val html = args.stringOrNull("html").orEmpty()
        if (html.isBlank()) {
            return errorJson("""Missing or empty "html". Pass the complete HTML document with inline CSS and JS.""")
        }
        val appId = args.stringOrNull("appId")?.trim()?.takeIf { it.isNotBlank() }
        val app = htmlAppGateway.publish(
            title = title.ifBlank { "HTML App" },
            html = html,
            conversationId = conversationId,
            appId = appId,
        )
        val version = runCatching { htmlAppGateway.listVersions(app.id).firstOrNull()?.version ?: 1 }
            .getOrDefault(1)
        return GSON.toJson(
            mapOf(
                "status" to "published",
                "appId" to app.id,
                "title" to app.title,
                "version" to version,
                "updated" to (appId != null),
                "note" to "Use read_file/edit_file/write_file with htmlapp://${app.id}/index.html to iterate. Commit via commit_html_app.",
            ),
        )
    }

    /** 提交工作树（write/edit 之后）。无改动返回提示。 */
    suspend fun commit(conversationId: String, args: JsonObject): String {
        if (conversationId.isBlank()) return errorJson("conversationId is required")
        val appId = args.stringOrNull("appId")?.trim().orEmpty()
        if (appId.isBlank()) return errorJson("""Missing "appId". Pass the appId of an existing HTML app.""")
        val message = args.stringOrNull("message")?.trim()
        val committed = htmlAppGateway.commit(appId, message)
            ?: return errorJson("Nothing to commit (working tree clean) for $appId")
        val version = runCatching { htmlAppGateway.listVersions(appId).firstOrNull()?.version ?: 1 }
            .getOrDefault(1)
        return GSON.toJson(
            mapOf(
                "status" to "committed",
                "appId" to committed.id,
                "version" to version,
                "message" to "HTML app committed (working tree snapshot).",
            ),
        )
    }

    /** 回滚 HTML App 到某已提交版本。 */
    suspend fun rollback(conversationId: String, args: JsonObject): String {
        if (conversationId.isBlank()) return errorJson("conversationId is required")
        val appId = args.stringOrNull("appId")?.trim().orEmpty()
        if (appId.isBlank()) return errorJson("""Missing "appId". Pass the appId of an existing HTML app.""")
        val version = args.intOrNull("version") ?: return errorJson("""Missing "version". Pass a committed version number.""")
        val app = htmlAppGateway.rollback(appId, version)
            ?: return errorJson("Rollback failed: app or snapshot v$version not found")
        val currentVersion = runCatching { htmlAppGateway.listVersions(appId).firstOrNull()?.version ?: 1 }
            .getOrDefault(1)
        return GSON.toJson(
            mapOf(
                "status" to "rolled_back",
                "appId" to app.id,
                "version" to currentVersion,
                "message" to "HTML app rolled back to v$version",
            ),
        )
    }

    /** 提交历史。 */
    suspend fun versions(conversationId: String, args: JsonObject): String {
        val appId = args.stringOrNull("appId")?.trim().orEmpty()
        if (appId.isBlank()) return errorJson("""Missing "appId". Pass the appId of an existing HTML app.""")
        val app = htmlAppGateway.getById(appId)
            ?: return errorJson("HTML app not found: $appId")
        if (app.conversationId != conversationId) return errorJson("HTML app not in this conversation")
        val versions = htmlAppGateway.listVersions(appId)
        return GSON.toJson(
            mapOf(
                "success" to true,
                "appId" to app.id,
                "title" to app.title,
                "versions" to versions.map { mapOf("version" to it.version, "message" to it.message, "timestamp" to it.timestamp) },
            ),
        )
    }

    /** 列当前会话的 HTML Apps。 */
    suspend fun list(conversationId: String): String {
        if (conversationId.isBlank()) return GSON.toJson(mapOf("success" to true, "apps" to emptyList<Any>()))
        val apps = htmlAppGateway.observeByConversation(conversationId).first()
        val result = apps.map { app ->
            mapOf(
                "appId" to app.id,
                "title" to app.title,
                "version" to (runCatching { htmlAppGateway.listVersions(app.id).firstOrNull()?.version ?: 1 }.getOrDefault(1)),
            )
        }
        return GSON.toJson(mapOf("success" to true, "apps" to result))
    }

    // ---- FileToolRouter htmlapp:// 后端（read/edit/write/delete 经统一 file CRUD） ----

    /** read_file: `htmlapp://{appId}` → 元信息；`htmlapp://{appId}/index.html` → 按行切片。 */
    suspend fun read(appId: String, args: JsonObject): String {
        val app = htmlAppGateway.getById(appId) ?: return errorJson("HTML app not found: $appId")
        val versions = htmlAppGateway.listVersions(appId)
        val meta = mapOf(
            "appId" to app.id,
            "title" to app.title,
            "version" to (versions.firstOrNull()?.version ?: 1),
            "versions" to versions.map { mapOf("version" to it.version, "message" to it.message) },
            "paths" to listOf("index.html"),
        )
        val offset = args.intOrNull("offset") ?: 1
        val limit = args.intOrNull("limit") ?: 400
        val slice = htmlAppGateway.readHtmlSlice(appId, offset, limit)
            ?: return errorJson("index.html not found for $appId")
        return GSON.toJson(
            mapOf(
                "success" to true,
                "app" to meta,
                "path" to slice.path,
                "content" to slice.content,
                "totalLines" to slice.totalLines,
                "offset" to slice.offset,
                "limit" to slice.limit,
                "truncated" to slice.truncated,
                "note" to if (slice.truncated) {
                    "Truncated. Re-read with offset=${slice.offset + slice.content.lines().size} to continue."
                } else {
                    "Working tree = live. edit_file/write_file take effect immediately; commit_html_app to version."
                },
            ),
        )
    }

    /** edit_file: StrReplace 工作树 index.html。立即生效，标记 dirty。 */
    suspend fun edit(appId: String, args: JsonObject): String {
        val oldString = args.stringOrNull("old_string").orEmpty()
        val newString = args.stringOrNull("new_string").orEmpty()
        if (oldString.isEmpty()) return errorJson("old_string must not be empty")
        val replaceAll = args.booleanOrNull("replace_all") ?: false
        return when (val result = htmlAppGateway.editFile(appId, oldString, newString, replaceAll)) {
            is Either.Left -> errorJson(result.value)
            is Either.Right -> GSON.toJson(
                mapOf(
                    "success" to true,
                    "appId" to appId,
                    "replacements" to result.value,
                    "note" to "Working tree updated (live). Commit via commit_html_app to snapshot a version.",
                ),
            )
        }
    }

    /** write_file: 覆写工作树 index.html。立即生效，标记 dirty。 */
    suspend fun write(appId: String, args: JsonObject): String {
        val content = args.stringOrNull("content").orEmpty()
        if (content.isBlank()) return errorJson("""Missing or empty "content". Pass the complete HTML document.""")
        val error = htmlAppGateway.writeFile(appId, content)
            ?: return GSON.toJson(
                mapOf(
                    "success" to true,
                    "appId" to appId,
                    "note" to "index.html written (live). Commit via commit_html_app to snapshot a version.",
                ),
            )
        return errorJson(error)
    }

    /** delete_file: `htmlapp://{appId}` 删除整个 app（实体 + 目录 + 快照）。 */
    suspend fun delete(appId: String, sourceConversationId: String?): String {
        val app = htmlAppGateway.getById(appId)
            ?: return errorJson("HTML app not found: $appId")
        if (sourceConversationId != null && app.conversationId != sourceConversationId) {
            return errorJson("HTML app not in this conversation")
        }
        htmlAppGateway.delete(appId)
        return GSON.toJson(
            mapOf(
                "success" to true,
                "appId" to appId,
                "note" to "HTML app deleted (files + metadata + history).",
            ),
        )
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        val el = get(key) ?: return null
        if (el.isJsonNull) return null
        return runCatching { el.asString }.getOrNull()
    }

    private fun JsonObject.intOrNull(key: String): Int? {
        val el = get(key) ?: return null
        if (el.isJsonNull) return null
        return runCatching { el.asInt }.getOrNull()
    }

    private fun JsonObject.booleanOrNull(key: String): Boolean? {
        val el = get(key) ?: return null
        if (el.isJsonNull) return null
        return runCatching {
            when {
                el.asJsonPrimitive.isBoolean -> el.asBoolean
                else -> el.asString.equals("true", ignoreCase = true)
            }
        }.getOrNull()
    }

    companion object {
        const val TOOL_PUBLISH_HTML_APP = "publish_html_app"
        const val TOOL_COMMIT_HTML_APP = "commit_html_app"
        const val TOOL_ROLLBACK_HTML_APP = "rollback_html_app"
        const val TOOL_LIST_HTML_APP_VERSIONS = "list_html_app_versions"
        const val TOOL_LIST_HTML_APPS = "list_html_apps"

        /** 供 FileToolRouter 的 htmlapp:// 路径识别。 */
        const val SCHEME = "htmlapp"

        /**
         * publish 工具描述：GameBridge 上行 API、updateGameState 下行契约、__game_update__ 协议、
         * 混合驱动指导、迭代说明（走统一 file CRUD）。
         */
        val PUBLISH_DESCRIPTION = """
            Publish a complete standalone HTML application (game, interactive visualization, utility) that the user can run full-screen in a sandboxed WebView. Inline CSS and JS required; the sandbox blocks arbitrary network traffic, so do NOT rely on loading JS/CSS from external URLs.

            NETWORK RESOURCES (allowed): the player whitelists image and font assets over http/https — e.g. <img src="https://...">, CSS url() to images, and web fonts (Google Fonts / CDN @font-face). This works as long as the URL path ends in an image/font extension (.png .jpg .jpeg .webp .gif .svg .ico .bmp .woff .woff2 .ttf .otf) or contains "image"/"font". Everything else (scripts, fetch/XHR, JSON APIs) is blocked.

            The generated page can talk to the native side via the injected `GameBridge` JS object:

            CONTEXT (read-only):
            - getWorldBooks() -> JSON array of world books
            - getCharacters() -> JSON array of bound characters
            - getOutline() -> markdown outline string
            - getRecentMessages(n) -> JSON array of recent {role, content}

            VIEWPORT:
            - getViewport() -> JSON {width, height, screenWidth, screenHeight, density, pixelWidth, pixelHeight}. width/height are the WebView's CSS-pixel viewport (the actual play area). Use this (not a fixed size) for canvas layout and responsive design. Include <meta name="viewport" content="width=device-width, initial-scale=1"> in the HTML.

            STATE:
            - loadGameState() -> previously saved game state JSON, or null
            - saveGameState(jsonStr) -> persist game state for the next session

            CHAT:
            - sendToChat(message) -> appear as a user message in the conversation
            - notifyContext(json) -> silently inject context into your next reply

            LIFECYCLE:
            - exit() -> close the player

            MIXED DRIVING (IMPORTANT):
            - High-frequency values (coins, exp, hp, time, inventory) MUST be computed and rendered by the page's own JavaScript — do not rely on the AI for every +1. Use setInterval/events locally.
            - The page MUST define window.updateGameState = function(json){...} to receive your narrative-driven updates.
            - When you (the AI) make a narrative decision that changes game values (a plot event, an encounter, a settlement), append at the very END of your reply a JSON block: {"__game_update__": { ...values to change... }}. It is stripped from the visible text and pushed to the running game.
            - Report the player's current status to yourself periodically by having the page call notifyContext({coins, exp, day, hp, location, ...}).

            ITERATION: the source lives at htmlapp://{appId}/index.html — read/edit/write it via the generic read_file / edit_file / write_file tools. To create a new app omit appId; to overwrite+version an existing one pass the SAME appId.
        """.trimIndent()

        val COMMIT_DESCRIPTION = """
            Snapshot the current working tree of an HTML app into a new committed version (bumps version, clears dirty). The runtime already reflects the working tree (live); commit only matters for history/rollback. Use when the user asks to save/version, or before a risky change.
        """.trimIndent()

        val ROLLBACK_DESCRIPTION = """
            Roll an HTML app (game) back to a previously committed version. Pass appId + version. The working file is restored to that snapshot; a new version is committed on top (history is never lost). Use after the user says a change made the game worse.
        """.trimIndent()

        val LIST_VERSIONS_DESCRIPTION = """
            List commit history of an HTML app: versions, messages, timestamps. Pass appId.
        """.trimIndent()

        val LIST_APPS_DESCRIPTION = """
            List the HTML apps (games) in this conversation, with appId + title + current version. Use the appId to read/edit via file tools (htmlapp://appId/index.html), commit, or rollback.
        """.trimIndent()

        private fun errorJson(message: String): String =
            GSON.toJson(mapOf("success" to false, "error" to message))
    }
}
