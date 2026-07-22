package com.cax.cax_backend.user.listener;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.cax.cax_backend.common.enums.NotificationEnums.NotificationType;
import com.cax.cax_backend.notification.service.NotificationService;
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
    private final NotificationService notificationService;

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
            List<OrganizationMember> existingMembers = organizationMemberRepository.findByOrganizationId(community.getId());

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

            notifyExistingMembersOfNewJoin(community, existingMembers, user);
        }
    }

    private void notifyExistingMembersOfNewJoin(Organization community, List<OrganizationMember> existingMembers, User newUser) {
        if (existingMembers.isEmpty()) {
            return;
        }

        String newMemberName = newUser.getName() != null && !newUser.getName().isBlank()
                ? newUser.getName()
                : "A new student";

        String title = "New member joined!";
        String body = newMemberName + " just joined the CAX Community.";

        Map<String, String> data = new HashMap<>();
        data.put("type", "COMMUNITY_MEMBER_JOINED");
        data.put("organizationId", community.getId());
        data.put("newMemberUserId", newUser.getUserId());
        data.put("newMemberName", newMemberName);
        data.put("deepLink", "app://club/" + community.getId());

        int successCount = 0;
        int failCount = 0;
        for (OrganizationMember existingMember : existingMembers) {
            try {
                if (notificationService.createNotification(existingMember.getUserId(), title, body, NotificationType.SYSTEM, data) != null) {
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                failCount++;
                log.error("Failed to notify community member {} of new join {}: {}",
                        existingMember.getUserId(), newUser.getUserId(), e.getMessage());
            }
        }
        log.info("Notified CAX Community {} of new member {}. Targets: {}, Successes: {}, Failures/Skipped: {}",
                community.getId(), newUser.getUserId(), existingMembers.size(), successCount, failCount);
    }
}
