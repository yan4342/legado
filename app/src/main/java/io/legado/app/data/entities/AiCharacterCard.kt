package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_character_cards")
data class AiCharacterCard(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String = "",
    val openingLine: String = "",
    @ColumnInfo(defaultValue = "")
    val worldBookIds: String = "",
    @ColumnInfo(defaultValue = "")
    val personality: String = "",
    @ColumnInfo(defaultValue = "")
    val scenario: String = "",
    @ColumnInfo(defaultValue = "")
    val exampleDialogues: String = "",
    @ColumnInfo(defaultValue = "")
    val postHistoryInstructions: String = "",
    /** JSON array of alternate opening lines. */
    @ColumnInfo(defaultValue = "[]")
    val alternateOpenings: String = "[]",
    /** JSON array of alternate names used for speech speaker matching. */
    @ColumnInfo(defaultValue = "[]")
    val aliasesJson: String = "[]",
    @ColumnInfo(defaultValue = "unknown")
    val voiceGender: String = VOICE_GENDER_UNKNOWN,
    @ColumnInfo(defaultValue = "unknown")
    val voiceAgeBand: String = VOICE_AGE_UNKNOWN,
    /** Optional source book binding (same shape as AiWorldBook). */
    @ColumnInfo(defaultValue = "")
    val bookUrl: String = "",
    @ColumnInfo(defaultValue = "")
    val bookName: String = "",
    @ColumnInfo(defaultValue = "")
    val bookAuthor: String = "",
    /** 正典标记:canonical=1 的卡属于书级正典层(对二创不可见),canonical=0 属会话衍生层。 */
    @ColumnInfo(defaultValue = "0")
    val canonical: Boolean = false,
    /** 衍生卡 fork 自的正典卡 id(空表示非 fork 卡)。 */
    @ColumnInfo(defaultValue = "")
    val forkedFromCardId: String = "",
    /** Absolute path under app filesDir/character_avatars, or empty. */
    @ColumnInfo(defaultValue = "")
    val avatarPath: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val VOICE_GENDER_MALE = "male"
        const val VOICE_GENDER_FEMALE = "female"
        const val VOICE_GENDER_UNKNOWN = "unknown"
        val ALL_VOICE_GENDERS = listOf(VOICE_GENDER_MALE, VOICE_GENDER_FEMALE, VOICE_GENDER_UNKNOWN)

        const val VOICE_AGE_CHILD = "child"
        const val VOICE_AGE_TEEN = "teen"
        const val VOICE_AGE_YOUNG_ADULT = "young_adult"
        const val VOICE_AGE_ADULT = "adult"
        const val VOICE_AGE_ELDERLY = "elderly"
        const val VOICE_AGE_UNKNOWN = "unknown"
        val ALL_VOICE_AGE_BANDS = listOf(
            VOICE_AGE_CHILD, VOICE_AGE_TEEN, VOICE_AGE_YOUNG_ADULT,
            VOICE_AGE_ADULT, VOICE_AGE_ELDERLY, VOICE_AGE_UNKNOWN,
        )
    }
}
