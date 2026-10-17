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
import pl.vistula.checker.core.AttendanceTracker;
import pl.vistula.checker.core.GradeSemesters;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.view.View;
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
        try { saved = AttendanceTracker.reconcile(saved, snapshot); }
        catch (JSONException e) { throw new IllegalStateException(e); }
        prefs.edit().putString("data", saved.toString()).apply();
        List<String> terms = new ArrayList<>();
        if (snapshot != null) terms.addAll(GradeSemesters.ordered(snapshot, java.time.LocalDate.now(pl.vistula.checker.UsosClient.ZONE)));
        java.util.Iterator<String> savedKeys = saved.keys();
        while (savedKeys.hasNext()) {
            JSONObject entry = saved.optJSONObject(savedKeys.next());
            if (entry != null && !terms.contains(entry.optString("term"))) terms.add(entry.optString("term"));
        }
        if (terms.isEmpty()) terms.add("");
        String selectedTerm = prefs.getString("term", terms.get(0));
        if (!terms.contains(selectedTerm)) selectedTerm = terms.get(0);
        final String activeTerm = selectedTerm;
        String[] termLabels = new String[terms.size()];
        for (int i = 0; i < terms.size(); i++) {
            String id = terms.get(i);
            termLabels[i] = id.isEmpty() ? activity.getString(R.string.attendance_unassigned) :
                snapshot != null && snapshot.terms.containsKey(id) ? snapshot.terms.get(id).name + " · " + id : id;
        }
        UiHelper.add(activity, content, activity.getString(R.string.attendance_semester), 14, UiHelper.MUTED, true);
        Spinner semester = new Spinner(activity);
        semester.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, termLabels));
        semester.setSelection(terms.indexOf(activeTerm));
        prefs.edit().putString("term", activeTerm).apply();
        content.addView(semester);
        semester.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String next = terms.get(position);
                if (!next.equals(activeTerm)) { prefs.edit().putString("term", next).apply(); requestRender.run(); }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        String activeType = prefs.getString("classType", "all");
        java.util.LinkedHashSet<String> types = new java.util.LinkedHashSet<>();
        types.add("all");
        savedKeys = saved.keys();
        while (savedKeys.hasNext()) {
            JSONObject entry = saved.optJSONObject(savedKeys.next());
            if (entry != null && activeTerm.equals(entry.optString("term"))) types.add(entry.optString("classType"));
        }
        if (!types.contains(activeType)) activeType = "all";
        final String selectedType = activeType;
        String[] typeValues = types.toArray(new String[0]);
        String[] typeLabels = types.stream().map(t -> t.equals("all") ? activity.getString(R.string.attendance_all_types) :
            t.isEmpty() ? activity.getString(R.string.attendance_unknown_type) : t).toArray(String[]::new);
        UiHelper.chips(activity, content, typeValues, typeLabels, selectedType,
            next -> { prefs.edit().putString("classType", next).apply(); requestRender.run(); });

        int totalCourses = 0, warningCount = 0, exceededCount = 0, totalAbsences = 0;
        Iterator<String> keys = saved.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject item = saved.optJSONObject(key);
            if (item == null) continue;
            if (!matches(item, activeTerm, selectedType)) continue;
            totalCourses++;
            int count = item.optInt("absences", 0);
            int limit = AttendanceTracker.LIMIT;
            totalAbsences += count;
            if (AttendanceTracker.exceeded(count)) exceededCount++;
            else if (AttendanceTracker.warning(count)) warningCount++;
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
            if (item == null || !matches(item, activeTerm, selectedType)) continue;
            int count = item.optInt("absences", 0);
            int limit = AttendanceTracker.LIMIT;
            String name = item.optString("name", code);
            String notes = item.optString("notes", "");

            if ("warning".equals(filter) && !AttendanceTracker.warning(count)) continue;
            if ("exceeded".equals(filter) && !AttendanceTracker.exceeded(count)) continue;

            shown++;
            LinearLayout itemCard = UiHelper.card(activity, list, Color.WHITE);
            itemCard.setClickable(true);
            itemCard.setFocusable(true);
            itemCard.setOnClickListener(v -> showEditCourseDialog(activity, code, requestRender));
            UiHelper.add(activity, itemCard, name, 17, UiHelper.INK, true);
            UiHelper.note(activity, itemCard, item.optString("classType").isEmpty() ? activity.getString(R.string.attendance_unknown_type) : item.optString("classType"), UiHelper.MUTED);

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
            progress.setTrackColor(UiHelper.background(activity, Color.rgb(230, 235, 232)));
            progress.setIndicatorColor(UiHelper.foreground(activity, badgeColor));
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
                    org.json.JSONArray lessons = item.optJSONArray("lessons");
                    if (lessons != null && lessons.length() > 0) lessons.remove(lessons.length() - 1);
                    finalSavedRef.put(code, item);
                    prefs.edit().putString("data", finalSavedRef.toString()).apply();
                    requestRender.run();
                } catch (JSONException ignored) {}
            }, false);
            minusBtn.setEnabled(count > 0);

            Button plusBtn = UiHelper.button(activity, activity.getString(R.string.attendance_btn_plus), () -> {
                try {
                    item.put("absences", count + 1);
                    org.json.JSONArray lessons = item.optJSONArray("lessons");
                    if (lessons == null) lessons = new org.json.JSONArray();
                    item.put("lessons", lessons.put(""));
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
            if (!"all".equals(filter)) {
                list.addView(UiHelper.button(activity, activity.getString(R.string.attendance_filter_all, totalCourses), () -> onFilterChanged.accept("all"), false));
            }
        }

        UiHelper.note(activity, content, activity.getString(R.string.attendance_rule_footer), UiHelper.MUTED);
    }

    private static boolean matches(JSONObject item, String term, String type) {
        return term.equals(item.optString("term")) && (type.equals("all") || type.equals(item.optString("classType")));
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
        EditText termField = UiHelper.field(activity, form, activity.getString(R.string.attendance_semester_hint),
            existing != null ? existing.optString("term") : prefs.getString("term", ""), false);
        UiHelper.add(activity, form, activity.getString(R.string.attendance_class_type), 14, UiHelper.MUTED, true);
        List<String> typeOptions = new ArrayList<>(java.util.Arrays.asList("Wykład", "Ćwiczenia", "Laboratorium", "Projekt", "Seminarium", "Lektorat", "Konwersatorium", "Inne"));
        String oldType = existing == null ? "Wykład" : existing.optString("classType");
        if (!typeOptions.contains(oldType)) typeOptions.add(oldType);
        Spinner typeField = new Spinner(activity);
        String[] typeLabels = typeOptions.stream().map(t -> t.isEmpty() ? activity.getString(R.string.attendance_unknown_type) : t).toArray(String[]::new);
        typeField.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, typeLabels));
        typeField.setSelection(Math.max(0, typeOptions.indexOf(oldType)));
        form.addView(typeField);
        UiHelper.note(activity, form, activity.getString(R.string.attendance_fixed_limit), UiHelper.MUTED);

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
            String term = termField.getText().toString().trim();
            if (term.isEmpty()) { termField.setError(activity.getString(R.string.attendance_semester_required)); return; }
            String type = typeOptions.get(typeField.getSelectedItemPosition());
            if (type.isEmpty()) {
                Toast.makeText(activity, R.string.attendance_type_required, Toast.LENGTH_SHORT).show(); return;
            }
            try {
                String course = existing != null ? existing.optString("courseId", code) : name;
                String c = AttendanceTracker.key(term, course, type);
                JSONObject target = finalSaved.optJSONObject(c);
                if (!c.equals(code) && target != null && (target.optInt("absences") > 0 || !target.optString("notes").isEmpty())) {
                    nameField.setError(activity.getString(R.string.attendance_duplicate)); return;
                }
                JSONObject item = existing != null ? existing : new JSONObject();
                item.put("name", name);
                item.put("limit", AttendanceTracker.LIMIT).put("courseId", course).put("term", term).put("classType", type);
                item.put("notes", notesField.getText() != null ? notesField.getText().toString().trim() : "");
                if (!item.has("absences")) item.put("absences", 0);
                if (code != null && !code.equals(c)) finalSaved.remove(code);
                finalSaved.put(c, item);
                prefs.edit().putString("data", finalSaved.toString()).apply();
                dialog.dismiss();
                onComplete.run();
            } catch (JSONException ignored) {}
        }));
        dialog.show();
    }
}
