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
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import com.kakao.sdk.common.util.Utility
import com.kakao.sdk.common.model.ClientError
import com.navercorp.nid.NidOAuth
import com.navercorp.nid.oauth.util.NidOAuthCallback
import com.navercorp.nid.profile.util.NidProfileCallback
import com.navercorp.nid.profile.domain.vo.NidProfile

class login : AppCompatActivity() {
    private lateinit var mFirebaseAuth: FirebaseAuth
    private lateinit var mDatabaseRef: DatabaseReference
    private lateinit var mEtEmail: EditText
    private lateinit var mEtPwd: EditText
    private lateinit var mBtnRegister: Button
    private lateinit var mBtnLogin: Button
    private lateinit var mBtnGoogle: SignInButton

    fun generateSecureRandomNonce(byteLength: Int = 32): String {
        val randomBytes = ByteArray(byteLength)
        SecureRandom().nextBytes(randomBytes)
        return Base64.encodeToString(randomBytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }

    private fun signInWithFirebase(idToken: String) {
        val auth = FirebaseAuth.getInstance()
        val credential = GoogleAuthProvider.getCredential(idToken, null)

        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Firebase 구글 로그인 성공")
                    val user = auth.currentUser
                    mDatabaseRef.child("UserAccount").child(user!!.uid)
                        .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                                if (!snapshot.exists()) {
                                    val account = UserAccount()
                                    account.idToken = user.uid
                                    account.emailId = user.email
                                    account.name = user.displayName
                                    mDatabaseRef.child("UserAccount").child(user.uid).setValue(account)

                                    Toast.makeText(this@login, "첫 방문을 환영합니다! 정보를 입력해 주세요.", Toast.LENGTH_SHORT).show()
                                    val intent = Intent(this@login, change::class.java)
                                    startActivity(intent)
                                    finish()
                                } else {
                                    Toast.makeText(this@login, "로그인에 성공하셨습니다", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this@login, MainActivity::class.java))
                                    finish()
                                }
                            }

                            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                                Toast.makeText(this@login, "로그인 성공!", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this@login, MainActivity::class.java))
                                finish()
                            }
                        })
                } else {
                    Log.w(TAG,"Firebase 구글 로그인 실패")
                    Toast.makeText(this,"인증에 실패했습니다",Toast.LENGTH_SHORT).show()
                    Log.w(TAG, "Firebase 구글 로그인 실패", task.exception)
                    Toast.makeText(this, "인증에 실패했습니다: ${task.exception?.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun handleSignIn(result: GetCredentialResponse) {
        val credential = result.credential

        when (credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val idToken = googleIdTokenCredential.idToken

                        signInWithFirebase(idToken)
                    } catch (_: GoogleIdTokenParsingException){
                        Log.e(TAG, "Received an invalid google id token response")
                        if (idToken != null) {
                            signInWithFirebase(idToken)
                        } else {
                            Log.e(TAG, "구글 ID 토큰이 비어 있습니다.")
                        }
                    } catch (e: GoogleIdTokenParsingException) {
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
            } catch (_: Exception) {
                Log.e(TAG, "강제 로그인 창 띄우기 실패")
                Toast.makeText(this@login, "구글 로그인을 시작할 수 없습니다. 기기 설정을 확인해 주세요.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "강제 로그인 창 띄우기 실패: ${e.message}")
                Toast.makeText(this@login, "구글 로그인을 시작할 수 없습니다: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startLogin() {
        val WEB_CLIENT_ID: String = getString(R.string.default_web_client_id)
        val nonce = generateSecureRandomNonce()

        val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
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
            .build()

        val credentialManager = CredentialManager.create(this)

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@login,
                )
                handleSignIn(result)
            } catch (e: GetCredentialException) {
                when (e) {
                    is GetCredentialCancellationException -> {
                        Log.d(TAG, "사용자가 로그인을 취소함")
                    }
                    is NoCredentialException->{
                        Log.e(TAG,"저장된 자격 증명이 없습니다")
                        Toast.makeText(this@login, "계정을 선택하세요.", Toast.LENGTH_SHORT).show()
                        tryWithGoogleSignInOption(WEB_CLIENT_ID, credentialManager, nonce)
                    }
                    else -> {
                        Log.e(TAG, "알 수 없는 로그인 오류 발생")
                        Toast.makeText(this@login,"로그인 중 오류가 발생했습니다. 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                    is NoCredentialException -> {
                        Log.e(TAG, "저장된 자격 증명이 없습니다: ${e.message}")
                        tryWithGoogleSignInOption(WEB_CLIENT_ID, credentialManager, nonce)
                    }
                    else -> {
                        Log.e(TAG, "알 수 없는 로그인 오류 발생: ${e.message}", e)
                        Toast.makeText(this@login, "로그인 오류: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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

        val keyHash = Utility.getKeyHash(this)
        Log.d("KeyHash", keyHash)
        Log.d("KeyHash", "카카오 디버그 키 해시: $keyHash")

        val btnKakao = findViewById<Button>(R.id.btn_kakao_login)
        btnKakao.setOnClickListener {
            startKakaoLogin()
        }

        NidOAuth.initialize(
            this,
            "jRAJ8Vp4K2awP6iW7RpY",
            "a9pgmiQTTM",
            "DATACLE"
        )

        val btnNaver = findViewById<Button>(R.id.btn_naver_login)
        btnNaver.setOnClickListener {
            startNaverLogin()
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
                        val errMsg = task.exception?.localizedMessage ?: "로그인 실패"
                        Toast.makeText(this@login, "로그인 실패: $errMsg", Toast.LENGTH_LONG).show()
                    }
                }
        }

        mBtnRegister.setOnClickListener {
            startActivity(Intent(this@login, register::class.java))
            finish()
        }
    }

    private fun startKakaoLogin() {
        val callback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
            if (error != null) {
                Log.e(TAG, "카카오계정으로 로그인 실패")
                Toast.makeText(this, "카카오 로그인 실패: ${error.localizedMessage}", Toast.LENGTH_SHORT)
                    .show()
                Log.e(TAG, "카카오계정으로 로그인 실패", error)
                Toast.makeText(this, "카카오 로그인 실패: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
            } else if (token != null) {
                Log.i(TAG, "카카오계정으로 로그인 성공")
                getKakaoUserInfo()
            }
        }

        if (UserApiClient.instance.isKakaoTalkLoginAvailable(this)) {
            UserApiClient.instance.loginWithKakaoTalk(this) { token, error ->
                if (error != null) {
                    Log.e(TAG, "카카오톡으로 로그인 실패")

                    if (error is ClientError && error.reason == ClientErrorCause.Cancelled) {
                        return@loginWithKakaoTalk
                    }

                    UserApiClient.instance.loginWithKakaoAccount(this, callback = callback)
                } else if (token != null) {
                    Log.i(TAG, "카카오톡으로 로그인 성공")
                    getKakaoUserInfo()
                }
            }
        } else {
            UserApiClient.instance.loginWithKakaoAccount(this, callback = callback)
        }
    }

    private fun getKakaoUserInfo() {
        UserApiClient.instance.me { user, error ->
            if (error != null) {
                Log.e(TAG, "사용자 정보 요청 실패")
            } else if (user != null) {
                val nickname = user.kakaoAccount?.profile?.nickname
                // 기존 파이어베이스/데이터베이스 로그인 로직 연동
                // ...
                Toast.makeText(this, "${nickname ?: "사용자"}님 환영합니다!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
                Log.e(TAG, "사용자 정보 요청 실패", error)
                Toast.makeText(this, "카카오 사용자 정보 요청 실패: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
            } else if (user != null) {
                val kakaoUserId = user.id.toString()
                val email = user.kakaoAccount?.email ?: "kakao_$kakaoUserId@kakao.com"
                val nickname = user.kakaoAccount?.profile?.nickname ?: "카카오 사용자"

                Log.d(TAG, "카카오 사용자 정보: ID=$kakaoUserId, 이메일=$email, 닉네임=$nickname")

                firebaseAuthWithSocialAccount(
                    email = email,
                    socialId = kakaoUserId,
                    name = nickname,
                    providerPrefix = "kakao"
                )
            }
        }
    }

    private fun startNaverLogin() {
        val oauthLoginCallback = object : NidOAuthCallback {
            override fun onSuccess() {
                getNaverUserInfo()
            }

            override fun onFailure(errorCode: String, errorDesc: String) {
                Log.w(TAG, "네이버 로그인 실패: code=$errorCode")
                Toast.makeText(this@login, "네이버 로그인 실패: $errorDesc", Toast.LENGTH_SHORT).show()
            override fun onFailure(httpStatus: Int, message: String) {
                val errorDescription = NaverIdLoginSDK.getLastErrorDescription()
                Toast.makeText(this@login, "네이버 로그인 실패: $errorDescription", Toast.LENGTH_SHORT).show()
            }

            override fun onError(errorCode: Int, message: String) {
                onFailure(errorCode, message)
            }
        }

        NidOAuth.requestLogin(this, oauthLoginCallback)
    }

    private fun getNaverUserInfo() {
        NidOAuth.getUserProfile(object : NidProfileCallback<NidProfile> {
            override fun onSuccess(result: NidProfile) {
                val displayName = result.profile.nickname.ifBlank {
                    result.profile.name.ifBlank { "사용자" }
                }
                Toast.makeText(this@login, "${displayName}님 환영합니다!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@login, MainActivity::class.java))
                finish()
        NidOAuthLogin().callProfileApi(object : NidProfileCallback<NidProfileResponse> {
            override fun onSuccess(result: NidProfileResponse) {
                val naverUserId = result.profile?.id ?: System.currentTimeMillis().toString()
                val email = result.profile?.email ?: "naver_$naverUserId@naver.com"
                val name = result.profile?.name
                val nickname = result.profile?.nickname ?: name ?: "네이버 사용자"

                Log.d(TAG, "네이버 정보 - ID: $naverUserId, 이메일: $email, 닉네임: $nickname")

                firebaseAuthWithSocialAccount(
                    email = email,
                    socialId = naverUserId,
                    name = nickname,
                    providerPrefix = "naver"
                )
            }

            override fun onFailure(errorCode: String, errorDesc: String) {
                Log.e(TAG, "네이버 프로필 실패: code=$errorCode")
                Toast.makeText(this@login, "사용자 정보 불러오기 실패", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun firebaseAuthWithSocialAccount(
        email: String,
        socialId: String,
        name: String,
        providerPrefix: String
    ) {
        val authEmail = if (email.contains("@")) email else "${providerPrefix}_${socialId}@$providerPrefix.com"
        val authPassword = "social_${providerPrefix}_$socialId"

        mFirebaseAuth.signInWithEmailAndPassword(authEmail, authPassword)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val firebaseUser = mFirebaseAuth.currentUser
                    val uid = firebaseUser?.uid ?: "${providerPrefix}_$socialId"

                    mDatabaseRef.child("UserAccount").child(uid)
                        .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                                if (!snapshot.exists()) {
                                    val account = UserAccount()
                                    account.idToken = uid
                                    account.emailId = authEmail
                                    account.name = name
                                    mDatabaseRef.child("UserAccount").child(uid).setValue(account)

                                    Toast.makeText(this@login, "첫 방문을 환영합니다! 정보를 입력해 주세요.", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this@login, change::class.java))
                                    finish()
                                } else {
                                    Toast.makeText(this@login, "${name}님 환영합니다!", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this@login, MainActivity::class.java))
                                    finish()
                                }
                            }

                            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                                Toast.makeText(this@login, "${name}님 환영합니다!", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this@login, MainActivity::class.java))
                                finish()
                            }
                        })
                } else {
                    mFirebaseAuth.createUserWithEmailAndPassword(authEmail, authPassword)
                        .addOnCompleteListener(this) { createTask ->
                            if (createTask.isSuccessful) {
                                val firebaseUser = mFirebaseAuth.currentUser
                                val uid = firebaseUser?.uid ?: "${providerPrefix}_$socialId"

                                val account = UserAccount()
                                account.idToken = uid
                                account.emailId = authEmail
                                account.name = name

                                mDatabaseRef.child("UserAccount").child(uid).setValue(account)

                                Toast.makeText(this@login, "첫 방문을 환영합니다! 정보를 입력해 주세요.", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this@login, change::class.java))
                                finish()
                            } else {
                                Log.w(TAG, "Firebase Auth 생성 실패, Realtime Database 직접 저장 시도: ${createTask.exception?.message}")
                                saveToRealtimeDatabaseOnly("${providerPrefix}_$socialId", authEmail, name)
                            }
                        }
                }
            }
    }

    private fun saveToRealtimeDatabaseOnly(userKey: String, email: String, name: String) {
        val account = UserAccount()
        account.idToken = userKey
        account.emailId = email
        account.name = name

        mDatabaseRef.child("UserAccount").child(userKey)
            .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    if (!snapshot.exists()) {
                        mDatabaseRef.child("UserAccount").child(userKey).setValue(account)
                        Toast.makeText(this@login, "첫 방문을 환영합니다! 정보를 입력해 주세요.", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@login, change::class.java))
                        finish()
                    } else {
                        Toast.makeText(this@login, "${name}님 환영합니다!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@login, MainActivity::class.java))
                        finish()
                    }
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    Toast.makeText(this@login, "${name}님 환영합니다!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@login, MainActivity::class.java))
                    finish()
                }
            })
    }
}
