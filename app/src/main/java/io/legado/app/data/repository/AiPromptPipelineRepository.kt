package io.legado.app.data.repository

import io.legado.app.data.dao.AiPromptPipelinePresetDao
import io.legado.app.domain.gateway.AiPromptPipelineGateway
import io.legado.app.domain.prompt.PromptBlockId
import io.legado.app.domain.prompt.PromptBlockPosition
import io.legado.app.domain.prompt.PromptBlockSpec
import io.legado.app.domain.prompt.PromptPipelineDefaults
import io.legado.app.domain.prompt.PromptPipelineMode
import io.legado.app.domain.prompt.PromptPipelinePreset
import io.legado.app.utils.PromptPipelineCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AiPromptPipelineRepository(
    private val dao: AiPromptPipelinePresetDao,
) : AiPromptPipelineGateway {

    override fun observeAll(): Flow<List<PromptPipelinePreset>> =
        dao.observeAll().map { list -> list.map(PromptPipelineCodec::fromEntity) }

    override suspend fun getAll(): List<PromptPipelinePreset> = withContext(Dispatchers.IO) {
        dao.getAll().map(PromptPipelineCodec::fromEntity)
    }

    override suspend fun getById(id: String): PromptPipelinePreset? = withContext(Dispatchers.IO) {
        dao.getById(id)?.let(PromptPipelineCodec::fromEntity)
    }

    override suspend fun getPresetForMode(mode: PromptPipelineMode): PromptPipelinePreset? =
        withContext(Dispatchers.IO) {
            val stored = dao.getDefaultForMode(mode.value)?.let(PromptPipelineCodec::fromEntity)
                ?: PromptPipelineDefaults.presetForMode(mode)
            // Only seed missing blocks; never rewrite user position / order / depth.
            withMultiBubbleProtocolIfNeeded(
                withSkillsCatalogIfNeeded(
                    withLocalTimeIfNeeded(
                        withCharacterCardsCatalogIfNeeded(
                            withWorldEntriesIfNeeded(stored, mode),
                            mode,
                        ),
                        mode,
                    ),
                    mode,
                ),
                mode,
            )
        }

    override suspend fun upsert(preset: PromptPipelinePreset) = withContext(Dispatchers.IO) {
        dao.upsert(PromptPipelineCodec.toEntity(preset))
    }

    override suspend fun deleteCustom(id: String) = withContext(Dispatchers.IO) {
        dao.deleteCustom(id)
    }

    override suspend fun ensureDefaults() = withContext(Dispatchers.IO) {
        val existing = dao.getAll().associateBy { it.id }
        PromptPipelineDefaults.allPresets().forEach { preset ->
            val row = existing[preset.id]
            if (row == null) {
                dao.upsert(PromptPipelineCodec.toEntity(preset))
                return@forEach
            }
            val current = PromptPipelineCodec.fromEntity(row)
            val mode = PromptPipelineMode.fromValue(current.mode) ?: return@forEach
            // Only seed missing blocks. Do not rewrite position / depth / order —
            // those are user-configurable on the pipeline screen.
            val patched = withWorldEntriesIfNeeded(current, mode)
                .let { withCharacterCardsCatalogIfNeeded(it, mode) }
                .let { withLocalTimeIfNeeded(it, mode) }
                .let { withSkillsCatalogIfNeeded(it, mode) }
                .let { withMultiBubbleProtocolIfNeeded(it, mode) }
            if (patched.blocks != current.blocks) {
                dao.upsert(
                    PromptPipelineCodec.toEntity(
                        patched.copy(updatedAt = System.currentTimeMillis()),
                    ),
                )
            }
        }
    }

    /**
     * Built-in writing presets may have been seeded without [PromptBlockId.WorldEntries];
     * inject it so lore keyword scan works without requiring a pipeline reset.
     */
    private fun withWorldEntriesIfNeeded(
        preset: PromptPipelinePreset,
        mode: PromptPipelineMode,
    ): PromptPipelinePreset {
        if (mode != PromptPipelineMode.WritingRoleplay && mode != PromptPipelineMode.WritingAuthor) {
            return preset
        }
        if (preset.blocks.any { it.id == PromptBlockId.WorldEntries }) return preset
        val worldEntries = PromptBlockSpec(
            id = PromptBlockId.WorldEntries,
            enabled = true,
            order = 110,
        )
        val blocks = preset.blocks.toMutableList()
        val speakerAt = blocks.indexOfFirst { it.id == PromptBlockId.CurrentSpeaker }
        val indexAt = blocks.indexOfFirst { it.id == PromptBlockId.WorkspaceIndex }
        val anchorAt = maxOf(speakerAt, indexAt)
        if (anchorAt >= 0) {
            blocks.add(anchorAt + 1, worldEntries)
        } else {
            val postAt = blocks.indexOfFirst { it.id == PromptBlockId.PostHistory }
            if (postAt >= 0) blocks.add(postAt + 1, worldEntries)
            else blocks.add(worldEntries)
        }
        return preset.copy(blocks = blocks)
    }

    /** Seeded chat presets without [PromptBlockId.CharacterCardsCatalog] get it after memory tables. */
    private fun withCharacterCardsCatalogIfNeeded(
        preset: PromptPipelinePreset,
        mode: PromptPipelineMode,
    ): PromptPipelinePreset {
        if (mode != PromptPipelineMode.Chat) return preset
        if (preset.blocks.any { it.id == PromptBlockId.CharacterCardsCatalog }) return preset
        val catalog = PromptBlockSpec(
            id = PromptBlockId.CharacterCardsCatalog,
            enabled = true,
            order = 25,
        )
        val blocks = preset.blocks.toMutableList()
        val memAt = blocks.indexOfFirst { it.id == PromptBlockId.MemoryTables }
        if (memAt >= 0) {
            blocks.add(memAt + 1, catalog)
        } else {
            blocks.add(catalog)
        }
        return preset.copy(blocks = blocks)
    }

    /**
     * Seeded chat presets without [PromptBlockId.LocalTime] get it as InChat
     * (clock in Prefix would bust DeepSeek automatic prefix cache every turn).
     * Existing rows keep user-configured position / depth.
     */
    private fun withLocalTimeIfNeeded(
        preset: PromptPipelinePreset,
        mode: PromptPipelineMode,
    ): PromptPipelinePreset {
        if (mode != PromptPipelineMode.Chat) return preset
        if (preset.blocks.any { it.id == PromptBlockId.LocalTime }) return preset
        val localTime = PromptBlockSpec(
            id = PromptBlockId.LocalTime,
            enabled = true,
            order = 5,
            position = PromptBlockPosition.InChat,
            depth = 0,
        )
        val blocks = preset.blocks.toMutableList()
        val mainAt = blocks.indexOfFirst { it.id == PromptBlockId.Main }
        if (mainAt >= 0) {
            blocks.add(mainAt + 1, localTime)
        } else {
            blocks.add(0, localTime)
        }
        return preset.copy(blocks = blocks)
    }

    /**
     * Seeded presets without [PromptBlockId.SkillsCatalog] get skill workflow injection.
     */
    private fun withSkillsCatalogIfNeeded(
        preset: PromptPipelinePreset,
        mode: PromptPipelineMode,
    ): PromptPipelinePreset {
        if (mode == PromptPipelineMode.WritingHelpReply) return preset
        if (preset.blocks.any { it.id == PromptBlockId.SkillsCatalog }) return preset
        val skills = PromptBlockSpec(
            id = PromptBlockId.SkillsCatalog,
            enabled = true,
            order = if (mode == PromptPipelineMode.Chat) 6 else 22,
            maxTokens = 600,
        )
        val blocks = preset.blocks.toMutableList()
        val anchorId = when (mode) {
            PromptPipelineMode.Chat -> PromptBlockId.LocalTime
            else -> PromptBlockId.WritingXmlNotice
        }
        val anchorAt = blocks.indexOfFirst { it.id == anchorId }
        if (anchorAt >= 0) {
            blocks.add(anchorAt + 1, skills)
        } else {
            val mainAt = blocks.indexOfFirst { it.id == PromptBlockId.Main }
            if (mainAt >= 0) blocks.add(mainAt + 1, skills)
            else blocks.add(0, skills)
        }
        return preset.copy(blocks = blocks)
    }

    /**
     * Seeded chat / roleplay presets without [PromptBlockId.MultiBubbleProtocol]
     * get the `<msg>` protocol block.
     */
    private fun withMultiBubbleProtocolIfNeeded(
        preset: PromptPipelinePreset,
        mode: PromptPipelineMode,
    ): PromptPipelinePreset {
        if (mode != PromptPipelineMode.Chat && mode != PromptPipelineMode.WritingRoleplay) {
            return preset
        }
        if (preset.blocks.any { it.id == PromptBlockId.MultiBubbleProtocol }) return preset
        val multiBubble = PromptBlockSpec(
            id = PromptBlockId.MultiBubbleProtocol,
            enabled = true,
            order = if (mode == PromptPipelineMode.Chat) 8 else 25,
        )
        val blocks = preset.blocks.toMutableList()
        val anchorId = if (mode == PromptPipelineMode.Chat) {
            PromptBlockId.LocalTime
        } else {
            PromptBlockId.WritingXmlNotice
        }
        val anchorAt = blocks.indexOfFirst { it.id == anchorId }
        if (anchorAt >= 0) {
            blocks.add(anchorAt + 1, multiBubble)
        } else {
            val mainAt = blocks.indexOfFirst { it.id == PromptBlockId.Main }
            if (mainAt >= 0) blocks.add(mainAt + 1, multiBubble)
            else blocks.add(multiBubble)
        }
        return preset.copy(blocks = blocks)
    }
}
