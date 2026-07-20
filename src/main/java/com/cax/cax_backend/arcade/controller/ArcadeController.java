package com.cax.cax_backend.arcade.controller;

import com.cax.cax_backend.arcade.dto.ArcadeHistoryDtos;
import com.cax.cax_backend.arcade.dto.ArcadeRequests;
import com.cax.cax_backend.arcade.dto.ArcadeStateResponse;
import com.cax.cax_backend.arcade.model.ArcadeSession;
import com.cax.cax_backend.arcade.service.ArcadeHistoryService;
import com.cax.cax_backend.arcade.service.ArcadeSessionService;
import com.cax.cax_backend.arcade.service.ArcadeStateService;
import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * The Arcade API.
 *
 * <p>Every route here is authenticated — unlike Bingo, there is no public read surface. The
 * games are built on hidden information, so an unauthenticated spectator endpoint would be a
 * way to watch a round you are not entitled to see, and a code is a way to find a game rather
 * than permission to observe one.
 *
 * <p>Two conventions run through the whole controller. Identity always comes from the token
 * and never from the path or body, so no request can be made on another player's behalf. And
 * no write endpoint returns state — clients re-poll {@code /state} afterwards, which keeps a
 * single code path responsible for deciding what each viewer may see.
 */
@RestController
@RequestMapping("/api/arcade")
@RequiredArgsConstructor
public class ArcadeController {

    private final ArcadeSessionService sessionService;
    private final ArcadeStateService stateService;
    private final ArcadeHistoryService historyService;

    // ── Hub ───────────────────────────────────────────────────────────────────

    /** Unfinished games the caller can jump back into. */
    @GetMapping("/sessions/active")
    public ResponseEntity<ApiResponse<List<ArcadeHistoryDtos.ActiveSessionCard>>> activeSessions(
            Authentication auth) {
        String userId = requireUserId(auth);
        String caxId = sessionService.requireUser(userId).getCaxId();
        return ResponseEntity.ok(ApiResponse.success(historyService.activeSessionsFor(caxId, userId)));
    }

    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<Map<String, String>>> create(
            @RequestBody ArcadeRequests.CreateSession req, Authentication auth) {
        ArcadeSession session = sessionService.createSession(req, requireUserId(auth));
        // Only the code comes back: the client then polls /state like everyone else, so the
        // host has no privileged view built on a different code path.
        return ResponseEntity.ok(ApiResponse.created("Game created",
                Map.of("gameCode", session.getGameCode())));
    }

    @PostMapping("/sessions/{gameCode}/join")
    public ResponseEntity<ApiResponse<Map<String, String>>> join(
            @PathVariable String gameCode, Authentication auth) {
        ArcadeSession session = sessionService.joinSession(gameCode, requireUserId(auth));
        return ResponseEntity.ok(ApiResponse.success("Joined",
                Map.of("gameCode", session.getGameCode(),
                        "gameType", session.getGameType().name())));
    }

    @PostMapping("/sessions/{gameCode}/leave")
    public ResponseEntity<ApiResponse<String>> leave(
            @PathVariable String gameCode, Authentication auth) {
        sessionService.leaveSession(gameCode, requireUserId(auth));
        return ResponseEntity.ok(ApiResponse.success("Left the game"));
    }

    // ── Live play ─────────────────────────────────────────────────────────────

    /**
     * The polling endpoint. Pass the last {@code stateVersion} rendered and an unchanged
     * session answers with {@code null} data — the payload most polls return, since a lobby
     * waiting on a slow typist changes nothing for seconds at a time.
     *
     * <p>Polling here is also what refreshes presence and drives phase transitions, so a client
     * that stops polling correctly reads as gone.
     */
    @GetMapping("/sessions/{gameCode}/state")
    public ResponseEntity<ApiResponse<ArcadeStateResponse>> state(
            @PathVariable String gameCode,
            @RequestParam(required = false) Long since,
            Authentication auth) {

        ArcadeStateResponse state = stateService.buildFor(gameCode, requireUserId(auth));
        if (since != null && state.getStateVersion() == since) {
            return ResponseEntity.ok(ApiResponse.success(null));
        }
        return ResponseEntity.ok(ApiResponse.success(state));
    }

    @PostMapping("/sessions/{gameCode}/start")
    public ResponseEntity<ApiResponse<String>> start(
            @PathVariable String gameCode, Authentication auth) {
        sessionService.startSession(gameCode, requireUserId(auth));
        return ResponseEntity.ok(ApiResponse.success("Game started"));
    }

    @PostMapping("/sessions/{gameCode}/submit")
    public ResponseEntity<ApiResponse<String>> submit(
            @PathVariable String gameCode,
            @RequestBody ArcadeRequests.Submit req,
            Authentication auth) {
        sessionService.submit(gameCode, requireUserId(auth), req);
        return ResponseEntity.ok(ApiResponse.success("Locked in"));
    }

    @PostMapping("/sessions/{gameCode}/vote")
    public ResponseEntity<ApiResponse<String>> vote(
            @PathVariable String gameCode,
            @RequestBody ArcadeRequests.Vote req,
            Authentication auth) {
        sessionService.vote(gameCode, requireUserId(auth), req);
        return ResponseEntity.ok(ApiResponse.success("Vote counted"));
    }

    /** The between-rounds gate: tells the server this player is back and ready to continue. */
    @PostMapping("/sessions/{gameCode}/ready")
    public ResponseEntity<ApiResponse<String>> ready(
            @PathVariable String gameCode, Authentication auth) {
        sessionService.markReady(gameCode, requireUserId(auth));
        return ResponseEntity.ok(ApiResponse.success("Ready"));
    }

    @PostMapping("/sessions/{gameCode}/end")
    public ResponseEntity<ApiResponse<String>> end(
            @PathVariable String gameCode, Authentication auth) {
        sessionService.endSession(gameCode, requireUserId(auth));
        return ResponseEntity.ok(ApiResponse.success("Game ended"));
    }

    // ── History ───────────────────────────────────────────────────────────────

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ArcadeHistoryDtos.HistoryEntry>>> history(
            @RequestParam(defaultValue = "20") int limit, Authentication auth) {
        String caxId = sessionService.requireUser(requireUserId(auth)).getCaxId();
        return ResponseEntity.ok(ApiResponse.success(historyService.historyFor(caxId, limit)));
    }

    @GetMapping("/history/{sessionId}")
    public ResponseEntity<ApiResponse<ArcadeHistoryDtos.HistoryEntry>> historyDetail(
            @PathVariable String sessionId, Authentication auth) {
        String caxId = sessionService.requireUser(requireUserId(auth)).getCaxId();
        return ResponseEntity.ok(ApiResponse.success(historyService.detailFor(sessionId, caxId)));
    }

    /**
     * Lifetime stats for the caller. There is deliberately no variant of this that takes
     * another player's id.
     */
    @GetMapping("/stats/me")
    public ResponseEntity<ApiResponse<ArcadeHistoryDtos.UserStats>> myStats(Authentication auth) {
        String caxId = sessionService.requireUser(requireUserId(auth)).getCaxId();
        return ResponseEntity.ok(ApiResponse.success(historyService.statsFor(caxId)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * The caller's internal userId, taken from the verified JWT.
     *
     * <p>This is the only source of identity in the Arcade. It throws rather than returning
     * null so that a misconfigured filter can never let a request through as an anonymous
     * player instead of failing loudly.
     */
    private String requireUserId(Authentication auth) {
        if (auth == null || auth.getCredentials() == null) {
            throw new BusinessException.ForbiddenException("Please sign in to play");
        }
        Claims claims = (Claims) auth.getCredentials();
        String userId = claims.getSubject();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException.ForbiddenException("Please sign in to play");
        }
        return userId;
    }
}
