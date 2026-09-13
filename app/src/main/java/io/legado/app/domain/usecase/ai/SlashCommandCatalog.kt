package io.legado.app.domain.usecase.ai

/**
 * Built-in chat slash commands for composer autocomplete.
 * Execution still lives in [TempSlashCommand] / ViewModel send path.
 */
object SlashCommandCatalog {

    data class Entry(
        val id: String,
        /** Primary token after `/` (ASCII). */
        val primary: String,
        val aliases: List<String> = emptyList(),
        /** Text inserted on pick (includes leading `/` and trailing space). */
        val insertText: String,
    ) {
        fun matches(partial: String): Boolean {
            if (partial.isBlank()) return true
            val q = partial.lowercase()
            if (primary.lowercase().startsWith(q) || primary.lowercase().contains(q)) return true
            return aliases.any { alias ->
                alias.lowercase().startsWith(q) || alias.contains(partial, ignoreCase = true)
            }
        }
    }

    val ALL: List<Entry> = listOf(
        Entry(
            id = "temp",
            primary = "temp",
            aliases = listOf("临时"),
            insertText = "/temp ",
        ),
    )

    /**
     * While the composer is completing a leading `/command` token (no space/body yet),
     * returns the partial after `/`. Otherwise null (hide menu).
     */
    fun completionQuery(input: String): String? {
        val slashIdx = input.indexOf('/')
        if (slashIdx < 0) return null
        // Only treat a leading slash (allow whitespace before it).
        if (input.substring(0, slashIdx).any { !it.isWhitespace() }) return null
        val after = input.substring(slashIdx + 1)
        if (after.contains(' ') || after.contains('\n') || after.contains('\t')) return null
        return after
    }

    fun match(partial: String): List<Entry> =
        ALL.filter { it.matches(partial) }
}
