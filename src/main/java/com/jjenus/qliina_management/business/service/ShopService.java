package com.jjenus.qliina_management.business.service;

import com.jjenus.qliina_management.business.dto.CreateShopRequest;
import com.jjenus.qliina_management.business.dto.ShopDTO;
import com.jjenus.qliina_management.business.dto.UpdateShopRequest;
import com.jjenus.qliina_management.business.model.Shop;
import com.jjenus.qliina_management.business.repository.BusinessRepository;
import com.jjenus.qliina_management.business.repository.ShopRepository;
import com.jjenus.qliina_management.common.AddressDTO;
import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.identity.model.Address;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * Service for managing Shop entities within a Business.
 *
 * All mutation methods verify the target shop belongs to the given businessId
 * before applying changes, preventing cross-tenant data access.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopRepository     shopRepository;
    private final BusinessRepository businessRepository;

    @Transactional(readOnly = true)
    public PageResponse<ShopDTO> listShops(UUID businessId, Pageable pageable) {
        assertBusinessExists(businessId);
        return PageResponse.from(shopRepository.findByBusinessId(businessId, pageable).map(this::toDTO));
    }

    @Transactional(readOnly = true)
    public ShopDTO getShop(UUID businessId, UUID shopId) {
        return toDTO(findAndVerify(businessId, shopId));
    }

    @Transactional
    public ShopDTO createShop(UUID businessId, CreateShopRequest req) {
        assertBusinessExists(businessId);
        String code = req.getCode().toUpperCase();
        if (shopRepository.existsByCode(code)) {
            throw new BusinessException("Shop code already in use", "SHOP_CODE_EXISTS", "code");
        }
        Shop shop = new Shop();
        shop.setBusinessId(businessId); shop.setName(req.getName()); shop.setCode(code);
        shop.setPhone(req.getPhone()); shop.setEmail(req.getEmail());
        shop.setTimezone(req.getTimezone()); shop.setActive(true);
        if (req.getAddress()        != null) shop.setAddress(mapAddress(req.getAddress()));
        if (req.getOperatingHours() != null) shop.setOperatingHours(req.getOperatingHours());
        shop = shopRepository.save(shop);
        log.info("Shop created: code={}, businessId={}", code, businessId);
        return toDTO(shop);
    }

    @Transactional
    public ShopDTO updateShop(UUID businessId, UUID shopId, UpdateShopRequest req) {
        Shop shop = findAndVerify(businessId, shopId);
        if (req.getName()           != null) shop.setName(req.getName());
        if (req.getPhone()          != null) shop.setPhone(req.getPhone());
        if (req.getEmail()          != null) shop.setEmail(req.getEmail());
        if (req.getTimezone()       != null) shop.setTimezone(req.getTimezone());
        if (req.getAddress()        != null) shop.setAddress(mapAddress(req.getAddress()));
        if (req.getOperatingHours() != null) shop.setOperatingHours(req.getOperatingHours());
        return toDTO(shopRepository.save(shop));
    }

    @Transactional
    public void deactivateShop(UUID businessId, UUID shopId) {
        Shop shop = findAndVerify(businessId, shopId);
        shop.setActive(false);
        shopRepository.save(shop);
        log.info("Shop deactivated: shopId={}, businessId={}", shopId, businessId);
    }

    @Transactional
    public ShopDTO reactivateShop(UUID businessId, UUID shopId) {
        Shop shop = findAndVerify(businessId, shopId);
        shop.setActive(true);
        return toDTO(shopRepository.save(shop));
    }

    // -------------------------------------------------------------------------

    private Shop findAndVerify(UUID businessId, UUID shopId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new BusinessException("Shop not found", "SHOP_NOT_FOUND"));
        // same error message — avoids leaking other tenants' shop IDs
        if (!businessId.equals(shop.getBusinessId())) {
            throw new BusinessException("Shop not found", "SHOP_NOT_FOUND");
        }
        return shop;
    }

    private void assertBusinessExists(UUID businessId) {
        if (!businessRepository.existsById(businessId)) {
            throw new BusinessException("Business not found", "BUSINESS_NOT_FOUND");
        }
    }

    private Address mapAddress(AddressDTO dto) {
        if (dto == null) return null;
        return new Address(dto.getAddressLine1(), dto.getAddressLine2(),
                dto.getCity(), dto.getState(), dto.getPostalCode(),
                dto.getCountry(), dto.getLatitude(), dto.getLongitude());
    }

    ShopDTO toDTO(Shop s) {
        return ShopDTO.builder()
                .id(s.getId()).businessId(s.getBusinessId()).name(s.getName()).code(s.getCode())
                .phone(s.getPhone()).email(s.getEmail()).timezone(s.getTimezone()).active(s.getActive())
                .operatingHours(s.getOperatingHours()).createdAt(s.getCreatedAt()).updatedAt(s.getUpdatedAt())
                .build();
    }
}
