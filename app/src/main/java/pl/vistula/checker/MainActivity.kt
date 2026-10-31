package pl.vistula.checker

import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.core.view.get
import androidx.core.view.size
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
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
import pl.vistula.checker.core.AccountRole
import pl.vistula.checker.core.ApiCatalog
import pl.vistula.checker.core.CampusAffiliation
import pl.vistula.checker.core.LessonTime
import pl.vistula.checker.core.OAuth
import pl.vistula.checker.core.StudentServices
import pl.vistula.checker.core.TaskTools
import pl.vistula.checker.ui.AccountScreenController
import pl.vistula.checker.ui.ApiErrorUi
import pl.vistula.checker.ui.ApiExplorerController
import pl.vistula.checker.ui.Tabs
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
    private val history = pl.vistula.checker.ui.HistoryUiState()
    private val grades = pl.vistula.checker.ui.GradesUiState()
    fun gradeSemesterExpansion(): MutableMap<String, Boolean> = grades.semesterExpansion

    private val tasks = pl.vistula.checker.ui.TasksUiState()
    private var pendingCalendar: String? = null
    private val schedule = pl.vistula.checker.ui.ScheduleUiState()
    private var newsQuery: String = ""
    private var attendanceFilter: String = "all"
    private var documentQuery: String = ""
    private var demoPlanDays: Int = 56
    private var renderedMinute: Long = 0
    private var loadingState: Boolean = false
    private var settingsPage: String? = null
    private var demoCampus: String = "0000"

    // API catalog state
    private val api = pl.vistula.checker.ui.ApiExplorerState()
    private lateinit var pendingFiles: PendingFilesModel
    private var uploadBytes: ByteArray?
        get() = pendingFiles.upload
        set(value) { pendingFiles.upload = value }
    private var exportResponse: UsosClient.Response?
        get() = pendingFiles.export
        set(value) { pendingFiles.export = value }
    private val selectUpload = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        onDocumentResult(21, result.resultCode, result.data)
    }
    private val createExport = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        onDocumentResult(22, result.resultCode, result.data)
    }
    private val createCalendar = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        onDocumentResult(23, result.resultCode, result.data)
    }

    private lateinit var repository: Repository
    private val handler: Handler = Handler(Looper.getMainLooper())
    private val refresh: Runnable = object : Runnable {
        override fun run() {
            if (!busy &&
                api.currentMethod == null &&
                tab != Tabs.ACCOUNT && tab != Tabs.API && tab != Tabs.PAYMENTS && tab != Tabs.GPA_SIMULATOR && tab != Tabs.CAMPUS_GUIDE && tab != Tabs.NEWS && tab != Tabs.ATTENDANCE &&
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
        pendingFiles = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == PendingFilesModel::class.java)
                return PendingFilesModel() as T
            }
        })[PendingFilesModel::class.java]
        repository = Repository(this)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (busy) return
                    if (welcomeStep > 0) {
                        showWelcome(welcomeStep - 1)
                    } else if (tab == Tabs.ACCOUNT && settingsPage != null && settingsPage != "overview") {
                        openAccountPage(null)
                    } else if (api.currentMethod != null) {
                        api.closeMethod()
                        render()
                    } else if (tab != Tabs.HOME) {
                        navigate(if (tab == Tabs.HISTORY || tab == Tabs.GRADES || tab == Tabs.SCHEDULE || tab == Tabs.MORE) 0 else 6)
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        )
        tasks.order = getSharedPreferences("student_tools", MODE_PRIVATE).getString("taskOrder", "due") ?: "due"
        grades.order = getSharedPreferences("student_tools", MODE_PRIVATE).getString("gradeOrder", "name") ?: "name"
        schedule.mode = getSharedPreferences("student_tools", MODE_PRIVATE).getString("calendarMode", "list") ?: "list"
        api.favorites = HashSet(
            getSharedPreferences("api_catalog", MODE_PRIVATE).getStringSet("favorites", emptySet()) ?: emptySet()
        )

        if (bundle != null) {
            welcomeStep = bundle.getInt("welcomeStep", -1)
            settingsPage = bundle.getString("settingsPage")
            demoCampus = bundle.getString("demoCampus", "0000")
            demo = bundle.getBoolean("demo")
            demoChanged = bundle.getBoolean("demoChanged")
            tab = bundle.getInt("tab")
            history.restore(bundle)
            grades.restore(bundle)
            schedule.restore(bundle)
            demoPlanDays = bundle.getInt("demoPlanDays", 56)
            tasks.restore(bundle)
            pendingCalendar = bundle.getString("pendingCalendar")
            newsQuery = bundle.getString("newsQuery", "")
            attendanceFilter = bundle.getString("attendanceFilter", "all")
            documentQuery = bundle.getString("documentQuery", "")
            api.restore(bundle)
            try {
                demoTasks = JSONArray(bundle.getString("demoTasks", "[]"))
            } catch (ignored: JSONException) {
            }
            simTargetGpa = bundle.getDouble("simTargetGpa", 4.50)
            simRemainingEcts = bundle.getDouble("simRemainingEcts", 30.0)
        } else if (intent.getBooleanExtra("showHistory", false)) {
            tab = 1
            history.filter = historyFilter(intent)
        } else if (intent.hasExtra("tab")) {
            tab = Tabs.fromExternal(intent.getIntExtra("tab", 0))
        }

        schedule.focusCurrent = bundle == null && tab == Tabs.SCHEDULE
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
            run {
                // Measure translated labels, rather than assuming a font-scale threshold fits every phone.
                val parent = nav.parent as LinearLayout
                val position = parent.indexOfChild(nav)
                val outerParams = nav.layoutParams
                parent.removeView(nav)
                val viewport = HorizontalScrollView(this).apply {
                    isHorizontalScrollBarEnabled = false
                    isFillViewport = true
                    background = nav.background
                    clipToOutline = true
                    addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                        (view.background?.mutate() as? android.graphics.drawable.GradientDrawable)?.cornerRadius = view.height / 2f
                    }
                }
                nav.background = null
                nav.setLabelMaxLines(1)
                viewport.addView(nav, android.widget.FrameLayout.LayoutParams(-1, -2))
                parent.addView(viewport, position, outerParams)
                root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
                    }
                    val required = (nav as? SwipeNavigationView)?.requiredItemWidth()
                        ?: ((0 until nav.menu.size).maxOf { paint.measureText(nav.menu[it].title.toString()) }.toInt() + UiHelper.dp(this, 24))
                    val available = (root.width - root.paddingLeft - root.paddingRight - UiHelper.dp(this, 24)).coerceAtLeast(0)
                    val viewportWidth = minOf(available, UiHelper.dp(this, 560))
                    val params = viewport.layoutParams as LinearLayout.LayoutParams
                    if (params.width != viewportWidth) {
                        params.width = viewportWidth; params.gravity = android.view.Gravity.CENTER_HORIZONTAL
                        viewport.layoutParams = params
                    }
                    val width = max(viewportWidth, max(UiHelper.dp(this, 48), required) * nav.menu.size)
                    if (nav.layoutParams.width != width) {
                        nav.minimumWidth = width
                        nav.layoutParams = nav.layoutParams.apply { this.width = width }
                    }
                    (nav as? SwipeNavigationView)?.setTabScrolling(width > viewportWidth)
                }
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            root.setPadding(bars.left, bars.top, bars.right, max(bars.bottom, keyboard.bottom))
            setNavigationVisible(!(welcomeStep >= 0 && !demo), insets.isVisible(WindowInsetsCompat.Type.ime()))
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
                R.id.nav_item_home -> navigate(Tabs.HOME)
                R.id.nav_item_grades -> navigate(Tabs.GRADES)
                R.id.nav_item_plan -> navigate(Tabs.SCHEDULE)
                R.id.nav_item_history -> navigate(Tabs.HISTORY)
                R.id.nav_item_more -> navigate(Tabs.MORE)
            }
            true
        }

        (navigation as? SwipeNavigationView)?.setOnTabSwipe { direction ->
            if (busy) return@setOnTabSwipe
            val tabs = intArrayOf(0, 5, 2, 1, 6)
            val current = when (tab) {
                Tabs.HOME -> 0
                Tabs.SCHEDULE -> 1
                Tabs.GRADES -> 2
                Tabs.HISTORY -> 3
                else -> 4
            }
            val next = current + direction
            if (next in tabs.indices) navigate(tabs[next])
        }

        navigation?.setOnItemReselectedListener { item ->
            if (!busy && item.itemId == R.id.nav_item_more && tab != Tabs.MORE) navigate(Tabs.MORE)
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
                        welcome.edit { putBoolean("seen", true) }
                    } else if (!demo) {
                        welcomeStep = welcome.getInt("step", 0).coerceIn(0, 1)
                    }
                }
                ensureSchedule()
                val method = api.currentMethod
                if (method != null) {
                    showMethod(method)
                    return@task
                }
                render()
                showReleaseUpdatesIfRequested()
            }
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("welcomeStep", welcomeStep)
        outState.putString("settingsPage", settingsPage)
        outState.putString("demoCampus", demoCampus)
        outState.putString("documentQuery", documentQuery)
        api.save(outState)
        schedule.save(outState)
        outState.putDouble("simTargetGpa", simTargetGpa)
        outState.putDouble("simRemainingEcts", simRemainingEcts)
        super.onSaveInstanceState(outState)
        outState.putBoolean("demo", demo)
        outState.putBoolean("demoChanged", demoChanged)
        outState.putInt("tab", tab)
        history.save(outState)
        grades.save(outState)
        tasks.save(outState)
        outState.putInt("demoPlanDays", demoPlanDays)
        outState.putString("pendingCalendar", pendingCalendar)
        outState.putString("newsQuery", newsQuery)
        outState.putString("attendanceFilter", attendanceFilter)
        outState.putString("demoTasks", demoTasks.toString())
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showReleaseUpdatesIfRequested()
        if (intent.getBooleanExtra("showHistory", false)) {
            history.open(historyFilter(intent))
            navigate(Tabs.HISTORY)
        } else if (intent.hasExtra("tab")) {
            navigate(Tabs.fromExternal(intent.getIntExtra("tab", 0)))
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
        if (::content.isInitialized && !busy && tab != Tabs.ACCOUNT && tab != Tabs.API && tab != Tabs.PAYMENTS && tab != Tabs.GPA_SIMULATOR && tab != Tabs.CAMPUS_GUIDE && tab != Tabs.NEWS && tab != Tabs.ATTENDANCE) {
            load()
        }
        if (tab == Tabs.ACCOUNT && state.optString("requestToken").isNotEmpty() && !connected()) {
            checkClipboardForPin()
        }
        if (::content.isInitialized && !busy && tab == Tabs.ACCOUNT && settingsPage == "settings.notifications") render()
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

    private class PendingFilesModel : ViewModel() {
        var export: UsosClient.Response? = null
        var upload: ByteArray? = null
        override fun onCleared() { export = null; upload = null }
    }

    fun navigate(target: Int) {
        navigate(target, true)
    }

    fun openScheduleDate(date: String) {
        schedule.select(date)
        navigate(Tabs.SCHEDULE, false)
    }

    fun selectScheduleDay(date: String) {
        schedule.mode = "day"
        openScheduleDate(date)
    }

    fun hasSelectedScheduleDay(): Boolean = schedule.dateSelected

    private fun navigate(target: Int, focusCurrent: Boolean) {
        welcomeStep = -1
        if (target != tab) settingsPage = null
        if (target != tab) {
            val focused = currentFocus
            focused?.clearFocus()
            val keyboard = getSystemService(InputMethodManager::class.java)
            keyboard?.hideSoftInputFromWindow(root.windowToken, 0)
        }
        schedule.focusCurrent = focusCurrent && target == Tabs.SCHEDULE && tab != Tabs.SCHEDULE
        if (schedule.focusCurrent) schedule.dateSelected = false
        api.closeMethod()
        tab = target
        scroll.scrollTo(0, 0)
        render()
        if (!demo && !busy) {
            if (target == Tabs.CAMPUS_GUIDE && connected() && System.currentTimeMillis() - (studentServices().optJSONObject("affiliation")?.optLong("attemptAt") ?: 0L) > 24 * 60 * 60_000L) {
                refreshStudentServices("affiliation")
                return
            }
            val targetSections = when (target) { Tabs.PAYMENTS -> listOf("payments", "remittances"); Tabs.NEWS -> listOf("news"); Tabs.CARDS -> listOf("cards"); Tabs.DOCUMENTS -> listOf("documents"); Tabs.LECTURER -> if (AccountRole.lecturer(serviceAccountState())) listOf("teaching", "lecturerGroups", "lecturerTests", "lecturerMeetings", "lecturerAttendance", "lecturerReports") else emptyList(); Tabs.ACADEMIC_EXTRAS -> listOf("registrations", "tokenRegistrations", "exams", "academicCalendar", "mobility", "placementAndSlips", "campusGuide", "communications", "theses"); Tabs.STAFF_DIRECTORY -> listOf("staffDirectory"); else -> emptyList() }; val section = targetSections.firstOrNull()
            if (target == Tabs.NEWS && !connected() && CampusAffiliation.selected(state).isEmpty()) return
            val services = studentServices()
            val mobile = services.optJSONObject("mcard")
            // An upgraded installation may have a recent mobile-card cache but no physical-card entry yet.
            val physicalCardsMissing = section == "cards" && services.optJSONObject("physicalCards") == null &&
                mobile != null && mobile.optLong("fetchedAt") > 0 && mobile.optString("error").isEmpty() &&
                StudentServices.hasScope(state.optString("scopes"), "cards") && state.optLong("retryAt") <= System.currentTimeMillis()
            val optionalScopes = setOf("news", "teaching", "lecturerGroups", "lecturerMeetings", "lecturerAttendance", "lecturerReports", "staffDirectory", "academicCalendar", "mobility", "placementAndSlips", "campusGuide", "communications", "theses")
            val staleSections = targetSections.filter { candidate ->
                val allowed = candidate in optionalScopes ||
                    StudentServices.hasScope(state.optString("scopes"), StudentServiceLoader.requiredScope(candidate))
                val cacheKey = if (candidate == "cards") "mcard" else candidate
                allowed && (physicalCardsMissing && candidate == "cards" ||
                    System.currentTimeMillis() - (services.optJSONObject(cacheKey)?.optLong("attemptAt") ?: 0L) > 15 * 60_000L)
            }
            if (section != null && (section == "news" || connected()) && staleSections.isNotEmpty()) {
                refreshStudentServices(*staleSections.toTypedArray())
            }
        }
    }

    private fun setNavigationVisible(visible: Boolean, keyboardVisible: Boolean = ViewCompat.getRootWindowInsets(root)?.isVisible(WindowInsetsCompat.Type.ime()) == true) {
        val nav = navigation ?: return
        val visibility = if (visible && !keyboardVisible) View.VISIBLE else View.GONE
        nav.visibility = visibility
        (nav.parent as? HorizontalScrollView)?.visibility = visibility
    }

    fun render() {
        if (isDestroyed) return
        renderedMinute = System.currentTimeMillis() / 60000
        content.removeAllViews()
        setScheduleNavigation(null)
        val gutter = max(16, (resources.configuration.screenWidthDp - 560) / 2)
        content.setPadding(
            UiHelper.dp(this, gutter),
            UiHelper.dp(this, if (tab == Tabs.SCHEDULE) 0 else 12),
            UiHelper.dp(this, gutter),
            UiHelper.dp(this, 24)
        )
        if (welcomeStep >= 0 && !demo && state.optString("token").isEmpty()) {
            setNavigationVisible(false)
            pl.vistula.checker.ui.WelcomeScreenController.render(this, content, welcomeStep)
            return
        }
        setNavigationVisible(true)
        navigation?.let { nav ->
            val navMenuId = when (tab) {
                Tabs.GRADES -> R.id.nav_item_grades
                Tabs.SCHEDULE -> R.id.nav_item_plan
                Tabs.HISTORY -> R.id.nav_item_history
                6, 3, 4, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17 -> R.id.nav_item_more
                else -> R.id.nav_item_home
            }
            nav.menu.findItem(navMenuId).isChecked = true
            (nav.parent as? HorizontalScrollView)?.let { viewport ->
                nav.post {
                    nav.findViewById<View>(navMenuId)?.let { item ->
                        // A zero-distance smooth scroll still starts a fling animation in
                        // HorizontalScrollView and can consume the next tap or swipe.
                        val target = if (nav.width > viewport.width) item.left - (viewport.width - item.width) / 2 else 0
                        viewport.scrollTo(target.coerceAtLeast(0), 0)
                    }
                }
            }
        }

        try {
            if (demo) content.addView(UiHelper.text(this, getString(R.string.demo_banner_note), 11, UiHelper.MUTED, false).apply {
                gravity = android.view.Gravity.CENTER
                setPadding(0, 0, 0, UiHelper.dp(this@MainActivity, 8))
            })

            when (tab) {
                Tabs.LECTURER -> pl.vistula.checker.ui.LecturerScreenController.render(this, content, serviceAccountState(), studentServices())
                Tabs.ACADEMIC_EXTRAS -> pl.vistula.checker.ui.LecturerScreenController.renderAcademicExtras(this, content, studentServices())
                Tabs.STAFF_DIRECTORY -> pl.vistula.checker.ui.LecturerScreenController.renderStaffDirectory(this, content, studentServices())
                Tabs.CARDS -> StudentCardsController.render(
                    this,
                    content,
                    if (demo) JSONObject().put("user", "Aleksandra Nowak · DEMO") else state,
                    studentServices()
                )
                Tabs.DOCUMENTS -> StudentDocumentsController.render(this, content, state, studentServices())
                Tabs.HOME -> HomeScreenController.render(this, content, display(), state, demo, demoTasks) { lessonDetails(it) }
                Tabs.HISTORY -> HistoryScreenController.render(
                    this,
                    content,
                    display(),
                    history.filter,
                    history.query,
                    demo,
                    { f ->
                        history.filter = f
                        render()
                    },
                    { q -> history.query = q },
                    { sync() }
                )
                Tabs.GRADES -> {
                    if (!grades.semesterInitialized) {
                        display().optJSONObject("snapshot")?.let { raw ->
                            val snapshot = pl.vistula.checker.core.Snapshot.from(raw)
                            grades.semester = pl.vistula.checker.core.GradeSemesters.current(snapshot, java.time.LocalDate.now(UsosClient.ZONE))
                                ?: pl.vistula.checker.core.GradeSemesters.ordered(snapshot, java.time.LocalDate.now(UsosClient.ZONE)).firstOrNull().orEmpty()
                            grades.semesterInitialized = true
                        }
                    }
                    GradesScreenController.render(
                    this,
                    content,
                    display(),
                    state,
                    demo,
                    grades.semester,
                    grades.type,
                    grades.order,
                    grades.query,
                    grades.filtersExpanded,
                    { exp ->
                        grades.filtersExpanded = exp
                        render()
                    },
                    { sem ->
                        grades.semester = sem
                        if (sem.isNotEmpty()) grades.semesterExpansion[sem] = true
                        render()
                    },
                    { gt ->
                        grades.type = gt
                        render()
                    },
                    { go ->
                        grades.order = go
                        getSharedPreferences("student_tools", MODE_PRIVATE).edit { putString("gradeOrder", go) }
                        render()
                    },
                    { gq -> grades.query = gq },
                    {
                        grades.resetFilters()
                        getSharedPreferences("student_tools", MODE_PRIVATE).edit { putString("gradeOrder", grades.order) }
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
                }
                Tabs.API -> {
                    val method = api.currentMethod
                    val methodJson = api.currentMethodJson
                    if (method != null && methodJson != null) {
                        ApiExplorerController.renderMethodDetails(
                            this,
                            content,
                            scroll,
                            methodJson,
                            api.favorites,
                            uploadBytes,
                            {
                                api.closeMethod()
                                render()
                            },
                            object : ApiExplorerController.ApiCallback {
                                override fun onExecuteRequest(
                                    metadata: JSONObject,
                                    values: Map<String, String>,
                                    method: String,
                                    uploadBytes: ByteArray?,
                                    onSuccess: Consumer<UsosClient.Response>,
                                    onFailure: Consumer<String>
                                ) {
                                    executeApiRequest(metadata, values, method, uploadBytes, onSuccess, onFailure)
                                }

                                override fun onSelectUploadFile(currentMethod: String) {
                                    api.uploadMethod = currentMethod
                                    selectUpload.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE))
                                }

                                override fun onSaveResponse(response: UsosClient.Response) {
                                    saveResponseFile(response, "usos-response")
                                }
                            }
                        )
                    } else {
                        ApiExplorerController.renderCatalog(
                            this,
                            content,
                            catalog,
                            api.query,
                            api.limit,
                            api.favoritesOnly,
                            api.favorites,
                            { q -> api.query = q },
                            { lim -> api.limit = lim },
                            { fav ->
                                api.favoritesOnly = fav
                                api.limit = pl.vistula.checker.ui.ApiExplorerState.DEFAULT_LIMIT
                                render()
                            },
                            { showMethod(it) }
                        )
                    }
                }
                Tabs.ACCOUNT -> AccountScreenController.render(
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
                                    api.drafts = JSONObject()
                                    uploadBytes = null
                                    api.uploadMethod = null
                                    exportResponse = null
                                    pendingCalendar = null
                                    grades.clearSemester()
                                    demo = false
                                    render()
                                }
                            )
                        }

                        override fun onStartDemo() {
                            grades.clearSemester()
                            grades.semesterInitialized = false
                            demo = true
                            tab = 0
                            render()
                        }

                        override fun onEndDemo() {
                            grades.clearSemester()
                            demo = false
                            render()
                        }

                        override fun onRequestNotifications() {
                            requestNotifications()
                        }
                    }
                )
                Tabs.SCHEDULE -> {
                    ScheduleScreenController.render(
                        this,
                        content,
                        display(),
                        state,
                        demo,
                        schedule.date,
                        schedule.mode,
                        demoPlanDays,
                        { dVal ->
                            schedule.date = dVal
                            render()
                        },
                        { mVal ->
                            schedule.mode = mVal
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
                    if (schedule.focusCurrent && content.findViewById<View?>(R.id.schedule_swipe_content) != null) {
                        schedule.focusCurrent = false
                        val ongoing = content.findViewById<View?>(R.id.schedule_current_lesson)
                        if (ongoing != null) {
                            OneShotPreDrawListener.add(scroll) {
                                if (tab != Tabs.SCHEDULE || content.findViewById<View?>(R.id.schedule_current_lesson) !== ongoing) return@add
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
                Tabs.MORE -> MoreScreenController.render(this, content)
                Tabs.TASKS -> TasksScreenController.render(
                    this,
                    content,
                    personalTasks(),
                    tasks.filter,
                    tasks.order,
                    tasks.query,
                    { tf ->
                        tasks.filter = tf
                        render()
                    },
                    { to ->
                        tasks.order = to
                        getSharedPreferences("student_tools", MODE_PRIVATE).edit { putString("taskOrder", to) }
                        render()
                    },
                    { tq -> tasks.query = tq },
                    taskCallback()
                )
                Tabs.PAYMENTS -> PaymentsScreenController.render(this, content, display(), state, connected(), studentServices()) { refreshStudentServices("payments", "remittances") }
                Tabs.GPA_SIMULATOR -> GpaSimulatorController.render(
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
                Tabs.CAMPUS_GUIDE -> CampusGuideController.render(this, content)
                Tabs.NEWS -> NewsScreenController.render(
                    this,
                    content,
                    display(),
                    newsQuery,
                    { q -> newsQuery = q },
                    { refreshStudentServices("news") },
                    studentServices().optJSONObject("news") ?: JSONObject()
                )
                Tabs.ATTENDANCE -> AttendanceScreenController.render(
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
        getSharedPreferences("welcome", MODE_PRIVATE).edit { putInt("step", welcomeStep) }
        render()
        scroll.scrollTo(0, 0)
    }

    fun finishWelcome(startDemo: Boolean, configure: Boolean) {
        getSharedPreferences("welcome", MODE_PRIVATE).edit { putBoolean("seen", true).remove("step") }
        welcomeStep = -1
        if (startDemo) {
            demo = true
            navigate(Tabs.HOME)
        } else if (configure) {
            demo = false
            tab = 4
            settingsPage = "settings.connection"
            render()
            scroll.scrollTo(0, 0)
        } else {
            navigate(Tabs.HOME)
        }
    }

    fun attendancePreferences(): SharedPreferences =
        getSharedPreferences(if (demo) "attendance_tracker_demo" else "attendance_tracker", MODE_PRIVATE)

    fun setDemo(demo: Boolean) {
        this.demo = demo
        if (demo) welcomeStep = -1
        render()
    }

    fun selectGradeSemester(term: String) {
        grades.select(term)
        if (tab == Tabs.GRADES) render()
    }

    fun getCalendarDate(): String = schedule.date
    fun setCalendarDate(date: String) {
        schedule.date = date
    }

    fun shouldFocusCurrentSchedule(): Boolean = schedule.focusCurrent

    fun setScheduleNavigation(action: IntConsumer?) {
        if (scroll is ScheduleScrollView) {
            (scroll as ScheduleScrollView).setScheduleNavigation(action)
        }
    }

    fun getCalendarMode(): String = schedule.mode
    fun setCalendarMode(mode: String) {
        schedule.mode = mode
    }

    fun getDocumentQuery(): String = documentQuery
    fun getApiModule(): String = api.module
    fun setApiModule(module: String) {
        api.module = module
        api.limit = pl.vistula.checker.ui.ApiExplorerState.DEFAULT_LIMIT
        render()
    }

    fun setDocumentQuery(query: String) {
        documentQuery = query
    }

    fun serviceAccountState(): JSONObject = if (demo) JSONObject().put("campusOverride", demoCampus).put("staffStatus", 2).put("studentStatusCode", 2) else state

    fun currentServiceCampus(): String = CampusAffiliation.selected(repository.read())

    @Throws(Exception::class)
    private fun studentServices(): JSONObject {
        if (demo) return DemoStudentServices.data()
        return state.optJSONObject("studentServices") ?: JSONObject()
    }

    fun refreshStudentServices(vararg sections: String) {
        if (sections.isEmpty()) return
        val section = sections.first()
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
            // Mapped scope for optional student and lecturer services
            val scope = StudentServiceLoader.requiredScope(section)
            val scopeOptional = setOf("news", "usosNews", "affiliation", "teaching", "lecturerGroups", "lecturerMeetings", "lecturerAttendance", "lecturerReports", "staffDirectory", "academicCalendar", "mobility", "placementAndSlips", "campusGuide", "communications")
            if (sections.size == 1 && section !in scopeOptional && !StudentServices.hasScope(state.optString("scopes"), scope)) {
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
                val scopeOptional = setOf("news", "usosNews", "affiliation", "teaching", "lecturerGroups", "lecturerMeetings", "lecturerAttendance", "lecturerReports", "staffDirectory", "academicCalendar", "mobility", "placementAndSlips", "campusGuide", "communications")
                for (sec in sections) {
                    val secScope = StudentServiceLoader.requiredScope(sec)
                    if (sec in scopeOptional || StudentServices.hasScope(state.optString("scopes"), secScope)) {
                        try {
                            StudentServiceLoader.refresh(repository, sec)
                        } catch (_: Exception) {
                            // Ensure remaining sections in batch are loaded so all tables receive data
                        }
                    }
                }
                repository.read()
            },
            { result ->
                state = result
                render()
                if (section == "affiliation" && tab == Tabs.NEWS && CampusAffiliation.selected(state).isNotEmpty()) refreshStudentServices("news")
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
            if (tab == Tabs.NEWS && CampusAffiliation.selected(state).isNotEmpty()) refreshStudentServices("news")
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

    fun <T> universityRead(operation: (UsosClient) -> T, done: (T) -> Unit) {
        if (demo || !connected()) {
            Toast.makeText(this, if (demo) R.string.services_demo_action else R.string.services_connect_first, Toast.LENGTH_LONG).show()
            return
        }
        val originTab = tab
        task({
            val credentials = repository.read()
            check(SharedRepository.sameAccount(state, credentials) && !credentials.optBoolean("authorizationError"))
            check(credentials.optLong("retryAt") <= System.currentTimeMillis()) { getString(R.string.error_rate_limit) }
            val client = UsosClient(credentials, {}, { address ->
                check(SharedRepository.sameAccount(credentials, repository.read()))
                java.net.URI(address).toURL().openConnection() as java.net.HttpURLConnection
            })
            val result = operation(client)
            check(SharedRepository.sameAccount(credentials, repository.read()))
            result
        }, { result -> if (tab == originTab) done(result) })
    }

    fun openApiModule(module: String) {
        api.openModule(module)
        navigate(Tabs.API)
    }

    fun openApiMethod(method: String, parameters: Map<String, String>) {
        try {
            api.drafts.put(method, JSONObject(parameters))
        } catch (e: JSONException) {
            error(ApiErrorUi.message(this, e))
            return
        }
        tab = 3
        showMethod(method)
    }

    fun apiDraft(method: String): JSONObject {
        var draft = api.drafts.optJSONObject(method)
        if (draft == null) {
            draft = JSONObject()
            try {
                api.drafts.put(method, draft)
            } catch (ignored: JSONException) {
            }
        }
        return draft
    }

    fun apiUpload(method: String): ByteArray? = if (method == api.uploadMethod) uploadBytes else null

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

    fun performLecturerWrite(isPoints: Boolean, nodeId: String, studentId: String, value: String, comment: String) {
        if (!liveService("crstests")) return
        val prefs = getSharedPreferences("student_tools", MODE_PRIVATE)
        val kvStore = object : KeyValueStore {
            override fun getString(key: String, defaultValue: String): String = prefs.getString(key, defaultValue) ?: defaultValue
            override fun putString(key: String, value: String) { prefs.edit { putString(key, value) } }
            override fun getBoolean(key: String, defaultValue: Boolean): Boolean = prefs.getBoolean(key, defaultValue)
            override fun putBoolean(key: String, value: Boolean) { prefs.edit { putBoolean(key, value) } }
            override fun getLong(key: String, defaultValue: Long): Long = prefs.getLong(key, defaultValue)
            override fun putLong(key: String, value: Long) { prefs.edit { putLong(key, value) } }
            override fun clear() { prefs.edit { clear() } }
        }
        task(
            {
                val credentials = serviceCredentials("crstests")
                val client = UsosClient(credentials)
                val sameAccount = {
                    check(SharedRepository.sameAccount(credentials, repository.read())) {
                        getString(R.string.service_action_unavailable)
                    }
                }
                if (isPoints) {
                    LecturerWriteActions.updatePoints(client, credentials, nodeId, studentId, value.replace(',', '.').toDoubleOrNull() ?: throw IllegalArgumentException("Invalid points"), comment, null, kvStore, sameAccount)
                } else {
                    LecturerWriteActions.updateGrade(client, credentials, nodeId, studentId, value.toIntOrNull() ?: throw IllegalArgumentException("Invalid grade order"), comment, "", null, kvStore, sameAccount)
                }
                StudentServiceLoader.refresh(repository, "lecturerTests")
                repository.read()
            },
            { result ->
                state = result
                render()
                Toast.makeText(this, R.string.lecturer_write_success, Toast.LENGTH_LONG).show()
            }
        )
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
                        try {
                            StudentServiceLoader.refresh(repository, "cards")
                            repository.read()
                        } catch (e: Exception) {
                            throw MobileCardActions.ConfirmedRefreshFailed(e)
                        }
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
        var filename = name.replace(UNSAFE_FILE_CHARS, "_")
        if (filename.length > 100) filename = filename.substring(0, 100)
        if (filename.trim().isEmpty()) filename = "usos-document"
        try {
            createExport.launch(
                Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .setType(response.mime)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_TITLE, filename + extension)
            )
        } catch (e: ActivityNotFoundException) {
            exportResponse = null
            error(getString(R.string.toast_export_calendar_error))
        }
    }

    fun getCalendarQuery(): String = schedule.query
    fun setCalendarQuery(query: String) {
        schedule.query = query
    }

    fun saveCalendar(ics: String) {
        pendingCalendar = ics
        try {
            createCalendar.launch(
                Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .setType("text/calendar")
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_TITLE, "plan_zajec_vistula.ics")
            )
        } catch (e: ActivityNotFoundException) {
            pendingCalendar = null
            Toast.makeText(this, R.string.toast_export_calendar_error, Toast.LENGTH_SHORT).show()
        }
    }

    fun personalTasks(): JSONArray =
        if (demo) demoTasks else (state.optJSONArray("tasks") ?: JSONArray())

    fun openTasks(selectedFilter: String) {
        tasks.open(selectedFilter)
        navigate(Tabs.TASKS)
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
            override fun onSaveTask(task: JSONObject) {
                if (demo != wasDemo) return
                savePersonalTask(task, session)
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
                        try { StudentServiceLoader.refresh(repository, "affiliation") } catch (e: Exception) { Diagnostics.swallowed("MainActivity", e) /* Optional campus data must not discard a valid grade sync. */ }
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
                api.currentMethod = name
                api.currentMethodJson = json
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
                    credentials.put("secret", BuildConfig.USOS_SECRET.trim())
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
            { if (tab == Tabs.API && api.currentMethod == metadata.optString("name")) onSuccess.accept(it) },
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
                    val clockChanged = (tab == Tabs.HOME || tab == Tabs.SCHEDULE) && renderedMinute != System.currentTimeMillis() / 60000
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
        if (isDestroyed) return
        if (busy) {
            Toast.makeText(this, R.string.loading, Toast.LENGTH_SHORT).show()
            return
        }
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
                    if (tab == Tabs.HOME || tab == Tabs.HISTORY || tab == Tabs.GRADES || tab == Tabs.SCHEDULE || tab == Tabs.TASKS) render()
                    failed.accept(ApiErrorUi.message(this, e))
                    error(ApiErrorUi.message(this, e))
                }
            }
        }
    }

    private fun updateBusy(value: Boolean) {
        setButtons(root, !value)
        navigation?.let { nav ->
            for (i in 0 until nav.menu.size) {
                nav.menu[i].isEnabled = !value
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
                    val clip = item.text.toString().trim().replace(WHITESPACE, "")
                    if (clip.matches(PIN_SHAPE) && clip != detectedPin) {
                        detectedPin = clip
                        Toast.makeText(this, R.string.pin_detected_clipboard, Toast.LENGTH_LONG).show()
                        render()
                    }
                }
            }
        } catch (e: Exception) {
            pl.vistula.checker.Diagnostics.swallowed("MainActivity", e)
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

    private fun onDocumentResult(requestCode: Int, resultCode: Int, data: Intent?) {
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
            if (requestCode == 21 && api.currentMethod != null && api.currentMethod == api.uploadMethod) {
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
        private val UNSAFE_FILE_CHARS = Regex("[^\\p{L}\\p{N}._ -]")
        private val WHITESPACE = Regex("\\s+")
        private val PIN_SHAPE = Regex("^[a-zA-Z0-9]{6,16}$")
        private fun historyFilter(intent: Intent): String {
            val kind = intent.getStringExtra("historyKind")
            return if ("ocena" == kind || "plan" == kind) kind else pl.vistula.checker.ui.HistoryUiState.ALL
        }
    }
}

