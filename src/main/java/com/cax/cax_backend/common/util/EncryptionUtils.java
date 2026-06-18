package com.cax.cax_backend.common.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
public class EncryptionUtils {
    private static final String GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128; // in bits

    // Fallback/Legacy details
    private static final byte[] LEGACY_KEY = "CaxGroupChatSecr".getBytes(StandardCharsets.UTF_8);

    // Active key (initialized to SHA-256 of the legacy key as default)
    private static byte[] secretKey;

    static {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            secretKey = sha.digest(LEGACY_KEY);
        } catch (Exception e) {
            log.error("Failed to initialize default secret key", e);
        }
    }

    /**
     * Initialize the active key with a SHA-256 hash of the configured key string to run at AES-256.
     */
    public static void init(String key) {
        if (key == null || key.isBlank()) return;
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            secretKey = sha.digest(key.getBytes(StandardCharsets.UTF_8));
            log.info("EncryptionUtils successfully initialized with custom runtime key");
        } catch (Exception e) {
            throw new RuntimeException("Error initializing encryption key", e);
        }
    }

    /**
     * Encrypt a string using AES-256-GCM. Prepend GCM IV and prefix "v2:".
     */
    public static String encrypt(String value) {
        if (value == null) return null;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            SecretKeySpec keySpec = new SecretKeySpec(secretKey, "AES");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

            // Combine IV + ciphertext
            byte[] encryptedWithIv = new byte[GCM_IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, GCM_IV_LENGTH);
            System.arraycopy(ciphertext, 0, encryptedWithIv, GCM_IV_LENGTH, ciphertext.length);

            return "v2:" + Base64.getEncoder().encodeToString(encryptedWithIv);
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting value", e);
        }
    }

    /**
     * Decrypt a string. Supports GCM decryption for "v2:" prefix, and falls back to legacy AES-ECB
     * decryption or returning the original string if decryption fails.
     */
    public static String decrypt(String encryptedValue) {
        if (encryptedValue == null) return null;
        try {
            if (encryptedValue.startsWith("v2:")) {
                String base64Data = encryptedValue.substring(3);
                byte[] encryptedWithIv = Base64.getDecoder().decode(base64Data);

                byte[] iv = new byte[GCM_IV_LENGTH];
                System.arraycopy(encryptedWithIv, 0, iv, 0, GCM_IV_LENGTH);

                int ciphertextLength = encryptedWithIv.length - GCM_IV_LENGTH;
                byte[] ciphertext = new byte[ciphertextLength];
                System.arraycopy(encryptedWithIv, GCM_IV_LENGTH, ciphertext, 0, ciphertextLength);

                SecretKeySpec keySpec = new SecretKeySpec(secretKey, "AES");
                GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

                Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
                cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

                byte[] decrypted = cipher.doFinal(ciphertext);
                return new String(decrypted, StandardCharsets.UTF_8);
            } else {
                try {
                    // Try legacy AES-ECB decryption using the original legacy key
                    SecretKeySpec keySpec = new SecretKeySpec(LEGACY_KEY, "AES");
                    Cipher cipher = Cipher.getInstance("AES");
                    cipher.init(Cipher.DECRYPT_MODE, keySpec);
                    byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedValue));
                    return new String(decrypted, StandardCharsets.UTF_8);
                } catch (Exception ex) {
                    // If Base64 decoding or legacy decryption fails, it is likely plain text. Return as-is.
                    return encryptedValue;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to decrypt string, returning original value: {}", e.getMessage());
            return encryptedValue;
        }
    }

    /**
     * Securely hash a string using SHA-256.
     */
    public static String hashSHA256(String value) {
        if (value == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error hashing value", e);
        }
    }
}
