package org.codeflow.veterineryproject.notification.dto;

/** Aynı filtrelerle kanal × durum sayısı (gönderim kaydı özet kartları). */
public record DeliverySummaryRow(String channel, String status, long count) {
}
