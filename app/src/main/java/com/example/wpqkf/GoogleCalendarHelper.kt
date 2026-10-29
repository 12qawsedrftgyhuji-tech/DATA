package com.example.wpqkf

import android.content.Context
import android.accounts.Account
import androidx.core.content.edit
import com.google.android.gms.common.api.Scope
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.HttpResponseException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.firebase.auth.FirebaseAuth
import java.util.Calendar
import java.util.TimeZone
import java.util.regex.Pattern

object GoogleCalendarHelper {
    // Only the user's own primary calendar is used; shared calendars need no access.
    const val SCOPE = "https://www.googleapis.com/auth/calendar.events.owned"
    const val ACCOUNT_TYPE = "com.google"
    const val ACCOUNT_PREFS = "google_calendar_accounts"
    val requestedScopes: List<Scope> get() = listOf(Scope(SCOPE))
    fun ownerId(): String = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"

    // Store the chosen account per app user, never a token. Each API action reauthorizes.
    fun getSelectedAccount(context: Context): Account? = context
        .getSharedPreferences(ACCOUNT_PREFS, Context.MODE_PRIVATE)
        .getString(ownerId(), null)?.takeIf { it.isNotBlank() }?.let { Account(it, ACCOUNT_TYPE) }

    fun rememberAccount(context: Context, account: Account) {
        context.getSharedPreferences(ACCOUNT_PREFS, Context.MODE_PRIVATE).edit {
            putString(ownerId(), account.name)
        }
    }

    fun forgetAccount(context: Context) {
        context.getSharedPreferences(ACCOUNT_PREFS, Context.MODE_PRIVATE).edit {
            remove(ownerId())
        }
    }

    internal fun service(accessToken: String, transport: HttpTransport = NetHttpTransport()) =
        com.google.api.services.calendar.Calendar.Builder(
            transport, GsonFactory.getDefaultInstance()
        ) { request ->
            request.headers.authorization = "Bearer $accessToken"
            request.connectTimeout = 15_000
            request.readTimeout = 20_000
            request.isLoggingEnabled = false
            request.isCurlLoggingEnabled = false
        }.setApplicationName("DATA Schedule App").build()

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

    fun buildEventDateTime(cal: Calendar): Pair<com.google.api.client.util.DateTime, String> {
        val timeZone = TimeZone.getDefault()
        val dateTime = com.google.api.client.util.DateTime(cal.time, timeZone)
        return Pair(dateTime, timeZone.id)
    }

    fun createCalendarEvent(
        accessToken: String,
        title: String,
        category: String,
        startCal: Calendar,
        endCal: Calendar,
        transport: HttpTransport = NetHttpTransport(),
        eventId: String = java.util.UUID.randomUUID().toString().replace("-", "")
    ): Result<Event> {
        return try {
            val (startDateTime, startTz) = buildEventDateTime(startCal)
            val (endDateTime, endTz) = buildEventDateTime(endCal)

            val event = Event().apply {
                id = eventId
                summary = title
                description = if (category.isNotBlank()) "카테고리: $category" else null
                start = EventDateTime().setDateTime(startDateTime).setTimeZone(startTz)
                end = EventDateTime().setDateTime(endDateTime).setTimeZone(endTz)

                setSummary(title)
                if (category.isNotBlank()) {
                    setDescription("카테고리: $category")
                }
                setStart(EventDateTime().setDateTime(startDateTime).setTimeZone(startTz))
                setEnd(EventDateTime().setDateTime(endDateTime).setTimeZone(endTz))
            }

            val calendar = service(accessToken, transport)
            val createdEvent = try {
                calendar.events().insert("primary", event).execute()
            } catch (e: HttpResponseException) {
                // A previous request may have succeeded before a network timeout.
                if (e.statusCode != 409) throw e
                calendar.events().get("primary", eventId).execute()
            }
            Result.success(createdEvent)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listUpcomingEvents(accessToken: String, transport: HttpTransport = NetHttpTransport()): Result<List<Event>> =
        runCatching {
            service(accessToken, transport).events().list("primary")
                .setTimeMin(DateTime(System.currentTimeMillis()))
                .setSingleEvents(true).setOrderBy("startTime").setMaxResults(10)
                .execute().items.orEmpty()
        }
}
