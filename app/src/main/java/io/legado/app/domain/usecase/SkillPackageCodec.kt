package io.legado.app.domain.usecase

import io.legado.app.data.entities.AiSkill

/**
 * Cursor-aligned Skill codec: a single `SKILL.md` file with YAML frontmatter + markdown body.
 *
 * Frontmatter:
 * - `name` (required): lowercase letters, digits, hyphens; used as skill id
 * - `description` (required): what it does and when to use it
 * - `mode` (optional): `chat` | `writing` | `both` (legado extension; default `both`)
 * - `resources` (optional): comma-separated help doc ids under `assets/web/help/md/`
 *   (e.g. `mimoTtsHelp`) — linked on demand, not duplicated into the skill folder
 *
 * Body becomes the core instructions ([AiSkill.instruction]) — loaded on demand, not via catalog.
 */
object SkillPackageCodec {

    const val DOC_ID_SKILL = "SKILL"
    const val TEMPLATE = """---
name: my-skill
description: What this skill does and when to use it.
mode: both
---

# My Skill

## Instructions

- Step-by-step guidance for the agent
"""

    /**  skill name / id (hyphens preferred; underscores allowed). */
    val ID_PATTERN = Regex("^[a-z][a-z0-9_-]{0,63}$")

    fun isValidId(id: String): Boolean = ID_PATTERN.matches(id.trim())

    data class SkillDoc(
        val id: String,
        val title: String = "",
        val markdown: String = "",
    )

    data class SkillPackage(
        val skill: AiSkill,
        val docs: List<SkillDoc> = emptyList(),
        /** Full SKILL.md source used for edit/export. */
        val markdown: String = "",
    )

    sealed class ParseResult {
        data class Success(val pkg: SkillPackage) : ParseResult()
        data class Error(val message: String) : ParseResult()
    }

    fun validateContent(instruction: String): String? =
        if (instruction.isBlank()) "SKILL.md body is required" else null

    fun parse(raw: String): ParseResult {
        val trimmed = raw.trim().removePrefix("\uFEFF")
        if (trimmed.isBlank()) return ParseResult.Error("SKILL.md is empty")
        val (frontMatter, body) = splitFrontMatter(trimmed)
            ?: return ParseResult.Error("SKILL.md must start with YAML frontmatter (---)")
        val yaml = parseSimpleYaml(frontMatter)
        val name = yaml["name"]?.trim().orEmpty()
        if (!isValidId(name)) {
            return ParseResult.Error(
                "Invalid name: lowercase letters/digits/hyphens, start with a letter (max 64)",
            )
        }
        val description = yaml["description"]?.trim().orEmpty()
        if (description.isBlank()) {
            return ParseResult.Error("description is required in frontmatter")
        }
        val mode = yaml["mode"]?.trim()?.lowercase().orEmpty().ifBlank { AiSkill.MODE_BOTH }
        if (mode !in setOf(AiSkill.MODE_CHAT, AiSkill.MODE_WRITING, AiSkill.MODE_BOTH)) {
            return ParseResult.Error("mode must be chat, writing, or both")
        }
        val linkedResources = parseResourceIds(yaml["resources"])
        val instruction = body.trim()
        validateContent(instruction)?.let { return ParseResult.Error(it) }
        val displayName = firstHeading(instruction) ?: humanizeName(name)
        val markdown = exportMarkdown(
            name = name,
            description = description,
            mode = mode,
            body = instruction,
            resources = linkedResources,
        )
        val docIds = listOf(DOC_ID_SKILL) + linkedResources
        val skill = AiSkill(
            skillId = name,
            name = displayName,
            description = description,
            mode = mode,
            enabled = true,
            sortOrder = 1000,
            toolNamesJson = "[]",
            docIdsJson = AiSkill.encodeList(docIds),
            hint = description,
            instruction = instruction,
            builtin = false,
        )
        val docs = listOf(
            SkillDoc(id = DOC_ID_SKILL, title = displayName, markdown = markdown),
        )
        return ParseResult.Success(SkillPackage(skill = skill, docs = docs, markdown = markdown))
    }

    fun export(skill: AiSkill, body: String = skill.instruction): String {
        val name = skill.skillId
        val description = skill.description.ifBlank { skill.hint }.ifBlank { skill.name }
        val resources = skill.docIds().filter { it != DOC_ID_SKILL }
        return exportMarkdown(
            name = name,
            description = description,
            mode = skill.mode.ifBlank { AiSkill.MODE_BOTH },
            body = body.trim(),
            resources = resources,
        )
    }

    fun exportMarkdown(
        name: String,
        description: String,
        mode: String,
        body: String,
        resources: List<String> = emptyList(),
    ): String {
        return buildString {
            appendLine("---")
            appendYamlLine("name", name)
            appendYamlLine("description", description)
            appendYamlLine("mode", mode)
            if (resources.isNotEmpty()) {
                appendYamlLine("resources", resources.joinToString(", "))
            }
            appendLine("---")
            appendLine()
            append(body.trim())
            if (!body.trimEnd().endsWith("\n")) appendLine()
        }
    }

    /**
     * Split leading YAML frontmatter. Returns null if the file does not start with `---`.
     */
    fun splitFrontMatter(text: String): Pair<String, String>? {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        if (!normalized.startsWith("---")) return null
        val afterOpen = normalized.removePrefix("---").removePrefix("\n")
        val end = afterOpen.indexOf("\n---")
        if (end < 0) return null
        val fm = afterOpen.substring(0, end).trim()
        var body = afterOpen.substring(end + "\n---".length)
        if (body.startsWith("\n")) body = body.removePrefix("\n")
        return fm to body
    }

    private fun parseSimpleYaml(text: String): Map<String, String> {
        val map = linkedMapOf<String, String>()
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) {
                i++
                continue
            }
            val colon = t.indexOf(':')
            if (colon <= 0) {
                i++
                continue
            }
            val key = t.substring(0, colon).trim()
            var value = t.substring(colon + 1).trim()
            when {
                value == ">" || value == ">:" || value == ">|" || value == "|" ||
                    value == ">-" || value == "|-" -> {
                    val folded = mutableListOf<String>()
                    i++
                    while (i < lines.size) {
                        val next = lines[i]
                        if (next.isNotEmpty() && !next.startsWith(" ") && !next.startsWith("\t")) break
                        folded += next.trim()
                        i++
                    }
                    map[key] = folded.joinToString(" ").trim()
                    continue
                }
                value.length >= 2 && value.startsWith("\"") && value.endsWith("\"") -> {
                    value = value.substring(1, value.length - 1).replace("\\\"", "\"")
                }
                value.length >= 2 && value.startsWith("'") && value.endsWith("'") -> {
                    value = value.substring(1, value.length - 1)
                }
            }
            map[key] = value
            i++
        }
        return map
    }

    private fun parseResourceIds(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',', ' ', ';')
            .map { it.trim() }
            .filter { it.isNotBlank() && it != DOC_ID_SKILL }
            .distinct()
    }

    private fun StringBuilder.appendYamlLine(key: String, value: String) {
        val escaped = value.replace("\r\n", "\n").replace('\r', '\n').replace("\n", " ").trim()
        if (escaped.any { it == ':' || it == '"' || it == '#' }) {
            appendLine("$key: \"${escaped.replace("\"", "\\\"")}\"")
        } else {
            appendLine("$key: $escaped")
        }
    }

    private fun firstHeading(markdown: String): String? {
        return markdown.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("#") }
            ?.trimStart('#')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun humanizeName(name: String): String =
        name.split('-')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
}
