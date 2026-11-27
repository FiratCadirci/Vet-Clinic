package org.codeflow.veterineryproject.billing.service;

import org.codeflow.veterineryproject.common.api.BusinessRuleException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Satış satırı fiyatlama kuralı (FG-G1). Fiyatlar KDV hariçtir:
 * brüt = round(miktar × birim, 2); indirim tutar olarak verilir (oran verilirse round(brüt × oran / 100, 2));
 * net = brüt − indirim (indirim brütü aşamaz); KDV = round(net × KDV oranı / 100, 2) satır bazında, yarım kuruş
 * yukarı; satır toplamı = net + KDV. Satış toplamları satırların toplamıdır.
 *
 * <p>Aynı formül V28'deki invoice_lines üretilmiş sütunlarında ve web önizlemesinde ({@code components/admin/pricing.ts})
 * vardır; DB değerleri saklanan kaynaktır, bu sınıf indirim tutarını üretir ve doğrular. Üç uygulamanın eşliği
 * aynı test vektörleriyle kanıtlanır (LinePricingTest, pricing.test.ts, BillingSalesIT).
 */
public final class LinePricing {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private LinePricing() {
    }

    public record PricedLine(BigDecimal gross, BigDecimal discount, BigDecimal net, BigDecimal tax, BigDecimal total) {
    }

    public record Totals(BigDecimal gross, BigDecimal discount, BigDecimal net, BigDecimal tax, BigDecimal total) {
    }

    /**
     * @param discountAmount indirim tutarı (TL); null ise {@code discountRate} kullanılır
     * @param discountRate   yüzde indirim (0–100); ikisi de null ise indirim yok
     * @throws BusinessRuleException indirim satır tutarını aşıyorsa (422)
     */
    public static PricedLine price(BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountAmount,
                                   BigDecimal discountRate, BigDecimal taxRate) {
        BigDecimal gross = round(quantity.multiply(unitPrice));
        BigDecimal discount = discountAmount != null ? round(discountAmount)
                : discountRate != null ? round(gross.multiply(discountRate).divide(HUNDRED)) : BigDecimal.ZERO.setScale(2);
        if (discount.compareTo(gross) > 0) {
            throw new BusinessRuleException("İndirim satır tutarını aşamaz.");
        }
        BigDecimal net = gross.subtract(discount);
        BigDecimal tax = round(net.multiply(taxRate == null ? BigDecimal.ZERO : taxRate).divide(HUNDRED));
        return new PricedLine(gross, discount, net, tax, net.add(tax));
    }

    public static Totals totals(List<PricedLine> lines) {
        BigDecimal gross = BigDecimal.ZERO.setScale(2);
        BigDecimal discount = gross;
        BigDecimal tax = gross;
        for (PricedLine l : lines) {
            gross = gross.add(l.gross());
            discount = discount.add(l.discount());
            tax = tax.add(l.tax());
        }
        BigDecimal net = gross.subtract(discount);
        return new Totals(gross, discount, net, tax, net.add(tax));
    }

    /** İki ondalığa, yarım kuruş sıfırdan uzağa (PostgreSQL round(numeric, 2) ve JS Math.round ile aynı, pozitiflerde). */
    static BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
