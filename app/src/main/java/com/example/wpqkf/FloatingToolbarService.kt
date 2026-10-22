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
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
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
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

class FloatingToolbarService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplayImage: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    @Volatile
    private var latestBitmap: Bitmap? = null

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

    // 마이크 UI 상태 관리 (textRecognizer 와 동일)
    private val uiHandler = Handler(Looper.getMainLooper())
    private val micIdleRunnable = Runnable { setMicIdle() }
    private var micRed = false
    private var isListening = false

    private val visionModel by lazy {
        GenerativeModel(
            modelName = "gemini-3.8-flash",
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
            setupImageReader()
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

        val maxDim = 1280
        if (realWidth > realHeight) {
            captureHeight = maxDim
            captureWidth = (realWidth.toFloat() / realHeight * maxDim).toInt()
        } else {
            captureWidth = maxDim
            captureHeight = (realHeight.toFloat() / realWidth * maxDim).toInt()
        }

        if (captureWidth % 2 != 0) captureWidth--
        if (captureHeight % 2 != 0) captureHeight--
    }

    private fun setupImageReader() {
        if (virtualDisplayImage != null) return

        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]
                val rowPadding = plane.rowStride - plane.pixelStride * captureWidth
                val temp = Bitmap.createBitmap(
                    captureWidth + rowPadding / plane.pixelStride,
                    captureHeight,
                    Bitmap.Config.ARGB_8888
                )
                temp.copyPixelsFromBuffer(plane.buffer)
                latestBitmap = Bitmap.createBitmap(temp, 0, 0, captureWidth, captureHeight)
                temp.recycle()
            } catch (e: Exception) {
                Log.e("FloatingSvc", "ImageReader 버퍼 변환 실패", e)
            } finally {
                image.close()
            }
        }, Handler(Looper.getMainLooper()))

        virtualDisplayImage = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            captureWidth,
            captureHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
    }

    fun captureCurrentScreen(onCaptured: (Bitmap?) -> Unit) {
        val currentBitmap = latestBitmap
        if (currentBitmap != null && !currentBitmap.isRecycled) {
            onCaptured(currentBitmap.copy(Bitmap.Config.ARGB_8888, false))
        } else {
            onCaptured(null)
        }
    }

    // 마이크 상태별 버튼 배경 색상 제어 (기본/파랑/빨강)
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

    private fun sendQuestionToAi(userPrompt: String) {
        val prompt = if (userPrompt.isNotBlank()) userPrompt else "현재 화면을 설명해주고 내가 어디를 눌러야 할지 알려주세요."
        Log.d("FloatingSvc", "전송 클릭: '$prompt', projection=${mediaProjection != null}, bitmap=${latestBitmap != null}")

        tvAiResponse.text = "AI가 화면을 분석하는 중입니다..."

        captureCurrentScreen { bitmap ->
            if (bitmap != null) {
                analyzeScreenWithAi(bitmap, prompt)
            } else {
                Log.e("FloatingSvc", "화면 캡처 비트맵이 null임")
                tvAiResponse.text = "화면 분석 준비 중입니다. 잠시 후 다시 전송을 눌러주세요."
                Toast.makeText(this, "화면을 분석할 준비가 되지 않았습니다. (잠시 후 시도)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun analyzeScreenWithAi(bitmap: Bitmap, userPrompt: String) {
        Toast.makeText(this, "AI가 화면을 분석 중입니다...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputContent = content {
                    image(bitmap)
                    text(
                        "당신은 디지털 기기 사용이 어려운 어르신을 돕는 친절한 디지털 도우미입니다.\n" +
                                "현재 스마트폰 화면과 사용자의 질문: '$userPrompt'\n" +
                                "어느 버튼을 눌러야 하는지 마크다운 기호 없이 2~3문장의 쉬운 존댓말로 답변해 주세요."
                    )
                }

                val response = visionModel.generateContent(inputContent)
                val aiResponseText = response.text ?: "답변을 가져오지 못했습니다."

                withContext(Dispatchers.Main) {
                    tvAiResponse.text = aiResponseText
                    Toast.makeText(this@FloatingToolbarService, aiResponseText, Toast.LENGTH_LONG).show()
                    tts?.speak(aiResponseText, TextToSpeech.QUEUE_FLUSH, null, null)
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                Log.e("FloatingSvc", "Gemini 호출 실패", e)
                withContext(Dispatchers.Main) {
                    tvAiResponse.text = "오류 발생: ${e.message}"
                    Toast.makeText(this@FloatingToolbarService, "오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
                    bitmap.recycle()
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
        uiHandler.removeCallbacks(micIdleRunnable)
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
        if (::floatingView.isInitialized && floatingView.isAttachedToWindow) {
            windowManager.removeView(floatingView)
        }
        virtualDisplayImage?.release()
        imageReader?.close()
        mediaProjection?.stop()
    }
}
