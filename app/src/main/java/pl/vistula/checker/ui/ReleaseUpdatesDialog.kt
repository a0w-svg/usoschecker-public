package pl.vistula.checker.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import pl.vistula.checker.BuildConfig
import pl.vistula.checker.R
import pl.vistula.checker.ReleaseUpdates
import pl.vistula.checker.core.AppRelease

class ReleaseUpdatesDialog private constructor() {
    companion object {
        @JvmStatic
        fun show(activity: AppCompatActivity) {
            if (activity.isFinishing || activity.isDestroyed) return
            val application = activity.applicationContext
            val layout = LinearLayout(activity)
            layout.orientation = LinearLayout.VERTICAL
            val padding = (24 * activity.resources.displayMetrics.density).roundToInt()
            layout.setPadding(padding, 0, padding, padding)
            val status = UiHelper.text(activity, "", 15, UiHelper.INK, false)
            layout.addView(status)
            status.setTextIsSelectable(true)
            var latest: AppRelease? = ReleaseUpdates.cached(application)
            render(activity, status, latest, false)
            val download = UiHelper.button(
                activity,
                activity.getString(
                    if (latest != null && latest!!.newerThan(BuildConfig.VERSION_NAME)) {
                        R.string.updates_download
                    } else {
                        R.string.updates_release_history
                    }
                ),
                {
                    val release = latest
                    UiHelper.browser(
                        activity,
                        if (release != null && release.newerThan(BuildConfig.VERSION_NAME)) {
                            release.apkUrl
                        } else {
                            AppRelease.REPOSITORY + "/releases/latest"
                        }
                    )
                },
                false
            )
            layout.addView(download)
            val enabled = MaterialSwitch(activity)
            enabled.setText(R.string.updates_notify_enabled)
            enabled.isChecked = ReleaseUpdates.enabled(application)
            layout.addView(enabled)
            enabled.setOnCheckedChangeListener { _, checked ->
                ReleaseUpdates.setEnabled(application, checked)
                if (checked && Build.VERSION.SDK_INT >= 33 &&
                    activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
                }
            }
            layout.addView(
                UiHelper.button(
                    activity,
                    activity.getString(R.string.updates_notification_settings),
                    {
                        if (Build.VERSION.SDK_INT >= 33 &&
                            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
                        } else {
                            activity.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                            )
                        }
                    },
                    false
                )
            )
            UiHelper.note(activity, layout, activity.getString(R.string.updates_background_note), UiHelper.MUTED)
            UiHelper.add(
                activity,
                layout,
                activity.getString(R.string.updates_installed_notes, BuildConfig.VERSION_NAME),
                16,
                UiHelper.INK,
                true
            )
            UiHelper.note(activity, layout, installedNotes(activity), UiHelper.MUTED)
            val scroll = ScrollView(activity)
            scroll.addView(layout)
            val dialog = MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.updates_title)
                .setView(scroll)
                .setPositiveButton(R.string.updates_check, null)
                .setNegativeButton(R.string.btn_close, null)
                .create()
            val executor = Executors.newSingleThreadExecutor()
            val lifecycle = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY) dialog.dismiss()
            }
            activity.lifecycle.addObserver(lifecycle)
            dialog.setOnDismissListener {
                executor.shutdownNow()
                activity.lifecycle.removeObserver(lifecycle)
            }
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                    status.setText(R.string.updates_checking)
                    executor.execute {
                        var result: AppRelease? = null
                        try {
                            result = ReleaseUpdates.check(application, true)
                        } catch (_: Exception) {
                        }
                        val checked = result
                        Handler(Looper.getMainLooper()).post {
                            if (!dialog.isShowing || activity.isFinishing || activity.isDestroyed) return@post
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                            if (checked != null) latest = checked
                            download.setText(
                                if (latest != null && latest!!.newerThan(BuildConfig.VERSION_NAME)) {
                                    R.string.updates_download
                                } else {
                                    R.string.updates_release_history
                                }
                            )
                            render(activity, status, latest, checked != null)
                            if (checked == null) {
                                status.text = activity.getString(R.string.updates_error) + "\n\n" + status.text
                            }
                        }
                    }
                }
            }
            dialog.show()
        }

        private fun render(context: Context, status: TextView, release: AppRelease?, fresh: Boolean) {
            if (release != null && release.newerThan(BuildConfig.VERSION_NAME)) {
                status.text = context.getString(R.string.updates_available, release.version) + "\n\n" +
                    (if (release.notes.isBlank()) context.getString(R.string.updates_notes_missing) else release.notes) +
                    (if (fresh) "" else "\n\n" + context.getString(R.string.updates_cached))
            } else {
                status.text = context.getString(if (fresh) R.string.updates_current else R.string.updates_not_checked)
            }
        }

        private fun installedNotes(context: Context): String {
            return try {
                context.resources.openRawResource(R.raw.whats_new).use { input ->
                    String(input.readBytes(), StandardCharsets.UTF_8)
                }
            } catch (_: Exception) {
                context.getString(R.string.updates_notes_missing)
            }
        }
    }
}

