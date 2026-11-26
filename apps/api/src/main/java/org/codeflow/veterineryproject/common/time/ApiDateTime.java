package org.codeflow.veterineryproject.common.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** API'de tarih-saat değerleri yalnızca "yyyy-MM-dd HH:mm" (saat ve dakika) olarak gösterilir. */
public final class ApiDateTime {

    public static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    public static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private ApiDateTime() {
    }

    public static String format(Instant value) {
        return FORMAT.format(value.atZone(ZONE));
    }

    public static String format(OffsetDateTime value) {
        return FORMAT.format(value.atZoneSameInstant(ZONE));
    }

    public static String format(ZonedDateTime value) {
        return FORMAT.format(value.withZoneSameInstant(ZONE));
    }

    public static String format(LocalDateTime value) {
        return FORMAT.format(value);
    }

    /** Hem "yyyy-MM-dd HH:mm" hem ISO-8601 girişleri kabul edilir. */
    public static OffsetDateTime parseOffset(String text) {
        String value = text.trim();
        try {
            return LocalDateTime.parse(value, FORMAT).atZone(ZONE).toOffsetDateTime();
        } catch (DateTimeParseException ignored) {
            return OffsetDateTime.parse(value);
        }
    }

    public static Instant parseInstant(String text) {
        return parseOffset(text).toInstant();
    }

    public static LocalDateTime parseLocal(String text) {
        String value = text.trim();
        try {
            return LocalDateTime.parse(value, FORMAT);
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.parse(value);
        }
    }
}
