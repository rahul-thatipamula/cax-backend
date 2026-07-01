package com.cax.cax_backend.common.util;

import java.util.Random;

public class PseudonymUtils {
    private static final String[] ADJECTIVES = {
        "creative", "curious", "friendly", "happy", "clever", 
        "brave", "calm", "gentle", "bright", "swift", 
        "wise", "bold", "active", "sharp", "jolly",
        "kind", "lively", "warm", "loyal", "eager"
    };

    private static final String[] NOUNS = {
        "owl", "fox", "panda", "koala", "dolphin", 
        "tiger", "falcon", "eagle", "beaver", "badger", 
        "squirrel", "otter", "rabbit", "lion", "panther",
        "wolf", "deer", "leopard", "cheetah", "jaguar"
    };

    public static String generatePseudonym(String userId) {
        if (userId == null || userId.isBlank()) {
            return "anon_student_" + (int)(Math.random() * 900 + 100);
        }
        int hash = userId.hashCode();
        String adjective = ADJECTIVES[Math.abs(hash) % ADJECTIVES.length];
        String noun = NOUNS[Math.abs(hash) % NOUNS.length];
        int idNum = (Math.abs(hash) % 900) + 100;
        return adjective + "_" + noun + "_" + idNum;
    }

    public static String generateRandomNickname() {
        String chars = "abcdefghijklmnopqrstuvwxyz";
        String extraChars = "abcdefghijklmnopqrstuvwxyz0123456789_.";
        Random rnd = new Random();
        
        int length = rnd.nextInt(8) + 8; // length 8 to 15
        StringBuilder sb = new StringBuilder();
        sb.append(chars.charAt(rnd.nextInt(chars.length())));
        
        for (int i = 1; i < length; i++) {
            sb.append(extraChars.charAt(rnd.nextInt(extraChars.length())));
        }
        
        return sb.toString();
    }

    public static boolean isValidNickname(String nickname) {
        if (nickname == null || nickname.length() < 8 || nickname.length() > 16) {
            return false;
        }
        return nickname.matches("^[a-zA-Z0-9_.]+$");
    }
}
