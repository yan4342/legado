package io.legado.app.data.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.DailyReadRecord

data class DailyReadSummary(
    val date: String = "",
    @ColumnInfo(name = "readTime") val readTime: Long = 0L
)

data class BookReadSummary(
    val bookName: String = "",
    @ColumnInfo(name = "readTime") val readTime: Long = 0L
)

@Dao
interface DailyReadRecordDao {

    @get:Query("select * from dailyReadRecord order by date desc")
    val all: List<DailyReadRecord>

    @Query("select * from dailyReadRecord where date between :startDate and :endDate order by date")
    fun getByDateRange(startDate: String, endDate: String): List<DailyReadRecord>

    @Query("select * from dailyReadRecord where bookName = :bookName order by date")
    fun getByBook(bookName: String): List<DailyReadRecord>

    @Query("select coalesce(sum(readTime), 0) from dailyReadRecord where date between :startDate and :endDate")
    fun sumByDateRange(startDate: String, endDate: String): Long

    @Query("select date, sum(readTime) as readTime from dailyReadRecord where date between :startDate and :endDate group by date order by date")
    fun sumDailyByDateRange(startDate: String, endDate: String): List<DailyReadSummary>

    @Query("select bookName, sum(readTime) as readTime from dailyReadRecord where date between :startDate and :endDate group by bookName order by readTime desc limit :limit")
    fun topBooksByDateRange(startDate: String, endDate: String, limit: Int = 10): List<BookReadSummary>

    @Query("select readTime from dailyReadRecord where date = :date and bookName = :bookName")
    fun getReadTime(date: String, bookName: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg record: DailyReadRecord)

    @Query("delete from dailyReadRecord")
    fun clear()

    @Query("delete from dailyReadRecord where bookName = :bookName")
    fun deleteByBook(bookName: String)
}
