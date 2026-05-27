package com.example.wpqkf

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

private lateinit var mBtnHome6: Button
private lateinit var mBtnChange: Button
private lateinit var mEtName: EditText
private lateinit var mEtAge: EditText
private lateinit var mEtNumber: EditText
private lateinit var mFirebaseAuth: FirebaseAuth
private lateinit var mDatabaseRef: DatabaseReference
private lateinit var mTvEmail: TextView
private lateinit var mTvName: TextView
private lateinit var mTvAge: TextView
private lateinit var mTvNumber: TextView

class change : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_change)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        mFirebaseAuth = FirebaseAuth.getInstance()
        mDatabaseRef =
            FirebaseDatabase.getInstance().getReference("UserAccount")
        mTvEmail = findViewById(R.id.이메일정보)
        mTvName = findViewById(R.id.이름바꿈)
        mTvAge = findViewById(R.id.나이바꿈)
        mTvNumber = findViewById(R.id.전번바꿈)

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
                    if (account != null) {
                        mTvEmail.text = account.emailId
                        mTvName.text = account.name
                        mTvAge.text = account.age
                        mTvNumber.text = account.number
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@change, "사용자 정보를 가여오지 못했습니다.", Toast.LENGTH_SHORT).show()
                }
            })
        }
        mBtnHome6 = findViewById(R.id.돌아가기)
        mBtnChange = findViewById(R.id.정보바꿈버튼)
        mEtName = findViewById(R.id.이름바꿈)
        mEtAge = findViewById(R.id.나이바꿈)
        mEtNumber = findViewById(R.id.전번바꿈)

        mBtnHome6.setOnClickListener {
            val intent = Intent(this@change, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
        mBtnChange.setOnClickListener {
            val NewName = mEtName.text.toString()
            val NewAge = mEtAge.text.toString()
            val NewNumber = mEtNumber.text.toString()
            //정보를 바꿀 리스트를 생성
            val updates = HashMap<String, Any?>()
            updates["name"] = NewName
            updates["age"] = NewAge
            updates["number"] = NewNumber

            //파이어베이스 유저 칸을 찾아서 업데이트 리스트에 있는 것 들만 바꿔주기
            if (firebaseUser != null) {
                mDatabaseRef.child(firebaseUser.uid).updateChildren(updates)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this@change, "정보가 수정되었습니다.", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this@change, MainActivity::class.java)
                            startActivity(intent)
                            finish()
                        }
                    }
            }
        }
    }
}