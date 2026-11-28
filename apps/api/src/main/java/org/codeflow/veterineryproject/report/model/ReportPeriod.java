package org.codeflow.veterineryproject.report.model;

import org.codeflow.veterineryproject.common.time.ApiDateTime;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

/**
 * Rapor zaman aralığı (değer nesnesi): {@code from} dahil, {@code to} hariç. Verilmezse son 30 gün.
 * En fazla 1 yıl (366 gün); daha uzun aralıklar rapor sorgularını gereksiz ağırlaştırır.
 */
public record ReportPeriod(OffsetDateTime from, OffsetDateTime to) {

    public static final int DEFAULT_DAYS = 30;
    public static final int MAX_DAYS = 366;

    public ReportPeriod {
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("Başlangıç tarihi bitiş tarihinden önce olmalıdır.");
        }
        if (Duration.between(from, to).compareTo(Duration.ofDays(MAX_DAYS)) > 0) {
            throw new IllegalArgumentException("Rapor aralığı en fazla 1 yıl olabilir.");
        }
    }

    /** İstekteki metinlerden aralık (ISO-8601 ya da "yyyy-MM-dd HH:mm"); eksik uçlar varsayılanla tamamlanır. */
    public static ReportPeriod parse(String fromText, String toText, OffsetDateTime now) {
        OffsetDateTime to = isBlank(toText) ? now : ApiDateTime.parseOffset(toText);
        OffsetDateTime from = isBlank(fromText) ? to.minusDays(DEFAULT_DAYS) : ApiDateTime.parseOffset(fromText);
        return new ReportPeriod(from, to);
    }

    /** Aralığın kapsadığı ilk yerel gün (Türkiye saati). */
    public LocalDate firstDay() {
        return from.atZoneSameInstant(ApiDateTime.ZONE).toLocalDate();
    }

    /** Aralıktan sonraki ilk yerel gün (hariç): bitiş gece yarısıysa o gün, değilse ertesi gün. */
    public LocalDate endDayExclusive() {
        ZonedDateTime end = to.atZoneSameInstant(ApiDateTime.ZONE);
        LocalDate day = end.toLocalDate();
        return end.toLocalTime().equals(LocalTime.MIDNIGHT) ? day : day.plusDays(1);
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
