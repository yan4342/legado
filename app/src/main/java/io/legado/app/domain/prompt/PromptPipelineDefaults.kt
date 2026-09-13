package io.legado.app.domain.prompt

object PromptPipelineDefaults {

    const val ID_CHAT_DEFAULT = "chat_default"
    const val ID_WRITING_ROLEPLAY = "writing_roleplay"
    const val ID_WRITING_AUTHOR = "writing_author"
    const val ID_WRITING_HELP_REPLY = "writing_help_reply"

    fun allPresets(): List<PromptPipelinePreset> = listOf(
        chatDefault(),
        writingRoleplay(),
        writingAuthor(),
        writingHelpReply(),
    )

    fun chatDefault(): PromptPipelinePreset = PromptPipelinePreset(
        id = ID_CHAT_DEFAULT,
        name = "Chat Default",
        mode = PromptPipelineMode.Chat.value,
        isDefault = true,
        blocks = listOf(
            block(PromptBlockId.Main, 0),
            // InChat: DeepSeek/OpenAI prefix cache needs a byte-stable SYSTEM prefix.
            block(PromptBlockId.LocalTime, 5, position = PromptBlockPosition.InChat, depth = 0),
            block(PromptBlockId.SkillsCatalog, 6, maxTokens = 600),
            block(PromptBlockId.MultiBubbleProtocol, 8),
            block(PromptBlockId.WorldCatalog, 10),
            block(PromptBlockId.MemoryTables, 20),
            block(PromptBlockId.CharacterCardsCatalog, 25),
            block(PromptBlockId.UserMemory, 30),
            // Keyword lore last: keeps stable blocks cacheable when hits change.
            block(PromptBlockId.WorldEntries, 40),
        ),
    )

    fun writingRoleplay(): PromptPipelinePreset = PromptPipelinePreset(
        id = ID_WRITING_ROLEPLAY,
        name = "Writing Roleplay",
        mode = PromptPipelineMode.WritingRoleplay.value,
        isDefault = true,
        blocks = listOf(
            block(PromptBlockId.WritingSubmode, 0),
            block(PromptBlockId.WritingInputFormat, 10),
            block(PromptBlockId.WritingXmlNotice, 20),
            block(PromptBlockId.SkillsCatalog, 22, maxTokens = 600),
            block(PromptBlockId.MultiBubbleProtocol, 25),
            block(PromptBlockId.WritingIdentity, 30),
            block(PromptBlockId.MultiCharacter, 40),
            // Stable session contracts before semi/volatile blocks (DeepSeek prefix).
            block(PromptBlockId.WritingStyleGuide, 45),
            block(PromptBlockId.PostHistory, 50),
            block(PromptBlockId.WorkspaceIndex, 60),
            block(PromptBlockId.WorkspacePrefetch, 70, enabled = false, maxTokens = 900),
            // @-speaker and keyword lore change more often — keep last.
            // CurrentSpeaker picked the current speaker (volatile) → INCHAT user-role
            // so the SYSTEM prefix (deepseek/openai prefix cache) stays byte-stable.
            block(PromptBlockId.CurrentSpeaker, 90, position = PromptBlockPosition.InChat, depth = 0),
            block(PromptBlockId.WorldEntries, 110),
        ),
    )

    fun writingAuthor(): PromptPipelinePreset = PromptPipelinePreset(
        id = ID_WRITING_AUTHOR,
        name = "Writing Author",
        mode = PromptPipelineMode.WritingAuthor.value,
        isDefault = true,
        blocks = listOf(
            block(PromptBlockId.WritingSubmode, 0),
            block(PromptBlockId.WritingInputFormat, 10),
            block(PromptBlockId.WritingXmlNotice, 20),
            block(PromptBlockId.SkillsCatalog, 22, maxTokens = 600),
            block(PromptBlockId.MultiCharacter, 40),
            block(PromptBlockId.WritingStyleGuide, 45),
            block(PromptBlockId.PostHistory, 50),
            block(PromptBlockId.WorkspaceIndex, 60),
            block(PromptBlockId.WorkspacePrefetch, 70, enabled = false, maxTokens = 900),
            block(PromptBlockId.CurrentSpeaker, 90, position = PromptBlockPosition.InChat, depth = 0),
            block(PromptBlockId.WorldEntries, 110),
        ),
    )

    fun writingHelpReply(): PromptPipelinePreset = PromptPipelinePreset(
        id = ID_WRITING_HELP_REPLY,
        name = "Writing Help Reply",
        mode = PromptPipelineMode.WritingHelpReply.value,
        isDefault = true,
        blocks = listOf(
            block(PromptBlockId.HelpReplySystem, 0),
            block(PromptBlockId.WritingXmlNotice, 10),
            block(PromptBlockId.UserCard, 20),
            // Character cards bias the model into NPC voice; off by default for help-reply.
            block(PromptBlockId.Character, 30, enabled = false),
        ),
    )

    fun presetForMode(mode: PromptPipelineMode): PromptPipelinePreset = when (mode) {
        PromptPipelineMode.Chat -> chatDefault()
        PromptPipelineMode.WritingRoleplay -> writingRoleplay()
        PromptPipelineMode.WritingAuthor -> writingAuthor()
        PromptPipelineMode.WritingHelpReply -> writingHelpReply()
    }

    private fun block(
        id: PromptBlockId,
        order: Int,
        enabled: Boolean = true,
        position: PromptBlockPosition = PromptBlockPosition.Prefix,
        depth: Int = 0,
        maxTokens: Int = 0,
    ): PromptBlockSpec = PromptBlockSpec(
        id = id,
        enabled = enabled,
        order = order,
        position = position,
        depth = depth,
        maxTokens = maxTokens,
    )
}
