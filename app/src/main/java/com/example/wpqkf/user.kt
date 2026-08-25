package com.example.wpqkf

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

// 2. UserAccount를 최상위(Top-level) 클래스로 이동
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

// 3. Activity 클래스는 독립적으로 유지가 되어야 함
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
}