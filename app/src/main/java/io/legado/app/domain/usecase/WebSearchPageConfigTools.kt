package io.legado.app.domain.usecase

import com.google.gson.JsonObject
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.WebSearchPageProfiles
import io.legado.app.utils.GSON

/**
 * Read/patch public search-page profiles (switchable sets of parse rules).
 * Does not touch API key / provider / base URL / quotas.
 */
object WebSearchPageConfigTools {

    fun read(args: JsonObject = JsonObject()): String {
        val profiles = WebSearchPageProfiles.list()
        val active = WebSearchPageProfiles.getActive()
        val detailId = args.stringOrNull("profileId")
            ?: args.stringOrNull("name")?.let { WebSearchPageProfiles.findByName(it)?.id }
        val detail = detailId?.let { WebSearchPageProfiles.get(it) }
        return GSON.toJson(
            buildMap {
                put("success", true)
                put("mode", AppConfig.aiWebSearchMode)
                put("activeProfileId", active.id)
                put("profiles", profiles.map { mapOf("id" to it.id, "name" to it.name) })
                put("active", active.toMap())
                if (detail != null) put("profile", detail.toMap())
            },
        )
    }

    fun patch(args: JsonObject): String {
        val changed = mutableListOf<String>()

        args.stringOrNull("mode")?.let { raw ->
            val next = raw.trim().lowercase()
            if (next != "api" && next != "page") {
                return """{"error":"mode must be api or page"}"""
            }
            AppConfig.aiWebSearchMode = next
            changed.add("mode")
        }

        val action = args.stringOrNull("action")?.trim()?.lowercase().orEmpty().ifBlank {
            when {
                hasProfileFieldPatch(args) -> "update"
                else -> ""
            }
        }

        val result = when (action) {
            "" -> {
                if (changed.isEmpty()) {
                    return """{"error":"Provide action (activate|update|create|delete) and/or mode / profile fields"}"""
                }
                null
            }
            "activate" -> {
                val profile = resolveProfile(args)
                    ?: return """{"error":"activate requires profileId or name"}"""
                WebSearchPageProfiles.setActive(profile.id).fold(
                    onSuccess = {
                        changed.add("activate")
                        it
                    },
                    onFailure = { return """{"error":"${it.message}"}""" },
                )
            }
            "update" -> {
                val profile = resolveProfile(args) ?: WebSearchPageProfiles.getActive()
                val patched = applyFieldPatch(profile, args)
                WebSearchPageProfiles.update(patched).fold(
                    onSuccess = {
                        changed.add("update")
                        it
                    },
                    onFailure = { return """{"error":"${it.message}"}""" },
                )
            }
            "create" -> {
                val name = args.stringOrNull("name")?.trim().orEmpty()
                if (name.isBlank()) return """{"error":"create requires name"}"""
                val activate = args.booleanOrNull("activate") == true
                WebSearchPageProfiles.create(
                    name = name,
                    pageUrlTemplate = args.stringOrNull("pageUrlTemplate"),
                    delayMs = args.intOrNull("delayMs"),
                    resultSelector = args.stringOrNull("resultSelector"),
                    titleSelector = args.stringOrNull("titleSelector"),
                    snippetSelector = args.stringOrNull("snippetSelector"),
                    redirectParam = if (args.has("redirectParam") && !args.get("redirectParam").isJsonNull) {
                        args.get("redirectParam").asString
                    } else null,
                    excludeHosts = args.stringOrNull("excludeHosts"),
                    activate = activate,
                ).fold(
                    onSuccess = {
                        changed.add("create")
                        if (activate) changed.add("activate")
                        it
                    },
                    onFailure = { return """{"error":"${it.message}"}""" },
                )
            }
            "delete" -> {
                val profile = resolveProfile(args)
                    ?: return """{"error":"delete requires profileId or name"}"""
                WebSearchPageProfiles.delete(profile.id).fold(
                    onSuccess = {
                        changed.add("delete")
                        it
                    },
                    onFailure = { return """{"error":"${it.message}"}""" },
                )
            }
            else -> return """{"error":"Unknown action: $action. Use activate|update|create|delete"}"""
        }

        val active = WebSearchPageProfiles.getActive()
        return GSON.toJson(
            buildMap {
                put("success", true)
                put("changed", changed)
                put("mode", AppConfig.aiWebSearchMode)
                put("activeProfileId", active.id)
                put("profiles", WebSearchPageProfiles.list().map {
                    mapOf("id" to it.id, "name" to it.name)
                })
                put("active", active.toMap())
                if (result != null) put("profile", result.toMap())
            },
        )
    }

    fun summary(args: JsonObject): String {
        val action = args.stringOrNull("action")?.trim()?.lowercase().orEmpty().ifBlank {
            if (hasProfileFieldPatch(args)) "update" else "patch"
        }
        val target = args.stringOrNull("name")
            ?: args.stringOrNull("profileId")?.take(8)
            ?: "active"
        return "Web search page config: $action ($target)"
    }

    fun previewDetail(args: JsonObject): String = summary(args)

    private fun resolveProfile(args: JsonObject): WebSearchPageProfiles.PageProfile? {
        args.stringOrNull("profileId")?.let { id ->
            WebSearchPageProfiles.get(id)?.let { return it }
        }
        args.stringOrNull("name")?.let { name ->
            WebSearchPageProfiles.findByName(name)?.let { return it }
        }
        return null
    }

    private fun hasProfileFieldPatch(args: JsonObject): Boolean =
        listOf(
            "pageUrlTemplate", "delayMs", "resultSelector", "titleSelector",
            "snippetSelector", "redirectParam", "excludeHosts", "name",
        ).any { args.has(it) && !args.get(it).isJsonNull }

    private fun applyFieldPatch(
        profile: WebSearchPageProfiles.PageProfile,
        args: JsonObject,
    ): WebSearchPageProfiles.PageProfile {
        var next = profile
        args.stringOrNull("name")?.let { next = next.copy(name = it) }
        args.stringOrNull("pageUrlTemplate")?.let { next = next.copy(pageUrlTemplate = it) }
        args.intOrNull("delayMs")?.let { next = next.copy(delayMs = it) }
        args.stringOrNull("resultSelector")?.let { next = next.copy(resultSelector = it) }
        args.stringOrNull("titleSelector")?.let { next = next.copy(titleSelector = it) }
        args.stringOrNull("snippetSelector")?.let { next = next.copy(snippetSelector = it) }
        if (args.has("redirectParam") && !args.get("redirectParam").isJsonNull) {
            next = next.copy(redirectParam = args.get("redirectParam").asString)
        }
        args.stringOrNull("excludeHosts")?.let { next = next.copy(excludeHosts = it) }
        return next
    }

    private fun WebSearchPageProfiles.PageProfile.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "pageUrlTemplate" to pageUrlTemplate,
        "delayMs" to delayMs,
        "resultSelector" to resultSelector,
        "titleSelector" to titleSelector,
        "snippetSelector" to snippetSelector,
        "redirectParam" to redirectParam,
        "excludeHosts" to excludeHosts,
    )

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

    private fun JsonObject.booleanOrNull(key: String): Boolean? {
        val el = get(key) ?: return null
        if (el.isJsonNull) return null
        return when {
            el.isJsonPrimitive && el.asJsonPrimitive.isBoolean -> el.asBoolean
            else -> el.asString.toBooleanStrictOrNull()
        }
    }
}
