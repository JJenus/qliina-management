package com.jjenus.qliina_management.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * Service for encrypting and decrypting sensitive data.
 * Uses AES-GCM for authenticated encryption with PBKDF2 for key derivation.
 */
@Slf4j
@Service
public class EncryptionService {
    
    private final EncryptionProperties properties;
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final String KEY_FACTORY_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    
    public EncryptionService(EncryptionProperties properties) {
        this.properties = properties;
    }
    
    /**
     * Encrypts a plaintext string using AES-GCM.
     * 
     * @param plaintext The text to encrypt
     * @return Base64-encoded encrypted data with IV prepended
     * @throws EncryptionException if encryption fails
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        
        try {
            SecretKey key = deriveKey(properties.getSecret(), properties.getSalt());
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            
            byte[] iv = generateIv();
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);
            
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());
            return encode(iv, ciphertext);
            
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new EncryptionException("Failed to encrypt data", e);
        }
    }
    
    /**
     * Decrypts an encrypted string.
     * 
     * @param encryptedText Base64-encoded encrypted data with IV prepended
     * @return Decrypted plaintext
     * @throws EncryptionException if decryption fails
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null) return null;
        
        try {
            SecretKey key = deriveKey(properties.getSecret(), properties.getSalt());
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            
            byte[] decoded = Base64.getDecoder().decode(encryptedText);
            byte[] iv = extractIv(decoded);
            byte[] ciphertext = extractCiphertext(decoded);
            
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);
            
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext);
            
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new EncryptionException("Failed to decrypt data", e);
        }
    }
    
    /**
     * Generates a cryptographically secure random IV.
     */
    private byte[] generateIv() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }
    
    /**
     * Derives an AES key from password and salt using PBKDF2.
     */
    private SecretKey deriveKey(String secret, String salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_FACTORY_ALGORITHM);
        KeySpec spec = new PBEKeySpec(
            secret.toCharArray(), 
            salt.getBytes(), 
            properties.getIterationCount(), 
            properties.getKeyLength()
        );
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    }
    
    /**
     * Encodes IV and ciphertext into a single Base64 string.
     * Format: [IV (12 bytes)][Ciphertext (variable)]
     */
    private String encode(byte[] iv, byte[] ciphertext) {
        byte[] encrypted = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, encrypted, 0, iv.length);
        System.arraycopy(ciphertext, 0, encrypted, iv.length, ciphertext.length);
        return Base64.getEncoder().encodeToString(encrypted);
    }
    
    /**
     * Extracts IV from the combined encrypted data.
     */
    private byte[] extractIv(byte[] encrypted) {
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(encrypted, 0, iv, 0, iv.length);
        return iv;
    }
    
    /**
     * Extracts ciphertext from the combined encrypted data.
     */
    private byte[] extractCiphertext(byte[] encrypted) {
        byte[] ciphertext = new byte[encrypted.length - GCM_IV_LENGTH];
        System.arraycopy(encrypted, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);
        return ciphertext;
    }
    
    /**
     * Custom exception for encryption errors.
     */
    public static class EncryptionException extends RuntimeException {
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}