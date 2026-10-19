package pl.vistula.checker.ui

import android.app.UiModeManager
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import pl.vistula.checker.R

/** Device-local appearance preference, independent of the connected student account. */
class Appearance private constructor() {
    companion object {
        @JvmStatic
        fun mode(context: Context): Int {
            val stored = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).all["nightMode"]
            return if (stored is Int && supported(stored)) {
                stored
            } else {
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        }

        private fun supported(mode: Int): Boolean {
            return mode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM ||
                mode == AppCompatDelegate.MODE_NIGHT_NO ||
                mode == AppCompatDelegate.MODE_NIGHT_YES
        }

        /** Android 12+ persists this app-local choice for cold starts and the launch screen. */
        @JvmStatic
        fun apply(context: Context) {
            val mode = mode(context)
            val manager = context.getSystemService(UiModeManager::class.java)
            if (manager != null) {
                // AUTO clears the package override, inheriting the phone's current configuration.
                manager.setApplicationNightMode(
                    when (mode) {
                        AppCompatDelegate.MODE_NIGHT_NO -> UiModeManager.MODE_NIGHT_NO
                        AppCompatDelegate.MODE_NIGHT_YES -> UiModeManager.MODE_NIGHT_YES
                        else -> UiModeManager.MODE_NIGHT_AUTO
                    }
                )
            }
        }

        @JvmStatic
        fun setMode(context: Context, mode: Int) {
            require(supported(mode)) { "Nieprawidłowy tryb wyglądu." }
            context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
                .edit()
                .putInt("nightMode", mode)
                .apply()
            apply(context)
        }

        @JvmStatic
        fun choose(activity: AppCompatActivity) {
            val modes = intArrayOf(
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                AppCompatDelegate.MODE_NIGHT_NO,
                AppCompatDelegate.MODE_NIGHT_YES
            )
            val currentMode = mode(activity)
            var selected = 0
            for (i in modes.indices) {
                if (modes[i] == currentMode) selected = i
            }
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.appearance_title)
                .setSingleChoiceItems(R.array.appearance_modes, selected) { dialog, which ->
                    dialog.dismiss()
                    setMode(activity, modes[which])
                }
                .setNegativeButton(R.string.btn_close, null)
                .show()
        }
    }
}

