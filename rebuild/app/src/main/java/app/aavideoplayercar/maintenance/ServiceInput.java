package app.aavideoplayercar.maintenance;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Validation is independent of the UI and database. Dates are calendar days, not timezone-sensitive instants. */
public final class ServiceInput {
    public final String title, type, notes;
    public final Long serviceDay, serviceKm, dueDay, dueKm;
    public ServiceInput(String title, String type, String date, String km, String dueDate, String dueKm, String notes) {
        this.title = title.trim(); this.type = type.trim(); this.notes = notes;
        if (this.title.isEmpty()) throw new IllegalArgumentException("Enter a service title.");
        if (this.title.length() > 200 || this.type.length() > 100 || notes.length() > 100000)
            throw new IllegalArgumentException("Title, type or notes are too long.");
        serviceDay = day(date, "Service date"); this.serviceKm = mileage(km, "Service mileage");
        this.dueDay = day(dueDate, "Due date"); this.dueKm = mileage(dueKm, "Due mileage");
        if (serviceDay != null && this.dueDay != null && this.dueDay < serviceDay)
            throw new IllegalArgumentException("Due date must not precede the service date.");
        if (this.serviceKm != null && this.dueKm != null && this.dueKm < this.serviceKm)
            throw new IllegalArgumentException("Due mileage must not precede the service mileage.");
    }
    private static Long day(String value, String label) {
        value = value.trim(); if (value.isEmpty()) return null;
        try {
            if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) throw new DateTimeParseException("format", value, 0);
            LocalDate date = LocalDate.parse(value);
            if (date.getYear() < 1900) throw new DateTimeParseException("year", value, 0);
            return date.toEpochDay();
        } catch (DateTimeParseException e) { throw new IllegalArgumentException(label + " must be a valid YYYY-MM-DD date (1900 or later)."); }
    }
    private static Long mileage(String value, String label) {
        value = value.trim(); if (value.isEmpty()) return null;
        try {
            if (!value.matches("[0-9]+")) throw new NumberFormatException();
            long parsed = Long.parseLong(value);
            if (parsed > 999999999L) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException e) { throw new IllegalArgumentException(label + " must be whole kilometers between 0 and 999999999."); }
    }
    public static String date(Long day) { return day == null ? "" : LocalDate.ofEpochDay(day).toString(); }
    public static String mileage(Long km) { return km == null ? "" : Long.toString(km); }
}
