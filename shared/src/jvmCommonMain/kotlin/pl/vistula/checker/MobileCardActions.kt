package pl.vistula.checker

import org.json.JSONObject
import pl.vistula.checker.core.StudentServices

/** Activation data stays transient; issue/revoke rechecks eligibility before a single POST. */
object MobileCardActions {
    class Uncertain(cause: Exception) : java.io.IOException("Card action result unknown", cause)
    class ConfirmedRefreshFailed(cause: Exception) : java.io.IOException("Card action confirmed but refresh failed", cause)
    fun ready(status: String): Boolean = status == "ready_to_receive" || status == "ready"

    fun activation(client: UsosClient, id: String): JSONObject {
        require(id.isNotBlank())
        val result = JSONObject(client.get("services/cards/mcard_order2", mapOf(
            "order_id" to id, "fields" to "id|status|token|distribution_pass"
        )))
        check(StudentServices.text(result, "id") == id && ready(StudentServices.text(result, "status")) &&
            StudentServices.text(result, "token").isNotBlank() && StudentServices.text(result, "distribution_pass").isNotBlank()) {
            "Card activation unavailable"
        }
        return result
    }

    fun perform(client: UsosClient, state: JSONObject, order: Boolean, id: String, checkAccount: () -> Unit = {}) {
        check(state.optString("token").isNotBlank() && state.optString("token") != "demo" &&
            !state.optBoolean("authorizationError") && state.optLong("retryAt") <= System.currentTimeMillis() &&
            StudentServices.hasScope(state.optString("scopes"), "cards")) { "Card authorization unavailable" }
        if (!order) require(id.isNotBlank())
        val current = JSONObject(client.get("services/cards/mcard2", mapOf(
            "fields" to "why_cannot_order|why_cannot_revoke|latest_order[id|status]"
        )))
        val field = if (order) "why_cannot_order" else "why_cannot_revoke"
        check(StudentServices.validEligibility(current, field)) { "Card eligibility unavailable" }
        // Null and an empty language dictionary both mean no rejection; malformed values fail closed.
        val reason = current.opt(field)
        check(StudentServices.localized(reason).isBlank()) { "Card action unavailable" }
        if (!order) {
            val latest = current.optJSONObject("latest_order")
            check(latest != null && StudentServices.text(latest, "id") == id &&
                !StudentServices.text(latest, "status").startsWith("revoked")) { "Card order changed" }
        }
        checkAccount()
        val response = try { JSONObject(client.request("POST",
            if (order) "services/cards/make_mcard_order" else "services/cards/revoke_mcard",
            if (order) emptyMap() else mapOf("order_id" to id), true,
            state.optString("token"), state.optString("tokenSecret")))
        } catch (e: UsosClient.ApiException) { if (e.status == 408 || e.status >= 500) throw Uncertain(e) else throw e
        } catch (e: Exception) { throw Uncertain(e) }
        when (response.opt("success")) {
            true -> Unit
            false -> error("Card action rejected")
            else -> throw Uncertain(IllegalStateException("Unexpected card response"))
        }
    }
}
