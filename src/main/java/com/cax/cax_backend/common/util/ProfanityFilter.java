package com.cax.cax_backend.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Blocks post content that matches known bad words/phrases across multiple languages.
 * Word lists are sourced per-language (English, Hindi, Telugu, Marathi) plus a small
 * hand-curated Hinglish supplement, and are checked together regardless of the
 * language the post is actually written in.
 */
@Slf4j
public class ProfanityFilter {

    private static final String[] WORD_LIST_FILES = {
        "profanity/en.txt", "profanity/hi.txt", "profanity/te.txt", "profanity/mr.txt"
    };

    // Single-token entries, matched by exact word after normalization.
    private static final Set<String> SINGLE_WORDS = new HashSet<>();
    // Multi-word entries (e.g. "axe wound"), matched by substring on the normalized, space-padded text.
    private static final List<String> PHRASES = new ArrayList<>();

    static {
        for (String file : WORD_LIST_FILES) {
            loadWordList(file);
        }
        log.info("ProfanityFilter loaded {} single words and {} phrases from {} lists",
                SINGLE_WORDS.size(), PHRASES.size(), WORD_LIST_FILES.length);
    }

    private static void loadWordList(String classpathFile) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(classpathFile).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String entry = line.trim().toLowerCase();
                if (entry.isEmpty()) continue;
                if (entry.contains(" ") || entry.contains("-")) {
                    PHRASES.add(entry.replace('-', ' ').replaceAll("\\s+", " "));
                } else {
                    SINGLE_WORDS.add(entry);
                }
            }
        } catch (IOException e) {
            log.error("Failed to load profanity word list: {}", classpathFile, e);
        }
    }

    // Common leetspeak/symbol substitutions used to dodge a plain word match.
    private static final char[][] SUBSTITUTIONS = {
        {'@', 'a'}, {'4', 'a'}, {'3', 'e'}, {'1', 'i'}, {'!', 'i'}, {'0', 'o'}, {'$', 's'}, {'5', 's'}, {'7', 't'}
    };

    public static boolean isOffensive(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase();
        for (char[] sub : SUBSTITUTIONS) {
            normalized = normalized.replace(sub[0], sub[1]);
        }
        normalized = normalized.replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();

        String[] words = normalized.split(" ");
        for (String word : words) {
            // Collapse runs of 3+ repeated letters (e.g. "fuuuck" -> "fuck") to catch stretched-out spelling.
            String collapsed = word.replaceAll("(.)\\1{2,}", "$1$1");
            if (SINGLE_WORDS.contains(word) || SINGLE_WORDS.contains(collapsed)) {
                return true;
            }
        }

        String padded = " " + normalized + " ";
        for (String phrase : PHRASES) {
            if (padded.contains(" " + phrase + " ")) {
                return true;
            }
        }
        return false;
    }
}
