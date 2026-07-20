package com.cax.cax_backend.arcade.service;

import com.cax.cax_backend.arcade.model.ArcadeGameType;
import com.cax.cax_backend.arcade.model.ArcadePrompt;
import com.cax.cax_backend.arcade.repository.ArcadePromptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Seeds the starter prompt bank on first boot.
 *
 * <p>Idempotent by design: it seeds a game type only when that type has no rows at all, so a
 * restart never duplicates prompts and never overwrites additions made later through the
 * admin tooling.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArcadePromptSeeder implements CommandLineRunner {

    private final ArcadePromptRepository promptRepository;

    @Override
    public void run(String... args) {
        seed(ArcadeGameType.MOST_LIKELY_TO, mostLikelyTo(), null);
        seed(ArcadeGameType.WHO_SAID_IT, whoSaidIt(), null);
        seedWords();
    }

    private void seed(ArcadeGameType type, List<String> texts, String category) {
        if (promptRepository.countByGameType(type) > 0) return;

        List<ArcadePrompt> rows = new ArrayList<>(texts.size());
        for (String text : texts) {
            rows.add(ArcadePrompt.builder().gameType(type).text(text).category(category).build());
        }
        promptRepository.saveAll(rows);
        log.info("Seeded {} arcade prompts for {}", rows.size(), type);
    }

    private void seedWords() {
        if (promptRepository.countByGameType(ArcadeGameType.IMPOSTER) > 0) return;

        List<ArcadePrompt> rows = new ArrayList<>();
        addWords(rows, "Campus", "Library", "Canteen", "Hostel", "Lecture hall", "Parking lot",
                "Lab", "Auditorium", "Rooftop", "Bus stop", "Notice board");
        addWords(rows, "Food", "Maggi", "Samosa", "Cold coffee", "Biryani", "Chai",
                "Ice cream", "Pizza", "Momos", "Pani puri");
        addWords(rows, "Objects", "Charger", "Umbrella", "Headphones", "Water bottle",
                "Calculator", "Backpack", "Whiteboard", "Stapler", "Bicycle");
        addWords(rows, "Places", "Airport", "Beach", "Cinema", "Hospital", "Temple",
                "Gym", "Railway station", "Barber shop", "Zoo");
        addWords(rows, "Activities", "Cricket", "Dancing", "Sleeping", "Swimming",
                "Shopping", "Photography", "Cooking", "Trekking", "Gaming");

        promptRepository.saveAll(rows);
        log.info("Seeded {} arcade secret words", rows.size());
    }

    private void addWords(List<ArcadePrompt> rows, String category, String... words) {
        for (String word : words) {
            rows.add(ArcadePrompt.builder()
                    .gameType(ArcadeGameType.IMPOSTER)
                    .text(word)
                    .category(category)
                    .build());
        }
    }

    /**
     * Prompts are about the people in the room rather than about trivia — that is what makes
     * the game endless, since the answer changes with the group rather than running out.
     */
    private List<String> mostLikelyTo() {
        return List.of(
                "Most likely to fail this sem",
                "Most likely to marry first",
                "Most likely to become a millionaire",
                "Most likely to sleep through their own presentation",
                "Most likely to start a startup and drop out",
                "Most likely to be late to their own wedding",
                "Most likely to get placed first",
                "Most likely to cry during a placement interview",
                "Most likely to become a professor",
                "Most likely to move abroad and never come back",
                "Most likely to survive a zombie apocalypse",
                "Most likely to text their crush at 3am",
                "Most likely to forget their own birthday",
                "Most likely to argue with a professor and win",
                "Most likely to become internet famous",
                "Most likely to lose their phone tonight",
                "Most likely to top the class without attending",
                "Most likely to plan a trip and cancel it",
                "Most likely to adopt ten cats",
                "Most likely to still be in college five years from now",
                "Most likely to reply 'on my way' from bed",
                "Most likely to become a politician",
                "Most likely to get away with anything",
                "Most likely to fall asleep in the front row",
                "Most likely to eat the same thing every single day",
                "Most likely to run a marathon on a dare",
                "Most likely to know everyone's secrets",
                "Most likely to accidentally become the class representative",
                "Most likely to submit an assignment one minute before the deadline",
                "Most likely to make friends with a total stranger"
        );
    }

    private List<String> whoSaidIt() {
        return List.of(
                "Your worst subject, honestly",
                "Your biggest fear before placements",
                "The most embarrassing thing you've done on campus",
                "A hill you would genuinely die on",
                "The worst advice you've ever been given",
                "Something you pretend to understand but don't",
                "Your most useless talent",
                "The last lie you told a professor",
                "What you actually do during online classes",
                "The pettiest reason you've held a grudge",
                "Your most controversial food opinion",
                "Something you spent money on and regret",
                "The weirdest thing in your search history",
                "A rule you break constantly",
                "What you'd do with a free year and no consequences",
                "The compliment you want to hear most",
                "Your most irrational fear",
                "Something everyone likes that you secretly hate",
                "The worst haircut you've ever had",
                "What you'd change about this college",
                "The dumbest thing you've argued about",
                "Your go-to excuse for skipping class",
                "Something you're weirdly competitive about",
                "The last thing that made you genuinely laugh",
                "A talent you wish you had",
                "The most overrated thing about college life",
                "What you were like in school",
                "Something you'll never do again",
                "Your most unpopular opinion about movies",
                "The one thing you'd take from this campus if you could"
        );
    }
}
