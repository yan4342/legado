package io.legado.app.domain.usecase

import com.google.gson.JsonObject
import io.legado.app.data.entities.AiSkill
import io.legado.app.domain.gateway.AiSkillGateway
import io.legado.app.domain.usecase.SkillImportConflictException
import io.legado.app.utils.GSON

/**
 * Chat tools for listing / reading / creating / updating / deleting AI Skills (SKILL.md packages).
 */
class SkillTools(
    private val skillGateway: AiSkillGateway,
) {

    suspend fun list(args: JsonObject): String {
        val modeFilter = args.stringOrNull("mode")?.trim()?.lowercase().orEmpty()
        val enabledOnly = args.booleanOrNull("enabledOnly") == true
        var skills = skillGateway.getAll()
        if (enabledOnly) skills = skills.filter { it.enabled }
        if (modeFilter.isNotBlank()) {
            skills = skills.filter { it.matchesMode(modeFilter) || it.mode == modeFilter }
        }
        return GSON.toJson(
            mapOf(
                "success" to true,
                "count" to skills.size,
                "skills" to skills.sortedBy { it.sortOrder }.map { it.toSummaryMap() },
            ),
        )
    }

    suspend fun read(args: JsonObject): String {
        val skillId = resolveSkillId(args)
            ?: return errorJson("Provide skillId or name")
        val skill = skillGateway.getById(skillId)
            ?: return errorJson("Skill not found: $skillId")
        val markdown = skillGateway.loadMarkdownForEdit(skillId).getOrElse {
            return errorJson(it.message ?: "Failed to load skill")
        }
        return GSON.toJson(
            mapOf(
                "success" to true,
                "skill" to skill.toDetailMap(),
                "markdown" to markdown,
            ),
        )
    }

    /** Read one resource doc (e.g. skill://{id}/{docId}) — user override wins, else built-in fallback. */
    suspend fun readResourceDoc(skillId: String, docId: String): String {
        val skill = skillGateway.getById(skillId)
            ?: return errorJson("Skill not found: $skillId")
        val content = skillGateway.loadResourceDocForEdit(skillId, docId).getOrElse {
            return errorJson(it.message ?: "Failed to read resource doc: $docId")
        }
        return GSON.toJson(
            mapOf(
                "success" to true,
                "skillId" to skill.skillId,
                "doc" to docId,
                "content" to content,
            ),
        )
    }

    suspend fun save(args: JsonObject): String {
        val enabledOnlyPatch = args.booleanOrNull("enabled")
        val markdownArg = args.stringOrNull("markdown")?.trim().orEmpty()
        val skillIdHint = resolveSkillId(args)

        // Lightweight enable/disable without rewriting body.
        if (markdownArg.isBlank() &&
            !hasBodyFields(args) &&
            enabledOnlyPatch != null &&
            !skillIdHint.isNullOrBlank()
        ) {
            val existing = skillGateway.getById(skillIdHint)
                ?: return errorJson("Skill not found: $skillIdHint")
            val updated = existing.copy(
                enabled = enabledOnlyPatch,
                updatedAt = System.currentTimeMillis(),
            )
            skillGateway.save(updated)
            return GSON.toJson(
                mapOf(
                    "success" to true,
                    "action" to "set_enabled",
                    "skill" to updated.toSummaryMap(),
                ),
            )
        }

        val markdown = when {
            markdownArg.isNotBlank() -> markdownArg
            else -> buildMarkdownFromFields(args)
                ?: return errorJson(
                    "Provide markdown (full SKILL.md) or name + description + body " +
                        "(and optional mode/resources/enabled)",
                )
        }

        val before = runCatching {
            when (val parsed = SkillPackageCodec.parse(markdown)) {
                is SkillPackageCodec.ParseResult.Success ->
                    skillGateway.getById(parsed.pkg.skill.skillId)
                is SkillPackageCodec.ParseResult.Error -> null
            }
        }.getOrNull()
        val saved = skillGateway.saveUserSkillMarkdown(markdown).getOrElse {
            return errorJson(it.message ?: "Failed to save skill")
        }
        val enabled = args.booleanOrNull("enabled")
        val finalSkill = if (enabled != null && enabled != saved.enabled) {
            val patched = saved.copy(enabled = enabled, updatedAt = System.currentTimeMillis())
            skillGateway.save(patched)
            patched
        } else {
            saved
        }
        return GSON.toJson(
            mapOf(
                "success" to true,
                "action" to if (before == null) "create" else "update",
                "skill" to finalSkill.toSummaryMap(),
            ),
        )
    }

    suspend fun delete(args: JsonObject): String {
        val skillId = resolveSkillId(args)
            ?: return errorJson("Provide skillId or name")
        val existing = skillGateway.getById(skillId)
            ?: return errorJson("Skill not found: $skillId")
        if (existing.builtin) {
            return errorJson(
                "Cannot delete builtin skill `$skillId` (it would be re-seeded). " +
                    "Disable it with write_file(enabled=false), or overwrite via write_file markdown.",
            )
        }
        val ok = skillGateway.delete(skillId)
        if (!ok) return errorJson("Failed to delete skill: $skillId")
        return GSON.toJson(
            mapOf(
                "success" to true,
                "action" to "delete",
                "skillId" to skillId,
            ),
        )
    }

    suspend fun installFromUrl(
        args: JsonObject,
        onProgress: io.legado.app.domain.model.AiToolProgressCallback? = null,
    ): String {
        val url = args.stringOrNull("url")?.trim().orEmpty()
        if (url.isBlank()) return errorJson("Provide url (GitHub / Gitee / GitLab / raw SKILL.md)")
        val overwrite = args.booleanOrNull("overwrite") == true
        val skill = skillGateway.importFromUrl(url, overwrite = overwrite, onProgress = onProgress)
            .getOrElse { err ->
                return when (err) {
                    is SkillImportConflictException ->
                        GSON.toJson(
                            mapOf(
                                "error" to "Skill already exists: ${err.conflictIds.joinToString(",")}. " +
                                    "Retry with overwrite=true to replace.",
                                "conflictIds" to err.conflictIds,
                            ),
                        )
                    else -> errorJson(err.message ?: "Import from URL failed")
                }
            }
        return GSON.toJson(
            mapOf(
                "success" to true,
                "action" to "install_from_url",
                "skill" to skill.toSummaryMap(),
            ),
        )
    }

    suspend fun edit(args: JsonObject): String {
        val skillId = resolveSkillId(args)
            ?: return errorJson("Provide skillId or name")
        val skill = skillGateway.getById(skillId)
            ?: return errorJson("Skill not found: $skillId")
        val docId = args.stringOrNull("doc")?.trim()?.takeIf { it.isNotBlank() }
            ?: SkillPackageCodec.DOC_ID_SKILL
        val oldString = args.stringOrNull("old_string")?.trimEnd()
            ?: args.stringOrNull("oldString")?.trimEnd()
        val newString = args.stringOrNull("new_string")
            ?: args.stringOrNull("newString")
            ?: ""
        if (oldString.isNullOrEmpty()) return errorJson("old_string must not be empty")
        val replaceAll = args.booleanOrNull("replace_all")
            ?: args.booleanOrNull("replaceAll")
            ?: false

        // Load current content: SKILL.md or a resource doc.
        val current = if (docId == SkillPackageCodec.DOC_ID_SKILL) {
            skillGateway.loadMarkdownForEdit(skillId).getOrElse {
                return errorJson(it.message ?: "Failed to read skill")
            }
        } else {
            skillGateway.loadResourceDocForEdit(skillId, docId).getOrElse {
                return errorJson(it.message ?: "Failed to read resource doc: $docId")
            }
        }

        val count = FileToolsStrReplace.countOccurrences(current, oldString)
        when {
            count == 0 -> {
                val probe = oldString.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty().take(64)
                val hit = if (probe.length >= 4) current.indexOf(probe) else -1
                val snippet = if (hit >= 0) {
                    val start = (hit - 20).coerceAtLeast(0)
                    val end = (hit + probe.length + 20).coerceAtMost(current.length)
                    current.substring(start, end)
                } else {
                    current.take(100)
                }
                val hint = if (hit >= 0) {
                    "old_string not found at exact position — similar text at offset ~$hit. " +
                        "Re-read and widen old_string context."
                } else {
                    "old_string not found in markdown. Re-read the skill and try again."
                }
                return GSON.toJson(
                    mapOf(
                        "error" to "old_string not found (0 matches)",
                        "snippet" to snippet,
                        "hint" to hint,
                    ),
                )
            }
            count > 1 && !replaceAll -> {
                return GSON.toJson(
                    mapOf(
                        "error" to "$count matches found. Add more context to old_string to make it unique, or set replace_all=true.",
                        "matchCount" to count,
                        "hint" to "Re-read, widen old_string context, and retry.",
                    ),
                )
            }
        }
        val next = if (replaceAll) {
            current.replace(oldString, newString)
        } else {
            current.replaceFirst(oldString, newString)
        }
        val replacements = if (replaceAll) count else 1

        // Save: SKILL.md vs resource doc.
        if (docId == SkillPackageCodec.DOC_ID_SKILL) {
            val saved = skillGateway.saveUserSkillMarkdown(next).getOrElse {
                return errorJson(it.message ?: "Failed to save skill")
            }
            return GSON.toJson(
                mapOf(
                    "success" to true,
                    "action" to "edit",
                    "skill" to saved.toSummaryMap(),
                    "replacements" to replacements,
                    "note" to if (replaceAll && replacements > 1) {
                        "$replacements replacements applied."
                    } else {
                        "1 replacement applied."
                    },
                ),
            )
        } else {
            skillGateway.saveResourceDoc(skillId, docId, next).getOrElse {
                return errorJson(it.message ?: "Failed to save resource doc: $docId")
            }
            return GSON.toJson(
                mapOf(
                    "success" to true,
                    "action" to "edit_resource",
                    "skillId" to skillId,
                    "doc" to docId,
                    "replacements" to replacements,
                    "note" to if (replaceAll && replacements > 1) {
                        "$replacements replacements applied."
                    } else {
                        "1 replacement applied. Resource doc $docId overridden — will be used instead of built-in version."
                    },
                ),
            )
        }
    }

    companion object {
        fun summaryInstallFromUrl(args: JsonObject): String {
            val url = args.stringOrNull("url")?.trim().orEmpty().ifBlank { "…" }
            val short = if (url.length > 64) url.take(61) + "…" else url
            return "Install skill from URL: $short"
        }

        fun previewDetailInstallFromUrl(args: JsonObject): String = summaryInstallFromUrl(args)

        private fun hasBodyFields(args: JsonObject): Boolean =
            !args.stringOrNull("body").isNullOrBlank() ||
                !args.stringOrNull("instruction").isNullOrBlank()

        private fun JsonObject.stringOrNull(key: String): String? {
            val el = get(key) ?: return null
            if (el.isJsonNull) return null
            return runCatching { el.asString }.getOrNull()
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

    private suspend fun resolveSkillId(args: JsonObject): String? {
        args.stringOrNull("skillId")?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        val name = args.stringOrNull("name")?.trim().orEmpty()
        if (name.isBlank()) return null
        skillGateway.getById(name)?.let { return it.skillId }
        return skillGateway.getAll().firstOrNull {
            it.skillId.equals(name, ignoreCase = true) ||
                it.name.equals(name, ignoreCase = true)
        }?.skillId
    }

    private fun buildMarkdownFromFields(args: JsonObject): String? {
        val name = args.stringOrNull("name")?.trim().orEmpty()
            .ifBlank { args.stringOrNull("skillId")?.trim().orEmpty() }
        val description = args.stringOrNull("description")?.trim().orEmpty()
        val body = args.stringOrNull("body")?.trim().orEmpty()
            .ifBlank { args.stringOrNull("instruction")?.trim().orEmpty() }
        if (name.isBlank() || description.isBlank() || body.isBlank()) return null
        val mode = args.stringOrNull("mode")?.trim()?.lowercase().orEmpty()
            .ifBlank { AiSkill.MODE_BOTH }
        val resources = args.stringOrNull("resources")
            ?.split(',', '，', ';', '；', '\n')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        return SkillPackageCodec.exportMarkdown(
            name = name,
            description = description,
            mode = mode,
            body = body,
            resources = resources,
        )
    }

    private fun AiSkill.toSummaryMap(): Map<String, Any?> = mapOf(
        "skillId" to skillId,
        "name" to name,
        "description" to description.ifBlank { hint },
        "mode" to mode,
        "enabled" to enabled,
        "builtin" to builtin,
        "resources" to docIds().filter { it != SkillPackageCodec.DOC_ID_SKILL },
    )

    private fun AiSkill.toDetailMap(): Map<String, Any?> = toSummaryMap() + mapOf(
        "sortOrder" to sortOrder,
        "hint" to hint,
        "updatedAt" to updatedAt,
    )

    private fun errorJson(message: String): String =
        GSON.toJson(mapOf("error" to message))

    private fun JsonObject.stringOrNull(key: String): String? {
        val el = get(key) ?: return null
        if (el.isJsonNull) return null
        return runCatching { el.asString }.getOrNull()
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
