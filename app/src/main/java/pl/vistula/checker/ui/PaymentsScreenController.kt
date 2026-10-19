package pl.vistula.checker.ui

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import kotlin.math.min
import org.json.JSONException
import org.json.JSONObject
import pl.vistula.checker.MainActivity
import pl.vistula.checker.R
import pl.vistula.checker.core.Snapshot
import pl.vistula.checker.core.StudentServices

/**
 * Controller for rendering the Payments, Subaccount and Installments screen.
 */
class PaymentsScreenController private constructor() {

    companion object {
        @JvmStatic
        @Throws(JSONException::class)
        fun render(
            activity: MainActivity,
            content: LinearLayout,
            d: JSONObject,
            state: JSONObject,
            connected: Boolean,
            onRefresh: Runnable
        ) {
            ToolScreenUi.header(activity, content, R.string.tool_payments_title) { menu ->
                ToolScreenUi.action(menu, R.string.payments_refresh_btn, onRefresh)
                ToolScreenUi.action(menu, R.string.tool_api_methods) { activity.openApiModule("payments") }
            }
            UiHelper.note(
                activity,
                content,
                activity.getString(R.string.payments_data_freshness, UiHelper.timestamp(d.optLong("successAt"))),
                UiHelper.MUTED
            )

            val raw = d.optJSONObject("snapshot")
            if (raw == null) {
                UiHelper.empty(
                    activity,
                    content,
                    activity.getString(R.string.payments_no_data_title),
                    activity.getString(R.string.payments_no_data_desc)
                )
                content.addView(
                    UiHelper.button(
                        activity,
                        if (connected) activity.getString(R.string.btn_refresh) else activity.getString(R.string.btn_connect),
                        { if (connected) onRefresh.run() else activity.navigate(4) },
                        true
                    )
                )
                return
            }

            val snapshot = Snapshot.from(raw)
            val payments = snapshot.payments

            val authorizedScopes = state.optString("scopes", "")
            val hasPaymentsScope = StudentServices.hasScope(authorizedScopes, "payments")
            val scopeMissing = (payments != null && payments.scopeMissing) ||
                (connected && authorizedScopes.isNotEmpty() && !hasPaymentsScope)

            if (scopeMissing) {
                val warnBox = UiHelper.section(activity, content, Color.rgb(255, 248, 230))
                UiHelper.add(activity, warnBox, activity.getString(R.string.payments_missing_scope_badge), 11, Color.rgb(180, 110, 0), true)
                UiHelper.add(activity, warnBox, activity.getString(R.string.payments_missing_scope_title), 17, UiHelper.INK, true)
                UiHelper.note(
                    activity,
                    warnBox,
                    activity.getString(R.string.payments_missing_scope_desc, if (authorizedScopes.isEmpty()) "brak" else authorizedScopes),
                    UiHelper.INK
                )
                UiHelper.note(activity, warnBox, activity.getString(R.string.payments_missing_scope_action_note), UiHelper.MUTED)
                warnBox.addView(
                    UiHelper.button(
                        activity,
                        activity.getString(R.string.payments_missing_scope_btn),
                        { activity.navigate(4) },
                        true
                    )
                )
            }

            if (payments == null || (payments.accounts.isEmpty() && payments.installments.isEmpty())) {
                if (!scopeMissing) {
                    UiHelper.empty(
                        activity,
                        content,
                        activity.getString(R.string.payments_empty_title),
                        activity.getString(R.string.payments_empty_desc)
                    )
                }
                return
            }

            if (payments.accounts.isNotEmpty()) {
                UiHelper.heading(activity, content, activity.getString(R.string.payments_accounts_heading))
                for (acc in payments.accounts) {
                    val box = UiHelper.section(activity, content, Color.WHITE)
                    UiHelper.add(
                        activity,
                        box,
                        if (acc.name.isEmpty()) activity.getString(R.string.payments_account_default_name) else acc.name,
                        18,
                        UiHelper.INK,
                        true
                    )
                    if (acc.faculty.isNotEmpty()) {
                        UiHelper.add(activity, box, activity.getString(R.string.payments_faculty, acc.faculty), 13, UiHelper.MUTED, false)
                    }
                    if (acc.accountOwner.isNotEmpty()) {
                        UiHelper.add(activity, box, activity.getString(R.string.payments_owner, acc.accountOwner), 13, UiHelper.MUTED, false)
                    }
                    UiHelper.add(activity, box, formatIban(acc.bankAccountNumber), 16, UiHelper.GREEN, true)
                    if (acc.swift.isNotEmpty()) {
                        UiHelper.add(activity, box, activity.getString(R.string.payments_swift, acc.swift), 12, UiHelper.MUTED, false)
                    }
                    if (acc.balance != 0.0) {
                        UiHelper.add(activity, box, activity.getString(R.string.payments_balance, acc.balance, acc.currency), 13, UiHelper.MUTED, false)
                    }
                    box.addView(
                        UiHelper.button(
                            activity,
                            activity.getString(R.string.payments_copy_iban),
                            {
                                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                                if (clipboard != null) {
                                    clipboard.setPrimaryClip(UiHelper.privateClip("Subkonto USOS", acc.bankAccountNumber.replace(Regex("\\s+"), "")))
                                    Toast.makeText(activity, activity.getString(R.string.payments_iban_copied), Toast.LENGTH_SHORT).show()
                                }
                            },
                            false
                        )
                    )
                }
            }

            if (payments.installments.isNotEmpty()) {
                UiHelper.heading(activity, content, activity.getString(R.string.payments_schedule_heading))
                val totalUnpaid = payments.totalUnpaidAmount()
                val summaryBox = UiHelper.section(
                    activity,
                    content,
                    if (payments.hasOverdue()) Color.rgb(255, 240, 240) else Color.rgb(238, 245, 242)
                )
                UiHelper.add(
                    activity,
                    summaryBox,
                    activity.getString(R.string.payments_status_settlement),
                    11,
                    if (payments.hasOverdue()) Color.rgb(160, 48, 40) else UiHelper.GREEN,
                    true
                )
                UiHelper.add(activity, summaryBox, activity.getString(R.string.payments_total_due, totalUnpaid), 22, UiHelper.INK, true)
                UiHelper.note(
                    activity,
                    summaryBox,
                    when {
                        payments.hasOverdue() -> activity.getString(R.string.payments_status_overdue_warn)
                        totalUnpaid == 0.0 -> activity.getString(R.string.payments_status_all_paid)
                        else -> activity.getString(R.string.payments_status_pending)
                    },
                    if (payments.hasOverdue()) Color.rgb(160, 48, 40) else UiHelper.MUTED
                )

                val inflater = LayoutInflater.from(activity)
                for (inst in payments.installments) {
                    val instCard = inflater.inflate(R.layout.item_payment_card, content, false)
                    val title = instCard.findViewById<TextView>(R.id.payment_item_title)
                    val status = instCard.findViewById<TextView>(R.id.payment_item_status)
                    val amount = instCard.findViewById<TextView>(R.id.payment_item_amount)
                    val deadline = instCard.findViewById<TextView>(R.id.payment_item_deadline)
                    val paidDate = instCard.findViewById<TextView>(R.id.payment_item_paid_date)
                    val account = instCard.findViewById<TextView>(R.id.payment_item_account)
                    val btnCopy = instCard.findViewById<MaterialButton>(R.id.payment_item_btn_copy)

                    title.text = inst.name
                    val paid = inst.isPaid()
                    val overdue = inst.isOverdue()
                    val statusLabel = when {
                        paid -> activity.getString(R.string.payment_status_paid)
                        overdue -> activity.getString(R.string.payment_status_overdue)
                        inst.status.isEmpty() -> activity.getString(R.string.payment_status_to_pay)
                        else -> inst.status
                    }
                    val statusColor = when {
                        paid -> UiHelper.GREEN
                        overdue -> Color.rgb(160, 48, 40)
                        else -> Color.rgb(180, 110, 0)
                    }

                    status.text = statusLabel
                    status.setTextColor(UiHelper.foreground(activity, statusColor))
                    val badgeBg = GradientDrawable()
                    badgeBg.setColor(
                        UiHelper.background(
                            activity,
                            when {
                                paid -> Color.rgb(232, 243, 236)
                                overdue -> Color.rgb(255, 235, 235)
                                else -> Color.rgb(255, 248, 230)
                            }
                        )
                    )
                    badgeBg.cornerRadius = UiHelper.dp(activity, 8).toFloat()
                    status.background = badgeBg

                    amount.text = activity.getString(R.string.payments_amount, inst.amount, inst.currency)

                    if (inst.deadline.isNotEmpty()) {
                        deadline.visibility = View.VISIBLE
                        deadline.text = activity.getString(R.string.payments_deadline, HistoryScreenController.formatFullDate(inst.deadline))
                        if (overdue) deadline.setTextColor(UiHelper.foreground(activity, Color.rgb(160, 48, 40)))
                    }

                    if (inst.paymentDate.isNotEmpty()) {
                        paidDate.visibility = View.VISIBLE
                        paidDate.text = activity.getString(R.string.payments_date_paid, HistoryScreenController.formatFullDate(inst.paymentDate))
                    }

                    if (inst.bankAccountNumber.isNotEmpty()) {
                        account.visibility = View.VISIBLE
                        account.text = activity.getString(R.string.payments_account_for_installment, formatIban(inst.bankAccountNumber))
                        btnCopy.visibility = View.VISIBLE
                        val copyAction = Runnable {
                            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                            if (clipboard != null) {
                                clipboard.setPrimaryClip(
                                    UiHelper.privateClip(
                                        activity.getString(R.string.payment_clipboard_label),
                                        inst.bankAccountNumber.replace(Regex("\\s+"), "")
                                    )
                                )
                                Toast.makeText(activity, activity.getString(R.string.payments_account_copied), Toast.LENGTH_SHORT).show()
                            }
                        }
                        btnCopy.setOnClickListener { copyAction.run() }
                        instCard.isClickable = true
                        instCard.isFocusable = true
                        instCard.setOnClickListener { copyAction.run() }
                    }

                    content.addView(instCard)
                }
            }
        }

        @JvmStatic
        fun formatIban(raw: String?): String {
            if (raw == null) return ""
            val clean = raw.replace(Regex("\\s+"), "")
            if (clean.length < 10) return raw
            val sb = StringBuilder()
            var offset = 0
            if (clean.length > 2 && clean[0].isLetter() && clean[1].isLetter()) {
                sb.append(clean, 0, 2)
                offset = 2
                if (clean.length >= 4) {
                    sb.append(" ").append(clean, 2, 4)
                    offset = 4
                }
            } else if (clean.length == 26) {
                sb.append(clean, 0, 2)
                offset = 2
            }
            var i = offset
            while (i < clean.length) {
                if (sb.isNotEmpty()) sb.append(" ")
                sb.append(clean.substring(i, min(i + 4, clean.length)))
                i += 4
            }
            return sb.toString()
        }
    }
}
