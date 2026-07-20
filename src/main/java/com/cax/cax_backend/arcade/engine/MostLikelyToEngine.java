package com.cax.cax_backend.arcade.engine;

import com.cax.cax_backend.arcade.dto.ArcadeRequests;
import com.cax.cax_backend.arcade.dto.ArcadeStateResponse;
import com.cax.cax_backend.arcade.model.*;
import com.cax.cax_backend.arcade.service.ArcadePromptService;
import com.cax.cax_backend.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * <b>Most Likely To.</b> One prompt goes up on every phone, everyone votes for a person, and
 * the winner is revealed.
 *
 * <p>There is nothing to write, so this game has no submission phase — it opens straight into
 * voting. It also has no secrets: the prompt is public by design and the only thing withheld
 * is the running tally, held back until the reveal so that voting late does not mean voting
 * with more information than voting early.
 *
 * <p>Self-voting is allowed here on purpose. "Most likely to fall asleep in class" is funnier
 * when someone owns it, and there is no exploit in it — a self-vote is worth exactly what any
 * other single vote is worth.
 */
@Component
@RequiredArgsConstructor
public class MostLikelyToEngine extends AbstractArcadeEngine {

    private final ArcadePromptService promptService;

    /** Points the nominee earns per vote received. */
    private static final int POINTS_PER_VOTE = 30;

    /** Bonus for being the round's most-voted player. */
    private static final int CROWN_BONUS = 100;

    /** Consolation for voters who picked the eventual winner — rewards reading the room. */
    private static final int READ_THE_ROOM = 20;

    @Override
    public ArcadeGameType type() {
        return ArcadeGameType.MOST_LIKELY_TO;
    }

    @Override
    public boolean hasSubmissionPhase() {
        return false;
    }

    @Override
    public void prepareRound(ArcadeSession session, ArcadeRound round, List<ArcadeParticipant> players) {
        ArcadePrompt prompt = promptService.drawFor(session);
        round.setPrompt(prompt.getText());
        session.getUsedPromptIds().add(prompt.getId());
    }

    @Override
    public String normaliseSubmission(String raw) {
        // Reachable only if a client posts to the submit endpoint for a game that has no
        // submission phase. Refused rather than silently ignored, so a tampered client gets a
        // clear error instead of appearing to have submitted something.
        throw new BusinessException.BadRequestException("This game has nothing to submit");
    }

    @Override
    public void validateVote(ArcadeRound round,
                             List<ArcadeParticipant> players,
                             List<ArcadeSubmission> submissions,
                             String voterCaxId,
                             ArcadeRequests.Vote request) {
        // Membership of the target is checked centrally by the session service; there are no
        // additional restrictions in this game.
    }

    @Override
    public RoundOutcome scoreRound(ArcadeRound round,
                                   List<ArcadeParticipant> players,
                                   List<ArcadeSubmission> submissions,
                                   List<ArcadeVote> votes) {
        Map<String, String> names = namesOf(players);
        Map<String, Integer> counts = countVotes(votes);
        List<String> leaders = topVoted(counts);

        RoundOutcome outcome = RoundOutcome.builder().winnerCaxIds(leaders).build();

        if (leaders.isEmpty()) {
            outcome.setSummary("Nobody voted this round.");
            return outcome;
        }

        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            String caxId = entry.getKey();
            int received = entry.getValue();
            boolean crowned = leaders.contains(caxId);
            int points = received * POINTS_PER_VOTE + (crowned ? CROWN_BONUS : 0);
            outcome.award(caxId, points,
                    crowned ? "Most votes" : received + (received == 1 ? " vote" : " votes"));
        }

        for (ArcadeVote vote : votes) {
            if (leaders.contains(vote.getTargetCaxId())) {
                vote.setCorrect(true);
                outcome.award(vote.getVoterCaxId(), READ_THE_ROOM, "Read the room");
            }
        }

        int topCount = counts.get(leaders.get(0));
        outcome.setSummary(leaders.size() == 1
                ? joinNames(leaders, names) + " — with " + topCount
                        + (topCount == 1 ? " vote." : " votes.")
                : "It's a tie: " + joinNames(leaders, names) + ".");
        return outcome;
    }

    @Override
    public ArcadeStateResponse.RoundView buildRoundView(ArcadeSession session,
                                                        ArcadeRound round,
                                                        List<ArcadeParticipant> players,
                                                        List<ArcadeSubmission> submissions,
                                                        List<ArcadeVote> votes,
                                                        String viewerCaxId) {
        ArcadeStateResponse.RoundView.RoundViewBuilder view = ArcadeStateResponse.RoundView.builder()
                .roundNo(round.getRoundNo())
                .prompt(round.getPrompt());

        // Tallies and winners exist only after the reveal. While voting is open every viewer
        // gets the same content-free payload, so there is no running count to peek at.
        if (round.isRevealed()) {
            view.tallies(buildTallies(votes, namesOf(players)))
                    .winnerCaxIds(round.getWinnerCaxIds())
                    .outcomeSummary(round.getOutcomeSummary());
        }
        return view.build();
    }
}
