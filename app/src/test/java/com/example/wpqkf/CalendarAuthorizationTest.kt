package com.example.wpqkf

import com.google.api.client.http.LowLevelHttpRequest
import com.google.api.client.testing.http.MockHttpTransport
import com.google.api.client.testing.http.MockLowLevelHttpRequest
import com.google.api.client.testing.http.MockLowLevelHttpResponse
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Calendar

class CalendarAuthorizationTest {
    private fun transport(status: Int, json: String) = MockHttpTransport.Builder()
        .setLowLevelHttpResponse(MockLowLevelHttpResponse().setStatusCode(status)
            .setContentType("application/json; charset=UTF-8").setContent(json)).build()

    @Test fun organizationPolicyIsDistinctFromDisabledApi() {
        assertEquals(CalendarError.ADMIN_POLICY, CalendarError.from(Exception("admin_policy_enforced")))
        assertEquals(CalendarError.ADMIN_POLICY, CalendarError.from(Exception("access_not_configured")))
        assertEquals(CalendarError.CONFIGURATION, CalendarError.from(Exception("accessNotConfigured")))
        assertEquals(CalendarError.CONFIGURATION, CalendarError.from(Exception("SERVICE_DISABLED")))
        assertEquals(CalendarError.CONFIGURATION, CalendarError.from(Exception("org_internal")))
    }

    @Test fun networkAndUnknownErrorsDoNotImplyAdminPolicy() {
        assertEquals(CalendarError.NETWORK, CalendarError.from(SocketTimeoutException()))
        assertEquals(CalendarError.NETWORK, CalendarError.from(IOException()))
        assertEquals(CalendarError.UNKNOWN, CalendarError.from(Exception("unknown failure")))
        assertEquals(CalendarError.PERMISSION, CalendarError.from(Exception("access_denied")))
    }

    @Test fun createsEventOnPrimaryCalendarWithChosenTimeAndSafeHeaders() {
        val transport = transport(200, """{"id":"abc123","summary":"School"}""")
        val start = Calendar.getInstance().apply { set(2026, 9, 23, 14, 0, 0) }
        val end = (start.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, 1) }
        val result = GoogleCalendarHelper.createCalendarEvent("test-token", "School", "Study", start, end,
            transport = transport, eventId = "abc123")
        assertEquals("abc123", result.getOrThrow().id)
        assertTrue(transport.lowLevelHttpRequest.url.contains("/calendars/primary/events"))
        val body = transport.lowLevelHttpRequest.contentAsString
        assertTrue(body.contains("School"))
        assertTrue(body.contains("abc123"))
        val request = GoogleCalendarHelper.service("test-token", transport).events().list("primary").buildHttpRequest()
        assertEquals("Bearer test-token", request.headers.authorization)
        assertFalse(request.isLoggingEnabled)
        assertFalse(request.isCurlLoggingEnabled)
    }

    @Test fun listsUpcomingPrimaryEventsWithBoundedResults() {
        val transport = transport(200, """{"items":[{"id":"abc123","summary":"School"}]}""")
        val result = GoogleCalendarHelper.listUpcomingEvents("test-token", transport).getOrThrow()
        assertEquals("School", result.single().summary)
        val url = transport.lowLevelHttpRequest.url
        assertTrue(url.contains("/calendars/primary/events"))
        assertTrue(url.contains("maxResults=10"))
        assertTrue(url.contains("orderBy=startTime"))
        assertTrue(url.contains("timeMin="))
    }

    @Test fun rejectedApiRequestsAreReturnedWithoutCrashing() {
        val policy = GoogleCalendarHelper.listUpcomingEvents("test-token", transport(403,
            """{"error":{"code":403,"message":"Blocked","errors":[{"reason":"domainPolicy","message":"Blocked"}]}}"""))
        assertTrue(policy.isFailure)
        assertEquals(CalendarError.ADMIN_POLICY, CalendarError.from(policy.exceptionOrNull()!!))
        val permission = GoogleCalendarHelper.listUpcomingEvents("test-token", transport(403,
            """{"error":{"code":403,"message":"Forbidden","errors":[{"reason":"insufficientPermissions"}]}}"""))
        assertEquals(CalendarError.PERMISSION, CalendarError.from(permission.exceptionOrNull()!!))
        val disabled = GoogleCalendarHelper.listUpcomingEvents("test-token", transport(403,
            """{"error":{"code":403,"message":"Disabled","errors":[{"reason":"accessNotConfigured"}]}}"""))
        assertEquals(CalendarError.CONFIGURATION, CalendarError.from(disabled.exceptionOrNull()!!))
    }

    @Test fun expiredTokenIsReportedAsPermissionFailure() {
        val result = GoogleCalendarHelper.listUpcomingEvents("expired-test-token", transport(401,
            """{"error":{"code":401,"message":"Invalid Credentials"}}"""))
        assertTrue(result.isFailure)
        assertEquals(CalendarError.PERMISSION, CalendarError.from(result.exceptionOrNull()!!))
    }

    @Test fun retryWithSameEventIdRecoversAnAlreadyInsertedEvent() {
        var calls = 0
        val transport = object : MockHttpTransport() {
            override fun buildRequest(method: String, url: String): LowLevelHttpRequest {
                calls++
                val response = if (method == "POST") {
                    MockLowLevelHttpResponse().setStatusCode(409)
                        .setContent("""{"error":{"code":409,"message":"Already exists"}}""")
                } else {
                    assertTrue(url.endsWith("/events/abc123"))
                    MockLowLevelHttpResponse().setStatusCode(200).setContent("""{"id":"abc123"}""")
                }
                return MockLowLevelHttpRequest(url).setResponse(response.setContentType("application/json"))
            }
        }
        val start = Calendar.getInstance()
        val result = GoogleCalendarHelper.createCalendarEvent("test-token", "School", "", start, start,
            transport = transport, eventId = "abc123")
        assertEquals("abc123", result.getOrThrow().id)
        assertEquals(2, calls)
    }
}
