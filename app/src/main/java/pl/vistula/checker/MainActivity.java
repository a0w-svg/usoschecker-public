package pl.vistula.checker;
import pl.vistula.checker.core.OAuth;
import pl.vistula.checker.R.string;
import pl.vistula.checker.R.drawable;
import org.json.JSONTokener;
import org.json.JSONObject;
import org.json.JSONException;
import org.json.JSONArray;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.UUID;
import java.util.TreeSet;
import java.util.SortedSet;
import java.util.Set;
import java.util.Objects;
import java.util.Map;
import java.util.Locale;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Comparator;
import java.util.Arrays;
import java.util.ArrayList;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.Instant;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import com.google.android.material.materialswitch.MaterialSwitch;
import androidx.core.view.WindowInsetsCompat.Type;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.ViewCompat;
import androidx.core.graphics.Insets;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.CompoundButton;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.view.ViewGroup;
import android.view.View;
import android.text.TextWatcher;
import android.text.Html;
import android.text.Editable;
import android.os.Build.VERSION;
import android.os.Looper;
import android.os.Handler;
import android.os.Bundle;
import android.content.Intent;
import android.content.DialogInterface;
import android.content.ActivityNotFoundException;
import android.app.DatePickerDialog;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.core.view.GravityCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.text.method.PasswordTransformationMethod;
import android.view.*;
import android.widget.*;
import androidx.core.app.NotificationManagerCompat;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import pl.vistula.checker.core.LessonTime;
import pl.vistula.checker.core.Change;
import pl.vistula.checker.core.GradeSummary;
import pl.vistula.checker.core.GradeBrowser;
import pl.vistula.checker.core.DaySummary;
import pl.vistula.checker.core.StudentSearch;
import pl.vistula.checker.core.PlanBrowser;
import pl.vistula.checker.core.CalendarExport;
import pl.vistula.checker.core.Record;
import pl.vistula.checker.core.Snapshot;
import pl.vistula.checker.core.TaskTools;
import pl.vistula.checker.core.UsosParser;

public final class MainActivity extends androidx.appcompat.app.AppCompatActivity {
    private static final int INK = Color.rgb(27, 37, 32), GREEN = Color.rgb(23, 107, 87);
    private static final int MUTED = Color.rgb(89, 105, 96), BG = Color.rgb(250, 252, 250);
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private JSONObject state = new JSONObject();
    private JSONArray catalog = new JSONArray();
    private LinearLayout root, content;
    private DrawerLayout drawer;
    private NavigationView sideMenu;
    private BottomNavigationView navigation;
    private MaterialToolbar toolbar;
    private JSONArray demoTasks = new JSONArray();
    private ScrollView scroll;
    private TextView progress;
    private boolean demo, demoChanged, busy;
    private int tab;
    private String filter = "wszystkie", dataFilter = "ocena";
    private String dataQuery = "", historyQuery = "", planRange = "all";
    private String gradeQuery = "", gradeType = "all", gradeOrder = "name";
    private boolean gradeFiltersExpanded;
    private String semester = "", taskFilter = "all", taskQuery = "", pendingCalendar;
    private String taskOrder = "due";
    private String calendarDate = "", calendarMode = "list";
    private int demoPlanDays = 56;
    private long renderedMinute;
    private boolean loadingState;
    private String apiQuery = "";
    private int apiLimit = 60;
    private boolean apiFavoritesOnly;
    private Set<String> apiFavorites = new HashSet<>();
    private String currentMethod;
    private byte[] uploadBytes;
    private String uploadMethod;
    private UsosClient.Response exportResponse;
    private Button uploadButton;
    private Repository repository;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!busy && currentMethod == null && tab != 4 && tab != 3 && !(getCurrentFocus() instanceof EditText)) load();
            handler.postDelayed(this, 30000);
        }
    };
    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        repository = new Repository(this);
        taskOrder = getSharedPreferences("student_tools", MODE_PRIVATE).getString("taskOrder", "due");
        gradeOrder = getSharedPreferences("student_tools", MODE_PRIVATE).getString("gradeOrder", "name");
        calendarMode = getSharedPreferences("student_tools", MODE_PRIVATE).getString("calendarMode", "list");
        apiFavorites = new HashSet<>(getSharedPreferences("api_catalog", MODE_PRIVATE).getStringSet("favorites", Collections.emptySet()));
        if (bundle != null) {
            demo = bundle.getBoolean("demo"); demoChanged = bundle.getBoolean("demoChanged"); tab = bundle.getInt("tab");
            filter = bundle.getString("filter", "wszystkie"); dataFilter = bundle.getString("dataFilter", "ocena");
            dataQuery = bundle.getString("dataQuery", ""); historyQuery = bundle.getString("historyQuery", "");
            gradeQuery = bundle.getString("gradeQuery", ""); gradeType = bundle.getString("gradeType", "all"); gradeOrder = bundle.getString("gradeOrder", gradeOrder);
            gradeFiltersExpanded = bundle.getBoolean("gradeFiltersExpanded");
            planRange = bundle.getString("planRange", "all");
            calendarDate = bundle.getString("calendarDate", ""); calendarMode = bundle.getString("calendarMode", calendarMode); demoPlanDays = bundle.getInt("demoPlanDays", 56);
            semester = bundle.getString("semester", ""); taskFilter = bundle.getString("taskFilter", "all");
            taskQuery = bundle.getString("taskQuery", ""); pendingCalendar = bundle.getString("pendingCalendar");
            taskOrder = bundle.getString("taskOrder", "due");
            try { demoTasks = new JSONArray(bundle.getString("demoTasks", "[]")); } catch (JSONException ignored) {}
            apiQuery = bundle.getString("apiQuery", ""); apiLimit = bundle.getInt("apiLimit", 60);
            apiFavoritesOnly = bundle.getBoolean("apiFavoritesOnly");
        } else if (getIntent().getBooleanExtra("showHistory", false)) tab = 1;
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            androidx.core.graphics.Insets keyboard = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime());
            root.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
            if (navigation != null) navigation.setVisibility(insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) ? View.GONE : View.VISIBLE);
            return androidx.core.view.WindowInsetsCompat.CONSUMED;
        });
        toolbar = new MaterialToolbar(this); toolbar.setTitle("Vistula");
        toolbar.setSubtitle("Twój dzień na uczelni"); toolbar.setBackgroundColor(BG);
        toolbar.setTitleTextColor(INK); toolbar.setSubtitleTextColor(MUTED);
        toolbar.setNavigationIcon(R.drawable.ic_nav_menu);
        toolbar.setNavigationContentDescription("Otwórz menu");
        toolbar.setNavigationOnClickListener(v -> { if (!busy) drawer.openDrawer(GravityCompat.START); });
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(64)));
        progress = text("Trwa wykonywanie operacji…", 12, GREEN, false);
        progress.setPadding(dp(24), 0, dp(24), dp(8)); progress.setVisibility(View.GONE); root.addView(progress);
        scroll = new ScrollView(this); scroll.setFillViewport(true);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(20), dp(12), dp(20), dp(24));
        scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        navigation = new BottomNavigationView(this);
        navigation.setBackgroundColor(Color.rgb(240, 245, 241));
        navigation.setLabelVisibilityMode(com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED);
        navigation.setItemHorizontalTranslationEnabled(false);
        int[] targets = {0, 2, 5, 1, 6};
        String[] labels = {"Start", "Oceny", "Plan", "Zmiany", "Więcej"};
        int[] icons = {R.drawable.ic_nav_home, R.drawable.ic_nav_grades, R.drawable.ic_nav_plan, R.drawable.ic_nav_history, R.drawable.ic_nav_more};
        for (int i = 0; i < targets.length; i++) navigation.getMenu().add(0, 100 + targets[i], i, labels[i]).setIcon(icons[i]);
        navigation.setOnItemSelectedListener(item -> { if (busy) return false; navigate(item.getItemId() - 100); return true; });
        root.addView(navigation, new LinearLayout.LayoutParams(-1, -2));
        drawer = new DrawerLayout(this); drawer.addView(root, new DrawerLayout.LayoutParams(-1, -1));
        sideMenu = new NavigationView(this);
        LinearLayout header = new LinearLayout(this); header.setOrientation(LinearLayout.VERTICAL); header.setPadding(dp(24), dp(48), dp(24), dp(24));
        add(header, "Vistula", 28, INK, true); add(header, "Twój asystent uczelni", 14, MUTED, false);
        sideMenu.addHeaderView(header);
        int[] sections = {0, 2, 5, 1, 7, 4, 3};
        String[] names = {"Start", "Oceny", "Plan zajęć", "Historia zmian", "Zadania", "Konto i ustawienia", "Katalog API"};
        int[] sectionIcons = {R.drawable.ic_nav_home, R.drawable.ic_nav_grades, R.drawable.ic_nav_plan, R.drawable.ic_nav_history, R.drawable.ic_nav_tasks, R.drawable.ic_nav_account, R.drawable.ic_nav_api};
        for (int i = 0; i < sections.length; i++) sideMenu.getMenu().add(0, 200 + sections[i], i, names[i]).setIcon(sectionIcons[i]).setCheckable(true);
        sideMenu.setNavigationItemSelectedListener(item -> { if (busy) return false; navigate(item.getItemId() - 200); drawer.closeDrawers(); return true; });
        DrawerLayout.LayoutParams drawerParams = new DrawerLayout.LayoutParams(dp(300), -1); drawerParams.gravity = GravityCompat.START;
        drawer.addView(sideMenu, drawerParams); setContentView(drawer);
        task(() -> {
            catalog = new JSONArray(asset("api/index.json"));
            return repository.read();
        }, result -> { state = result; ensureSchedule(); render(); });
    }
    @Override public void onSaveInstanceState(Bundle out) {
        out.putBoolean("apiFavoritesOnly", apiFavoritesOnly);
        super.onSaveInstanceState(out); out.putBoolean("demo", demo); out.putBoolean("demoChanged", demoChanged); out.putInt("tab", tab);
        out.putString("filter", filter); out.putString("dataFilter", dataFilter);
        out.putString("semester", semester); out.putString("taskFilter", taskFilter); out.putString("taskQuery", taskQuery);
        out.putString("taskOrder", taskOrder);
        out.putString("calendarDate", calendarDate); out.putString("calendarMode", calendarMode); out.putInt("demoPlanDays", demoPlanDays);
        out.putString("pendingCalendar", pendingCalendar);
        out.putString("demoTasks", demoTasks.toString());
        out.putString("dataQuery", dataQuery); out.putString("historyQuery", historyQuery); out.putString("planRange", planRange);
        out.putString("gradeQuery", gradeQuery); out.putString("gradeType", gradeType); out.putString("gradeOrder", gradeOrder);
        out.putBoolean("gradeFiltersExpanded", gradeFiltersExpanded);
        out.putString("apiQuery", apiQuery); out.putInt("apiLimit", apiLimit);
    }
    @Override protected void onResume() { super.onResume(); handler.removeCallbacks(refresh); handler.postDelayed(refresh, 30000); if (content != null && !busy && tab != 4 && tab != 3) load(); }
    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(refresh); }
    @Override protected void onDestroy() { super.onDestroy(); handler.removeCallbacks(refresh); io.shutdown(); }
    private void ensureSchedule() {
        if (state.optBoolean("enabled") && connected()) SyncWorker.schedule(this, state.optLong("interval", 30));
    }
    private boolean connected() { return !state.optString("token").isEmpty(); }
    private JSONObject display() throws JSONException { return demo ? Demo.state(demoChanged, demoPlanDays) : state; }
    private void load() {
        if (loadingState || busy || isDestroyed()) return; loadingState = true;
        io.execute(() -> {
            try {
                JSONObject latest = repository.read();
                runOnUiThread(() -> {
                    loadingState = false; if (isDestroyed() || busy) return;
                    boolean clockChanged = (tab == 0 || tab == 5) && renderedMinute != System.currentTimeMillis() / 60000;
                    if (!state.toString().equals(latest.toString()) || clockChanged) {
                        int position = scroll.getScrollY(); state = latest; render(); scroll.post(() -> scroll.scrollTo(0, position));
                    }
                });
            } catch (Exception e) { runOnUiThread(() -> { loadingState = false; }); }
        });
    }
    private interface Job<T> { T run() throws Exception; }
    private interface Done<T> { void accept(T value) throws Exception; }
    private <T> void task(Job<T> job, Done<T> done) {
        task(job, done, message -> {});
    }
    private <T> void task(Job<T> job, Done<T> done, java.util.function.Consumer<String> failed) {
        if (busy || isDestroyed()) return;
        busy = true; updateBusy(true);
        io.execute(() -> {
            try {
                T result = job.run();
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    busy = false; updateBusy(false);
                    try { done.accept(result); } catch (Exception e) { error(Repository.message(e)); }
                });
            } catch (Exception e) {
                JSONObject latest;
                try { latest = repository.read(); } catch (Exception ignored) { latest = state; }
                JSONObject recovered = latest;
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    busy = false; state = recovered; updateBusy(false);
                    if (tab == 0 || tab == 1 || tab == 2 || tab == 5 || tab == 7) render();
                    failed.accept(Repository.message(e));
                    error(Repository.message(e));
                });
            }
        });
    }
    private void updateBusy(boolean value) {
        setButtons(root, !value);
        if (navigation != null) for (int i = 0; i < navigation.getMenu().size(); i++) navigation.getMenu().getItem(i).setEnabled(!value);
        if (value) progress.setText(R.string.loading);
        progress.setVisibility(value ? View.VISIBLE : View.GONE);

    }
    private void setButtons(View v, boolean enabled) {
        if (v instanceof Button || v instanceof CompoundButton || v instanceof Spinner || v instanceof EditText) {
            if (!enabled) { if (!(v.getTag() instanceof Boolean)) v.setTag(v.isEnabled()); v.setEnabled(false); }
            else if (v.getTag() instanceof Boolean) { v.setEnabled((Boolean) v.getTag()); v.setTag(null); }
        }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) setButtons(((ViewGroup) v).getChildAt(i), enabled);
    }
    private void render() {
        if (currentMethod != null || isDestroyed()) return;
        renderedMinute = System.currentTimeMillis() / 60000;
        content.removeAllViews();
        int selected = tab == 3 || tab == 4 || tab == 7 ? 6 : tab;
        navigation.getMenu().findItem(100 + selected).setChecked(true);
        for (int i = 0; i < sideMenu.getMenu().size(); i++) sideMenu.getMenu().getItem(i).setChecked(sideMenu.getMenu().getItem(i).getItemId() == 200 + tab);
        String[] titles = {"Start", "Historia zmian", "Oceny", "Katalog API", "Konto", "Plan zajęć", "Więcej", "Zadania"};
        toolbar.setTitle(titles[tab]); toolbar.setSubtitle(demo ? "Konto demonstracyjne" : connected() ? state.optString("user", "USOS Vistula") : "USOS Vistula");
        try {
            if (demo) note(content, "TRYB DEMO · przykładowe dane, bez połączenia z USOS", GREEN);
            switch (tab) {
                case 0: home(); break;
                case 1: history(); break;
                case 2: dataFilter = "ocena"; data(); break;
                case 3: api(); break;
                case 4: account(); break;
                case 5: dataFilter = "plan"; calendar(); break;
                case 6: more(); break;
                case 7: tasks(); break;
            }
        } catch (Exception e) { note(content, Repository.message(e), Color.rgb(167, 49, 49)); }
        updateBusy(busy);
    }
    void navigate(int target) {
        currentMethod = null; tab = target; render(); scroll.post(() -> scroll.scrollTo(0, 0));
    }
    private void more() {
        title("Więcej", "Wszystkie narzędzia w jednym miejscu.");
        content.addView(button("Zadania", () -> navigate(7), true));
        content.addView(button("Konto", () -> navigate(4), false));
        content.addView(button("API", () -> navigate(3), false));
        note(content, "Zadania zapisujesz dla siebie na telefonie. Oceny i plan pochodzą z USOS.", MUTED);
    }
    private JSONArray personalTasks() { return demo ? demoTasks : state.optJSONArray("tasks") == null ? new JSONArray() : state.optJSONArray("tasks"); }
    private void dashboard(JSONObject d) throws Exception {
        JSONObject raw = d.optJSONObject("snapshot"); List<Record> records = raw == null ? new ArrayList<>() : new ArrayList<>(Snapshot.from(raw).records.values());
        int grades = 0, todayCount = 0, openTasks = 0; Instant now = Instant.now(); LocalDate today = LocalDate.now(UsosClient.ZONE);
        for (Record r : records) { if (r.kind.equals("ocena")) grades++; else if (LessonTime.of(r).intersects(today)) todayCount++; }
        JSONArray tasks = personalTasks(); for (int i = 0; i < tasks.length(); i++) if (!tasks.getJSONObject(i).optBoolean("done")) openTasks++;
        LinearLayout stats = new LinearLayout(this);
        String[] labels = {grades + "\nOceny", todayCount + "\nDzisiaj", openTasks + "\nZadania"}; int[] destinations = {2, 5, 7};
        for (int i = 0; i < labels.length; i++) {
            final int target = destinations[i]; Button b = button(labels[i], () -> navigate(target), false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1); params.setMarginEnd(dp(6)); stats.addView(b, params);
        }
        content.addView(stats); currentLecture(content, records);
        heading("Najbliższe zajęcia"); LessonTime upcoming = null;
        for (LessonTime lesson : LessonTime.sorted(records)) if (lesson.start.toInstant().isAfter(now)) { upcoming = lesson; break; }
        if (upcoming == null) empty("Brak kolejnych zajęć w zapisanym planie", raw == null ? "Połącz konto i odśwież dane." : "Sprawdź agendę lub wczytaj dalszy zakres.");
        else {
            LinearLayout box = card(content, Color.WHITE); add(box, upcoming.record.title, 20, INK, true);
            add(box, shortDate(upcoming.start.toLocalDate()), 13, GREEN, true); add(box, upcoming.record.value, 14, MUTED, false);
            LessonTime next = upcoming;
            box.addView(button("Otwórz plan zajęć", () -> { calendarDate = next.start.toLocalDate().toString(); calendarMode = "day"; navigate(5); }, false));
            box.addView(button("Szczegóły zajęć", () -> lessonDetails(next), false));
        }
    }
    private void currentLecture(LinearLayout parent, Collection<Record> rows) {
        List<LessonTime> current = LessonTime.current(rows, Instant.now());
        if (current.isEmpty()) { note(parent, "Teraz nie masz zajęć według zapisanego planu.", MUTED); return; }
        for (LessonTime lesson : current) {
            LinearLayout box = card(parent, Color.rgb(214, 241, 229));
            add(box, "TRWAJĄ TERAZ", 11, GREEN, true); add(box, lesson.record.title, 22, INK, true); add(box, lesson.record.value, 14, INK, false);
            long remaining = Math.max(1, (java.time.Duration.between(Instant.now(), lesson.end.toInstant()).getSeconds() + 59) / 60);
            add(box, "Do końca: " + remaining + " min · według ostatnio pobranego planu", 12, GREEN, false);
        }
    }
   private void deadlinePreview() throws JSONException {
      List<JSONObject> open = new ArrayList();
      JSONArray rows = this.personalTasks();

      for(int i = 0; i < rows.length(); ++i) {
         if (!rows.getJSONObject(i).optBoolean("done")) {
            open.add(rows.getJSONObject(i));
         }
      }

      if (!open.isEmpty()) {
         open.sort(Comparator.comparing((tx) -> tx.optString("due")));
         this.heading("Najbliższe terminy");
         LinearLayout box = this.card(this.content, -1);
         LocalDate today = LocalDate.now(UsosClient.ZONE);

         for(int i = 0; i < Math.min(3, open.size()); ++i) {
            JSONObject t = (JSONObject)open.get(i);
            LocalDate due = LocalDate.parse(t.optString("due"));
            this.add(box, t.optString("title"), 16, INK, true);
            this.add(box, (due.isBefore(today) ? "Po terminie · " : (due.equals(today) ? "Dzisiaj · " : "")) + due.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")), 13, due.isBefore(today) ? Color.rgb(160, 48, 40) : GREEN, false);
         }

         box.addView(this.button("Otwórz zadania", () -> this.navigate(7), false));
      }
   }

   private void tasks() throws Exception {
      this.title("Twoje zadania", "Osobista lista na telefonie · bez wysyłania do USOS");
      this.content.addView(this.button("Dodaj zadanie", this::addTask, true));
      JSONArray rows = this.personalTasks();
      int pending = 0;

      for(int i = 0; i < rows.length(); ++i) {
         if (!rows.getJSONObject(i).optBoolean("done")) {
            ++pending;
         }
      }

      this.note(this.content, "Do zrobienia: " + pending + " · ukończone: " + (rows.length() - pending), MUTED);
      if (rows.length() > 0) {
         com.google.android.material.progressindicator.LinearProgressIndicator completion = new com.google.android.material.progressindicator.LinearProgressIndicator(this);
         completion.setMax(rows.length()); completion.setProgress(rows.length() - pending);
         completion.setContentDescription("Ukończone zadania: " + (rows.length() - pending) + " z " + rows.length());
         LinearLayout.LayoutParams completionParams = new LinearLayout.LayoutParams(-1, dp(8)); completionParams.bottomMargin = dp(16);
         content.addView(completion, completionParams);
      }
      this.chips(new String[]{"all", "open", "done"}, new String[]{"Wszystkie", "Otwarte", "Ukończone"}, this.taskFilter, (value) -> {
         this.taskFilter = value;
         this.render();
      });
      this.chips(new String[]{"today", "overdue"}, new String[]{"Na dzisiaj", "Po terminie"}, this.taskFilter, (value) -> {
         this.taskFilter = value;
         this.render();
      });
      this.note(content, "Sortowanie · ukończone zawsze na końcu", MUTED);
      this.chips(new String[]{"due", "priority", "title"}, new String[]{"Termin", "Priorytet", "Nazwa"}, taskOrder, value -> {
         taskOrder = value; getSharedPreferences("student_tools", MODE_PRIVATE).edit().putString("taskOrder", value).apply(); render();
      });
      EditText search = this.field(this.content, "Szukaj zadania…", this.taskQuery, false);
      LinearLayout list = new LinearLayout(this);
      list.setOrientation(LinearLayout.VERTICAL);
      this.content.addView(list);
      List<JSONObject> sorted = TaskTools.sorted(rows, taskOrder);
      Runnable populate = () -> {
         list.removeAllViews();
         int count = 0;
         LocalDate today = LocalDate.now(UsosClient.ZONE);

         for(JSONObject t : sorted) {
            boolean done = t.optBoolean("done");
            LocalDate due = LocalDate.parse(t.optString("due"));
            if (this.matches(this.taskQuery, t.optString("title"), t.optString("due"), t.optString("notes")) && (!this.taskFilter.equals("open") || !done) && (!this.taskFilter.equals("done") || done) && (!this.taskFilter.equals("today") || !done && due.equals(today)) && (!this.taskFilter.equals("overdue") || !done && due.isBefore(today))) {
               ++count;
               String id = t.optString("id");
               LinearLayout box = this.card(list, -1);
               this.add(box, t.optString("title"), 18, done ? MUTED : INK, true);
               this.note(box, new String[]{"Niski priorytet", "Normalny priorytet", "Wysoki priorytet"}[TaskTools.priority(t)], TaskTools.priority(t) == 2 && !done ? Color.rgb(160, 48, 40) : MUTED);
               if (!t.optString("notes").isEmpty()) this.add(box, t.optString("notes"), 14, MUTED, false);
               this.add(box, (done ? "Ukończone · " : (due.isBefore(today) ? "Po terminie · " : (due.equals(today) ? "Dzisiaj · " : "Termin · "))) + due.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")), 13, done ? MUTED : (due.isBefore(today) ? Color.rgb(160, 48, 40) : GREEN), true);
               box.addView(this.button("Edytuj zadanie", () -> this.editTask(t), false));
               box.addView(this.button(done ? "Oznacz jako do zrobienia" : "Oznacz jako ukończone", () -> this.mutateTasks(id, false), false));
               box.addView(this.button("Usuń zadanie", () -> (new MaterialAlertDialogBuilder(this)).setTitle("Usunąć zadanie?").setMessage(t.optString("title")).setNegativeButton("Anuluj", (DialogInterface.OnClickListener)null).setPositiveButton("Usuń", (dialog, which) -> this.mutateTasks(id, true)).show(), false));
            }
         }

         if (count == 0) {
            LinearLayout box = this.card(list, -1);
            this.add(box, rows.length() == 0 ? "Spokojna głowa" : "Brak pasujących zadań", 17, INK, true);
            this.add(box, rows.length() == 0 ? "Dodaj termin projektu, naukę do egzaminu lub własną notatkę." : "Zmień filtr lub wyszukiwanie.", 14, MUTED, false);
         }

         this.note(list, "Wyświetlono zadań: " + count, MUTED);
      };
      this.watchSearch(search, (value) -> {
         this.taskQuery = value;
         populate.run();
      });
      populate.run();
   }

   private void addTask() {
      this.editTask((JSONObject)null);
   }

   private void editTask(JSONObject existing) {
      editTask(existing, "");
   }
   private void editTask(JSONObject existing, String suggestedTitle) {
      LinearLayout form = new LinearLayout(this);
      form.setOrientation(LinearLayout.VERTICAL);
      form.setPadding(this.dp(24), this.dp(8), this.dp(24), 0);
      EditText name = this.field(form, "Co masz do zrobienia?", existing == null ? suggestedTitle : existing.optString("title"), false);
      name.setId(R.id.task_title);
      this.note(form, "Priorytet", MUTED);
      Spinner priority = new Spinner(this);
      priority.setId(R.id.task_priority);
      priority.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Niski", "Normalny", "Wysoki"}));
      priority.setSelection(existing == null ? 1 : TaskTools.priority(existing));
      form.addView(priority);
      EditText notes = this.field(form, "Notatka (opcjonalnie)", existing == null ? "" : existing.optString("notes"), false);
      notes.setId(R.id.task_notes);
      notes.setSingleLine(false); notes.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
      notes.setMinLines(2); notes.setMaxLines(4);
      LocalDate[] due = new LocalDate[]{existing == null ? LocalDate.now(UsosClient.ZONE) : LocalDate.parse(existing.optString("due"))};
      Button date = this.button(this.getString(string.task_due_date, new Object[]{due[0].toString()}), () -> {
      }, false);
      date.setOnClickListener((v) -> (new DatePickerDialog(this, (picker, year, month, day) -> {
            due[0] = LocalDate.of(year, month + 1, day);
            date.setText(this.getString(string.task_due_date, new Object[]{due[0].toString()}));
         }, due[0].getYear(), due[0].getMonthValue() - 1, due[0].getDayOfMonth())).show());
      form.addView(date);
      ScrollView formScroll = new ScrollView(this); formScroll.addView(form);
      AlertDialog dialog = (new MaterialAlertDialogBuilder(this)).setTitle(existing == null ? "Nowe zadanie" : "Edytuj zadanie").setView(formScroll).setNegativeButton("Anuluj", (DialogInterface.OnClickListener)null).setPositiveButton(existing == null ? "Dodaj" : "Zapisz", (DialogInterface.OnClickListener)null).create();
      dialog.setOnShowListener((v) -> dialog.getButton(-1).setOnClickListener((b) -> {
            String title = name.getText().toString().trim();
            if (title.isEmpty()) {
               name.setError("Wpisz treść zadania");
            } else if (title.length() > 200) {
               name.setError("Maksymalnie 200 znaków");
            } else if (notes.getText().length() > 2000) {
               notes.setError("Maksymalnie 2000 znaków"); notes.requestFocus();
            } else if (existing == null && this.personalTasks().length() >= 200) {
               name.setError("Limit 200 zadań. Usuń ukończone wpisy.");
            } else {
               try {
                  JSONObject entry = existing == null ? new JSONObject() : new JSONObject(existing.toString());
                  entry.put("id", existing == null ? UUID.randomUUID().toString() : existing.getString("id")).put("title", title).put("due", due[0].toString()).put("done", existing != null && existing.optBoolean("done"))
                     .put("priority", priority.getSelectedItemPosition()).put("notes", notes.getText().toString().trim());
                  dialog.dismiss();
                  if (this.demo) {
                     this.demoTasks = this.savedTask(this.demoTasks, entry);
                     this.render();
                  } else {
                     this.task(() -> {
                        this.repository.update((s) -> s.put("tasks", this.savedTask(s.optJSONArray("tasks"), entry)));
                        return this.repository.read();
                     }, (result) -> {
                        this.state = result;
                        this.render();
                     });
                  }
               } catch (JSONException e) {
                  this.error(Repository.message(e));
               }

            }
         }));
      dialog.show();
   }

   private JSONArray savedTask(JSONArray source, JSONObject replacement) throws JSONException {
      JSONArray result = new JSONArray();
      boolean found = false;
      if (source != null) {
         for(int i = 0; i < source.length(); ++i) {
            JSONObject entry = source.getJSONObject(i);
            if (entry.getString("id").equals(replacement.getString("id"))) {
               result.put(replacement);
               found = true;
            } else {
               result.put(entry);
            }
         }
      }

      if (!found) {
         result.put(replacement);
      }

      return result;
   }

   private void mutateTasks(String id, boolean remove) {
      JSONObject deleted = null;
      if (remove) for (int i = 0; i < personalTasks().length(); i++) {
         JSONObject row = personalTasks().optJSONObject(i);
         if (row != null && row.optString("id").equals(id)) { deleted = row; break; }
      }
      JSONObject undoEntry = deleted;
      boolean wasDemo = demo;
      String accountToken = state.optString("token");
      if (this.demo) {
         try {
            this.demoTasks = this.changedTasks(this.demoTasks, id, remove);
            this.render();
            if (undoEntry != null) offerTaskUndo(undoEntry, wasDemo, accountToken);
         } catch (JSONException e) {
            this.error(Repository.message(e));
         }

      } else {
         this.task(() -> {
            this.repository.update((s) -> s.put("tasks", this.changedTasks(s.optJSONArray("tasks"), id, remove)));
            return this.repository.read();
         }, (result) -> {
            this.state = result;
            this.render();
            if (undoEntry != null) offerTaskUndo(undoEntry, wasDemo, accountToken);
         });
      }
   }

   private void offerTaskUndo(JSONObject entry, boolean wasDemo, String accountToken) {
      com.google.android.material.snackbar.Snackbar.make(root, R.string.task_deleted, 10000).setAction(R.string.undo, view -> {
         if (busy || demo != wasDemo) return;
         if (wasDemo) {
            try { demoTasks = TaskTools.restore(demoTasks, entry); render(); }
            catch (Exception e) { error(Repository.message(e)); }
         } else task(() -> {
            repository.update(s -> {
               if (!s.optString("token").equals(accountToken)) throw new IllegalStateException("Konto zostało zmienione. Nie można przywrócić zadania.");
               s.put("tasks", TaskTools.restore(s.optJSONArray("tasks"), entry));
            });
            return repository.read();
         }, result -> { state = result; render(); });
      }).show();
   }

   private void lessonDetails(LessonTime lesson) {
      String details = shortDate(lesson.start.toLocalDate()) + "\n\n" + lesson.record.value;
      new MaterialAlertDialogBuilder(this).setTitle(lesson.record.title).setMessage(details)
         .setNegativeButton("Zamknij", null)
         .setNeutralButton("Dodaj zadanie", (dialog, which) -> editTask(null, lesson.record.title))
         .setPositiveButton("Udostępnij", (dialog, which) -> {
            Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, lesson.record.title + "\n" + details);
            try { startActivity(Intent.createChooser(share, "Udostępnij zajęcia")); }
            catch (ActivityNotFoundException e) { error("Brak aplikacji do udostępniania."); }
         }).show();
   }

   private JSONArray changedTasks(JSONArray source, String id, boolean remove) throws JSONException {
      JSONArray result = new JSONArray();
      if (source != null) {
         for(int i = 0; i < source.length(); ++i) {
            JSONObject entry = new JSONObject(source.getJSONObject(i).toString());
            if (entry.getString("id").equals(id)) {
               if (remove) {
                  continue;
               }

               entry.put("done", !entry.optBoolean("done"));
            }

            result.put(entry);
         }
      }

      return result;
   }

   private void home() throws Exception {
      JSONObject d = this.display();
      LinearLayout hero = this.card(this.content, INK);
      this.add(hero, "TWÓJ ASYSTENT UCZELNI", 11, Color.rgb(124, 219, 192), true);
      this.add(hero, "Dobry dzień zaczyna się tutaj.", 26, -1, true);
      this.add(hero, LocalDate.now(UsosClient.ZONE).format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("pl-PL"))), 15, Color.rgb(209, 221, 233), false);
      this.dashboard(d);
      this.deadlinePreview();
      this.heading("Stan monitorowania");
      LinearLayout status = this.card(this.content, -1);
      boolean active = this.connected() && this.state.optBoolean("enabled") && !this.demo;
      this.add(status, this.demo ? "Podgląd demonstracyjny" : (active ? "Monitorowanie włączone" : (this.connected() ? "Monitorowanie wstrzymane" : "Połącz konto USOS")), 19, INK, true);
      this.add(status, this.demo ? "Te dane służą do sprawdzenia działania aplikacji." : (this.connected() ? this.state.optString("user", "Konto USOS") : "Zaloguj się przez stronę uczelni, aby śledzić swoje dane."), 14, MUTED, false);
      this.add(status, "Ostatnie udane sprawdzenie\n" + this.timestamp(d.optLong("successAt")), 13, MUTED, false);
      if (!this.state.optString("error").isEmpty() && !this.demo) {
         this.note(status, this.state.optString("error"), Color.rgb(167, 49, 49));
         this.add(status, "Ostatnia próba\n" + this.timestamp(this.state.optLong("attemptAt")), 13, MUTED, false);
         if (this.state.optLong("retryAt") > System.currentTimeMillis()) this.note(status, "Kolejna próba po " + this.timestamp(this.state.optLong("retryAt")), MUTED);
         if (this.state.optBoolean("authorizationError")) status.addView(this.button("Napraw połączenie konta", () -> this.navigate(4), false));
      }

      if (!this.demo && !this.connected()) {
         status.addView(this.button("Połącz konto", () -> {
            this.tab = 4;
            this.render();
         }, true));
      } else {
         status.addView(this.button(this.demo ? "Symuluj zmiany" : "Sprawdź teraz", this::sync, true));
      }

      if (!this.demo && !this.connected()) {
         status.addView(this.button("Zobacz wersję demonstracyjną", () -> {
            this.demo = true;
            this.render();
         }, false));
      }

      JSONArray rows = d.optJSONArray("history");
      this.heading("Ostatnie zmiany");
      if (rows != null && rows.length() != 0) {
         for(int i = 0; i < Math.min(3, rows.length()); ++i) {
            this.changeCard(Change.from(rows.getJSONObject(i)));
         }

         this.content.addView(this.button("Otwórz całą historię", () -> {
            this.tab = 1;
            this.render();
         }, false));
      } else {
         this.empty("Jeszcze bez zmian", "Pierwsze pobranie zapisuje punkt odniesienia. Kolejne różnice zobaczysz tutaj.");
      }

      this.note(this.content, "Plan obejmuje 8 tygodni i można go rozszerzyć do 16 tygodni. Sprawdzanie w tle co " + this.state.optLong("interval", 30L) + " min może być opóźnione przez Androida. Czas wykrycia nie oznacza czasu publikacji.", MUTED);
   }

   private static final class SyncResult {
      final JSONObject state; final int changes; final boolean first;
      SyncResult(JSONObject state, int changes, boolean first) { this.state = state; this.changes = changes; this.first = first; }
   }
   private void sync() {
      if (this.demo) {
         this.demoChanged = true;
         this.tab = 1;
         this.render();
      } else {
         int position = scroll.getScrollY(); boolean first = state.optJSONObject("snapshot") == null;
         this.task(() -> {
            List<Change> changes = this.repository.syncWithProgress(true, message -> runOnUiThread(() -> { if (!isDestroyed()) progress.setText(message); }));
            SyncWorker.notifyChanges(this, changes);
            return new SyncResult(this.repository.read(), changes.size(), first);
         }, (result) -> {
            this.state = result.state;
            this.render();
            scroll.post(() -> scroll.scrollTo(0, position));
            String message = result.first ? "Zapisano pierwsze pobranie ocen i planu" : result.changes == 0 ? "Dane aktualne · brak nowych zmian" : "Odświeżono dane · wykryte zmiany: " + result.changes;
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
         });
      }
   }

   private void history() throws Exception {
      this.title("Historia zmian", "Kiedy aplikacja zauważyła różnicę w USOS.");
      this.chips(new String[]{"wszystkie", "ocena", "plan"}, new String[]{"Wszystkie", "Oceny", "Plan"}, this.filter, (value) -> {
         this.filter = value;
         this.render();
      });
      JSONArray history = this.display().optJSONArray("history");
      List<Change> rows = new ArrayList();
      if (history != null) {
         for(int i = 0; i < history.length(); ++i) {
            Change c = Change.from(history.getJSONObject(i));
            if (this.filter.equals("wszystkie") || this.filter.equals(c.kind)) {
               rows.add(c);
            }
         }
      }

      EditText search = this.field(this.content, "Szukaj przedmiotu lub zmiany…", this.historyQuery, false);
      LinearLayout list = new LinearLayout(this);
      list.setOrientation(LinearLayout.VERTICAL);
      this.content.addView(list);
      Runnable populate = () -> {
         list.removeAllViews();
         int count = 0;

         for(Change c : rows) {
            if (this.matches(this.historyQuery, c.title, c.before, c.after, c.action)) {
               this.changeCard(list, c);
               ++count;
            }
         }

         if (count == 0) {
            LinearLayout box = this.card(list, -1);
            this.add(box, this.historyQuery.trim().isEmpty() ? "Brak zapisanych zmian" : "Brak pasujących zmian", 17, INK, true);
            this.add(box, this.historyQuery.trim().isEmpty() ? "Zmiany pojawią się po porównaniu kolejnych pobrań." : "Zmień wyszukiwanie lub wybierz inną kategorię.", 14, MUTED, false);
         }

         this.note(list, "Wyświetlono " + count + " z " + rows.size() + " zmian w tej kategorii. Historia: ostatnie 200 wpisów.", MUTED);
      };
      this.watchSearch(search, (value) -> {
         this.historyQuery = value;
         populate.run();
      });
      populate.run();
      if (this.demo) {
         this.content.addView(this.button("Symuluj zmianę oceny i sali", this::sync, true));
      }

   }

   private void changeCard(Change c) {
      this.changeCard(this.content, c);
   }

   private void changeCard(LinearLayout parent, Change c) {
      LinearLayout box = this.card(parent, -1);
      this.add(box, (c.kind.equals("ocena") ? "OCENA" : "PLAN ZAJĘĆ") + "  ·  " + c.action, 11, GREEN, true);
      this.add(box, c.title, 18, INK, true);
      if (!c.before.isEmpty()) {
         this.add(box, "Wcześniej: " + c.before, 14, MUTED, false);
      }

      if (!c.after.isEmpty()) {
         this.add(box, "Teraz: " + c.after, 14, INK, false);
      }

      this.add(box, "Wykryto: " + this.timestamp(c.detectedAt), 12, MUTED, false);
      if (!c.sourceTime.isEmpty()) {
         this.add(box, "Modyfikacja w USOS: " + c.sourceTime + " (Warszawa)", 12, MUTED, false);
      }

   }

    private String shortDate(LocalDate date) { return date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.forLanguageTag("pl-PL"))); }
    private void calendar() throws Exception {
        JSONObject d = display(); note(content, "Godziny w strefie Warszawa", MUTED);
        note(content, "Aktualność danych: " + timestamp(d.optLong("successAt")), MUTED);
        if (!demo && !state.optString("error").isEmpty()) note(content, state.optString("error"), Color.rgb(160, 48, 40));
        JSONObject raw = d.optJSONObject("snapshot");
        if (raw == null) {
            empty("Brak pobranych danych", "Połącz konto i odśwież plan.");
            if (connected()) content.addView(button("Odśwież plan", this::sync, true)); return;
        }
        Snapshot snapshot = Snapshot.from(raw); LocalDate start = LocalDate.parse(snapshot.start), end = LocalDate.parse(snapshot.end).minusDays(1);
        LocalDate today = LocalDate.now(UsosClient.ZONE), selected = calendarDate.isEmpty() ? today : LocalDate.parse(calendarDate);
        if (selected.isBefore(start)) selected = start; if (selected.isAfter(end)) selected = end;
        calendarDate = selected.toString(); final LocalDate day = selected;
        note(content, "Wczytany zakres: " + shortDate(start) + " – " + shortDate(end), GREEN);
        List<LessonTime> all = LessonTime.sorted(snapshot.records.values());
        LocalDate week = day.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LinearLayout arrows = new LinearLayout(this);
        Button previous = button("←", () -> { calendarDate = day.minusWeeks(1).isBefore(start) ? start.toString() : day.minusWeeks(1).toString(); render(); }, false);
        previous.setContentDescription("Poprzedni tydzień");
        Button next = button("→", () -> { calendarDate = day.plusWeeks(1).isAfter(end) ? end.toString() : day.plusWeeks(1).toString(); render(); }, false);
        next.setContentDescription("Następny tydzień");
        arrows.addView(previous, new LinearLayout.LayoutParams(0, dp(48), 1));
        arrows.addView(button("Dzisiaj", () -> { calendarDate = today.toString(); if (!calendarMode.equals("day")) calendarMode = "list"; render(); }, false), new LinearLayout.LayoutParams(0, dp(48), 2));
        arrows.addView(next, new LinearLayout.LayoutParams(0, dp(48), 1)); content.addView(arrows);
        previous.setEnabled(week.isAfter(start)); next.setEnabled(week.plusWeeks(1).isBefore(end.plusDays(1)));
        LinearLayout strip = new LinearLayout(this); String[] names = {"PN", "WT", "ŚR", "CZ", "PT", "SO", "ND"};
        HorizontalScrollView daysStrip = new HorizontalScrollView(this); daysStrip.setFillViewport(true); daysStrip.setHorizontalScrollBarEnabled(false); daysStrip.addView(strip);
        Button selectedDayButton = null;
        for (int i = 0; i < 7; i++) {
            LocalDate target = week.plusDays(i); int count = 0; for (LessonTime lesson : all) if (lesson.intersects(target)) count++;
            Button b = button(names[i] + "\n" + target.getDayOfMonth() + (count > 0 ? " •" : ""), () -> { calendarDate = target.toString(); if (!calendarMode.equals("day")) calendarMode = "list"; render(); }, target.equals(day));
            b.setTextSize(11); b.setPadding(0, 0, 0, 0); b.setMinWidth(0); b.setContentDescription(shortDate(target) + ", zajęcia: " + count);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48), dp(64), 1); params.setMarginEnd(dp(2)); strip.addView(b, params);
            b.setSelected(target.equals(day)); if (target.equals(day)) selectedDayButton = b;
            b.setEnabled(!target.isBefore(start) && !target.isAfter(end));
        }
        Button selectedDayView = selectedDayButton; if (selectedDayView != null) daysStrip.post(() -> daysStrip.smoothScrollTo(Math.max(0, selectedDayView.getRight() - daysStrip.getWidth()), 0));
        content.addView(daysStrip);
        Button chooseDate = button("Wybierz datę", () -> {
            DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, date) -> {
                calendarDate = LocalDate.of(year, month + 1, date).toString(); if (!calendarMode.equals("day")) calendarMode = "list"; render();
            }, day.getYear(), day.getMonthValue() - 1, day.getDayOfMonth());
            picker.getDatePicker().setMinDate(start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
            picker.getDatePicker().setMaxDate(end.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()); picker.show();
        }, false);
        chooseDate.setText(R.string.choose_date_short); chooseDate.setContentDescription("Wybierz datę planu zajęć");
        Runnable jumpNext = () -> {
            LocalDate target = PlanBrowser.nextDay(all, day, end, dataQuery);
            if (target != null) { calendarDate = target.toString(); if (!calendarMode.equals("day")) calendarMode = "list"; render(); scroll.post(() -> scroll.scrollTo(0, 0)); }
        };
        Button nextClass = button("Kolejny dzień z zajęciami", jumpNext, false);
        arrows.addView(chooseDate, new LinearLayout.LayoutParams(0, -2, 1.5f));
        chips(new String[]{"list", "day", "week", "agenda"}, new String[]{"Lista dnia", "Oś dnia", "Lista tygodnia", "Agenda · cały plan"}, calendarMode, value -> {
            calendarMode = value; getSharedPreferences("student_tools", MODE_PRIVATE).edit().putString("calendarMode", value).apply(); render();
        });
        EditText search = searchField(content, "Szukaj zajęć…", dataQuery);
        search.setContentDescription("Szukaj zajęć po przedmiocie, sali lub prowadzącym");
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); content.addView(list);
        Runnable populate = () -> {
            list.removeAllViews(); List<LessonTime> visible = PlanBrowser.select(all, day, calendarMode, dataQuery);
            List<Record> exported = new ArrayList<>(); for (LessonTime lesson : visible) exported.add(lesson.record);
            LocalDate nextMatch = PlanBrowser.nextDay(all, day, end, dataQuery); nextClass.setEnabled(nextMatch != null);
            boolean daily = calendarMode.equals("day") || calendarMode.equals("list");
            if (daily) {
                add(list, day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("pl-PL"))), 20, INK, true);
                if (visible.isEmpty()) {
                    LinearLayout box = card(list, Color.WHITE);
                    add(box, dataQuery.trim().isEmpty() ? "Brak zajęć w tym dniu" : "Brak pasujących zajęć w tym dniu", 17, INK, true);
                    note(box, "Sprawdź kolejny dzień lub wybierz listę tygodnia.", MUTED);
                    if (nextMatch != null) box.addView(button("Przejdź do " + shortDate(nextMatch), jumpNext, true));
                    if (!dataQuery.trim().isEmpty()) box.addView(button("Wyczyść wyszukiwanie planu", () -> { dataQuery = ""; render(); }, false));
                } else if (calendarMode.equals("day")) {
                    if (day.equals(today)) currentLecture(list, exported);
                    DayTimelineView timeline = new DayTimelineView(this, day, visible, this::lessonDetails);
                    if (day.equals(today)) list.addView(button("Przejdź do teraz", () -> scroll.smoothScrollTo(0, Math.max(0, list.getTop() + timeline.getTop() + timeline.nowOffset() - dp(160))), false));
                    list.addView(timeline); daySummary(list, visible, day);
                } else {
                    for (LessonTime lesson : visible) {
                        lessonCard(list, lesson);
                        if (lesson.start.toLocalDate().isBefore(day)) note(list, "Zajęcia rozpoczęły się poprzedniego dnia.", MUTED);
                    }
                    daySummary(list, visible, day);
                }
            } else {
                if (calendarMode.equals("week")) add(list, "Tydzień: " + week.format(DateTimeFormatter.ofPattern("dd.MM")) + " – " + week.plusDays(6).format(DateTimeFormatter.ofPattern("dd.MM")), 18, INK, true);
                note(list, "Widoczne zajęcia: " + visible.size(), MUTED);
                String date = "";
                for (LessonTime lesson : visible) {
                    LocalDate headingDay = calendarMode.equals("week") && lesson.start.toLocalDate().isBefore(week) ? week : lesson.start.toLocalDate();
                    if (!date.equals(headingDay.toString())) {
                        if (!date.isEmpty()) daySummary(list, visible, LocalDate.parse(date));
                        date = headingDay.toString(); add(list, headingDay.format(DateTimeFormatter.ofPattern("EEEE, dd.MM", Locale.forLanguageTag("pl-PL"))), 18, INK, true);
                    }
                    lessonCard(list, lesson);
                }
                if (!date.isEmpty()) daySummary(list, visible, LocalDate.parse(date));
                if (visible.isEmpty()) {
                    LinearLayout box = card(list, Color.WHITE); add(box, "Brak pasujących danych", 17, INK, true);
                    note(box, "Zmień wyszukiwanie lub wybierz inny tydzień.", MUTED);
                    if (!dataQuery.trim().isEmpty()) box.addView(button("Wyczyść wyszukiwanie planu", () -> { dataQuery = ""; render(); }, false));
                }
            }
            Button export = button("Eksportuj widoczny plan (.ics)", () -> exportPlan(exported), false); export.setEnabled(!exported.isEmpty()); list.addView(export);
            note(list, "Eksport obejmuje widoczne zajęcia. Plik jest kopią planu.", MUTED);
        };
        watchSearch(search, value -> { dataQuery = value; populate.run(); }); populate.run();
        content.addView(nextClass);
        content.addView(button("Odśwież plan", () -> { if (demo) render(); else sync(); }, false));
        int days = demo ? demoPlanDays : Math.max(56, state.optInt("planDays", 56));
        if (days < 112) content.addView(button("Wczytaj kolejne 4 tygodnie", () -> {
            if (demo) { demoPlanDays = Math.min(112, demoPlanDays + 28); render(); return; }
            task(() -> {
                repository.update(s -> s.put("planDays", Math.min(112, days + 28)));
                repository.syncWithProgress(true, message -> runOnUiThread(() -> { if (!isDestroyed()) progress.setText(message); }));
                return repository.read();
            }, result -> { state = result; render(); });
        }, true));
        note(content, "Plan pochodzi z ostatniego pobrania. Pusty dzień oznacza brak zajęć w zapisanych danych; zakres możesz rozszerzyć przyciskiem wczytania kolejnych tygodni.", MUTED);
    }
    private void lessonCard(LinearLayout parent, LessonTime lesson) {
        LinearLayout box = card(parent, Color.WHITE); add(box, lesson.record.title, 18, INK, true);
        DateTimeFormatter clock = DateTimeFormatter.ofPattern("HH:mm");
        add(box, lesson.start.format(clock) + " – " + lesson.end.format(clock) + (lesson.end.toLocalDate().isAfter(lesson.start.toLocalDate()) ? " · następny dzień" : ""), 20, GREEN, true);
        String[] lines = lesson.record.value.split("\n", 2); if (lines.length > 1) add(box, lines[1], 14, MUTED, false);
        if (lesson.current(Instant.now()) && !lesson.tentative()) {
            note(box, "TRWAJĄ TERAZ", GREEN);
            long minutes = Math.max(1, (java.time.Duration.between(Instant.now(), lesson.end.toInstant()).getSeconds() + 59) / 60);
            note(box, "Do końca: " + minutes + " min · według zapisanego planu", GREEN);
        } else note(box, "Czas zajęć: " + java.time.Duration.between(lesson.start.toInstant(), lesson.end.toInstant()).toMinutes() + " min", MUTED);
        box.addView(button("Szczegóły zajęć", () -> lessonDetails(lesson), false));
    }
    private void daySummary(LinearLayout parent, List<LessonTime> visible, LocalDate day) {
        DaySummary summary = DaySummary.of(visible, day);
        if (summary.meetings == 0) return;
        LinearLayout box = card(parent, Color.rgb(232, 243, 236));
        add(box, "Zajęcia: " + summary.meetings + " · czas: " + summary.busyMinutes + " min", 15, INK, true);
        note(box, "Przerwy: " + summary.gapMinutes + " min", MUTED);
        if (summary.conflicts > 0) note(box, "Nakładające się pary zajęć: " + summary.conflicts + ". Sprawdź godziny w szczegółach.", Color.rgb(160, 48, 40));
        if (summary.tentative > 0) note(box, "Terminy orientacyjne: " + summary.tentative + ". Podsumowanie obejmuje również te wpisy.", MUTED);
    }
   private void data() throws Exception {
      JSONObject d = display(); heading("Twoje oceny");
      note(content, "Aktualność danych: " + timestamp(d.optLong("successAt")), MUTED);
      if (!demo && !state.optString("error").isEmpty()) note(content, state.optString("error"), Color.rgb(160, 48, 40));
      JSONObject raw = d.optJSONObject("snapshot");
      if (raw == null) {
         empty("Brak pobranych danych", "Połącz konto i wybierz „Sprawdź teraz”.");
         content.addView(button(connected() ? "Odśwież oceny" : "Połącz konto", () -> { if (connected()) sync(); else navigate(4); }, true));
         return;
      }
      Snapshot snapshot = Snapshot.from(raw);
      if (!semester.isEmpty() && snapshot.records.values().stream().noneMatch(row -> row.kind.equals("ocena") && row.term.equals(semester))) semester = "";
      Button options = button(gradeFiltersExpanded ? "Ukryj filtry i sortowanie" : "Filtry i sortowanie", () -> { gradeFiltersExpanded = !gradeFiltersExpanded; render(); }, false);
      options.setContentDescription((gradeFiltersExpanded ? "Zwiń" : "Rozwiń") + " filtry i sortowanie ocen"); content.addView(options);
      if (gradeFiltersExpanded) {
         semesterPicker(snapshot);
         chips(new String[]{"all", "numeric", "other"}, new String[]{"Wszystkie oceny", "Liczbowe", "Pozostałe"}, gradeType, value -> { gradeType = value; render(); });
         note(content, "Sortowanie przedmiotów", MUTED);
         chips(new String[]{"name", "recent", "value"}, new String[]{"Przedmiot", "Ostatnia zmiana", "Najwyższa ocena"}, gradeOrder, value -> {
            gradeOrder = value; getSharedPreferences("student_tools", MODE_PRIVATE).edit().putString("gradeOrder", value).apply(); render();
         });
      } else note(content, (semester.isEmpty() ? "Wszystkie semestry" : semester) + " · " + (gradeType.equals("numeric") ? "liczbowe" : gradeType.equals("other") ? "pozostałe" : "wszystkie oceny")
         + " · " + (gradeOrder.equals("recent") ? "ostatnia zmiana" : gradeOrder.equals("value") ? "najwyższa ocena" : "wg przedmiotu"), MUTED);
      EditText search = searchField(content, "Szukaj ocen…", gradeQuery);
      search.setContentDescription("Szukaj ocen po przedmiocie, symbolu, komentarzu lub semestrze");
      Button resetGrades = button("Wyczyść filtry ocen", () -> { semester = ""; gradeType = "all"; gradeQuery = ""; render(); }, false); content.addView(resetGrades);
      LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); content.addView(list);
      Runnable populate = () -> {
         list.removeAllViews();
         List<Record> visible = GradeBrowser.select(snapshot.records.values(), semester, gradeType, gradeOrder, gradeQuery);
         for (List<Record> group : GradeBrowser.groups(visible)) gradeCourseCard(list, group);
         gradeSummary(list, visible);
         resetGrades.setVisibility(!semester.isEmpty() || !gradeType.equals("all") || !gradeQuery.trim().isEmpty() ? View.VISIBLE : View.GONE);
         if (visible.isEmpty()) {
            LinearLayout box = card(list, Color.WHITE);
            add(box, gradeQuery.trim().isEmpty() ? "Brak danych w tym zakresie" : "Brak pasujących danych", 17, INK, true);
            note(box, "Zmień semestr, typ ocen lub wyszukiwanie.", MUTED);
         }
         note(list, "Wyświetlono " + visible.size() + " wpisów · grup przedmiotów: " + GradeBrowser.groups(visible).size(), MUTED);
      };
      watchSearch(search, value -> { gradeQuery = value; populate.run(); }); populate.run();
      content.addView(button("Odśwież oceny", () -> { if (demo) render(); else sync(); }, false));
   }
   private void gradeCourseCard(LinearLayout parent, List<Record> rows) {
      Record first = rows.get(0); LinearLayout box = card(parent, Color.WHITE);
      add(box, first.title, 19, INK, true);
      if (!first.term.isEmpty()) add(box, "Semestr: " + first.term, 12, GREEN, true);
      if (rows.size() > 1) note(box, "Wpisy: " + rows.size() + " · wszystkie terminy zachowane", MUTED);
      for (Record row : rows) {
         LinearLayout entry = new LinearLayout(this); entry.setGravity(Gravity.CENTER_VERTICAL); entry.setPadding(0, dp(8), 0, dp(8));
         TextView symbol = text(GradeBrowser.symbol(row), 25, GREEN, true); symbol.setGravity(Gravity.CENTER);
         GradientDrawable badge = new GradientDrawable(); badge.setColor(Color.rgb(232, 243, 236)); badge.setCornerRadius(dp(12)); symbol.setBackground(badge);
         symbol.setMinWidth(dp(64)); symbol.setMaxWidth(dp(112)); symbol.setMinHeight(dp(56)); symbol.setPadding(dp(10), dp(8), dp(10), dp(8));
         entry.addView(symbol, new LinearLayout.LayoutParams(-2, -2));
         int separator = row.value.indexOf('·'); String entryText = separator < 0 ? "Wpis z USOS" : row.value.substring(separator + 1).trim();
         if (entryText.startsWith("termin ")) entryText = "Termin " + entryText.substring(7);
         if (entryText.length() > 180) entryText = entryText.substring(0, 180) + "…";
         TextView description = text(entryText, 14, INK, false); LinearLayout.LayoutParams details = new LinearLayout.LayoutParams(0, -2, 1); details.setMarginStart(dp(14)); entry.addView(description, details);
         entry.setClickable(true); entry.setFocusable(true); entry.setContentDescription("Szczegóły oceny: " + row.title + ", " + row.value);
         android.util.TypedValue ripple = new android.util.TypedValue(); getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true); entry.setBackgroundResource(ripple.resourceId);
         entry.setOnClickListener(v -> gradeDetails(row)); box.addView(entry);
         if (!row.sourceTime.isEmpty()) note(box, "Modyfikacja w USOS: " + row.sourceTime, MUTED);
      }
      note(box, "Dotknij oceny, aby zobaczyć szczegóły.", MUTED);
   }
   private void gradeDetails(Record row) {
      String details = row.value + (row.term.isEmpty() ? "" : "\n\nSemestr: " + row.term)
         + (row.sourceTime.isEmpty() ? "\n\nBrak czasu modyfikacji w USOS." : "\n\nModyfikacja w USOS: " + row.sourceTime + " (Warszawa)");
      new MaterialAlertDialogBuilder(this).setTitle(row.title).setMessage(details).setNegativeButton("Zamknij", null)
         .setPositiveButton("Dodaj zadanie", (dialog, which) -> editTask(null, "Nauka: " + row.title)).show();
   }
   private void semesterPicker(Snapshot snapshot) {
      SortedSet<String> terms = new TreeSet(Comparator.reverseOrder());

      for(Record row : snapshot.records.values()) {
         if (row.kind.equals("ocena") && !row.term.isEmpty()) {
            terms.add(row.term);
         }
      }

      if (!terms.contains(this.semester)) {
         this.semester = "";
      }

      final List<String> values = new ArrayList();
      values.add("");
      values.addAll(terms);
      List<String> labels = new ArrayList();
      labels.add("Wszystkie semestry");
      labels.addAll(terms);
      this.note(this.content, "Semestr", MUTED);
      Spinner picker = new Spinner(this);
      picker.setContentDescription("Filtr semestru");
      picker.setAdapter(new ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels));
      picker.setSelection(values.indexOf(this.semester));
      this.content.addView(picker, new LinearLayout.LayoutParams(-1, this.dp(56)));
      picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
         public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            String selected = (String)values.get(position);
            if (!MainActivity.this.semester.equals(selected)) {
               MainActivity.this.semester = selected;
               MainActivity.this.render();
            }

         }

         public void onNothingSelected(AdapterView<?> parent) {
         }
      });
   }

   private void gradeSummary(LinearLayout parent, List<Record> rows) {
      if (rows.isEmpty()) return;
      GradeSummary summary = GradeSummary.of(rows); LinearLayout box = card(parent, Color.rgb(232, 243, 236));
      LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
      header.addView(text("Podsumowanie ocen", 16, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
      Button help = button("?", () -> {
         String statistics = summary.numeric == 0 ? "Brak ocen liczbowych.\n\n" : String.format(Locale.forLanguageTag("pl-PL"), "Średnia: %.2f\nMinimum: %.2f · maksimum: %.2f\n\n", summary.average, summary.min, summary.max);
         new MaterialAlertDialogBuilder(this).setTitle("Podsumowanie ocen").setMessage(statistics
            + "Podsumowanie obejmuje tylko widoczne wpisy po wyszukiwaniu i filtrach. Symbole będące liczbami (np. 4,5) tworzą prostą średnią, bez wag i punktów ECTS. Różne terminy egzaminów pozostają osobnymi wpisami. Symbole opisowe i oznaczenia takie jak 4+ nie wchodzą do średniej. To nie jest oficjalna średnia uczelni.").setPositiveButton("Rozumiem", null).show();
      }, false);
      help.setContentDescription("Jak liczymy podsumowanie ocen? Minimum i maksimum."); help.setMinWidth(0); help.setPadding(0, 0, 0, 0);
      header.addView(help, new LinearLayout.LayoutParams(dp(48), dp(48))); box.addView(header);
      LinearLayout values = new LinearLayout(this); values.setGravity(Gravity.CENTER_VERTICAL); values.setPadding(0, dp(8), 0, dp(8));
      TextView average = text(summary.numeric == 0 ? "—" : String.format(Locale.forLanguageTag("pl-PL"), "%.2f", summary.average), 32, GREEN, true);
      average.setPadding(0, 0, dp(16), 0); values.addView(average, new LinearLayout.LayoutParams(-2, -2));
      LinearLayout counts = new LinearLayout(this); counts.setOrientation(LinearLayout.VERTICAL);
      counts.addView(text(summary.numeric == 0 ? "Brak ocen liczbowych" : "Prosta średnia", 12, INK, false));
      counts.addView(text("Liczbowe: " + summary.numeric + "\nPozostałe: " + summary.other, 13, MUTED, false));
      values.addView(counts, new LinearLayout.LayoutParams(0, -2, 1)); box.addView(values);
      note(box, "Średnia nieważona · nieoficjalna", MUTED);
   }
   private void exportPlan(List<Record> rows) {
      List<Record> visible = new ArrayList();

      for(Record row : rows) {
         if (StudentSearch.matches(this.dataQuery, row.title, row.value, row.term, row.date)) {
            visible.add(row);
         }
      }

      if (visible.isEmpty()) {
         this.error("Brak widocznych zajęć do eksportu.");
      } else {
         try {
            String calendar = CalendarExport.create(visible, Instant.now());
            if (calendar.getBytes(StandardCharsets.UTF_8).length > 200000) {
               this.error("Plan jest zbyt duży. Zawęź zakres dat lub wyszukiwanie.");
               return;
            }

            this.pendingCalendar = calendar;
            this.startActivityForResult((new Intent("android.intent.action.CREATE_DOCUMENT")).setType("text/calendar").addCategory("android.intent.category.OPENABLE").putExtra("android.intent.extra.TITLE", "vistula-plan-" + LocalDate.now(UsosClient.ZONE) + ".ics"), 23);
         } catch (ActivityNotFoundException var5) {
            this.pendingCalendar = null;
            this.error("Brak aplikacji do zapisania dokumentu.");
         } catch (IllegalArgumentException e) {
            this.error(Repository.message(e));
         }

      }
   }

   private boolean matches(String query, String... values) {
      String haystack = String.join(" ", values).toLowerCase(Locale.ROOT);

      for(String word : query.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
         if (!haystack.contains(word)) {
            return false;
         }
      }

      return true;
   }

   private void watchSearch(EditText search, final Choice action) {
      search.addTextChangedListener(new TextWatcher() {
         public void beforeTextChanged(CharSequence s, int st, int count, int after) {
         }

         public void onTextChanged(CharSequence s, int st, int before, int count) {
            action.choose(s.toString());
         }

         public void afterTextChanged(Editable e) {
         }
      });
   }

   private void account() {
      this.title("Konto i ustawienia", "Akademia Finansów i Biznesu Vistula");
      if (this.demo) {
         this.content.addView(this.button("Zakończ tryb demonstracyjny", () -> {
            this.demo = false;
            this.render();
         }, true));
      }

      if (this.connected()) {
         LinearLayout box = this.card(this.content, -1);
         this.add(box, this.state.optString("user", "Konto USOS"), 19, INK, true);
         this.add(box, "Zgoda OAuth: " + this.state.optString("scopes", "grades|studies|offline_access"), 13, MUTED, false);
         MaterialSwitch toggle = new MaterialSwitch(this);
         toggle.setText(string.background_check);
         toggle.setTextColor(INK);
         toggle.setChecked(this.state.optBoolean("enabled"));
         if (this.state.optBoolean("authorizationError")) {
            toggle.setEnabled(false);
            this.note(box, "Sesja wygasła. Odłącz konto poniżej i zaloguj się ponownie. Zapisane dane pozostają dostępne do czasu odłączenia.", Color.rgb(167, 49, 49));
         }
         box.addView(toggle);
         toggle.setOnCheckedChangeListener((v, checked) -> this.task(() -> {
               this.repository.update((s) -> s.put("enabled", checked));
               return this.repository.read();
            }, (s) -> {
               this.state = s;
               if (checked) {
                  SyncWorker.schedule(this, this.state.optLong("interval", 30L));
               } else {
                  SyncWorker.cancel(this);
               }

               this.render();
            }));
         this.add(box, "Częstotliwość", 14, MUTED, true);
         Spinner interval = new Spinner(this);
         interval.setAdapter(new ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"15 minut", "30 minut", "1 godzina", "2 godziny"}));
         long[] values = new long[]{15L, 30L, 60L, 120L};
         long selected = this.state.optLong("interval", 30L);
         int position = 1;

         for(int i = 0; i < values.length; ++i) {
            if (selected == values[i]) {
               position = i;
            }
         }

         interval.setSelection(position);
         box.addView(interval);
         box.addView(this.button("Zapisz częstotliwość", () -> this.task(() -> {
               this.repository.update((s) -> s.put("interval", values[interval.getSelectedItemPosition()]));
               return this.repository.read();
            }, (s) -> {
               this.state = s;
               this.ensureSchedule();
               this.render();
            }), false));
         box.addView(this.button("Odłącz konto i usuń lokalne dane", () -> (new MaterialAlertDialogBuilder(this)).setTitle("Odłączyć konto?").setMessage("Tokeny, zapis ocen, plan, historia i własne zadania zostaną usunięte z tego telefonu. Zgodę dla aplikacji możesz odwołać również w USOSapps.").setNegativeButton("Anuluj", (DialogInterface.OnClickListener)null).setPositiveButton("Odłącz", (dialog, which) -> this.task(() -> {
                  SyncWorker.cancel(this);
                  this.repository.disconnect();
                  return this.repository.read();
               }, (s) -> {
                  this.state = s;
                  this.demo = false;
                  this.render();
               })).show(), false));
      } else {
         LinearLayout box = this.card(this.content, -1);
         this.add(box, "Połącz z USOS", 20, INK, true);
         this.add(box, "Wpisz klucz aplikacji z USOSapps. Hasło uczelniane podajesz wyłącznie na stronie uczelni w przeglądarce.", 14, MUTED, false);
         EditText key = this.field(box, "Consumer Key", "", false);
         EditText secret = this.field(box, "Consumer Secret", "", true);
         EditText scopes = this.field(box, "Zakresy zgody OAuth (oddzielone |)", this.state.optString("scopes", "grades|studies|offline_access"), false);
         this.add(box, "Monitor wymaga grades, studies i offline_access. Inne zakresy dodaj tylko dla metod, których chcesz używać w katalogu API.", 12, MUTED, false);
         box.addView(this.button("1. Zaloguj w przeglądarce", () -> {
            String k = key.getText().toString().trim();
            String sec = secret.getText().toString().trim();
            String sc = scopes.getText().toString().trim();
            if (!k.isEmpty() && !sec.isEmpty()) {
               Set<String> requested = new HashSet(Arrays.asList(sc.split("\\|")));
               if (!requested.containsAll(List.of("grades", "studies", "offline_access"))) {
                  this.error("Dodaj wymagane zakresy: grades|studies|offline_access.");
               } else {
                  this.task(() -> {
                     this.repository.begin(k, sec, sc);
                     return this.repository.read();
                  }, (s) -> {
                     this.state = s;
                     this.render();
                     this.browser("https://usosapps.vistula.edu.pl/services/oauth/authorize?oauth_token=" + OAuth.encode(s.getString("requestToken")) + "&interactivity=confirm_user");
                  });
               }
            } else {
               this.error("Wpisz Consumer Key i Consumer Secret. Rejestrację klucza możesz wykonać później.");
            }
         }, true));
         if (!this.state.optString("requestToken").isEmpty()) {
            this.add(box, "Po zaakceptowaniu zgody skopiuj kod PIN z USOS i wróć do aplikacji.", 14, MUTED, false);
            EditText pin = this.field(box, "PIN autoryzacji z USOS", "", true);
            box.addView(this.button("2. Potwierdź PIN i połącz", () -> {
               String value = pin.getText().toString().trim();
               if (value.isEmpty()) {
                  this.error("Wpisz kod PIN wyświetlony przez USOS.");
               } else {
                  this.task(() -> {
                     this.repository.finish(value);
                     return this.repository.read();
                  }, (s) -> {
                     this.state = s;
                     this.demo = false;
                     this.tab = 0;
                     this.ensureSchedule();
                     this.render();
                     if (VERSION.SDK_INT >= 33 && this.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        this.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 10);
                     }

                     this.sync();
                  });
               }
            }, true));
            box.addView(this.button("Wróć do autoryzacji w przeglądarce", () -> this.browser("https://usosapps.vistula.edu.pl/services/oauth/authorize?oauth_token=" + OAuth.encode(this.state.optString("requestToken")) + "&interactivity=confirm_user"), false));
         }

         this.content.addView(this.button("Uruchom tryb demonstracyjny", () -> {
            this.demo = true;
            this.tab = 0;
            this.render();
         }, false));
      }

      LinearLayout notices = this.card(this.content, -1);
      this.add(notices, "Powiadomienia", 18, INK, true);
      this.add(notices, NotificationManagerCompat.from(this).areNotificationsEnabled() ? "System zezwala na powiadomienia." : "Powiadomienia są wyłączone. Historia nadal zapisuje zmiany.", 14, MUTED, false);
      notices.addView(this.button("Ustawienia powiadomień", this::requestNotifications, false));
      this.add(notices, "Tokeny i historia są szyfrowane na telefonie. Aplikacja nie przechowuje hasła do uczelni i nie ma własnego serwera.", 13, MUTED, false);
      this.add(notices, "Czas prezentowany w strefie Europe/Warsaw. Historia: 200 wpisów. Plan: 8–16 tygodni. Oceny: semestry zwrócone dla konta przez USOS.", 12, MUTED, false);
      this.content.addView(this.button("Otwórz USOSapps Vistuli", () -> this.browser("https://usosapps.vistula.edu.pl/"), false));
   }

   private void api() {
      this.title("Katalog USOS API", this.catalog.length() + " metod · oficjalna dokumentacja Vistuli");
      this.note(this.content, "Dokumentacja jest dostępna offline. Wykonanie żądania wymaga internetu oraz uprawnień wskazanych przy metodzie. Tryb demo nie podpisuje żądań przykładowym kontem.", MUTED);
      this.chips(new String[]{"all", "favorites"}, new String[]{"Wszystkie metody", "Ulubione"}, apiFavoritesOnly ? "favorites" : "all", value -> {
         apiFavoritesOnly = value.equals("favorites"); apiLimit = 60; render();
      });
      EditText search = this.field(this.content, "Szukaj metody, modułu lub opisu…", this.apiQuery, false);
      final LinearLayout list = new LinearLayout(this);
      list.setOrientation(LinearLayout.VERTICAL);
      this.content.addView(list);
      this.populateMethods(list, this.apiQuery);
      search.addTextChangedListener(new TextWatcher() {
         public void beforeTextChanged(CharSequence s, int st, int count, int after) {
         }

         public void onTextChanged(CharSequence s, int st, int before, int count) {
            MainActivity.this.apiQuery = s.toString();
            MainActivity.this.apiLimit = 60;
            MainActivity.this.populateMethods(list, MainActivity.this.apiQuery);
         }

         public void afterTextChanged(Editable e) {
         }
      });
   }

   private void populateMethods(LinearLayout list, String search) {
      list.removeAllViews();
      String needle = search.trim().toLowerCase(Locale.ROOT);
      int count = 0;
      int shown = 0;

      try {
         for(int i = 0; i < this.catalog.length(); ++i) {
            JSONObject entry = this.catalog.getJSONObject(i);
            String name = entry.getString("name");
            String brief = entry.optString("brief_description").trim();
            if ((!apiFavoritesOnly || apiFavorites.contains(name)) && (name + " " + brief).toLowerCase(Locale.ROOT).contains(needle)) {
               ++count;
               if (shown++ < this.apiLimit) {
                  Button b = this.button(name.substring("services/".length()) + "\n" + brief, () -> this.showMethod(name), false);
                  b.setGravity(8388627);
                  b.setTextSize(13.0F);
                  list.addView(b, new LinearLayout.LayoutParams(-1, -2));
               }
            }
         }
      } catch (JSONException var11) {
         this.error("Nie można odczytać katalogu API.");
      }

      this.note(list, count == 0 ? "Brak pasujących metod. Spróbuj innej nazwy lub opisu." : "Wyświetlono " + Math.min(count, this.apiLimit) + " z " + count + " pasujących metod.", MUTED);
      if (count > this.apiLimit) {
         list.addView(this.button("Pokaż kolejne metody", () -> {
            this.apiLimit += 60;
            this.populateMethods(list, this.apiQuery);
         }, false));
      }

   }

   private void showMethod(String name) {
      this.task(() -> new JSONObject(this.asset("api/" + name.replace("/", "__") + ".json")), this::methodView);
   }

   private void methodView(JSONObject metadata) throws Exception {
      this.uploadBytes = null;
      this.uploadMethod = null;
      this.uploadButton = null;
      this.exportResponse = null;
      this.currentMethod = metadata.getString("name");
      this.content.removeAllViews();
      this.scroll.smoothScrollTo(0, 0);
      this.content.addView(this.button("← Wróć do katalogu", () -> {
         this.currentMethod = null;
         this.tab = 3;
         this.render();
      }, false));
      this.title(this.currentMethod.substring("services/".length()), this.plain(metadata.optString("brief_description")));
      String favoriteMethod = this.currentMethod;
      Button favorite = this.button(apiFavorites.contains(favoriteMethod) ? "Usuń z ulubionych" : "Dodaj do ulubionych", () -> {}, false);
      favorite.setOnClickListener(v -> {
         if (busy) return;
         if (!apiFavorites.remove(favoriteMethod)) apiFavorites.add(favoriteMethod);
         getSharedPreferences("api_catalog", MODE_PRIVATE).edit().putStringSet("favorites", new HashSet<>(apiFavorites)).apply();
         favorite.setText(apiFavorites.contains(favoriteMethod) ? "Usuń z ulubionych" : "Dodaj do ulubionych");
      });
      content.addView(favorite);
      JSONObject auth = metadata.getJSONObject("auth_options");
      LinearLayout docs = this.card(this.content, -1);
      this.add(docs, "Dostęp i uprawnienia", 17, INK, true);
      this.add(docs, "Klucz: " + auth.optString("consumer") + " · token: " + auth.optString("token") + "\nZakresy: " + auth.optJSONArray("scopes") + (auth.optBoolean("administrative_only") ? "\nWYMAGANY KLUCZ ADMINISTRACYJNY UCZELNI" : ""), 13, MUTED, false);
      if (metadata.optBoolean("beta")) {
         this.note(docs, "Metoda beta — może zmienić zachowanie.", GREEN);
      }

      if (!metadata.isNull("deprecated")) {
         this.note(docs, "Metoda wycofywana: " + this.plain(metadata.opt("deprecated").toString()), Color.rgb(167, 99, 24));
      }

      this.add(docs, this.plain(metadata.optString("description")), 14, INK, false);
      JSONArray arguments = metadata.getJSONArray("arguments");
      LinkedHashMap<String, EditText> inputs = new LinkedHashMap();
      Set<String> required = new HashSet();
      LinearLayout params = this.card(this.content, -1);
      this.add(params, "Parametry żądania", 18, INK, true);

      for(int i = 0; i < arguments.length(); ++i) {
         JSONObject arg = arguments.getJSONObject(i);
         String key = arg.getString("name");
         if (!Set.of("oauth_consumer_key", "oauth_nonce", "oauth_timestamp", "oauth_signature", "oauth_signature_method", "oauth_version", "oauth_token").contains(key)) {
            boolean needed = arg.optBoolean("is_required");
            if (needed) {
               required.add(key);
            }

            String defaultValue = arg.isNull("default_value") ? "" : arg.optString("default_value");
            this.add(params, key + (needed ? " *" : "") + (defaultValue.isEmpty() ? "" : " · domyślnie: " + defaultValue), 13, INK, true);
            if (key.equals("data") && arg.optString("description").contains("multipart/form-data")) {
               this.uploadButton = this.button("Wybierz plik do wysłania (maks. 8 MB)", () -> {
                  this.uploadMethod = this.currentMethod;
                  this.startActivityForResult((new Intent("android.intent.action.OPEN_DOCUMENT")).setType("*/*").addCategory("android.intent.category.OPENABLE"), 21);
               }, false);
               params.addView(this.uploadButton);
               this.add(params, this.plain(arg.optString("description")), 12, MUTED, false);
            } else {
               EditText input = this.field(params, needed ? "Wymagany parametr" : "Puste = domyślna wartość serwera", "", key.toLowerCase(Locale.ROOT).contains("secret"));
               inputs.put(key, input);
               this.add(params, this.plain(arg.optString("description")), 12, MUTED, false);
            }
         }
      }

      this.add(params, "Transport", 13, INK, true);
      Spinner transport = new Spinner(this);
      transport.setAdapter(new ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"GET", "POST (formularz)"}));
      params.addView(transport);
      TextView result = this.text("Odpowiedź pojawi się po wykonaniu żądania.", 12, INK, false);
      result.setTextIsSelectable(true);
      result.setTypeface(Typeface.MONOSPACE);
      params.addView(this.button("Wykonaj żądanie API", () -> {
         Map<String, String> values = new LinkedHashMap();

         for(Map.Entry<String, EditText> input : inputs.entrySet()) {
            String value = ((EditText)input.getValue()).getText().toString();
            if (value.trim().isEmpty()) {
               if (required.contains(input.getKey())) {
                  input.getValue().setError("Wymagany parametr");
                  input.getValue().requestFocus();
                  this.error("Wypełnij parametr: " + (String)input.getKey());
                  return;
               }
            } else {
               values.put((String)input.getKey(), value);
            }
         }

         if (this.uploadButton != null && this.uploadBytes == null) {
            this.error("Wybierz plik do wysłania.");
         } else {
            String method = transport.getSelectedItemPosition() == 0 ? "GET" : "POST";
            String endpoint = metadata.optString("name");
            (new MaterialAlertDialogBuilder(this)).setTitle("Wykonać żądanie " + method + "?").setMessage(endpoint + "\n\nWybrane parametry zostaną przekazane Vistuli. Niektóre metody mogą zmieniać lub usuwać dane w USOS. Sprawdź opis tej metody przed wykonaniem.").setNegativeButton("Anuluj", (DialogInterface.OnClickListener)null).setPositiveButton("Wykonaj", (dialog, which) -> {
               this.exportResponse = null;
               result.setText(R.string.api_sending);
               this.task(() -> {
                  JSONObject credentials = this.repository.read();
                  if (this.demo) {
                     credentials = new JSONObject();
                  }

                  return (new UsosClient(credentials)).explore(metadata, values, method, this.uploadBytes, "upload.bin");
               }, (response) -> {
                  this.exportResponse = response;
                  String formatted = response.binary() ? "Odpowiedź binarna: " + response.mime + " · " + response.bytes.length + " bajtów. Użyj „Zapisz odpowiedź”." : response.text();

                  try {
                     Object parsed = (new JSONTokener(response.text())).nextValue();
                     if (parsed instanceof JSONObject) {
                        formatted = ((JSONObject)parsed).toString(2);
                     } else if (parsed instanceof JSONArray) {
                        formatted = ((JSONArray)parsed).toString(2);
                     }
                  } catch (JSONException var6) {
                  }

                  if (endpoint.startsWith("services/oauth/") || endpoint.startsWith("services/oauth2/")) {
                     formatted = "Wykonano operację OAuth. Odpowiedź ukryta ze względu na możliwe dane uwierzytelniające. Do logowania użyj ekranu Konto.";
                     this.exportResponse = null;
                  }

                  if (formatted.length() > 100000) {
                     formatted = formatted.substring(0, 100000) + "\n[Podgląd ograniczony do 100 000 znaków]";
                  }

                  result.setText(formatted);
               }, message -> result.setText(getString(R.string.api_failed, message)));
            }).show();
         }
      }, true));
      LinearLayout output = this.card(this.content, -1);
      this.add(output, "Odpowiedź API", 17, INK, true);
      output.addView(result);
      output.addView(this.button("Zapisz odpowiedź", () -> {
         if (this.exportResponse == null) {
            this.error("Najpierw wykonaj żądanie zwracające dane do zapisania.");
         } else {
            String extension = this.exportResponse.mime.contains("json") ? ".json" : (this.exportResponse.mime.contains("pdf") ? ".pdf" : (this.exportResponse.mime.contains("calendar") ? ".ics" : (this.exportResponse.binary() ? ".bin" : ".txt")));
            this.startActivityForResult((new Intent("android.intent.action.CREATE_DOCUMENT")).setType(this.exportResponse.mime).addCategory("android.intent.category.OPENABLE").putExtra("android.intent.extra.TITLE", "usos-response" + extension), 22);
         }
      }, false));
      LinearLayout returns = this.card(this.content, -1);
      this.add(returns, "Struktura odpowiedzi", 17, INK, true);
      this.add(returns, this.plain(metadata.optString("returns")), 13, MUTED, false);
      JSONArray fields = metadata.optJSONArray("result_fields");
      if (fields != null && fields.length() > 0) {
         this.add(returns, "Pola odpowiedzi (parametr fields)", 16, INK, true);

         for(int i = 0; i < fields.length(); ++i) {
            JSONObject f = fields.getJSONObject(i);
            this.add(returns, f.getString("name") + (f.optBoolean("is_secondary") ? " · zagnieżdżone" : ""), 13, INK, true);
            this.add(returns, this.plain(f.optString("description")), 12, MUTED, false);
         }
      }

      if (!metadata.optString("errors").isEmpty()) {
         this.add(returns, "Błędy", 16, INK, true);
         this.add(returns, this.plain(metadata.optString("errors")), 13, MUTED, false);
      }

      this.content.addView(this.button("Dokumentacja online", () -> this.browser("https://usosapps.vistula.edu.pl/developers/api/" + metadata.optString("name").substring("services/".length(), metadata.optString("name").lastIndexOf(47)) + "/"), false));
      this.note(this.content, "GET, POST i wysyłanie plików blobbox przez multipart. Odpowiedzi tekstowe i pliki można zapisać przez systemowy wybór dokumentu. Limit transferu: 8 MB. Interaktywne metody OAuth obsługuje ekran Konto. Uprawnienia sprawdza USOS.", MUTED);
   }

   protected void onActivityResult(int requestCode, int resultCode, Intent data) {
      super.onActivityResult(requestCode, resultCode, data);
      if (resultCode == -1 && data != null && data.getData() != null) {
         Uri uri = data.getData();
         if (requestCode == 23) {
            if (this.pendingCalendar == null) {
               this.error("Nie można odzyskać eksportu. Ponownie wybierz eksport planu.");
               return;
            }

            byte[] bytes = this.pendingCalendar.getBytes(StandardCharsets.UTF_8);
            this.pendingCalendar = null;
            this.task(() -> {
               OutputStream output = this.getContentResolver().openOutputStream(uri);

               try {
                  if (output == null) {
                     throw new IOException();
                  }

                  output.write(bytes);
               } catch (Throwable var7) {
                  if (output != null) {
                     try {
                        output.close();
                     } catch (Throwable var6) {
                        var7.addSuppressed(var6);
                     }
                  }

                  throw var7;
               }

               if (output != null) {
                  output.close();
               }

               return true;
            }, (ok) -> Toast.makeText(this, "Zapisano plan w formacie kalendarza", Toast.LENGTH_SHORT).show());
         } else if (requestCode == 21 && this.currentMethod != null && this.currentMethod.equals(this.uploadMethod)) {
            this.task(() -> {
               InputStream input = this.getContentResolver().openInputStream(uri);

               byte[] var6;
               try {
                  ByteArrayOutputStream bytes = new ByteArrayOutputStream();

                  try {
                     if (input == null) {
                        throw new IOException();
                     }

                     byte[] buffer = new byte[8192];

                     int count;
                     while((count = input.read(buffer)) != -1) {
                        if (bytes.size() + count > 8388608) {
                           throw new IllegalArgumentException("Plik przekracza limit 8 MB.");
                        }

                        bytes.write(buffer, 0, count);
                     }

                     var6 = bytes.toByteArray();
                  } catch (Throwable var9) {
                     try {
                        bytes.close();
                     } catch (Throwable var8) {
                        var9.addSuppressed(var8);
                     }

                     throw var9;
                  }

                  bytes.close();
               } catch (Throwable var10) {
                  if (input != null) {
                     try {
                        input.close();
                     } catch (Throwable var7) {
                        var10.addSuppressed(var7);
                     }
                  }

                  throw var10;
               }

               if (input != null) {
                  input.close();
               }

               return var6;
            }, (bytesx) -> {
               this.uploadBytes = bytesx;
               this.uploadButton.setText(this.getString(string.selected_upload, new Object[]{bytesx.length}));
            });
         } else if (requestCode == 22 && this.exportResponse != null) {
            byte[] bytes = this.exportResponse.bytes;
            this.task(() -> {
               OutputStream output = this.getContentResolver().openOutputStream(uri);

               try {
                  if (output == null) {
                     throw new IOException();
                  }

                  output.write(bytes);
               } catch (Throwable var7) {
                  if (output != null) {
                     try {
                        output.close();
                     } catch (Throwable var6) {
                        var7.addSuppressed(var6);
                     }
                  }

                  throw var7;
               }

               if (output != null) {
                  output.close();
               }

               return true;
            }, (ok) -> Toast.makeText(this, "Zapisano odpowiedź API", Toast.LENGTH_SHORT).show());
         }

      } else {
         if (requestCode == 23) {
            this.pendingCalendar = null;
         }

      }
   }


    @Override public void onBackPressed() {
        if (busy) return;
        if (drawer.isDrawerOpen(GravityCompat.START)) { drawer.closeDrawers(); return; }
        if (currentMethod != null) { currentMethod = null; render(); }
        else if (tab != 0) { tab = 0; render(); }
        else super.onBackPressed();
    }
    private String asset(String path) throws IOException {
        try (InputStream input = getAssets().open(path); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return bytes.toString("UTF-8");
        }
    }
    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
    }
    private void browser(String address) {
        Uri uri = Uri.parse(address);
        if (!"https".equals(uri.getScheme()) || !"usosapps.vistula.edu.pl".equals(uri.getHost())) { error("Adres spoza USOS Vistuli."); return; }
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (ActivityNotFoundException e) { error("Zainstaluj przeglądarkę, aby otworzyć USOS."); }
    }
    private void error(String message) {
        new MaterialAlertDialogBuilder(this).setTitle("Nie udało się wykonać operacji").setMessage(message).setPositiveButton("OK", null)
            .setNeutralButton("Skopiuj opis błędu", (dialog, which) -> {
                android.content.ClipboardManager clipboard = getSystemService(android.content.ClipboardManager.class);
                if (clipboard != null) clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Błąd aplikacji", message));
            }).show();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setLineSpacing(dp(3), 1f); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }
    private void add(LinearLayout target, String value, int size, int color, boolean bold) {
        TextView t = text(value, size, color, bold); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(12); target.addView(t, p);
    }
    private LinearLayout card(LinearLayout parent, int color) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20), dp(20), dp(20), dp(12));
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(20));
        if (color == Color.WHITE) shape.setStroke(dp(1), Color.rgb(224, 231, 225)); box.setBackground(shape);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(14); parent.addView(box, p); return box;
    }
    private Button button(String label, Runnable action, boolean primary) {
        MaterialButton b = new MaterialButton(this); b.setMaxLines(4); b.setSingleLine(false); b.setText(label); b.setAllCaps(false); b.setTextSize(14);
        b.setTextColor(primary ? Color.WHITE : GREEN); b.setCornerRadius(dp(14));
        b.setBackgroundTintList(ColorStateList.valueOf(primary ? GREEN : Color.rgb(232, 243, 236)));
        b.setPadding(dp(16), dp(10), dp(16), dp(10)); b.setMinHeight(dp(48));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(8); b.setLayoutParams(p);
        b.setOnClickListener(v -> { if (!busy) action.run(); }); return b;
    }
    private EditText searchField(LinearLayout parent, String hint, String value) {
        EditText input = field(parent, hint, value, false);
        TextInputLayout wrapper = (TextInputLayout) parent.getChildAt(parent.getChildCount() - 1);
        wrapper.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        wrapper.setEndIconContentDescription("Wyczyść wyszukiwanie");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT); input.setSingleLine(true);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        input.setOnEditorActionListener((view, action, event) -> {
            if (action != android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) return false;
            android.view.inputmethod.InputMethodManager keyboard = getSystemService(android.view.inputmethod.InputMethodManager.class);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
            input.clearFocus(); return true;
        });
        return input;
    }
    private EditText field(LinearLayout parent, String hint, String value, boolean password) {
        TextInputLayout wrapper = new TextInputLayout(this);
        wrapper.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE); wrapper.setHint(hint);
        wrapper.setBoxCornerRadii(dp(12), dp(12), dp(12), dp(12));
        if (password) wrapper.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        TextInputEditText e = new TextInputEditText(wrapper.getContext()); e.setText(value); e.setTextSize(14); e.setTextColor(INK);
        e.setSaveEnabled(false); wrapper.setSaveEnabled(false); e.setMinHeight(dp(56));
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | (password ? android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD : android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE));
        if (password) e.setTransformationMethod(PasswordTransformationMethod.getInstance());
        wrapper.addView(e, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(16); parent.addView(wrapper, p); return e;
    }
    private void heading(String value) { add(content, value, 20, INK, true); }
    private void title(String value, String description) { add(content, value, 28, INK, true); add(content, description, 14, MUTED, false); }
    private void empty(String title, String description) { LinearLayout box = card(content, Color.WHITE); add(box, title, 17, INK, true); add(box, description, 14, MUTED, false); }
    private void note(LinearLayout parent, String value, int color) { add(parent, value, 12, color, false); }
    private interface Choice { void choose(String value); }
    private void chips(String[] values, String[] labels, String selected, Choice action) {
        LinearLayout line = new LinearLayout(this);
        HorizontalScrollView strip = new HorizontalScrollView(this); strip.setHorizontalScrollBarEnabled(false); strip.addView(line);
        Button active = null;
        for (int i = 0; i < values.length; i++) {
            final String value = values[i]; Button b = button(labels[i], () -> action.choose(value), value.equals(selected));
            b.setSelected(value.equals(selected)); b.setSingleLine(true); b.setMinHeight(dp(48));
            if (value.equals(selected)) active = b;
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2); p.rightMargin = dp(6); line.addView(b, p);
        }
        Button selection = active; if (selection != null) strip.post(() -> strip.smoothScrollTo(Math.max(0, selection.getLeft() - dp(12)), 0));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(12); content.addView(strip, p);
    }
    private String timestamp(long millis) {
        return millis == 0 ? "Jeszcze nie sprawdzano" : Instant.ofEpochMilli(millis).atZone(UsosClient.ZONE)
            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm:ss", Locale.forLanguageTag("pl-PL")));
    }
    private String plain(String html) { return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().trim(); }
}
