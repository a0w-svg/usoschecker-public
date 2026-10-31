package pl.vistula.checker.ui

import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.min
import org.json.JSONException
import org.json.JSONObject
import pl.vistula.checker.DemoStudentServices
import pl.vistula.checker.MainActivity
import pl.vistula.checker.R
import pl.vistula.checker.ToolServiceData
import pl.vistula.checker.core.PaymentAmounts
import pl.vistula.checker.core.PaymentInfo
import pl.vistula.checker.core.Snapshot
import pl.vistula.checker.core.StudentServices

/**
 * Controller for the Payments screen. Every block is inflated from XML (`view_payments_*`, `item_payment_*`,
 * `item_remittance`); this class only chooses what to show and binds the data.
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
            val remittances = services.optJSONObject("remittances") ?: JSONObject()
            if (payments == null) {
                renderRemittances(activity, content, remittances)
                UiHelper.empty(activity, content, activity.getString(R.string.payments_no_data_title),
                    activity.getString(R.string.services_not_loaded))
                content.addView(UiHelper.button(activity,
                    activity.getString(if (connected) R.string.btn_refresh else R.string.btn_connect),
                    { if (connected) onRefresh.run() else activity.navigate(Tabs.ACCOUNT) }, true))
                return
            }

            val authorizedScopes = state.optString("scopes", "")
            val hasPaymentsScope = StudentServices.hasScope(authorizedScopes, "payments")
            val scopeMissing = payments.scopeMissing ||
                (connected && authorizedScopes.isNotEmpty() && !hasPaymentsScope)
            if (scopeMissing) renderScopeWarning(activity, content, authorizedScopes)

            renderDetails(activity, content, entry.optJSONObject("data"))
            renderRemittances(activity, content, remittances)
            if (payments.accounts.isEmpty() && payments.installments.isEmpty()) {
                if (!scopeMissing) {
                    UiHelper.empty(activity, content, activity.getString(R.string.payments_empty_title),
                        activity.getString(R.string.payments_empty_desc))
                }
                return
            }
            if (payments.accounts.isNotEmpty()) renderAccounts(activity, content, payments)
            if (payments.installments.isNotEmpty()) {
                UiHelper.heading(activity, content, activity.getString(R.string.payments_schedule_heading))
                renderSummary(activity, content, payments, language)
                for (installment in payments.installments) renderInstallment(activity, content, installment)
            }
        }

        private fun inflate(activity: MainActivity, layout: Int, parent: LinearLayout): View =
            LayoutInflater.from(activity).inflate(layout, parent, false)

        private fun copySensitive(activity: MainActivity, label: String, number: String, confirmation: Int) {
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager? ?: return
            clipboard.setPrimaryClip(UiHelper.privateClip(label, number.replace(Regex("\\s+"), "")))
            Toast.makeText(activity, activity.getString(confirmation), Toast.LENGTH_SHORT).show()
        }

        private fun TextView.showIfNotEmpty(value: String) {
            text = value
            visibility = if (value.isEmpty()) View.GONE else View.VISIBLE
        }

        private fun renderScopeWarning(activity: MainActivity, content: LinearLayout, authorizedScopes: String) {
            val box = inflate(activity, R.layout.view_payments_scope_warning, content)
            box.findViewById<TextView>(R.id.payments_scope_badge).setText(R.string.payments_missing_scope_badge)
            box.findViewById<TextView>(R.id.payments_scope_title).setText(R.string.payments_missing_scope_title)
            box.findViewById<TextView>(R.id.payments_scope_desc).text = activity.getString(
                R.string.payments_missing_scope_desc, if (authorizedScopes.isEmpty()) "brak" else authorizedScopes
            )
            box.findViewById<TextView>(R.id.payments_scope_note).setText(R.string.payments_missing_scope_action_note)
            box.findViewById<MaterialButton>(R.id.payments_scope_button).apply {
                setText(R.string.payments_missing_scope_btn)
                setOnClickListener { activity.navigate(Tabs.ACCOUNT) }
            }
            content.addView(box)
        }

        private fun renderAccounts(activity: MainActivity, content: LinearLayout, payments: PaymentInfo) {
            val accounts = UiHelper.disclosure(activity, content, "payments.accounts", R.string.payments_accounts_heading)
            UiHelper.note(activity, accounts, activity.getString(R.string.payments_accounts_information), UiHelper.MUTED)
            for (acc in payments.accounts) {
                val box = inflate(activity, R.layout.item_payment_account, accounts)
                box.findViewById<TextView>(R.id.payments_account_name).text =
                    acc.name.ifEmpty { activity.getString(R.string.payments_account_default_name) }
                box.findViewById<TextView>(R.id.payments_account_faculty)
                    .showIfNotEmpty(if (acc.faculty.isEmpty()) "" else activity.getString(R.string.payments_faculty, acc.faculty))
                box.findViewById<TextView>(R.id.payments_account_owner)
                    .showIfNotEmpty(if (acc.accountOwner.isEmpty()) "" else activity.getString(R.string.payments_owner, acc.accountOwner))
                box.findViewById<TextView>(R.id.payments_account_iban).text = formatIban(acc.bankAccountNumber)
                box.findViewById<TextView>(R.id.payments_account_swift)
                    .showIfNotEmpty(if (acc.swift.isEmpty()) "" else activity.getString(R.string.payments_swift, acc.swift))
                box.findViewById<TextView>(R.id.payments_account_balance).showIfNotEmpty(
                    if (acc.balance == 0.0) "" else activity.getString(R.string.payments_balance, acc.balance, acc.currency)
                )
                box.findViewById<MaterialButton>(R.id.payments_account_copy).setOnClickListener {
                    copySensitive(activity, "Subkonto USOS", acc.bankAccountNumber, R.string.payments_iban_copied)
                }
                accounts.addView(box)
            }
        }

        private fun renderSummary(activity: MainActivity, content: LinearLayout, payments: PaymentInfo, language: String) {
            val unpaid = payments.installments.filter { it.isUnpaid() && it.amount > 0 }.groupBy { it.currency }
                .mapValues { (_, entries) -> entries.sumOf { it.amount } }
            val open = payments.installments.filter { !it.isPaid() && !it.isCancelled() && it.amount > 0 }.groupBy { it.currency }
                .mapValues { (_, entries) -> entries.sumOf { it.amount } }
            val totals = unpaid.ifEmpty { open }
            val overdue = payments.hasOverdue()

            val box = inflate(activity, R.layout.view_payments_summary, content)
            box.findViewById<TextView>(R.id.payments_summary_label).setText(R.string.payments_installment_total_label)
            box.findViewById<TextView>(R.id.payments_summary_warning).apply {
                setText(R.string.payments_status_overdue_warn)
                visibility = if (payments.hasUnknownSettlement() && overdue) View.VISIBLE else View.GONE
            }
            val amounts = box.findViewById<LinearLayout>(R.id.payments_summary_amounts)
            val lines = if (totals.isNotEmpty()) {
                totals.map { (currency, amount) -> PaymentAmounts.format(amount, currency, language) }
            } else {
                val currency = payments.installments.firstOrNull()?.currency
                    ?: payments.accounts.firstOrNull()?.currency
                    ?: "PLN"
                listOf(PaymentAmounts.format(0, currency, language))
            }
            for (line in lines) {
                (inflate(activity, R.layout.item_payment_amount, amounts) as TextView).also {
                    it.text = line
                    amounts.addView(it)
                }
            }
            box.findViewById<TextView>(R.id.payments_summary_status).apply {
                setText(
                    when {
                        payments.hasUnknownSettlement() -> R.string.payments_settlement_unknown
                        overdue -> R.string.payments_status_overdue_warn
                        unpaid.isEmpty() -> R.string.payments_status_none_due
                        else -> R.string.payments_status_pending
                    }
                )
                if (overdue) setTextColor(activity.getColor(R.color.app_error))
            }
            content.addView(box)
        }

        private fun installmentStatusLabel(activity: MainActivity, inst: PaymentInfo.Installment): String {
            val status = inst.status.lowercase(java.util.Locale.ROOT)
            return when {
                inst.isPaid() -> activity.getString(R.string.payment_status_paid)
                inst.isOverdue() -> activity.getString(R.string.payment_status_overdue)
                inst.status == "settlement_unknown" -> activity.getString(R.string.payment_status_unknown)
                inst.isSettlementUnknown() || status in setOf("scheduled", "zaplanowana", "заплановано") -> activity.getString(R.string.payment_status_scheduled)
                status in setOf("cancelled", "anulowana", "скасовано") -> activity.getString(R.string.payment_status_cancelled)
                status in setOf("underpaid", "niedopłata", "недоплата") -> activity.getString(R.string.payment_status_underpaid)
                inst.status.isEmpty() || status in setOf("do zapłaty", "due", "unsettled", "pending", "до сплати") -> activity.getString(R.string.payment_status_to_pay)
                else -> inst.status
            }
        }

        private fun renderInstallment(activity: MainActivity, content: LinearLayout, inst: PaymentInfo.Installment) {
            val card = inflate(activity, R.layout.item_payment_card, content)
            val paid = inst.isPaid()
            val overdue = inst.isOverdue()

            card.findViewById<TextView>(R.id.payment_item_title).text = inst.name
            card.findViewById<TextView>(R.id.payment_item_status).apply {
                text = installmentStatusLabel(activity, inst)
                setTextColor(activity.getColor(if (paid) R.color.app_ink else if (overdue) R.color.app_error else R.color.app_warning))
                setBackgroundResource(if (paid) R.drawable.bg_badge_success else if (overdue) R.drawable.bg_badge_error else R.drawable.bg_badge_warning)
            }
            card.findViewById<TextView>(R.id.payment_item_amount).text = activity.getString(R.string.payments_amount, inst.amount, inst.currency)
            card.findViewById<TextView>(R.id.payment_item_deadline).apply {
                showIfNotEmpty(if (inst.deadline.isEmpty()) "" else activity.getString(R.string.payments_deadline, HistoryScreenController.formatFullDate(inst.deadline)))
                if (overdue) setTextColor(activity.getColor(R.color.app_error))
            }
            card.findViewById<TextView>(R.id.payment_item_paid_date).showIfNotEmpty(
                if (inst.paymentDate.isEmpty()) "" else activity.getString(R.string.payments_date_paid, HistoryScreenController.formatFullDate(inst.paymentDate))
            )
            if (inst.bankAccountNumber.isNotEmpty()) {
                card.findViewById<TextView>(R.id.payment_item_account).showIfNotEmpty(
                    activity.getString(R.string.payments_account_for_installment, formatIban(inst.bankAccountNumber))
                )
                val copy = Runnable {
                    copySensitive(activity, activity.getString(R.string.payment_clipboard_label), inst.bankAccountNumber, R.string.payments_account_copied)
                }
                card.findViewById<MaterialButton>(R.id.payment_item_btn_copy).apply {
                    visibility = View.VISIBLE
                    setOnClickListener { copy.run() }
                }
                card.isClickable = true
                card.isFocusable = true
                card.setOnClickListener { copy.run() }
            }
            content.addView(card)
        }

        private fun addLine(activity: MainActivity, parent: LinearLayout, value: String) {
            val line = inflate(activity, R.layout.item_payment_line, parent) as TextView
            line.text = value
            parent.addView(line)
        }

        private fun renderDetails(activity: MainActivity, content: LinearLayout, data: JSONObject?) {
            val language = Language.currentLocale(activity).language
            val items = data?.optJSONArray("details") ?: return
            if (items.length() == 0) return
            val body = UiHelper.disclosure(activity, content, "payments.details", R.string.payments_details)
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val currency = PaymentAmounts.currencyCode(item.opt("currency"))
                val box = inflate(activity, R.layout.item_payment_detail, body)
                box.findViewById<TextView>(R.id.payments_detail_title).text = StudentServices.localized(item.opt("description"), language)
                val lines = box.findViewById<LinearLayout>(R.id.payments_detail_lines)
                val plansView = box.findViewById<LinearLayout>(R.id.payments_detail_plans)
                for ((key, label) in listOf("total_amount" to R.string.payments_api_amount, "saldo_amount" to R.string.payments_balance_value,
                    "interest" to R.string.payments_interest_value)) {
                    if (!item.isNull(key) && item.has(key)) {
                        val formatted = PaymentAmounts.format(item.opt(key), currency, language)
                        addLine(activity, lines, activity.getString(label, formatted, "").trim())
                    }
                }
                if (item.optBoolean("has_bonus")) {
                    val formattedBonus = PaymentAmounts.format(item.opt("bonus_amount"), currency, language)
                    addLine(activity, lines, activity.getString(R.string.payments_bonus_value, formattedBonus, "",
                        HistoryScreenController.formatFullDate(StudentServices.text(item, "bonus_deadline"))).replace("  ,", ","))
                }
                if (StudentServices.text(item, "default_choice_date").isNotEmpty()) {
                    addLine(activity, lines, activity.getString(R.string.payments_default_choice,
                        HistoryScreenController.formatFullDate(StudentServices.text(item, "default_choice_date"))))
                }
                val plans = item.optJSONArray("plans")
                if (plans != null) {
                    for (j in 0 until plans.length()) renderPlan(activity, plansView, item, plans.getJSONObject(j), language)
                }
                body.addView(box)
            }
        }

        private fun renderPlan(activity: MainActivity, parent: LinearLayout, item: JSONObject, plan: JSONObject, language: String) {
            val rows = plan.getJSONArray("installments")
            val description = (0 until rows.length()).joinToString("\n") { n ->
                val row = rows.getJSONObject(n)
                activity.getString(R.string.payments_plan_line,
                    HistoryScreenController.formatFullDate(StudentServices.text(row, "payment_date")),
                    PaymentAmounts.format(row.opt("amount"), PaymentAmounts.currency(plan, item), language))
            }
            val view = inflate(activity, R.layout.item_payment_plan, parent)
            view.findViewById<TextView>(R.id.payments_plan_label).text = activity.getString(R.string.payments_plan_label, plan.optString("id"))
            view.findViewById<TextView>(R.id.payments_plan_rows).text = description
            val chosen = item.optString("chosenPlan") == plan.optString("id")
            if (chosen) view.findViewById<TextView>(R.id.payments_plan_selected).apply {
                setText(R.string.payments_selected_plan)
                visibility = View.VISIBLE
            }
            if (StudentServices.text(item, "id").isNotEmpty() && StudentServices.text(item, "type").isNotEmpty() &&
                StudentServices.text(plan, "id").isNotEmpty() && !chosen) {
                view.findViewById<MaterialButton>(R.id.payments_plan_choose).apply {
                    visibility = View.VISIBLE
                    setOnClickListener { StudentActionsController.choosePlan(activity, item, plan, description) }
                }
            }
            parent.addView(view)
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
                val box = inflate(activity, R.layout.item_remittance, body)
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
                box.findViewById<TextView>(R.id.remittance_title).text = StudentServices.localized(item.opt("description"), language)
                box.findViewById<TextView>(R.id.remittance_date).text = HistoryScreenController.formatFullDate(StudentServices.text(item, "date"))
                box.findViewById<TextView>(R.id.remittance_amount).text = PaymentAmounts.format(item.opt("amount"), currency, language)
                if (item.has("is_settled") && !item.isNull("is_settled")) {
                    box.findViewById<TextView>(R.id.remittance_settled).apply {
                        setText(if (item.optBoolean("is_settled")) R.string.payments_settled else R.string.payments_not_settled)
                        visibility = View.VISIBLE
                    }
                }
                if (!item.isNull("remaining") && item.has("remaining")) {
                    val formattedRemaining = PaymentAmounts.format(item.opt("remaining"), currency, language)
                    box.findViewById<TextView>(R.id.remittance_remaining).apply {
                        text = activity.getString(R.string.payments_remittance_remaining, formattedRemaining, "").trim()
                        visibility = View.VISIBLE
                    }
                }
                body.addView(box)
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
