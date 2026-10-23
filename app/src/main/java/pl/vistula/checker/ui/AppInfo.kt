package pl.vistula.checker.ui

import android.content.Context
import android.graphics.Color
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.IOException
import java.nio.charset.StandardCharsets
import pl.vistula.checker.BuildConfig
import pl.vistula.checker.R

/** Attribution and an offline copy of the project's license. */
class AppInfo private constructor() {
    companion object {
        @JvmStatic
        fun render(context: Context, parent: LinearLayout) {
            val card = UiHelper.card(context, parent, Color.WHITE)
            UiHelper.add(context, card, context.getString(R.string.about_title), 18, UiHelper.INK, true)
            UiHelper.add(context, card, context.getString(R.string.about_author), 14, UiHelper.INK, false)
            card.addView(UiHelper.actionRow(context, context.getString(R.string.design_credit), {
                UiHelper.browser(context, "https://github.com/Kaydjen")
            }, false))
            UiHelper.note(context, card, context.getString(R.string.about_version, BuildConfig.VERSION_NAME), UiHelper.MUTED)
            if (context is AppCompatActivity) {
                card.addView(
                    UiHelper.button(
                        context,
                        context.getString(R.string.updates_title),
                        { ReleaseUpdatesDialog.show(context) },
                        false
                    )
                )
            }
            UiHelper.note(context, card, context.getString(R.string.about_license), UiHelper.MUTED)
            card.addView(
                UiHelper.button(
                    context,
                    context.getString(R.string.about_license_open),
                    { showLicense(context) },
                    false
                )
            )
        }

        private fun showLicense(context: Context) {
            val text = try {
                context.resources.openRawResource(R.raw.license_mit).use { stream ->
                    String(stream.readBytes(), StandardCharsets.UTF_8)
                }
            } catch (e: IOException) {
                Toast.makeText(context, R.string.about_license_error, Toast.LENGTH_SHORT).show()
                return
            }
            MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.about_license))
                .setMessage(text + "\n\n" + context.getString(R.string.about_license_scope))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }
}

