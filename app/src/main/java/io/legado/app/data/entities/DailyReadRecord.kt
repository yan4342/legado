package io.legado.app.data.entities

import androidx.room.Entity

@Entity(tableName = "dailyReadRecord", primaryKeys = ["date", "bookName"])
data class DailyReadRecord(
    var date: String = "",
    var bookName: String = "",
    var readTime: Long = 0L
)
