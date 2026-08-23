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
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts

class ModifyText : AppCompatActivity() {
    private lateinit var database : DatabaseReference
    private lateinit var mtitle : EditText
    private lateinit var mauthor : EditText
    private lateinit var btnMdf : Button
    private lateinit var btnCancel : Button
    private lateinit var mFirebaseAuth: FirebaseAuth
    private lateinit var mDatabaseRef: DatabaseReference
    private lateinit var webViewBody : WebView
    private lateinit var spinnerCt : Spinner
    private var selectedCategory = ""
    private var pendingBodyHtml = ""
    private var webViewLoaded = false

    private fun setupWebView() {
        webViewBody.settings.javaScriptEnabled = true
        webViewBody.webChromeClient = WebChromeClient()
        webViewBody.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                webViewLoaded = true
                if (pendingBodyHtml.isNotEmpty()) {
                    setEditorBody(pendingBodyHtml)
                }
            }
        }
        webViewBody.loadUrl("file:///android_asset/editor.html")
    }
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            uploadImageUriAndInsert(uri)
        } else {
            Toast.makeText(this, "이미지를 선택하지 않았습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setEditorBody(html: String) {
        val safeHtml = html
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
        webViewBody.evaluateJavascript("setEditorContent('$safeHtml')", null)
    }

    private fun uploadImageUriAndInsert(uri: Uri) {
        val fileName = "post_images/${UUID.randomUUID()}.jpg"
        val storageRef = FirebaseStorage.getInstance().reference.child(fileName)

        storageRef.putFile(uri)
            .continueWithTask { storageRef.downloadUrl }
            .addOnSuccessListener { downloadUri ->
                val safeUrl = downloadUri.toString().replace("'", "\\'")
                webViewBody.evaluateJavascript("insertImageAtCursor('$safeUrl')", null)
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
    private val categories = arrayListOf<String>()
    private lateinit var categoryAdapter: ArrayAdapter<String>

    private fun setupCategorySpinner() {
        categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCt.adapter = categoryAdapter

        database.child("categories").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                categories.clear()
                for (catSnapshot in snapshot.children) {
                    val cat = catSnapshot.getValue(String::class.java)
                    if (cat != null) categories.add(cat)
                }
                if (!categories.contains("직접 입력")) categories.add("직접 입력")
                categoryAdapter.notifyDataSetChanged()

                // Post 데이터가 이미 로드되어 selectedCategory가 세팅됐다면 반영
                applyCategorySelection()
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        spinnerCt.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (categories[position] == "직접 입력") {
                    val builder = AlertDialog.Builder(this@ModifyText)
                    builder.setTitle("새 종류 추가")
                    val input = EditText(this@ModifyText)
                    builder.setView(input)
                    builder.setPositiveButton("추가") { _, _ ->
                        val newCat = input.text.toString().trim()
                        if (newCat.isNotEmpty() && !categories.contains(newCat)) {
                            database.child("categories").push().setValue(newCat)
                            selectedCategory = newCat
                        }
                    }
                    builder.setNegativeButton("취소") { dialog, _ ->
                        dialog.cancel()
                        applyCategorySelection() // 취소하면 원래 카테고리로 되돌림
                    }
                    builder.show()
                } else {
                    selectedCategory = categories[position]
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun applyCategorySelection() {
        if (selectedCategory.isNotEmpty() && categories.contains(selectedCategory)) {
            spinnerCt.setSelection(categories.indexOf(selectedCategory))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_modifytext)
        mtitle = findViewById(R.id.tv_title)
        mauthor = findViewById(R.id.tv_author)
        webViewBody = findViewById(R.id.wv_mainText)
        spinnerCt = findViewById(R.id.spinner_category)
        btnCancel = findViewById(R.id.btn_cancel)
        btnMdf = findViewById(R.id.btn_goMdf)
        val btnPickImage : Button = findViewById(R.id.btn_pick_image)
        btnPickImage.setOnClickListener {
            pickImageLauncher.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        }

        database = Firebase.database.reference

        setupWebView()
        setupCategorySpinner()

        val postKey = intent.getStringExtra("post_key")

        if (postKey != null) {
            database.child("posts").child(postKey).get().addOnSuccessListener { snapshot ->
                val post = snapshot.getValue(Post::class.java)
                if (post != null) {
                    mtitle.setText(post.title)
                    mauthor.setText(post.author)
                    selectedCategory = post.category ?: ""
                    pendingBodyHtml = post.body ?: ""

                    // 카테고리 목록이 이미 로드돼있다면 바로 선택 반영
                    applyCategorySelection()
                    // 본문은 WebView가 로딩 끝났으면 바로, 아니면 onPageFinished에서 자동 세팅됨
                    if (webViewLoaded) setEditorBody(pendingBodyHtml)
                }
            }.addOnFailureListener {
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

            webViewBody.evaluateJavascript("getEditorContent()") { rawHtml ->
                val bodytext = unescapeJsString(rawHtml).trim()

                if (titletext.isNotEmpty() && authortext.isNotEmpty() && bodytext.isNotEmpty() && (firebaseUser != null)) {
                    if (postKey != null) {
                        val updatedPost = mapOf(
                            "title" to titletext,
                            "author" to authortext,
                            "body" to bodytext,
                            "category" to selectedCategory
                        )
                        database.child("posts").child(postKey).updateChildren(updatedPost)
                            .addOnSuccessListener {
                                Toast.makeText(this, "수정이 완료되었습니다.", Toast.LENGTH_SHORT).show()
                                val intent = Intent(this@ModifyText, MainActivity::class.java)
                                startActivity(intent)
                                finish()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "수정 실패", Toast.LENGTH_SHORT).show()
                            }
                    }
                } else {
                    Toast.makeText(this, "전체 칸을 모두 채워주세요", Toast.LENGTH_SHORT).show()
                }
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