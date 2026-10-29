package com.example.wpqkf
//post용 코드
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database

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

        // 링크를 누르면 외부 브라우저로 열기
        wvBody.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return true
                // http/https 링크만 외부 브라우저로 열기
                if (url.scheme == "http" || url.scheme == "https") {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, url))
                    } catch (e: Exception) {
                        Toast.makeText(this@postdt, "링크를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
                return true // WebView 안에서 이동하지 않음
            }
        }

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

                    // 글자로만 적힌 URL(https://...)도 자동으로 링크 처리
                    val rawBody = post.body ?: ""
                    val urlRegex = Regex("""(?<!["'=])((?:https?://)(?:(?!&nbsp;)[^\s<>"'])+)(?![^<]*</a>)""")
                    val bodyHtml = urlRegex.replace(rawBody) { "<a href=\"${it.value}\">${it.value}</a>" }

                    val fullHtml = """
        <html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
            body { margin:0; padding:0; font-family:sans-serif; font-size:16px; line-height:1.6; color:#000000; }
            img { max-width:100%; height:auto; display:block; margin:6px 0; border-radius:6px; }
            a { color:#1a73e8; word-break:break-all; }
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
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        // UserAccount 전체 객체 변환 대신 emailId 필드값만 안전하게 가져옴
                        val email = snapshot.child("emailId").value?.toString()
                            ?: mFirebaseAuth.currentUser?.email

                        val isDeveloper = (email == "h_s_posan25_1219@dge.go.kr") || (email == "h_s_posan25_1201@dge.go.kr")

                        if (isDeveloper) {
                            btndelete.visibility = View.VISIBLE
                            btnmodify.visibility = View.VISIBLE
                        } else {
                            btndelete.visibility = View.GONE
                            btnmodify.visibility = View.GONE
                        }
                    } catch (e: Exception) {
                        // 예외 발생 시 버튼 숨김 처리
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