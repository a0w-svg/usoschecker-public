package pl.vistula.checker.ui

import android.graphics.Color
import android.widget.LinearLayout
import org.json.JSONObject
import pl.vistula.checker.MainActivity
import pl.vistula.checker.R
import pl.vistula.checker.core.StudentServices

class StudentCardsController private constructor() {
    companion object {
        @JvmStatic
        @Throws(Exception::class)
        fun render(activity: MainActivity, content: LinearLayout, state: JSONObject, services: JSONObject) {
            ToolScreenUi.header(activity, content, R.string.cards_title) { menu ->
                ToolScreenUi.action(menu, R.string.cards_refresh) { activity.refreshStudentServices("cards") }
            }
            renderAccess(activity, content, state, "cards")
            UiHelper.heading(activity, content, activity.getString(R.string.cards_physical_title))
            val physical = entry(services, "physicalCards")
            status(activity, content, physical)
            val cards = physical.optJSONArray("data")
            if (cards == null || cards.length() == 0) {
                UiHelper.note(activity, content, activity.getString(if (cards == null) R.string.services_load_hint else R.string.cards_physical_empty), UiHelper.MUTED)
            } else {
                for (i in 0 until cards.length()) {
                    val card = StudentServices.Card(cards.optJSONObject(i))
                    val cardBox = UiHelper.section(activity, content, Color.WHITE)
                    line(activity, cardBox, R.string.cards_type, card.type)
                    line(activity, cardBox, R.string.cards_student_number, card.studentNumber)
                    line(activity, cardBox, R.string.cards_issue_date, HistoryScreenController.formatFullDate(card.issued))
                    line(activity, cardBox, R.string.cards_expiration, HistoryScreenController.formatFullDate(card.expires))
                }
            }
            UiHelper.heading(activity, content, activity.getString(R.string.cards_mobile_title))
            UiHelper.note(activity, content, activity.getString(R.string.cards_usage_guide), UiHelper.MUTED)
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
                    if (id.isNotEmpty() && pl.vistula.checker.MobileCardActions.ready(status)) {
                        box.addView(UiHelper.button(activity, activity.getString(R.string.cards_activation),
                            { activity.showMobileActivation(id) }, true))
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
                box.addView(UiHelper.button(activity, activity.getString(R.string.nav_account), { activity.navigate(Tabs.ACCOUNT) }, false))
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
                "ready", "ready_to_receive" -> R.string.mcard_ready
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

