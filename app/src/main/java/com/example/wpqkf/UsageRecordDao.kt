package com.example.wpqkf

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UsageRecordDao {

    /**
     * LiveData로 반환 → 테이블 내용이 바뀌면 자동으로 관찰자(Activity)에게 알림
     * Firebase의 addValueEventListener와 동일한 역할을 로컬에서 수행
     */
    @Query("SELECT * FROM usage_records ORDER BY totalTime DESC LIMIT 10")
    fun getTopRecords(): LiveData<List<UsageRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<UsageRecord>)

    @Query("DELETE FROM usage_records")
    suspend fun clearAll()
}