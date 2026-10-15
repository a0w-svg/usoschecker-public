package pl.vistula.checker.ui;

import android.graphics.Color;
import android.widget.LinearLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

import pl.vistula.checker.MainActivity;
import pl.vistula.checker.R;
import pl.vistula.checker.core.GpaCalculator;
import pl.vistula.checker.core.GradeSummary;
import pl.vistula.checker.core.Snapshot;

/**
 * Controller for rendering the GPA Calculator and Scholarship Simulator screen.
 */
public final class GpaSimulatorController {

    private GpaSimulatorController() {}

    public static void render(MainActivity activity, LinearLayout content, JSONObject d,
                              double targetGpa, double remainingEcts,
                              java.util.function.Consumer<Double> onTargetChanged,
                              java.util.function.Consumer<Double> onEctsChanged) throws JSONException {
        UiHelper.title(activity, content,
            activity.getString(R.string.gpa_title),
            activity.getString(R.string.gpa_subtitle));

        JSONObject raw = d.optJSONObject("snapshot");
        if (raw == null) {
            UiHelper.empty(activity, content,
                activity.getString(R.string.gpa_empty_title),
                activity.getString(R.string.gpa_empty_desc));
            content.addView(UiHelper.button(activity,
                activity.getString(R.string.btn_connect),
                () -> activity.navigate(4), true));
            return;
        }

        Snapshot snapshot = Snapshot.from(raw);
        GradeSummary summary = GradeSummary.of(snapshot.records.values());

        LinearLayout currentCard = UiHelper.card(activity, content, Color.rgb(238, 245, 242));
        UiHelper.add(activity, currentCard, activity.getString(R.string.gpa_status_header), 11, UiHelper.GREEN, true);

        double currentWeighted = !Double.isNaN(summary.ectsAverage) ? summary.ectsAverage : summary.average;
        if (!Double.isNaN(summary.ectsAverage)) {
            UiHelper.add(activity, currentCard,
                activity.getString(R.string.gpa_weighted, summary.ectsAverage), 24, UiHelper.INK, true);
            UiHelper.add(activity, currentCard,
                activity.getString(R.string.gpa_ects_summary, summary.totalEcts, summary.average), 13, UiHelper.MUTED, false);
        } else if (!Double.isNaN(summary.average)) {
            UiHelper.add(activity, currentCard,
                activity.getString(R.string.gpa_arithmetic, summary.average), 24, UiHelper.INK, true);
            UiHelper.add(activity, currentCard,
                activity.getString(R.string.gpa_no_ects_note), 13, UiHelper.MUTED, false);
        } else {
            UiHelper.add(activity, currentCard,
                activity.getString(R.string.gpa_no_numeric), 20, UiHelper.INK, true);
        }
        UiHelper.add(activity, currentCard,
            activity.getString(R.string.gpa_index_entries, (summary.numeric + summary.other), summary.numeric), 12, UiHelper.MUTED, false);

        // Rector's Scholarship
        LinearLayout scholarCard = UiHelper.card(activity, content, Color.WHITE);
        UiHelper.add(activity, scholarCard, activity.getString(R.string.gpa_rector_title), 18, UiHelper.INK, true);
        String scholarText = GpaCalculator.scholarshipStatus(currentWeighted);
        boolean qualifiesScholar = currentWeighted >= GpaCalculator.SCHOLARSHIP_THRESHOLD;
        UiHelper.add(activity, scholarCard, scholarText, 15, qualifiesScholar ? UiHelper.GREEN : Color.rgb(180, 100, 0), true);
        UiHelper.note(activity, scholarCard, activity.getString(R.string.gpa_rector_rule), UiHelper.MUTED);

        // Honors Diploma
        LinearLayout honorsCard = UiHelper.card(activity, content, Color.WHITE);
        UiHelper.add(activity, honorsCard, activity.getString(R.string.gpa_honors_title), 18, UiHelper.INK, true);
        String honorsText = GpaCalculator.honorsStatus(currentWeighted);
        boolean qualifiesHonors = currentWeighted >= GpaCalculator.HONORS_THRESHOLD;
        UiHelper.add(activity, honorsCard, honorsText, 15, qualifiesHonors ? UiHelper.GREEN : UiHelper.MUTED, true);
        UiHelper.note(activity, honorsCard, activity.getString(R.string.gpa_honors_rule), UiHelper.MUTED);

        // Simulator
        LinearLayout simCard = UiHelper.card(activity, content, Color.WHITE);
        UiHelper.add(activity, simCard, activity.getString(R.string.gpa_sim_card_title), 18, UiHelper.INK, true);
        UiHelper.note(activity, simCard, activity.getString(R.string.gpa_sim_card_desc), UiHelper.MUTED);

        UiHelper.add(activity, simCard, activity.getString(R.string.gpa_target_label), 13, UiHelper.INK, true);
        UiHelper.chips(activity, simCard,
            new String[]{"4.00", "4.25", "4.50", "4.75", "4.80", "5.00"},
            new String[]{
                activity.getString(R.string.gpa_chip_target_400),
                activity.getString(R.string.gpa_chip_target_425),
                activity.getString(R.string.gpa_chip_target_450),
                activity.getString(R.string.gpa_chip_target_475),
                activity.getString(R.string.gpa_chip_target_480),
                activity.getString(R.string.gpa_chip_target_500)
            },
            String.format(Locale.US, "%.2f", targetGpa),
            value -> {
                try { onTargetChanged.accept(Double.parseDouble(value)); } catch (Exception ignored) {}
            });

        UiHelper.add(activity, simCard, activity.getString(R.string.gpa_remaining_ects_label), 13, UiHelper.INK, true);
        UiHelper.chips(activity, simCard,
            new String[]{"10", "15", "20", "30", "45", "60"},
            new String[]{
                activity.getString(R.string.gpa_chip_ects_10),
                activity.getString(R.string.gpa_chip_ects_15),
                activity.getString(R.string.gpa_chip_ects_20),
                activity.getString(R.string.gpa_chip_ects_30),
                activity.getString(R.string.gpa_chip_ects_45),
                activity.getString(R.string.gpa_chip_ects_60)
            },
            String.format(Locale.US, "%.0f", remainingEcts),
            value -> {
                try { onEctsChanged.accept(Double.parseDouble(value)); } catch (Exception ignored) {}
            });

        double currentEcts = !Double.isNaN(summary.totalEcts) && summary.totalEcts > 0 ? summary.totalEcts : 30.0;
        GpaCalculator.SimulationResult sim = GpaCalculator.simulate(currentWeighted, currentEcts, targetGpa, remainingEcts);

        LinearLayout simResultBox = UiHelper.card(activity, simCard, sim.achievable ? Color.rgb(232, 243, 236) : Color.rgb(255, 238, 238));
        UiHelper.add(activity, simResultBox,
            sim.achievable ? activity.getString(R.string.gpa_status_achievable) : activity.getString(R.string.gpa_status_unachievable),
            11, sim.achievable ? UiHelper.GREEN : Color.rgb(160, 48, 40), true);
        UiHelper.add(activity, simResultBox, sim.statusMessage, 15, UiHelper.INK, true);
        if (sim.achievable && sim.requiredGpa > 2.0 && sim.requiredGpa <= 5.0) {
            UiHelper.add(activity, simResultBox,
                activity.getString(R.string.gpa_required_average, sim.requiredGpa), 16, UiHelper.GREEN, true);
        }

        content.addView(UiHelper.button(activity, activity.getString(R.string.gpa_btn_all_grades), () -> activity.navigate(2), false));
    }
}
