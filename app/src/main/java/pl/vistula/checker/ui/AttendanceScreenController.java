package pl.vistula.checker.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pl.vistula.checker.MainActivity;
import pl.vistula.checker.R;
import pl.vistula.checker.core.Record;
import pl.vistula.checker.core.Snapshot;

/**
 * Controller for tracking course attendance and absence limits.
 */
public final class AttendanceScreenController {

    private AttendanceScreenController() {}

    public static void render(MainActivity activity, LinearLayout content, JSONObject d,
                              String filter, java.util.function.Consumer<String> onFilterChanged,
                              Runnable requestRender) {
        UiHelper.title(activity, content,
            activity.getString(R.string.attendance_title),
            activity.getString(R.string.attendance_subtitle));

        SharedPreferences prefs = activity.getSharedPreferences("attendance_tracker", Context.MODE_PRIVATE);
        JSONObject saved;
        try { saved = new JSONObject(prefs.getString("data", "{}")); } catch (Exception e) { saved = new JSONObject(); }

        JSONObject raw = d.optJSONObject("snapshot");
        Snapshot snapshot = null;
        if (raw != null) {
            try { snapshot = Snapshot.from(raw); } catch (JSONException ignored) {}
        }
        Map<String, String> courses = new LinkedHashMap<>();

        if (snapshot != null) {
            for (Record r : snapshot.records.values()) {
                String code = r.optMeta("courseId");
                if (code.isEmpty()) code = r.title;
                if (!courses.containsKey(code)) {
                    courses.put(code, r.title);
                }
            }
        }

        boolean changed = false;
        for (Map.Entry<String, String> entry : courses.entrySet()) {
            if (!saved.has(entry.getKey())) {
                JSONObject item = new JSONObject();
                try {
                    item.put("name", entry.getValue());
                    item.put("absences", 0);
                    item.put("limit", 2);
                    item.put("notes", "");
                    saved.put(entry.getKey(), item);
                    changed = true;
                } catch (JSONException ignored) {}
            }
        }
        if (changed) {
            prefs.edit().putString("data", saved.toString()).apply();
        }

        int totalCourses = 0, warningCount = 0, exceededCount = 0, totalAbsences = 0;
        Iterator<String> keys = saved.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject item = saved.optJSONObject(key);
            if (item == null) continue;
            totalCourses++;
            int count = item.optInt("absences", 0);
            int limit = item.optInt("limit", 2);
            totalAbsences += count;
            if (count >= limit) exceededCount++;
            else if (count >= limit - 1 && limit > 1) warningCount++;
        }

        LinearLayout summaryBox = UiHelper.card(activity, content,
            exceededCount > 0 ? Color.rgb(255, 238, 238) : (warningCount > 0 ? Color.rgb(255, 248, 230) : Color.rgb(232, 243, 236)));
        UiHelper.add(activity, summaryBox,
            exceededCount > 0 ? activity.getString(R.string.attendance_status_exceeded) : (warningCount > 0 ? activity.getString(R.string.attendance_status_warning) : activity.getString(R.string.attendance_status_ok)),
            11, exceededCount > 0 ? Color.rgb(160, 48, 40) : (warningCount > 0 ? Color.rgb(217, 119, 6) : UiHelper.GREEN), true);
        UiHelper.add(activity, summaryBox,
            activity.getString(R.string.attendance_stats_summary, totalCourses, totalAbsences), 15, UiHelper.INK, true);
        if (exceededCount > 0) {
            UiHelper.note(activity, summaryBox, activity.getString(R.string.attendance_note_exceeded, exceededCount), Color.rgb(160, 48, 40));
        } else if (warningCount > 0) {
            UiHelper.note(activity, summaryBox, activity.getString(R.string.attendance_note_warning, warningCount), Color.rgb(217, 119, 6));
        } else {
            UiHelper.note(activity, summaryBox, activity.getString(R.string.attendance_note_ok), UiHelper.GREEN);
        }

        UiHelper.chips(activity, content,
            new String[]{"all", "warning", "exceeded"},
            new String[]{
                activity.getString(R.string.attendance_filter_all, totalCourses),
                activity.getString(R.string.attendance_filter_warning, warningCount),
                activity.getString(R.string.attendance_filter_exceeded, exceededCount)
            }, filter, onFilterChanged);

        content.addView(UiHelper.button(activity, activity.getString(R.string.attendance_btn_add_course),
            () -> showEditCourseDialog(activity, null, requestRender), true));

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);

        List<String> sortedKeys = new ArrayList<>();
        keys = saved.keys();
        while (keys.hasNext()) sortedKeys.add(keys.next());
        final JSONObject finalSavedRef = saved;
        sortedKeys.sort((a, b) -> {
            JSONObject objA = finalSavedRef.optJSONObject(a);
            JSONObject objB = finalSavedRef.optJSONObject(b);
            String nameA = objA != null ? objA.optString("name", a) : a;
            String nameB = objB != null ? objB.optString("name", b) : b;
            return nameA.compareToIgnoreCase(nameB);
        });

        int shown = 0;
        for (String code : sortedKeys) {
            JSONObject item = saved.optJSONObject(code);
            if (item == null) continue;
            int count = item.optInt("absences", 0);
            int limit = item.optInt("limit", 2);
            String name = item.optString("name", code);
            String notes = item.optString("notes", "");

            if ("warning".equals(filter) && (count < limit - 1 || count >= limit)) continue;
            if ("exceeded".equals(filter) && count < limit) continue;

            shown++;
            LinearLayout itemCard = UiHelper.card(activity, list, Color.WHITE);
            UiHelper.add(activity, itemCard, name, 17, UiHelper.INK, true);
            if (!code.equals(name)) UiHelper.note(activity, itemCard, activity.getString(R.string.attendance_course_id, code), UiHelper.MUTED);

            String badgeText;
            int badgeColor;
            if (count > limit) {
                badgeText = activity.getString(R.string.attendance_badge_exceeded, count, limit, (count - limit));
                badgeColor = Color.rgb(160, 48, 40);
            } else if (count == limit) {
                badgeText = activity.getString(R.string.attendance_badge_limit_reached, count, limit);
                badgeColor = Color.rgb(180, 83, 9);
            } else if (count == limit - 1 && limit > 1) {
                badgeText = activity.getString(R.string.attendance_badge_last_one, count, limit);
                badgeColor = Color.rgb(217, 119, 6);
            } else {
                badgeText = activity.getString(R.string.attendance_badge_safe, count, limit, (limit - count));
                badgeColor = UiHelper.GREEN;
            }
            UiHelper.add(activity, itemCard, badgeText, 14, badgeColor, true);

            LinearProgressIndicator progress = new LinearProgressIndicator(activity);
            progress.setMax(limit > 0 ? limit : 1);
            progress.setProgress(Math.min(count, limit));
            progress.setTrackColor(Color.rgb(230, 235, 232));
            progress.setIndicatorColor(badgeColor);
            LinearLayout.LayoutParams pParams = new LinearLayout.LayoutParams(-1, UiHelper.dp(activity, 8));
            pParams.bottomMargin = UiHelper.dp(activity, 14);
            itemCard.addView(progress, pParams);

            if (!notes.isEmpty()) {
                UiHelper.add(activity, itemCard, activity.getString(R.string.attendance_notes_label, notes), 13, UiHelper.MUTED, false);
            }

            LinearLayout actions = new LinearLayout(activity);
            actions.setOrientation(LinearLayout.HORIZONTAL);

            Button minusBtn = UiHelper.button(activity, activity.getString(R.string.attendance_btn_minus), () -> {
                int newCount = Math.max(0, count - 1);
                try {
                    item.put("absences", newCount);
                    finalSavedRef.put(code, item);
                    prefs.edit().putString("data", finalSavedRef.toString()).apply();
                    requestRender.run();
                } catch (JSONException ignored) {}
            }, false);
            minusBtn.setEnabled(count > 0);

            Button plusBtn = UiHelper.button(activity, activity.getString(R.string.attendance_btn_plus), () -> {
                try {
                    item.put("absences", count + 1);
                    finalSavedRef.put(code, item);
                    prefs.edit().putString("data", finalSavedRef.toString()).apply();
                    requestRender.run();
                } catch (JSONException ignored) {}
            }, true);

            Button editBtn = UiHelper.button(activity, activity.getString(R.string.attendance_btn_options),
                () -> showEditCourseDialog(activity, code, requestRender), false);

            LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, -2, 1f); b1.setMarginEnd(UiHelper.dp(activity, 4));
            LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0, -2, 2.2f); b2.setMarginEnd(UiHelper.dp(activity, 4));
            LinearLayout.LayoutParams b3 = new LinearLayout.LayoutParams(0, -2, 1.2f);
            actions.addView(minusBtn, b1);
            actions.addView(plusBtn, b2);
            actions.addView(editBtn, b3);
            itemCard.addView(actions);
        }

        if (shown == 0) {
            UiHelper.empty(activity, list,
                activity.getString(R.string.attendance_empty_filter),
                activity.getString(R.string.attendance_empty_desc));
        }

        UiHelper.note(activity, content, activity.getString(R.string.attendance_rule_footer), UiHelper.MUTED);
    }

    public static void showEditCourseDialog(MainActivity activity, String code, Runnable onComplete) {
        SharedPreferences prefs = activity.getSharedPreferences("attendance_tracker", Context.MODE_PRIVATE);
        JSONObject saved;
        try { saved = new JSONObject(prefs.getString("data", "{}")); } catch (Exception e) { saved = new JSONObject(); }
        JSONObject existing = code != null ? saved.optJSONObject(code) : null;

        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(UiHelper.dp(activity, 24), UiHelper.dp(activity, 8), UiHelper.dp(activity, 24), 0);

        EditText nameField = UiHelper.field(activity, form, activity.getString(R.string.attendance_dialog_name_hint),
            existing != null ? existing.optString("name", code) : "", false);
        EditText limitField = UiHelper.field(activity, form, activity.getString(R.string.attendance_dialog_limit_hint),
            String.valueOf(existing != null ? existing.optInt("limit", 2) : 2), false);
        limitField.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

        EditText notesField = UiHelper.field(activity, form, activity.getString(R.string.attendance_dialog_notes_hint),
            existing != null ? existing.optString("notes", "") : "", false);
        notesField.setSingleLine(false);
        notesField.setMinLines(2);

        ScrollView formScroll = new ScrollView(activity);
        formScroll.addView(form);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
            .setTitle(existing == null ? activity.getString(R.string.attendance_dialog_add) : activity.getString(R.string.attendance_dialog_edit, existing.optString("name", code)))
            .setView(formScroll)
            .setNegativeButton(activity.getString(R.string.btn_cancel), null)
            .setPositiveButton(activity.getString(R.string.btn_save), null);

        if (existing != null) {
            final JSONObject delSaved = saved;
            builder.setNeutralButton(activity.getString(R.string.attendance_dialog_delete), (dlg, which) -> {
                delSaved.remove(code);
                prefs.edit().putString("data", delSaved.toString()).apply();
                onComplete.run();
            });
        }

        AlertDialog dialog = builder.create();
        final JSONObject finalSaved = saved;
        dialog.setOnShowListener(v -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(b -> {
            String name = nameField.getText() != null ? nameField.getText().toString().trim() : "";
            if (name.isEmpty()) {
                nameField.setError(activity.getString(R.string.attendance_dialog_error_name));
                return;
            }
            int lim;
            try {
                lim = Integer.parseInt(limitField.getText() != null ? limitField.getText().toString().trim() : "2");
                if (lim < 1 || lim > 20) throw new NumberFormatException();
            } catch (Exception e) {
                limitField.setError(activity.getString(R.string.attendance_dialog_error_limit));
                return;
            }
            try {
                String c = code != null ? code : name;
                JSONObject item = existing != null ? existing : new JSONObject();
                item.put("name", name);
                item.put("limit", lim);
                item.put("notes", notesField.getText() != null ? notesField.getText().toString().trim() : "");
                if (!item.has("absences")) item.put("absences", 0);
                finalSaved.put(c, item);
                prefs.edit().putString("data", finalSaved.toString()).apply();
                dialog.dismiss();
                onComplete.run();
            } catch (JSONException ignored) {}
        }));
        dialog.show();
    }
}
