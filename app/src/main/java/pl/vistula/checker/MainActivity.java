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
import pl.vistula.checker.core.PaymentInfo;
import pl.vistula.checker.core.AcademicTerm;
import pl.vistula.checker.core.ThesisInfo;
import pl.vistula.checker.core.CourseTestRecord;
import pl.vistula.checker.core.GpaCalculator;
import pl.vistula.checker.core.IcsExporter;
import pl.vistula.checker.core.NewsArticle;
import pl.vistula.checker.core.ScheduleConflictDetector;

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
    private double simTargetGpa = 4.50;
    private double simRemainingEcts = 30.0;
    private String filter = "wszystkie", dataFilter = "ocena";
    private String dataQuery = "", historyQuery = "", planRange = "all";
    private String gradeQuery = "", gradeType = "all", gradeOrder = "name";
    private boolean gradeFiltersExpanded;
    private String semester = "", taskFilter = "all", taskQuery = "", pendingCalendar;
    private String taskOrder = "due";
    private String calendarDate = "", calendarMode = "list";
    private String newsQuery = "";
    private String attendanceFilter = "all";
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
            if (!busy && currentMethod == null && tab != 4 && tab != 3 && tab != 8 && tab != 9 && tab != 10 && tab != 11 && tab != 12 && !(getCurrentFocus() instanceof EditText)) load();
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
            newsQuery = bundle.getString("newsQuery", "");
            attendanceFilter = bundle.getString("attendanceFilter", "all");
            try { demoTasks = new JSONArray(bundle.getString("demoTasks", "[]")); } catch (JSONException ignored) {}
            apiQuery = bundle.getString("apiQuery", ""); apiLimit = bundle.getInt("apiLimit", 60);
            apiFavoritesOnly = bundle.getBoolean("apiFavoritesOnly");
            simTargetGpa = bundle.getDouble("simTargetGpa", 4.50);
            simRemainingEcts = bundle.getDouble("simRemainingEcts", 30.0);
        } else if (getIntent().getBooleanExtra("showHistory", false)) tab = 1;
        else if (getIntent().hasExtra("tab")) tab = getIntent().getIntExtra("tab", 0);
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
        int[] sections = {0, 2, 5, 1, 11, 12, 9, 8, 7, 10, 4, 3};
        String[] names = {"Start", "Oceny", "Plan zajęć", "Historia zmian", "Komunikaty uczelni", "Licznik nieobecności", "Kalkulator średniej", "Płatności", "Zadania", "Niezbędnik i Kampus", "Konto i profil", "Katalog API"};
        int[] sectionIcons = {R.drawable.ic_nav_home, R.drawable.ic_nav_grades, R.drawable.ic_nav_plan, R.drawable.ic_nav_history, R.drawable.ic_nav_news, R.drawable.ic_nav_attendance, R.drawable.ic_nav_grades, R.drawable.ic_nav_more, R.drawable.ic_nav_tasks, R.drawable.ic_nav_home, R.drawable.ic_nav_account, R.drawable.ic_nav_api};
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
        out.putDouble("simTargetGpa", simTargetGpa);
        out.putDouble("simRemainingEcts", simRemainingEcts);
        super.onSaveInstanceState(out); out.putBoolean("demo", demo); out.putBoolean("demoChanged", demoChanged); out.putInt("tab", tab);
        out.putString("filter", filter); out.putString("dataFilter", dataFilter);
        out.putString("semester", semester); out.putString("taskFilter", taskFilter); out.putString("taskQuery", taskQuery);
        out.putString("taskOrder", taskOrder);
        out.putString("calendarDate", calendarDate); out.putString("calendarMode", calendarMode); out.putInt("demoPlanDays", demoPlanDays);
        out.putString("pendingCalendar", pendingCalendar);
        out.putString("newsQuery", newsQuery);
        out.putString("attendanceFilter", attendanceFilter);
        out.putString("demoTasks", demoTasks.toString());
        out.putString("dataQuery", dataQuery); out.putString("historyQuery", historyQuery); out.putString("planRange", planRange);
        out.putString("gradeQuery", gradeQuery); out.putString("gradeType", gradeType); out.putString("gradeOrder", gradeOrder);
        out.putBoolean("gradeFiltersExpanded", gradeFiltersExpanded);
        out.putString("apiQuery", apiQuery); out.putInt("apiLimit", apiLimit);
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra("showHistory", false)) navigate(1);
        else if (intent.hasExtra("tab")) navigate(intent.getIntExtra("tab", 0));
    }
    @Override protected void onResume() { super.onResume(); handler.removeCallbacks(refresh); handler.postDelayed(refresh, 30000); if (content != null && !busy && tab != 4 && tab != 3 && tab != 8 && tab != 9 && tab != 10 && tab != 11 && tab != 12) load(); }
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
        int selected = tab == 3 || tab == 4 || tab == 7 || tab == 8 || tab == 9 || tab == 10 || tab == 11 || tab == 12 ? 6 : tab;
        navigation.getMenu().findItem(100 + selected).setChecked(true);
        for (int i = 0; i < sideMenu.getMenu().size(); i++) sideMenu.getMenu().getItem(i).setChecked(sideMenu.getMenu().getItem(i).getItemId() == 200 + tab);
        String[] titles = {"Start", "Historia zmian", "Oceny", "Katalog API", "Konto i profil", "Plan zajęć", "Więcej", "Zadania", "Płatności i subkonto", "Kalkulator średniej", "Niezbędnik i Kampus", "Komunikaty uczelni", "Licznik nieobecności"};
        toolbar.setTitle(tab < titles.length ? titles[tab] : "USOS Vistula"); toolbar.setSubtitle(demo ? "Konto demonstracyjne" : connected() ? state.optString("user", "USOS Vistula") : "USOS Vistula");
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
                case 8: paymentsView(); break;
                case 9: gpaSimulatorView(); break;
                case 10: campusGuideView(); break;
                case 11: newsView(); break;
                case 12: attendanceView(); break;
            }
        } catch (Exception e) { note(content, Repository.message(e), Color.rgb(167, 49, 49)); }
        updateBusy(busy);
    }
    void navigate(int target) {
        currentMethod = null; tab = target; render(); scroll.post(() -> scroll.scrollTo(0, 0));
    }
    private void more() {
        title("Więcej", "Wszystkie narzędzia w jednym miejscu.");
        content.addView(button("Komunikaty i aktualności uczelni", () -> navigate(11), true));
        content.addView(button("Licznik nieobecności na zajęciach", () -> navigate(12), true));
        content.addView(button("Kalkulator średniej i stypendium", () -> navigate(9), false));
        content.addView(button("Płatności i subkonto", () -> navigate(8), false));
        content.addView(button("Niezbędnik studenta i kampus", () -> navigate(10), false));
        content.addView(button("Zadania", () -> navigate(7), false));
        content.addView(button("Konto i profil", () -> navigate(4), false));
        content.addView(button("Katalog USOS API", () -> navigate(3), false));
        note(content, "Zadania i licznik nieobecności zapisujesz dla siebie na telefonie. Oceny, plan i płatności pochodzą z USOS.", MUTED);
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
      JSONObject rawSnap = d.optJSONObject("snapshot");
      if (rawSnap != null) {
         Snapshot snap = Snapshot.from(rawSnap);
         LocalDate today = LocalDate.now(UsosClient.ZONE);
         AcademicTerm currentTerm = null;
         for (AcademicTerm t : snap.terms.values()) {
            if (t.isCurrent(today)) { currentTerm = t; break; }
         }
         if (currentTerm == null && !snap.terms.isEmpty()) currentTerm = snap.terms.values().iterator().next();
         if (currentTerm != null) {
            LinearLayout termBox = this.card(this.content, Color.rgb(238, 245, 242));
            this.add(termBox, "KALENDARZ AKADEMICKI", 11, GREEN, true);
            this.add(termBox, currentTerm.name, 17, INK, true);
            long days = currentTerm.daysRemaining(today);
            String termInfo = days >= 0 ? "Do końca semestru: " + days + " dni · koniec: " + currentTerm.endDate : "Zakończony: " + currentTerm.endDate;
            this.add(termBox, termInfo, 13, MUTED, false);
         }
         if (snap.payments != null) {
            double unpaid = snap.payments.totalUnpaidAmount();
            if (unpaid > 0 || snap.payments.hasOverdue()) {
               LinearLayout payBox = this.card(this.content, snap.payments.hasOverdue() ? Color.rgb(255, 240, 240) : Color.WHITE);
               this.add(payBox, snap.payments.hasOverdue() ? "PŁATNOŚCI · WYMAGANA UWAGA" : "PŁATNOŚCI I CZESNE", 11, snap.payments.hasOverdue() ? Color.rgb(160, 48, 40) : GREEN, true);
               this.add(payBox, String.format(Locale.forLanguageTag("pl-PL"), "Do zapłaty: %.2f PLN", unpaid), 19, INK, true);
               if (!snap.payments.accounts.isEmpty()) {
                  this.add(payBox, "Subkonto: " + snap.payments.accounts.get(0).bankAccountNumber, 13, MUTED, false);
               }
               payBox.addView(this.button("Zobacz harmonogram opłat", () -> this.navigate(8), false));
            }
         }
          List<ScheduleConflictDetector.Conflict> conflicts = ScheduleConflictDetector.findConflicts(snap.records.values());
          if (!conflicts.isEmpty()) {
             LinearLayout conflictBox = this.card(this.content, Color.rgb(255, 243, 224));
             this.add(conflictBox, "KOLIZJE W PLANIE ZAJĘĆ (" + conflicts.size() + ")", 11, Color.rgb(217, 119, 6), true);
             this.add(conflictBox, "Wykryto nakładające się zajęcia!", 17, INK, true);
             this.add(conflictBox, conflicts.get(0).description(), 13, INK, false);
             if (conflicts.size() > 1) {
                this.note(conflictBox, "+ " + (conflicts.size() - 1) + " innych kolizji w pobranym planie.", MUTED);
             }
             conflictBox.addView(this.button("Pokaż kolizję w planie zajęć", () -> {
                calendarDate = conflicts.get(0).first.date;
                calendarMode = "day";
                this.navigate(5);
             }, false));
          }
          if (snap.news != null && !snap.news.isEmpty()) {
             NewsArticle top = snap.news.get(0);
             LinearLayout newsBox = this.card(this.content, Color.WHITE);
             this.add(newsBox, "KOMUNIKATY I AKTUALNOŚCI UCZELNI", 11, GREEN, true);
             this.add(newsBox, top.title, 17, INK, true);
             if (!top.headline.isEmpty()) this.add(newsBox, top.headline, 13, MUTED, false);
             this.add(newsBox, top.publicationDate + (!top.author.isEmpty() ? " · " + top.author : ""), 12, GREEN, false);
             newsBox.addView(this.button("Wszystkie komunikaty (" + snap.news.size() + ")", () -> this.navigate(11), false));
          }
      }
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
         List<Change> recent = new ArrayList<>();
         for(int i = 0; i < rows.length(); ++i) {
            recent.add(Change.from(rows.getJSONObject(i)));
         }
         recent.sort((a, b) -> Long.compare(b.effectiveTimestamp(), a.effectiveTimestamp()));
         for(int i = 0; i < Math.min(3, recent.size()); ++i) {
            this.changeCard(recent.get(i));
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
         pl.vistula.checker.widget.TodayScheduleWidget.updateAll(this);
         pl.vistula.checker.widget.QuickStatusWidget.updateAll(this);
      } else {
         int position = scroll.getScrollY(); boolean first = state.optJSONObject("snapshot") == null;
         this.task(() -> {
            List<Change> changes = this.repository.syncWithProgress(true, message -> runOnUiThread(() -> { if (!isDestroyed()) progress.setText(message); }));
            SyncWorker.notifyChanges(this, changes);
            pl.vistula.checker.widget.TodayScheduleWidget.updateAll(this);
            pl.vistula.checker.widget.QuickStatusWidget.updateAll(this);
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
      this.title("Historia zmian", "Daty publikacji ocen i zmian pobrane z USOS.");
      this.chips(new String[]{"wszystkie", "ocena", "plan"}, new String[]{"Wszystkie", "Oceny", "Plan"}, this.filter, (value) -> {
         this.filter = value;
         this.render();
      });
      JSONArray history = this.display().optJSONArray("history");
      List<Change> rows = new ArrayList<>();
      if (history != null) {
         for(int i = 0; i < history.length(); ++i) {
            Change c = Change.from(history.getJSONObject(i));
            if (this.filter.equals("wszystkie") || this.filter.equals(c.kind)) {
               rows.add(c);
            }
         }
         rows.sort((a, b) -> Long.compare(b.effectiveTimestamp(), a.effectiveTimestamp()));
      }

      EditText search = this.field(this.content, "Szukaj przedmiotu lub zmiany…", this.historyQuery, false);
      LinearLayout list = new LinearLayout(this);
      list.setOrientation(LinearLayout.VERTICAL);
      this.content.addView(list);
      Runnable populate = () -> {
         list.removeAllViews();
         int count = 0;

         for(Change c : rows) {
            if (this.matches(this.historyQuery, c.title, c.before, c.after, c.action, c.sourceTime, c.date,
                  c.beforeDate, c.afterDate,
                  c.optMeta("courseId"), c.optMeta("room"), c.optMeta("lecturers"), c.optMeta("comment"), c.optMeta("valueDescription"), c.optMeta("term"),
                  c.optBeforeMeta("room"), c.optAfterMeta("room"),
                  c.optBeforeMeta("comment"), c.optAfterMeta("comment"),
                  c.optBeforeMeta("valueDescription"), c.optAfterMeta("valueDescription"),
                  c.optBeforeMeta("lecturers"), c.optAfterMeta("lecturers"),
                  c.optBeforeMeta("building"), c.optAfterMeta("building"))) {
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

   private TextView badge(LinearLayout parent, String text, int bg, int fg) {
      TextView t = new TextView(this);
      t.setText(text);
      t.setTextSize(11);
      t.setTextColor(fg);
      t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
      t.setPadding(dp(10), dp(4), dp(10), dp(4));
      GradientDrawable d = new GradientDrawable();
      d.setColor(bg);
      d.setCornerRadius(dp(8));
      t.setBackground(d);
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
      p.bottomMargin = dp(8);
      p.rightMargin = dp(6);
      parent.addView(t, p);
      return t;
   }

   private void changeCard(LinearLayout parent, Change c) {
      LinearLayout box = this.card(parent, -1);

      // Nagłówek: kategoria + plakietka akcji
      LinearLayout topBar = new LinearLayout(this);
      topBar.setOrientation(LinearLayout.HORIZONTAL);
      topBar.setGravity(Gravity.CENTER_VERTICAL);

      int actionBg, actionFg;
      String actionText;
      if (c.action.equals("Dodano")) {
         actionBg = Color.rgb(232, 245, 233);
         actionFg = Color.rgb(27, 94, 32);
         actionText = "+ DODANO";
      } else if (c.action.equals("Usunięto")) {
         actionBg = Color.rgb(255, 235, 238);
         actionFg = Color.rgb(183, 28, 28);
         actionText = "− USUNIĘTO";
      } else {
         actionBg = Color.rgb(238, 242, 255);
         actionFg = Color.rgb(49, 46, 129);
         actionText = "Δ ZMIENIONO";
      }
      badge(topBar, (c.kind.equals("ocena") ? "OCENA" : "PLAN ZAJĘĆ"), Color.rgb(241, 245, 249), GREEN);
      badge(topBar, actionText, actionBg, actionFg);
      box.addView(topBar);

      this.add(box, c.title, 18, INK, true);

      String courseId = c.optMeta("courseId");
      String term = c.optMeta("term");
      if (!courseId.isEmpty() || !term.isEmpty()) {
         String sub = (!courseId.isEmpty() ? "Kod: " + courseId : "") + (!courseId.isEmpty() && !term.isEmpty() ? "  ·  " : "") + (!term.isEmpty() ? "Semestr: " + term : "");
         this.add(box, sub, 12, MUTED, false);
      }

      // Wyróżnienia konkretnych różnic wykrytych z USOS API (ocena, zaliczenie, sala, godziny, prowadzący itp.)
      LinearLayout tags = new LinearLayout(this);
      tags.setOrientation(LinearLayout.HORIZONTAL);
      boolean hasTags = false;

      String diffGrade = c.optMeta("diffGrade");
      if (!diffGrade.isEmpty()) {
         badge(tags, "Ocena: " + diffGrade, Color.rgb(227, 242, 253), Color.rgb(13, 71, 161));
         hasTags = true;
      }
      String diffPasses = c.optMeta("diffPasses");
      if (!diffPasses.isEmpty()) {
         badge(tags, "Zaliczenie: " + diffPasses, Color.rgb(255, 243, 224), Color.rgb(230, 81, 0));
         hasTags = true;
      } else if (c.getPasses() != null) {
         boolean passes = c.getPasses();
         badge(tags, passes ? "Zaliczono" : "Brak zaliczenia", passes ? Color.rgb(232, 245, 233) : Color.rgb(255, 235, 238), passes ? Color.rgb(27, 94, 32) : Color.rgb(183, 28, 28));
         hasTags = true;
      }
      String diffRoom = c.optMeta("diffRoom");
      if (!diffRoom.isEmpty()) {
         badge(tags, "Sala: " + diffRoom, Color.rgb(255, 243, 224), Color.rgb(230, 81, 0));
         hasTags = true;
      }
      String diffTime = c.optMeta("diffTime");
      if (!diffTime.isEmpty()) {
         badge(tags, "Godziny: " + diffTime, Color.rgb(227, 242, 253), Color.rgb(13, 71, 161));
         hasTags = true;
      }
      String diffDate = c.optMeta("diffDate");
      if (!diffDate.isEmpty()) {
         badge(tags, "Termin zajęć: " + diffDate, Color.rgb(232, 245, 233), Color.rgb(27, 94, 32));
         hasTags = true;
      }
      String diffGroup = c.optMeta("diffGroup");
      if (!diffGroup.isEmpty()) {
         badge(tags, "Grupa: " + diffGroup, Color.rgb(243, 229, 245), Color.rgb(74, 20, 140));
         hasTags = true;
      }
      String diffLecturers = c.optMeta("diffLecturers");
      if (!diffLecturers.isEmpty()) {
         badge(tags, "Prowadzący: " + diffLecturers, Color.rgb(232, 245, 233), Color.rgb(27, 94, 32));
         hasTags = true;
      }
      String diffBuilding = c.optMeta("diffBuilding");
      if (!diffBuilding.isEmpty()) {
         badge(tags, "Budynek: " + diffBuilding, Color.rgb(241, 245, 249), INK);
         hasTags = true;
      }
      String diffClassType = c.optMeta("diffClassType");
      if (!diffClassType.isEmpty()) {
         badge(tags, "Forma: " + diffClassType, Color.rgb(241, 245, 249), INK);
         hasTags = true;
      }
      String diffComment = c.optMeta("diffComment");
      if (!diffComment.isEmpty()) {
         badge(tags, "Komentarz: " + diffComment, Color.rgb(255, 248, 225), Color.rgb(191, 54, 12));
         hasTags = true;
      }
      String diffEcts = c.optMeta("diffEcts");
      if (!diffEcts.isEmpty()) {
         badge(tags, "ECTS: " + diffEcts, Color.rgb(241, 245, 249), INK);
         hasTags = true;
      } else if (!Double.isNaN(c.getEcts())) {
         badge(tags, String.format(Locale.forLanguageTag("pl-PL"), "%.0f ECTS", c.getEcts()), Color.rgb(241, 245, 249), INK);
         hasTags = true;
      }
      if (hasTags) {
         HorizontalScrollView scrollTags = new HorizontalScrollView(this);
         scrollTags.setHorizontalScrollBarEnabled(false);
         scrollTags.addView(tags);
         LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
         lp.bottomMargin = dp(8);
         box.addView(scrollTags, lp);
      }

      // Panel 1: PRZED ZMIANĄ (Wcześniej) wraz z pełnymi danymi i datą z USOS
      this.stateBlock(box, "PRZED ZMIANĄ (Wcześniej)", Color.rgb(75, 85, 99),
         Color.rgb(243, 244, 246), Color.rgb(55, 65, 81), Color.rgb(249, 250, 251),
         c.beforeDate, c.before, c.beforeMeta,
         c.action.equals("Dodano") ? "(Brak wcześniejszego wpisu — pozycja dodana do USOS)" : "(Brak danych)");

      // Panel 2: PO ZMIANIE (Teraz) wraz z pełnymi danymi i datą z USOS
      if (c.action.equals("Usunięto")) {
         this.stateBlock(box, "PO ZMIANIE (Teraz)", Color.rgb(185, 28, 28),
            Color.rgb(254, 226, 226), Color.rgb(153, 27, 27), Color.rgb(254, 242, 242),
            c.afterDate, c.after, c.afterMeta,
            "(Wpis został wycofany / usunięty z USOS)");
      } else {
         this.stateBlock(box, "PO ZMIANIE (Teraz)", GREEN,
            Color.rgb(220, 252, 231), Color.rgb(22, 101, 52), Color.rgb(240, 253, 244),
            c.afterDate, c.after, c.afterMeta,
            "(Brak danych)");
      }

      this.add(box, "Wykryto przez aplikację: " + this.timestamp(c.detectedAt), 12, MUTED, false);

      Button detailsBtn = button("Pełne szczegóły z USOS API (Audyt)", () -> this.showChangeDetails(c), false);
      detailsBtn.setMinHeight(dp(42));
      detailsBtn.setTextSize(13);
      box.addView(detailsBtn);
   }

   private LinearLayout stateBlock(LinearLayout parent, String title, int titleColor,
                                   int pillBg, int pillFg, int boxBg,
                                   String dateText, String valueText, JSONObject meta,
                                   String emptyPlaceholder) {
      LinearLayout b = new LinearLayout(this);
      b.setOrientation(LinearLayout.VERTICAL);
      b.setPadding(dp(14), dp(12), dp(14), dp(12));
      GradientDrawable shape = new GradientDrawable();
      shape.setColor(boxBg);
      shape.setCornerRadius(dp(14));
      shape.setStroke(dp(1), Color.rgb(222, 229, 225));
      b.setBackground(shape);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
      lp.bottomMargin = dp(10);
      parent.addView(b, lp);

      LinearLayout head = new LinearLayout(this);
      head.setOrientation(LinearLayout.VERTICAL);

      TextView tPill = new TextView(this);
      tPill.setText(title);
      tPill.setTextSize(10);
      tPill.setTextColor(pillFg);
      tPill.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
      tPill.setPadding(dp(8), dp(3), dp(8), dp(3));
      GradientDrawable pillDrawable = new GradientDrawable();
      pillDrawable.setColor(pillBg);
      pillDrawable.setCornerRadius(dp(6));
      tPill.setBackground(pillDrawable);
      LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(-2, -2);
      pillLp.bottomMargin = dp(6);
      head.addView(tPill, pillLp);

      TextView tDate = new TextView(this);
      if (dateText != null && !dateText.trim().isEmpty()) {
         tDate.setText("Data w USOS: " + formatUsosDate(dateText));
         tDate.setTextColor(titleColor);
      } else {
         tDate.setText(emptyPlaceholder != null ? emptyPlaceholder : "Brak daty w USOS");
         tDate.setTextColor(MUTED);
      }
      tDate.setTextSize(11);
      tDate.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
      LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(-1, -2);
      dateLp.bottomMargin = dp(8);
      head.addView(tDate, dateLp);
      b.addView(head);

      if (valueText != null && !valueText.trim().isEmpty()) {
         TextView tVal = text(valueText, 14, INK, true);
         LinearLayout.LayoutParams valLp = new LinearLayout.LayoutParams(-1, -2);
         valLp.bottomMargin = dp(6);
         b.addView(tVal, valLp);
      } else if (emptyPlaceholder != null) {
         TextView tEmpty = text(emptyPlaceholder, 13, MUTED, false);
         LinearLayout.LayoutParams emptyLp = new LinearLayout.LayoutParams(-1, -2);
         emptyLp.bottomMargin = dp(6);
         b.addView(tEmpty, emptyLp);
      }

      if (meta != null && meta.length() > 0) {
         String valDesc = meta.optString("valueDescription", "");
         if (!valDesc.isEmpty()) addMiniMeta(b, "Opis w USOS:", valDesc);
         if (meta.has("passes")) {
            boolean passes = meta.optBoolean("passes");
            addMiniMeta(b, "Status zaliczenia:", passes ? "Zaliczono (TAK)" : "Brak zaliczenia (NIE)");
         }
         if (meta.has("ects")) {
            double ects = meta.optDouble("ects", Double.NaN);
            if (!Double.isNaN(ects)) addMiniMeta(b, "Punkty ECTS:", String.format(Locale.forLanguageTag("pl-PL"), "%.1f", ects));
         }
         if (meta.has("countsIntoAverage")) {
            boolean avg = meta.optBoolean("countsIntoAverage");
            addMiniMeta(b, "Średnia ocen:", avg ? "Wliczana" : "Niewliczana");
         }
         String sessionNum = meta.optString("sessionNumber", "");
         if (!sessionNum.isEmpty()) {
            addMiniMeta(b, "Termin egzaminu:", "Termin " + sessionNum + (sessionNum.equals("1") ? " (podstawowy)" : " (poprawkowy)"));
         }
         String examId = meta.optString("examId", "");
         if (!examId.isEmpty()) addMiniMeta(b, "Protokół USOS:", "#" + examId);
         String gradeTypeId = meta.optString("gradeTypeId", "");
         if (!gradeTypeId.isEmpty()) addMiniMeta(b, "Skala ocen:", gradeTypeId);
         String dateAcq = meta.optString("dateAcquisition", "");
         if (!dateAcq.isEmpty()) addMiniMeta(b, "Data uzyskania:", formatUsosDate(dateAcq));
         String modAuthor = meta.optString("modificationAuthor", "");
         if (!modAuthor.isEmpty()) addMiniMeta(b, "Autor zmiany:", "ID " + modAuthor);
         String comment = meta.optString("comment", "");
         if (!comment.isEmpty() && (valueText == null || !valueText.contains(comment))) {
            addMiniMeta(b, "Komentarz:", comment);
         }

         String classType = meta.optString("classType", "");
         String room = meta.optString("room", "");
         String bld = meta.optString("building", "");
         String grp = meta.optString("groupNumber", "");
         String lec = meta.optString("lecturers", "");
         String freq = meta.optString("frequency", "");
         String startTime = meta.optString("startTime", "");
         String endTime = meta.optString("endTime", "");
         String classDate = meta.optString("date", "");

         if (!classType.isEmpty()) addMiniMeta(b, "Forma zajęć:", classType);
         if (!room.isEmpty() || !bld.isEmpty()) {
            addMiniMeta(b, "Miejsce:", (!room.isEmpty() ? "Sala " + room : "") + (!room.isEmpty() && !bld.isEmpty() ? " · " : "") + bld);
         }
         if (!grp.isEmpty()) addMiniMeta(b, "Grupa:", "Grupa " + grp);
         if (!lec.isEmpty()) addMiniMeta(b, "Prowadzący:", lec);
         if (!freq.isEmpty()) addMiniMeta(b, "Częstotliwość:", freq);
         if (!classDate.isEmpty()) addMiniMeta(b, "Dzień zajęć:", formatUsosDate(classDate));
         if (!startTime.isEmpty() && !endTime.isEmpty()) {
            String sH = startTime.length() >= 16 ? startTime.substring(11, 16) : startTime;
            String eH = endTime.length() >= 16 ? endTime.substring(11, 16) : endTime;
            addMiniMeta(b, "Godziny:", sH + " – " + eH);
         }
      }

      return b;
   }

   private void addMiniMeta(LinearLayout parent, String label, String value) {
      TextView t = new TextView(this);
      t.setText(Html.fromHtml("<b>" + label + "</b> " + value, Html.FROM_HTML_MODE_LEGACY));
      t.setTextSize(12);
      t.setTextColor(INK);
      t.setLineSpacing(dp(2), 1f);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
      lp.bottomMargin = dp(3);
      parent.addView(t, lp);
   }

   private void showChangeDetails(Change c) {
      StringBuilder sb = new StringBuilder();
      sb.append("═══════════════════════════════════\n");
      sb.append("  INFORMACJE O ZMIANIE\n");
      sb.append("═══════════════════════════════════\n");
      sb.append("Przedmiot: ").append(c.title).append("\n");
      sb.append("Kategoria: ").append(c.kind.equals("ocena") ? "Ocena" : "Plan zajęć").append("\n");
      sb.append("Typ operacji: ").append(c.action).append("\n");
      String courseId = c.optMeta("courseId");
      if (!courseId.isEmpty()) sb.append("Kod przedmiotu: ").append(courseId).append("\n");
      String term = c.optMeta("term");
      if (!term.isEmpty()) sb.append("Semestr: ").append(term).append("\n");
      sb.append("Wykryto w aplikacji: ").append(timestamp(c.detectedAt)).append("\n\n");

      sb.append("───────────────────────────────────\n");
      sb.append("  WYKRYTE RÓŻNICE (DIFF)\n");
      sb.append("───────────────────────────────────\n");
      boolean hasDiffs = false;
      for (String key : new String[]{"diffGrade", "diffPasses", "diffRoom", "diffTime", "diffDate", "diffLecturers", "diffGroup", "diffBuilding", "diffClassType", "diffComment", "diffEcts"}) {
         String val = c.optMeta(key);
         if (!val.isEmpty()) {
            sb.append("• ").append(diffLabel(key)).append(": ").append(val).append("\n");
            hasDiffs = true;
         }
      }
      if (!hasDiffs) sb.append("• Brak pojedynczych różnic atrybutów\n");
      sb.append("\n");

      sb.append("───────────────────────────────────\n");
      sb.append("  STAN PRZED ZMIANĄ (WCZEŚNIEJ)\n");
      sb.append("───────────────────────────────────\n");
      if (c.before.isEmpty() && c.beforeDate.isEmpty() && (c.beforeMeta == null || c.beforeMeta.length() == 0)) {
         sb.append("(Brak wcześniejszego wpisu — pozycja została dodana do USOS)\n\n");
      } else {
         sb.append("Data w USOS: ").append(c.beforeDate.isEmpty() ? "(brak daty)" : formatUsosDate(c.beforeDate)).append("\n");
         if (!c.before.isEmpty()) sb.append("Wartość: ").append(c.before.replace("\n", "\n         ")).append("\n");
         appendMetaAudit(sb, c.beforeMeta, c.kind);
         sb.append("\n");
      }

      sb.append("───────────────────────────────────\n");
      sb.append("  STAN PO ZMIANIE (TERAZ)\n");
      sb.append("───────────────────────────────────\n");
      if (c.after.isEmpty() && c.afterDate.isEmpty() && (c.afterMeta == null || c.afterMeta.length() == 0)) {
         sb.append("(Wpis wycofany / usunięty z USOS)\n\n");
      } else {
         sb.append("Data w USOS: ").append(c.afterDate.isEmpty() ? "(brak daty)" : formatUsosDate(c.afterDate)).append("\n");
         if (!c.after.isEmpty()) sb.append("Wartość: ").append(c.after.replace("\n", "\n         ")).append("\n");
         appendMetaAudit(sb, c.afterMeta, c.kind);
         sb.append("\n");
      }

      new MaterialAlertDialogBuilder(this)
         .setTitle("Szczegóły z USOS API")
         .setMessage(sb.toString())
         .setPositiveButton("Zamknij", null)
         .show();
   }

   private void appendMetaAudit(StringBuilder sb, JSONObject m, String kind) {
      if (m == null || m.length() == 0) return;
      if (kind.equals("ocena")) {
         if (!m.optString("valueSymbol", "").isEmpty()) sb.append("• Symbol oceny (value_symbol): ").append(m.optString("valueSymbol")).append("\n");
         if (!m.optString("valueDescription", "").isEmpty()) sb.append("• Słowny opis (value_description): ").append(m.optString("valueDescription")).append("\n");
         if (m.has("passes")) sb.append("• Status zaliczenia (passes): ").append(m.optBoolean("passes") ? "TAK (zaliczono)" : "NIE (brak zaliczenia)").append("\n");
         if (m.has("ects")) {
            double e = m.optDouble("ects", Double.NaN);
            if (!Double.isNaN(e)) sb.append("• Punkty ECTS: ").append(String.format(Locale.forLanguageTag("pl-PL"), "%.1f", e)).append("\n");
         }
         if (m.has("countsIntoAverage")) sb.append("• Wliczanie do średniej (counts_into_average): ").append(m.optBoolean("countsIntoAverage") ? "TAK" : "NIE").append("\n");
         if (!m.optString("sessionNumber", "").isEmpty()) sb.append("• Numer terminu (exam_session_number): ").append(m.optString("sessionNumber")).append("\n");
         if (!m.optString("examId", "").isEmpty()) sb.append("• ID protokołu USOS (exam_id): ").append(m.optString("examId")).append("\n");
         if (!m.optString("gradeTypeId", "").isEmpty()) sb.append("• Skala ocen (grade_type_id): ").append(m.optString("gradeTypeId")).append("\n");
         if (!m.optString("dateAcquisition", "").isEmpty()) sb.append("• Data uzyskania (date_acquisition): ").append(m.optString("dateAcquisition")).append("\n");
         if (!m.optString("dateModified", "").isEmpty()) sb.append("• Data modyfikacji (date_modified): ").append(m.optString("dateModified")).append("\n");
         if (!m.optString("modificationAuthor", "").isEmpty()) sb.append("• Autor modyfikacji (modification_author): ID ").append(m.optString("modificationAuthor")).append("\n");
         if (!m.optString("comment", "").isEmpty()) sb.append("• Komentarz (comment): ").append(m.optString("comment")).append("\n");
      } else {
         if (!m.optString("classType", "").isEmpty()) sb.append("• Forma zajęć (classtype_name): ").append(m.optString("classType")).append("\n");
         if (!m.optString("classTypeId", "").isEmpty()) sb.append("• ID formy zajęć (classtype_id): ").append(m.optString("classTypeId")).append("\n");
         if (!m.optString("room", "").isEmpty()) sb.append("• Numer sali (room_number): ").append(m.optString("room")).append("\n");
         if (!m.optString("roomId", "").isEmpty()) sb.append("• ID sali (room_id): ").append(m.optString("roomId")).append("\n");
         if (!m.optString("building", "").isEmpty()) sb.append("• Budynek (building_name): ").append(m.optString("building")).append("\n");
         if (!m.optString("buildingId", "").isEmpty()) sb.append("• ID budynku (building_id): ").append(m.optString("buildingId")).append("\n");
         if (!m.optString("groupNumber", "").isEmpty()) sb.append("• Numer grupy (group_number): ").append(m.optString("groupNumber")).append("\n");
         if (!m.optString("unitId", "").isEmpty()) sb.append("• ID jednostki (unit_id): ").append(m.optString("unitId")).append("\n");
         if (!m.optString("lecturers", "").isEmpty()) sb.append("• Prowadzący (lecturer_ids): ").append(m.optString("lecturers")).append("\n");
         if (!m.optString("frequency", "").isEmpty()) sb.append("• Częstotliwość (frequency): ").append(m.optString("frequency")).append("\n");
         if (!m.optString("type", "").isEmpty()) sb.append("• Typ wpisu w USOS (type): ").append(m.optString("type")).append("\n");
         if (!m.optString("smId", "").isEmpty()) sb.append("• ID zajęć sm_id: ").append(m.optString("smId")).append("\n");
         if (!m.optString("cgwmId", "").isEmpty()) sb.append("• ID cgwm_id: ").append(m.optString("cgwmId")).append("\n");
         if (!m.optString("startTime", "").isEmpty()) sb.append("• Godzina rozpoczęcia (start_time): ").append(m.optString("startTime")).append("\n");
         if (!m.optString("endTime", "").isEmpty()) sb.append("• Godzina zakończenia (end_time): ").append(m.optString("endTime")).append("\n");
         if (!m.optString("date", "").isEmpty()) sb.append("• Data zajęć (date): ").append(m.optString("date")).append("\n");
         if (!m.optString("url", "").isEmpty()) sb.append("• URL: ").append(m.optString("url")).append("\n");
         if (!m.optString("profileUrl", "").isEmpty()) sb.append("• Profil grupy URL: ").append(m.optString("profileUrl")).append("\n");
      }
   }

   private String diffLabel(String key) {
      switch (key) {
         case "diffGrade": return "Ocena";
         case "diffPasses": return "Status zaliczenia";
         case "diffRoom": return "Sala";
         case "diffTime": return "Godziny zajęć";
         case "diffDate": return "Termin zajęć";
         case "diffLecturers": return "Prowadzący";
         case "diffGroup": return "Grupa";
         case "diffBuilding": return "Budynek";
         case "diffClassType": return "Forma zajęć";
         case "diffComment": return "Komentarz";
         case "diffEcts": return "Punkty ECTS";
         default: return key;
      }
   }

   private String formatUsosDate(String source) {
      if (source == null || source.trim().isEmpty()) return "";
      String trimmed = source.trim();
      try {
         LocalDateTime dt = LocalDateTime.parse(trimmed, UsosParser.TIME);
         return dt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm:ss", Locale.forLanguageTag("pl-PL")));
      } catch (Exception ignored) {}
      try {
         LocalDate d = LocalDate.parse(trimmed);
         return d.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.forLanguageTag("pl-PL")));
      } catch (Exception ignored) {}
      return trimmed;
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
        if (!snapshot.terms.isEmpty()) {
            AcademicTerm active = null;
            for (AcademicTerm t : snapshot.terms.values()) {
                if (t.isCurrent(today)) { active = t; break; }
            }
            if (active != null) {
                LinearLayout termBanner = card(content, Color.rgb(238, 245, 242));
                add(termBanner, active.name, 16, INK, true);
                long left = active.daysRemaining(today);
                add(termBanner, "Pozostało " + left + " dni semestru · koniec: " + active.endDate, 13, GREEN, false);
            }
        }
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
                        lessonCard(list, lesson, hasConflict(lesson, all));
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
                    lessonCard(list, lesson, hasConflict(lesson, all));
                }
                if (!date.isEmpty()) daySummary(list, visible, LocalDate.parse(date));
                if (visible.isEmpty()) {
                    LinearLayout box = card(list, Color.WHITE); add(box, "Brak pasujących danych", 17, INK, true);
                    note(box, "Zmień wyszukiwanie lub wybierz inny tydzień.", MUTED);
                    if (!dataQuery.trim().isEmpty()) box.addView(button("Wyczyść wyszukiwanie planu", () -> { dataQuery = ""; render(); }, false));
                }
            }
            Button export = button("Zapisz plik kalendarza (.ics)", () -> exportPlan(exported), false); export.setEnabled(!exported.isEmpty()); list.addView(export);
            Button share = button("Udostępnij do Kalendarza / Aplikacji", () -> sharePlan(exported), false); share.setEnabled(!exported.isEmpty()); list.addView(share);
            note(list, "Eksport obejmuje widoczne zajęcia. Plik możesz zaimportować do Google Calendar, Outlooka lub aplikacji systemowej.", MUTED);
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
    private boolean hasConflict(LessonTime lesson, List<LessonTime> all) {
        if (lesson == null || all == null) return false;
        for (LessonTime other : all) {
            if (other != lesson && lesson.start.toLocalDate().equals(other.start.toLocalDate())
                && lesson.start.isBefore(other.end) && other.start.isBefore(lesson.end)) {
                return true;
            }
        }
        return false;
    }
    private void lessonCard(LinearLayout parent, LessonTime lesson) {
        lessonCard(parent, lesson, false);
    }
    private void lessonCard(LinearLayout parent, LessonTime lesson, boolean conflict) {
        LinearLayout box = card(parent, Color.WHITE); add(box, lesson.record.title, 18, INK, true);
        DateTimeFormatter clock = DateTimeFormatter.ofPattern("HH:mm");
        add(box, lesson.start.format(clock) + " – " + lesson.end.format(clock) + (lesson.end.toLocalDate().isAfter(lesson.start.toLocalDate()) ? " · następny dzień" : ""), 20, GREEN, true);
        if (conflict) {
            note(box, "⚠️ KOLIZJA · Nakłada się z innymi zajęciami w tym samym czasie!", Color.rgb(217, 119, 6));
        }
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
      content.addView(button("Kalkulator średniej i symulator stypendium", () -> navigate(9), true));
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
         if (snapshot.tests != null && !snapshot.tests.isEmpty()) {
            heading("Sprawdziany i kolokwia");
            for (CourseTestRecord ctr : snapshot.tests) {
               LinearLayout testBox = card(list, Color.WHITE);
               add(testBox, ctr.testName, 17, INK, true);
               add(testBox, ctr.courseName, 13, GREEN, true);
               if (ctr.maxPoints > 0) {
                  add(testBox, String.format(Locale.forLanguageTag("pl-PL"), "Punkty: %.1f / %.1f (%.1f%%)", ctr.points, ctr.maxPoints, ctr.percentage()), 15, INK, false);
               } else if (!Double.isNaN(ctr.points) && ctr.points > 0) {
                  add(testBox, String.format(Locale.forLanguageTag("pl-PL"), "Punkty: %.1f", ctr.points), 15, INK, false);
               }
               if (!ctr.grade.isEmpty()) {
                  add(testBox, "Ocena: " + ctr.grade, 14, GREEN, true);
               }
               if (!ctr.date.isEmpty()) {
                  add(testBox, "Termin sprawdzianu: " + ctr.date, 12, MUTED, false);
               }
            }
         }
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
         if (!Double.isNaN(summary.ectsAverage)) {
            statistics += String.format(Locale.forLanguageTag("pl-PL"), "Średnia ważona ECTS: %.2f\nSuma punktów ECTS: %.1f\n\n", summary.ectsAverage, summary.totalEcts);
         }
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
      if (!Double.isNaN(summary.ectsAverage)) {
         counts.addView(text(String.format(Locale.forLanguageTag("pl-PL"), "Średnia ważona ECTS: %.2f (%.0f ECTS)", summary.ectsAverage, summary.totalEcts), 13, GREEN, true));
      }
      counts.addView(text("Liczbowe: " + summary.numeric + "\nPozostałe: " + summary.other, 13, MUTED, false));
      values.addView(counts, new LinearLayout.LayoutParams(0, -2, 1)); box.addView(values);
      box.addView(button("Kalkulator średniej i stypendium", () -> gradeSimulatorDialog(summary), false));
      note(box, "Średnia nieważona oraz ważona ECTS · kalkulator stypendialny", MUTED);
   }

   private void gradeSimulatorDialog(GradeSummary summary) {
      LinearLayout dialogLayout = new LinearLayout(this);
      dialogLayout.setOrientation(LinearLayout.VERTICAL);
      dialogLayout.setPadding(dp(24), dp(16), dp(24), dp(8));

      double currentAvg = Double.isNaN(summary.ectsAverage) ? summary.average : summary.ectsAverage;
      String currentAvgStr = Double.isNaN(currentAvg) ? "brak" : String.format(Locale.forLanguageTag("pl-PL"), "%.2f", currentAvg);

      TextView currentLabel = text("Aktualna średnia: " + currentAvgStr, 14, INK, true);
      dialogLayout.addView(currentLabel);

      EditText targetField = field(dialogLayout, "Docelowa średnia (np. 4.50)", "4.50", false);
      EditText countField = field(dialogLayout, "Liczba nowych ocen (do średniej prostej)", "2", false);
      countField.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

      EditText ectsField = field(dialogLayout, "Punkty ECTS nowego kursu (do ważonej)", "6", false);
      ectsField.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);

      TextView resultView = text("Wpisz parametry i zobacz wymagane oceny.", 13, GREEN, true);
      resultView.setPadding(0, dp(8), 0, dp(16));
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
               sb.append(String.format(Locale.forLanguageTag("pl-PL"), "Średnia prosta: z kolejnych %d ocen potrzebujesz średniej %.2f", count, reqSimple));
               if (reqSimple > 5.0) sb.append(" (niemożliwe w obecnym semestrze, max 5.0!)");
               else if (reqSimple <= 3.0) sb.append(" (cel osiągnięty nawet przy ocenie 3.0)");
            }
            if (!Double.isNaN(reqEcts) && !Double.isNaN(summary.ectsAverage)) {
               if (sb.length() > 0) sb.append("\n\n");
               sb.append(String.format(Locale.forLanguageTag("pl-PL"), "Średnia ważona ECTS: z przedmiotu za %.0f ECTS potrzebujesz oceny %.2f", ectsPoints, reqEcts));
               if (reqEcts > 5.0) sb.append(" (wymaga oceny > 5.0)");
               else if (reqEcts <= 3.0) sb.append(" (cel osiągnięty przy ocenie 3.0)");
            }
            resultView.setText(sb.toString());
         } catch (Exception e) {
            resultView.setText("Wpisz prawidłowe liczby (np. cel: 4.50, oceny: 2, ECTS: 6)");
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

      new MaterialAlertDialogBuilder(this)
         .setTitle("Symulator średniej i stypendium")
         .setView(dialogLayout)
         .setPositiveButton("Zamknij", null)
         .show();
   }
   private void exportPlan(List<Record> rows) {
      List<Record> visible = new ArrayList<>();
      for (Record row : rows) {
         if (StudentSearch.matches(this.dataQuery, row.title, row.value, row.term, row.date)) {
            visible.add(row);
         }
      }
      if (visible.isEmpty()) {
         this.error("Brak widocznych zajęć do eksportu.");
      } else {
         try {
            String calendar = IcsExporter.export(visible, "Plan zajęć Vistula");
            if (calendar.getBytes(StandardCharsets.UTF_8).length > 500000) {
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

   private void sharePlan(List<Record> rows) {
      List<Record> visible = new ArrayList<>();
      for (Record row : rows) {
         if (StudentSearch.matches(this.dataQuery, row.title, row.value, row.term, row.date)) {
            visible.add(row);
         }
      }
      if (visible.isEmpty()) {
         this.error("Brak widocznych zajęć do udostępnienia.");
         return;
      }
      try {
         String calendar = IcsExporter.export(visible, "Plan zajęć Vistula");
         Intent send = new Intent(Intent.ACTION_SEND);
         send.setType("text/calendar");
         send.putExtra(Intent.EXTRA_SUBJECT, "Plan zajęć Vistula (" + visible.size() + " zajęć)");
         send.putExtra(Intent.EXTRA_TEXT, calendar);
         startActivity(Intent.createChooser(send, "Udostępnij lub dodaj do kalendarza"));
      } catch (Exception e) {
         this.error("Brak aplikacji do udostępnienia kalendarza.");
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
      this.title("Konto i profil", "Akademia Finansów i Biznesu Vistula");
      if (this.demo) {
         this.content.addView(this.button("Zakończ tryb demonstracyjny", () -> {
            this.demo = false;
            this.render();
         }, true));
      }

      if (this.connected() || this.demo) {
         JSONObject d = null;
         try { d = this.display(); } catch (Exception ignored) {}
         JSONObject raw = d != null ? d.optJSONObject("snapshot") : null;
         Snapshot snapshot = null;
         if (raw != null) {
            try { snapshot = Snapshot.from(raw); } catch (Exception ignored) {}
         }

         LinearLayout profile = this.card(this.content, -1);
         this.add(profile, "KARTA STUDENTA", 11, GREEN, true);
         String userName = this.demo ? "Jan Kowalski" : this.state.optString("user", "Student USOS");
         this.add(profile, userName, 22, INK, true);
         String studentNr = this.demo ? "34589" : this.state.optString("studentNumber", "");
         if (!studentNr.isEmpty()) this.add(profile, "Nr albumu: " + studentNr, 15, INK, true);
         String email = this.demo ? "j.kowalski@vistula.edu.pl" : this.state.optString("email", "");
         if (!email.isEmpty()) this.add(profile, "Email: " + email, 14, MUTED, false);
         String program = this.demo ? "Informatyka, studia I stopnia" : this.state.optString("program", "");
         if (!program.isEmpty()) this.add(profile, "Kierunek: " + program, 14, INK, false);
         String stage = this.demo ? "Semestr 5 · Studia stacjonarne" : this.state.optString("stage", "");
         if (!stage.isEmpty()) this.add(profile, "Etap: " + stage, 13, MUTED, false);
         String admission = this.demo ? "2024-10-01" : this.state.optString("admissionDate", "");
         if (!admission.isEmpty()) this.add(profile, "Data przyjęcia: " + admission, 12, MUTED, false);

         if (snapshot != null && snapshot.thesis != null && snapshot.thesis.hasThesis()) {
            this.add(profile, "PRACA DYPLOMOWA", 11, GREEN, true);
            this.add(profile, snapshot.thesis.title, 15, INK, true);
            if (!snapshot.thesis.status.isEmpty()) this.add(profile, "Status: " + snapshot.thesis.status, 13, MUTED, false);
            if (!snapshot.thesis.supervisors.isEmpty()) this.add(profile, "Promotor: " + snapshot.thesis.supervisors, 13, MUTED, false);
            if (!snapshot.thesis.reviewers.isEmpty()) this.add(profile, "Recenzent: " + snapshot.thesis.reviewers, 13, MUTED, false);
         }

         LinearLayout secBadge = new LinearLayout(this);
         secBadge.setGravity(Gravity.CENTER_VERTICAL);
         TextView badgeText = this.text("BEZPIECZNY SEJF SPRZĘTOWY", 10, GREEN, true);
         badgeText.setPadding(this.dp(8), this.dp(4), this.dp(8), this.dp(4));
         GradientDrawable badgeBg = new GradientDrawable();
         badgeBg.setColor(Color.rgb(232, 243, 236));
         badgeBg.setCornerRadius(this.dp(8));
         badgeText.setBackground(badgeBg);
         secBadge.addView(badgeText);
         profile.addView(secBadge);
         this.note(profile, "Szyfrowanie Android Keystore (AES-256-GCM). Wszystkie dane przechowywane wyłącznie lokalnie.", MUTED);
      }

      if (this.connected()) {
         LinearLayout box = this.card(this.content, -1);
         this.add(box, "Ustawienia synchronizacji USOS", 19, INK, true);
         String authorizedScopes = this.state.optString("scopes", "grades|studies|payments|offline_access");
         this.add(box, "Zgoda OAuth: " + authorizedScopes, 13, MUTED, false);
         if (!authorizedScopes.contains("payments")) {
            this.note(box, "Uwaga: Twoje konto nie posiada zakresu 'payments'. Subkonta i harmonogram płatności nie będą pobierane dopóki nie odłączysz konta i nie połączysz go ponownie z zakresem 'payments'.", Color.rgb(180, 110, 0));
         }
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
         String buildKey = BuildConfig.USOS_KEY != null ? BuildConfig.USOS_KEY.trim() : "";
         String buildSecret = BuildConfig.USOS_SECRET != null ? BuildConfig.USOS_SECRET.trim() : "";
         String defaultKey = this.state.optString("key", buildKey);
         if (defaultKey.isEmpty()) defaultKey = buildKey;
         String defaultSecret = this.state.optString("secret", buildSecret);
         if (defaultSecret.isEmpty()) defaultSecret = buildSecret;

         EditText key = this.field(box, "Consumer Key", defaultKey, false);
         EditText secret = this.field(box, "Consumer Secret", defaultSecret, true);
         if (!buildKey.isEmpty()) {
            this.note(box, "Wczytano klucz aplikacji (Consumer Key & Secret) z pliku local.properties.", GREEN);
         }
         EditText scopes = this.field(box, "Zakresy zgody OAuth (oddzielone |)", this.state.optString("scopes", "grades|studies|payments|offline_access"), false);
         this.add(box, "Monitor wymaga grades, studies, payments i offline_access. Inne zakresy dodaj tylko dla metod, których chcesz używać w katalogu API.", 12, MUTED, false);
         box.addView(this.button("1. Zaloguj w przeglądarce", () -> {
            String k = key.getText().toString().trim();
            if (k.isEmpty()) k = buildKey;
            String sec = secret.getText().toString().trim();
            if (sec.isEmpty()) sec = buildSecret;
            String sc = scopes.getText().toString().trim();
            if (!k.isEmpty() && !sec.isEmpty()) {
               List<String> cleanScopes = new ArrayList<>();
               for (String part : sc.split("\\|")) {
                  String trimmed = part.trim();
                  if (!trimmed.isEmpty() && !cleanScopes.contains(trimmed)) {
                     cleanScopes.add(trimmed);
                  }
               }
               if (!cleanScopes.contains("grades")) cleanScopes.add("grades");
               if (!cleanScopes.contains("studies")) cleanScopes.add("studies");
               if (!cleanScopes.contains("offline_access")) cleanScopes.add("offline_access");
               final String normalizedScopes = String.join("|", cleanScopes);

               final String finalK = k;
               final String finalSec = sec;
               this.task(() -> {
                  this.repository.begin(finalK, finalSec, normalizedScopes);
                  return this.repository.read();
               }, (s) -> {
                  this.state = s;
                  this.render();
                  this.browser("https://usosapps.vistula.edu.pl/services/oauth/authorize?oauth_token=" + OAuth.encode(s.getString("requestToken")));
               });
            } else {
               this.error("Wpisz Consumer Key i Consumer Secret (lub podaj je w pliku local.properties).");
            }
         }, true));
         if (!this.state.optString("requestToken").isEmpty()) {
            this.add(box, "Po zaakceptowaniu zgody skopiuj kod PIN z USOS i wróć do aplikacji.", 14, MUTED, false);
            EditText pin = this.field(box, "PIN autoryzacji z USOS", "", true);
            box.addView(this.button("2. Potwierdź PIN i połącz", () -> {
               String value = pin.getText().toString().trim().replaceAll("\\s+", "");
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
            box.addView(this.button("Wróć do autoryzacji w przeglądarce", () -> this.browser("https://usosapps.vistula.edu.pl/services/oauth/authorize?oauth_token=" + OAuth.encode(this.state.optString("requestToken"))), false));
         }

         this.content.addView(this.button("Uruchom tryb demonstracyjny", () -> {
            this.demo = true;
            this.tab = 0;
            this.render();
         }, false));
      }

      LinearLayout notices = this.card(this.content, -1);
      this.add(notices, "Powiadomienia i alerty", 18, INK, true);
      this.add(notices, NotificationManagerCompat.from(this).areNotificationsEnabled() ? "System zezwala na powiadomienia." : "Powiadomienia są wyłączone w systemie. Włącz je, aby otrzymywać alerty o nowych ocenach i zmianach sal.", 14, MUTED, false);
      notices.addView(this.button("Ustawienia powiadomień w systemie", this::requestNotifications, false));
      notices.addView(this.button("Wyślij testowe powiadomienie o ocenach", () -> {
         NotificationHelper.sendTestNotification(this);
         Toast.makeText(this, "Wysłano testowe powiadomienie", Toast.LENGTH_SHORT).show();
      }, false));

      SharedPreferences toolsPrefs = getSharedPreferences("student_tools", MODE_PRIVATE);
      boolean remindersOn = toolsPrefs.getBoolean(ClassReminderReceiver.PREF_REMINDERS_ENABLED, false);
      int reminderMin = toolsPrefs.getInt(ClassReminderReceiver.PREF_REMINDER_MINUTES, 15);

      LinearLayout reminderRow = new LinearLayout(this);
      reminderRow.setOrientation(LinearLayout.VERTICAL);
      reminderRow.setPadding(0, dp(8), 0, dp(4));

      MaterialSwitch reminderSwitch = new MaterialSwitch(this);
      reminderSwitch.setText("Przypomnienia przed rozpoczęciem zajęć");
      reminderSwitch.setChecked(remindersOn);
      reminderSwitch.setTextColor(INK);
      reminderSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
          toolsPrefs.edit().putBoolean(ClassReminderReceiver.PREF_REMINDERS_ENABLED, isChecked).apply();
          if (isChecked) {
              ClassReminderReceiver.scheduleNext(this);
              Toast.makeText(this, "Włączono przypomnienia przed zajęciami", Toast.LENGTH_SHORT).show();
          } else {
              ClassReminderReceiver.cancel(this);
              Toast.makeText(this, "Wyłączono przypomnienia przed zajęciami", Toast.LENGTH_SHORT).show();
          }
          render();
      });
      reminderRow.addView(reminderSwitch);

      if (remindersOn) {
          note(reminderRow, "Wyprzedzenie powiadomienia przed zajęciami:", MUTED);
          chips(new String[]{"15", "30", "45"}, new String[]{"15 minut przed", "30 minut przed", "45 minut przed"}, String.valueOf(reminderMin), val -> {
              int min = Integer.parseInt(val);
              toolsPrefs.edit().putInt(ClassReminderReceiver.PREF_REMINDER_MINUTES, min).apply();
              ClassReminderReceiver.scheduleNext(this);
              render();
          });
      }
      notices.addView(reminderRow);

      this.note(notices, "Dedykowane kanały: Oceny (wysoki priorytet z dźwiękiem), Plan zajęć oraz Przypomnienia.", MUTED);

      LinearLayout widgetsCard = this.card(this.content, -1);
      this.add(widgetsCard, "Widgety na ekranie głównym", 18, INK, true);
      this.add(widgetsCard, "Dostępne są 2 interaktywne widgety na pulpit telefonu:\n1. Plan zajęć na dziś (najbliższe zajęcia, sala, czas, odliczanie)\n2. Karta Studenta i oceny (średnia ważona ECTS, status czesnego)\n\nAby je dodać, przytrzymaj palec na pustym miejscu ekranu głównego telefonu i wybierz „Widżety → Vistula”.", 13, MUTED, false);
      widgetsCard.addView(this.button("Zaktualizuj dane na widgetach", () -> {
         pl.vistula.checker.widget.TodayScheduleWidget.updateAll(this);
         pl.vistula.checker.widget.QuickStatusWidget.updateAll(this);
         Toast.makeText(this, "Zaktualizowano widgety", Toast.LENGTH_SHORT).show();
      }, false));

      this.note(this.content, "Tokeny i historia są szyfrowane sprzętowo (Android Keystore). Aplikacja nie przechowuje hasła do uczelni i nie korzysta z pośredniczących serwerów.", MUTED);
      this.content.addView(this.button("Otwórz USOSapps Vistuli", () -> this.browser("https://usosapps.vistula.edu.pl/"), false));
   }

   private void api() {
      this.title("Katalog USOS API", this.catalog.length() + " metod · oficjalna dokumentacja Vistuli");
      this.note(this.content, "Dokumentacja jest dostępna offline. Wykonanie żądania wymaga internetu oraz uprawnień wskazanych przy metodzie. Tryb demo nie podpisuje żądań przykładowym kontem.", MUTED);
      this.chips(new String[]{"all", "favorites"}, new String[]{"Wszystkie metody", "Ulubione"}, apiFavoritesOnly ? "favorites" : "all", value -> {
         apiFavoritesOnly = value.equals("favorites"); apiLimit = 60; render();
      });
      this.heading("Szybki dostęp do kluczowych usług");
      String[] quickMethods = {
         "services/users/user", "services/grades/terms2", "services/tt/student",
         "services/payments/user_accounts", "services/payments/user_payments", "services/theses/user", "services/crstests/user", "services/terms/terms"
      };
      String[] quickLabels = {
         "Profil studenta", "Oceny semestralne", "Plan zajęć",
         "Subkonta bankowe", "Należności i raty", "Prace dyplomowe", "Kolokwia i testy", "Semestry i kalendarz"
      };
      LinearLayout quickScrollLayout = new LinearLayout(this);
      HorizontalScrollView quickScroll = new HorizontalScrollView(this);
      quickScroll.setHorizontalScrollBarEnabled(false);
      quickScroll.addView(quickScrollLayout);
      for (int i = 0; i < quickMethods.length; i++) {
         final String qm = quickMethods[i];
         Button qb = this.button(quickLabels[i], () -> this.showMethod(qm), false);
         qb.setTextSize(12); qb.setSingleLine(true);
         LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(-2, -2);
         qp.rightMargin = this.dp(6);
         quickScrollLayout.addView(qb, qp);
      }
      LinearLayout.LayoutParams qsp = new LinearLayout.LayoutParams(-1, -2);
      qsp.bottomMargin = this.dp(12);
      this.content.addView(quickScroll, qsp);
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
                  JSONObject credentials = new JSONObject(this.repository.read().toString());
                  if (this.demo) {
                     credentials = new JSONObject();
                  }
                  if (credentials.optString("key").isEmpty() && BuildConfig.USOS_KEY != null && !BuildConfig.USOS_KEY.isEmpty()) {
                     credentials.put("key", BuildConfig.USOS_KEY.trim());
                     credentials.put("secret", BuildConfig.USOS_SECRET != null ? BuildConfig.USOS_SECRET.trim() : "");
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
        if (address == null || address.trim().isEmpty()) return;
        Uri uri = Uri.parse(address.trim());
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            error("Nieprawidłowy adres URL: " + address);
            return;
        }
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (ActivityNotFoundException e) { error("Zainstaluj przeglądarkę, aby otworzyć link."); }
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
    private void paymentsView() throws Exception {
        title("Płatności i subkonto", "Subkonta bankowe i harmonogram należności USOS");
        JSONObject d = display();
        note(content, "Aktualność danych: " + timestamp(d.optLong("successAt")), MUTED);
        JSONObject raw = d.optJSONObject("snapshot");
        if (raw == null) {
            empty("Brak pobranych danych", "Połącz konto i odśwież dane.");
            content.addView(button(connected() ? "Odśwież dane" : "Połącz konto", () -> { if (connected()) sync(); else navigate(4); }, true));
            return;
        }
        Snapshot snapshot = Snapshot.from(raw);
        PaymentInfo payments = snapshot.payments;

        String authorizedScopes = this.state.optString("scopes", "");
        boolean hasPaymentsScope = Arrays.asList(authorizedScopes.split("\\|")).contains("payments");
        boolean scopeMissing = (payments != null && payments.scopeMissing)
            || (connected() && !authorizedScopes.isEmpty() && !hasPaymentsScope);

        if (scopeMissing) {
            LinearLayout warnBox = card(content, Color.rgb(255, 248, 230));
            add(warnBox, "BRAK ZAKRESU PŁATNOŚCI W OAUTH", 11, Color.rgb(180, 110, 0), true);
            add(warnBox, "Wymagane uprawnienie 'payments'", 17, INK, true);
            note(warnBox, "Twoje połączone konto USOS ma aktualnie uprawnienia: [" + (authorizedScopes.isEmpty() ? "brak danych" : authorizedScopes) + "]. Moduł finansów uczelni (indywidualne subkonta bankowe, harmonogram rat i czesne) wymaga zgody na zakres 'payments'.", INK);
            note(warnBox, "Aby pobierać dane o płatnościach, przejdź do ekranu Konto, odłącz konto i połącz je ponownie z zakresem: grades|studies|payments|offline_access.", MUTED);
            warnBox.addView(button("Przejdź do ustawień konta", () -> navigate(4), true));
        }

        if (payments == null || (payments.accounts.isEmpty() && payments.installments.isEmpty())) {
            if (!scopeMissing) {
                empty("Brak danych finansowych", "Brak zarejestrowanych należności ani indywidualnych subkont w systemie USOS dla Twojego profilu studenta. Wszelkie opłaty są rozliczone na bieżąco.");
                content.addView(button("Odśwież dane z USOS", this::sync, false));
            }
            return;
        }

        if (!payments.accounts.isEmpty()) {
            heading("Subkonta bankowe studenta");
            for (PaymentInfo.Account acc : payments.accounts) {
                LinearLayout box = card(content, Color.WHITE);
                add(box, acc.name.isEmpty() ? "Subkonto indywidualne" : acc.name, 18, INK, true);
                if (!acc.faculty.isEmpty()) {
                    add(box, "Wydział: " + acc.faculty, 13, MUTED, false);
                }
                if (!acc.accountOwner.isEmpty()) {
                    add(box, "Właściciel: " + acc.accountOwner, 13, MUTED, false);
                }
                add(box, acc.bankAccountNumber, 16, GREEN, true);
                if (!acc.swift.isEmpty()) {
                    add(box, "SWIFT / BIC: " + acc.swift, 12, MUTED, false);
                }
                if (acc.balance != 0) {
                    add(box, String.format(Locale.forLanguageTag("pl-PL"), "Saldo konta: %.2f %s", acc.balance, acc.currency), 13, MUTED, false);
                }
                box.addView(button("Kopiuj numer subkonta (IBAN)", () -> {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("Subkonto USOS", acc.bankAccountNumber.replaceAll("\\s+", "")));
                        Toast.makeText(this, "Numer subkonta skopiowany do schowka", Toast.LENGTH_SHORT).show();
                    }
                }, false));
            }
        }

        if (!payments.installments.isEmpty()) {
            heading("Harmonogram należności");
            double totalUnpaid = payments.totalUnpaidAmount();
            LinearLayout summaryBox = card(content, payments.hasOverdue() ? Color.rgb(255, 240, 240) : Color.rgb(238, 245, 242));
            add(summaryBox, "STAN ROZLICZEŃ", 11, payments.hasOverdue() ? Color.rgb(160, 48, 40) : GREEN, true);
            add(summaryBox, String.format(Locale.forLanguageTag("pl-PL"), "Łącznie do zapłaty: %.2f PLN", totalUnpaid), 22, INK, true);
            note(summaryBox, payments.hasOverdue() ? "Uwaga: Część rat jest po terminie płatności!" : (totalUnpaid == 0 ? "Wszystkie należności są opłacone na bieżąco." : "Pamiętaj o terminowym regulowaniu rat."), payments.hasOverdue() ? Color.rgb(160, 48, 40) : MUTED);

            for (PaymentInfo.Installment inst : payments.installments) {
                LinearLayout instBox = card(content, Color.WHITE);
                LinearLayout topRow = new LinearLayout(this);
                topRow.setGravity(Gravity.CENTER_VERTICAL);
                topRow.addView(text(inst.name, 17, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
                boolean paid = inst.isPaid();
                boolean overdue = inst.isOverdue();
                String statusLabel = paid ? "Opłacona" : (overdue ? "Po terminie" : (inst.status.isEmpty() ? "Do zapłaty" : inst.status));
                int statusColor = paid ? GREEN : (overdue ? Color.rgb(160, 48, 40) : Color.rgb(180, 110, 0));
                TextView statusBadge = text(statusLabel, 12, statusColor, true);
                statusBadge.setPadding(dp(8), dp(4), dp(8), dp(4));
                GradientDrawable badgeBg = new GradientDrawable();
                badgeBg.setColor(paid ? Color.rgb(232, 243, 236) : (overdue ? Color.rgb(255, 235, 235) : Color.rgb(255, 248, 230)));
                badgeBg.setCornerRadius(dp(8));
                statusBadge.setBackground(badgeBg);
                topRow.addView(statusBadge, new LinearLayout.LayoutParams(-2, -2));
                instBox.addView(topRow);

                add(instBox, String.format(Locale.forLanguageTag("pl-PL"), "Kwota: %.2f %s", inst.amount, inst.currency), 16, INK, false);
                if (!inst.deadline.isEmpty()) {
                    add(instBox, "Termin płatności: " + inst.deadline, 13, overdue ? Color.rgb(160, 48, 40) : MUTED, overdue);
                }
                if (!inst.paymentDate.isEmpty()) {
                    add(instBox, "Data wpłaty: " + inst.paymentDate, 12, GREEN, false);
                }
                if (!inst.bankAccountNumber.isEmpty()) {
                    add(instBox, "Konto do wpłaty: " + inst.bankAccountNumber, 13, MUTED, false);
                    instBox.addView(button("Kopiuj konto dla tej raty", () -> {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("Konto wpłaty USOS", inst.bankAccountNumber.replaceAll("\\s+", "")));
                            Toast.makeText(this, "Numer konta skopiowany do schowka", Toast.LENGTH_SHORT).show();
                        }
                    }, false));
                }
            }
        }
        content.addView(button("Odśwież płatności z USOS", this::sync, false));
    }
    private void gpaSimulatorView() throws Exception {
        title("Kalkulator średniej i stypendium", "Symulacja ocen, progi stypendium rektora i wyróżnienia");
        JSONObject d = display();
        JSONObject raw = d.optJSONObject("snapshot");
        if (raw == null) {
            empty("Brak danych ocen", "Połącz konto lub włącz tryb demonstracyjny.");
            content.addView(button(connected() ? "Odśwież oceny" : "Połącz konto", () -> { if (connected()) sync(); else navigate(4); }, true));
            return;
        }

        Snapshot snapshot = Snapshot.from(raw);
        GradeSummary summary = GradeSummary.of(snapshot.records.values());

        LinearLayout currentCard = card(content, Color.rgb(238, 245, 242));
        add(currentCard, "AKTUALNY STAN STUDIÓW", 11, GREEN, true);
        double currentWeighted = !Double.isNaN(summary.ectsAverage) ? summary.ectsAverage : summary.average;
        if (!Double.isNaN(summary.ectsAverage)) {
            add(currentCard, String.format(Locale.forLanguageTag("pl-PL"), "Średnia ważona ECTS: %.2f", summary.ectsAverage), 24, INK, true);
            add(currentCard, String.format(Locale.forLanguageTag("pl-PL"), "Zdobyte punkty ECTS: %.1f · średnia arytmetyczna: %.2f", summary.totalEcts, summary.average), 13, MUTED, false);
        } else if (!Double.isNaN(summary.average)) {
            add(currentCard, String.format(Locale.forLanguageTag("pl-PL"), "Średnia arytmetyczna: %.2f", summary.average), 24, INK, true);
            add(currentCard, "Brak przypisanych wag ECTS przy części ocen.", 13, MUTED, false);
        } else {
            add(currentCard, "Brak ocen liczbowych", 20, INK, true);
        }
        add(currentCard, "Liczba wpisów w indeksie: " + (summary.numeric + summary.other) + " (w tym liczbowych: " + summary.numeric + ")", 12, MUTED, false);

        LinearLayout scholarCard = card(content, Color.WHITE);
        add(scholarCard, "Stypendium Rektora Vistula", 18, INK, true);
        String scholarText = GpaCalculator.scholarshipStatus(currentWeighted);
        boolean qualifiesScholar = currentWeighted >= GpaCalculator.SCHOLARSHIP_THRESHOLD;
        add(scholarCard, scholarText, 15, qualifiesScholar ? GREEN : Color.rgb(180, 100, 0), true);
        note(scholarCard, "Kwalifikacja do stypendium rektora dla najlepszych studentów wymaga w Vistuli średniej ważonej min. 4.50 oraz terminowego zaliczenia semestru.", MUTED);

        LinearLayout honorsCard = card(content, Color.WHITE);
        add(honorsCard, "Dyplom ukończenia studiów z wyróżnieniem", 18, INK, true);
        String honorsText = GpaCalculator.honorsStatus(currentWeighted);
        boolean qualifiesHonors = currentWeighted >= GpaCalculator.HONORS_THRESHOLD;
        add(honorsCard, honorsText, 15, qualifiesHonors ? GREEN : MUTED, true);
        note(honorsCard, "Zgodnie z Regulaminem Studiów Vistula wyróżnienie przysługuje przy średniej ze studiów min. 4.75 oraz ocenie bardzo dobrej (5.0) z pracy dyplomowej i egzaminu dyplomowego.", MUTED);

        LinearLayout simCard = card(content, Color.WHITE);
        add(simCard, "Symulator: jaki wynik musisz osiągnąć?", 18, INK, true);
        note(simCard, "Wybierz cel i liczbę pozostałych punktów ECTS (np. 30 ECTS na kolejny semestr):", MUTED);

        add(simCard, "Twój cel średniej:", 13, INK, true);
        chips(new String[]{"4.50", "4.75", "4.80", "5.00"}, new String[]{"4.50 (Stypendium)", "4.75 (Wyróżnienie)", "4.80 (Max styp.)", "5.00 (Maksimum)"},
            String.format(Locale.US, "%.2f", simTargetGpa), value -> {
                try { simTargetGpa = Double.parseDouble(value); render(); } catch (Exception ignored) {}
            });

        add(simCard, "Pozostałe punkty ECTS do zdobycia:", 13, INK, true);
        chips(new String[]{"15", "30", "45", "60"}, new String[]{"15 ECTS (pół semestru)", "30 ECTS (1 semestr)", "45 ECTS", "60 ECTS (cały rok)"},
            String.format(Locale.US, "%.0f", simRemainingEcts), value -> {
                try { simRemainingEcts = Double.parseDouble(value); render(); } catch (Exception ignored) {}
            });

        double currentEcts = !Double.isNaN(summary.totalEcts) && summary.totalEcts > 0 ? summary.totalEcts : 30.0;
        GpaCalculator.SimulationResult sim = GpaCalculator.simulate(currentWeighted, currentEcts, simTargetGpa, simRemainingEcts);

        LinearLayout simResultBox = card(simCard, sim.achievable ? Color.rgb(232, 243, 236) : Color.rgb(255, 238, 238));
        add(simResultBox, sim.achievable ? "CEL MOŻLIWY DO OSIĄGNIĘCIA" : "CEL MATEMATYCZNIE NIEOSIĄGALNY", 11, sim.achievable ? GREEN : Color.rgb(160, 48, 40), true);
        add(simResultBox, sim.statusMessage, 15, INK, true);
        if (sim.achievable && sim.requiredGpa > 2.0 && sim.requiredGpa <= 5.0) {
            add(simResultBox, String.format(Locale.forLanguageTag("pl-PL"), "Wymagana średnia z pozostałych egzaminów: %.2f", sim.requiredGpa), 16, GREEN, true);
        }

        content.addView(button("Przejdź do pełnej listy ocen", () -> navigate(2), false));
    }

    private void campusGuideView() {
        title("Niezbędnik studenta & Kampus", "Najważniejsze działy, kontakty i lokalizacja Vistuli");

        LinearLayout campusCard = card(content, Color.WHITE);
        add(campusCard, "Kampus Uczelni Vistula", 18, INK, true);
        add(campusCard, "ul. Stokłosy 3, 02-787 Warszawa", 15, GREEN, true);
        add(campusCard, "Metro M1: stacja Stokłosy (wyjście w stronę ul. Stokłosy, 200 m pieszo)", 13, MUTED, false);
        campusCard.addView(button("Nawiguj do kampusu (Google Maps)", () -> {
            try {
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:52.158145,21.034326?q=Akademia+Finansów+i+Biznesu+Vistula,+ul.+Stokłosy+3,+Warszawa"));
                startActivity(mapIntent);
            } catch (Exception e) {
                browser("https://maps.google.com/?q=Akademia+Finansów+i+Biznesu+Vistula,+Warszawa");
            }
        }, true));
        campusCard.addView(button("Strona główna uczelni (vistula.edu.pl)", () -> browser("https://www.vistula.edu.pl/"), false));

        LinearLayout dziekanatCard = card(content, Color.WHITE);
        add(dziekanatCard, "Dziekanat (Pokój 118, parter)", 18, INK, true);
        add(dziekanatCard, "Obsługa toku studiów, zaświadczenia, legitymacje, podania do Dziekana", 13, MUTED, false);
        add(dziekanatCard, "Godziny otwarcia:\nPoniedziałek – Piątek: 09:00 – 16:00\nSobota – Niedziela (zjazdy): 08:30 – 15:30", 13, INK, false);
        dziekanatCard.addView(button("Zadzwoń do Dziekanatu (+48 22 45 72 300)", () -> {
            try { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:+48224572300"))); } catch (Exception ignored) {}
        }, false));
        dziekanatCard.addView(button("Napisz do Dziekanatu (dziekanat@vistula.edu.pl)", () -> {
            try { startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:dziekanat@vistula.edu.pl"))); } catch (Exception ignored) {}
        }, false));

        LinearLayout financeCard = card(content, Color.WHITE);
        add(financeCard, "Dział Finansowy i Czesne (Pokój 120)", 18, INK, true);
        add(financeCard, "Informacje o płatnościach, faktury, indywidualne subkonta studenta", 13, MUTED, false);
        financeCard.addView(button("Zobacz Twoje subkonta i raty w aplikacji", () -> navigate(8), true));
        financeCard.addView(button("Napisz do Finansów (finanse@vistula.edu.pl)", () -> {
            try { startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:finanse@vistula.edu.pl"))); } catch (Exception ignored) {}
        }, false));

        LinearLayout libraryCard = card(content, Color.WHITE);
        add(libraryCard, "Biblioteka Główna (Budynek Główny, Wejście C)", 18, INK, true);
        add(libraryCard, "Wypożyczalnia, czytelnia ze strefą cichej nauki, dostęp do baz Springer, ScienceDirect, Legalis.", 13, MUTED, false);
        libraryCard.addView(button("Napisz do Biblioteki (biblioteka@vistula.edu.pl)", () -> {
            try { startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:biblioteka@vistula.edu.pl"))); } catch (Exception ignored) {}
        }, false));

        LinearLayout linksCard = card(content, Color.WHITE);
        add(linksCard, "Regulaminy i procedury uczelni", 18, INK, true);
        linksCard.addView(button("Regulamin studiów Vistula", () -> browser("https://www.vistula.edu.pl/student/strefa-studenta/regulaminy"), false));
        linksCard.addView(button("Wzory podań do Dziekana", () -> browser("https://www.vistula.edu.pl/student/strefa-studenta/wzory-podan"), false));
        linksCard.addView(button("Plany studiów i programy USOS", () -> browser("https://usosapps.vistula.edu.pl/"), false));
    }

    private void newsView() throws Exception {
        JSONObject d = display();
        title("Komunikaty uczelni", "Oficjalne ogłoszenia i zarządzenia Rektora AFiBV");
        JSONObject raw = d.optJSONObject("snapshot");
        Snapshot snapshot = raw != null ? Snapshot.from(raw) : null;
        List<NewsArticle> articles = snapshot != null && snapshot.news != null ? snapshot.news : Collections.emptyList();

        EditText search = searchField(content, "Szukaj w komunikatach…", newsQuery);
        search.setContentDescription("Szukaj komunikatów po tytule, treści lub autorze");

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);

        Runnable populate = () -> {
            list.removeAllViews();
            String q = newsQuery.trim().toLowerCase(Locale.ROOT);
            List<NewsArticle> filtered = new ArrayList<>();
            for (NewsArticle a : articles) {
                if (q.isEmpty() || a.title.toLowerCase(Locale.ROOT).contains(q)
                    || a.headline.toLowerCase(Locale.ROOT).contains(q)
                    || a.author.toLowerCase(Locale.ROOT).contains(q)) {
                    filtered.add(a);
                }
            }

            if (filtered.isEmpty()) {
                LinearLayout emptyBox = card(list, Color.WHITE);
                add(emptyBox, q.isEmpty() ? "Brak komunikatów w pamięci" : "Brak pasujących wyników", 17, INK, true);
                note(emptyBox, q.isEmpty() ? "Wybierz „Odśwież komunikaty”, aby pobrać najnowsze ogłoszenia z USOS." : "Zmień frazę wyszukiwania.", MUTED);
                if (!q.isEmpty()) {
                    emptyBox.addView(button("Wyczyść wyszukiwanie", () -> { newsQuery = ""; render(); }, false));
                }
            } else {
                note(list, "Liczba komunikatów: " + filtered.size(), MUTED);
                for (NewsArticle a : filtered) {
                    LinearLayout itemCard = card(list, Color.WHITE);
                    add(itemCard, a.title, 18, INK, true);
                    String meta = a.publicationDate + (!a.author.isEmpty() ? " · " + a.author : "");
                    add(itemCard, meta, 13, GREEN, true);
                    if (!a.headline.isEmpty()) {
                        add(itemCard, a.headline, 14, INK, false);
                    }
                    itemCard.addView(button("Czytaj pełny komunikat", () -> showNewsDetails(a), false));
                }
            }
        };

        watchSearch(search, value -> { newsQuery = value; populate.run(); });
        populate.run();

        content.addView(button("Odśwież komunikaty z USOS", () -> {
            if (demo) render(); else sync();
        }, true));
    }

    private void showNewsDetails(NewsArticle article) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), dp(8));

        add(box, article.title, 20, INK, true);
        String meta = article.publicationDate + (!article.author.isEmpty() ? " · " + article.author : "");
        add(box, meta, 13, GREEN, true);

        if (!article.headline.isEmpty()) {
            add(box, article.headline, 15, INK, false);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
            .setView(scroll)
            .setPositiveButton("Zamknij", null)
            .setNeutralButton("Kopiuj treść", (dlg, which) -> {
                android.content.ClipboardManager cb = getSystemService(android.content.ClipboardManager.class);
                if (cb != null) {
                    String text = article.title + "\n\n" + meta + "\n\n" + article.headline + (article.url.isEmpty() ? "" : "\n\n" + article.url);
                    cb.setPrimaryClip(android.content.ClipData.newPlainText("Komunikat Vistula", text));
                    Toast.makeText(this, "Skopiowano treść komunikatu", Toast.LENGTH_SHORT).show();
                }
            });

        if (!article.url.isEmpty()) {
            builder.setNegativeButton("Otwórz stronę", (dlg, which) -> browser(article.url));
        }

        builder.show();
    }

    private void attendanceView() throws Exception {
        JSONObject d = display();
        title("Licznik nieobecności", "Kontrola dozwolonych limitów opuszczeń zajęć");

        SharedPreferences prefs = getSharedPreferences("attendance_tracker", MODE_PRIVATE);
        JSONObject saved;
        try { saved = new JSONObject(prefs.getString("data", "{}")); } catch (Exception e) { saved = new JSONObject(); }

        JSONObject raw = d.optJSONObject("snapshot");
        Snapshot snapshot = raw != null ? Snapshot.from(raw) : null;
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
                item.put("name", entry.getValue());
                item.put("absences", 0);
                item.put("limit", 2);
                item.put("notes", "");
                saved.put(entry.getKey(), item);
                changed = true;
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

        LinearLayout summaryBox = card(content, exceededCount > 0 ? Color.rgb(255, 238, 238) : (warningCount > 0 ? Color.rgb(255, 248, 230) : Color.rgb(232, 243, 236)));
        add(summaryBox, exceededCount > 0 ? "PRZEKROCZONY LIMIT NIEOBECNOŚCI" : (warningCount > 0 ? "UWAGA NA LIMIT NIEOBECNOŚCI" : "STAN NIEOBECNOŚCI W NORMIE"), 11, exceededCount > 0 ? Color.rgb(160, 48, 40) : (warningCount > 0 ? Color.rgb(217, 119, 6) : GREEN), true);
        String sumText = String.format(Locale.forLanguageTag("pl-PL"), "Śledzone przedmioty: %d · Suma opuszczeń: %d", totalCourses, totalAbsences);
        add(summaryBox, sumText, 15, INK, true);
        if (exceededCount > 0) {
            note(summaryBox, "Liczba przedmiotów z przekroczonym limitem: " + exceededCount + "! Wymagany kontakt z prowadzącym lub Dziekanatem.", Color.rgb(160, 48, 40));
        } else if (warningCount > 0) {
            note(summaryBox, "Liczba przedmiotów na granicy limitu (zostało 1 opuszczenie): " + warningCount + ".", Color.rgb(217, 119, 6));
        } else {
            note(summaryBox, "Brak zagrożeń. Zgodnie z regulaminem studiów domyślny limit to 2 nieobecności bez usprawiedliwienia.", GREEN);
        }

        chips(new String[]{"all", "warning", "exceeded"}, new String[]{"Wszystkie (" + totalCourses + ")", "Ostrzeżenia (" + warningCount + ")", "Przekroczone (" + exceededCount + ")"}, attendanceFilter, value -> {
            attendanceFilter = value;
            render();
        });

        content.addView(button("Dodaj przedmiot ręcznie", () -> editCourseAttendance(null), true));

        LinearLayout list = new LinearLayout(this);
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

            if ("warning".equals(attendanceFilter) && (count < limit - 1 || count >= limit)) continue;
            if ("exceeded".equals(attendanceFilter) && count < limit) continue;

            shown++;
            LinearLayout itemCard = card(list, Color.WHITE);
            add(itemCard, name, 17, INK, true);
            if (!code.equals(name)) note(itemCard, "Identyfikator: " + code, MUTED);

            String badgeText;
            int badgeColor;
            if (count > limit) {
                badgeText = count + " / " + limit + " · PRZEKROCZONO LIMIT (" + (count - limit) + " za dużo)";
                badgeColor = Color.rgb(160, 48, 40);
            } else if (count == limit) {
                badgeText = count + " / " + limit + " · LIMIT WYCZERPANY";
                badgeColor = Color.rgb(180, 83, 9);
            } else if (count == limit - 1 && limit > 1) {
                badgeText = count + " / " + limit + " · Zostało 1 opuszczenie";
                badgeColor = Color.rgb(217, 119, 6);
            } else {
                badgeText = count + " / " + limit + " · Bezpiecznie (zostało " + (limit - count) + ")";
                badgeColor = GREEN;
            }
            add(itemCard, badgeText, 14, badgeColor, true);

            com.google.android.material.progressindicator.LinearProgressIndicator progress = new com.google.android.material.progressindicator.LinearProgressIndicator(this);
            progress.setMax(limit > 0 ? limit : 1);
            progress.setProgress(Math.min(count, limit));
            progress.setTrackColor(Color.rgb(230, 235, 232));
            progress.setIndicatorColor(badgeColor);
            LinearLayout.LayoutParams pParams = new LinearLayout.LayoutParams(-1, dp(8));
            pParams.bottomMargin = dp(14);
            itemCard.addView(progress, pParams);

            if (!notes.isEmpty()) {
                add(itemCard, "Notatki:\n" + notes, 13, MUTED, false);
            }

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);

            Button minusBtn = button("− 1", () -> {
                int newCount = Math.max(0, count - 1);
                try {
                    item.put("absences", newCount);
                    finalSavedRef.put(code, item);
                    prefs.edit().putString("data", finalSavedRef.toString()).apply();
                    render();
                } catch (JSONException ignored) {}
            }, false);
            minusBtn.setEnabled(count > 0);

            Button plusBtn = button("+ 1 nieobecność", () -> {
                try {
                    item.put("absences", count + 1);
                    finalSavedRef.put(code, item);
                    prefs.edit().putString("data", finalSavedRef.toString()).apply();
                    render();
                } catch (JSONException ignored) {}
            }, true);

            Button editBtn = button("Opcje", () -> editCourseAttendance(code), false);

            LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, -2, 1f); b1.setMarginEnd(dp(4));
            LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0, -2, 2.2f); b2.setMarginEnd(dp(4));
            LinearLayout.LayoutParams b3 = new LinearLayout.LayoutParams(0, -2, 1.2f);
            actions.addView(minusBtn, b1);
            actions.addView(plusBtn, b2);
            actions.addView(editBtn, b3);
            itemCard.addView(actions);
        }

        if (shown == 0) {
            empty("Brak przedmiotów na wybranym filtrze", "Zmień filtr lub dodaj przedmiot ręcznie.");
        }

        note(content, "Dane licznika są zapisywane lokalnie na Twoim urządzeniu. W Vistuli standardowy limit nieobecności to 2 na przedmiot na semestr.", MUTED);
    }

    private void editCourseAttendance(String code) {
        SharedPreferences prefs = getSharedPreferences("attendance_tracker", MODE_PRIVATE);
        JSONObject saved;
        try { saved = new JSONObject(prefs.getString("data", "{}")); } catch (Exception e) { saved = new JSONObject(); }
        JSONObject existing = code != null ? saved.optJSONObject(code) : null;

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), 0);

        EditText nameField = field(form, "Nazwa przedmiotu", existing != null ? existing.optString("name", code) : "", false);
        EditText limitField = field(form, "Limit nieobecności (domyślnie 2)", String.valueOf(existing != null ? existing.optInt("limit", 2) : 2), false);
        limitField.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

        EditText notesField = field(form, "Notatki / Usprawiedliwienia (opcjonalnie)", existing != null ? existing.optString("notes", "") : "", false);
        notesField.setSingleLine(false);
        notesField.setMinLines(2);

        ScrollView formScroll = new ScrollView(this);
        formScroll.addView(form);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
            .setTitle(existing == null ? "Dodaj przedmiot" : "Edycja: " + existing.optString("name", code))
            .setView(formScroll)
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Zapisz", null);

        if (existing != null) {
            final JSONObject delSaved = saved;
            builder.setNeutralButton("Usuń", (dlg, which) -> {
                delSaved.remove(code);
                prefs.edit().putString("data", delSaved.toString()).apply();
                render();
            });
        }

        AlertDialog dialog = builder.create();
        final JSONObject finalSaved = saved;
        dialog.setOnShowListener(v -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(b -> {
            String name = nameField.getText().toString().trim();
            if (name.isEmpty()) {
                nameField.setError("Wpisz nazwę przedmiotu");
                return;
            }
            int lim;
            try {
                lim = Integer.parseInt(limitField.getText().toString().trim());
                if (lim < 1 || lim > 20) throw new NumberFormatException();
            } catch (Exception e) {
                limitField.setError("Podaj liczbę od 1 do 20");
                return;
            }
            try {
                String c = code != null ? code : name;
                JSONObject item = existing != null ? existing : new JSONObject();
                item.put("name", name);
                item.put("limit", lim);
                item.put("notes", notesField.getText().toString().trim());
                if (!item.has("absences")) item.put("absences", 0);
                finalSaved.put(c, item);
                prefs.edit().putString("data", finalSaved.toString()).apply();
                dialog.dismiss();
                render();
            } catch (JSONException ex) {
                error(Repository.message(ex));
            }
        }));
        dialog.show();
    }

    private String plain(String html) { return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().trim(); }
}
