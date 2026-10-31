package pl.vistula.checker.ui

import androidx.annotation.ColorRes
import pl.vistula.checker.R

/**
 * Semantic text colour. Each tone resolves to a colour resource, so light and dark themes
 * (values and values-night) decide the actual colour; callers never pass raw RGB values.
 */
enum class Tone(@ColorRes val color: Int) {
    /** Primary text. */
    INK(R.color.app_ink),

    /** Secondary text, hints and metadata. */
    MUTED(R.color.app_muted),

    /** Needs attention but is not a failure (almost due, limit reached). */
    WARNING(R.color.app_warning),

    /** Failure or overdue. */
    ERROR(R.color.app_error)
}

/** Semantic card background. [DEFAULT] is the plain card surface; the others are subtle tinted panels. */
enum class Surface(@ColorRes val color: Int) {
    DEFAULT(R.color.app_card),
    SUCCESS(R.color.app_badge_success_bg),
    WARNING(R.color.app_badge_warning_bg),
    ERROR(R.color.app_badge_error_bg),
    ACCENT(R.color.app_accent_container)
}

/** Sets the text colour from a semantic [tone]. */
fun android.widget.TextView.setTone(tone: Tone) = setTextColor(context.getColor(tone.color))
