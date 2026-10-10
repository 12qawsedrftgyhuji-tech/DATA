package com.example.wpqkf

import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.SignInButton
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import java.security.SecureRandom
import androidx.activity.result.contract.ActivityResultContracts

class login : AppCompatActivity() {
    private lateinit var mFirebaseAuth: FirebaseAuth
    private lateinit var mDatabaseRef: DatabaseReference
    private lateinit var mEtEmail: EditText
    private lateinit var mEtPwd: EditText
    private lateinit var mBtnRegister: Button
    private lateinit var mBtnLogin: Button
    private lateinit var mBtnGoogle: SignInButton

    private var addAccountLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        Toast.makeText(this, "계정이 추가되었습니다. 로그인을 다시 시도합시다.", Toast.LENGTH_SHORT).show()

        startLogin()
    }
    fun generateSecureRandomNonce(byteLength : Int = 32): String {
        val randomBytes = ByteArray(byteLength)
        SecureRandom().nextBytes(randomBytes)
        return Base64.encodeToString(randomBytes,Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }

    private fun signInWithFirebase(idToken: String){
        val auth=FirebaseAuth.getInstance()

        val credential = GoogleAuthProvider.getCredential(idToken,null)

        auth.signInWithCredential(credential)
            .addOnCompleteListener(this){task->
                if (task.isSuccessful){
                    Log.d(TAG, "Firebase 구글 로그인 성공")
                    val user = auth.currentUser
                    mDatabaseRef.child("UserAccount").child(user!!.uid)
                        .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                                if (!snapshot.exists()) {
                                    // 1. 정보가 없다면 (첫 구글 로그인)
                                    // 기본 틀만 먼저 만들고
                                    val account = UserAccount()
                                    account.idToken = user.uid
                                    account.emailId = user.email//auth.currentUser.email이랑 동치
                                    account.name = user.displayName
                                    mDatabaseRef.child("UserAccount").child(user.uid).setValue(account)

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
                            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
                        })
                } else {
                    Log.w(TAG,"Firebase 구글 로그인 실패",task.exception)
                    Toast.makeText(this,"인증에 실패했습니다",Toast.LENGTH_SHORT).show()
                }

            }
    }
    private fun handleSignIn(result : GetCredentialResponse) {
        val credential = result.credential

        when (credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL){
                    try{
                        val googleIdTokenCredential = GoogleIdTokenCredential
                            .createFrom(credential.data)

                        val idToken = googleIdTokenCredential.idToken

                        if (idToken != null){
                            signInWithFirebase(idToken)
                        } else {
                            Log.e(TAG,"구글 ID 토큰이 비어 있습니다.")
                        }
                    } catch (e: GoogleIdTokenParsingException){
                        Log.e(TAG, "Received an invalid google id token response", e)
                    }
                } else {
                    Log.e(TAG, "Unexpected type of credential")
                }
            }
            else -> {
                Log.e(TAG, "Unexpected type of credential")
            }
        }
    }

    private fun tryWithGoogleSignInOption(webClientId: String, credentialManager: CredentialManager, nonce: String) {
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId = webClientId)
            .setNonce(nonce)
            .build()

        val backupRequest = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    request = backupRequest,
                    context = this@login,
                )
                handleSignIn(result)
            } catch (e: Exception) {
                Log.e(TAG, "강제 로그인 창 띄우기 실패: ${e.message}")
                Toast.makeText(this@login, "구글 로그인을 시작할 수 없습니다. 기기 설정을 확인해 주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun startLogin(){

        val WEB_CLIENT_ID: String = getString(R.string.default_web_client_id)

        val nonce = generateSecureRandomNonce()
        val googleIdOption : GetGoogleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .setNonce(nonce)
            .build()
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId = WEB_CLIENT_ID)
            .setNonce(nonce)
            .build()

        val request: GetCredentialRequest = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .addCredentialOption(signInWithGoogleOption)
            .build()

        val credentialManager = CredentialManager.create(this)


        lifecycleScope.launch{
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@login,
                )
                handleSignIn(result)
            } catch(e: GetCredentialException){
                when (e){
                    is GetCredentialCancellationException ->{
                        Log.d(TAG,"사용자가 로그인을 취소함")
                    }
                    is NoCredentialException->{
                        Log.e(TAG,"저장된 자격 증명이 없습니다:${e.message}")
                        Toast.makeText(this@login, "계정을 선택하세요.", Toast.LENGTH_SHORT).show()
                        tryWithGoogleSignInOption(WEB_CLIENT_ID, credentialManager, nonce)
                    }
                    else -> {
                        Log.e(TAG, "알 수 없는 로그인 오류 발생:${e.message}",e)
                        Toast.makeText(this@login,"로그인 중 오류가 발생했습니다. 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

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

        mEtEmail = findViewById(R.id.et_id_input)
        mEtPwd = findViewById(R.id.et_password_input)
        mBtnRegister = findViewById(R.id.btn_register)
        mBtnLogin = findViewById(R.id.btn_login)
        mBtnGoogle = findViewById(R.id.btn_google_login)



        mBtnGoogle.setOnClickListener {
            startLogin()
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

}