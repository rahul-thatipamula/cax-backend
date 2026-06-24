package com.cax.cax_backend.event.seeder;

import com.cax.cax_backend.event.model.Event;
import com.cax.cax_backend.event.repository.EventRepository;
import com.cax.cax_backend.club.repository.ClubRepository;
import com.cax.cax_backend.club.model.Club;
import com.cax.cax_backend.college.repository.CollegeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class EventMigrationSeeder implements CommandLineRunner {

    private final EventRepository eventRepository;
    private final ClubRepository clubRepository;
    private final CollegeRepository collegeRepository;

    @Override
    public void run(String... args) {
        List<Event> allEvents = eventRepository.findAll();
        List<Event> toUpdate = new ArrayList<>();

        for (Event event : allEvents) {
            boolean updated = false;

            // Initialize coordinators list if null/empty in DB
            if (event.getCoordinators() == null || event.getCoordinators().isEmpty()) {
                event.setCoordinators(new ArrayList<>());
                updated = true;
            }

            // Populate collegeId if missing
            if (event.getCollegeId() == null || event.getCollegeId().isBlank()) {
                if (event.getClubId() != null) {
                    try {
                        Club club = clubRepository.findById(event.getClubId()).orElse(null);
                        if (club != null) {
                            event.setCollegeId(club.getCollegeId());
                            updated = true;
                        }
                    } catch (Exception e) {
                        log.warn("Failed to find club {} for event migration: {}", event.getClubId(), e.getMessage());
                    }
                }
            }

            // Populate collegeName if missing
            if (event.getCollegeName() == null || event.getCollegeName().isBlank()) {
                if (event.getCollegeId() != null) {
                    try {
                        collegeRepository.findById(event.getCollegeId()).ifPresent(c -> {
                            event.setCollegeName(c.getCollegeName());
                        });
                        if (event.getCollegeName() != null) {
                            updated = true;
                        }
                    } catch (Exception e) {
                        log.warn("Failed to find college {} for event migration: {}", event.getCollegeId(), e.getMessage());
                    }
                }
            }

            if (updated) {
                toUpdate.add(event);
            }
        }

        if (!toUpdate.isEmpty()) {
            try {
                eventRepository.saveAll(toUpdate);
                log.info("Migrated {} existing events to initialize coordinators or college details.", toUpdate.size());
            } catch (Exception e) {
                log.error("Failed to migrate existing events: ", e);
            }
        } else {
            log.info("All existing events are already compatible with coordinators array and college details.");
        }
    }
}
