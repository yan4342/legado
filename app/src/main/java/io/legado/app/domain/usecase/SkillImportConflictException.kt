package io.legado.app.domain.usecase

/** Thrown when importing skills whose ids already exist and overwrite was not requested. */
class SkillImportConflictException(
    val conflictIds: List<String>,
) : IllegalStateException(
    "Skills already exist: ${conflictIds.joinToString(", ")}",
)
