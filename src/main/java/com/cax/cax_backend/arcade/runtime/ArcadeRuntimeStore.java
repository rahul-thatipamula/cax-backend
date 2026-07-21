package com.cax.cax_backend.arcade.runtime;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of live {@link ArcadeSessionRuntime}s, keyed by session id.
 *
 * <p>In-memory only and single-instance: the same reason the STOMP broker is the built-in
 * simple broker (no Redis/RabbitMQ configured), so all clients of one game must be served by
 * one backend instance for this to be coherent.
 */
@Component
public class ArcadeRuntimeStore {

    private final Map<String, ArcadeSessionRuntime> runtimes = new ConcurrentHashMap<>();

    public ArcadeSessionRuntime get(String sessionId) {
        return runtimes.computeIfAbsent(sessionId, k -> new ArcadeSessionRuntime());
    }

    /** Frees a finished session's live state. */
    public void discard(String sessionId) {
        runtimes.remove(sessionId);
    }
}
