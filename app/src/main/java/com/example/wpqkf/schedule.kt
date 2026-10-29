package com.example.wpqkf

import android.app.TimePickerDialog
import android.accounts.Account
import android.accounts.AccountManager
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
import androidx.activity.result.IntentSenderRequest
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.AccountPicker
import com.google.api.client.http.HttpResponseException
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
    private var calendarBusy = false
    private var waitingForGoogleUi = false
    private var authorizationOwner: String? = null
    private var pendingAccount: Account? = null
    private var pendingAction: ((String, Account) -> Unit)? = null
    private var googleCalendarSwitch: MaterialSwitch? = null
    private val authorizationClient by lazy { Identity.getAuthorizationClient(this) }

    private val accountPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        waitingForGoogleUi = false
        if (!isCurrentAuthorization()) {
            finishCalendarAction()
        } else {
            val name = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (result.resultCode == RESULT_OK && !name.isNullOrBlank()) {
                authorizeCalendar(Account(name, GoogleCalendarHelper.ACCOUNT_TYPE))
            } else {
                finishCalendarAction()
                showCalendarError(CalendarError.CANCELLED)
            }
        }
    }

    private val authorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        waitingForGoogleUi = false
        if (!isCurrentAuthorization()) {
            finishCalendarAction()
        } else {
            // Parse error-bearing intents even on RESULT_CANCELED. Google may return only
            // a cancellation; in that case the app cannot infer the administrator's policy.
            val data = result.data
            if (data == null) {
                finishCalendarAction()
                showCalendarError(CalendarError.CANCELLED)
            } else try {
                acceptAuthorization(authorizationClient.getAuthorizationResultFromIntent(data))
            } catch (e: Exception) {
                finishCalendarAction()
                showCalendarError(CalendarError.from(e))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_schedule)
        authorizationOwner = savedInstanceState?.getString("calendar_authorization_owner")
        waitingForGoogleUi = savedInstanceState?.getBoolean("calendar_waiting_for_ui") == true
        pendingAccount = savedInstanceState?.getString("calendar_authorization_account")
            ?.let { Account(it, GoogleCalendarHelper.ACCOUNT_TYPE) }
        calendarBusy = authorizationOwner != null
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

        updateGoogleAccountUi()

        loadSchedules()
        refreshScheduleList()

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            selectedDate = "${year}년 ${month + 1}월 ${dayOfMonth}일"
            refreshScheduleList()
        }

        fabAdd.setOnClickListener {
            val bottomSheetDialog = BottomSheetDialog(this)
            bottomSheetDialog.setContentView(R.layout.bottom_sheet_add_schedule)
            val sheetView = checkNotNull(bottomSheetDialog.findViewById<View>(R.id.bottom_sheet_content))
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
            googleCalendarSwitch = swAddToGoogleCalendar

            bottomSheetDialog.setOnDismissListener { googleCalendarSwitch = null }
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

            val currentAccount = GoogleCalendarHelper.getSelectedAccount(this)
            swAddToGoogleCalendar.isChecked = (currentAccount != null)

            swAddToGoogleCalendar.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && GoogleCalendarHelper.getSelectedAccount(this) == null) {
                    swAddToGoogleCalendar.isChecked = false
                    AlertDialog.Builder(this)
                        .setTitle("Google Calendar 연동")
                        .setMessage("Google 계정을 연결해야 Google Calendar에 일정을 추가할 수 있습니다. 지금 연결하시겠습니까?")
                        .setPositiveButton("연결") { _, _ ->
                            chooseGoogleAccount()
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

                // Capture user choices before the asynchronous Firebase write completes.
                val addToGoogle = swAddToGoogleCalendar.isChecked
                val calendarAccount = GoogleCalendarHelper.getSelectedAccount(this)
                val eventDate = selectedDate
                val eventStartHour = pickedStartHour
                val eventStartMinute = pickedStartMinute
                val eventEndHour = pickedEndHour
                val eventEndMinute = pickedEndMinute

                newRef.setValue(newItem)
                    .addOnSuccessListener {
                        if (isFinishing || isDestroyed || uid != GoogleCalendarHelper.ownerId()) {
                            return@addOnSuccessListener
                        }
                        bottomSheetDialog.dismiss()
                        if (addToGoogle) {
                            val signedInAccount = calendarAccount
                            if (signedInAccount != null) {
                                syncWithGoogleCalendar(
                                    account = signedInAccount,
                                    date = eventDate,
                                    title = name,
                                    category = category,
                                    startHour = eventStartHour,
                                    startMinute = eventStartMinute,
                                    endHour = eventEndHour,
                                    endMinute = eventEndMinute
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
                    .addOnFailureListener {
                        if (isFinishing || isDestroyed) return@addOnFailureListener
                        btnAddSchedule.isEnabled = true
                        Toast.makeText(this, "일정을 저장하지 못했습니다. 네트워크 연결을 확인해 주세요.", Toast.LENGTH_SHORT).show()
                        Log.e(TAG, "파이어베이스 일정 저장 실패")
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
        updateGoogleAccountUi()
    }

    override fun onStop() {
        saveSchedules()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Tokens and pending event writes are deliberately not persisted or replayed.
        if (waitingForGoogleUi) {
            outState.putString("calendar_authorization_owner", authorizationOwner)
            outState.putString("calendar_authorization_account", pendingAccount?.name)
            outState.putBoolean("calendar_waiting_for_ui", true)
        }
    }

    private fun updateGoogleAccountUi() {
        val account = GoogleCalendarHelper.getSelectedAccount(this)
        val tvStatus = findViewById<TextView>(R.id.tv_google_account_status) ?: return
        val btnConnect = findViewById<MaterialButton>(R.id.btn_google_connect) ?: return
        btnConnect.isEnabled = !calendarBusy
        tvStatus.text = if (account == null) getString(R.string.google_calendar_not_connected)
            else getString(R.string.google_calendar_connected, account.name)
        btnConnect.text = getString(if (account == null) R.string.google_calendar_connect else R.string.google_calendar_manage)
        btnConnect.setOnClickListener { view ->
            if (account == null) chooseGoogleAccount() else showGoogleAccountMenu(view)
        }
        if (account == null) googleCalendarSwitch?.isChecked = false
    }

    private fun isCurrentAuthorization() = !isFinishing && !isDestroyed &&
        authorizationOwner != null && authorizationOwner == GoogleCalendarHelper.ownerId()

    private fun finishCalendarAction() {
        calendarBusy = false
        waitingForGoogleUi = false
        pendingAccount = null
        pendingAction = null
        authorizationOwner = null
        if (!isDestroyed) updateGoogleAccountUi()
    }

    private fun chooseGoogleAccount() {
        if (calendarBusy) return
        calendarBusy = true
        authorizationOwner = GoogleCalendarHelper.ownerId()
        pendingAction = { _, _ ->
            finishCalendarAction()
            googleCalendarSwitch?.isChecked = true
            Toast.makeText(this, R.string.google_calendar_connection_success, Toast.LENGTH_SHORT).show()
        }
        updateGoogleAccountUi()
        try {
            val options = AccountPicker.AccountChooserOptions.Builder()
                .setAllowableAccountsTypes(listOf(GoogleCalendarHelper.ACCOUNT_TYPE))
                .setAlwaysShowAccountPicker(true).build()
            waitingForGoogleUi = true
            accountPickerLauncher.launch(AccountPicker.newChooseAccountIntent(options))
        } catch (e: Exception) {
            finishCalendarAction()
            showCalendarError(CalendarError.from(e))
        }
    }

    private fun authorizeCalendar(account: Account) {
        pendingAccount = account
        try {
            val request = AuthorizationRequest.builder()
                .setAccount(account)
                .setRequestedScopes(GoogleCalendarHelper.requestedScopes)
                .setOptOutIncludingGrantedScopes(true)
                .build()
            authorizationClient.authorize(request)
                .addOnSuccessListener { result ->
                    if (!isCurrentAuthorization()) {
                        finishCalendarAction()
                        return@addOnSuccessListener
                    }
                    try {
                        if (result.hasResolution()) {
                            val resolution = result.pendingIntent ?: error("Missing authorization resolution")
                            waitingForGoogleUi = true
                            authorizationLauncher.launch(IntentSenderRequest.Builder(resolution.intentSender).build())
                        } else acceptAuthorization(result)
                    } catch (e: Exception) {
                        finishCalendarAction()
                        showCalendarError(CalendarError.from(e))
                    }
                }
                .addOnFailureListener { error ->
                    val current = isCurrentAuthorization()
                    finishCalendarAction()
                    if (current) showCalendarError(CalendarError.from(error))
                }
        } catch (e: Exception) {
            finishCalendarAction()
            showCalendarError(CalendarError.from(e))
        }
    }

    private fun acceptAuthorization(result: AuthorizationResult) {
        val account = pendingAccount
        val token = result.accessToken
        if (!isCurrentAuthorization() || account == null) {
            finishCalendarAction()
            return
        }
        if (token.isNullOrBlank() || GoogleCalendarHelper.SCOPE !in result.grantedScopes) {
            finishCalendarAction()
            showCalendarError(CalendarError.PERMISSION)
            return
        }
        GoogleCalendarHelper.rememberAccount(this, account)
        val action = pendingAction
        pendingAction = null
        pendingAccount = null
        if (action != null) action(token, account) else {
            // Activity recreation may finish linking, but must never replay an event write.
            finishCalendarAction()
            Toast.makeText(this, R.string.google_calendar_connection_success, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showGoogleAccountMenu(view: View) {
        if (calendarBusy) return
        val popup = androidx.appcompat.widget.PopupMenu(this, view)
        popup.menu.add(getString(R.string.google_calendar_view_events))
        popup.menu.add(getString(R.string.google_calendar_change_account))
        popup.menu.add(getString(R.string.google_calendar_disconnect))
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                getString(R.string.google_calendar_view_events) -> showGoogleEvents()
                getString(R.string.google_calendar_change_account) -> chooseGoogleAccount()
                getString(R.string.google_calendar_disconnect) -> disconnectGoogleCalendar()
            }
            true
        }
        popup.show()
    }

    private fun disconnectGoogleCalendar() {
        val account = GoogleCalendarHelper.getSelectedAccount(this) ?: return
        // Stop using the account locally even if the network revocation fails.
        GoogleCalendarHelper.forgetAccount(this)
        calendarBusy = true
        updateGoogleAccountUi()
        val owner = GoogleCalendarHelper.ownerId()
        try {
            authorizationClient.revokeAccess(RevokeAccessRequest.builder()
                .setAccount(account).setScopes(GoogleCalendarHelper.requestedScopes).build())
                .addOnCompleteListener { task ->
                    finishCalendarAction()
                    if (!isFinishing && !isDestroyed && owner == GoogleCalendarHelper.ownerId()) {
                        Toast.makeText(this, if (task.isSuccessful) R.string.google_calendar_disconnected
                            else R.string.google_calendar_disconnect_local_only, Toast.LENGTH_LONG).show()
                    }
                }
        } catch (_: Exception) {
            finishCalendarAction()
            Toast.makeText(this, R.string.google_calendar_disconnect_local_only, Toast.LENGTH_LONG).show()
        }
    }

    private fun <T> runCalendarAction(account: Account, operation: (String) -> Result<T>,
        retry: () -> Unit, onSuccess: (T) -> Unit) {
        if (calendarBusy) {
            Toast.makeText(this, R.string.google_calendar_busy, Toast.LENGTH_LONG).show()
            return
        }
        calendarBusy = true
        authorizationOwner = GoogleCalendarHelper.ownerId()
        pendingAction = { token, _ ->
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) { operation(token) }
                if (!isCurrentAuthorization()) {
                    finishCalendarAction()
                    return@launch
                }
                result.fold(onSuccess = { value ->
                    finishCalendarAction()
                    onSuccess(value)
                }, onFailure = { error ->
                    if ((error as? HttpResponseException)?.statusCode == 401) {
                        // Do not keep reusing a revoked token. A retry must authorize again.
                        try {
                            authorizationClient.clearToken(ClearTokenRequest.builder().setToken(token).build())
                                .addOnCompleteListener {
                                    val current = isCurrentAuthorization()
                                    finishCalendarAction()
                                    if (current) showCalendarError(CalendarError.from(error), retry)
                                }
                        } catch (_: Exception) {
                            finishCalendarAction()
                            showCalendarError(CalendarError.from(error), retry)
                        }
                    } else {
                        finishCalendarAction()
                        showCalendarError(CalendarError.from(error), retry)
                    }
                })
            }
        }
        updateGoogleAccountUi()
        authorizeCalendar(account)
    }

    private fun showCalendarError(error: CalendarError, retry: (() -> Unit)? = null) {
        if (isFinishing || isDestroyed) return
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.google_calendar_error_title)
            .setMessage(getString(error.messageRes) + "\n\n" + getString(R.string.google_calendar_local_available))
            .setPositiveButton(R.string.google_calendar_choose_other_account) { _, _ -> chooseGoogleAccount() }
            .setNegativeButton(R.string.google_calendar_continue_without) { _, _ ->
                googleCalendarSwitch?.isChecked = false
            }
        if (retry != null && error != CalendarError.ADMIN_POLICY && error != CalendarError.CONFIGURATION) {
            dialog.setNeutralButton(R.string.google_calendar_retry_button) { _, _ -> retry() }
        }
        dialog.show()
    }

    private fun syncWithGoogleCalendar(
        account: Account,
        date: String,
        title: String,
        category: String,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        eventId: String = java.util.UUID.randomUUID().toString().replace("-", "")
    ) {
        val startCal = GoogleCalendarHelper.parseKoreanDate(date) ?: return
        startCal.set(Calendar.HOUR_OF_DAY, startHour)
        startCal.set(Calendar.MINUTE, startMinute)
        val endCal = (startCal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, endHour)
            set(Calendar.MINUTE, endMinute)
            if (before(startCal)) add(Calendar.DAY_OF_MONTH, 1)
            else if (timeInMillis == startCal.timeInMillis) add(Calendar.HOUR_OF_DAY, 1)
        }
        runCalendarAction(account,
            operation = { token -> GoogleCalendarHelper.createCalendarEvent(token, title, category, startCal, endCal, eventId = eventId) },
            retry = { syncWithGoogleCalendar(account, date, title, category, startHour, startMinute, endHour, endMinute, eventId) },
            onSuccess = { Toast.makeText(this, R.string.google_calendar_add_success, Toast.LENGTH_SHORT).show() })
    }

    private fun showGoogleEvents() {
        val account = GoogleCalendarHelper.getSelectedAccount(this) ?: return
        runCalendarAction(account, { token -> GoogleCalendarHelper.listUpcomingEvents(token) },
            retry = { showGoogleEvents() }, onSuccess = { events ->
                val lines = events.map { event ->
                    val start = event.start?.dateTime ?: event.start?.date
                    "${start ?: ""}  ${event.summary ?: getString(R.string.google_calendar_untitled)}"
                }
                AlertDialog.Builder(this).setTitle(R.string.google_calendar_view_events)
                    .setMessage(if (lines.isEmpty()) getString(R.string.google_calendar_no_events) else lines.joinToString("\n\n"))
                    .setPositiveButton(R.string.schedule_close, null).show()
            })
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

                for ((_, name, hour, minute, category) in schedules) {
                    val scheduleJson = JSONObject().apply {
                        put("name", name)
                        put("hour", hour)
                        put("minute", minute)
                        put("category", category)
                    }
                    daySchedulesJson.put(scheduleJson)
                }

                schedulesJson.put(date, daySchedulesJson)
            }

            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
                putString(getScheduleStorageKey(), schedulesJson.toString())
            }
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
