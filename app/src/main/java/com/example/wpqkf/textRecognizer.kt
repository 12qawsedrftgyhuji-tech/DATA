package com.example.wpqkf

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ScrollView
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
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
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

const val DAILY_TOKEN_LIMIT = 100_000L
//OCR 작업
data class OcrBlock(
    val text: String,
    val box: Rect?,
    val lines: List<String>,
    val matchedKeywords: List<String>
)
data class KioskResult(
    val isKiosk: Boolean,
    val score: Int,
    val blocks: List<OcrBlock>
)
class TextAnalyzer(
    private val onResult: (KioskResult) -> Unit
) : ImageAnalysis.Analyzer {

    // 한국어 텍스트 인식기 객체 생성
    private val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

    private val kioskKeywords = mapOf(
        "결제" to 2,
        "주문" to 2,
        "카드" to 1,
        "포장" to 1,
        "매장" to 1,
        "번호표" to 2,
        "터치" to 1
    )
    private val threshold = 3
    private var lastTime = 0L

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        val mediaImage = imageProxy.image
        if (mediaImage == null || now - lastTime < 300) {
            imageProxy.close()
            return
        }
        lastTime = now

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val blocks = visionText.textBlocks.map { block ->
                    // 공백 제거 후 비교 (OCR 띄어쓰기 오류 대비)
                    val compact = block.text.replace("\\s".toRegex(), "")
                    OcrBlock(
                        text = block.text,
                        box = block.boundingBox,
                        lines = block.lines.map { it.text },
                        matchedKeywords = kioskKeywords.keys.filter { compact.contains(it) }
                    )
                }
                val matched = blocks.flatMap { it.matchedKeywords }.toSet()
                val score = matched.sumOf { kioskKeywords[it] ?: 0 }

                val result = KioskResult(score >= threshold, score, blocks)

                blocks.forEachIndexed { i, b ->
                    Log.d("TextAnalyzer", "블록 $i: ${b.text.replace("\n", " ")}")
                    Log.d("TextAnalyzer", "  위치: ${b.box}, 키워드: ${b.matchedKeywords}")
                }
                Log.d("TextAnalyzer", "키오스크 판별: ${result.isKiosk} (점수 ${result.score})")

                onResult(result)
            }
            .addOnFailureListener { e ->
                Log.e("TextAnalyzer", "OCR 실패 :", e)
            }
            .addOnCompleteListener {
                // 분석이 끝나면 imageProxy를 닫아주어야 다음 프레임이 전달됨
                imageProxy.close()
            }
    }
}

//대화 한 턴 (질문 + 답변)
data class ChatTurn(
    val question: String,
    val answer: String
)

//Firestore 저장소: users/{uid}/chats/{자동ID}
class ChatRepository {
    private val db = FirebaseFirestore.getInstance("engineersfirestore")

    private fun chatsRef() = FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
        db.collection("users").document(uid).collection("chats")
    }

    fun save(question: String, answer: String, ocr: String) {
        val ref = chatsRef() ?: return
        val data = hashMapOf(
            "question" to question,
            "answer" to answer,
            "ocr" to ocr.take(3000), // 너무 길어지지 않게 제한
            "time" to FieldValue.serverTimestamp()
        )
        ref.add(data).addOnFailureListener { Log.e("ChatRepo", "저장 실패", it) }
    }

    // 최근 limit개를 오래된 순서로 반환
    fun loadRecent(limit: Long, onLoaded: (List<ChatTurn>) -> Unit) {
        val ref = chatsRef() ?: return onLoaded(emptyList())
        ref.orderBy("time", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .addOnSuccessListener { snap ->
                val turns = snap.documents.mapNotNull { d ->
                    val q = d.getString("question")
                    val a = d.getString("answer")
                    if (q != null && a != null) ChatTurn(q, a) else null
                }.reversed()
                onLoaded(turns)
            }
            .addOnFailureListener {
                Log.e("ChatRepo", "불러오기 실패", it)
                onLoaded(emptyList())
            }
    }

    // ---- 하루 토큰 사용량 (users/{uid}/usage/{yyyy-MM-dd}) ----
    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
            .apply { timeZone = TimeZone.getTimeZone("Asia/Seoul") }
            .format(Date())

    private fun usageRef() = FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
        db.collection("users").document(uid).collection("usage").document(todayKey())
    }

    // 오늘 사용한 토큰 수 (확인 실패 시 null)
    fun getTodayUsage(onLoaded: (Long?) -> Unit) {
        val ref = usageRef() ?: return onLoaded(null)
        ref.get()
            .addOnSuccessListener { onLoaded(it.getLong("tokens") ?: 0L) }
            .addOnFailureListener {
                Log.e("ChatRepo", "사용량 확인 실패", it)
                onLoaded(null)
            }
    }

    fun addUsage(tokens: Int) {
        if (tokens <= 0) return
        val ref = usageRef() ?: return
        val data = hashMapOf(
            "tokens" to FieldValue.increment(tokens.toLong()),
            "updated" to FieldValue.serverTimestamp()
        )
        ref.set(data, SetOptions.merge())
            .addOnFailureListener { Log.e("ChatRepo", "사용량 기록 실패", it) }
    }

    fun clearAll(onDone: () -> Unit) {
        val ref = chatsRef() ?: return onDone()
        ref.limit(500).get().addOnSuccessListener { snap ->
            val batch = db.batch()
            snap.documents.forEach { batch.delete(it.reference) }
            batch.commit().addOnCompleteListener { onDone() }
        }.addOnFailureListener { onDone() }
    }
}
private var username = "사용자"
//Gemini 대화 작업 (채팅 세션으로 대화 맥락 유지)
class GeminiClient {

    private val model = GenerativeModel(
        modelName = "gemini-3.1-flash-lite",
        apiKey = BuildConfig.GEMINI_API_KEY,
        systemInstruction = content {
            text(
                """
                당신은 키오스크 사용이 어려운 사용자를 돕는 친절한 도우미입니다.
                사용자의 이름은 ${username}입니다.
                사용자가 질문할 때마다 카메라로 촬영한 키오스크 화면의 OCR 텍스트가 함께 전달됩니다.
                OCR 오타가 있을 수 있으니 문맥으로 보정하세요.
                현재 화면을 기준으로, 디지털 소외 계층 분들도 이해할 수 있게 쉬운 말로 단계별로 짧게 답하세요.
                OCR텍스트는 높임말로 변환하지말고 있는 그대로 전달해주세요.(주문하기 버튼을 주문하세요 버튼으로 치환 금지)
                이전 대화 내용을 참고해 후속 질문에도 자연스럽게 답하세요.
                """.trimIndent()
            )
        }
    )

    private var chat = model.startChat()

    // Firestore에서 불러온 과거 대화를 채팅 세션에 복원
    fun setHistory(turns: List<ChatTurn>) {
        val history = turns.flatMap { t ->
            listOf(
                content("user") { text(t.question) },
                content("model") { text(t.answer) }
            )
        }
        chat = model.startChat(history)
    }

    fun resetChat() {
        chat = model.startChat()
    }

    fun ask(
        ocrText: String,
        question: String,
        onResult: (answer: String, totalTokens: Int) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val prompt = """
            [현재 화면 OCR 텍스트]
            ${ocrText.ifBlank { "(인식된 텍스트 없음)" }}

            [사용자 질문]
            $question
        """.trimIndent()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = chat.sendMessage(prompt)
                onResult(response.text ?: "", response.usageMetadata?.totalTokenCount ?: 0)
            } catch (e: Exception) {
                onError(e)
            }
        }
    }
}

class textRecognizer : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var mBtnSchedule4: ImageButton
    private lateinit var mBtnRecord4: ImageButton
    private lateinit var mBtnHome4: ImageButton
    private lateinit var mBtnAi4: ImageButton
    private lateinit var mBtnProfile4: ImageButton
    val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

    //cameraX - MLkit 작업용
    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService

    // 새 UI
    private lateinit var btnMic: ImageButton
    private lateinit var btnSend: ImageButton
    private lateinit var micStatus: TextView
    private lateinit var tvAnswer: TextView
    private lateinit var etMessage: EditText
    private lateinit var switchAutoRead: SwitchCompat

    // 음성 출력(TTS)
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // Gemini / 음성인식
    private val geminiClient = GeminiClient()
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var lastPartialText = ""
    private var googleUser = false

    // 대화 기록
    private val chatRepo = ChatRepository()
    private val chatLog = StringBuilder()
    private var isSending = false
    private lateinit var answerScroll: ScrollView

    // 가장 최근에 인식된 OCR 텍스트 (분석 스레드에서 쓰고 메인 스레드에서 읽음)
    @Volatile
    private var latestOcrText: String = ""

    private var hitCount = 0
    private var confirmed = false

    private fun handleResult(result: KioskResult) {
        latestOcrText = result.blocks.joinToString("\n") { it.text }

        hitCount = if (result.isKiosk) hitCount + 1 else 0
        val nowConfirmed = hitCount >= 3

        if (nowConfirmed != confirmed) {
            confirmed = nowConfirmed
            runOnUiThread {
                // 예: statusTextView.text = if (confirmed) "키오스크 감지됨" else "탐색 중..."
            }
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startListening()
            else Toast.makeText(this, "마이크 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }

    fun isGoogleUser(): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false

        return user.providerData.any { profile ->
            profile.providerId == GoogleAuthProvider.PROVIDER_ID
        }
    }

    fun checkUserAndShowDialog(AreuOk: Boolean) {
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
                .also { it.setAnalyzer(cameraExecutor, TextAnalyzer { handleResult(it) }) }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    // ---------------- 음성 출력(TTS) ----------------

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = tts?.setLanguage(Locale.KOREAN)
            ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            tts?.setSpeechRate(0.85f)
        }
    }

    private fun speak(text: String) {
        if (!ttsReady) return
        // 마크다운 기호를 "별표"처럼 읽지 않도록 제거
        val clean = text.replace("**", "")
            .replace("*", "")
            .replace("#", "")
            .replace("`", "")
            .trim()
        if (clean.isEmpty()) return
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "msg_${System.currentTimeMillis()}")
    }

    // ---------------- 음성 인식(STT) ----------------

    // 마이크 버튼: 첫 터치 = 녹음 시작, 두 번째 터치 = 녹음 종료 → STT → 자동 전송
    private fun onMicClicked() {
        if (isListening) {
            micStatus.text = "변환 중..."
            setMicOff() // 두 번째 터치: 빨간색, 2초 뒤 기본색으로
            speechRecognizer?.stopListening() // 결과는 onResults 로 전달됨
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "이 기기에서는 음성 인식을 사용할 수 없습니다", Toast.LENGTH_SHORT).show()
            return
        }
        tts?.stop() // 읽어주는 중이면 중단 (마이크에 읽는 소리가 섞이지 않게)
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(recognitionListener)
            }
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREAN.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
        }
        lastPartialText = ""
        isListening = true
        setMicOn() // 첫 번째 터치: 파란색
        micStatus.text = "듣는 중... 다 말씀하셨으면 다시 눌러주세요"
        speechRecognizer?.startListening(intent)
    }

    // ---- 마이크 버튼 색 상태: 기본(흰/회색) → 파랑(듣는 중) → 빨강(종료, 2초) → 기본 ----
    private val uiHandler = Handler(Looper.getMainLooper())
    private val micIdleRunnable = Runnable { setMicIdle() }
    private var micRed = false

    private fun setMicOn() {
        uiHandler.removeCallbacks(micIdleRunnable)
        micRed = false
        btnMic.setBackgroundResource(R.drawable.bg_mic_on)
        btnMic.setColorFilter(Color.WHITE)
    }

    private fun setMicOff() {
        micRed = true
        btnMic.setBackgroundResource(R.drawable.bg_mic_off)
        btnMic.setColorFilter(Color.WHITE)
        uiHandler.removeCallbacks(micIdleRunnable)
        uiHandler.postDelayed(micIdleRunnable, 2000)
    }

    private fun setMicIdle() {
        micRed = false
        btnMic.setBackgroundResource(R.drawable.bg_circle)
    }

    private fun resetMicUi() {
        isListening = false
        if (!micRed) setMicOff() // 터치 없이 끝난 경우(오류 등)도 빨강 → 기본색
        micStatus.text = "마이크를 눌러 말씀하세요"
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
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
                lastPartialText = text
                etMessage.setText(text) // 말하는 내용을 입력창에 실시간 표시
            }
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?: lastPartialText
            resetMicUi()
            if (text.isBlank()) {
                Toast.makeText(this@textRecognizer, "음성이 인식되지 않았어요. 다시 말씀해 주세요", Toast.LENGTH_SHORT).show()
            } else {
                etMessage.setText(text)
                sendToGemini(text) // STT 완료 → 자동 전송
            }
        }

        override fun onError(error: Int) {
            val partial = lastPartialText
            resetMicUi()
            // 인식은 끝났지만 중간 결과가 있으면 그것을 사용
            if (partial.isNotBlank() &&
                (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
            ) {
                sendToGemini(partial)
            } else {
                Toast.makeText(this@textRecognizer, "음성 인식 실패($error). 다시 시도해 주세요", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------------- Gemini 전송 ----------------

    private fun renderLog(pending: String = "") {
        tvAnswer.text = if (chatLog.isEmpty() && pending.isEmpty())
            "Gemini 답변이 여기에 표시됩니다."
        else chatLog.toString() + pending
        answerScroll.post { answerScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun loadHistory() {
        chatRepo.loadRecent(20) { turns ->
            geminiClient.setHistory(turns)
            turns.forEach { chatLog.append(" ${it.question}\n ${it.answer}\n\n") }
            renderLog()
        }
    }

    private fun confirmClearHistory() {
        MaterialAlertDialogBuilder(this)
            .setTitle("대화 기록 삭제")
            .setMessage("저장된 대화 기록을 모두 삭제할까요?")
            .setPositiveButton("삭제") { _, _ ->
                chatRepo.clearAll {
                    chatLog.clear()
                    geminiClient.resetChat()
                    renderLog()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun sendToGemini(question: String) {
        if (!googleUser) {
            checkUserAndShowDialog(false)
            return
        }
        val q = question.trim()
        if (q.isEmpty() || isSending) return
        isSending = true
        tts?.stop() // 새 질문을 보내면 읽던 것을 멈춤

        renderLog(" $q\n 사용량 확인 중...")

        // 전송 전에 오늘 사용량을 서버에서 확인 (다른 기기 사용분도 반영)
        chatRepo.getTodayUsage { used ->
            if (used == null) {
                renderLog("사용량을 확인하지 못했어요. 인터넷 연결을 확인해 주세요.")
                isSending = false
                return@getTodayUsage
            }
            if (used >= DAILY_TOKEN_LIMIT) {
                val limitMsg = "오늘 사용할 수 있는 AI 사용량을 모두 사용했어요. 내일 다시 이용해 주세요."
                renderLog(" $limitMsg")
                if (switchAutoRead.isChecked) speak(limitMsg)
                isSending = false
                return@getTodayUsage
            }

            val ocr = latestOcrText // 전송 시점의 OCR 결과
            renderLog(" $q\n 답변을 만드는 중...")

            geminiClient.ask(
                ocrText = ocr,
                question = q,
                onResult = { answer, tokens ->
                    runOnUiThread {
                        val a = answer.ifBlank { "답변을 받지 못했어요." }
                        val percent = ((used + tokens) * 100 / DAILY_TOKEN_LIMIT).coerceAtMost(100)
                        chatLog.append(" $q\n $a\n 오늘 사용량 $percent%\n\n")
                        renderLog()
                        if (switchAutoRead.isChecked) speak(a) // 읽어주기 ON이면 답변 낭독
                        chatRepo.save(q, a, ocr)
                        chatRepo.addUsage(tokens)
                        isSending = false
                    }
                },
                onError = { e ->
                    Log.e("Gemini", "요청 실패", e)
                    runOnUiThread {
                        renderLog(" $q\n⚠ 오류가 발생했어요. 잠시 후 다시 시도해 주세요.")
                        isSending = false
                    }
                }
            )
        }
    }

    private fun onSendClicked() {
        val text = etMessage.text.toString()
        if (text.isBlank()) return
        sendToGemini(text)
        etMessage.text.clear()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(etMessage.windowToken, 0)
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
        uiHandler.removeCallbacks(micIdleRunnable)
        cameraExecutor.shutdown()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_tr)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            // 키보드가 올라오면 입력창이 가려지지 않도록 키보드 높이만큼 아래 여백 확보
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                maxOf(systemBars.bottom, ime.bottom)
            )
            insets
        }
        //username 가져오기.
        username = FirebaseAuth.getInstance().currentUser?.displayName.orEmpty()
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

        googleUser = isGoogleUser()
        checkUserAndShowDialog(googleUser)//구글 계정일 때만 ai 기능-> gemini 쓸 수 있게끔 ㄱㄱ

        // 새 UI 연결
        btnMic = findViewById(R.id.btnMic)
        setMicIdle()
        btnSend = findViewById(R.id.btnSend)
        micStatus = findViewById(R.id.micStatus)
        tvAnswer = findViewById(R.id.tvAnswer)
        etMessage = findViewById(R.id.etMessage)

        // 읽어주기 스위치 + TTS 초기화
        switchAutoRead = findViewById(R.id.switchAutoRead)
        switchAutoRead.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) tts?.stop() // 끄면 읽던 것도 바로 멈춤
        }
        tts = TextToSpeech(this, this)

        btnMic.setOnClickListener { onMicClicked() }
        btnSend.setOnClickListener { onSendClicked() }
        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                onSendClicked()
                true
            } else false
        }

        answerScroll = findViewById(R.id.answerScroll)
        btnSend.setOnLongClickListener { // 전송 버튼 길게 누르면 기록 삭제
            confirmClearHistory()
            true
        }
        if (googleUser) loadHistory()

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