package io.legado.app.domain.usecase.ai

/**
 * `/temp` (alias `/临时`): send a one-shot director note that is stored for UI
 * but excluded from subsequent model context. Assistant replies stay in context.
 */
object TempSlashCommand {

    private val pattern = Regex(
        """(?is)^\s*/(?:temp|临时)(?:\s+(.*))?$""",
        RegexOption.DOT_MATCHES_ALL,
    )

    data class Parsed(
        /** Body after the command; may be blank when user typed only `/temp`. */
        val body: String,
    )

    fun parse(raw: String): Parsed? {
        val match = pattern.matchEntire(raw.trim()) ?: return null
        return Parsed(body = match.groupValues.getOrElse(1) { "" }.trim())
    }
}
