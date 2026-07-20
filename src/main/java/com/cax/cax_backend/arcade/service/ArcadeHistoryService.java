package com.cax.cax_backend.arcade.service;

import com.cax.cax_backend.arcade.dto.ArcadeHistoryDtos;
import com.cax.cax_backend.arcade.dto.ArcadeStateResponse;
import com.cax.cax_backend.arcade.model.*;
import com.cax.cax_backend.arcade.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Read-only views over finished and in-flight games: the hub's "resume this" cards, the
 * played-games history, and lifetime per-user stats.
 *
 * <p>Everything here is scoped to the caller. A player can read the history of games they took
 * part in and nothing else — there is no endpoint that takes someone else's caxId, so browsing
 * another student's game history is not something the UI is choosing not to offer, it is
 * something the API cannot express.
 */
@Service
@RequiredArgsConstructor
public class ArcadeHistoryService {

    private final ArcadeSessionRepository sessionRepository;
    private final ArcadeParticipantRepository participantRepository;
    private final ArcadeResultRepository resultRepository;

    private static final int MAX_HISTORY_PAGE = 50;

    /**
     * Unfinished games the caller is part of, so someone who closed the app mid-game finds it
     * waiting for them rather than having to remember a six-character code.
     */
    public List<ArcadeHistoryDtos.ActiveSessionCard> activeSessionsFor(String caxId, String userId) {
        List<ArcadeParticipant> memberships = participantRepository.findByCaxIdOrderByJoinedAtDesc(caxId);
        if (memberships.isEmpty()) return List.of();

        Map<String, ArcadeParticipant> bySession = new LinkedHashMap<>();
        for (ArcadeParticipant p : memberships) bySession.put(p.getSessionId(), p);

        List<ArcadeSession> sessions = sessionRepository.findByIdIn(bySession.keySet());
        Instant now = Instant.now();
        List<ArcadeHistoryDtos.ActiveSessionCard> cards = new ArrayList<>();

        for (ArcadeSession session : sessions) {
            if (session.isFinished()) continue;

            List<ArcadeParticipant> players = participantRepository.findBySessionId(session.getId());
            int present = 0;
            for (ArcadeParticipant p : players) {
                if (p.isPresent(now, ArcadeSessionService.PRESENCE_WINDOW_SECONDS)) present++;
            }

            ArcadeParticipant me = bySession.get(session.getId());
            cards.add(ArcadeHistoryDtos.ActiveSessionCard.builder()
                    .gameCode(session.getGameCode())
                    .gameType(session.getGameType())
                    .phase(session.getPhase())
                    .currentRound(session.getCurrentRound())
                    .totalRounds(session.getTotalRounds())
                    .playerCount(players.size())
                    .presentCount(present)
                    .viewerIsHost(session.getHostUserId().equals(userId))
                    .canRejoin(!me.isPresent(now, ArcadeSessionService.PRESENCE_WINDOW_SECONDS))
                    .hostName(session.getHostName())
                    .createdAt(session.getCreatedAt())
                    .build());
        }

        cards.sort(Comparator.comparing(ArcadeHistoryDtos.ActiveSessionCard::getCreatedAt).reversed());
        return cards;
    }

    /** Finished games the caller played, newest first. */
    public List<ArcadeHistoryDtos.HistoryEntry> historyFor(String caxId, int limit) {
        int size = Math.max(1, Math.min(MAX_HISTORY_PAGE, limit));
        List<ArcadeResult> results = resultRepository
                .findByParticipantCaxIdsContainsOrderByEndedAtDesc(caxId, PageRequest.of(0, size));

        List<ArcadeHistoryDtos.HistoryEntry> entries = new ArrayList<>(results.size());
        for (ArcadeResult r : results) {
            ArcadeResult.Standing mine = r.getStandings().stream()
                    .filter(s -> s.getCaxId().equals(caxId))
                    .findFirst().orElse(null);

            entries.add(ArcadeHistoryDtos.HistoryEntry.builder()
                    .sessionId(r.getSessionId())
                    .gameCode(r.getGameCode())
                    .gameType(r.getGameType())
                    .hostName(r.getHostName())
                    .playerCount(r.getPlayerCount())
                    .roundsPlayed(r.getRoundsPlayed())
                    .myScore(mine == null ? 0 : mine.getScore())
                    .myRank(mine == null ? 0 : mine.getRank())
                    .iWon(r.getWinnerCaxIds().contains(caxId))
                    .winnerLine(winnerLineOf(r))
                    .standings(r.getStandings().stream().map(this::toStanding).toList())
                    .endedAt(r.getEndedAt())
                    .build());
        }
        return entries;
    }

    /**
     * Lifetime aggregates.
     *
     * <p>Computed from the frozen result documents rather than from live participant rows, so
     * a player's record reflects how each game actually ended and cannot shift because a
     * session was later reaped or a name was changed.
     */
    public ArcadeHistoryDtos.UserStats statsFor(String caxId) {
        List<ArcadeResult> results = resultRepository.findByParticipantCaxIdsContains(caxId);

        int played = results.size();
        int won = 0;
        int total = 0;
        int best = 0;

        Map<ArcadeGameType, int[]> byType = new EnumMap<>(ArcadeGameType.class);

        for (ArcadeResult r : results) {
            ArcadeResult.Standing mine = r.getStandings().stream()
                    .filter(s -> s.getCaxId().equals(caxId))
                    .findFirst().orElse(null);
            int score = mine == null ? 0 : mine.getScore();
            boolean isWin = r.getWinnerCaxIds().contains(caxId);

            total += score;
            best = Math.max(best, score);
            if (isWin) won++;

            // [played, won, bestScore]
            int[] agg = byType.computeIfAbsent(r.getGameType(), k -> new int[3]);
            agg[0]++;
            if (isWin) agg[1]++;
            agg[2] = Math.max(agg[2], score);
        }

        List<ArcadeHistoryDtos.GameTypeStat> breakdown = new ArrayList<>();
        byType.forEach((type, agg) -> breakdown.add(ArcadeHistoryDtos.GameTypeStat.builder()
                .gameType(type)
                .played(agg[0])
                .won(agg[1])
                .bestScore(agg[2])
                .build()));

        return ArcadeHistoryDtos.UserStats.builder()
                .gamesPlayed(played)
                .gamesWon(won)
                .totalScore(total)
                .bestScore(best)
                .winRatePercent(played == 0 ? 0 : Math.round((won * 100f) / played))
                .byGameType(breakdown)
                .build();
    }

    /**
     * The standings of one finished game, for the history detail screen. Scoped to
     * participants — a code alone does not open someone else's results.
     */
    public ArcadeHistoryDtos.HistoryEntry detailFor(String sessionId, String caxId) {
        ArcadeResult result = resultRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new com.cax.cax_backend.common.exception.BusinessException
                        .ResourceNotFoundException("Game result"));

        if (!result.getParticipantCaxIds().contains(caxId)) {
            throw new com.cax.cax_backend.common.exception.BusinessException
                    .ForbiddenException("You didn't play in that game");
        }

        ArcadeResult.Standing mine = result.getStandings().stream()
                .filter(s -> s.getCaxId().equals(caxId))
                .findFirst().orElse(null);

        return ArcadeHistoryDtos.HistoryEntry.builder()
                .sessionId(result.getSessionId())
                .gameCode(result.getGameCode())
                .gameType(result.getGameType())
                .hostName(result.getHostName())
                .playerCount(result.getPlayerCount())
                .roundsPlayed(result.getRoundsPlayed())
                .myScore(mine == null ? 0 : mine.getScore())
                .myRank(mine == null ? 0 : mine.getRank())
                .iWon(result.getWinnerCaxIds().contains(caxId))
                .winnerLine(winnerLineOf(result))
                .standings(result.getStandings().stream().map(this::toStanding).toList())
                .endedAt(result.getEndedAt())
                .build();
    }

    private ArcadeStateResponse.Standing toStanding(ArcadeResult.Standing s) {
        return ArcadeStateResponse.Standing.builder()
                .rank(s.getRank())
                .caxId(s.getCaxId())
                .displayName(s.getDisplayName())
                .avatarUrl(s.getAvatarUrl())
                .score(s.getScore())
                .roundsPlayed(s.getRoundsPlayed())
                .build();
    }

    private String winnerLineOf(ArcadeResult result) {
        List<String> names = result.getStandings().stream()
                .filter(s -> result.getWinnerCaxIds().contains(s.getCaxId()))
                .map(ArcadeResult.Standing::getDisplayName)
                .toList();
        if (names.isEmpty()) return "No winner.";
        if (names.size() == 1) return names.get(0) + " won.";
        return String.join(" and ", names) + " tied.";
    }
}
