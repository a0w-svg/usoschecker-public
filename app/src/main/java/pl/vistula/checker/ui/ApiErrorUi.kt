package pl.vistula.checker.ui

import android.content.Context
import java.io.IOException
import java.net.SocketTimeoutException
import org.json.JSONException
import org.json.JSONObject
import pl.vistula.checker.R
import pl.vistula.checker.SharedRepository
import pl.vistula.checker.UsosClient

/** Translate errors at display time so cached failures follow the selected app language. */
object ApiErrorUi {
    fun message(context: Context, error: Exception): String {
        val cause = SharedRepository.cause(error)
        val explicit = listOf(R.string.service_action_uncertain, R.string.service_action_unavailable,
            R.string.services_connect_first, R.string.error_rate_limit).map { context.getString(it) }
        if (cause.message in explicit) return cause.message!!
        val label = when (cause) {
            is pl.vistula.checker.MobileCardActions.Uncertain -> R.string.service_action_uncertain
            is pl.vistula.checker.MobileCardActions.ConfirmedRefreshFailed -> R.string.cards_confirmed_refresh_failed
            is UsosClient.ApiException -> http(cause.status, cause.authorization)
            is SocketTimeoutException -> R.string.error_timeout
            is JSONException -> R.string.error_format
            is IOException -> R.string.error_network
            else -> R.string.error_operation
        }
        return context.getString(label)
    }

    fun cached(context: Context, entry: JSONObject): String = context.getString(when (entry.optString("errorKind")) {
        "timeout" -> R.string.error_timeout
        "format" -> R.string.error_format
        "network" -> R.string.error_network
        "http" -> http(entry.optInt("httpStatus"), entry.optBoolean("authorizationError"))
        else -> if (entry.optBoolean("authorizationError")) R.string.account_session_expired else R.string.error_operation
    })

    private fun http(status: Int, authorization: Boolean): Int = when {
        authorization -> R.string.account_session_expired
        status == 401 -> R.string.error_signature
        status == 403 -> R.string.error_access
        status == 429 -> R.string.error_rate_limit
        status == 408 -> R.string.error_timeout
        status >= 500 -> R.string.error_server
        else -> R.string.error_operation
    }
}
