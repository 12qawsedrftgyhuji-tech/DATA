package com.example.wpqkf

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class ai : AppCompatActivity() {
    private lateinit var mBtnSchedule4: ImageButton
    private lateinit var mBtnRecord4: ImageButton
    private lateinit var mBtnHome4: ImageButton
    private lateinit var mBtnAi4: ImageButton
    private lateinit var mBtnProfile4: ImageButton
    private lateinit var mBtnTR: Button
    private lateinit var mBtnTa: Button
    private lateinit var btnStartExternalAi: Button

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        checkMicAndLaunch()
    }

    private val recordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        checkAccessibilityAndLaunch()
    }

    private val accessibilitySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (isAccessibilityServiceEnabled()) {
            launchScreenCapture()
        } else {
            Toast.makeText(this, "접근성 권한이 꺼져 있어 AI 화면 안내를 쓸 수 없어요.", Toast.LENGTH_LONG).show()
        }
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, FloatingToolbarService::class.java).apply {
                putExtra("RESULT_CODE", result.resultCode)
                putExtra("RESULT_DATA", result.data)
            }

            ContextCompat.startForegroundService(this, serviceIntent)
            Toast.makeText(this, "AI 외부 도우미가 시작되었습니다.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "화면 캡처 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, ScreenCaptureAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(':').any {
            ComponentName.unflattenFromString(it) == expected
        }
    }

    private fun checkAccessibilityAndLaunch() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || isAccessibilityServiceEnabled()) {
            launchScreenCapture()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("접근성 권한이 필요해요")
            .setMessage("AI가 지금 화면을 보고 어디를 눌러야 하는지 알려 드리려면, 설정에서 'AI 화면 도우미'를 켜 주세요. 한 번만 켜 두면 다음부터는 묻지 않아요.")
            .setPositiveButton("설정으로 이동") { _, _ ->
                accessibilitySettingsLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun startExternalAiHelper() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "다른 앱 위에 그리기 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            checkMicAndLaunch()
        }
    }

    private fun checkMicAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            checkAccessibilityAndLaunch()
        }
    }

    private fun launchScreenCapture() {
        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val captureIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            projectionManager.createScreenCaptureIntent(
                MediaProjectionConfig.createConfigForDefaultDisplay()
            )
        } else {
            projectionManager.createScreenCaptureIntent()
        }

        screenCaptureLauncher.launch(captureIntent)
    }

    fun isEmailUser(): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false

        return user.providerData.any { profile ->
            (profile.providerId == GoogleAuthProvider.PROVIDER_ID) ||
                    (profile.providerId == "oidc.kakao") ||
                    (profile.providerId == "naver")
        }
    }

    fun checkUserAndShowDialog(AreuOk: Boolean) {
        if (!AreuOk) {
            MaterialAlertDialogBuilder(this)
                .setTitle("알림")
                .setMessage("Gemini AI 기능은 구글, 카카오, 네이버 계정으로 로그인한 경우에만 사용할 수 있습니다.")
                .setPositiveButton("확인") { dialog, _ ->
                    dialog.dismiss()
                    startActivity(Intent(this@ai, MainActivity::class.java))
                    finish()
                }
                .setCancelable(false)
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_ai)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        checkUserAndShowDialog(isEmailUser())

        mBtnSchedule4 = findViewById(R.id.schedule)
        mBtnRecord4 = findViewById(R.id.record)
        mBtnHome4 = findViewById(R.id.home)
        mBtnAi4 = findViewById(R.id.ai)
        mBtnProfile4 = findViewById(R.id.profile)
        mBtnTR = findViewById(R.id.BtnTR)
        mBtnTa = findViewById(R.id.btnTa)
        btnStartExternalAi = findViewById(R.id.btnStartExternalAi)

        btnStartExternalAi.setOnClickListener {
            startExternalAiHelper()
        }

        mBtnHome4.setOnClickListener {
            val intent = Intent(this@ai, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
        mBtnSchedule4.setOnClickListener {
            val intent = Intent(this@ai, schedule::class.java)
            startActivity(intent)
            finish()
        }
        mBtnRecord4.setOnClickListener {
            val intent = Intent(this@ai, record::class.java)
            startActivity(intent)
            finish()
        }
        mBtnProfile4.setOnClickListener {
            val intent = Intent(this@ai, profile::class.java)
            startActivity(intent)
            finish()
        }
        mBtnTR.setOnClickListener {
            val intent = Intent(this@ai, textRecognizer::class.java)
            startActivity(intent)
            finish()
        }
        mBtnTa.setOnClickListener {
            val intent = Intent(this@ai, talkwithai::class.java)
            startActivity(intent)
            finish()
        }
    }
}
