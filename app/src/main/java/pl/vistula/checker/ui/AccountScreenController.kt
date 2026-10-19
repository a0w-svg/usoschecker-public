package pl.vistula.checker.ui

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
            if (demo) {
                content.addView(UiHelper.button(activity, activity.getString(R.string.btn_end_demo), { listener.onEndDemo() }, true))
            }

            val connected = state.optString("token").isNotEmpty()
            content.addView(
                UiHelper.actionRow(
                    activity,
                    activity.getString(R.string.appearance_title),
                    { Appearance.choose(activity) },
                    false
                )
            )
            content.addView(
                UiHelper.actionRow(
                    activity,
                    activity.getString(R.string.language_title),
                    { Language.choose(activity) },
                    false
                )
            )

            if (connected || demo) {
                val raw = d?.optJSONObject("snapshot")
                var snapshot: Snapshot? = null
                if (raw != null) {
                    try {
                        snapshot = Snapshot.from(raw)
                    } catch (_: Exception) {
                    }
                }

                val profile = UiHelper.section(activity, content, Color.WHITE)
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
                UiHelper.add(activity, nameCol, activity.getString(R.string.student_card_badge), 11, UiHelper.GREEN, true)
                UiHelper.add(activity, nameCol, userName, 20, UiHelper.INK, true)
                if (studentNr.isNotEmpty()) {
                    val nrView = UiHelper.text(activity, "Nr albumu: $studentNr  📋", 14, UiHelper.INK, true)
                    nrView.setOnClickListener { UiHelper.copyToClipboard(activity, "Numer albumu", studentNr) }
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
                }
            }

            renderAcademicTerm(activity, content, d)

            if (demo || connected) {
                content.addView(
                    UiHelper.button(
                        activity,
                        activity.getString(if (demo) R.string.btn_simulate_changes else R.string.btn_check_now),
                        { activity.sync() },
                        false
                    )
                )
            }

            if (connected) {
                val box = UiHelper.section(activity, content, Color.WHITE)
                UiHelper.add(activity, box, activity.getString(R.string.sync_settings_title), 19, UiHelper.INK, true)
                UiHelper.note(
                    activity,
                    box,
                    activity.getString(R.string.monitoring_last_success) + "\n" +
                        UiHelper.timestamp(d?.optLong("successAt") ?: 0L),
                    UiHelper.MUTED
                )
                if (state.optString("error").isNotEmpty()) {
                    UiHelper.note(activity, box, state.optString("error"), Color.rgb(167, 49, 49))
                    if (state.optLong("retryAt") > System.currentTimeMillis()) {
                        UiHelper.note(
                            activity,
                            box,
                            activity.getString(R.string.home_next_retry, UiHelper.timestamp(state.optLong("retryAt"))),
                            UiHelper.MUTED
                        )
                    }
                }
                val authorizedScopes = state.optString("scopes")
                UiHelper.add(activity, box, activity.getString(R.string.account_oauth_consent, authorizedScopes), 13, UiHelper.MUTED, false)
                if (!StudentServices.hasScope(authorizedScopes, "payments")) {
                    UiHelper.note(activity, box, activity.getString(R.string.account_warn_no_payments_scope), Color.rgb(180, 110, 0))
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
                box.addView(
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
            } else {
                val box = UiHelper.section(activity, content, Color.WHITE)
                UiHelper.add(activity, box, activity.getString(R.string.connect_usos_title), 20, UiHelper.INK, true)
                UiHelper.add(activity, box, activity.getString(R.string.connect_usos_desc), 14, UiHelper.MUTED, false)

                val buildKey = BuildConfig.USOS_KEY?.trim() ?: ""
                val buildSecret = BuildConfig.USOS_SECRET?.trim() ?: ""
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
                            if (setup.visibility == View.GONE) {
                                setup.visibility = View.VISIBLE
                                advanced.visibility = View.VISIBLE
                            } else {
                                advanced.visibility = if (advanced.visibility == View.VISIBLE) View.GONE else View.VISIBLE
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
                        "Wczytano klucz aplikacji (Consumer Key & Secret) z pliku local.properties.",
                        UiHelper.GREEN
                    )
                }
                val scopes = UiHelper.field(
                    activity,
                    advanced,
                    "Zakresy zgody OAuth (oddzielone |)",
                    state.optString("scopes", "grades|studies|payments|cards|adm_documents|crstests|offline_access"),
                    false
                )
                val scopeScroll = HorizontalScrollView(activity)
                scopeScroll.isHorizontalScrollBarEnabled = false
                val scopeRow = LinearLayout(activity)
                scopeRow.orientation = LinearLayout.HORIZONTAL
                val b1 = UiHelper.button(
                    activity,
                    activity.getString(R.string.scope_recommended),
                    { scopes.setText("grades|studies|payments|cards|adm_documents|crstests|offline_access") },
                    false
                )
                val b2 = UiHelper.button(
                    activity,
                    activity.getString(R.string.scope_basic),
                    { scopes.setText("grades|studies|offline_access") },
                    false
                )
                val b3 = UiHelper.button(
                    activity,
                    activity.getString(R.string.scope_complete),
                    { scopes.setText("grades|studies|payments|cards|adm_documents|crstests|photo|events|email|offline_access") },
                    false
                )
                b1.minHeight = UiHelper.dp(activity, 48)
                b1.textSize = 12f
                b2.minHeight = UiHelper.dp(activity, 48)
                b2.textSize = 12f
                b3.minHeight = UiHelper.dp(activity, 48)
                b3.textSize = 12f
                val sp = LinearLayout.LayoutParams(-2, -2)
                sp.rightMargin = UiHelper.dp(activity, 6)
                scopeRow.addView(b1, sp)
                scopeRow.addView(b2, sp)
                scopeRow.addView(b3, sp)
                val cardsScopes = UiHelper.button(
                    activity,
                    activity.getString(R.string.cards_scope_profile),
                    {
                        val values = LinkedHashSet<String>()
                        for (value in scopes.text.toString().split("|")) {
                            if (value.trim().isNotEmpty()) values.add(value.trim())
                        }
                        values.add("cards")
                        values.add("adm_documents")
                        scopes.setText(values.joinToString("|"))
                    },
                    false
                )
                scopeRow.addView(cardsScopes, LinearLayout.LayoutParams(-2, -2))
                scopeScroll.addView(scopeRow)
                advanced.addView(scopeScroll)
                UiHelper.add(activity, advanced, activity.getString(R.string.account_scopes_hint), 12, UiHelper.MUTED, false)
                UiHelper.note(activity, advanced, activity.getString(R.string.services_scope_hint), UiHelper.MUTED)

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
                                } catch (_: Exception) {
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
            val notices = UiHelper.section(activity, content, Color.WHITE)
            UiHelper.add(activity, notices, activity.getString(R.string.account_notices_heading), 18, UiHelper.INK, true)
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
                toolsPrefs.edit().putBoolean(ClassReminderReceiver.PREF_REMINDERS_ENABLED, isChecked).apply()
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
                    toolsPrefs.edit().putInt(ClassReminderReceiver.PREF_REMINDER_MINUTES, min).apply()
                    ClassReminderReceiver.scheduleNext(activity)
                    activity.navigate(4)
                }
            }
            notices.addView(reminderRow)
            UiHelper.note(activity, notices, activity.getString(R.string.account_channels_note), UiHelper.MUTED)

            // Widgets
            val widgetsCard = UiHelper.section(activity, content, Color.WHITE)
            UiHelper.add(activity, widgetsCard, activity.getString(R.string.account_widgets_heading), 18, UiHelper.INK, true)
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

            content.addView(
                UiHelper.button(
                    activity,
                    activity.getString(R.string.account_btn_open_usosapps),
                    { UiHelper.browser(activity, "https://usosapps.vistula.edu.pl/") },
                    false
                )
            )
            AppInfo.render(activity, content)
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

