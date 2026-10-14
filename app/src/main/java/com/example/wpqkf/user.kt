package com.example.wpqkf


import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat


class user : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_user)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

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
        // 사용 기록을 맵 형태로 저장 (최대 10개 관리용)
        // 키(Key)는 패키지명, 값은 사용 기록 객체
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