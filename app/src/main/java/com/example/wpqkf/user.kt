package com.example.wpqkf

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

data class UserAccount(
    var idToken: Any? = null,  // Firebase Uid (고유 토큰 정보)
    var emailId: Any? = null,  // 이메일 아이디
    var password: Any? = null,  // 비밀번호
    var name: Any? = null,
    var age: Any? = null,
    var number: Any? = null,
    var image : Any? = null
)

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
