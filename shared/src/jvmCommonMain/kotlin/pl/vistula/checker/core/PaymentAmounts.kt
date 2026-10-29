package pl.vistula.checker.core

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import org.json.JSONObject

/** Display units explicitly; never infer PLN or combine different currencies. */
object PaymentAmounts {
    @JvmStatic
    fun currencyCode(value: Any?): String = when (value) {
        is String -> value.trim()
        is JSONObject -> StudentServices.text(value, "id").trim()
            .ifEmpty { StudentServices.text(value, "code").trim() }
            .ifEmpty { ApiLanguage.text(value.opt("name")).trim() }
        else -> ""
    }

    @JvmStatic
    fun currency(plan: JSONObject?, payment: JSONObject?): String =
        currencyCode(plan?.opt("currency")).ifEmpty { currencyCode(payment?.opt("currency")) }

    @JvmStatic
    fun format(amount: Any?, currency: String, language: String): String {
        val lang = ApiLanguage.normalize(language)
        val unit = currency.trim().ifEmpty {
            when (lang) { "en" -> "currency not provided"; "uk" -> "валюту не вказано"; else -> "waluta niepodana" }
        }
        val number = try { BigDecimal(amount?.toString() ?: "") } catch (_: NumberFormatException) { null }
        val formatted = if (number == null) "—" else NumberFormat.getNumberInstance(Locale.forLanguageTag(lang)).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(number)
        return "$formatted $unit"
    }

    @JvmStatic
    fun unpaid(info: PaymentInfo, language: String): String {
        val sums = info.installments.filter { it.isUnpaid() && it.amount > 0 }.groupBy { it.currency }
            .mapValues { (_, items) -> items.fold(BigDecimal.ZERO) { sum, item -> sum + BigDecimal.valueOf(item.amount) } }
        return if (sums.isEmpty()) format(0, info.installments.firstOrNull()?.currency ?: info.accounts.firstOrNull()?.currency ?: "", language)
            else sums.entries.joinToString(" · ") { (unit, amount) -> format(amount, unit, language) }
    }
}
