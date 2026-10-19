package pl.vistula.checker.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import pl.vistula.checker.MainActivity;
import pl.vistula.checker.R;
import pl.vistula.checker.core.CourseTestRecord;
import pl.vistula.checker.core.GradeBrowser;
import pl.vistula.checker.core.GradeSemesters;
import pl.vistula.checker.core.GradeSummary;
import pl.vistula.checker.core.Record;
import pl.vistula.checker.core.Snapshot;

/**
 * Controller for rendering the Grades screen, filters, course items, tests and simulator.
 */
public final class GradesScreenController {

    private GradesScreenController() {}

    public interface GradesListener {
        void onFilterChanged();
        void onOpenTask(String title);
    }

    public static void render(MainActivity activity, LinearLayout content, JSONObject d,
                              JSONObject state, boolean demo,
                              String semester, String gradeType, String gradeOrder, String gradeQuery,
                              boolean filtersExpanded,
                              java.util.function.Consumer<Boolean> onExpandToggled,
                              java.util.function.Consumer<String> onSemesterChanged,
                              java.util.function.Consumer<String> onTypeChanged,
                              java.util.function.Consumer<String> onOrderChanged,
                              java.util.function.Consumer<String> onQueryChanged,
                              Runnable onResetFilters, Runnable onRefresh,
                              GradesListener listener) throws Exception {

        if (!demo && !state.optString("error").isEmpty()) {
            UiHelper.note(activity, content, state.optString("error"), Color.rgb(160, 48, 40));
        }

        JSONObject raw = d.optJSONObject("snapshot");
        if (raw == null) {
            UiHelper.empty(activity, content,
                activity.getString(R.string.grades_no_data),
                activity.getString(R.string.grades_no_match));
            boolean connected = !state.optString("token").isEmpty();
            content.addView(UiHelper.button(activity,
                connected ? activity.getString(R.string.grades_refresh) : activity.getString(R.string.btn_connect),
                () -> { if (connected) onRefresh.run(); else activity.navigate(4); }, true));
            return;
        }

        Snapshot snapshot = Snapshot.from(raw);
        java.time.LocalDate today = java.time.LocalDate.now(pl.vistula.checker.UsosClient.ZONE);
        List<String> terms = GradeSemesters.ordered(snapshot, today);
        String[] types = {"all", "labs", "exercises", "numeric", "other", "retakes"};
        int[] typeLabels = {R.string.grades_chip_all, R.string.grades_type_labs_label,
            R.string.grades_type_exercises_label, R.string.grades_chip_numeric, R.string.grades_chip_other, R.string.grades_chip_retakes};
        String[] orders = {"name", "recent", "value"};
        int[] orderLabels = {R.string.grades_sort_subject, R.string.grades_sort_recent, R.string.grades_sort_value};
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        String semesterLabel = semester.isEmpty() ? activity.getString(R.string.grades_all_terms)
            : snapshot.terms.containsKey(semester) && !snapshot.terms.get(semester).name.isEmpty() ? snapshot.terms.get(semester).name : semester;
        Button termPicker = ScheduleScreenController.headerButton(activity, semesterLabel + " ▾",
            () -> showSemesterPicker(activity, snapshot, semester, onSemesterChanged));
        termPicker.setId(R.id.grades_semester);
        termPicker.setTextSize(20);
        termPicker.setTextColor(activity.getColor(R.color.app_ink));
        termPicker.setTypeface(null, android.graphics.Typeface.BOLD);
        termPicker.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        termPicker.setPadding(UiHelper.dp(activity, 8), UiHelper.dp(activity, 4), UiHelper.dp(activity, 8), UiHelper.dp(activity, 4));
        termPicker.setContentDescription(activity.getString(R.string.grade_picker_semester) + ": " + semesterLabel);
        header.addView(termPicker, new LinearLayout.LayoutParams(0, -2, 1));
        Button more = ScheduleScreenController.headerIcon(activity, R.drawable.ic_schedule_more, () -> {});
        more.setId(R.id.grades_more);
        more.setContentDescription(activity.getString(R.string.grades_options));
        header.addView(more, new LinearLayout.LayoutParams(UiHelper.dp(activity, 56), -2));
        content.addView(header);
        if (snapshot.serviceErrors.containsKey("crstests")) {
            String reason = snapshot.serviceErrors.get("crstests");
            UiHelper.note(activity, content, "crstests".equals(reason)
                ? activity.getString(R.string.services_scope_required, "crstests")
                : activity.getString(R.string.services_refresh_error, reason), UiHelper.MUTED);
        }
        if (!gradeType.equals("all") || !gradeOrder.equals("name")) {
            List<String> active = new ArrayList<>();
            for (int i = 0; i < types.length; i++) if (!gradeType.equals("all") && types[i].equals(gradeType)) active.add(activity.getString(typeLabels[i]));
            for (int i = 0; i < orders.length; i++) if (!gradeOrder.equals("name") && orders[i].equals(gradeOrder)) active.add(activity.getString(orderLabels[i]));
            Button clear = ScheduleScreenController.headerButton(activity, String.join(" · ", active) + " ×", onResetFilters);
            clear.setId(R.id.grades_active_filters);
            clear.setTextSize(14);
            clear.setSelected(true);
            clear.setContentDescription(String.join(" · ", active) + ". " + activity.getString(R.string.grades_filter_reset));
            content.addView(clear);
        }
        View[] searchContainer = new View[1];
        EditText search = UiHelper.searchField(activity, content, activity.getString(R.string.grades_search_hint),
            gradeQuery, null, () -> {
                onQueryChanged.accept("");
                if (searchContainer[0] != null) {
                    ((android.view.inputmethod.InputMethodManager) activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(searchContainer[0].getWindowToken(), 0);
                    searchContainer[0].clearFocus(); searchContainer[0].setVisibility(View.GONE);
                }
            });
        View searchBox = content.getChildAt(content.getChildCount() - 1);
        searchContainer[0] = searchBox;
        searchBox.setVisibility(gradeQuery.isEmpty() ? View.GONE : View.VISIBLE);
        more.setOnClickListener(view -> {
            android.widget.PopupMenu menu = new android.widget.PopupMenu(activity, more);
            menu.getMenu().add(0, 1, 0, R.string.grades_search_action);
            android.view.SubMenu filterMenu = menu.getMenu().addSubMenu(R.string.grades_filter_type);
            for (int i = 0; i < types.length; i++) filterMenu.add(1, 10 + i, i, typeLabels[i]).setCheckable(true).setChecked(types[i].equals(gradeType));
            filterMenu.setGroupCheckable(1, true, true);
            android.view.SubMenu sortMenu = menu.getMenu().addSubMenu(R.string.grades_sort_title);
            for (int i = 0; i < orders.length; i++) sortMenu.add(2, 20 + i, i, orderLabels[i]).setCheckable(true).setChecked(orders[i].equals(gradeOrder));
            sortMenu.setGroupCheckable(2, true, true);
            List<Record> visible = GradeBrowser.select(snapshot.records.values(), semester, gradeType, gradeOrder, search.getText().toString(), snapshot);
            menu.getMenu().add(0, 2, 3, R.string.grades_summary_title).setEnabled(!visible.isEmpty());
            menu.getMenu().add(0, 3, 4, R.string.grades_calc_button);
            menu.getMenu().add(0, 4, 5, R.string.grades_refresh);
            menu.getMenu().add(0, 5, 6, R.string.grades_filter_reset).setEnabled(!semester.isEmpty() || !gradeType.equals("all")
                || !gradeOrder.equals("name") || !search.getText().toString().trim().isEmpty());
            menu.getMenu().add(0, 6, 7, R.string.grades_data_status);
            menu.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id >= 10 && id < 10 + types.length) onTypeChanged.accept(types[id - 10]);
                else if (id >= 20 && id < 20 + orders.length) onOrderChanged.accept(orders[id - 20]);
                else if (id == 1) {
                    searchBox.setVisibility(View.VISIBLE); search.requestFocus();
                    search.post(() -> ((android.view.inputmethod.InputMethodManager) activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
                        .showSoftInput(search, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT));
                } else if (id == 2) {
                    LinearLayout body = new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL);
                    for (String term : terms) {
                        List<Record> rows = GradeSemesters.inTerm(visible, term);
                        if (rows.isEmpty()) continue;
                        String label = snapshot.terms.containsKey(term) && !snapshot.terms.get(term).name.isEmpty() ? snapshot.terms.get(term).name : term;
                        UiHelper.heading(activity, body, label.isEmpty() ? activity.getString(R.string.grades_semester_unknown) : label);
                        renderGradeSummary(activity, body, rows, state, term);
                    }
                    new MaterialAlertDialogBuilder(activity).setTitle(R.string.grades_summary_title)
                        .setView(UiHelper.dialogScroll(activity, body)).setPositiveButton(R.string.btn_close, null).show();
                } else if (id == 3) activity.navigate(9);
                else if (id == 4) onRefresh.run();
                else if (id == 5) onResetFilters.run();
                else if (id == 6) new MaterialAlertDialogBuilder(activity).setTitle(R.string.grades_data_status)
                    .setMessage(activity.getString(R.string.payments_data_freshness, UiHelper.timestamp(d.optLong("successAt"))))
                    .setPositiveButton(R.string.btn_close, null).show();
                return id != 0;
            });
            menu.show();
        });

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);

        Runnable populate = () -> {
            list.removeAllViews();
            String currentQuery = search.getText().toString();
            List<Record> visible = GradeBrowser.select(snapshot.records.values(), semester, gradeType, gradeOrder, currentQuery, snapshot);
            Set<String> renderedTestIds = new HashSet<>();
            int pendingCount = 0, extraTestCount = 0;
            for (String term : terms) {
                if (!semester.isEmpty() && !semester.equals(term)) continue;
                List<Record> termRows = GradeSemesters.inTerm(visible, term);
                List<Record> pendingCourses = GradeBrowser.ungraded(snapshot, term, gradeType, currentQuery);
                pendingCount += pendingCourses.size();
                if (termRows.isEmpty() && pendingCourses.isEmpty() && (!currentQuery.trim().isEmpty() || !gradeType.equals("all"))) continue;
                String label = term.isEmpty() ? activity.getString(R.string.grades_semester_unknown)
                    : snapshot.terms.containsKey(term) && !snapshot.terms.get(term).name.isEmpty() ? snapshot.terms.get(term).name : term;
                LinearLayout section = semester.isEmpty()
                    ? semesterSection(activity, list, term, label, termRows.size() + pendingCourses.size(), true) : list;
                for (List<Record> group : GradeBrowser.groups(termRows))
                    renderCourseGroup(activity, section, group, snapshot, renderedTestIds, listener);
                for (Record course : pendingCourses) renderUngradedCourse(activity, section, course);
                if (termRows.isEmpty() && pendingCourses.isEmpty()) {
                    boolean hasGrades = snapshot.records.values().stream().anyMatch(row -> row.kind.equals("ocena") && row.term.equals(term));
                    UiHelper.note(activity, section, activity.getString(hasGrades ? R.string.grades_semester_empty : R.string.grades_semester_no_grades), UiHelper.MUTED);
                }
            }

            if (snapshot.tests != null && !snapshot.tests.isEmpty()) {
                List<CourseTestRecord> unassigned = new ArrayList<>();
                for (CourseTestRecord ctr : snapshot.tests) {
                    boolean courseVisible = visible.stream().anyMatch(r -> (!ctr.courseId.isEmpty() && ctr.courseId.equals(r.optMeta("courseId")))
                        || (!ctr.courseName.isEmpty() && ctr.courseName.equals(r.title)));
                    boolean matchesTest = pl.vistula.checker.core.StudentSearch.matches(currentQuery, ctr.courseName, ctr.testName, ctr.grade, ctr.date);
                    if (!renderedTestIds.contains(ctr.id) && (semester.isEmpty() || semester.equals(GradeSemesters.testTerm(snapshot, ctr)))
                        && (courseVisible || (gradeType.equals("all") && matchesTest))) {
                        unassigned.add(ctr); extraTestCount++;
                    }
                }
                if (!unassigned.isEmpty()) {
                    java.util.Map<String, List<CourseTestRecord>> grouped = new java.util.LinkedHashMap<>();
                    for (CourseTestRecord test : unassigned) {
                        String term = GradeSemesters.testTerm(snapshot, test);
                        grouped.computeIfAbsent(term == null ? "" : term, key -> new ArrayList<>()).add(test);
                    }
                    for (java.util.Map.Entry<String, List<CourseTestRecord>> group : grouped.entrySet()) {
                    String term = group.getKey();
                    String heading = term.isEmpty() ? activity.getString(R.string.grades_unassigned_tests)
                        : activity.getString(R.string.grades_tests_heading) + " · " + (snapshot.terms.containsKey(term) ? snapshot.terms.get(term).name : term);
                    LinearLayout testsSection = semesterSection(activity, list, "\u0000tests" + term, heading, group.getValue().size(), !term.isEmpty());
                    for (CourseTestRecord ctr : group.getValue()) {
                        LinearLayout testBox = UiHelper.section(activity, testsSection, Color.WHITE);
                        UiHelper.add(activity, testBox, ctr.testName, 17, UiHelper.INK, true);
                        UiHelper.add(activity, testBox, ctr.courseName, 13, UiHelper.GREEN, true);
                        if (ctr.maxPoints > 0) {
                            UiHelper.add(activity, testBox, String.format(Language.currentLocale(activity), activity.getString(R.string.grades_points_format), ctr.points, ctr.maxPoints, ctr.percentage()), 15, UiHelper.INK, false);
                        } else if (!Double.isNaN(ctr.points) && ctr.points > 0) {
                            UiHelper.add(activity, testBox, String.format(Language.currentLocale(activity), activity.getString(R.string.grades_points_simple), ctr.points), 15, UiHelper.INK, false);
                        }
                        if (!ctr.grade.isEmpty()) {
                            UiHelper.add(activity, testBox, activity.getString(R.string.grades_test_grade, ctr.grade), 14, UiHelper.GREEN, true);
                        }
                        if (!ctr.date.isEmpty()) {
                            UiHelper.add(activity, testBox, activity.getString(R.string.grades_test_date, ctr.date), 12, UiHelper.MUTED, false);
                        }
                    }
                    }
                }
            }

            if (visible.isEmpty() && pendingCount == 0 && extraTestCount == 0 && (terms.isEmpty() || !currentQuery.trim().isEmpty())) {
                LinearLayout box = UiHelper.card(activity, list, Color.WHITE);
                UiHelper.add(activity, box,
                    currentQuery.trim().isEmpty() ? activity.getString(R.string.grades_empty_title) : activity.getString(R.string.grades_no_match),
                    17, UiHelper.INK, true);
                UiHelper.note(activity, box, activity.getString(R.string.grades_no_match_hint), UiHelper.MUTED);
            }
        };

        UiHelper.watchSearch(search, value -> {
            onQueryChanged.accept(value);
            populate.run();
        });
        populate.run();

    }

    private static void renderUngradedCourse(MainActivity activity, LinearLayout parent, Record course) {
        View row = LayoutInflater.from(activity).inflate(R.layout.item_grade_card, parent, false);
        ((TextView) row.findViewById(R.id.grade_item_course_name)).setText(course.title);
        TextView value = row.findViewById(R.id.grade_item_value_badge);
        value.setText("—"); value.setTextColor(activity.getColor(R.color.app_muted));
        ((TextView) row.findViewById(R.id.grade_item_course_meta)).setText(R.string.grades_no_grade);
        row.setOnClickListener(view -> {
            LinearLayout body = new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(UiHelper.dp(activity, 24), 0, UiHelper.dp(activity, 24), UiHelper.dp(activity, 16));
            UiHelper.add(activity, body, activity.getString(R.string.grades_no_grade), 15, UiHelper.MUTED, false);
            if (!course.optMeta("courseId").isEmpty()) UiHelper.add(activity, body, course.optMeta("courseId"), 14, UiHelper.INK, false);
            if (Double.isFinite(course.ects)) UiHelper.add(activity, body, activity.getString(R.string.grade_ects_points,
                String.format(Language.currentLocale(activity), "%s", course.ects)), 14, UiHelper.INK, false);
            new MaterialAlertDialogBuilder(activity).setTitle(course.title).setView(body).setPositiveButton(R.string.btn_close, null).show();
        });
        androidx.core.view.ViewCompat.setScreenReaderFocusable(row, true);
        parent.addView(row);
    }

    private static LinearLayout semesterSection(MainActivity activity, LinearLayout parent, String key,
                                                String title, int count, boolean defaultExpanded) {
        LinearLayout section = new LinearLayout(activity);
        section.setOrientation(LinearLayout.VERTICAL);
        boolean[] expanded = {activity.gradeSemesterExpansion().getOrDefault(key, defaultExpanded)};
        Button[] header = new Button[1];
        Runnable update = () -> {
            section.setVisibility(expanded[0] ? View.VISIBLE : View.GONE);
            header[0].setText(title + (expanded[0] ? " ▾" : " ▸"));
            header[0].setContentDescription(activity.getString(R.string.grades_semester_toggle,
                title, count, activity.getString(expanded[0] ? R.string.grades_semester_collapse : R.string.grades_semester_expand)));
        };
        header[0] = UiHelper.button(activity, "", () -> {
            expanded[0] = !expanded[0];
            activity.gradeSemesterExpansion().put(key, expanded[0]);
            update.run();
        }, false);
        header[0].setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
        header[0].setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        header[0].setTextSize(13);
        header[0].setTextColor(activity.getColor(R.color.app_muted));
        header[0].setPadding(UiHelper.dp(activity, 8), 0, UiHelper.dp(activity, 8), 0);
        header[0].setMaxLines(Integer.MAX_VALUE);
        androidx.core.view.ViewCompat.setAccessibilityHeading(header[0], true);
        parent.addView(header[0]);
        parent.addView(section);
        update.run();
        return section;
    }

    private static void showSemesterPicker(MainActivity activity, Snapshot snapshot, String selected,
                                           java.util.function.Consumer<String> onSelected) {
        List<String> values = new ArrayList<>(); values.add("");
        for (String term : GradeSemesters.ordered(snapshot, java.time.LocalDate.now(pl.vistula.checker.UsosClient.ZONE)))
            if (!term.isEmpty()) values.add(term);
        String[] labels = new String[values.size()];
        for (int i = 0; i < values.size(); i++) {
            String term = values.get(i);
            labels[i] = term.isEmpty() ? activity.getString(R.string.grades_all_terms)
                : snapshot.terms.containsKey(term) && !snapshot.terms.get(term).name.isEmpty() ? snapshot.terms.get(term).name : term;
        }
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.grade_picker_semester)
            .setSingleChoiceItems(labels, values.indexOf(selected), (dialog, index) -> {
                dialog.dismiss(); onSelected.accept(values.get(index));
            }).setNegativeButton(R.string.btn_close, null).show();
    }

    private static void renderCourseGroup(MainActivity activity, LinearLayout parent, List<Record> rows,
                                          Snapshot snapshot, Set<String> renderedTestIds, GradesListener listener) {
        Record first = rows.get(0);
        View row = LayoutInflater.from(activity).inflate(R.layout.item_grade_card, parent, false);
        ((TextView) row.findViewById(R.id.grade_item_course_name)).setText(first.title);
        List<String> symbols = new ArrayList<>();
        Set<String> forms = new java.util.LinkedHashSet<>();
        for (Record grade : rows) {
            symbols.add(GradeBrowser.symbol(grade));
            String form = gradeFormLabel(activity, grade, snapshot);
            if (!form.isEmpty()) forms.add(form);
        }
        TextView values = row.findViewById(R.id.grade_item_value_badge);
        android.text.SpannableStringBuilder preview = new android.text.SpannableStringBuilder();
        List<String> descriptions = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Record grade = rows.get(i);
            String form = gradeFormLabel(activity, grade, snapshot);
            int session = grade.metadata.optInt("sessionNumber", 0);
            String label = form;
            if (session > 1) label += " · " + activity.getString(R.string.grades_session_term, String.valueOf(session));
            descriptions.add(label + ": " + symbols.get(i));
            if (i >= 3) continue;
            if (i > 0) preview.append("\n");
            if (rows.size() > 1) {
                int start = preview.length();
                preview.append(label).append("  ");
                preview.setSpan(new android.text.style.RelativeSizeSpan(0.65f), start, preview.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                preview.setSpan(new android.text.style.ForegroundColorSpan(activity.getColor(R.color.app_muted)), start, preview.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                preview.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.NORMAL), start, preview.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            int symbolStart = preview.length();
            preview.append(symbols.get(i));
            preview.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), symbolStart, preview.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (rows.size() > 3) preview.append("\n+").append(String.valueOf(rows.size() - 3));
        values.setTypeface(null, android.graphics.Typeface.NORMAL);
        values.setText(preview);
        values.setMaxWidth(UiHelper.dp(activity, rows.size() > 1 ? 160 : 96));
        values.setTextSize(20);
        values.setLineSpacing(UiHelper.dp(activity, 5), 1f);
        values.setContentDescription(activity.getString(R.string.grades_values_description, String.join(", ", descriptions)));
        TextView meta = row.findViewById(R.id.grade_item_course_meta);
        meta.setText(String.join(" · ", forms));
        meta.setVisibility(rows.size() > 1 || forms.isEmpty() ? View.GONE : View.VISIBLE);
        row.setOnClickListener(view -> {
            List<Record> all = new ArrayList<>();
            for (Record candidate : snapshot.records.values()) if (candidate.kind.equals("ocena") && candidate.term.equals(first.term)
                && (!first.optMeta("courseId").isEmpty() ? first.optMeta("courseId").equals(candidate.optMeta("courseId"))
                    : first.title.equals(candidate.title))) all.add(candidate);
            showGradeGroupDetails(activity, all, snapshot, listener);
        });
        androidx.core.view.ViewCompat.setScreenReaderFocusable(row, true);
        for (CourseTestRecord test : snapshot.tests) if (first.term.equals(GradeSemesters.testTerm(snapshot, test))
            && ((!first.optMeta("courseId").isEmpty() && first.optMeta("courseId").equals(test.courseId)) || first.title.equals(test.courseName)))
            renderedTestIds.add(test.id);
        parent.addView(row);
    }

    private static String gradeFormLabel(MainActivity activity, Record row, Snapshot snapshot) {
        String label = GradeBrowser.classType(row, snapshot);
        if (label.equals("Ocena z protokołu przedmiotu")) return activity.getString(R.string.grades_scope_course);
        if (label.equals("Ocena z protokołu zajęć")) return activity.getString(R.string.grades_scope_unit);
        if (label.equals("Typ zajęć niepodany")) return activity.getString(R.string.grades_type_unknown);
        return label;
    }

    private static void showGradeGroupDetails(MainActivity activity, List<Record> rows, Snapshot snapshot, GradesListener listener) {
        LinearLayout body = new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(UiHelper.dp(activity, 20), UiHelper.dp(activity, 8), UiHelper.dp(activity, 20), UiHelper.dp(activity, 8));
        StringBuilder copied = new StringBuilder();
        for (Record row : rows) {
            UiHelper.heading(activity, body, GradeBrowser.symbol(row) + " · " + GradeBrowser.classType(row, snapshot));
            for (java.util.Map.Entry<String, String> field : pl.vistula.checker.core.RecordDetails.fields(row, snapshot).entrySet()) {
                UiHelper.detailField(activity, body, field.getKey(), field.getValue());
                copied.append(field.getKey()).append(": ").append(field.getValue()).append("\n");
            }
            try {
                String raw = row.json().toString(2);
                copied.append(raw).append("\n\n");
                TextView rawView = UiHelper.text(activity, raw, 12, UiHelper.INK, false);
                rawView.setTypeface(android.graphics.Typeface.MONOSPACE); rawView.setTextIsSelectable(true); rawView.setVisibility(View.GONE);
                body.addView(UiHelper.button(activity, activity.getString(R.string.grades_full_data), () ->
                    rawView.setVisibility(rawView.getVisibility() == View.GONE ? View.VISIBLE : View.GONE), false));
                body.addView(rawView);
            } catch (Exception ignored) {}
        }
        Set<String> courseIds = new HashSet<>(); for (Record row : rows) if (!row.optMeta("courseId").isEmpty()) courseIds.add(row.optMeta("courseId"));
        for (CourseTestRecord test : snapshot.tests) if ((courseIds.contains(test.courseId) || rows.get(0).title.equals(test.courseName))
                && rows.get(0).term.equals(GradeSemesters.testTerm(snapshot, test))) {
            UiHelper.heading(activity, body, test.testName);
            try {
                if (test.maxPoints > 0) UiHelper.add(activity, body, String.format(Language.currentLocale(activity),
                    activity.getString(R.string.grades_points_format), test.points, test.maxPoints, test.percentage()), 15, UiHelper.INK, false);
                if (!test.grade.isEmpty()) UiHelper.add(activity, body, activity.getString(R.string.grades_test_grade, test.grade), 15, UiHelper.INK, true);
                if (!test.date.isEmpty()) UiHelper.note(activity, body, activity.getString(R.string.grades_test_date, test.date), UiHelper.MUTED);
                TextView text = UiHelper.text(activity, test.json().toString(2), 13, UiHelper.INK, false);
                text.setTextIsSelectable(true); text.setVisibility(View.GONE);
                body.addView(UiHelper.button(activity, activity.getString(R.string.grades_full_data), () ->
                    text.setVisibility(text.getVisibility() == View.GONE ? View.VISIBLE : View.GONE), false));
                body.addView(text); copied.append(test.json().toString(2)).append("\n");
            } catch (Exception ignored) {}
        }
        androidx.appcompat.app.AlertDialog[] detailDialog = new androidx.appcompat.app.AlertDialog[1];
        Button copy = UiHelper.button(activity, activity.getString(R.string.services_copy), () ->
            UiHelper.copyToClipboard(activity, rows.get(0).title, copied.toString()), false);
        body.addView(copy, 0);
        Button task = UiHelper.button(activity, activity.getString(R.string.grades_btn_add_task), () -> {
            detailDialog[0].dismiss(); listener.onOpenTask(rows.get(0).title);
        }, false);
        body.addView(task, 1);
        detailDialog[0] = new MaterialAlertDialogBuilder(activity).setTitle(rows.get(0).title)
            .setView(UiHelper.dialogScroll(activity, body)).setPositiveButton(R.string.btn_close, null).create();
        detailDialog[0].show();
    }

    private static void renderGradeSummary(MainActivity activity, LinearLayout parent, List<Record> rows,
                                           JSONObject state, String currentSemester) {
        if (rows.isEmpty()) return;
        GradeSummary summary = GradeSummary.of(rows);
        LinearLayout box = UiHelper.card(activity, parent, Color.rgb(232, 243, 236));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(UiHelper.text(activity, activity.getString(R.string.grades_summary_title), 16, UiHelper.INK, true), new LinearLayout.LayoutParams(0, -2, 1));

        Button help = UiHelper.button(activity, "?", () -> {
            String statistics = summary.numeric == 0 ? activity.getString(R.string.grades_no_numeric_stats) : String.format(Language.currentLocale(activity), activity.getString(R.string.grades_stats_simple), summary.average, summary.min, summary.max);
            if (!Double.isNaN(summary.ectsAverage)) {
                statistics += String.format(Language.currentLocale(activity), activity.getString(R.string.grades_stats_weighted), summary.ectsAverage, summary.totalEcts);
            }
            new MaterialAlertDialogBuilder(activity).setTitle(activity.getString(R.string.grades_summary_title)).setMessage(statistics
                + activity.getString(R.string.grades_stats_disclaimer)).setPositiveButton(activity.getString(R.string.btn_understand), null).show();
        }, false);
        help.setMinWidth(0);
        help.setPadding(0, 0, 0, 0);
        header.addView(help, new LinearLayout.LayoutParams(UiHelper.dp(activity, 48), UiHelper.dp(activity, 48)));
        box.addView(header);

        LinearLayout values = new LinearLayout(activity);
        values.setGravity(Gravity.CENTER_VERTICAL);
        values.setPadding(0, UiHelper.dp(activity, 8), 0, UiHelper.dp(activity, 8));

        TextView average = UiHelper.text(activity, summary.numeric == 0 ? "—" : String.format(Language.currentLocale(activity), "%.2f", summary.average), 32, UiHelper.GREEN, true);
        average.setPadding(0, 0, UiHelper.dp(activity, 16), 0);
        values.addView(average, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout counts = new LinearLayout(activity);
        counts.setOrientation(LinearLayout.VERTICAL);
        counts.addView(UiHelper.text(activity, summary.numeric == 0 ? activity.getString(R.string.grades_card_no_numeric) : activity.getString(R.string.grades_card_simple_avg), 12, UiHelper.INK, false));
        if (!Double.isNaN(summary.ectsAverage)) {
            counts.addView(UiHelper.text(activity, String.format(Language.currentLocale(activity), activity.getString(R.string.grades_card_weighted_avg), summary.ectsAverage, summary.totalEcts), 13, UiHelper.GREEN, true));
        }
        counts.addView(UiHelper.text(activity, activity.getString(R.string.grades_card_counts, summary.numeric, summary.other), 13, UiHelper.MUTED, false));
        values.addView(counts, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(values);

        LinearLayout summaryButtons = new LinearLayout(activity);
        summaryButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button calcBtn = UiHelper.button(activity, activity.getString(R.string.grades_calc_goals), () -> showGradeSimulatorDialog(activity, summary), false);
        Button shareBtn = UiHelper.button(activity, activity.getString(R.string.grades_share_report), () -> shareGradesReport(activity, rows, summary, state, currentSemester), false);
        calcBtn.setMinHeight(UiHelper.dp(activity, 48));
        calcBtn.setTextSize(12);
        shareBtn.setMinHeight(UiHelper.dp(activity, 48));
        shareBtn.setTextSize(12);
        LinearLayout.LayoutParams bp1 = new LinearLayout.LayoutParams(0, -2, 1);
        bp1.setMarginEnd(UiHelper.dp(activity, 6));
        summaryButtons.addView(calcBtn, bp1);
        summaryButtons.addView(shareBtn, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(summaryButtons);

        UiHelper.note(activity, box, activity.getString(R.string.grades_card_note), UiHelper.MUTED);
    }

    private static void shareGradesReport(MainActivity activity, List<Record> rows, GradeSummary summary,
                                          JSONObject state, String currentSemester) {
        if (rows == null || rows.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        sb.append(activity.getString(R.string.grades_share_header));
        String studentNr = state.optString("studentNumber", "");
        if (!studentNr.isEmpty()) sb.append(activity.getString(R.string.account_album_number, studentNr)).append("\n");
        sb.append(activity.getString(R.string.grades_share_term, currentSemester.isEmpty() ? activity.getString(R.string.grades_all_terms) : currentSemester)).append("\n");
        if (!Double.isNaN(summary.ectsAverage)) {
            sb.append(String.format(Language.currentLocale(activity), activity.getString(R.string.grades_share_weighted), summary.ectsAverage, summary.totalEcts));
        }
        if (summary.numeric > 0) {
            sb.append(String.format(Language.currentLocale(activity), activity.getString(R.string.grades_share_arithmetic), summary.average));
        }
        sb.append(activity.getString(R.string.grades_share_courses_header));
        for (List<Record> group : GradeBrowser.groups(rows)) {
            Record first = group.get(0);
            sb.append("• ").append(first.title).append(":\n");
            for (Record r : group) {
                sb.append("   - ").append(GradeBrowser.classType(r, null)).append(": ")
                  .append(GradeBrowser.symbol(r)).append("\n");
            }
        }
        sb.append("\n").append(activity.getString(R.string.share_generated_by));

        Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.grades_share_subject));
        share.putExtra(Intent.EXTRA_TEXT, sb.toString());
        try {
            activity.startActivity(Intent.createChooser(share, activity.getString(R.string.grades_share_title)));
        } catch (ActivityNotFoundException e) {
            UiHelper.copyToClipboard(activity, activity.getString(R.string.grades_clipboard_label), sb.toString());
        }
    }

    private static void showGradeSimulatorDialog(MainActivity activity, GradeSummary summary) {
        LinearLayout dialogLayout = new LinearLayout(activity);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(UiHelper.dp(activity, 24), UiHelper.dp(activity, 16), UiHelper.dp(activity, 24), UiHelper.dp(activity, 8));

        double currentAvg = Double.isNaN(summary.ectsAverage) ? summary.average : summary.ectsAverage;
        String currentAvgStr = Double.isNaN(currentAvg) ? activity.getString(R.string.grades_sim_current_none) : String.format(Language.currentLocale(activity), "%.2f", currentAvg);

        TextView currentLabel = UiHelper.text(activity, activity.getString(R.string.grades_sim_current, currentAvgStr), 14, UiHelper.INK, true);
        dialogLayout.addView(currentLabel);

        EditText targetField = UiHelper.field(activity, dialogLayout, activity.getString(R.string.grades_sim_target_hint), "4.50", false);
        EditText countField = UiHelper.field(activity, dialogLayout, activity.getString(R.string.grades_sim_count_hint), "2", false);
        countField.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

        EditText ectsField = UiHelper.field(activity, dialogLayout, activity.getString(R.string.grades_sim_ects_hint), "6", false);
        ectsField.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);

        TextView resultView = UiHelper.text(activity, activity.getString(R.string.grades_sim_intro), 13, UiHelper.GREEN, true);
        resultView.setPadding(0, UiHelper.dp(activity, 8), 0, UiHelper.dp(activity, 16));
        dialogLayout.addView(resultView);

        Runnable compute = () -> {
            try {
                double target = Double.parseDouble(targetField.getText().toString().replace(',', '.'));
                int count = Integer.parseInt(countField.getText().toString());
                double reqSimple = summary.requiredAverage(target, count);

                double ectsPoints = Double.parseDouble(ectsField.getText().toString().replace(',', '.'));
                double reqEcts = summary.requiredEctsGrade(target, ectsPoints);

                StringBuilder sb = new StringBuilder();
                if (!Double.isNaN(reqSimple)) {
                    sb.append(String.format(Language.currentLocale(activity), activity.getString(R.string.grades_sim_req_simple), count, reqSimple));
                    if (reqSimple > 5.0) sb.append(activity.getString(R.string.grades_sim_impossible));
                    else if (reqSimple <= 3.0) sb.append(activity.getString(R.string.grades_sim_already_reached));
                }
                if (!Double.isNaN(reqEcts) && !Double.isNaN(summary.ectsAverage)) {
                    if (sb.length() > 0) sb.append("\n\n");
                    sb.append(String.format(Language.currentLocale(activity), activity.getString(R.string.grades_sim_req_weighted), ectsPoints, reqEcts));
                    if (reqEcts > 5.0) sb.append(activity.getString(R.string.grades_sim_impossible));
                    else if (reqEcts <= 3.0) sb.append(activity.getString(R.string.grades_sim_already_reached));
                }
                resultView.setText(sb.toString());
            } catch (Exception e) {
                resultView.setText(activity.getString(R.string.grades_sim_input_error));
            }
        };

        targetField.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { compute.run(); }
            public void afterTextChanged(Editable s) {}
        });
        countField.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { compute.run(); }
            public void afterTextChanged(Editable s) {}
        });
        ectsField.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { compute.run(); }
            public void afterTextChanged(Editable s) {}
        });
        compute.run();

        new MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.grades_simulator_title))
            .setView(dialogLayout)
            .setPositiveButton(activity.getString(R.string.btn_close), null)
            .show();
    }
}
