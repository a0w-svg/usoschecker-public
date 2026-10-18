package pl.vistula.checker;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pl.vistula.checker.core.Change;
import pl.vistula.checker.core.LessonTime;
import pl.vistula.checker.core.OAuth;
import pl.vistula.checker.core.Record;
import pl.vistula.checker.core.TaskTools;
import pl.vistula.checker.ui.AccountScreenController;
import pl.vistula.checker.ui.ApiExplorerController;
import pl.vistula.checker.ui.AttendanceScreenController;
import pl.vistula.checker.ui.CampusGuideController;
import pl.vistula.checker.ui.GpaSimulatorController;
import pl.vistula.checker.ui.GradesScreenController;
import pl.vistula.checker.ui.HistoryScreenController;
import pl.vistula.checker.ui.HomeScreenController;
import pl.vistula.checker.ui.MoreScreenController;
import pl.vistula.checker.ui.NewsScreenController;
import pl.vistula.checker.ui.PaymentsScreenController;
import pl.vistula.checker.ui.ScheduleScreenController;
import pl.vistula.checker.ui.TasksScreenController;
import pl.vistula.checker.ui.UiHelper;
import pl.vistula.checker.ui.StudentCardsController;
import pl.vistula.checker.ui.StudentDocumentsController;
import pl.vistula.checker.core.StudentServices;
import pl.vistula.checker.core.ApiCatalog;

/**
 * Main application activity orchestrating modular screen controllers, navigation, and background sync.
 */
public final class MainActivity extends AppCompatActivity {
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
    private SwipeRefreshLayout swipeRefresh;
    private String detectedPin = "";
    private TextView progress;
    private boolean demo, demoChanged, busy;
    private int tab;

    // State preserved across configuration changes
    private double simTargetGpa = 4.50;
    private double simRemainingEcts = 30.0;
    private String filter = "wszystkie";
    private String historyQuery = "";
    private String gradeQuery = "", gradeType = "all", gradeOrder = "name";
    private boolean gradeFiltersExpanded;
    private final java.util.Map<String, Boolean> gradeSemesterExpansion = new java.util.HashMap<>();
    public java.util.Map<String, Boolean> gradeSemesterExpansion() { return gradeSemesterExpansion; }
    private String semester = "", taskFilter = "all", taskQuery = "", pendingCalendar;
    private String taskOrder = "due";
    private String calendarDate = "", calendarMode = "list", calendarQuery = "";
    private boolean focusCurrentSchedule;
    private String newsQuery = "";
    private String attendanceFilter = "all";
    private String documentQuery = "", apiModule = "";
    private JSONObject apiDrafts = new JSONObject();
    private int demoPlanDays = 56;
    private long renderedMinute;
    private boolean loadingState;

    // API catalog state
    private String apiQuery = "";
    private int apiLimit = 60;
    private boolean apiFavoritesOnly;
    private Set<String> apiFavorites = new HashSet<>();
    private String currentMethod;
    private JSONObject currentMethodJson;
    private byte[] uploadBytes;
    private String uploadMethod;
    private UsosClient.Response exportResponse;

    private Repository repository;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!busy && currentMethod == null && tab != 4 && tab != 3 && tab != 8 && tab != 9 && tab != 10 && tab != 11 && tab != 12 && !(getCurrentFocus() instanceof EditText)) load();
            handler.postDelayed(this, 30000);
        }
    };

    @Override public void onCreate(Bundle bundle) {
        pl.vistula.checker.ui.Appearance.apply(this);
        super.onCreate(bundle);
        ReleaseUpdateWorker.schedule(getApplicationContext());
        Object retained = getLastCustomNonConfigurationInstance();
        if (retained instanceof PendingFiles) {
            exportResponse = ((PendingFiles) retained).export;
            uploadBytes = ((PendingFiles) retained).upload;
        }
        repository = new Repository(this);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (busy) return;
                if (drawer != null && (drawer.isDrawerOpen(GravityCompat.START) || drawer.isDrawerVisible(GravityCompat.START))) { drawer.closeDrawers(); return; }
                if (currentMethod != null) { currentMethod = null; currentMethodJson = null; render(); }
                else if (tab != 0) { tab = 0; render(); }
                else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
        taskOrder = getSharedPreferences("student_tools", MODE_PRIVATE).getString("taskOrder", "due");
        gradeOrder = getSharedPreferences("student_tools", MODE_PRIVATE).getString("gradeOrder", "name");
        calendarMode = getSharedPreferences("student_tools", MODE_PRIVATE).getString("calendarMode", "list");
        apiFavorites = new HashSet<>(getSharedPreferences("api_catalog", MODE_PRIVATE).getStringSet("favorites", Collections.emptySet()));

        if (bundle != null) {
            demo = bundle.getBoolean("demo");
            demoChanged = bundle.getBoolean("demoChanged");
            tab = bundle.getInt("tab");
            filter = bundle.getString("filter", "wszystkie");
            historyQuery = bundle.getString("historyQuery", "");
            gradeQuery = bundle.getString("gradeQuery", "");
            gradeType = bundle.getString("gradeType", "all");
            gradeOrder = bundle.getString("gradeOrder", gradeOrder);
            gradeFiltersExpanded = bundle.getBoolean("gradeFiltersExpanded");
            Bundle expandedTerms = bundle.getBundle("gradeSemesterExpansion");
            if (expandedTerms != null) for (String term : expandedTerms.keySet())
                gradeSemesterExpansion.put(term, expandedTerms.getBoolean(term));
            calendarDate = bundle.getString("calendarDate", "");
            calendarQuery = bundle.getString("calendarQuery", "");
            calendarMode = bundle.getString("calendarMode", calendarMode);
            demoPlanDays = bundle.getInt("demoPlanDays", 56);
            semester = bundle.getString("semester", "");
            taskFilter = bundle.getString("taskFilter", "all");
            taskQuery = bundle.getString("taskQuery", "");
            pendingCalendar = bundle.getString("pendingCalendar");
            taskOrder = bundle.getString("taskOrder", "due");
            newsQuery = bundle.getString("newsQuery", "");
            attendanceFilter = bundle.getString("attendanceFilter", "all");
            documentQuery = bundle.getString("documentQuery", "");
            apiModule = bundle.getString("apiModule", "");
            try { apiDrafts = new JSONObject(bundle.getString("apiDrafts", "{}")); } catch (JSONException ignored) {}
            currentMethod = bundle.getString("currentMethod");
            uploadMethod = bundle.getString("uploadMethod");
            try { demoTasks = new JSONArray(bundle.getString("demoTasks", "[]")); } catch (JSONException ignored) {}
            apiQuery = bundle.getString("apiQuery", "");
            apiLimit = bundle.getInt("apiLimit", 60);
            apiFavoritesOnly = bundle.getBoolean("apiFavoritesOnly");
            simTargetGpa = bundle.getDouble("simTargetGpa", 4.50);
            simRemainingEcts = bundle.getDouble("simRemainingEcts", 30.0);
        } else if (getIntent().getBooleanExtra("showHistory", false)) {
            tab = 1;
            filter = historyFilter(getIntent());
        } else if (getIntent().hasExtra("tab")) {
            tab = getIntent().getIntExtra("tab", 0);
        }

        focusCurrentSchedule = bundle == null && tab == 5;
        setContentView(R.layout.activity_main);
        drawer = findViewById(R.id.drawer_layout);
        root = findViewById(R.id.main_root);
        toolbar = findViewById(R.id.toolbar);
        progress = findViewById(R.id.progress_text);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        scroll = findViewById(R.id.main_scroll);
        content = findViewById(R.id.main_content);
        navigation = findViewById(R.id.bottom_navigation);
        sideMenu = findViewById(R.id.nav_view);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            androidx.core.graphics.Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            root.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
            if (navigation != null) {
                navigation.setVisibility(insets.isVisible(WindowInsetsCompat.Type.ime()) ? View.GONE : View.VISIBLE);
            }
            return WindowInsetsCompat.CONSUMED;
        });

        toolbar.setNavigationOnClickListener(v -> {
            if (!busy) drawer.openDrawer(GravityCompat.START);
        });

        swipeRefresh.setColorSchemeColors(UiHelper.GREEN, Color.rgb(20, 110, 60));
        swipeRefresh.setOnRefreshListener(() -> {
            if (connected()) sync();
            else {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(this, R.string.refresh_offline, Toast.LENGTH_SHORT).show();
            }
        });

        navigation.setOnItemSelectedListener(item -> {
            if (busy) return false;
            int id = item.getItemId();
            if (id == R.id.nav_item_home) navigate(0);
            else if (id == R.id.nav_item_grades) navigate(2);
            else if (id == R.id.nav_item_plan) navigate(5);
            else if (id == R.id.nav_item_history) navigate(1);
            else if (id == R.id.nav_item_more) navigate(6);
            return true;
        });

        sideMenu.setNavigationItemSelectedListener(item -> {
            if (busy) return false;
            int id = item.getItemId();
            if (id == R.id.drawer_item_home) navigate(0);
            else if (id == R.id.drawer_item_grades) navigate(2);
            else if (id == R.id.drawer_item_plan) navigate(5);
            else if (id == R.id.drawer_item_history) navigate(1);
            else if (id == R.id.drawer_item_news) navigate(11);
            else if (id == R.id.drawer_item_attendance) navigate(12);
            else if (id == R.id.drawer_item_gpa) navigate(9);
            else if (id == R.id.drawer_item_payments) navigate(8);
            else if (id == R.id.drawer_item_tasks) navigate(7);
            else if (id == R.id.drawer_item_campus) navigate(10);
            else if (id == R.id.drawer_item_account) navigate(4);
            else if (id == R.id.drawer_item_api) navigate(3);
            else if (id == R.id.drawer_item_cards) navigate(13);
            else if (id == R.id.drawer_item_documents) navigate(14);
            drawer.closeDrawers();
            return true;
        });

        task(() -> {
            catalog = new JSONArray(asset("api/index.json"));
            return repository.read();
        }, result -> {
            state = result;
            ensureSchedule();
            if (currentMethod != null) {
                String method = currentMethod;
                showMethod(method);
                return;
            }
            render();
            showReleaseUpdatesIfRequested();
        });
    }

    @Override public void onSaveInstanceState(Bundle out) {
        out.putString("documentQuery", documentQuery);
        out.putString("apiModule", apiModule);
        out.putString("currentMethod", currentMethod);
        out.putString("uploadMethod", uploadMethod);
        try { out.putString("apiDrafts", ApiCatalog.restorableDrafts(apiDrafts).toString()); } catch (JSONException ignored) {}
        out.putBoolean("apiFavoritesOnly", apiFavoritesOnly);
        out.putString("calendarQuery", calendarQuery);
        out.putBoolean("apiFavoritesOnly", apiFavoritesOnly);
        out.putDouble("simTargetGpa", simTargetGpa);
        out.putDouble("simRemainingEcts", simRemainingEcts);
        super.onSaveInstanceState(out);
        out.putBoolean("demo", demo);
        out.putBoolean("demoChanged", demoChanged);
        out.putInt("tab", tab);
        out.putString("filter", filter);
        out.putString("semester", semester);
        out.putString("taskFilter", taskFilter);
        out.putString("taskQuery", taskQuery);
        out.putString("taskOrder", taskOrder);
        out.putString("calendarDate", calendarDate);
        out.putString("calendarMode", calendarMode);
        out.putInt("demoPlanDays", demoPlanDays);
        out.putString("pendingCalendar", pendingCalendar);
        out.putString("newsQuery", newsQuery);
        out.putString("attendanceFilter", attendanceFilter);
        out.putString("demoTasks", demoTasks.toString());
        out.putString("historyQuery", historyQuery);
        out.putString("gradeQuery", gradeQuery);
        out.putString("gradeType", gradeType);
        out.putString("gradeOrder", gradeOrder);
        out.putBoolean("gradeFiltersExpanded", gradeFiltersExpanded);
        Bundle expandedTerms = new Bundle();
        for (java.util.Map.Entry<String, Boolean> entry : gradeSemesterExpansion.entrySet())
            expandedTerms.putBoolean(entry.getKey(), entry.getValue());
        out.putBundle("gradeSemesterExpansion", expandedTerms);
        out.putString("apiQuery", apiQuery);
        out.putInt("apiLimit", apiLimit);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showReleaseUpdatesIfRequested();
        if (intent.getBooleanExtra("showHistory", false)) {
            filter = historyFilter(intent);
            historyQuery = "";
            navigate(1);
        }
        else if (intent.hasExtra("tab")) navigate(intent.getIntExtra("tab", 0));
    }

    private static String historyFilter(Intent intent) {
        String kind = intent.getStringExtra("historyKind");
        return "ocena".equals(kind) || "plan".equals(kind) ? kind : "wszystkie";
    }

    private void showReleaseUpdatesIfRequested() {
        if (!getIntent().getBooleanExtra(ReleaseUpdates.SHOW_UPDATES, false)) return;
        getIntent().removeExtra(ReleaseUpdates.SHOW_UPDATES);
        pl.vistula.checker.ui.ReleaseUpdatesDialog.show(this);
    }

    @Override protected void onResume() {
        super.onResume();
        io.execute(() -> ClassReminderReceiver.scheduleNext(getApplicationContext()));
        handler.removeCallbacks(refresh);
        handler.postDelayed(refresh, 30000);
        if (content != null && !busy && tab != 4 && tab != 3 && tab != 8 && tab != 9 && tab != 10 && tab != 11 && tab != 12) load();
        if (tab == 4 && !state.optString("requestToken").isEmpty() && !connected()) {
            checkClipboardForPin();
        }
    }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresh);
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 10) io.execute(() -> ClassReminderReceiver.scheduleNext(getApplicationContext()));
        if (requestCode == 11) ReleaseUpdateWorker.schedule(getApplicationContext());
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(refresh);
        io.shutdown();
    }
    @Override public Object onRetainCustomNonConfigurationInstance() {
        return new PendingFiles(exportResponse, uploadBytes);
    }
    private static final class PendingFiles {
        final UsosClient.Response export;
        final byte[] upload;
        PendingFiles(UsosClient.Response export, byte[] upload) { this.export = export; this.upload = upload; }
    }

    public void navigate(int target) {
        navigate(target, true);
    }
    public void openScheduleDate(String date) {
        calendarDate = date;
        navigate(5, false);
    }
    private void navigate(int target, boolean focusCurrent) {
        focusCurrentSchedule = focusCurrent && target == 5 && tab != 5;
        currentMethod = null;
        currentMethodJson = null;
        tab = target;
        scroll.scrollTo(0, 0);
        render();
    }

    public void render() {
        if (isDestroyed()) return;
        renderedMinute = System.currentTimeMillis() / 60000;
        content.removeAllViews();
        setScheduleNavigation(null);
        content.setPadding(UiHelper.dp(this, 20), UiHelper.dp(this, tab == 5 ? 0 : 12), UiHelper.dp(this, 20), UiHelper.dp(this, 24));
        android.view.ViewGroup.LayoutParams toolbarParams = toolbar.getLayoutParams();
        toolbarParams.height = UiHelper.dp(this, tab == 5 ? 48 : 64);
        toolbar.setLayoutParams(toolbarParams);

        if (navigation != null) {
            int navMenuId = R.id.nav_item_home;
            if (tab == 2) navMenuId = R.id.nav_item_grades;
            else if (tab == 5) navMenuId = R.id.nav_item_plan;
            else if (tab == 1) navMenuId = R.id.nav_item_history;
            else if (tab == 6 || tab == 3 || tab == 4 || tab == 7 || tab == 8 || tab == 9 || tab == 10 || tab == 11 || tab == 12 || tab == 13 || tab == 14) navMenuId = R.id.nav_item_more;
            navigation.getMenu().findItem(navMenuId).setChecked(true);
        }

        if (sideMenu != null) {
            int drawerMenuId = R.id.drawer_item_home;
            if (tab == 2) drawerMenuId = R.id.drawer_item_grades;
            else if (tab == 5) drawerMenuId = R.id.drawer_item_plan;
            else if (tab == 1) drawerMenuId = R.id.drawer_item_history;
            else if (tab == 11) drawerMenuId = R.id.drawer_item_news;
            else if (tab == 12) drawerMenuId = R.id.drawer_item_attendance;
            else if (tab == 9) drawerMenuId = R.id.drawer_item_gpa;
            else if (tab == 8) drawerMenuId = R.id.drawer_item_payments;
            else if (tab == 7) drawerMenuId = R.id.drawer_item_tasks;
            else if (tab == 10) drawerMenuId = R.id.drawer_item_campus;
            else if (tab == 4) drawerMenuId = R.id.drawer_item_account;
            else if (tab == 3) drawerMenuId = R.id.drawer_item_api;
            else if (tab == 13) drawerMenuId = R.id.drawer_item_cards;
            else if (tab == 14) drawerMenuId = R.id.drawer_item_documents;
            sideMenu.getMenu().findItem(drawerMenuId).setChecked(true);
        }

        int[] titleRes = {
            R.string.nav_home, R.string.nav_history_full, R.string.nav_grades, R.string.nav_api,
            R.string.nav_account, R.string.nav_plan_full, R.string.nav_more, R.string.nav_tasks,
            R.string.nav_payments, R.string.nav_gpa, R.string.nav_campus, R.string.nav_news,
            R.string.nav_attendance, R.string.cards_title, R.string.documents_title
        };
        toolbar.setTitle(tab == 5 && demo ? getString(R.string.schedule_demo_title)
            : tab < titleRes.length ? getString(titleRes[tab]) : getString(R.string.app_name));
        toolbar.setSubtitle(tab == 5 ? null : demo ? getString(R.string.demo_account_subtitle) : connected() ? state.optString("user", getString(R.string.app_name)) : getString(R.string.app_name));

        try {
            if (demo && tab != 5) UiHelper.note(this, content, getString(R.string.demo_banner_note), UiHelper.GREEN);

            switch (tab) {
                case 13:
                    StudentCardsController.render(this, content, demo ? new JSONObject().put("user", "Aleksandra Nowak · DEMO") : state, studentServices());
                    break;
                case 14:
                    StudentDocumentsController.render(this, content, state, studentServices());
                    break;
                case 0:
                    HomeScreenController.render(this, content, display(), state, demo, demoTasks, this::sync, this::lessonDetails);
                    break;
                case 1:
                    HistoryScreenController.render(this, content, display(), filter, historyQuery, demo,
                        f -> { filter = f; render(); },
                        q -> { historyQuery = q; },
                        this::sync);
                    break;
                case 2:
                    GradesScreenController.render(this, content, display(), state, demo,
                        semester, gradeType, gradeOrder, gradeQuery, gradeFiltersExpanded,
                        exp -> { gradeFiltersExpanded = exp; render(); },
                        sem -> { semester = sem; if (!sem.isEmpty()) gradeSemesterExpansion.put(sem, true); render(); },
                        gt -> { gradeType = gt; render(); },
                        go -> {
                            gradeOrder = go;
                            getSharedPreferences("student_tools", MODE_PRIVATE).edit().putString("gradeOrder", go).apply();
                            render();
                        },
                        gq -> { gradeQuery = gq; },
                        () -> {
                            semester = ""; gradeType = "all"; gradeQuery = ""; gradeOrder = "name";
                            gradeSemesterExpansion.clear();
                            getSharedPreferences("student_tools", MODE_PRIVATE).edit().putString("gradeOrder", gradeOrder).apply();
                            render();
                        },
                        this::sync,
                        new GradesScreenController.GradesListener() {
                            @Override public void onFilterChanged() { render(); }
                            @Override public void onOpenTask(String title) { editTask(null, title); }
                        });
                    break;
                case 3:
                    if (currentMethod != null && currentMethodJson != null) {
                        ApiExplorerController.renderMethodDetails(this, content, scroll, currentMethodJson, apiFavorites, uploadBytes,
                            () -> { currentMethod = null; currentMethodJson = null; render(); },
                            new ApiExplorerController.ApiCallback() {
                                @Override public void onExecuteRequest(JSONObject metadata, java.util.Map<String, String> values, String method, byte[] upload, java.util.function.Consumer<UsosClient.Response> onSuccess, java.util.function.Consumer<String> onFailure) {
                                    executeApiRequest(metadata, values, method, upload, onSuccess, onFailure);
                                }
                                @Override public void onSelectUploadFile(String method) {
                                    uploadMethod = method;
                                    startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), 21);
                                }
                                @Override public void onSaveResponse(UsosClient.Response resp) {
                                    saveResponseFile(resp, "usos-response");
                                }
                            });
                    } else {
                        ApiExplorerController.renderCatalog(this, content, catalog, apiQuery, apiLimit, apiFavoritesOnly, apiFavorites,
                            q -> { apiQuery = q; },
                            lim -> { apiLimit = lim; },
                            fav -> { apiFavoritesOnly = fav; apiLimit = 60; render(); },
                            this::showMethod);
                    }
                    break;
                case 4:
                    AccountScreenController.render(this, content, display(), state, demo, detectedPin,
                        new AccountScreenController.AccountListener() {
                            @Override public void onBeginOAuth(String key, String secret, String scopes) {
                                task(() -> {
                                    repository.begin(key, secret, scopes);
                                    return repository.read();
                                }, s -> {
                                    state = s;
                                    render();
                                    browser("https://usosapps.vistula.edu.pl/services/oauth/authorize?oauth_token=" + OAuth.encode(s.getString("requestToken")));
                                });
                            }
                            @Override public void onConfirmPin(String pin) {
                                task(() -> {
                                    repository.finish(pin);
                                    return repository.read();
                                }, s -> {
                                    state = s;
                                    detectedPin = "";
                                    demo = false;
                                    tab = 0;
                                    ensureSchedule();
                                    render();
                                    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
                                    }
                                    sync();
                                });
                            }
                            @Override public void onToggleEnabled(boolean enabled) {
                                task(() -> {
                                    repository.update(s -> s.put("enabled", enabled));
                                    return repository.read();
                                }, s -> {
                                    state = s;
                                    if (enabled) SyncWorker.schedule(MainActivity.this, state.optLong("interval", 30L));
                                    else SyncWorker.cancel(MainActivity.this);
                                    render();
                                });
                            }
                            @Override public void onUpdateInterval(long interval) {
                                task(() -> {
                                    repository.update(s -> s.put("interval", interval));
                                    return repository.read();
                                }, s -> {
                                    state = s;
                                    ensureSchedule();
                                    render();
                                });
                            }
                            @Override public void onDisconnect() {
                                task(() -> {
                                    SyncWorker.cancel(MainActivity.this);
                                    repository.disconnect();
                                    ClassReminderReceiver.cancel(MainActivity.this);
                                    androidx.core.app.NotificationManagerCompat.from(MainActivity.this).cancelAll();
                                    pl.vistula.checker.widget.TodayScheduleWidget.updateAll(MainActivity.this);
                                    pl.vistula.checker.widget.QuickStatusWidget.updateAll(MainActivity.this);
                                    return repository.read();
                                }, s -> {
                                    state = s;
                                    detectedPin = "";
                                    apiDrafts = new JSONObject();
                                    uploadBytes = null;
                                    uploadMethod = null;
                                    exportResponse = null;
                                    pendingCalendar = null;
                                    gradeSemesterExpansion.clear();
                                    semester = "";
                                    demo = false;
                                    render();
                                });
                            }
                            @Override public void onStartDemo() {
                                gradeSemesterExpansion.clear();
                                semester = "";
                                demo = true;
                                tab = 0;
                                render();
                            }
                            @Override public void onEndDemo() {
                                gradeSemesterExpansion.clear();
                                semester = "";
                                demo = false;
                                render();
                            }
                            @Override public void onRequestNotifications() {
                                requestNotifications();
                            }
                        });
                    break;
                case 5:
                    ScheduleScreenController.render(this, content, display(), state, demo,
                        calendarDate, calendarMode, demoPlanDays,
                        dVal -> { calendarDate = dVal; render(); },
                        mVal -> { calendarMode = mVal; render(); },
                        this::sync,
                        new ScheduleScreenController.ScheduleListener() {
                            @Override public void onOpenTask(String title) { editTask(null, title); }
                            @Override public void onAbsenceRecorded() { render(); }
                        });
                    if (focusCurrentSchedule && content.findViewById(R.id.schedule_swipe_content) != null) {
                        focusCurrentSchedule = false;
                        View ongoing = content.findViewById(R.id.schedule_current_lesson);
                        if (ongoing != null) androidx.core.view.OneShotPreDrawListener.add(scroll, () -> {
                            if (tab != 5 || content.findViewById(R.id.schedule_current_lesson) != ongoing) return;
                            int[] cardPosition = new int[2], viewportPosition = new int[2];
                            ongoing.getLocationOnScreen(cardPosition);
                            scroll.getLocationOnScreen(viewportPosition);
                            scroll.scrollTo(0, Math.max(0, scroll.getScrollY() + cardPosition[1] - viewportPosition[1] - UiHelper.dp(this, 8)));
                        });
                    }
                    break;
                case 6:
                    MoreScreenController.render(this, content);
                    break;
                case 7:
                    TasksScreenController.render(this, content, personalTasks(), taskFilter, taskOrder, taskQuery,
                        tf -> { taskFilter = tf; render(); },
                        to -> {
                            taskOrder = to;
                            getSharedPreferences("student_tools", MODE_PRIVATE).edit().putString("taskOrder", to).apply();
                            render();
                        },
                        tq -> { taskQuery = tq; },
                        taskCallback());
                    break;
                case 8:
                    PaymentsScreenController.render(this, content, display(), state, connected(), this::sync);
                    break;
                case 9:
                    GpaSimulatorController.render(this, content, display(), simTargetGpa, simRemainingEcts,
                        target -> { simTargetGpa = target; render(); },
                        ects -> { simRemainingEcts = ects; render(); });
                    break;
                case 10:
                    CampusGuideController.render(this, content);
                    break;
                case 11:
                    NewsScreenController.render(this, content, display(), newsQuery,
                        q -> { newsQuery = q; },
                        () -> { if (demo) render(); else sync(); });
                    break;
                case 12:
                    AttendanceScreenController.render(this, content, display(), attendanceFilter,
                        f -> { attendanceFilter = f; render(); },
                        this::render);
                    break;
            }
        } catch (Exception e) {
            UiHelper.note(this, content, Repository.message(e), Color.rgb(167, 49, 49));
        }
        updateBusy(busy);
    }

    public JSONObject display() throws JSONException {
        return demo ? Demo.state(demoChanged, demoPlanDays) : state;
    }

    public boolean connected() {
        return !state.optString("token").isEmpty();
    }

    public boolean isDemo() { return demo; }
    public android.content.SharedPreferences attendancePreferences() {
        return getSharedPreferences(demo ? "attendance_tracker_demo" : "attendance_tracker", MODE_PRIVATE);
    }
    public void setDemo(boolean demo) { this.demo = demo; render(); }

    public String getCalendarDate() { return calendarDate; }
    public void setCalendarDate(String date) { this.calendarDate = date; }
    public boolean shouldFocusCurrentSchedule() { return focusCurrentSchedule; }
    public void setScheduleNavigation(java.util.function.IntConsumer action) {
        if (scroll instanceof pl.vistula.checker.ui.ScheduleScrollView)
            ((pl.vistula.checker.ui.ScheduleScrollView) scroll).setScheduleNavigation(action);
    }

    public String getCalendarMode() { return calendarMode; }
    public void setCalendarMode(String mode) { this.calendarMode = mode; }
    public String getDocumentQuery() { return documentQuery; }
    public String getApiModule() { return apiModule; }
    public void setApiModule(String module) { apiModule = module; apiLimit = 60; render(); }
    public void setDocumentQuery(String query) { documentQuery = query; }
    public JSONObject serviceAccountState() { return demo ? new JSONObject() : state; }
    private JSONObject studentServices() throws Exception {
        if (demo) return DemoStudentServices.data();
        JSONObject services = state.optJSONObject("studentServices");
        return services == null ? new JSONObject() : services;
    }
    public void refreshStudentServices(String section) {
        if (demo) { render(); return; }
        task(() -> { StudentServiceLoader.refresh(repository, section); return repository.read(); }, result -> { state = result; render(); });
    }
    public void openApiModule(String module) {
        apiModule = module; apiQuery = ""; apiLimit = 60; apiFavoritesOnly = false;
        navigate(3);
    }
    public void openApiMethod(String method, java.util.Map<String, String> parameters) {
        try { apiDrafts.put(method, new JSONObject(parameters)); }
        catch (JSONException e) { error(Repository.message(e)); return; }
        tab = 3; showMethod(method);
    }
    public JSONObject apiDraft(String method) {
        JSONObject draft = apiDrafts.optJSONObject(method);
        if (draft == null) {
            draft = new JSONObject();
            try { apiDrafts.put(method, draft); } catch (JSONException ignored) {}
        }
        return draft;
    }
    public byte[] apiUpload(String method) { return method.equals(uploadMethod) ? uploadBytes : null; }
    private boolean liveService(String scope) {
        if (demo) { Toast.makeText(this, R.string.services_demo_action, Toast.LENGTH_LONG).show(); return false; }
        if (!connected()) { Toast.makeText(this, R.string.services_connect_first, Toast.LENGTH_LONG).show(); return false; }
        if (!StudentServices.hasScope(state.optString("scopes"), scope)) {
            error(getString(R.string.services_scope_required, scope)); return false;
        }
        return true;
    }
    private JSONObject serviceCredentials(String scope) throws Exception {
        JSONObject credentials = repository.read();
        if (credentials.optString("token").isEmpty() || credentials.optBoolean("authorizationError"))
            throw new IllegalStateException(getString(R.string.services_connect_first));
        if (!StudentServices.hasScope(credentials.optString("scopes"), scope))
            throw new IllegalStateException(getString(R.string.services_scope_required, scope));
        if (credentials.optLong("retryAt") > System.currentTimeMillis())
            throw new IllegalStateException("USOS prosi o przerwę. Spróbuj później.");
        return credentials;
    }
    public void mobileCardAction(boolean order, String id) {
        if (!liveService("cards")) return;
        new MaterialAlertDialogBuilder(this).setTitle(order ? R.string.cards_order : R.string.cards_revoke)
            .setMessage(order ? getString(R.string.cards_order_confirm) : getString(R.string.cards_revoke_confirm, id))
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(order ? R.string.cards_order : R.string.cards_revoke, (dialog, which) -> task(() -> {
                JSONObject credentials = serviceCredentials("cards");
                UsosClient client = new UsosClient(credentials);
                JSONObject eligibility = new JSONObject(client.get("services/cards/mcard2", java.util.Map.of("fields", "why_cannot_order|why_cannot_revoke")));
                if (!eligibility.has(order ? "why_cannot_order" : "why_cannot_revoke"))
                    throw new IllegalStateException(getString(R.string.cards_action_rejected));
                String reason = StudentServices.localized(eligibility.opt(order ? "why_cannot_order" : "why_cannot_revoke"));
                if (!reason.isEmpty()) throw new IllegalStateException(reason);
                String endpoint = order ? "services/cards/make_mcard_order" : "services/cards/revoke_mcard";
                JSONObject response = new JSONObject(client.request("POST", endpoint, order ? java.util.Map.of() : java.util.Map.of("order_id", id),
                    true, credentials.optString("token"), credentials.optString("tokenSecret")));
                if (!response.optBoolean("success")) throw new IllegalStateException(getString(R.string.cards_action_rejected));
                StudentServiceLoader.refresh(repository, "cards");
                return repository.read();
            }, result -> { state = result; render(); Toast.makeText(this, R.string.cards_action_success, Toast.LENGTH_LONG).show(); })).show();
    }
    public void showMobileActivation(String id) {
        if (!liveService("cards")) return;
        task(() -> new JSONObject(new UsosClient(serviceCredentials("cards")).get("services/cards/mcard_order2", java.util.Map.of(
            "order_id", id, "fields", "status|token|distribution_pass"))), data -> {
                String token = StudentServices.text(data, "token"), password = StudentServices.text(data, "distribution_pass");
                if (!"ready_to_receive".equals(StudentServices.text(data, "status")) || token.isEmpty() || password.isEmpty()) {
                    error(getString(R.string.cards_activation_unavailable)); return;
                }
                androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.cards_activation_title).setMessage(getString(R.string.cards_activation_data, token, password))
                    .setPositiveButton(R.string.btn_close, null).create();
                if (dialog.getWindow() != null) dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
                dialog.show();
                TextView message = dialog.findViewById(android.R.id.message);
                if (message != null) message.setTextIsSelectable(true);
            });
    }
    public void downloadStudentDocument(String endpoint, String argument, String id, String name) {
        if (!liveService("adm_documents")) return;
        if (!Set.of("services/blobbox/get", "services/adm_documents/get_upo", "services/adm_documents/get_upd").contains(endpoint)) return;
        task(() -> {
            JSONObject credentials = serviceCredentials("adm_documents");
            return new UsosClient(credentials).requestData("GET", endpoint, java.util.Map.of(argument, id), true,
                credentials.optString("token"), credentials.optString("tokenSecret"), null, null);
        }, response -> saveResponseFile(response, name));
    }
    private void saveResponseFile(UsosClient.Response response, String name) {
        exportResponse = response;
        String extension = response.mime.contains("json") ? ".json" : response.mime.contains("pdf") ? ".pdf"
            : response.mime.contains("xml") ? ".xml" : response.mime.contains("calendar") ? ".ics" : response.binary() ? ".bin" : ".txt";
        String filename = name.replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        if (filename.length() > 100) filename = filename.substring(0, 100);
        if (filename.trim().isEmpty()) filename = "usos-document";
        try { startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(response.mime)
            .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, filename + extension), 22); }
        catch (ActivityNotFoundException e) { exportResponse = null; error(getString(R.string.toast_export_calendar_error)); }
    }
    public String getCalendarQuery() { return calendarQuery; }
    public void setCalendarQuery(String query) { calendarQuery = query; }

    public void saveCalendar(String ics) {
        pendingCalendar = ics;
        try {
            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/calendar")
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "plan_zajec_vistula.ics"), 23);
        } catch (ActivityNotFoundException e) {
            pendingCalendar = null;
            Toast.makeText(this, R.string.toast_export_calendar_error, Toast.LENGTH_SHORT).show();
        }
    }

    public JSONArray personalTasks() {
        return demo ? demoTasks : (state.optJSONArray("tasks") == null ? new JSONArray() : state.optJSONArray("tasks"));
    }

    public void openTasks(String selectedFilter) {
        taskFilter = selectedFilter;
        taskQuery = "";
        navigate(7);
    }

    public void editTask(JSONObject existing, String suggestedTitle) {
        TasksScreenController.showTaskDialog(this, existing, suggestedTitle, personalTasks().length(),
            taskCallback());
    }

    private TasksScreenController.TaskCallback taskCallback() {
        Repository.EditSession session = repository.editSession(state);
        boolean wasDemo = demo;
        return new TasksScreenController.TaskCallback() {
            @Override public void onSaveTask(JSONObject t) {
                if (demo != wasDemo) return;
                savePersonalTask(t, session);
            }
            @Override public void onMutateTask(String id, boolean remove) {
                if (demo != wasDemo) return;
                mutateTasks(id, remove, session);
            }
        };
    }

    public void savePersonalTask(JSONObject entry) {
        savePersonalTask(entry, repository.editSession(state));
    }

    private void savePersonalTask(JSONObject entry, Repository.EditSession session) {
        if (demo) {
            try { demoTasks = TaskTools.save(demoTasks, entry); render(); }
            catch (Exception e) { error(Repository.message(e)); }
        } else {
            task(() -> {
                repository.update(session, s -> s.put("tasks", TaskTools.save(s.optJSONArray("tasks"), entry)));
                return repository.read();
            }, result -> {
                state = result;
                render();
            });
        }
    }

    public void mutateTasks(String id, boolean remove) {
        mutateTasks(id, remove, repository.editSession(state));
    }

    private void mutateTasks(String id, boolean remove, Repository.EditSession session) {
        JSONObject deleted = null;
        if (remove) {
            JSONArray current = personalTasks();
            for (int i = 0; i < current.length(); i++) {
                JSONObject row = current.optJSONObject(i);
                if (row != null && row.optString("id").equals(id)) { deleted = row; break; }
            }
        }
        JSONObject undoEntry = deleted;
        boolean wasDemo = demo;

        if (demo) {
            try {
                demoTasks = changedTasks(demoTasks, id, remove);
                render();
                if (undoEntry != null) offerTaskUndo(undoEntry, wasDemo, session);
            } catch (JSONException e) {
                error(Repository.message(e));
            }
        } else {
            task(() -> {
                repository.update(session, s -> s.put("tasks", changedTasks(s.optJSONArray("tasks"), id, remove)));
                return repository.read();
            }, result -> {
                state = result;
                render();
                if (undoEntry != null) offerTaskUndo(undoEntry, wasDemo, session);
            });
        }
    }

    private void offerTaskUndo(JSONObject entry, boolean wasDemo, Repository.EditSession session) {
        Snackbar.make(root, R.string.task_deleted, 10000).setAction(R.string.undo, view -> {
            if (busy || demo != wasDemo) return;
            if (wasDemo) {
                try { demoTasks = TaskTools.restore(demoTasks, entry); render(); }
                catch (Exception e) { error(Repository.message(e)); }
            } else {
                task(() -> {
                    repository.update(session, s -> {
                        s.put("tasks", TaskTools.restore(s.optJSONArray("tasks"), entry));
                    });
                    return repository.read();
                }, result -> { state = result; render(); });
            }
        }).show();
    }

    private JSONArray changedTasks(JSONArray source, String id, boolean remove) throws JSONException {
        JSONArray result = new JSONArray();
        if (source != null) {
            for (int i = 0; i < source.length(); ++i) {
                JSONObject entry = new JSONObject(source.getJSONObject(i).toString());
                if (entry.getString("id").equals(id)) {
                    if (remove) continue;
                    entry.put("done", !entry.optBoolean("done"));
                }
                result.put(entry);
            }
        }
        return result;
    }

    public void lessonDetails(LessonTime lesson) {
        ScheduleScreenController.showLessonDetails(this, lesson, new ScheduleScreenController.ScheduleListener() {
            @Override public void onOpenTask(String title) { editTask(null, title); }
            @Override public void onAbsenceRecorded() { render(); }
        });
    }

    public void sync() {
        if (demo) {
            demoChanged = true;
            tab = 1;
            render();
            pl.vistula.checker.widget.TodayScheduleWidget.updateAll(this);
            pl.vistula.checker.widget.QuickStatusWidget.updateAll(this);
        } else {
            int position = scroll.getScrollY();
            boolean first = state.optJSONObject("snapshot") == null;
            task(() -> {
                List<Change> changes = repository.syncAndNotify(true, message -> runOnUiThread(() -> {
                    if (!isDestroyed()) progress.setText(message);
                }));
                ClassReminderReceiver.scheduleNext(this);
                pl.vistula.checker.widget.TodayScheduleWidget.updateAll(this);
                pl.vistula.checker.widget.QuickStatusWidget.updateAll(this);
                return new SyncResult(repository.read(), changes.size(), first);
            }, result -> {
                state = result.state;
                render();
                scroll.post(() -> scroll.scrollTo(0, position));
                String message = result.first
                    ? getString(R.string.first_sync_saved)
                    : result.changes == 0 ? getString(R.string.sync_result_up_to_date) : getString(R.string.sync_result_changes, result.changes);
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            });
        }
    }

    private void showMethod(String name) {
        task(() -> new JSONObject(asset("api/" + name.replace("/", "__") + ".json")), json -> {
            currentMethod = name;
            currentMethodJson = json;
            render();
        });
    }

    private void executeApiRequest(JSONObject metadata, java.util.Map<String, String> values, String method, byte[] upload,
                                   java.util.function.Consumer<UsosClient.Response> onSuccess,
                                   java.util.function.Consumer<String> onFailure) {
        task(() -> {
            JSONObject credentials = new JSONObject(repository.read().toString());
            if (demo) credentials = new JSONObject();
            if (credentials.optString("key").isEmpty() && BuildConfig.USOS_KEY != null && !BuildConfig.USOS_KEY.isEmpty()) {
                credentials.put("key", BuildConfig.USOS_KEY.trim());
                credentials.put("secret", BuildConfig.USOS_SECRET != null ? BuildConfig.USOS_SECRET.trim() : "");
            }
            return new UsosClient(credentials).explore(metadata, values, method, upload, "upload.bin");
        }, onSuccess::accept, onFailure);
    }

    private void ensureSchedule() {
        if (state.optBoolean("enabled") && connected()) SyncWorker.schedule(this, state.optLong("interval", 30));
    }

    private void load() {
        if (loadingState || busy || isDestroyed()) return;
        loadingState = true;
        io.execute(() -> {
            try {
                JSONObject latest = repository.read();
                runOnUiThread(() -> {
                    loadingState = false;
                    if (isDestroyed() || busy) return;
                    boolean clockChanged = (tab == 0 || tab == 5) && renderedMinute != System.currentTimeMillis() / 60000;
                    if (!state.toString().equals(latest.toString()) || clockChanged) {
                        int position = scroll.getScrollY();
                        state = latest;
                        render();
                        scroll.post(() -> scroll.scrollTo(0, position));
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> { loadingState = false; });
            }
        });
    }

    private interface Job<T> { T run() throws Exception; }
    private interface Done<T> { void accept(T value) throws Exception; }

    private <T> void task(Job<T> job, Done<T> done) {
        task(job, done, message -> {});
    }

    private <T> void task(Job<T> job, Done<T> done, java.util.function.Consumer<String> failed) {
        if (busy || isDestroyed()) return;
        busy = true;
        updateBusy(true);
        io.execute(() -> {
            try {
                T result = job.run();
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    busy = false;
                    updateBusy(false);
                    try { done.accept(result); } catch (Exception e) { error(Repository.message(e)); }
                });
            } catch (Exception e) {
                JSONObject latest;
                try { latest = repository.read(); } catch (Exception ignored) { latest = state; }
                JSONObject recovered = latest;
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    busy = false;
                    state = recovered;
                    updateBusy(false);
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
        if (swipeRefresh != null) swipeRefresh.setRefreshing(value);
    }

    private void setButtons(View v, boolean enabled) {
        if (v instanceof Button || v instanceof CompoundButton || v instanceof Spinner || v instanceof EditText || v.isClickable()) {
            if (!enabled) { if (!(v.getTag() instanceof Boolean)) v.setTag(v.isEnabled()); v.setEnabled(false); }
            else if (v.getTag() instanceof Boolean) { v.setEnabled((Boolean) v.getTag()); v.setTag(null); }
        }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) setButtons(((ViewGroup) v).getChildAt(i), enabled);
    }

    private void checkClipboardForPin() {
        try {
            android.content.ClipboardManager cb = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cb != null && cb.hasPrimaryClip() && cb.getPrimaryClip() != null && cb.getPrimaryClip().getItemCount() > 0) {
                android.content.ClipData.Item item = cb.getPrimaryClip().getItemAt(0);
                if (item != null && item.getText() != null) {
                    String clip = item.getText().toString().trim().replaceAll("\\s+", "");
                    if (clip.matches("^[a-zA-Z0-9]{6,16}$") && !clip.equals(detectedPin)) {
                        detectedPin = clip;
                        Toast.makeText(this, R.string.pin_detected_clipboard, Toast.LENGTH_LONG).show();
                        render();
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        } else {
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        }
    }

    public void browser(String address) {
        UiHelper.browser(this, address);
    }

    private void error(String message) {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.error_dialog_title).setMessage(message).setPositiveButton(R.string.btn_ok, null)
            .setNeutralButton(R.string.error_dialog_copy, (dialog, which) -> {
                android.content.ClipboardManager clipboard = getSystemService(android.content.ClipboardManager.class);
                if (clipboard != null) clipboard.setPrimaryClip(UiHelper.privateClip(getString(R.string.error_clipboard_label), message));
            }).show();
    }

    private String asset(String path) throws IOException {
        try (InputStream input = getAssets().open(path); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return bytes.toString("UTF-8");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 23 && resultCode != RESULT_OK) pendingCalendar = null;
        if (requestCode == 22 && resultCode != RESULT_OK) exportResponse = null;
        if (resultCode == -1 && data != null && data.getData() != null) {
            Uri uri = data.getData();
            if ((requestCode == 21 || requestCode == 22 || requestCode == 23)
                    && (!"content".equals(uri.getScheme()) || uri.getAuthority() == null || uri.getAuthority().isEmpty())) {
                if (requestCode == 22) exportResponse = null;
                if (requestCode == 23) pendingCalendar = null;
                error(getString(R.string.error_invalid_document_uri));
                return;
            }
            if (requestCode == 21 && currentMethod != null && currentMethod.equals(uploadMethod)) {
                task(() -> {
                    try (InputStream input = getContentResolver().openInputStream(uri);
                         ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                        if (input == null) throw new IOException(getString(R.string.error_cant_open_file));
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            if (bytes.size() + count > 8388608) throw new IllegalArgumentException(getString(R.string.error_file_too_large));
                            bytes.write(buffer, 0, count);
                        }
                        return bytes.toByteArray();
                    }
                }, bytesx -> {
                    uploadBytes = bytesx;
                    render();
                    Toast.makeText(this, getString(R.string.selected_upload, bytesx.length), Toast.LENGTH_SHORT).show();
                });
            } else if (requestCode == 23 && pendingCalendar != null) {
                byte[] bytes = pendingCalendar.getBytes(StandardCharsets.UTF_8);
                pendingCalendar = null;
                task(() -> {
                    try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                        if (output == null) throw new IOException(getString(R.string.error_cant_open_file));
                        output.write(bytes);
                    }
                    return true;
                }, ok -> Toast.makeText(this, R.string.schedule_calendar_saved, Toast.LENGTH_SHORT).show());
            } else if (requestCode == 22 && exportResponse != null) {
                byte[] bytes = exportResponse.bytes;
                exportResponse = null;
                task(() -> {
                    try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                        if (output == null) throw new IOException();
                        output.write(bytes);
                    }
                    return true;
                }, ok -> Toast.makeText(this, R.string.toast_api_response_saved, Toast.LENGTH_SHORT).show());
            }
        }
    }

    private static final class SyncResult {
        final JSONObject state;
        final int changes;
        final boolean first;
        SyncResult(JSONObject state, int changes, boolean first) {
            this.state = state;
            this.changes = changes;
            this.first = first;
        }
    }
}
