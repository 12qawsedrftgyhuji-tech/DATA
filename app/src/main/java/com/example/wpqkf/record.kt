package com.example.wpqkf

import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 어댑터와 다이얼로그가 같이 쓰는 시간 포맷 함수 (중복 제거)
private fun formatUsageTime(totalTime: Long): String {
    val seconds = totalTime / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (minutes > 0) "${minutes}분 ${remainingSeconds}초" else "${remainingSeconds}초"
}

class record : AppCompatActivity() {
    private lateinit var mBtnSchedule2: ImageButton
    private lateinit var mBtnRecord2: ImageButton
    private lateinit var mBtnHome2: ImageButton
    private lateinit var mBtnAi2: ImageButton
    private lateinit var mBtnProfile2: ImageButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: RecordAdapter

    // Firebase 대신 온디바이스 Room 데이터베이스 사용
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_record)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        db = AppDatabase.getInstance(this)

        recyclerView = findViewById(R.id.recyclerViewRecord)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // 클릭하면 바로 앱을 실행하는 대신, 상세 다이얼로그(사용시간/바로가기/메모)를 띄움
        adapter = RecordAdapter(emptyList(), packageManager) { record ->
            showAppDetailDialog(record)
        }
        recyclerView.adapter = adapter

        // Room의 LiveData를 관찰 → DB 내용이 바뀔 때마다 자동으로 화면 갱신
        db.usageRecordDao().getTopRecords().observe(this) { records ->
            adapter.updateData(records)
        }

        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        // 화면에 다시 들어올 때마다 자동으로 최신 사용 기록을 분석해서 저장
        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "사용 기록 접근 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } else {
            refreshData()
        }
    }

    private fun refreshData() {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (24 * 60 * 60 * 1000) // 24시간 전

        lifecycleScope.launch {
            saveUsageStatsToDb(startTime, endTime)
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * 해당 패키지가 런처에서 실행 가능한 "진짜 앱"인지 확인
     * (시스템 서비스, 백그라운드 프로세스 등을 걸러냄)
     */
    private fun isLaunchableApp(packageName: String): Boolean {
        return packageManager.getLaunchIntentForPackage(packageName) != null
    }

    /**
     * queryEvents를 사용하여 정밀하게 사용 시간을 계산하고 온디바이스 Room DB에 저장하는 함수
     */
    private suspend fun saveUsageStatsToDb(startTime: Long, endTime: Long) {
        val records = withContext(Dispatchers.IO) {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()

            val activeApps = mutableMapOf<String, Long>()
            val usageMap = mutableMapOf<String, Long>()

            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                val pName = event.packageName

                if (pName == packageName || pName.contains("launcher") || pName.contains("trebuchet")) continue

                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        activeApps[pName] = event.timeStamp
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED -> {
                        val start = activeApps.remove(pName)
                        if (start != null) {
                            val duration = event.timeStamp - start
                            if (duration > 0) {
                                usageMap[pName] = (usageMap[pName] ?: 0L) + duration
                            }
                        }
                    }
                }
            }

            val filteredEntries = usageMap.entries.filter { entry ->
                entry.value > 5000 && isLaunchableApp(entry.key)
            }

            val now = System.currentTimeMillis()
            filteredEntries.map { entry ->
                UsageRecord(
                    packageName = entry.key,
                    appName = getAppNameFromPackage(entry.key),
                    totalTime = entry.value,
                    lastTimeUsed = now
                )
            }
        }

        db.usageRecordDao().clearAll()
        if (records.isNotEmpty()) {
            db.usageRecordDao().insertAll(records)
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

    /**
     * 리스트 아이템 클릭 시 뜨는 상세 다이얼로그
     * 1칸: 사용 시간 / 2칸: 바로가기 / 3칸: 메모, 맨 위엔 앱 아이콘+이름
     */
    private fun showAppDetailDialog(record: UsageRecord) {
        val pName = record.packageName

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_app_detail, null)


        val ivIcon = view.findViewById<ImageView>(R.id.ivDialogAppIcon)
        val tvName = view.findViewById<TextView>(R.id.tvDialogAppName)
        val tvUsage = view.findViewById<TextView>(R.id.tvDialogUsageTime)
        val btnShortcut = view.findViewById<Button>(R.id.btnDialogShortcut)
        val etMemo = view.findViewById<EditText>(R.id.etDialogMemo)
        val btnSaveMemo = view.findViewById<Button>(R.id.btnDialogSaveMemo)

        // 맨 위: 아이콘 + 이름
        val icon: Drawable? = try {
            packageManager.getApplicationIcon(pName)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        ivIcon.setImageDrawable(icon ?: getDrawable(android.R.drawable.sym_def_app_icon))
        tvName.text = record.appName

        // 1칸: 사용 시간
        tvUsage.text = "오늘 사용 시간: ${formatUsageTime(record.totalTime)}"

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setNegativeButton("닫기", null)
            .create()

        // 2칸: 바로가기
        btnShortcut.setOnClickListener {
            launchApp(pName)
            dialog.dismiss()
        }

        // 3칸: 메모 - 기존 메모 불러오기
        lifecycleScope.launch {
            val existing = db.appMemoDao().getMemo(pName)
            etMemo.setText(existing?.memo ?: "")
        }

        btnSaveMemo.setOnClickListener {
            val text = etMemo.text.toString()
            lifecycleScope.launch {
                db.appMemoDao().saveMemo(AppMemo(packageName = pName, memo = text))
                Toast.makeText(this@record, "메모가 저장되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
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

        mBtnRecord2.setOnClickListener {
            refreshData()
        }
    }
}

// 클릭 콜백을 packageName(String) 대신 UsageRecord 전체로 넘김
// -> 다이얼로그에서 앱 이름/사용시간을 다시 조회할 필요 없이 그대로 사용
class RecordAdapter(
    private var records: List<UsageRecord>,
    private val packageManager: PackageManager,
    private val onClick: (UsageRecord) -> Unit
) : RecyclerView.Adapter<RecordAdapter.ViewHolder>() {

    fun updateData(newRecords: List<UsageRecord>) {
        records = newRecords
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val layout: View = view.findViewById(R.id.layoutItem)
        val tvName: TextView = view.findViewById(R.id.tvAppName)
        val tvTime: TextView = view.findViewById(R.id.tvUsageTime)
        val ivIcon: ImageView = view.findViewById(R.id.ivAppIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val data = records[position]
        val pName = data.packageName

        holder.tvName.text = data.appName

        val timeAgo = android.text.format.DateUtils.getRelativeTimeSpanString(
            data.lastTimeUsed,
            System.currentTimeMillis(),
            android.text.format.DateUtils.MINUTE_IN_MILLIS
        )

        holder.tvTime.text = "${formatUsageTime(data.totalTime)} 사용함 • $timeAgo"

        val icon: Drawable? = try {
            packageManager.getApplicationIcon(pName)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

        if (icon != null) {
            holder.ivIcon.setImageDrawable(icon)
        } else {
            holder.ivIcon.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        holder.layout.setOnClickListener {
            onClick(data) // UsageRecord 전체를 넘김
        }
    }

    override fun getItemCount() = records.size
}