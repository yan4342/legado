package io.legado.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Idempotency of [AiToolRepository.decorateDescriptionForApproval]: a tool description must
 * never end up with two "requires confirmation" sentences or two "do not claim success" guards
 * when the static description already carries one. Regression: `patch_outline`'s static text ends
 * with "Requires confirmation; do not claim success…" (different wording from the runtime guard),
 * which used to make the guard-detection fail and append a duplicate confirmation sentence.
 */
class AiToolRepositoryApprovalTest {

    private fun decorate(name: String, description: String): String =
        AiToolRepository.decorateDescriptionForApproval(
            name = name,
            description = description,
            requireMutationApproval = true,
            conversationType = "chat",
        )

    private fun countIgnoreCase(haystack: String, needle: String): Int =
        Regex(needle, RegexOption.IGNORE_CASE).findAll(haystack).count()

    @Test
    fun `appends full policy when nothing present`() {
        val out = decorate("patch_outline", "Patch the story outline.")
        assertTrue(out.contains("Requires in-app confirmation before execution;"))
        assertTrue(out.contains("Do NOT claim the write already succeeded until the tool result returns."))
        assertEquals(1, countIgnoreCase(out, "requires"))
    }

    @Test
    fun `does not duplicate confirmation when static text already has it`() {
        val out = decorate("patch_outline", "Patch the story outline. Requires confirmation.")
        assertEquals(1, countIgnoreCase(out, "requires"))
        assertEquals(1, countIgnoreCase(out, "do not claim"))
    }

    @Test
    fun `returns unchanged when exact guard already present`() {
        val description = "Patch history. Requires in-app confirmation before execution; Do NOT claim the write already succeeded until the tool result returns."
        assertEquals(description, decorate("patch_history_memory", description))
    }

    @Test
    fun `returns unchanged for differently-worded guard`() {
        // patch_outline static text — the reported double-append bug.
        val description = "Patch the story outline. Requires confirmation; do not claim success until the tool result returns."
        assertEquals(description, decorate("patch_outline", description))
    }

    @Test
    fun `returns unchanged when tool needs no approval`() {
        val description = "Search the local bookshelf."
        assertEquals(description, decorate("search_books", description))
    }
}
