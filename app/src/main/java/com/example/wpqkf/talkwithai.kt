package com.example.wpqkf

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.wpqkf.databinding.ActivityTaBinding
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.collections.toList

class talkwithai : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityTaBinding
    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()
    private var nextId = 0L

    private var tts: TextToSpeech? = null
    private lateinit var mFirebaseAuth: FirebaseAuth
    private lateinit var mDatabaseRef: DatabaseReference
    private var ttsReady = false
    private val chatRepo = ChatRepository()
    private var username = "사용자"

    private fun buildSystemPrompt(): String {
        val nameRule = if (username.isNotBlank()) {
            "10. 사용자의 이름은 '$username'입니다. 처음 인사할 때와 격려할 때 '$username 님'이라고 가끔 불러드리되, 매 문장마다 부르지는 않습니다."
        } else ""

        return """
        당신은 스마트폰, 컴퓨터, 키오스크, 인터넷 사용이 어려운 어르신과 디지털 소외 계층을 돕는 친절한 '디지털 도우미'입니다.
        반드시 다음 규칙을 지키세요.
        1. 항상 존댓말로, 따뜻하고 차분하게 말합니다.
        2. 어려운 용어(앱, 설정, 다운로드, 와이파이 등)는 쉬운 우리말로 풀어서 설명하고, 꼭 필요한 용어는 뜻을 함께 알려줍니다.
        3. 방법은 "첫째, 둘째, 셋째"처럼 한 단계씩 짧게 나누어 설명하고, 각 단계에서 화면의 어느 부분을 누르는지 구체적으로 알려줍니다.
        4. 한 번에 너무 많은 내용을 말하지 말고, 답변은 최대 6~8문장 이내로 합니다. 마지막에는 "여기까지 따라 하셨나요? 잘 안 되면 말씀해 주세요."처럼 확인합니다.
        5. 마크다운 기호(*, #, -, 백틱 등)와 이모지는 절대 사용하지 않고 평범한 문장으로만 답합니다.
        6. 휴대폰 기종마다 화면이 다를 수 있음을 필요할 때 알려주고, 어떤 기종인지 물어봐도 좋습니다.
        7. 보이스피싱, 스미싱, 개인정보 요구, 수상한 링크에 대해서는 반드시 조심하도록 안내합니다. 비밀번호, 주민등록번호, 카드번호는 누구에게도 알려주면 안 된다고 말해 줍니다.
        8. 모르는 것은 아는 척하지 말고, 가까운 가족이나 주민센터, 통신사 고객센터에 물어보시도록 안내합니다.
        9. 사용자가 실수하거나 못 알아들어도 절대 나무라지 않고 격려합니다.
        $nameRule
    """.trimIndent()
    }

    private val model by lazy {
        GenerativeModel(
            modelName = "gemini-3.1-flash-lite",
            apiKey = BuildConfig.GEMINI_API_KEY,
            systemInstruction = content { text(buildSystemPrompt()) },
            generationConfig = generationConfig {
                temperature = 0.5f
                maxOutputTokens = 800
            }
        )
    }
    private val chat by lazy { model.startChat() }

    private val speechLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) {
                sendMessage(text)
            }
        }

    // ---------- 구글 로그인 확인 (기존 기능) ----------
    fun isEmailUser(): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        return user.providerData.any { profile ->
            (profile.providerId == GoogleAuthProvider.PROVIDER_ID) || (profile.providerId == "oidc.kakao") || (profile.providerId == "naver")
        }
    }

    fun checkUserAndShowDialog(AreuOk: Boolean) {
        if (!AreuOk) {
            MaterialAlertDialogBuilder(this)
                .setTitle("알림")
                .setMessage("Gemini AI 기능은 구글 계정으로 로그인한 경우에만 사용할 수 있습니다.")
                .setPositiveButton("확인") { dialog, _ ->
                    dialog.dismiss()
                    startActivity(Intent(this@talkwithai, MainActivity::class.java))
                    finish()
                }
                .setCancelable(false)
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityTaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        username = FirebaseAuth.getInstance().currentUser?.displayName.orEmpty()


        checkUserAndShowDialog(isEmailUser())

        setupGreetingGradient()
        tts = TextToSpeech(this, this)

        adapter = ChatAdapter { text -> speak(text) }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        setupChatListeners()
        setupNavigation()
    }

    // ---------- 하단 메뉴 (기존 기능) ----------
    private fun setupNavigation() {
        binding.home.setOnClickListener {
            startActivity(Intent(this@talkwithai, MainActivity::class.java))
            finish()
        }
        binding.schedule.setOnClickListener {
            startActivity(Intent(this@talkwithai, schedule::class.java))
            finish()
        }
        binding.record.setOnClickListener {
            startActivity(Intent(this@talkwithai, record::class.java))
            finish()
        }
        binding.profile.setOnClickListener {
            startActivity(Intent(this@talkwithai, profile::class.java))
            finish()
        }
    }

    private fun setupGreetingGradient() {
        val tv = binding.tvGreeting
        val width = tv.paint.measureText(tv.text.toString())
        tv.paint.shader = LinearGradient(
            0f, 0f, width, 0f,
            intArrayOf(0xFF4285F4.toInt(), 0xFF9B72CB.toInt(), 0xFFD96570.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
        tv.invalidate()
    }

    private fun setupChatListeners() {
        binding.btnSend.setOnClickListener {
            sendMessage(binding.etInput.text.toString())
        }

        binding.etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage(binding.etInput.text.toString())
                true
            } else false
        }

        binding.btnMic.setOnClickListener { startVoiceInput() }

        binding.btnQ1.setOnClickListener { sendMessage("스마트폰 글자를 크게 하는 방법을 알려주세요") }
        binding.btnQ2.setOnClickListener { sendMessage("스마트폰으로 사진 찍는 방법을 알려주세요") }
        binding.btnQ3.setOnClickListener { sendMessage("카카오톡으로 가족에게 메시지 보내는 방법을 알려주세요") }
        binding.btnQ4.setOnClickListener { sendMessage("와이파이에 연결하는 방법을 알려주세요") }
        binding.btnQ5.setOnClickListener { sendMessage("보이스피싱이나 문자 사기를 조심하려면 어떻게 해야 하나요?") }

        binding.switchAutoRead.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) tts?.stop()
        }
    }

    private fun sendMessage(raw: String) {
        // 구글/카카오/네이버 계정이 아니면 AI 사용 불가 (기존 정책 유지)
        if (!isEmailUser()) {
            checkUserAndShowDialog(false)
            return
        }

        val text = raw.trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "궁금한 내용을 적어주세요", Toast.LENGTH_SHORT).show()
            return
        }

        tts?.stop()
        binding.etInput.setText("")
        binding.welcomeLayout.visibility = View.GONE
        addMessage(text, isUser = true)
        setLoading(true)

        // 전송 전에 오늘 사용량 확인
        chatRepo.getTodayUsage { used ->
            when {
                used == null -> {
                    addMessage("사용량을 확인하지 못했어요. 인터넷 연결을 확인해 주세요.", isUser = false)
                    setLoading(false)
                }
                used >= DAILY_TOKEN_LIMIT -> {
                    addMessage(
                        "오늘 사용할 수 있는 AI 사용량을 모두 사용하셨어요. 내일 다시 이용해 주세요.",
                        isUser = false,
                        readAloud = true
                    )
                    setLoading(false)
                }
                else -> askGemini(text)
            }
        }
    }

    private fun askGemini(text: String) {
        lifecycleScope.launch {
            try {
                val response = chat.sendMessage(text)
                chatRepo.addUsage(response.usageMetadata?.totalTokenCount ?: 0) // 사용량 누적
                val answer = cleanText(response.text.orEmpty())
                    .ifBlank { "죄송해요, 잘 이해하지 못했어요. 다시 한 번 말씀해 주시겠어요?" }
                addMessage(answer, isUser = false, readAloud = true)
            } catch (e: Exception) {
                Log.e("GeminiError", "호출 실패", e)
                addMessage(
                    "오류: ${e.javaClass.simpleName}\n${e.message}",
                    isUser = false,
                    readAloud = false
                )
            } finally {
                setLoading(false)
            }
        }
    }


    private fun addMessage(text: String, isUser: Boolean, readAloud: Boolean = false) {
        messages.add(ChatMessage(nextId++, text, isUser))
        adapter.submitList(messages.toList()) {
            binding.recyclerView.scrollToPosition(messages.size - 1)
        }
        if (readAloud && binding.switchAutoRead.isChecked) speak(text)
    }

    private fun setLoading(loading: Boolean) {
        binding.tvStatus.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSend.isEnabled = !loading
        binding.btnMic.isEnabled = !loading
    }

    private fun cleanText(s: String): String =
        s.replace("**", "")
            .replace("*", "")
            .replace("#", "")
            .replace("`", "")
            .trim()

    // ---------- 음성 입력 ----------
    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "말씀해 주세요")
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "이 휴대폰에서는 음성 입력을 쓸 수 없어요", Toast.LENGTH_LONG).show()
        }
    }

    // ---------- 음성 출력(TTS) ----------
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = tts?.setLanguage(Locale.KOREAN)
            ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            tts?.setSpeechRate(0.85f)
        }
    }

    private fun speak(text: String) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "msg_${System.currentTimeMillis()}")
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}