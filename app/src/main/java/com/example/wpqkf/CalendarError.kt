package com.example.wpqkf

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpResponseException
import java.io.IOException
import java.util.Locale

enum class CalendarError(val messageRes: Int) {
    ADMIN_POLICY(R.string.google_calendar_admin_policy),
    CONFIGURATION(R.string.google_calendar_configuration_error),
    NETWORK(R.string.google_calendar_network_error),
    PERMISSION(R.string.google_calendar_permission_error),
    CANCELLED(R.string.google_calendar_cancelled),
    UNKNOWN(R.string.google_calendar_unknown_error);

    companion object {
        fun from(error: Throwable): CalendarError {
            val causes = generateSequence(error) { it.cause }.take(8).toList()
            // Inspect only for classification. Never show or log raw OAuth/HTTP payloads.
            val detail = causes.joinToString(" ") { cause ->
                if (cause is GoogleJsonResponseException) {
                    cause.details?.errors.orEmpty().joinToString(" ") { "${it.reason} ${it.message}" } +
                        " " + cause.details?.message.orEmpty()
                } else cause.message.orEmpty()
            }.lowercase(Locale.ROOT)
            if (listOf("admin_policy_enforced", "access_not_configured", "domainpolicy",
                    "administrator", "조직의 관리자", "조직의 보안 정책").any { it in detail }) {
                return ADMIN_POLICY
            }
            // accessNotConfigured (API disabled) differs from OAuth access_not_configured.
            if (listOf("accessnotconfigured", "service_disabled", "servicedisabled", "api_disabled",
                    "invalid_client", "deleted_client", "redirect_uri_mismatch", "org_internal").any { it in detail }) {
                return CONFIGURATION
            }
            val api = causes.filterIsInstance<ApiException>().firstOrNull()
            if (api?.statusCode == CommonStatusCodes.DEVELOPER_ERROR) return CONFIGURATION
            if (api?.statusCode == CommonStatusCodes.NETWORK_ERROR) return NETWORK
            if (api?.statusCode == CommonStatusCodes.CANCELED || api?.statusCode == 12501) return CANCELLED
            val http = causes.filterIsInstance<HttpResponseException>().firstOrNull()
            if (http != null) {
                return if (http.statusCode == 401 || http.statusCode == 403) PERMISSION else UNKNOWN
            }
            if ("access_denied" in detail || "insufficientpermissions" in detail ||
                "insufficient_scope" in detail) return PERMISSION
            return if (causes.any { it is IOException }) NETWORK else UNKNOWN
        }
    }
}
