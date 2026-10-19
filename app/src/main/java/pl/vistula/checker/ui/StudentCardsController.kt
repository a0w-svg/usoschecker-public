package pl.vistula.checker.ui

import android.graphics.Color
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import pl.vistula.checker.MainActivity
import pl.vistula.checker.R
import pl.vistula.checker.UsosClient
import pl.vistula.checker.core.StudentServices

class StudentCardsController private constructor() {
    companion object {
        @JvmStatic
        @Throws(Exception::class)
        fun render(activity: MainActivity, content: LinearLayout, state: JSONObject, services: JSONObject) {
            ToolScreenUi.header(activity, content, R.string.cards_title) { menu ->
                ToolScreenUi.action(menu, R.string.cards_refresh) { activity.refreshStudentServices("cards") }
                ToolScreenUi.action(menu, R.string.cards_all_methods) { activity.openApiModule("cards") }
                ToolScreenUi.action(menu, R.string.cards_information_only) {
                    MaterialAlertDialogBuilder(activity)
                        .setMessage(R.string.cards_information_only)
                        .setPositiveButton(R.string.btn_close, null)
                        .show()
                }
            }
            renderAccess(activity, content, state, "cards")
            val physical = entry(services, "cards")
            status(activity, content, physical)
            val cards = physical.optJSONArray("data")
            if (cards == null || cards.length() == 0) {
                UiHelper.empty(
                    activity,
                    content,
                    activity.getString(R.string.cards_empty),
                    activity.getString(if (physical.has("fetchedAt")) R.string.cards_none_returned else R.string.services_load_hint)
                )
            } else {
                for (card in StudentServices.cards(cards)) {
                    renderCard(activity, content, state, card)
                }
            }

            UiHelper.heading(activity, content, activity.getString(R.string.cards_mobile_title))
            val mobile = entry(services, "mcard")
            status(activity, content, mobile)
            val data = mobile.optJSONObject("data")
            if (data == null) {
                UiHelper.empty(activity, content, activity.getString(R.string.cards_mobile_empty), activity.getString(R.string.services_load_hint))
            } else {
                val box = UiHelper.section(activity, content, Color.WHITE)
                val latest = data.optJSONObject("latest_order")
                val orderReason = StudentServices.localized(data.opt("why_cannot_order"), Language.currentLocale(activity).language)
                val revokeReason = StudentServices.localized(data.opt("why_cannot_revoke"), Language.currentLocale(activity).language)
                if (latest == null) {
                    UiHelper.add(activity, box, activity.getString(R.string.cards_no_order), 18, UiHelper.INK, true)
                } else {
                    val status = StudentServices.text(latest, "status")
                    val id = StudentServices.text(latest, "id")
                    UiHelper.badge(activity, box, mobileStatus(activity, status), Color.rgb(214, 241, 229), UiHelper.GREEN)
                    line(activity, box, R.string.cards_order_date, HistoryScreenController.formatFullDate(StudentServices.text(latest, "order_date")))
                    line(activity, box, R.string.cards_expiration, HistoryScreenController.formatFullDate(StudentServices.text(latest, "expiration_date")))
                    if (status == "ready_to_receive" && id.isNotEmpty()) {
                        box.addView(
                            UiHelper.button(
                                activity,
                                activity.getString(R.string.cards_activation),
                                { activity.showMobileActivation(id) },
                                true
                            )
                        )
                    }
                    if (id.isNotEmpty() && data.optBoolean("revokeEligibilityKnown") && revokeReason.isEmpty() && !status.startsWith("revoked")) {
                        box.addView(
                            UiHelper.button(
                                activity,
                                activity.getString(R.string.cards_revoke),
                                { activity.mobileCardAction(false, id) },
                                false
                            )
                        )
                    }
                }
                if (orderReason.isNotEmpty()) {
                    UiHelper.note(activity, box, orderReason, UiHelper.MUTED)
                } else if (data.optBoolean("orderEligibilityKnown")) {
                    box.addView(
                        UiHelper.button(
                            activity,
                            activity.getString(R.string.cards_order),
                            { activity.mobileCardAction(true, "") },
                            false
                        )
                    )
                }
                if (revokeReason.isNotEmpty()) UiHelper.note(activity, box, revokeReason, UiHelper.MUTED)
                line(
                    activity,
                    box,
                    R.string.cards_expected_expiration,
                    HistoryScreenController.formatFullDate(StudentServices.text(data, "expected_expiration_date"))
                )
                UiHelper.note(activity, box, activity.getString(R.string.cards_activation_hint), UiHelper.MUTED)
                val orders = data.optJSONArray("orders")
                if (orders != null && orders.length() > 0) {
                    box.addView(
                        UiHelper.button(
                            activity,
                            activity.getString(R.string.cards_order_history, orders.length()),
                            { showOrders(activity, orders) },
                            false
                        )
                    )
                }
            }
        }

        private fun showOrders(activity: MainActivity, orders: JSONArray) {
            val body = LinearLayout(activity)
            body.orientation = LinearLayout.VERTICAL
            body.setPadding(UiHelper.dp(activity, 20), UiHelper.dp(activity, 8), UiHelper.dp(activity, 20), UiHelper.dp(activity, 8))
            for (i in 0 until orders.length()) {
                val order = orders.optJSONObject(i) ?: continue
                val card = UiHelper.section(activity, body, Color.WHITE)
                UiHelper.add(activity, card, mobileStatus(activity, StudentServices.text(order, "status")), 18, UiHelper.INK, true)
                line(activity, card, R.string.cards_order_date, HistoryScreenController.formatFullDate(StudentServices.text(order, "order_date")))
                line(activity, card, R.string.cards_expiration, HistoryScreenController.formatFullDate(StudentServices.text(order, "expiration_date")))
                line(activity, card, R.string.cards_order_id, StudentServices.text(order, "id"))
            }
            val scroll = UiHelper.dialogScroll(activity, body)
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.cards_order_history_title)
                .setView(scroll)
                .setPositiveButton(R.string.btn_close, null)
                .show()
        }

        private fun renderCard(activity: MainActivity, parent: LinearLayout, state: JSONObject, card: StudentServices.Card) {
            val today = LocalDate.now(UsosClient.ZONE)
            val box = UiHelper.section(activity, parent, UiHelper.INK)
            UiHelper.add(activity, box, activity.getString(R.string.cards_physical_title), 12, UiHelper.MUTED, true)
            UiHelper.add(activity, box, state.optString("user", activity.getString(R.string.cards_owner)), 23, UiHelper.INK, true)
            if (card.studentNumber.isNotEmpty()) {
                UiHelper.add(activity, box, activity.getString(R.string.cards_student_number, card.studentNumber), 16, UiHelper.INK, true)
            }
            val statusColor = if (card.expired(today)) UiHelper.foreground(activity, Color.rgb(160, 48, 40)) else UiHelper.MUTED
            val formattedExp = HistoryScreenController.formatFullDate(card.expires)
            val expiration = if (card.expires.isEmpty()) {
                activity.getString(R.string.cards_no_expiration)
            } else {
                activity.getString(R.string.cards_expiration, if (formattedExp.isEmpty()) card.expires else formattedExp)
            }
            UiHelper.add(activity, box, expiration, 16, statusColor, true)
            if (card.expired(today)) {
                UiHelper.note(activity, box, activity.getString(R.string.cards_expired), statusColor)
            } else if (card.expiresSoon(today)) {
                UiHelper.note(activity, box, activity.getString(R.string.cards_expires_soon), statusColor)
            }
            val details = UiHelper.disclosure(activity, box, "cards.physical.details", R.string.simple_details)
            if (card.issued.isNotEmpty()) {
                UiHelper.note(activity, details, activity.getString(R.string.cards_issue_date, HistoryScreenController.formatFullDate(card.issued)), UiHelper.MUTED)
            }
            if (card.type.isNotEmpty()) {
                UiHelper.note(activity, details, activity.getString(R.string.cards_type, card.type), UiHelper.MUTED)
            }
            if (card.barcode.isNotEmpty()) {
                details.addView(
                    UiHelper.button(
                        activity,
                        activity.getString(R.string.cards_show_number),
                        {
                            MaterialAlertDialogBuilder(activity)
                                .setTitle(R.string.cards_barcode_title)
                                .setMessage(card.barcode)
                                .setPositiveButton(R.string.btn_close, null)
                                .setNeutralButton(R.string.services_copy) { _, _ ->
                                    UiHelper.copyToClipboard(activity, activity.getString(R.string.cards_barcode_title), card.barcode)
                                }
                                .show()
                        },
                        false
                    )
                )
            }
        }

        @JvmStatic
        fun entry(services: JSONObject, key: String): JSONObject {
            return services.optJSONObject(key) ?: JSONObject()
        }

        @JvmStatic
        fun status(activity: MainActivity, content: LinearLayout, entry: JSONObject) {
            if (entry.optLong("fetchedAt") > 0) {
                UiHelper.note(
                    activity,
                    content,
                    activity.getString(R.string.services_last_fetch, UiHelper.timestamp(entry.optLong("fetchedAt"))),
                    UiHelper.MUTED
                )
            }
            if (entry.optString("error").isNotEmpty()) {
                if (entry.has("data")) UiHelper.note(activity, content, activity.getString(R.string.services_cached_note), UiHelper.MUTED)
                UiHelper.note(
                    activity,
                    content,
                    activity.getString(R.string.services_refresh_error, ApiErrorUi.cached(activity, entry)),
                    Color.rgb(160, 48, 40)
                )
            }
            if (entry.optLong("retryAt") > System.currentTimeMillis()) {
                UiHelper.note(activity, content, activity.getString(R.string.services_retry_at,
                    UiHelper.timestamp(entry.optLong("retryAt"))), UiHelper.MUTED)
            }
        }

        @JvmStatic
        fun renderAccess(activity: MainActivity, content: LinearLayout, state: JSONObject, scope: String) {
            if (activity.isDemo()) return
            if (!activity.connected() || !StudentServices.hasScope(state.optString("scopes"), scope)) {
                val box = UiHelper.section(activity, content, Color.WHITE)
                UiHelper.add(activity, box, activity.getString(R.string.services_scope_required, scope), 14, UiHelper.INK, false)
                box.addView(UiHelper.button(activity, activity.getString(R.string.nav_account), { activity.navigate(4) }, false))
            }
        }

        @JvmStatic
        fun line(activity: MainActivity, box: LinearLayout, label: Int, value: String) {
            if (value.isNotEmpty()) {
                UiHelper.add(activity, box, activity.getString(label, value), 14, UiHelper.MUTED, false)
            }
        }

        private fun mobileStatus(activity: MainActivity, status: String): String {
            val resource = when (status) {
                "pending" -> R.string.mcard_pending
                "in_progress" -> R.string.mcard_in_progress
                "ready_to_receive" -> R.string.mcard_ready
                "received" -> R.string.mcard_received
                "error" -> R.string.mcard_error
                "revoked_by_me" -> R.string.mcard_revoked_by_me
                "revoked" -> R.string.mcard_revoked
                else -> return if (status.isEmpty()) {
                    activity.getString(R.string.mcard_status_missing)
                } else {
                    activity.getString(R.string.mcard_unknown, status)
                }
            }
            return activity.getString(resource)
        }
    }
}

