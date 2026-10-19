package pl.vistula.checker.ui

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
import android.text.InputType
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
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

        @JvmStatic
        fun text(context: Context, value: String?, size: Int, color: Int, bold: Boolean): TextView {
            val t = TextView(context)
            t.text = value
            t.textSize = size.toFloat()
            t.setTextColor(foreground(context, color))
            t.setLineSpacing(dp(context, 3).toFloat(), 1f)
            if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            return t
        }

        @JvmStatic
        fun add(context: Context, target: LinearLayout, value: String?, size: Int, color: Int, bold: Boolean) {
            val t = text(context, value, size, color, bold)
            val p = LinearLayout.LayoutParams(-1, -2)
            p.bottomMargin = dp(context, 12)
            target.addView(t, p)
        }

        @JvmStatic
        fun card(context: Context, parent: LinearLayout, color: Int): LinearLayout {
            val box = LinearLayout(context)
            box.orientation = LinearLayout.VERTICAL
            box.setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 12))
            val shape = GradientDrawable()
            shape.setColor(background(context, color))
            shape.cornerRadius = dp(context, 20).toFloat()
            if (color == Color.WHITE || isDark(context)) {
                shape.setStroke(dp(context, 1), context.getColor(R.color.app_outline_soft))
            }
            box.background = shape
            val p = LinearLayout.LayoutParams(-1, -2)
            p.bottomMargin = dp(context, 14)
            parent.addView(box, p)
            return box
        }

        @JvmStatic
        fun section(context: Context, parent: LinearLayout, ignoredColor: Int): LinearLayout {
            val box = LinearLayout(context)
            box.orientation = LinearLayout.VERTICAL
            box.setPadding(dp(context, 8), dp(context, 16), dp(context, 8), dp(context, 12))
            box.setBackgroundResource(R.drawable.bg_section_divider)
            val params = LinearLayout.LayoutParams(-1, -2)
            params.bottomMargin = dp(context, 8)
            parent.addView(box, params)
            return box
        }

        /** Secondary content stays reachable without competing with the screen's main task. */
        @JvmStatic
        fun disclosure(context: Context, parent: LinearLayout, key: String, title: Int): LinearLayout {
            val prefs = context.getSharedPreferences("tool_layout", Context.MODE_PRIVATE)
            val group = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val body = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
            }
            var expanded = prefs.getBoolean(key, false)
            val toggle = actionRow(context, context.getString(title), {}, false)
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
                prefs.edit().putBoolean(key, expanded).apply()
                update()
            }
            group.addView(toggle)
            group.addView(body)
            group.setBackgroundResource(R.drawable.bg_section_divider)
            parent.addView(group, LinearLayout.LayoutParams(-1, -2))
            update()
            return body
        }

        @JvmStatic
        fun button(context: Context, label: String?, action: Runnable, primary: Boolean): Button {
            val b = MaterialButton(context)
            b.maxLines = 4
            b.isSingleLine = false
            b.text = label
            b.isAllCaps = false
            b.textSize = 14f
            val states = arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf())
            b.setTextColor(
                ColorStateList(
                    states,
                    intArrayOf(
                        context.getColor(R.color.app_disabled_content),
                        context.getColor(if (primary) R.color.app_on_accent else R.color.app_ink)
                    )
                )
            )
            b.cornerRadius = dp(context, 14)
            b.backgroundTintList = ColorStateList(
                states,
                intArrayOf(
                    context.getColor(R.color.app_disabled_surface),
                    context.getColor(if (primary) R.color.app_accent else R.color.app_inner_surface)
                )
            )
            b.setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10))
            b.minHeight = dp(context, 48)
            val p = LinearLayout.LayoutParams(-1, -2)
            p.bottomMargin = dp(context, 8)
            b.layoutParams = p
            b.setOnClickListener { action.run() }
            return b
        }

        @JvmStatic
        fun actionRow(context: Context, label: String?, action: Runnable, ignoredPrimary: Boolean): Button {
            val button = button(context, label, action, false)
            button.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            button.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            button.setPadding(dp(context, 8), dp(context, 10), dp(context, 8), dp(context, 10))
            return button
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
            add(context, parent, value, 20, INK, true)
            ViewCompat.setAccessibilityHeading(parent.getChildAt(parent.childCount - 1), true)
        }

        @JvmStatic
        fun title(context: Context, parent: LinearLayout, value: String?, description: String?) {
            add(context, parent, value, 28, INK, true)
            ViewCompat.setAccessibilityHeading(parent.getChildAt(parent.childCount - 1), true)
            add(context, parent, description, 14, MUTED, false)
        }

        @JvmStatic
        fun empty(context: Context, parent: LinearLayout, title: String?, description: String?) {
            val box = card(context, parent, Color.WHITE)
            add(context, box, title, 17, INK, true)
            add(context, box, description, 14, MUTED, false)
        }

        @JvmStatic
        fun note(context: Context, parent: LinearLayout, value: String?, color: Int) {
            add(context, parent, value, 14, color, false)
        }

        @JvmStatic
        fun detailField(context: Context, parent: LinearLayout, label: String?, value: String?) {
            val caption = text(context, label, 12, MUTED, false)
            val captionParams = LinearLayout.LayoutParams(-1, -2)
            captionParams.bottomMargin = dp(context, 3)
            parent.addView(caption, captionParams)
            val textView = text(context, value, 15, INK, false)
            textView.setTextIsSelectable(true)
            val valueParams = LinearLayout.LayoutParams(-1, -2)
            valueParams.bottomMargin = dp(context, 14)
            parent.addView(textView, valueParams)
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

        @JvmStatic
        fun field(context: Context, parent: LinearLayout, hint: String?, value: String?, password: Boolean): EditText {
            val wrapper = TextInputLayout(context)
            wrapper.boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            wrapper.hint = hint
            wrapper.defaultHintTextColor = ColorStateList.valueOf(context.getColor(R.color.app_muted))
            val radius = dp(context, 12).toFloat()
            wrapper.setBoxCornerRadii(radius, radius, radius, radius)
            if (password) wrapper.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            val e = TextInputEditText(wrapper.context)
            e.setText(value)
            e.textSize = 14f
            e.setTextColor(foreground(context, INK))
            e.isSaveEnabled = false
            e.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            e.imeOptions = e.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            wrapper.isSaveEnabled = false
            e.minHeight = dp(context, 56)
            e.inputType = InputType.TYPE_CLASS_TEXT or
                (if (password) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_FLAG_MULTI_LINE)
            if (password) e.transformationMethod = PasswordTransformationMethod.getInstance()
            if (password) {
                e.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                e.imeOptions = e.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            }
            wrapper.addView(e, LinearLayout.LayoutParams(-1, -2))
            val p = LinearLayout.LayoutParams(-1, -2)
            p.bottomMargin = dp(context, 16)
            parent.addView(wrapper, p)
            return e
        }

        @JvmStatic
        fun copyToClipboard(context: Context, label: String?, text: String?) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
            if (clipboard != null) {
                clipboard.setPrimaryClip(privateClip(label, text))
                Toast.makeText(context, R.string.services_copied, Toast.LENGTH_SHORT).show()
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

        @JvmStatic
        fun badge(context: Context, parent: LinearLayout, text: String?, bg: Int, fg: Int): TextView {
            val t = TextView(context)
            t.text = text
            t.textSize = 11f
            t.setTextColor(foreground(context, fg))
            t.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            t.setPadding(dp(context, 10), dp(context, 4), dp(context, 10), dp(context, 4))
            val d = GradientDrawable()
            d.setColor(background(context, bg))
            d.cornerRadius = dp(context, 8).toFloat()
            t.background = d
            val p = LinearLayout.LayoutParams(-2, -2)
            p.bottomMargin = dp(context, 8)
            p.rightMargin = dp(context, 6)
            parent.addView(t, p)
            return t
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
            val input = field(context, parent, hint, value, false)
            if (contentDescription != null) input.contentDescription = contentDescription
            val wrapper = parent.getChildAt(parent.childCount - 1) as TextInputLayout
            wrapper.endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
            wrapper.endIconContentDescription = context.getString(R.string.btn_clear_search)
            if (onClear != null) {
                wrapper.setEndIconOnClickListener {
                    input.setText("")
                    onClear.run()
                }
            }
            input.inputType = InputType.TYPE_CLASS_TEXT
            input.isSingleLine = true
            input.imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            input.setOnEditorActionListener { _, action, _ ->
                if (action != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
                val keyboard = context.getSystemService(InputMethodManager::class.java)
                keyboard?.hideSoftInputFromWindow(input.windowToken, 0)
                input.clearFocus()
                true
            }
            return input
        }

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

