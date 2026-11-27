package org.codeflow.veterineryproject.billing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.codeflow.veterineryproject.billing.service.LinePricing.PricedLine;
import org.codeflow.veterineryproject.billing.service.LinePricing.Totals;
import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FG-G1 fiyatlama kuralı (DB'siz). Vektörler web önizlemesinin testiyle ortaktır
 * (src/test/resources/billing/pricing-vectors.json; web: components/admin/pricing.test.ts).
 */
class LinePricingTest {

    static JsonNode vectors() throws IOException {
        try (InputStream in = LinePricingTest.class.getResourceAsStream("/billing/pricing-vectors.json")) {
            return new ObjectMapper().readTree(in);
        }
    }

    static PricedLine price(JsonNode v) {
        return LinePricing.price(dec(v, "quantity"), dec(v, "unitPrice"), dec(v, "discountAmount"), null,
                dec(v, "taxRate"));
    }

    static BigDecimal dec(JsonNode node, String field) {
        return new BigDecimal(node.get(field).asText());
    }

    @Test
    void everySharedVectorMatchesGrossDiscountNetTaxAndTotal() throws IOException {
        for (JsonNode v : vectors().get("lines")) {
            PricedLine p = price(v);
            JsonNode e = v.get("expected");
            String name = v.get("name").asText();
            assertThat(p.gross()).as(name + " brüt").isEqualByComparingTo(dec(e, "gross"));
            assertThat(p.discount()).as(name + " indirim").isEqualByComparingTo(dec(e, "discount"));
            assertThat(p.net()).as(name + " net").isEqualByComparingTo(dec(e, "net"));
            assertThat(p.tax()).as(name + " KDV").isEqualByComparingTo(dec(e, "tax"));
            assertThat(p.total()).as(name + " toplam").isEqualByComparingTo(dec(e, "total"));
            assertThat(p.total()).as(name + " toplam = net + KDV").isEqualByComparingTo(p.net().add(p.tax()));
        }
    }

    @Test
    void saleTotalsAreSumOfLines_grossMinusDiscountPlusTaxEqualsTotal() throws IOException {
        JsonNode sale = vectors().get("sale");
        List<PricedLine> lines = new ArrayList<>();
        sale.get("lines").forEach(l -> lines.add(price(l)));

        Totals t = LinePricing.totals(lines);

        JsonNode e = sale.get("expected");
        assertThat(t.gross()).isEqualByComparingTo(dec(e, "gross"));
        assertThat(t.discount()).isEqualByComparingTo(dec(e, "discount"));
        assertThat(t.tax()).isEqualByComparingTo(dec(e, "tax"));
        assertThat(t.total()).isEqualByComparingTo(dec(e, "total"));
        assertThat(t.total()).isEqualByComparingTo(lines.stream().map(PricedLine::total).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void discountAboveGrossIsRejected() throws IOException {
        JsonNode bad = vectors().get("invalid");
        assertThatThrownBy(() -> price(bad)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("İndirim");
    }

    @Test
    void percentageDiscountIsConvertedToAmountOnGross() {
        PricedLine p = LinePricing.price(new BigDecimal("3"), new BigDecimal("33.33"), null, new BigDecimal("12.5"),
                new BigDecimal("20"));

        assertThat(p.gross()).isEqualByComparingTo("99.99");
        assertThat(p.discount()).isEqualByComparingTo("12.50"); // 12,49875 → 12,50
        assertThat(p.net()).isEqualByComparingTo("87.49");
        assertThat(p.tax()).isEqualByComparingTo("17.50"); // 17,498 → 17,50
        assertThat(p.total()).isEqualByComparingTo("104.99");
    }

    @Test
    void noDiscountAndNoTaxRateMeansZero() {
        PricedLine p = LinePricing.price(new BigDecimal("2"), new BigDecimal("10"), null, null, null);
        assertThat(p.discount()).isEqualByComparingTo("0");
        assertThat(p.tax()).isEqualByComparingTo("0");
        assertThat(p.total()).isEqualByComparingTo("20.00");
    }
}
