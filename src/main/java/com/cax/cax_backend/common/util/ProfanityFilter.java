package com.cax.cax_backend.common.util;

import java.util.Set;

public class ProfanityFilter {
    private static final Set<String> BLACKLIST = Set.of(
        "abuse", "asshole", "bitch", "bastard", "cunt", "dick", "fuck", "nigger", "pussy", "shit", "slut", "whore"
        // We can add more common profanities
    );

    public static boolean isOffensive(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase().replaceAll("[^a-zA-Z0-9\\s]", "");
        String[] words = normalized.split("\\s+");
        for (String word : words) {
            if (BLACKLIST.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
