package com.cax.cax_backend.user.listener;

import java.time.Instant;
import java.util.Optional;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.cax.cax_backend.organization.model.Organization;
import com.cax.cax_backend.organization.model.OrganizationMember;
import com.cax.cax_backend.organization.repository.OrganizationMemberRepository;
import com.cax.cax_backend.organization.repository.OrganizationRepository;
import com.cax.cax_backend.email.service.EmailService;
import com.cax.cax_backend.user.event.CollegeSelectedEvent;
import com.cax.cax_backend.user.model.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollegeSelectedListener {


    private final OrganizationRepository clubRepository;
    private final OrganizationMemberRepository organizationMemberRepository;

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

        Optional<Organization> existing = clubRepository.findByCollegeIdAndName(collegeId, "CAX Community");

        Organization community;
        if (existing.isPresent()) {
            community = existing.get();
        } else {
            community = Organization.builder()
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

        if (!organizationMemberRepository.existsByOrganizationIdAndUserId(community.getId(), user.getUserId())) {
            OrganizationMember member = OrganizationMember.builder()
                    .organizationId(community.getId())
                    .userId(user.getUserId())
                    .name(user.getName())
                    .email(user.getEmail())
                    .picture(user.getPicture())
                    .role("Member")
                    .joinedAt(Instant.now())
                    .build();
            organizationMemberRepository.save(member);
            log.info("Added user {} to CAX Community for college {}", user.getUserId(), collegeName);
        }
    }
}
