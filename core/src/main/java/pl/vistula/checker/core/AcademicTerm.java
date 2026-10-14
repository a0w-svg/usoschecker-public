package pl.vistula.checker.core;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.json.*;

public final class AcademicTerm {
    public final String id;
    public final String name;
    public final String startDate;
    public final String endDate;
    public final String finishDate;

    public AcademicTerm(String id, String name, String startDate, String endDate, String finishDate) {
        this.id = id; this.name = name; this.startDate = startDate; this.endDate = endDate; this.finishDate = finishDate;
    }

    public boolean isCurrent(LocalDate today) {
        if (startDate.isEmpty() || endDate.isEmpty()) return false;
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            return !today.isBefore(start) && !today.isAfter(end);
        } catch (Exception e) {
            return false;
        }
    }

    public long daysRemaining(LocalDate today) {
        if (endDate.isEmpty()) return -1;
        try {
            LocalDate end = LocalDate.parse(endDate);
            return ChronoUnit.DAYS.between(today, end);
        } catch (Exception e) {
            return -1;
        }
    }

    public JSONObject json() throws JSONException {
        return new JSONObject().put("id", id).put("name", name).put("startDate", startDate)
            .put("endDate", endDate).put("finishDate", finishDate);
    }

    public static AcademicTerm from(JSONObject o) {
        if (o == null) return new AcademicTerm("", "", "", "", "");
        return new AcademicTerm(o.optString("id", ""), o.optString("name", ""),
            o.optString("startDate", ""), o.optString("endDate", ""), o.optString("finishDate", ""));
    }
}
