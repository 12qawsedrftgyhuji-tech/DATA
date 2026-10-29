package com.example.wpqkf

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.AccountPicker
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarFailureUiTest {
    private fun launch(): ActivityScenario<schedule> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return ActivityScenario.launch<schedule>(Intent(context, schedule::class.java)).also { awaitFocus(it) }
    }

    private fun awaitFocus(scenario: ActivityScenario<schedule>) {
        val deadline = android.os.SystemClock.uptimeMillis() + 30_000
        var focused = false
        while (!focused && android.os.SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { focused = it.hasWindowFocus() }
            if (!focused) Thread.sleep(100)
        }
        assertTrue("The emulator must give the schedule window focus before UI interaction", focused)
    }

    @Test fun administratorFailureCanBeDismissedAndLocalScheduleFormStillWorks() {
        launch().use { scenario ->
            scenario.onActivity { activity ->
                val method = schedule::class.java.getDeclaredMethod("showCalendarError", CalendarError::class.java, Function0::class.java)
                method.isAccessible = true
                method.invoke(activity, CalendarError.ADMIN_POLICY, null)
            }
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            onView(withSubstring(context.getString(R.string.google_calendar_admin_policy)))
                .inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(R.string.google_calendar_continue_without))
                .inRoot(isDialog()).perform(click())
            onView(withId(R.id.fab_add)).perform(click())
            onView(withId(R.id.et_schedule_name))
                .inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withId(R.id.btn_cancel_schedule)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.btn_google_connect)).check(matches(isEnabled()))
        }
    }

    @Test fun networkAndCancellationStatusesAreNotReportedAsAdministratorBlocks() {
        assertEquals(CalendarError.NETWORK, CalendarError.from(ApiException(Status(CommonStatusCodes.NETWORK_ERROR))))
        assertEquals(CalendarError.CANCELLED, CalendarError.from(ApiException(Status(CommonStatusCodes.CANCELED))))
        assertEquals(CalendarError.CONFIGURATION, CalendarError.from(ApiException(Status(CommonStatusCodes.DEVELOPER_ERROR))))
    }

    @Test fun cancelledAccountPickerKeepsScheduleScreenUsable() {
        val pickerAction = checkNotNull(AccountPicker.newChooseAccountIntent(
            AccountPicker.AccountChooserOptions.Builder()
                .setAllowableAccountsTypes(listOf(GoogleCalendarHelper.ACCOUNT_TYPE)).build()
        ).action)
        Intents.init()
        try {
            intending(hasAction(pickerAction))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
            launch().use { scenario ->
                scenario.onActivity { activity -> GoogleCalendarHelper.forgetAccount(activity) }
                scenario.recreate()
                awaitFocus(scenario)
                onView(withId(R.id.btn_google_connect)).perform(click())
                intended(hasAction(pickerAction))
                onView(withText(R.string.google_calendar_continue_without))
                    .inRoot(isDialog()).perform(click())
                scenario.onActivity { assertFalse(it.isFinishing) }
                onView(withId(R.id.btn_google_connect)).check(matches(isEnabled()))
                onView(withId(R.id.fab_add)).perform(click())
                onView(withId(R.id.et_schedule_name))
                    .inRoot(isDialog()).check(matches(isDisplayed()))
            }
        } finally {
            Intents.release()
        }
    }
}
