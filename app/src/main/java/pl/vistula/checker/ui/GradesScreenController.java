package pl.vistula.checker.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
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
        UiHelper.title(activity, content, activity.getString(R.string.grades_heading), activity.getString(R.string.grades_semester_overview));
        UiHelper.note(activity, content, activity.getString(R.string.payments_data_freshness, UiHelper.timestamp(d.optLong("successAt"))), UiHelper.MUTED);

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
        String currentTerm = GradeSemesters.current(snapshot, today);
        List<String> terms = GradeSemesters.ordered(snapshot, today);
        Button options = UiHelper.button(activity,
            filtersExpanded ? activity.getString(R.string.grades_filter_collapse) : activity.getString(R.string.grades_filter_expand),
            () -> onExpandToggled.accept(!filtersExpanded), false);
        content.addView(options);

        if (filtersExpanded) {
            renderSemesterPicker(activity, content, snapshot, semester, onSemesterChanged);
            UiHelper.chips(activity, content,
                new String[]{"all", "labs", "exercises", "numeric", "other", "retakes"},
                new String[]{
                    activity.getString(R.string.grades_chip_all),
                    activity.getString(R.string.grades_chip_labs),
                    activity.getString(R.string.grades_chip_exercises),
                    activity.getString(R.string.grades_chip_numeric),
                    activity.getString(R.string.grades_chip_other),
                    activity.getString(R.string.grades_chip_retakes)
                },
                gradeType, onTypeChanged);

            UiHelper.note(activity, content, activity.getString(R.string.grades_sort_title), UiHelper.MUTED);
            UiHelper.chips(activity, content,
                new String[]{"name", "recent", "value"},
                new String[]{activity.getString(R.string.grades_sort_subject), activity.getString(R.string.grades_sort_recent), activity.getString(R.string.grades_sort_value)},
                gradeOrder, onOrderChanged);
        } else {
            String typeLabel = gradeType.equals("labs") ? activity.getString(R.string.grades_type_labs_label)
                : gradeType.equals("exercises") ? activity.getString(R.string.grades_type_exercises_label)
                : gradeType.equals("numeric") ? activity.getString(R.string.grades_type_numeric_label)
                : gradeType.equals("other") ? activity.getString(R.string.grades_type_other_label)
                : gradeType.equals("retakes") ? activity.getString(R.string.grades_chip_retakes)
                : activity.getString(R.string.grades_type_all_label);
            UiHelper.note(activity, content,
                (semester.isEmpty() ? activity.getString(R.string.grades_all_terms) : semester) + " · "
                    + typeLabel + " · "
                    + (gradeOrder.equals("recent") ? activity.getString(R.string.grades_order_recent_label) : gradeOrder.equals("value") ? activity.getString(R.string.grades_order_value_label) : activity.getString(R.string.grades_order_subject_label)),
                UiHelper.MUTED);
        }

        EditText search = UiHelper.searchField(activity, content,
            activity.getString(R.string.grades_search_hint),
            gradeQuery, activity.getString(R.string.grades_search_desc),
            () -> onQueryChanged.accept(""));

        Button resetGrades = UiHelper.button(activity, activity.getString(R.string.grades_filter_reset), onResetFilters, false);
        content.addView(resetGrades);

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);

        Runnable populate = () -> {
            list.removeAllViews();
            String currentQuery = search.getText().toString();
            List<Record> visible = GradeBrowser.select(snapshot.records.values(), semester, gradeType, gradeOrder, currentQuery, snapshot);
            Set<String> renderedTestIds = new HashSet<>();
            int pendingCount = 0;
            for (String term : terms) {
                if (!semester.isEmpty() && !semester.equals(term)) continue;
                List<Record> termRows = GradeSemesters.inTerm(visible, term);
                List<Record> pendingCourses = GradeBrowser.ungraded(snapshot, term, gradeType, currentQuery);
                pendingCount += pendingCourses.size();
                String label = term.isEmpty() ? activity.getString(R.string.grades_semester_unknown)
                    : snapshot.terms.containsKey(term) && !snapshot.terms.get(term).name.isEmpty() ? snapshot.terms.get(term).name : term;
                if (term.equals(currentTerm)) label = activity.getString(R.string.grades_semester_current, label);
                LinearLayout section = semesterSection(activity, list, term, label, termRows.size() + pendingCourses.size(), true);
                renderGradeSummary(activity, section, termRows, state, label);
                for (List<Record> group : GradeBrowser.groups(termRows))
                    renderCourseGroup(activity, section, group, snapshot, renderedTestIds, listener);
                for (Record course : pendingCourses) renderUngradedCourse(activity, section, course);
                if (termRows.isEmpty() && pendingCourses.isEmpty()) {
                    boolean hasGrades = snapshot.records.values().stream().anyMatch(row -> row.kind.equals("ocena") && row.term.equals(term));
                    UiHelper.note(activity, section, activity.getString(hasGrades ? R.string.grades_semester_empty : R.string.grades_semester_no_grades), UiHelper.MUTED);
                }
                else if (!termRows.isEmpty()) UiHelper.note(activity, section, activity.getString(R.string.grades_count_summary,
                    termRows.size(), GradeBrowser.groups(termRows).size()), UiHelper.MUTED);
            }

            if (snapshot.tests != null && !snapshot.tests.isEmpty()) {
                List<CourseTestRecord> unassigned = new ArrayList<>();
                for (CourseTestRecord ctr : snapshot.tests) {
                    boolean courseVisible = visible.stream().anyMatch(r -> (!ctr.courseId.isEmpty() && ctr.courseId.equals(r.optMeta("courseId")))
                        || (!ctr.courseName.isEmpty() && ctr.courseName.equals(r.title)));
                    boolean matchesTest = pl.vistula.checker.core.StudentSearch.matches(currentQuery, ctr.courseName, ctr.testName, ctr.grade, ctr.date);
                    if (!renderedTestIds.contains(ctr.id) && (courseVisible || (semester.isEmpty() && gradeType.equals("all") && matchesTest))) {
                        unassigned.add(ctr);
                    }
                }
                if (!unassigned.isEmpty()) {
                    LinearLayout testsSection = semesterSection(activity, list, "\u0000tests", activity.getString(R.string.grades_unassigned_tests), unassigned.size(), false);
                    for (CourseTestRecord ctr : unassigned) {
                        LinearLayout testBox = UiHelper.card(activity, testsSection, Color.WHITE);
                        UiHelper.add(activity, testBox, ctr.testName, 17, UiHelper.INK, true);
                        UiHelper.add(activity, testBox, ctr.courseName, 13, UiHelper.GREEN, true);
                        if (ctr.maxPoints > 0) {
                            UiHelper.add(activity, testBox, String.format(Locale.forLanguageTag("pl-PL"), activity.getString(R.string.grades_points_format), ctr.points, ctr.maxPoints, ctr.percentage()), 15, UiHelper.INK, false);
                        } else if (!Double.isNaN(ctr.points) && ctr.points > 0) {
                            UiHelper.add(activity, testBox, String.format(Locale.forLanguageTag("pl-PL"), activity.getString(R.string.grades_points_simple), ctr.points), 15, UiHelper.INK, false);
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

            resetGrades.setVisibility(!semester.isEmpty() || !gradeType.equals("all") || !currentQuery.trim().isEmpty() || !gradeOrder.equals("name") ? View.VISIBLE : View.GONE);
            if (visible.isEmpty() && pendingCount == 0 && (terms.isEmpty() || !currentQuery.trim().isEmpty())) {
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

        content.addView(UiHelper.button(activity, activity.getString(R.string.grades_refresh), onRefresh, false));
        content.addView(UiHelper.button(activity, activity.getString(R.string.grades_calc_button), () -> activity.navigate(9), false));
    }

    private static void renderUngradedCourse(MainActivity activity, LinearLayout parent, Record course) {
        View card = LayoutInflater.from(activity).inflate(R.layout.item_grade_card, parent, false);
        ((TextView) card.findViewById(R.id.grade_item_course_name)).setText(course.title);
        TextView badge = card.findViewById(R.id.grade_item_value_badge);
        badge.setText(R.string.grades_no_grade);
        badge.setTextSize(13);
        badge.setTextColor(UiHelper.foreground(activity, UiHelper.MUTED));
        ((TextView) card.findViewById(R.id.grade_item_course_meta)).setText(course.optMeta("courseId"));
        TextView ects = card.findViewById(R.id.grade_item_ects);
        if (Double.isFinite(course.ects)) ects.setText(activity.getString(R.string.grade_ects_points,
            String.format(Locale.forLanguageTag("pl-PL"), "%s", course.ects)));
        else ects.setVisibility(View.GONE);
        card.findViewById(R.id.grade_item_btn_details).setVisibility(View.GONE);
        parent.addView(card);
    }

    private static LinearLayout semesterSection(MainActivity activity, LinearLayout parent, String key,
                                                String title, int count, boolean defaultExpanded) {
        LinearLayout section = new LinearLayout(activity);
        section.setOrientation(LinearLayout.VERTICAL);
        boolean[] expanded = {activity.gradeSemesterExpansion().getOrDefault(key, defaultExpanded)};
        Button[] header = new Button[1];
        Runnable update = () -> {
            section.setVisibility(expanded[0] ? View.VISIBLE : View.GONE);
            header[0].setText((expanded[0] ? "▾ " : "▸ ") + activity.getString(R.string.grades_semester_toggle,
                title, count, activity.getString(expanded[0] ? R.string.grades_semester_collapse : R.string.grades_semester_expand)));
            header[0].setContentDescription(activity.getString(R.string.grades_semester_toggle,
                title, count, activity.getString(expanded[0] ? R.string.grades_semester_collapse : R.string.grades_semester_expand)));
        };
        header[0] = UiHelper.button(activity, "", () -> {
            expanded[0] = !expanded[0];
            activity.gradeSemesterExpansion().put(key, expanded[0]);
            update.run();
        }, false);
        header[0].setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        header[0].setMaxLines(Integer.MAX_VALUE);
        androidx.core.view.ViewCompat.setAccessibilityHeading(header[0], true);
        parent.addView(header[0]);
        parent.addView(section);
        update.run();
        return section;
    }

    private static void renderSemesterPicker(MainActivity activity, LinearLayout content, Snapshot snapshot,
                                             String currentSemester, java.util.function.Consumer<String> onSelected) {
        List<String> terms = new ArrayList<>(GradeSemesters.ordered(snapshot, java.time.LocalDate.now(pl.vistula.checker.UsosClient.ZONE)));
        terms.remove("");

        final List<String> values = new ArrayList<>();
        values.add("");
        values.addAll(terms);
        List<String> labels = new ArrayList<>();
        labels.add(activity.getString(R.string.grades_all_terms));
        labels.addAll(terms);

        UiHelper.note(activity, content, activity.getString(R.string.grade_picker_semester), UiHelper.MUTED);
        Spinner picker = new Spinner(activity);
        picker.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, labels));
        picker.setSelection(Math.max(0, values.indexOf(currentSemester)));
        content.addView(picker, new LinearLayout.LayoutParams(-1, UiHelper.dp(activity, 56)));
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = values.get(position);
                if (!currentSemester.equals(selected)) {
                    onSelected.accept(selected);
                }
            }
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private static void renderCourseGroup(MainActivity activity, LinearLayout parent, List<Record> rows,
                                          Snapshot snapshot, Set<String> renderedTestIds, GradesListener listener) {
        Record first = rows.get(0);
        LayoutInflater inflater = LayoutInflater.from(activity);
        View card = inflater.inflate(R.layout.item_grade_card, parent, false);

        TextView courseName = card.findViewById(R.id.grade_item_course_name);
        TextView valueBadge = card.findViewById(R.id.grade_item_value_badge);
        TextView courseMeta = card.findViewById(R.id.grade_item_course_meta);
        TextView ectsView = card.findViewById(R.id.grade_item_ects);
        TextView commentView = card.findViewById(R.id.grade_item_comment);
        TextView componentsTitle = card.findViewById(R.id.grade_item_components_title);
        LinearLayout componentsContainer = card.findViewById(R.id.grade_item_components_container);
        TextView testsTitle = card.findViewById(R.id.grade_item_tests_title);
        LinearLayout testsContainer = card.findViewById(R.id.grade_item_tests_container);
        MaterialButton btnDetails = card.findViewById(R.id.grade_item_btn_details);

        courseName.setText(first.title);

        Record topRecord = first;
        for (Record r : rows) {
            String scope = r.optMeta("gradeScope");
            String ct = GradeBrowser.classType(r, snapshot).toLowerCase(Locale.ROOT);
            if ("course".equals(scope) || r.metadata.optBoolean("alsoCourseProtocol")) {
                topRecord = r;
                break;
            }
        }
        String topSymbol = GradeBrowser.symbol(topRecord);
        // A course can have several units and exam sessions. Do not present one arbitrary entry as its final grade.
        boolean multipleEntries = rows.size() > 1;
        valueBadge.setText(multipleEntries ? activity.getString(R.string.grades_group_entry_count, rows.size()) : topSymbol);
        valueBadge.setTextSize(multipleEntries ? 13 : 22);
        int topBgColor = multipleEntries ? UiHelper.GREEN : getGradeBadgeColor(topRecord, topSymbol);
        GradientDrawable topBg = new GradientDrawable();
        topBg.setColor(UiHelper.background(activity, topBgColor));
        topBg.setCornerRadius(UiHelper.dp(activity, 8));
        valueBadge.setBackground(topBg);
        valueBadge.setTextColor(UiHelper.onColor(activity, UiHelper.background(activity, topBgColor)));

        String meta = (!first.term.isEmpty() ? activity.getString(R.string.grade_meta_term, first.term) : "")
            + (rows.size() > 1 ? " · " + activity.getString(R.string.grade_meta_count, rows.size()) : " · " + activity.getString(R.string.grade_meta_single));
        courseMeta.setText(meta);

        String ectsPoints = first.optMeta("ects");
        if (ectsPoints.isEmpty() && !Double.isNaN(first.ects)) {
            ectsPoints = first.ects == Math.floor(first.ects) ? String.format(Locale.forLanguageTag("pl-PL"), "%.0f", first.ects) : String.format(Locale.forLanguageTag("pl-PL"), "%.1f", first.ects);
        }
        if (!ectsPoints.isEmpty()) {
            ectsView.setVisibility(View.VISIBLE);
            ectsView.setText(activity.getString(R.string.grade_ects_points, ectsPoints));
        } else {
            ectsView.setVisibility(View.GONE);
        }

        String comment = first.optMeta("comment");
        if (!comment.isEmpty() && rows.size() == 1) {
            commentView.setVisibility(View.VISIBLE);
            commentView.setText(activity.getString(R.string.grade_comment_prefix, comment));
        } else {
            commentView.setVisibility(View.GONE);
        }

        componentsTitle.setVisibility(View.VISIBLE);
        componentsContainer.removeAllViews();
        for (Record r : rows) {
            View compView = inflater.inflate(R.layout.item_grade_component, componentsContainer, false);
            TextView typeBadge = compView.findViewById(R.id.grade_comp_type_badge);
            TextView sessionView = compView.findViewById(R.id.grade_comp_session);
            TextView valBadge = compView.findViewById(R.id.grade_comp_value);
            TextView compComment = compView.findViewById(R.id.grade_comp_comment);
            TextView compDate = compView.findViewById(R.id.grade_comp_date);

            String cType = GradeBrowser.classType(r, snapshot);
            String badgeText;
            int badgeBgColor;
            int badgeTextColor;

            String lower = cType.toLowerCase(Locale.ROOT);
            if (lower.contains("lab")) {
                badgeText = "🧪 " + cType;
                badgeBgColor = Color.rgb(224, 242, 241);
                badgeTextColor = Color.rgb(0, 121, 107);
            } else if (lower.contains("ćwicz") || lower.contains("cwicz")) {
                badgeText = "📝 " + cType;
                badgeBgColor = Color.rgb(237, 231, 246);
                badgeTextColor = Color.rgb(81, 45, 168);
            } else if (lower.contains("proj")) {
                badgeText = "💻 " + cType;
                badgeBgColor = Color.rgb(255, 243, 224);
                badgeTextColor = Color.rgb(230, 81, 0);
            } else if (lower.contains("wykł") || lower.contains("wykl")) {
                badgeText = "📚 " + cType;
                badgeBgColor = Color.rgb(232, 245, 233);
                badgeTextColor = Color.rgb(46, 125, 50);
            } else if (lower.contains("lekt")) {
                badgeText = "🗣️ " + cType;
                badgeBgColor = Color.rgb(227, 242, 253);
                badgeTextColor = Color.rgb(21, 101, 192);
            } else {
                badgeText = "🎓 " + cType;
                badgeBgColor = Color.rgb(232, 243, 236);
                badgeTextColor = Color.rgb(23, 107, 87);
            }

            typeBadge.setText(badgeText);
            GradientDrawable tBg = new GradientDrawable();
            tBg.setColor(UiHelper.background(activity, badgeBgColor));
            tBg.setCornerRadius(UiHelper.dp(activity, 8));
            typeBadge.setBackground(tBg);
            typeBadge.setTextColor(UiHelper.foreground(activity, badgeTextColor));

            String sessionNum = r.optMeta("sessionNumber");
            if (sessionNum.isEmpty()) {
                if (r.value.contains("termin 1")) sessionNum = "1";
                else if (r.value.contains("termin 2")) sessionNum = "2";
                else if (r.value.contains("termin 3")) sessionNum = "3";
            }
            if (!sessionNum.isEmpty()) {
                sessionView.setText(activity.getString(R.string.grades_session_term, sessionNum));
            } else {
                sessionView.setText(activity.getString(R.string.grades_session_unknown));
            }

            String sym = GradeBrowser.symbol(r);
            valBadge.setText(sym);
            int vBgColor = getGradeBadgeColor(r, sym);
            GradientDrawable vBg = new GradientDrawable();
            vBg.setColor(UiHelper.background(activity, vBgColor));
            vBg.setCornerRadius(UiHelper.dp(activity, 8));
            valBadge.setBackground(vBg);
            valBadge.setTextColor(UiHelper.onColor(activity, UiHelper.background(activity, vBgColor)));

            String rComment = r.optMeta("comment");
            if (!rComment.isEmpty()) {
                compComment.setVisibility(View.VISIBLE);
                compComment.setText(rComment);
            } else {
                compComment.setVisibility(View.GONE);
            }

            String modTime = !r.sourceTime.isEmpty() ? r.sourceTime : r.optMeta("dateAcquisition");
            if (!modTime.isEmpty()) {
                compDate.setVisibility(View.VISIBLE);
                compDate.setText(HistoryScreenController.formatFullDate(modTime));
            } else {
                compDate.setVisibility(View.GONE);
            }

            compView.setOnClickListener(v -> showGradeDetails(activity, r, snapshot, listener));
            componentsContainer.addView(compView);
        }

        List<CourseTestRecord> matchedTests = new ArrayList<>();
        String courseId = first.optMeta("courseId");
        if (snapshot.tests != null) {
            for (CourseTestRecord ctr : snapshot.tests) {
                boolean matchId = !courseId.isEmpty() && courseId.equalsIgnoreCase(ctr.courseId);
                boolean matchName = !first.title.isEmpty() && (first.title.equalsIgnoreCase(ctr.courseName)
                    || (ctr.courseName != null && ctr.courseName.toLowerCase(Locale.ROOT).contains(first.title.toLowerCase(Locale.ROOT))));
                if ((matchId || matchName) && first.term.equals(GradeSemesters.testTerm(snapshot, ctr))) {
                    matchedTests.add(ctr);
                    renderedTestIds.add(ctr.id);
                }
            }
        }

        if (!matchedTests.isEmpty()) {
            testsTitle.setVisibility(View.VISIBLE);
            testsContainer.removeAllViews();
            for (CourseTestRecord ctr : matchedTests) {
                View testView = inflater.inflate(R.layout.item_grade_test_subitem, testsContainer, false);
                TextView testName = testView.findViewById(R.id.grade_test_name);
                TextView testPoints = testView.findViewById(R.id.grade_test_points);
                TextView testGrade = testView.findViewById(R.id.grade_test_grade);
                TextView testDate = testView.findViewById(R.id.grade_test_date);

                testName.setText(ctr.testName);
                if (ctr.maxPoints > 0) {
                    testPoints.setText(String.format(Locale.forLanguageTag("pl-PL"), activity.getString(R.string.grades_points_format), ctr.points, ctr.maxPoints, ctr.percentage()));
                } else if (!Double.isNaN(ctr.points) && ctr.points > 0) {
                    testPoints.setText(String.format(Locale.forLanguageTag("pl-PL"), activity.getString(R.string.grades_points_simple), ctr.points));
                } else {
                    testPoints.setVisibility(View.GONE);
                }

                if (!ctr.grade.isEmpty()) {
                    testGrade.setVisibility(View.VISIBLE);
                    testGrade.setText(activity.getString(R.string.grades_test_grade, ctr.grade));
                } else {
                    testGrade.setVisibility(View.GONE);
                }

                if (!ctr.date.isEmpty()) {
                    testDate.setVisibility(View.VISIBLE);
                    testDate.setText(activity.getString(R.string.grades_test_date, ctr.date));
                } else {
                    testDate.setVisibility(View.GONE);
                }

                testsContainer.addView(testView);
            }
        } else {
            testsTitle.setVisibility(View.GONE);
            testsContainer.removeAllViews();
        }

        btnDetails.setText(R.string.grades_subject_details);
        btnDetails.setOnClickListener(v -> {
            List<Record> allCourseRows = new ArrayList<>();
            for (Record candidate : snapshot.records.values()) if (candidate.kind.equals("ocena") && candidate.term.equals(first.term)
                && (!first.optMeta("courseId").isEmpty() ? first.optMeta("courseId").equals(candidate.optMeta("courseId"))
                    : first.title.equals(candidate.title))) allCourseRows.add(candidate);
            showGradeGroupDetails(activity, allCourseRows, snapshot, listener);
        });
        parent.addView(card);
    }

    private static int getGradeBadgeColor(Record r, String sym) {
        Boolean passes = r.metadata != null && r.metadata.opt("passes") instanceof Boolean ? r.metadata.optBoolean("passes") : null;
        if (passes != null) return passes ? UiHelper.GREEN : Color.rgb(198, 40, 40);
        return Color.rgb(84, 110, 122);
    }

    private static void showGradeDetails(MainActivity activity, Record row, Snapshot snapshot, GradesListener listener) {
        showGradeGroupDetails(activity, java.util.Collections.singletonList(row), snapshot, listener);
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
        for (CourseTestRecord test : snapshot.tests) if (courseIds.contains(test.courseId)
                && rows.get(0).term.equals(GradeSemesters.testTerm(snapshot, test))) {
            UiHelper.heading(activity, body, test.testName);
            try {
                TextView text = UiHelper.text(activity, test.json().toString(2), 13, UiHelper.INK, false);
                text.setTextIsSelectable(true); body.addView(text); copied.append(test.json().toString(2)).append("\n");
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
            String statistics = summary.numeric == 0 ? activity.getString(R.string.grades_no_numeric_stats) : String.format(Locale.forLanguageTag("pl-PL"), activity.getString(R.string.grades_stats_simple), summary.average, summary.min, summary.max);
            if (!Double.isNaN(summary.ectsAverage)) {
                statistics += String.format(Locale.forLanguageTag("pl-PL"), activity.getString(R.string.grades_stats_weighted), summary.ectsAverage, summary.totalEcts);
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

        TextView average = UiHelper.text(activity, summary.numeric == 0 ? "—" : String.format(Locale.forLanguageTag("pl-PL"), "%.2f", summary.average), 32, UiHelper.GREEN, true);
        average.setPadding(0, 0, UiHelper.dp(activity, 16), 0);
        values.addView(average, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout counts = new LinearLayout(activity);
        counts.setOrientation(LinearLayout.VERTICAL);
        counts.addView(UiHelper.text(activity, summary.numeric == 0 ? activity.getString(R.string.grades_card_no_numeric) : activity.getString(R.string.grades_card_simple_avg), 12, UiHelper.INK, false));
        if (!Double.isNaN(summary.ectsAverage)) {
            counts.addView(UiHelper.text(activity, String.format(Locale.forLanguageTag("pl-PL"), activity.getString(R.string.grades_card_weighted_avg), summary.ectsAverage, summary.totalEcts), 13, UiHelper.GREEN, true));
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
            sb.append(String.format(Locale.forLanguageTag("pl-PL"), activity.getString(R.string.grades_share_weighted), summary.ectsAverage, summary.totalEcts));
        }
        if (summary.numeric > 0) {
            sb.append(String.format(Locale.forLanguageTag("pl-PL"), activity.getString(R.string.grades_share_arithmetic), summary.average));
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
        String currentAvgStr = Double.isNaN(currentAvg) ? "brak" : String.format(Locale.forLanguageTag("pl-PL"), "%.2f", currentAvg);

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
                    sb.append(String.format(Locale.forLanguageTag("pl-PL"), activity.getString(R.string.grades_sim_req_simple), count, reqSimple));
                    if (reqSimple > 5.0) sb.append(activity.getString(R.string.grades_sim_impossible));
                    else if (reqSimple <= 3.0) sb.append(activity.getString(R.string.grades_sim_already_reached));
                }
                if (!Double.isNaN(reqEcts) && !Double.isNaN(summary.ectsAverage)) {
                    if (sb.length() > 0) sb.append("\n\n");
                    sb.append(String.format(Locale.forLanguageTag("pl-PL"), activity.getString(R.string.grades_sim_req_weighted), ectsPoints, reqEcts));
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
