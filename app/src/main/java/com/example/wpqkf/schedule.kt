package com.example.wpqkf

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CalendarView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private val allSchedules = HashMap<String, ArrayList<user.ScheduleItem>>()
    private val displayList = ArrayList<user.ScheduleItem>()
    private lateinit var listAdapter: ScheduleAdapter
    private lateinit var mListView: ListView
    private lateinit var mTvSelectedDate: TextView
    private lateinit var mTvScheduleCount: TextView
    private lateinit var mTvEmptySchedule: TextView

    private var selectedDate: String = ""

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                Toast.makeText(this, "Google 계정이 연결되었습니다.", Toast.LENGTH_SHORT).show()
                updateGoogleAccountUi(account)
            } catch (e: ApiException) {
                Log.e(TAG, "Google 로그인 실패 status: ${e.statusCode}", e)
                Toast.makeText(this, "Google 계정 연결 실패 (${e.statusCode})", Toast.LENGTH_SHORT).show()
                updateGoogleAccountUi(GoogleCalendarHelper.getSignedInAccount(this))
            }
        } else {
            Toast.makeText(this, "Google 계정 연결이 취소되었습니다.", Toast.LENGTH_SHORT).show()
            updateGoogleAccountUi(GoogleCalendarHelper.getSignedInAccount(this))
        }
    }

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

        listAdapter = ScheduleAdapter(this, displayList)
        mListView.adapter = listAdapter

        val currentCal = Calendar.getInstance()
        selectedDate = "${currentCal.get(Calendar.YEAR)}년 ${currentCal.get(Calendar.MONTH) + 1}월 ${
            currentCal.get(Calendar.DAY_OF_MONTH)
        }일"

        updateGoogleAccountUi(GoogleCalendarHelper.getSignedInAccount(this))

        loadSchedules()
        refreshScheduleList()

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            selectedDate = "${year}년 ${month + 1}월 ${dayOfMonth}일"
            refreshScheduleList()
        }

        fabAdd.setOnClickListener {
            val sheetView =
                LayoutInflater.from(this).inflate(R.layout.bottom_sheet_add_schedule, null)
            val tvSheetDate = sheetView.findViewById<TextView>(R.id.tv_bottom_sheet_date)
            val btnClose = sheetView.findViewById<ImageButton>(R.id.btn_close_bottom_sheet)
            val btnCancel = sheetView.findViewById<MaterialButton>(R.id.btn_cancel_schedule)
            val btnAddSchedule = sheetView.findViewById<MaterialButton>(R.id.btn_add_schedule)
            val nameLayout = sheetView.findViewById<TextInputLayout>(R.id.til_schedule_name)
            val timeLayout = sheetView.findViewById<TextInputLayout>(R.id.til_schedule_time)
            val endTimeLayout = sheetView.findViewById<TextInputLayout>(R.id.til_schedule_end_time)
            val etName = sheetView.findViewById<EditText>(R.id.et_schedule_name)
            val etTime = sheetView.findViewById<EditText>(R.id.et_schedule_time)
            val etEndTime = sheetView.findViewById<EditText>(R.id.et_schedule_end_time)
            val etCategory = sheetView.findViewById<EditText>(R.id.et_schedule_category)
            val swAddToGoogleCalendar = sheetView.findViewById<MaterialSwitch>(R.id.sw_add_to_google_calendar)

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

            val pickerCal = Calendar.getInstance()
            var pickedStartHour = pickerCal.get(Calendar.HOUR_OF_DAY)
            var pickedStartMinute = pickerCal.get(Calendar.MINUTE)

            var pickedEndHour = (pickedStartHour + 1) % 24
            var pickedEndMinute = pickedStartMinute
            var userChangedEndTime = false

            etTime.setText(String.format(Locale.getDefault(), "%02d:%02d", pickedStartHour, pickedStartMinute))
            etEndTime.setText(String.format(Locale.getDefault(), "%02d:%02d", pickedEndHour, pickedEndMinute))

            etTime.setOnClickListener {
                val timePickerDialog = TimePickerDialog(this, { _, hourOfDay, minute ->
                    pickedStartHour = hourOfDay
                    pickedStartMinute = minute
                    etTime.setText(String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute))
                    timeLayout.error = null

                    if (!userChangedEndTime) {
                        pickedEndHour = (pickedStartHour + 1) % 24
                        pickedEndMinute = pickedStartMinute
                        etEndTime.setText(String.format(Locale.getDefault(), "%02d:%02d", pickedEndHour, pickedEndMinute))
                    }
                }, pickedStartHour, pickedStartMinute, true)
                timePickerDialog.show()
            }
            timeLayout.setEndIconOnClickListener { etTime.performClick() }

            etEndTime.setOnClickListener {
                val timePickerDialog = TimePickerDialog(this, { _, hourOfDay, minute ->
                    pickedEndHour = hourOfDay
                    pickedEndMinute = minute
                    userChangedEndTime = true
                    etEndTime.setText(String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute))
                    endTimeLayout.error = null
                }, pickedEndHour, pickedEndMinute, true)
                timePickerDialog.show()
            }
            endTimeLayout.setEndIconOnClickListener { etEndTime.performClick() }

            val currentAccount = GoogleCalendarHelper.getSignedInAccount(this)
            swAddToGoogleCalendar.isChecked = (currentAccount != null)

            swAddToGoogleCalendar.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && GoogleCalendarHelper.getSignedInAccount(this) == null) {
                    AlertDialog.Builder(this)
                        .setTitle("Google Calendar 연동")
                        .setMessage("Google 계정을 연결해야 Google Calendar에 일정을 추가할 수 있습니다. 지금 연결하시겠습니까?")
                        .setPositiveButton("연결") { _, _ ->
                            launchGoogleSignIn()
                        }
                        .setNegativeButton("취소") { _, _ ->
                            swAddToGoogleCalendar.isChecked = false
                        }
                        .show()
                }
            }

            btnAddSchedule.setOnClickListener addClick@{
                if (!btnAddSchedule.isEnabled) return@addClick
                btnAddSchedule.isEnabled = false

                val name = etName.text.toString().trim()
                val category = etCategory.text.toString().trim()
                val timeText = etTime.text.toString().trim()
                val endTimeText = etEndTime.text.toString().trim()

                nameLayout.error = null
                timeLayout.error = null
                endTimeLayout.error = null

                if (name.isEmpty()) {
                    nameLayout.error = getString(R.string.schedule_name_required)
                    etName.requestFocus()
                    btnAddSchedule.isEnabled = true
                    return@addClick
                }

                if (timeText.isEmpty()) {
                    timeLayout.error = getString(R.string.schedule_time_required)
                    btnAddSchedule.isEnabled = true
                    return@addClick
                }

                if (endTimeText.isEmpty()) {
                    endTimeLayout.error = getString(R.string.google_calendar_end_time_required)
                    btnAddSchedule.isEnabled = true
                    return@addClick
                }

                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
                val databaseRef = FirebaseDatabase.getInstance()
                    .getReference("UserAccount")
                    .child(uid)
                    .child("schedules")
                    .child(selectedDate)

                val newRef = databaseRef.push()
                val newItem = user.ScheduleItem(
                    id = newRef.key ?: "",
                    name = name,
                    hour = pickedStartHour,
                    minute = pickedStartMinute,
                    category = category
                )

                newRef.setValue(newItem)
                    .addOnSuccessListener {
                        if (swAddToGoogleCalendar.isChecked) {
                            val signedInAccount = GoogleCalendarHelper.getSignedInAccount(this)
                            if (signedInAccount != null) {
                                syncWithGoogleCalendar(
                                    account = signedInAccount,
                                    title = name,
                                    category = category,
                                    startHour = pickedStartHour,
                                    startMinute = pickedStartMinute,
                                    endHour = pickedEndHour,
                                    endMinute = pickedEndMinute,
                                    onComplete = {
                                        bottomSheetDialog.dismiss()
                                    }
                                )
                            } else {
                                Toast.makeText(
                                    this,
                                    "앱 내 일정은 추가되었으나 Google 계정이 연결되어 있지 않습니다.",
                                    Toast.LENGTH_LONG
                                ).show()
                                bottomSheetDialog.dismiss()
                            }
                        } else {
                            Toast.makeText(this, "일정이 저장되었습니다.", Toast.LENGTH_SHORT).show()
                            bottomSheetDialog.dismiss()
                        }
                    }
                    .addOnFailureListener { e ->
                        btnAddSchedule.isEnabled = true
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

    override fun onResume() {
        super.onResume()
        updateGoogleAccountUi(GoogleCalendarHelper.getSignedInAccount(this))
    }

    override fun onStop() {
        saveSchedules()
        super.onStop()
    }

    private fun updateGoogleAccountUi(account: GoogleSignInAccount?) {
        val tvStatus = findViewById<TextView>(R.id.tv_google_account_status) ?: return
        val btnConnect = findViewById<MaterialButton>(R.id.btn_google_connect) ?: return

        if (account != null && GoogleCalendarHelper.hasCalendarPermission(this, account)) {
            tvStatus.text = getString(R.string.google_calendar_connected, account.email ?: "")
            btnConnect.text = getString(R.string.google_calendar_manage)
            btnConnect.setOnClickListener { view ->
                showGoogleAccountMenu(view)
            }
        } else {
            tvStatus.text = getString(R.string.google_calendar_not_connected)
            btnConnect.text = getString(R.string.google_calendar_connect)
            btnConnect.setOnClickListener {
                launchGoogleSignIn()
            }
        }
    }

    private fun launchGoogleSignIn() {
        val client = GoogleCalendarHelper.getGoogleSignInClient(this)
        googleSignInLauncher.launch(client.signInIntent)
    }

    private fun showGoogleAccountMenu(view: View) {
        val popup = androidx.appcompat.widget.PopupMenu(this, view)
        popup.menu.add(getString(R.string.google_calendar_change_account))
        popup.menu.add(getString(R.string.google_calendar_disconnect))
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.title) {
                getString(R.string.google_calendar_change_account) -> {
                    GoogleCalendarHelper.getGoogleSignInClient(this).signOut().addOnCompleteListener {
                        launchGoogleSignIn()
                    }
                    true
                }
                getString(R.string.google_calendar_disconnect) -> {
                    GoogleCalendarHelper.getGoogleSignInClient(this).signOut().addOnCompleteListener {
                        Toast.makeText(this, "Google 계정 연동이 해제되었습니다.", Toast.LENGTH_SHORT).show()
                        updateGoogleAccountUi(null)
                    }
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun syncWithGoogleCalendar(
        account: GoogleSignInAccount,
        title: String,
        category: String,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        onComplete: () -> Unit
    ) {
        val startCal = GoogleCalendarHelper.parseKoreanDate(selectedDate) ?: Calendar.getInstance()
        startCal.set(Calendar.HOUR_OF_DAY, startHour)
        startCal.set(Calendar.MINUTE, startMinute)
        startCal.set(Calendar.SECOND, 0)
        startCal.set(Calendar.MILLISECOND, 0)

        val endCal = startCal.clone() as Calendar
        endCal.set(Calendar.HOUR_OF_DAY, endHour)
        endCal.set(Calendar.MINUTE, endMinute)
        endCal.set(Calendar.SECOND, 0)
        endCal.set(Calendar.MILLISECOND, 0)

        if (endCal.before(startCal)) {
            // End time is earlier than start time (e.g. 23:00 to 01:00 next day) -> set end date to next day
            endCal.add(Calendar.DAY_OF_MONTH, 1)
        } else if (endCal == startCal) {
            // End time same as start time -> default to 1 hour duration
            endCal.add(Calendar.HOUR_OF_DAY, 1)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val result = GoogleCalendarHelper.createCalendarEvent(
                context = this@schedule,
                account = account,
                title = title,
                category = category,
                startCal = startCal,
                endCal = endCal
            )

            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    Toast.makeText(this@schedule, getString(R.string.google_calendar_add_success), Toast.LENGTH_SHORT).show()
                    onComplete()
                } else {
                    val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "알 수 없는 오류"
                    showRetryDialog(
                        title = title,
                        category = category,
                        startHour = startHour,
                        startMinute = startMinute,
                        endHour = endHour,
                        endMinute = endMinute,
                        errorMsg = errorMsg,
                        account = account,
                        onComplete = onComplete
                    )
                }
            }
        }
    }

    private fun showRetryDialog(
        title: String,
        category: String,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        errorMsg: String,
        account: GoogleSignInAccount,
        onComplete: () -> Unit
    ) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.google_calendar_retry_prompt))
            .setMessage(getString(R.string.google_calendar_retry_message, errorMsg))
            .setPositiveButton(getString(R.string.google_calendar_retry_button)) { _, _ ->
                syncWithGoogleCalendar(
                    account = account,
                    title = title,
                    category = category,
                    startHour = startHour,
                    startMinute = startMinute,
                    endHour = endHour,
                    endMinute = endMinute,
                    onComplete = onComplete
                )
            }
            .setNegativeButton("닫기") { _, _ ->
                onComplete()
            }
            .setCancelable(false)
            .show()
    }

    private fun getScheduleStorageKey(): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        return "${SCHEDULE_KEY_PREFIX}$uid"
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

            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(getScheduleStorageKey(), schedulesJson.toString())
                .apply()
        } catch (e: JSONException) {
            Log.e(TAG, "일정을 저장하지 못했습니다.", e)
        }
    }

    private fun loadSchedules() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        val databaseRef = FirebaseDatabase.getInstance()
            .getReference("UserAccount")
            .child(uid)
            .child("schedules")

        databaseRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
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
                refreshScheduleList()
            }

            override fun onCancelled(error: DatabaseError) {
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
            val holder: ScheduleViewHolder

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
                holder = rowView.tag as ScheduleViewHolder
            }

            val schedule = getItem(position)
            if (schedule != null) {
                holder.time.text =
                    String.format(Locale.getDefault(), "%02d:%02d", schedule.hour, schedule.minute)
                holder.name.text = schedule.name
                holder.category.text =
                    schedule.category.ifBlank { getString(R.string.schedule_uncategorized) }

                holder.btnMore.setOnClickListener { view ->
                    val popup = androidx.appcompat.widget.PopupMenu(context, view)
                    popup.menu.add("삭제")
                    popup.setOnMenuItemClickListener { menuItem ->
                        if (menuItem.title == "삭제") {
                            val uid =
                                FirebaseAuth.getInstance().currentUser?.uid
                                    ?: "guest"
                            if (schedule.id.isNotEmpty()) {
                                FirebaseDatabase.getInstance()
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

    private fun refreshScheduleList() {
        displayList.clear()
        val dayList = allSchedules[selectedDate]

        if (dayList != null && dayList.isNotEmpty()) {
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
