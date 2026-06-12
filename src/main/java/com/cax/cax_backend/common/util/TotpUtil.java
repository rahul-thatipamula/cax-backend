package com.cax.cax_backend.common.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

public class TotpUtil {

    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    public static byte[] decodeBase32(String base32) {
        base32 = base32.toUpperCase().replaceAll("[^A-Z2-7]", "");
        int len = base32.length();
        byte[] bytes = new byte[len * 5 / 8];
        int val = 0;
        int bits = 0;
        int index = 0;
        for (int i = 0; i < len; i++) {
            val = (val << 5) | BASE32_CHARS.indexOf(base32.charAt(i));
            bits += 5;
            if (bits >= 8) {
                bytes[index++] = (byte) ((val >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return bytes;
    }

    public static String generateSecretKey() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(BASE32_CHARS.charAt(random.nextInt(BASE32_CHARS.length())));
        }
        return sb.toString();
    }

    public static boolean verifyCode(String secret, String codeStr, int window) {
        try {
            int code = Integer.parseInt(codeStr);
            long timeIndex = System.currentTimeMillis() / 1000 / 30;
            byte[] secretBytes = decodeBase32(secret);
            for (int i = -window; i <= window; i++) {
                if (getOTP(secretBytes, timeIndex + i) == code) {
                    return true;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    private static int getOTP(byte[] key, long time) throws GeneralSecurityException {
        byte[] data = ByteBuffer.allocate(8).putLong(time).array();
        SecretKeySpec signKey = new SecretKeySpec(key, "HmacSHA1");
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(signKey);
        byte[] hash = mac.doFinal(data);
        int offset = hash[hash.length - 1] & 0xF;
        long truncatedHash = 0;
        for (int i = 0; i < 4; ++i) {
            truncatedHash <<= 8;
            truncatedHash |= (hash[offset + i] & 0xFF);
        }
        truncatedHash &= 0x7FFFFFFF;
        truncatedHash %= 1000000;
        return (int) truncatedHash;
    }

    public static String getQrCodeUrl(String email, String secret, String issuer) {
        return "otpauth://totp/" + issuer + ":" + email + "?secret=" + secret + "&issuer=" + issuer;
    }
}
