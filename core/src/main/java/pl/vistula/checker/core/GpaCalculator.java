package pl.vistula.checker.core;

import java.util.*;

public final class GpaCalculator {
    public static final double SCHOLARSHIP_THRESHOLD = 4.50;
    public static final double HONORS_THRESHOLD = 4.75;

    public static final class SimulationResult {
        public final double currentWeightedGpa;
        public final double currentEcts;
        public final double targetGpa;
        public final double remainingEcts;
        public final double requiredGpa;
        public final boolean achievable;
        public final String statusMessage;

        public SimulationResult(double currentWeightedGpa, double currentEcts, double targetGpa,
                                double remainingEcts, double requiredGpa, boolean achievable, String statusMessage) {
            this.currentWeightedGpa = currentWeightedGpa;
            this.currentEcts = currentEcts;
            this.targetGpa = targetGpa;
            this.remainingEcts = remainingEcts;
            this.requiredGpa = requiredGpa;
            this.achievable = achievable;
            this.statusMessage = statusMessage;
        }
    }

    private GpaCalculator() {}

    public static GradeSummary calculate(Collection<Record> records) {
        return GradeSummary.of(records);
    }

    public static SimulationResult simulate(double currentGpa, double currentEcts, double targetGpa, double remainingEcts) {
        if (remainingEcts <= 0) {
            boolean achieved = currentGpa >= targetGpa;
            return new SimulationResult(currentGpa, currentEcts, targetGpa, 0, currentGpa, achieved,
                achieved ? "Cel został już osiągnięty!" : "Brak pozostałych punktów ECTS do podniesienia średniej.");
        }

        if (currentEcts <= 0) {
            boolean achievable = targetGpa >= 2.0 && targetGpa <= 5.0;
            return new SimulationResult(0, 0, targetGpa, remainingEcts, targetGpa, achievable,
                achievable ? "Wymagana średnia: " + String.format(Locale.forLanguageTag("pl-PL"), "%.2f", targetGpa) : "Cel poza zakresem skali 2.0 - 5.0.");
        }

        double totalEcts = currentEcts + remainingEcts;
        double requiredTotalPoints = targetGpa * totalEcts;
        double currentTotalPoints = currentGpa * currentEcts;
        double requiredRemainingPoints = requiredTotalPoints - currentTotalPoints;
        double requiredGpa = requiredRemainingPoints / remainingEcts;

        if (requiredGpa > 5.0) {
            return new SimulationResult(currentGpa, currentEcts, targetGpa, remainingEcts, requiredGpa, false,
                String.format(Locale.forLanguageTag("pl-PL"), "Matematycznie nieosiągalne (wymagana średnia %.2f przekracza maksymalną ocenę 5.0).", requiredGpa));
        } else if (requiredGpa <= 2.0) {
            return new SimulationResult(currentGpa, currentEcts, targetGpa, remainingEcts, 2.0, true,
                "Cel jest już zagwarantowany przy dowolnych ocenach pozytywnych!");
        } else {
            return new SimulationResult(currentGpa, currentEcts, targetGpa, remainingEcts, requiredGpa, true,
                String.format(Locale.forLanguageTag("pl-PL"), "Aby osiągnąć cel %.2f, z pozostałych %.0f ECTS potrzebujesz średniej co najmniej %.2f.", targetGpa, remainingEcts, requiredGpa));
        }
    }

    public static String scholarshipStatus(double weightedGpa) {
        if (Double.isNaN(weightedGpa)) return "Brak ocen do wyliczenia stypendium";
        if (weightedGpa >= 4.80) return "Wysoka szansa na stypendium Rektora (Kategoria I)";
        if (weightedGpa >= SCHOLARSHIP_THRESHOLD) return "Kwalifikacja do stypendium Rektora Vistula (min. 4.50)";
        double missing = SCHOLARSHIP_THRESHOLD - weightedGpa;
        return String.format(Locale.forLanguageTag("pl-PL"), "Do progu stypendialnego 4.50 brakuje %.2f pkt", missing);
    }

    public static String honorsStatus(double weightedGpa) {
        if (Double.isNaN(weightedGpa)) return "Brak ocen";
        if (weightedGpa >= HONORS_THRESHOLD) return "Średnia kwalifikuje do dyplomu z wyróżnieniem (min. 4.75)";
        double missing = HONORS_THRESHOLD - weightedGpa;
        return String.format(Locale.forLanguageTag("pl-PL"), "Do dyplomu z wyróżnieniem (4.75) brakuje %.2f pkt", missing);
    }
}
