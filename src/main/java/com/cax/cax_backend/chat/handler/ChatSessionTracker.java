package com.cax.cax_backend.chat.handler;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatSessionTracker {

    // Map: clubId -> Map: userId -> Set of sessionIds
    private final Map<String, Map<String, Set<String>>> activeUsersInClubs = new ConcurrentHashMap<>();

    public void registerSession(String userId, String clubId, String sessionId) {
        if (userId == null || clubId == null || sessionId == null) {
            return;
        }
        activeUsersInClubs
                .computeIfAbsent(clubId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                .add(sessionId);
    }

    public void deregisterSession(String userId, String clubId, String sessionId) {
        if (userId == null || clubId == null || sessionId == null) {
            return;
        }
        Map<String, Set<String>> userSessions = activeUsersInClubs.get(clubId);
        if (userSessions != null) {
            Set<String> sessions = userSessions.get(userId);
            if (sessions != null) {
                sessions.remove(sessionId);
                if (sessions.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
            if (userSessions.isEmpty()) {
                activeUsersInClubs.remove(clubId);
            }
        }
    }

    public boolean isUserActiveInClub(String userId, String clubId) {
        if (userId == null || clubId == null) {
            return false;
        }
        Map<String, Set<String>> userSessions = activeUsersInClubs.get(clubId);
        if (userSessions == null) {
            return false;
        }
        Set<String> sessions = userSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }
}
