package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Links global [AiCharacterCard] rows to a bookshelf book for multi-speaker read-aloud.
 */
@Entity(
    tableName = "book_character_cast",
    primaryKeys = ["bookUrl", "characterCardId"],
    foreignKeys = [
        ForeignKey(
            entity = AiCharacterCard::class,
            parentColumns = ["id"],
            childColumns = ["characterCardId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["bookUrl"]),
        Index(value = ["characterCardId"]),
    ],
)
data class BookCharacterCast(
    val bookUrl: String,
    val characterCardId: String,
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0,
    /** male_lead | female_lead | male_supporting | female_supporting | empty */
    @ColumnInfo(defaultValue = "")
    val dramaticRole: String = "",
)
