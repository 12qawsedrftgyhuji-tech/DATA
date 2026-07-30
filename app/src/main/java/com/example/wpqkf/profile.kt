package com.example.wpqkf

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch

class profile : AppCompatActivity() {
    //이동버튼들
    private lateinit var mBtnSchedule5: ImageButton
    private lateinit var mBtnRecord5: ImageButton
    private lateinit var mBtnHome5: ImageButton
    private lateinit var mBtnAi5: ImageButton
    private lateinit var mBtnProfile5: ImageButton
    private lateinit var mBtnChange: ImageButton
    private lateinit var mBtnLogout: Button
    //프로필화면에 이메일정보를 가져올 정보들
    private lateinit var mFirebaseAuth: FirebaseAuth
    private lateinit var mDatabaseRef: DatabaseReference
    private lateinit var mTvEmail: TextView
    private lateinit var mTvName: TextView
    private lateinit var mTvAge: TextView
    private lateinit var mTvNickmame: TextView
    private lateinit var mTvTitlename: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        //이메일 정보를 가져오는데 필요한 준비(현재 사용자의 인증객체 가져오기, UserAccount에서 정보를 가져올 준비)
        mFirebaseAuth = FirebaseAuth.getInstance()
        mDatabaseRef =
            FirebaseDatabase.getInstance().getReference("UserAccount")
        mTvEmail = findViewById(R.id.tv_profile_email)
        mTvName = findViewById(R.id.tv_profile_name)
        mTvAge = findViewById(R.id.tv_profile_age)
        mTvNickmame = findViewById(R.id.tv_profile_nickname)
        mTvTitlename = findViewById(R.id.tv_profile_title_name)

        //로그인 정보 가져오기
        val firebaseUser = mFirebaseAuth.currentUser
        //로그인 상태일떄만 정보를 가져오도록 하기
        if (firebaseUser != null) {
            //사용자의 고유 번호로 들어가서 이메일 칸을 찾고 데이터는 한번만 일고 파이어베이스에 정보 요청하기
            mDatabaseRef.child(firebaseUser.uid).addListenerForSingleValueEvent(object :
                ValueEventListener {
                //정보를 자져오는데 성공하면 자동으로 호출되는 함수
                override fun onDataChange(snapshot: DataSnapshot) {
                    //가져온 정보를 변수 안에 형식에 맞게 가져오기
                    val account = snapshot.getValue(user.UserAccount::class.java)
                    if (account?.emailId == null) {
                        //이메일을 다시 가져오자
                        account?.emailId = mFirebaseAuth.currentUser?.email
                    }
                    if (account != null) {
                        mTvTitlename.text = account.name
                        mTvEmail.text = "이메일: " + account.emailId
                        mTvName.text = "이름: " + account.name
                        mTvAge.text = "나이: " + account.age
                        mTvNickmame.text = "전화번호: " + account.number
                        com.bumptech.glide.Glide.with(this@profile)
                            .load(account.image) // 서버에 저장된 주소 불러오기
                            .into(findViewById(R.id.iv_profile_image_pr)) // 프로필 화면의 이미지뷰에 넣기
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@profile, "사용자 정보를 가져오지 못했습니다.", Toast.LENGTH_SHORT).show()
                }
            })
        }
        fun logout(context: Context){
            val auth = FirebaseAuth.getInstance()
            val sharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()

            editor.clear()
            editor.apply()
            auth.signOut()

            val credentialManager = CredentialManager.create(this)

            lifecycleScope.launch {
                try {
                    val clearRequest = ClearCredentialStateRequest()
                    credentialManager.clearCredentialState(clearRequest)
                    Log.d("LOGOUT", "구글 로그인 세션 초기화 성공")
                } catch (e: Exception) {
                    Log.e("LOGOUT", "구글 로그인 세션 초기화 실패", e)
                }
                val intent = Intent(this@profile, login::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
        }
        mBtnSchedule5 = findViewById(R.id.schedule)
        mBtnRecord5 = findViewById(R.id.record)
        mBtnHome5 = findViewById(R.id.home)
        mBtnAi5 = findViewById(R.id.ai)
        mBtnProfile5 = findViewById(R.id.profile)
        mBtnChange = findViewById(R.id.change)
        mBtnLogout = findViewById(R.id.logout)

        mBtnHome5.setOnClickListener {
            val intent = Intent(this@profile, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
        mBtnSchedule5.setOnClickListener {
            val intent = Intent(this@profile, schedule::class.java)
            startActivity(intent)
            finish()
        }
        mBtnRecord5.setOnClickListener {
            val intent = Intent(this@profile, record::class.java)
            startActivity(intent)
            finish()
        }
        mBtnAi5.setOnClickListener {
            val intent = Intent(this@profile, ai::class.java)
            startActivity(intent)
            finish()
        }
        mBtnChange.setOnClickListener {
            val intent = Intent(this@profile, change::class.java)
            startActivity(intent)
            finish()
        }
        mBtnLogout.setOnClickListener {
            logout(this)
        }
    }
}