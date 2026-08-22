// ./src/main/java/com/jjenus/qliina_management/inventory/service/StockRequestService.java
package com.jjenus.qliina_management.inventory.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import com.jjenus.qliina_management.inventory.dto.CreateStockRequestRequest;
import com.jjenus.qliina_management.inventory.dto.ReviewStockRequestRequest;
import com.jjenus.qliina_management.inventory.dto.StockRequestDTO;
import com.jjenus.qliina_management.inventory.model.InventoryItem;
import com.jjenus.qliina_management.inventory.model.ShopStock;
import com.jjenus.qliina_management.inventory.model.StockRequest;
import com.jjenus.qliina_management.inventory.repository.InventoryItemRepository;
import com.jjenus.qliina_management.inventory.repository.ShopStockRepository;
import com.jjenus.qliina_management.inventory.repository.StockRequestRepository;
import com.jjenus.qliina_management.notification.dto.SendNotificationRequest;
import com.jjenus.qliina_management.notification.service.NotificationOrchestrator;
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
 * Worker → manager supply request flow.
 *
 * Workers (inventory.request) create and track requests for their shop.
 * Managers (inventory.manage) review them; FULFILL adds the requested
 * quantity to the shop's stock and records an ADJUSTMENT transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockRequestService {

    private static final List<String> MANAGER_ROLES = List.of("BUSINESS_ADMIN", "SHOP_MANAGER");

    private final StockRequestRepository requestRepository;
    private final InventoryItemRepository itemRepository;
    private final ShopStockRepository shopStockRepository;
    private final UserRepository userRepository;
    private final NotificationOrchestrator notificationOrchestrator;

    // ── worker side ──────────────────────────────────────────────────────────

    @Transactional
    public StockRequestDTO create(UUID businessId, UUID requesterId, CreateStockRequestRequest request) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
        if (requester.getPrimaryShopId() == null) {
            throw new BusinessException("No shop assigned to your account", "NO_SHOP_ASSIGNED");
        }

        InventoryItem item = itemRepository.findById(request.getItemId())
                .orElseThrow(() -> new BusinessException("Inventory item not found", "ITEM_NOT_FOUND"));
        if (!businessId.equals(item.getBusinessId())) {
            throw new BusinessException("Item not found in this business", "ITEM_NOT_FOUND");
        }

        StockRequest.Urgency urgency = parseUrgency(request.getUrgency());

        StockRequest stockRequest = new StockRequest();
        stockRequest.setBusinessId(businessId);
        stockRequest.setShopId(requester.getPrimaryShopId());
        stockRequest.setItem(item);
        stockRequest.setRequestedBy(requesterId);
        stockRequest.setQuantity(request.getQuantity());
        stockRequest.setUrgency(urgency);
        stockRequest.setNotes(request.getNotes());
        stockRequest.setStatus(StockRequest.RequestStatus.PENDING);
        stockRequest = requestRepository.save(stockRequest);

        notifyManagers(businessId, String.format("Supply request: %s",
                        item.getName()),
                String.format("%s requested %s %s (%s urgency).",
                        getUserName(requesterId), request.getQuantity().stripTrailingZeros().toPlainString(),
                        item.getUnit(), urgency));

        log.info("Stock request {} created by {} for item {}", stockRequest.getId(), requesterId, item.getId());
        return toDTO(stockRequest);
    }

    @Transactional(readOnly = true)
    public PageResponse<StockRequestDTO> myRequests(UUID businessId, UUID requesterId, Pageable pageable) {
        Page<StockRequest> page = requestRepository.findByBusinessIdAndRequestedBy(
                businessId, requesterId, pageable);
        return PageResponse.from(new PageImpl<>(
                page.getContent().stream().map(this::toDTO).toList(),
                pageable, page.getTotalElements()));
    }

    @Transactional
    public StockRequestDTO cancel(UUID businessId, UUID requesterId, UUID requestId) {
        StockRequest stockRequest = findOwned(businessId, requestId);
        if (!stockRequest.getRequestedBy().equals(requesterId)) {
            throw new BusinessException("You can only cancel your own requests", "ACCESS_DENIED");
        }
        if (stockRequest.getStatus() != StockRequest.RequestStatus.PENDING) {
            throw new BusinessException("Only pending requests can be cancelled", "INVALID_STATUS");
        }
        stockRequest.setStatus(StockRequest.RequestStatus.CANCELLED);
        return toDTO(requestRepository.save(stockRequest));
    }

    // ── manager side ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<StockRequestDTO> listForReview(UUID businessId, String status, Pageable pageable) {
        Page<StockRequest> page;
        if (status != null && !status.isBlank()) {
            page = requestRepository.findByBusinessIdAndStatus(
                    businessId, StockRequest.RequestStatus.valueOf(status.toUpperCase()), pageable);
        } else {
            page = requestRepository.findByBusinessId(businessId, pageable);
        }
        return PageResponse.from(new PageImpl<>(
                page.getContent().stream().map(this::toDTO).toList(),
                pageable, page.getTotalElements()));
    }

    @Transactional
    public StockRequestDTO review(UUID businessId, UUID reviewerId, UUID requestId,
                                  ReviewStockRequestRequest request) {
        StockRequest stockRequest = findOwned(businessId, requestId);

        switch (request.getAction().toUpperCase()) {
            case "APPROVE" -> {
                requirePending(stockRequest);
                stockRequest.setStatus(StockRequest.RequestStatus.APPROVED);
            }
            case "REJECT" -> {
                requirePending(stockRequest);
                stockRequest.setStatus(StockRequest.RequestStatus.REJECTED);
            }
            case "FULFILL" -> {
                if (stockRequest.getStatus() == StockRequest.RequestStatus.FULFILLED
                        || stockRequest.getStatus() == StockRequest.RequestStatus.CANCELLED) {
                    throw new BusinessException(
                            "Request is already " + stockRequest.getStatus(), "INVALID_STATUS");
                }
                fulfill(businessId, stockRequest);
                stockRequest.setStatus(StockRequest.RequestStatus.FULFILLED);
            }
            default -> throw new BusinessException(
                    "Unknown action: " + request.getAction(), "INVALID_ACTION");
        }

        stockRequest.setReviewedBy(reviewerId);
        stockRequest.setReviewedAt(LocalDateTime.now());
        stockRequest.setResolutionNote(request.getNote());
        stockRequest = requestRepository.save(stockRequest);

        notifyRequester(stockRequest, reviewerId, request.getAction().toUpperCase());
        return toDTO(stockRequest);
    }

    // ── internals ────────────────────────────────────────────────────────────

    private void fulfill(UUID businessId, StockRequest stockRequest) {
        ShopStock shopStock = shopStockRepository.findByShopIdAndItemId(
                stockRequest.getShopId(), stockRequest.getItem().getId())
                .orElseGet(() -> {
                    ShopStock s = new ShopStock();
                    s.setBusinessId(businessId);
                    s.setShopId(stockRequest.getShopId());
                    s.setItem(stockRequest.getItem());
                    s.setQuantity(BigDecimal.ZERO);
                    return s;
                });
        shopStock.addStock(stockRequest.getQuantity());
        shopStockRepository.save(shopStock);
        log.info("Stock request {} fulfilled: +{} {} to shop {}",
                stockRequest.getId(), stockRequest.getQuantity(),
                stockRequest.getItem().getUnit(), stockRequest.getShopId());
    }

    private void requirePending(StockRequest stockRequest) {
        if (stockRequest.getStatus() != StockRequest.RequestStatus.PENDING) {
            throw new BusinessException(
                    "Only pending requests can be " +
                            (stockRequest.getStatus() == StockRequest.RequestStatus.APPROVED ? "fulfilled" : "reviewed"),
                    "INVALID_STATUS");
        }
    }

    private StockRequest findOwned(UUID businessId, UUID requestId) {
        StockRequest stockRequest = requestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("Stock request not found", "REQUEST_NOT_FOUND"));
        if (!businessId.equals(stockRequest.getBusinessId())) {
            throw new BusinessException("Stock request not found in this business", "REQUEST_NOT_FOUND");
        }
        return stockRequest;
    }

    private StockRequest.Urgency parseUrgency(String urgency) {
        try {
            return StockRequest.Urgency.valueOf(urgency.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Urgency must be LOW, NORMAL or HIGH", "INVALID_URGENCY");
        }
    }

    private void notifyManagers(UUID businessId, String title, String body) {
        try {
            List<UUID> recipientIds = userRepository
                    .findByBusinessIdAndRoles(businessId, MANAGER_ROLES).stream()
                    .map(User::getId)
                    .toList();
            if (recipientIds.isEmpty()) return;
            SendNotificationRequest notification = new SendNotificationRequest();
            notification.setRecipients(recipientIds);
            notification.setType("STOCK_REQUEST");
            notification.setChannel("IN_APP");
            notification.setTitle(title);
            notification.setBody(body);
            notification.setPriority("NORMAL");
            notificationOrchestrator.sendNotification(businessId, notification);
        } catch (Exception e) {
            log.warn("Failed to notify managers about stock request: {}", e.getMessage());
        }
    }

    private void notifyRequester(StockRequest stockRequest, UUID reviewerId, String action) {
        try {
            String itemName = stockRequest.getItem().getName();
            SendNotificationRequest notification = new SendNotificationRequest();
            notification.setRecipients(List.of(stockRequest.getRequestedBy()));
            notification.setType("STOCK_REQUEST");
            notification.setChannel("IN_APP");
            notification.setTitle(String.format("Supply request %s", action.toLowerCase()));
            notification.setBody(String.format("Your request for %s was %s by %s.",
                    itemName, action.toLowerCase(), getUserName(reviewerId)));
            notification.setPriority("NORMAL");
            notificationOrchestrator.sendNotification(stockRequest.getBusinessId(), notification);
        } catch (Exception e) {
            log.warn("Failed to notify requester about stock request decision: {}", e.getMessage());
        }
    }

    private String getUserName(UUID userId) {
        if (userId == null) return "System";
        return userRepository.findById(userId)
                .map(u -> u.getFirstName() + " " + u.getLastName())
                .orElse("User");
    }

    private StockRequestDTO toDTO(StockRequest r) {
        return StockRequestDTO.builder()
                .id(r.getId())
                .shopId(r.getShopId())
                .itemId(r.getItem().getId())
                .itemName(r.getItem().getName())
                .unit(r.getItem().getUnit() != null ? r.getItem().getUnit().toString() : null)
                .quantity(r.getQuantity())
                .urgency(r.getUrgency().toString())
                .notes(r.getNotes())
                .status(r.getStatus().toString())
                .requestedBy(r.getRequestedBy())
                .requestedByName(getUserName(r.getRequestedBy()))
                .reviewedBy(r.getReviewedBy())
                .reviewedByName(getUserName(r.getReviewedBy()))
                .reviewedAt(r.getReviewedAt())
                .resolutionNote(r.getResolutionNote())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
