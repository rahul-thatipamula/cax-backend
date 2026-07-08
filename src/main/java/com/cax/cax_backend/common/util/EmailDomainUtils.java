package com.cax.cax_backend.common.util;

import java.util.Set;

/**
 * Shared blocklist of consumer email providers. A domain not in this set is treated as a
 * potential college domain — colleges are validated against their registered
 * {@code College.emailDomains}, not against a hardcoded academic suffix (many colleges use
 * domains that don't end in .edu/.ac.in/.edu.in).
 */
public final class EmailDomainUtils {

    private EmailDomainUtils() {
    }

    private static final Set<String> PERSONAL_EMAIL_DOMAINS = Set.of(
        "gmail.com", "googlemail.com",
        "yahoo.com", "yahoo.co.in", "yahoo.in", "yahoo.co.uk", "ymail.com", "rocketmail.com",
        "hotmail.com", "hotmail.co.in", "hotmail.co.uk",
        "outlook.com", "outlook.in", "live.com", "msn.com",
        "icloud.com", "me.com", "mac.com",
        "aol.com",
        "protonmail.com", "proton.me", "pm.me",
        "rediffmail.com",
        "mail.com",
        "zoho.com",
        "tutanota.com",
        "fastmail.com",
        "inbox.com",
        "yandex.com", "yandex.ru"
    );

    public static boolean isPersonalEmailDomain(String domain) {
        if (domain == null || domain.isBlank()) return true;
        return PERSONAL_EMAIL_DOMAINS.contains(domain.toLowerCase().trim());
    }

    public static String extractDomain(String email) {
        if (email == null) return "";
        int atIndex = email.indexOf('@');
        return atIndex != -1 ? email.substring(atIndex + 1) : "";
    }
}
