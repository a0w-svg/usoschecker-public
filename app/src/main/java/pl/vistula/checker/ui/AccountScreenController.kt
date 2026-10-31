package pl.vistula.checker.ui

import androidx.core.content.edit
import androidx.core.view.isGone
import androidx.core.view.isVisible
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import java.time.LocalDate
import org.json.JSONException
import org.json.JSONObject
import pl.vistula.checker.BuildConfig
import pl.vistula.checker.ClassReminderReceiver
import pl.vistula.checker.MainActivity
import pl.vistula.checker.NotificationHelper
import pl.vistula.checker.R
import pl.vistula.checker.UsosClient
import pl.vistula.checker.core.AcademicTerm
import pl.vistula.checker.core.FeatureAccess
import pl.vistula.checker.core.OAuth
import pl.vistula.checker.core.Snapshot
import pl.vistula.checker.core.StudentServices
import pl.vistula.checker.widget.QuickStatusWidget
import pl.vistula.checker.widget.TodayScheduleWidget

/**
 * Controller for rendering the Account, Profile, OAuth, and Sync settings screen.
 */
class AccountScreenController private constructor() {

    interface AccountListener {
        fun onBeginOAuth(key: String, secret: String, scopes: String)
        fun onConfirmPin(pin: String)
        fun onToggleEnabled(enabled: Boolean)
        fun onUpdateInterval(interval: Long)
        fun onDisconnect()
        fun onStartDemo()
        fun onEndDemo()
        fun onRequestNotifications()
    }

    companion object {
        @JvmStatic
        fun render(
            activity: MainActivity,
            content: LinearLayout,
            d: JSONObject?,
            state: JSONObject,
            demo: Boolean,
            detectedPin: String?,
            listener: AccountListener
        ) {
            ToolScreenUi.header(activity, content, R.string.nav_account, null)
            val pages = SettingsPages(activity, content)
            if (!demo) UiHelper.note(activity, content, activity.getString(when {
                state.optBoolean("authorizationError") -> R.string.account_session_expired
                state.optString("token").isNotEmpty() -> R.string.settings_connected
                else -> R.string.settings_disconnected
            }), UiHelper.MUTED)
            val campus = pages.page("settings.campus", R.string.campus_affiliation_title)
            CampusSettingsController.render(activity, campus, if (demo) activity.serviceAccountState() else state)
            val appearance = pages.page("settings.appearance", R.string.settings_personalization)
            if (demo) {
                content.addView(UiHelper.button(activity, activity.getString(R.string.btn_end_demo), { listener.onEndDemo() }, true))
                content.addView(UiHelper.button(activity, activity.getString(R.string.btn_simulate_changes), { activity.sync() }, false))
            }

            val connected = state.optString("token").isNotEmpty()
            appearance.addView(
                UiHelper.actionRow(
                    activity,
                    activity.getString(R.string.appearance_title),
                    { Appearance.choose(activity) },
                    false
                )
            )
            appearance.addView(
                UiHelper.actionRow(
                    activity,
                    activity.getString(R.string.language_title),
                    { Language.choose(activity) },
                    false
                )
            )

            var profileBody: LinearLayout? = null
            if (connected || demo) {
                val raw = d?.optJSONObject("snapshot")
                var snapshot: Snapshot? = null
                if (raw != null) {
                    try {
                        snapshot = Snapshot.from(raw)
                    } catch (e: Exception) {
                        pl.vistula.checker.Diagnostics.swallowed("AccountScreenController", e)
                    }
                }

                val profile = pages.page("settings.profile", R.string.settings_profile)
                profileBody = profile
                val userName = if (demo) "Jan Kowalski" else state.optString("user", "Student USOS")
                val studentNr = if (demo) "34589" else state.optString("studentNumber", "")
                val email = if (demo) "j.kowalski@vistula.edu.pl" else state.optString("email", "")
                val program = if (demo) "Informatyka, studia I stopnia" else state.optString("program", state.optString("programme", ""))
                val stage = if (demo) "Semestr 5 · Studia stacjonarne" else state.optString("stage", "")
                val admission = if (demo) "2024-10-01" else state.optString("admissionDate", "")

                val topRow = LinearLayout(activity)
                topRow.orientation = LinearLayout.HORIZONTAL
                topRow.gravity = Gravity.CENTER_VERTICAL
                topRow.setPadding(0, 0, 0, UiHelper.dp(activity, 12))

                val avatar = UiHelper.text(activity, UiHelper.initials(userName), 19, Color.WHITE, true)
                avatar.gravity = Gravity.CENTER
                val avatarBg = GradientDrawable()
                avatarBg.shape = GradientDrawable.OVAL
                avatarBg.setColor(UiHelper.background(activity, UiHelper.GREEN))
                avatar.background = avatarBg
                val avParams = LinearLayout.LayoutParams(UiHelper.dp(activity, 52), UiHelper.dp(activity, 52))
                avParams.rightMargin = UiHelper.dp(activity, 14)
                topRow.addView(avatar, avParams)

                val nameCol = LinearLayout(activity)
                nameCol.orientation = LinearLayout.VERTICAL
                nameCol.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                UiHelper.add(activity, nameCol, activity.getString(if (pl.vistula.checker.core.AccountRole.lecturer(state)) R.string.lecturer_title else R.string.student_card_badge), 11, UiHelper.GREEN, true)
                UiHelper.add(activity, nameCol, userName, 20, UiHelper.INK, true)
                if (studentNr.isNotEmpty()) {
                    val nrView = UiHelper.text(activity, activity.getString(R.string.cards_student_number, studentNr) + "  📋", 14, UiHelper.INK, true)
                    nrView.setOnClickListener { UiHelper.copyToClipboard(activity, activity.getString(R.string.cards_student_number, studentNr), studentNr) }
                    nameCol.addView(nrView)
                }
                topRow.addView(nameCol)
                profile.addView(topRow)

                if (email.isNotEmpty()) {
                    val emailRow = LinearLayout(activity)
                    emailRow.orientation = LinearLayout.HORIZONTAL
                    emailRow.gravity = Gravity.CENTER_VERTICAL
                    emailRow.setPadding(0, UiHelper.dp(activity, 2), 0, UiHelper.dp(activity, 4))
                    val emailText = UiHelper.text(activity, "✉ $email", 14, UiHelper.MUTED, false)
                    emailText.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    val copyEmail = UiHelper.button(
                        activity,
                        activity.getString(R.string.btn_copy),
                        { UiHelper.copyToClipboard(activity, "Email", email) },
                        false
                    )
                    copyEmail.minHeight = UiHelper.dp(activity, 36)
                    copyEmail.textSize = 11f
                    emailRow.addView(emailText)
                    emailRow.addView(copyEmail)
                    profile.addView(emailRow)
                }

                if (program.isNotEmpty()) {
                    UiHelper.add(activity, profile, activity.getString(R.string.account_program, program), 14, UiHelper.INK, false)
                }
                if (stage.isNotEmpty()) {
                    UiHelper.add(activity, profile, activity.getString(R.string.account_stage, stage), 13, UiHelper.MUTED, false)
                }
                if (admission.isNotEmpty()) {
                    UiHelper.add(
                        activity,
                        profile,
                        activity.getString(R.string.account_admission_date, HistoryScreenController.formatFullDate(admission)),
                        12,
                        UiHelper.MUTED,
                        false
                    )
                }

                val thesis = snapshot?.thesis
                if (thesis != null && thesis.hasThesis()) {
                    UiHelper.add(activity, profile, activity.getString(R.string.account_thesis_header), 11, UiHelper.GREEN, true)
                    UiHelper.add(activity, profile, thesis.title, 15, UiHelper.INK, true)
                    if (thesis.status.isNotEmpty()) {
                        UiHelper.add(activity, profile, activity.getString(R.string.account_thesis_status, thesis.status), 13, UiHelper.MUTED, false)
                    }
                    if (thesis.supervisors.isNotEmpty()) {
                        UiHelper.add(activity, profile, activity.getString(R.string.account_thesis_supervisor, thesis.supervisors), 13, UiHelper.MUTED, false)
                    }
                    if (thesis.reviewers.isNotEmpty()) {
                        UiHelper.add(activity, profile, activity.getString(R.string.account_thesis_reviewer, thesis.reviewers), 13, UiHelper.MUTED, false)
                    }
                } else if (snapshot?.serviceErrors?.containsKey("theses") == true) {
                    UiHelper.note(activity, profile, activity.getString(R.string.account_thesis_unavailable), UiHelper.MUTED)
                }
            }

            profileBody?.let { profile ->
                renderAcademicTerm(activity, UiHelper.disclosure(activity, profile, "profile.term", R.string.academic_calendar_title), d)
            }

            if (connected) {
                if (state.optBoolean("authorizationError")) {
                    UiHelper.note(activity, content, activity.getString(R.string.account_session_expired), activity.getColor(R.color.app_error))
                }
                val box = pages.page("settings.sync", R.string.sync_settings_title)
                UiHelper.note(activity, box, activity.getString(R.string.settings_sync_hint), UiHelper.MUTED)
                box.addView(UiHelper.button(activity, activity.getString(R.string.btn_check_now), { activity.sync() }, true))
                UiHelper.note(
                    activity,
                    box,
                    activity.getString(R.string.monitoring_last_success) + "\n" +
                        UiHelper.timestamp(d?.optLong("successAt") ?: 0L),
                    UiHelper.MUTED
                )
                if (state.optString("error").isNotEmpty()) {
                    UiHelper.note(activity, box, ApiErrorUi.cached(activity, state), Color.rgb(167, 49, 49))
                    if (state.optLong("retryAt") > System.currentTimeMillis()) {
                        UiHelper.note(
                            activity,
                            box,
                            activity.getString(R.string.home_next_retry, UiHelper.timestamp(state.optLong("retryAt"))),
                            UiHelper.MUTED
                        )
                    }
                }
                val accountControls = pages.page("settings.account", R.string.settings_account_access)
                val authorizedScopes = state.optString("scopes")
                if (pl.vistula.checker.core.AccountRole.lecturer(state)) {
                    val prefs = activity.getSharedPreferences("student_tools", Context.MODE_PRIVATE)
                    val expSwitch = MaterialSwitch(activity).apply {
                        setText(R.string.lecturer_experimental_setting)
                        setTextColor(UiHelper.foreground(activity, UiHelper.INK))
                        isChecked = prefs.getBoolean(pl.vistula.checker.LecturerWriteActions.PREF_EXPERIMENTAL_WRITES, true)
                    }
                    accountControls.addView(expSwitch)
                    expSwitch.setOnCheckedChangeListener { _, checked ->
                        if (checked && !prefs.getBoolean(pl.vistula.checker.LecturerWriteActions.PREF_EXPERIMENTAL_WRITES_ACK, false)) {
                            expSwitch.isChecked = false
                            MaterialAlertDialogBuilder(activity)
                                .setTitle(R.string.lecturer_experimental_notice_title)
                                .setMessage(R.string.lecturer_experimental_notice_body)
                                .setNegativeButton(R.string.btn_cancel, null)
                                .setPositiveButton(R.string.lecturer_experimental_ack) { _, _ ->
                                    prefs.edit {
                                        putBoolean(pl.vistula.checker.LecturerWriteActions.PREF_EXPERIMENTAL_WRITES_ACK, true)
                                        putBoolean(pl.vistula.checker.LecturerWriteActions.PREF_EXPERIMENTAL_WRITES, true)
                                    }
                                    expSwitch.isChecked = true
                                }
                                .show()
                        } else {
                            prefs.edit { putBoolean(pl.vistula.checker.LecturerWriteActions.PREF_EXPERIMENTAL_WRITES, checked) }
                        }
                    }
                }
                val access = UiHelper.disclosure(activity, accountControls, "settings.granted", R.string.settings_access)
                UiHelper.add(activity, access, activity.getString(R.string.account_oauth_consent, authorizedScopes), 14, UiHelper.MUTED, false)
                if (!StudentServices.hasScope(authorizedScopes, "payments")) {
                    UiHelper.note(activity, accountControls, activity.getString(R.string.account_warn_no_payments_scope), Color.rgb(180, 110, 0))
                }
                val toggle = MaterialSwitch(activity)
                toggle.setText(R.string.background_check)
                toggle.setTextColor(UiHelper.foreground(activity, UiHelper.INK))
                toggle.isChecked = state.optBoolean("enabled")
                if (state.optBoolean("authorizationError")) {
                    toggle.isEnabled = false
                    UiHelper.note(activity, box, activity.getString(R.string.account_session_expired), Color.rgb(167, 49, 49))
                }
                box.addView(toggle)
                toggle.setOnCheckedChangeListener { _, checked -> listener.onToggleEnabled(checked) }

                UiHelper.add(activity, box, activity.getString(R.string.sync_frequency), 14, UiHelper.MUTED, true)
                val interval = Spinner(activity)
                interval.adapter = ArrayAdapter(
                    activity,
                    android.R.layout.simple_spinner_dropdown_item,
                    arrayOf(
                        activity.getString(R.string.interval_15m),
                        activity.getString(R.string.interval_30m),
                        activity.getString(R.string.interval_1h),
                        activity.getString(R.string.interval_2h)
                    )
                )
                val values = longArrayOf(15L, 30L, 60L, 120L)
                val selected = state.optLong("interval", 30L)
                var position = 1
                for (i in values.indices) {
                    if (selected == values[i]) position = i
                }
                interval.setSelection(position)
                box.addView(interval)
                box.addView(
                    UiHelper.button(
                        activity,
                        activity.getString(R.string.btn_save_frequency),
                        { listener.onUpdateInterval(values[interval.selectedItemPosition]) },
                        false
                    )
                )
                accountControls.addView(
                    UiHelper.button(
                        activity,
                        activity.getString(R.string.btn_disconnect_account),
                        {
                            MaterialAlertDialogBuilder(activity)
                                .setTitle(activity.getString(R.string.account_disconnect_title))
                                .setMessage(activity.getString(R.string.account_disconnect_message))
                                .setNegativeButton(activity.getString(R.string.btn_cancel), null)
                                .setPositiveButton(activity.getString(R.string.account_disconnect_btn)) { _, _ ->
                                    listener.onDisconnect()
                                }
                                .show()
                        },
                        false
                    )
                )
            } else if (!demo) {
                val box = pages.page("settings.connection", R.string.connect_usos_title, true)
                UiHelper.add(activity, box, activity.getString(R.string.connect_usos_desc), 14, UiHelper.MUTED, false)
                box.addView(UiHelper.actionRow(activity, activity.getString(R.string.welcome_reopen), { activity.showWelcome(1) }, false))

                val buildKey = BuildConfig.USOS_KEY.trim()
                val buildSecret = BuildConfig.USOS_SECRET.trim()
                var defaultKey = state.optString("key", buildKey)
                if (defaultKey.isEmpty()) defaultKey = buildKey
                var defaultSecret = state.optString("secret", buildSecret)
                if (defaultSecret.isEmpty()) defaultSecret = buildSecret

                val pending = state.optString("requestToken").isNotEmpty()
                val credentialsReady = defaultKey.isNotEmpty() && defaultSecret.isNotEmpty()
                UiHelper.note(
                    activity,
                    box,
                    activity.getString(
                        when {
                            pending -> R.string.login_step_pin
                            credentialsReady -> R.string.login_ready
                            else -> R.string.login_setup
                        }
                    ),
                    UiHelper.MUTED
                )
                val setup = LinearLayout(activity)
                setup.orientation = LinearLayout.VERTICAL
                box.addView(setup)
                setup.visibility = if (pending) View.GONE else View.VISIBLE
                val advanced = LinearLayout(activity)
                advanced.orientation = LinearLayout.VERTICAL
                setup.addView(advanced)
                advanced.visibility = if (credentialsReady) View.GONE else View.VISIBLE
                box.addView(
                    UiHelper.button(
                        activity,
                        activity.getString(R.string.login_settings),
                        {
                            if (setup.isGone) {
                                setup.visibility = View.VISIBLE
                                advanced.visibility = View.VISIBLE
                            } else {
                                advanced.visibility = if (advanced.isVisible) View.GONE else View.VISIBLE
                            }
                        },
                        false
                    )
                )
                advanced.addView(
                    UiHelper.button(
                        activity,
                        activity.getString(R.string.btn_how_to_register),
                        { showApiKeyHelpDialog(activity) },
                        false
                    )
                )
                val key = UiHelper.field(activity, advanced, "Consumer Key", defaultKey, false)
                val secret = UiHelper.field(activity, advanced, "Consumer Secret", defaultSecret, true)
                if (buildKey.isNotEmpty()) {
                    UiHelper.note(
                        activity,
                        advanced,
                        activity.getString(R.string.settings_debug_credentials),
                        UiHelper.GREEN
                    )
                }
                val rawScopes = UiHelper.disclosure(activity, advanced, "settings.rawScopes", R.string.settings_scope_advanced)
                val scopes = UiHelper.field(
                    activity, rawScopes, activity.getString(R.string.settings_scope_advanced),
                    state.optString("scopes", "grades|studies|payments|cards|adm_documents|crstests|offline_access"), false
                )
                val access = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                setup.addView(access)
                UiHelper.heading(activity, access, activity.getString(R.string.settings_access))
                UiHelper.note(activity, access, activity.getString(R.string.settings_access_hint), UiHelper.MUTED)
                val features = linkedMapOf(
                    "payments" to R.string.tool_payments_title,
                    "cards" to R.string.cards_title,
                    "documents" to R.string.documents_title,
                    "tests" to R.string.settings_tests_access,
                    "profile" to R.string.settings_profile_access,
                    "lecturer" to R.string.lecturer_title
                )
                var updatingChoices = false
                val choices = linkedMapOf<String, MaterialSwitch>()
                for ((feature, title) in features) {
                    val choice = MaterialSwitch(activity).apply {
                        text = activity.getString(title)
                        minHeight = UiHelper.dp(activity, 48)
                        setTextColor(UiHelper.foreground(activity, UiHelper.INK))
                        isChecked = FeatureAccess.selected(scopes.text.toString(), feature)
                    }
                    choices[feature] = choice
                    access.addView(choice)
                    choice.setOnCheckedChangeListener { _, enabled ->
                        if (!updatingChoices) {
                            val next = FeatureAccess.change(scopes.text.toString(), feature, enabled)
                            if (next != scopes.text.toString()) scopes.setText(next)
                        }
                    }
                }
                UiHelper.watchSearch(scopes) { value ->
                    updatingChoices = true
                    for ((feature, choice) in choices) {
                        val selected = FeatureAccess.selected(value, feature)
                        if (choice.isChecked != selected) choice.isChecked = selected
                    }
                    updatingChoices = false
                }

                setup.addView(
                    UiHelper.button(
                        activity,
                        activity.getString(R.string.btn_login_browser),
                        {
                            var k = key.text.toString().trim()
                            if (k.isEmpty()) k = buildKey
                            var sec = secret.text.toString().trim()
                            if (sec.isEmpty()) sec = buildSecret
                            val sc = scopes.text.toString().trim()
                            if (k.isNotEmpty() && sec.isNotEmpty()) {
                                val cleanScopes = ArrayList<String>()
                                for (part in sc.split("|")) {
                                    val trimmed = part.trim()
                                    if (trimmed.isNotEmpty() && !cleanScopes.contains(trimmed)) {
                                        cleanScopes.add(trimmed)
                                    }
                                }
                                if (!cleanScopes.contains("grades")) cleanScopes.add("grades")
                                if (!cleanScopes.contains("studies")) cleanScopes.add("studies")
                                if (!cleanScopes.contains("offline_access")) cleanScopes.add("offline_access")
                                listener.onBeginOAuth(k, sec, cleanScopes.joinToString("|"))
                            } else {
                                advanced.visibility = View.VISIBLE
                                if (k.isEmpty()) key.error = activity.getString(R.string.login_key_required)
                                if (sec.isEmpty()) secret.error = activity.getString(R.string.login_secret_required)
                            }
                        },
                        true
                    )
                )

                if (state.optString("requestToken").isNotEmpty()) {
                    UiHelper.add(activity, box, activity.getString(R.string.pin_hint), 14, UiHelper.MUTED, false)
                    val initialPin = detectedPin ?: ""
                    val pin = UiHelper.field(activity, box, activity.getString(R.string.account_pin_field_hint), initialPin, true)
                    if (initialPin.isNotEmpty()) {
                        UiHelper.note(activity, box, activity.getString(R.string.pin_detected_clipboard), UiHelper.GREEN)
                    }
                    box.addView(
                        UiHelper.button(
                            activity,
                            activity.getString(R.string.btn_paste_pin),
                            {
                                try {
                                    val cb = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                                    val primaryClip = cb?.primaryClip
                                    if (cb != null && cb.hasPrimaryClip() && primaryClip != null && primaryClip.itemCount > 0) {
                                        val item = primaryClip.getItemAt(0)
                                        if (item != null && item.text != null) {
                                            val clip = item.text.toString().trim().replace(Regex("\\s+"), "")
                                            pin.setText(clip)
                                            Toast.makeText(activity, R.string.account_toast_pin_pasted, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    pl.vistula.checker.Diagnostics.swallowed("AccountScreenController", e)
                                }
                            },
                            false
                        )
                    )
                    val confirmPin = Runnable {
                        val value = pin.text.toString().trim().replace(Regex("\\s+"), "")
                        if (value.isEmpty()) {
                            pin.error = activity.getString(R.string.account_toast_enter_pin)
                        } else {
                            listener.onConfirmPin(value)
                        }
                    }
                    pin.isSingleLine = true
                    // setSingleLine replaces the password transformation on Android.
                    pin.transformationMethod = PasswordTransformationMethod.getInstance()
                    pin.imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                    pin.setOnEditorActionListener { _, action, _ ->
                        if (action != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
                        confirmPin.run()
                        true
                    }
                    box.addView(UiHelper.button(activity, activity.getString(R.string.btn_confirm_pin), confirmPin, true))
                    box.addView(
                        UiHelper.button(
                            activity,
                            activity.getString(R.string.btn_return_to_browser),
                            {
                                UiHelper.browser(
                                    activity,
                                    "https://usosapps.vistula.edu.pl/services/oauth/authorize?oauth_token=" +
                                        OAuth.encode(state.optString("requestToken"))
                                )
                            },
                            false
                        )
                    )
                }

                content.addView(UiHelper.button(activity, activity.getString(R.string.btn_start_demo), { listener.onStartDemo() }, false))
            }

            // Notifications
            val notices = pages.page("settings.notifications", R.string.account_notices_heading)
            UiHelper.add(
                activity,
                notices,
                if (NotificationManagerCompat.from(activity).areNotificationsEnabled()) {
                    activity.getString(R.string.account_notif_system_allowed)
                } else {
                    activity.getString(R.string.account_notif_system_blocked)
                },
                14,
                UiHelper.MUTED,
                false
            )
            notices.addView(
                UiHelper.button(
                    activity,
                    activity.getString(R.string.account_btn_system_notif_settings),
                    { listener.onRequestNotifications() },
                    false
                )
            )
            notices.addView(
                UiHelper.button(
                    activity,
                    activity.getString(R.string.account_btn_send_test_notif),
                    {
                        NotificationHelper.sendTestNotification(activity)
                        Toast.makeText(activity, R.string.account_toast_test_notif_sent, Toast.LENGTH_SHORT).show()
                    },
                    false
                )
            )

            val toolsPrefs = activity.getSharedPreferences("student_tools", Context.MODE_PRIVATE)
            val remindersOn = toolsPrefs.getBoolean(ClassReminderReceiver.PREF_REMINDERS_ENABLED, false)
            val reminderMin = toolsPrefs.getInt(ClassReminderReceiver.PREF_REMINDER_MINUTES, 15)

            val reminderRow = LinearLayout(activity)
            reminderRow.orientation = LinearLayout.VERTICAL
            reminderRow.setPadding(0, UiHelper.dp(activity, 8), 0, UiHelper.dp(activity, 4))

            val reminderSwitch = MaterialSwitch(activity)
            reminderSwitch.text = activity.getString(R.string.account_reminder_switch_label)
            reminderSwitch.isChecked = remindersOn
            reminderSwitch.setTextColor(UiHelper.foreground(activity, UiHelper.INK))
            reminderSwitch.setOnCheckedChangeListener { _, isChecked ->
                toolsPrefs.edit { putBoolean(ClassReminderReceiver.PREF_REMINDERS_ENABLED, isChecked) }
                if (isChecked) {
                    ClassReminderReceiver.scheduleNext(activity)
                    if (!ClassReminderReceiver.canNotify(activity)) listener.onRequestNotifications()
                    Toast.makeText(activity, R.string.account_toast_reminders_enabled, Toast.LENGTH_SHORT).show()
                } else {
                    ClassReminderReceiver.cancel(activity)
                    Toast.makeText(activity, R.string.account_toast_reminders_disabled, Toast.LENGTH_SHORT).show()
                }
                activity.navigate(4)
            }
            reminderRow.addView(reminderSwitch)

            if (remindersOn) {
                if (!ClassReminderReceiver.canNotify(activity)) {
                    UiHelper.note(activity, reminderRow, activity.getString(R.string.account_reminder_permission_missing), UiHelper.MUTED)
                }
                UiHelper.note(activity, reminderRow, activity.getString(R.string.account_reminder_lead_time_label), UiHelper.MUTED)
                UiHelper.chips(
                    activity,
                    reminderRow,
                    arrayOf("15", "30", "45"),
                    arrayOf(
                        activity.getString(R.string.account_lead_time_15),
                        activity.getString(R.string.account_lead_time_30),
                        activity.getString(R.string.account_lead_time_45)
                    ),
                    reminderMin.toString()
                ) { value ->
                    val min = value.toInt()
                    toolsPrefs.edit { putInt(ClassReminderReceiver.PREF_REMINDER_MINUTES, min) }
                    ClassReminderReceiver.scheduleNext(activity)
                    activity.navigate(4)
                }
            }
            notices.addView(reminderRow)
            UiHelper.note(activity, notices, activity.getString(R.string.account_channels_note), UiHelper.MUTED)

            // Widgets
            val widgetsCard = pages.page("settings.widgets", R.string.account_widgets_heading)
            UiHelper.add(activity, widgetsCard, activity.getString(R.string.account_widgets_desc), 13, UiHelper.MUTED, false)
            widgetsCard.addView(
                UiHelper.button(
                    activity,
                    activity.getString(R.string.account_btn_update_widgets),
                    {
                        TodayScheduleWidget.updateAll(activity)
                        QuickStatusWidget.updateAll(activity)
                        Toast.makeText(activity, R.string.account_toast_widgets_updated, Toast.LENGTH_SHORT).show()
                    },
                    false
                )
            )

            val about = pages.page("settings.about", R.string.settings_about)
            about.addView(
                UiHelper.button(
                    activity,
                    activity.getString(R.string.account_btn_open_usosapps),
                    { UiHelper.browser(activity, "https://usosapps.vistula.edu.pl/") },
                    false
                )
            )
            AppInfo.render(activity, about)
            pages.finish(if (!connected && state.optString("requestToken").isNotEmpty()) "settings.connection" else null)
        }

        private fun renderAcademicTerm(activity: MainActivity, content: LinearLayout, data: JSONObject?) {
            val raw = data?.optJSONObject("snapshot") ?: return
            try {
                val snap = Snapshot.from(raw)
                val today = LocalDate.now(UsosClient.ZONE)
                var currentTerm: AcademicTerm? = null
                for (t in snap.terms.values) {
                    if (t.isCurrent(today)) {
                        currentTerm = t
                        break
                    }
                }
                if (currentTerm == null && snap.terms.isNotEmpty()) {
                    currentTerm = snap.terms.values.iterator().next()
                }
                if (currentTerm != null) {
                    val termBox = UiHelper.section(activity, content, Color.rgb(238, 245, 242))
                    UiHelper.add(activity, termBox, activity.getString(R.string.academic_calendar_title), 11, UiHelper.GREEN, true)
                    UiHelper.add(activity, termBox, currentTerm.name, 17, UiHelper.INK, true)
                    val days = currentTerm.daysRemaining(today)
                    val termInfo = if (days >= 0) {
                        activity.getString(R.string.home_term_remaining, days, currentTerm.endDate)
                    } else {
                        activity.getString(R.string.home_term_ended, currentTerm.endDate)
                    }
                    UiHelper.add(activity, termBox, termInfo, 13, UiHelper.MUTED, false)
                }
            } catch (_: JSONException) {
                // Missing cached academic information must not block account settings.
            }
        }

        private fun showApiKeyHelpDialog(activity: MainActivity) {
            MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.api_key_help_title))
                .setMessage(activity.getString(R.string.api_key_help_message))
                .setPositiveButton(activity.getString(R.string.btn_register_usosapps)) { _, _ ->
                    UiHelper.browser(activity, "https://usosapps.vistula.edu.pl/developers/")
                }
                .setNegativeButton(activity.getString(R.string.btn_close), null)
                .show()
        }
    }
}

