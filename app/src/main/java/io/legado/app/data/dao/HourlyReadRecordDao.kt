package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.HourlyReadRecord

data class HourlyReadSummary(
    val dateHour: String = "",
    val readTime: Long = 0L
)

@Dao
interface HourlyReadRecordDao {

    @get:Query("select * from hourlyReadRecord order by dateHour desc")
    val all: List<HourlyReadRecord>

    @Query("select coalesce(sum(readTime), 0) from hourlyReadRecord where dateHour between :start and :end")
    fun sumByDateHourRange(start: String, end: String): Long

    @Query("select dateHour, sum(readTime) as readTime from hourlyReadRecord where dateHour between :start and :end group by dateHour order by dateHour")
    fun sumHourlyByDateHourRange(start: String, end: String): List<HourlyReadSummary>

    @Query("select readTime from hourlyReadRecord where dateHour = :dateHour and bookName = :bookName")
    fun getReadTime(dateHour: String, bookName: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg record: HourlyReadRecord)

    @Query("delete from hourlyReadRecord")
    fun clear()

    @Query("delete from hourlyReadRecord where bookName = :bookName")
    fun deleteByBook(bookName: String)
}
