package pl.vistula.checker.ui

import android.graphics.Color
import android.widget.LinearLayout
import pl.vistula.checker.MainActivity
import pl.vistula.checker.R

/** A local first-run guide. Credentials are entered only in the existing encrypted account flow. */
object WelcomeScreenController {
    fun render(activity: MainActivity, content: LinearLayout, step: Int) {
        UiHelper.add(activity, content, activity.getString(R.string.welcome_progress, step + 1), 12, Tone.MUTED, false)
        val title = UiHelper.text(activity, activity.getString(if (step == 0) R.string.welcome_title else R.string.welcome_key_title), 30, Tone.INK, true)
        title.setPadding(0, UiHelper.dp(activity, 24), 0, UiHelper.dp(activity, 16))
        androidx.core.view.ViewCompat.setAccessibilityHeading(title, true)
        content.addView(title)
        UiHelper.note(activity, content, activity.getString(if (step == 0) R.string.welcome_intro else R.string.welcome_key_intro), Tone.MUTED)
        val box = UiHelper.card(activity, content)
        if (step == 0) {
            UiHelper.add(activity, box, activity.getString(R.string.welcome_own_key), 17, Tone.INK, true)
            UiHelper.note(activity, box, activity.getString(R.string.welcome_privacy), Tone.MUTED)
            UiHelper.note(activity, box, activity.getString(R.string.welcome_login_explained), Tone.MUTED)
        } else {
            UiHelper.add(activity, box, activity.getString(R.string.welcome_get_key), 16, Tone.INK, true)
            UiHelper.note(activity, box, activity.getString(R.string.welcome_form_details), Tone.MUTED)
            box.addView(UiHelper.button(activity, activity.getString(R.string.btn_register_usosapps), {
                UiHelper.browser(activity, "https://usosapps.vistula.edu.pl/developers/")
            }, false))
            UiHelper.add(activity, box, activity.getString(R.string.welcome_copy_keys), 16, Tone.INK, true)
            UiHelper.note(activity, box, activity.getString(R.string.welcome_key_meaning), Tone.MUTED)
            UiHelper.note(activity, box, activity.getString(R.string.welcome_no_key), Tone.MUTED)
        }
        content.addView(UiHelper.button(activity, activity.getString(if (step == 0) R.string.welcome_start else R.string.welcome_enter_keys), {
            if (step == 0) activity.showWelcome(1) else activity.finishWelcome(false, true)
        }, true).apply { id = R.id.welcome_continue })
        if (step > 0) content.addView(UiHelper.actionRow(activity, activity.getString(R.string.welcome_back), { activity.showWelcome(0) }))
        content.addView(UiHelper.button(activity, activity.getString(R.string.btn_start_demo), { activity.finishWelcome(true, false) }, false).apply { id = R.id.welcome_demo })
        content.addView(UiHelper.actionRow(activity, activity.getString(R.string.welcome_skip), { activity.finishWelcome(false, false) }).apply { id = R.id.welcome_skip })
        content.addView(UiHelper.actionRow(activity, activity.getString(R.string.language_title), { Language.choose(activity) }))
    }
}
