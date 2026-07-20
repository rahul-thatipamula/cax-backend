package com.cax.cax_backend.common.turnstile;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/** Verifies Cloudflare Turnstile CAPTCHA tokens for public, unauthenticated write
 *  endpoints (currently just the bulletin-event submission form). */
@Slf4j
@Service
public class TurnstileService {

    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    @Value("${turnstile.secret-key:}")
    private String secretKey;

    /** Escape hatch for local dev without Cloudflare keys. Must be set deliberately —
     *  an unset/misconfigured secret in production fails closed instead of silently
     *  turning the public submit endpoint into an open, un-CAPTCHA'd relay. */
    @Value("${turnstile.allow-insecure-skip:false}")
    private boolean allowInsecureSkip;

    /** Returns true if the token is valid. With no secret configured this returns false
     *  (rejecting the submission) unless turnstile.allow-insecure-skip is explicitly on. */
    public boolean verify(String token, String remoteIp) {
        if (secretKey == null || secretKey.isBlank()) {
            if (allowInsecureSkip) {
                log.warn("TURNSTILE_SECRET_KEY not configured and allow-insecure-skip=true "
                        + "— CAPTCHA verification SKIPPED. Never run this way in production.");
                return true;
            }
            log.error("TURNSTILE_SECRET_KEY not configured — rejecting public submission. "
                    + "Set TURNSTILE_SECRET_KEY, or TURNSTILE_ALLOW_INSECURE_SKIP=true for local dev.");
            return false;
        }
        if (token == null || token.isBlank()) {
            return false;
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("secret", secretKey);
            form.add("response", token);
            if (remoteIp != null && !remoteIp.isBlank()) {
                form.add("remoteip", remoteIp);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(VERIFY_URL, form, Map.class);
            return response != null && Boolean.TRUE.equals(response.get("success"));
        } catch (Exception e) {
            log.error("Turnstile verification request failed: {}", e.getMessage());
            return false;
        }
    }
}
