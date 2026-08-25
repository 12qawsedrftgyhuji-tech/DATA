package com.example.wpqkf

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 사용기록 하나를 나타내는 Room 엔티티
 * (예전에는 Firebase의 Map<String, Any>로 다뤘던 데이터를 이제 로컬 DB 테이블 한 줄로 관리)
 */
@Entity(tableName = "usage_records")
data class UsageRecord(
    @PrimaryKey val packageName: String, // 패키지명을 기본키로 사용 (같은 앱은 덮어씀)
    val appName: String,
    val totalTime: Long,
    val lastTimeUsed: Long
)