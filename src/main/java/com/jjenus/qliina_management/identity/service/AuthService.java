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

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
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
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
            
            // Check if account is locked
            if (isAccountLocked(user)) {
                throw new BusinessException("Account is locked. Please try again later.", "ACCOUNT_LOCKED");
            }
            
            // Check if 2FA is required
            if (user.getAuthAccount() != null && Boolean.TRUE.equals(user.getAuthAccount().getTotpEnabled())) {
                // Return partial response indicating 2FA required
                return AuthResponse.builder()
                    .requires2FA(true)
                    .user(mapToUserInfo(user))
                    .build();
            }
            
            // Update last login
            updateLastLogin(user);
            
            // Clear failed attempts
            clearFailedAttempts(user);
            
            // Generate tokens
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
        
        // Verify TOTP code (simplified - in production use proper TOTP verification)
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
        
        // Generate TOTP secret
        String secret = generateTOTPSecret();
        String qrCodeUrl = generateQRCodeUrl(user.getUsername(), secret);
        List<String> backupCodes = generateBackupCodes();
        
        AuthAccount authAccount = user.getAuthAccount();
        authAccount.setTotpSecret(secret);
        authAccountRepository.save(authAccount);
        
        // Store backup codes hashed (simplified)
        
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
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new BusinessException("Invalid refresh token", "INVALID_REFRESH_TOKEN");
        }
        
        String username = jwtProvider.extractUsername(refreshToken);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
        // Validate refresh token in database
        String tokenHash = hashToken(refreshToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new BusinessException("Refresh token not found", "REFRESH_TOKEN_NOT_FOUND"));
        
        if (storedToken.getRevokedAt() != null || storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Refresh token has been revoked or expired", "REFRESH_TOKEN_INVALID");
        }
        
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
        User user = userRepository.findByUsername(request.getUsername())
            .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
        
        // Generate reset token
        String token = generateSecureToken();
        String tokenHash = hashToken(token);
        
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setTokenHash(tokenHash);
        resetToken.setExpiresAt(LocalDateTime.now().plusHours(24));
        
        passwordResetTokenRepository.save(resetToken);
        
        // Send email with token (simplified)
        log.info("Password reset token for user {}: {}", user.getUsername(), token);
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
        
        // Revoke all refresh tokens
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
        
        // Optionally revoke all refresh tokens
        refreshTokenRepository.deleteByUserId(userId);
    }
    
    private AuthResponse generateAuthResponse(User user, UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("businessId", user.getBusinessId());
        claims.put("permissions", getEffectivePermissions(user));
        
        String accessToken = jwtProvider.generateToken(claims, userDetails);
        String refreshToken = jwtProvider.generateRefreshToken(userDetails);
        
        // Store refresh token
        storeRefreshToken(user, refreshToken, null);
        
        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .tokenType("Bearer")
            .expiresIn(86400000L) // 24 hours in milliseconds
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
    
    private void handleFailedLogin(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
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
        return Base64.getEncoder().encodeToString(bytes);
    }
    
    private String generateQRCodeUrl(String username, String secret) {
        // In production, generate proper otpauth:// URL
        return String.format("otpauth://totp/Laundry:%s?secret=%s&issuer=Laundry", username, secret);
    }
    
    private List<String> generateBackupCodes() {
        SecureRandom random = new SecureRandom();
        return java.util.stream.Stream.generate(() -> {
            byte[] bytes = new byte[8];
            random.nextBytes(bytes);
            return Base64.getEncoder().encodeToString(bytes).substring(0, 10);
        }).limit(8).collect(Collectors.toList());
    }
    
    private boolean verifyTOTP(String secret, String code) {
        // In production, implement proper TOTP verification
        return true;
    }
    
    private String generateSecureToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    private String hashToken(String token) {
        // In production, use a proper hash function
        return Base64.getEncoder().encodeToString(token.getBytes());
    }
}
