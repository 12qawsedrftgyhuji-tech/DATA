package com.example.wpqkf

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.Manifest
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts

class TextAnalyzer : ImageAnalysis.Analyzer {

    // 한국어 텍스트 인식기 객체 생성
    private val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run{
            imageProxy.close()
            return
        }
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                Log.d("TextAnalyzer","OCR 성공:${visionText}")
            }
            .addOnFailureListener { e ->
                Log.e("TextAnalyzer","OCR 실패 :",e)
            }
            .addOnCompleteListener {
                // 분석이 끝나면 imageProxy를 닫아주어야 다음 프레임이 전달됨
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

    //cameraX - MLkit 작업용
    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }

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

    //cameraX - MLkit 작업
    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val cameraProvider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, TextAnalyzer()) }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
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
        //CameraX - MLkit 작업
        previewView = findViewById(R.id.previewView) // preview 화면을 의미한다.
        cameraExecutor = Executors.newSingleThreadExecutor() // 싱글쓰레드에서 카메라가 작업할 수 있도록 도와줄 변수다.

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }



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