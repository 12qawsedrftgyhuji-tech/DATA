package com.example.wpqkf

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [UsageRecord::class, AppMemo::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun usageRecordDao(): UsageRecordDao
    abstract fun appMemoDao(): AppMemoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_usage_db"
                )
                    // 버전이 올라가면서 기존 스키마와 안 맞아 크래시 나는 걸 방지.
                    // usage_record 테이블은 어차피 refreshData()가 매번 최근 24시간 기준으로
                    // 다시 계산해서 덮어쓰므로(clearAll() 후 insertAll), 마이그레이션 중 DB가
                    // 통째로 초기화돼도 실질적인 데이터 손실은 없음.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}