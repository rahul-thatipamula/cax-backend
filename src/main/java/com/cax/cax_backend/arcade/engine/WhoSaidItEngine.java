package com.cax.cax_backend.arcade.engine;

import com.cax.cax_backend.arcade.dto.ArcadeRequests;
import com.cax.cax_backend.arcade.dto.ArcadeStateResponse;
import com.cax.cax_backend.arcade.model.*;
import com.cax.cax_backend.arcade.service.ArcadePromptService;
import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.common.util.ProfanityFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * <b>Who Said It.</b> Everyone secretly answers a prompt, all answers appear anonymously, and
 * the group guesses who wrote which. Points for guessing right, and for fooling someone.
 *
 * <p>The whole game is one withheld field. {@code ArcadeSubmission.caxId} is the author link,
 * and until the round reveals it is never placed into any payload — not even flagged and
 * hidden. The one exception is the viewer's own submission, marked with {@code mine} so a
 * player who rejoined mid-round can see what they already wrote and is not offered the chance
 * to guess themselves.
 *
 * <p>Answers are shuffled with a per-round seed rather than left in insertion order, because
 * arrival order correlates with who types fast and would leak authorship on its own.
 */
@Component
@RequiredArgsConstructor
public class WhoSaidItEngine extends AbstractArcadeEngine {

    private final ArcadePromptService promptService;

    private static final int MAX_ANSWER_LENGTH = 120;
    private static final int MIN_ANSWER_LENGTH = 1;

    /** Correctly attributing an answer to its author. */
    private static final int CORRECT_GUESS = 100;

    /** Earned by an author each time someone attributes their answer to the wrong person. */
    private static final int FOOLED_SOMEONE = 60;

    @Override
    public ArcadeGameType type() {
        return ArcadeGameType.WHO_SAID_IT;
    }

    @Override
    public boolean hasSubmissionPhase() {
        return true;
    }

    @Override
    public void prepareRound(ArcadeSession session, ArcadeRound round, List<ArcadeParticipant> players) {
        ArcadePrompt prompt = promptService.drawFor(session);
        round.setPrompt(prompt.getText());
        session.getUsedPromptIds().add(prompt.getId());
    }

    @Override
    public String normaliseSubmission(String raw) {
        String text = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (text.length() < MIN_ANSWER_LENGTH) {
            throw new BusinessException.BadRequestException("Write something first");
        }
        if (text.length() > MAX_ANSWER_LENGTH) {
            throw new BusinessException.BadRequestException(
                    "Keep it under " + MAX_ANSWER_LENGTH + " characters");
        }
        // Answers are broadcast to every phone in the room, so they go through the same
        // moderation gate as any other user-authored content in the app.
        if (ProfanityFilter.isOffensive(text)) {
            throw new BusinessException.BadRequestException("Let's keep it clean — try another answer");
        }
        return text;
    }

    @Override
    public void validateVote(ArcadeRound round,
                             List<ArcadeParticipant> players,
                             List<ArcadeSubmission> submissions,
                             String voterCaxId,
                             ArcadeRequests.Vote request) {
        String submissionId = request.getTargetSubmissionId();
        if (submissionId == null || submissionId.isBlank()) {
            throw new BusinessException.BadRequestException("Pick an answer to guess");
        }

        ArcadeSubmission target = submissions.stream()
                .filter(s -> s.getId().equals(submissionId))
                .findFirst()
                .orElseThrow(() -> new BusinessException.BadRequestException(
                        "That answer is not part of this round"));

        // Guessing your own answer would be a free 100 points, so it is refused server-side
        // rather than merely hidden by the UI.
        if (target.getCaxId().equals(voterCaxId)) {
            throw new BusinessException.BadRequestException("That's your own answer");
        }
    }

    @Override
    public RoundOutcome scoreRound(ArcadeRound round,
                                   List<ArcadeParticipant> players,
                                   List<ArcadeSubmission> submissions,
                                   List<ArcadeVote> votes) {
        Map<String, String> names = namesOf(players);
        Map<String, String> authorBySubmission = new HashMap<>();
        for (ArcadeSubmission s : submissions) {
            authorBySubmission.put(s.getId(), s.getCaxId());
        }

        RoundOutcome outcome = new RoundOutcome();
        List<String> correctGuessers = new ArrayList<>();

        for (ArcadeVote vote : votes) {
            String trueAuthor = authorBySubmission.get(vote.getTargetSubmissionId());
            if (trueAuthor == null) continue;

            boolean correct = trueAuthor.equals(vote.getTargetCaxId());
            vote.setCorrect(correct);

            if (correct) {
                correctGuessers.add(vote.getVoterCaxId());
                outcome.award(vote.getVoterCaxId(), CORRECT_GUESS, "Nailed it");
            } else {
                // The author is rewarded for writing something that did not sound like them.
                outcome.award(trueAuthor, FOOLED_SOMEONE, "Fooled someone");
            }
        }

        outcome.setWinnerCaxIds(correctGuessers);
        outcome.setSummary(correctGuessers.isEmpty()
                ? "Nobody guessed right — everyone kept their cover."
                : joinNames(correctGuessers, names)
                        + (correctGuessers.size() == 1 ? " guessed right." : " guessed right."));
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

        ArcadeStateResponse.RoundView.RoundViewBuilder view = ArcadeStateResponse.RoundView.builder()
                .roundNo(round.getRoundNo())
                .prompt(round.getPrompt());

        // Answers stay hidden entirely while people are still writing — seeing what others
        // wrote before you write would let a player mimic someone else's voice on purpose.
        if (round.isRevealed() || session.getPhase() == ArcadePhase.VOTING) {
            List<ArcadeSubmission> ordered = shuffledForRound(submissions, round);
            List<ArcadeStateResponse.SubmissionView> views = new ArrayList<>(ordered.size());

            for (ArcadeSubmission s : ordered) {
                boolean mine = s.getCaxId().equals(viewerCaxId);
                views.add(ArcadeStateResponse.SubmissionView.builder()
                        .id(s.getId())
                        .content(s.getContent())
                        .mine(mine)
                        // Authorship is attached only at reveal. Before that the fields are
                        // absent from the JSON, not merely blanked.
                        .authorCaxId(revealed ? s.getCaxId() : null)
                        .authorName(revealed ? nameOf(names, s.getCaxId()) : null)
                        .build());
            }
            view.submissions(views);
        }

        if (revealed) {
            view.tallies(buildTallies(votes, names))
                    .winnerCaxIds(round.getWinnerCaxIds())
                    .outcomeSummary(round.getOutcomeSummary());
        }
        return view.build();
    }

    /**
     * A stable shuffle keyed on the round id: identical for every viewer and across polls, so
     * the list does not reorder under someone's finger mid-tap, but unrelated to who submitted
     * first.
     */
    private List<ArcadeSubmission> shuffledForRound(List<ArcadeSubmission> submissions, ArcadeRound round) {
        List<ArcadeSubmission> copy = new ArrayList<>(submissions);
        Collections.shuffle(copy, new Random(round.getId().hashCode()));
        return copy;
    }
}
