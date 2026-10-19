package pl.vistula.checker.ui

import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import org.json.JSONObject
import pl.vistula.checker.MainActivity
import pl.vistula.checker.R
import pl.vistula.checker.core.StudentServices

class StudentDocumentsController private constructor() {
    companion object {
        @JvmStatic
        fun render(activity: MainActivity, content: LinearLayout, state: JSONObject, services: JSONObject) {
            ToolScreenUi.header(activity, content, R.string.documents_title) { menu ->
                ToolScreenUi.action(menu, R.string.documents_refresh) { activity.refreshStudentServices("documents") }
                ToolScreenUi.action(menu, R.string.documents_all_methods) { activity.openApiModule("adm_documents") }
            }
            StudentCardsController.renderAccess(activity, content, state, "adm_documents")
            val entry = StudentCardsController.entry(services, "documents")
            StudentCardsController.status(activity, content, entry)
            val rows = entry.optJSONArray("data")
            val search = UiHelper.searchField(
                activity,
                content,
                activity.getString(R.string.documents_search),
                activity.getDocumentQuery(),
                null
            ) { activity.setDocumentQuery("") }
            val list = LinearLayout(activity)
            list.orientation = LinearLayout.VERTICAL
            content.addView(list)
            val populate = Runnable {
                list.removeAllViews()
                var shown = 0
                if (rows != null) {
                    for (i in 0 until rows.length()) {
                        val raw = rows.optJSONObject(i) ?: continue
                        val document = StudentServices.Document(raw, Language.currentLocale(activity).language)
                        if (!document.matches(search.text.toString())) continue
                        shown++
                        val card = UiHelper.section(activity, list, Color.WHITE)
                        UiHelper.add(
                            activity,
                            card,
                            if (document.name.isEmpty()) activity.getString(R.string.documents_unnamed) else document.name,
                            16,
                            UiHelper.INK,
                            true
                        )
                        if (document.status.isNotEmpty()) {
                            UiHelper.badge(activity, card, document.status, Color.rgb(214, 241, 229), UiHelper.GREEN)
                        }
                        val details = UiHelper.disclosure(activity, card, "documents.details", R.string.simple_details)
                        if (document.typeName.isNotEmpty()) {
                            UiHelper.note(activity, details, document.typeName, UiHelper.MUTED)
                        }
                        StudentCardsController.line(activity, details, R.string.documents_published, HistoryScreenController.formatFullDate(document.published))
                        StudentCardsController.line(activity, details, R.string.documents_delivered, HistoryScreenController.formatFullDate(document.delivered))
                        val row = LinearLayout(activity)
                        row.gravity = Gravity.CENTER_VERTICAL
                        val title = card.getChildAt(0)
                        card.removeView(title)
                        row.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
                        card.addView(row, 0)
                        val options = ToolScreenUi.overflow(
                            activity,
                            row,
                            activity.getString(R.string.tool_options, document.name)
                        ) { menu ->
                            if (document.downloadable()) {
                                ToolScreenUi.action(menu, R.string.documents_download) {
                                    activity.downloadStudentDocument("services/blobbox/get", "blob_id", document.blobId, document.name)
                                }
                            }
                            if (document.actions.contains("GET_UPO")) {
                                ToolScreenUi.action(menu, R.string.documents_upo) {
                                    activity.downloadStudentDocument("services/adm_documents/get_upo", "adm_document_id", document.id, "upo-${document.id}")
                                }
                            }
                            if (document.actions.contains("GET_UPD")) {
                                ToolScreenUi.action(menu, R.string.documents_upd) {
                                    activity.downloadStudentDocument("services/adm_documents/get_upd", "adm_document_id", document.id, "upd-${document.id}")
                                }
                            }
                            if (document.actions.contains("RECEIVE_DOCUMENT")) {
                                ToolScreenUi.action(menu, R.string.documents_get_code) { StudentActionsController.sendCode(activity, document) }
                                ToolScreenUi.action(menu, R.string.documents_receive) { StudentActionsController.receive(activity, document) }
                            }
                            ToolScreenUi.action(menu, R.string.documents_details) {
                                StudentActionsController.documentDetails(activity, document)
                            }
                        }
                        options.id = R.id.document_more
                    }
                }
                if (shown == 0) {
                    UiHelper.empty(
                        activity,
                        list,
                        activity.getString(if (rows == null) R.string.services_load_hint else R.string.documents_empty),
                        activity.getString(R.string.documents_empty_hint)
                    )
                    if (search.text.toString().trim().isNotEmpty()) {
                        list.addView(
                            UiHelper.button(
                                activity,
                                activity.getString(R.string.btn_clear_search),
                                {
                                    search.setText("")
                                    activity.setDocumentQuery("")
                                },
                                false
                            )
                        )
                    }
                }
                UiHelper.note(activity, list, activity.getString(R.string.documents_count, shown), UiHelper.MUTED)
            }
            UiHelper.watchSearch(search) { query ->
                activity.setDocumentQuery(query)
                populate.run()
            }
            populate.run()
        }
    }
}

