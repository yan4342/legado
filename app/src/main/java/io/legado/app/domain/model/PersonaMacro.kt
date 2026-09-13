package io.legado.app.domain.model

data class PersonaMacroContext(
    val userLabel: String,
    val characterNames: List<String>,
) {
    companion object {
        fun from(
            userName: String,
            userCardEnabled: Boolean,
            characterNames: List<String>,
            defaultUserLabel: String = "用户",
        ): PersonaMacroContext {
            val userLabel = if (userCardEnabled && userName.isNotBlank()) {
                userName
            } else {
                defaultUserLabel
            }
            return PersonaMacroContext(userLabel = userLabel, characterNames = characterNames)
        }
    }
}

/** @deprecated Prefer [AiMacroEngine] with [AiMacroContext]. */
object PersonaMacro {
    fun expand(text: String, ctx: PersonaMacroContext): String =
        AiMacroEngine.expand(
            text,
            AiMacroContext(
                userLabel = ctx.userLabel,
                characterNames = ctx.characterNames,
                currentSpeaker = ctx.characterNames.firstOrNull().orEmpty(),
            ),
        )
}
