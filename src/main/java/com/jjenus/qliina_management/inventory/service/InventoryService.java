package com.jjenus.qliina_management.inventory.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.identity.repository.ShopRepository;
import com.jjenus.qliina_management.inventory.dto.*;
import com.jjenus.qliina_management.inventory.model.*;
import com.jjenus.qliina_management.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jjenus.qliina_management.common.AddressDTO;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {
    
    private final InventoryItemRepository itemRepository;
    private final ShopStockRepository shopStockRepository;
    private final StockTransactionRepository transactionRepository;
    private final SupplierRepository supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final StockAlertRepository alertRepository;
    private final ShopRepository shopRepository;
    
    // ==================== Inventory Items ====================
    
    @Transactional(readOnly = true)
    public PageResponse<InventoryItemDTO> listItems(UUID businessId, InventoryFilter filter, Pageable pageable) {
        Specification<InventoryItem> spec = createItemSpecification(businessId, filter);
        Page<InventoryItem> page = itemRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(this::mapToItemDTO));
    }
    
    @Transactional(readOnly = true)
    public InventoryItemDetailDTO getItem(UUID itemId) {
        InventoryItem item = itemRepository.findById(itemId)
            .orElseThrow(() -> new BusinessException("Item not found", "ITEM_NOT_FOUND"));
        return mapToItemDetailDTO(item);
    }
    
    @Transactional
    public InventoryItemDTO createItem(UUID businessId, CreateInventoryItemRequest request) {
        // Check if SKU already exists
        if (itemRepository.existsBySku(request.getSku())) {
            throw new BusinessException("SKU already exists", "DUPLICATE_SKU", "sku");
        }
        
        InventoryItem item = new InventoryItem();
        item.setBusinessId(businessId);
        item.setSku(request.getSku());
        item.setName(request.getName());
        item.setDescription(request.getDescription());
        item.setCategory(InventoryItem.ItemCategory.valueOf(request.getCategory()));
        item.setUnit(InventoryItem.UnitOfMeasure.valueOf(request.getUnit()));
        item.setReorderLevel(request.getReorderLevel());
        item.setReorderQuantity(request.getReorderQuantity());
        item.setUnitPrice(request.getUnitPrice());
        item.setSupplierId(request.getSupplierId());
        item.setMinStockLevel(request.getMinStockLevel());
        item.setMaxStockLevel(request.getMaxStockLevel());
        item.setLocation(request.getLocation());
        item.setIsActive(true);
        
        item = itemRepository.save(item);
        
        // Create initial stock entries for all shops
        List<UUID> shopIds = shopRepository.findActiveShopIdsByBusinessId(businessId);
        for (UUID shopId : shopIds) {
            ShopStock shopStock = new ShopStock();
            shopStock.setBusinessId(businessId);
            shopStock.setShopId(shopId);
            shopStock.setItem(item);
            shopStock.setQuantity(BigDecimal.ZERO);
            shopStock.setStatus(ShopStock.StockStatus.NORMAL);
            shopStockRepository.save(shopStock);
        }
        
        return mapToItemDTO(item);
    }
    
    @Transactional
    public InventoryItemDTO updateItem(UUID itemId, UpdateInventoryItemRequest request) {
        InventoryItem item = itemRepository.findById(itemId)
            .orElseThrow(() -> new BusinessException("Item not found", "ITEM_NOT_FOUND"));
        
        if (request.getName() != null) {
            item.setName(request.getName());
        }
        if (request.getDescription() != null) {
            item.setDescription(request.getDescription());
        }
        if (request.getCategory() != null) {
            item.setCategory(InventoryItem.ItemCategory.valueOf(request.getCategory()));
        }
        if (request.getUnit() != null) {
            item.setUnit(InventoryItem.UnitOfMeasure.valueOf(request.getUnit()));
        }
        if (request.getReorderLevel() != null) {
            item.setReorderLevel(request.getReorderLevel());
        }
        if (request.getReorderQuantity() != null) {
            item.setReorderQuantity(request.getReorderQuantity());
        }
        if (request.getUnitPrice() != null) {
            item.setUnitPrice(request.getUnitPrice());
        }
        if (request.getSupplierId() != null) {
            item.setSupplierId(request.getSupplierId());
        }
        if (request.getIsActive() != null) {
            item.setIsActive(request.getIsActive());
        }
        if (request.getMinStockLevel() != null) {
            item.setMinStockLevel(request.getMinStockLevel());
        }
        if (request.getMaxStockLevel() != null) {
            item.setMaxStockLevel(request.getMaxStockLevel());
        }
        if (request.getLocation() != null) {
            item.setLocation(request.getLocation());
        }
        
        item = itemRepository.save(item);
        return mapToItemDTO(item);
    }
    
    @Transactional(readOnly = true)
    public List<ShopStockDTO> getShopStock(UUID businessId, UUID shopId) {
        Page<ShopStock> shopStocks = shopStockRepository.findByBusinessIdAndShopId(businessId, shopId, Pageable.unpaged());
        
        String shopName = shopRepository.findById(shopId)
            .map(s -> s.getName())
            .orElse("");
        
        List<ShopStockDTO.ShopStockItemDTO> items = shopStocks.getContent().stream()
            .map(ss -> {
                Integer suggestedOrder = null;
                if (ss.getStatus() == ShopStock.StockStatus.LOW || ss.getStatus() == ShopStock.StockStatus.CRITICAL) {
                    suggestedOrder = ss.getItem().getReorderQuantity();
                }
                
                return ShopStockDTO.ShopStockItemDTO.builder()
                    .itemId(ss.getItem().getId())
                    .itemName(ss.getItem().getName())
                    .sku(ss.getItem().getSku())
                    .quantity(ss.getQuantity())
                    .unit(ss.getItem().getUnit().toString())
                    .lastRestocked(ss.getLastRestocked())
                    .status(ss.getStatus().toString())
                    .locationDetails(ss.getLocationDetails())
                    .reorderLevel(BigDecimal.valueOf(ss.getItem().getReorderLevel()))
                    .suggestedOrder(suggestedOrder)
                    .build();
            })
            .collect(Collectors.toList());
        
        return Collections.singletonList(ShopStockDTO.builder()
            .shopId(shopId)
            .shopName(shopName)
            .items(items)
            .build());
    }
    
    @Transactional
    public StockAdjustmentResultDTO adjustStock(UUID businessId, AdjustStockRequest request) {
        List<StockAdjustmentResultDTO.AdjustmentResult> results = new ArrayList<>();
        List<StockAlertDTO> generatedAlerts = new ArrayList<>();
        
        for (AdjustStockRequest.StockAdjustment adjustment : request.getAdjustments()) {
            try {
                InventoryItem item = itemRepository.findById(adjustment.getItemId())
                    .orElseThrow(() -> new BusinessException("Item not found", "ITEM_NOT_FOUND"));
                
                ShopStock shopStock = shopStockRepository.findByShopIdAndItemId(request.getShopId(), adjustment.getItemId())
                    .orElseGet(() -> {
                        ShopStock newStock = new ShopStock();
                        newStock.setBusinessId(businessId);
                        newStock.setShopId(request.getShopId());
                        newStock.setItem(item);
                        newStock.setQuantity(BigDecimal.ZERO);
                        newStock.setStatus(ShopStock.StockStatus.NORMAL);
                        return shopStockRepository.save(newStock);
                    });
                
                BigDecimal previousQuantity = shopStock.getQuantity();
                BigDecimal adjustmentQuantity = adjustment.getQuantity();
                
                // Create transaction
                StockTransaction transaction = new StockTransaction();
                transaction.setBusinessId(businessId);
                transaction.setShopId(request.getShopId());
                transaction.setItem(item);
                transaction.setQuantity(adjustmentQuantity.abs());
                transaction.setType(determineTransactionType(adjustment));
                transaction.setReason(adjustment.getReason());
                transaction.setReference(adjustment.getReference());
                transaction.setNotes(adjustment.getNotes());
                transaction.setPerformedBy(getCurrentUserId());
                transaction.setBeforeQuantity(previousQuantity);
                transaction.setTransactionDate(LocalDateTime.now());
                
                // Apply adjustment
                if (adjustmentQuantity.compareTo(BigDecimal.ZERO) > 0) {
                    shopStock.addStock(adjustmentQuantity);
                    transaction.setAfterQuantity(shopStock.getQuantity());
                } else {
                    shopStock.removeStock(adjustmentQuantity.abs());
                    transaction.setAfterQuantity(shopStock.getQuantity());
                }
                
                transaction.setUnitCost(item.getUnitPrice());
                transaction.setTotalCost(item.getUnitPrice().multiply(adjustmentQuantity.abs()));
                
                transactionRepository.save(transaction);
                shopStock = shopStockRepository.save(shopStock);
                
                // Check for low stock alerts
                if (shopStock.getStatus() == ShopStock.StockStatus.LOW || 
                    shopStock.getStatus() == ShopStock.StockStatus.CRITICAL) {
                    StockAlert alert = createStockAlert(businessId, request.getShopId(), item, shopStock);
                    generatedAlerts.add(mapToAlertDTO(alert));
                }
                
                results.add(StockAdjustmentResultDTO.AdjustmentResult.builder()
                    .itemId(item.getId())
                    .itemName(item.getName())
                    .previousQuantity(previousQuantity)
                    .newQuantity(shopStock.getQuantity())
                    .adjustment(adjustmentQuantity)
                    .success(true)
                    .message("Stock adjusted successfully")
                    .build());
                
            } catch (Exception e) {
                results.add(StockAdjustmentResultDTO.AdjustmentResult.builder()
                    .itemId(adjustment.getItemId())
                    .success(false)
                    .message(e.getMessage())
                    .build());
            }
        }
        
        return StockAdjustmentResultDTO.builder()
            .shopId(request.getShopId())
            .results(results)
            .generatedAlerts(generatedAlerts)
            .build();
    }
    
    @Transactional
    public void transferStock(UUID businessId, TransferStockRequest request) {
        // Validate source shop has enough stock
        ShopStock sourceStock = shopStockRepository.findByShopIdAndItemId(request.getSourceShopId(), request.getItemId())
            .orElseThrow(() -> new BusinessException("Source shop does not have this item", "ITEM_NOT_FOUND_IN_SOURCE"));
        
        if (sourceStock.getQuantity().compareTo(request.getQuantity()) < 0) {
            throw new BusinessException("Insufficient stock in source shop", "INSUFFICIENT_STOCK");
        }
        
        // Get or create target shop stock
        ShopStock targetStock = shopStockRepository.findByShopIdAndItemId(request.getTargetShopId(), request.getItemId())
            .orElseGet(() -> {
                InventoryItem item = itemRepository.findById(request.getItemId())
                    .orElseThrow(() -> new BusinessException("Item not found", "ITEM_NOT_FOUND"));
                ShopStock newStock = new ShopStock();
                newStock.setBusinessId(businessId);
                newStock.setShopId(request.getTargetShopId());
                newStock.setItem(item);
                newStock.setQuantity(BigDecimal.ZERO);
                return shopStockRepository.save(newStock);
            });
        
        // Remove from source
        sourceStock.removeStock(request.getQuantity());
        shopStockRepository.save(sourceStock);
        
        // Add to target
        targetStock.addStock(request.getQuantity());
        shopStockRepository.save(targetStock);
        
        // Create transactions
        createTransferTransaction(businessId, request.getSourceShopId(), request.getItemId(), 
            request.getQuantity().negate(), "TRANSFER_OUT", "Transfer to shop " + request.getTargetShopId(), 
            request.getNotes());
        
        createTransferTransaction(businessId, request.getTargetShopId(), request.getItemId(), 
            request.getQuantity(), "TRANSFER_IN", "Transfer from shop " + request.getSourceShopId(), 
            request.getNotes());
    }
    
    @Transactional(readOnly = true)
    public PageResponse<StockTransactionDTO> listTransactions(UUID businessId, UUID shopId, UUID itemId, 
                                                               String type, LocalDateTime fromDate, 
                                                               LocalDateTime toDate, Pageable pageable) {
        StockTransaction.TransactionType transactionType = type != null ? 
            StockTransaction.TransactionType.valueOf(type) : null;
        
        Page<StockTransaction> page = transactionRepository.findByFilters(
            businessId, shopId, itemId, transactionType, fromDate, toDate, pageable);
        
        return PageResponse.from(page.map(this::mapToTransactionDTO));
    }
    
    @Transactional(readOnly = true)
    public List<StockAlertDTO> getStockAlerts(UUID businessId, UUID shopId) {
        List<StockAlert> alerts;
        if (shopId != null) {
            alerts = alertRepository.findActiveAlertsByShop(shopId);
        } else {
            alerts = alertRepository.findActiveAlerts(businessId);
        }
        return alerts.stream().map(this::mapToAlertDTO).collect(Collectors.toList());
    }
    
    @Transactional
    public void acknowledgeAlert(UUID alertId) {
        StockAlert alert = alertRepository.findById(alertId)
            .orElseThrow(() -> new BusinessException("Alert not found", "ALERT_NOT_FOUND"));
        
        alert.setStatus(StockAlert.AlertStatus.ACKNOWLEDGED);
        alert.setAcknowledgedAt(LocalDateTime.now());
        alert.setAcknowledgedBy(getCurrentUserId());
        alertRepository.save(alert);
    }
    
    @Transactional
    public void resolveAlert(UUID alertId) {
        StockAlert alert = alertRepository.findById(alertId)
            .orElseThrow(() -> new BusinessException("Alert not found", "ALERT_NOT_FOUND"));
        
        alert.setStatus(StockAlert.AlertStatus.RESOLVED);
        alert.setResolvedAt(LocalDateTime.now());
        alert.setResolvedBy(getCurrentUserId());
        alertRepository.save(alert);
    }
    
    // ==================== Suppliers ====================
    
    @Transactional(readOnly = true)
    public PageResponse<SupplierDTO> listSuppliers(UUID businessId, String search, Pageable pageable) {
        Page<Supplier> page;
        if (search != null && !search.trim().isEmpty()) {
            page = supplierRepository.searchSuppliers(businessId, search, pageable);
        } else {
            page = supplierRepository.findActiveSuppliers(businessId, pageable);
        }
        return PageResponse.from(page.map(this::mapToSupplierDTO));
    }
    
    @Transactional(readOnly = true)
    public SupplierDTO getSupplier(UUID supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId)
            .orElseThrow(() -> new BusinessException("Supplier not found", "SUPPLIER_NOT_FOUND"));
        return mapToSupplierDTO(supplier);
    }
    
    @Transactional
    public SupplierDTO createSupplier(UUID businessId, CreateSupplierRequest request) {
        Supplier supplier = new Supplier();
        supplier.setBusinessId(businessId);
        supplier.setName(request.getName());
        supplier.setContactPerson(request.getContactPerson());
        supplier.setEmail(request.getEmail());
        supplier.setPhone(request.getPhone());
        supplier.setPaymentTerms(request.getPaymentTerms());
        supplier.setLeadTimeDays(request.getLeadTimeDays());
        supplier.setCategories(request.getCategories() != null ? request.getCategories() : new ArrayList<>());
        supplier.setMinimumOrderAmount(request.getMinimumOrderAmount());
        supplier.setShippingCost(request.getShippingCost());
        supplier.setTaxId(request.getTaxId());
        supplier.setWebsite(request.getWebsite());
        supplier.setNotes(request.getNotes());
        supplier.setIsActive(true);
        supplier.setRating(BigDecimal.ZERO);
        
        if (request.getAddress() != null) {
            Supplier.Address address = new Supplier.Address();
            address.setAddressLine1(request.getAddress().getAddressLine1());
            address.setAddressLine2(request.getAddress().getAddressLine2());
            address.setCity(request.getAddress().getCity());
            address.setState(request.getAddress().getState());
            address.setPostalCode(request.getAddress().getPostalCode());
            address.setCountry(request.getAddress().getCountry());
            supplier.setAddress(address);
        }
        
        supplier = supplierRepository.save(supplier);
        return mapToSupplierDTO(supplier);
    }
    
    @Transactional
    public SupplierDTO updateSupplier(UUID supplierId, UpdateSupplierRequest request) {
        Supplier supplier = supplierRepository.findById(supplierId)
            .orElseThrow(() -> new BusinessException("Supplier not found", "SUPPLIER_NOT_FOUND"));
        
        if (request.getName() != null) {
            supplier.setName(request.getName());
        }
        if (request.getContactPerson() != null) {
            supplier.setContactPerson(request.getContactPerson());
        }
        if (request.getEmail() != null) {
            supplier.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            supplier.setPhone(request.getPhone());
        }
        if (request.getPaymentTerms() != null) {
            supplier.setPaymentTerms(request.getPaymentTerms());
        }
        if (request.getLeadTimeDays() != null) {
            supplier.setLeadTimeDays(request.getLeadTimeDays());
        }
        if (request.getCategories() != null) {
            supplier.setCategories(request.getCategories());
        }
        if (request.getRating() != null) {
            supplier.setRating(request.getRating());
        }
        if (request.getIsActive() != null) {
            supplier.setIsActive(request.getIsActive());
        }
        if (request.getMinimumOrderAmount() != null) {
            supplier.setMinimumOrderAmount(request.getMinimumOrderAmount());
        }
        if (request.getShippingCost() != null) {
            supplier.setShippingCost(request.getShippingCost());
        }
        if (request.getTaxId() != null) {
            supplier.setTaxId(request.getTaxId());
        }
        if (request.getWebsite() != null) {
            supplier.setWebsite(request.getWebsite());
        }
        if (request.getNotes() != null) {
            supplier.setNotes(request.getNotes());
        }
        if (request.getAddress() != null) {
            Supplier.Address address = new Supplier.Address();
            address.setAddressLine1(request.getAddress().getAddressLine1());
            address.setAddressLine2(request.getAddress().getAddressLine2());
            address.setCity(request.getAddress().getCity());
            address.setState(request.getAddress().getState());
            address.setPostalCode(request.getAddress().getPostalCode());
            address.setCountry(request.getAddress().getCountry());
            supplier.setAddress(address);
        }
        
        supplier = supplierRepository.save(supplier);
        return mapToSupplierDTO(supplier);
    }
    
    // ==================== Purchase Orders ====================
    
    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderDTO> listPurchaseOrders(UUID businessId, UUID supplierId, UUID shopId,
                                                               String status, LocalDateTime fromDate,
                                                               LocalDateTime toDate, Pageable pageable) {
        PurchaseOrder.PurchaseOrderStatus orderStatus = status != null ?
            PurchaseOrder.PurchaseOrderStatus.valueOf(status) : null;
        
        Page<PurchaseOrder> page = purchaseOrderRepository.findByFilters(
            businessId, supplierId, shopId, orderStatus, fromDate, toDate, pageable);
        
        return PageResponse.from(page.map(this::mapToPurchaseOrderDTO));
    }
    
    @Transactional(readOnly = true)
    public PurchaseOrderDTO getPurchaseOrder(UUID poId) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
            .orElseThrow(() -> new BusinessException("Purchase order not found", "PO_NOT_FOUND"));
        return mapToPurchaseOrderDTO(po);
    }
    
    @Transactional
    public PurchaseOrderDTO createPurchaseOrder(UUID businessId, CreatePurchaseOrderRequest request) {
        Supplier supplier = supplierRepository.findById(request.getSupplierId())
            .orElseThrow(() -> new BusinessException("Supplier not found", "SUPPLIER_NOT_FOUND"));
        
        PurchaseOrder po = new PurchaseOrder();
        po.setBusinessId(businessId);
        po.setPoNumber(generatePONumber(businessId));
        po.setSupplier(supplier);
        po.setShopId(request.getShopId());
        po.setStatus(PurchaseOrder.PurchaseOrderStatus.DRAFT);
        po.setOrderDate(LocalDateTime.now());
        po.setExpectedDelivery(request.getExpectedDelivery());
        po.setTax(request.getTax());
        po.setShipping(request.getShipping());
        po.setNotes(request.getNotes());
        po.setTerms(request.getTerms());
        
        List<PurchaseOrderItem> items = new ArrayList<>();
        for (CreatePurchaseOrderRequest.PurchaseOrderItem itemDto : request.getItems()) {
            InventoryItem item = itemRepository.findById(itemDto.getItemId())
                .orElseThrow(() -> new BusinessException("Item not found: " + itemDto.getItemId(), "ITEM_NOT_FOUND"));
            
            PurchaseOrderItem poItem = new PurchaseOrderItem();
            poItem.setPurchaseOrder(po);
            poItem.setItem(item);
            poItem.setQuantity(itemDto.getQuantity());
            poItem.setReceivedQuantity(0);
            poItem.setUnitPrice(itemDto.getUnitPrice());
            poItem.setNotes(itemDto.getNotes());
            poItem.calculateTotal();
            items.add(poItem);
        }
        
        po.setItems(items);
        po.calculateTotals();
        
        po = purchaseOrderRepository.save(po);
        return mapToPurchaseOrderDTO(po);
    }
    
    @Transactional
    public PurchaseOrderDTO updatePurchaseOrderStatus(UUID poId, UpdatePurchaseOrderStatusRequest request) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
            .orElseThrow(() -> new BusinessException("Purchase order not found", "PO_NOT_FOUND"));
        
        PurchaseOrder.PurchaseOrderStatus newStatus = PurchaseOrder.PurchaseOrderStatus.valueOf(request.getStatus());
        po.setStatus(newStatus);
        
        if (newStatus == PurchaseOrder.PurchaseOrderStatus.APPROVED) {
            po.setApprovedBy(getCurrentUserId());
            po.setApprovedAt(LocalDateTime.now());
        }
        
        po.setNotes(po.getNotes() + "\nStatus updated: " + request.getNotes());
        po = purchaseOrderRepository.save(po);
        
        return mapToPurchaseOrderDTO(po);
    }
    
    @Transactional
    public PurchaseOrderDTO receivePurchaseOrder(UUID poId, ReceivePurchaseOrderRequest request) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
            .orElseThrow(() -> new BusinessException("Purchase order not found", "PO_NOT_FOUND"));
        
        Map<UUID, Integer> receivedMap = request.getItems().stream()
            .collect(Collectors.toMap(
                ReceivePurchaseOrderRequest.ReceivedItem::getItemId,
                ReceivePurchaseOrderRequest.ReceivedItem::getReceivedQuantity
            ));
        
        boolean allReceived = true;
        
        for (PurchaseOrderItem item : po.getItems()) {
            Integer received = receivedMap.get(item.getItem().getId());
            if (received != null) {
                item.setReceivedQuantity(item.getReceivedQuantity() + received);
                
                // Update shop stock
                adjustStock(po.getBusinessId(), AdjustStockRequest.builder()
                    .shopId(po.getShopId())
                    .adjustments(Collections.singletonList(
                        AdjustStockRequest.StockAdjustment.builder()
                            .itemId(item.getItem().getId())
                            .quantity(BigDecimal.valueOf(received))
                            .reason("PURCHASE_ORDER")
                            .reference(po.getPoNumber())
                            .notes("Received from PO")
                            .build()
                    ))
                    .build());
            }
            
            if (!item.isFullyReceived()) {
                allReceived = false;
            }
        }
        
        po.setStatus(allReceived ? 
            PurchaseOrder.PurchaseOrderStatus.RECEIVED : 
            PurchaseOrder.PurchaseOrderStatus.PARTIALLY_RECEIVED);
        po.setDeliveredAt(LocalDateTime.now());
        po.setDeliveredBy(getCurrentUserId());
        
        po = purchaseOrderRepository.save(po);
        return mapToPurchaseOrderDTO(po);
    }
    
    // ==================== Helper Methods ====================
    
    private Specification<InventoryItem> createItemSpecification(
        UUID businessId,
        InventoryFilter filter
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("businessId"), businessId));
            
            if (filter != null) {
                if (filter.getCategory() != null) {
                    predicates.add(cb.equal(
                            root.get("category"),
                            InventoryItem.ItemCategory.valueOf(filter.getCategory())
                    ));
                }
                
                if (filter.getSupplierId() != null) {
                    predicates.add(cb.equal(
                            root.get("supplierId"),
                            filter.getSupplierId()
                    ));
                }
                
                if (Boolean.TRUE.equals(filter.getIsActive())) {
                    predicates.add(cb.isTrue(root.get("isActive")));
                }
                
                if (Boolean.TRUE.equals(filter.getLowStockOnly())) {
                    Path<BigDecimal> currentStock = root.get("currentStock");
                    Expression<BigDecimal> reorderLevel =
                            root.get("reorderLevel").as(BigDecimal.class);
                    predicates.add(cb.lessThanOrEqualTo(currentStock, reorderLevel));
                }
                
                if (Boolean.TRUE.equals(filter.getCriticalStockOnly())) {
                    Path<BigDecimal> currentStock = root.get("currentStock");
                    Expression<BigDecimal> halfReorder =
                            cb.quot(
                                    root.get("reorderLevel").as(BigDecimal.class),
                                    BigDecimal.valueOf(2)
                            ).as(BigDecimal.class);
                    predicates.add(cb.lessThanOrEqualTo(currentStock, halfReorder));
                }
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
    
    private StockTransaction.TransactionType determineTransactionType(AdjustStockRequest.StockAdjustment adjustment) {
        if (adjustment.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            switch (adjustment.getReason()) {
                case "RECEIVED": return StockTransaction.TransactionType.RECEIVED;
                case "RETURNED": return StockTransaction.TransactionType.RETURNED;
                default: return StockTransaction.TransactionType.ADJUSTMENT;
            }
        } else {
            switch (adjustment.getReason()) {
                case "USED": return StockTransaction.TransactionType.USED;
                case "WASTED": return StockTransaction.TransactionType.WASTED;
                default: return StockTransaction.TransactionType.ADJUSTMENT;
            }
        }
    }
    
    private StockAlert createStockAlert(UUID businessId, UUID shopId, InventoryItem item, ShopStock shopStock) {
        // Check if active alert already exists
        Optional<StockAlert> existingAlert = alertRepository.findActiveAlertByItem(item.getId());
        if (existingAlert.isPresent()) {
            return existingAlert.get();
        }
        
        StockAlert alert = new StockAlert();
        alert.setBusinessId(businessId);
        alert.setShopId(shopId);
        alert.setItem(item);
        alert.setCurrentStock(shopStock.getQuantity());
        alert.setReorderLevel(item.getReorderLevel());
        alert.setStatus(StockAlert.AlertStatus.ACTIVE);
        alert.setSuggestedOrder(item.getReorderQuantity());
        alert.setSeverity(shopStock.getStatus() == ShopStock.StockStatus.CRITICAL ? 
            StockAlert.AlertSeverity.CRITICAL : StockAlert.AlertSeverity.WARNING);
        alert.setMessage(String.format("Stock level for %s is %s. Current: %.2f, Reorder level: %d",
            item.getName(), shopStock.getStatus(), shopStock.getQuantity(), item.getReorderLevel()));
        
        return alertRepository.save(alert);
    }
    
    private void createTransferTransaction(UUID businessId, UUID shopId, UUID itemId, BigDecimal quantity,
                                            String type, String reason, String notes) {
        InventoryItem item = itemRepository.findById(itemId)
            .orElseThrow(() -> new BusinessException("Item not found", "ITEM_NOT_FOUND"));
        
        ShopStock shopStock = shopStockRepository.findByShopIdAndItemId(shopId, itemId)
            .orElseThrow(() -> new BusinessException("Shop stock not found", "SHOP_STOCK_NOT_FOUND"));
        
        StockTransaction transaction = new StockTransaction();
        transaction.setBusinessId(businessId);
        transaction.setShopId(shopId);
        transaction.setItem(item);
        transaction.setQuantity(quantity.abs());
        transaction.setType(StockTransaction.TransactionType.valueOf(type));
        transaction.setReason(reason);
        transaction.setNotes(notes);
        transaction.setPerformedBy(getCurrentUserId());
        transaction.setBeforeQuantity(shopStock.getQuantity().subtract(quantity));
        transaction.setAfterQuantity(shopStock.getQuantity());
        transaction.setUnitCost(item.getUnitPrice());
        transaction.setTotalCost(item.getUnitPrice().multiply(quantity.abs()));
        transaction.setTransactionDate(LocalDateTime.now());
        
        transactionRepository.save(transaction);
    }
    
    private String generatePONumber(UUID businessId) {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = String.format("%04d", new Random().nextInt(10000));
        return String.format("PO-%s-%s-%s", businessId.toString().substring(0, 4), datePart, randomPart);
    }
    
    private InventoryItemDTO mapToItemDTO(InventoryItem item) {
        String supplierName =
        item.getSupplierId() != null
                ? supplierRepository.findById(item.getSupplierId())
                    .map(Supplier::getName)
                    .orElse(null)
                : null;
        
        String stockStatus = "NORMAL";
        if (item.isCriticalStock()) {
            stockStatus = "CRITICAL";
        } else if (item.isLowStock()) {
            stockStatus = "LOW";
        }
        
        return InventoryItemDTO.builder()
            .id(item.getId())
            .sku(item.getSku())
            .name(item.getName())
            .description(item.getDescription())
            .category(item.getCategory().toString())
            .unit(item.getUnit().toString())
            .reorderLevel(item.getReorderLevel())
            .reorderQuantity(item.getReorderQuantity())
            .currentStock(item.getCurrentStock())
            .unitPrice(item.getUnitPrice())
            .supplierId(item.getSupplierId())
            .supplierName(supplierName)
            .isActive(item.getIsActive())
            .minStockLevel(item.getMinStockLevel())
            .maxStockLevel(item.getMaxStockLevel())
            .location(item.getLocation())
            .stockStatus(stockStatus)
            .build();
    }
    
    // ==================== Helper Methods for DTO Mapping ====================
    
    private InventoryItemDetailDTO.ShopStockSummaryDTO mapToShopStockSummary(ShopStock ss) {
        String shopName = shopRepository.findById(ss.getShopId())
            .map(s -> s.getName())
            .orElse("");
        
        return InventoryItemDetailDTO.ShopStockSummaryDTO.builder()
            .shopId(ss.getShopId())
            .shopName(shopName)
            .quantity(ss.getQuantity())
            .status(ss.getStatus().toString())
            .lastRestocked(ss.getLastRestocked())
            .locationDetails(ss.getLocationDetails())
            .build();
    }
    
    private InventoryItemDetailDTO mapToItemDetailDTO(InventoryItem item) {
        InventoryItemDTO base = mapToItemDTO(item);
        
        List<InventoryItemDetailDTO.ShopStockSummaryDTO> shopStocks = item.getShopStocks().stream()
            .map(this::mapToShopStockSummary)
            .collect(Collectors.toList());
        
        List<InventoryItemDetailDTO.TransactionSummaryDTO> recentTransactions = item.getTransactions().stream()
            .limit(10)
            .map(t -> {
                String performerName = getUserName(t.getPerformedBy());
                return InventoryItemDetailDTO.TransactionSummaryDTO.builder()
                    .id(t.getId())
                    .type(t.getType().toString())
                    .quantity(t.getQuantity())
                    .transactionDate(t.getTransactionDate())
                    .reason(t.getReason())
                    .performedBy(performerName)
                    .build();
            })
            .collect(Collectors.toList());
        
        InventoryItemDetailDTO.SupplierSummaryDTO supplierSummary = null;
        if (item.getSupplierId() != null) {
            Optional<Supplier> supplierOpt = supplierRepository.findById(item.getSupplierId());
            if (supplierOpt.isPresent()) {
                Supplier s = supplierOpt.get();
                supplierSummary = InventoryItemDetailDTO.SupplierSummaryDTO.builder()
                    .id(s.getId())
                    .name(s.getName())
                    .contactPerson(s.getContactPerson())
                    .phone(s.getPhone())
                    .email(s.getEmail())
                    .build();
            }
        }
        
        InventoryItemDetailDTO.MetadataDTO metadata = InventoryItemDetailDTO.MetadataDTO.builder()
            .createdBy(item.getCreatedBy())
            .createdAt(item.getCreatedAt())
            .updatedBy(item.getUpdatedBy())
            .updatedAt(item.getUpdatedAt())
            .build();
        
        return InventoryItemDetailDTO.builder()
            .id(base.getId())
            .sku(base.getSku())
            .name(base.getName())
            .description(base.getDescription())
            .category(base.getCategory())
            .unit(base.getUnit())
            .reorderLevel(base.getReorderLevel())
            .reorderQuantity(base.getReorderQuantity())
            .currentStock(base.getCurrentStock())
            .unitPrice(base.getUnitPrice())
            .supplierId(base.getSupplierId())
            .supplierName(base.getSupplierName())
            .isActive(base.getIsActive())
            .minStockLevel(base.getMinStockLevel())
            .maxStockLevel(base.getMaxStockLevel())
            .location(base.getLocation())
            .stockStatus(base.getStockStatus())
            .shopStocks(shopStocks)
            .recentTransactions(recentTransactions)
            .supplier(supplierSummary)
            .metadata(metadata)
            .build();
    }
    
    private StockTransactionDTO mapToTransactionDTO(StockTransaction transaction) {
        String shopName = shopRepository.findById(transaction.getShopId())
            .map(s -> s.getName())
            .orElse("");
        
        String performerName = getUserName(transaction.getPerformedBy());
        
        return StockTransactionDTO.builder()
            .id(transaction.getId())
            .shopId(transaction.getShopId())
            .shopName(shopName)
            .itemId(transaction.getItem().getId())
            .itemName(transaction.getItem().getName())
            .itemSku(transaction.getItem().getSku())
            .quantity(transaction.getQuantity())
            .type(transaction.getType().toString())
            .reason(transaction.getReason())
            .reference(transaction.getReference())
            .notes(transaction.getNotes())
            .performedBy(performerName)
            .transactionDate(transaction.getTransactionDate())
            .beforeQuantity(transaction.getBeforeQuantity())
            .afterQuantity(transaction.getAfterQuantity())
            .unitCost(transaction.getUnitCost())
            .totalCost(transaction.getTotalCost())
            .build();
    }
    
    private StockAlertDTO mapToAlertDTO(StockAlert alert) {
        String shopName = shopRepository.findById(alert.getShopId())
            .map(s -> s.getName())
            .orElse("");
        
        String acknowledgedByName = alert.getAcknowledgedBy() != null ?
            getUserName(alert.getAcknowledgedBy()) : null;
        
        String resolvedByName = alert.getResolvedBy() != null ?
            getUserName(alert.getResolvedBy()) : null;
        
        return StockAlertDTO.builder()
            .id(alert.getId())
            .shopId(alert.getShopId())
            .shopName(shopName)
            .itemId(alert.getItem().getId())
            .itemName(alert.getItem().getName())
            .itemSku(alert.getItem().getSku())
            .currentStock(alert.getCurrentStock())
            .reorderLevel(alert.getReorderLevel())
            .status(alert.getStatus().toString())
            .suggestedOrder(alert.getSuggestedOrder())
            .acknowledgedAt(alert.getAcknowledgedAt())
            .acknowledgedBy(acknowledgedByName)
            .resolvedAt(alert.getResolvedAt())
            .message(alert.getMessage())
            .severity(alert.getSeverity().toString())
            .build();
    }
    
    private SupplierDTO mapToSupplierDTO(Supplier supplier) {
        AddressDTO addressDTO = null;
        if (supplier.getAddress() != null) {
            addressDTO = AddressDTO.builder()
                .addressLine1(supplier.getAddress().getAddressLine1())
                .addressLine2(supplier.getAddress().getAddressLine2())
                .city(supplier.getAddress().getCity())
                .state(supplier.getAddress().getState())
                .postalCode(supplier.getAddress().getPostalCode())
                .country(supplier.getAddress().getCountry())
                .build();
        }
        
        return SupplierDTO.builder()
            .id(supplier.getId())
            .name(supplier.getName())
            .contactPerson(supplier.getContactPerson())
            .email(supplier.getEmail())
            .phone(supplier.getPhone())
            .address(addressDTO)
            .paymentTerms(supplier.getPaymentTerms())
            .leadTimeDays(supplier.getLeadTimeDays())
            .categories(supplier.getCategories())
            .rating(supplier.getRating())
            .taxId(supplier.getTaxId())
            .website(supplier.getWebsite())
            .notes(supplier.getNotes())
            .isActive(supplier.getIsActive())
            .minimumOrderAmount(supplier.getMinimumOrderAmount())
            .shippingCost(supplier.getShippingCost())
            .createdAt(supplier.getCreatedAt())
            .build();
    }
    
    private PurchaseOrderDTO mapToPurchaseOrderDTO(PurchaseOrder po) {
        // Extract values to make them effectively final
        UUID shopId = po.getShopId();
        UUID approvedById = po.getApprovedBy();
        UUID deliveredById = po.getDeliveredBy();
        List<PurchaseOrderItem> items = po.getItems();
        
        String shopName = shopRepository.findById(shopId)
            .map(s -> s.getName())
            .orElse("");
        
        String approvedByName = approvedById != null ?
            getUserName(approvedById) : null;
        
        String deliveredByName = deliveredById != null ?
            getUserName(deliveredById) : null;
        
        List<PurchaseOrderDTO.PurchaseOrderItemDTO> itemDTOs = items.stream()
            .map(item -> PurchaseOrderDTO.PurchaseOrderItemDTO.builder()
                .id(item.getId())
                .itemId(item.getItem().getId())
                .itemName(item.getItem().getName())
                .itemSku(item.getItem().getSku())
                .quantity(item.getQuantity())
                .receivedQuantity(item.getReceivedQuantity())
                .unitPrice(item.getUnitPrice())
                .total(item.getTotal())
                .notes(item.getNotes())
                .build())
            .collect(Collectors.toList());
        
        return PurchaseOrderDTO.builder()
            .id(po.getId())
            .poNumber(po.getPoNumber())
            .supplierId(po.getSupplier().getId())
            .supplierName(po.getSupplier().getName())
            .shopId(shopId)
            .shopName(shopName)
            .status(po.getStatus().toString())
            .orderDate(po.getOrderDate())
            .expectedDelivery(po.getExpectedDelivery())
            .deliveredAt(po.getDeliveredAt())
            .deliveredBy(deliveredByName)
            .items(itemDTOs)
            .subtotal(po.getSubtotal())
            .tax(po.getTax())
            .shipping(po.getShipping())
            .total(po.getTotal())
            .notes(po.getNotes())
            .terms(po.getTerms())
            .approvedBy(approvedByName)
            .approvedAt(po.getApprovedAt())
            .createdAt(po.getCreatedAt())
            .build();
    }
    
    private String getUserName(UUID userId) {
        if (userId == null) return "System";
        // In a real implementation, fetch from user repository
        return "User " + userId.toString().substring(0, 8);
    }
    
    private UUID getCurrentUserId() {
        // In a real implementation, get from SecurityContext
        return UUID.randomUUID();
    }
}