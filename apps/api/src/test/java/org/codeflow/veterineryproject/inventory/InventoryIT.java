package org.codeflow.veterineryproject.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.event.DomainEvents.StockLowEvent;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.common.test.ApiTestSupport;
import org.codeflow.veterineryproject.common.test.InventoryTestData;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.ConsumptionResult;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.LotAllocation;
import org.codeflow.veterineryproject.inventory.service.InsufficientStockException;
import org.codeflow.veterineryproject.inventory.service.StockConsumptionService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.codeflow.veterineryproject.common.test.InventoryTestData.*;
import static org.codeflow.veterineryproject.common.test.TenantTestData.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Stok, ilaç ve mama: FG-F1..F5, FG-J2; izolasyon (404), yetki (403), eşzamanlı tüketim. */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class InventoryIT extends ApiTestSupport {

    private static final String S1 = BRANCH_A1_S1.toString();
    private static final String S2 = BRANCH_A1_S2.toString();
    private static final String PRODUCTS = "/api/v1/products";
    private static final String INV = "/api/v1/inventory";
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Istanbul"));

    @Autowired
    private StockConsumptionService consumption;

    @Autowired
    private ApplicationEvents events;

    @BeforeAll
    static void seed() {
        InventoryTestData.seed(superUserDataSource());
    }

    @AfterAll
    static void cleanup() {
        InventoryTestData.cleanup(superUserDataSource());
    }

    // ------------------------------------------------------------------ FG-F1 ürün kartı

    @Test
    void productCrud_barcodeUniqueWithinOrganization() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_KEEPER_A1);
        String barcode = "869" + System.nanoTime();
        Map<String, Object> req = new HashMap<>(product("Mama Yetişkin 15 kg", "FOOD", false, 3));
        req.put("barcode", barcode);
        req.put("purchasePrice", "800.00");
        JsonNode created = body(postJson(t, S1, PRODUCTS, req).andExpect(status().isCreated()));
        String id = created.get("id").asText();
        assertThat(created.get("sku").asText()).startsWith("URN-");
        assertThat(created.get("productType").asText()).isEqualTo("FOOD");
        assertThat(created.get("purchasePrice").decimalValue()).isEqualByComparingTo("800");
        assertThat(created.get("salePrice").decimalValue()).isEqualByComparingTo("1000");
        assertThat(created.get("taxRate").decimalValue()).isEqualByComparingTo("10");
        assertThat(created.get("minimumStock").decimalValue()).isEqualByComparingTo("3");
        assertThat(created.get("active").asBoolean()).isTrue();

        getJson(t, PRODUCTS + "/" + id).andExpect(status().isOk()).andExpect(jsonPath("$.barcode").value(barcode));
        JsonNode list = body(getJson(t, PRODUCTS + "?search=" + barcode).andExpect(status().isOk()));
        assertThat(list.get("totalElements").asLong()).isEqualTo(1);

        // Aynı barkod organizasyon içinde ikinci kez kullanılamaz.
        Map<String, Object> dup = new HashMap<>(product("Başka ürün", "MEDICATION", true, 0));
        dup.put("barcode", barcode);
        postJson(t, S1, PRODUCTS, dup).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_BARCODE_EXISTS"));
        String other = createProduct(t, "Barkodsuz ürün", "CONSUMABLE", false, 0);
        patchJson(t, S1, PRODUCTS + "/" + other, Map.of("barcode", barcode)).andExpect(status().isConflict());

        JsonNode updated = body(patchJson(t, S1, PRODUCTS + "/" + id,
                Map.of("salePrice", "1100.50", "active", false, "tracksLot", true)).andExpect(status().isOk()));
        assertThat(updated.get("salePrice").decimalValue()).isEqualByComparingTo("1100.50");
        assertThat(updated.get("active").asBoolean()).isFalse();
        assertThat(updated.get("tracksLot").asBoolean()).isTrue();
        assertThat(updated.get("name").asText()).isEqualTo("Mama Yetişkin 15 kg");

        // Doğrulama: geçersiz ürün tipi / eksik ad 400; X-Clinic-Id yazmada zorunlu.
        postJson(t, S1, PRODUCTS, Map.of("name", "X", "productType", "TOY", "baseUnit", "adet"))
                .andExpect(status().isBadRequest());
        postJson(t, S1, PRODUCTS, Map.of("productType", "FOOD", "baseUnit", "adet")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.name").exists());
        postJson(t, PRODUCTS, product("Y", "FOOD", false, 0)).andExpect(status().isBadRequest());

        // Hareketi olmayan ürün silinir; hareketi olan silinemez (409).
        deleteJson(t, S1, PRODUCTS + "/" + other).andExpect(status().isNoContent());
        getJson(t, PRODUCTS + "/" + other).andExpect(status().isNotFound());
        String used = createProduct(t, "Kullanılmış ürün", "CONSUMABLE", false, 0);
        receive(t, S1, used, 1, null, null).andExpect(status().isCreated());
        deleteJson(t, S1, PRODUCTS + "/" + used).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_IN_USE"));
    }

    // ------------------------------------------------------------------ FG-F2 lot zorunluluğu

    @Test
    void receipt_lotTrackedProductRequiresLotAndExpiry() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_KEEPER_A1);
        String vaccine = createProduct(t, "Kuduz aşısı", "VACCINE", true, 0);
        receive(t, S1, vaccine, 5, null, TODAY.plusDays(90)).andExpect(status().isUnprocessableEntity());
        receive(t, S1, vaccine, 5, "KD-1", null).andExpect(status().isUnprocessableEntity());
        receive(t, S1, vaccine, 5, "KD-0", TODAY.minusDays(1)).andExpect(status().isUnprocessableEntity());
        postJson(t, S1, INV + "/receipts", Map.of("productId", vaccine, "quantity", 0, "lotNumber", "KD-1",
                "expiresAt", TODAY.plusDays(90).toString())).andExpect(status().isBadRequest());

        JsonNode r = body(receive(t, S1, vaccine, 5, "KD-1", TODAY.plusDays(90)).andExpect(status().isCreated()));
        assertThat(r.get("transactionType").asText()).isEqualTo("RECEIPT");
        assertThat(r.path("stock").get("onHand").decimalValue()).isEqualByComparingTo("5");
        // Aynı lot farklı SKT ile girilemez.
        receive(t, S1, vaccine, 1, "KD-1", TODAY.plusDays(91)).andExpect(status().isUnprocessableEntity());

        JsonNode item = stockItem(t, S1, vaccine);
        assertThat(item.get("onHand").decimalValue()).isEqualByComparingTo("5");
        assertThat(item.get("lots")).hasSize(1);
        assertThat(item.get("lots").get(0).get("lotNumber").asText()).isEqualTo("KD-1");
        assertThat(item.get("lots").get(0).get("expiresAt").asText()).isEqualTo(TODAY.plusDays(90).toString());

        // Lot takipsiz üründe lot istenmez.
        String gauze = createProduct(t, "Gazlı bez", "CONSUMABLE", false, 0);
        receive(t, S1, gauze, 20, null, null).andExpect(status().isCreated());
        assertThat(stockItem(t, S1, gauze).get("onHand").decimalValue()).isEqualByComparingTo("20");
    }

    // ------------------------------------------------------------------ FG-F3 düzeltme, transfer, hareket kaydı

    @Test
    void adjustmentTransferAndMovementHistory() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_KEEPER_A1);
        String drug = createProduct(t, "Amoksisilin 250 mg", "MEDICATION", true, 0);
        receive(t, S1, drug, 10, "AMX-1", TODAY.plusDays(200)).andExpect(status().isCreated());

        adjust(t, S1, drug, -3, "AMX-1", null, "Kırık ambalaj").andExpect(status().isCreated())
                .andExpect(jsonPath("$.stock.onHand").value(7.0));
        adjust(t, S1, drug, 2, "AMX-1", null, "Ay sonu sayımı").andExpect(status().isCreated())
                .andExpect(jsonPath("$.stock.onHand").value(9.0));
        adjust(t, S1, drug, 0, "AMX-1", null, "Sıfır fark").andExpect(status().isBadRequest());
        adjust(t, S1, drug, -1, "AMX-1", null, "").andExpect(status().isBadRequest());
        adjust(t, S1, drug, -1, null, null, "Lotsuz").andExpect(status().isUnprocessableEntity());
        // Yeni lot ile sayım fazlası SKT ister.
        adjust(t, S1, drug, 1, "AMX-NEW", null, "Sayım fazlası").andExpect(status().isUnprocessableEntity());

        JsonNode tr = body(postJson(t, S1, INV + "/transfers", Map.of("targetClinicId", S2, "productId", drug,
                "quantity", 4, "notes", "Acil ihtiyaç")).andExpect(status().isCreated()));
        assertThat(tr.path("source").get("transactionType").asText()).isEqualTo("TRANSFER_OUT");
        assertThat(tr.path("source").path("stock").get("onHand").decimalValue()).isEqualByComparingTo("5");
        assertThat(tr.path("target").get("transactionType").asText()).isEqualTo("TRANSFER_IN");
        assertThat(tr.path("target").path("stock").get("onHand").decimalValue()).isEqualByComparingTo("4");

        // Lot SKT'siyle hedef şubeye taşınır.
        JsonNode target = stockItem(t, S2, drug);
        assertThat(target.get("lots").get(0).get("lotNumber").asText()).isEqualTo("AMX-1");
        assertThat(target.get("lots").get(0).get("expiresAt").asText()).isEqualTo(TODAY.plusDays(200).toString());

        // Hedef: aynı şube 422, başka klinik grubu 422, kullanıcının şubesi olmayan (başka organizasyon) 403.
        transfer(t, S1, S1, drug, 1).andExpect(status().isUnprocessableEntity());
        transfer(t, S1, BRANCH_A2_S1.toString(), drug, 1).andExpect(status().isUnprocessableEntity());
        transfer(t, S1, BRANCH_B1_S1.toString(), drug, 1).andExpect(status().isForbidden());

        JsonNode history = body(getJson(t, S1, INV + "/movements?clinicId=" + S1 + "&productId=" + drug)
                .andExpect(status().isOk()));
        List<String> types = new ArrayList<>();
        history.get("content").forEach(m -> types.add(m.get("transactionType").asText()));
        assertThat(types).containsExactlyInAnyOrder("RECEIPT", "ADJUSTMENT", "ADJUSTMENT", "TRANSFER_OUT");
        JsonNode out = body(getJson(t, S1, INV + "/movements?clinicId=" + S1 + "&productId=" + drug
                + "&type=TRANSFER_OUT&from=" + TODAY + "&to=" + TODAY).andExpect(status().isOk()));
        assertThat(out.get("totalElements").asLong()).isEqualTo(1);
        JsonNode line = out.get("content").get(0);
        assertThat(line.get("quantity").decimalValue()).isEqualByComparingTo("-4");
        assertThat(line.get("lotNumber").asText()).isEqualTo("AMX-1");
        assertThat(line.get("referenceType").asText()).isEqualTo("TRANSFER");
        assertThat(line.get("createdByName").asText()).isEqualTo(EMAIL_KEEPER_A1);
        JsonNode future = body(getJson(t, S1, INV + "/movements?productId=" + drug + "&from=" + TODAY.plusDays(1))
                .andExpect(status().isOk()));
        assertThat(future.get("totalElements").asLong()).isZero();
        // Tüm şubelerim: kaynak ve hedef hareketleri birlikte.
        JsonNode all = body(getJson(t, S1, INV + "/movements?productId=" + drug).andExpect(status().isOk()));
        assertThat(all.get("totalElements").asLong()).isEqualTo(5);
        getJson(t, S1, INV + "/movements?clinicId=" + BRANCH_B1_S1).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ FG-F4 negatif stok reddi

    @Test
    void stockNeverGoesNegative_rejectedWith409AndNothingWritten() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_KEEPER_A1);
        String drug = createProduct(t, "Meloksikam", "MEDICATION", true, 0);
        receive(t, S1, drug, 2, "MLX-1", TODAY.plusDays(100)).andExpect(status().isCreated());
        long before = lineCount(drug);

        adjust(t, S1, drug, -5, "MLX-1", null, "Sayım eksiği").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        postJson(t, S1, INV + "/transfers", Map.of("targetClinicId", S2, "productId", drug, "quantity", 3,
                "lotNumber", "MLX-1")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        transfer(t, S1, S2, drug, 3).andExpect(status().isConflict());

        assertThat(lineCount(drug)).isEqualTo(before);
        assertThat(transactionCount(drug)).isEqualTo(1);
        assertThat(stockItem(t, S1, drug).get("onHand").decimalValue()).isEqualByComparingTo("2");
        assertThat(stockItem(t, S2, drug)).isNull();

        try (TenantContext.Scope ignored = TenantContext.withTenant(ORG_A, KEEPER_A1, BRANCH_A1_S1)) {
            assertThatThrownBy(() -> consumption.consume(BRANCH_A1_S1, UUID.fromString(drug), new BigDecimal("3"),
                    "TEST", null)).isInstanceOf(InsufficientStockException.class);
        }
        assertThat(lineCount(drug)).isEqualTo(before);
    }

    // ------------------------------------------------------------------ FG-F4 FEFO

    @Test
    void consume_usesFefoOrderAndWritesPostedDispense() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_KEEPER_A1);
        UUID vaccine = UUID.fromString(createProduct(t, "Karma aşı", "VACCINE", true, 0));
        receive(t, S1, vaccine.toString(), 5, "LATE", TODAY.plusDays(300)).andExpect(status().isCreated());
        receive(t, S1, vaccine.toString(), 3, "SOON", TODAY.plusDays(20)).andExpect(status().isCreated());
        receive(t, S1, vaccine.toString(), 2, "MID", TODAY.plusDays(100)).andExpect(status().isCreated());

        UUID ref = UUID.randomUUID();
        try (TenantContext.Scope ignored = TenantContext.withTenant(ORG_A, KEEPER_A1, BRANCH_A1_S1)) {
            List<LotAllocation> plan = consumption.allocateFefo(BRANCH_A1_S1, vaccine, new BigDecimal("6"));
            assertThat(plan).extracting(LotAllocation::lotNumber).containsExactly("SOON", "MID", "LATE");
            assertThat(plan).extracting(LotAllocation::quantity)
                    .usingElementComparator(BigDecimal::compareTo)
                    .containsExactly(new BigDecimal("3"), new BigDecimal("2"), new BigDecimal("1"));
            assertThatThrownBy(() -> consumption.allocateFefo(BRANCH_A1_S1, vaccine, new BigDecimal("11")))
                    .isInstanceOf(InsufficientStockException.class);

            ConsumptionResult r = consumption.consume(BRANCH_A1_S1, vaccine, new BigDecimal("4"), "VACCINE_RECORD", ref);
            assertThat(r.transactionId()).isNotNull();
            assertThat(r.allocations()).extracting(LotAllocation::lotNumber).containsExactly("SOON", "MID");
            assertThat(r.stock().onHand()).isEqualByComparingTo("6");
        }

        JsonNode item = stockItem(t, S1, vaccine.toString());
        Map<String, BigDecimal> lots = new HashMap<>();
        item.get("lots").forEach(l -> lots.put(l.get("lotNumber").asText(), l.get("quantity").decimalValue()));
        assertThat(lots).containsOnlyKeys("MID", "LATE");
        assertThat(lots.get("MID")).isEqualByComparingTo("1");
        assertThat(lots.get("LATE")).isEqualByComparingTo("5");

        JsonNode dispense = body(getJson(t, S1, INV + "/movements?productId=" + vaccine + "&type=DISPENSE")
                .andExpect(status().isOk()));
        assertThat(dispense.get("totalElements").asLong()).isEqualTo(2);
        assertThat(dispense.get("content").get(0).get("referenceType").asText()).isEqualTo("VACCINE_RECORD");
        assertThat(dispense.get("content").get(0).get("referenceId").asText()).isEqualTo(ref.toString());
    }

    // ------------------------------------------------------------------ FG-F4 / FOG-3 eşzamanlılık

    @Test
    void concurrentConsumption_neverDrivesStockNegative() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_KEEPER_A1);
        UUID product = UUID.fromString(createProduct(t, "Serum fizyolojik", "CONSUMABLE", false, 0));
        receive(t, S1, product.toString(), 5, null, null).andExpect(status().isCreated());

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> jobs = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                jobs.add(() -> {
                    try (TenantContext.Scope ignored = TenantContext.withTenant(ORG_A, KEEPER_A1, BRANCH_A1_S1)) {
                        consumption.consume(BRANCH_A1_S1, product, BigDecimal.ONE, "TEST", null);
                        return true;
                    } catch (InsufficientStockException ex) {
                        return false;
                    }
                });
            }
            int ok = 0;
            for (Future<Boolean> f : pool.invokeAll(jobs)) {
                ok += f.get() ? 1 : 0;
            }
            assertThat(ok).isEqualTo(5);
        } finally {
            pool.shutdownNow();
        }
        assertThat(stockItem(t, S1, product.toString()).get("onHand").decimalValue()).isEqualByComparingTo("0");
    }

    /** DB guard'ın kanıtı: aynı son birimi iki bağlantı aynı anda POSTED yapmaya çalışır; ikincisi 23514 alır. */
    @Test
    void stockGuard_secondConcurrentPostOfLastUnitIsRejected() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_KEEPER_A1);
        UUID product = UUID.fromString(createProduct(t, "Son birim", "CONSUMABLE", false, 0));
        receive(t, S1, product.toString(), 1, null, null).andExpect(status().isCreated());
        UUID location = defaultLocation(BRANCH_A1_S1);

        DataSource pool = pooledVetAppDataSource(2);
        ExecutorService exec = Executors.newSingleThreadExecutor();
        try (Connection first = pool.getConnection(); Connection second = pool.getConnection()) {
            UUID tx1 = draftDispense(first, location, product);
            UUID tx2 = draftDispense(second, location, product);
            post(first, tx1);
            Future<?> blocked = exec.submit(() -> {
                post(second, tx2);
                second.commit();
                return null;
            });
            awaitAdvisoryWaiter();
            first.commit();
            Throwable failure = null;
            try {
                blocked.get();
            } catch (ExecutionException ex) {
                failure = ex.getCause();
            }
            assertThat(failure).isInstanceOf(SQLException.class);
            assertThat(((SQLException) failure).getSQLState()).isEqualTo("23514");
            second.rollback();
        } finally {
            exec.shutdownNow();
            ((com.zaxxer.hikari.HikariDataSource) pool).close();
        }
        assertThat(stockItem(t, S1, product.toString()).get("onHand").decimalValue()).isEqualByComparingTo("0");
    }

    // ------------------------------------------------------------------ FG-J2 şube izolasyonu, 404, 403

    @Test
    void stockIsSeparatePerBranch() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_KEEPER_A1);
        String product = createProduct(t, "Şube ürünü", "FOOD", false, 0);
        receive(t, S1, product, 7, null, null).andExpect(status().isCreated());
        receive(t, S2, product, 2, null, null).andExpect(status().isCreated());

        assertThat(stockItem(t, S1, product).get("onHand").decimalValue()).isEqualByComparingTo("7");
        assertThat(stockItem(t, S2, product).get("onHand").decimalValue()).isEqualByComparingTo("2");

        String s2Only = token(ORG_A_CODE, EMAIL_KEEPER_A1S2);
        assertThat(stockItem(s2Only, S2, product).get("onHand").decimalValue()).isEqualByComparingTo("2");
        getJson(s2Only, S1, INV + "/stock").andExpect(status().isForbidden());
        receive(s2Only, S1, product, 1, null, null).andExpect(status().isForbidden());
        // Şube 2 kullanıcısının hareket geçmişinde şube 1 hareketleri yoktur.
        JsonNode history = body(getJson(s2Only, S2, INV + "/movements?productId=" + product).andExpect(status().isOk()));
        assertThat(history.get("totalElements").asLong()).isEqualTo(1);
        assertThat(history.get("content").get(0).get("clinicId").asText()).isEqualTo(S2);
    }

    @Test
    void otherOrganizationGets404() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_KEEPER_A1);
        String product = createProduct(t, "Org A ürünü", "MEDICATION", false, 0);
        receive(t, S1, product, 3, null, null).andExpect(status().isCreated());

        String b = token(ORG_B_CODE, EMAIL_KEEPER_B1);
        String b1 = BRANCH_B1_S1.toString();
        getJson(b, PRODUCTS + "/" + product).andExpect(status().isNotFound());
        patchJson(b, b1, PRODUCTS + "/" + product, Map.of("name", "x")).andExpect(status().isNotFound());
        receive(b, b1, product, 1, null, null).andExpect(status().isNotFound());
        adjust(b, b1, product, -1, null, null, "deneme").andExpect(status().isNotFound());
        JsonNode list = body(getJson(b, PRODUCTS + "?search=Org A").andExpect(status().isOk()));
        assertThat(list.get("totalElements").asLong()).isZero();
        assertThat(stockItem(b, b1, product)).isNull();
    }

    @Test
    void veterinarianRoleCanReadButNotWriteStock() throws Exception {
        String keeper = token(ORG_A_CODE, EMAIL_KEEPER_A1);
        String product = createProduct(keeper, "Hekim görür", "MEDICATION", false, 0);

        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        getJson(vet, S1, INV + "/stock").andExpect(status().isOk());
        getJson(vet, PRODUCTS + "/" + product).andExpect(status().isOk());
        postJson(vet, S1, PRODUCTS, product("Hekim ürünü", "FOOD", false, 0)).andExpect(status().isForbidden());
        patchJson(vet, S1, PRODUCTS + "/" + product, Map.of("name", "x")).andExpect(status().isForbidden());
        receive(vet, S1, product, 1, null, null).andExpect(status().isForbidden());
        adjust(vet, S1, product, 1, null, null, "deneme").andExpect(status().isForbidden());
        transfer(vet, S1, S2, product, 1).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ FG-F5 uyarılar

    @Test
    void alerts_lowStockAndExpiringLots_andStockLowEvent() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_KEEPER_A1);
        String drug = createProduct(t, "Uyarı ilacı", "MEDICATION", true, 5);
        receive(t, S1, drug, 4, "EXP-SOON", TODAY.plusDays(10)).andExpect(status().isCreated());
        receive(t, S1, drug, 3, "EXP-LATE", TODAY.plusDays(120)).andExpect(status().isCreated());
        assertThat(events.stream(StockLowEvent.class)).isEmpty();

        adjust(t, S1, drug, -2, "EXP-LATE", null, "Fire").andExpect(status().isCreated())
                .andExpect(jsonPath("$.stock.low").value(true));
        List<StockLowEvent> low = events.stream(StockLowEvent.class).toList();
        assertThat(low).hasSize(1);
        assertThat(low.get(0).productId()).isEqualTo(UUID.fromString(drug));
        assertThat(low.get(0).clinicId()).isEqualTo(BRANCH_A1_S1);
        assertThat(low.get(0).quantity()).isEqualByComparingTo("5");
        // Zaten minimumdayken yeni düşüş tekrar olay üretmez.
        adjust(t, S1, drug, -1, "EXP-SOON", null, "Fire").andExpect(status().isCreated());
        assertThat(events.stream(StockLowEvent.class)).hasSize(1);

        JsonNode alerts = body(getJson(t, S1, INV + "/alerts").andExpect(status().isOk()));
        assertThat(alerts.get("expiryDays").asInt()).isEqualTo(30);
        JsonNode lowItem = find(alerts.get("lowStock"), "productId", drug);
        assertThat(lowItem).isNotNull();
        assertThat(lowItem.get("onHand").decimalValue()).isEqualByComparingTo("4");
        assertThat(lowItem.get("minimumStock").decimalValue()).isEqualByComparingTo("5");
        List<String> expiringLots = new ArrayList<>();
        alerts.get("expiring").forEach(e -> {
            if (e.get("productId").asText().equals(drug)) {
                expiringLots.add(e.get("lotNumber").asText() + ":" + e.get("daysLeft").asLong());
            }
        });
        assertThat(expiringLots).containsExactly("EXP-SOON:10");

        JsonNode wide = body(getJson(t, S1, INV + "/alerts?expiryDays=200").andExpect(status().isOk()));
        assertThat(wide.get("expiring").toString()).contains("EXP-LATE");
        // Tüm şubelerim: diğer şubede stok yok ama minimum tanımlı → şube 2 için de düşük stok.
        JsonNode all = body(getJson(t, S1, INV + "/alerts?allClinics=true").andExpect(status().isOk()));
        List<String> clinics = new ArrayList<>();
        all.get("lowStock").forEach(a -> {
            if (a.get("productId").asText().equals(drug)) {
                clinics.add(a.get("clinicId").asText());
            }
        });
        assertThat(clinics).contains(S1, S2);
        JsonNode stock = stockItem(t, S1, drug);
        assertThat(stock.get("low").asBoolean()).isTrue();
        assertThat(stock.get("expiringSoon").asBoolean()).isTrue();
    }

    // ------------------------------------------------------------------ yardımcılar

    private static Map<String, Object> product(String name, String type, boolean tracksLot, int minimum) {
        return Map.of("name", name, "productType", type, "baseUnit", "adet", "tracksLot", tracksLot,
                "minimumStock", minimum, "salePrice", "1000.00", "taxRate", 10);
    }

    private String createProduct(String token, String name, String type, boolean tracksLot, int minimum) throws Exception {
        return body(postJson(token, S1, PRODUCTS, product(name, type, tracksLot, minimum))
                .andExpect(status().isCreated())).get("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions receive(String token, String clinic, String productId,
                                                                      int qty, String lot, LocalDate expires) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("productId", productId);
        body.put("quantity", qty);
        body.put("lotNumber", lot);
        body.put("expiresAt", expires == null ? null : expires.toString());
        return postJson(token, clinic, INV + "/receipts", body);
    }

    private org.springframework.test.web.servlet.ResultActions adjust(String token, String clinic, String productId,
                                                                     int delta, String lot, LocalDate expires,
                                                                     String reason) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("productId", productId);
        body.put("quantityDelta", delta);
        body.put("lotNumber", lot);
        body.put("expiresAt", expires == null ? null : expires.toString());
        body.put("reason", reason);
        return postJson(token, clinic, INV + "/adjustments", body);
    }

    private org.springframework.test.web.servlet.ResultActions transfer(String token, String clinic, String target,
                                                                       String productId, int qty) throws Exception {
        return postJson(token, clinic, INV + "/transfers", Map.of("targetClinicId", target, "productId", productId,
                "quantity", qty));
    }

    private org.springframework.test.web.servlet.ResultActions patchJson(String token, String clinic, String url,
                                                                        Object body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(url)
                .header("Authorization", "Bearer " + token).header("X-Clinic-Id", clinic)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private org.springframework.test.web.servlet.ResultActions deleteJson(String token, String clinic, String url)
            throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url)
                .header("Authorization", "Bearer " + token).header("X-Clinic-Id", clinic));
    }

    private JsonNode stockItem(String token, String clinic, String productId) throws Exception {
        JsonNode stock = body(getJson(token, clinic, INV + "/stock").andExpect(status().isOk()));
        return find(stock, "productId", productId);
    }

    private static JsonNode find(JsonNode array, String field, String value) {
        for (JsonNode n : array) {
            if (n.get(field).asText().equals(value)) {
                return n;
            }
        }
        return null;
    }

    private static long lineCount(String productId) throws SQLException {
        return count("SELECT count(*) FROM vet.inventory_transaction_lines WHERE product_id = ?", productId);
    }

    private static long transactionCount(String productId) throws SQLException {
        return count("SELECT count(DISTINCT transaction_id) FROM vet.inventory_transaction_lines WHERE product_id = ?",
                productId);
    }

    private static long count(String sql, String productId) throws SQLException {
        try (Connection c = superUserDataSource().getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(productId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static UUID defaultLocation(UUID clinic) throws SQLException {
        try (Connection c = superUserDataSource().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id FROM vet.inventory_locations WHERE clinic_id = ? AND code = 'MAIN'")) {
            ps.setObject(1, clinic);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    /** vet_app bağlantısında RLS bağlamını kurar ve tek OUT satırlı DRAFT DISPENSE hareketi yazar (commit edilmez). */
    private static UUID draftDispense(Connection c, UUID location, UUID product) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("SELECT set_config('app.organization_id', '" + ORG_A + "', true), set_config('app.user_id', '"
                    + KEEPER_A1 + "', true), set_config('app.clinic_id', '" + BRANCH_A1_S1 + "', true)");
        }
        UUID tx = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO vet.inventory_transactions (id, organization_id, "
                + "location_id, transaction_no, transaction_type, status) VALUES (?, ?, ?, ?, 'DISPENSE', 'DRAFT')")) {
            ps.setObject(1, tx);
            ps.setObject(2, ORG_A);
            ps.setObject(3, location);
            ps.setString(4, "IT-" + tx);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO vet.inventory_transaction_lines (organization_id, "
                + "transaction_id, product_id, direction, quantity) VALUES (?, ?, ?, 'OUT', 1)")) {
            ps.setObject(1, ORG_A);
            ps.setObject(2, tx);
            ps.setObject(3, product);
            ps.executeUpdate();
        }
        return tx;
    }

    private static void post(Connection c, UUID tx) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE vet.inventory_transactions SET status = 'POSTED', posted_at = now() WHERE id = ?")) {
            ps.setObject(1, tx);
            ps.executeUpdate();
        }
    }

    /** İkinci bağlantı stok guard'ının advisory lock'unda beklemeye başlayana kadar bekler. */
    private static void awaitAdvisoryWaiter() throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            try (Connection c = superUserDataSource().getConnection(); Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT count(*) FROM pg_stat_activity "
                         + "WHERE wait_event_type = 'Lock' AND wait_event = 'advisory'")) {
                rs.next();
                if (rs.getLong(1) > 0) {
                    return;
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("İkinci bağlantı advisory lock beklemesine girmedi.");
    }
}
