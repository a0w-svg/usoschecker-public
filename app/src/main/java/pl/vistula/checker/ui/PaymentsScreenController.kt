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
import pl.vistula.checker.DemoStudentServices
import pl.vistula.checker.ToolServiceData
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import pl.vistula.checker.core.PaymentAmounts
import pl.vistula.checker.core.PaymentInfo
import pl.vistula.checker.core.Snapshot
import pl.vistula.checker.core.StudentServices

/**
 * Controller for rendering the Payments, Subaccount and Installments screen.
 */
class PaymentsScreenController private constructor() {

    companion object {
        @JvmStatic
        @JvmOverloads
        @Throws(JSONException::class)
        fun render(
            activity: MainActivity,
            content: LinearLayout,
            d: JSONObject,
            state: JSONObject,
            connected: Boolean,
            servicesOverride: JSONObject? = null,
            onRefresh: Runnable
        ) {
            val services = servicesOverride ?: state.optJSONObject("studentServices") ?: JSONObject()
            ToolScreenUi.header(activity, content, R.string.tool_payments_title) { menu ->
                ToolScreenUi.action(menu, R.string.payments_refresh_btn, onRefresh)
                ToolScreenUi.action(menu, R.string.payments_history_refresh) { activity.refreshStudentServices("remittances") }
                ToolScreenUi.action(menu, R.string.tool_api_methods) { activity.openApiModule("payments") }
            }
            UiHelper.note(activity, content, activity.getString(R.string.payments_usage_guide), UiHelper.MUTED)
            val entry = services.optJSONObject("payments") ?: JSONObject()
            val language = pl.vistula.checker.core.ApiLanguage.normalize(Language.currentLocale(activity).language)
            val cached = entry.optJSONObject("data")?.optJSONObject(language)
            val raw = d.optJSONObject("snapshot")
            val useCache = cached != null && (raw == null || entry.optLong("fetchedAt") >= d.optLong("successAt"))
            val payments = if (useCache) PaymentInfo.from(cached) else raw?.let { Snapshot.from(it).payments }
            if (useCache || entry.optLong("attemptAt") > d.optLong("successAt")) {
                StudentCardsController.status(activity, content, entry)
            } else if (raw != null) {
                UiHelper.note(activity, content, activity.getString(R.string.payments_data_freshness,
                    UiHelper.timestamp(d.optLong("successAt"))), UiHelper.MUTED)
            }
            if (payments == null) {
                renderRemittances(activity, content, services.optJSONObject("remittances") ?: JSONObject())
                UiHelper.empty(activity, content, activity.getString(R.string.payments_no_data_title),
                    activity.getString(R.string.services_not_loaded))
                content.addView(UiHelper.button(activity,
                    activity.getString(if (connected) R.string.btn_refresh else R.string.btn_connect),
                    { if (connected) onRefresh.run() else activity.navigate(4) }, true))
                return
            }

            val authorizedScopes = state.optString("scopes", "")
            val hasPaymentsScope = StudentServices.hasScope(authorizedScopes, "payments")
            val scopeMissing = payments.scopeMissing ||
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

            renderDetails(activity, content, entry.optJSONObject("data"))
            renderRemittances(activity, content, services.optJSONObject("remittances") ?: JSONObject())
            if (payments.accounts.isEmpty() && payments.installments.isEmpty()) {
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
                val accounts = UiHelper.disclosure(activity, content, "payments.accounts", R.string.payments_accounts_heading)
                UiHelper.note(activity, accounts, activity.getString(R.string.payments_accounts_information), UiHelper.MUTED)
                for (acc in payments.accounts) {
                    val box = UiHelper.section(activity, accounts, Color.WHITE)
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
                val amounts = payments.installments.filter { it.isUnpaid() && it.amount > 0 }.groupBy { it.currency }
                    .mapValues { (_, entries) -> entries.sumOf { it.amount } }
                val openAmounts = payments.installments.filter { !it.isPaid() && !it.isCancelled() && it.amount > 0 }.groupBy { it.currency }
                    .mapValues { (_, entries) -> entries.sumOf { it.amount } }
                val displayAmounts = amounts.ifEmpty { openAmounts }
                val summaryBox = UiHelper.section(
                    activity,
                    content,
                    if (payments.hasOverdue()) Color.rgb(255, 240, 240) else Color.rgb(238, 245, 242)
                )
                UiHelper.note(activity, summaryBox, activity.getString(R.string.payments_installment_total_label), UiHelper.MUTED)
                if (payments.hasUnknownSettlement() && payments.hasOverdue()) UiHelper.note(activity, summaryBox,
                    activity.getString(R.string.payments_status_overdue_warn), Color.rgb(160, 48, 40))
                if (displayAmounts.isNotEmpty()) {
                    for ((currency, amount) in displayAmounts) {
                        UiHelper.add(activity, summaryBox, PaymentAmounts.format(amount, currency, language), 22, UiHelper.INK, true)
                    }
                } else {
                    val defaultCurrency = payments.installments.firstOrNull()?.currency
                        ?: payments.accounts.firstOrNull()?.currency
                        ?: "PLN"
                    UiHelper.add(activity, summaryBox, PaymentAmounts.format(0, defaultCurrency, language), 22, UiHelper.INK, true)
                }
                UiHelper.note(
                    activity,
                    summaryBox,
                    when {
                        payments.hasUnknownSettlement() -> activity.getString(R.string.payments_settlement_unknown)
                        payments.hasOverdue() -> activity.getString(R.string.payments_status_overdue_warn)
                        amounts.isEmpty() -> activity.getString(R.string.payments_status_none_due)
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
                        inst.status == "settlement_unknown" -> activity.getString(R.string.payment_status_unknown)
                        inst.isSettlementUnknown() || inst.status.lowercase(java.util.Locale.ROOT) in setOf("scheduled", "zaplanowana", "заплановано") -> activity.getString(R.string.payment_status_scheduled)
                        inst.status.lowercase(java.util.Locale.ROOT) in setOf("cancelled", "anulowana", "скасовано") -> activity.getString(R.string.payment_status_cancelled)
                        inst.status.lowercase(java.util.Locale.ROOT) in setOf("underpaid", "niedopłata", "недоплата") -> activity.getString(R.string.payment_status_underpaid)
                        inst.status.isEmpty() || inst.status.lowercase(java.util.Locale.ROOT) in setOf("do zapłaty", "due", "unsettled", "pending", "до сплати") -> activity.getString(R.string.payment_status_to_pay)
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

        private fun renderDetails(activity: MainActivity, content: LinearLayout, data: JSONObject?) {
            val language = Language.currentLocale(activity).language
            val items = data?.optJSONArray("details") ?: return
            if (items.length() == 0) return
            val body = UiHelper.disclosure(activity, content, "payments.details", R.string.payments_details)
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val currency = PaymentAmounts.currencyCode(item.opt("currency"))
                val box = UiHelper.section(activity, body, Color.WHITE)
                UiHelper.heading(activity, box, StudentServices.localized(item.opt("description"), language))
                for ((key, label) in listOf("total_amount" to R.string.payments_api_amount, "saldo_amount" to R.string.payments_balance_value,
                    "interest" to R.string.payments_interest_value)) {
                    if (!item.isNull(key) && item.has(key)) {
                        val formatted = PaymentAmounts.format(item.opt(key), currency, language)
                        UiHelper.note(activity, box, activity.getString(label, formatted, "").trim(), UiHelper.MUTED)
                    }
                }
                if (item.optBoolean("has_bonus")) {
                    val formattedBonus = PaymentAmounts.format(item.opt("bonus_amount"), currency, language)
                    UiHelper.note(activity, box, activity.getString(R.string.payments_bonus_value,
                        formattedBonus, "",
                        HistoryScreenController.formatFullDate(StudentServices.text(item, "bonus_deadline"))).replace("  ,", ","), UiHelper.MUTED)
                }
                if (StudentServices.text(item, "default_choice_date").isNotEmpty()) {
                    UiHelper.note(activity, box, activity.getString(R.string.payments_default_choice,
                        HistoryScreenController.formatFullDate(StudentServices.text(item, "default_choice_date"))), UiHelper.MUTED)
                }
                val plans = item.optJSONArray("plans") ?: continue
                for (j in 0 until plans.length()) {
                    val plan = plans.getJSONObject(j)
                    val rows = plan.getJSONArray("installments")
                    val description = (0 until rows.length()).joinToString("\n") { n ->
                        val row = rows.getJSONObject(n)
                        activity.getString(R.string.payments_plan_line,
                            HistoryScreenController.formatFullDate(StudentServices.text(row, "payment_date")),
                            PaymentAmounts.format(row.opt("amount"),
                                PaymentAmounts.currency(plan, item), language))
                    }
                    UiHelper.add(activity, box, activity.getString(R.string.payments_plan_label, plan.optString("id")), 16, UiHelper.INK, true)
                    UiHelper.note(activity, box, description, UiHelper.MUTED)
                    if (item.optString("chosenPlan") == plan.optString("id")) {
                        UiHelper.note(activity, box, activity.getString(R.string.payments_selected_plan), UiHelper.MUTED)
                    }
                    if (StudentServices.text(item, "id").isNotEmpty() && StudentServices.text(item, "type").isNotEmpty() &&
                        StudentServices.text(plan, "id").isNotEmpty() && item.optString("chosenPlan") != plan.optString("id")) {
                        box.addView(UiHelper.button(activity, activity.getString(R.string.payments_plan_choose), {
                            StudentActionsController.choosePlan(activity, item, plan, description)
                        }, false))
                    }
                }
            }
        }

        private fun renderRemittances(activity: MainActivity, content: LinearLayout, entry: JSONObject) {
            val language = Language.currentLocale(activity).language
            val body = UiHelper.disclosure(activity, content, "payments.remittances", R.string.payments_history)
            StudentCardsController.status(activity, body, entry)
            body.addView(UiHelper.button(activity, activity.getString(R.string.payments_history_refresh), {
                activity.refreshStudentServices("remittances")
            }, false))
            val items = entry.optJSONObject("data")?.optJSONArray("items")
            if (items == null || items.length() == 0) {
                UiHelper.note(activity, body, activity.getString(if (items == null) R.string.services_not_loaded else R.string.payments_empty_title), UiHelper.MUTED)
                return
            }
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val currency = PaymentAmounts.currencyCode(item.opt("currency"))
                val box = UiHelper.section(activity, body, Color.WHITE)
                val remittanceId = StudentServices.text(item, "id")
                if (remittanceId.isNotEmpty()) {
                    box.isClickable = true
                    box.isFocusable = true
                    box.setOnClickListener {
                        if (activity.isDemo()) showRemittanceDetails(activity, DemoStudentServices.demoRemittanceDetail(remittanceId), language)
                        else activity.universityRead({ client -> ToolServiceData.fetchRemittanceDetail(client, remittanceId) }) { detail ->
                            showRemittanceDetails(activity, detail, language)
                        }
                    }
                }
                UiHelper.add(activity, box, StudentServices.localized(item.opt("description"), language), 16, UiHelper.INK, true)
                UiHelper.note(activity, box, HistoryScreenController.formatFullDate(StudentServices.text(item, "date")), UiHelper.MUTED)
                UiHelper.add(activity, box, PaymentAmounts.format(item.opt("amount"), currency, language), 18, UiHelper.INK, true)
                if (item.has("is_settled") && !item.isNull("is_settled")) {
                    UiHelper.note(activity, box, activity.getString(if (item.optBoolean("is_settled"))
                        R.string.payments_settled else R.string.payments_not_settled), UiHelper.MUTED)
                }
                if (!item.isNull("remaining") && item.has("remaining")) {
                    val formattedRemaining = PaymentAmounts.format(item.opt("remaining"), currency, language)
                    UiHelper.note(activity, box,
                        activity.getString(R.string.payments_remittance_remaining, formattedRemaining, "").trim(), UiHelper.MUTED)
                }
            }
        }

        private fun showRemittanceDetails(activity: MainActivity, data: JSONObject, language: String) {
            val currency = PaymentAmounts.currencyCode(data.opt("currency"))
            val lines = mutableListOf<String>()
            HistoryScreenController.formatFullDate(StudentServices.text(data, "date")).takeIf { it.isNotEmpty() }?.let { lines.add(it) }
            lines.add(PaymentAmounts.format(data.opt("amount"), currency, language))
            if (data.has("remaining") && !data.isNull("remaining")) {
                lines.add(activity.getString(R.string.payments_remittance_remaining, PaymentAmounts.format(data.opt("remaining"), currency, language), "").trim())
            }
            if (data.has("is_settled") && !data.isNull("is_settled")) {
                lines.add(activity.getString(if (data.optBoolean("is_settled")) R.string.payments_settled else R.string.payments_not_settled))
            }
            StudentServices.text(data, "status").takeIf { it.isNotEmpty() }?.let { lines.add(it) }
            StudentServices.localized(data.opt("faculty_name"), language).takeIf { it.isNotBlank() }
                ?.let { lines.add(activity.getString(R.string.remittance_faculty, it)) }
            MaterialAlertDialogBuilder(activity)
                .setTitle(StudentServices.localized(data.opt("description"), language).ifEmpty { activity.getString(R.string.payments_history) })
                .setMessage(lines.joinToString("\n\n"))
                .setPositiveButton(android.R.string.ok, null).show()
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
