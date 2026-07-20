package com.cax.cax_backend.arcade.service;

import com.cax.cax_backend.arcade.dto.ArcadeStateResponse;
import com.cax.cax_backend.arcade.engine.ArcadeGameEngine;
import com.cax.cax_backend.arcade.model.*;
import com.cax.cax_backend.arcade.repository.ArcadeRoundRepository;
import com.cax.cax_backend.arcade.repository.ArcadeSubmissionRepository;
import com.cax.cax_backend.arcade.repository.ArcadeVoteRepository;
import com.cax.cax_backend.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Assembles the state payload a single viewer is allowed to see.
 *
 * <p>The redaction of round content is delegated to the game's engine, since only the engine
 * knows what its own secrets are. What this class owns is everything around that: presence,
 * the progress counters, the viewer's private view of their own actions, and the final
 * standings. The rule it follows for the shared fields mirrors the engines' — other players'
 * actions are reported as booleans ("has acted") and never as content, so the lobby can show
 * progress without leaking answers.
 */
@Service
@RequiredArgsConstructor
public class ArcadeStateService {

    private final ArcadeSessionService sessionService;
    private final ArcadeRoundRepository roundRepository;
    private final ArcadeSubmissionRepository submissionRepository;
    private final ArcadeVoteRepository voteRepository;
    private final List<ArcadeGameEngine> engineBeans;

    private Map<ArcadeGameType, ArcadeGameEngine> engines;

    private ArcadeGameEngine engineFor(ArcadeGameType type) {
        if (engines == null) {
            Map<ArcadeGameType, ArcadeGameEngine> byType = new EnumMap<>(ArcadeGameType.class);
            for (ArcadeGameEngine e : engineBeans) byType.put(e.type(), e);
            engines = byType;
        }
        ArcadeGameEngine engine = engines.get(type);
        if (engine == null) throw new BusinessException.BadRequestException("That game is not available");
        return engine;
    }

    /**
     * Builds state for one viewer, advancing the session first if its phase is due to end.
     *
     * <p>Polling is what drives the games forward, so this is deliberately not a pure read.
     */
    public ArcadeStateResponse buildFor(String gameCode, String userId) {
        ArcadeSession session = sessionService.getSessionOrThrow(gameCode);
        var user = sessionService.requireUser(userId);
        String viewerCaxId = user.getCaxId();

        // Membership is required even to read state. A game code is short and gets read aloud,
        // so it is a way to find a game, never a credential for watching one.
        ArcadeParticipant viewer = sessionService.requireParticipant(session, viewerCaxId);

        sessionService.touch(session, viewerCaxId);
        session = sessionService.advanceIfDue(session);

        List<ArcadeParticipant> players = sessionService.allPlayers(session);
        Instant now = Instant.now();

        ArcadeRound round = session.getCurrentRound() > 0
                ? roundRepository.findBySessionIdAndRoundNo(session.getId(), session.getCurrentRound()).orElse(null)
                : null;

        List<ArcadeSubmission> submissions = round == null
                ? List.of() : submissionRepository.findByRoundId(round.getId());
        List<ArcadeVote> votes = round == null
                ? List.of() : voteRepository.findByRoundId(round.getId());

        Set<String> submitted = new HashSet<>();
        submissions.forEach(s -> submitted.add(s.getCaxId()));
        Set<String> voted = new HashSet<>();
        votes.forEach(v -> voted.add(v.getVoterCaxId()));

        // Which action counts as "acted" depends on the phase — the lobby progress line reads
        // "8 of 10 answered" during submission and "8 of 10 voted" during the vote.
        Set<String> actedThisPhase = switch (session.getPhase()) {
            case SUBMITTING -> submitted;
            case VOTING -> voted;
            default -> Set.of();
        };

        int nextRound = session.getCurrentRound() + 1;
        int presentCount = 0;
        int readyCount = 0;
        List<ArcadeStateResponse.PlayerView> playerViews = new ArrayList<>(players.size());

        for (ArcadeParticipant p : players) {
            boolean present = p.isPresent(now, ArcadeSessionService.PRESENCE_WINDOW_SECONDS);
            if (present) presentCount++;

            boolean acted = actedThisPhase.contains(p.getCaxId());
            boolean ready = p.getReadyForRound() >= nextRound;
            if (present && (session.getPhase() == ArcadePhase.INTERMISSION ? ready : acted)) {
                readyCount++;
            }

            playerViews.add(ArcadeStateResponse.PlayerView.builder()
                    .caxId(p.getCaxId())
                    .displayName(p.getDisplayName())
                    .avatarUrl(p.getAvatarUrl())
                    .score(p.getScore())
                    .host(p.isHost())
                    .present(present)
                    .rejoined(p.getRejoinCount() > 0)
                    .acted(acted)
                    .ready(ready)
                    .build());
        }

        ArcadeStateResponse.RoundView roundView = null;
        if (round != null && session.getPhase() != ArcadePhase.FINISHED) {
            roundView = engineFor(session.getGameType())
                    .buildRoundView(session, round, players, submissions, votes, viewerCaxId);
            if (round.isRevealed()) {
                roundView.setDeltas(buildDeltas(round, players));
            }
        }

        return ArcadeStateResponse.builder()
                .gameCode(session.getGameCode())
                .gameType(session.getGameType())
                .phase(session.getPhase())
                .stateVersion(session.getStateVersion())
                .currentRound(session.getCurrentRound())
                .totalRounds(session.getTotalRounds())
                .phaseDeadlineAtMillis(session.getPhaseDeadlineAt() == null
                        ? null : session.getPhaseDeadlineAt().toEpochMilli())
                // Clients run their countdown off the offset between this and their own clock,
                // so a device with a wrong or deliberately shifted clock still sees the real
                // remaining time.
                .serverTimeMillis(now.toEpochMilli())
                .hostCaxId(session.getHostCaxId())
                .hostName(session.getHostName())
                .viewerIsHost(session.getHostUserId().equals(userId))
                .players(playerViews)
                .presentCount(presentCount)
                .totalCount(players.size())
                .readyCount(readyCount)
                .round(roundView)
                .me(buildMe(session, viewer, players, submissions, votes, nextRound))
                .finalResult(session.isFinished() ? buildFinal(session, players) : null)
                .build();
    }

    /** The viewer's own private state. All of it is their own data, so none of it is redacted. */
    private ArcadeStateResponse.MeView buildMe(ArcadeSession session,
                                               ArcadeParticipant viewer,
                                               List<ArcadeParticipant> players,
                                               List<ArcadeSubmission> submissions,
                                               List<ArcadeVote> votes,
                                               int nextRound) {

        ArcadeSubmission mine = submissions.stream()
                .filter(s -> s.getCaxId().equals(viewer.getCaxId()))
                .findFirst().orElse(null);

        ArcadeVote myVote = votes.stream()
                .filter(v -> v.getVoterCaxId().equals(viewer.getCaxId()))
                .findFirst().orElse(null);

        int rank = 1;
        for (ArcadeParticipant p : players) {
            if (p.getScore() > viewer.getScore()) rank++;
        }

        return ArcadeStateResponse.MeView.builder()
                .caxId(viewer.getCaxId())
                .displayName(viewer.getDisplayName())
                .score(viewer.getScore())
                .rank(rank)
                .host(viewer.isHost())
                .submitted(mine != null)
                .voted(myVote != null)
                .ready(viewer.getReadyForRound() >= nextRound)
                // Echoed back so a player who rejoined mid-round sees what they already wrote
                // instead of an empty box inviting them to answer twice.
                .mySubmission(mine == null ? null : mine.getContent())
                .myVoteTargetCaxId(myVote == null ? null : myVote.getTargetCaxId())
                .myVoteSubmissionId(myVote == null ? null : myVote.getTargetSubmissionId())
                .build();
    }

    private List<ArcadeStateResponse.ScoreDelta> buildDeltas(ArcadeRound round,
                                                             List<ArcadeParticipant> players) {
        if (round.getScoreDeltas() == null || round.getScoreDeltas().isEmpty()) return List.of();

        Map<String, String> names = new HashMap<>();
        players.forEach(p -> names.put(p.getCaxId(), p.getDisplayName()));

        List<ArcadeStateResponse.ScoreDelta> deltas = new ArrayList<>();
        round.getScoreDeltas().forEach((caxId, points) -> deltas.add(
                ArcadeStateResponse.ScoreDelta.builder()
                        .caxId(caxId)
                        .displayName(names.getOrDefault(caxId, "Someone"))
                        .delta(points)
                        .reason(round.getDeltaReasons() == null ? null : round.getDeltaReasons().get(caxId))
                        .build()));

        deltas.sort(Comparator.comparingInt(ArcadeStateResponse.ScoreDelta::getDelta).reversed());
        return deltas;
    }

    /** Final standings, with ties sharing a rank so a drawn game reports two winners. */
    private ArcadeStateResponse.FinalView buildFinal(ArcadeSession session,
                                                     List<ArcadeParticipant> players) {
        List<ArcadeParticipant> sorted = new ArrayList<>(players);
        sorted.sort(Comparator.comparingInt(ArcadeParticipant::getScore).reversed());

        List<ArcadeStateResponse.Standing> standings = new ArrayList<>(sorted.size());
        List<String> winners = new ArrayList<>();
        int rank = 0;
        int lastScore = Integer.MIN_VALUE;

        for (int i = 0; i < sorted.size(); i++) {
            ArcadeParticipant p = sorted.get(i);
            if (p.getScore() != lastScore) {
                rank = i + 1;
                lastScore = p.getScore();
            }
            standings.add(ArcadeStateResponse.Standing.builder()
                    .rank(rank)
                    .caxId(p.getCaxId())
                    .displayName(p.getDisplayName())
                    .avatarUrl(p.getAvatarUrl())
                    .score(p.getScore())
                    .roundsPlayed(p.getRoundsPlayed())
                    .build());
            if (rank == 1 && p.getScore() > 0) winners.add(p.getCaxId());
        }

        String winnerLine;
        if (winners.isEmpty()) {
            winnerLine = "No winner this time.";
        } else if (winners.size() == 1) {
            winnerLine = standings.get(0).getDisplayName() + " wins.";
        } else {
            List<String> names = standings.stream()
                    .filter(s -> s.getRank() == 1)
                    .map(ArcadeStateResponse.Standing::getDisplayName)
                    .toList();
            winnerLine = String.join(" and ", names) + " tie for the win.";
        }

        return ArcadeStateResponse.FinalView.builder()
                .standings(standings)
                .winnerCaxIds(winners)
                .winnerLine(winnerLine)
                .roundsPlayed(session.getCurrentRound())
                .endReason(session.getEndReason())
                .build();
    }
}
