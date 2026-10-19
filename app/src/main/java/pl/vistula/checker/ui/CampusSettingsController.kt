package pl.vistula.checker.ui

import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject
import pl.vistula.checker.MainActivity
import pl.vistula.checker.R
import pl.vistula.checker.core.CampusAffiliation
import pl.vistula.checker.core.StudentServices

object CampusSettingsController {
    fun render(activity: MainActivity, content: LinearLayout, state: JSONObject) {
        val selected = CampusAffiliation.selected(state)
        UiHelper.heading(activity, content, CampusAffiliation.campuses[selected] ?: activity.getString(R.string.campus_unknown))
        UiHelper.note(activity, content, activity.getString(if (state.optString("campusOverride").isEmpty())
            R.string.campus_automatic_hint else R.string.campus_manual_hint), UiHelper.MUTED)
        UiHelper.note(activity, content, activity.getString(R.string.campus_filter_hint), UiHelper.MUTED)
        content.addView(UiHelper.button(activity, activity.getString(R.string.campus_choose), { choose(activity, state) }, true))
        if (activity.connected() && !activity.isDemo()) {
            content.addView(UiHelper.button(activity, activity.getString(R.string.campus_detect), {
                activity.refreshStudentServices("affiliation")
            }, false))
        }
        val entry = state.optJSONObject("studentServices")?.optJSONObject("affiliation") ?: JSONObject()
        StudentCardsController.status(activity, content, entry)
        val units = entry.optJSONObject("data")?.optJSONArray("units") ?: return
        for (i in 0 until units.length()) {
            val unit = units.getJSONObject(i)
            if (unit.optString("campus") != selected || selected.isEmpty()) continue
            UiHelper.add(activity, content, StudentServices.localized(unit.opt("name"), Language.currentLocale(activity).language), 16, UiHelper.INK, true)
            UiHelper.note(activity, content, StudentServices.text(unit, "address"), UiHelper.MUTED)
        }
    }

    fun choose(activity: MainActivity, state: JSONObject) {
        val keys = listOf("") + CampusAffiliation.campuses.keys
        val labels = arrayOf(activity.getString(R.string.campus_automatic)) + CampusAffiliation.campuses.values.toTypedArray()
        MaterialAlertDialogBuilder(activity).setTitle(R.string.campus_choose)
            .setSingleChoiceItems(labels, keys.indexOf(state.optString("campusOverride"))) { dialog, index ->
                dialog.dismiss()
                activity.selectCampus(keys[index])
            }.setNegativeButton(R.string.btn_cancel, null).show()
    }

    fun campusInformation(activity: MainActivity, content: LinearLayout, state: JSONObject) {
        val selected = CampusAffiliation.selected(state)
        if (selected.isEmpty()) {
            UiHelper.empty(activity, content, activity.getString(R.string.campus_unknown), activity.getString(R.string.campus_automatic_hint))
        } else {
            UiHelper.heading(activity, content, CampusAffiliation.campuses.getValue(selected))
            val entry = state.optJSONObject("studentServices")?.optJSONObject("affiliation") ?: JSONObject()
            StudentCardsController.status(activity, content, entry)
            val units = entry.optJSONObject("data")?.optJSONArray("units")
            var hasContacts = false
            if (units != null) for (i in 0 until units.length()) {
                val unit = units.getJSONObject(i)
                if (unit.optString("campus") != selected) continue
                val box = UiHelper.section(activity, content, android.graphics.Color.WHITE)
                UiHelper.heading(activity, box, StudentServices.localized(unit.opt("name"), Language.currentLocale(activity).language))
                for (key in listOf("address", "email")) {
                    val value = StudentServices.text(unit, key)
                    if (value.isNotEmpty()) {
                        hasContacts = true
                        UiHelper.add(activity, box, value, 16, UiHelper.INK, false)
                    }
                }
            }
            if (!hasContacts) UiHelper.note(activity, content, activity.getString(R.string.campus_contacts_unavailable), UiHelper.MUTED)
            content.addView(UiHelper.actionRow(activity, activity.getString(R.string.campus_student_portal), {
                UiHelper.browser(activity, "https://usosweb.vistula.edu.pl/")
            }, false))
            CampusAffiliation.website(selected)?.let { website ->
                content.addView(UiHelper.button(activity, activity.getString(R.string.campus_official_site), { UiHelper.browser(activity, website) }, true))
            }
        }
        content.addView(UiHelper.actionRow(activity, activity.getString(R.string.campus_choose), {
            activity.navigate(4)
            activity.openAccountPage("settings.campus")
        }, false))
    }
}
