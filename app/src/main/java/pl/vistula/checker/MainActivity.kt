package pl.vistula.checker

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.Consumer
import java.util.function.IntConsumer
import kotlin.math.max
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import pl.vistula.checker.core.ApiCatalog
import pl.vistula.checker.core.CampusAffiliation
import pl.vistula.checker.core.LessonTime
import pl.vistula.checker.core.OAuth
import pl.vistula.checker.core.StudentServices
import pl.vistula.checker.core.TaskTools
import pl.vistula.checker.ui.AccountScreenController
import pl.vistula.checker.ui.ApiErrorUi
import pl.vistula.checker.ui.ApiExplorerController
import pl.vistula.checker.ui.Appearance
import pl.vistula.checker.ui.AttendanceScreenController
import pl.vistula.checker.ui.CampusGuideController
import pl.vistula.checker.ui.GpaSimulatorController
import pl.vistula.checker.ui.GradesScreenController
import pl.vistula.checker.ui.HistoryScreenController
import pl.vistula.checker.ui.HomeScreenController
import pl.vistula.checker.ui.Language
import pl.vistula.checker.ui.MoreScreenController
import pl.vistula.checker.ui.NewsScreenController
import pl.vistula.checker.ui.PaymentsScreenController
import pl.vistula.checker.ui.ReleaseUpdatesDialog
import pl.vistula.checker.ui.ScheduleScreenController
import pl.vistula.checker.ui.ScheduleScrollView
import pl.vistula.checker.ui.StudentCardsController
import pl.vistula.checker.ui.StudentDocumentsController
import pl.vistula.checker.ui.SwipeNavigationView
import pl.vistula.checker.ui.TasksScreenController
import pl.vistula.checker.ui.UiHelper
import pl.vistula.checker.widget.QuickStatusWidget
import pl.vistula.checker.widget.TodayScheduleWidget

/**
 * Main application activity orchestrating modular screen controllers, navigation, and background sync.
 */
class MainActivity : AppCompatActivity() {
    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private var state: JSONObject = JSONObject()
    private var catalog: JSONArray = JSONArray()
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private var navigation: BottomNavigationView? = null
    private var welcomeStep = -1
    private var demoTasks: JSONArray = JSONArray()
    private lateinit var scroll: ScrollView
    private var swipeRefresh: SwipeRefreshLayout? = null
    private var detectedPin: String = ""
    private lateinit var progress: TextView
    private var demo: Boolean = false
    private var demoChanged: Boolean = false
    private var busy: Boolean = false
    private var mobileActivationDialog: androidx.appcompat.app.AlertDialog? = null
    private var tab: Int = 0

    // State preserved across configuration changes
    private var simTargetGpa: Double = 4.50
    private var simRemainingEcts: Double = 30.0
    private var filter: String = "wszystkie"
    private var historyQuery: String = ""
    private var gradeQuery: String = ""
    private var gradeType: String = "all"
    private var gradeOrder: String = "name"
    private var gradeFiltersExpanded: Boolean = false
    private val gradeSemesterExpansion: MutableMap<String, Boolean> = HashMap()
    fun gradeSemesterExpansion(): MutableMap<String, Boolean> = gradeSemesterExpansion

    private var semester: String = ""
    private var taskFilter: String = "all"
    private var taskQuery: String = ""
    private var pendingCalendar: String? = null
    private var taskOrder: String = "due"
    private var calendarDate: String = ""
    private var calendarMode: String = "list"
    private var calendarQuery: String = ""
    private var focusCurrentSchedule: Boolean = false
    private var newsQuery: String = ""
    private var attendanceFilter: String = "all"
    private var documentQuery: String = ""
    private var apiModule: String = ""
    private var apiDrafts: JSONObject = JSONObject()
    private var demoPlanDays: Int = 56
    private var renderedMinute: Long = 0
    private var loadingState: Boolean = false
    private var settingsPage: String? = null
    private var demoCampus: String = "0000"

    // API catalog state
    private var apiQuery: String = ""
    private var apiLimit: Int = 60
    private var apiFavoritesOnly: Boolean = false
    private var apiFavorites: MutableSet<String> = HashSet()
    private var currentMethod: String? = null
    private var currentMethodJson: JSONObject? = null
    private var uploadBytes: ByteArray? = null
    private var uploadMethod: String? = null
    private var exportResponse: UsosClient.Response? = null

    private lateinit var repository: Repository
    private val handler: Handler = Handler(Looper.getMainLooper())
    private val refresh: Runnable = object : Runnable {
        override fun run() {
            if (!busy &&
                currentMethod == null &&
                tab != 4 && tab != 3 && tab != 8 && tab != 9 && tab != 10 && tab != 11 && tab != 12 &&
                currentFocus !is EditText
            ) {
                load()
            }
            handler.postDelayed(this, 30000)
        }
    }

    override fun onCreate(bundle: Bundle?) {
        Appearance.apply(this)
        super.onCreate(bundle)
        ReleaseUpdateWorker.schedule(applicationContext)
        val retained = lastCustomNonConfigurationInstance
        if (retained is PendingFiles) {
            exportResponse = retained.export
            uploadBytes = retained.upload
        }
        repository = Repository(this)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (busy) return
                    if (welcomeStep > 0) {
                        showWelcome(welcomeStep - 1)
                    } else if (tab == 4 && settingsPage != null && settingsPage != "overview") {
                        openAccountPage(null)
                    } else if (currentMethod != null) {
                        currentMethod = null
                        currentMethodJson = null
                        render()
                    } else if (tab != 0) {
                        navigate(if (tab == 1 || tab == 2 || tab == 5 || tab == 6) 0 else 6)
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        )
        taskOrder = getSharedPreferences("student_tools", MODE_PRIVATE).getString("taskOrder", "due") ?: "due"
        gradeOrder = getSharedPreferences("student_tools", MODE_PRIVATE).getString("gradeOrder", "name") ?: "name"
        calendarMode = getSharedPreferences("student_tools", MODE_PRIVATE).getString("calendarMode", "list") ?: "list"
        apiFavorites = HashSet(
            getSharedPreferences("api_catalog", MODE_PRIVATE).getStringSet("favorites", emptySet()) ?: emptySet()
        )

        if (bundle != null) {
            welcomeStep = bundle.getInt("welcomeStep", -1)
            settingsPage = bundle.getString("settingsPage")
            demoCampus = bundle.getString("demoCampus", "0000")
            demo = bundle.getBoolean("demo")
            demoChanged = bundle.getBoolean("demoChanged")
            tab = bundle.getInt("tab")
            filter = bundle.getString("filter", "wszystkie")
            historyQuery = bundle.getString("historyQuery", "")
            gradeQuery = bundle.getString("gradeQuery", "")
            gradeType = bundle.getString("gradeType", "all")
            gradeOrder = bundle.getString("gradeOrder", gradeOrder)
            gradeFiltersExpanded = bundle.getBoolean("gradeFiltersExpanded")
            val expandedTerms = bundle.getBundle("gradeSemesterExpansion")
            if (expandedTerms != null) {
                for (term in expandedTerms.keySet()) {
                    gradeSemesterExpansion[term] = expandedTerms.getBoolean(term)
                }
            }
            calendarDate = bundle.getString("calendarDate", "")
            calendarQuery = bundle.getString("calendarQuery", "")
            calendarMode = bundle.getString("calendarMode", calendarMode)
            demoPlanDays = bundle.getInt("demoPlanDays", 56)
            semester = bundle.getString("semester", "")
            taskFilter = bundle.getString("taskFilter", "all")
            taskQuery = bundle.getString("taskQuery", "")
            pendingCalendar = bundle.getString("pendingCalendar")
            taskOrder = bundle.getString("taskOrder", "due")
            newsQuery = bundle.getString("newsQuery", "")
            attendanceFilter = bundle.getString("attendanceFilter", "all")
            documentQuery = bundle.getString("documentQuery", "")
            apiModule = bundle.getString("apiModule", "")
            try {
                apiDrafts = JSONObject(bundle.getString("apiDrafts", "{}"))
            } catch (ignored: JSONException) {
            }
            currentMethod = bundle.getString("currentMethod")
            uploadMethod = bundle.getString("uploadMethod")
            try {
                demoTasks = JSONArray(bundle.getString("demoTasks", "[]"))
            } catch (ignored: JSONException) {
            }
            apiQuery = bundle.getString("apiQuery", "")
            apiLimit = bundle.getInt("apiLimit", 60)
            apiFavoritesOnly = bundle.getBoolean("apiFavoritesOnly")
            simTargetGpa = bundle.getDouble("simTargetGpa", 4.50)
            simRemainingEcts = bundle.getDouble("simRemainingEcts", 30.0)
        } else if (intent.getBooleanExtra("showHistory", false)) {
            tab = 1
            filter = historyFilter(intent)
        } else if (intent.hasExtra("tab")) {
            tab = intent.getIntExtra("tab", 0)
        }

        focusCurrentSchedule = bundle == null && tab == 5
        setContentView(R.layout.activity_main)
        root = findViewById(R.id.main_root)
        progress = findViewById(R.id.progress_text)
        swipeRefresh = findViewById(R.id.swipe_refresh)
        scroll = findViewById(R.id.main_scroll)
        content = findViewById(R.id.main_content)
        navigation = findViewById(R.id.bottom_navigation)
        navigation?.let { nav ->
            val indicatorHeight = max(48, (40 * resources.configuration.fontScale).toInt())
            nav.itemActiveIndicatorExpandedHeight = UiHelper.dp(this, indicatorHeight)
            nav.minimumHeight = UiHelper.dp(this, indicatorHeight + 16)
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            root.setPadding(bars.left, bars.top, bars.right, max(bars.bottom, keyboard.bottom))
            navigation?.visibility = if (insets.isVisible(WindowInsetsCompat.Type.ime()) || (welcomeStep >= 0 && !demo)) View.GONE else View.VISIBLE
            WindowInsetsCompat.CONSUMED
        }

        swipeRefresh?.setColorSchemeColors(UiHelper.GREEN, Color.rgb(20, 110, 60))
        swipeRefresh?.setOnRefreshListener {
            if (connected()) {
                sync()
            } else {
                swipeRefresh?.isRefreshing = false
                Toast.makeText(this, R.string.refresh_offline, Toast.LENGTH_SHORT).show()
            }
        }

        navigation?.setOnItemSelectedListener { item ->
            if (busy) return@setOnItemSelectedListener false
            when (item.itemId) {
                R.id.nav_item_home -> navigate(0)
                R.id.nav_item_grades -> navigate(2)
                R.id.nav_item_plan -> navigate(5)
                R.id.nav_item_history -> navigate(1)
                R.id.nav_item_more -> navigate(6)
            }
            true
        }

        (navigation as? SwipeNavigationView)?.setOnTabSwipe { direction ->
            if (busy) return@setOnTabSwipe
            val tabs = intArrayOf(0, 5, 2, 1, 6)
            val current = when (tab) {
                0 -> 0
                5 -> 1
                2 -> 2
                1 -> 3
                else -> 4
            }
            val next = current + direction
            if (next in tabs.indices) navigate(tabs[next])
        }

        navigation?.setOnItemReselectedListener { item ->
            if (!busy && item.itemId == R.id.nav_item_more && tab != 6) navigate(6)
        }

        task(
            {
                catalog = JSONArray(asset("api/index.json"))
                repository.read()
            },
            { result ->
                state = result
                val welcome = getSharedPreferences("welcome", MODE_PRIVATE)
                if (!welcome.getBoolean("seen", false)) {
                    if (state.optString("token").isNotEmpty() || state.optString("key").isNotEmpty()) {
                        welcome.edit().putBoolean("seen", true).apply()
                    } else if (!demo) {
                        welcomeStep = welcome.getInt("step", 0).coerceIn(0, 1)
                    }
                }
                ensureSchedule()
                val method = currentMethod
                if (method != null) {
                    showMethod(method)
                    return@task
                }
                render()
                showReleaseUpdatesIfRequested()
            }
        )
    }

    override fun onSaveInstanceState(out: Bundle) {
        out.putInt("welcomeStep", welcomeStep)
        out.putString("settingsPage", settingsPage)
        out.putString("demoCampus", demoCampus)
        out.putString("documentQuery", documentQuery)
        out.putString("apiModule", apiModule)
        out.putString("currentMethod", currentMethod)
        out.putString("uploadMethod", uploadMethod)
        try {
            out.putString("apiDrafts", ApiCatalog.restorableDrafts(apiDrafts).toString())
        } catch (ignored: JSONException) {
        }
        out.putBoolean("apiFavoritesOnly", apiFavoritesOnly)
        out.putString("calendarQuery", calendarQuery)
        out.putBoolean("apiFavoritesOnly", apiFavoritesOnly)
        out.putDouble("simTargetGpa", simTargetGpa)
        out.putDouble("simRemainingEcts", simRemainingEcts)
        super.onSaveInstanceState(out)
        out.putBoolean("demo", demo)
        out.putBoolean("demoChanged", demoChanged)
        out.putInt("tab", tab)
        out.putString("filter", filter)
        out.putString("semester", semester)
        out.putString("taskFilter", taskFilter)
        out.putString("taskQuery", taskQuery)
        out.putString("taskOrder", taskOrder)
        out.putString("calendarDate", calendarDate)
        out.putString("calendarMode", calendarMode)
        out.putInt("demoPlanDays", demoPlanDays)
        out.putString("pendingCalendar", pendingCalendar)
        out.putString("newsQuery", newsQuery)
        out.putString("attendanceFilter", attendanceFilter)
        out.putString("demoTasks", demoTasks.toString())
        out.putString("historyQuery", historyQuery)
        out.putString("gradeQuery", gradeQuery)
        out.putString("gradeType", gradeType)
        out.putString("gradeOrder", gradeOrder)
        out.putBoolean("gradeFiltersExpanded", gradeFiltersExpanded)
        val expandedTerms = Bundle()
        for ((key, value) in gradeSemesterExpansion) {
            expandedTerms.putBoolean(key, value)
        }
        out.putBundle("gradeSemesterExpansion", expandedTerms)
        out.putString("apiQuery", apiQuery)
        out.putInt("apiLimit", apiLimit)
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showReleaseUpdatesIfRequested()
        if (intent.getBooleanExtra("showHistory", false)) {
            filter = historyFilter(intent)
            historyQuery = ""
            navigate(1)
        } else if (intent.hasExtra("tab")) {
            navigate(intent.getIntExtra("tab", 0))
        }
    }

    private fun showReleaseUpdatesIfRequested() {
        if (!intent.getBooleanExtra(ReleaseUpdates.SHOW_UPDATES, false)) return
        intent.removeExtra(ReleaseUpdates.SHOW_UPDATES)
        ReleaseUpdatesDialog.show(this)
    }

    override fun onResume() {
        super.onResume()
        io.execute { ClassReminderReceiver.scheduleNext(applicationContext) }
        handler.removeCallbacks(refresh)
        handler.postDelayed(refresh, 30000)
        if (::content.isInitialized && !busy && tab != 4 && tab != 3 && tab != 8 && tab != 9 && tab != 10 && tab != 11 && tab != 12) {
            load()
        }
        if (tab == 4 && state.optString("requestToken").isNotEmpty() && !connected()) {
            checkClipboardForPin()
        }
        if (::content.isInitialized && !busy && tab == 4 && settingsPage == "settings.notifications") render()
    }

    override fun onPause() {
        mobileActivationDialog?.dismiss()
        mobileActivationDialog = null
        super.onPause()
        handler.removeCallbacks(refresh)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10) io.execute { ClassReminderReceiver.scheduleNext(applicationContext) }
        if (requestCode == 11) ReleaseUpdateWorker.schedule(applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(refresh)
        io.shutdown()
    }

    override fun onRetainCustomNonConfigurationInstance(): Any =
        PendingFiles(exportResponse, uploadBytes)

    private class PendingFiles(
        val export: UsosClient.Response?,
        val upload: ByteArray?
    )

    fun navigate(target: Int) {
        navigate(target, true)
    }

    fun openScheduleDate(date: String) {
        calendarDate = date
        navigate(5, false)
    }

    private fun navigate(target: Int, focusCurrent: Boolean) {
        welcomeStep = -1
        if (target != tab) settingsPage = null
        if (target != tab) {
            val focused = currentFocus
            focused?.clearFocus()
            val keyboard = getSystemService(InputMethodManager::class.java)
            keyboard?.hideSoftInputFromWindow(root.windowToken, 0)
        }
        focusCurrentSchedule = focusCurrent && target == 5 && tab != 5
        currentMethod = null
        currentMethodJson = null
        tab = target
        scroll.scrollTo(0, 0)
        render()
        if (!demo && !busy) {
            if (target == 10 && connected() && System.currentTimeMillis() - (studentServices().optJSONObject("affiliation")?.optLong("attemptAt") ?: 0L) > 24 * 60 * 60_000L) {
                refreshStudentServices("affiliation")
                return
            }
            val section = when (target) { 11 -> "news"; 13 -> "cards"; 14 -> "documents"; 8 -> "payments"; 15 -> "teaching"; else -> null }
            if (target == 11 && !connected() && CampusAffiliation.selected(state).isEmpty()) return
            if (section != null && (section == "news" || connected()) &&
                System.currentTimeMillis() - (studentServices().optJSONObject(if (section == "cards") "mcard" else section)?.optLong("attemptAt") ?: 0L) > 15 * 60_000L) refreshStudentServices(section)
        }
    }

    fun render() {
        if (isDestroyed) return
        renderedMinute = System.currentTimeMillis() / 60000
        content.removeAllViews()
        setScheduleNavigation(null)
        val gutter = max(20, (resources.configuration.screenWidthDp - 560) / 2)
        content.setPadding(
            UiHelper.dp(this, gutter),
            UiHelper.dp(this, if (tab == 5) 0 else 12),
            UiHelper.dp(this, gutter),
            UiHelper.dp(this, 24)
        )
        if (welcomeStep >= 0 && !demo && state.optString("token").isEmpty()) {
            navigation?.visibility = View.GONE
            pl.vistula.checker.ui.WelcomeScreenController.render(this, content, welcomeStep)
            return
        }
        navigation?.visibility = View.VISIBLE
        navigation?.let { nav ->
            val navMenuId = when (tab) {
                2 -> R.id.nav_item_grades
                5 -> R.id.nav_item_plan
                1 -> R.id.nav_item_history
                6, 3, 4, 7, 8, 9, 10, 11, 12, 13, 14, 15 -> R.id.nav_item_more
                else -> R.id.nav_item_home
            }
            nav.menu.findItem(navMenuId).isChecked = true
        }

        try {
            if (demo) UiHelper.note(this, content, getString(R.string.demo_banner_note), UiHelper.MUTED)

            when (tab) {
                15 -> pl.vistula.checker.ui.LecturerScreenController.render(this, content, state)
                13 -> StudentCardsController.render(
                    this,
                    content,
                    if (demo) JSONObject().put("user", "Aleksandra Nowak · DEMO") else state,
                    studentServices()
                )
                14 -> StudentDocumentsController.render(this, content, state, studentServices())
                0 -> HomeScreenController.render(this, content, display(), state, demo, demoTasks) { lessonDetails(it) }
                1 -> HistoryScreenController.render(
                    this,
                    content,
                    display(),
                    filter,
                    historyQuery,
                    demo,
                    { f ->
                        filter = f
                        render()
                    },
                    { q -> historyQuery = q },
                    { sync() }
                )
                2 -> GradesScreenController.render(
                    this,
                    content,
                    display(),
                    state,
                    demo,
                    semester,
                    gradeType,
                    gradeOrder,
                    gradeQuery,
                    gradeFiltersExpanded,
                    { exp ->
                        gradeFiltersExpanded = exp
                        render()
                    },
                    { sem ->
                        semester = sem
                        if (sem.isNotEmpty()) gradeSemesterExpansion[sem] = true
                        render()
                    },
                    { gt ->
                        gradeType = gt
                        render()
                    },
                    { go ->
                        gradeOrder = go
                        getSharedPreferences("student_tools", MODE_PRIVATE).edit().putString("gradeOrder", go).apply()
                        render()
                    },
                    { gq -> gradeQuery = gq },
                    {
                        semester = ""
                        gradeType = "all"
                        gradeQuery = ""
                        gradeOrder = "name"
                        gradeSemesterExpansion.clear()
                        getSharedPreferences("student_tools", MODE_PRIVATE).edit().putString("gradeOrder", gradeOrder).apply()
                        render()
                    },
                    { sync() },
                    object : GradesScreenController.GradesListener {
                        override fun onFilterChanged() {
                            render()
                        }

                        override fun onOpenTask(title: String) {
                            editTask(null, title)
                        }
                    }
                )
                3 -> {
                    val method = currentMethod
                    val methodJson = currentMethodJson
                    if (method != null && methodJson != null) {
                        ApiExplorerController.renderMethodDetails(
                            this,
                            content,
                            scroll,
                            methodJson,
                            apiFavorites,
                            uploadBytes,
                            {
                                currentMethod = null
                                currentMethodJson = null
                                render()
                            },
                            object : ApiExplorerController.ApiCallback {
                                override fun onExecuteRequest(
                                    metadata: JSONObject,
                                    values: Map<String, String>,
                                    method: String,
                                    upload: ByteArray?,
                                    onSuccess: Consumer<UsosClient.Response>,
                                    onFailure: Consumer<String>
                                ) {
                                    executeApiRequest(metadata, values, method, upload, onSuccess, onFailure)
                                }

                                override fun onSelectUploadFile(method: String) {
                                    uploadMethod = method
                                    startActivityForResult(
                                        Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),
                                        21
                                    )
                                }

                                override fun onSaveResponse(resp: UsosClient.Response) {
                                    saveResponseFile(resp, "usos-response")
                                }
                            }
                        )
                    } else {
                        ApiExplorerController.renderCatalog(
                            this,
                            content,
                            catalog,
                            apiQuery,
                            apiLimit,
                            apiFavoritesOnly,
                            apiFavorites,
                            { q -> apiQuery = q },
                            { lim -> apiLimit = lim },
                            { fav ->
                                apiFavoritesOnly = fav
                                apiLimit = 60
                                render()
                            },
                            { showMethod(it) }
                        )
                    }
                }
                4 -> AccountScreenController.render(
                    this,
                    content,
                    display(),
                    state,
                    demo,
                    detectedPin,
                    object : AccountScreenController.AccountListener {
                        override fun onBeginOAuth(key: String, secret: String, scopes: String) {
                            task(
                                {
                                    repository.begin(key, secret, scopes)
                                    repository.read()
                                },
                                { s ->
                                    state = s
                                    render()
                                    browser(
                                        "https://usosapps.vistula.edu.pl/services/oauth/authorize?oauth_token=" +
                                            OAuth.encode(s.getString("requestToken"))
                                    )
                                }
                            )
                        }

                        override fun onConfirmPin(pin: String) {
                            task(
                                {
                                    repository.finish(pin)
                                    repository.read()
                                },
                                { s ->
                                    state = s
                                    detectedPin = ""
                                    settingsPage = null
                                    demo = false
                                    tab = 0
                                    ensureSchedule()
                                    render()
                                    if (Build.VERSION.SDK_INT >= 33 &&
                                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
                                    }
                                    sync()
                                }
                            )
                        }

                        override fun onToggleEnabled(enabled: Boolean) {
                            task(
                                {
                                    repository.update { s -> s.put("enabled", enabled) }
                                    repository.read()
                                },
                                { s ->
                                    state = s
                                    if (enabled) SyncWorker.schedule(this@MainActivity, state.optLong("interval", 30L))
                                    else SyncWorker.cancel(this@MainActivity)
                                    render()
                                }
                            )
                        }

                        override fun onUpdateInterval(interval: Long) {
                            task(
                                {
                                    repository.update { s -> s.put("interval", interval) }
                                    repository.read()
                                },
                                { s ->
                                    state = s
                                    ensureSchedule()
                                    render()
                                }
                            )
                        }

                        override fun onDisconnect() {
                            task(
                                {
                                    SyncWorker.cancel(this@MainActivity)
                                    repository.disconnect()
                                    ClassReminderReceiver.cancel(this@MainActivity)
                                    androidx.core.app.NotificationManagerCompat.from(this@MainActivity).cancelAll()
                                    TodayScheduleWidget.updateAll(this@MainActivity)
                                    QuickStatusWidget.updateAll(this@MainActivity)
                                    repository.read()
                                },
                                { s ->
                                    state = s
                                    detectedPin = ""
                                    apiDrafts = JSONObject()
                                    uploadBytes = null
                                    uploadMethod = null
                                    exportResponse = null
                                    pendingCalendar = null
                                    gradeSemesterExpansion.clear()
                                    semester = ""
                                    demo = false
                                    render()
                                }
                            )
                        }

                        override fun onStartDemo() {
                            gradeSemesterExpansion.clear()
                            semester = ""
                            demo = true
                            tab = 0
                            render()
                        }

                        override fun onEndDemo() {
                            gradeSemesterExpansion.clear()
                            semester = ""
                            demo = false
                            render()
                        }

                        override fun onRequestNotifications() {
                            requestNotifications()
                        }
                    }
                )
                5 -> {
                    ScheduleScreenController.render(
                        this,
                        content,
                        display(),
                        state,
                        demo,
                        calendarDate,
                        calendarMode,
                        demoPlanDays,
                        { dVal ->
                            calendarDate = dVal
                            render()
                        },
                        { mVal ->
                            calendarMode = mVal
                            render()
                        },
                        { sync() },
                        object : ScheduleScreenController.ScheduleListener {
                            override fun onOpenTask(title: String) {
                                editTask(null, title)
                            }

                            override fun onAbsenceRecorded() {
                                render()
                            }
                        }
                    )
                    if (focusCurrentSchedule && content.findViewById<View?>(R.id.schedule_swipe_content) != null) {
                        focusCurrentSchedule = false
                        val ongoing = content.findViewById<View?>(R.id.schedule_current_lesson)
                        if (ongoing != null) {
                            OneShotPreDrawListener.add(scroll) {
                                if (tab != 5 || content.findViewById<View?>(R.id.schedule_current_lesson) !== ongoing) return@add
                                val cardPosition = IntArray(2)
                                val viewportPosition = IntArray(2)
                                ongoing.getLocationOnScreen(cardPosition)
                                scroll.getLocationOnScreen(viewportPosition)
                                scroll.scrollTo(
                                    0,
                                    max(0, scroll.scrollY + cardPosition[1] - viewportPosition[1] - UiHelper.dp(this, 8))
                                )
                            }
                        }
                    }
                }
                6 -> MoreScreenController.render(this, content)
                7 -> TasksScreenController.render(
                    this,
                    content,
                    personalTasks(),
                    taskFilter,
                    taskOrder,
                    taskQuery,
                    { tf ->
                        taskFilter = tf
                        render()
                    },
                    { to ->
                        taskOrder = to
                        getSharedPreferences("student_tools", MODE_PRIVATE).edit().putString("taskOrder", to).apply()
                        render()
                    },
                    { tq -> taskQuery = tq },
                    taskCallback()
                )
                8 -> PaymentsScreenController.render(this, content, display(), state, connected()) { refreshStudentServices("payments") }
                9 -> GpaSimulatorController.render(
                    this,
                    content,
                    display(),
                    simTargetGpa,
                    simRemainingEcts,
                    { target ->
                        simTargetGpa = target
                        render()
                    },
                    { ects ->
                        simRemainingEcts = ects
                        render()
                    }
                )
                10 -> CampusGuideController.render(this, content)
                11 -> NewsScreenController.render(
                    this,
                    content,
                    display(),
                    newsQuery,
                    { q -> newsQuery = q },
                    { refreshStudentServices("news") },
                    studentServices().optJSONObject("news") ?: JSONObject()
                )
                12 -> AttendanceScreenController.render(
                    this,
                    content,
                    display(),
                    attendanceFilter,
                    { f ->
                        attendanceFilter = f
                        render()
                    },
                    { render() }
                )
            }
        } catch (e: Exception) {
            UiHelper.note(this, content, ApiErrorUi.message(this, e), Color.rgb(167, 49, 49))
        }
        updateBusy(busy)
    }

    @Throws(JSONException::class)
    fun display(): JSONObject = if (demo) Demo.state(demoChanged, demoPlanDays) else state

    fun connected(): Boolean = state.optString("token").isNotEmpty()

    fun isDemo(): Boolean = demo

    fun showWelcome(step: Int = 0) {
        welcomeStep = step.coerceIn(0, 1)
        getSharedPreferences("welcome", MODE_PRIVATE).edit().putInt("step", welcomeStep).apply()
        render()
        scroll.scrollTo(0, 0)
    }

    fun finishWelcome(startDemo: Boolean, configure: Boolean) {
        getSharedPreferences("welcome", MODE_PRIVATE).edit().putBoolean("seen", true).remove("step").apply()
        welcomeStep = -1
        if (startDemo) {
            demo = true
            navigate(0)
        } else if (configure) {
            demo = false
            tab = 4
            settingsPage = "settings.connection"
            render()
            scroll.scrollTo(0, 0)
        } else {
            navigate(0)
        }
    }

    fun attendancePreferences(): SharedPreferences =
        getSharedPreferences(if (demo) "attendance_tracker_demo" else "attendance_tracker", MODE_PRIVATE)

    fun setDemo(demo: Boolean) {
        this.demo = demo
        if (demo) welcomeStep = -1
        render()
    }

    fun getCalendarDate(): String = calendarDate
    fun setCalendarDate(date: String) {
        this.calendarDate = date
    }

    fun shouldFocusCurrentSchedule(): Boolean = focusCurrentSchedule

    fun setScheduleNavigation(action: IntConsumer?) {
        if (scroll is ScheduleScrollView) {
            (scroll as ScheduleScrollView).setScheduleNavigation(action)
        }
    }

    fun getCalendarMode(): String = calendarMode
    fun setCalendarMode(mode: String) {
        this.calendarMode = mode
    }

    fun getDocumentQuery(): String = documentQuery
    fun getApiModule(): String = apiModule
    fun setApiModule(module: String) {
        apiModule = module
        apiLimit = 60
        render()
    }

    fun setDocumentQuery(query: String) {
        documentQuery = query
    }

    fun serviceAccountState(): JSONObject = if (demo) JSONObject().put("campusOverride", demoCampus) else state

    @Throws(Exception::class)
    private fun studentServices(): JSONObject {
        if (demo) return DemoStudentServices.data()
        return state.optJSONObject("studentServices") ?: JSONObject()
    }

    fun refreshStudentServices(section: String) {
        if (demo) {
            render()
            return
        }
        val publicNews = section == "news" && CampusAffiliation.canShowPublicNews(state)
        if (!publicNews) {
            if (!connected() || state.optBoolean("authorizationError")) {
                error(getString(if (state.optBoolean("authorizationError")) R.string.account_session_expired else R.string.services_connect_first))
                return
            }
            if (state.optLong("retryAt") > System.currentTimeMillis()) {
                error(getString(R.string.services_retry_at, UiHelper.timestamp(state.optLong("retryAt"))))
                return
            }
            val scope = when (section) { "documents" -> "adm_documents"; "remittances" -> "payments"; else -> section }
            if (section !in setOf("news", "usosNews", "affiliation", "teaching") && !StudentServices.hasScope(state.optString("scopes"), scope)) {
                error(getString(R.string.services_scope_required, scope))
                return
            }
        }
        task(
            {
                if (section in setOf("news", "usosNews") && connected() && !state.optBoolean("authorizationError") &&
                    repository.read().optJSONObject("studentServices")?.optJSONObject("affiliation") == null) {
                    StudentServiceLoader.refresh(repository, "affiliation")
                }
                StudentServiceLoader.refresh(repository, section)
                repository.read()
            },
            { result ->
                state = result
                render()
                if (section == "affiliation" && tab == 11 && CampusAffiliation.selected(state).isNotEmpty()) refreshStudentServices("news")
            }
        )
    }

    fun accountPage(): String? = settingsPage
    fun rememberAccountPage(page: String) { settingsPage = page }

    fun openAccountPage(page: String?) {
        settingsPage = page ?: "overview"
        render()
        scroll.scrollTo(0, 0)
    }

    fun selectCampus(campus: String) {
        if (campus.isNotEmpty() && !CampusAffiliation.campuses.containsKey(campus)) return
        if (demo) { demoCampus = campus.ifEmpty { "0000" }; render(); return }
        val session = repository.editSession(state)
        task({
            repository.update(session) { latest ->
                if (campus.isEmpty()) latest.remove("campusOverride") else latest.put("campusOverride", campus)
                latest.optJSONObject("studentServices")?.remove("news")
            }
            val credentials = repository.read()
            if (credentials.optString("token").isNotEmpty() && !credentials.optBoolean("authorizationError") &&
                credentials.optLong("retryAt") <= System.currentTimeMillis()) StudentServiceLoader.refresh(repository, "affiliation")
            repository.read()
        }, { latest ->
            state = latest
            render()
            if (tab == 11 && CampusAffiliation.selected(state).isNotEmpty()) refreshStudentServices("news")
        })
    }

    /** Actions are single-shot and may only deliver a result to the account that initiated them. */
    fun <T> studentAction(scope: String, operation: (UsosClient, JSONObject) -> T, done: (T) -> Unit) {
        if (!liveService(scope)) return
        task(
            {
                val credentials = serviceCredentials(scope)
                val result = try {
                    val guarded = UsosClient(credentials, {}, { address ->
                        check(Repository.sameAccount(credentials, serviceCredentials(scope))) {
                            getString(R.string.service_action_unavailable)
                        }
                        java.net.URI(address).toURL().openConnection() as java.net.HttpURLConnection
                    })
                    operation(guarded, credentials)
                } catch (failure: Exception) {
                    if (failure is UsosClient.ApiException) {
                        repository.update { latest ->
                            if (Repository.sameAccount(credentials, latest)) {
                                if (failure.retryAt > latest.optLong("retryAt")) latest.put("retryAt", failure.retryAt)
                                if (failure.authorization) latest.put("authorizationError", true).put("enabled", false)
                            }
                        }
                    }
                    if (failure is IOException && failure !is UsosClient.ApiException) {
                        throw IllegalStateException(getString(R.string.service_action_uncertain))
                    }
                    if (failure is IllegalStateException) throw IllegalStateException(getString(R.string.service_action_unavailable))
                    throw failure
                }
                check(Repository.sameAccount(credentials, repository.read())) { getString(R.string.service_action_unavailable) }
                result
            },
            { done(it) }
        )
    }

    fun openApiModule(module: String) {
        apiModule = module
        apiQuery = ""
        apiLimit = 60
        apiFavoritesOnly = false
        navigate(3)
    }

    fun openApiMethod(method: String, parameters: Map<String, String>) {
        try {
            apiDrafts.put(method, JSONObject(parameters))
        } catch (e: JSONException) {
            error(ApiErrorUi.message(this, e))
            return
        }
        tab = 3
        showMethod(method)
    }

    fun apiDraft(method: String): JSONObject {
        var draft = apiDrafts.optJSONObject(method)
        if (draft == null) {
            draft = JSONObject()
            try {
                apiDrafts.put(method, draft)
            } catch (ignored: JSONException) {
            }
        }
        return draft
    }

    fun apiUpload(method: String): ByteArray? = if (method == uploadMethod) uploadBytes else null

    private fun liveService(scope: String): Boolean {
        if (demo) {
            Toast.makeText(this, R.string.services_demo_action, Toast.LENGTH_LONG).show()
            return false
        }
        if (!connected()) {
            Toast.makeText(this, R.string.services_connect_first, Toast.LENGTH_LONG).show()
            return false
        }
        if (!StudentServices.hasScope(state.optString("scopes"), scope)) {
            error(getString(R.string.services_scope_required, scope))
            return false
        }
        return true
    }

    @Throws(Exception::class)
    private fun serviceCredentials(scope: String): JSONObject {
        val credentials = repository.read()
        if (credentials.optString("token").isEmpty() || credentials.optBoolean("authorizationError")) {
            throw IllegalStateException(getString(R.string.services_connect_first))
        }
        if (!StudentServices.hasScope(credentials.optString("scopes"), scope)) {
            throw IllegalStateException(getString(R.string.services_scope_required, scope))
        }
        if (credentials.optLong("retryAt") > System.currentTimeMillis()) {
            throw IllegalStateException(getString(R.string.error_rate_limit))
        }
        return credentials
    }

    fun mobileCardAction(order: Boolean, id: String) {
        if (!liveService("cards")) return
        MaterialAlertDialogBuilder(this)
            .setTitle(if (order) R.string.cards_order else R.string.cards_revoke)
            .setMessage(if (order) getString(R.string.cards_order_confirm) else getString(R.string.cards_revoke_confirm, id))
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(if (order) R.string.cards_order else R.string.cards_revoke) { _, _ ->
                task(
                    {
                        val credentials = serviceCredentials("cards")
                        val client = UsosClient(credentials)
                        pl.vistula.checker.MobileCardActions.perform(client, credentials, order, id) {
                            check(SharedRepository.sameAccount(credentials, repository.read())) {
                                getString(R.string.service_action_unavailable)
                            }
                        }
                        StudentServiceLoader.refresh(repository, "cards")
                        repository.read()
                    },
                    { result ->
                        state = result
                        render()
                        Toast.makeText(this, R.string.cards_action_success, Toast.LENGTH_LONG).show()
                    }
                )
            }
            .show()
    }

    fun showMobileActivation(id: String) {
        if (!liveService("cards")) return
        task({
            val credentials = serviceCredentials("cards")
            val data = MobileCardActions.activation(UsosClient(credentials), id)
            Pair(credentials, data)
        }, { result ->
            if (!SharedRepository.sameAccount(result.first, repository.read()) ||
                !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return@task
            val data = result.second
            mobileActivationDialog?.dismiss()
            val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(R.string.cards_activation_title)
                .setMessage(getString(R.string.cards_activation_data,
                    StudentServices.text(data, "token"), StudentServices.text(data, "distribution_pass")))
                .setPositiveButton(R.string.btn_close, null).create()
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialog.setOnDismissListener {
                dialog.findViewById<TextView>(android.R.id.message)?.text = ""
                mobileActivationDialog = null
            }
            mobileActivationDialog = dialog
            dialog.show()
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialog.findViewById<TextView>(android.R.id.message)?.setTextIsSelectable(true)
        })
    }

    fun downloadStudentDocument(endpoint: String, argument: String, id: String, name: String) {
        if (!liveService("adm_documents")) return
        if (!setOf("services/blobbox/get", "services/adm_documents/get_upo", "services/adm_documents/get_upd").contains(endpoint)) return
        task(
            {
                val credentials = serviceCredentials("adm_documents")
                UsosClient(credentials).requestData(
                    "GET",
                    endpoint,
                    mapOf(argument to id),
                    true,
                    credentials.optString("token"),
                    credentials.optString("tokenSecret"),
                    null,
                    null
                )
            },
            { response -> saveResponseFile(response, name) }
        )
    }

    private fun saveResponseFile(response: UsosClient.Response, name: String) {
        exportResponse = response
        val extension = when {
            response.mime.contains("json") -> ".json"
            response.mime.contains("pdf") -> ".pdf"
            response.mime.contains("xml") -> ".xml"
            response.mime.contains("calendar") -> ".ics"
            response.binary() -> ".bin"
            else -> ".txt"
        }
        var filename = name.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_")
        if (filename.length > 100) filename = filename.substring(0, 100)
        if (filename.trim().isEmpty()) filename = "usos-document"
        try {
            startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .setType(response.mime)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_TITLE, filename + extension),
                22
            )
        } catch (e: ActivityNotFoundException) {
            exportResponse = null
            error(getString(R.string.toast_export_calendar_error))
        }
    }

    fun getCalendarQuery(): String = calendarQuery
    fun setCalendarQuery(query: String) {
        calendarQuery = query
    }

    fun saveCalendar(ics: String) {
        pendingCalendar = ics
        try {
            startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .setType("text/calendar")
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_TITLE, "plan_zajec_vistula.ics"),
                23
            )
        } catch (e: ActivityNotFoundException) {
            pendingCalendar = null
            Toast.makeText(this, R.string.toast_export_calendar_error, Toast.LENGTH_SHORT).show()
        }
    }

    fun personalTasks(): JSONArray =
        if (demo) demoTasks else (state.optJSONArray("tasks") ?: JSONArray())

    fun openTasks(selectedFilter: String) {
        taskFilter = selectedFilter
        taskQuery = ""
        navigate(7)
    }

    fun editTask(existing: JSONObject?, suggestedTitle: String?) {
        TasksScreenController.showTaskDialog(
            this,
            existing,
            suggestedTitle ?: "",
            personalTasks().length(),
            taskCallback()
        )
    }

    private fun taskCallback(): TasksScreenController.TaskCallback {
        val session = repository.editSession(state)
        val wasDemo = demo
        return object : TasksScreenController.TaskCallback {
            override fun onSaveTask(t: JSONObject) {
                if (demo != wasDemo) return
                savePersonalTask(t, session)
            }

            override fun onMutateTask(id: String, remove: Boolean) {
                if (demo != wasDemo) return
                mutateTasks(id, remove, session)
            }
        }
    }

    fun savePersonalTask(entry: JSONObject) {
        savePersonalTask(entry, repository.editSession(state))
    }

    private fun savePersonalTask(entry: JSONObject, session: Repository.EditSession) {
        if (demo) {
            try {
                demoTasks = TaskTools.save(demoTasks, entry)
                render()
            } catch (e: Exception) {
                error(ApiErrorUi.message(this, e))
            }
        } else {
            task(
                {
                    repository.update(session) { s -> s.put("tasks", TaskTools.save(s.optJSONArray("tasks"), entry)) }
                    repository.read()
                },
                { result ->
                    state = result
                    render()
                }
            )
        }
    }

    fun mutateTasks(id: String, remove: Boolean) {
        mutateTasks(id, remove, repository.editSession(state))
    }

    private fun mutateTasks(id: String, remove: Boolean, session: Repository.EditSession) {
        var deleted: JSONObject? = null
        if (remove) {
            val current = personalTasks()
            for (i in 0 until current.length()) {
                val row = current.optJSONObject(i)
                if (row != null && row.optString("id") == id) {
                    deleted = row
                    break
                }
            }
        }
        val undoEntry = deleted
        val wasDemo = demo

        if (demo) {
            try {
                demoTasks = changedTasks(demoTasks, id, remove)
                render()
                if (undoEntry != null) offerTaskUndo(undoEntry, wasDemo, session)
            } catch (e: JSONException) {
                error(ApiErrorUi.message(this, e))
            }
        } else {
            task(
                {
                    repository.update(session) { s -> s.put("tasks", changedTasks(s.optJSONArray("tasks"), id, remove)) }
                    repository.read()
                },
                { result ->
                    state = result
                    render()
                    if (undoEntry != null) offerTaskUndo(undoEntry, wasDemo, session)
                }
            )
        }
    }

    private fun offerTaskUndo(entry: JSONObject, wasDemo: Boolean, session: Repository.EditSession) {
        Snackbar.make(root, R.string.task_deleted, 10000).setAction(R.string.undo) {
            if (busy || demo != wasDemo) return@setAction
            if (wasDemo) {
                try {
                    demoTasks = TaskTools.restore(demoTasks, entry)
                    render()
                } catch (e: Exception) {
                    error(ApiErrorUi.message(this, e))
                }
            } else {
                task(
                    {
                        repository.update(session) { s ->
                            s.put("tasks", TaskTools.restore(s.optJSONArray("tasks"), entry))
                        }
                        repository.read()
                    },
                    { result ->
                        state = result
                        render()
                    }
                )
            }
        }.show()
    }

    @Throws(JSONException::class)
    private fun changedTasks(source: JSONArray?, id: String, remove: Boolean): JSONArray {
        val result = JSONArray()
        if (source != null) {
            for (i in 0 until source.length()) {
                val entry = JSONObject(source.getJSONObject(i).toString())
                if (entry.getString("id") == id) {
                    if (remove) continue
                    entry.put("done", !entry.optBoolean("done"))
                }
                result.put(entry)
            }
        }
        return result
    }

    fun lessonDetails(lesson: LessonTime) {
        ScheduleScreenController.showLessonDetails(
            this,
            lesson,
            object : ScheduleScreenController.ScheduleListener {
                override fun onOpenTask(title: String) {
                    editTask(null, title)
                }

                override fun onAbsenceRecorded() {
                    render()
                }
            }
        )
    }

    fun sync() {
        if (demo) {
            demoChanged = true
            tab = 1
            render()
            TodayScheduleWidget.updateAll(this)
            QuickStatusWidget.updateAll(this)
        } else {
            val position = scroll.scrollY
            val first = state.optJSONObject("snapshot") == null
            task(
                {
                    val changes = repository.syncAndNotify(true) { message ->
                        runOnUiThread {
                            if (!isDestroyed) progress.text = pl.vistula.checker.core.SyncProgress.localized(message, Language.currentLocale(this).language)
                        }
                    }
                    val latest = repository.read()
                    val lastAffiliation = latest.optJSONObject("studentServices")?.optJSONObject("affiliation")?.optLong("attemptAt") ?: 0L
                    if (System.currentTimeMillis() - lastAffiliation > 24 * 60 * 60_000L) {
                        try { StudentServiceLoader.refresh(repository, "affiliation") } catch (_: Exception) { /* Optional campus data must not discard a valid grade sync. */ }
                    }
                    ClassReminderReceiver.scheduleNext(this)
                    TodayScheduleWidget.updateAll(this)
                    QuickStatusWidget.updateAll(this)
                    SyncResult(repository.read(), changes.size, first)
                },
                { result ->
                    state = result.state
                    render()
                    scroll.post { scroll.scrollTo(0, position) }
                    val message = when {
                        result.first -> getString(R.string.first_sync_saved)
                        result.changes == 0 -> getString(R.string.sync_result_up_to_date)
                        else -> getString(R.string.sync_result_changes, result.changes)
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun showMethod(name: String) {
        task(
            { JSONObject(asset("api/" + name.replace("/", "__") + ".json")) },
            { json ->
                currentMethod = name
                currentMethodJson = json
                render()
            }
        )
    }

    private fun executeApiRequest(
        metadata: JSONObject,
        values: Map<String, String>,
        method: String,
        upload: ByteArray?,
        onSuccess: Consumer<UsosClient.Response>,
        onFailure: Consumer<String>
    ) {
        task(
            {
                val expected = repository.read()
                val wasDemo = demo
                var credentials = JSONObject(expected.toString())
                if (demo) credentials = JSONObject()
                check(demo || expected.optLong("retryAt") <= System.currentTimeMillis()) { getString(R.string.error_rate_limit) }
                if (credentials.optString("key").isEmpty() && !BuildConfig.USOS_KEY.isNullOrEmpty()) {
                    credentials.put("key", BuildConfig.USOS_KEY.trim())
                    credentials.put("secret", if (BuildConfig.USOS_SECRET != null) BuildConfig.USOS_SECRET.trim() else "")
                }
                val response = try {
                    UsosClient(credentials).explore(metadata, values, method, upload, "upload.bin")
                } catch (failure: UsosClient.ApiException) {
                    if (!wasDemo) repository.update { latest ->
                        if (Repository.sameAccount(expected, latest)) {
                            if (failure.retryAt > latest.optLong("retryAt")) latest.put("retryAt", failure.retryAt)
                            if (failure.authorization) latest.put("authorizationError", true).put("enabled", false)
                        }
                    }
                    throw failure
                }
                val latest = repository.read()
                check(demo == wasDemo && listOf("key", "secret", "token", "tokenSecret", "accountSession").all {
                    expected.optString(it) == latest.optString(it)
                }) { getString(R.string.service_action_unavailable) }
                response
            },
            { if (tab == 3 && currentMethod == metadata.optString("name")) onSuccess.accept(it) },
            onFailure
        )
    }

    private fun ensureSchedule() {
        if (state.optBoolean("enabled") && connected()) {
            SyncWorker.schedule(this, state.optLong("interval", 30))
        }
    }

    private fun load() {
        if (loadingState || busy || isDestroyed) return
        loadingState = true
        io.execute {
            try {
                val latest = repository.read()
                runOnUiThread {
                    loadingState = false
                    if (isDestroyed || busy) return@runOnUiThread
                    val clockChanged = (tab == 0 || tab == 5) && renderedMinute != System.currentTimeMillis() / 60000
                    if (state.toString() != latest.toString() || clockChanged) {
                        val position = scroll.scrollY
                        state = latest
                        render()
                        scroll.post { scroll.scrollTo(0, position) }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { loadingState = false }
            }
        }
    }

    private fun interface Job<T> {
        @Throws(Exception::class)
        fun run(): T
    }

    private fun interface Done<T> {
        @Throws(Exception::class)
        fun accept(value: T)
    }

    private fun <T> task(job: Job<T>, done: Done<T>) {
        task(job, done, Consumer {})
    }

    private fun <T> task(job: Job<T>, done: Done<T>, failed: Consumer<String>) {
        if (busy || isDestroyed) return
        busy = true
        updateBusy(true)
        io.execute {
            try {
                val result = job.run()
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    busy = false
                    updateBusy(false)
                    try {
                        done.accept(result)
                    } catch (e: Exception) {
                        error(ApiErrorUi.message(this, e))
                    }
                }
            } catch (e: Exception) {
                val latest: JSONObject = try {
                    repository.read()
                } catch (ignored: Exception) {
                    state
                }
                val recovered = latest
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    busy = false
                    state = recovered
                    updateBusy(false)
                    if (tab == 0 || tab == 1 || tab == 2 || tab == 5 || tab == 7) render()
                    failed.accept(ApiErrorUi.message(this, e))
                    error(ApiErrorUi.message(this, e))
                }
            }
        }
    }

    private fun updateBusy(value: Boolean) {
        setButtons(root, !value)
        navigation?.let { nav ->
            for (i in 0 until nav.menu.size()) {
                nav.menu.getItem(i).isEnabled = !value
            }
        }
        if (value) progress.setText(R.string.loading)
        progress.visibility = if (value) View.VISIBLE else View.GONE
        swipeRefresh?.isRefreshing = value
    }

    private fun setButtons(v: View, enabled: Boolean) {
        if (v is Button || v is CompoundButton || v is Spinner || v is EditText || v.isClickable) {
            if (!enabled) {
                if (v.tag !is Boolean) v.tag = v.isEnabled
                v.isEnabled = false
            } else if (v.tag is Boolean) {
                v.isEnabled = v.tag as Boolean
                v.tag = null
            }
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                setButtons(v.getChildAt(i), enabled)
            }
        }
    }

    private fun checkClipboardForPin() {
        try {
            val cb = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
            if (cb != null && cb.hasPrimaryClip() && cb.primaryClip != null && cb.primaryClip!!.itemCount > 0) {
                val item = cb.primaryClip!!.getItemAt(0)
                if (item != null && item.text != null) {
                    val clip = item.text.toString().trim().replace(Regex("\\s+"), "")
                    if (clip.matches(Regex("^[a-zA-Z0-9]{6,16}$")) && clip != detectedPin) {
                        detectedPin = clip
                        Toast.makeText(this, R.string.pin_detected_clipboard, Toast.LENGTH_LONG).show()
                        render()
                    }
                }
            }
        } catch (ignored: Exception) {
        }
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        } else {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        }
    }

    fun browser(address: String) {
        UiHelper.browser(this, address)
    }

    private fun error(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.error_dialog_title)
            .setMessage(message)
            .setPositiveButton(R.string.btn_ok, null)
            .setNeutralButton(R.string.error_dialog_copy) { _, _ ->
                val clipboard = getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(UiHelper.privateClip(getString(R.string.error_clipboard_label), message))
            }
            .show()
    }

    @Throws(IOException::class)
    private fun asset(path: String): String {
        assets.open(path).use { input ->
            ByteArrayOutputStream().use { bytes ->
                val buffer = ByteArray(8192)
                var count: Int
                while (input.read(buffer).also { count = it } != -1) {
                    bytes.write(buffer, 0, count)
                }
                return bytes.toString("UTF-8")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 23 && resultCode != RESULT_OK) pendingCalendar = null
        if (requestCode == 22 && resultCode != RESULT_OK) exportResponse = null
        val uri = data?.data
        if (resultCode == -1 && uri != null) {
            if ((requestCode == 21 || requestCode == 22 || requestCode == 23) &&
                ("content" != uri.scheme || uri.authority.isNullOrEmpty())
            ) {
                if (requestCode == 22) exportResponse = null
                if (requestCode == 23) pendingCalendar = null
                error(getString(R.string.error_invalid_document_uri))
                return
            }
            if (requestCode == 21 && currentMethod != null && currentMethod == uploadMethod) {
                task(
                    {
                        contentResolver.openInputStream(uri).use { input ->
                            ByteArrayOutputStream().use { bytes ->
                                if (input == null) throw IOException(getString(R.string.error_cant_open_file))
                                val buffer = ByteArray(8192)
                                var count: Int
                                while (input.read(buffer).also { count = it } != -1) {
                                    if (bytes.size() + count > 8388608) {
                                        throw IllegalArgumentException(getString(R.string.error_file_too_large))
                                    }
                                    bytes.write(buffer, 0, count)
                                }
                                bytes.toByteArray()
                            }
                        }
                    },
                    { bytesx ->
                        uploadBytes = bytesx
                        render()
                        Toast.makeText(this, getString(R.string.selected_upload, bytesx.size), Toast.LENGTH_SHORT).show()
                    }
                )
            } else if (requestCode == 23 && pendingCalendar != null) {
                val bytes = pendingCalendar!!.toByteArray(StandardCharsets.UTF_8)
                pendingCalendar = null
                task(
                    {
                        contentResolver.openOutputStream(uri, "wt").use { output ->
                            if (output == null) throw IOException(getString(R.string.error_cant_open_file))
                            output.write(bytes)
                        }
                        true
                    },
                    { Toast.makeText(this, R.string.schedule_calendar_saved, Toast.LENGTH_SHORT).show() }
                )
            } else if (requestCode == 22 && exportResponse != null) {
                val bytes = exportResponse!!.bytes
                exportResponse = null
                task(
                    {
                        contentResolver.openOutputStream(uri, "wt").use { output ->
                            if (output == null) throw IOException()
                            output.write(bytes)
                        }
                        true
                    },
                    { Toast.makeText(this, R.string.toast_api_response_saved, Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }

    private class SyncResult(
        val state: JSONObject,
        val changes: Int,
        val first: Boolean
    )

    companion object {
        private fun historyFilter(intent: Intent): String {
            val kind = intent.getStringExtra("historyKind")
            return if ("ocena" == kind || "plan" == kind) kind else "wszystkie"
        }
    }
}

