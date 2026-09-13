package io.legado.app.ui.config.ai

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.domain.prompt.PromptBlockId
import io.legado.app.domain.prompt.PromptBlockPosition
import io.legado.app.domain.prompt.PromptPipelineDefaults
import io.legado.app.domain.prompt.PromptPipelineMode
import io.legado.app.domain.prompt.PromptPipelinePreset

@Composable
fun promptPresetTitle(preset: PromptPipelinePreset): String = when (preset.id) {
    PromptPipelineDefaults.ID_CHAT_DEFAULT -> stringResource(R.string.ai_pipeline_preset_chat)
    PromptPipelineDefaults.ID_WRITING_ROLEPLAY -> stringResource(R.string.ai_pipeline_preset_writing_roleplay)
    PromptPipelineDefaults.ID_WRITING_AUTHOR -> stringResource(R.string.ai_pipeline_preset_writing_author)
    PromptPipelineDefaults.ID_WRITING_HELP_REPLY -> stringResource(R.string.ai_pipeline_preset_writing_help_reply)
    else -> preset.name
}

@Composable
fun promptModeLabel(mode: String): String = when (PromptPipelineMode.fromValue(mode)) {
    PromptPipelineMode.Chat -> stringResource(R.string.ai_pipeline_mode_chat)
    PromptPipelineMode.WritingRoleplay -> stringResource(R.string.ai_pipeline_mode_writing_roleplay)
    PromptPipelineMode.WritingAuthor -> stringResource(R.string.ai_pipeline_mode_writing_author)
    PromptPipelineMode.WritingHelpReply -> stringResource(R.string.ai_pipeline_mode_writing_help_reply)
    null -> mode
}

@Composable
fun promptBlockTitle(id: PromptBlockId): String = when (id) {
    PromptBlockId.Main -> stringResource(R.string.ai_pipeline_block_main)
    PromptBlockId.Persona -> stringResource(R.string.ai_pipeline_block_persona)
    PromptBlockId.Character -> stringResource(R.string.ai_pipeline_block_character)
    PromptBlockId.WorldCatalog -> stringResource(R.string.ai_pipeline_block_world_catalog)
    PromptBlockId.WorldEntries -> stringResource(R.string.ai_pipeline_block_world_entries)
    PromptBlockId.MemoryTables -> stringResource(R.string.ai_pipeline_block_memory_tables)
    PromptBlockId.CharacterCardsCatalog -> stringResource(R.string.ai_pipeline_block_character_cards_catalog)
    PromptBlockId.UserMemory -> stringResource(R.string.ai_pipeline_block_user_memory)
    PromptBlockId.LocalTime -> stringResource(R.string.ai_pipeline_block_local_time)
    PromptBlockId.UserCard -> stringResource(R.string.ai_pipeline_block_user_card)
    PromptBlockId.WorkspaceIndex -> stringResource(R.string.ai_pipeline_block_workspace_index)
    PromptBlockId.WorkspacePrefetch -> stringResource(R.string.ai_pipeline_block_workspace_prefetch)
    PromptBlockId.WritingSubmode -> stringResource(R.string.ai_pipeline_block_writing_submode)
    PromptBlockId.WritingInputFormat -> stringResource(R.string.ai_pipeline_block_writing_input_format)
    PromptBlockId.WritingXmlNotice -> stringResource(R.string.ai_pipeline_block_writing_xml_notice)
    PromptBlockId.WritingIdentity -> stringResource(R.string.ai_pipeline_block_writing_identity)
    PromptBlockId.MultiCharacter -> stringResource(R.string.ai_pipeline_block_multi_character)
    PromptBlockId.CurrentSpeaker -> stringResource(R.string.ai_pipeline_block_current_speaker)
    PromptBlockId.WritingStyleGuide -> stringResource(R.string.ai_pipeline_block_writing_style_guide)
    PromptBlockId.WritingActionContinue -> stringResource(R.string.ai_pipeline_block_writing_action_continue)
    PromptBlockId.PostHistory -> stringResource(R.string.ai_pipeline_block_post_history)
    PromptBlockId.HelpReplySystem -> stringResource(R.string.ai_pipeline_block_help_reply_system)
    PromptBlockId.MultiBubbleProtocol -> stringResource(R.string.ai_pipeline_block_multi_bubble)
    PromptBlockId.SkillsCatalog -> stringResource(R.string.ai_pipeline_block_skills_catalog)
}

@Composable
fun promptBlockDescription(id: PromptBlockId): String = when (id) {
    PromptBlockId.Main -> stringResource(R.string.ai_pipeline_block_main_desc)
    PromptBlockId.Persona -> stringResource(R.string.ai_pipeline_block_persona_desc)
    PromptBlockId.Character -> stringResource(R.string.ai_pipeline_block_character_desc)
    PromptBlockId.WorldCatalog -> stringResource(R.string.ai_pipeline_block_world_catalog_desc)
    PromptBlockId.WorldEntries -> stringResource(R.string.ai_pipeline_block_world_entries_desc)
    PromptBlockId.MemoryTables -> stringResource(R.string.ai_pipeline_block_memory_tables_desc)
    PromptBlockId.CharacterCardsCatalog -> stringResource(R.string.ai_pipeline_block_character_cards_catalog_desc)
    PromptBlockId.UserMemory -> stringResource(R.string.ai_pipeline_block_user_memory_desc)
    PromptBlockId.LocalTime -> stringResource(R.string.ai_pipeline_block_local_time_desc)
    PromptBlockId.UserCard -> stringResource(R.string.ai_pipeline_block_user_card_desc)
    PromptBlockId.WorkspaceIndex -> stringResource(R.string.ai_pipeline_block_workspace_index_desc)
    PromptBlockId.WorkspacePrefetch -> stringResource(R.string.ai_pipeline_block_workspace_prefetch_desc)
    PromptBlockId.WritingSubmode -> stringResource(R.string.ai_pipeline_block_writing_submode_desc)
    PromptBlockId.WritingInputFormat -> stringResource(R.string.ai_pipeline_block_writing_input_format_desc)
    PromptBlockId.WritingXmlNotice -> stringResource(R.string.ai_pipeline_block_writing_xml_notice_desc)
    PromptBlockId.WritingIdentity -> stringResource(R.string.ai_pipeline_block_writing_identity_desc)
    PromptBlockId.MultiCharacter -> stringResource(R.string.ai_pipeline_block_multi_character_desc)
    PromptBlockId.CurrentSpeaker -> stringResource(R.string.ai_pipeline_block_current_speaker_desc)
    PromptBlockId.WritingStyleGuide -> stringResource(R.string.ai_pipeline_block_writing_style_guide_desc)
    PromptBlockId.WritingActionContinue -> stringResource(R.string.ai_pipeline_block_writing_action_continue_desc)
    PromptBlockId.PostHistory -> stringResource(R.string.ai_pipeline_block_post_history_desc)
    PromptBlockId.HelpReplySystem -> stringResource(R.string.ai_pipeline_block_help_reply_system_desc)
    PromptBlockId.MultiBubbleProtocol -> stringResource(R.string.ai_pipeline_block_multi_bubble_desc)
    PromptBlockId.SkillsCatalog -> stringResource(R.string.ai_pipeline_block_skills_catalog_desc)
}

@Composable
fun promptBlockPositionLabel(position: PromptBlockPosition, depth: Int): String = when (position) {
    PromptBlockPosition.Prefix -> stringResource(R.string.ai_pipeline_position_prefix)
    PromptBlockPosition.InChat -> stringResource(R.string.ai_pipeline_position_in_chat, depth)
}
