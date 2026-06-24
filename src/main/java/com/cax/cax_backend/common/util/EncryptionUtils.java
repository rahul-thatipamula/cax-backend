package com.cax.cax_backend.common.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
public class EncryptionUtils {
    private static final String GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private static byte[] secretKey;

    public static void init(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY must not be blank");
        }
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            secretKey = sha.digest(key.getBytes(StandardCharsets.UTF_8));
            log.info("EncryptionUtils initialized with configured key");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to initialize encryption key", e);
        }
    }

    public static String encryptIfNeeded(String value) {
        if (value == null) return null;
        if (value.startsWith("v2:")) return value;
        return encrypt(value);
    }

    public static String encrypt(String value) {
        if (value == null) return null;
        requireInitialized();
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            SecretKeySpec keySpec = new SecretKeySpec(secretKey, "AES");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

            byte[] encryptedWithIv = new byte[GCM_IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, GCM_IV_LENGTH);
            System.arraycopy(ciphertext, 0, encryptedWithIv, GCM_IV_LENGTH, ciphertext.length);

            return "v2:" + Base64.getEncoder().encodeToString(encryptedWithIv);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public static String decrypt(String encryptedValue) {
        if (encryptedValue == null) return null;
        requireInitialized();

        if (!encryptedValue.startsWith("v2:")) {
            // Plain/legacy unencrypted value — return as-is so old data still works
            log.warn("decrypt called on plain-text value (no v2: prefix) — returning as-is");
            return encryptedValue;
        }

        byte[] encryptedWithIv;
        try {
            encryptedWithIv = Base64.getDecoder().decode(encryptedValue.substring(3));
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Decryption failed: v2 value contains malformed Base64", e);
        }
        if (encryptedWithIv.length <= GCM_IV_LENGTH) {
            throw new RuntimeException("Decryption failed: v2 value is too short to contain IV and ciphertext (got "
                    + encryptedWithIv.length + " bytes, need >" + GCM_IV_LENGTH + ")");
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(encryptedWithIv, 0, iv, 0, GCM_IV_LENGTH);

            byte[] ciphertext = new byte[encryptedWithIv.length - GCM_IV_LENGTH];
            System.arraycopy(encryptedWithIv, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            SecretKeySpec keySpec = new SecretKeySpec(secretKey, "AES");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("GCM decryption failed", e);
        }
    }

    private static void requireInitialized() {
        if (secretKey == null) {
            throw new IllegalStateException("EncryptionUtils not initialized — APP_ENCRYPTION_KEY must be set");
        }
    }

    public static String hashSHA256(String value) {
        if (value == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 hashing failed", e);
        }
    }
}
