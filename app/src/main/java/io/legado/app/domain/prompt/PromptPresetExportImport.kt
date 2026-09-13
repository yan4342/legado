package io.legado.app.domain.prompt

import io.legado.app.data.entities.AiPromptTemplate
import io.legado.app.domain.gateway.AiPromptPipelineGateway
import io.legado.app.domain.gateway.AiPromptTemplateGateway
import io.legado.app.utils.GSON
import io.legado.app.utils.PromptPipelineCodec

data class PromptPresetExport(
    val version: Int = 1,
    val name: String,
    val mode: String,
    val pipeline: PromptPipelinePreset,
    val templates: Map<String, String> = emptyMap(),
)

class PromptPresetExporter(
    private val pipelineGateway: AiPromptPipelineGateway,
    private val promptTemplateGateway: AiPromptTemplateGateway,
) {
    suspend fun export(presetId: String, includeTemplates: Boolean = true): String {
        val preset = pipelineGateway.getById(presetId)
            ?: error("Pipeline preset not found")
        val templates = if (!includeTemplates) {
            emptyMap()
        } else {
            collectTemplateKeys(preset).associateWith { key ->
                promptTemplateGateway.getPrompt(key)
            }
        }
        val export = PromptPresetExport(
            name = preset.name,
            mode = preset.mode,
            pipeline = preset,
            templates = templates,
        )
        return GSON.toJson(export)
    }

    private fun collectTemplateKeys(preset: PromptPipelinePreset): Set<String> {
        val keys = linkedSetOf<String>()
        preset.blocks.forEach { block ->
            when (block.id) {
                PromptBlockId.Main -> keys.add(AiPromptTemplate.CHAT_SYSTEM_PROMPT)
                PromptBlockId.WritingSubmode -> {
                    keys.add(AiPromptTemplate.WRITING_SUBMODE_ROLEPLAY)
                    keys.add(AiPromptTemplate.WRITING_SUBMODE_AUTHOR)
                }
                PromptBlockId.WritingInputFormat -> {
                    keys.add(AiPromptTemplate.WRITING_USER_INPUT_FORMAT)
                    keys.add(AiPromptTemplate.WRITING_USER_INPUT_FORMAT_ROLEPLAY)
                }
                PromptBlockId.WritingIdentity -> keys.add(AiPromptTemplate.WRITING_ROLEPLAY_IDENTITY)
                PromptBlockId.MultiCharacter -> keys.add(AiPromptTemplate.MULTI_CHARACTER_INSTRUCTION)
                PromptBlockId.HelpReplySystem -> keys.add(AiPromptTemplate.HELP_REPLY_SYSTEM_PROMPT)
                PromptBlockId.MultiBubbleProtocol -> {
                    keys.add(AiPromptTemplate.CHAT_MULTI_BUBBLE_PROTOCOL)
                    keys.add(AiPromptTemplate.ROLEPLAY_MULTI_BUBBLE_PROTOCOL)
                }
                else -> Unit
            }
        }
        return keys
    }
}

class PromptPresetImporter(
    private val pipelineGateway: AiPromptPipelineGateway,
    private val promptTemplateGateway: AiPromptTemplateGateway,
) {
    suspend fun import(json: String, overwriteTemplates: Boolean = false): PromptPipelinePreset {
        val export = GSON.fromJson(json, PromptPresetExport::class.java)
            ?: error("Invalid preset JSON")
        val preset = export.pipeline.copy(
            id = "import_${System.currentTimeMillis()}",
            isDefault = false,
            updatedAt = System.currentTimeMillis(),
        )
        pipelineGateway.upsert(preset)
        if (overwriteTemplates) {
            export.templates.forEach { (key, content) ->
                promptTemplateGateway.upsert(
                    io.legado.app.data.entities.AiPromptTemplate(
                        promptKey = key,
                        content = content,
                    ),
                )
            }
        }
        return preset
    }
}
