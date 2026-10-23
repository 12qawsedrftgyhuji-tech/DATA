package com.example.wpqkf

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.method.ScrollingMovementMethod
import android.util.DisplayMetrics
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.InvalidAPIKeyException
import com.google.ai.client.generativeai.type.QuotaExceededException
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class FloatingToolbarService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplayRecord: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null

    private var currentTempFile: File? = null
    private var currentFinalFile: File? = null

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var params: WindowManager.LayoutParams

    private lateinit var collapsedView: LinearLayout
    private lateinit var expandedToolbar: LinearLayout
    private lateinit var btnCollapse: ImageButton
    private lateinit var etQuestion: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnMic: ImageButton
    private lateinit var btnStopRecording: Button
    private lateinit var tvAiResponse: TextView

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private val uiHandler = Handler(Looper.getMainLooper())
    private val micIdleRunnable = Runnable { setMicIdle() }
    private var micRed = false
    private var isListening = false
    private var isRecording = false

    // 요청 상태 및 한도 차단 관리
    private var isRequesting = false
    private var quotaBlockedUntil = 0L
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val visionModel by lazy {
        GenerativeModel(
            modelName = "gemini-3.5-flash-lite",
            apiKey = BuildConfig.GEMINI_API_KEY
        )
    }

    private var captureWidth = 720
    private var captureHeight = 1280
    private var screenDensity = 160

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val themedContext = ContextThemeWrapper(this, R.style.Theme_Wpqkf)
        floatingView = LayoutInflater.from(themedContext).inflate(R.layout.layout_floating_toolbar, null)

        collapsedView = floatingView.findViewById(R.id.collapsed_view)
        expandedToolbar = floatingView.findViewById(R.id.expanded_toolbar)
        btnCollapse = floatingView.findViewById(R.id.btn_collapse)
        etQuestion = floatingView.findViewById(R.id.et_question)
        btnSend = floatingView.findViewById(R.id.btn_send)
        btnMic = floatingView.findViewById(R.id.btn_mic)
        btnStopRecording = floatingView.findViewById(R.id.btn_stop_recording)
        tvAiResponse = floatingView.findViewById(R.id.tv_ai_response)

        tvAiResponse.movementMethod = ScrollingMovementMethod()

        collapsedView.visibility = View.VISIBLE
        expandedToolbar.visibility = View.GONE

        setMicIdle()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        windowManager.addView(floatingView, params)

        setupTouchAndClickListeners()
        initSpeechAndTts()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()

        val resultCode = intent?.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED

        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("RESULT_DATA", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra<Intent>("RESULT_DATA")
        }

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            val projectionManager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopSelf()
                }
            }, Handler(Looper.getMainLooper()))

            initScreenMetrics()
            startScreenRecording()
        } else {
            Log.e("FloatingSvc", "projection 데이터 없음: code=$resultCode, data=${resultData != null}")
        }

        return START_NOT_STICKY
    }

    private fun initScreenMetrics() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        var realWidth = 720
        var realHeight = 1280

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            realWidth = bounds.width()
            realHeight = bounds.height()
            screenDensity = resources.configuration.densityDpi
        } else {
            @Suppress("DEPRECATION")
            val metrics = DisplayMetrics()
            wm.defaultDisplay.getMetrics(metrics)
            realWidth = metrics.widthPixels
            realHeight = metrics.heightPixels
            screenDensity = metrics.densityDpi
        }

        if (realWidth > realHeight) {
            captureWidth = 1280
            captureHeight = 720
        } else {
            captureWidth = 720
            captureHeight = 1280
        }
    }

    private fun startScreenRecording() {
        try {
            val recordDir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "ai_recordings")
            if (!recordDir.exists()) recordDir.mkdirs()

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

            currentTempFile = File(recordDir, "REC_$timeStamp.mp4.tmp")
            currentFinalFile = File(recordDir, "REC_$timeStamp.mp4")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(2 * 1024 * 1024)
                setVideoFrameRate(24)
                setVideoSize(captureWidth, captureHeight)
                setOutputFile(currentTempFile?.absolutePath)
                prepare()
            }

            virtualDisplayRecord = mediaProjection?.createVirtualDisplay(
                "ScreenRecord",
                captureWidth,
                captureHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder?.surface,
                null,
                null
            )

            mediaRecorder?.start()
            isRecording = true
            Log.d("FloatingSvc", "전체 화면 녹화 시작: ${currentTempFile?.absolutePath}")

        } catch (e: Exception) {
            Log.e("FloatingSvc", "화면 녹화 시작 실패: ${e.message}", e)
            try { mediaRecorder?.release() } catch (_: Exception) {}
            mediaRecorder = null
            try { virtualDisplayRecord?.release() } catch (_: Exception) {}
            virtualDisplayRecord = null
            currentTempFile?.delete()
            currentTempFile = null
            currentFinalFile = null
        }
    }

    private fun stopScreenRecording() {
        if (!isRecording) return

        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null
            virtualDisplayRecord?.release()
            virtualDisplayRecord = null
            isRecording = false

            if (currentTempFile != null && currentFinalFile != null && currentTempFile!!.exists() && currentTempFile!!.length() > 0) {
                val renamed = currentTempFile!!.renameTo(currentFinalFile!!)
                Log.d("FloatingSvc", "녹화 파일 변환 완료: 성공=$renamed, 경로=${currentFinalFile!!.absolutePath}, 크기=${currentFinalFile!!.length()}")
            } else {
                Log.e("FloatingSvc", "임시 녹화 파일이 존재하지 않거나 0바이트입니다.")
                currentTempFile?.delete()
            }

            val recordDir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "ai_recordings")
            VideoStorageManager(recordDir).checkAndCleanStorage()

        } catch (e: Exception) {
            Log.e("FloatingSvc", "녹화 중지 처리 예외", e)
            currentTempFile?.delete()
        } finally {
            currentTempFile = null
            currentFinalFile = null
        }
    }

    fun captureCurrentScreen(onCaptured: (Bitmap?) -> Unit) {
        val svc = ScreenCaptureAccessibilityService.instance
        if (svc != null) {
            floatingView.visibility = View.INVISIBLE
            uiHandler.postDelayed({
                svc.captureScreen { bmp ->
                    floatingView.visibility = View.VISIBLE
                    onCaptured(bmp)
                }
            }, 150)
        } else {
            onCaptured(null)
        }
    }

    private fun setMicOn() {
        uiHandler.removeCallbacks(micIdleRunnable)
        micRed = false
        btnMic.setBackgroundResource(R.drawable.bg_mic_on)
    }

    private fun setMicOff() {
        micRed = true
        btnMic.setBackgroundResource(R.drawable.bg_mic_off)
        uiHandler.removeCallbacks(micIdleRunnable)
        uiHandler.postDelayed(micIdleRunnable, 2000)
    }

    private fun setMicIdle() {
        micRed = false
        btnMic.setBackgroundResource(R.drawable.bg_circle)
    }

    private fun resetMicUi() {
        isListening = false
        if (!micRed) setMicOff()
    }

    private fun setupTouchAndClickListeners() {
        collapsedView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val diffX = abs(event.rawX - initialTouchX)
                        val diffY = abs(event.rawY - initialTouchY)
                        if (diffX < 10 && diffY < 10) {
                            toggleToolbar(expand = true)
                        }
                        return true
                    }
                }
                return false
            }
        })

        etQuestion.setOnTouchListener { _, _ ->
            updateWindowFocus(true)
            false
        }

        btnCollapse.setOnClickListener {
            toggleToolbar(expand = false)
        }

        btnSend.setOnClickListener {
            val userText = etQuestion.text.toString().trim()
            sendQuestionToAi(userText)
        }

        btnMic.setOnClickListener {
            onMicClicked()
        }

        btnStopRecording.setOnClickListener {
            stopSelf()
        }
    }

    private fun onMicClicked() {
        if (isListening) {
            tvAiResponse.text = "음성을 텍스트로 변환 중입니다..."
            setMicOff()
            speechRecognizer?.stopListening()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else {
            Toast.makeText(this, "마이크 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "이 기기에서는 음성 인식을 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        tts?.stop()
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(recognitionListener)
            }
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        isListening = true
        setMicOn()
        tvAiResponse.text = "말씀해 주세요... (다 말씀하셨으면 마이크 버튼을 다시 눌러주세요)"
        speechRecognizer?.startListening(intent)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d("STT", "듣기 준비 완료")
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) {
                etQuestion.setText(text)
            }
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            resetMicUi()
            if (!text.isNullOrBlank()) {
                etQuestion.setText(text)
                sendQuestionToAi(text)
            } else {
                Toast.makeText(this@FloatingToolbarService, "음성을 알아듣지 못했습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onError(error: Int) {
            resetMicUi()
            val msg = when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한이 없습니다."
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "네트워크 연결을 확인해 주세요."
                SpeechRecognizer.ERROR_NO_MATCH -> "음성을 알아듣지 못했습니다. 다시 말씀해 주세요."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "말씀이 들리지 않았습니다."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "음성 인식기가 사용 중입니다."
                else -> "음성 인식 오류 (코드: $error)"
            }
            Log.e("FloatingSvc", "음성 인식 에러: $error ($msg)")
            Toast.makeText(this@FloatingToolbarService, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateWindowFocus(focusable: Boolean) {
        if (focusable) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        if (::floatingView.isInitialized && floatingView.isAttachedToWindow) {
            windowManager.updateViewLayout(floatingView, params)
        }
    }

    private fun initSpeechAndTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.KOREAN
            }
        }
    }

    private fun finishRequest() {
        isRequesting = false
        btnSend.isEnabled = true
        btnSend.alpha = 1f
    }

    private fun sendQuestionToAi(userPrompt: String) {
        Log.d("FloatingSvc", "전송 클릭: '$userPrompt', 접근성 instance=${ScreenCaptureAccessibilityService.instance}")

        // 1) 이미 요청 중이면 중복 요청 방지
        if (isRequesting) {
            tvAiResponse.text = "AI가 아직 답변을 준비하고 있어요. 잠시만 기다려 주세요."
            return
        }

        // 2) 한도 초과 직후 30분 동안 차단
        if (System.currentTimeMillis() < quotaBlockedUntil) {
            tvAiResponse.text = "오늘 AI 사용량을 모두 썼어요. 나중에 다시 이용해 주세요."
            return
        }

        val prompt = if (userPrompt.isNotBlank()) userPrompt else "현재 화면을 설명해주고 내가 어디를 눌러야 할지 알려주세요."

        isRequesting = true
        btnSend.isEnabled = false
        btnSend.alpha = 0.4f
        tvAiResponse.text = "AI가 화면을 분석하는 중입니다..."

        if (ScreenCaptureAccessibilityService.instance == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tvAiResponse.text = "화면 도우미 연결이 끊겼어요. 설정 → 접근성에서 'AI 화면 도우미'를 껐다가 다시 켜 주세요."
            finishRequest()
            return
        }

        captureCurrentScreen { bitmap ->
            if (bitmap != null) {
                analyzeScreenWithAi(bitmap, prompt)
            } else {
                Log.e("FloatingSvc", "화면 캡처 비트맵이 null임")
                tvAiResponse.text = "화면을 가져오지 못했어요. 잠시 후 다시 눌러 주세요."
                finishRequest()
            }
        }
    }

    private suspend fun generateWithRetry(inputContent: Content, maxAttempts: Int = 2): String? {
        var lastError: Exception? = null
        for (attempt in 1..maxAttempts) {
            try {
                return visionModel.generateContent(inputContent).text
            } catch (e: CancellationException) {
                throw e
            } catch (e: QuotaExceededException) {
                throw e
            } catch (e: InvalidAPIKeyException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                Log.e("FloatingSvc", "Gemini 호출 실패 ($attempt/$maxAttempts)", e)
                if (attempt < maxAttempts) delay(2000L)
            }
        }
        throw lastError ?: IllegalStateException("알 수 없는 오류")
    }

    private fun analyzeScreenWithAi(bitmap: Bitmap, userPrompt: String) {
        serviceScope.launch {
            try {
                val inputContent = content {
                    image(bitmap)
                    text(
                        "당신은 디지털 기기 사용이 어려운 어르신을 돕는 친절한 디지털 도우미입니다.\n" +
                                "현재 스마트폰 화면과 사용자의 질문: '$userPrompt'\n" +
                                "어느 버튼을 눌러야 하는지 마크다운 기호 없이 2~3문장의 쉬운 존댓말로 답변해 주세요."
                    )
                }

                val answer = generateWithRetry(inputContent) ?: "답변을 가져오지 못했어요. 다시 한 번 눌러 주세요."

                withContext(Dispatchers.Main) {
                    tvAiResponse.text = answer
                    tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, null)
                    bitmap.recycle()
                    finishRequest()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("FloatingSvc", "Gemini 최종 호출 실패", e)

                if (e is QuotaExceededException) {
                    quotaBlockedUntil = System.currentTimeMillis() + 30 * 60 * 1000L
                }

                val msg = when (e) {
                    is QuotaExceededException -> "오늘 AI 사용량을 모두 썼어요. 나중에 다시 이용해 주세요."
                    is InvalidAPIKeyException -> "AI 설정에 문제가 있어요. 앱 관리자에게 알려 주세요."
                    else -> "지금 AI가 많이 바빠요. 잠시 후에 다시 눌러 주세요."
                }

                withContext(Dispatchers.Main) {
                    tvAiResponse.text = msg
                    bitmap.recycle()
                    finishRequest()
                }
            }
        }
    }

    private fun toggleToolbar(expand: Boolean) {
        if (expand) {
            collapsedView.visibility = View.GONE
            expandedToolbar.visibility = View.VISIBLE
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            updateWindowFocus(true)
        } else {
            expandedToolbar.visibility = View.GONE
            collapsedView.visibility = View.VISIBLE
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            updateWindowFocus(false)
        }
    }

    private fun startForegroundNotification() {
        val channelId = "ai_screen_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "AI 화면 도우미 서비스",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("AI 외부 화면 도우미 실행 중")
            .setContentText("외부 앱 사용 중 AI 도움을 받을 수 있습니다.")
            .setSmallIcon(R.drawable.ic_ai)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            val hasMic = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasMic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(1001, notification, type)
        } else {
            startForeground(1001, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopScreenRecording()
        uiHandler.removeCallbacks(micIdleRunnable)
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
        if (::floatingView.isInitialized && floatingView.isAttachedToWindow) {
            windowManager.removeView(floatingView)
        }
        virtualDisplayRecord?.release()
        mediaProjection?.stop()
    }
}
