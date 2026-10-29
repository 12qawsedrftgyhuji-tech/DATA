package com.example.wpqkf

import androidx.appcompat.app.AppCompatActivity

// Firebase 데이터베이스용 최상위 사용자 계정 데이터 모델
data class UserAccount(
    var idToken: Any? = null,  // Firebase Uid (고유 토큰 정보)
    var emailId: Any? = null,  // 이메일 아이디
    var password: Any? = null, // 비밀번호
    var name: Any? = null,
    var age: Any? = null,
    var number: Any? = null,
    var image: Any? = null,
    var usageRecords: Map<String, UsageRecord>? = null
) {
    constructor() : this(null, null, null, null, null, null, null, null) // Firebase용 기본 생성자
}

class user : AppCompatActivity() {

    data class UsageRecord(
        var appName: String? = null,
        var packageName: String? = null,
        var lastTimeUsed: Long? = null,
        var totalTime: Long? = null
    )

    data class UserAccount(
        var idToken: String? = null,  // Firebase Uid (고유 토큰 정보)
        var emailId: String? = null,  // 이메일 아이디
        var password: String? = null,  // 비밀번호
        var name: String? = null,
        var age: String? = null,
        var number: String? = null,
        var image: String? = null,
        var usageRecords: Map<String, UsageRecord>? = null
    )

    data class ScheduleItem(
        var id: String = "",        // 파이어베이스에서 생성해 준 고유 ID
        var name: String = "",      // 일정 이름
        var hour: Int = 0,          // 시간
        var minute: Int = 0,        // 분
        var category: String = ""
    )
}
