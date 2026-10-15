package pl.vistula.checker.ui;

import android.graphics.Color;
import android.widget.LinearLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import pl.vistula.checker.MainActivity;
import pl.vistula.checker.R;
import pl.vistula.checker.UsosClient;
import pl.vistula.checker.core.StudentServices;

public final class StudentCardsController {
    private StudentCardsController() {}
    public static void render(MainActivity activity, LinearLayout content, JSONObject state, JSONObject services) throws Exception {
        UiHelper.title(activity, content, activity.getString(R.string.cards_title), activity.getString(R.string.cards_description));
        UiHelper.note(activity, content, activity.getString(R.string.cards_information_only), UiHelper.MUTED);
        renderAccess(activity, content, state, "cards");
        content.addView(UiHelper.button(activity, activity.getString(R.string.cards_refresh), () -> activity.refreshStudentServices("cards"), true));
        JSONObject physical = entry(services, "cards");
        status(activity, content, physical);
        JSONArray cards = physical.optJSONArray("data");
        if (cards == null || cards.length() == 0) UiHelper.empty(activity, content, activity.getString(R.string.cards_empty),
            activity.getString(physical.has("fetchedAt") ? R.string.cards_none_returned : R.string.services_load_hint));
        else for (StudentServices.Card card : StudentServices.cards(cards)) renderCard(activity, content, state, card);

        UiHelper.heading(activity, content, activity.getString(R.string.cards_mobile_title));
        JSONObject mobile = entry(services, "mcard");
        status(activity, content, mobile);
        JSONObject data = mobile.optJSONObject("data");
        if (data == null) UiHelper.empty(activity, content, activity.getString(R.string.cards_mobile_empty), activity.getString(R.string.services_load_hint));
        else {
            LinearLayout box = UiHelper.card(activity, content, Color.WHITE);
            JSONObject latest = data.optJSONObject("latest_order");
            String orderReason = StudentServices.localized(data.opt("why_cannot_order"));
            String revokeReason = StudentServices.localized(data.opt("why_cannot_revoke"));
            if (latest == null) UiHelper.add(activity, box, activity.getString(R.string.cards_no_order), 18, UiHelper.INK, true);
            else {
                String status = StudentServices.text(latest, "status"), id = StudentServices.text(latest, "id");
                UiHelper.badge(activity, box, mobileStatus(activity, status), Color.rgb(214, 241, 229), UiHelper.GREEN);
                line(activity, box, R.string.cards_order_date, StudentServices.text(latest, "order_date"));
                line(activity, box, R.string.cards_expiration, StudentServices.text(latest, "expiration_date"));
                if (status.equals("ready_to_receive") && !id.isEmpty()) box.addView(UiHelper.button(activity,
                    activity.getString(R.string.cards_activation), () -> activity.showMobileActivation(id), true));
                if (!id.isEmpty() && data.optBoolean("revokeEligibilityKnown") && revokeReason.isEmpty() && !status.startsWith("revoked")) box.addView(UiHelper.button(activity,
                    activity.getString(R.string.cards_revoke), () -> activity.mobileCardAction(false, id), false));
            }
            if (!orderReason.isEmpty()) UiHelper.note(activity, box, orderReason, UiHelper.MUTED);
            else if (data.optBoolean("orderEligibilityKnown")) box.addView(UiHelper.button(activity, activity.getString(R.string.cards_order), () -> activity.mobileCardAction(true, ""), false));
            if (!revokeReason.isEmpty()) UiHelper.note(activity, box, revokeReason, UiHelper.MUTED);
            line(activity, box, R.string.cards_expected_expiration, StudentServices.text(data, "expected_expiration_date"));
            UiHelper.note(activity, box, activity.getString(R.string.cards_activation_hint), UiHelper.MUTED);
            JSONArray orders = data.optJSONArray("orders");
            if (orders != null && orders.length() > 0) box.addView(UiHelper.button(activity,
                activity.getString(R.string.cards_order_history, orders.length()), () -> showOrders(activity, orders), false));
        }
        content.addView(UiHelper.button(activity, activity.getString(R.string.cards_all_methods), () -> activity.openApiModule("cards"), false));
    }
    private static void showOrders(MainActivity activity, JSONArray orders) {
        LinearLayout body = new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(UiHelper.dp(activity, 20), UiHelper.dp(activity, 8), UiHelper.dp(activity, 20), UiHelper.dp(activity, 8));
        for (int i = 0; i < orders.length(); i++) {
            JSONObject order = orders.optJSONObject(i); if (order == null) continue;
            LinearLayout card = UiHelper.card(activity, body, Color.WHITE);
            UiHelper.add(activity, card, mobileStatus(activity, StudentServices.text(order, "status")), 18, UiHelper.INK, true);
            line(activity, card, R.string.cards_order_date, StudentServices.text(order, "order_date"));
            line(activity, card, R.string.cards_expiration, StudentServices.text(order, "expiration_date"));
            line(activity, card, R.string.cards_order_id, StudentServices.text(order, "id"));
        }
        android.widget.ScrollView scroll = UiHelper.dialogScroll(activity, body);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setTitle(R.string.cards_order_history_title)
            .setView(scroll).setPositiveButton(R.string.btn_close, null).show();
    }
    private static void renderCard(MainActivity activity, LinearLayout parent, JSONObject state, StudentServices.Card card) {
        LocalDate today = LocalDate.now(UsosClient.ZONE);
        LinearLayout box = UiHelper.card(activity, parent, UiHelper.INK);
        UiHelper.add(activity, box, activity.getString(R.string.cards_physical_title), 12, Color.rgb(124, 219, 192), true);
        UiHelper.add(activity, box, state.optString("user", activity.getString(R.string.cards_owner)), 23, Color.WHITE, true);
        if (!card.studentNumber.isEmpty()) UiHelper.add(activity, box, activity.getString(R.string.cards_student_number, card.studentNumber), 16, Color.WHITE, true);
        int statusColor = card.expired(today) ? Color.rgb(255, 183, 178) : Color.rgb(124, 219, 192);
        String expiration = card.expires.isEmpty() ? activity.getString(R.string.cards_no_expiration)
            : activity.getString(R.string.cards_expiration, card.expires);
        UiHelper.add(activity, box, expiration, 16, statusColor, true);
        if (card.expired(today)) UiHelper.note(activity, box, activity.getString(R.string.cards_expired), statusColor);
        else if (card.expiresSoon(today)) UiHelper.note(activity, box, activity.getString(R.string.cards_expires_soon), statusColor);
        if (!card.issued.isEmpty()) UiHelper.note(activity, box, activity.getString(R.string.cards_issue_date, card.issued), Color.LTGRAY);
        if (!card.type.isEmpty()) UiHelper.note(activity, box, activity.getString(R.string.cards_type, card.type), Color.LTGRAY);
        if (!card.barcode.isEmpty()) box.addView(UiHelper.button(activity, activity.getString(R.string.cards_show_number), () ->
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.cards_barcode_title).setMessage(card.barcode)
                .setPositiveButton(R.string.btn_close, null)
                .setNeutralButton(R.string.services_copy, (dialog, which) -> UiHelper.copyToClipboard(activity,
                    activity.getString(R.string.cards_barcode_title), card.barcode)).show(), false));
    }
    static JSONObject entry(JSONObject services, String key) {
        JSONObject entry = services.optJSONObject(key); return entry == null ? new JSONObject() : entry;
    }
    static void status(MainActivity activity, LinearLayout content, JSONObject entry) {
        if (entry.optLong("fetchedAt") > 0) UiHelper.note(activity, content,
            activity.getString(R.string.services_last_fetch, UiHelper.timestamp(entry.optLong("fetchedAt"))), UiHelper.MUTED);
        if (!entry.optString("error").isEmpty()) UiHelper.note(activity, content,
            activity.getString(R.string.services_refresh_error, entry.optString("error")), Color.rgb(160, 48, 40));
    }
    static void renderAccess(MainActivity activity, LinearLayout content, JSONObject state, String scope) {
        if (activity.isDemo()) return;
        if (!activity.connected() || !StudentServices.hasScope(state.optString("scopes"), scope)) {
            LinearLayout box = UiHelper.card(activity, content, Color.WHITE);
            UiHelper.add(activity, box, activity.getString(R.string.services_scope_required, scope), 14, UiHelper.INK, false);
            box.addView(UiHelper.button(activity, activity.getString(R.string.nav_account), () -> activity.navigate(4), false));
        }
    }
    static void line(MainActivity activity, LinearLayout box, int label, String value) {
        if (!value.isEmpty()) UiHelper.add(activity, box, activity.getString(label, value), 14, UiHelper.MUTED, false);
    }
    private static String mobileStatus(MainActivity activity, String status) {
        int resource;
        switch (status) {
            case "pending": resource = R.string.mcard_pending; break;
            case "in_progress": resource = R.string.mcard_in_progress; break;
            case "ready_to_receive": resource = R.string.mcard_ready; break;
            case "received": resource = R.string.mcard_received; break;
            case "error": resource = R.string.mcard_error; break;
            case "revoked_by_me": resource = R.string.mcard_revoked_by_me; break;
            case "revoked": resource = R.string.mcard_revoked; break;
            default: return status.isEmpty() ? activity.getString(R.string.mcard_status_missing) : activity.getString(R.string.mcard_unknown, status);
        }
        return activity.getString(resource);
    }
}
