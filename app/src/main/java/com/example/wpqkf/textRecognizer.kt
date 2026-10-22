package com.example.wpqkf

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.ImageButton
import android.widget.SeekBar
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
import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
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
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

const val DAILY_TOKEN_LIMIT = 100_000L

var ttsSpeed = 1.0f

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

// ---------------- 규칙 기반 추천 질문 (토큰 0) ----------------
// 버튼에 보일 짧은 주제 + 눌렀을 때 실제로 보낼 질문
data class Suggestion(
    val label: String,     // 버튼 표시용 (5~8자)
    val question: String   // Gemini에 보낼 전체 질문
)

object SuggestionEngine {
    private data class Rule(
        val keyword: String,
        val label: String,
        val question: String,
        val priority: Int
    )

    private val rules = listOf(
        Rule("결제", "결제 방법", "결제는 어떻게 해요?", 5),
        Rule("카드", "카드 넣는 곳", "카드는 어디에 넣어야 해요?", 4),
        Rule("번호표", "번호표 받기", "번호표는 어떻게 받아요?", 4),
        Rule("포장", "포장/매장 선택", "포장이랑 매장 식사 중에 뭘 눌러야 해요?", 3),
        Rule("매장", "포장/매장 선택", "포장이랑 매장 식사 중에 뭘 눌러야 해요?", 3),
        Rule("주문", "주문 시작", "주문은 어떻게 시작해요?", 2),
        Rule("터치", "누를 곳", "어디를 눌러야 해요?", 1)
    )

    private val defaults = listOf(
        Suggestion("지금 할 일", "지금 화면에서 뭘 해야 해요?"),
        Suggestion("화면 설명", "이 화면은 무슨 화면이에요?")
    )

    fun suggest(ocrText: String): List<Suggestion> {
        val compact = ocrText.replace("\\s".toRegex(), "")
        val matched = rules
            .filter { compact.contains(it.keyword) }
            .sortedByDescending { it.priority }
            .map { Suggestion(it.label, it.question) }
        return (matched + defaults).distinct().take(2)
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
private var userage = -1
//Gemini 대화 작업 (채팅 세션으로 대화 맥락 유지)
// 이름/나이가 확정된 뒤 init()을 호출해야 모델이 만들어진다.
class GeminiClient {

    private var model: GenerativeModel? = null
    private var chat: Chat? = null
    private var suggestModel: GenerativeModel? = null   // AI 추천 전용 (대화 기록 없음)

    val isReady: Boolean get() = chat != null

    private suspend fun <T> withRetry(
        times: Int = 2,
        initialDelayMs: Long = 500L,
        block: suspend () -> T
    ): T {
        var delayMs = initialDelayMs
        repeat(times - 1) {
            try {
                return block()
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                val retryable = msg.contains("503") ||
                        msg.contains("UNAVAILABLE") ||
                        msg.contains("high demand") ||
                        msg.contains("overloaded")
                if (!retryable) throw e
                kotlinx.coroutines.delay(delayMs)
                delayMs *= 2
            }
        }
        return block()   // 마지막 시도는 예외를 그대로 던짐
    }

    fun init(name: String, age: Int, history: List<ChatTurn> = emptyList()) {
        val nameText = if (name.isNotBlank()) "사용자의 이름은 ${name}입니다." else ""
        val ageText = if (age > 0) "사용자의 나이는 ${age}세입니다. 나이에 맞게 존대 등을 조절하세요." else ""

        val m = GenerativeModel(
            modelName = "gemini-3.1-flash-lite",
            apiKey = BuildConfig.GEMINI_API_KEY,
            systemInstruction = content {
                text(
                    """
                    당신은 디지털 기기(ex:키오스크) 사용이 어려운 사용자를 돕는 친절한 도우미입니다.
                    $nameText
                    $ageText
                    사용자가 질문할 때마다 카메라로 촬영한 디지털 기기의 화면의 OCR 텍스트가 함께 전달됩니다.
                    OCR 오타가 있을 수 있으니 문맥으로 보정하세요.
                    현재 화면을 기준으로, 디지털 소외 계층 분들도 이해할 수 있게 쉬운 말로 단계별로 짧게 답하세요.
                    OCR텍스트는 높임말로 변환하지말고 있는 그대로 전달해주세요.(예를 들어 주문하기 버튼을 주문하세요 버튼으로 치환 금지)
                    이전 대화 내용을 참고해 후속 질문에도 자연스럽게 답하세요.
                    """.trimIndent()
                )
            }
        )
        model = m
        chat = m.startChat(history.toHistoryContents())

        suggestModel = GenerativeModel(
            modelName = "gemini-3.5-flash-lite",
            apiKey = BuildConfig.GEMINI_API_KEY,
            generationConfig = generationConfig {
                maxOutputTokens = 1024          // 여유 있게 (프롬프트가 짧게 답하도록 제한하고 있어서 실제 사용량은 적음)
                temperature = 0.4f
            },
            systemInstruction = content {
                text(
                    """
당신은 디지털 기기(키오스크 등) 사용이 어려운 사용자를 돕는 도우미입니다.
화면 OCR 텍스트를 보고 사용자가 지금 물어볼 만한 질문 2개를 추천하세요.
- 정확히 2줄, 각 줄은 "주제|질문" 형식입니다.
- 주제는 5~8자, 질문은 25자 이내의 쉬운 말로 쓰세요.
- 화면에 실제로 보이는 단어(버튼 이름 등)를 활용하세요.
- 다른 설명, 번호, 마크다운은 쓰지 마세요.
예:
결제 방법|결제는 어떻게 해요?
포장 선택|포장하려면 뭘 눌러야 해요?
""".trimIndent()
                )
            }
        )
    }

    private fun List<ChatTurn>.toHistoryContents() = flatMap { t ->
        listOf(
            content("user") { text(t.question) },
            content("model") { text(t.answer) }
        )
    }

    fun resetChat() {
        chat = model?.startChat()
    }

    fun ask(
        ocrText: String,
        question: String,
        onResult: (answer: String, totalTokens: Int) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val c = chat
        if (c == null) {
            onError(IllegalStateException("Gemini 모델이 아직 초기화되지 않았습니다."))
            return
        }

        val prompt = """
            [현재 화면 OCR 텍스트]
            ${ocrText.ifBlank { "(인식된 텍스트 없음)" }}

            [사용자 질문]
            $question
        """.trimIndent()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = c.sendMessage(prompt)
                onResult(response.text ?: "", response.usageMetadata?.totalTokenCount ?: 0)
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    // 대화 기록 없이 1회성 호출 (OCR은 1000자로 제한)
    fun suggest(
        ocrText: String,
        onResult: (suggestions: List<Suggestion>, totalTokens: Int) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val m = suggestModel
        if (m == null) {
            onError(IllegalStateException("Gemini 모델이 아직 초기화되지 않았습니다."))
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = withRetry { m.generateContent("[화면 OCR 텍스트]\n${ocrText.take(600)}") }

                val list = response.text.orEmpty().lines()
                    .map { it.trim().trimStart('-', '*', ' ') }
                    .filter { it.contains("|") }
                    .map {
                        val parts = it.split("|", limit = 2)
                        Suggestion(parts[0].trim().take(8), parts[1].trim())
                    }
                    .filter { it.label.isNotBlank() && it.question.isNotBlank() }
                    .take(2)

                onResult(list, response.usageMetadata?.totalTokenCount ?: 0)
            } catch (e: Exception) {
                onError(e)
            }
        }
    }
}

class textRecognizer : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var mFirebaseAuth: FirebaseAuth
    private lateinit var mDatabaseRef: DatabaseReference
    private lateinit var mBtnSchedule4: ImageButton
    private lateinit var mBtnRecord4: ImageButton
    private lateinit var mBtnHome4: ImageButton
    private lateinit var mBtnAi4: ImageButton
    private lateinit var mBtnProfile4: ImageButton
    // Activity 멤버 변수
    private var cachedUsage: Long? = null

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

    // 추천 질문 버튼
    private lateinit var btnSuggest1: Button
    private lateinit var btnSuggest2: Button
    private lateinit var btnAiSuggest: Button

    private var shownSuggestions: List<Suggestion> = emptyList()
    private var candidateSuggestions: List<Suggestion>? = null
    private var candidateCount = 0
    private var isSuggesting = false
    private var aiHoldUntil = 0L   // AI 추천이 이 시각까지는 규칙 기반 추천이 덮어쓰지 않음

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
        val ocrSnapshot = latestOcrText
        runOnUiThread { updateSuggestions(ocrSnapshot) }   // 규칙 기반 추천 갱신

        hitCount = if (result.isKiosk) hitCount + 1 else 0
        val nowConfirmed = hitCount >= 3

        if (nowConfirmed != confirmed) {
            confirmed = nowConfirmed
            runOnUiThread {
                // 예: statusTextView.text = if (confirmed) "키오스크 감지됨" else "탐색 중..."
            }
        }
    }

    // ---------------- 추천 질문 ----------------

    private fun updateSuggestions(ocrText: String) {
        if (isFinishing || isDestroyed) return
        if (SystemClock.elapsedRealtime() < aiHoldUntil) return   // AI 추천 표시 중에는 유지
        if (ocrText.isBlank()) return   // 텍스트가 안 잡히면 기존 추천 유지

        val next = SuggestionEngine.suggest(ocrText)

        if (next == shownSuggestions) {   // 이미 표시 중이면 후보 초기화
            candidateSuggestions = null
            candidateCount = 0
            return
        }

        // 같은 후보가 연속 3프레임(약 1초) 나올 때만 교체 → 버튼 깜빡임 방지
        if (next == candidateSuggestions) candidateCount++
        else {
            candidateSuggestions = next
            candidateCount = 1
        }

        if (candidateCount >= 3) {
            shownSuggestions = next
            applySuggestions(next)
        }
    }

    private fun applySuggestions(list: List<Suggestion>) {
        val s1 = list.getOrNull(0)
        val s2 = list.getOrNull(1)

        btnSuggest1.text = s1?.label.orEmpty()
        btnSuggest1.tag = s1?.question
        btnSuggest2.text = s2?.label.orEmpty()
        btnSuggest2.tag = s2?.question

        btnSuggest1.visibility = if (s1 != null) View.VISIBLE else View.GONE
        btnSuggest2.visibility = if (s2 != null) View.VISIBLE else View.GONE
    }

    private fun resetAiButton() {
        isSuggesting = false
        btnAiSuggest.isEnabled = true
        btnAiSuggest.text = "질문 추천"
    }

    // 버튼을 눌렀을 때만 Gemini를 1회 호출해서 추천 질문을 만든다
    private fun requestAiSuggestions() {
        if (!googleUser) {
            checkUserAndShowDialog(false)
            return
        }
        if (!geminiClient.isReady) {
            Toast.makeText(this, "질문을 준비하는 중이에요. 잠시 후 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        if (isSuggesting || isSending) return

        val ocr = latestOcrText
        if (ocr.isBlank()) {
            Toast.makeText(this, "화면의 글자가 아직 인식되지 않았어요. 화면을 비춰 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        isSuggesting = true
        btnAiSuggest.isEnabled = false
        btnAiSuggest.text = "생성 중..."

        chatRepo.getTodayUsage { used ->
            if (used == null || used >= DAILY_TOKEN_LIMIT) {
                val msg = if (used == null) "사용량을 확인하지 못했어요." else "오늘 AI 사용량을 모두 사용했어요."
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                resetAiButton()
                return@getTodayUsage
            }

            geminiClient.suggest(
                ocrText = ocr,
                onResult = { list, tokens ->
                    runOnUiThread {
                        if (list.isEmpty()) {
                            Toast.makeText(this, "추천을 만들지 못했어요. 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                        } else {
                            shownSuggestions = list
                            candidateSuggestions = null
                            candidateCount = 0
                            applySuggestions(list)
                            aiHoldUntil = SystemClock.elapsedRealtime() + 30_000   // 30초간 유지
                            chatRepo.addUsage(tokens)
                        }
                        resetAiButton()
                    }
                },
                onError = { e ->
                    Log.e("Gemini", "추천 요청 실패", e)
                    runOnUiThread {
                        Toast.makeText(this, "추천 중 오류가 발생했어요.", Toast.LENGTH_SHORT).show()
                        resetAiButton()
                    }
                }
            )
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

    fun isEmailUser(): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false

        return user.providerData.any { profile ->
            (profile.providerId == GoogleAuthProvider.PROVIDER_ID) || (profile.providerId == "oidc.kakao") || (profile.providerId == "naver")
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
            tts?.setSpeechRate(ttsSpeed)
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
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
        if (!micRed) setMicOff() // 터치 없이 끝난 경우(오류 등)도 빨강 → 기본색
        micStatus.text = "마이크를 눌러 말씀하세요"
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { Log.d("STT", "듣기 준비 완료") }
        override fun onBeginningOfSpeech() { Log.d("STT", "말 시작 감지") }
        override fun onRmsChanged(rmsdB: Float) { Log.d("STT", "rms=$rmsdB") }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { Log.d("STT", "말 끝 감지") }
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
            speechRecognizer?.destroy()
            speechRecognizer = null
            resetMicUi()
            if (text.isBlank()) {
                Toast.makeText(this@textRecognizer, "음성이 인식되지 않았어요. 다시 말씀해 주세요", Toast.LENGTH_SHORT).show()
            } else {
                sendToGemini(text) // STT 완료 → 자동 전송
                etMessage.text.clear()
            }
        }

        override fun onError(error: Int) {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null   // 다음 startListening()에서 새로 생성됨

            val partial = lastPartialText
            resetMicUi()
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

    // 이름/나이가 확정된 뒤에 호출: 과거 대화를 불러와 모델을 만들고 화면에 표시
    private fun initGemini() {
        if (!googleUser) return
        chatRepo.getTodayUsage { cachedUsage = it }
        chatRepo.loadRecent(20) { turns ->
            if (isFinishing || isDestroyed) return@loadRecent
            geminiClient.init(username, userage, turns)
            chatLog.clear()
            turns.forEach { chatLog.append("질문: ${it.question}\n답변: ${it.answer}\n\n") }
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
        if (!geminiClient.isReady) {
            Toast.makeText(this, "AI를 준비하는 중이에요. 잠시 후 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        val q = question.trim()
        if (q.isEmpty() || isSending) return

        // 캐시된 사용량으로 한도 확인 (서버 응답을 기다리지 않음)
        val used = cachedUsage
        if (used == null) {
            renderLog("사용량을 확인하는 중이에요. 잠시 후 다시 시도해 주세요.")
            return
        }
        if (used >= DAILY_TOKEN_LIMIT) {
            val limitMsg = "오늘 사용할 수 있는 AI 사용량을 모두 사용했어요. 내일 다시 이용해 주세요."
            renderLog(" $limitMsg")
            if (switchAutoRead.isChecked) speak(limitMsg)
            return
        }

        isSending = true
        tts?.stop() // 새 질문을 보내면 읽던 것을 멈춤

        val ocr = latestOcrText // 전송 시점의 OCR 결과
        renderLog(" $q\n 답변을 만드는 중...")

        geminiClient.ask(
            ocrText = ocr,
            question = q,
            onResult = { answer, tokens ->
                runOnUiThread {
                    val a = answer.ifBlank { "답변을 받지 못했어요." }
                    val newUsed = (cachedUsage ?: 0L) + tokens
                    cachedUsage = newUsed
                    val percent = (newUsed * 100 / DAILY_TOKEN_LIMIT).coerceAtMost(100)
                    chatLog.append("질문:$q\n답변:$a\n 오늘 사용량 $percent%\n\n")
                    renderLog()
                    if (switchAutoRead.isChecked) speak(a) // 읽어주기 ON이면 답변 낭독
                    chatRepo.save(q, a, ocr)
                    chatRepo.addUsage(tokens)
                    isSending = false
                }
            },
            onError = { e ->
                Log.e("Gemini", "요청 실패", e)
                val msg = if (e.message?.contains("503") == true || e.message?.contains("high demand") == true)
                    "지금 AI가 많이 바빠요. 잠시 후 다시 시도해 주세요."
                else
                    "오류가 발생했어요. 잠시 후 다시 시도해 주세요."
                runOnUiThread {
                    renderLog(" $q\n$msg")
                    isSending = false
                }
            }
        )
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

        // 새 UI 연결 (콜백에서 쓰이는 뷰들은 먼저 연결)
        btnMic = findViewById(R.id.btnMic)
        setMicIdle()
        btnSend = findViewById(R.id.btnSend)
        micStatus = findViewById(R.id.micStatus)
        tvAnswer = findViewById(R.id.tvAnswer)
        etMessage = findViewById(R.id.etMessage)
        answerScroll = findViewById(R.id.answerScroll)

        // 추천 질문 버튼 연결 (카메라 콜백에서 쓰이므로 카메라 시작 전에 연결)
        btnSuggest1 = findViewById(R.id.btnSuggest1)
        btnSuggest2 = findViewById(R.id.btnSuggest2)
        btnAiSuggest = findViewById(R.id.btnAiSuggest)

        shownSuggestions = SuggestionEngine.suggest("")   // 시작할 땐 기본 추천
        applySuggestions(shownSuggestions)

        btnSuggest1.setOnClickListener {
            (btnSuggest1.tag as? String)?.let { sendToGemini(it) }
        }
        btnSuggest2.setOnClickListener {
            (btnSuggest2.tag as? String)?.let { sendToGemini(it) }
        }
        btnAiSuggest.setOnClickListener { requestAiSuggestions() }

        //seekbar 읽기 속도 조절
        val speedSeekBar = findViewById<SeekBar>(R.id.speedSeekBar)
        val speedEditText = findViewById<EditText>(R.id.speedEditText)

        var isUpdating = false        // 서로 값 갱신 시 무한루프 방지

        // 속도 <-> progress 변환
        fun speedToProgress(speed: Float) = Math.round(speed * 10) - 5
        fun progressToSpeed(progress: Int) = (progress + 5) / 10f

        // 속도 적용 (SeekBar, EditText, TTS 모두 동기화)
        fun applySpeed(speed: Float, updateEditText: Boolean = true, updateSeekBar: Boolean = true) {
            val clamped = speed.coerceIn(0.5f, 2.0f)
            ttsSpeed = clamped
            isUpdating = true
            if (updateSeekBar) speedSeekBar.progress = speedToProgress(clamped)
            if (updateEditText) {
                speedEditText.setText(String.format(Locale.US, "%.1f", clamped))
                speedEditText.setSelection(speedEditText.text.length)
            }
            isUpdating = false
            tts?.setSpeechRate(clamped)
        }

        // 초기값
        applySpeed(1.0f)

        // SeekBar -> EditText
        speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (isUpdating || !fromUser) return
                applySpeed(progressToSpeed(progress), updateSeekBar = false)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // EditText -> SeekBar (입력 중 실시간 반영, 범위 초과 시 막기)
        speedEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdating) return
                val value = s?.toString()?.toFloatOrNull() ?: return
                if (value > 2.0f) {
                    // 2 초과 입력 시 2.0으로 막기
                    applySpeed(2.0f)
                } else if (value >= 0.5f) {
                    // 정상 범위면 입력 중인 텍스트는 건드리지 않고 SeekBar/TTS만 갱신
                    applySpeed(value, updateEditText = false)
                }
                // 0.5 미만은 타이핑 중간값(예: "0")일 수 있어 완료 시점에 보정
            }
        })

        // 입력 완료(키보드 Done / 포커스 해제) 시 0.5 미만 보정
        speedEditText.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                applySpeed(v.text.toString().toFloatOrNull() ?: 1.0f)
                v.clearFocus()
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(v.windowToken, 0)
                true
            } else false
        }
        speedEditText.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) applySpeed((v as EditText).text.toString().toFloatOrNull() ?: 1.0f)
        }

        // ★ googleUser / username 은 Firebase 요청보다 "먼저" 세팅해야 한다
        googleUser = isEmailUser()
        username = FirebaseAuth.getInstance().currentUser?.displayName.orEmpty()

        // ★ userage 가져오기 → 가져온 뒤에 Gemini 초기화
        mFirebaseAuth = FirebaseAuth.getInstance()
        mDatabaseRef = FirebaseDatabase.getInstance().getReference("UserAccount")
        val firebaseUser = mFirebaseAuth.currentUser
        if (firebaseUser != null) {
            // 사용자의 고유 번호로 들어가서 정보를 한 번만 읽기
            mDatabaseRef.child(firebaseUser.uid).addListenerForSingleValueEvent(object :
                ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val account = snapshot.getValue(UserAccount::class.java)
                    userage = account?.age?.toString()?.toIntOrNull() ?: -1
                    initGemini() // 나이 확정 후 모델 생성 + 기록 불러오기
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@textRecognizer, "사용자 정보를 가져오지 못했습니다.", Toast.LENGTH_SHORT).show()
                    initGemini() // 실패해도 일단 초기화 (나이 -1 → 프롬프트에서 생략됨)
                }
            })
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

        checkUserAndShowDialog(googleUser) // 구글 계정일 때만 ai 기능 -> gemini 쓸 수 있게끔

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

        btnSend.setOnLongClickListener { // 전송 버튼 길게 누르면 기록 삭제
            confirmClearHistory()
            true
        }
        // (기존 if (googleUser) loadHistory() 는 삭제 → initGemini()가 대신 처리)

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
        findViewById<ImageButton>(R.id.btnClear).setOnClickListener {
            confirmClearHistory() // 삭제 확인 다이얼로그 → 확인 시 Firestore 기록 + 화면 + Gemini 세션 초기화
        }
    }
}