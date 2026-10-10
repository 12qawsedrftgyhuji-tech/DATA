package com.example.wpqkf

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.vertexai.vertexAI
import androidx.lifecycle.lifecycleScope
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.initialize
import kotlinx.coroutines.launch
import com.google.firebase.appcheck.appCheck
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions

class YourImageAnalyzer : ImageAnalysis.Analyzer {

    // 한국어 텍스트 인식기 객체 생성
    private val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            // ML Kit으로 이미지 분석 실행
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    // 텍스트 인식 성공 시 처리 (visionText.text)
                }
                .addOnFailureListener { e ->
                    // 에러 처리
                }
                .addOnCompleteListener {
                    // 분석이 끝나면 반드시 imageProxy를 닫아주어야 다음 프레임이 전달됩니다.
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
class textRecognizer : AppCompatActivity() {
    private lateinit var mBtnSchedule4: ImageButton
    private lateinit var mBtnRecord4: ImageButton
    private lateinit var mBtnHome4: ImageButton
    private lateinit var mBtnAi4: ImageButton
    private lateinit var mBtnProfile4: ImageButton
    val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

    fun isGoogleUser() :Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false

        return user.providerData.any{
            profile -> profile.providerId == GoogleAuthProvider.PROVIDER_ID
        }
    }

    fun checkUserAndShowDialog(AreuOk : Boolean) {
        if (!AreuOk) {
            MaterialAlertDialogBuilder(this) // Activity 내인 경우 this 사용
                .setTitle("알림")
                .setMessage("Gemini AI 기능은 구글 계정으로 로그인한 경우에만 사용할 수 있습니다.")
                .setPositiveButton("확인") { dialog, _ ->
                    dialog.dismiss() // 확인 누르면 닫기
                }
                .setCancelable(true) // 창 바깥 터치 시 닫기 허용
                .show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_tr)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        //메인 코드 작성하는 공간

        checkUserAndShowDialog(isGoogleUser())//구글 계정일 때만 ai 기능-> gemini 쓸 수 있게끔 ㄱㄱ
        mBtnSchedule4 = findViewById(R.id.schedule)
        mBtnRecord4 = findViewById(R.id.record)
        mBtnHome4 = findViewById(R.id.home)
        mBtnAi4 = findViewById(R.id.ai)
        mBtnProfile4 = findViewById(R.id.profile)

        mBtnHome4.setOnClickListener {
            val intent = Intent(this@textRecognizer, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
        mBtnSchedule4.setOnClickListener {
            val intent = Intent(this@textRecognizer, schedule::class.java)
            startActivity(intent)
            finish()
        }
        mBtnRecord4.setOnClickListener {
            val intent = Intent(this@textRecognizer, record::class.java)
            startActivity(intent)
            finish()
        }
        mBtnProfile4.setOnClickListener {
            val intent = Intent(this@textRecognizer, profile::class.java)
            startActivity(intent)
            finish()
        }
    }
}