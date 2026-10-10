package com.example.wpqkf

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.vertexai.vertexAI
import androidx.lifecycle.lifecycleScope
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.initialize
import kotlinx.coroutines.launch
import com.google.firebase.appcheck.appCheck

class ai : AppCompatActivity() {
    private lateinit var mBtnSchedule4: ImageButton
    private lateinit var mBtnRecord4: ImageButton
    private lateinit var mBtnHome4: ImageButton
    private lateinit var mBtnAi4: ImageButton
    private lateinit var mBtnProfile4: ImageButton
    private lateinit var mBtnTR : Button
    private lateinit var mBtnTa : Button

    fun isGoogleUser() :Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false

        return user.providerData.any{
            profile -> profile.providerId == GoogleAuthProvider.PROVIDER_ID
        }
    }

    fun checkUserAndShowDialog(AreuOk: Boolean) {
        if (!AreuOk) {
            MaterialAlertDialogBuilder(this)
                .setTitle("알림")
                .setMessage("Gemini AI 기능은 구글 계정으로 로그인한 경우에만 사용할 수 있습니다.")
                .setPositiveButton("확인") { dialog, _ ->
                    dialog.dismiss()
                    startActivity(Intent(this@ai, MainActivity::class.java))
                    finish()
                }
                .setCancelable(false)
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_ai)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        checkUserAndShowDialog(isGoogleUser())//구글 계정일 때만 ai 기능-> gemini 쓸 수 있게끔 ㄱㄱ
        mBtnSchedule4 = findViewById(R.id.schedule)
        mBtnRecord4 = findViewById(R.id.record)
        mBtnHome4 = findViewById(R.id.home)
        mBtnAi4 = findViewById(R.id.ai)
        mBtnProfile4 = findViewById(R.id.profile)
        mBtnTR = findViewById(R.id.BtnTR)
        mBtnTa = findViewById(R.id.btnTa)

        mBtnHome4.setOnClickListener {
            val intent = Intent(this@ai, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
        mBtnSchedule4.setOnClickListener {
            val intent = Intent(this@ai, schedule::class.java)
            startActivity(intent)
            finish()
        }
        mBtnRecord4.setOnClickListener {
            val intent = Intent(this@ai, record::class.java)
            startActivity(intent)
            finish()
        }
        mBtnProfile4.setOnClickListener {
            val intent = Intent(this@ai, profile::class.java)
            startActivity(intent)
            finish()
        }
        mBtnTR.setOnClickListener {
            val intent = Intent(this@ai, textRecognizer::class.java)
            startActivity(intent)
            finish()
        }
        mBtnTa.setOnClickListener {
            val intent = Intent(this@ai, talkwithai::class.java)
            startActivity(intent)
            finish()
        }
    }
}