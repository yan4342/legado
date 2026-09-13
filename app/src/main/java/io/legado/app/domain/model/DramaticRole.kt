package io.legado.app.domain.model

/** Book-scoped cast identity (男主/女主/男配/女配). Multiple leads per book are allowed. */
object DramaticRole {
    const val MALE_LEAD = "male_lead"
    const val FEMALE_LEAD = "female_lead"
    const val MALE_SUPPORTING = "male_supporting"
    const val FEMALE_SUPPORTING = "female_supporting"
    const val NONE = ""

    val ALL = listOf(
        MALE_LEAD,
        FEMALE_LEAD,
        MALE_SUPPORTING,
        FEMALE_SUPPORTING,
        NONE,
    )

    fun normalize(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value in ALL) return value
        return when (value.lowercase()) {
            "男主", "male lead", "malelead" -> MALE_LEAD
            "女主", "female lead", "femalelead" -> FEMALE_LEAD
            "男配", "male supporting", "malesupporting" -> MALE_SUPPORTING
            "女配", "female supporting", "femalesupporting" -> FEMALE_SUPPORTING
            else -> NONE
        }
    }

    fun isLead(role: String): Boolean =
        role == MALE_LEAD || role == FEMALE_LEAD

    /** Lower sorts earlier: male_lead → female_lead → male_supporting → female_supporting → other. */
    fun sortKey(role: String): Int = when (normalize(role)) {
        MALE_LEAD -> 0
        FEMALE_LEAD -> 1
        MALE_SUPPORTING -> 2
        FEMALE_SUPPORTING -> 3
        else -> 4
    }

    fun labelRes(role: String): Int? = when (normalize(role)) {
        MALE_LEAD -> io.legado.app.R.string.dramatic_role_male_lead
        FEMALE_LEAD -> io.legado.app.R.string.dramatic_role_female_lead
        MALE_SUPPORTING -> io.legado.app.R.string.dramatic_role_male_supporting
        FEMALE_SUPPORTING -> io.legado.app.R.string.dramatic_role_female_supporting
        else -> null
    }
}
