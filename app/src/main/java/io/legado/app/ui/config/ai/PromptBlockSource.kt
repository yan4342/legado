package io.legado.app.ui.config.ai

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.domain.prompt.PromptBlockId

enum class PromptBlockSourceKind {
    Template,
    Runtime,
    Fixed,
}

data class PromptBlockSourceInfo(
    val kind: PromptBlockSourceKind,
    @param:StringRes val sourceRes: Int,
)

@Composable
fun promptBlockSourceKindLabel(kind: PromptBlockSourceKind): String = when (kind) {
    PromptBlockSourceKind.Template -> stringResource(R.string.ai_pipeline_source_kind_template)
    PromptBlockSourceKind.Runtime -> stringResource(R.string.ai_pipeline_source_kind_runtime)
    PromptBlockSourceKind.Fixed -> stringResource(R.string.ai_pipeline_source_kind_fixed)
}

fun promptBlockSourceInfo(id: PromptBlockId): PromptBlockSourceInfo = when (id) {
    PromptBlockId.Main -> PromptBlockSourceInfo(
        PromptBlockSourceKind.Template,
        R.string.ai_pipeline_source_main,
    )
    PromptBlockId.Persona -> PromptBlockSourceInfo(
        PromptBlockSourceKind.Runtime,
        R.string.ai_pipeline_source_persona,
    )
    PromptBlockId.Character -> PromptBlockSourceInfo(
        PromptBlockSourceKind.Runtime,
        R.string.ai_pipeline_source_character,
    )
    PromptBlockId.WorldCatalog -> PromptBlockSourceInfo(
        PromptBlockSourceKind.Runtime,
        R.string.ai_pipeline_source_world_catalog,
    )
    PromptBlockId.WorldEntries -> PromptBlockSourceInfo(
        PromptBlockSourceKind.Runtime,
        R.string.ai_pipeline_source_world_entries,
    )
    PromptBlockId.MemoryTables -> PromptBlockSourceInfo(
        PromptBlockSourceKind.Runtime,
        R.string.ai_pipeline_source_memory_tables,
    )
    PromptBlockId.CharacterCardsCatalog -> PromptBlockSourceInfo(
        PromptBlockSourceKind.Runtime,
        R.string.ai_pipeline_source_character_cards_catalog,
    )
    PromptBlockId.UserMemory -> PromptBlockSourceInfo(
        PromptBlockSourceKind.Runtime,
        R.string.ai_pipeline_source_user_memory,
    )
    PromptBlockId.LocalTime -> PromptBlockSourceInfo(
        PromptBlockSourceKind.Runtime,
        R.string.ai_pipeline_source_local_time,
    )
    PromptBlockId.UserCard -> PromptBlockSourceInfo(
        PromptBlockSourceKind.Runtime,
        R.string.ai_pipeline_source_user_card,
    )
    PromptBlockId.WorkspaceIndex -> PromptBlockSourceInfo(
        PromptBlockSourceKind.Runtime,
        R.string.ai_pipeline_source_workspace_index,
    )
    PromptBlockId.WorkspacePrefetch -> PromptBlockSourceInfo(
        PromptBlockSourceKind.Runtime,
        R.string.ai_pipeline_source_workspace_prefetch,
    )
    PromptBlockId.WritingSubmode -> PromptBlockSourceInfo(
        PromptBlockSourceKind.Template,
        R.string.ai_pipeline_source_writing_submode,
    )
    PromptBlockId.WritingInputFormat -> PromptBlockSourceInfo(
        PromptBlockSourceKind.Template,
        R.string.ai_pipeline_source_writing_input_format,
    )
    PromptBlockId.WritingXmlNotice -> PromptBlockSourceInfo(
        PromptBlockSourceKind.Fixed,
        R.string.ai_pipeline_source_writing_xml_notice,
    )
    PromptBlockId.WritingIdentity -> PromptBlockSourceInfo(
        PromptBlockSourceKind.Template,
        R.string.ai_pipeline_source_writing_identity,
    )
    PromptBlockId.MultiCharacter -> PromptBlockSourceInfo(
        PromptBlockSourceKind.Template,
        R.string.ai_pipeline_source_multi_character,
    )
    PromptBlockId.CurrentSpeaker -> PromptBlockSourceInfo(
        PromptBlockSourceKind.Runtime,
        R.string.ai_pipeline_source_current_speaker,
    )
    PromptBlockId.WritingStyleGuide -> PromptBlockSourceInfo(
        PromptBlockSourceKind.Runtime,
        R.string.ai_pipeline_source_writing_style_guide,
    )
    PromptBlockId.WritingActionContinue -> PromptBlockSourceInfo(
        PromptBlockSourceKind.Runtime,
        R.string.ai_pipeline_source_writing_action_continue,
    )
    PromptBlockId.PostHistory -> PromptBlockSourceInfo(
        PromptBlockSourceKind.Runtime,
        R.string.ai_pipeline_source_post_history,
    )
    PromptBlockId.HelpReplySystem -> PromptBlockSourceInfo(
        PromptBlockSourceKind.Template,
        R.string.ai_pipeline_source_help_reply_system,
    )
    PromptBlockId.MultiBubbleProtocol -> PromptBlockSourceInfo(
        PromptBlockSourceKind.Template,
        R.string.ai_pipeline_source_multi_bubble,
    )
    PromptBlockId.SkillsCatalog -> PromptBlockSourceInfo(
        PromptBlockSourceKind.Runtime,
        R.string.ai_pipeline_source_skills_catalog,
    )
}

@Composable
fun promptBlockSourceText(id: PromptBlockId): String {
    val info = promptBlockSourceInfo(id)
    return stringResource(info.sourceRes)
}

@Composable
fun promptBlockGateText(id: PromptBlockId): String? {
    val res = when (id) {
        PromptBlockId.WorldCatalog,
        PromptBlockId.WorldEntries,
        -> R.string.ai_pipeline_gate_world_book
        PromptBlockId.MemoryTables -> R.string.ai_pipeline_gate_memory_table
        PromptBlockId.CharacterCardsCatalog -> R.string.ai_pipeline_gate_character_card
        PromptBlockId.UserCard -> R.string.ai_pipeline_gate_user_card
        PromptBlockId.Persona -> R.string.ai_pipeline_gate_user_card
        PromptBlockId.WritingStyleGuide,
        PromptBlockId.WritingActionContinue,
        PromptBlockId.HelpReplySystem,
        -> R.string.ai_pipeline_gate_writing_prompt
        PromptBlockId.MultiCharacter -> R.string.ai_pipeline_gate_multi_character
        PromptBlockId.CurrentSpeaker -> R.string.ai_pipeline_gate_current_speaker
        PromptBlockId.PostHistory -> R.string.ai_pipeline_gate_post_history
        else -> null
    }
    return res?.let { stringResource(it) }
}
