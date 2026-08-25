package com.example.wpqkf

import androidx.room.Entity
import androidx.room.PrimaryKey

// 앱 하나당 메모 1개 (packageName이 기본키)
@Entity(tableName = "app_memo")
data class AppMemo(
    @PrimaryKey
    val packageName: String,
    val memo: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)