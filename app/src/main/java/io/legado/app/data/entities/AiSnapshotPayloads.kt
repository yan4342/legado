package io.legado.app.data.entities

/** Gson-friendly undo payload for outline snapshots (release/R8 safe). */
data class AiOutlineSnapshotPayload(
    val content: String = "",
    val enabled: Boolean = true,
    val source: String? = null,
    val diffSummary: String? = null,
)

/** Gson-friendly undo payload for conversation user-persona snapshots. */
data class AiUserCardSnapshotPayload(
    val conversationId: String = "",
    val userName: String = "",
    val userDescription: String = "",
    val enabled: Boolean = false,
)
