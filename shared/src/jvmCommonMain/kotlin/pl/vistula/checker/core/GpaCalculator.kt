package pl.vistula.checker.core

import java.util.Locale

object GpaCalculator {
    const val SCHOLARSHIP_THRESHOLD: Double = 4.50
    const val HONORS_THRESHOLD: Double = 4.75

    class SimulationResult(
        @JvmField val currentWeightedGpa: Double,
        @JvmField val currentEcts: Double,
        @JvmField val targetGpa: Double,
        @JvmField val remainingEcts: Double,
        @JvmField val requiredGpa: Double,
        @JvmField val achievable: Boolean,
        @JvmField val statusMessage: String
    )

    @JvmStatic
    fun calculate(records: Collection<Record>): GradeSummary = GradeSummary.of(records)

    @JvmStatic
    fun simulate(currentGpa: Double, currentEcts: Double, targetGpa: Double, remainingEcts: Double): SimulationResult {
        if (remainingEcts <= 0) {
            val achieved = currentGpa >= targetGpa
            return SimulationResult(
                currentGpa, currentEcts, targetGpa, 0.0, currentGpa, achieved,
                if (achieved) ApiLanguage.label("Cel został już osiągnięty!", "The target has already been reached!", "Ціль уже досягнуто!") else ApiLanguage.label("Brak pozostałych punktów ECTS do podniesienia średniej.", "No remaining ECTS credits to improve the average.", "Немає залишкових кредитів ECTS для підвищення середнього балу.")
            )
        }

        if (currentEcts <= 0) {
            val achievable = targetGpa in 2.0..5.0
            return SimulationResult(
                0.0, 0.0, targetGpa, remainingEcts, targetGpa, achievable,
                if (achievable) {
                    ApiLanguage.label("Wymagana średnia: ", "Required average: ", "Потрібний середній бал: ") + String.format(Locale.forLanguageTag(ApiLanguage.current()), "%.2f", targetGpa)
                } else {
                    ApiLanguage.label("Cel poza zakresem skali 2.0 - 5.0.", "Target outside the 2.0–5.0 grading scale.", "Ціль поза шкалою оцінок 2.0–5.0.")
                }
            )
        }

        val totalEcts = currentEcts + remainingEcts
        val requiredTotalPoints = targetGpa * totalEcts
        val currentTotalPoints = currentGpa * currentEcts
        val requiredRemainingPoints = requiredTotalPoints - currentTotalPoints
        val requiredGpa = requiredRemainingPoints / remainingEcts

        return when {
            requiredGpa > 5.0 -> SimulationResult(
                currentGpa, currentEcts, targetGpa, remainingEcts, requiredGpa, false,
                String.format(
                    Locale.forLanguageTag(ApiLanguage.current()),
                    ApiLanguage.label("Matematycznie nieosiągalne (wymagana średnia %.2f przekracza maksymalną ocenę 5.0).", "Mathematically unreachable (required average %.2f exceeds the maximum grade of 5.0).", "Математично недосяжно (потрібний середній бал %.2f перевищує максимальну оцінку 5.0)."),
                    requiredGpa
                )
            )
            requiredGpa <= 2.0 -> SimulationResult(
                currentGpa, currentEcts, targetGpa, remainingEcts, 2.0, true,
                ApiLanguage.label("Cel jest już zagwarantowany przy dowolnych ocenach pozytywnych!", "Any passing grades will achieve this target.", "Будь-які позитивні оцінки забезпечать досягнення цілі.")
            )
            else -> SimulationResult(
                currentGpa, currentEcts, targetGpa, remainingEcts, requiredGpa, true,
                String.format(
                    Locale.forLanguageTag(ApiLanguage.current()),
                    ApiLanguage.label("Aby osiągnąć cel %.2f, z pozostałych %.0f ECTS potrzebujesz średniej co najmniej %.2f.", "To reach %.2f, over the remaining %.0f ECTS you need an average of at least %.2f.", "Для досягнення %.2f за решту %.0f ECTS потрібен середній бал щонайменше %.2f."),
                    targetGpa, remainingEcts, requiredGpa
                )
            )
        }
    }

    @JvmStatic
    fun scholarshipStatus(weightedGpa: Double): String {
        if (weightedGpa.isNaN()) return ApiLanguage.label("Brak ocen do wyliczenia stypendium", "No grades available for an estimate", "Немає оцінок для розрахунку")
        if (weightedGpa >= 4.80) return ApiLanguage.label("Wysoka szansa na stypendium Rektora (Kategoria I)", "High average (category I estimate)", "Високий середній бал (оцінка категорії I)")
        if (weightedGpa >= SCHOLARSHIP_THRESHOLD) return ApiLanguage.label("Kwalifikacja do stypendium Rektora Vistula (min. 4.50)", "Average meets the 4.50 reference threshold", "Середній бал відповідає орієнтовному порогу 4.50")
        val missing = SCHOLARSHIP_THRESHOLD - weightedGpa
        return String.format(Locale.forLanguageTag(ApiLanguage.current()), ApiLanguage.label("Do progu stypendialnego 4.50 brakuje %.2f pkt", "%.2f points below the 4.50 reference threshold", "До орієнтовного порогу 4.50 бракує %.2f бала"), missing)
    }

    @JvmStatic
    fun honorsStatus(weightedGpa: Double): String {
        if (weightedGpa.isNaN()) return ApiLanguage.label("Brak ocen", "No grades", "Немає оцінок")
        if (weightedGpa >= HONORS_THRESHOLD) return ApiLanguage.label("Średnia kwalifikuje do dyplomu z wyróżnieniem (min. 4.75)", "Average meets the 4.75 reference threshold for honors", "Середній бал відповідає орієнтовному порогу 4.75 для відзнаки")
        val missing = HONORS_THRESHOLD - weightedGpa
        return String.format(Locale.forLanguageTag(ApiLanguage.current()), ApiLanguage.label("Do dyplomu z wyróżnieniem (4.75) brakuje %.2f pkt", "%.2f points below the 4.75 honors reference threshold", "До орієнтовного порогу 4.75 для відзнаки бракує %.2f бала"), missing)
    }
}

