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
    @Test fun cancelledInstallmentsAreNotDebtInAnySupportedLanguage() {
        for (status in listOf("cancelled", "Anulowana", "Скасовано")) {
            val cancelled = PaymentInfo.Installment("1", "", 100.0, "PLN", "2020-01-01", status, "")
            val info = PaymentInfo(emptyList(), listOf(cancelled))
            assertFalse(cancelled.isPaid())
            assertFalse(cancelled.isOverdue())
            assertEquals(0.0, info.totalUnpaidAmount(), 0.0)
            assertEquals("0,00 PLN", PaymentAmounts.unpaid(info, "pl"))
        }
    }
    @Test fun planScheduleDoesNotAssertIndividualSettlement() {
        val payments = org.json.JSONArray("""[{"id":"p","state":"unsettled","saldo_amount":25,"currency":"PLN",
            "date_of_plan_choice":"2020-01-01","chosen_installment_plan":{"installments":[
            {"number":1,"amount":100,"payment_date":"2020-01-01"}]}}]""")
        val info = UsosParser.payments(null, payments)
        val row = info.installments.single()
        assertTrue(row.isSettlementUnknown())
        assertFalse(row.isOverdue())
        assertEquals("", row.paymentDate)
        assertEquals(0.0, info.totalUnpaidAmount(), 0.0)
        assertTrue(PaymentInfo.from(info.json()).hasUnknownSettlement())
        val legacy = PaymentInfo.Installment("p:rata:1", "", 100.0, "PLN", "2020-01-01", "Po terminie", "")
        assertTrue(legacy.isSettlementUnknown())
        assertFalse(legacy.isOverdue())
        assertTrue(PaymentInfo.Installment("inst:1:1", "", 100.0, "PLN", "2020-01-01", "Po terminie", "").isSettlementUnknown())
        val mixed = PaymentInfo(emptyList(), listOf(row, PaymentInfo.Installment("known", "", 10.0, "PLN", "2020-01-01", "overdue", "")))
        assertTrue(mixed.hasUnknownSettlement())
        assertTrue(mixed.hasOverdue())
        val direct = UsosParser.payments(null, org.json.JSONArray("""[{"id":"p","type":"fee","state":"unsettled","total_amount":100,"saldo_amount":25}]"""))
        assertTrue(direct.hasUnknownSettlement())
        assertEquals(0.0, direct.totalUnpaidAmount(), 0.0)
    }
    @Test fun legacyCachedAmountsRemainUnknownAfterRoundTrip() {
        val old = JSONObject("""{"accounts":[],"installments":[
            {"id":"payment","amount":100,"currency":"EUR","status":"Do zapłaty","deadline":"2020-01-01"},
            {"id":"p:rata:1","amount":40,"currency":"PLN","status":"Opłacona","paymentDate":"2020-01-01"},
            {"id":"cancelled","amount":15,"status":"Anulowana"}]}""")
        val restored = PaymentInfo.from(old)
        assertTrue(restored.hasUnknownSettlement())
        assertFalse(restored.hasOverdue())
        assertEquals("", restored.installments[1].paymentDate)
        val roundTrip = PaymentInfo.from(restored.json())
        assertTrue(roundTrip.installments[0].isSettlementUnknown())
        assertTrue(roundTrip.installments[1].isPaid())
        assertTrue(roundTrip.installments[2].isCancelled())
        val snapshot = JSONObject().put("start", "2026-01-01").put("end", "2027-01-01")
            .put("records", org.json.JSONArray()).put("payments", old)
        val localized = JSONObject(snapshot.toString())
        snapshot.put("localized", JSONObject().put("en", localized).put("uk", JSONObject(localized.toString())))
        val parsed = Snapshot.from(snapshot)
        assertTrue(parsed.payments!!.hasUnknownSettlement())
        assertTrue(parsed.forLanguage("en").payments!!.hasUnknownSettlement())
        assertTrue(parsed.forLanguage("uk").payments!!.hasUnknownSettlement())
    }
    @Test fun nonFiniteAmountsAreRejectedOrMarkedUnknown() {
        val raw = org.json.JSONArray("""[{"id":"p","type":"fee","total_amount":"Infinity"}]""")
        assertThrows(org.json.JSONException::class.java) { UsosParser.payments(null, raw) }
        val old = JSONObject("""{"settlementModelVersion":1,"installments":[{"amount":"Infinity","status":"paid"}]}""")
        val restored = PaymentInfo.from(old)
        assertTrue(restored.hasUnknownSettlement())
        assertTrue(restored.installments.single().amount.isFinite())
        val accounts = org.json.JSONArray("""[{"id":"a1","account_number":"PL123","currency":{"id":"EUR","name":{"pl":"Euro"}}}]""")
        val payments = org.json.JSONArray("""[{"id":"p1","type":"tuition_fee","state":"unsettled","total_amount":0,"saldo_amount":1300,"currency":{"id":"EUR","name":{"pl":"Euro"}}}]""")
        val info = UsosParser.payments(accounts, payments)
        assertEquals("EUR", info.accounts.first().currency)
        assertEquals("EUR", info.installments.single().currency)
        assertEquals(1300.0, info.installments.single().amount, 0.0)
        assertEquals("EUR", PaymentServices.details(payments).getJSONObject(0).getString("currency"))
    }
}
