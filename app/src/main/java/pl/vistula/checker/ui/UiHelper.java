package pl.vistula.checker.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import pl.vistula.checker.R;
import pl.vistula.checker.UsosClient;

/**
 * Reusable UI styling and layout helpers for Vistula Checker.
 */
public final class UiHelper {
    public static final int INK = Color.rgb(27, 37, 32);
    public static final int GREEN = Color.rgb(23, 107, 87);
    public static final int MUTED = Color.rgb(89, 105, 96);
    public static final int BG = Color.rgb(250, 252, 250);

    private UiHelper() {}

    public static boolean isDark(Context context) {
        return (context.getResources().getConfiguration().uiMode
            & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
            == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /** Resolve shared semantic colors consistently for XML and programmatic screens. */
    public static int foreground(Context context, int color) {
        if (color == INK) return context.getColor(R.color.app_ink);
        if (color == MUTED) return context.getColor(R.color.app_muted);
        if (color == GREEN) return context.getColor(R.color.app_ink);
        if (color == Color.rgb(217, 119, 6) || color == Color.rgb(180, 83, 9))
            return context.getColor(R.color.app_warning);
        if (color == Color.rgb(160, 48, 40) || color == Color.rgb(167, 49, 49))
            return context.getColor(R.color.app_error);
        if (!isDark(context)) return color;
        // Keep status colors distinguishable and legible on dark cards.
        int surface = Color.rgb(24, 24, 24);
        for (int i = 0; i <= 20; i++) {
            int candidate = androidx.core.graphics.ColorUtils.blendARGB(color, Color.WHITE, i / 20f);
            if (androidx.core.graphics.ColorUtils.calculateContrast(candidate, surface) >= 4.5) return candidate;
        }
        return Color.WHITE;
    }

    public static int background(Context context, int color) {
        if (color == Color.WHITE) return context.getColor(R.color.app_card);
        if (color == BG) return context.getColor(R.color.app_background);
        if (color == INK) return context.getColor(R.color.app_brand_surface);
        if (color == GREEN) return context.getColor(R.color.app_accent);
        if (color == Color.rgb(232, 243, 236)) return context.getColor(R.color.app_button_surface);
        if (color == Color.rgb(214, 241, 229)) return context.getColor(R.color.app_accent_container);
        if (color == Color.rgb(240, 245, 241)) return context.getColor(R.color.app_surface_high);
        if (!isDark(context)) return color;
        // Pale information/status panels become subtle tinted surfaces.
        if (androidx.core.graphics.ColorUtils.calculateLuminance(color) > .5)
            return androidx.core.graphics.ColorUtils.blendARGB(Color.BLACK, color, .09f);
        return color;
    }

    /** Text for dynamically colored badges must contrast with their actual fill. */
    public static int onColor(Context context, int fill) {
        int ink = context.getColor(R.color.app_ink);
        if (androidx.core.graphics.ColorUtils.calculateContrast(ink, fill) >= 4.5) return ink;
        return androidx.core.graphics.ColorUtils.calculateContrast(Color.WHITE, fill)
            >= androidx.core.graphics.ColorUtils.calculateContrast(Color.BLACK, fill) ? Color.WHITE : Color.BLACK;
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static TextView text(Context context, String value, int size, int color, boolean bold) {
        TextView t = new TextView(context);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(foreground(context, color));
        t.setLineSpacing(dp(context, 3), 1f);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    public static void add(Context context, LinearLayout target, String value, int size, int color, boolean bold) {
        TextView t = text(context, value, size, color, bold);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(context, 12);
        target.addView(t, p);
    }

    public static LinearLayout card(Context context, LinearLayout parent, int color) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 12));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(background(context, color));
        shape.setCornerRadius(dp(context, 20));
        if (color == Color.WHITE || isDark(context)) shape.setStroke(dp(context, 1), context.getColor(R.color.app_outline_soft));
        box.setBackground(shape);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(context, 14);
        parent.addView(box, p);
        return box;
    }

    public static Button button(Context context, String label, Runnable action, boolean primary) {
        MaterialButton b = new MaterialButton(context);
        b.setMaxLines(4);
        b.setSingleLine(false);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        int[][] states = {new int[]{-android.R.attr.state_enabled}, new int[]{}};
        b.setTextColor(new ColorStateList(states, new int[]{context.getColor(R.color.app_disabled_content),
            context.getColor(primary ? R.color.app_on_accent : R.color.app_ink)}));
        b.setCornerRadius(dp(context, 14));
        b.setBackgroundTintList(new ColorStateList(states, new int[]{context.getColor(R.color.app_disabled_surface),
            context.getColor(primary ? R.color.app_accent : R.color.app_inner_surface)}));
        b.setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10));
        b.setMinHeight(dp(context, 48));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(context, 8);
        b.setLayoutParams(p);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    public static View toolTile(Context context, LinearLayout parent, String icon, String title, String subtitle, Runnable action) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View tile = inflater.inflate(R.layout.item_tool_tile, parent, false);

        TextView iconView = tile.findViewById(R.id.tool_tile_icon);
        TextView titleView = tile.findViewById(R.id.tool_tile_title);
        TextView subtitleView = tile.findViewById(R.id.tool_tile_subtitle);

        if (iconView != null) iconView.setText(icon);
        if (titleView != null) titleView.setText(title);
        if (subtitleView != null) subtitleView.setText(subtitle);

        tile.setContentDescription(title + ". " + subtitle);
        androidx.core.view.ViewCompat.setScreenReaderFocusable(tile, true);
        if (iconView != null) iconView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (titleView != null) titleView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (subtitleView != null) subtitleView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        tile.setOnClickListener(v -> action.run());
        parent.addView(tile);
        return tile;
    }

    public static View kpiCard(Context context, LinearLayout parent, String count, String title, int countColor, View.OnClickListener onClickListener) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View card = inflater.inflate(R.layout.item_kpi_stat, parent, false);

        TextView countView = card.findViewById(R.id.kpi_count);
        TextView titleView = card.findViewById(R.id.kpi_title);

        if (countView != null) {
            countView.setText(count);
            countView.setTextColor(foreground(context, countColor));
        }
        if (titleView != null) {
            titleView.setText(title);
        }

        card.setOnClickListener(onClickListener);
        card.setContentDescription(title + ": " + count);
        androidx.core.view.ViewCompat.setScreenReaderFocusable(card, true);
        if (countView != null) countView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (titleView != null) titleView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        parent.addView(card);
        return card;
    }
    public static View toolTile(Context context, LinearLayout parent, int iconResource, String title, String subtitle, Runnable action) {
        View tile = toolTile(context, parent, "", title, subtitle, action);
        TextView icon = tile.findViewById(R.id.tool_tile_icon);
        icon.setPadding(dp(context, 10), 0, 0, 0);
        icon.setCompoundDrawablesWithIntrinsicBounds(iconResource, 0, 0, 0);
        return tile;
    }

    public static void heading(Context context, LinearLayout parent, String value) {
        add(context, parent, value, 20, INK, true);
        androidx.core.view.ViewCompat.setAccessibilityHeading(parent.getChildAt(parent.getChildCount() - 1), true);
    }

    public static void title(Context context, LinearLayout parent, String value, String description) {
        add(context, parent, value, 28, INK, true);
        androidx.core.view.ViewCompat.setAccessibilityHeading(parent.getChildAt(parent.getChildCount() - 1), true);
        add(context, parent, description, 14, MUTED, false);
    }

    public static void empty(Context context, LinearLayout parent, String title, String description) {
        LinearLayout box = card(context, parent, Color.WHITE);
        add(context, box, title, 17, INK, true);
        add(context, box, description, 14, MUTED, false);
    }

    public static void note(Context context, LinearLayout parent, String value, int color) {
        add(context, parent, value, 12, color, false);
    }
    public static void detailField(Context context, LinearLayout parent, String label, String value) {
        TextView caption = text(context, label, 12, MUTED, false);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(-1, -2);
        captionParams.bottomMargin = dp(context, 3);
        parent.addView(caption, captionParams);
        TextView text = text(context, value, 15, INK, false);
        text.setTextIsSelectable(true);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(-1, -2);
        valueParams.bottomMargin = dp(context, 14);
        parent.addView(text, valueParams);
    }
    public static android.widget.ScrollView dialogScroll(Context context, View body) {
        android.widget.ScrollView scroll = new android.widget.ScrollView(context) {
            @Override protected void onMeasure(int width, int height) {
                int maximum = Math.round(getResources().getDisplayMetrics().heightPixels * .50f);
                int available = View.MeasureSpec.getMode(height) == View.MeasureSpec.UNSPECIFIED ? maximum
                    : Math.min(maximum, View.MeasureSpec.getSize(height));
                super.onMeasure(width, View.MeasureSpec.makeMeasureSpec(available, View.MeasureSpec.AT_MOST));
            }
        };
        scroll.addView(body);
        return scroll;
    }

    public static EditText field(Context context, LinearLayout parent, String hint, String value, boolean password) {
        TextInputLayout wrapper = new TextInputLayout(context);
        wrapper.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        wrapper.setHint(hint);
        wrapper.setHintTextColor(ColorStateList.valueOf(context.getColor(R.color.app_muted)));
        wrapper.setBoxCornerRadii(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));
        if (password) wrapper.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        TextInputEditText e = new TextInputEditText(wrapper.getContext());
        e.setText(value);
        e.setTextSize(14);
        e.setTextColor(foreground(context, INK));
        e.setSaveEnabled(false);
        e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        e.setImeOptions(e.getImeOptions() | android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        wrapper.setSaveEnabled(false);
        e.setMinHeight(dp(context, 56));
        e.setInputType(InputType.TYPE_CLASS_TEXT | (password ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_FLAG_MULTI_LINE));
        if (password) e.setTransformationMethod(PasswordTransformationMethod.getInstance());
        if (password) {
            e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            e.setImeOptions(e.getImeOptions() | android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        }
        wrapper.addView(e, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(context, 16);
        parent.addView(wrapper, p);
        return e;
    }

    public static void copyToClipboard(Context context, String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(privateClip(label, text));
            Toast.makeText(context, R.string.services_copied, Toast.LENGTH_SHORT).show();
        }
    }

    public static ClipData privateClip(String label, String text) {
        ClipData clip = ClipData.newPlainText(label, text);
        android.os.PersistableBundle extras = new android.os.PersistableBundle();
        extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
        clip.getDescription().setExtras(extras);
        return clip;
    }

    public static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "US";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase(Locale.ROOT);
        }
        return name.substring(0, Math.min(2, name.length())).toUpperCase(Locale.ROOT);
    }

    public static String timestamp(long millis) {
        return millis == 0 ? "Jeszcze nie sprawdzano" : Instant.ofEpochMilli(millis).atZone(UsosClient.ZONE)
            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm:ss", Locale.forLanguageTag("pl-PL")));
    }

    public static String shortDate(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.forLanguageTag("pl-PL")));
    }

    public static TextView badge(Context context, LinearLayout parent, String text, int bg, int fg) {
        TextView t = new TextView(context);
        t.setText(text);
        t.setTextSize(11);
        t.setTextColor(foreground(context, fg));
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(dp(context, 10), dp(context, 4), dp(context, 10), dp(context, 4));
        GradientDrawable d = new GradientDrawable();
        d.setColor(background(context, bg));
        d.setCornerRadius(dp(context, 8));
        t.setBackground(d);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.bottomMargin = dp(context, 8);
        p.rightMargin = dp(context, 6);
        parent.addView(t, p);
        return t;
    }

    public static void chips(Context context, LinearLayout parent, String[] values, String[] labels, String selected, java.util.function.Consumer<String> action) {
        LinearLayout line = new LinearLayout(context);
        HorizontalScrollView strip = new HorizontalScrollView(context);
        strip.setHorizontalScrollBarEnabled(false);
        strip.addView(line);
        Button active = null;
        for (int i = 0; i < values.length; i++) {
            final String value = values[i];
            Button b = button(context, labels[i], () -> action.accept(value), value.equals(selected));
            b.setSelected(value.equals(selected));
            b.setSingleLine(true);
            b.setMinHeight(dp(context, 48));
            if (value.equals(selected)) active = b;
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
            p.rightMargin = dp(context, 6);
            line.addView(b, p);
        }
        Button selection = active;
        if (selection != null) strip.post(() -> strip.smoothScrollTo(Math.max(0, selection.getLeft() - dp(context, 12)), 0));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(context, 12);
        parent.addView(strip, p);
    }

    public static EditText searchField(Context context, LinearLayout parent, String hint, String value, String contentDescription, Runnable onClear) {
        EditText input = field(context, parent, hint, value, false);
        if (contentDescription != null) input.setContentDescription(contentDescription);
        TextInputLayout wrapper = (TextInputLayout) parent.getChildAt(parent.getChildCount() - 1);
        wrapper.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        wrapper.setEndIconContentDescription(context.getString(R.string.btn_clear_search));
        if (onClear != null) {
            wrapper.setEndIconOnClickListener(v -> {
                input.setText("");
                onClear.run();
            });
        }
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH | android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        input.setOnEditorActionListener((view, action, event) -> {
            if (action != android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) return false;
            android.view.inputmethod.InputMethodManager keyboard = context.getSystemService(android.view.inputmethod.InputMethodManager.class);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
            input.clearFocus();
            return true;
        });
        return input;
    }

    public static void watchSearch(EditText search, java.util.function.Consumer<String> action) {
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                action.accept(s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    public static void browser(Context context, String address) {
        if (!pl.vistula.checker.core.WebLinks.isWebUrl(address)) {
            Toast.makeText(context, R.string.toast_cant_open_browser, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            androidx.browser.customtabs.CustomTabsIntent intent = new androidx.browser.customtabs.CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setDefaultColorSchemeParams(new androidx.browser.customtabs.CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(GREEN)
                    .build())
                .build();
            intent.launchUrl(context, android.net.Uri.parse(address));
        } catch (Exception e) {
            try {
                context.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(address)));
            } catch (Exception ignored) {
                Toast.makeText(context, R.string.toast_cant_open_browser, Toast.LENGTH_SHORT).show();
            }
        }
    }

    public static String plain(String html) {
        return android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim();
    }
}

