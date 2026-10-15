package pl.vistula.checker;

import org.junit.Test;
import static org.junit.Assert.*;

import pl.vistula.checker.ui.HistoryScreenController;
import pl.vistula.checker.ui.PaymentsScreenController;
import pl.vistula.checker.ui.UiHelper;

public class UiFormattersTest {

    @Test
    public void testFormatIbanStandardPolish() {
        String raw = "12345678901234567890123456";
        String formatted = PaymentsScreenController.formatIban(raw);
        assertEquals("12 3456 7890 1234 5678 9012 3456", formatted);
    }

    @Test
    public void testFormatIbanWithCountryCode() {
        String raw = "PL12345678901234567890123456";
        String formatted = PaymentsScreenController.formatIban(raw);
        assertEquals("PL 12 3456 7890 1234 5678 9012 3456", formatted);
    }

    @Test
    public void testFormatIbanShortOrNull() {
        assertEquals("", PaymentsScreenController.formatIban(null));
        assertEquals("123", PaymentsScreenController.formatIban("123"));
    }

    @Test
    public void testFormatFullDateIso() {
        String iso = "2026-10-15T14:30:00";
        String formatted = HistoryScreenController.formatFullDate(iso);
        assertTrue(formatted.contains("2026"));
        assertTrue(formatted.contains("14:30"));
        assertTrue(formatted.contains("październik"));
    }

    @Test
    public void testFormatFullDateSimple() {
        String date = "2026-10-15";
        String formatted = HistoryScreenController.formatFullDate(date);
        assertTrue(formatted.contains("2026"));
        assertTrue(formatted.contains("październik"));
    }

    @Test
    public void testUiHelperInitials() {
        assertEquals("JN", UiHelper.initials("Jan Nowak"));
        assertEquals("AN", UiHelper.initials("Anna"));
        assertEquals("US", UiHelper.initials(""));
        assertEquals("US", UiHelper.initials(null));
    }
}
