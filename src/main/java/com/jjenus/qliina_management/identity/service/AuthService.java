package com.jjenus.qliina_management.identity.service;

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
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;
    private final AuthAccountRepository authAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse authenticate(LoginRequest request) {
        // FIX: use findByIdentity (username OR email) for the pre-auth lock check
        User preCheckUser = userRepository.findByIdentity(request.getUsername()).orElse(null);
        if (preCheckUser != null && isAccountLocked(preCheckUser)) {
            throw new BusinessException("Account is locked. Please try again later.", "ACCOUNT_LOCKED");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // FIX: UserDetails.getUsername() now always returns the canonical DB username
            // (because CustomUserDetailsService always calls .username(user.getUsername())),
            // so findByUsername() is safe here regardless of whether the caller used
            // their email or username to log in.
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userRepository.findByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found after authentication"));

            // Check 2FA before issuing tokens
            if (user.getAuthAccount() != null && Boolean.TRUE.equals(user.getAuthAccount().getTotpEnabled())) {
                return AuthResponse.builder()
                        .requires2FA(true)
                        .user(mapToUserInfo(user))
                        .build();
            }

            updateLastLogin(user);
            clearFailedAttempts(user);

            return generateAuthResponse(user, userDetails);

        } catch (BadCredentialsException e) {
            handleFailedLogin(request.getUsername());
            throw new BadCredentialsException("Invalid username or password");
        }
    }

    @Transactional
    public AuthResponse verify2FA(Verify2FARequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));

        if (!verifyTOTP(user.getAuthAccount().getTotpSecret(), request.getCode())) {
            throw new BusinessException("Invalid 2FA code", "INVALID_2FA_CODE");
        }

        updateLastLogin(user);
        clearFailedAttempts(user);

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        return generateAuthResponse(user, userDetails);
    }

    @Transactional
    public Setup2FAResponse setup2FA(Setup2FARequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));

        String secret = generateTOTPSecret();
        String qrCodeUrl = generateQRCodeUrl(user.getUsername(), secret);
        List<String> backupCodes = generateBackupCodes();

        AuthAccount authAccount = user.getAuthAccount();
        authAccount.setTotpSecret(secret);
        authAccount.setTotpEnabled(false); // stays false until user confirms a valid code
        authAccountRepository.save(authAccount);

        return Setup2FAResponse.builder()
                .secret(secret)
                .qrCodeUrl(qrCodeUrl)
                .backupCodes(backupCodes)
                .build();
    }

    @Transactional
    public void disable2FA(Disable2FARequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));

        AuthAccount authAccount = user.getAuthAccount();
        authAccount.setTotpSecret(null);
        authAccount.setTotpEnabled(false);
        authAccountRepository.save(authAccount);
    }

    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        // Validate DB record first — catches revoked tokens before touching JWT
        String tokenHash = hashToken(refreshToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException("Refresh token not found", "REFRESH_TOKEN_NOT_FOUND"));

        if (storedToken.getRevokedAt() != null || storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Refresh token has been revoked or expired", "REFRESH_TOKEN_INVALID");
        }

        if (!jwtProvider.validateToken(refreshToken)) {
            throw new BusinessException("Invalid refresh token", "INVALID_REFRESH_TOKEN");
        }

        String username = jwtProvider.extractUsername(refreshToken);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // Rotate: revoke used token before issuing new pair
        storedToken.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(storedToken);

        return generateAuthResponse(user, userDetails);
    }

    @Transactional
    public void logout(String refreshToken) {
        String tokenHash = hashToken(refreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(token);
        });
        SecurityContextHolder.clearContext();
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        // Silent no-op when user not found — prevents user enumeration
        // FIX: also uses findByIdentity so reset works when user provides email
        userRepository.findByIdentity(request.getUsername()).ifPresent(user -> {
            String token = generateSecureToken();
            String tokenHash = hashToken(token);

            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUser(user);
            resetToken.setTokenHash(tokenHash);
            resetToken.setExpiresAt(LocalDateTime.now().plusHours(1));

            passwordResetTokenRepository.save(resetToken);

            // TODO: send email with token
            log.info("Password reset token for user {}: {}", user.getUsername(), token);
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Passwords do not match", "PASSWORD_MISMATCH");
        }

        String tokenHash = hashToken(request.getToken());
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException("Invalid or expired token", "INVALID_RESET_TOKEN"));

        if (resetToken.getUsedAt() != null || resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Token has been used or expired", "TOKEN_EXPIRED");
        }

        User user = resetToken.getUser();
        AuthAccount authAccount = user.getAuthAccount();
        authAccount.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        authAccount.setPasswordLastChanged(LocalDateTime.now());
        authAccountRepository.save(authAccount);

        resetToken.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);

        refreshTokenRepository.deleteByUserId(user.getId());
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Passwords do not match", "PASSWORD_MISMATCH");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));

        AuthAccount authAccount = user.getAuthAccount();

        if (!passwordEncoder.matches(request.getCurrentPassword(), authAccount.getPasswordHash())) {
            throw new BusinessException("Current password is incorrect", "INVALID_CURRENT_PASSWORD");
        }

        authAccount.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        authAccount.setPasswordLastChanged(LocalDateTime.now());
        authAccountRepository.save(authAccount);

        refreshTokenRepository.deleteByUserId(userId);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private AuthResponse generateAuthResponse(User user, UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("businessId", user.getBusinessId());
        claims.put("permissions", getEffectivePermissions(user));

        String accessToken = jwtProvider.generateToken(claims, userDetails);
        String refreshToken = jwtProvider.generateRefreshToken(userDetails);

        storeRefreshToken(user, refreshToken, null);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(86400000L)
                .user(mapToUserInfo(user))
                .requires2FA(false)
                .build();
    }

    private void storeRefreshToken(User user, String token, String deviceInfo) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hashToken(token));
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(7));
        refreshToken.setDeviceInfo(deviceInfo);
        refreshTokenRepository.save(refreshToken);
    }

    private AuthResponse.UserInfo mapToUserInfo(User user) {
        return AuthResponse.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .businessId(user.getBusinessId())
                .currentShopId(user.getPrimaryShopId())
                .roles(user.getRoles().stream()
                        .map(userRole -> userRole.getRole().getName())
                        .collect(Collectors.toList()))
                .permissions(getEffectivePermissions(user))
                .requires2FA(user.getAuthAccount() != null &&
                        Boolean.TRUE.equals(user.getAuthAccount().getTotpEnabled()))
                .profileImage(user.getProfileImage())
                .build();
    }

    private List<String> getEffectivePermissions(User user) {
        return user.getRoles().stream()
                .flatMap(userRole -> userRole.getRole().getPermissions().stream())
                .map(Permission::getName)
                .distinct()
                .collect(Collectors.toList());
    }

    private void updateLastLogin(User user) {
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        AuthAccount authAccount = user.getAuthAccount();
        authAccount.setLastLogin(LocalDateTime.now());
        authAccountRepository.save(authAccount);
    }

    private void clearFailedAttempts(User user) {
        AuthAccount authAccount = user.getAuthAccount();
        authAccount.setFailedAttempts(0);
        authAccount.setLockedUntil(null);
        authAccountRepository.save(authAccount);
    }

    private void handleFailedLogin(String identity) {
        // FIX: use findByIdentity so failed attempts are tracked even when login
        // was attempted with an email address
        userRepository.findByIdentity(identity).ifPresent(user -> {
            AuthAccount authAccount = user.getAuthAccount();
            authAccount.setFailedAttempts(authAccount.getFailedAttempts() + 1);

            if (authAccount.getFailedAttempts() >= 5) {
                authAccount.setLockedUntil(LocalDateTime.now().plusMinutes(30));
            }

            authAccountRepository.save(authAccount);
        });
    }

    private boolean isAccountLocked(User user) {
        if (user.getAuthAccount() == null || user.getAuthAccount().getLockedUntil() == null) {
            return false;
        }
        return user.getAuthAccount().getLockedUntil().isAfter(LocalDateTime.now());
    }

    private String generateTOTPSecret() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        return base32Encode(bytes);
    }

    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = data[0];
        int next = 1;
        int bitsLeft = 8;
        while (bitsLeft > 0 || next < data.length) {
            if (bitsLeft < 5) {
                if (next < data.length) {
                    buffer <<= 8;
                    buffer |= data[next++] & 0xff;
                    bitsLeft += 8;
                } else {
                    int pad = 5 - bitsLeft;
                    buffer <<= pad;
                    bitsLeft += pad;
                }
            }
            int index = 0x1f & (buffer >> (bitsLeft - 5));
            bitsLeft -= 5;
            sb.append(BASE32_CHARS.charAt(index));
        }
        return sb.toString();
    }

    private String generateQRCodeUrl(String username, String secret) {
        return String.format("otpauth://totp/Laundry:%s?secret=%s&issuer=Laundry", username, secret);
    }

    private List<String> generateBackupCodes() {
        SecureRandom random = new SecureRandom();
        return java.util.stream.Stream.generate(() -> {
            byte[] bytes = new byte[8];
            random.nextBytes(bytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, 10);
        }).limit(8).collect(Collectors.toList());
    }

    private boolean verifyTOTP(String secret, String code) {
        // TODO: implement real TOTP verification, e.g. with com.warrenstrange:googleauth
        throw new BusinessException(
                "2FA verification is not fully implemented.",
                "TOTP_NOT_IMPLEMENTED");
    }

    private String generateSecureToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
