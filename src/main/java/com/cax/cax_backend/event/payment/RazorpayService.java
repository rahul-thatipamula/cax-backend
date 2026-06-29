package com.cax.cax_backend.event.payment;

import com.cax.cax_backend.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class RazorpayService {

    @Value("${razorpay.key-id}")
    private String keyId;

    @Value("${razorpay.key-secret}")
    private String keySecret;

    private static final String RAZORPAY_ORDERS_URL = "https://api.razorpay.com/v1/orders";

    /**
     * Creates a Razorpay order and returns the raw response map.
     * @param amountInPaise amount in smallest currency unit (paise for INR)
     * @param receipt       unique receipt identifier (max 40 chars)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> createOrder(long amountInPaise, String receipt) {
        if (amountInPaise < 100) {
            throw new BusinessException.BadRequestException("Minimum payable amount is ₹1 (100 paise).");
        }

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(keyId, keySecret);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("amount", amountInPaise);
        body.put("currency", "INR");
        body.put("receipt", receipt.length() > 40 ? receipt.substring(0, 40) : receipt);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            Map<String, Object> response = restTemplate.postForObject(RAZORPAY_ORDERS_URL, request, Map.class);
            if (response == null) {
                throw new BusinessException.BadRequestException("Empty response from Razorpay.");
            }
            return response;
        } catch (BusinessException.BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Razorpay create-order failed", e);
            throw new BusinessException.BadRequestException("Failed to create Razorpay order: " + e.getMessage());
        }
    }

    /**
     * Returns the public key ID (safe to send to clients).
     */
    public String getKeyId() {
        return keyId;
    }

    /**
     * Verifies the Razorpay payment signature.
     * Algorithm: HMAC-SHA256(orderId + "|" + paymentId, keySecret)
     */
    public boolean verifySignature(String orderId, String paymentId, String signature) {
        try {
            String payload = orderId + "|" + paymentId;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String generated = HexFormat.of().formatHex(hmacBytes);
            return generated.equals(signature);
        } catch (Exception e) {
            log.error("Razorpay signature verification error", e);
            return false;
        }
    }
}
