package com.example.wpqkf

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.*

class record : AppCompatActivity() {
    private lateinit var mBtnSchedule2: ImageButton
    private lateinit var mBtnRecord2: ImageButton
    private lateinit var mBtnHome2: ImageButton
    private lateinit var mBtnAi2: ImageButton
    private lateinit var mBtnProfile2: ImageButton
    private lateinit var recyclerView: RecyclerView

    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_record)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        recyclerView = findViewById(R.id.recyclerViewRecord)
        recyclerView.layoutManager = LinearLayoutManager(this)

        setupButtons()

        // 권한 확인 및 데이터 처리
        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "사용 기록 접근 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } else {
            refreshData() // 데이터 저장 및 불러오기 통합 함수
        }
    }
    private fun refreshData() {
        saveUsageStatsToFirebase {
            // 저장이 완료된 후 데이터를 불러와 화면 갱신
            loadRecordsFromFirebase()
        }
    }

    private fun loadRecordsFromFirebase() {
        val userId = auth.currentUser?.uid ?: return
        val userRef = database.getReference("users").child(userId).child("usage_records")

        userRef.get().addOnSuccessListener { snapshot ->
            val recordList = mutableListOf<Map<String, Any>>()
            for (child in snapshot.children) {
                val data = child.value as? Map<String, Any>
                if (data != null) {
                    recordList.add(data)
                }
            }

            //마지막 사용 시간순으로 정렬하여 상위 10개만 화면에 표시
            val sortedList = recordList.sortedByDescending {
                it["lastTimeUsed"]?.toString()?.toLongOrNull() ?: 0L
            }.take(10)

            recyclerView.adapter = RecordAdapter(sortedList) { packageName ->
                launchApp(packageName)
            }
        }.addOnFailureListener {
            Toast.makeText(this, "데이터 로딩 실패", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    //저장 완료 후 다음 동작을 수행하기 위해 콜백(onComplete) 추가
    private fun saveUsageStatsToFirebase(onComplete: () -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        val userRef = database.getReference("users").child(userId).child("usage_records")
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, -1)

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            calendar.timeInMillis,
            System.currentTimeMillis()
        )

        if (stats.isNullOrEmpty()) {
            onComplete()
            return
        }

        //실행 가능한 앱들만 필터링
        val targetStats = stats.filter {
            packageManager.getLaunchIntentForPackage(it.packageName) != null && it.packageName != packageName
        }

        if (targetStats.isEmpty()) {
            onComplete()
            return
        }

        var saveCount = 0
        for (usageStats in targetStats) {
            val pName = usageStats.packageName
            val totalTime = usageStats.totalTimeInForeground

            //최소 5초 이상 사용한 앱만 저장
            if (totalTime < 5000) {
                saveCount++
                if (saveCount == targetStats.size) onComplete()
                continue
            }

            //불필요한 런처 등 제외
            if (pName.contains("launcher") || pName.contains("trebuchet")) {
                saveCount++
                if (saveCount == targetStats.size) onComplete()
                continue
            }

            val appName = getAppNameFromPackage(pName)
            val recordData = mapOf(
                "appName" to appName,
                "packageName" to pName,
                "lastTimeUsed" to usageStats.lastTimeUsed,
                "totalTime" to totalTime
            )

            userRef.child(pName.replace(".", "_")).setValue(recordData).addOnCompleteListener {
                saveCount++
                if (saveCount == targetStats.size) {
                    onComplete() //모든 데이터 저장이 끝나면 콜백 실행
                }
            }
        }
        for (usageStats in stats) {
            // 서버에 기록을 남기는 지점
            val appName = getAppNameFromPackage(packageName)
            val recordData = mapOf(
                "appName" to appName,
                "packageName" to packageName,
                "totalTime" to usageStats.totalTimeInForeground
            )

            userRef.child(packageName.replace(".", "_")).setValue(recordData)
        }
    }


    private fun getAppNameFromPackage(packageName: String): String {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "앱을 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupButtons() {
        mBtnSchedule2 = findViewById(R.id.schedule)
        mBtnRecord2 = findViewById(R.id.record)
        mBtnHome2 = findViewById(R.id.home)
        mBtnAi2 = findViewById(R.id.ai)
        mBtnProfile2 = findViewById(R.id.profile)

        mBtnHome2.setOnClickListener { startActivity(Intent(this, MainActivity::class.java)); finish() }
        mBtnSchedule2.setOnClickListener { startActivity(Intent(this, schedule::class.java)); finish() }
        mBtnAi2.setOnClickListener { startActivity(Intent(this, ai::class.java)); finish() }
        mBtnProfile2.setOnClickListener { startActivity(Intent(this, profile::class.java)); finish() }

        //기록 세로고침
        mBtnRecord2.setOnClickListener {
            refreshData()
            Toast.makeText(this, "기록을 업데이트했습니다.", Toast.LENGTH_SHORT).show()
        }
    }
}

class RecordAdapter(
    private val records: List<Map<String, Any>>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<RecordAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val layout: View = view.findViewById(R.id.layoutItem)
        val tvName: TextView = view.findViewById(R.id.tvAppName)
        val tvTime: TextView = view.findViewById(R.id.tvUsageTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val data = records[position]
        holder.tvName.text = data["appName"]?.toString() ?: "알 수 없는 앱"

        //총 사용 시간 계산
        val totalTimeStr = data["totalTime"]?.toString() ?: "0"
        val totalTimeMillis = totalTimeStr.toLongOrNull() ?: 0L
        val minutes = totalTimeMillis / (1000 * 60)

        //마지막 사용 시각 계산
        val lastUsed = data["lastTimeUsed"]?.toString()?.toLongOrNull() ?: 0L
        val timeAgo = android.text.format.DateUtils.getRelativeTimeSpanString(
            lastUsed,
            System.currentTimeMillis(),
            android.text.format.DateUtils.MINUTE_IN_MILLIS
        )
        //화면 표시
        holder.tvTime.text = "${minutes}분 사용함 • $timeAgo"

        holder.layout.setOnClickListener {
            onClick(data["packageName"]?.toString() ?: "")
        }
    }

    override fun getItemCount() = records.size
}