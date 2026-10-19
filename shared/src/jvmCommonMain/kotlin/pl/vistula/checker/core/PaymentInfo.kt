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
                return Account(
                    o.optString("id", ""), o.optString("name", ""), o.optString("currency", "PLN"),
                    o.optString("bankAccountNumber", ""), o.optDouble("balance", 0.0),
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

        fun isOverdue(): Boolean {
            if ("overdue".equals(status, ignoreCase = true) || "przeterminowana".equals(status, ignoreCase = true) ||
                "po terminie".equals(status, ignoreCase = true) || "прострочено".equals(status, ignoreCase = true)) return true
            if (isPaid()) return false
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
                return Installment(
                    o.optString("id", ""), o.optString("name", ""), o.optDouble("amount", 0.0),
                    o.optString("currency", "PLN"), o.optString("deadline", ""), o.optString("status", ""),
                    o.optString("paymentDate", ""), o.optString("bankAccountNumber", ""), o.optString("type", ""),
                )
            }
        }
    }

    fun totalUnpaidAmount(): Double {
        var sum = 0.0
        for (inst in installments) {
            if (!inst.isPaid() && inst.amount > 0) sum += inst.amount
        }
        return sum
    }

    fun hasOverdue(): Boolean {
        for (inst in installments) {
            if (inst.isOverdue()) return true
        }
        return false
    }

    @Throws(JSONException::class)
    fun json(): JSONObject {
        val accArr = JSONArray()
        for (a in accounts) accArr.put(a.json())
        val instArr = JSONArray()
        for (i in installments) instArr.put(i.json())
        return JSONObject().put("accounts", accArr).put("installments", instArr)
            .put("scopeMissing", scopeMissing).put("statusNotice", statusNotice)
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
            if (instArr != null) {
                for (i in 0 until instArr.length()) {
                    instList.add(Installment.from(instArr.optJSONObject(i)))
                }
            }
            val scopeMissing = o.optBoolean("scopeMissing", false)
            val statusNotice = o.optString("statusNotice", "")
            return PaymentInfo(accList, instList, scopeMissing, statusNotice)
        }
    }
}

