package pl.vistula.checker.ui.compose

import org.json.JSONObject
import pl.vistula.checker.core.ApiLanguage
import pl.vistula.checker.core.PaymentInfo
import pl.vistula.checker.core.Snapshot

internal object ToolReadModels {
    fun payments(state: JSONObject, snapshot: Snapshot?, language: String): PaymentInfo? {
        val entry = state.optJSONObject("studentServices")?.optJSONObject("payments")
        val data = entry?.optJSONObject("data")?.optJSONObject(ApiLanguage.normalize(language))
        return if (data != null && entry.optLong("fetchedAt") >= state.optLong("successAt")) PaymentInfo.from(data)
            else snapshot?.payments
    }
}
