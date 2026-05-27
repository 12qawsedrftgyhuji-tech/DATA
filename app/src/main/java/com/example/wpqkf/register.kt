package com.example.wpqkf


import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase


class register : AppCompatActivity() {
    private lateinit var mFirebaseAuth: FirebaseAuth    //파이어 베이스 인증
    private lateinit var mDatabaseRef: DatabaseReference    //실시간 데이터 베이스
    private lateinit var mEtEmail: EditText
    private lateinit var mEtPwd: EditText
    private lateinit var mEtName: EditText
    private lateinit var mEtAge: EditText
    private lateinit var mEtNumber: EditText
    private lateinit var mBtnRegister: Button
    private lateinit var mBtnReturn: Button


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)


        // 시스템 바 여백 설정
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        // 파이어베이스 및 뷰 초기화
        mFirebaseAuth = FirebaseAuth.getInstance()
        mDatabaseRef = FirebaseDatabase.getInstance().reference


        mEtEmail = findViewById(R.id.이메일입력)
        mEtPwd = findViewById(R.id.비번입력레지)
        mEtName = findViewById(R.id.이름입력)
        mEtAge = findViewById(R.id.나이입력)
        mEtNumber = findViewById(R.id.전번입력)
        mBtnRegister = findViewById(R.id.가입버튼)
        mBtnReturn = findViewById(R.id.돌아가기)


        // 회원가입 처리 시작
        mBtnRegister.setOnClickListener {
            val strEmail = mEtEmail.text.toString()
            val strPwd = mEtPwd.text.toString()
            val strName = mEtName.text.toString()
            val strAge = mEtAge.text.toString()
            val strNumber = mEtNumber.text.toString()

            // Firebase Auth  진행
            mFirebaseAuth.createUserWithEmailAndPassword(strEmail, strPwd)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val firebaseUser = mFirebaseAuth.currentUser


                        val account = user.UserAccount()
                        account.idToken = firebaseUser?.uid
                        account.emailId = firebaseUser?.email
                        account.password = strPwd
                        account.name = strName
                        account.age = strAge
                        account.number = strNumber

                        mDatabaseRef.child("UserAccount").child(firebaseUser!!.uid).setValue(account)
                        Toast.makeText(this@register, "회원가입에 성공하셨습니다", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this@register, login::class.java)
                        startActivity(intent)
                        finish()


                    } else {
                        Toast.makeText(this@register, "회원가입에 실패하셨습니다", Toast.LENGTH_SHORT).show()
                    }
                }
        }
        mBtnReturn.setOnClickListener {
            val intent = Intent(this@register, login::class.java)
            startActivity(intent)
            finish()
        }


    }
}
