package com.example.wpqkf

import android.app.TimePickerDialog
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CalendarView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.ArrayList
import java.util.Calendar
import java.util.Locale

class schedule : AppCompatActivity() {
    private lateinit var mBtnSchedule3: ImageButton
    private lateinit var mBtnRecord3: ImageButton
    private lateinit var mBtnHome3: ImageButton
    private lateinit var mBtnAi3: ImageButton
    private lateinit var mBtnProfile3: ImageButton

    companion object {
        private const val TAG = "Schedule"
        private const val PREFS_NAME = "schedule_preferences"
        private const val SCHEDULE_KEY_PREFIX = "schedules_"
    }

    private data class ScheduleViewHolder(
        val time: TextView,
        val name: TextView,
        val category: TextView,
        var btnMore: ImageButton,
    )

    // 날짜별로 일정 리스트를 따로 보관할 주머니 (Key: "2026년 5월 28일", Value: 일정 리스트)
    private val allSchedules = HashMap<String, java.util.ArrayList<user.ScheduleItem>>()

    // 리스트뷰에 실제로 표시할 일정 리스트와 카드형 어댑터
    private val displayList = ArrayList<user.ScheduleItem>()
    private lateinit var listAdapter: ScheduleAdapter
    private lateinit var mListView: ListView
    private lateinit var mTvSelectedDate: TextView
    private lateinit var mTvScheduleCount: TextView
    private lateinit var mTvEmptySchedule: TextView

    // 현재 유저가 달력에서 선택한 날짜를 기억할 변수
    private var selectedDate: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_schedule)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        mBtnSchedule3 = findViewById(R.id.schedulesc)
        mBtnRecord3 = findViewById(R.id.recordsc)
        mBtnHome3 = findViewById(R.id.homesc)
        mBtnAi3 = findViewById(R.id.aisc)
        mBtnProfile3 = findViewById(R.id.profilesc)

        val calendarView = findViewById<CalendarView>(R.id.calendarView)
        mListView = findViewById(R.id.listView_schedule)
        mTvSelectedDate = findViewById(R.id.tv_selected_date)
        mTvScheduleCount = findViewById(R.id.tv_schedule_count)
        mTvEmptySchedule = findViewById(R.id.tv_empty_schedule)
        val fabAdd = findViewById<ExtendedFloatingActionButton>(R.id.fab_add)

        // 리스트뷰 및 어댑터 초기화 세팅
        listAdapter = ScheduleAdapter(this, displayList)
        mListView.adapter = listAdapter

        // [초기화] 앱을 켜자마자 '오늘 날짜'를 기본 선택 상태로 만들기
        val currentCal = Calendar.getInstance()
        selectedDate = "${currentCal.get(Calendar.YEAR)}년 ${currentCal.get(Calendar.MONTH) + 1}월 ${
            currentCal.get(Calendar.DAY_OF_MONTH)
        }일"

        // 저장된 일정을 불러온 뒤 첫 화면에 오늘 일정을 시간순으로 표시
        loadSchedules()
        refreshScheduleList()

        // 1. 달력에서 날짜를 선택했을 때 이벤트
        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            // 선택한 날짜 갱신
            selectedDate = "${year}년 ${month + 1}월 ${dayOfMonth}일"
            // 해당 날짜의 일정만 새로고침해서 띄우기
            refreshScheduleList()
        }

        // 2. 우측 하단 + 버튼 클릭 시 일정 추가 Bottom Sheet 띄우기
        fabAdd.setOnClickListener {
            val sheetView =
                LayoutInflater.from(this).inflate(R.layout.bottom_sheet_add_schedule, null)
            val tvSheetDate = sheetView.findViewById<TextView>(R.id.tv_bottom_sheet_date)
            val btnClose = sheetView.findViewById<ImageButton>(R.id.btn_close_bottom_sheet)
            val btnCancel = sheetView.findViewById<MaterialButton>(R.id.btn_cancel_schedule)
            val btnAddSchedule = sheetView.findViewById<MaterialButton>(R.id.btn_add_schedule)
            val nameLayout = sheetView.findViewById<TextInputLayout>(R.id.til_schedule_name)
            val timeLayout = sheetView.findViewById<TextInputLayout>(R.id.til_schedule_time)
            val etName = sheetView.findViewById<EditText>(R.id.et_schedule_name)
            val etTime = sheetView.findViewById<EditText>(R.id.et_schedule_time)
            val etCategory = sheetView.findViewById<EditText>(R.id.et_schedule_category)

            val bottomSheetDialog = BottomSheetDialog(this)
            bottomSheetDialog.setContentView(sheetView)
            tvSheetDate.text = selectedDate

            bottomSheetDialog.setOnShowListener {
                val bottomSheet = bottomSheetDialog.findViewById<View>(
                    com.google.android.material.R.id.design_bottom_sheet
                )
                bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
                bottomSheet?.let {
                    BottomSheetBehavior.from(it).apply {
                        state = BottomSheetBehavior.STATE_EXPANDED
                        skipCollapsed = true
                    }
                }
            }

            btnClose.setOnClickListener { bottomSheetDialog.dismiss() }
            btnCancel.setOnClickListener { bottomSheetDialog.dismiss() }

            // 타임피커에서 선택된 시, 분을 임시 저장할 변수 (기본값 현재 시간)
            val pickerCal = Calendar.getInstance()
            var pickedHour = pickerCal.get(Calendar.HOUR_OF_DAY)
            var pickedMinute = pickerCal.get(Calendar.MINUTE)

            // 시간 선택창(EditText) 클릭 시 안드로이드 정식 TimePickerDialog 호출
            etTime.setOnClickListener {
                val timePickerDialog = TimePickerDialog(this, { _, hourOfDay, minute ->
                    pickedHour = hourOfDay
                    pickedMinute = minute
                    etTime.setText(
                        String.format(
                            Locale.getDefault(),
                            "%02d:%02d",
                            hourOfDay,
                            minute
                        )
                    )
                    timeLayout.error = null
                }, pickedHour, pickedMinute, true) // true 설정 시 24시간 형식 사용
                timePickerDialog.show()
            }
            timeLayout.setEndIconOnClickListener { etTime.performClick() }

            btnAddSchedule.setOnClickListener addClick@{
                val name = etName.text.toString().trim()
                val category = etCategory.text.toString().trim()
                val timeText = etTime.text.toString().trim()

                // 기존 에러 메시지 초기화
                nameLayout.error = null
                timeLayout.error = null

                // 1. 이름 미입력 시 경고 표시
                if (name.isEmpty()) {
                    nameLayout.error = getString(R.string.schedule_name_required)
                    etName.requestFocus()
                    return@addClick
                }

                // 2. 시간 미선택 시 경고 표시
                if (timeText.isEmpty()) {
                    timeLayout.error = getString(R.string.schedule_time_required)
                    return@addClick
                }

                // 3. 파이어베이스 업로드
                val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
                val databaseRef = com.google.firebase.database.FirebaseDatabase.getInstance()
                    .getReference("UserAccount")
                    .child(uid)
                    .child("schedules")
                    .child(selectedDate)

                val newRef = databaseRef.push() // 파이어베이스 고유 키 생성
                val newItem = user.ScheduleItem(
                    id = newRef.key ?: "",
                    name = name,
                    hour = pickedHour,
                    minute = pickedMinute,
                    category = category
                )

                newRef.setValue(newItem)
                    .addOnSuccessListener {
                        Toast.makeText(this, "일정이 저장되었습니다.", Toast.LENGTH_SHORT).show()
                        bottomSheetDialog.dismiss()
                    }
                    .addOnFailureListener { e ->
                        // 실패 시 오류 메시지 출력
                        Toast.makeText(this, "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e(TAG, "파이어베이스 일정 저장 실패", e)
                    }
            }
            bottomSheetDialog.show()
        }

        mBtnHome3.setOnClickListener {
            val intent = Intent(this@schedule, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
        mBtnRecord3.setOnClickListener {
            val intent = Intent(this@schedule, record::class.java)
            startActivity(intent)
            finish()
        }
        mBtnAi3.setOnClickListener {
            val intent = Intent(this@schedule, ai::class.java)
            startActivity(intent)
            finish()
        }
        mBtnProfile3.setOnClickListener {
            val intent = Intent(this@schedule, profile::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onStop() {
        saveSchedules()
        super.onStop()
    }

    private fun getScheduleStorageKey(): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        return "${com.example.wpqkf.schedule.Companion.SCHEDULE_KEY_PREFIX}$uid"
    }

    private fun saveSchedules() {
        try {
            val schedulesJson = JSONObject()

            for ((date, schedules) in allSchedules) {
                val daySchedulesJson = JSONArray()

                for (schedule in schedules) {
                    val scheduleJson = JSONObject().apply {
                        put("name", schedule.name)
                        put("hour", schedule.hour)
                        put("minute", schedule.minute)
                        put("category", schedule.category)
                    }
                    daySchedulesJson.put(scheduleJson)
                }

                schedulesJson.put(date, daySchedulesJson)
            }

            getSharedPreferences(com.example.wpqkf.schedule.Companion.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(getScheduleStorageKey(), schedulesJson.toString())
                .apply()
        } catch (e: JSONException) {
            Log.e(com.example.wpqkf.schedule.Companion.TAG, "일정을 저장하지 못했습니다.", e)
        }
    }

    private fun loadSchedules() {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        // UserAccount/{uid}/schedules 경로 참조
        val databaseRef = com.google.firebase.database.FirebaseDatabase.getInstance()
            .getReference("UserAccount")
            .child(uid)
            .child("schedules")

        databaseRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                allSchedules.clear()
                for (dateSnapshot in snapshot.children) {
                    val date = dateSnapshot.key ?: continue
                    val daySchedules = ArrayList<user.ScheduleItem>()
                    for (itemSnapshot in dateSnapshot.children) {
                        val item = itemSnapshot.getValue(user.ScheduleItem::class.java)
                        if (item != null) {
                            item.id = itemSnapshot.key ?: ""
                            daySchedules.add(item)
                        }
                    }
                    allSchedules[date] = daySchedules
                }
                refreshScheduleList() // 리스트 UI 갱신
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.e(TAG, "일정 로드 실패: ${error.message}")
            }
        })
    }

    private inner class ScheduleAdapter(
        context: Context,
        items: ArrayList<user.ScheduleItem>,
    ) : ArrayAdapter<user.ScheduleItem>(context, 0, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val rowView: View
            val holder: com.example.wpqkf.schedule.ScheduleViewHolder


            if (convertView == null) {
                rowView =
                    LayoutInflater.from(context).inflate(R.layout.item_schedule, parent, false)
                holder = ScheduleViewHolder(
                    time = rowView.findViewById(R.id.tv_item_time),
                    name = rowView.findViewById(R.id.tv_item_name),
                    category = rowView.findViewById(R.id.tv_item_category),
                    btnMore = rowView.findViewById(R.id.btn_item_more)
                )
                rowView.tag = holder
            } else {
                rowView = convertView
                holder = rowView.tag as com.example.wpqkf.schedule.ScheduleViewHolder
            }

            val schedule = getItem(position)
            if (schedule != null) {
                holder.time.text =
                    String.format(Locale.getDefault(), "%02d:%02d", schedule.hour, schedule.minute)
                holder.name.text = schedule.name
                holder.category.text =
                    schedule.category.ifBlank { getString(R.string.schedule_uncategorized) }

                // 점 3개 버튼 클릭 이벤트 (삭제 팝업)
                holder.btnMore.setOnClickListener { view ->
                    val popup = androidx.appcompat.widget.PopupMenu(context, view)
                    popup.menu.add("삭제")
                    popup.setOnMenuItemClickListener { menuItem ->
                        if (menuItem.title == "삭제") {
                            val uid =
                                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                                    ?: "guest"
                            if (schedule.id.isNotEmpty()) {
                                // 파이어베이스에서 해당 항목 삭제
                                com.google.firebase.database.FirebaseDatabase.getInstance()
                                    .getReference("UserAccount")
                                    .child(uid)
                                    .child("schedules")
                                    .child(selectedDate)
                                    .child(schedule.id)
                                    .removeValue()
                                    .addOnSuccessListener {
                                        Toast.makeText(context, "일정이 삭제되었습니다.", Toast.LENGTH_SHORT)
                                            .show()
                                    }
                            }
                            true
                        } else {
                            false
                        }
                    }
                    popup.show()
                }
            }

            return rowView

        }
    }

        // 현재 선택된 날짜의 일정을 [시간순 정렬]하여 화면에 반영하는 핵심 내장 함수
        private fun refreshScheduleList() {
            displayList.clear()
            val dayList = allSchedules[selectedDate]

            if (dayList != null && dayList.isNotEmpty()) {
                // 시간순 정렬 수행: 시(Hour) * 60 + 분(Minute) 값이 작은 순서대로 정렬
                dayList.sortBy { it.hour * 60 + it.minute }
                displayList.addAll(dayList)
            }

            val scheduleCount = displayList.size
            mTvSelectedDate.text = selectedDate
            mTvScheduleCount.text = getString(R.string.schedule_count, scheduleCount)
            mListView.visibility = if (scheduleCount > 0) View.VISIBLE else View.GONE
            mTvEmptySchedule.visibility = if (scheduleCount == 0) View.VISIBLE else View.GONE
            listAdapter.notifyDataSetChanged()
        }
    }