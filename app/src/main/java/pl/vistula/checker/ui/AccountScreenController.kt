package pl.vistula.checker.ui

import androidx.core.content.edit
import androidx.core.view.isGone
import androidx.core.view.isVisible
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
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
        /** Starts a new browser login with the saved app key after the session expired. */
        fun onReconnect(key: String, secret: String, scopes: String) = onBeginOAuth(key, secret, scopes)
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
            }), Tone.MUTED)
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
                    { Appearance.choose(activity) })
            )
            appearance.addView(
                UiHelper.actionRow(
                    activity,
                    activity.getString(R.string.language_title),
                    { Language.choose(activity) })
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

                val head = LayoutInflater.from(activity).inflate(R.layout.view_account_header, content, false)
                head.findViewById<TextView>(R.id.account_header_avatar).text = UiHelper.initials(userName)
                head.findViewById<TextView>(R.id.account_header_name).text = userName
                head.findViewById<TextView>(R.id.account_header_detail).apply {
                    val detail = listOf(
                        activity.getString(if (pl.vistula.checker.core.AccountRole.lecturer(state)) R.string.lecturer_title else R.string.student_card_badge),
                        if (studentNr.isEmpty()) "" else activity.getString(R.string.cards_student_number, studentNr)
                    ).filter { it.isNotEmpty() }.joinToString(" · ")
                    text = detail
                }
                head.contentDescription = userName
                pages.header(head)
                bindProfile(activity, profile, state, snapshot, demo, userName, studentNr, email, program, stage, admission)
            }

            profileBody?.let { profile ->
                renderAcademicTerm(activity, UiHelper.disclosure(activity, profile, "profile.term", R.string.academic_calendar_title), d)
            }

            if (connected) {
                if (state.optBoolean("authorizationError")) {
                    UiHelper.note(activity, content, activity.getString(R.string.account_session_expired), Tone.ERROR)
                    val savedKey = state.optString("key")
                    val savedSecret = state.optString("secret")
                    if (savedKey.isNotEmpty() && savedSecret.isNotEmpty()) {
                        content.addView(
                            UiHelper.button(activity, activity.getString(R.string.btn_reconnect), {
                                listener.onReconnect(savedKey, savedSecret, state.optString("scopes"))
                            }, true)
                        )
                    }
                }
                val box = pages.page("settings.sync", R.string.sync_settings_title)
                UiHelper.note(activity, box, activity.getString(R.string.settings_sync_hint), Tone.MUTED)
                box.addView(UiHelper.button(activity, activity.getString(R.string.btn_check_now), { activity.sync() }, true))
                UiHelper.note(
                    activity,
                    box,
                    activity.getString(R.string.monitoring_last_success) + "\n" +
                        UiHelper.timestamp(d?.optLong("successAt") ?: 0L),
                    Tone.MUTED
                )
                if (state.optString("error").isNotEmpty()) {
                    UiHelper.note(activity, box, ApiErrorUi.cached(activity, state), Tone.ERROR)
                    if (state.optLong("retryAt") > System.currentTimeMillis()) {
                        UiHelper.note(
                            activity,
                            box,
                            activity.getString(R.string.home_next_retry, UiHelper.timestamp(state.optLong("retryAt"))),
                            Tone.MUTED
                        )
                    }
                }
                val accountControls = pages.page("settings.account", R.string.settings_account_access)
                val authorizedScopes = state.optString("scopes")
                if (pl.vistula.checker.core.AccountRole.lecturer(state)) {
                    val prefs = activity.getSharedPreferences("student_tools", Context.MODE_PRIVATE)
                    val expSwitch = UiHelper.switchRow(
                        activity, accountControls, activity.getString(R.string.lecturer_experimental_setting),
                        prefs.getBoolean(pl.vistula.checker.LecturerWriteActions.PREF_EXPERIMENTAL_WRITES, true)
                    )
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
                UiHelper.add(activity, access, activity.getString(R.string.account_oauth_consent, authorizedScopes), 14, Tone.MUTED, false)
                if (!StudentServices.hasScope(authorizedScopes, "payments")) {
                    UiHelper.note(activity, accountControls, activity.getString(R.string.account_warn_no_payments_scope), Tone.WARNING)
                }
                val toggle = UiHelper.switchRow(activity, box, activity.getString(R.string.background_check), state.optBoolean("enabled"))
                if (state.optBoolean("authorizationError")) {
                    toggle.isEnabled = false
                    UiHelper.note(activity, box, activity.getString(R.string.account_session_expired), Tone.ERROR)
                }
                toggle.setOnCheckedChangeListener { _, checked -> listener.onToggleEnabled(checked) }

                val frequency = LayoutInflater.from(activity).inflate(R.layout.view_sync_frequency, box, false)
                val interval = frequency.findViewById<Spinner>(R.id.sync_interval)
                val values = activity.resources.getIntArray(R.array.sync_interval_minutes).map { it.toLong() }
                interval.setSelection(values.indexOf(state.optLong("interval", 30L)).takeIf { it >= 0 } ?: 1)
                frequency.findViewById<View>(R.id.sync_interval_save).setOnClickListener {
                    listener.onUpdateInterval(values[interval.selectedItemPosition])
                }
                box.addView(frequency)
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
                renderConnection(activity, box, state, detectedPin, listener)
                content.addView(UiHelper.button(activity, activity.getString(R.string.btn_start_demo), { listener.onStartDemo() }, false))
            }

            renderNotifications(activity, pages.page("settings.notifications", R.string.account_notices_heading), listener)
            renderWidgets(activity, pages.page("settings.widgets", R.string.account_widgets_heading))

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

        /** Notification status, test button and class reminders (`view_account_notifications`). */
        private fun renderNotifications(activity: MainActivity, page: LinearLayout, listener: AccountListener) {
            val view = LayoutInflater.from(activity).inflate(R.layout.view_account_notifications, page, false)
            val allowed = NotificationManagerCompat.from(activity).areNotificationsEnabled()
            view.findViewById<TextView>(R.id.notif_status).apply {
                setText(if (allowed) R.string.account_notif_system_allowed else R.string.account_notif_system_blocked)
                if (!allowed) setTone(Tone.WARNING)
            }
            view.findViewById<View>(R.id.notif_settings).setOnClickListener { listener.onRequestNotifications() }
            view.findViewById<View>(R.id.notif_test).setOnClickListener {
                NotificationHelper.sendTestNotification(activity)
                Toast.makeText(activity, R.string.account_toast_test_notif_sent, Toast.LENGTH_SHORT).show()
            }

            val prefs = activity.getSharedPreferences("student_tools", Context.MODE_PRIVATE)
            val detail = view.findViewById<View>(R.id.reminders_detail)
            val permission = view.findViewById<View>(R.id.reminders_permission)
            fun showDetail(on: Boolean) {
                detail.visibility = if (on) View.VISIBLE else View.GONE
                permission.visibility = if (on && !ClassReminderReceiver.canNotify(activity)) View.VISIBLE else View.GONE
            }
            val switch = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.reminders_switch)
            switch.isChecked = prefs.getBoolean(ClassReminderReceiver.PREF_REMINDERS_ENABLED, false)
            showDetail(switch.isChecked)

            val lead = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.reminders_lead)
            val chips = mapOf(R.id.lead_15 to 15, R.id.lead_30 to 30, R.id.lead_45 to 45)
            val saved = prefs.getInt(ClassReminderReceiver.PREF_REMINDER_MINUTES, 15)
            lead.check(chips.entries.firstOrNull { it.value == saved }?.key ?: R.id.lead_15)
            lead.setOnCheckedStateChangeListener { _, ids ->
                val minutes = ids.firstOrNull()?.let { chips[it] } ?: return@setOnCheckedStateChangeListener
                prefs.edit { putInt(ClassReminderReceiver.PREF_REMINDER_MINUTES, minutes) }
                ClassReminderReceiver.scheduleNext(activity)
            }
            switch.setOnCheckedChangeListener { _, checked ->
                prefs.edit { putBoolean(ClassReminderReceiver.PREF_REMINDERS_ENABLED, checked) }
                if (checked) {
                    ClassReminderReceiver.scheduleNext(activity)
                    if (!ClassReminderReceiver.canNotify(activity)) listener.onRequestNotifications()
                    Toast.makeText(activity, R.string.account_toast_reminders_enabled, Toast.LENGTH_SHORT).show()
                } else {
                    ClassReminderReceiver.cancel(activity)
                    Toast.makeText(activity, R.string.account_toast_reminders_disabled, Toast.LENGTH_SHORT).show()
                }
                showDetail(checked)
            }
            page.addView(view)
        }

        private fun renderWidgets(activity: MainActivity, page: LinearLayout) {
            val view = LayoutInflater.from(activity).inflate(R.layout.view_account_widgets, page, false)
            view.findViewById<View>(R.id.widgets_update).setOnClickListener {
                TodayScheduleWidget.updateAll(activity)
                QuickStatusWidget.updateAll(activity)
                Toast.makeText(activity, R.string.account_toast_widgets_updated, Toast.LENGTH_SHORT).show()
            }
            page.addView(view)
        }

        /** Sign-in setup (`view_account_connection`): credentials, access scopes, browser login and PIN confirmation. */
        private fun renderConnection(activity: MainActivity, box: LinearLayout, state: JSONObject, detectedPin: String?, listener: AccountListener) {
            val view = LayoutInflater.from(activity).inflate(R.layout.view_account_connection, box, false)
            val buildKey = BuildConfig.USOS_KEY.trim()
            val buildSecret = BuildConfig.USOS_SECRET.trim()
            val defaultKey = state.optString("key", buildKey).ifEmpty { buildKey }
            val defaultSecret = state.optString("secret", buildSecret).ifEmpty { buildSecret }
            val pending = state.optString("requestToken").isNotEmpty()
            val credentialsReady = defaultKey.isNotEmpty() && defaultSecret.isNotEmpty()

            view.findViewById<View>(R.id.connection_reopen).setOnClickListener { UiHelper.browser(activity, "https://usosapps.vistula.edu.pl/developers/") }
            view.findViewById<TextView>(R.id.connection_desc).setText(R.string.connect_usos_desc)
            view.findViewById<TextView>(R.id.connection_status).setText(
                when {
                    pending -> R.string.login_step_pin
                    credentialsReady -> R.string.login_ready
                    else -> R.string.login_setup
                }
            )
            val status = view.findViewById<View>(R.id.connection_status)
            val setup = view.findViewById<View>(R.id.connection_setup)
            val advanced = view.findViewById<View>(R.id.connection_advanced)
            setup.visibility = if (pending) View.GONE else View.VISIBLE
            advanced.visibility = if (credentialsReady) View.GONE else View.VISIBLE
            status.visibility = if (pending || credentialsReady) View.VISIBLE else View.GONE
            view.findViewById<View>(R.id.connection_toggle).setOnClickListener {
                if (setup.isGone) {
                    setup.visibility = View.VISIBLE
                    advanced.visibility = View.VISIBLE
                } else {
                    advanced.visibility = if (advanced.isVisible) View.GONE else View.VISIBLE
                }
            }
            view.findViewById<View>(R.id.connection_help).setOnClickListener { showApiKeyHelpDialog(activity) }

            val keyLayout = UiHelper.fieldLayout(activity, view.findViewById(R.id.connection_key_host), activity.getString(R.string.login_consumer_key), defaultKey, false)
            val secretLayout = UiHelper.fieldLayout(activity, view.findViewById(R.id.connection_secret_host), activity.getString(R.string.login_consumer_secret), defaultSecret, true)
            val key = keyLayout.findViewById<TextInputEditText>(R.id.field_input)
            val secret = secretLayout.findViewById<TextInputEditText>(R.id.field_input)
            if (buildKey.isNotEmpty()) view.findViewById<View>(R.id.connection_debug).visibility = View.VISIBLE

            val rawScopes = UiHelper.disclosure(activity, view.findViewById(R.id.connection_raw_scopes), "settings.rawScopes", R.string.settings_scope_advanced)
            val scopes = UiHelper.field(
                activity, rawScopes, activity.getString(R.string.settings_scope_advanced),
                state.optString("scopes", "grades|studies|payments|cards|adm_documents|crstests|offline_access"), false
            )
            val features = linkedMapOf(
                "payments" to R.string.tool_payments_title,
                "cards" to R.string.cards_title,
                "documents" to R.string.documents_title,
                "tests" to R.string.settings_tests_access,
                "profile" to R.string.settings_profile_access,
                "lecturer" to R.string.lecturer_title
            )
            val accessBody = UiHelper.disclosure(activity, view.findViewById(R.id.connection_access), "settings.access", R.string.settings_access)
            UiHelper.note(activity, accessBody, activity.getString(R.string.settings_access_hint), Tone.MUTED)
            val choicesHost = LinearLayout(activity).apply {
                id = R.id.connection_choices
                orientation = LinearLayout.VERTICAL
            }
            accessBody.addView(choicesHost)
            var updatingChoices = false
            val choices = linkedMapOf<String, MaterialSwitch>()
            for ((feature, title) in features) {
                val choice = UiHelper.switchRow(activity, choicesHost, activity.getString(title), FeatureAccess.selected(scopes.text.toString(), feature))
                choices[feature] = choice
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

            val emailLayout = UiHelper.fieldLayout(activity, view.findViewById(R.id.connection_email_host), activity.getString(R.string.auto_key_email_hint), state.optString("email"), false)
            val email = emailLayout.findViewById<TextInputEditText>(R.id.field_input)
            email.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            val autoKey = view.findViewById<View>(R.id.connection_auto_key)
            autoKey.setOnClickListener {
                val address = email.text.toString().trim()
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()) {
                    emailLayout.error = activity.getString(R.string.auto_key_bad_email)
                    return@setOnClickListener
                }
                emailLayout.error = null
                autoKey.isEnabled = false
                Toast.makeText(activity, R.string.auto_key_working, Toast.LENGTH_SHORT).show()
                Thread {
                    val result = runCatching {
                        pl.vistula.checker.KeyRegistration.register("USOS Checker", "", address)
                    }
                    activity.runOnUiThread {
                        if (activity.isDestroyed) return@runOnUiThread
                        autoKey.isEnabled = true
                        result.onSuccess { keys ->
                            key.setText(keys.key)
                            secret.setText(keys.secret)
                            keyLayout.error = null
                            secretLayout.error = null
                            view.findViewById<View>(R.id.connection_login).performClick()
                        }.onFailure { failure ->
                            val reason = (failure as? pl.vistula.checker.KeyRegistration.Rejected)?.message
                                ?: activity.getString(R.string.auto_key_unreachable)
                            Toast.makeText(activity, activity.getString(R.string.auto_key_failed, reason), Toast.LENGTH_LONG).show()
                        }
                    }
                }.start()
            }
            view.findViewById<View>(R.id.connection_paste_keys).setOnClickListener {
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                val pair = KeyPaste.parse(text)
                if (pair == null) {
                    Toast.makeText(activity, R.string.paste_keys_not_found, Toast.LENGTH_LONG).show()
                } else {
                    key.setText(pair.first)
                    secret.setText(pair.second)
                    keyLayout.error = null
                    secretLayout.error = null
                    view.findViewById<View>(R.id.connection_login).performClick()
                }
            }
            view.findViewById<View>(R.id.connection_login).setOnClickListener {
                val k = key.text.toString().trim().ifEmpty { buildKey }
                val sec = secret.text.toString().trim().ifEmpty { buildSecret }
                if (k.isNotEmpty() && sec.isNotEmpty()) {
                    val clean = LinkedHashSet<String>()
                    for (part in scopes.text.toString().trim().split("|")) {
                        val trimmed = part.trim()
                        if (trimmed.isNotEmpty()) clean.add(trimmed)
                    }
                    clean.addAll(listOf("grades", "studies", "offline_access"))
                    listener.onBeginOAuth(k, sec, clean.joinToString("|"))
                } else {
                    advanced.visibility = View.VISIBLE
                    if (k.isEmpty()) keyLayout.error = activity.getString(R.string.login_key_required)
                    if (sec.isEmpty()) secretLayout.error = activity.getString(R.string.login_secret_required)
                }
            }
            if (pending) bindPinSection(activity, view, state, detectedPin, listener)
            box.addView(view)
        }

        private fun bindPinSection(activity: MainActivity, view: View, state: JSONObject, detectedPin: String?, listener: AccountListener) {
            view.findViewById<View>(R.id.connection_pin_section).visibility = View.VISIBLE
            val initialPin = detectedPin ?: ""
            val pinLayout = UiHelper.fieldLayout(activity, view.findViewById(R.id.connection_pin_host), activity.getString(R.string.account_pin_field_hint), initialPin, true)
            val pin = pinLayout.findViewById<TextInputEditText>(R.id.field_input)
            if (initialPin.isNotEmpty()) view.findViewById<View>(R.id.connection_pin_detected).visibility = View.VISIBLE
            val confirmPin = Runnable {
                val value = pin.text.toString().trim().replace(Regex("\\s+"), "")
                if (value.isEmpty()) pinLayout.error = activity.getString(R.string.account_toast_enter_pin) else listener.onConfirmPin(value)
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
            view.findViewById<View>(R.id.connection_paste).setOnClickListener {
                try {
                    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                    val text = clipboard?.primaryClip?.takeIf { clipboard.hasPrimaryClip() && it.itemCount > 0 }?.getItemAt(0)?.text
                    if (text != null) {
                        pin.setText(text.toString().trim().replace(Regex("\\s+"), ""))
                        Toast.makeText(activity, R.string.account_toast_pin_pasted, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    pl.vistula.checker.Diagnostics.swallowed("AccountScreenController", e)
                }
            }
            view.findViewById<View>(R.id.connection_confirm).setOnClickListener { confirmPin.run() }
            view.findViewById<View>(R.id.connection_return).setOnClickListener {
                UiHelper.browser(
                    activity,
                    "https://usosapps.vistula.edu.pl/services/oauth/authorize?oauth_token=" + OAuth.encode(state.optString("requestToken"))
                )
            }
        }

        /** Fills `view_account_profile`; empty values hide their line. */
        private fun bindProfile(
            activity: MainActivity, parent: LinearLayout, state: JSONObject, snapshot: Snapshot?, demo: Boolean,
            userName: String, studentNr: String, email: String, program: String, stage: String, admission: String
        ) {
            val view = LayoutInflater.from(activity).inflate(R.layout.view_account_profile, parent, false)
            fun line(id: Int, value: String) = view.findViewById<TextView>(id).apply {
                text = value
                visibility = if (value.isEmpty()) View.GONE else View.VISIBLE
            }
            view.findViewById<TextView>(R.id.profile_avatar).text = UiHelper.initials(userName)
            view.findViewById<TextView>(R.id.profile_role).setText(
                if (pl.vistula.checker.core.AccountRole.lecturer(state)) R.string.lecturer_title else R.string.student_card_badge
            )
            view.findViewById<TextView>(R.id.profile_name).text = userName
            line(R.id.profile_student_number, if (studentNr.isEmpty()) "" else activity.getString(R.string.account_student_number_copy, activity.getString(R.string.cards_student_number, studentNr))).setOnClickListener {
                UiHelper.copyToClipboard(activity, activity.getString(R.string.cards_student_number, studentNr), studentNr)
            }
            if (email.isNotEmpty()) {
                view.findViewById<View>(R.id.profile_email_row).visibility = View.VISIBLE
                view.findViewById<TextView>(R.id.profile_email).text = activity.getString(R.string.account_email_line, email)
                view.findViewById<View>(R.id.profile_email_copy).setOnClickListener { UiHelper.copyToClipboard(activity, "Email", email) }
            }
            line(R.id.profile_program, if (program.isEmpty()) "" else activity.getString(R.string.account_program, program))
            line(R.id.profile_stage, if (stage.isEmpty()) "" else activity.getString(R.string.account_stage, stage))
            line(R.id.profile_admission, if (admission.isEmpty()) "" else activity.getString(R.string.account_admission_date, HistoryScreenController.formatFullDate(admission)))
            val thesis = snapshot?.thesis
            if (thesis != null && thesis.hasThesis()) {
                line(R.id.profile_thesis_header, activity.getString(R.string.account_thesis_header))
                line(R.id.profile_thesis_title, thesis.title)
                line(R.id.profile_thesis_status, if (thesis.status.isEmpty()) "" else activity.getString(R.string.account_thesis_status, thesis.status))
                line(R.id.profile_thesis_supervisor, if (thesis.supervisors.isEmpty()) "" else activity.getString(R.string.account_thesis_supervisor, thesis.supervisors))
                line(R.id.profile_thesis_reviewer, if (thesis.reviewers.isEmpty()) "" else activity.getString(R.string.account_thesis_reviewer, thesis.reviewers))
            } else if (snapshot?.serviceErrors?.containsKey("theses") == true) {
                line(R.id.profile_thesis_unavailable, activity.getString(R.string.account_thesis_unavailable))
            }
            parent.addView(view)
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
                    val termBox = UiHelper.section(activity, content)
                    UiHelper.add(activity, termBox, activity.getString(R.string.academic_calendar_title), 11, Tone.INK, true)
                    UiHelper.add(activity, termBox, currentTerm.name, 17, Tone.INK, true)
                    val days = currentTerm.daysRemaining(today)
                    val termInfo = if (days >= 0) {
                        activity.getString(R.string.home_term_remaining, days, currentTerm.endDate)
                    } else {
                        activity.getString(R.string.home_term_ended, currentTerm.endDate)
                    }
                    UiHelper.add(activity, termBox, termInfo, 13, Tone.MUTED, false)
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

