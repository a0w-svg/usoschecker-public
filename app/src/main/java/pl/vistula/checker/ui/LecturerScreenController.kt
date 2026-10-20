package pl.vistula.checker.ui

import android.graphics.Color
import android.widget.LinearLayout
import org.json.JSONObject
import pl.vistula.checker.MainActivity
import pl.vistula.checker.R
import pl.vistula.checker.core.AccountRole
import pl.vistula.checker.core.StudentServices

object LecturerScreenController {
    fun render(activity: MainActivity, content: LinearLayout, state: JSONObject) {
        if (!AccountRole.lecturer(state)) return
        ToolScreenUi.header(activity, content, R.string.lecturer_title) { menu ->
            ToolScreenUi.action(menu, R.string.btn_refresh) { activity.refreshStudentServices("teaching") }
        }
        UiHelper.note(activity, content, activity.getString(R.string.lecturer_description), UiHelper.MUTED)
        content.addView(UiHelper.button(activity, activity.getString(R.string.lecturer_schedule), { activity.navigate(5) }, true))
        val entry = state.optJSONObject("studentServices")?.optJSONObject("teaching") ?: JSONObject()
        StudentCardsController.status(activity, content, entry)
        val courses = entry.optJSONObject("data")?.optJSONArray("courses")
        if (courses == null || courses.length() == 0) {
            UiHelper.note(activity, content, activity.getString(if (entry.has("fetchedAt")) R.string.lecturer_empty else R.string.services_load_hint), UiHelper.MUTED)
        } else for (i in 0 until courses.length()) {
            val course = courses.getJSONObject(i)
            val box = UiHelper.section(activity, content, Color.WHITE)
            val language = Language.currentLocale(activity).language
            UiHelper.add(activity, box, StudentServices.localized(course.opt("name"), language), 18, UiHelper.INK, true)
            UiHelper.note(activity, box, StudentServices.localized(course.opt("term"), language), UiHelper.MUTED)
        }
        content.addView(UiHelper.actionRow(activity, activity.getString(R.string.lecturer_usosweb), {
            UiHelper.browser(activity, "https://usosweb.vistula.edu.pl/kontroler.php?_action=dla_prac")
        }, false))
    }
}
