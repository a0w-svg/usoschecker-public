package pl.vistula.checker.core;

import java.time.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlanBrowserTest {
    private LessonTime lesson(String id, String date, String value) { return LessonTime.of(new Record(id,"plan",id,value,"",date)); }
    @Test public void nextDayRespectsTheActiveSearchAndSavedRange() {
        List<LessonTime> rows = List.of(lesson("A","2026-10-14","09:00 – 10:00\nSala 101"),lesson("B","2026-10-16","09:00 – 10:00\nSala 204"));
        assertEquals(LocalDate.of(2026,10,16), PlanBrowser.nextDay(rows,LocalDate.of(2026,10,13),LocalDate.of(2026,10,20),"204"));
        assertNull(PlanBrowser.nextDay(rows,LocalDate.of(2026,10,13),LocalDate.of(2026,10,15),"204"));
        assertNull(PlanBrowser.nextDay(rows,LocalDate.of(2026,10,13),LocalDate.of(2026,10,20),"315"));
        assertEquals(LocalDate.of(2026,10,14),PlanBrowser.nextDay(List.of(rows.get(1),rows.get(0)),LocalDate.of(2026,10,13),LocalDate.of(2026,10,20),""));
    }
    @Test public void dailyListsIncludeIncomingOvernightClassesAndExcludeEndBoundaries() {
        List<LessonTime> rows = List.of(lesson("A","2026-10-13","23:00 – 01:00"),lesson("B","2026-10-13","22:00 – 00:00"));
        List<LessonTime> visible = PlanBrowser.select(rows,LocalDate.of(2026,10,14),"list","");
        assertEquals(1,visible.size()); assertEquals("A",visible.get(0).record.id);
        assertEquals(LocalDate.of(2026,10,14),PlanBrowser.nextDay(rows,LocalDate.of(2026,10,13),LocalDate.of(2026,10,15),""));
    }
    @Test public void weeklyAndAgendaModesPreserveTheirDistinctRanges() {
        List<LessonTime> rows = List.of(lesson("A","2026-10-14","09:00 – 10:00\nĆwiczenia"),lesson("B","2026-10-21","09:00 – 10:00"));
        assertEquals(1,PlanBrowser.select(rows,LocalDate.of(2026,10,14),"week","").size());
        assertEquals(2,PlanBrowser.select(rows,LocalDate.of(2026,10,14),"agenda","").size());
        assertEquals(1,PlanBrowser.select(rows,LocalDate.of(2026,10,14),"list","cwiczenia").size());
    }
}
