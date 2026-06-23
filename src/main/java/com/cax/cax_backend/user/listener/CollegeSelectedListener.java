package com.cax.cax_backend.user.listener;

import java.time.Instant;
import java.util.Optional;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.cax.cax_backend.club.model.Club;
import com.cax.cax_backend.club.model.ClubMember;
import com.cax.cax_backend.club.repository.ClubMemberRepository;
import com.cax.cax_backend.club.repository.ClubRepository;
import com.cax.cax_backend.email.service.EmailService;
import com.cax.cax_backend.user.event.CollegeSelectedEvent;
import com.cax.cax_backend.user.model.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollegeSelectedListener {


    private final ClubRepository clubRepository;
    private final ClubMemberRepository clubMemberRepository;

    @Async("taskExecutor")
    @EventListener
    public void handleCollegeSelectedEvent(CollegeSelectedEvent event) {
        User user = event.getUser();
        log.info("Received CollegeSelectedEvent for user: {}", user.getUserId());

       

        try {
            enrollInCaxCommunity(user);
        } catch (Exception e) {
            log.error("Error enrolling user {} in CAX Community: ", user.getUserId(), e);
        }
    }

    private void enrollInCaxCommunity(User user) {
        String collegeId = user.getCollegeDetails().getCollegeId();
        String collegeName = user.getCollegeDetails().getCollegeName();

        Optional<Club> existing = clubRepository.findByCollegeIdAndName(collegeId, "CAX Community");

        Club community;
        if (existing.isPresent()) {
            community = existing.get();
        } else {
            community = Club.builder()
                    .name("CAX Community")
                    .description("The official CAX community for " + collegeName + " — your college's home on CAX.")
                    .collegeId(collegeId)
                    .allowJoining(false)
                    .isApprovalRequired(false)
                    .isPaid(false)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            community = clubRepository.save(community);
            log.info("Created CAX Community for college: {} ({})", collegeName, collegeId);
        }

        if (!clubMemberRepository.existsByClubIdAndUserId(community.getId(), user.getUserId())) {
            ClubMember member = ClubMember.builder()
                    .clubId(community.getId())
                    .userId(user.getUserId())
                    .name(user.getName())
                    .email(user.getEmail())
                    .picture(user.getPicture())
                    .role("Member")
                    .joinedAt(Instant.now())
                    .build();
            clubMemberRepository.save(member);
            log.info("Added user {} to CAX Community for college {}", user.getUserId(), collegeName);
        }
    }
}
