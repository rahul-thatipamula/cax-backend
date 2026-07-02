package com.cax.cax_backend.game.service;

import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.game.dto.BingoLeaderboardEntry;
import com.cax.cax_backend.game.dto.CreateBingoGameRequest;
import com.cax.cax_backend.game.dto.MarkCellRequest;
import com.cax.cax_backend.game.dto.SignerUsageStat;
import com.cax.cax_backend.game.model.BingoGame;
import com.cax.cax_backend.game.model.BingoPlayerCard;
import com.cax.cax_backend.game.repository.BingoGameRepository;
import com.cax.cax_backend.game.repository.BingoPlayerCardRepository;
import com.cax.cax_backend.organization.model.OrganizationMember;
import com.cax.cax_backend.organization.repository.OrganizationMemberRepository;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BingoGameService {

    private final BingoGameRepository gameRepository;
    private final BingoPlayerCardRepository cardRepository;
    private final UserRepository userRepository;
    private final OrganizationMemberRepository memberRepository;
    private final MongoTemplate mongoTemplate;

    // A player's card is always a 5x5 grid (25 cells); the game's prompt pool can be
    // larger so each player draws a random 25-prompt subset instead of an identical card.
    private static final int GRID_SIZE = 25;
    private static final int MAX_PROMPTS = 400;
    // Payload caps — game codes are guessable-by-design short codes, so anything a
    // leader submits gets bounded before it's stored and echoed back to players.
    private static final int MAX_TITLE_LENGTH = 80;
    private static final int MAX_PROMPT_LENGTH = 200;

    private static final SecureRandom GAME_CODE_RANDOM = new SecureRandom();

    // All bingo line patterns for a 5x5 grid
    private static final int[][] BINGO_LINES = {
        {0,1,2,3,4}, {5,6,7,8,9}, {10,11,12,13,14}, {15,16,17,18,19}, {20,21,22,23,24},
        {0,5,10,15,20}, {1,6,11,16,21}, {2,7,12,17,22}, {3,8,13,18,23}, {4,9,14,19,24},
        {0,6,12,18,24}, {4,8,12,16,20}
    };

    // ── Dynamic leaderboard scoring ──────────────────────────────────────────
    // Every mark event pays a guaranteed reward to both the marker and the signer, plus
    // a scarcity bonus that's shared out across everyone who's used that same signer so
    // far — so one player using a signer someone else already used dilutes the bonus
    // portion of that earlier mark the next time the leaderboard is computed. The base
    // rewards are flat and never diluted, so scanning and being scanned are always worth it.
    private static final double MARK_BASE_POINTS = 100.00;
    private static final double SIGNER_REWARD_POINTS = 50.00;
    private static final double SCARCITY_BONUS_POOL = 50.00;
    private static final double LINE_BONUS_POINTS = 100.00;
    private static final double BINGO_BONUS_POINTS = 200.00;

    public BingoGame createGame(CreateBingoGameRequest req, String userId) {
        if (req.getOrganizationId() == null || req.getOrganizationId().isBlank()) {
            throw new BusinessException.BadRequestException("organizationId is required");
        }
        if (req.getPrompts() == null || req.getPrompts().size() < GRID_SIZE || req.getPrompts().size() > MAX_PROMPTS) {
            throw new BusinessException.BadRequestException(
                    "Between " + GRID_SIZE + " and " + MAX_PROMPTS + " prompts are required");
        }
        if (req.getMaxSignerUsesPerGame() != null && req.getMaxSignerUsesPerGame() < 1) {
            throw new BusinessException.BadRequestException("maxSignerUsesPerGame must be at least 1");
        }
        // Trim and bound every prompt — the UI enforces this too, but the API must not
        // trust it: an oversized payload would otherwise be stored and echoed to every player.
        List<String> prompts = new ArrayList<>(req.getPrompts().size());
        for (String p : req.getPrompts()) {
            String trimmed = p == null ? "" : p.trim();
            if (trimmed.isEmpty()) {
                throw new BusinessException.BadRequestException("Prompts cannot be blank");
            }
            if (trimmed.length() > MAX_PROMPT_LENGTH) {
                throw new BusinessException.BadRequestException(
                        "Prompts must be at most " + MAX_PROMPT_LENGTH + " characters");
            }
            prompts.add(trimmed);
        }
        String title = req.getTitle() != null && !req.getTitle().isBlank() ? req.getTitle().trim() : "Human Bingo";
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new BusinessException.BadRequestException(
                    "Title must be at most " + MAX_TITLE_LENGTH + " characters");
        }
        assertOrgLeader(req.getOrganizationId(), userId);
        BingoGame game = BingoGame.builder()
                .gameCode(generateGameCode())
                .title(title)
                .prompts(prompts)
                .maxSignerUsesPerGame(req.getMaxSignerUsesPerGame())
                .status(BingoGame.GameStatus.LOBBY)
                .createdBy(userId)
                .organizationId(req.getOrganizationId())
                .build();
        return gameRepository.save(game);
    }

    @CacheEvict(value = "bingoGame", key = "#gameCode")
    public BingoGame startGame(String gameCode, String userId) {
        BingoGame game = getGameOrThrow(gameCode);
        assertOrgLeader(game.getOrganizationId(), userId);
        if (game.getStatus() != BingoGame.GameStatus.LOBBY) {
            throw new BusinessException.BadRequestException("Game is not in LOBBY state");
        }
        game.setStatus(BingoGame.GameStatus.ACTIVE);
        game.setStartedAt(Instant.now());
        return gameRepository.save(game);
    }

    @CacheEvict(value = "bingoGame", key = "#gameCode")
    public BingoGame endGame(String gameCode, String userId) {
        BingoGame game = getGameOrThrow(gameCode);
        assertOrgLeader(game.getOrganizationId(), userId);
        if (game.getStatus() != BingoGame.GameStatus.ACTIVE) {
            throw new BusinessException.BadRequestException("Game is not ACTIVE");
        }
        game.setStatus(BingoGame.GameStatus.ENDED);
        game.setEndedAt(Instant.now());
        return gameRepository.save(game);
    }

    public List<BingoGame> getGamesByOrganization(String organizationId, String userId) {
        assertOrgLeader(organizationId, userId);
        return gameRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId);
    }

    /** Public lobby-polling lookup. Cached briefly so thousands of players polling the
     *  same game code hit Caffeine, not Mongo; start/end evict so status flips show fast. */
    @Cacheable(value = "bingoGame", key = "#gameCode")
    public BingoGame getGame(String gameCode) {
        return getGameOrThrow(gameCode);
    }

    @Cacheable(value = "bingoPlayerCount", key = "#gameCode")
    public long getPlayerCount(String gameCode) {
        return cardRepository.countByGameCode(gameCode);
    }

    public List<BingoGame> getAllGames() {
        return gameRepository.findAll();
    }

    @CacheEvict(value = "bingoPlayerCount", key = "#gameCode")
    public BingoPlayerCard joinGame(String gameCode, String caxId) {
        BingoGame game = getGameOrThrow(gameCode);
        // Idempotent — return existing card without re-checking status
        if (cardRepository.existsByGameCodeAndCaxId(gameCode, caxId)) {
            return resolveDisplayNames(cardRepository.findByGameCodeAndCaxId(gameCode, caxId).get());
        }
        // New join: only allowed while game is in LOBBY
        if (game.getStatus() == BingoGame.GameStatus.ACTIVE) {
            throw new BusinessException.BadRequestException("Game has already started — new players cannot join");
        }
        if (game.getStatus() == BingoGame.GameStatus.ENDED) {
            throw new BusinessException.BadRequestException("This game has ended");
        }

        User user = userRepository.findByCaxId(caxId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("User", caxId));

        // Full Fisher-Yates shuffle then take the first GRID_SIZE — an unbiased random
        // sample of 25 prompts (already in random order) drawn from the game's pool,
        // which may hold up to MAX_PROMPTS entries.
        List<String> shuffled = new ArrayList<>(game.getPrompts());
        Collections.shuffle(shuffled);
        shuffled = new ArrayList<>(shuffled.subList(0, Math.min(GRID_SIZE, shuffled.size())));

        String collegeName = user.getCollegeDetails() != null ? user.getCollegeDetails().getCollegeName() : null;

        BingoPlayerCard card = BingoPlayerCard.builder()
                .gameCode(gameCode)
                .caxId(caxId)
                .playerName(user.getThoughtsDisplayName())
                .collegeName(collegeName)
                .grid(shuffled)
                .build();
        return resolveDisplayNames(cardRepository.save(card));
    }

    public BingoPlayerCard getPlayerCard(String gameCode, String caxId) {
        return resolveDisplayNames(cardRepository.findByGameCodeAndCaxId(gameCode, caxId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Player card")));
    }

    @Caching(evict = {
            @CacheEvict(value = "bingoLeaderboard", key = "#gameCode"),
            @CacheEvict(value = "bingoSignerUsage", key = "#gameCode")
    })
    public BingoPlayerCard markCell(String gameCode, String caxId, MarkCellRequest req) {
        BingoGame game = getGameOrThrow(gameCode);
        if (game.getStatus() != BingoGame.GameStatus.ACTIVE) {
            throw new BusinessException.BadRequestException("Game is not active");
        }

        BingoPlayerCard card = cardRepository.findByGameCodeAndCaxId(gameCode, caxId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Player card"));

        int cellIndex = req.getCellIndex();
        if (cellIndex < 0 || cellIndex > 24) {
            throw new BusinessException.BadRequestException("Invalid cell index");
        }

        String signerCaxId = req.getSignerCaxId();
        if (signerCaxId == null || signerCaxId.isBlank()) {
            throw new BusinessException.BadRequestException("signerCaxId is required");
        }
        if (signerCaxId.equals(caxId)) {
            throw new BusinessException.BadRequestException("Cannot sign your own cell");
        }

        // Friendly pre-checks — these give players a specific error message. The actual
        // enforcement is the atomic findAndModify below, which is race-safe under
        // concurrent requests from the same player (double-tap / two devices).
        boolean alreadyMarked = card.getMarkedCells().stream()
                .anyMatch(m -> m.getCellIndex() == cellIndex);
        if (alreadyMarked) {
            throw new BusinessException.ResourceConflictException("Cell already marked");
        }

        if (!cardRepository.existsByGameCodeAndCaxId(gameCode, signerCaxId)) {
            throw new BusinessException.BadRequestException("Signer is not in this game");
        }

        boolean signerAlreadyUsed = card.getMarkedCells().stream()
                .anyMatch(m -> m.getSignerCaxId().equals(signerCaxId));
        if (signerAlreadyUsed) {
            throw new BusinessException.ResourceConflictException("This person has already been used to mark another cell");
        }

        if (game.getMaxSignerUsesPerGame() != null) {
            long signerUsesInGame = cardRepository.countByGameCodeAndMarkedCells_SignerCaxId(gameCode, signerCaxId);
            if (signerUsesInGame >= game.getMaxSignerUsesPerGame()) {
                throw new BusinessException.ResourceConflictException(
                        "This person has reached the maximum number of times they can be used in this game");
            }
        }

        User signer = userRepository.findByCaxId(signerCaxId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Signer", signerCaxId));

        BingoPlayerCard.CellMark mark = BingoPlayerCard.CellMark.builder()
                .cellIndex(cellIndex)
                .signerCaxId(signerCaxId)
                .signerName(signer.getThoughtsDisplayName())
                .markedAt(Instant.now())
                .build();

        // Atomic guarded push: only appends the mark if no existing element on this card
        // already has this cellIndex or this signer ($ne on an array field means "no
        // element equals"). A concurrent duplicate request matches nothing and gets null
        // back instead of writing a second mark — no read-modify-write race.
        Query guard = Query.query(Criteria.where("gameCode").is(gameCode)
                .and("caxId").is(caxId)
                .and("markedCells.cellIndex").ne(cellIndex)
                .and("markedCells.signerCaxId").ne(signerCaxId));
        Update push = new Update().push("markedCells", mark);
        BingoPlayerCard updated = mongoTemplate.findAndModify(
                guard, push, FindAndModifyOptions.options().returnNew(true), BingoPlayerCard.class);
        if (updated == null) {
            throw new BusinessException.ResourceConflictException("Cell already marked");
        }

        // Recompute derived fields from the post-push document and persist them.
        int completedLines = countCompletedLines(updated.getMarkedCells());
        updated.setMarkedCount(updated.getMarkedCells().size());
        updated.setCompletedLines(completedLines);
        updated.setBingo(completedLines > 0);
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(updated.getId())),
                new Update()
                        .set("markedCount", updated.getMarkedCount())
                        .set("completedLines", updated.getCompletedLines())
                        .set("bingo", updated.isBingo()),
                BingoPlayerCard.class);

        return resolveDisplayNames(updated);
    }

    /** Returns, for every caxId that has signed at least one cell in this game, how many
     *  cards they've signed and (if set) the game's per-signer cap. Sorted by count desc.
     *  Cached briefly — every player polls this while the game is live. */
    @Cacheable(value = "bingoSignerUsage", key = "#gameCode")
    public List<SignerUsageStat> getSignerUsageStats(String gameCode) {
        BingoGame game = getGameOrThrow(gameCode);
        List<BingoPlayerCard> cards = cardRepository.findByGameCode(gameCode);

        Map<String, Long> countsByCaxId = countSignerUses(cards);
        Map<String, String> namesByCaxId = displayNamesFor(countsByCaxId.keySet());

        return countsByCaxId.entrySet().stream()
                .map(e -> SignerUsageStat.builder()
                        .caxId(e.getKey())
                        .name(namesByCaxId.get(e.getKey()))
                        .count(e.getValue())
                        .maxAllowed(game.getMaxSignerUsesPerGame())
                        .build())
                .sorted(Comparator.comparingLong(SignerUsageStat::getCount).reversed())
                .toList();
    }

    /** Counts, across all cards in the given list, how many times each caxId has been
     *  used as a signer. Shared by the signer-usage-stats view and the live scoring. */
    private Map<String, Long> countSignerUses(List<BingoPlayerCard> cards) {
        Map<String, Long> counts = new HashMap<>();
        for (BingoPlayerCard card : cards) {
            for (BingoPlayerCard.CellMark mark : card.getMarkedCells()) {
                counts.merge(mark.getSignerCaxId(), 1L, Long::sum);
            }
        }
        return counts;
    }

    /** Dynamic score for one card: guaranteed base + signer reward + a scarcity bonus
     *  that's split across everyone who's used the same signer, so it's recomputed live
     *  from current game-wide usage rather than stored — one player's new mark can change
     *  the score another player sees the next time the leaderboard is fetched. */
    private double computeScore(BingoPlayerCard card, Map<String, Long> signerUseCounts) {
        double markerPoints = 0;
        for (BingoPlayerCard.CellMark mark : card.getMarkedCells()) {
            long uses = signerUseCounts.getOrDefault(mark.getSignerCaxId(), 1L);
            markerPoints += MARK_BASE_POINTS + (SCARCITY_BONUS_POOL / uses);
        }
        double signerPoints = SIGNER_REWARD_POINTS * signerUseCounts.getOrDefault(card.getCaxId(), 0L);
        double lineBonus = LINE_BONUS_POINTS * card.getCompletedLines();
        double bingoBonus = card.isBingo() ? BINGO_BONUS_POINTS : 0;

        double total = markerPoints + signerPoints + lineBonus + bingoBonus;
        return Math.round(total * 100.0) / 100.0;
    }

    @Cacheable(value = "bingoLeaderboard", key = "#gameCode")
    public List<BingoLeaderboardEntry> getLeaderboard(String gameCode) {
        getGameOrThrow(gameCode);
        List<BingoPlayerCard> cards = cardRepository.findByGameCode(gameCode);
        resolveDisplayNames(cards);

        Map<String, Long> signerUseCounts = countSignerUses(cards);

        return cards.stream()
                .map(card -> new BingoLeaderboardEntry(card, computeScore(card, signerUseCounts)))
                .sorted(Comparator.comparingDouble(BingoLeaderboardEntry::getScore).reversed()
                        .thenComparing(Comparator.comparingInt(BingoLeaderboardEntry::getMarkedCount).reversed())
                        .thenComparing(Comparator.comparingInt(BingoLeaderboardEntry::getCompletedLines).reversed()))
                .toList();
    }

    /** Returns games the user has already joined (their card exists), sorted newest first. */
    public List<BingoGame> getVisibleGames(String userId) {
        String caxId = getCaxIdByUserId(userId);
        List<BingoPlayerCard> myCards = cardRepository.findByCaxId(caxId);
        if (myCards.isEmpty()) return List.of();
        Set<String> myCodes = myCards.stream()
                .map(BingoPlayerCard::getGameCode)
                .collect(Collectors.toSet());
        return gameRepository.findByGameCodeIn(myCodes).stream()
                .sorted(Comparator.comparing(BingoGame::getCreatedAt).reversed())
                .toList();
    }

    /** Looks up the caxId for an authenticated user by their internal userId. */
    public String getCaxIdByUserId(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("User", userId));
        if (user.getCaxId() == null || user.getCaxId().isBlank()) {
            throw new BusinessException.BadRequestException("Your account does not have a CAX ID assigned yet");
        }
        return user.getCaxId();
    }

    private int countCompletedLines(List<BingoPlayerCard.CellMark> marks) {
        Set<Integer> markedIndices = new HashSet<>();
        marks.forEach(m -> markedIndices.add(m.getCellIndex()));
        int lines = 0;
        for (int[] line : BINGO_LINES) {
            boolean complete = true;
            for (int idx : line) {
                if (!markedIndices.contains(idx)) { complete = false; break; }
            }
            if (complete) lines++;
        }
        return lines;
    }

    /** Throws 403 if the given userId is not President or Vice President of the organization. */
    private void assertOrgLeader(String organizationId, String userId) {
        if (organizationId == null || userId == null) {
            throw new BusinessException.BadRequestException("Invalid organization or user");
        }
        OrganizationMember member = memberRepository
                .findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new BusinessException.ForbiddenException("You are not a member of this organization"));
        String role = member.getRole();
        if (!"President".equalsIgnoreCase(role) && !"Vice President".equalsIgnoreCase(role)) {
            throw new BusinessException.ForbiddenException("Only the President or Vice President can manage games");
        }
    }

    private BingoGame getGameOrThrow(String gameCode) {
        return gameRepository.findByGameCode(gameCode)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Game", gameCode));
    }

    private String generateGameCode() {
        // SecureRandom: game codes gate public read endpoints, so they must not be
        // predictable from previously issued codes the way java.util.Random output is.
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        String code;
        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) sb.append(chars.charAt(GAME_CODE_RANDOM.nextInt(chars.length())));
            code = sb.toString();
        } while (gameRepository.findByGameCode(code).isPresent());
        return code;
    }

    /** One batched query for the display names of the given caxIds. */
    private Map<String, String> displayNamesFor(Collection<String> caxIds) {
        if (caxIds.isEmpty()) return Map.of();
        return userRepository.findByCaxIdIn(caxIds).stream()
                .filter(u -> u.getCaxId() != null && u.getThoughtsDisplayName() != null)
                .collect(Collectors.toMap(User::getCaxId, User::getThoughtsDisplayName, (a, b) -> a));
    }

    private BingoPlayerCard resolveDisplayNames(BingoPlayerCard card) {
        if (card == null) return null;
        resolveDisplayNames(List.of(card));
        return card;
    }

    /** Fills in current player/signer display names for all cards with a single user
     *  query, instead of one lookup per card + per mark (the old N+1 pattern that made
     *  the leaderboard cost hundreds of Mongo round-trips per fetch). */
    private void resolveDisplayNames(List<BingoPlayerCard> cards) {
        Set<String> caxIds = new HashSet<>();
        for (BingoPlayerCard card : cards) {
            caxIds.add(card.getCaxId());
            if (card.getMarkedCells() != null) {
                for (BingoPlayerCard.CellMark mark : card.getMarkedCells()) {
                    caxIds.add(mark.getSignerCaxId());
                }
            }
        }
        caxIds.remove(null);
        Map<String, String> names = displayNamesFor(caxIds);
        for (BingoPlayerCard card : cards) {
            String playerName = names.get(card.getCaxId());
            if (playerName != null) card.setPlayerName(playerName);
            if (card.getMarkedCells() != null) {
                for (BingoPlayerCard.CellMark mark : card.getMarkedCells()) {
                    String signerName = names.get(mark.getSignerCaxId());
                    if (signerName != null) mark.setSignerName(signerName);
                }
            }
        }
    }
}
