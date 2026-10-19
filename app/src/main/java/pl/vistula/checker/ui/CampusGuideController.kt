package pl.vistula.checker.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.widget.LinearLayout
import pl.vistula.checker.MainActivity
import pl.vistula.checker.R

/**
 * Controller for rendering the Campus Guide and Student Essentials screen.
 */
class CampusGuideController private constructor() {

    companion object {
        @JvmStatic
        fun render(activity: MainActivity, content: LinearLayout) {
            ToolScreenUi.header(activity, content, R.string.tool_campus_title, null)
            // Campus Location
            val campusCard = UiHelper.section(activity, content, Color.WHITE)
            UiHelper.add(activity, campusCard, activity.getString(R.string.campus_name), 18, UiHelper.INK, true)
            UiHelper.add(activity, campusCard, activity.getString(R.string.campus_address), 15, UiHelper.GREEN, true)
            UiHelper.add(activity, campusCard, activity.getString(R.string.campus_metro), 13, UiHelper.MUTED, false)
            campusCard.addView(
                UiHelper.actionRow(
                    activity,
                    activity.getString(R.string.campus_btn_maps),
                    {
                        try {
                            val mapIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("geo:52.158145,21.034326?q=Akademia+Finansów+i+Biznesu+Vistula,+ul.+Stokłosy+3,+Warszawa")
                            )
                            activity.startActivity(mapIntent)
                        } catch (_: Exception) {
                            UiHelper.browser(
                                activity,
                                "https://www.google.com/maps/search/?api=1&query=Akademia%20Finans%C3%B3w%20i%20Biznesu%20Vistula%20Stok%C5%82osy%203%20Warszawa"
                            )
                        }
                    },
                    true
                )
            )
            campusCard.addView(
                UiHelper.actionRow(
                    activity,
                    activity.getString(R.string.campus_btn_website),
                    { UiHelper.browser(activity, "https://vistula.edu.pl/") },
                    false
                )
            )

            // Dziekanat
            val dziekanatCard = UiHelper.section(activity, content, Color.WHITE)
            UiHelper.add(activity, dziekanatCard, activity.getString(R.string.campus_dziekanat_title), 18, UiHelper.INK, true)
            UiHelper.add(activity, dziekanatCard, activity.getString(R.string.campus_dziekanat_desc), 13, UiHelper.MUTED, false)
            UiHelper.add(activity, dziekanatCard, activity.getString(R.string.campus_dziekanat_hours), 13, UiHelper.INK, false)
            dziekanatCard.addView(
                UiHelper.actionRow(
                    activity,
                    activity.getString(R.string.campus_dziekanat_call),
                    {
                        try {
                            activity.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+48224572300")))
                        } catch (_: Exception) {
                        }
                    },
                    false
                )
            )
            dziekanatCard.addView(
                UiHelper.actionRow(
                    activity,
                    activity.getString(R.string.campus_dziekanat_email),
                    {
                        try {
                            activity.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:dziekanat@vistula.edu.pl")))
                        } catch (_: Exception) {
                        }
                    },
                    false
                )
            )

            // Finance
            val financeCard = UiHelper.section(activity, content, Color.WHITE)
            UiHelper.add(activity, financeCard, activity.getString(R.string.campus_finanse_title), 18, UiHelper.INK, true)
            UiHelper.add(activity, financeCard, activity.getString(R.string.campus_finanse_desc), 13, UiHelper.MUTED, false)
            financeCard.addView(
                UiHelper.actionRow(
                    activity,
                    activity.getString(R.string.campus_finanse_view_subaccounts),
                    { activity.navigate(8) },
                    true
                )
            )
            financeCard.addView(
                UiHelper.actionRow(
                    activity,
                    activity.getString(R.string.campus_finanse_email),
                    {
                        try {
                            activity.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:finanse@vistula.edu.pl")))
                        } catch (_: Exception) {
                        }
                    },
                    false
                )
            )

            // Library
            val libraryCard = UiHelper.section(activity, content, Color.WHITE)
            UiHelper.add(activity, libraryCard, activity.getString(R.string.campus_library_title), 18, UiHelper.INK, true)
            UiHelper.add(activity, libraryCard, activity.getString(R.string.campus_library_desc), 13, UiHelper.MUTED, false)
            libraryCard.addView(
                UiHelper.actionRow(
                    activity,
                    activity.getString(R.string.campus_library_email),
                    {
                        try {
                            activity.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:biblioteka@vistula.edu.pl")))
                        } catch (_: Exception) {
                        }
                    },
                    false
                )
            )

            // Wi-Fi eduroam
            val wifiCard = UiHelper.section(activity, content, Color.WHITE)
            UiHelper.add(activity, wifiCard, activity.getString(R.string.campus_wifi_title), 18, UiHelper.INK, true)
            UiHelper.add(activity, wifiCard, activity.getString(R.string.campus_wifi_desc), 13, UiHelper.MUTED, false)
            UiHelper.add(activity, wifiCard, activity.getString(R.string.campus_wifi_instructions), 13, UiHelper.INK, false)
            wifiCard.addView(
                UiHelper.actionRow(
                    activity,
                    activity.getString(R.string.campus_wifi_copy),
                    { UiHelper.copyToClipboard(activity, "SSID", "eduroam") },
                    false
                )
            )

            // IT Support
            val itCard = UiHelper.section(activity, content, Color.WHITE)
            UiHelper.add(activity, itCard, activity.getString(R.string.campus_it_title), 18, UiHelper.INK, true)
            UiHelper.add(activity, itCard, activity.getString(R.string.campus_it_desc), 13, UiHelper.MUTED, false)
            itCard.addView(
                UiHelper.actionRow(
                    activity,
                    activity.getString(R.string.campus_it_email),
                    {
                        try {
                            activity.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:pomocit@vistula.edu.pl")))
                        } catch (_: Exception) {
                        }
                    },
                    false
                )
            )

            // Regulations
            val linksCard = UiHelper.section(activity, content, Color.WHITE)
            UiHelper.add(activity, linksCard, activity.getString(R.string.campus_rules_title), 18, UiHelper.INK, true)
            linksCard.addView(
                UiHelper.actionRow(
                    activity,
                    activity.getString(R.string.campus_rules_studies),
                    { UiHelper.browser(activity, "https://vistula.edu.pl/uczelnia/wazne-dokumenty") },
                    false
                )
            )
            linksCard.addView(
                UiHelper.actionRow(
                    activity,
                    activity.getString(R.string.campus_rules_templates),
                    { UiHelper.browser(activity, "https://vistula.edu.pl/student/sprawy-studenckie") },
                    false
                )
            )
            linksCard.addView(
                UiHelper.actionRow(
                    activity,
                    activity.getString(R.string.campus_rules_programs),
                    { UiHelper.browser(activity, "https://usosweb.vistula.edu.pl/") },
                    false
                )
            )
        }
    }
}

