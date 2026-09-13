package io.legado.app.utils

import com.google.gson.reflect.TypeToken
import io.legado.app.domain.prompt.PromptBlockId
import io.legado.app.domain.prompt.PromptBlockSpec
import io.legado.app.domain.prompt.PromptPipelinePreset

object PromptPipelineCodec {
    private val blockListType = object : TypeToken<List<PromptBlockSpec>>() {}.type

    fun encodeBlocks(blocks: List<PromptBlockSpec>): String =
        GSON.toJson(blocks)

    fun decodeBlocks(json: String): List<PromptBlockSpec> =
        runCatching {
            GSON.fromJson<List<PromptBlockSpec>>(json, blockListType)
        }.getOrNull().orEmpty()

    fun toEntity(preset: PromptPipelinePreset): io.legado.app.data.entities.AiPromptPipelinePreset =
        io.legado.app.data.entities.AiPromptPipelinePreset(
            id = preset.id,
            name = preset.name,
            mode = preset.mode,
            blocksJson = encodeBlocks(preset.blocks),
            isDefault = preset.isDefault,
            updatedAt = preset.updatedAt,
        )

    fun fromEntity(entity: io.legado.app.data.entities.AiPromptPipelinePreset): PromptPipelinePreset =
        PromptPipelinePreset(
            id = entity.id,
            name = entity.name,
            mode = entity.mode,
            blocks = decodeBlocks(entity.blocksJson)
                .filter { it.id != PromptBlockId.WritingActionContinue },
            isDefault = entity.isDefault,
            updatedAt = entity.updatedAt,
        )
}
