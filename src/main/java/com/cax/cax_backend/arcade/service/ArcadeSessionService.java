package com.cax.cax_backend.arcade.service;

import com.cax.cax_backend.arcade.dto.ArcadeRequests;
import com.cax.cax_backend.arcade.engine.ArcadeGameEngine;
import com.cax.cax_backend.arcade.engine.RoundOutcome;
import com.cax.cax_backend.arcade.model.*;
import com.cax.cax_backend.arcade.repository.*;
import com.cax.cax_backend.arcade.event.ArcadeParticipantJoinedEvent;
import com.cax.cax_backend.arcade.runtime.ArcadeRuntimeStore;
import com.cax.cax_backend.arcade.runtime.ArcadeSessionRuntime;
import com.cax.cax_backend.coins.service.CoinService;
import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

/**
 * The shared machinery behind every Arcade game: codes, joining and rejoining, presence,
 * the between-rounds gate, timers, scoring and end conditions.
 *
 * <p><b>Advancement is lazy.</b> There is no scheduler driving these games. Every state poll
 * calls {@link #advanceIfDue} first, which checks whether the current phase has completed —
 * either everyone present has acted, or the deadline has passed — and moves the session on.
 * With a room full of phones polling every second and a half, transitions land promptly
 * without a background thread per game, and a session that everyone abandons simply stops
 * costing anything instead of ticking forever.
 *
 * <p><b>Transitions are atomic.</b> Thirty clients polling at once means thirty threads can
 * decide simultaneously that voting is over. Each one attempts a guarded {@code findAndModify}
 * conditioned on the phase and round it observed; exactly one matches and the rest get null
 * back and re-read. Round scoring hangs off that same winning write, so deltas are applied
 * once no matter how many callers raced.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArcadeSessionService {

    private final ArcadeSessionRepository sessionRepository;
    private final ArcadeParticipantRepository participantRepository;
    private final ArcadeRoundRepository roundRepository;
    private final ArcadeSubmissionRepository submissionRepository;
    private final ArcadeVoteRepository voteRepository;
    private final ArcadeResultRepository resultRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final List<ArcadeGameEngine> engineBeans;
    private final CoinService coinService;
    private final ArcadeRuntimeStore runtimeStore;
    private final ApplicationEventPublisher eventPublisher;

    private Map<ArcadeGameType, ArcadeGameEngine> engines;

    private static final SecureRandom CODE_RANDOM = new SecureRandom();

    /** Ambiguous glyphs (0/O, 1/I) are excluded — these codes get read aloud across a room. */
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;

    /**
     * How long a player counts as present after their last poll. Comfortably longer than the
     * client's polling interval, so a single dropped request does not flicker someone out of
     * the room, but short enough that a genuine departure is noticed within a few seconds.
     */
    public static final long PRESENCE_WINDOW_SECONDS = 15;

    private static final int MIN_ROUNDS = 3;
    private static final int MAX_ROUNDS = 12;
    private static final int DEFAULT_ROUNDS = 5;

    private static final int SUBMIT_SECONDS = 50;
    private static final int VOTE_SECONDS = 40;
    private static final int REVEAL_SECONDS = 14;
    private static final int INTERMISSION_SECONDS = 20;

    /**
     * How many unfinished games one person may host. Without a cap, a script could mint codes
     * until the namespace was uncomfortably crowded and every lookup slowed down.
     */
    private static final int MAX_OPEN_SESSIONS_PER_HOST = 3;

    /** Sessions untouched for this long are abandoned and get closed out. */
    private static final long IDLE_REAP_MINUTES = 90;

    // ── Lookup helpers ────────────────────────────────────────────────────────

    private ArcadeGameEngine engineFor(ArcadeGameType type) {
        if (engines == null) {
            Map<ArcadeGameType, ArcadeGameEngine> byType = new EnumMap<>(ArcadeGameType.class);
            for (ArcadeGameEngine e : engineBeans) byType.put(e.type(), e);
            engines = byType;
        }
        ArcadeGameEngine engine = engines.get(type);
        if (engine == null) {
            throw new BusinessException.BadRequestException("That game is not available");
        }
        return engine;
    }

    public ArcadeSession getSessionOrThrow(String gameCode) {
        return sessionRepository.findByGameCode(normaliseCode(gameCode))
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Game", gameCode));
    }

    /**
     * Codes are matched case- and whitespace-insensitively, because they are typed by hand
     * from someone reading them out.
     */
    private String normaliseCode(String raw) {
        if (raw == null) throw new BusinessException.BadRequestException("Enter a game code");
        String code = raw.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (code.isEmpty()) throw new BusinessException.BadRequestException("Enter a game code");
        return code;
    }

    public User requireUser(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("User", userId));
        if (user.getCaxId() == null || user.getCaxId().isBlank()) {
            throw new BusinessException.BadRequestException(
                    "Your account does not have a CAX ID assigned yet");
        }
        return user;
    }

    /**
     * The participant row for a caller, or a 403.
     *
     * <p>Every player-facing action funnels through this. It is the check that stops someone
     * who knows a code — codes are short and get shouted across rooms — from voting in a game
     * they never joined.
     */
    public ArcadeParticipant requireParticipant(ArcadeSession session, String caxId) {
        return participantRepository.findBySessionIdAndCaxId(session.getId(), caxId)
                .orElseThrow(() -> new BusinessException.ForbiddenException(
                        "You are not in this game"));
    }

    private void requireHost(ArcadeSession session, String userId) {
        if (!session.getHostUserId().equals(userId)) {
            throw new BusinessException.ForbiddenException("Only the host can do that");
        }
    }

    // ── Creating and joining ──────────────────────────────────────────────────

    public ArcadeSession createSession(ArcadeRequests.CreateSession req, String userId) {
        if (req == null || req.getGameType() == null) {
            throw new BusinessException.BadRequestException("Pick a game");
        }
        User host = requireUser(userId);

        long open = sessionRepository.countByHostUserIdAndPhaseNot(userId, ArcadePhase.FINISHED);
        if (open >= MAX_OPEN_SESSIONS_PER_HOST) {
            throw new BusinessException.ResourceConflictException(
                    "You already have " + MAX_OPEN_SESSIONS_PER_HOST
                            + " games open. Finish one before starting another.");
        }

        // Round count is clamped rather than rejected — the client offers a slider, and a
        // value outside the range is far more likely to be a stale build than an attack.
        int rounds = req.getTotalRounds() == null ? DEFAULT_ROUNDS
                : Math.max(MIN_ROUNDS, Math.min(MAX_ROUNDS, req.getTotalRounds()));

        Integer targetScore = req.getTargetScore() == null ? null
                : Math.max(100, Math.min(5000, req.getTargetScore()));

        String gameCode = generateGameCode();

        double cost = coinService.getConfig().getArcadeGameCost();
        if (cost > 0) {
            // Deducted before the session is persisted, so a failed or insufficient
            // balance never leaves an orphaned game a player didn't pay for.
            coinService.deductCoins(userId, cost, gameCode, "Created arcade game", "SPENT_ARCADE");
        }

        ArcadeSession session = ArcadeSession.builder()
                .gameCode(gameCode)
                .gameType(req.getGameType())
                .hostUserId(userId)
                .hostCaxId(host.getCaxId())
                .hostName(host.getThoughtsDisplayName())
                .collegeId(host.getCollegeDetails() != null ? host.getCollegeDetails().getCollegeId() : null)
                .phase(ArcadePhase.LOBBY)
                .totalRounds(rounds)
                .targetScore(targetScore)
                .submitSeconds(SUBMIT_SECONDS)
                .voteSeconds(VOTE_SECONDS)
                .revealSeconds(REVEAL_SECONDS)
                .intermissionSeconds(INTERMISSION_SECONDS)
                .build();

        session = sessionRepository.save(session);
        upsertParticipant(session, host, true);
        return session;
    }

    /**
     * Joins a game, or rejoins one already joined.
     *
     * <p>Rejoining is the same call as joining and resolves to the same participant document,
     * which is what makes a mid-game disconnect survivable: the score, the rounds played and
     * any submission already made are all on that row, so a player who force-quits and comes
     * back lands exactly where they left off. This is also why joining stays open after the
     * game has started — for an existing player it is a reconnect, and only genuinely new
     * players are held to the lobby-only rule.
     */
    public ArcadeSession joinSession(String gameCode, String userId) {
        ArcadeSession session = getSessionOrThrow(gameCode);
        User user = requireUser(userId);

        Optional<ArcadeParticipant> existing =
                participantRepository.findBySessionIdAndCaxId(session.getId(), user.getCaxId());

        if (existing.isPresent()) {
            rejoin(session, existing.get(), user);
            return session;
        }

        if (session.isFinished()) {
            throw new BusinessException.BadRequestException("This game has already finished");
        }
        if (!session.isInLobby()) {
            throw new BusinessException.BadRequestException(
                    "This game has already started — ask the host for the next one");
        }

        long playerCount = participantRepository.countBySessionId(session.getId());
        if (playerCount >= session.getGameType().getMaxPlayers()) {
            throw new BusinessException.ResourceConflictException("This game is full");
        }

        upsertParticipant(session, user, false);
        bumpVersion(session.getId());

        // Fire-and-forget: the listener pushes to the host and lights up the lobby feed off the
        // request thread, so a slow push never delays the joining player.
        eventPublisher.publishEvent(new ArcadeParticipantJoinedEvent(
                session.getGameCode(), session.getHostUserId(), user.getCaxId(),
                user.getThoughtsDisplayName()));
        return session;
    }

    /** Restores a returning player: same row, same score, presence refreshed. */
    private void rejoin(ArcadeSession session, ArcadeParticipant participant, User user) {
        boolean wasAway = participant.getLeftAt() != null
                || !participant.isPresent(Instant.now(), PRESENCE_WINDOW_SECONDS);

        Update update = new Update()
                .set("lastSeenAt", Instant.now())
                .unset("leftAt")
                // The display name is refreshed on every rejoin so a rename between games
                // does not leave a stale name on the leaderboard.
                .set("displayName", user.getThoughtsDisplayName())
                .set("avatarUrl", user.getPicture());

        if (wasAway) {
            update.inc("rejoinCount", 1);
        }

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(participant.getId())),
                update, ArcadeParticipant.class);

        if (wasAway) {
            // Only a genuine return changes what other players see, so an ordinary reconnect
            // does not force every phone in the room to re-render.
            bumpVersion(session.getId());
        }
    }

    private ArcadeParticipant upsertParticipant(ArcadeSession session, User user, boolean host) {
        ArcadeParticipant participant = ArcadeParticipant.builder()
                .sessionId(session.getId())
                .gameCode(session.getGameCode())
                .caxId(user.getCaxId())
                .userId(user.getUserId())
                .displayName(user.getThoughtsDisplayName())
                .avatarUrl(user.getPicture())
                .host(host)
                .build();
        try {
            return participantRepository.save(participant);
        } catch (DuplicateKeyException e) {
            // Two join taps landing together. The unique index settled it; return the winner.
            return participantRepository.findBySessionIdAndCaxId(session.getId(), user.getCaxId())
                    .orElseThrow(() -> new BusinessException.ResourceConflictException(
                            "Could not join this game"));
        }
    }

    /**
     * Leaves a game. The participant row and its score are kept: leaving is a presence change,
     * not a deletion, so the player can come back and the final standings still account for
     * everyone who took part.
     */
    public void leaveSession(String gameCode, String userId) {
        ArcadeSession session = getSessionOrThrow(gameCode);
        User user = requireUser(userId);
        ArcadeParticipant participant = requireParticipant(session, user.getCaxId());

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(participant.getId())),
                new Update().set("leftAt", Instant.now()),
                ArcadeParticipant.class);

        bumpVersion(session.getId());

        // A host who walks out would otherwise strand the room with nobody able to advance it,
        // so hosting passes to whoever is still present.
        if (participant.isHost()) {
            reassignHostOrEnd(session);
        }
    }

    private void reassignHostOrEnd(ArcadeSession session) {
        List<ArcadeParticipant> present = presentPlayers(session);
        if (present.isEmpty()) {
            endSessionInternal(session, "Everyone left");
            return;
        }
        ArcadeParticipant heir = present.get(0);
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(heir.getId())),
                new Update().set("host", true), ArcadeParticipant.class);
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(session.getId())),
                new Update().set("hostUserId", heir.getUserId())
                        .set("hostCaxId", heir.getCaxId())
                        .set("hostName", heir.getDisplayName())
                        .inc("stateVersion", 1),
                ArcadeSession.class);
    }

    // ── Presence ──────────────────────────────────────────────────────────────

    /**
     * Refreshes the caller's presence. Called on every state poll, so "who is still here" is
     * a natural by-product of clients staying subscribed rather than a separate heartbeat the
     * client could forget to send.
     *
     * <p>Kept purely in memory: this was the single hottest write in the feature (one Mongo
     * update per client per poll). {@link #allPlayers} overlays it back onto the persisted
     * {@code lastSeenAt} so every presence check downstream sees the live value.
     */
    public void touch(ArcadeSession session, String caxId) {
        runtimeStore.get(session.getId()).touch(caxId);
    }

    public List<ArcadeParticipant> allPlayers(ArcadeSession session) {
        List<ArcadeParticipant> players = participantRepository.findBySessionIdOrderByScoreDesc(session.getId());
        ArcadeSessionRuntime runtime = runtimeStore.get(session.getId());
        for (ArcadeParticipant p : players) {
            Instant liveSeen = runtime.lastSeen(p.getCaxId());
            // Presence lives in memory; a rejoin also refreshes it in Mongo, so take whichever
            // is later so a just-reconnected player isn't wrongly read as absent after a restart.
            if (liveSeen != null && (p.getLastSeenAt() == null || liveSeen.isAfter(p.getLastSeenAt()))) {
                p.setLastSeenAt(liveSeen);
            }
        }
        return players;
    }

    // ── In-memory round data (submissions / votes) ────────────────────────────
    // During SUBMITTING/VOTING these live in the runtime, not Mongo; they are flushed to Mongo
    // once at reveal. Reads go through these helpers so a round that is still being played is
    // served from memory and a finished/pre-flush round is served from the durable copy.

    public List<ArcadeSubmission> submissionsFor(ArcadeSession session, ArcadeRound round) {
        if (round == null) return List.of();
        ArcadeSessionRuntime runtime = runtimeStore.get(session.getId());
        if (runtime.tracksRound(round.getId())) return runtime.submissions(round.getId());
        return submissionRepository.findByRoundId(round.getId());
    }

    public List<ArcadeVote> votesFor(ArcadeSession session, ArcadeRound round) {
        if (round == null) return List.of();
        ArcadeSessionRuntime runtime = runtimeStore.get(session.getId());
        if (runtime.tracksRound(round.getId())) return runtime.votes(round.getId());
        return voteRepository.findByRoundId(round.getId());
    }

    /**
     * The version clients poll against: the durable session version (bumped by joins, leaves and
     * phase transitions) scaled up, plus the in-memory submit/vote counter. Scaling keeps the two
     * sources in disjoint ranges so a transition can never collide with an answer count.
     */
    public long effectiveStateVersion(ArcadeSession session) {
        return session.getStateVersion() * 1000L + (runtimeStore.get(session.getId()).actionCounter() % 1000L);
    }

    public List<ArcadeParticipant> presentPlayers(ArcadeSession session) {
        Instant now = Instant.now();
        return allPlayers(session).stream()
                .filter(p -> p.isPresent(now, PRESENCE_WINDOW_SECONDS))
                .toList();
    }

    // ── Starting and rounds ───────────────────────────────────────────────────

    public ArcadeSession startSession(String gameCode, String userId) {
        ArcadeSession session = getSessionOrThrow(gameCode);
        requireHost(session, userId);

        if (!session.isInLobby()) {
            throw new BusinessException.BadRequestException("This game has already started");
        }

        int present = presentPlayers(session).size();
        int required = session.getGameType().getMinPlayers();
        if (present < required) {
            throw new BusinessException.BadRequestException(
                    "You need at least " + required + " players to start");
        }

        openRound(session, 1);
        return getSessionOrThrow(gameCode);
    }

    /**
     * Opens a round: creates its document, lets the engine fill in the content and secrets,
     * then flips the session into the first phase of the round.
     *
     * <p>The round document is written before the session moves, so no client can ever observe
     * a session pointing at a round that does not exist yet.
     */
    private void openRound(ArcadeSession session, int roundNo) {
        ArcadeGameEngine engine = engineFor(session.getGameType());
        List<ArcadeParticipant> players = allPlayers(session);

        ArcadeRound round = ArcadeRound.builder()
                .sessionId(session.getId())
                .gameType(session.getGameType())
                .roundNo(roundNo)
                .build();

        engine.prepareRound(session, round, players);

        try {
            roundRepository.save(round);
        } catch (DuplicateKeyException e) {
            // Another thread already opened this round; its version of the content stands.
            return;
        }

        // Start tracking this round's answers in memory; also clears the previous round's.
        runtimeStore.get(session.getId()).beginRound(round.getId());

        ArcadePhase first = engine.hasSubmissionPhase() ? ArcadePhase.SUBMITTING : ArcadePhase.VOTING;
        int seconds = engine.hasSubmissionPhase() ? session.getSubmitSeconds() : session.getVoteSeconds();

        Update update = new Update()
                .set("phase", first)
                .set("currentRound", roundNo)
                .set("phaseDeadlineAt", Instant.now().plusSeconds(seconds))
                .set("usedPromptIds", session.getUsedPromptIds())
                .set("lastActivityAt", Instant.now())
                .inc("stateVersion", 1);

        if (roundNo == 1) {
            update.set("startedAt", Instant.now());
        }

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(session.getId())),
                update, ArcadeSession.class);
    }

    public ArcadeRound currentRound(ArcadeSession session) {
        if (session.getCurrentRound() < 1) return null;
        return roundRepository.findBySessionIdAndRoundNo(session.getId(), session.getCurrentRound())
                .orElse(null);
    }

    // ── Player actions ────────────────────────────────────────────────────────

    /**
     * Records a submission for the current round.
     *
     * <p>Note what is not a parameter: which round to submit to. The server uses whichever
     * round is currently open, so a replayed request cannot land against a past round, and a
     * client that has drifted behind gets a clear phase error instead of silently corrupting
     * an earlier round's results.
     */
    public void submit(String gameCode, String userId, ArcadeRequests.Submit req) {
        ArcadeSession session = advanceIfDue(getSessionOrThrow(gameCode));
        User user = requireUser(userId);
        ArcadeParticipant participant = requireParticipant(session, user.getCaxId());

        if (!session.getPhase().acceptsSubmissions()) {
            throw new BusinessException.BadRequestException(
                    session.getPhase() == ArcadePhase.VOTING
                            ? "Time's up — voting has started"
                            : "You can't submit right now");
        }

        ArcadeRound round = currentRound(session);
        if (round == null) throw new BusinessException.BadRequestException("No round is open");

        String content = engineFor(session.getGameType()).normaliseSubmission(req == null ? null : req.getContent());

        // The id is assigned here rather than by Mongo because votes in Who Said It reference a
        // submission by id during VOTING, before this row is ever flushed to the database.
        ArcadeSubmission submission = ArcadeSubmission.builder()
                .id(UUID.randomUUID().toString())
                .roundId(round.getId())
                .sessionId(session.getId())
                .roundNo(round.getRoundNo())
                .caxId(participant.getCaxId())
                .content(content)
                .build();

        boolean stored = runtimeStore.get(session.getId()).addSubmission(round.getId(), submission);
        if (!stored) {
            // In-memory one-answer-per-round check, standing in for the unique index while the
            // submission is held in memory until reveal.
            throw new BusinessException.ResourceConflictException("You've already answered this round");
        }

        runtimeStore.get(session.getId()).bumpAction();
        advanceIfDue(getSessionOrThrow(gameCode));
    }

    /** Records a vote for the current round, subject to the same round-pinning as submissions. */
    public void vote(String gameCode, String userId, ArcadeRequests.Vote req) {
        ArcadeSession session = advanceIfDue(getSessionOrThrow(gameCode));
        User user = requireUser(userId);
        ArcadeParticipant participant = requireParticipant(session, user.getCaxId());

        if (!session.getPhase().acceptsVotes()) {
            throw new BusinessException.BadRequestException("Voting isn't open right now");
        }
        if (req == null || req.getTargetCaxId() == null || req.getTargetCaxId().isBlank()) {
            throw new BusinessException.BadRequestException("Pick someone first");
        }

        ArcadeRound round = currentRound(session);
        if (round == null) throw new BusinessException.BadRequestException("No round is open");

        List<ArcadeParticipant> players = allPlayers(session);

        // The target must be someone actually in this game — otherwise a crafted request could
        // award points to an arbitrary account, or to a player in a completely different room.
        boolean targetInGame = players.stream()
                .anyMatch(p -> p.getCaxId().equals(req.getTargetCaxId()));
        if (!targetInGame) {
            throw new BusinessException.BadRequestException("That person isn't in this game");
        }

        List<ArcadeSubmission> submissions = submissionsFor(session, round);
        engineFor(session.getGameType())
                .validateVote(round, players, submissions, participant.getCaxId(), req);

        ArcadeVote vote = ArcadeVote.builder()
                .id(UUID.randomUUID().toString())
                .roundId(round.getId())
                .sessionId(session.getId())
                .roundNo(round.getRoundNo())
                .voterCaxId(participant.getCaxId())
                .targetCaxId(req.getTargetCaxId())
                .targetSubmissionId(req.getTargetSubmissionId())
                .build();

        boolean stored = runtimeStore.get(session.getId()).addVote(round.getId(), vote);
        if (!stored) {
            throw new BusinessException.ResourceConflictException("You've already voted this round");
        }

        runtimeStore.get(session.getId()).bumpAction();
        advanceIfDue(getSessionOrThrow(gameCode));
    }

    /**
     * Marks the caller ready for the next round — the explicit half of the between-rounds gate.
     */
    public void markReady(String gameCode, String userId) {
        ArcadeSession session = getSessionOrThrow(gameCode);
        User user = requireUser(userId);
        ArcadeParticipant participant = requireParticipant(session, user.getCaxId());

        if (session.getPhase() != ArcadePhase.INTERMISSION) {
            throw new BusinessException.BadRequestException("Nothing to get ready for right now");
        }

        int nextRound = session.getCurrentRound() + 1;
        if (participant.getReadyForRound() >= nextRound) return;

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(participant.getId())),
                new Update().set("readyForRound", nextRound).set("lastSeenAt", Instant.now()),
                ArcadeParticipant.class);

        bumpVersion(session.getId());
        advanceIfDue(getSessionOrThrow(gameCode));
    }

    // ── The phase engine ──────────────────────────────────────────────────────

    /**
     * Advances the session if its current phase is complete. Safe to call from anywhere and
     * from any number of threads at once: each transition is a guarded conditional write, so
     * the losers of the race simply observe the result of the winner.
     */
    public ArcadeSession advanceIfDue(ArcadeSession session) {
        if (session == null || session.isFinished()) return session;

        Instant now = Instant.now();
        boolean deadlinePassed = session.getPhaseDeadlineAt() != null
                && now.isAfter(session.getPhaseDeadlineAt());

        switch (session.getPhase()) {
            case LOBBY -> {
                return session;
            }
            case SUBMITTING -> {
                ArcadeRound round = currentRound(session);
                if (round == null) return session;
                if (everyonePresentActed(session, round, true) || deadlinePassed) {
                    return toVoting(session);
                }
            }
            case VOTING -> {
                ArcadeRound round = currentRound(session);
                if (round == null) return session;
                if (everyonePresentActed(session, round, false) || deadlinePassed) {
                    return toReveal(session);
                }
            }
            case REVEAL -> {
                if (deadlinePassed) return toIntermission(session);
            }
            case INTERMISSION -> {
                // The gate: wait for everyone present to acknowledge, but never past the
                // deadline. Someone who wandered off mid-game must not be able to hold the
                // room hostage, and their score survives regardless because it lives on their
                // participant row rather than in the round.
                List<ArcadeParticipant> present = presentPlayers(session);
                int nextRound = session.getCurrentRound() + 1;
                boolean allReady = !present.isEmpty()
                        && present.stream().allMatch(p -> p.getReadyForRound() >= nextRound);
                if (allReady || deadlinePassed) {
                    return startNextRoundOrFinish(session);
                }
            }
            default -> {
                return session;
            }
        }
        return session;
    }

    /**
     * Whether every currently-present player has submitted (or voted) this round — the
     * "everyone's in, move on early" condition.
     *
     * <p>The participant list is read once and reused. This runs on every poll from every
     * phone in the room, so re-querying it per check would multiply the read load on the
     * hottest path in the feature by the size of the room.
     */
    private boolean everyonePresentActed(ArcadeSession session, ArcadeRound round, boolean submissions) {
        List<ArcadeParticipant> present = presentPlayers(session);
        if (present.isEmpty()) return false;

        Set<String> acted = new HashSet<>();
        if (submissions) {
            submissionsFor(session, round).forEach(s -> acted.add(s.getCaxId()));
        } else {
            votesFor(session, round).forEach(v -> acted.add(v.getVoterCaxId()));
        }

        for (ArcadeParticipant p : present) {
            if (!acted.contains(p.getCaxId())) return false;
        }
        return true;
    }

    private ArcadeSession toVoting(ArcadeSession session) {
        ArcadeSession updated = transition(session, ArcadePhase.SUBMITTING, session.getCurrentRound(),
                new Update().set("phase", ArcadePhase.VOTING)
                        .set("phaseDeadlineAt", Instant.now().plusSeconds(session.getVoteSeconds())));
        return updated != null ? updated : reload(session);
    }

    /**
     * Closes voting, scores the round and reveals it.
     *
     * <p>Scoring is deliberately attached to the winning transition write. Whichever caller
     * successfully flips VOTING to REVEAL is the one that applies the deltas, so no arrangement
     * of concurrent polls can pay a round out twice.
     */
    private ArcadeSession toReveal(ArcadeSession session) {
        ArcadeSession updated = transition(session, ArcadePhase.VOTING, session.getCurrentRound(),
                new Update().set("phase", ArcadePhase.REVEAL)
                        .set("phaseDeadlineAt", Instant.now().plusSeconds(session.getRevealSeconds())));

        if (updated == null) return reload(session);

        ArcadeRound round = currentRound(updated);
        if (round == null) return updated;

        List<ArcadeParticipant> players = allPlayers(updated);
        List<ArcadeSubmission> submissions = submissionsFor(updated, round);
        List<ArcadeVote> votes = votesFor(updated, round);

        // Scoring mutates each vote's `correct` flag, so this must run before the flush that
        // persists them.
        RoundOutcome outcome = engineFor(updated.getGameType())
                .scoreRound(round, players, submissions, votes);

        flushRound(round, submissions, votes);
        applyOutcome(updated, round, players, outcome);
        return reload(updated);
    }

    /**
     * The single durable write of a round's answers: submissions and votes accumulated in memory
     * during play are inserted here, once, with each vote's reveal-time {@code correct} flag
     * already set by scoring. Whichever caller won the guarded transition to REVEAL is the only
     * one that reaches this, so a flush cannot happen twice for the same round.
     */
    private void flushRound(ArcadeRound round, List<ArcadeSubmission> submissions, List<ArcadeVote> votes) {
        try {
            if (!submissions.isEmpty()) submissionRepository.saveAll(submissions);
            if (!votes.isEmpty()) voteRepository.saveAll(votes);
        } catch (DuplicateKeyException e) {
            // In-memory dedup makes this unreachable in normal play; if a restart-era duplicate
            // slips through, don't abort the reveal — the round is already scored.
            log.warn("Duplicate on round flush for round {}: {}", round.getId(), e.getMessage());
        }
    }

    /**
     * Persists a round's result: score increments and round metadata. Per-vote correctness is
     * already set on the vote rows by {@link #flushRound}, which runs just before this.
     */
    private void applyOutcome(ArcadeSession session,
                              ArcadeRound round,
                              List<ArcadeParticipant> players,
                              RoundOutcome outcome) {

        for (Map.Entry<String, Integer> entry : outcome.getDeltas().entrySet()) {
            // An increment rather than a computed total: two rounds resolving close together
            // can never overwrite each other's contribution.
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("sessionId").is(session.getId())
                            .and("caxId").is(entry.getKey())),
                    new Update().inc("score", entry.getValue()),
                    ArcadeParticipant.class);
        }

        // Everyone present is credited with having played the round, whether or not they scored.
        for (ArcadeParticipant p : players) {
            if (p.isPresent(Instant.now(), PRESENCE_WINDOW_SECONDS)) {
                mongoTemplate.updateFirst(
                        Query.query(Criteria.where("id").is(p.getId())),
                        new Update().inc("roundsPlayed", 1),
                        ArcadeParticipant.class);
            }
        }

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(round.getId())),
                new Update().set("revealed", true)
                        .set("winnerCaxIds", outcome.getWinnerCaxIds())
                        .set("outcomeSummary", outcome.getSummary())
                        // Persisted so the reveal screen can show each score change to a player
                        // who polls a moment later — including a rejoiner with no prior total
                        // to diff against.
                        .set("scoreDeltas", outcome.getDeltas())
                        .set("deltaReasons", outcome.getReasons())
                        .set("endedAt", Instant.now()),
                ArcadeRound.class);

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(session.getId())),
                new Update().inc("stateVersion", 1),
                ArcadeSession.class);
    }

    private ArcadeSession toIntermission(ArcadeSession session) {
        boolean lastRound = session.getCurrentRound() >= session.getTotalRounds();
        if (lastRound || targetScoreReached(session)) {
            return finishFrom(session, ArcadePhase.REVEAL,
                    lastRound ? "All rounds played" : "Score target reached");
        }
        ArcadeSession updated = transition(session, ArcadePhase.REVEAL, session.getCurrentRound(),
                new Update().set("phase", ArcadePhase.INTERMISSION)
                        .set("phaseDeadlineAt",
                                Instant.now().plusSeconds(session.getIntermissionSeconds())));
        return updated != null ? updated : reload(session);
    }

    private ArcadeSession startNextRoundOrFinish(ArcadeSession session) {
        List<ArcadeParticipant> present = presentPlayers(session);
        if (present.size() < session.getGameType().getMinPlayers()) {
            return finishFrom(session, ArcadePhase.INTERMISSION, "Not enough players left");
        }

        // Push the deadline out first so the gate stops re-firing while the round is being
        // built. This claim does not change the phase, so it is not by itself exclusive —
        // two callers can both get through here. The actual guarantee is one level down:
        // openRound writes the round against a unique (sessionId, roundNo) index and bails
        // out on the duplicate, so a raced second caller cannot create a parallel round.
        ArcadeSession claimed = transition(session, ArcadePhase.INTERMISSION, session.getCurrentRound(),
                new Update().set("phaseDeadlineAt", Instant.now().plusSeconds(5)));
        if (claimed == null) return reload(session);

        openRound(claimed, claimed.getCurrentRound() + 1);
        return reload(claimed);
    }

    private boolean targetScoreReached(ArcadeSession session) {
        if (session.getTargetScore() == null) return false;
        return allPlayers(session).stream().anyMatch(p -> p.getScore() >= session.getTargetScore());
    }

    // ── Ending ────────────────────────────────────────────────────────────────

    public ArcadeSession endSession(String gameCode, String userId) {
        ArcadeSession session = getSessionOrThrow(gameCode);
        requireHost(session, userId);
        if (session.isFinished()) return session;
        endSessionInternal(session, "Ended by host");
        return reload(session);
    }

    private ArcadeSession finishFrom(ArcadeSession session, ArcadePhase from, String reason) {
        ArcadeSession updated = transition(session, from, session.getCurrentRound(),
                new Update().set("phase", ArcadePhase.FINISHED)
                        .set("endedAt", Instant.now())
                        .set("endReason", reason)
                        .unset("phaseDeadlineAt"));
        if (updated == null) return reload(session);
        writeResult(updated, reason);
        runtimeStore.discard(session.getId());
        return reload(updated);
    }

    private void endSessionInternal(ArcadeSession session, String reason) {
        ArcadeSession updated = mongoTemplate.findAndModify(
                Query.query(Criteria.where("id").is(session.getId())
                        .and("phase").ne(ArcadePhase.FINISHED)),
                new Update().set("phase", ArcadePhase.FINISHED)
                        .set("endedAt", Instant.now())
                        .set("endReason", reason)
                        .unset("phaseDeadlineAt")
                        .inc("stateVersion", 1),
                FindAndModifyOptions.options().returnNew(true),
                ArcadeSession.class);
        if (updated != null) {
            writeResult(updated, reason);
            runtimeStore.discard(session.getId());
        }
    }

    /**
     * Freezes the final standings into their own document.
     *
     * <p>Written once, guarded by the unique index on sessionId, so the history a player sees
     * months later is the result as it stood at the final whistle rather than something
     * recomputed from rows that may since have changed.
     */
    private void writeResult(ArcadeSession session, String reason) {
        if (resultRepository.findBySessionId(session.getId()).isPresent()) return;

        List<ArcadeParticipant> players = allPlayers(session);
        players = new ArrayList<>(players);
        players.sort(Comparator.comparingInt(ArcadeParticipant::getScore).reversed());

        List<ArcadeResult.Standing> standings = new ArrayList<>(players.size());
        List<String> participantIds = new ArrayList<>(players.size());
        int rank = 0;
        int lastScore = Integer.MIN_VALUE;

        for (int i = 0; i < players.size(); i++) {
            ArcadeParticipant p = players.get(i);
            // Equal scores share a rank — a tie at the top means two winners, not an
            // arbitrary ordering decided by document order.
            if (p.getScore() != lastScore) {
                rank = i + 1;
                lastScore = p.getScore();
            }
            standings.add(ArcadeResult.Standing.builder()
                    .caxId(p.getCaxId())
                    .displayName(p.getDisplayName())
                    .avatarUrl(p.getAvatarUrl())
                    .score(p.getScore())
                    .rank(rank)
                    .roundsPlayed(p.getRoundsPlayed())
                    .rejoinCount(p.getRejoinCount())
                    .build());
            participantIds.add(p.getCaxId());
        }

        List<String> winners = standings.stream()
                .filter(s -> s.getRank() == 1 && s.getScore() > 0)
                .map(ArcadeResult.Standing::getCaxId)
                .toList();

        try {
            resultRepository.save(ArcadeResult.builder()
                    .sessionId(session.getId())
                    .gameCode(session.getGameCode())
                    .gameType(session.getGameType())
                    .hostCaxId(session.getHostCaxId())
                    .hostName(session.getHostName())
                    .collegeId(session.getCollegeId())
                    .standings(standings)
                    .winnerCaxIds(winners)
                    .participantCaxIds(participantIds)
                    .roundsPlayed(session.getCurrentRound())
                    .playerCount(players.size())
                    .endReason(reason)
                    .startedAt(session.getStartedAt())
                    .build());
        } catch (DuplicateKeyException e) {
            log.debug("Arcade result already written for session {}", session.getId());
        }
    }

    // ── Low-level write helpers ───────────────────────────────────────────────

    /**
     * A phase change conditioned on the phase and round the caller observed. Returns null when
     * another thread got there first, which callers treat as "re-read and carry on" rather
     * than as an error.
     */
    private ArcadeSession transition(ArcadeSession session, ArcadePhase from, int roundNo, Update update) {
        update.inc("stateVersion", 1).set("lastActivityAt", Instant.now());
        return mongoTemplate.findAndModify(
                Query.query(Criteria.where("id").is(session.getId())
                        .and("phase").is(from)
                        .and("currentRound").is(roundNo)),
                update,
                FindAndModifyOptions.options().returnNew(true),
                ArcadeSession.class);
    }

    /** Signals "something changed" without changing the phase, so idle clients re-render. */
    private void bumpVersion(String sessionId) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(sessionId)),
                new Update().inc("stateVersion", 1).set("lastActivityAt", Instant.now()),
                ArcadeSession.class);
    }

    private ArcadeSession reload(ArcadeSession session) {
        return sessionRepository.findById(session.getId()).orElse(session);
    }

    // ── Housekeeping ──────────────────────────────────────────────────────────

    /**
     * Closes out sessions nobody has touched in a long time, so abandoned lobbies stop holding
     * their codes and stop appearing on their host's "resume" list.
     */
    public int reapIdleSessions() {
        Instant cutoff = Instant.now().minusSeconds(IDLE_REAP_MINUTES * 60);
        List<ArcadeSession> idle =
                sessionRepository.findByPhaseNotAndLastActivityAtBefore(ArcadePhase.FINISHED, cutoff);
        for (ArcadeSession session : idle) {
            endSessionInternal(session, "Abandoned");
        }
        return idle.size();
    }

    /**
     * Advances every live session whose phase deadline has passed, returning the codes of those
     * that actually moved. This is what lets the games run on a server-side clock instead of
     * needing a client to poll: a round still ends on time when everyone is sitting idle on the
     * WebSocket. Early "everyone answered" advances still happen inline on the submit/vote call.
     */
    public List<String> advanceDueSessions() {
        List<ArcadeSession> due =
                sessionRepository.findByPhaseNotAndPhaseDeadlineAtBefore(ArcadePhase.FINISHED, Instant.now());
        List<String> changed = new ArrayList<>();
        for (ArcadeSession session : due) {
            try {
                long beforeVersion = session.getStateVersion();
                ArcadePhase beforePhase = session.getPhase();
                ArcadeSession after = advanceIfDue(session);
                if (after != null
                        && (after.getStateVersion() != beforeVersion || after.getPhase() != beforePhase)) {
                    changed.add(after.getGameCode());
                }
            } catch (Exception e) {
                log.warn("Arcade: failed to advance session {}", session.getGameCode(), e);
            }
        }
        return changed;
    }

    private String generateGameCode() {
        for (int attempt = 0; attempt < 12; attempt++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_ALPHABET.charAt(CODE_RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            String code = sb.toString();
            if (!sessionRepository.existsByGameCode(code)) return code;
        }
        throw new BusinessException.ResourceConflictException(
                "Could not create a game right now. Please try again.");
    }
}
