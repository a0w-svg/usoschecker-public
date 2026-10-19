package pl.vistula.checker.ui;

import android.graphics.Color;
import android.widget.EditText;
import android.widget.LinearLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import pl.vistula.checker.MainActivity;
import pl.vistula.checker.R;
import pl.vistula.checker.core.StudentServices;

public final class StudentDocumentsController {
    private StudentDocumentsController() {}
    public static void render(MainActivity activity, LinearLayout content, JSONObject state, JSONObject services) {
        ToolScreenUi.header(activity, content, R.string.documents_title, menu -> {
            ToolScreenUi.action(menu, R.string.documents_refresh, () -> activity.refreshStudentServices("documents"));
            ToolScreenUi.action(menu, R.string.documents_all_methods, () -> activity.openApiModule("adm_documents"));
        });
        StudentCardsController.renderAccess(activity, content, state, "adm_documents");
        JSONObject entry = StudentCardsController.entry(services, "documents");
        StudentCardsController.status(activity, content, entry);
        JSONArray rows = entry.optJSONArray("data");
        EditText search = UiHelper.searchField(activity, content, activity.getString(R.string.documents_search), activity.getDocumentQuery(), null, () -> activity.setDocumentQuery(""));
        LinearLayout list = new LinearLayout(activity); list.setOrientation(LinearLayout.VERTICAL); content.addView(list);
        Runnable populate = () -> {
            list.removeAllViews(); int shown = 0;
            if (rows != null) for (int i = 0; i < rows.length(); i++) {
                JSONObject raw = rows.optJSONObject(i); if (raw == null) continue;
                StudentServices.Document document = new StudentServices.Document(raw, Language.currentLocale(activity).getLanguage());
                if (!document.matches(search.getText().toString())) continue;
                shown++;
                LinearLayout card = UiHelper.section(activity, list, Color.WHITE);
                UiHelper.add(activity, card, document.name.isEmpty() ? activity.getString(R.string.documents_unnamed) : document.name, 16, UiHelper.INK, true);
                if (!document.status.isEmpty()) UiHelper.badge(activity, card, document.status, Color.rgb(214, 241, 229), UiHelper.GREEN);
                if (!document.typeName.isEmpty()) UiHelper.note(activity, card, document.typeName, UiHelper.MUTED);
                StudentCardsController.line(activity, card, R.string.documents_published, HistoryScreenController.formatFullDate(document.published));
                StudentCardsController.line(activity, card, R.string.documents_delivered, HistoryScreenController.formatFullDate(document.delivered));
                LinearLayout row = new LinearLayout(activity);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                android.view.View title = card.getChildAt(0); card.removeView(title);
                row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
                card.addView(row, 0);
                android.view.View options = ToolScreenUi.overflow(activity, row,
                    activity.getString(R.string.tool_options, document.name), menu -> {
                    if (document.downloadable()) ToolScreenUi.action(menu, R.string.documents_download, () ->
                        activity.downloadStudentDocument("services/blobbox/get", "blob_id", document.blobId, document.name));
                    if (document.actions.contains("GET_UPO")) ToolScreenUi.action(menu, R.string.documents_upo, () ->
                        activity.downloadStudentDocument("services/adm_documents/get_upo", "adm_document_id", document.id, "upo-" + document.id));
                    if (document.actions.contains("GET_UPD")) ToolScreenUi.action(menu, R.string.documents_upd, () ->
                        activity.downloadStudentDocument("services/adm_documents/get_upd", "adm_document_id", document.id, "upd-" + document.id));
                    if (document.actions.contains("RECEIVE_DOCUMENT")) {
                        ToolScreenUi.action(menu, R.string.documents_get_code, () -> activity.openApiMethod("services/adm_documents/generate_code",
                            java.util.Map.of("id", document.id, "lang", "pl".equals(Language.currentLocale(activity).getLanguage()) ? "pl" : "en")));
                        ToolScreenUi.action(menu, R.string.documents_receive, () -> activity.openApiMethod("services/adm_documents/receive", java.util.Map.of("id", document.id)));
                    }
                    ToolScreenUi.action(menu, R.string.documents_details, () -> activity.openApiMethod("services/adm_documents/document", java.util.Map.of("id", document.id)));
                });
                options.setId(R.id.document_more);

            }
            if (shown == 0) {
                UiHelper.empty(activity, list, activity.getString(rows == null ? R.string.services_load_hint : R.string.documents_empty),
                    activity.getString(R.string.documents_empty_hint));
                if (!search.getText().toString().trim().isEmpty()) {
                    list.addView(UiHelper.button(activity, activity.getString(R.string.btn_clear_search), () -> {
                        search.setText("");
                        activity.setDocumentQuery("");
                    }, false));
                }
            }
            UiHelper.note(activity, list, activity.getString(R.string.documents_count, shown), UiHelper.MUTED);
        };
        UiHelper.watchSearch(search, query -> { activity.setDocumentQuery(query); populate.run(); });
        populate.run();
    }
}
