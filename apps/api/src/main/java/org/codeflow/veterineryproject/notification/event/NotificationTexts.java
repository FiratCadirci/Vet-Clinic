package org.codeflow.veterineryproject.notification.event;

import org.codeflow.veterineryproject.common.time.ApiDateTime;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Bildirim metinlerinde Türkçe tarih / saat / miktar biçimleri (Europe/Istanbul). */
final class NotificationTexts {

    private static final Locale TR = Locale.forLanguageTag("tr-TR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", TR);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMMM yyyy HH:mm", TR);

    private NotificationTexts() {
    }

    static String date(LocalDate value) {
        return DATE.format(value);
    }

    static String dateTime(OffsetDateTime value) {
        return DATE_TIME.format(value.atZoneSameInstant(ApiDateTime.ZONE));
    }

    static String quantity(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
