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
import com.google.android.material.progressindicator.LinearProgressIndicator;
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
        UiHelper.title(activity, content,
            activity.getString(R.string.tasks_title),
            activity.getString(R.string.tasks_subtitle));

        content.addView(UiHelper.button(activity,
            activity.getString(R.string.btn_add_task),
            () -> showTaskDialog(activity, null, "", rows.length(), callback), true));

        int pending = 0;
        for (int i = 0; i < rows.length(); ++i) {
            if (!rows.optJSONObject(i).optBoolean("done")) {
                ++pending;
            }
        }

        UiHelper.note(activity, content,
            activity.getString(R.string.tasks_summary, pending, (rows.length() - pending)),
            UiHelper.MUTED);

        if (rows.length() > 0) {
            LinearProgressIndicator completion = new LinearProgressIndicator(activity);
            completion.setMax(rows.length());
            completion.setProgress(rows.length() - pending);
            completion.setContentDescription(activity.getString(R.string.tasks_progress_desc, (rows.length() - pending), rows.length()));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, UiHelper.dp(activity, 8));
            cp.bottomMargin = UiHelper.dp(activity, 16);
            content.addView(completion, cp);
        }

        UiHelper.chips(activity, content,
            new String[]{"all", "open", "done"},
            new String[]{activity.getString(R.string.tasks_chip_all), activity.getString(R.string.tasks_chip_open), activity.getString(R.string.tasks_chip_done)},
            taskFilter, onFilterChanged);

        UiHelper.chips(activity, content,
            new String[]{"today", "week", "overdue"},
            new String[]{activity.getString(R.string.tasks_chip_today), activity.getString(R.string.tasks_chip_week), activity.getString(R.string.tasks_chip_overdue)},
            taskFilter, onFilterChanged);

        UiHelper.note(activity, content, activity.getString(R.string.tasks_sort_note), UiHelper.MUTED);
        UiHelper.chips(activity, content,
            new String[]{"due", "priority", "title"},
            new String[]{activity.getString(R.string.tasks_sort_due), activity.getString(R.string.tasks_sort_priority), activity.getString(R.string.tasks_sort_title)},
            taskOrder, onOrderChanged);

        EditText search = UiHelper.searchField(activity, content,
            activity.getString(R.string.tasks_search_hint),
            taskQuery, null, () -> onQueryChanged.accept(""));

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);

        List<JSONObject> sorted = TaskTools.sorted(rows, taskOrder);
        Runnable populate = () -> {
            list.removeAllViews();
            int count = 0;
            LocalDate today = LocalDate.now(UsosClient.ZONE);
            LayoutInflater inflater = LayoutInflater.from(activity);

            for (JSONObject t : sorted) {
                boolean done = t.optBoolean("done");
                LocalDate due = TaskTools.dueDate(t);
                String titleText = t.optString("title");
                String notesText = t.optString("notes");

                if (TaskTools.matches(t, taskFilter, search.getText().toString(), today)) {
                    ++count;
                    String id = t.optString("id");
                    View card = inflater.inflate(R.layout.item_task_card, list, false);

                    TextView title = card.findViewById(R.id.task_item_title);
                    TextView priority = card.findViewById(R.id.task_item_priority);
                    TextView notes = card.findViewById(R.id.task_item_notes);
                    TextView dueView = card.findViewById(R.id.task_item_due);
                    MaterialButton btnEdit = card.findViewById(R.id.task_item_btn_edit);
                    MaterialButton btnToggle = card.findViewById(R.id.task_item_btn_toggle);
                    MaterialButton btnDelete = card.findViewById(R.id.task_item_btn_delete);

                    title.setText(titleText);
                    title.setTextColor(done ? UiHelper.MUTED : UiHelper.INK);

                    int prio = TaskTools.priority(t);
                    String[] prioLabels = {activity.getString(R.string.tasks_priority_low), activity.getString(R.string.tasks_priority_normal), activity.getString(R.string.tasks_priority_high)};
                    priority.setText(prioLabels[prio]);
                    priority.setTextColor(prio == 2 && !done ? Color.rgb(160, 48, 40) : UiHelper.MUTED);

                    if (!notesText.isEmpty()) {
                        notes.setVisibility(View.VISIBLE);
                        notes.setText(notesText);
                    } else {
                        notes.setVisibility(View.GONE);
                    }

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
                    dueView.setText(statusText);
                    dueView.setTextColor(statusColor);

                    btnToggle.setText(done ? activity.getString(R.string.tasks_btn_mark_open) : activity.getString(R.string.tasks_btn_mark_done));
                    btnToggle.setOnClickListener(v -> callback.onMutateTask(id, false));

                    btnEdit.setOnClickListener(v -> showTaskDialog(activity, t, "", rows.length(), callback));

                    btnDelete.setOnClickListener(v -> new MaterialAlertDialogBuilder(activity)
                        .setTitle(activity.getString(R.string.tasks_delete_dialog_title))
                        .setMessage(titleText)
                        .setNegativeButton(activity.getString(R.string.btn_cancel), null)
                        .setPositiveButton(activity.getString(R.string.tasks_btn_delete), (dialog, which) -> callback.onMutateTask(id, true))
                        .show());

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
            }

            UiHelper.note(activity, list, activity.getString(R.string.tasks_shown_count, count), UiHelper.MUTED);
        };

        UiHelper.watchSearch(search, value -> {
            onQueryChanged.accept(value);
            populate.run();
        });
        populate.run();
    }

    public static void showTaskDialog(MainActivity activity, JSONObject existing, String suggestedTitle,
                                      int totalCount, TaskCallback callback) {
        LayoutInflater inflater = LayoutInflater.from(activity);
        View dialogView = inflater.inflate(R.layout.dialog_task_input, null);

        TextInputEditText name = dialogView.findViewById(R.id.task_input_title);
        Spinner priority = dialogView.findViewById(R.id.task_input_priority);
        TextInputEditText notes = dialogView.findViewById(R.id.task_input_notes);
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
            } else if (title.length() > 200) {
                name.setError(activity.getString(R.string.tasks_error_title_length));
            } else if (notes.getText() != null && notes.getText().length() > 2000) {
                notes.setError(activity.getString(R.string.tasks_error_notes_length));
                notes.requestFocus();
            } else if (existing == null && totalCount >= 200) {
                name.setError(activity.getString(R.string.tasks_error_limit));
            } else {
                try {
                    JSONObject entry = existing == null ? new JSONObject() : new JSONObject(existing.toString());
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
