package io.legado.app.domain.gateway

import io.legado.app.data.entities.AiSkill
import io.legado.app.domain.model.AiToolProgressCallback
import io.legado.app.domain.usecase.SkillPackageCodec
import kotlinx.coroutines.flow.Flow

interface AiSkillGateway {
    fun observeAll(): Flow<List<AiSkill>>
    suspend fun getAll(): List<AiSkill>
    suspend fun getById(skillId: String): AiSkill?
    suspend fun save(skill: AiSkill)
    suspend fun delete(skillId: String): Boolean
    suspend fun ensureSeeded()
    suspend fun moveSort(skillId: String, direction: Int)
    /** Skills matching conversation type (chat/writing) for prompt metadata injection. */
    suspend fun skillsForMode(conversationType: String): List<AiSkill>
    /**
     * Catalog lines for prompt injection.
     * @param skillIdsCsv null = all globally-enabled skills for [conversationType];
     *   otherwise CSV allowlist intersected with globally-enabled + mode.
     */
    suspend fun catalogTextForMode(conversationType: String, skillIdsCsv: String? = null): String?
    /** Resolve markdown for a skill doc (asset or imported file). */
    suspend fun readDocMarkdown(skillId: String, docId: String): String?
    /**
     * Import a single `SKILL.md`.
     * @return 1 on success
     * @throws io.legado.app.domain.usecase.SkillImportConflictException when id exists and [overwrite] is false
     */
    suspend fun importPackage(raw: String, overwrite: Boolean = false): Result<Int>
    /**
     * Fetch `SKILL.md` from a git-hosting / raw URL (GitHub / Gitee / GitLab / direct) and import.
     * Also tries to download companion `.md` resources listed in frontmatter from the same directory.
     */
    suspend fun importFromUrl(
        url: String,
        overwrite: Boolean = false,
        onProgress: AiToolProgressCallback? = null,
    ): Result<AiSkill>
    /** Create or update a user skill from full `SKILL.md` markdown. */
    suspend fun saveUserSkillMarkdown(markdown: String): Result<AiSkill>
    /** Load full `SKILL.md` text for the edit dialog. */
    suspend fun loadMarkdownForEdit(skillId: String): Result<String>
    /**
     * Load a resource doc (e.g. mimoTtsHelp) for editing.
     * Checks user override first, then falls back to assets/web/help/md/.
     */
    suspend fun loadResourceDocForEdit(skillId: String, docId: String): Result<String>
    /**
     * Write/replace a resource doc override under [skillId] (e.g. mimoTtsHelp.md).
     * After write, [readDocMarkdown] will return this content instead of the built-in version.
     */
    suspend fun saveResourceDoc(skillId: String, docId: String, content: String): Result<Unit>
    /** Export one skill as `SKILL.md`. */
    suspend fun exportPackage(skillId: String): Result<String>
    fun listDocMetas(skill: AiSkill): List<SkillPackageCodec.SkillDoc>
}
