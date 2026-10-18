package pl.vistula.checker.ui;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationManagerCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import pl.vistula.checker.BuildConfig;
import pl.vistula.checker.ClassReminderReceiver;
import pl.vistula.checker.MainActivity;
import pl.vistula.checker.NotificationHelper;
import pl.vistula.checker.R;
import pl.vistula.checker.SyncWorker;
import pl.vistula.checker.core.AcademicTerm;
import pl.vistula.checker.UsosClient;
import java.time.LocalDate;
import pl.vistula.checker.core.OAuth;
import pl.vistula.checker.core.Snapshot;

/**
 * Controller for rendering the Account, Profile, OAuth, and Sync settings screen.
 */
public final class AccountScreenController {

    private AccountScreenController() {}

    public interface AccountListener {
        void onBeginOAuth(String key, String secret, String scopes);
        void onConfirmPin(String pin);
        void onToggleEnabled(boolean enabled);
        void onUpdateInterval(long interval);
        void onDisconnect();
        void onStartDemo();
        void onEndDemo();
        void onRequestNotifications();
    }

    public static void render(MainActivity activity, LinearLayout content, JSONObject d,
                              JSONObject state, boolean demo, String detectedPin,
                              AccountListener listener) {

        if (demo) {
            content.addView(UiHelper.button(activity, activity.getString(R.string.btn_end_demo), listener::onEndDemo, true));
        }

        boolean connected = !state.optString("token").isEmpty();
        content.addView(UiHelper.button(activity, activity.getString(R.string.appearance_title),
            () -> Appearance.choose(activity), false));

        if (connected || demo) {
            JSONObject raw = d != null ? d.optJSONObject("snapshot") : null;
            Snapshot snapshot = null;
            if (raw != null) {
                try { snapshot = Snapshot.from(raw); } catch (Exception ignored) {}
            }

            LinearLayout profile = UiHelper.card(activity, content, Color.WHITE);
            String userName = demo ? "Jan Kowalski" : state.optString("user", "Student USOS");
            String studentNr = demo ? "34589" : state.optString("studentNumber", "");
            String email = demo ? "j.kowalski@vistula.edu.pl" : state.optString("email", "");
            String program = demo ? "Informatyka, studia I stopnia" : state.optString("program", state.optString("programme", ""));
            String stage = demo ? "Semestr 5 · Studia stacjonarne" : state.optString("stage", "");
            String admission = demo ? "2024-10-01" : state.optString("admissionDate", "");

            LinearLayout topRow = new LinearLayout(activity);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);
            topRow.setPadding(0, 0, 0, UiHelper.dp(activity, 12));

            TextView avatar = UiHelper.text(activity, UiHelper.initials(userName), 19, Color.WHITE, true);
            avatar.setGravity(Gravity.CENTER);
            GradientDrawable avatarBg = new GradientDrawable();
            avatarBg.setShape(GradientDrawable.OVAL);
            avatarBg.setColor(UiHelper.background(activity, UiHelper.GREEN));
            avatar.setBackground(avatarBg);
            LinearLayout.LayoutParams avParams = new LinearLayout.LayoutParams(UiHelper.dp(activity, 52), UiHelper.dp(activity, 52));
            avParams.rightMargin = UiHelper.dp(activity, 14);
            topRow.addView(avatar, avParams);

            LinearLayout nameCol = new LinearLayout(activity);
            nameCol.setOrientation(LinearLayout.VERTICAL);
            nameCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            UiHelper.add(activity, nameCol, activity.getString(R.string.student_card_badge), 11, UiHelper.GREEN, true);
            UiHelper.add(activity, nameCol, userName, 20, UiHelper.INK, true);
            if (!studentNr.isEmpty()) {
                TextView nrView = UiHelper.text(activity, "Nr albumu: " + studentNr + "  📋", 14, UiHelper.INK, true);
                nrView.setOnClickListener(v -> UiHelper.copyToClipboard(activity, "Numer albumu", studentNr));
                nameCol.addView(nrView);
            }
            topRow.addView(nameCol);
            profile.addView(topRow);

            if (!email.isEmpty()) {
                LinearLayout emailRow = new LinearLayout(activity);
                emailRow.setOrientation(LinearLayout.HORIZONTAL);
                emailRow.setGravity(Gravity.CENTER_VERTICAL);
                emailRow.setPadding(0, UiHelper.dp(activity, 2), 0, UiHelper.dp(activity, 4));
                TextView emailText = UiHelper.text(activity, "✉ " + email, 14, UiHelper.MUTED, false);
                emailText.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
                Button copyEmail = UiHelper.button(activity, activity.getString(R.string.btn_copy), () -> UiHelper.copyToClipboard(activity, "Email", email), false);
                copyEmail.setMinHeight(UiHelper.dp(activity, 36));
                copyEmail.setTextSize(11);
                emailRow.addView(emailText);
                emailRow.addView(copyEmail);
                profile.addView(emailRow);
            }

            if (!program.isEmpty()) UiHelper.add(activity, profile, activity.getString(R.string.account_program, program), 14, UiHelper.INK, false);
            if (!stage.isEmpty()) UiHelper.add(activity, profile, activity.getString(R.string.account_stage, stage), 13, UiHelper.MUTED, false);
            if (!admission.isEmpty()) UiHelper.add(activity, profile, activity.getString(R.string.account_admission_date, HistoryScreenController.formatFullDate(admission)), 12, UiHelper.MUTED, false);

            if (snapshot != null && snapshot.thesis != null && snapshot.thesis.hasThesis()) {
                UiHelper.add(activity, profile, activity.getString(R.string.account_thesis_header), 11, UiHelper.GREEN, true);
                UiHelper.add(activity, profile, snapshot.thesis.title, 15, UiHelper.INK, true);
                if (!snapshot.thesis.status.isEmpty()) UiHelper.add(activity, profile, activity.getString(R.string.account_thesis_status, snapshot.thesis.status), 13, UiHelper.MUTED, false);
                if (!snapshot.thesis.supervisors.isEmpty()) UiHelper.add(activity, profile, activity.getString(R.string.account_thesis_supervisor, snapshot.thesis.supervisors), 13, UiHelper.MUTED, false);
                if (!snapshot.thesis.reviewers.isEmpty()) UiHelper.add(activity, profile, activity.getString(R.string.account_thesis_reviewer, snapshot.thesis.reviewers), 13, UiHelper.MUTED, false);
            }


        }

        renderAcademicTerm(activity, content, d);

        if (demo || connected) {
            content.addView(UiHelper.button(activity, activity.getString(demo ? R.string.btn_simulate_changes : R.string.btn_check_now), activity::sync, false));
        }

        if (connected) {
            LinearLayout box = UiHelper.card(activity, content, Color.WHITE);
            UiHelper.add(activity, box, activity.getString(R.string.sync_settings_title), 19, UiHelper.INK, true);
            UiHelper.note(activity, box, activity.getString(R.string.monitoring_last_success) + "\n"
                + UiHelper.timestamp(d == null ? 0 : d.optLong("successAt")), UiHelper.MUTED);
            if (!state.optString("error").isEmpty()) {
                UiHelper.note(activity, box, state.optString("error"), Color.rgb(167, 49, 49));
                if (state.optLong("retryAt") > System.currentTimeMillis()) UiHelper.note(activity, box,
                    activity.getString(R.string.home_next_retry, UiHelper.timestamp(state.optLong("retryAt"))), UiHelper.MUTED);
            }
            String authorizedScopes = state.optString("scopes");
            UiHelper.add(activity, box, activity.getString(R.string.account_oauth_consent, authorizedScopes), 13, UiHelper.MUTED, false);
            if (!pl.vistula.checker.core.StudentServices.hasScope(authorizedScopes, "payments")) {
                UiHelper.note(activity, box, activity.getString(R.string.account_warn_no_payments_scope), Color.rgb(180, 110, 0));
            }
            MaterialSwitch toggle = new MaterialSwitch(activity);
            toggle.setText(R.string.background_check);
            toggle.setTextColor(UiHelper.foreground(activity, UiHelper.INK));
            toggle.setChecked(state.optBoolean("enabled"));
            if (state.optBoolean("authorizationError")) {
                toggle.setEnabled(false);
                UiHelper.note(activity, box, activity.getString(R.string.account_session_expired), Color.rgb(167, 49, 49));
            }
            box.addView(toggle);
            toggle.setOnCheckedChangeListener((v, checked) -> listener.onToggleEnabled(checked));

            UiHelper.add(activity, box, activity.getString(R.string.sync_frequency), 14, UiHelper.MUTED, true);
            Spinner interval = new Spinner(activity);
            interval.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, new String[]{
                activity.getString(R.string.interval_15m),
                activity.getString(R.string.interval_30m),
                activity.getString(R.string.interval_1h),
                activity.getString(R.string.interval_2h)
            }));
            long[] values = new long[]{15L, 30L, 60L, 120L};
            long selected = state.optLong("interval", 30L);
            int position = 1;
            for (int i = 0; i < values.length; ++i) {
                if (selected == values[i]) position = i;
            }
            interval.setSelection(position);
            box.addView(interval);
            box.addView(UiHelper.button(activity, activity.getString(R.string.btn_save_frequency), () -> {
                listener.onUpdateInterval(values[interval.getSelectedItemPosition()]);
            }, false));
            box.addView(UiHelper.button(activity, activity.getString(R.string.btn_disconnect_account), () -> {
                new MaterialAlertDialogBuilder(activity)
                    .setTitle(activity.getString(R.string.account_disconnect_title))
                    .setMessage(activity.getString(R.string.account_disconnect_message))
                    .setNegativeButton(activity.getString(R.string.btn_cancel), null)
                    .setPositiveButton(activity.getString(R.string.account_disconnect_btn), (dialog, which) -> listener.onDisconnect())
                    .show();
            }, false));
        } else {
            LinearLayout box = UiHelper.card(activity, content, Color.WHITE);
            UiHelper.add(activity, box, activity.getString(R.string.connect_usos_title), 20, UiHelper.INK, true);
            UiHelper.add(activity, box, activity.getString(R.string.connect_usos_desc), 14, UiHelper.MUTED, false);

            String buildKey = BuildConfig.USOS_KEY != null ? BuildConfig.USOS_KEY.trim() : "";
            String buildSecret = BuildConfig.USOS_SECRET != null ? BuildConfig.USOS_SECRET.trim() : "";
            String defaultKey = state.optString("key", buildKey);
            if (defaultKey.isEmpty()) defaultKey = buildKey;
            String defaultSecret = state.optString("secret", buildSecret);
            if (defaultSecret.isEmpty()) defaultSecret = buildSecret;

            boolean pending = !state.optString("requestToken").isEmpty();
            boolean credentialsReady = !defaultKey.isEmpty() && !defaultSecret.isEmpty();
            UiHelper.note(activity, box, activity.getString(pending ? R.string.login_step_pin :
                credentialsReady ? R.string.login_ready : R.string.login_setup), UiHelper.MUTED);
            LinearLayout setup = new LinearLayout(activity);
            setup.setOrientation(LinearLayout.VERTICAL);
            box.addView(setup);
            setup.setVisibility(pending ? android.view.View.GONE : android.view.View.VISIBLE);
            LinearLayout advanced = new LinearLayout(activity);
            advanced.setOrientation(LinearLayout.VERTICAL);
            setup.addView(advanced);
            advanced.setVisibility(credentialsReady ? android.view.View.GONE : android.view.View.VISIBLE);
            box.addView(UiHelper.button(activity, activity.getString(R.string.login_settings), () -> {
                if (setup.getVisibility() == android.view.View.GONE) {
                    setup.setVisibility(android.view.View.VISIBLE);
                    advanced.setVisibility(android.view.View.VISIBLE);
                } else advanced.setVisibility(advanced.getVisibility() == android.view.View.VISIBLE ? android.view.View.GONE : android.view.View.VISIBLE);
            }, false));
            advanced.addView(UiHelper.button(activity, activity.getString(R.string.btn_how_to_register), () -> showApiKeyHelpDialog(activity), false));
            EditText key = UiHelper.field(activity, advanced, "Consumer Key", defaultKey, false);
            EditText secret = UiHelper.field(activity, advanced, "Consumer Secret", defaultSecret, true);
            if (!buildKey.isEmpty()) {
                UiHelper.note(activity, advanced, "Wczytano klucz aplikacji (Consumer Key & Secret) z pliku local.properties.", UiHelper.GREEN);
            }
            EditText scopes = UiHelper.field(activity, advanced, "Zakresy zgody OAuth (oddzielone |)", state.optString("scopes", "grades|studies|payments|cards|offline_access"), false);
            HorizontalScrollView scopeScroll = new HorizontalScrollView(activity);
            scopeScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout scopeRow = new LinearLayout(activity);
            scopeRow.setOrientation(LinearLayout.HORIZONTAL);
            Button b1 = UiHelper.button(activity, activity.getString(R.string.scope_recommended), () -> scopes.setText("grades|studies|payments|cards|offline_access"), false);
            Button b2 = UiHelper.button(activity, activity.getString(R.string.scope_basic), () -> scopes.setText("grades|studies|offline_access"), false);
            Button b3 = UiHelper.button(activity, activity.getString(R.string.scope_complete), () -> scopes.setText("grades|studies|payments|cards|adm_documents|photo|events|email|offline_access"), false);
            b1.setMinHeight(UiHelper.dp(activity, 48)); b1.setTextSize(12);
            b2.setMinHeight(UiHelper.dp(activity, 48)); b2.setTextSize(12);
            b3.setMinHeight(UiHelper.dp(activity, 48)); b3.setTextSize(12);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2); sp.rightMargin = UiHelper.dp(activity, 6);
            scopeRow.addView(b1, sp);
            scopeRow.addView(b2, sp);
            scopeRow.addView(b3, sp);
            Button cardsScopes = UiHelper.button(activity, activity.getString(R.string.cards_scope_profile), () -> {
                java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>();
                for (String value : scopes.getText().toString().split("\\|")) if (!value.trim().isEmpty()) values.add(value.trim());
                values.add("cards"); values.add("adm_documents");
                scopes.setText(String.join("|", values));
            }, false);
            scopeRow.addView(cardsScopes, new LinearLayout.LayoutParams(-2, -2));
            scopeScroll.addView(scopeRow);
            advanced.addView(scopeScroll);
            UiHelper.add(activity, advanced, activity.getString(R.string.account_scopes_hint), 12, UiHelper.MUTED, false);
            UiHelper.note(activity, advanced, activity.getString(R.string.services_scope_hint), UiHelper.MUTED);

            setup.addView(UiHelper.button(activity, activity.getString(R.string.btn_login_browser), () -> {
                String k = key.getText().toString().trim();
                if (k.isEmpty()) k = buildKey;
                String sec = secret.getText().toString().trim();
                if (sec.isEmpty()) sec = buildSecret;
                String sc = scopes.getText().toString().trim();
                if (!k.isEmpty() && !sec.isEmpty()) {
                    List<String> cleanScopes = new ArrayList<>();
                    for (String part : sc.split("\\|")) {
                        String trimmed = part.trim();
                        if (!trimmed.isEmpty() && !cleanScopes.contains(trimmed)) cleanScopes.add(trimmed);
                    }
                    if (!cleanScopes.contains("grades")) cleanScopes.add("grades");
                    if (!cleanScopes.contains("studies")) cleanScopes.add("studies");
                    if (!cleanScopes.contains("offline_access")) cleanScopes.add("offline_access");
                    listener.onBeginOAuth(k, sec, String.join("|", cleanScopes));
                } else {
                    advanced.setVisibility(android.view.View.VISIBLE);
                    if (k.isEmpty()) key.setError(activity.getString(R.string.login_key_required));
                    if (sec.isEmpty()) secret.setError(activity.getString(R.string.login_secret_required));
                }
            }, true));

            if (!state.optString("requestToken").isEmpty()) {
                UiHelper.add(activity, box, activity.getString(R.string.pin_hint), 14, UiHelper.MUTED, false);
                String initialPin = detectedPin != null ? detectedPin : "";
                EditText pin = UiHelper.field(activity, box, activity.getString(R.string.account_pin_field_hint), initialPin, true);
                if (!initialPin.isEmpty()) {
                    UiHelper.note(activity, box, activity.getString(R.string.pin_detected_clipboard), UiHelper.GREEN);
                }
                box.addView(UiHelper.button(activity, activity.getString(R.string.btn_paste_pin), () -> {
                    try {
                        ClipboardManager cb = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cb != null && cb.hasPrimaryClip() && cb.getPrimaryClip() != null && cb.getPrimaryClip().getItemCount() > 0) {
                            ClipData.Item item = cb.getPrimaryClip().getItemAt(0);
                            if (item != null && item.getText() != null) {
                                String clip = item.getText().toString().trim().replaceAll("\\s+", "");
                                pin.setText(clip);
                                Toast.makeText(activity, R.string.account_toast_pin_pasted, Toast.LENGTH_SHORT).show();
                            }
                        }
                    } catch (Exception ignored) {}
                }, false));
                Runnable confirmPin = () -> {
                    String value = pin.getText().toString().trim().replaceAll("\\s+", "");
                    if (value.isEmpty()) {
                        pin.setError(activity.getString(R.string.account_toast_enter_pin));
                    } else {
                        listener.onConfirmPin(value);
                    }
                };
                pin.setSingleLine(true);
                // setSingleLine replaces the password transformation on Android.
                pin.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
                pin.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE | android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
                pin.setOnEditorActionListener((v, action, event) -> {
                    if (action != android.view.inputmethod.EditorInfo.IME_ACTION_DONE) return false;
                    confirmPin.run(); return true;
                });
                box.addView(UiHelper.button(activity, activity.getString(R.string.btn_confirm_pin), confirmPin, true));
                box.addView(UiHelper.button(activity, activity.getString(R.string.btn_return_to_browser),
                    () -> UiHelper.browser(activity, "https://usosapps.vistula.edu.pl/services/oauth/authorize?oauth_token=" + OAuth.encode(state.optString("requestToken"))), false));
            }

            content.addView(UiHelper.button(activity, activity.getString(R.string.btn_start_demo), listener::onStartDemo, false));
        }

        // Notifications
        LinearLayout notices = UiHelper.card(activity, content, Color.WHITE);
        UiHelper.add(activity, notices, activity.getString(R.string.account_notices_heading), 18, UiHelper.INK, true);
        UiHelper.add(activity, notices, NotificationManagerCompat.from(activity).areNotificationsEnabled() ? activity.getString(R.string.account_notif_system_allowed) : activity.getString(R.string.account_notif_system_blocked), 14, UiHelper.MUTED, false);
        notices.addView(UiHelper.button(activity, activity.getString(R.string.account_btn_system_notif_settings), listener::onRequestNotifications, false));
        notices.addView(UiHelper.button(activity, activity.getString(R.string.account_btn_send_test_notif), () -> {
            NotificationHelper.sendTestNotification(activity);
            Toast.makeText(activity, R.string.account_toast_test_notif_sent, Toast.LENGTH_SHORT).show();
        }, false));

        SharedPreferences toolsPrefs = activity.getSharedPreferences("student_tools", Context.MODE_PRIVATE);
        boolean remindersOn = toolsPrefs.getBoolean(ClassReminderReceiver.PREF_REMINDERS_ENABLED, false);
        int reminderMin = toolsPrefs.getInt(ClassReminderReceiver.PREF_REMINDER_MINUTES, 15);

        LinearLayout reminderRow = new LinearLayout(activity);
        reminderRow.setOrientation(LinearLayout.VERTICAL);
        reminderRow.setPadding(0, UiHelper.dp(activity, 8), 0, UiHelper.dp(activity, 4));

        MaterialSwitch reminderSwitch = new MaterialSwitch(activity);
        reminderSwitch.setText(activity.getString(R.string.account_reminder_switch_label));
        reminderSwitch.setChecked(remindersOn);
        reminderSwitch.setTextColor(UiHelper.foreground(activity, UiHelper.INK));
        reminderSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            toolsPrefs.edit().putBoolean(ClassReminderReceiver.PREF_REMINDERS_ENABLED, isChecked).apply();
            if (isChecked) {
                ClassReminderReceiver.scheduleNext(activity);
                if (!ClassReminderReceiver.canNotify(activity)) listener.onRequestNotifications();
                Toast.makeText(activity, R.string.account_toast_reminders_enabled, Toast.LENGTH_SHORT).show();
            } else {
                ClassReminderReceiver.cancel(activity);
                Toast.makeText(activity, R.string.account_toast_reminders_disabled, Toast.LENGTH_SHORT).show();
            }
            activity.navigate(4);
        });
        reminderRow.addView(reminderSwitch);

        if (remindersOn) {
            if (!ClassReminderReceiver.canNotify(activity)) UiHelper.note(activity, reminderRow,
                activity.getString(R.string.account_reminder_permission_missing), UiHelper.MUTED);
            UiHelper.note(activity, reminderRow, activity.getString(R.string.account_reminder_lead_time_label), UiHelper.MUTED);
            UiHelper.chips(activity, reminderRow,
                new String[]{"15", "30", "45"},
                new String[]{
                    activity.getString(R.string.account_lead_time_15),
                    activity.getString(R.string.account_lead_time_30),
                    activity.getString(R.string.account_lead_time_45)
                },
                String.valueOf(reminderMin), val -> {
                    int min = Integer.parseInt(val);
                    toolsPrefs.edit().putInt(ClassReminderReceiver.PREF_REMINDER_MINUTES, min).apply();
                    ClassReminderReceiver.scheduleNext(activity);
                    activity.navigate(4);
                });
        }
        notices.addView(reminderRow);
        UiHelper.note(activity, notices, activity.getString(R.string.account_channels_note), UiHelper.MUTED);

        // Widgets
        LinearLayout widgetsCard = UiHelper.card(activity, content, Color.WHITE);
        UiHelper.add(activity, widgetsCard, activity.getString(R.string.account_widgets_heading), 18, UiHelper.INK, true);
        UiHelper.add(activity, widgetsCard, activity.getString(R.string.account_widgets_desc), 13, UiHelper.MUTED, false);
        widgetsCard.addView(UiHelper.button(activity, activity.getString(R.string.account_btn_update_widgets), () -> {
            pl.vistula.checker.widget.TodayScheduleWidget.updateAll(activity);
            pl.vistula.checker.widget.QuickStatusWidget.updateAll(activity);
            Toast.makeText(activity, R.string.account_toast_widgets_updated, Toast.LENGTH_SHORT).show();
        }, false));

        content.addView(UiHelper.button(activity, activity.getString(R.string.account_btn_open_usosapps), () -> UiHelper.browser(activity, "https://usosapps.vistula.edu.pl/"), false));
        AppInfo.render(activity, content);
    }

    private static void renderAcademicTerm(MainActivity activity, LinearLayout content, JSONObject data) {
        JSONObject raw = data == null ? null : data.optJSONObject("snapshot");
        if (raw == null) return;
        try {
            Snapshot snap = Snapshot.from(raw);
            LocalDate today = LocalDate.now(UsosClient.ZONE);
            AcademicTerm currentTerm = null;
            for (AcademicTerm t : snap.terms.values()) {
                if (t.isCurrent(today)) { currentTerm = t; break; }
            }
            if (currentTerm == null && !snap.terms.isEmpty()) currentTerm = snap.terms.values().iterator().next();
            if (currentTerm != null) {
                LinearLayout termBox = UiHelper.card(activity, content, Color.rgb(238, 245, 242));
                UiHelper.add(activity, termBox, activity.getString(R.string.academic_calendar_title), 11, UiHelper.GREEN, true);
                UiHelper.add(activity, termBox, currentTerm.name, 17, UiHelper.INK, true);
                long days = currentTerm.daysRemaining(today);
                String termInfo = days >= 0
                    ? activity.getString(R.string.home_term_remaining, days, currentTerm.endDate)
                    : activity.getString(R.string.home_term_ended, currentTerm.endDate);
                UiHelper.add(activity, termBox, termInfo, 13, UiHelper.MUTED, false);
            }

        } catch (org.json.JSONException ignored) {
            // Missing cached academic information must not block account settings.
        }
    }

    private static void showApiKeyHelpDialog(MainActivity activity) {
        new MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.api_key_help_title))
            .setMessage(activity.getString(R.string.api_key_help_message))
            .setPositiveButton(activity.getString(R.string.btn_register_usosapps), (d, w) -> UiHelper.browser(activity, "https://usosapps.vistula.edu.pl/developers/"))
            .setNegativeButton(activity.getString(R.string.btn_close), null)
            .show();
    }
}
