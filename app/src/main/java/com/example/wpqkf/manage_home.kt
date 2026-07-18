package com.example.wpqkf
//post용 코드
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database

data class Post(
    var uid: String? = "",
    var author: String? = "",
    var title: String? = "",
    var body: String? = "",
    var starCount: Int = 0,
    var stars: Map<String, Boolean> = HashMap()
) {

    // [START post_to_map]
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "uid" to uid,
            "author" to author,
            "title" to title,
            "body" to body,
            "starCount" to starCount,
            "stars" to stars,
        )
    }
    // [END post_to_map]
}

class manage_home : AppCompatActivity() {
    private lateinit var database: DatabaseReference
    private lateinit var mFirebaseAuth: FirebaseAuth
    private lateinit var mDatabaseRef: DatabaseReference
    fun writeNewpost(userId: String, name: String, title: String, body :String) {
        val key = database.child("posts").push().key
        if (key == null) {
            return
        }
        val post = Post(
            uid = userId,
            author = name,
            title = title,
            body = body
        )
        val postValues = post.toMap()
        val childUpdates = hashMapOf<String, Any>(
            "/posts/$key" to postValues,   //전체 글 목록에 저장
            "/user-posts/$userId/$key" to postValues
        )
        database.updateChildren(childUpdates) //데이터 베이스에 최종 저장ㄱ
        database.updateChildren(childUpdates).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("Firebase", "데이터 저장 성공!")
                Toast.makeText(this, "게시물이 등록되었습니다.", Toast.LENGTH_SHORT).show()
            } else {
                Log.e("Firebase", "데이터 저장 실패: ${task.exception}")
                Toast.makeText(this, "등록 실패: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        database = Firebase.database.reference// 얘는 onCreate 안에 넣어야!
        setContentView(R.layout.activity_manage_home)/////////////////////////////
        val mtitle : EditText = findViewById(R.id.tv_title)
        val mauthor : EditText = findViewById(R.id.tv_author)
        val mmaintext : EditText = findViewById(R.id.tv_mainText)
        val postBtn : Button = findViewById(R.id.btn_post)
        val CancelBtn : Button = findViewById(R.id.btn_cancel)
        mFirebaseAuth = FirebaseAuth.getInstance()
        mDatabaseRef =
            FirebaseDatabase.getInstance().getReference("UserAccount")
        val firebaseUser = mFirebaseAuth.currentUser
        if (firebaseUser != null) {
            mDatabaseRef.child(firebaseUser.uid).addListenerForSingleValueEvent(object :
                ValueEventListener {
                //정보를 가져오는데 성공하면 자동으로 호출되는 함수
                override fun onDataChange(snapshot: DataSnapshot) {
                    //가져온 정보를 변수 안에 형식에 맞게 가져오기
                    val account = snapshot.getValue(user.UserAccount::class.java)
                    if (account != null) {
                        mauthor.setText("${account.name}")
                    } else {
                        mauthor.setText("관리자")
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                }
            })
        }
        postBtn.setOnClickListener {
            val titletext = mtitle.text.toString().trim()
            val authortext = mauthor.text.toString().trim()
            val bodytext = mmaintext.text.toString().trim()
            if (titletext.isNotEmpty() && authortext.isNotEmpty() &&bodytext.isNotEmpty() &&(firebaseUser!=null)){
                writeNewpost(userId= firebaseUser.uid, name= authortext, title= titletext, body=bodytext)
                val intent = Intent(this@manage_home, MainActivity::class.java)
                startActivity(intent)
                finish()
            } else if (titletext.isEmpty() || authortext.isEmpty() || bodytext.isEmpty()){
                Toast.makeText(this,"전체 칸을 모두 채워주세요", Toast.LENGTH_SHORT).show()
            }
        }
        CancelBtn.setOnClickListener {
            val intent = Intent(this@manage_home, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}