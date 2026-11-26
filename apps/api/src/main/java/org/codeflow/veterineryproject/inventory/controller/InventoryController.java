package org.codeflow.veterineryproject.inventory.controller;

import jakarta.validation.Valid;
import org.codeflow.veterineryproject.auth.security.JwtAuthenticatedUser;
import org.codeflow.veterineryproject.auth.security.Permission;
import org.codeflow.veterineryproject.common.pagination.PageResponse;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.AdjustmentRequest;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.MovementResponse;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.MovementResult;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.ReceiptRequest;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.StockAlerts;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.StockItem;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.TransferRequest;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.TransferResult;
import org.codeflow.veterineryproject.inventory.service.StockMovementService;
import org.codeflow.veterineryproject.inventory.service.StockQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Şube stoğu (X-Clinic-Id): durum, uyarılar, hareket geçmişi; giriş, sayım düzeltmesi ve transfer.
 * Okuma inventory.read, yazma inventory.write.
 */
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final StockQueryService queries;
    private final StockMovementService movements;

    public InventoryController(StockQueryService queries, StockMovementService movements) {
        this.queries = queries;
        this.movements = movements;
    }

    @GetMapping("/stock")
    @PreAuthorize("hasAuthority('" + Permission.INVENTORY_READ + "')")
    public List<StockItem> stock(@RequestParam(defaultValue = "" + StockQueryService.DEFAULT_EXPIRY_DAYS) int expiryDays) {
        return queries.clinicStock(expiryDays);
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasAuthority('" + Permission.INVENTORY_READ + "')")
    public StockAlerts alerts(@AuthenticationPrincipal JwtAuthenticatedUser principal,
                              @RequestParam(defaultValue = "false") boolean allClinics,
                              @RequestParam(defaultValue = "" + StockQueryService.DEFAULT_EXPIRY_DAYS) int expiryDays) {
        return queries.alerts(allClinics, principal.clinicIds(), expiryDays);
    }

    @GetMapping("/movements")
    @PreAuthorize("hasAuthority('" + Permission.INVENTORY_READ + "')")
    public PageResponse<MovementResponse> movements(
            @AuthenticationPrincipal JwtAuthenticatedUser principal,
            @RequestParam(required = false) UUID clinicId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return queries.movements(clinicId, productId, type, from, to, page, size, principal.clinicIds());
    }

    @PostMapping("/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permission.INVENTORY_WRITE + "')")
    public MovementResult receive(@RequestHeader("X-Clinic-Id") String clinicId,
                                  @Valid @RequestBody ReceiptRequest request) {
        return movements.receive(request);
    }

    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permission.INVENTORY_WRITE + "')")
    public MovementResult adjust(@RequestHeader("X-Clinic-Id") String clinicId,
                                 @Valid @RequestBody AdjustmentRequest request) {
        return movements.adjust(request);
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permission.INVENTORY_WRITE + "')")
    public TransferResult transfer(@RequestHeader("X-Clinic-Id") String clinicId,
                                   @AuthenticationPrincipal JwtAuthenticatedUser principal,
                                   @Valid @RequestBody TransferRequest request) {
        return movements.transfer(request, principal.clinicIds());
    }
}
