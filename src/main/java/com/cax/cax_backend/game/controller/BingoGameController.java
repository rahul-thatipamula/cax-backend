package com.cax.cax_backend.game.controller;

import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.game.dto.BingoGameResponse;
import com.cax.cax_backend.game.dto.BingoLeaderboardEntry;
import com.cax.cax_backend.game.dto.CreateBingoGameRequest;
import com.cax.cax_backend.game.dto.MarkCellRequest;
import com.cax.cax_backend.game.dto.SignerUsageStat;
import com.cax.cax_backend.game.model.BingoGame;
import com.cax.cax_backend.game.model.BingoGamePrompt;
import com.cax.cax_backend.game.model.BingoPlayerCard;
import com.cax.cax_backend.game.service.BingoGamePromptService;
import com.cax.cax_backend.game.service.BingoGameService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/games/bingo")
@RequiredArgsConstructor
public class BingoGameController {

    private final BingoGameService bingoGameService;
    private final BingoGamePromptService bingoGamePromptService;

    // ── Prompt bank (random import for game creation) ─────────────────────────

    @GetMapping("/prompts/random")
    public ResponseEntity<ApiResponse<List<BingoGamePrompt>>> getRandomPrompts(
            @RequestParam(defaultValue = "10") int count) {
        return ResponseEntity.ok(ApiResponse.success(bingoGamePromptService.getRandomPrompts(count)));
    }

    // ── Org-leader management (President / VP only — enforced in service) ─────

    @PostMapping
    public ResponseEntity<ApiResponse<BingoGameResponse>> createGame(
            @RequestBody CreateBingoGameRequest req,
            Authentication auth) {
        String userId = getCallerId(auth);
        BingoGame game = bingoGameService.createGame(req, userId);
        long count = bingoGameService.getPlayerCount(game.getGameCode());
        return ResponseEntity.ok(ApiResponse.success(BingoGameResponse.of(game, count)));
    }

    @GetMapping("/org/{organizationId}")
    public ResponseEntity<ApiResponse<List<BingoGameResponse>>> getOrgGames(
            @PathVariable String organizationId,
            Authentication auth) {
        String userId = getCallerId(auth);
        List<BingoGame> games = bingoGameService.getGamesByOrganization(organizationId, userId);
        List<BingoGameResponse> response = games.stream()
                .map(g -> BingoGameResponse.of(g, bingoGameService.getPlayerCount(g.getGameCode())))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{gameCode}/start")
    public ResponseEntity<ApiResponse<BingoGameResponse>> startGame(
            @PathVariable String gameCode,
            Authentication auth) {
        String userId = getCallerId(auth);
        BingoGame game = bingoGameService.startGame(gameCode, userId);
        long count = bingoGameService.getPlayerCount(gameCode);
        return ResponseEntity.ok(ApiResponse.success(BingoGameResponse.of(game, count)));
    }

    @PostMapping("/{gameCode}/end")
    public ResponseEntity<ApiResponse<BingoGameResponse>> endGame(
            @PathVariable String gameCode,
            Authentication auth) {
        String userId = getCallerId(auth);
        BingoGame game = bingoGameService.endGame(gameCode, userId);
        long count = bingoGameService.getPlayerCount(gameCode);
        return ResponseEntity.ok(ApiResponse.success(BingoGameResponse.of(game, count)));
    }

    // ── Public read-only endpoints (no auth required) ────────────────────────

    @GetMapping("/public/{gameCode}")
    public ResponseEntity<ApiResponse<BingoGameResponse>> getGame(@PathVariable String gameCode) {
        BingoGame game = bingoGameService.getGame(gameCode);
        long count = bingoGameService.getPlayerCount(gameCode);
        return ResponseEntity.ok(ApiResponse.success(BingoGameResponse.of(game, count)));
    }

    @GetMapping("/public/{gameCode}/player/{caxId}")
    public ResponseEntity<ApiResponse<BingoPlayerCard>> getPlayerCard(
            @PathVariable String gameCode,
            @PathVariable String caxId) {
        return ResponseEntity.ok(ApiResponse.success(bingoGameService.getPlayerCard(gameCode, caxId)));
    }

    @GetMapping("/public/{gameCode}/leaderboard")
    public ResponseEntity<ApiResponse<List<BingoLeaderboardEntry>>> getLeaderboard(
            @PathVariable String gameCode) {
        return ResponseEntity.ok(ApiResponse.success(bingoGameService.getLeaderboard(gameCode)));
    }

    @GetMapping("/public/{gameCode}/signer-usage")
    public ResponseEntity<ApiResponse<List<SignerUsageStat>>> getSignerUsage(
            @PathVariable String gameCode) {
        return ResponseEntity.ok(ApiResponse.success(bingoGameService.getSignerUsageStats(gameCode)));
    }

    // ── Authenticated player write endpoints ─────────────────────────────────
    // caxId is derived from the JWT token — callers cannot impersonate other users

    @GetMapping("/player/games")
    public ResponseEntity<ApiResponse<List<BingoGameResponse>>> getPlayerGames(Authentication auth) {
        List<BingoGame> games = bingoGameService.getVisibleGames(getCallerId(auth));
        List<BingoGameResponse> response = games.stream()
                .map(g -> BingoGameResponse.of(g, bingoGameService.getPlayerCount(g.getGameCode())))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/player/{gameCode}/join")
    public ResponseEntity<ApiResponse<BingoPlayerCard>> joinGame(
            @PathVariable String gameCode,
            Authentication auth) {
        String caxId = resolveCaxId(auth);
        return ResponseEntity.ok(ApiResponse.success(bingoGameService.joinGame(gameCode, caxId)));
    }

    @PostMapping("/player/{gameCode}/mark")
    public ResponseEntity<ApiResponse<BingoPlayerCard>> markCell(
            @PathVariable String gameCode,
            @RequestBody MarkCellRequest req,
            Authentication auth) {
        String caxId = resolveCaxId(auth);
        return ResponseEntity.ok(ApiResponse.success(bingoGameService.markCell(gameCode, caxId, req)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getCallerId(Authentication auth) {
        if (auth == null || auth.getCredentials() == null) return null;
        Claims claims = (Claims) auth.getCredentials();
        return claims.getSubject();
    }

    /** Resolves the caller's caxId from their JWT → userId → User record. */
    private String resolveCaxId(Authentication auth) {
        String userId = getCallerId(auth);
        return bingoGameService.getCaxIdByUserId(userId);
    }
}
