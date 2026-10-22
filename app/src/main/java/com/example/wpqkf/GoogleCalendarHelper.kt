package com.example.wpqkf

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import java.util.Calendar
import java.util.TimeZone
import java.util.regex.Pattern

object GoogleCalendarHelper {
    private const val TAG = "GoogleCalendarHelper"
    val CALENDAR_SCOPE by lazy { Scope(CalendarScopes.CALENDAR_EVENTS) }

    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(CALENDAR_SCOPE)
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun getSignedInAccount(context: Context): GoogleSignInAccount? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return if (account != null && GoogleSignIn.hasPermissions(account, CALENDAR_SCOPE)) {
            account
        } else {
            null
        }
    }

    fun hasCalendarPermission(context: Context, account: GoogleSignInAccount?): Boolean {
        return account != null && GoogleSignIn.hasPermissions(account, CALENDAR_SCOPE)
    }

    fun parseKoreanDate(dateStr: String): Calendar? {
        val pattern = Pattern.compile("(\\d+)년\\s*(\\d+)월\\s*(\\d+)일")
        val matcher = pattern.matcher(dateStr)
        if (matcher.find()) {
            val year = matcher.group(1)?.toIntOrNull() ?: return null
            val month = matcher.group(2)?.toIntOrNull() ?: return null
            val day = matcher.group(3)?.toIntOrNull() ?: return null

            val cal = Calendar.getInstance()
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month - 1)
            cal.set(Calendar.DAY_OF_MONTH, day)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal
        }
        return null
    }

    fun buildEventDateTime(cal: Calendar): Pair<DateTime, String> {
        val timeZone = TimeZone.getDefault()
        val dateTime = DateTime(cal.time, timeZone)
        return Pair(dateTime, timeZone.id)
    }

    fun createCalendarEvent(
        context: Context,
        account: GoogleSignInAccount,
        title: String,
        category: String,
        startCal: Calendar,
        endCal: Calendar
    ): Result<Event> {
        return try {
            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                listOf(CalendarScopes.CALENDAR_EVENTS)
            ).setSelectedAccount(account.account)

            val calendarService = com.google.api.services.calendar.Calendar.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("DATA Schedule App").build()

            val (startDateTime, startTz) = buildEventDateTime(startCal)
            val (endDateTime, endTz) = buildEventDateTime(endCal)

            val event = Event().apply {
                summary = title
                description = if (category.isNotBlank()) "카테고리: $category" else null
                start = EventDateTime().setDateTime(startDateTime).setTimeZone(startTz)
                end = EventDateTime().setDateTime(endDateTime).setTimeZone(endTz)
            }

            val createdEvent = calendarService.events().insert("primary", event).execute()
            Log.d(TAG, "Successfully created Google Calendar event: ${createdEvent.id}")
            Result.success(createdEvent)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating Google Calendar event", e)
            Result.failure(e)
        }
    }
}
