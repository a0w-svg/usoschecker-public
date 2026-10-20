package pl.vistula.checker.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PaymentAmountsTest {
    @Test fun missingPlanCurrencyUsesPaymentCurrency() {
        val plan = JSONObject("""{"currency":null}""")
        assertEquals("PLN", PaymentAmounts.currency(plan, JSONObject("""{"currency":"PLN"}""")))
        val formatted = PaymentAmounts.format("1250", PaymentAmounts.currency(plan, JSONObject("""{"currency":"PLN"}""")), "pl")
        assertTrue(formatted.endsWith(",00 PLN"))
        assertTrue(formatted.replace("\u00a0", " ").startsWith("1 250"))
    }
    @Test fun explicitPlanCurrencyWinsAndMissingUnitIsVisible() {
        assertEquals("EUR", PaymentAmounts.currency(JSONObject("""{"currency":"EUR"}"""), JSONObject("""{"currency":"PLN"}""")))
        assertEquals("10,50 waluta niepodana", PaymentAmounts.format("10.5", "", "pl"))
        assertEquals("— PLN", PaymentAmounts.format(JSONObject.NULL, "PLN", "pl"))
    }
    @Test fun totalsKeepCurrenciesSeparateAndSkipPaidInstallments() {
        val info = PaymentInfo(emptyList(), listOf(
            PaymentInfo.Installment("1", "", 100.0, "PLN", "", "due", ""),
            PaymentInfo.Installment("2", "", 25.5, "EUR", "", "due", ""),
            PaymentInfo.Installment("3", "", 200.0, "PLN", "", "paid", "")))
        assertEquals("100,00 PLN · 25,50 EUR", PaymentAmounts.unpaid(info, "pl"))
    }
}
