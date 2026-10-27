package pl.vistula.checker.core

import java.time.LocalDate
import java.util.Collections
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class PaymentInfo @JvmOverloads constructor(
    accounts: List<Account>,
    installments: List<Installment>,
    @JvmField val scopeMissing: Boolean = false,
    statusNotice: String? = "",
) {
    @JvmField val accounts: List<Account> = Collections.unmodifiableList(ArrayList(accounts))
    @JvmField val installments: List<Installment> = Collections.unmodifiableList(ArrayList(installments))
    @JvmField val statusNotice: String = statusNotice ?: ""

    class Account @JvmOverloads constructor(
        id: String?,
        name: String?,
        currency: String?,
        bankAccountNumber: String?,
        @JvmField val balance: Double,
        faculty: String? = "",
        accountOwner: String? = "",
        swift: String? = "",
    ) {
        @JvmField val id: String = id ?: ""
        @JvmField val name: String = name ?: ""
        @JvmField val currency: String = if (!currency.isNullOrEmpty()) currency else "PLN"
        @JvmField val bankAccountNumber: String = bankAccountNumber ?: ""
        @JvmField val faculty: String = faculty ?: ""
        @JvmField val accountOwner: String = accountOwner ?: ""
        @JvmField val swift: String = swift ?: ""

        @Throws(JSONException::class)
        fun json(): JSONObject = JSONObject().put("id", id).put("name", name).put("currency", currency)
            .put("bankAccountNumber", bankAccountNumber).put("balance", balance)
            .put("faculty", faculty).put("accountOwner", accountOwner).put("swift", swift)

        companion object {
            @JvmStatic
            fun from(o: JSONObject?): Account {
                if (o == null) return Account("", "", "PLN", "", 0.0)
                val balance = o.optDouble("balance", 0.0).takeIf { it.isFinite() } ?: 0.0
                return Account(
                    o.optString("id", ""), o.optString("name", ""), o.optString("currency", "PLN"),
                    o.optString("bankAccountNumber", ""), balance,
                    o.optString("faculty", ""), o.optString("accountOwner", ""), o.optString("swift", ""),
                )
            }
        }
    }

    class Installment @JvmOverloads constructor(
        id: String?,
        name: String?,
        @JvmField val amount: Double,
        currency: String?,
        deadline: String?,
        status: String?,
        paymentDate: String?,
        bankAccountNumber: String? = "",
        type: String? = "",
    ) {
        @JvmField val id: String = id ?: ""
        @JvmField val name: String = name ?: ""
        @JvmField val currency: String = if (!currency.isNullOrEmpty()) currency else "PLN"
        @JvmField val deadline: String = deadline ?: ""
        @JvmField val status: String = status ?: ""
        @JvmField val paymentDate: String = paymentDate ?: ""
        @JvmField val bankAccountNumber: String = bankAccountNumber ?: ""
        @JvmField val type: String = type ?: ""

        @Throws(JSONException::class)
        fun json(): JSONObject = JSONObject().put("id", id).put("name", name).put("amount", amount)
            .put("currency", currency).put("deadline", deadline).put("status", status)
            .put("paymentDate", paymentDate).put("bankAccountNumber", bankAccountNumber)
            .put("type", type)

        fun isPaid(): Boolean =
            "paid".equals(status, ignoreCase = true) || "opłacona".equals(status, ignoreCase = true) ||
                "oplacona".equals(status, ignoreCase = true) || "settled".equals(status, ignoreCase = true) ||
                "rozliczona".equals(status, ignoreCase = true) || "сплачено".equals(status, ignoreCase = true)

        fun isCancelled(): Boolean =
            "cancelled".equals(status, ignoreCase = true) || "anulowana".equals(status, ignoreCase = true) ||
                "скасовано".equals(status, ignoreCase = true)

        // A plan describes scheduled amounts, not the settlement of each row.
        // The ID check also protects cached rows written by older versions.
        fun isSettlementUnknown(): Boolean = !isPaid() && !isCancelled() &&
            (id.contains(":rata:") || id.matches(Regex("inst:[0-9]+:[0-9]+")) ||
                "scheduled".equals(status, ignoreCase = true) || "settlement_unknown".equals(status, ignoreCase = true))

        fun isUnpaid(): Boolean = !isPaid() && !isCancelled() && !isSettlementUnknown()

        fun isOverdue(): Boolean {
            if (!isUnpaid()) return false
            if ("overdue".equals(status, ignoreCase = true) || "przeterminowana".equals(status, ignoreCase = true) ||
                "po terminie".equals(status, ignoreCase = true) || "прострочено".equals(status, ignoreCase = true)) return true
            if (deadline.isNotEmpty()) {
                try {
                    val d = if (deadline.length > 10) deadline.substring(0, 10) else deadline
                    return LocalDate.parse(d).isBefore(LocalDate.now())
                } catch (ignored: Exception) {
                }
            }
            return false
        }

        companion object {
            @JvmStatic
            fun from(o: JSONObject?): Installment {
                if (o == null) return Installment("", "", 0.0, "PLN", "", "", "")
                val amount = if (!o.has("amount") || o.isNull("amount")) 0.0 else o.optDouble("amount", Double.NaN)
                return Installment(
                    o.optString("id", ""), o.optString("name", ""), if (amount.isFinite()) amount else 0.0,
                    o.optString("currency", "PLN"), o.optString("deadline", ""), if (amount.isFinite()) o.optString("status", "") else "settlement_unknown",
                    o.optString("paymentDate", ""), o.optString("bankAccountNumber", ""), o.optString("type", ""),
                )
            }
        }
    }

    fun totalUnpaidAmount(): Double {
        var sum = 0.0
        for (inst in installments) {
            if (inst.isUnpaid() && inst.amount > 0) sum += inst.amount
        }
        return sum
    }

    fun hasOverdue(): Boolean {
        for (inst in installments) {
            if (inst.isOverdue()) return true
        }
        return false
    }

    fun hasUnknownSettlement(): Boolean = installments.any { it.isSettlementUnknown() }

    @Throws(JSONException::class)
    fun json(): JSONObject {
        val accArr = JSONArray()
        for (a in accounts) accArr.put(a.json())
        val instArr = JSONArray()
        for (i in installments) instArr.put(i.json())
        return JSONObject().put("accounts", accArr).put("installments", instArr)
            .put("scopeMissing", scopeMissing).put("statusNotice", statusNotice).put("settlementModelVersion", 1)
    }

    companion object {
        @JvmStatic
        fun from(o: JSONObject?): PaymentInfo {
            if (o == null) return PaymentInfo(emptyList(), emptyList())
            val accList = ArrayList<Account>()
            val accArr = o.optJSONArray("accounts")
            if (accArr != null) {
                for (i in 0 until accArr.length()) {
                    accList.add(Account.from(accArr.optJSONObject(i)))
                }
            }
            val instList = ArrayList<Installment>()
            val instArr = o.optJSONArray("installments")
            val legacy = o.optInt("settlementModelVersion", 0) < 1
            if (instArr != null) {
                for (i in 0 until instArr.length()) {
                    val original = Installment.from(instArr.optJSONObject(i))
                    if (legacy) {
                        val oldPlan = original.id.contains(":rata:") || original.id.matches(Regex("inst:[0-9]+:[0-9]+"))
                        instList.add(Installment(original.id, original.name, original.amount, original.currency, original.deadline,
                            if (original.isPaid() || original.isCancelled()) original.status else "settlement_unknown",
                            if (oldPlan) "" else original.paymentDate, original.bankAccountNumber, original.type))
                    } else instList.add(original)
                }
            }
            val scopeMissing = o.optBoolean("scopeMissing", false)
            val statusNotice = o.optString("statusNotice", "")
            return PaymentInfo(accList, instList, scopeMissing, statusNotice)
        }
    }
}

