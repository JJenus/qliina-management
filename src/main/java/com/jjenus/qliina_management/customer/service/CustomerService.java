package com.jjenus.qliina_management.customer.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.customer.dto.*;
import com.jjenus.qliina_management.customer.model.*;
import com.jjenus.qliina_management.customer.repository.*;
import com.jjenus.qliina_management.order.dto.OrderSummaryDTO;
import com.jjenus.qliina_management.order.model.Order;
import com.jjenus.qliina_management.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import com.jjenus.qliina_management.customer.model.LoyaltyTier;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {
    
    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository addressRepository;
    private final CustomerPreferencesRepository preferencesRepository;
    private final CustomerNoteRepository noteRepository;
    private final LoyaltyTransactionRepository loyaltyTransactionRepository;
    private final LoyaltyTierRepository loyaltyTierRepository;
    private final LoyaltyRewardRepository loyaltyRewardRepository;
    private final OrderRepository orderRepository;
    
    @Transactional(readOnly = true)
    public PageResponse<CustomerSummaryDTO> listCustomers(UUID businessId, CustomerFilter filter, Pageable pageable) {
        Page<Customer> page;
        
        if (filter != null && filter.getSearch() != null && !filter.getSearch().trim().isEmpty()) {
            page = customerRepository.searchCustomers(businessId, filter.getSearch(), pageable);
        } else if (filter != null && filter.getSegment() != null && !filter.getSegment().isEmpty()) {
            page = customerRepository.findByBusinessIdAndRfmSegment(businessId, filter.getSegment(), pageable);
        } else {
            var specification = CustomerSpecifications.withFilter(businessId, filter);
            page = customerRepository.findAll(specification, pageable);
        }
        
        return PageResponse.from(page.map(this::mapToSummaryDTO));
    }
    
    @Transactional(readOnly = true)
    public PageResponse<CustomerSummaryDTO> searchCustomers(UUID businessId, CustomerSearchRequest request) {
        Sort sort = parseSort(request.getSort());
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);
        
        Page<Customer> page = customerRepository.searchCustomers(
            businessId, 
            request.getQuery(), 
            pageable
        );
        
        return PageResponse.from(page.map(this::mapToSummaryDTO));
    }
    
    @Transactional(readOnly = true)
    public CustomerDetailDTO getCustomer(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new BusinessException("Customer not found", "CUSTOMER_NOT_FOUND"));
        return mapToDetailDTO(customer);
    }
    
    @Transactional
    public CustomerDetailDTO createCustomer(UUID businessId, CreateCustomerRequest request) {
        Optional<Customer> existing = customerRepository.findByBusinessIdAndPhone(businessId, request.getPhone());
        if (existing.isPresent()) {
            throw new BusinessException("Customer with this phone number already exists", "DUPLICATE_PHONE", "phone");
        }
        
        Customer customer = new Customer();
        customer.setBusinessId(businessId);
        customer.setFirstName(request.getFirstName());
        customer.setLastName(request.getLastName());
        customer.setPhone(request.getPhone());
        customer.setEmail(request.getEmail());
        customer.setTags(request.getTags() != null ? request.getTags() : new ArrayList<>());
        customer.setTotalOrders(0);
        customer.setTotalSpent(BigDecimal.ZERO);
        customer.setAverageOrderValue(BigDecimal.ZERO);
        customer.setLoyaltyPoints(0);
        
        // Set initial loyalty tier
        List<LoyaltyTier> tiers = loyaltyTierRepository.findByBusinessIdOrderByLevelAsc(businessId);
        if (!tiers.isEmpty()) {
            customer.setLoyaltyTier(tiers.get(0).getName());
        } else {
            customer.setLoyaltyTier("BRONZE");
        }
        
        customer = customerRepository.save(customer);
        
        // Create addresses
        if (request.getAddresses() != null) {
            for (CreateCustomerRequest.AddressDTO addrDto : request.getAddresses()) {
                CustomerAddress address = new CustomerAddress();
                address.setCustomer(customer);
                address.setType(addrDto.getType());
                address.setAddressLine1(addrDto.getAddressLine1());
                address.setAddressLine2(addrDto.getAddressLine2());
                address.setCity(addrDto.getCity());
                address.setState(addrDto.getState());
                address.setPostalCode(addrDto.getPostalCode());
                address.setCountry(addrDto.getCountry() != null ? addrDto.getCountry() : "USA");
                address.setDefault(addrDto.getIsDefault() != null ? addrDto.getIsDefault() : false);
                address.setInstructions(addrDto.getInstructions());
                
                if (address.isDefault()) {
                    addressRepository.resetDefaultAddress(customer.getId());
                }
                
                addressRepository.save(address);
                customer.getAddresses().add(address);
            }
        }
        
        // Create preferences
        CustomerPreferences preferences = new CustomerPreferences();
        preferences.setCustomer(customer);
        if (request.getPreferences() != null) {
            preferences.setNotifyViaSms(request.getPreferences().getNotifyViaSms());
            preferences.setNotifyViaEmail(request.getPreferences().getNotifyViaEmail());
            preferences.setPreferredPaymentMethod(request.getPreferences().getPreferredPaymentMethod());
            preferences.setPreferredShopId(request.getPreferences().getPreferredShopId());
        } else {
            preferences.setNotifyViaSms(true);
            preferences.setNotifyViaEmail(true);
        }
        preferencesRepository.save(preferences);
        customer.setPreferences(preferences);
        
        // Create initial note if provided
        if (request.getNotes() != null && !request.getNotes().trim().isEmpty()) {
            CustomerNote note = new CustomerNote();
            note.setCustomer(customer);
            note.setContent(request.getNotes());
            note.setType("GENERAL");
            noteRepository.save(note);
            customer.getNotes().add(note);
        }
        
        return mapToDetailDTO(customer);
    }
    
    @Transactional
    public CustomerDetailDTO updateCustomer(UUID customerId, UpdateCustomerRequest request) {
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new BusinessException("Customer not found", "CUSTOMER_NOT_FOUND"));
        
        boolean needsRecalculation = false;
        
        if (request.getFirstName() != null) {
            customer.setFirstName(request.getFirstName());
        }
        
        if (request.getLastName() != null) {
            customer.setLastName(request.getLastName());
        }
        
        if (request.getEmail() != null) {
            customer.setEmail(request.getEmail());
        }
        
        if (request.getPhone() != null) {
            if (!request.getPhone().equals(customer.getPhone())) {
                Optional<Customer> existing = customerRepository.findByBusinessIdAndPhone(
                    customer.getBusinessId(), request.getPhone());
                if (existing.isPresent() && !existing.get().getId().equals(customerId)) {
                    throw new BusinessException("Phone number already in use", "DUPLICATE_PHONE", "phone");
                }
            }
            customer.setPhone(request.getPhone());
        }
        
        if (request.getTags() != null) {
            customer.setTags(request.getTags());
        }
        
        // Update addresses if provided
        if (request.getAddresses() != null) {
            // Remove existing addresses
            addressRepository.deleteAll(customer.getAddresses());
            customer.getAddresses().clear();
            
            for (UpdateCustomerRequest.AddressDTO addrDto : request.getAddresses()) {
                CustomerAddress address = new CustomerAddress();
                address.setCustomer(customer);
                address.setType(addrDto.getType());
                address.setAddressLine1(addrDto.getAddressLine1());
                address.setAddressLine2(addrDto.getAddressLine2());
                address.setCity(addrDto.getCity());
                address.setState(addrDto.getState());
                address.setPostalCode(addrDto.getPostalCode());
                address.setCountry(addrDto.getCountry() != null ? addrDto.getCountry() : "USA");
                address.setDefault(addrDto.getIsDefault() != null ? addrDto.getIsDefault() : false);
                address.setInstructions(addrDto.getInstructions());
                
                if (address.isDefault()) {
                    addressRepository.resetDefaultAddress(customer.getId());
                }
                
                addressRepository.save(address);
                customer.getAddresses().add(address);
            }
        }
        
        // Update preferences if provided
        if (request.getPreferences() != null) {
            CustomerPreferences preferences = customer.getPreferences();
            if (preferences == null) {
                preferences = new CustomerPreferences();
                preferences.setCustomer(customer);
            }
            
            if (request.getPreferences().getNotifyViaSms() != null) {
                preferences.setNotifyViaSms(request.getPreferences().getNotifyViaSms());
            }
            if (request.getPreferences().getNotifyViaEmail() != null) {
                preferences.setNotifyViaEmail(request.getPreferences().getNotifyViaEmail());
            }
            if (request.getPreferences().getPreferredPaymentMethod() != null) {
                preferences.setPreferredPaymentMethod(request.getPreferences().getPreferredPaymentMethod());
            }
            if (request.getPreferences().getPreferredShopId() != null) {
                preferences.setPreferredShopId(request.getPreferences().getPreferredShopId());
            }
            
            preferencesRepository.save(preferences);
        }
        
        customer = customerRepository.save(customer);
        
        // Add note if provided
        if (request.getNotes() != null && !request.getNotes().trim().isEmpty()) {
            CustomerNote note = new CustomerNote();
            note.setCustomer(customer);
            note.setContent(request.getNotes());
            note.setType("GENERAL");
            noteRepository.save(note);
            customer.getNotes().add(note);
        }
        
        if (needsRecalculation) {
            recalculateCustomerMetrics(customer);
        }
        
        return mapToDetailDTO(customer);
    }
    
    @Transactional
    public void deleteCustomer(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new BusinessException("Customer not found", "CUSTOMER_NOT_FOUND"));
        
        // Soft delete - just mark as inactive
        customer.setEnabled(false);
        customerRepository.save(customer);
    }
    
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryDTO> getCustomerOrders(UUID customerId, Pageable pageable) {
        Page<Order> ordersPage = orderRepository.findByCustomerId(customerId, pageable);
        
        List<OrderSummaryDTO> orderSummaries = ordersPage.getContent().stream()
            .map(this::mapOrderToSummary)
            .collect(Collectors.toList());
        
        return PageResponse.from(
            new org.springframework.data.domain.PageImpl<>(
                orderSummaries, 
                pageable, 
                ordersPage.getTotalElements()
            )
        );
    }
    
    @Transactional(readOnly = true)
    public CustomerLoyaltyDTO getCustomerLoyalty(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new BusinessException("Customer not found", "CUSTOMER_NOT_FOUND"));
        
        List<LoyaltyTier> tiers = loyaltyTierRepository.findByBusinessIdOrderByLevelAsc(customer.getBusinessId());
        
        LoyaltyTier currentTier = null;
        LoyaltyTier nextTier = null;
        int pointsToNextTier = 0;
        double progress = 0.0;
        
        for (int i = 0; i < tiers.size(); i++) {
            LoyaltyTier tier = tiers.get(i);
            if (tier.getPointsRequired() <= customer.getLoyaltyPoints()) {
                currentTier = tier;
            } else {
                nextTier = tier;
                pointsToNextTier = tier.getPointsRequired() - customer.getLoyaltyPoints();
                if (i > 0) {
                    int tierRange = tier.getPointsRequired() - tiers.get(i-1).getPointsRequired();
                    int pointsInTier = customer.getLoyaltyPoints() - tiers.get(i-1).getPointsRequired();
                    progress = (double) pointsInTier / tierRange * 100;
                }
                break;
            }
        }
        
        if (currentTier == null && !tiers.isEmpty()) {
            currentTier = tiers.get(0);
            pointsToNextTier = currentTier.getPointsRequired() - customer.getLoyaltyPoints();
            progress = (double) customer.getLoyaltyPoints() / currentTier.getPointsRequired() * 100;
        }
        
        if (currentTier == null) {
            currentTier = LoyaltyTier.builder()
                .name("BRONZE")
                .level(1)
                .benefits(List.of("Basic benefits"))
                .pointsRequired(0)
                .build();
        }
        
        Page<LoyaltyTransaction> transactions = loyaltyTransactionRepository.findByCustomerId(
            customerId, PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt")));
        
        List<LoyaltyReward> availableRewards = loyaltyRewardRepository.findAvailableRewards(
            customer.getBusinessId(), LocalDateTime.now());
        
        int lifetimePoints = loyaltyTransactionRepository.sumPointsByCustomerId(customerId)
            .orElse(0);
        
        CustomerLoyaltyDTO.TierInfoDTO tierInfo = CustomerLoyaltyDTO.TierInfoDTO.builder()
            .name(currentTier.getName())
            .level(currentTier.getLevel())
            .benefits(currentTier.getBenefits())
            .pointsRequired(currentTier.getPointsRequired())
            .progress(progress)
            .build();
        
        List<CustomerLoyaltyDTO.PointsHistoryDTO> history = transactions.getContent().stream()
            .map(tx -> CustomerLoyaltyDTO.PointsHistoryDTO.builder()
                .id(tx.getId())
                .date(tx.getCreatedAt())
                .points(tx.getPoints())
                .balance(tx.getBalance())
                .source(tx.getSource())
                .sourceId(tx.getSourceId())
                .description(tx.getDescription())
                .build())
            .collect(Collectors.toList());
        
        List<CustomerLoyaltyDTO.AvailableRewardDTO> rewards = availableRewards.stream()
            .filter(r -> r.getPointsCost() <= customer.getLoyaltyPoints())
            .map(r -> CustomerLoyaltyDTO.AvailableRewardDTO.builder()
                .id(r.getId())
                .name(r.getName())
                .pointsCost(r.getPointsCost())
                .description(r.getDescription())
                .expiresAt(r.getExpiresAt())
                .build())
            .collect(Collectors.toList());
        
        return CustomerLoyaltyDTO.builder()
            .customerId(customer.getId())
            .customerName(customer.getFirstName() + " " + customer.getLastName())
            .currentPoints(customer.getLoyaltyPoints())
            .lifetimePoints(lifetimePoints)
            .tier(tierInfo)
            .pointsHistory(history)
            .availableRewards(rewards)
            .build();
    }
    
    @Transactional
    public CustomerLoyaltyDTO adjustPoints(UUID customerId, AdjustPointsRequest request) {
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new BusinessException("Customer not found", "CUSTOMER_NOT_FOUND"));
        
        int newPoints = customer.getLoyaltyPoints() + request.getPoints();
        if (newPoints < 0) {
            throw new BusinessException("Insufficient points", "INSUFFICIENT_POINTS");
        }
        
        LoyaltyTransaction transaction = new LoyaltyTransaction();
        transaction.setCustomer(customer);
        transaction.setPoints(request.getPoints());
        transaction.setBalance(newPoints);
        transaction.setSource(request.getSource());
        transaction.setDescription(request.getReason());
        
        loyaltyTransactionRepository.save(transaction);
        
        customer.setLoyaltyPoints(newPoints);
        updateCustomerTier(customer);
        customer = customerRepository.save(customer);
        
        return getCustomerLoyalty(customerId);
    }
    
    @Transactional(readOnly = true)
    public List<TopCustomerDTO> getTopCustomers(UUID businessId, TopCustomersRequest request) {
        LocalDateTime startDate;
        LocalDateTime endDate = LocalDateTime.now();
        
        if ("CUSTOM".equals(request.getPeriod()) && request.getStartDate() != null) {
            startDate = request.getStartDate();
            endDate = request.getEndDate() != null ? request.getEndDate() : endDate;
        } else {
            startDate = switch (request.getPeriod()) {
                case "WEEK" -> endDate.minus(7, ChronoUnit.DAYS);
                case "MONTH" -> endDate.minus(30, ChronoUnit.DAYS);
                case "QUARTER" -> endDate.minus(90, ChronoUnit.DAYS);
                case "YEAR" -> endDate.minus(365, ChronoUnit.DAYS);
                default -> endDate.minus(30, ChronoUnit.DAYS);
            };
        }
        
        Pageable pageable = PageRequest.of(0, request.getLimit());
        Page<Object[]> results;
        
        switch (request.getMetric()) {
            case "SPEND":
                results = orderRepository.findTopCustomersBySpend(businessId, startDate, endDate, pageable);
                break;
            case "FREQUENCY":
                results = orderRepository.findTopCustomersByFrequency(businessId, startDate, endDate, pageable);
                break;
            case "AOV":
                results = orderRepository.findTopCustomersByAOV(businessId, startDate, endDate, pageable);
                break;
            default:
                results = orderRepository.findTopCustomersBySpend(businessId, startDate, endDate, pageable);
        }
        
        List<TopCustomerDTO> topCustomers = new ArrayList<>();
        int rank = 1;
        
        for (Object[] result : results) {
            UUID custId = (UUID) result[0];
            Customer customer = customerRepository.findById(custId).orElse(null);
            if (customer == null) continue;
            
            BigDecimal metricValue = (BigDecimal) result[1];
            Long orderCount = (Long) result[2];
            BigDecimal totalSpent = (BigDecimal) result[3];
            
            TopCustomerDTO dto = TopCustomerDTO.builder()
                .rank(rank++)
                .customerId(customer.getId())
                .customerName(customer.getFirstName() + " " + customer.getLastName())
                .phone(customer.getPhone())
                .metric(metricValue.doubleValue())
                .orders(orderCount.intValue())
                .totalSpent(totalSpent)
                .averageOrderValue(orderCount > 0 ? totalSpent.divide(BigDecimal.valueOf(orderCount), 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .lastOrderDate(customer.getLastOrderDate())
                .build();
            
            topCustomers.add(dto);
        }
        
        return topCustomers;
    }
    
    @Transactional(readOnly = true)
    public RFMSegmentsDTO getRFMSegments(UUID businessId) {
        List<Customer> customers = customerRepository.findByBusinessId(businessId);
        
        if (customers.isEmpty()) {
            return RFMSegmentsDTO.builder()
                .segments(new ArrayList<>())
                .distribution(new HashMap<>())
                .build();
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        List<Integer> recencyValues = customers.stream()
            .filter(c -> c.getLastOrderDate() != null)
            .map(c -> (int) ChronoUnit.DAYS.between(c.getLastOrderDate(), now))
            .sorted()
            .collect(Collectors.toList());
        
        List<Integer> frequencyValues = customers.stream()
            .map(Customer::getTotalOrders)
            .sorted()
            .collect(Collectors.toList());
        
        List<Double> monetaryValues = customers.stream()
            .map(c -> c.getTotalSpent().doubleValue())
            .sorted()
            .collect(Collectors.toList());
        
        int r1 = !recencyValues.isEmpty() ? recencyValues.get(recencyValues.size() / 4) : 0;
        int r2 = !recencyValues.isEmpty() ? recencyValues.get(recencyValues.size() / 2) : 0;
        int r3 = !recencyValues.isEmpty() ? recencyValues.get(3 * recencyValues.size() / 4) : 0;
        
        int f1 = !frequencyValues.isEmpty() ? frequencyValues.get(frequencyValues.size() / 4) : 0;
        int f2 = !frequencyValues.isEmpty() ? frequencyValues.get(frequencyValues.size() / 2) : 0;
        int f3 = !frequencyValues.isEmpty() ? frequencyValues.get(3 * frequencyValues.size() / 4) : 0;
        
        double m1 = !monetaryValues.isEmpty() ? monetaryValues.get(monetaryValues.size() / 4) : 0;
        double m2 = !monetaryValues.isEmpty() ? monetaryValues.get(monetaryValues.size() / 2) : 0;
        double m3 = !monetaryValues.isEmpty() ? monetaryValues.get(3 * monetaryValues.size() / 4) : 0;
        
        Map<String, Long> segmentCounts = new HashMap<>();
        Map<String, Double> segmentValues = new HashMap<>();
        
        for (Customer customer : customers) {
            int rScore;
            if (customer.getLastOrderDate() == null) {
                rScore = 1;
            } else {
                int days = (int) ChronoUnit.DAYS.between(customer.getLastOrderDate(), now);
                if (days <= r1) rScore = 4;
                else if (days <= r2) rScore = 3;
                else if (days <= r3) rScore = 2;
                else rScore = 1;
            }
            
            int fScore;
            if (customer.getTotalOrders() >= f3) fScore = 4;
            else if (customer.getTotalOrders() >= f2) fScore = 3;
            else if (customer.getTotalOrders() >= f1) fScore = 2;
            else fScore = 1;
            
            int mScore;
            double spent = customer.getTotalSpent().doubleValue();
            if (spent >= m3) mScore = 4;
            else if (spent >= m2) mScore = 3;
            else if (spent >= m1) mScore = 2;
            else mScore = 1;
            
            int totalScore = rScore + fScore + mScore;
            String segment;
            
            if (totalScore >= 10 && rScore >= 3 && fScore >= 3) {
                segment = "CHAMPION";
            } else if (totalScore >= 9) {
                segment = "LOYAL";
            } else if (totalScore >= 7 && rScore >= 3) {
                segment = "POTENTIAL";
            } else if (totalScore >= 6 && rScore <= 2) {
                segment = "AT_RISK";
            } else {
                segment = "LOST";
            }
            
            customer.setRfmSegment(segment);
            
            segmentCounts.merge(segment, 1L, Long::sum);
            segmentValues.merge(segment, customer.getTotalSpent().doubleValue(), Double::sum);
        }
        
        customerRepository.saveAll(customers);
        
        List<RFMSegmentsDTO.SegmentDTO> segments = new ArrayList<>();
        long totalCustomers = customers.size();
        double totalValue = customers.stream().mapToDouble(c -> c.getTotalSpent().doubleValue()).sum();
        
        for (String segmentName : List.of("CHAMPION", "LOYAL", "POTENTIAL", "AT_RISK", "LOST")) {
            Long count = segmentCounts.getOrDefault(segmentName, 0L);
            Double value = segmentValues.getOrDefault(segmentName, 0.0);
            
            segments.add(RFMSegmentsDTO.SegmentDTO.builder()
                .name(segmentName)
                .count(count)
                .percentage(totalCustomers > 0 ? count * 100.0 / totalCustomers : 0)
                .totalValue(value)
                .averageValue(count > 0 ? value / count : 0)
                .build());
        }
        
        Map<String, List<RFMSegmentsDTO.DistributionDTO>> distribution = new HashMap<>();
        
        distribution.put("recency", createDistribution(recencyValues, "days"));
        distribution.put("frequency", createDistribution(frequencyValues, "orders"));
        distribution.put("monetary", createDistribution(monetaryValues, "amount"));
        
        return RFMSegmentsDTO.builder()
            .segments(segments)
            .distribution(distribution)
            .build();
    }
    
    @Transactional
    public ImportResultDTO importCustomers(UUID businessId, CustomerImportRequest request) {
        ImportResultDTO result = ImportResultDTO.builder()
            .totalRows(0)
            .imported(0)
            .updated(0)
            .failed(0)
            .errors(new ArrayList<>())
            .build();
        
        // Implementation would parse CSV/Excel and process rows
        // This is a placeholder for the actual import logic
        
        return result;
    }
    
    @Transactional
    public void updateCustomerFromOrder(UUID customerId, BigDecimal orderAmount) {
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new BusinessException("Customer not found", "CUSTOMER_NOT_FOUND"));
        
        customer.addOrder(orderAmount);
        
        // Add loyalty points (e.g., 10 points per dollar)
        int pointsEarned = orderAmount.multiply(BigDecimal.valueOf(10)).intValue();
        if (pointsEarned > 0) {
            customer.addLoyaltyPoints(pointsEarned);
            
            LoyaltyTransaction transaction = new LoyaltyTransaction();
            transaction.setCustomer(customer);
            transaction.setPoints(pointsEarned);
            transaction.setBalance(customer.getLoyaltyPoints());
            transaction.setSource("ORDER");
            transaction.setDescription("Points earned from order");
            loyaltyTransactionRepository.save(transaction);
        }
        
        updateCustomerTier(customer);
        customerRepository.save(customer);
    }
    
    private void updateCustomerTier(Customer customer) {
        List<LoyaltyTier> eligibleTiers = loyaltyTierRepository.findEligibleTiers(
            customer.getBusinessId(), customer.getLoyaltyPoints());
        
        if (!eligibleTiers.isEmpty()) {
            LoyaltyTier highestTier = eligibleTiers.get(eligibleTiers.size() - 1);
            customer.setLoyaltyTier(highestTier.getName());
        }
    }
    
    private void recalculateCustomerMetrics(Customer customer) {
        List<Order> orders = orderRepository.findByCustomerId(customer.getId());
        
        BigDecimal totalSpent = BigDecimal.ZERO;
        int totalOrders = orders.size();
        
        for (Order order : orders) {
            totalSpent = totalSpent.add(order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO);
        }
        
        customer.setTotalOrders(totalOrders);
        customer.setTotalSpent(totalSpent);
        
        if (totalOrders > 0) {
            customer.setAverageOrderValue(totalSpent.divide(BigDecimal.valueOf(totalOrders), 2, java.math.RoundingMode.HALF_UP));
        }
        
        if (!orders.isEmpty()) {
            customer.setLastOrderDate(orders.stream()
                .map(Order::getCreatedAt)
                .max(LocalDateTime::compareTo)
                .orElse(null));
        }
    }
    
    private CustomerSummaryDTO mapToSummaryDTO(Customer customer) {
        return CustomerSummaryDTO.builder()
            .id(customer.getId())
            .firstName(customer.getFirstName())
            .lastName(customer.getLastName())
            .phone(customer.getPhone())
            .email(customer.getEmail())
            .totalOrders(customer.getTotalOrders())
            .totalSpent(customer.getTotalSpent())
            .averageOrderValue(customer.getAverageOrderValue())
            .lastOrderDate(customer.getLastOrderDate())
            .loyaltyPoints(customer.getLoyaltyPoints())
            .loyaltyTier(customer.getLoyaltyTier())
            .rfmSegment(customer.getRfmSegment())
            .tags(customer.getTags())
            .createdAt(customer.getCreatedAt())
            .build();
    }
    
    private CustomerDetailDTO mapToDetailDTO(Customer customer) {
        List<CustomerDetailDTO.AddressDTO> addressDTOs = customer.getAddresses().stream()
            .map(addr -> CustomerDetailDTO.AddressDTO.builder()
                .id(addr.getId())
                .type(addr.getType())
                .addressLine1(addr.getAddressLine1())
                .addressLine2(addr.getAddressLine2())
                .city(addr.getCity())
                .state(addr.getState())
                .postalCode(addr.getPostalCode())
                .country(addr.getCountry())
                .isDefault(addr.isDefault())
                .instructions(addr.getInstructions())
                .build())
            .collect(Collectors.toList());
        
        CustomerDetailDTO.PreferencesDTO preferencesDTO = null;
        if (customer.getPreferences() != null) {
            preferencesDTO = CustomerDetailDTO.PreferencesDTO.builder()
                .fabricCare(customer.getPreferences().getFabricCare())
                .deliveryInstructions(customer.getPreferences().getDeliveryInstructions())
                .notifyViaSms(customer.getPreferences().getNotifyViaSms())
                .notifyViaEmail(customer.getPreferences().getNotifyViaEmail())
                .preferredPaymentMethod(customer.getPreferences().getPreferredPaymentMethod())
                .preferredShopId(customer.getPreferences().getPreferredShopId())
                .build();
        }
        
        List<CustomerDetailDTO.NoteDTO> noteDTOs = customer.getNotes().stream()
            .map(note -> CustomerDetailDTO.NoteDTO.builder()
                .id(note.getId())
                .content(note.getContent())
                .createdBy(note.getCreatedBy() != null ? note.getCreatedBy().toString() : null)
                .createdAt(note.getCreatedAt())
                .type(note.getType())
                .build())
            .collect(Collectors.toList());
        
        CustomerDetailDTO.MetadataDTO metadata = CustomerDetailDTO.MetadataDTO.builder()
            .createdBy(customer.getCreatedBy())
            .createdAt(customer.getCreatedAt())
            .updatedBy(customer.getUpdatedBy())
            .updatedAt(customer.getUpdatedAt())
            .build();
        
        CustomerDetailDTO.LoyaltyInfoDTO loyaltyInfo = CustomerDetailDTO.LoyaltyInfoDTO.builder()
            .points(customer.getLoyaltyPoints())
            .tier(customer.getLoyaltyTier())
            .build();
        
        return CustomerDetailDTO.builder()
            .id(customer.getId())
            .firstName(customer.getFirstName())
            .lastName(customer.getLastName())
            .phone(customer.getPhone())
            .email(customer.getEmail())
            .totalOrders(customer.getTotalOrders())
            .totalSpent(customer.getTotalSpent())
            .averageOrderValue(customer.getAverageOrderValue())
            .lastOrderDate(customer.getLastOrderDate())
            .loyaltyPoints(customer.getLoyaltyPoints())
            .loyaltyTier(customer.getLoyaltyTier())
            .rfmSegment(customer.getRfmSegment())
            .tags(customer.getTags())
            .createdAt(customer.getCreatedAt())
            .addresses(addressDTOs)
            .preferences(preferencesDTO)
            .notes(noteDTOs)
            .loyalty(loyaltyInfo)
            .metadata(metadata)
            .build();
    }
    
    private OrderSummaryDTO mapOrderToSummary(Order order) {
        return OrderSummaryDTO.builder()
            .id(order.getId())
            .orderNumber(order.getOrderNumber())
            .trackingNumber(order.getTrackingNumber())
            .customer(OrderSummaryDTO.CustomerInfo.builder()
                .id(order.getCustomerId())
                .name("") // Would need to fetch customer name
                .phone("")
                .build())
            .shop(OrderSummaryDTO.ShopInfo.builder()
                .id(order.getShopId())
                .name("")
                .build())
            .status(order.getStatus().toString())
            .itemCount(order.getItemCount())
            .totalAmount(order.getTotalAmount())
            .paidAmount(order.getPaidAmount())
            .balanceDue(order.getBalanceDue())
            .priority(order.getPriority().toString())
            .promisedDate(order.getPromisedDate())
            .receivedAt(order.getReceivedAt())
            .expectedReadyAt(order.getExpectedReadyAt())
            .actualReadyAt(order.getActualReadyAt())
            .completedAt(order.getCompletedAt())
            .createdAt(order.getCreatedAt())
            .tags(new ArrayList<>(order.getTags()))
            .build();
    }
    
    private Sort parseSort(String sort) {
        if (sort == null || sort.isEmpty()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        
        String[] parts = sort.split(",");
        if (parts.length < 2) {
            return Sort.by(Sort.Direction.DESC, parts[0]);
        }
        
        Sort.Direction direction = parts[1].equalsIgnoreCase("asc") ? 
            Sort.Direction.ASC : Sort.Direction.DESC;
        
        return Sort.by(direction, parts[0]);
    }
    
    private List<RFMSegmentsDTO.DistributionDTO> createDistribution(List<? extends Number> values, String unit) {
        if (values.isEmpty()) {
            return new ArrayList<>();
        }
        
        double min = values.get(0).doubleValue();
        double max = values.get(values.size() - 1).doubleValue();
        double range = max - min;
        
        int numBuckets = 5;
        double bucketSize = range / numBuckets;
        
        List<RFMSegmentsDTO.DistributionDTO> distribution = new ArrayList<>();
        long[] counts = new long[numBuckets];
        
        for (Number value : values) {
            double v = value.doubleValue();
            int bucket = (int) ((v - min) / bucketSize);
            if (bucket >= numBuckets) bucket = numBuckets - 1;
            counts[bucket]++;
        }
        
        for (int i = 0; i < numBuckets; i++) {
            double bucketMin = min + i * bucketSize;
            double bucketMax = (i == numBuckets - 1) ? max : min + (i + 1) * bucketSize;
            String bucketLabel = String.format("%.0f-%.0f %s", bucketMin, bucketMax, unit);
            
            distribution.add(RFMSegmentsDTO.DistributionDTO.builder()
                .bucket(bucketLabel)
                .count(counts[i])
                .build());
        }
        
        return distribution;
    }
}
