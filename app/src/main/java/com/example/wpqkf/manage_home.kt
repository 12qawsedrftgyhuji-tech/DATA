package com.example.wpqkf
//post용 코드
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
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
import android.webkit.WebView
import android.webkit.WebChromeClient
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts

data class Post(
    var uid: String? = "",
    var author: String? = "",
    var title: String? = "",
    var body: String? = "",
    var category: String? = "",
    var status: String?= "pending",
    var authEmail : String= "",
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
            "category" to category,
            "status" to status,
            "authEmail" to authEmail,
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
    private lateinit var webViewContentRef: WebView

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            uploadImageUriAndInsert(uri)
        } else {
            Toast.makeText(this, "이미지를 선택하지 않았습니다.", Toast.LENGTH_SHORT).show()
        }
    }
    fun writeNewpost(userId: String, name: String, title: String, body :String, category: String, authEmail: String) {
        val key = database.child("posts").push().key
        if (key == null) {
            return
        }
        val post = Post(
            uid = userId,
            author = name,
            title = title,
            body = body,
            category = category,
            status = "approved",
            authEmail = authEmail
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

    private fun uploadImageUriAndInsert(uri: Uri) {
        val fileName = "post_images/${UUID.randomUUID()}.jpg"
        val storageRef = FirebaseStorage.getInstance().reference.child(fileName)

        storageRef.putFile(uri)
            .continueWithTask { storageRef.downloadUrl }
            .addOnSuccessListener { downloadUri ->
                val safeUrl = downloadUri.toString().replace("'", "\\'")
                webViewContentRef.evaluateJavascript("insertImageAtCursor('$safeUrl')", null)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "이미지 업로드 실패", Toast.LENGTH_SHORT).show()
                Log.e("Upload", "실패", e)
            }
    }

    private fun unescapeJsString(raw: String): String {
        var s = raw.trim('"')

        // \u003C, \u003E, \u0026 같은 유니코드 이스케이프를 실제 문자로 변환
        val unicodeRegex = Regex("\\\\u([0-9a-fA-F]{4})")
        s = unicodeRegex.replace(s) { matchResult ->
            matchResult.groupValues[1].toInt(16).toChar().toString()
        }

        // 나머지 이스케이프 처리 (순서 중요: \\ 는 마지막에)
        s = s.replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")

        return s
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        database = Firebase.database.reference// 얘는 onCreate 안에 넣어야!
        setContentView(R.layout.activity_manage_home)/////////////////////////////
        var account : UserAccount? = null
        val mtitle : EditText = findViewById(R.id.tv_title)
        val mauthor : TextView = findViewById(R.id.tv_author)
        val webViewContent : WebView = findViewById(R.id.wv_mainText)//이미지 삽입을 위해 웹 뷰로 고침.
        val postBtn : Button = findViewById(R.id.btn_post)
        val CancelBtn : Button = findViewById(R.id.btn_cancel)
        val spinnerCt : Spinner = findViewById(R.id.spinner_category)
        val btnPickImage : Button = findViewById(R.id.btn_pick_image)
        btnPickImage.setOnClickListener {
            pickImageLauncher.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        }
        webViewContent.settings.javaScriptEnabled = true
        webViewContent.webChromeClient = WebChromeClient()
        webViewContent.loadUrl("file:///android_asset/editor.html")
        webViewContentRef = webViewContent
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
                    account = snapshot.getValue(UserAccount::class.java)
                }
                override fun onCancelled(error: DatabaseError) {
                }
            })
        }
        mauthor.setText("관리자")
        val categories = arrayListOf<String>()

        // 어댑터 연결 (목록과 디자인을 연결)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCt.adapter = adapter

        database.child("categories").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempSelected = spinnerCt.selectedItem?.toString() ?: ""
                categories.clear()

                for (catSnapshot in snapshot.children) {
                    val cat = catSnapshot.getValue(String::class.java)
                    if (cat != null) categories.add(cat)
                }

                // 데이터가 아예 없으면 기본값 세팅 (처음 한 번만 실행됨)
                if (categories.isEmpty()) {
                    val defaults = listOf("키오스크", "예매 방법", "모바일 앱 결제", "PC앱 결제")
                    defaults.forEach { database.child("categories").push().setValue(it) }
                }

                // 마지막에 항상 "직접 입력" 추가
                if (!categories.contains("직접 입력")) categories.add("직접 입력")

                adapter.notifyDataSetChanged()

                // 선택 상태 유지 로직
                if (tempSelected.isNotEmpty() && categories.contains(tempSelected)) {
                    spinnerCt.setSelection(categories.indexOf(tempSelected))
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        var selectedCategory = ""
        spinnerCt.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (categories[position] == "직접 입력") {
                    val builder = AlertDialog.Builder(this@manage_home)
                    builder.setTitle("새 종류 추가")
                    val input = EditText(this@manage_home)
                    builder.setView(input)
                    builder.setPositiveButton("추가") { _, _ ->
                        val newCat = input.text.toString().trim()
                        if (newCat.isNotEmpty() && !categories.contains(newCat)) {
                            database.child("categories").push().setValue(newCat)
                            spinnerCt.setSelection(categories.indexOf(newCat))
                            selectedCategory = newCat
                        }
                    }
                    builder.setNegativeButton("취소") { dialog, _ ->
                        dialog.cancel()
                        spinnerCt.setSelection(0) // 취소하면 다시 "메인"으로
                    }
                    builder.show()
                } else {
                    selectedCategory = categories[position]
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        if (firebaseUser != null) {
            mDatabaseRef.child("UserAccount").child(firebaseUser.uid).addListenerForSingleValueEvent(object :
                ValueEventListener {
                //정보를 가져오는데 성공하면 자동으로 호출되는 함수
                override fun onDataChange(snapshot: DataSnapshot) {
                    //가져온 정보를 변수 안에 형식에 맞게 가져오기
                    account = snapshot.getValue(UserAccount::class.java)
                    if (account != null && account?.emailId == null) {
                        //이메일을 다시 가져오자
                        account?.emailId = mFirebaseAuth.currentUser?.email
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                }
            })
        }
        postBtn.setOnClickListener {
            val titletext = mtitle.text.toString().trim()
            val authortext = mauthor.text.toString().trim()

            webViewContent.evaluateJavascript("getEditorContent()") { rawHtml ->
                val bodytext = unescapeJsString(rawHtml).trim()

                if (titletext.isNotEmpty() && authortext.isNotEmpty() && bodytext.isNotEmpty() && (firebaseUser != null)) {
                    writeNewpost(
                        userId = firebaseUser.uid,
                        name = authortext,
                        title = titletext,
                        body = bodytext,
                        category = selectedCategory,
                        authEmail = account?.emailId?.toString() ?: ""
                    )
                    val intent = Intent(this@manage_home, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this, "전체 칸을 모두 채워주세요", Toast.LENGTH_SHORT).show()
                }
            }
        }
        CancelBtn.setOnClickListener {
            val intent = Intent(this@manage_home, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}