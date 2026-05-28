package com.example.wpqkf

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase


class login : AppCompatActivity() {
    private lateinit var mFirebaseAuth: FirebaseAuth
    private lateinit var mDatabaseRef: DatabaseReference
    private lateinit var mEtEmail: EditText
    private lateinit var mEtPwd: EditText
    private lateinit var mBtnRegister: Button
    private lateinit var mBtnLogin: Button
    private lateinit var mGoogleSignInClient: GoogleSignInClient
    private lateinit var googleLoginLauncher: ActivityResultLauncher<Intent>
    private lateinit var mBtnGoogle: SignInButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        mFirebaseAuth = FirebaseAuth.getInstance()
        mDatabaseRef = FirebaseDatabase.getInstance().getReference()

        mEtEmail = findViewById(R.id.아이디입력)
        mEtPwd = findViewById(R.id.비번입력로그)
        mBtnRegister = findViewById(R.id.회원가입버튼)
        mBtnLogin = findViewById(R.id.로그인버튼)
        mBtnGoogle = findViewById(R.id.구글로그인버튼)

        // 구글 로그인 설정
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)

        // 결과 처리 런처
        googleLoginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)!!
                    // 이메일 대신 받은 idToken으로 파이어베이스 인증 진행
                    firebaseAuthWithGoogle(account.idToken!!)
                } catch (e: ApiException) {
                    Toast.makeText(this, "구글 로그인 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                // 이 부분이 실행된다면 구글 로그인 자체가 실패 (보통 SHA-1 문제)
                Toast.makeText(this, "구글 로그인 취소 또는 실패 (code: ${result.resultCode})", Toast.LENGTH_SHORT).show()
            }
        }

        mBtnGoogle.setOnClickListener {
            val signInIntent = mGoogleSignInClient.signInIntent
            googleLoginLauncher.launch(signInIntent)
        }

        mBtnLogin.setOnClickListener {
            val strEmail = mEtEmail.text.toString().trim()
            val strPwd = mEtPwd.text.toString().trim()

            if (strEmail.isEmpty() || strPwd.isEmpty()) {
                Toast.makeText(this, "이메일과 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            mFirebaseAuth.signInWithEmailAndPassword(strEmail, strPwd)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this@login, "로그인에 성공하셨습니다", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@login, MainActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this@login, "로그인에 실패하셨습니다", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        mBtnRegister.setOnClickListener {
            startActivity(Intent(this@login, register::class.java))
            finish()
        }
    }
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        mFirebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val firebaseUser = mFirebaseAuth.currentUser

                    // 데이터베이스에서 이 사용자의 정보가 있는지 확인
                    mDatabaseRef.child("UserAccount").child(firebaseUser!!.uid)
                        .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                                if (!snapshot.exists()) {
                                    // 1. 정보가 없다면 (첫 구글 로그인)
                                    // 기본 틀만 먼저 만들고
                                    val account = user.UserAccount()
                                    account.idToken = firebaseUser.uid
                                    account.emailId = firebaseUser.email
                                    mDatabaseRef.child("UserAccount").child(firebaseUser.uid).setValue(account)

                                    // 2. 정보 수정(가입 완료) 화면으로 보냄
                                    Toast.makeText(this@login, "첫 방문을 환영합니다! 정보를 입력해 주세요.", Toast.LENGTH_SHORT).show()
                                    val intent = Intent(this@login, change::class.java) // 정보 수정 화면으로!
                                    startActivity(intent)
                                    finish()
                                } else {
                                    // 3. 이미 정보가 있는 기존 사용자라면 메인으로
                                    Toast.makeText(this@login, "로그인에 성공하셨습니다", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this@login, MainActivity::class.java))
                                    finish()
                                }
                            }
                            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                                Toast.makeText(this@login, "데이터베이스 오류: ${error.message}", Toast.LENGTH_SHORT).show()
                            }
                        })
                } else {
                    Toast.makeText(this@login, "파이어베이스 인증 실패: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
}