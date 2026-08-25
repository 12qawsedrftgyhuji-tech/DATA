package com.example.wpqkf

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppMemoDao {

    // 저장/수정 (이미 있는 packageName이면 덮어쓰기)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMemo(memo: AppMemo)

    // 특정 앱의 메모 가져오기 (없으면 null)
    @Query("SELECT * FROM app_memo WHERE packageName = :packageName LIMIT 1")
    suspend fun getMemo(packageName: String): AppMemo?
}