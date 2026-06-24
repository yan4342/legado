package io.legado.app.data.entities

import androidx.room.Entity

@Entity(tableName = "hourlyReadRecord", primaryKeys = ["dateHour", "bookName"])
data class HourlyReadRecord(
    var dateHour: String = "",
    var bookName: String = "",
    var readTime: Long = 0L
)
