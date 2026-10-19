package pl.vistula.checker

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import pl.vistula.checker.core.LessonTime
import pl.vistula.checker.ui.compose.DarkColors
import pl.vistula.checker.ui.compose.LightColors
import pl.vistula.checker.ui.compose.OledColors
import pl.vistula.checker.ui.compose.TimelineLanePlanner
import pl.vistula.checker.ui.compose.UiStrings
import pl.vistula.checker.ui.compose.VistulaContrast
import pl.vistula.checker.ui.compose.VistulaIcons

class UxUiComponentsTest {

    @Test
    fun uiStringsAllLanguagesAreCompleteAndNonBlank() {
        for (lang in listOf("pl", "en", "uk", "unknown")) {
            val s = UiStrings.forLanguage(lang)
            assertFalse(s.tabHome.isBlank(), "tabHome blank for $lang")
            assertFalse(s.tabSchedule.isBlank(), "tabSchedule blank for $lang")
            assertFalse(s.tabGrades.isBlank(), "tabGrades blank for $lang")
            assertFalse(s.tabHistory.isBlank(), "tabHistory blank for $lang")
            assertFalse(s.tabMore.isBlank(), "tabMore blank for $lang")
            assertFalse(s.onboardingTitle.isBlank(), "onboardingTitle blank for $lang")
            assertFalse(s.onboardingPointPrivacy.isBlank(), "onboardingPointPrivacy blank for $lang")
            assertFalse(s.onboardingTryDemo.isBlank(), "onboardingTryDemo blank for $lang")
            assertFalse(s.onboardingConnectAccount.isBlank(), "onboardingConnectAccount blank for $lang")
            assertFalse(s.viewDayTimeline.isBlank(), "viewDayTimeline blank for $lang")
            assertFalse(s.viewAgenda.isBlank(), "viewAgenda blank for $lang")
            assertFalse(s.btnSync.isBlank(), "btnSync blank for $lang")
            assertFalse(s.attendanceOk.isBlank(), "attendanceOk blank for $lang")
        }
        assertEquals("Start", UiStrings.forLanguage("pl").tabHome)
        assertEquals("Home", UiStrings.forLanguage("en").tabHome)
        assertEquals("Старт", UiStrings.forLanguage("uk").tabHome)
    }

    @Test
    fun wcagAaContrastSatisfiedAcrossLightDarkAndOledSchemes() {
        val schemes = listOf(
            "Light" to LightColors,
            "Dark" to DarkColors,
            "OLED" to OledColors
        )
        for ((name, scheme) in schemes) {
            val bgRatio = VistulaContrast.contrastRatio(scheme.onBackground, scheme.background)
            assertTrue(bgRatio >= 4.5, "$name onBackground/background contrast too low: $bgRatio")

            val surfaceRatio = VistulaContrast.contrastRatio(scheme.onSurface, scheme.surface)
            assertTrue(surfaceRatio >= 4.5, "$name onSurface/surface contrast too low: $surfaceRatio")

            val mutedOnBgRatio = VistulaContrast.contrastRatio(scheme.onSurfaceVariant, scheme.background)
            assertTrue(mutedOnBgRatio >= 4.5, "$name onSurfaceVariant/background contrast too low: $mutedOnBgRatio")

            val mutedOnSurfaceRatio = VistulaContrast.contrastRatio(scheme.onSurfaceVariant, scheme.surface)
            assertTrue(mutedOnSurfaceRatio >= 4.5, "$name onSurfaceVariant/surface contrast too low: $mutedOnSurfaceRatio")

            val primaryButtonRatio = VistulaContrast.contrastRatio(scheme.onPrimary, scheme.primary)
            assertTrue(primaryButtonRatio >= 4.5, "$name onPrimary/primary contrast too low: $primaryButtonRatio")
        }
    }

    @Test
    fun timelineLanePlannerAssignsSeparateColumnsForOverlappingLessons() {
        val day = LocalDate.of(2026, 10, 19)
        val lessons = listOf(
            LessonTime.of(
                pl.vistula.checker.core.Record("1", "plan", "Algorytmy (Wykład)", "08:00 - 09:30", "B1", "2026-10-19", "", "2026-10-19 08:00:00", "2026-10-19 09:30:00")
            )!!,
            LessonTime.of(
                pl.vistula.checker.core.Record("2", "plan", "Bazy danych (Lab)", "08:30 - 10:00", "309", "2026-10-19", "", "2026-10-19 08:30:00", "2026-10-19 10:00:00")
            )!!,
            LessonTime.of(
                pl.vistula.checker.core.Record("3", "plan", "Sieci komputerowe", "10:15 - 11:45", "112", "2026-10-19", "", "2026-10-19 10:15:00", "2026-10-19 11:45:00")
            )!!
        )
        val placed = TimelineLanePlanner.placeLessons(lessons, day)
        assertEquals(3, placed.size)

        val first = placed[0]
        val second = placed[1]
        val third = placed[2]

        assertEquals(0, first.column)
        assertEquals(1, second.column)
        assertEquals(2, first.columnCount)
        assertEquals(2, second.columnCount)

        assertEquals(0, third.column)
        assertEquals(1, third.columnCount)

        val bounds = TimelineLanePlanner.hourBounds(lessons, day)
        assertEquals(8, bounds[0])
        assertEquals(20, bounds[1])
    }

    @Test
    fun vectorIconsAreValidNonEmptyVectors() {
        val icons = listOf(
            VistulaIcons.Home,
            VistulaIcons.Schedule,
            VistulaIcons.Grades,
            VistulaIcons.History,
            VistulaIcons.Settings,
            VistulaIcons.Check,
            VistulaIcons.Warning
        )
        for (icon in icons) {
            assertNotNull(icon)
            assertTrue(icon.name.isNotBlank())
            assertTrue(icon.defaultWidth.value > 0f)
            assertTrue(icon.defaultHeight.value > 0f)
        }
    }
}
