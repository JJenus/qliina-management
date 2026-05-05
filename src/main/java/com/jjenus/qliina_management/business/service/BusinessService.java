package com.jjenus.qliina_management.business.service;

import com.jjenus.qliina_management.business.dto.*;
import com.jjenus.qliina_management.business.model.Business;
import com.jjenus.qliina_management.business.model.Shop;
import com.jjenus.qliina_management.business.repository.BusinessRepository;
import com.jjenus.qliina_management.business.repository.ShopRepository;
import com.jjenus.qliina_management.common.AddressDTO;
import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.identity.model.*;
import com.jjenus.qliina_management.identity.repository.*;
import com.jjenus.qliina_management.identity.security.JwtProvider;
import com.jjenus.qliina_management.payment.service.PaymentMethodService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core service for managing Business entities and the open self-registration flow.
 *
 * The registerBusiness method is a single @Transactional operation that atomically
 * creates a Business row, the first Shop, the BUSINESS_OWNER User, AuthAccount
 * credentials, and returns JWT tokens so the caller is immediately authenticated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessService {

    private final BusinessRepository     businessRepository;
    private final ShopRepository         shopRepository;
    private final UserRepository         userRepository;
    private final AuthAccountRepository  authAccountRepository;
    private final RoleRepository         roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder        passwordEncoder;
    private final JwtProvider            jwtProvider;
    private final UserDetailsService     userDetailsService;
    private final PaymentMethodService     paymentMethodInitializer;
    private final ServiceCatalogService     serviceCatalogService;

    // -------------------------------------------------------------------------
    // Open registration
    // -------------------------------------------------------------------------

    /**
     * Creates a Business, its first Shop, and a BUSINESS_OWNER User atomically.
     * Returns JWT tokens so the caller is immediately authenticated.
     *
     * @param request validated registration payload
     * @return auth tokens + created entity IDs
     * @throws BusinessException if any uniqueness constraint is violated
     */
    @Transactional
    public BusinessRegistrationResponse registerBusiness(CreateBusinessRequest request) {

        // 1. Passwords match
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Passwords do not match", "PASSWORD_MISMATCH", "confirmPassword");
        }

        // 2. User uniqueness
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("Username already taken", "USERNAME_EXISTS", "username");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already registered", "EMAIL_EXISTS", "email");
        }
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new BusinessException("Phone already registered", "PHONE_EXISTS", "phone");
        }

        // 3. Shop code uniqueness
        String upperCode = request.getShopCode().toUpperCase();
        if (shopRepository.existsByCode(upperCode)) {
            throw new BusinessException("Shop code already in use", "SHOP_CODE_EXISTS", "shopCode");
        }

        // 4. Build unique slug
        String slug = buildSlug(request.getSlug(), request.getBusinessName());
        LocalDateTime now = LocalDateTime.now();

        // 5. Create Business
        Business business = Business.builder()
                .name(request.getBusinessName()).slug(slug)
                .status(Business.Status.TRIAL).plan(Business.Plan.FREE)
                .email(request.getBusinessEmail()).phone(request.getBusinessPhone())
                .trialEndsAt(now.plusDays(30)).build();
        if (request.getBusinessAddress() != null) {
            business.setAddress(mapAddress(request.getBusinessAddress()));
        }
        business = businessRepository.save(business);
        final UUID businessId = business.getId();
        
        paymentMethodInitializer.createDefaultMethodsForBusiness(businessId);
       
        serviceCatalogService.createDefaultsForBusiness(businessId);

        // 6. Create initial Shop
        Shop shop = new Shop();
        shop.setName(request.getShopName());
        shop.setCode(upperCode);
        shop.setBusinessId(businessId);
        shop.setActive(true);
        shop = shopRepository.save(shop);

        // 7. Create owner User
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail()).phone(request.getPhone())
                .firstName(request.getFirstName()).lastName(request.getLastName())
                .enabled(true).primaryShopId(shop.getId()).build();
        user.addShop(shop);
        user.setBusinessId(businessId);
        user = userRepository.save(user);

        // back-fill shop audit field
        shop.setCreatedBy(user.getId());
        shopRepository.save(shop);

        // 8. AuthAccount
        AuthAccount auth = new AuthAccount();
        auth.setUser(user);
        auth.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        auth.setPasswordLastChanged(now);
        auth.setFailedAttempts(0);
        auth.setTotpEnabled(false);
        AuthAccount savedAuth = authAccountRepository.save(auth);
          
        user.setAuthAccount(savedAuth);
        // 9. Assign BUSINESS_OWNER role (business-level, shopId = null)
        Role ownerRole = roleRepository.findByName("BUSINESS_OWNER")
                .orElseThrow(() -> new BusinessException(
                        "BUSINESS_OWNER role not found — ensure DataInitializer has run",
                        "ROLE_NOT_FOUND"));
        UserRole userRole = new UserRole();
        userRole.setUser(user); userRole.setRole(ownerRole);
        userRole.setBusinessId(businessId); userRole.setShopId(null);
        user.getRoles().add(userRole);
        user = userRepository.save(user);

        // 10. Issue JWT tokens
        UserDetails ud = userDetailsService.loadUserByUsername(user.getUsername());
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("businessId", businessId);
        claims.put("permissions", effectivePermissions(user));
        String accessToken  = jwtProvider.generateToken(claims, ud);
        String refreshToken = jwtProvider.generateRefreshToken(ud);
        storeRefreshToken(user, refreshToken);

        log.info("Business registered: slug={}, businessId={}, owner=@{}", slug, businessId, user.getUsername());

        List<String> roles = user.getRoles().stream()
                .map(ur -> ur.getRole().getName()).collect(Collectors.toList());

        return BusinessRegistrationResponse.builder()
                .accessToken(accessToken).refreshToken(refreshToken)
                .tokenType("Bearer").expiresIn(86400000L)
                .businessId(businessId).businessSlug(slug).shopId(shop.getId())
                .user(BusinessRegistrationResponse.UserInfo.builder()
                        .id(user.getId()).username(user.getUsername())
                        .email(user.getEmail()).phone(user.getPhone())
                        .firstName(user.getFirstName()).lastName(user.getLastName())
                        .businessId(businessId).primaryShopId(shop.getId())
                        .roles(roles).permissions(effectivePermissions(user)).build())
                .build();
    }

    // -------------------------------------------------------------------------
    // CRUD
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public BusinessDTO getBusiness(UUID id) {
        return toDTO(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<BusinessDTO> listBusinesses(Business.Status status, String search, Pageable pageable) {
        Page<Business> page;
        if (search != null && !search.isBlank())      page = businessRepository.searchByName(search, pageable);
        else if (status != null)                       page = businessRepository.findByStatus(status, pageable);
        else                                           page = businessRepository.findAll(pageable);
        return PageResponse.from(page.map(this::toDTO));
    }

    @Transactional
    public BusinessDTO updateBusiness(UUID id, UpdateBusinessRequest req) {
        Business b = findOrThrow(id);
        if (req.getName()    != null) b.setName(req.getName());
        if (req.getEmail()   != null) b.setEmail(req.getEmail());
        if (req.getPhone()   != null) b.setPhone(req.getPhone());
        if (req.getLogoUrl() != null) b.setLogoUrl(req.getLogoUrl());
        if (req.getPlan()    != null) b.setPlan(req.getPlan());
        if (req.getAddress() != null) b.setAddress(mapAddress(req.getAddress()));
        return toDTO(businessRepository.save(b));
    }

    @Transactional
    public BusinessDTO updateStatus(UUID id, Business.Status newStatus) {
        Business b = findOrThrow(id);
        b.setStatus(newStatus);
        log.info("Business {} status -> {}", id, newStatus);
        return toDTO(businessRepository.save(b));
    }

    // -------------------------------------------------------------------------
    // Login status check (called by AuthService)
    // -------------------------------------------------------------------------

    /**
     * Returns true if the business is operational (TRIAL or ACTIVE).
     * Called by AuthService.authenticate() to block logins for suspended
     * or cancelled businesses.
     */
    @Transactional(readOnly = true)
    public boolean isOperational(UUID id) {
        return businessRepository.findById(id)
                .map(b -> b.getStatus() == Business.Status.ACTIVE
                       || b.getStatus() == Business.Status.TRIAL)
                .orElse(false);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Business findOrThrow(UUID id) {
        return businessRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Business not found", "BUSINESS_NOT_FOUND"));
    }

    private BusinessDTO toDTO(Business b) {
        return BusinessDTO.builder()
                .id(b.getId()).name(b.getName()).slug(b.getSlug())
                .status(b.getStatus()).plan(b.getPlan())
                .email(b.getEmail()).phone(b.getPhone()).logoUrl(b.getLogoUrl())
                .trialEndsAt(b.getTrialEndsAt())
                .createdAt(b.getCreatedAt()).updatedAt(b.getUpdatedAt())
                .activeShopCount(shopRepository.countActiveByBusinessId(b.getId()))
                .build();
    }

    private String buildSlug(String override, String name) {
        String base = (override != null && !override.isBlank())
                ? override.toLowerCase().replaceAll("[^a-z0-9-]", "-")
                : name.toLowerCase().replaceAll("[^a-z0-9]", "-").replaceAll("-+", "-");
        Random rng = new Random();
        String candidate = base + "-" + String.format("%04x", rng.nextInt(0xFFFF));
        while (businessRepository.existsBySlug(candidate)) {
            candidate = base + "-" + String.format("%04x", rng.nextInt(0xFFFF));
        }
        return candidate;
    }

    private com.jjenus.qliina_management.identity.model.Address mapAddress(AddressDTO dto) {
        if (dto == null) return null;
        return new com.jjenus.qliina_management.identity.model.Address(
                dto.getAddressLine1(), dto.getAddressLine2(),
                dto.getCity(), dto.getState(), dto.getPostalCode(),
                dto.getCountry(), dto.getLatitude(), dto.getLongitude());
    }

    private List<String> effectivePermissions(User user) {
        return user.getRoles().stream()
                .flatMap(ur -> ur.getRole().getPermissions().stream())
                .map(Permission::getName).distinct().collect(Collectors.toList());
    }

    private void storeRefreshToken(User user, String token) {
        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setTokenHash(hashToken(token));
        rt.setExpiresAt(LocalDateTime.now().plusDays(7));
        refreshTokenRepository.save(rt);
    }

    private String hashToken(String token) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(d.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
}
