package org.codeflow.veterineryproject.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Rapor yanıtları (FG-L1). Para alanları TL (KDV dahil), oranlar yüzde ve bir ondalık basamaktır. */
public final class ReportDto {

    private ReportDto() {
    }

    /** Genel özet: muayene sayısı, durum bazında randevular, doluluk, geciken aşı ve satış toplamları. */
    public record Overview(OffsetDateTime from, OffsetDateTime to, long examinations, Map<String, Long> appointments,
                           BigDecimal occupancyRate, long overdueVaccinations, SalesTotals sales) {
    }

    /** Kesilmiş satış faturaları: adet, KDV dahil toplam ve bu faturalara uygulanmış tahsilat. */
    public record SalesTotals(long count, BigDecimal total, BigDecimal paid) {
    }

    /** Gün + şube + hekim + muayene türü bazında muayene sayısı. */
    public record ExaminationRow(LocalDate day, UUID clinicId, UUID veterinarianId, String vetName,
                                 String encounterType, long count) {
    }

    /** Hekim + şube bazında doluluk: dolu dakika / çalışma saatlerinden gelen müsait dakika. */
    public record OccupancyRow(UUID clinicId, UUID veterinarianId, String vetName, long availableMinutes,
                               long bookedMinutes, BigDecimal occupancyRate) {
    }

    /** Sonraki doz tarihi geçmiş ve aynı aşı sonradan yapılmamış kayıt. */
    public record OverdueVaccination(UUID vaccinationId, UUID animalId, String animalName, String speciesName,
                                     String vaccineName, LocalDate nextDueAt, UUID clinicId, long daysOverdue) {
    }

    /** Ürün + hareket türü bazında net miktar (giriş +, çıkış −) ve hareket satırı sayısı. */
    public record StockMovementRow(UUID productId, String productName, String productType, String transactionType,
                                   BigDecimal quantity, long movements) {
    }

    public record SalesReport(List<SalesItemRow> byItem, List<SalesDayRow> byDay) {
    }

    /** Kalem bazında satış; category ürünlerde ürün tipi (MEDICATION, FOOD ...), hizmet/diğer kalemlerde boş. */
    public record SalesItemRow(String lineType, UUID productId, UUID serviceId, String description, String category,
                               BigDecimal quantity, BigDecimal total) {
    }

    public record SalesDayRow(LocalDate day, long count, BigDecimal total) {
    }

    /** Şube bazında muayene, randevu, ciro ve doluluk. */
    public record BranchPerformance(UUID clinicId, String clinicName, long examinations, long appointments,
                                    BigDecimal revenue, BigDecimal occupancyRate) {
    }
}
