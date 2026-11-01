package pl.vistula.checker.ui

import androidx.core.content.edit
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.PersistableBundle
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.function.Consumer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import pl.vistula.checker.R
import pl.vistula.checker.UsosClient
import pl.vistula.checker.core.WebLinks

/**
 * Reusable UI styling and layout helpers for Vistula Checker.
 */
class UiHelper private constructor() {
    companion object {
        /**
         * Raw colours below are legacy: only the custom-drawn [pl.vistula.checker.DayTimelineView] and the theme tests use them.
         * Screens pass a [Tone] or [Surface] instead, which resolves through colour resources.
         */
        @JvmField
        val INK: Int = Color.rgb(27, 37, 32)

        @JvmField
        val GREEN: Int = Color.rgb(23, 107, 87)

        @JvmField
        val MUTED: Int = Color.rgb(89, 105, 96)

        @JvmField
        val BG: Int = Color.rgb(250, 252, 250)

        @JvmStatic
        fun isDark(context: Context): Boolean {
            return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }

        /** Resolve shared semantic colors consistently for XML and programmatic screens. */
        @JvmStatic
        fun foreground(context: Context, color: Int): Int {
            if (color == INK) return context.getColor(R.color.app_ink)
            if (color == MUTED) return context.getColor(R.color.app_muted)
            if (color == GREEN) return context.getColor(R.color.app_ink)
            if (color == Color.rgb(217, 119, 6) || color == Color.rgb(180, 83, 9)) {
                return context.getColor(R.color.app_warning)
            }
            if (color == Color.rgb(160, 48, 40) || color == Color.rgb(167, 49, 49)) {
                return context.getColor(R.color.app_error)
            }
            if (!isDark(context)) return color
            // Keep status colors distinguishable and legible on dark cards.
            val surface = Color.rgb(24, 24, 24)
            for (i in 0..20) {
                val candidate = ColorUtils.blendARGB(color, Color.WHITE, i / 20f)
                if (ColorUtils.calculateContrast(candidate, surface) >= 4.5) return candidate
            }
            return Color.WHITE
        }

        @JvmStatic
        fun background(context: Context, color: Int): Int {
            if (color == Color.WHITE) return context.getColor(R.color.app_card)
            if (color == BG) return context.getColor(R.color.app_background)
            if (color == INK) return context.getColor(R.color.app_brand_surface)
            if (color == GREEN) return context.getColor(R.color.app_accent)
            if (color == Color.rgb(232, 243, 236)) return context.getColor(R.color.app_button_surface)
            if (color == Color.rgb(214, 241, 229)) return context.getColor(R.color.app_accent_container)
            if (color == Color.rgb(240, 245, 241)) return context.getColor(R.color.app_surface_high)
            if (!isDark(context)) return color
            // Pale information/status panels become subtle tinted surfaces.
            if (ColorUtils.calculateLuminance(color) > .5) {
                return ColorUtils.blendARGB(Color.BLACK, color, .09f)
            }
            return color
        }

        /** Text for dynamically colored badges must contrast with their actual fill. */
        @JvmStatic
        fun onColor(context: Context, fill: Int): Int {
            val ink = context.getColor(R.color.app_ink)
            if (ColorUtils.calculateContrast(ink, fill) >= 4.5) return ink
            return if (ColorUtils.calculateContrast(Color.WHITE, fill) >= ColorUtils.calculateContrast(Color.BLACK, fill)) {
                Color.WHITE
            } else {
                Color.BLACK
            }
        }

        @JvmStatic
        fun dp(context: Context, value: Int): Int {
            return (value * context.resources.displayMetrics.density).roundToInt()
        }

        /** Text role from `res/values/vistula_components.xml` for an exact size and weight, or null if none matches. */
        private fun textStyle(size: Int, bold: Boolean): Int? = if (bold) {
            when (size) {
                28 -> R.style.Vistula_Text_Title
                20 -> R.style.Vistula_Text_Heading
                18 -> R.style.Vistula_Text_Heading18
                17 -> R.style.Vistula_Text_Subheading
                16 -> R.style.Vistula_Text_Strong16
                15 -> R.style.Vistula_Text_Strong
                14 -> R.style.Vistula_Text_Strong14
                else -> null
            }
        } else {
            when (size) {
                16 -> R.style.Vistula_Text_Note
                15 -> R.style.Vistula_Text_Body
                14 -> R.style.Vistula_Text_BodySmall
                13 -> R.style.Vistula_Text_Caption
                12 -> R.style.Vistula_Text_Micro
                else -> null
            }
        }

        private fun styleText(t: TextView, value: String?, size: Int, tone: Tone, bold: Boolean): TextView {
            t.text = value
            val style = textStyle(size, bold)
            if (style != null) {
                t.setTextAppearance(style)
            } else {
                t.textSize = size.toFloat()
                if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            }
            // INK is the layout's own text colour; other tones come from colour resources (day and night variants).
            if (tone != Tone.INK) t.setTextColor(t.context.getColor(tone.color))
            return t
        }

        @JvmStatic
        fun text(context: Context, value: String?, size: Int, tone: Tone, bold: Boolean): TextView {
            val t = LayoutInflater.from(context).inflate(R.layout.view_text, inflateHost(context), false) as TextView
            return styleText(t, value, size, tone, bold)
        }

        @JvmStatic
        fun add(context: Context, target: LinearLayout, value: String?, size: Int, tone: Tone, bold: Boolean) {
            val t = LayoutInflater.from(context).inflate(R.layout.view_text_block, target, false) as TextView
            // Body text in blocks is never smaller than 15sp, whatever size the caller asks for.
            target.addView(styleText(t, value, max(15, size), tone, bold))
        }

        @JvmStatic
        @JvmOverloads
        fun card(context: Context, parent: LinearLayout, surface: Surface = Surface.DEFAULT): LinearLayout {
            val box = LayoutInflater.from(context).inflate(R.layout.view_card, parent, false) as LinearLayout
            if (surface != Surface.DEFAULT) box.backgroundTintList = ColorStateList.valueOf(context.getColor(surface.color))
            parent.addView(box)
            return box
        }

        @JvmStatic
        fun tray(context: Context, parent: LinearLayout): LinearLayout {
            val box = LayoutInflater.from(context).inflate(R.layout.view_tray, parent, false) as LinearLayout
            parent.addView(box)
            return box
        }

        @JvmStatic
        fun trayDivider(context: Context, parent: LinearLayout) {
            parent.addView(LayoutInflater.from(context).inflate(R.layout.view_tray_divider, parent, false))
        }

        @JvmStatic
        fun sectionLabel(context: Context, parent: LinearLayout, title: String) {
            val label = LayoutInflater.from(context).inflate(R.layout.view_section_label, parent, false) as TextView
            label.text = title
            ViewCompat.setAccessibilityHeading(label, true)
            parent.addView(label)
        }

        @JvmStatic
        fun section(context: Context, parent: LinearLayout): LinearLayout {
            val box = LayoutInflater.from(context).inflate(R.layout.view_section, parent, false) as LinearLayout
            parent.addView(box)
            return box
        }

        /** Secondary content stays reachable without competing with the screen's main task. */
        @JvmStatic
        fun disclosure(context: Context, parent: LinearLayout, key: String, title: Int): LinearLayout {
            val prefs = context.getSharedPreferences("tool_layout", Context.MODE_PRIVATE)
            val group = LayoutInflater.from(context).inflate(R.layout.view_disclosure, parent, false) as LinearLayout
            val toggle = group.findViewById<MaterialButton>(R.id.disclosure_toggle)
            val body = group.findViewById<LinearLayout>(R.id.disclosure_body)
            toggle.text = context.getString(title)
            var expanded = prefs.getBoolean(key, false)
            fun update() {
                body.visibility = if (expanded) View.VISIBLE else View.GONE
                toggle.setCompoundDrawablesWithIntrinsicBounds(0, 0,
                    if (expanded) R.drawable.ic_tool_collapse else R.drawable.ic_tool_expand, 0)
                ViewCompat.setStateDescription(toggle, context.getString(
                    if (expanded) R.string.details_expanded else R.string.details_collapsed
                ))
            }
            toggle.setOnClickListener {
                expanded = !expanded
                prefs.edit { putBoolean(key, expanded) }
                update()
            }
            parent.addView(group)
            update()
            return body
        }

        /** Detached widgets are inflated against a LinearLayout parent so their XML width and margins are kept for callers that add them to one. */
        private fun inflateHost(context: Context): LinearLayout = LinearLayout(context)

        /** A labelled switch (48dp touch target, theme text colour) inflated from `view_switch_row`. */
        @JvmStatic
        fun switchRow(context: Context, parent: LinearLayout, label: String, checked: Boolean): com.google.android.material.materialswitch.MaterialSwitch {
            val row = LayoutInflater.from(context).inflate(R.layout.view_switch_row, parent, false) as com.google.android.material.materialswitch.MaterialSwitch
            row.text = label
            row.isChecked = checked
            parent.addView(row)
            return row
        }

        @JvmStatic
        fun button(context: Context, label: String?, action: Runnable, primary: Boolean): Button {
            val host = inflateHost(context)
            val layout = if (primary) R.layout.view_button_primary else R.layout.view_button
            val b = LayoutInflater.from(context).inflate(layout, host, false) as MaterialButton
            b.text = label
            b.setOnClickListener { action.run() }
            return b
        }

        @JvmStatic
        fun actionRow(context: Context, label: String?, action: Runnable): Button {
            val host = inflateHost(context)
            val b = LayoutInflater.from(context).inflate(R.layout.view_action_row, host, false) as MaterialButton
            b.text = label
            b.setOnClickListener { action.run() }
            return b
        }

        @JvmStatic
        fun toolTile(
            context: Context,
            parent: LinearLayout,
            icon: String?,
            title: String?,
            subtitle: String?,
            action: Runnable
        ): View {
            val inflater = LayoutInflater.from(context)
            val tile = inflater.inflate(R.layout.item_tool_tile, parent, false)

            val iconView = tile.findViewById<TextView>(R.id.tool_tile_icon)
            val titleView = tile.findViewById<TextView>(R.id.tool_tile_title)
            val subtitleView = tile.findViewById<TextView>(R.id.tool_tile_subtitle)

            if (iconView != null) iconView.text = icon
            if (titleView != null) titleView.text = title
            if (subtitleView != null) subtitleView.text = subtitle

            tile.contentDescription = "$title. $subtitle"
            ViewCompat.setScreenReaderFocusable(tile, true)
            if (iconView != null) iconView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            if (titleView != null) titleView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            if (subtitleView != null) subtitleView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO

            tile.setOnClickListener { action.run() }
            parent.addView(tile)
            return tile
        }

        @JvmStatic
        fun kpiCard(
            context: Context,
            parent: LinearLayout,
            count: String?,
            title: String?,
            countColor: Int,
            onClickListener: View.OnClickListener?
        ): View {
            val inflater = LayoutInflater.from(context)
            val card = inflater.inflate(R.layout.item_kpi_stat, parent, false)

            val countView = card.findViewById<TextView>(R.id.kpi_count)
            val titleView = card.findViewById<TextView>(R.id.kpi_title)

            if (countView != null) {
                countView.text = count
                countView.setTextColor(foreground(context, countColor))
            }
            if (titleView != null) {
                titleView.text = title
            }

            card.setOnClickListener(onClickListener)
            card.contentDescription = "$title: $count"
            ViewCompat.setScreenReaderFocusable(card, true)
            if (countView != null) countView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            if (titleView != null) titleView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            parent.addView(card)
            return card
        }

        @JvmStatic
        fun toolTile(
            context: Context,
            parent: LinearLayout,
            iconResource: Int,
            title: String?,
            subtitle: String?,
            action: Runnable
        ): View {
            val tile = toolTile(context, parent, "", title, subtitle, action)
            val icon = tile.findViewById<TextView>(R.id.tool_tile_icon)
            icon.setPadding(dp(context, 4), 0, 0, 0)
            icon.setCompoundDrawablesWithIntrinsicBounds(iconResource, 0, 0, 0)
            return tile
        }

        @JvmStatic
        fun heading(context: Context, parent: LinearLayout, value: String?) {
            add(context, parent, value, 20, Tone.INK, true)
            ViewCompat.setAccessibilityHeading(parent.getChildAt(parent.childCount - 1), true)
        }

        @JvmStatic
        fun title(context: Context, parent: LinearLayout, value: String?, description: String?) {
            add(context, parent, value, 28, Tone.INK, true)
            ViewCompat.setAccessibilityHeading(parent.getChildAt(parent.childCount - 1), true)
            add(context, parent, description, 14, Tone.MUTED, false)
        }

        /** Inflates [layout] against [parent] (so its XML width and margins apply) without attaching it. */
        @JvmStatic
        fun inflate(context: Context, @androidx.annotation.LayoutRes layout: Int, parent: android.view.ViewGroup?): View =
            LayoutInflater.from(context).inflate(layout, parent, false)

        /** Adds the empty-state card and returns it so callers can append an action button. */
        @JvmStatic
        fun empty(context: Context, parent: LinearLayout, title: String?, description: String?): LinearLayout {
            val box = inflate(context, R.layout.view_empty_state, parent) as LinearLayout
            box.findViewById<TextView>(R.id.empty_title).text = title
            box.findViewById<TextView>(R.id.empty_description).text = description
            parent.addView(box)
            return box
        }

        @JvmStatic
        fun note(context: Context, parent: LinearLayout, value: String?, tone: Tone) {
            add(context, parent, value, 16, tone, false)
        }

        @JvmStatic
        fun detailField(context: Context, parent: LinearLayout, label: String?, value: String?) {
            val field = inflate(context, R.layout.view_detail_field, parent)
            field.findViewById<TextView>(R.id.detail_caption).text = label
            field.findViewById<TextView>(R.id.detail_value).text = value
            parent.addView(field)
        }

        @JvmStatic
        fun dialogScroll(context: Context, body: View): ScrollView {
            val scroll = object : ScrollView(context) {
                override fun onMeasure(width: Int, height: Int) {
                    val maximum = (resources.displayMetrics.heightPixels * .50f).roundToInt()
                    val available = if (MeasureSpec.getMode(height) == MeasureSpec.UNSPECIFIED) {
                        maximum
                    } else {
                        min(maximum, MeasureSpec.getSize(height))
                    }
                    super.onMeasure(width, MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST))
                }
            }
            scroll.addView(body)
            return scroll
        }

        /** Adds a Material text field and returns its layout, for callers that show errors on it. */
        @JvmStatic
        fun fieldLayout(context: Context, parent: LinearLayout, hint: String?, value: String?, password: Boolean): TextInputLayout {
            val layout = inflate(context, R.layout.view_text_field, parent) as TextInputLayout
            layout.hint = hint
            val input = layout.findViewById<TextInputEditText>(R.id.field_input)
            input.setText(value)
            if (password) {
                layout.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
                input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            parent.addView(layout)
            return layout
        }

        @JvmStatic
        fun field(context: Context, parent: LinearLayout, hint: String?, value: String?, password: Boolean): EditText =
            fieldLayout(context, parent, hint, value, password).findViewById<TextInputEditText>(R.id.field_input)

        @JvmStatic
        @JvmOverloads
        fun copyToClipboard(context: Context, label: String?, text: String?, @androidx.annotation.StringRes confirmation: Int = R.string.services_copied) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
            if (clipboard != null) {
                clipboard.setPrimaryClip(privateClip(label, text))
                Toast.makeText(context, confirmation, Toast.LENGTH_SHORT).show()
            }
        }

        @JvmStatic
        fun privateClip(label: String?, text: String?): ClipData {
            val clip = ClipData.newPlainText(label, text)
            val extras = PersistableBundle()
            extras.putBoolean("android.content.extra.IS_SENSITIVE", true)
            clip.description.extras = extras
            return clip
        }

        @JvmStatic
        fun initials(name: String?): String {
            if (name == null || name.trim().isEmpty()) return "US"
            val parts = name.trim().split(Regex("\\s+"))
            if (parts.size >= 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).uppercase(Locale.ROOT)
            }
            return name.substring(0, min(2, name.length)).uppercase(Locale.ROOT)
        }

        @JvmStatic
        fun timestamp(millis: Long): String {
            return if (millis == 0L) {
                "—"
            } else {
                Instant.ofEpochMilli(millis).atZone(UsosClient.ZONE)
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm:ss", Language.currentLocale(null)))
            }
        }

        @JvmStatic
        fun shortDate(date: LocalDate): String {
            return date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Language.currentLocale(null)))
        }

        /** A small pill label on a tinted [surface] with [tone] text, inflated from `view_badge`. */
        @JvmStatic
        fun badge(context: Context, parent: LinearLayout, text: String?, surface: Surface, tone: Tone): TextView {
            val badge = inflate(context, R.layout.view_badge, parent) as TextView
            badge.text = text
            badge.backgroundTintList = ColorStateList.valueOf(context.getColor(surface.color))
            badge.setTone(tone)
            parent.addView(badge)
            return badge
        }

        @JvmStatic
        fun chips(
            context: Context,
            parent: LinearLayout,
            values: Array<String>,
            labels: Array<String>,
            selected: String?,
            action: Consumer<String>
        ) {
            val line = LinearLayout(context)
            val strip = HorizontalScrollView(context)
            strip.isHorizontalScrollBarEnabled = false
            strip.addView(line)
            var active: Button? = null
            for (i in values.indices) {
                val value = values[i]
                val isSelected = value == selected
                val b = button(context, labels[i], { action.accept(value) }, isSelected)
                b.isSelected = isSelected
                b.isSingleLine = true
                b.minHeight = dp(context, 48)
                if (isSelected) active = b
                val p = LinearLayout.LayoutParams(-2, -2)
                p.rightMargin = dp(context, 6)
                line.addView(b, p)
            }
            val selection = active
            if (selection != null) {
                strip.post { strip.smoothScrollTo(max(0, selection.left - dp(context, 12)), 0) }
            }
            val p = LinearLayout.LayoutParams(-1, -2)
            p.bottomMargin = dp(context, 12)
            parent.addView(strip, p)
        }

        @JvmStatic
        fun searchField(
            context: Context,
            parent: LinearLayout,
            hint: String?,
            value: String?,
            contentDescription: String?,
            onClear: Runnable?
        ): EditText {
            val layout = LayoutInflater.from(context).inflate(R.layout.view_search_field, parent, false) as TextInputLayout
            layout.hint = hint
            val input = layout.findViewById<TextInputEditText>(R.id.field_input)
            input.setText(value)
            if (contentDescription != null) input.contentDescription = contentDescription
            if (onClear != null) {
                layout.setEndIconOnClickListener {
                    input.setText("")
                    onClear.run()
                }
            }
            input.setOnEditorActionListener { _, action, _ ->
                if (action != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
                context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(input.windowToken, 0)
                input.clearFocus()
                true
            }
            parent.addView(layout)
            return input
        }

        /** The "clear search" action shown in empty states: empties [search] and reports the cleared query. */
        @JvmStatic
        fun clearSearchButton(context: Context, search: EditText, onQueryChanged: Consumer<String>): Button =
            button(context, context.getString(R.string.btn_clear_search), {
                search.setText("")
                onQueryChanged.accept("")
            }, false)

        @JvmStatic
        fun watchSearch(search: EditText, action: Consumer<String>) {
            search.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    action.accept(s.toString())
                }
                override fun afterTextChanged(s: Editable) {}
            })
        }

        @JvmStatic
        fun browser(context: Context, address: String?) {
            if (!WebLinks.isWebUrl(address)) {
                Toast.makeText(context, R.string.toast_cant_open_browser, Toast.LENGTH_SHORT).show()
                return
            }
            try {
                val intent = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .setDefaultColorSchemeParams(
                        CustomTabColorSchemeParams.Builder()
                            .setToolbarColor(GREEN)
                            .build()
                    )
                    .build()
                intent.launchUrl(context, Uri.parse(address))
            } catch (e: Exception) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(address)))
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.toast_cant_open_browser, Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JvmStatic
        fun plain(html: String): String {
            return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().trim()
        }
    }
}

/** Shows [value], or hides the view when it is empty. */
fun TextView.showIfNotEmpty(value: String) {
    text = value
    visibility = if (value.isEmpty()) View.GONE else View.VISIBLE
}
