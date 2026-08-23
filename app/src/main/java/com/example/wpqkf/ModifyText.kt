package com.example.wpqkf
//post용 코드
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.database

class ModifyText : AppCompatActivity() {
    private lateinit var database : DatabaseReference
    private lateinit var mtitle : EditText
    private lateinit var mauthor : EditText
    private lateinit var mbody : EditText
    private lateinit var btnMdf : Button
    private lateinit var btnCancel : Button
    private lateinit var mFirebaseAuth: FirebaseAuth
    private lateinit var mDatabaseRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_modifytext)
        mtitle = findViewById(R.id.tv_title)
        mauthor = findViewById(R.id.tv_author)
        mbody = findViewById(R.id.tv_mainText)
        btnCancel = findViewById(R.id.btn_cancel)
        btnMdf = findViewById(R.id.btn_goMdf)

        database = Firebase.database.reference

        val postKey = intent.getStringExtra("post_key")

        if (postKey != null) {
            // posts/글의고유키" 경로로 가서 해당 글의 데이터만 긁어옴
            database.child("posts").child(postKey).get().addOnSuccessListener { snapshot ->
                // 데이터베이스에서 가져온 데이터를 Post 클래스 형태로 변환
                val post = snapshot.getValue(Post::class.java)

                if (post != null) {
                    // 가져온 데이터를 텍스트 뷰에 넣어주기
                    mtitle.setText(post.title)
                    mauthor.setText(post.author)
                    mbody.setText(post.body)
                }
            }.addOnFailureListener {
                // 오류가 났을 때:
                Toast.makeText(this, "404에러 : 글을 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "올바르지 않은 접근입니다.", Toast.LENGTH_SHORT).show()
        }
        mFirebaseAuth = FirebaseAuth.getInstance()
        mDatabaseRef = FirebaseDatabase.getInstance().reference
        val firebaseUser = mFirebaseAuth.currentUser
        btnMdf.setOnClickListener {
            val titletext = mtitle.text.toString().trim()
            val authortext = mauthor.text.toString().trim()
            val bodytext = mbody.text.toString().trim()
            if (titletext.isNotEmpty() && authortext.isNotEmpty() &&bodytext.isNotEmpty() &&(firebaseUser!=null)){
                //여기서 수정 ㄱ
                if (postKey != null) {
                    val updatedPost = mapOf(
                        "title" to titletext,
                        "author" to authortext,
                        "body" to bodytext
                    )
                    database.child("posts").child(postKey).updateChildren(updatedPost)
                        .addOnSuccessListener {
                            Toast.makeText(this, "수정이 완료되었습니다.", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "수정 실패", Toast.LENGTH_SHORT).show()
                        }
                }
                val intent = Intent(this@ModifyText, MainActivity::class.java)
                startActivity(intent)
                finish()
            } else if (titletext.isEmpty() || authortext.isEmpty() || bodytext.isEmpty()){
                Toast.makeText(this,"전체 칸을 모두 채워주세요", Toast.LENGTH_SHORT).show()
            }
        }
        btnCancel.setOnClickListener {
            val intent = Intent(this@ModifyText,postdt::class.java)
            intent.apply{
                putExtra("post_key", postKey)
                startActivity(this)
            }
        }
    }
}