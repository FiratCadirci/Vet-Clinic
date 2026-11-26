package org.codeflow.veterineryproject.inventory.service;

import org.codeflow.veterineryproject.common.pagination.PageResponse;
import org.codeflow.veterineryproject.common.pagination.PaginationUtils;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.ExpiryAlert;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.LowStockAlert;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.MovementResponse;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.StockAlerts;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.StockItem;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.StockLevel;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.StockLot;
import org.codeflow.veterineryproject.inventory.repository.InventoryJdbcRepository;
import org.codeflow.veterineryproject.inventory.repository.InventoryJdbcRepository.MovementFilter;
import org.codeflow.veterineryproject.inventory.repository.InventoryJdbcRepository.OnHandRow;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Şube stok durumu, uyarılar (FG-F5) ve hareket geçmişi (FG-F3). Stok her zaman şube bazındadır (FG-J2). */
@Service
public class StockQueryService {

    /** SKT'si bu kadar gün içinde olan partiler "yaklaşan" sayılır (istekle değiştirilebilir). */
    public static final int DEFAULT_EXPIRY_DAYS = 30;

    private final InventoryJdbcRepository repository;

    public StockQueryService(InventoryJdbcRepository repository) {
        this.repository = repository;
    }

    /** Seçili şubenin (X-Clinic-Id) stoğu: ürün toplamı + lot dağılımı + SKT. */
    @Transactional(readOnly = true)
    public List<StockItem> clinicStock(int expiryDays) {
        UUID clinicId = TenantContext.require().clinicId();
        return items(List.of(clinicId), expiryDays);
    }

    /** Minimum stok altı ve SKT'si yaklaşan/geçmiş partiler; seçili şube ya da kullanıcının tüm şubeleri. */
    @Transactional(readOnly = true)
    public StockAlerts alerts(boolean allClinics, Collection<UUID> userClinicIds, int expiryDays) {
        UUID current = TenantContext.require().clinicId();
        List<UUID> clinics = allClinics && userClinicIds != null && !userClinicIds.isEmpty()
                ? List.copyOf(userClinicIds) : List.of(current);
        LocalDate today = LocalDate.now(StockLedger.ZONE);
        List<LowStockAlert> low = new ArrayList<>();
        List<ExpiryAlert> expiring = new ArrayList<>();
        for (StockItem item : items(clinics, expiryDays)) {
            if (item.low()) {
                low.add(new LowStockAlert(item.clinicId(), item.productId(), item.name(), item.baseUnit(),
                        item.onHand(), item.minimumStock()));
            }
            for (StockLot lot : item.lots()) {
                if (lot.expiringSoon()) {
                    expiring.add(new ExpiryAlert(item.clinicId(), item.productId(), item.name(), lot.lotId(),
                            lot.lotNumber(), lot.expiresAt(), lot.quantity(), lot.expired(),
                            ChronoUnit.DAYS.between(today, lot.expiresAt())));
                }
            }
        }
        expiring.sort(java.util.Comparator.comparing(ExpiryAlert::expiresAt));
        return new StockAlerts(expiryDays, low, expiring);
    }

    /** POSTED hareket satırları; şube filtresi verilmezse kullanıcının tüm şubeleri. */
    @Transactional(readOnly = true)
    public PageResponse<MovementResponse> movements(UUID clinicId, UUID productId, String type, LocalDate from,
                                                    LocalDate to, int page, int size, Collection<UUID> userClinicIds) {
        Collection<UUID> clinics;
        if (clinicId != null) {
            if (userClinicIds == null || !userClinicIds.contains(clinicId)) {
                throw new AccessDeniedException("Bu şube için yetkiniz yok.");
            }
            clinics = List.of(clinicId);
        } else {
            clinics = userClinicIds == null || userClinicIds.isEmpty()
                    ? List.of(TenantContext.require().clinicId()) : userClinicIds;
        }
        MovementFilter filter = new MovementFilter(clinics, productId, type, from, to);
        Pageable pageable = PaginationUtils.createPageable(page, size);
        long total = repository.countMovements(filter);
        return PageResponse.from(new PageImpl<>(
                repository.movements(filter, pageable.getPageSize(), pageable.getOffset()), pageable, total));
    }

    private List<StockItem> items(List<UUID> clinicIds, int expiryDays) {
        LocalDate today = LocalDate.now(StockLedger.ZONE);
        LocalDate warnUntil = today.plusDays(Math.max(expiryDays, 0));
        Map<String, List<OnHandRow>> grouped = new LinkedHashMap<>();
        for (OnHandRow r : repository.clinicStock(clinicIds)) {
            grouped.computeIfAbsent(r.clinicId() + ":" + r.productId(), k -> new ArrayList<>()).add(r);
        }
        List<StockItem> items = new ArrayList<>();
        for (List<OnHandRow> rows : grouped.values()) {
            OnHandRow first = rows.get(0);
            BigDecimal total = BigDecimal.ZERO;
            boolean soon = false;
            List<StockLot> lots = new ArrayList<>();
            for (OnHandRow r : rows) {
                if (r.quantity() == null) {
                    continue;
                }
                total = total.add(r.quantity());
                if (r.quantity().signum() > 0 && (r.lotId() != null || first.tracksLot())) {
                    boolean expired = r.expiresAt() != null && r.expiresAt().isBefore(today);
                    boolean expiring = r.expiresAt() != null && !r.expiresAt().isAfter(warnUntil);
                    soon |= expiring;
                    lots.add(new StockLot(r.lotId(), r.lotNumber(), r.expiresAt(), r.quantity(), expired, expiring));
                }
            }
            items.add(new StockItem(first.clinicId(), first.productId(), first.name(), first.productType(),
                    first.barcode(), first.baseUnit(), first.minimumStock(), first.tracksLot(), total,
                    StockLevel.isLow(total, first.minimumStock()), soon, lots));
        }
        return items;
    }
}
