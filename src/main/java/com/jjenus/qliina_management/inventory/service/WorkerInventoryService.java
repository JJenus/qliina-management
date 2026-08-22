// ./src/main/java/com/jjenus/qliina_management/inventory/service/WorkerInventoryService.java
package com.jjenus.qliina_management.inventory.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.employee.service.ShiftGateService;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import com.jjenus.qliina_management.inventory.dto.LogStockUsageRequest;
import com.jjenus.qliina_management.inventory.dto.StockUsageDTO;
import com.jjenus.qliina_management.inventory.dto.WorkerStockItemDTO;
import com.jjenus.qliina_management.inventory.model.ShopStock;
import com.jjenus.qliina_management.inventory.model.StockAlert;
import com.jjenus.qliina_management.inventory.model.StockTransaction;
import com.jjenus.qliina_management.inventory.repository.InventoryItemRepository;
import com.jjenus.qliina_management.inventory.repository.ShopStockRepository;
import com.jjenus.qliina_management.inventory.repository.StockAlertRepository;
import com.jjenus.qliina_management.inventory.repository.StockTransactionRepository;
import com.jjenus.qliina_management.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Worker-facing inventory operations: viewing consumables in their shop,
 * logging stock used while processing orders, and reviewing their own
 * usage history.
 *
 * Access is gated on the {@code inventory.use} permission AND an active shift.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerInventoryService {

    private final InventoryItemRepository itemRepository;
    private final ShopStockRepository shopStockRepository;
    private final StockTransactionRepository transactionRepository;
    private final StockAlertRepository alertRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ShiftGateService shiftGateService;

    /**
     * Active inventory items with the worker's shop stock levels.
     * Powers the "materials used" picker and supply request form.
     */
    @Transactional(readOnly = true)
    public List<WorkerStockItemDTO> listConsumableItems(UUID businessId, UUID workerId) {
        UUID shopId = requireWorkerShop(workerId);
        requireClockedIn(businessId, workerId);

        return itemRepository.findByBusinessIdAndIsActiveTrueOrderByNameAsc(businessId).stream()
                .map(item -> {
                    BigDecimal qty = shopStockRepository.findByShopIdAndItemId(shopId, item.getId())
                            .map(ShopStock::getQuantity)
                            .orElse(null);
                    return WorkerStockItemDTO.builder()
                            .id(item.getId())
                            .name(item.getName())
                            .sku(item.getSku())
                            .category(item.getCategory() != null ? item.getCategory().toString() : null)
                            .unit(item.getUnit() != null ? item.getUnit().toString() : null)
                            .availableQuantity(qty)
                            .build();
                })
                .toList();
    }

    /**
     * Logs consumables used by the worker, deducts shop stock and records a
     * USED transaction attributed to the given order (if any).
     */
    @Transactional
    public StockUsageDTO logUsage(UUID businessId, UUID workerId, LogStockUsageRequest request) {
        String role = requireClockedIn(businessId, workerId);
        UUID shopId = requireWorkerShop(workerId);

        var item = itemRepository.findById(request.getItemId())
                .orElseThrow(() -> new BusinessException("Inventory item not found", "ITEM_NOT_FOUND"));
        if (!businessId.equals(item.getBusinessId())) {
            throw new BusinessException("Item not found in this business", "ITEM_NOT_FOUND");
        }
        if (Boolean.FALSE.equals(item.getIsActive())) {
            throw new BusinessException("Item is no longer active", "ITEM_INACTIVE");
        }
        if (request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Quantity must be positive", "INVALID_QUANTITY");
        }

        ShopStock shopStock = shopStockRepository.findByShopIdAndItemId(shopId, item.getId())
                .orElseGet(() -> {
                    ShopStock stock = new ShopStock();
                    stock.setBusinessId(businessId);
                    stock.setShopId(shopId);
                    stock.setItem(item);
                    stock.setQuantity(BigDecimal.ZERO);
                    return stock;
                });

        if (shopStock.getQuantity().compareTo(request.getQuantity()) < 0) {
            throw new BusinessException(
                    String.format("Insufficient stock for %s. Available: %s",
                            item.getName(), shopStock.getQuantity()),
                    "INSUFFICIENT_STOCK");
        }

        BigDecimal before = shopStock.getQuantity();
        shopStock.removeStock(request.getQuantity());
        shopStock = shopStockRepository.save(shopStock);

        StockTransaction transaction = new StockTransaction();
        transaction.setBusinessId(businessId);
        transaction.setShopId(shopId);
        transaction.setItem(item);
        transaction.setQuantity(request.getQuantity());
        transaction.setType(StockTransaction.TransactionType.USED);
        transaction.setReason("Order processing usage");
        transaction.setNotes(request.getNotes());
        transaction.setPerformedBy(workerId);
        transaction.setOrderId(request.getOrderId());
        transaction.setOrderItemId(request.getOrderItemId());
        transaction.setBeforeQuantity(before);
        transaction.setAfterQuantity(shopStock.getQuantity());
        transaction.setUnitCost(item.getUnitPrice());
        transaction.setTotalCost(item.getUnitPrice() != null
                ? item.getUnitPrice().multiply(request.getQuantity()) : null);
        transaction.setTransactionDate(LocalDateTime.now());
        transaction = transactionRepository.save(transaction);

        maybeRaiseLowStockAlert(businessId, shopId, item, shopStock);
        log.info("Worker {} ({}) logged usage of {} {} for order {}",
                workerId, role, request.getQuantity(), item.getUnit(), request.getOrderId());

        return toUsageDTO(transaction);
    }

    /** The worker's own usage log, newest first. */
    @Transactional(readOnly = true)
    public PageResponse<StockUsageDTO> myUsages(UUID businessId, UUID workerId, Pageable pageable) {
        Page<StockTransaction> page = transactionRepository.findByBusinessIdAndPerformedByAndType(
                businessId, workerId, StockTransaction.TransactionType.USED, pageable);
        List<StockUsageDTO> dtos = page.getContent().stream().map(this::toUsageDTO).toList();
        return PageResponse.from(new PageImpl<>(dtos, pageable, page.getTotalElements()));
    }

    // ── internals ────────────────────────────────────────────────────────────

    private void maybeRaiseLowStockAlert(UUID businessId, UUID shopId,
                                         com.jjenus.qliina_management.inventory.model.InventoryItem item,
                                         ShopStock shopStock) {
        if (shopStock.getStatus() != ShopStock.StockStatus.LOW
                && shopStock.getStatus() != ShopStock.StockStatus.CRITICAL
                && shopStock.getStatus() != ShopStock.StockStatus.OUT_OF_STOCK) {
            return;
        }
        if (alertRepository.findActiveAlertByItem(item.getId()).isPresent()) {
            return;
        }
        StockAlert alert = new StockAlert();
        alert.setBusinessId(businessId);
        alert.setShopId(shopId);
        alert.setItem(item);
        alert.setCurrentStock(shopStock.getQuantity());
        alert.setReorderLevel(item.getReorderLevel());
        alert.setStatus(StockAlert.AlertStatus.ACTIVE);
        alert.setSuggestedOrder(item.getReorderQuantity());
        alert.setSeverity(shopStock.getStatus() == ShopStock.StockStatus.CRITICAL
                || shopStock.getStatus() == ShopStock.StockStatus.OUT_OF_STOCK
                ? StockAlert.AlertSeverity.CRITICAL : StockAlert.AlertSeverity.WARNING);
        alert.setMessage(String.format("Stock level for %s is %s after usage. Current: %s",
                item.getName(), shopStock.getStatus(), shopStock.getQuantity()));
        alertRepository.save(alert);
    }

    private String requireClockedIn(UUID businessId, UUID workerId) {
        String role = shiftGateService.getPrimaryRole(workerId);
        shiftGateService.requireClockedIn(workerId, role, businessId);
        return role;
    }

    private UUID requireWorkerShop(UUID workerId) {
        User worker = userRepository.findById(workerId)
                .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
        if (worker.getPrimaryShopId() == null) {
            throw new BusinessException("No shop assigned to your account", "NO_SHOP_ASSIGNED");
        }
        return worker.getPrimaryShopId();
    }

    private StockUsageDTO toUsageDTO(StockTransaction t) {
        String orderNumber = t.getOrderId() != null
                ? orderRepository.findById(t.getOrderId()).map(o -> o.getOrderNumber()).orElse(null)
                : null;
        return StockUsageDTO.builder()
                .id(t.getId())
                .itemId(t.getItem().getId())
                .itemName(t.getItem().getName())
                .unit(t.getItem().getUnit() != null ? t.getItem().getUnit().toString() : null)
                .quantity(t.getQuantity())
                .orderNumber(orderNumber)
                .notes(t.getNotes())
                .transactionDate(t.getTransactionDate())
                .build();
    }
}
