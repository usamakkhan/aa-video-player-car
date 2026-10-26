package app.aavideoplayercar;

import app.aavideoplayercar.maintenance.ServiceInput;
import java.time.LocalDate;
import org.junit.Test;
import static org.junit.Assert.*;

public class ServiceInputTest {
    @Test public void datesMileageAndExactNotesRoundTrip() {
        ServiceInput input = new ServiceInput(" Oil change ", "Engine", "2024-02-29", "12000", "2025-02-28", "18000", "  Preserve\nnotes exactly  ");
        assertEquals("Oil change", input.title);
        assertEquals(Long.valueOf(LocalDate.of(2024, 2, 29).toEpochDay()), input.serviceDay);
        assertEquals("2024-02-29", ServiceInput.date(input.serviceDay));
        assertEquals("12000", ServiceInput.mileage(input.serviceKm));
        assertEquals("  Preserve\nnotes exactly  ", input.notes);
    }
    @Test public void unknownLegacyDatesAndMileageStayUnknown() {
        ServiceInput input = new ServiceInput("Legacy", "", "", "", "", "", "original");
        assertNull(input.serviceDay); assertNull(input.serviceKm); assertNull(input.dueDay); assertNull(input.dueKm);
    }
    @Test public void malformedAndImpossibleDatesAreRejected() {
        for (String date : new String[]{"2023-02-29", "2024-13-01", "2024-2-01", "1899-12-31", "banana"})
            assertThrows(IllegalArgumentException.class, () -> new ServiceInput("Service", "", date, "", "", "", ""));
    }
    @Test public void negativeFractionalAndOverflowMileageAreRejected() {
        for (String km : new String[]{"-1", "1.5", "1000000000", "99999999999999999999999", "abc"})
            assertThrows(IllegalArgumentException.class, () -> new ServiceInput("Service", "", "", km, "", "", ""));
    }
    @Test public void dueValuesCannotPrecedeService() {
        assertThrows(IllegalArgumentException.class, () -> new ServiceInput("Service", "", "2026-10-12", "", "2026-10-11", "", ""));
        assertThrows(IllegalArgumentException.class, () -> new ServiceInput("Service", "", "", "1000", "", "999", ""));
        assertThrows(IllegalArgumentException.class, () -> new ServiceInput(" ", "", "", "", "", "", ""));
    }
}
