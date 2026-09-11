package com.jjenus.qliina_management.identity.service;

import com.jjenus.qliina_management.business.dto.BusinessRegistrationResponse;
import com.jjenus.qliina_management.business.dto.CreateBusinessRequest;
import com.jjenus.qliina_management.business.service.BusinessService;
import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.identity.dto.*;
import com.jjenus.qliina_management.identity.model.*;
import com.jjenus.qliina_management.identity.repository.*;
import com.jjenus.qliina_management.identity.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Handles authentication, session management, and password operations.
 *
 * Open business self-registration is delegated to BusinessService.registerBusiness
 * so all tenant-creation logic lives in one place.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager        authenticationManager;
    private final UserDetailsService           userDetailsService;
    private final JwtProvider                  jwtProvider;
    private final UserRepository               userRepository;
    private final AuthAccountRepository        authAccountRepository;
    private final RefreshTokenRepository       refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder              passwordEncoder;
    /** Used for the business-status check on login and the registration flow. */
    private final BusinessService              businessService;
    /** Used to persist failed-login counters outside the (rolling-back) login transaction. */
    private final PlatformTransactionManager   transactionManager;


    /**
     * Returns the full UserInfo payload for the currently authenticated user.
     * Called by GET /api/v1/auth/me.
     *
     * @param userId  the UUID resolved from the JWT by the controller
     * @return        a UserInfo DTO identical in shape to the login response
     */
    @Transactional(readOnly = true)
    public AuthResponse.UserInfo getCurrentUserInfo(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
        return mapToUserInfo(user);
    }
    
    // -------------------------------------------------------------------------
    // Authentication
    // -------------------------------------------------------------------------

    @Transactional
    public AuthResponse authenticate(LoginRequest request) {
        User preCheckUser = userRepository.findByIdentity(request.getUsername()).orElse(null);
        if (preCheckUser != null && isAccountLocked(preCheckUser)) {
            throw new BusinessException("Account is locked. Please try again later.", "ACCOUNT_LOCKED");
        }
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
            SecurityContextHolder.getContext().setAuthentication(auth);
            UserDetails ud = (UserDetails) auth.getPrincipal();
            User user = userRepository.findByUsername(ud.getUsername())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found after authentication"));

            // FIX (Flaw 9): block login for suspended / cancelled businesses
            if (user.getBusinessId() != null && !businessService.isOperational(user.getBusinessId())) {
                throw new BusinessException(
                        "This business account is not active. Please contact support.",
                        "BUSINESS_NOT_ACTIVE");
            }
            if (user.getAuthAccount() != null && Boolean.TRUE.equals(user.getAuthAccount().getTotpEnabled())) {
                return AuthResponse.builder().requires2FA(true).user(mapToUserInfo(user)).build();
            }
            updateLastLogin(user);
            clearFailedAttempts(user);
            return generateAuthResponse(user, ud);
        } catch (BadCredentialsException e) {
            handleFailedLogin(request.getUsername());
            throw new BadCredentialsException("Invalid username or password");
        }
    }

    // -------------------------------------------------------------------------
    // Open registration
    // -------------------------------------------------------------------------

    /** Delegates to BusinessService.registerBusiness — single transaction. */
    @Transactional
    public BusinessRegistrationResponse registerBusiness(CreateBusinessRequest request) {
        return businessService.registerBusiness(request);
    }

    // -------------------------------------------------------------------------
    // 2FA
    // -------------------------------------------------------------------------

    @Transactional
    public AuthResponse verify2FA(Verify2FARequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
        if (user.getAuthAccount() == null
                || !verifyTOTP(user.getAuthAccount().getTotpSecret(), request.getCode())) {
            throw new BusinessException("Invalid 2FA code", "INVALID_2FA_CODE");
        }
        // First successful verification also ENROLLS: setup2FA stores the secret
        // with totpEnabled=false; this flips it so future logins gate on 2FA.
        if (!Boolean.TRUE.equals(user.getAuthAccount().getTotpEnabled())) {
            user.getAuthAccount().setTotpEnabled(true);
            authAccountRepository.save(user.getAuthAccount());
        }
        updateLastLogin(user);
        clearFailedAttempts(user);
        return generateAuthResponse(user, userDetailsService.loadUserByUsername(user.getUsername()));
    }

    @Transactional
    public Setup2FAResponse setup2FA(Setup2FARequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
        String secret = generateTOTPSecret();
        AuthAccount a = user.getAuthAccount();
        a.setTotpSecret(secret); a.setTotpEnabled(false);
        authAccountRepository.save(a);
        return Setup2FAResponse.builder().secret(secret)
                .qrCodeUrl(generateQRCodeUrl(user.getUsername(), secret))
                .backupCodes(generateBackupCodes()).build();
    }

    @Transactional
    public void disable2FA(Disable2FARequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
        user.getAuthAccount().setTotpSecret(null);
        user.getAuthAccount().setTotpEnabled(false);
        authAccountRepository.save(user.getAuthAccount());
    }

    // -------------------------------------------------------------------------
    // Token management
    // -------------------------------------------------------------------------

    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        String hash = hashToken(refreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException("Refresh token not found", "REFRESH_TOKEN_NOT_FOUND"));
        if (stored.getRevokedAt() != null || stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Refresh token has been revoked or expired", "REFRESH_TOKEN_INVALID");
        }
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new BusinessException("Invalid refresh token", "INVALID_REFRESH_TOKEN");
        }
        String username = jwtProvider.extractUsername(refreshToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        stored.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(stored);
        return generateAuthResponse(user, userDetailsService.loadUserByUsername(username));
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByTokenHash(hashToken(refreshToken)).ifPresent(t -> {
            t.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(t);
        });
        SecurityContextHolder.clearContext();
    }

    // -------------------------------------------------------------------------
    // Password operations
    // -------------------------------------------------------------------------

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByIdentity(request.getUsername()).ifPresent(user -> {
            String token = generateSecureToken();
            PasswordResetToken rt = new PasswordResetToken();
            rt.setUser(user); rt.setTokenHash(hashToken(token));
            rt.setExpiresAt(LocalDateTime.now().plusHours(1));
            passwordResetTokenRepository.save(rt);
            log.info("Password reset token issued for user {}", user.getUsername());
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Passwords do not match", "PASSWORD_MISMATCH");
        }
        PasswordResetToken rt = passwordResetTokenRepository.findByTokenHash(hashToken(request.getToken()))
                .orElseThrow(() -> new BusinessException("Invalid or expired reset token", "INVALID_RESET_TOKEN"));
        if (rt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Reset token has expired", "RESET_TOKEN_EXPIRED");
        }
        if (rt.getUsedAt() != null) {
            throw new BusinessException("Reset token has already been used", "RESET_TOKEN_USED");
        }
        AuthAccount a = rt.getUser().getAuthAccount();
        a.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        a.setPasswordLastChanged(LocalDateTime.now());
        authAccountRepository.save(a);
        rt.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(rt);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Passwords do not match", "PASSWORD_MISMATCH");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
        AuthAccount a = user.getAuthAccount();
        if (!passwordEncoder.matches(request.getCurrentPassword(), a.getPasswordHash())) {
            throw new BusinessException("Current password is incorrect", "INVALID_PASSWORD");
        }
        a.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        a.setPasswordLastChanged(LocalDateTime.now());
        authAccountRepository.save(a);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private AuthResponse generateAuthResponse(User user, UserDetails ud) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId()); claims.put("businessId", user.getBusinessId());
        claims.put("permissions", effectivePermissions(user));
        String at = jwtProvider.generateToken(claims, ud);
        String rt = jwtProvider.generateRefreshToken(ud);
        storeRefreshToken(user, rt, null);
        return AuthResponse.builder().accessToken(at).refreshToken(rt)
                .tokenType("Bearer").expiresIn(86400L).user(mapToUserInfo(user)).requires2FA(false).build();
    }

    private void storeRefreshToken(User user, String token, String deviceInfo) {
        // Enforce a single active session per user: a new login (or token
        // rotation) revokes every previously issued refresh token for the user.
        refreshTokenRepository.deleteByUserId(user.getId());
        RefreshToken rt = new RefreshToken();
        rt.setUser(user); rt.setTokenHash(hashToken(token));
        rt.setExpiresAt(LocalDateTime.now().plusDays(7)); rt.setDeviceInfo(deviceInfo);
        refreshTokenRepository.save(rt);
    }

    private AuthResponse.UserInfo mapToUserInfo(User user) {
        return AuthResponse.UserInfo.builder()
                .id(user.getId()).username(user.getUsername()).email(user.getEmail()).phone(user.getPhone())
                .firstName(user.getFirstName()).lastName(user.getLastName())
                .businessId(user.getBusinessId()).currentShopId(user.getPrimaryShopId())
                .roles(user.getRoles().stream().map(ur -> ur.getRole().getName()).collect(Collectors.toList()))
                .permissions(effectivePermissions(user))
                .requires2FA(user.getAuthAccount() != null
                        && Boolean.TRUE.equals(user.getAuthAccount().getTotpEnabled()))
                .profileImage(user.getProfileImage()).build();
    }

    private List<String> effectivePermissions(User user) {
        return user.getRoles().stream().flatMap(ur -> ur.getRole().getPermissions().stream())
                .map(Permission::getName).distinct().collect(Collectors.toList());
    }

    private void updateLastLogin(User user) {
        user.setLastLogin(LocalDateTime.now()); userRepository.save(user);
        user.getAuthAccount().setLastLogin(LocalDateTime.now()); authAccountRepository.save(user.getAuthAccount());
    }

    private void clearFailedAttempts(User user) {
        user.getAuthAccount().setFailedAttempts(0); user.getAuthAccount().setLockedUntil(null);
        authAccountRepository.save(user.getAuthAccount());
    }

    private void handleFailedLogin(String identity) {
        // Run in its own REQUIRES_NEW transaction: the caller's @Transactional login
        // throws BadCredentialsException, which would roll back (and lose) the counter.
        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tt.executeWithoutResult(status -> {
            userRepository.findByIdentity(identity).ifPresent(user -> {
                AuthAccount a = user.getAuthAccount();
                a.setFailedAttempts(a.getFailedAttempts() + 1);
                if (a.getFailedAttempts() >= 5) a.setLockedUntil(LocalDateTime.now().plusMinutes(30));
                authAccountRepository.save(a);
            });
        });
    }

    private boolean isAccountLocked(User user) {
        if (user.getAuthAccount() == null || user.getAuthAccount().getLockedUntil() == null) return false;
        return user.getAuthAccount().getLockedUntil().isAfter(LocalDateTime.now());
    }

    private String generateTOTPSecret() {
        byte[] b = new byte[20]; new SecureRandom().nextBytes(b); return base32Encode(b);
    }

    private static final String B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buf = data[0], next = 1, bits = 8;
        while (bits > 0 || next < data.length) {
            if (bits < 5) {
                if (next < data.length) { buf <<= 8; buf |= data[next++] & 0xff; bits += 8; }
                else { buf <<= (5 - bits); bits = 5; }
            }
            sb.append(B32.charAt(0x1f & (buf >> (bits - 5)))); bits -= 5;
        }
        return sb.toString();
    }

    private String generateQRCodeUrl(String user, String secret) {
        return String.format("otpauth://totp/Qliina:%s?secret=%s&issuer=Qliina", user, secret);
    }

    private List<String> generateBackupCodes() {
        SecureRandom rng = new SecureRandom();
        return java.util.stream.Stream.generate(() -> {
            byte[] b = new byte[8]; rng.nextBytes(b);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(b).substring(0, 10);
        }).limit(8).collect(Collectors.toList());
    }

    private boolean verifyTOTP(String secret, String code) {
        if (secret == null || secret.isBlank() || code == null || code.isBlank()) {
            return false;
        }
        byte[] key = base32Decode(secret);
        long counter = Instant.now().getEpochSecond() / 30L;
        // RFC 6238 — accept the current 30s window plus one step either side
        // to tolerate clock drift at login.
        for (long offset = -1; offset <= 1; offset++) {
            if (constantTimeEquals(generateTOTP(key, counter + offset), code)) {
                return true;
            }
        }
        return false;
    }

    private static String generateTOTP(byte[] key, long counter) {
        byte[] data = new byte[8];
        for (int i = 7; i >= 0; i--) {
            data[i] = (byte) (counter & 0xff);
            counter >>= 8;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("TOTP generation failed", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    private byte[] base32Decode(String secret) {
        String normalized = secret.replace("-", "").replace(" ", "").toUpperCase();
        ByteArrayOutputStream decoded = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (int i = 0; i < normalized.length(); i++) {
            int value = B32.indexOf(normalized.charAt(i));
            if (value < 0) {
                continue; // tolerate stray padding/invalid chars
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                decoded.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return decoded.toByteArray();
    }

    private String generateSecureToken() {
        byte[] b = new byte[32]; new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
}
