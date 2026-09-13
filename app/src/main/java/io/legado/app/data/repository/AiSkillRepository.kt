package io.legado.app.data.repository

import io.legado.app.data.dao.AiSkillDao
import io.legado.app.data.entities.AiSkill
import io.legado.app.domain.gateway.AiSkillGateway
import io.legado.app.domain.usecase.SkillImportConflictException
import io.legado.app.domain.usecase.SkillPackageCodec
import io.legado.app.domain.usecase.SkillRepoUrlImporter
import io.legado.app.utils.AiIdListCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File

class AiSkillRepository(
    private val dao: AiSkillDao,
) : AiSkillGateway {

    private val seedMutex = Mutex()
    @Volatile
    private var seeded = false

    private val docsRoot: File
        get() = File(appCtx.filesDir, "ai_skills").also { it.mkdirs() }

    override fun observeAll(): Flow<List<AiSkill>> = dao.observeAll()

    override suspend fun getAll(): List<AiSkill> = withContext(Dispatchers.IO) {
        ensureSeeded()
        dao.getAll()
    }

    override suspend fun getById(skillId: String): AiSkill? = withContext(Dispatchers.IO) {
        ensureSeeded()
        dao.getById(skillId)
    }

    override suspend fun save(skill: AiSkill) = withContext(Dispatchers.IO) {
        ensureSeeded()
        dao.insert(skill.copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun delete(skillId: String): Boolean = withContext(Dispatchers.IO) {
        ensureSeeded()
        if (dao.getById(skillId) == null) return@withContext false
        dao.delete(skillId)
        File(docsRoot, skillId).deleteRecursively()
        true
    }

    override suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        if (seeded) return@withContext
        seedMutex.withLock {
            if (seeded) return@withLock
            // Drop obsolete whitelist-era builtins (ids no longer shipped under assets/ai_skills).
            val assetIds = listAssetSkillIds().toSet()
            dao.getAll()
                .filter { it.builtin && it.skillId !in assetIds }
                .forEach { obsolete ->
                    dao.delete(obsolete.skillId)
                    File(docsRoot, obsolete.skillId).deleteRecursively()
                }
            for (skillId in assetIds) {
                seedAssetSkillIfNeeded(skillId)
            }
            seeded = true
        }
    }

    private fun listAssetSkillIds(): List<String> =
        runCatching {
            appCtx.assets.list(ASSET_SKILLS_ROOT)
                ?.filter { id ->
                    id.isNotBlank() && runCatching {
                        appCtx.assets.open("$ASSET_SKILLS_ROOT/$id/${SkillPackageCodec.DOC_ID_SKILL}.md")
                            .close()
                        true
                    }.getOrDefault(false)
                }
                ?.sorted()
                .orEmpty()
        }.getOrDefault(emptyList())

    /**
     * Seed or refresh files for a packaged skill under [ASSET_SKILLS_ROOT].
     * Skips when a non-builtin skill with the same id already exists (user-owned).
     */
    private suspend fun seedAssetSkillIfNeeded(skillId: String) {
        val existing = dao.getById(skillId)
        if (existing != null && !existing.builtin) return
        val skillMd = readAssetText("$ASSET_SKILLS_ROOT/$skillId/${SkillPackageCodec.DOC_ID_SKILL}.md")
            ?: return
        val parsed = when (val result = SkillPackageCodec.parse(skillMd)) {
            is SkillPackageCodec.ParseResult.Error -> return
            is SkillPackageCodec.ParseResult.Success -> result.pkg
        }
        if (parsed.skill.skillId != skillId) return
        val dir = File(docsRoot, skillId).also { it.mkdirs() }
        File(dir, "${SkillPackageCodec.DOC_ID_SKILL}.md").writeText(skillMd)
        // Companion .md under assets/ai_skills/{id}/ only (unique skill files).
        // Shared help docs (e.g. mimoTtsHelp) stay in web/help/md and are linked via frontmatter resources.
        copyAssetSkillResources(skillId, dir)
        // Drop stale copies of linked help docs that used to be duplicated into the skill folder.
        for (docId in parsed.skill.docIds()) {
            if (docId == SkillPackageCodec.DOC_ID_SKILL) continue
            val helpAsset = "web/help/md/$docId.md"
            if (readAssetText(helpAsset) != null) {
                File(dir, "$docId.md").takeIf { it.isFile }?.delete()
            }
        }
        val docIds = mergeDocIds(listDocIdsOnDisk(skillId), parsed.skill.docIds())
        val maxSort = dao.getAll().maxOfOrNull { it.sortOrder } ?: 0
        dao.insert(
            parsed.skill.copy(
                sortOrder = existing?.sortOrder ?: (maxSort + 10),
                enabled = existing?.enabled ?: true,
                builtin = true,
                docIdsJson = AiSkill.encodeList(docIds),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun copyAssetSkillResources(skillId: String, destDir: File) {
        val names = runCatching { appCtx.assets.list("$ASSET_SKILLS_ROOT/$skillId") }.getOrNull().orEmpty()
        for (name in names) {
            if (!name.endsWith(".md", ignoreCase = true)) continue
            if (name.equals("${SkillPackageCodec.DOC_ID_SKILL}.md", ignoreCase = true)) continue
            val text = readAssetText("$ASSET_SKILLS_ROOT/$skillId/$name") ?: continue
            File(destDir, name).writeText(text)
        }
    }

    private fun readAssetText(path: String): String? =
        runCatching {
            appCtx.assets.open(path).bufferedReader().use { it.readText() }
        }.getOrNull()

    override suspend fun moveSort(skillId: String, direction: Int) = withContext(Dispatchers.IO) {
        ensureSeeded()
        val all = dao.getAll().toMutableList()
        val index = all.indexOfFirst { it.skillId == skillId }
        if (index < 0) return@withContext
        val target = index + direction
        if (target !in all.indices) return@withContext
        val a = all[index]
        val b = all[target]
        all[index] = a.copy(sortOrder = b.sortOrder, updatedAt = System.currentTimeMillis())
        all[target] = b.copy(sortOrder = a.sortOrder, updatedAt = System.currentTimeMillis())
        all.forEachIndexed { i, skill ->
            dao.insert(skill.copy(sortOrder = (i + 1) * 10))
        }
    }

    override suspend fun skillsForMode(conversationType: String): List<AiSkill> =
        withContext(Dispatchers.IO) {
            ensureSeeded()
            dao.getAll().filter { it.enabled && it.matchesMode(conversationType) }
        }

    override suspend fun catalogTextForMode(conversationType: String, skillIdsCsv: String?): String? =
        withContext(Dispatchers.IO) {
            ensureSeeded()
            var skills = skillsForMode(conversationType)
            if (skillIdsCsv != null) {
                val allow = AiIdListCodec.parse(skillIdsCsv).toSet()
                skills = skills.filter { it.skillId in allow }
            }
            if (skills.isEmpty()) return@withContext null
            buildString {
                appendLine("<ai_skills>")
                appendLine("Progressive disclosure:")
                appendLine("1. Metadata below is always loaded — use it to decide relevance.")
                appendLine("2. When relevant, load core instructions: search_rule_help scope=skill:{id} doc=SKILL")
                appendLine("3. Load listed resources only when needed: search_rule_help scope=skill:{id} doc={resourceId}")
                appendLine("4. Create/update/delete skills via write_file(\"skill://{id}\") / delete_file(\"skill://{id}\"); install from repo URL via install_skill_from_url (list_skills / read_file(\"skill://{id}\") to inspect; mutations need confirmation).")
                appendLine("Do not paste full skill docs into user-facing replies.")
                appendLine()
                for (skill in skills.sortedBy { it.sortOrder }) {
                    appendLine("- `${skill.skillId}` — ${skill.name}")
                    val desc = skill.description.ifBlank { skill.hint }.trim()
                    if (desc.isNotBlank()) appendLine("  description: $desc")
                    appendLine("  core: SKILL (on demand)")
                    val resources = resourceDocIds(skill)
                    if (resources.isNotEmpty()) {
                        appendLine("  resources: ${resources.joinToString(", ")} (very on demand)")
                    }
                }
                append("</ai_skills>")
            }.trim().takeIf { it.isNotBlank() }
        }

    override suspend fun readDocMarkdown(skillId: String, docId: String): String? =
        withContext(Dispatchers.IO) {
            ensureSeeded()
            val skill = dao.getById(skillId) ?: return@withContext null
            if (docId == SkillPackageCodec.DOC_ID_SKILL) {
                return@withContext readSkillMarkdown(skill)
            }
            val imported = File(docsRoot, "$skillId/$docId.md")
            if (imported.isFile) {
                return@withContext imported.readText()
            }
            if (docId !in skill.docIds()) return@withContext null
            runCatching {
                appCtx.assets.open("web/help/md/$docId.md").bufferedReader().use { it.readText() }
            }.getOrNull()
        }

    override suspend fun importPackage(raw: String, overwrite: Boolean): Result<Int> =
        withContext(Dispatchers.IO) {
            ensureSeeded()
            val parsed = when (val result = SkillPackageCodec.parse(raw)) {
                is SkillPackageCodec.ParseResult.Error ->
                    return@withContext Result.failure(IllegalArgumentException(result.message))
                is SkillPackageCodec.ParseResult.Success -> result.pkg
            }
            val existing = dao.getById(parsed.skill.skillId)
            if (existing != null && !overwrite) {
                return@withContext Result.failure(
                    SkillImportConflictException(listOf(parsed.skill.skillId)),
                )
            }
            val maxSort = dao.getAll().maxOfOrNull { it.sortOrder } ?: 0
            val skill = parsed.skill.copy(
                sortOrder = existing?.sortOrder ?: (maxSort + 10),
                enabled = existing?.enabled ?: true,
                builtin = false,
                updatedAt = System.currentTimeMillis(),
            )
            writeUserSkillMarkdown(skill, parsed.markdown)
            Result.success(1)
        }

    override suspend fun importFromUrl(
        url: String,
        overwrite: Boolean,
        onProgress: io.legado.app.domain.model.AiToolProgressCallback?,
    ): Result<AiSkill> =
        withContext(Dispatchers.IO) {
            ensureSeeded()
            onProgress?.onProgress(0.02f, "Resolving URL")
            val fetched = SkillRepoUrlImporter.fetch(url, onProgress).getOrElse {
                return@withContext Result.failure(it)
            }
            onProgress?.onProgress(0.88f, "Parsing SKILL.md")
            val parsed = when (val result = SkillPackageCodec.parse(fetched.markdown)) {
                is SkillPackageCodec.ParseResult.Error ->
                    return@withContext Result.failure(IllegalArgumentException(result.message))
                is SkillPackageCodec.ParseResult.Success -> result.pkg
            }
            val existing = dao.getById(parsed.skill.skillId)
            if (existing != null && !overwrite) {
                return@withContext Result.failure(
                    SkillImportConflictException(listOf(parsed.skill.skillId)),
                )
            }
            onProgress?.onProgress(0.94f, "Saving skill")
            val maxSort = dao.getAll().maxOfOrNull { it.sortOrder } ?: 0
            val skill = parsed.skill.copy(
                sortOrder = existing?.sortOrder ?: (maxSort + 10),
                enabled = existing?.enabled ?: true,
                builtin = false,
                updatedAt = System.currentTimeMillis(),
            )
            writeUserSkillMarkdown(skill, parsed.markdown, fetched.companions)
            onProgress?.onProgress(1f, "Done")
            Result.success(dao.getById(skill.skillId) ?: skill)
        }

    override suspend fun saveUserSkillMarkdown(markdown: String): Result<AiSkill> =
        withContext(Dispatchers.IO) {
            ensureSeeded()
            val parsed = when (val result = SkillPackageCodec.parse(markdown)) {
                is SkillPackageCodec.ParseResult.Error ->
                    return@withContext Result.failure(IllegalArgumentException(result.message))
                is SkillPackageCodec.ParseResult.Success -> result.pkg
            }
            val existing = dao.getById(parsed.skill.skillId)
            val maxSort = dao.getAll().maxOfOrNull { it.sortOrder } ?: 0
            val toSave = parsed.skill.copy(
                sortOrder = existing?.sortOrder ?: (maxSort + 10),
                enabled = existing?.enabled ?: true,
                builtin = false,
                updatedAt = System.currentTimeMillis(),
            )
            writeUserSkillMarkdown(toSave, parsed.markdown)
            Result.success(toSave)
        }

    override suspend fun loadMarkdownForEdit(skillId: String): Result<String> =
        withContext(Dispatchers.IO) {
            ensureSeeded()
            val skill = dao.getById(skillId)
                ?: return@withContext Result.failure(IllegalArgumentException("Skill not found"))
            Result.success(readSkillMarkdown(skill))
        }

    override suspend fun loadResourceDocForEdit(skillId: String, docId: String): Result<String> =
        withContext(Dispatchers.IO) {
            ensureSeeded()
            val skill = dao.getById(skillId)
                ?: return@withContext Result.failure(IllegalArgumentException("Skill not found: $skillId"))
            if (docId == SkillPackageCodec.DOC_ID_SKILL) {
                return@withContext Result.success(readSkillMarkdown(skill))
            }
            // Prefer user override on disk.
            val overrideFile = File(docsRoot, "$skillId/$docId.md")
            if (overrideFile.isFile) {
                return@withContext Result.success(overrideFile.readText())
            }
            // Fall back to shared help doc in assets.
            if (docId !in skill.docIds()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Resource doc not declared by skill: $docId"),
                )
            }
            val assetText = runCatching {
                appCtx.assets.open("web/help/md/$docId.md").bufferedReader().use { it.readText() }
            }.getOrNull()
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Resource doc not found: $docId"),
                )
            Result.success(assetText)
        }

    override suspend fun saveResourceDoc(
        skillId: String,
        docId: String,
        content: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        ensureSeeded()
        val skill = dao.getById(skillId)
            ?: return@withContext Result.failure(IllegalArgumentException("Skill not found: $skillId"))
        val safeId = docId.trim().takeIf {
            it.isNotBlank() && !it.contains('/') && !it.contains('\\')
        } ?: return@withContext Result.failure(IllegalArgumentException("Invalid docId: $docId"))
        if (safeId == SkillPackageCodec.DOC_ID_SKILL) {
            // For SKILL.md body edits, delegate to existing saveUserSkillMarkdown path.
            return@withContext saveUserSkillMarkdown(content).map { }
        }
        val dir = File(docsRoot, skillId).also { it.mkdirs() }
        File(dir, "$safeId.md").writeText(content)
        // Ensure docIds includes this resource.
        val currentIds = skill.docIds()
        if (safeId !in currentIds) {
            val updatedIds = currentIds + safeId
            dao.insert(
                skill.copy(
                    docIdsJson = AiSkill.encodeList(updatedIds),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
        Result.success(Unit)
    }

    private suspend fun writeUserSkillMarkdown(
        skill: AiSkill,
        markdown: String,
        companions: Map<String, String> = emptyMap(),
    ) {
        val dir = File(docsRoot, skill.skillId).also { it.mkdirs() }
        File(dir, "${SkillPackageCodec.DOC_ID_SKILL}.md").writeText(markdown)
        for ((docId, text) in companions) {
            val safeId = docId.trim().takeIf { it.isNotBlank() && !it.contains('/') && !it.contains('\\') }
                ?: continue
            File(dir, "$safeId.md").writeText(text)
        }
        // Preserve companion *.md resources already on disk; merge with frontmatter-linked help ids.
        val docIds = mergeDocIds(listDocIdsOnDisk(skill.skillId), skill.docIds())
        dao.insert(
            skill.copy(
                docIdsJson = AiSkill.encodeList(docIds),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun exportPackage(skillId: String): Result<String> = withContext(Dispatchers.IO) {
        ensureSeeded()
        val skill = dao.getById(skillId)
            ?: return@withContext Result.failure(IllegalArgumentException("Skill not found"))
        Result.success(readSkillMarkdown(skill))
    }

    override fun listDocMetas(skill: AiSkill): List<SkillPackageCodec.SkillDoc> {
        val ids = mergeDocIds(listDocIdsOnDisk(skill.skillId), skill.docIds())
        return ids.map { id ->
            SkillPackageCodec.SkillDoc(
                id = id,
                title = if (id == SkillPackageCodec.DOC_ID_SKILL) skill.name else id,
            )
        }
    }

    /** Non-SKILL.md docs — progressive disclosure level 3 (folder files + linked help ids). */
    private fun resourceDocIds(skill: AiSkill): List<String> =
        mergeDocIds(listDocIdsOnDisk(skill.skillId), skill.docIds())
            .filter { it != SkillPackageCodec.DOC_ID_SKILL }

    private fun mergeDocIds(diskIds: List<String>, declaredIds: List<String>): List<String> {
        val ordered = linkedSetOf<String>()
        ordered += SkillPackageCodec.DOC_ID_SKILL
        diskIds.filter { it != SkillPackageCodec.DOC_ID_SKILL }.forEach { ordered += it }
        declaredIds.filter { it != SkillPackageCodec.DOC_ID_SKILL }.forEach { ordered += it }
        return ordered.toList()
    }

    private fun listDocIdsOnDisk(skillId: String): List<String> {
        val dir = File(docsRoot, skillId)
        if (!dir.isDirectory) return listOf(SkillPackageCodec.DOC_ID_SKILL)
        val extras = dir.listFiles()
            ?.filter { it.isFile && it.extension.equals("md", ignoreCase = true) }
            ?.map { it.nameWithoutExtension }
            ?.filter { it.isNotBlank() && it != SkillPackageCodec.DOC_ID_SKILL }
            ?.sorted()
            .orEmpty()
        return listOf(SkillPackageCodec.DOC_ID_SKILL) + extras
    }

    private fun readSkillMarkdown(skill: AiSkill): String {
        val file = File(docsRoot, "${skill.skillId}/${SkillPackageCodec.DOC_ID_SKILL}.md")
        if (file.isFile) return file.readText()
        return SkillPackageCodec.export(skill)
    }

    companion object {
        private const val ASSET_SKILLS_ROOT = "ai_skills"
    }
}
