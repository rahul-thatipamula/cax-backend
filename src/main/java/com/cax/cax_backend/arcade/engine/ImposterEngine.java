package com.cax.cax_backend.arcade.engine;

import com.cax.cax_backend.arcade.dto.ArcadeRequests;
import com.cax.cax_backend.arcade.dto.ArcadeStateResponse;
import com.cax.cax_backend.arcade.model.*;
import com.cax.cax_backend.arcade.service.ArcadePromptService;
import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.common.util.ProfanityFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.*;

/**
 * <b>Odd One Out.</b> Everyone gets the same secret word except one imposter, who gets only a
 * category. Each player gives a one-word clue, then the group votes on who was faking.
 *
 * <p>This game has the strictest redaction rules in the Arcade, because two fields decide it
 * outright: the secret word and the imposter's identity. Both are chosen server-side and
 * placed into a payload only for the viewer entitled to them — the imposter's own state
 * response contains no {@code secretWord} key at all, and no player's response contains
 * {@code imposterCaxId} until the reveal. Anyone inspecting their own network traffic learns
 * exactly what the game intends them to know.
 *
 * <p>Clues are capped at a single word for the same reason the paper version is: a sentence
 * lets a knowing player prove they know, which collapses the deduction.
 */
@Component
@RequiredArgsConstructor
public class ImposterEngine extends AbstractArcadeEngine {

    private final ArcadePromptService promptService;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final int MAX_CLUE_LENGTH = 24;

    /** Awarded to each player who correctly fingered the imposter. */
    private static final int CAUGHT_THEM = 100;

    /** Awarded to the imposter for surviving the vote. */
    private static final int GOT_AWAY = 200;

    /** Consolation for spotting the imposter even though the room voted someone else out. */
    private static final int RIGHT_BUT_OUTVOTED = 50;

    @Override
    public ArcadeGameType type() {
        return ArcadeGameType.IMPOSTER;
    }

    @Override
    public boolean hasSubmissionPhase() {
        return true;
    }

    @Override
    public void prepareRound(ArcadeSession session, ArcadeRound round, List<ArcadeParticipant> players) {
        ArcadePrompt word = promptService.drawFor(session);
        round.setSecretWord(word.getText());
        round.setSecretCategory(word.getCategory());
        session.getUsedPromptIds().add(word.getId());

        // The imposter is drawn from players who are actually present. Picking someone who has
        // dropped out would produce a round nobody can win, since the imposter would never
        // submit a clue and the vote would be meaningless.
        List<ArcadeParticipant> eligible = players.stream()
                .filter(p -> p.getLeftAt() == null)
                .toList();
        List<ArcadeParticipant> pool = eligible.isEmpty() ? players : eligible;
        round.setImposterCaxId(pool.get(RANDOM.nextInt(pool.size())).getCaxId());
    }

    @Override
    public String normaliseSubmission(String raw) {
        String clue = raw == null ? "" : raw.trim();
        if (clue.isEmpty()) {
            throw new BusinessException.BadRequestException("Give a one-word clue");
        }
        // One word only. Enforced here because the rule is the game balance, not a UI nicety —
        // a client that lets someone paste a whole sentence would break the round for everyone.
        if (clue.matches(".*\\s.*")) {
            throw new BusinessException.BadRequestException("One word only");
        }
        if (clue.length() > MAX_CLUE_LENGTH) {
            throw new BusinessException.BadRequestException(
                    "Clues must be at most " + MAX_CLUE_LENGTH + " characters");
        }
        if (ProfanityFilter.isOffensive(clue)) {
            throw new BusinessException.BadRequestException("Let's keep it clean — try another clue");
        }
        return clue;
    }

    @Override
    public void validateVote(ArcadeRound round,
                             List<ArcadeParticipant> players,
                             List<ArcadeSubmission> submissions,
                             String voterCaxId,
                             ArcadeRequests.Vote request) {
        // Voting for yourself is refused: the imposter could otherwise self-accuse to muddy
        // the tally, and for everyone else it is a wasted ballot that only distorts the round.
        if (voterCaxId.equals(request.getTargetCaxId())) {
            throw new BusinessException.BadRequestException("You can't vote for yourself");
        }
    }

    @Override
    public RoundOutcome scoreRound(ArcadeRound round,
                                   List<ArcadeParticipant> players,
                                   List<ArcadeSubmission> submissions,
                                   List<ArcadeVote> votes) {
        Map<String, String> names = namesOf(players);
        String imposter = round.getImposterCaxId();

        Map<String, Integer> counts = countVotes(votes);
        List<String> leaders = topVoted(counts);

        // A tie counts as the imposter escaping: the room failed to settle on them, which is
        // the same practical outcome as voting for the wrong person.
        boolean caught = leaders.size() == 1 && leaders.get(0).equals(imposter);

        RoundOutcome outcome = new RoundOutcome();

        for (ArcadeVote vote : votes) {
            boolean spottedThem = imposter.equals(vote.getTargetCaxId());
            vote.setCorrect(spottedThem);
            if (spottedThem) {
                outcome.award(vote.getVoterCaxId(),
                        caught ? CAUGHT_THEM : RIGHT_BUT_OUTVOTED,
                        caught ? "Caught the imposter" : "You knew");
            }
        }

        if (caught) {
            outcome.setWinnerCaxIds(votes.stream()
                    .filter(ArcadeVote::isCorrect)
                    .map(ArcadeVote::getVoterCaxId)
                    .distinct()
                    .toList());
            outcome.setSummary(nameOf(names, imposter) + " was the imposter — and got caught.");
        } else {
            outcome.award(imposter, GOT_AWAY, "Got away with it");
            outcome.setWinnerCaxIds(List.of(imposter));
            outcome.setSummary(nameOf(names, imposter) + " was the imposter — and got away with it.");
        }
        return outcome;
    }

    @Override
    public ArcadeStateResponse.RoundView buildRoundView(ArcadeSession session,
                                                        ArcadeRound round,
                                                        List<ArcadeParticipant> players,
                                                        List<ArcadeSubmission> submissions,
                                                        List<ArcadeVote> votes,
                                                        String viewerCaxId) {
        Map<String, String> names = namesOf(players);
        boolean revealed = round.isRevealed();
        boolean viewerIsImposter = round.getImposterCaxId() != null
                && round.getImposterCaxId().equals(viewerCaxId);

        ArcadeStateResponse.RoundView.RoundViewBuilder view = ArcadeStateResponse.RoundView.builder()
                .roundNo(round.getRoundNo())
                .viewerIsImposter(viewerIsImposter)
                .secretCategory(round.getSecretCategory());

        // The single most important line in the Arcade: the imposter's payload never carries
        // the word. Once the round is revealed it is safe to send to everyone.
        if (revealed || !viewerIsImposter) {
            view.secretWord(round.getSecretWord());
        }

        // Clues are public once everyone has written theirs — that is the material the vote is
        // based on — but stay hidden during SUBMITTING so nobody can crib another player's clue.
        if (revealed || session.getPhase() == ArcadePhase.VOTING) {
            List<ArcadeStateResponse.SubmissionView> clues = new ArrayList<>(submissions.size());
            for (ArcadeSubmission s : submissions) {
                // Clue authorship is public in this game by design — knowing who said what is
                // precisely what the group reasons over.
                clues.add(ArcadeStateResponse.SubmissionView.builder()
                        .id(s.getId())
                        .content(s.getContent())
                        .authorCaxId(s.getCaxId())
                        .authorName(nameOf(names, s.getCaxId()))
                        .mine(s.getCaxId().equals(viewerCaxId))
                        .build());
            }
            view.submissions(clues);
        }

        if (revealed) {
            view.imposterCaxId(round.getImposterCaxId())
                    .tallies(buildTallies(votes, names))
                    .winnerCaxIds(round.getWinnerCaxIds())
                    .outcomeSummary(round.getOutcomeSummary());
        }
        return view.build();
    }
}
