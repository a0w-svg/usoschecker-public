package pl.vistula.checker.ui;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import pl.vistula.checker.MainActivity;
import pl.vistula.checker.R;
import pl.vistula.checker.UsosClient;
import pl.vistula.checker.core.TaskTools;
import pl.vistula.checker.core.PlatonLinks;

/**
 * Controller for rendering the Student Tasks Planner screen.
 */
public final class TasksScreenController {

    private TasksScreenController() {}

    public interface TaskCallback {
        void onSaveTask(JSONObject task);
        void onMutateTask(String id, boolean remove);
    }

    public static void render(MainActivity activity, LinearLayout content, JSONArray rows,
                              String taskFilter, String taskOrder, String taskQuery,
                              java.util.function.Consumer<String> onFilterChanged,
                              java.util.function.Consumer<String> onOrderChanged,
                              java.util.function.Consumer<String> onQueryChanged,
                              TaskCallback callback) throws JSONException {

        LocalDate today = LocalDate.now(UsosClient.ZONE);
        int pending = 0, doneCount = 0, todayCount = 0, weekCount = 0, overdueCount = 0, platonCount = 0;
        for (int i = 0; i < rows.length(); ++i) {
            JSONObject t = rows.optJSONObject(i);
            if (t == null) continue;
            if (PlatonLinks.isTaskLink(t.optString("platonUrl"))) platonCount++;
            boolean done = t.optBoolean("done");
            if (done) doneCount++; else pending++;
            LocalDate due = TaskTools.dueDate(t);
            if (!done && due != null) {
                if (due.isBefore(today)) overdueCount++;
                if (due.equals(today)) todayCount++;
                if (!due.isBefore(today) && due.isBefore(today.plusDays(7))) weekCount++;
            }
        }

        UiHelper.note(activity, content,
            activity.getString(R.string.tasks_summary, pending, doneCount),
            UiHelper.MUTED);

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        content.addView(header, content.getChildCount() - 1);
        TextView heading = new TextView(activity);
        heading.setText(R.string.nav_tasks);
        heading.setTextSize(20);
        heading.setPadding(UiHelper.dp(activity, 8), 0, 0, 0);
        androidx.core.view.ViewCompat.setAccessibilityHeading(heading, true);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setTextColor(UiHelper.foreground(activity, UiHelper.INK));
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        android.widget.Button add = ScheduleScreenController.headerButton(activity, "+", () -> {});
        add.setContentDescription(activity.getString(R.string.btn_add_task));
        add.setId(R.id.tasks_add);
        add.setOnClickListener(v -> showTaskDialog(activity, null, "", rows.length(), callback));
        header.addView(add, new LinearLayout.LayoutParams(UiHelper.dp(activity, 56), -2));
        View more = ScheduleScreenController.headerIcon(activity, R.drawable.ic_schedule_more, () -> {});
        more.setContentDescription(activity.getString(R.string.tasks_options));
        more.setId(R.id.tasks_more);
        header.addView(more, new LinearLayout.LayoutParams(UiHelper.dp(activity, 56), -2));

        String[] filterKeys = {"all", "open", "done", "platon", "today", "week", "overdue"};
        int[] filterLabels = {R.string.tasks_chip_all, R.string.tasks_chip_open, R.string.tasks_chip_done,
            R.string.tasks_platon_title, R.string.tasks_chip_today, R.string.tasks_chip_week, R.string.tasks_chip_overdue};
        int[] counts = {rows.length(), pending, doneCount, platonCount, todayCount, weekCount, overdueCount};
        String[] orderKeys = {"due", "priority", "title"};
        int[] orderLabels = {R.string.tasks_sort_due, R.string.tasks_sort_priority, R.string.tasks_sort_title};
        if (!"all".equals(taskFilter) || !"due".equals(taskOrder)) {
            String label = "";
            for (int i = 0; i < filterKeys.length; i++) if (filterKeys[i].equals(taskFilter)) label = activity.getString(filterLabels[i]);
            if (!"due".equals(taskOrder)) for (int i = 0; i < orderKeys.length; i++) if (orderKeys[i].equals(taskOrder)) label += " · " + activity.getString(orderLabels[i]);
            android.widget.Button selected = ScheduleScreenController.headerButton(activity, label + " ×", () -> {});
            selected.setContentDescription(activity.getString(R.string.grades_filter_reset));
            selected.setTextSize(12);
            selected.setOnClickListener(v -> { onOrderChanged.accept("due"); onFilterChanged.accept("all"); });
            content.addView(selected);
        }
        View[] searchContainer = new View[1];
        EditText search = UiHelper.searchField(activity, content,
            activity.getString(R.string.tasks_search_hint), taskQuery, null, () -> {
                onQueryChanged.accept("");
                if (searchContainer[0] != null) {
                    ((android.view.inputmethod.InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(searchContainer[0].getWindowToken(), 0);
                    searchContainer[0].clearFocus(); searchContainer[0].setVisibility(View.GONE);
                }
            });
        View searchBox = content.getChildAt(content.getChildCount() - 1);
        searchContainer[0] = searchBox;
        searchBox.setVisibility(taskQuery.isEmpty() ? View.GONE : View.VISIBLE);
        more.setOnClickListener(v -> {
            android.widget.PopupMenu popup = new android.widget.PopupMenu(activity, more);
            android.view.Menu menu = popup.getMenu();
            menu.add(R.string.tasks_search_action).setOnMenuItemClickListener(item -> {
                searchBox.setVisibility(View.VISIBLE); search.requestFocus();
                ((android.view.inputmethod.InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(search, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                return true;
            });
            android.view.SubMenu filters = menu.addSubMenu(R.string.tasks_filter_title);
            for (int i = 0; i < filterKeys.length; i++) {
                final String key = filterKeys[i];
                filters.add(activity.getString(filterLabels[i]) + " (" + counts[i] + ")")
                    .setCheckable(true).setChecked(key.equals(taskFilter))
                    .setOnMenuItemClickListener(item -> { onFilterChanged.accept(key); return true; });
            }
            android.view.SubMenu ordering = menu.addSubMenu(R.string.tasks_sort_note);
            for (int i = 0; i < orderKeys.length; i++) {
                final String key = orderKeys[i];
                ordering.add(orderLabels[i]).setCheckable(true).setChecked(key.equals(taskOrder))
                    .setOnMenuItemClickListener(item -> { onOrderChanged.accept(key); return true; });
            }
            menu.add(R.string.tasks_platon_open).setOnMenuItemClickListener(item -> { UiHelper.browser(activity, PlatonLinks.HOME); return true; });
            menu.add(R.string.tasks_platon_add).setOnMenuItemClickListener(item -> { showTaskDialog(activity, null, "", rows.length(), callback, true); return true; });
            menu.add(R.string.tasks_platon_title).setOnMenuItemClickListener(item -> {
                new MaterialAlertDialogBuilder(activity).setTitle(R.string.tasks_platon_title).setMessage(R.string.tasks_platon_desc).setPositiveButton(android.R.string.ok, null).show(); return true;
            });
            popup.show();
        });

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);

        List<JSONObject> sorted = TaskTools.sorted(rows, taskOrder);
        Runnable populate = () -> {
            list.removeAllViews();
            int count = 0;
            LayoutInflater inflater = LayoutInflater.from(activity);

            for (JSONObject t : sorted) {
                boolean done = t.optBoolean("done");
                LocalDate due = TaskTools.dueDate(t);
                String titleText = t.optString("title");

                if (TaskTools.matches(t, taskFilter, search.getText().toString(), today)) {
                    ++count;
                    String id = t.optString("id");
                    View card = inflater.inflate(R.layout.item_task_card, list, false);

                    TextView title = card.findViewById(R.id.task_item_title);
                    TextView dueView = card.findViewById(R.id.task_item_due);
                    android.widget.CheckBox toggle = card.findViewById(R.id.task_item_btn_toggle);
                    View options = card.findViewById(R.id.task_item_more);
                    title.setText(titleText);
                    title.setTextColor(UiHelper.foreground(activity, done ? UiHelper.MUTED : UiHelper.INK));
                    if (done) title.setPaintFlags(title.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);

                    String formattedDate = due == null ? activity.getString(R.string.tasks_due_unknown)
                        : due.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                    String statusText;
                    int statusColor;
                    if (done) {
                        statusText = activity.getString(R.string.tasks_status_done, formattedDate);
                        statusColor = UiHelper.MUTED;
                    } else if (due != null && due.isBefore(today)) {
                        statusText = activity.getString(R.string.tasks_status_overdue, formattedDate);
                        statusColor = Color.rgb(160, 48, 40);
                    } else if (today.equals(due)) {
                        statusText = activity.getString(R.string.tasks_status_today, formattedDate);
                        statusColor = UiHelper.GREEN;
                    } else {
                        statusText = activity.getString(R.string.tasks_status_due, formattedDate);
                        statusColor = UiHelper.GREEN;
                    }
                    if (!done && TaskTools.priority(t) == 2) statusText += " · " + activity.getString(R.string.tasks_priority_high);
                    if (PlatonLinks.isTaskLink(t.optString("platonUrl"))) statusText += " · Platon";
                    dueView.setText(statusText);
                    dueView.setTextColor(UiHelper.foreground(activity, statusColor));
                    card.setOnClickListener(v -> showTaskDialog(activity, t, "", rows.length(), callback));
                    toggle.setChecked(done);
                    toggle.setContentDescription(activity.getString(done ? R.string.tasks_btn_mark_open : R.string.tasks_btn_mark_done) + ": " + titleText);
                    toggle.setOnClickListener(v -> callback.onMutateTask(id, false));
                    options.setContentDescription(activity.getString(R.string.tasks_task_options, titleText));
                    options.setOnClickListener(v -> {
                        android.widget.PopupMenu popup = new android.widget.PopupMenu(activity, options);
                        popup.getMenu().add(R.string.tasks_btn_edit).setOnMenuItemClickListener(item -> { showTaskDialog(activity, t, "", rows.length(), callback); return true; });
                        popup.getMenu().add(done ? R.string.tasks_btn_mark_open : R.string.tasks_btn_mark_done)
                            .setOnMenuItemClickListener(item -> { callback.onMutateTask(id, false); return true; });
                        String url = t.optString("platonUrl");
                        if (PlatonLinks.isTaskLink(url)) popup.getMenu().add(R.string.tasks_platon_open_task)
                            .setOnMenuItemClickListener(item -> { UiHelper.browser(activity, url); return true; });
                        popup.getMenu().add(R.string.tasks_btn_delete).setOnMenuItemClickListener(item -> {
                            new MaterialAlertDialogBuilder(activity).setTitle(R.string.tasks_delete_dialog_title).setMessage(titleText)
                                .setNegativeButton(R.string.btn_cancel, null)
                                .setPositiveButton(R.string.tasks_btn_delete, (dialog, which) -> callback.onMutateTask(id, true)).show(); return true;
                        });
                        popup.show();
                    });

                    list.addView(card);
                }
            }

            if (count == 0) {
                LinearLayout emptyBox = UiHelper.card(activity, list, Color.WHITE);
                UiHelper.add(activity, emptyBox,
                    rows.length() == 0 ? activity.getString(R.string.tasks_empty_title) : activity.getString(R.string.tasks_no_match_title),
                    17, UiHelper.INK, true);
                UiHelper.add(activity, emptyBox,
                    rows.length() == 0 ? activity.getString(R.string.tasks_empty_desc) : activity.getString(R.string.tasks_no_match_desc),
                    14, UiHelper.MUTED, false);
                if (!search.getText().toString().trim().isEmpty()) {
                    emptyBox.addView(UiHelper.button(activity, activity.getString(R.string.btn_clear_search), () -> {
                        search.setText("");
                        onQueryChanged.accept("");
                    }, false));
                } else if (!"all".equals(taskFilter)) {
                    emptyBox.addView(UiHelper.button(activity, activity.getString(R.string.tasks_chip_all) + " (" + rows.length() + ")", () -> onFilterChanged.accept("all"), false));
                }
            }


        };

        UiHelper.watchSearch(search, value -> {
            onQueryChanged.accept(value);
            populate.run();
        });
        populate.run();
    }

    public static void showTaskDialog(MainActivity activity, JSONObject existing, String suggestedTitle,
                                      int totalCount, TaskCallback callback) {
        showTaskDialog(activity, existing, suggestedTitle, totalCount, callback, false);
    }

    private static void showTaskDialog(MainActivity activity, JSONObject existing, String suggestedTitle,
                                      int totalCount, TaskCallback callback, boolean requirePlaton) {
        LayoutInflater inflater = LayoutInflater.from(activity);
        View dialogView = inflater.inflate(R.layout.dialog_task_input, null);

        TextInputEditText name = dialogView.findViewById(R.id.task_input_title);
        Spinner priority = dialogView.findViewById(R.id.task_input_priority);
        TextInputEditText notes = dialogView.findViewById(R.id.task_input_notes);
        TextInputEditText platonUrl = dialogView.findViewById(R.id.task_input_platon_url);
        platonUrl.setText(existing == null ? "" : existing.optString("platonUrl"));
        MaterialButton dateBtn = dialogView.findViewById(R.id.task_input_date_btn);

        name.setText(existing == null ? suggestedTitle : existing.optString("title"));

        String[] prioOptions = {activity.getString(R.string.tasks_priority_low), activity.getString(R.string.tasks_priority_normal), activity.getString(R.string.tasks_priority_high)};
        priority.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, prioOptions));
        priority.setSelection(existing == null ? 1 : TaskTools.priority(existing));

        notes.setText(existing == null ? "" : existing.optString("notes"));

        LocalDate storedDue = existing == null ? null : TaskTools.dueDate(existing);
        LocalDate[] due = new LocalDate[]{storedDue == null ? LocalDate.now(UsosClient.ZONE) : storedDue};
        dateBtn.setText(activity.getString(R.string.task_due_date, due[0].toString()));
        dateBtn.setOnClickListener(v -> new DatePickerDialog(activity, (picker, year, month, day) -> {
            due[0] = LocalDate.of(year, month + 1, day);
            dateBtn.setText(activity.getString(R.string.task_due_date, due[0].toString()));
        }, due[0].getYear(), due[0].getMonthValue() - 1, due[0].getDayOfMonth()).show());

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
            .setTitle(existing == null ? activity.getString(R.string.tasks_dialog_new_title) : activity.getString(R.string.tasks_dialog_edit_title))
            .setView(dialogView)
            .setNegativeButton(activity.getString(R.string.btn_cancel), null)
            .setPositiveButton(existing == null ? activity.getString(R.string.btn_save) : activity.getString(R.string.btn_save), null)
            .create();

        dialog.setOnShowListener(v -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(b -> {
            String title = name.getText() != null ? name.getText().toString().trim() : "";
            if (title.isEmpty()) {
                name.setError(activity.getString(R.string.tasks_error_empty_title));
                name.requestFocus();
            } else if (title.length() > 200) {
                name.setError(activity.getString(R.string.tasks_error_title_length));
                name.requestFocus();
            } else if (notes.getText() != null && notes.getText().length() > 2000) {
                notes.setError(activity.getString(R.string.tasks_error_notes_length));
                notes.requestFocus();
            } else if (existing == null && totalCount >= 200) {
                name.setError(activity.getString(R.string.tasks_error_limit));
            } else {
                String link;
                try {
                    link = PlatonLinks.normalize(platonUrl.getText() == null ? "" : platonUrl.getText().toString());
                    if (requirePlaton && link.isEmpty()) throw new IllegalArgumentException();
                } catch (IllegalArgumentException e) {
                    platonUrl.setError(activity.getString(R.string.tasks_platon_invalid_link));
                    platonUrl.requestFocus(); return;
                }
                try {
                    JSONObject entry = existing == null ? new JSONObject() : new JSONObject(existing.toString());
                    if (link.isEmpty()) entry.remove("platonUrl"); else entry.put("platonUrl", link);
                    entry.put("id", existing == null ? UUID.randomUUID().toString() : existing.getString("id"))
                        .put("title", title)
                        .put("due", due[0].toString())
                        .put("done", existing != null && existing.optBoolean("done"))
                        .put("priority", priority.getSelectedItemPosition())
                        .put("notes", notes.getText() != null ? notes.getText().toString().trim() : "");
                    dialog.dismiss();
                    callback.onSaveTask(entry);
                } catch (JSONException ignored) {}
            }
        }));
        dialog.show();
    }

    private static boolean matches(String query, String... values) {
        if (query == null || query.trim().isEmpty()) return true;
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (String v : values) {
            if (v != null && v.toLowerCase(Locale.ROOT).contains(q)) return true;
        }
        return false;
    }
}
