package com.example.wpqkf
//post용 코드
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import android.webkit.WebView

class postdt : AppCompatActivity() {
    private lateinit var mBtnSchedule: ImageButton
    private lateinit var mBtnRecord: ImageButton
    private lateinit var mBtnHome: ImageButton
    private lateinit var mBtnAi: ImageButton
    private lateinit var mBtnProfile: ImageButton
    private lateinit var database : DatabaseReference
    private lateinit var btnPrev : Button
    private lateinit var btnNext : Button
    private lateinit var btndelete : Button
    private lateinit var btnmodify : Button
    private lateinit var mFirebaseAuth: FirebaseAuth
    private lateinit var mDatabaseRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_postdt)
        val tvTitle = findViewById<TextView>(R.id.tvDtTitle)
        val tvAuthor = findViewById<TextView>(R.id.tvDtAuthor)
        val wvBody = findViewById<WebView>(R.id.wvDtBody)
        wvBody.settings.javaScriptEnabled = false
        val tvCategory = findViewById<TextView>(R.id.tvDtCategory)

        database = Firebase.database.reference

        val postKey = intent.getStringExtra("post_key")

        if (postKey != null) {
            // posts/글의고유키" 경로로 가서 해당 글의 데이터만 긁어옴
            database.child("posts").child(postKey).get().addOnSuccessListener { snapshot ->
                // 데이터베이스에서 가져온 데이터를 Post 클래스 형태로 변환
                val post = snapshot.getValue(Post::class.java)

                if (post != null) {
                    tvTitle.text = post.title
                    tvAuthor.text = post.author
                    tvCategory.text = "[${post.category}]"

                    val bodyHtml = post.body ?: ""
                    val fullHtml = """
        <html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
            body { margin:0; padding:0; font-family:sans-serif; font-size:16px; line-height:1.6; color:#000000; }
            img { max-width:100%; height:auto; display:block; margin:6px 0; border-radius:6px; }
        </style>
        </head><body>$bodyHtml</body></html>
    """.trimIndent()

                    wvBody.loadDataWithBaseURL(null, fullHtml, "text/html", "UTF-8", null)
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
        mBtnSchedule = findViewById(R.id.schedule)
        mBtnRecord = findViewById(R.id.record)
        mBtnHome = findViewById(R.id.home)
        mBtnAi = findViewById(R.id.ai)
        mBtnProfile = findViewById(R.id.profile)
        btnPrev = findViewById(R.id.btnleft)
        btnNext = findViewById(R.id.btnright)
        btndelete = findViewById(R.id.btndelete)
        btnmodify = findViewById(R.id.btnmodify)

        mBtnSchedule.setOnClickListener {
            val intent = Intent(this, schedule::class.java)
            startActivity(intent)
            finish()
        }
        mBtnRecord.setOnClickListener {
            val intent = Intent(this, record::class.java)
            startActivity(intent)
            finish()
        }
        mBtnAi.setOnClickListener {
            val intent = Intent(this, ai::class.java)
            startActivity(intent)
            finish()
        }
        mBtnProfile.setOnClickListener {
            val intent = Intent(this, profile::class.java)
            startActivity(intent)
            finish()
        }
        mBtnHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }


        if (firebaseUser != null) {
            mDatabaseRef.child("UserAccount").child(firebaseUser.uid).addListenerForSingleValueEvent(object :
                ValueEventListener {
                //정보를 가져오는데 성공하면 자동으로 호출되는 함수
                override fun onDataChange(snapshot: DataSnapshot) {
                    //가져온 정보를 변수 안에 형식에 맞게 가져오기
                    val account = snapshot.getValue(UserAccount::class.java)
                    val email = account?.emailId?.toString() ?: mFirebaseAuth.currentUser?.email
                    
                    val isdeveloper= (email =="h_s_posan25_1219@dge.go.kr")
                    if (isdeveloper) {
                        btndelete.visibility = View.VISIBLE
                        btnmodify.visibility = View.VISIBLE
                    } else {
                        btndelete.visibility = View.GONE
                        btnmodify.visibility = View.GONE
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    btndelete.visibility = View.GONE
                    btnmodify.visibility = View.GONE
                }
            })
        }
        btndelete.setOnClickListener {
            if (postKey != null) {
                database.child("posts").child(postKey).removeValue()
                    .addOnSuccessListener {
                        Toast.makeText(this,"삭제를 완료했습니다.",Toast.LENGTH_SHORT).show()

                    }
                    .addOnFailureListener {
                        Toast.makeText(this,"에러: 삭제 실패",Toast.LENGTH_SHORT).show()
                    }
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
            }
        }
        btnmodify.setOnClickListener {
            val intent = Intent(this, ModifyText::class.java)
            intent.apply{
                putExtra("post_key", postKey)
                startActivity(this)
            }
        }
    }
}