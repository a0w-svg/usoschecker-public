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
                if (achieved) "Cel został już osiągnięty!" else "Brak pozostałych punktów ECTS do podniesienia średniej."
            )
        }

        if (currentEcts <= 0) {
            val achievable = targetGpa in 2.0..5.0
            return SimulationResult(
                0.0, 0.0, targetGpa, remainingEcts, targetGpa, achievable,
                if (achievable) {
                    "Wymagana średnia: " + String.format(Locale.forLanguageTag("pl-PL"), "%.2f", targetGpa)
                } else {
                    "Cel poza zakresem skali 2.0 - 5.0."
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
                    Locale.forLanguageTag("pl-PL"),
                    "Matematycznie nieosiągalne (wymagana średnia %.2f przekracza maksymalną ocenę 5.0).",
                    requiredGpa
                )
            )
            requiredGpa <= 2.0 -> SimulationResult(
                currentGpa, currentEcts, targetGpa, remainingEcts, 2.0, true,
                "Cel jest już zagwarantowany przy dowolnych ocenach pozytywnych!"
            )
            else -> SimulationResult(
                currentGpa, currentEcts, targetGpa, remainingEcts, requiredGpa, true,
                String.format(
                    Locale.forLanguageTag("pl-PL"),
                    "Aby osiągnąć cel %.2f, z pozostałych %.0f ECTS potrzebujesz średniej co najmniej %.2f.",
                    targetGpa, remainingEcts, requiredGpa
                )
            )
        }
    }

    @JvmStatic
    fun scholarshipStatus(weightedGpa: Double): String {
        if (weightedGpa.isNaN()) return "Brak ocen do wyliczenia stypendium"
        if (weightedGpa >= 4.80) return "Wysoka szansa na stypendium Rektora (Kategoria I)"
        if (weightedGpa >= SCHOLARSHIP_THRESHOLD) return "Kwalifikacja do stypendium Rektora Vistula (min. 4.50)"
        val missing = SCHOLARSHIP_THRESHOLD - weightedGpa
        return String.format(Locale.forLanguageTag("pl-PL"), "Do progu stypendialnego 4.50 brakuje %.2f pkt", missing)
    }

    @JvmStatic
    fun honorsStatus(weightedGpa: Double): String {
        if (weightedGpa.isNaN()) return "Brak ocen"
        if (weightedGpa >= HONORS_THRESHOLD) return "Średnia kwalifikuje do dyplomu z wyróżnieniem (min. 4.75)"
        val missing = HONORS_THRESHOLD - weightedGpa
        return String.format(Locale.forLanguageTag("pl-PL"), "Do dyplomu z wyróżnieniem (4.75) brakuje %.2f pkt", missing)
    }
}

