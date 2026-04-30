package com.jjenus.qliina_management.business.service;

import com.jjenus.qliina_management.business.dto.*;
import com.jjenus.qliina_management.business.model.*;
import com.jjenus.qliina_management.business.repository.*;
import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceCatalogService {

    private final ServiceTypeRepository serviceTypeRepository;
    private final GarmentTypeRepository garmentTypeRepository;
    private final ServiceGarmentPricingRepository pricingRepository;

    private static final String[][] DEFAULT_SERVICES = {
        {"Wash & Fold",  "Standard wash, dry, and fold",       "WASH",        "3.50",  "PER_KG",  "1", "i-heroicons-arrows-up-down"},
        {"Wash & Iron",  "Wash, dry, and iron",                "WASH",        "5.00",  "PER_KG",  "2", "i-heroicons-sparkles"},
        {"Dry Clean",    "Professional dry cleaning",           "DRY_CLEAN",   "8.00",  "PER_ITEM","3", "i-heroicons-beaker"},
        {"Ironing Only", "Ironing service for clean garments", "IRON",        "2.50",  "PER_ITEM","4", "i-heroicons-bolt"},
        {"Stain Removal","Special stain treatment",             "SPECIAL",     "6.00",  "PER_ITEM","5", "i-heroicons-exclamation-triangle"},
        {"Alteration",   "Basic garment alterations",           "ALTERATION", "10.00",  "PER_ITEM","6", "i-heroicons-scissors"}
    };

    private static final String[][] DEFAULT_GARMENTS = {
        {"Shirt",         "Standard shirts and blouses",        "TOPS",     "1", "i-heroicons-tag"},
        {"T-Shirt",       "T-shirts and casual tops",           "TOPS",     "2", "i-heroicons-tag"},
        {"Trousers",      "Trousers, pants, and slacks",        "BOTTOMS",  "3", "i-heroicons-tag"},
        {"Jeans",         "Denim jeans and pants",              "BOTTOMS",  "4", "i-heroicons-tag"},
        {"Jacket",        "Jackets, blazers, and coats",        "OUTERWEAR","5", "i-heroicons-tag"},
        {"Suit (2-pc)",   "Two-piece suit (jacket + trousers)", "FORMAL",   "6", "i-heroicons-tag"},
        {"Dress",         "Dresses and gowns",                  "DRESSES",  "7", "i-heroicons-tag"},
        {"Skirt",         "Skirts",                             "BOTTOMS",  "8", "i-heroicons-tag"},
        {"Duvet (Single)","Single duvet/comforter",             "BEDDING",  "9", "i-heroicons-tag"},
        {"Duvet (King)",  "King size duvet/comforter",          "BEDDING", "10", "i-heroicons-tag"},
        {"Curtains",      "Curtains (per panel)",               "HOME",    "11", "i-heroicons-tag"},
        {"Tablecloth",    "Tablecloths and runners",            "HOME",    "12", "i-heroicons-tag"}
    };

    // ==================== Service Types ====================

    @Transactional(readOnly = true)
    public List<ServiceTypeDTO> getActiveServices(UUID businessId) {
        return ensureDefaultsExist(businessId).stream()
                .map(this::toServiceDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<ServiceTypeDTO> getAllServices(UUID businessId, String search, Pageable pageable) {
        Page<ServiceType> page = (search != null && !search.isBlank())
                ? serviceTypeRepository.searchActive(businessId, search, pageable)
                : serviceTypeRepository.findByBusinessId(businessId, pageable);
        return PageResponse.from(page.map(this::toServiceDTO));
    }

    @Transactional(readOnly = true)
    public ServiceTypeDTO getService(UUID id) {
        return toServiceDTO(findServiceOrThrow(id));
    }

    @Transactional
    public ServiceTypeDTO createService(UUID businessId, CreateServiceTypeRequest req) {
        if (serviceTypeRepository.existsByBusinessIdAndNameIgnoreCase(businessId, req.getName())) {
            throw new BusinessException("Service with this name already exists", "SERVICE_EXISTS", "name");
        }
        ServiceType st = new ServiceType();
        st.setBusinessId(businessId);
        st.setName(req.getName());
        st.setDescription(req.getDescription());
        st.setCategory(req.getCategory());
        st.setDefaultPrice(req.getDefaultPrice());
        st.setUnit(req.getUnit() != null ? req.getUnit() : "PER_ITEM");
        st.setSortOrder(req.getSortOrder());
        st.setIcon(req.getIcon());
        st.setIsActive(true);
        return toServiceDTO(serviceTypeRepository.save(st));
    }

    @Transactional
    public ServiceTypeDTO updateService(UUID id, UpdateServiceTypeRequest req) {
        ServiceType st = findServiceOrThrow(id);
        if (req.getName() != null) st.setName(req.getName());
        if (req.getDescription() != null) st.setDescription(req.getDescription());
        if (req.getCategory() != null) st.setCategory(req.getCategory());
        if (req.getDefaultPrice() != null) st.setDefaultPrice(req.getDefaultPrice());
        if (req.getUnit() != null) st.setUnit(req.getUnit());
        if (req.getIsActive() != null) st.setIsActive(req.getIsActive());
        if (req.getSortOrder() != null) st.setSortOrder(req.getSortOrder());
        if (req.getIcon() != null) st.setIcon(req.getIcon());
        return toServiceDTO(serviceTypeRepository.save(st));
    }

    @Transactional
    public void deleteService(UUID id) {
        serviceTypeRepository.delete(findServiceOrThrow(id));
    }

    // ==================== Garment Types ====================

    @Transactional(readOnly = true)
    public List<GarmentTypeDTO> getActiveGarments(UUID businessId) {
        ensureDefaultsExist(businessId);
        return garmentTypeRepository.findByBusinessIdAndIsActiveTrueOrderBySortOrderAsc(businessId)
                .stream().map(this::toGarmentDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<GarmentTypeDTO> getAllGarments(UUID businessId, Pageable pageable) {
        return PageResponse.from(garmentTypeRepository.findByBusinessId(businessId, pageable)
                .map(this::toGarmentDTO));
    }

    @Transactional
    public GarmentTypeDTO createGarment(UUID businessId, CreateGarmentTypeRequest req) {
        if (garmentTypeRepository.existsByBusinessIdAndNameIgnoreCase(businessId, req.getName())) {
            throw new BusinessException("Garment with this name already exists", "GARMENT_EXISTS", "name");
        }
        GarmentType gt = new GarmentType();
        gt.setBusinessId(businessId);
        gt.setName(req.getName());
        gt.setDescription(req.getDescription());
        gt.setCategory(req.getCategory());
        gt.setSortOrder(req.getSortOrder());
        gt.setIcon(req.getIcon());
        gt.setIsActive(true);
        return toGarmentDTO(garmentTypeRepository.save(gt));
    }

    @Transactional
    public GarmentTypeDTO updateGarment(UUID id, UpdateGarmentTypeRequest req) {
        GarmentType gt = garmentTypeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Garment type not found", "GARMENT_NOT_FOUND"));
        if (req.getName() != null) gt.setName(req.getName());
        if (req.getDescription() != null) gt.setDescription(req.getDescription());
        if (req.getCategory() != null) gt.setCategory(req.getCategory());
        if (req.getIsActive() != null) gt.setIsActive(req.getIsActive());
        if (req.getSortOrder() != null) gt.setSortOrder(req.getSortOrder());
        if (req.getIcon() != null) gt.setIcon(req.getIcon());
        return toGarmentDTO(garmentTypeRepository.save(gt));
    }

    @Transactional
    public void deleteGarment(UUID id) {
        garmentTypeRepository.deleteById(id);
    }

    // ==================== Pricing ====================

    @Transactional(readOnly = true)
    public List<ServiceGarmentPricingDTO> getPricingGrid(UUID businessId) {
        ensureDefaultsExist(businessId);
        return pricingRepository.findActivePricingGrid(businessId).stream()
                .map(this::toPricingDTO).collect(Collectors.toList());
    }

    @Transactional
    public ServiceGarmentPricingDTO setPricing(UUID businessId, SetPricingRequest req) {
        ServiceGarmentPricing existing = pricingRepository
                .findByServiceTypeIdAndGarmentTypeIdAndIsActiveTrue(req.getServiceTypeId(), req.getGarmentTypeId())
                .orElse(null);

        if (existing != null) {
            existing.setPrice(req.getPrice());
            return toPricingDTO(pricingRepository.save(existing));
        }

        ServiceGarmentPricing pricing = new ServiceGarmentPricing();
        pricing.setBusinessId(businessId);
        pricing.setServiceTypeId(req.getServiceTypeId());
        pricing.setGarmentTypeId(req.getGarmentTypeId());
        pricing.setPrice(req.getPrice());
        pricing.setIsActive(true);
        return toPricingDTO(pricingRepository.save(pricing));
    }

    @Transactional
    public void removePricing(UUID pricingId) {
        ServiceGarmentPricing p = pricingRepository.findById(pricingId)
                .orElseThrow(() -> new BusinessException("Pricing not found", "PRICING_NOT_FOUND"));
        p.setIsActive(false);
        pricingRepository.save(p);
    }

    @Transactional(readOnly = true)
    public PricingLookupResult lookupPrice(UUID businessId, UUID serviceTypeId, UUID garmentTypeId) {
        ServiceType st = findServiceOrThrow(serviceTypeId);
        GarmentType gt = garmentTypeRepository.findById(garmentTypeId).orElse(null);

        Optional<ServiceGarmentPricing> specific = pricingRepository
                .findByServiceTypeIdAndGarmentTypeIdAndIsActiveTrue(serviceTypeId, garmentTypeId);

        if (specific.isPresent()) {
            return PricingLookupResult.builder()
                    .price(specific.get().getPrice())
                    .source("SPECIFIC")
                    .serviceName(st.getName())
                    .garmentName(gt != null ? gt.getName() : "Unknown")
                    .build();
        }

        return PricingLookupResult.builder()
                .price(st.getDefaultPrice())
                .source("DEFAULT")
                .serviceName(st.getName())
                .garmentName(gt != null ? gt.getName() : "Unknown")
                .build();
    }

    // ==================== Defaults ====================

    @Transactional
    public void createDefaultsForBusiness(UUID businessId) {
        log.info("Creating default service catalog for business {}", businessId);

        for (String[] def : DEFAULT_SERVICES) {
            if (!serviceTypeRepository.existsByBusinessIdAndNameIgnoreCase(businessId, def[0])) {
                ServiceType st = new ServiceType();
                st.setBusinessId(businessId);
                st.setName(def[0]);
                st.setDescription(def[1]);
                st.setCategory(def[2]);
                st.setDefaultPrice(new BigDecimal(def[3]));
                st.setUnit(def[4]);
                st.setSortOrder(Integer.parseInt(def[5]));
                st.setIcon(def[6]);
                st.setIsActive(true);
                serviceTypeRepository.save(st);
            }
        }

        for (String[] def : DEFAULT_GARMENTS) {
            if (!garmentTypeRepository.existsByBusinessIdAndNameIgnoreCase(businessId, def[0])) {
                GarmentType gt = new GarmentType();
                gt.setBusinessId(businessId);
                gt.setName(def[0]);
                gt.setDescription(def[1]);
                gt.setCategory(def[2]);
                gt.setSortOrder(Integer.parseInt(def[3]));
                gt.setIcon(def[4]);
                gt.setIsActive(true);
                garmentTypeRepository.save(gt);
            }
        }
    }

    private List<ServiceType> ensureDefaultsExist(UUID businessId) {
        List<ServiceType> services = serviceTypeRepository
                .findByBusinessIdAndIsActiveTrueOrderBySortOrderAsc(businessId);
        if (services.isEmpty()) {
            createDefaultsForBusiness(businessId);
            services = serviceTypeRepository
                    .findByBusinessIdAndIsActiveTrueOrderBySortOrderAsc(businessId);
        }
        return services;
    }

    // ==================== Helpers ====================

    private ServiceType findServiceOrThrow(UUID id) {
        return serviceTypeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Service type not found", "SERVICE_NOT_FOUND"));
    }

    private ServiceTypeDTO toServiceDTO(ServiceType st) {
        return ServiceTypeDTO.builder()
                .id(st.getId()).name(st.getName()).description(st.getDescription())
                .category(st.getCategory()).defaultPrice(st.getDefaultPrice())
                .unit(st.getUnit()).isActive(st.getIsActive())
                .sortOrder(st.getSortOrder()).icon(st.getIcon())
                .createdAt(st.getCreatedAt()).build();
    }

    private GarmentTypeDTO toGarmentDTO(GarmentType gt) {
        return GarmentTypeDTO.builder()
                .id(gt.getId()).name(gt.getName()).description(gt.getDescription())
                .category(gt.getCategory()).isActive(gt.getIsActive())
                .sortOrder(gt.getSortOrder()).icon(gt.getIcon())
                .createdAt(gt.getCreatedAt()).build();
    }

    private ServiceGarmentPricingDTO toPricingDTO(ServiceGarmentPricing p) {
        ServiceType st = serviceTypeRepository.findById(p.getServiceTypeId()).orElse(null);
        GarmentType gt = garmentTypeRepository.findById(p.getGarmentTypeId()).orElse(null);
        return ServiceGarmentPricingDTO.builder()
                .id(p.getId()).serviceTypeId(p.getServiceTypeId())
                .serviceName(st != null ? st.getName() : "Unknown")
                .garmentTypeId(p.getGarmentTypeId())
                .garmentName(gt != null ? gt.getName() : "Unknown")
                .price(p.getPrice()).isActive(p.getIsActive()).build();
    }
}