package com.cax.cax_backend.organization.service;

import com.cax.cax_backend.organization.model.Organization;
import com.cax.cax_backend.organization.model.OrganizationMember;
import com.cax.cax_backend.organization.model.Ripple;
import com.cax.cax_backend.organization.repository.OrganizationMemberRepository;
import com.cax.cax_backend.organization.repository.OrganizationRepository;
import com.cax.cax_backend.organization.repository.RippleRepository;
import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.notification.service.NotificationService;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RippleService {

    private final RippleRepository rippleRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final UserService userService;
    private final NotificationService notificationService;

    public List<Ripple> getRipples(String userId, String organizationId, int page, int size) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Organization", organizationId));

        // Enforce College Isolation
        User user = userService.getUserByUserId(userId);
        if (user.getRole() != com.cax.cax_backend.common.enums.UserRole.ADMIN) {
            if (user.getCollegeDetails() == null || user.getCollegeDetails().getCollegeId() == null
                    || !organization.getCollegeId().equals(user.getCollegeDetails().getCollegeId())) {
                throw new BusinessException.BadRequestException("You cannot access announcements from another college.");
            }

            // Enforce organization membership check
            boolean isMember = false;
            if (userId.equals(organization.getPresidentId()) || userId.equals(organization.getVicePresidentId())) {
                isMember = true;
            } else {
                isMember = organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, userId).isPresent();
            }

            if (!isMember) {
                throw new BusinessException.BadRequestException("Access denied: You must be a member of the organization to view its announcements.");
            }
        }

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return rippleRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId, pageable);
    }

    public Ripple createRipple(String userId, String organizationId, String content) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Organization", organizationId));

        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException.BadRequestException("Content cannot be empty.");
        }

        if (content.length() > 280) {
            throw new BusinessException.BadRequestException("Content exceeds maximum limit of 280 characters.");
        }

        // Check if user is president or vice president of the organization
        boolean isAuthorized = false;
        String creatorRole = "Member";

        if (userId.equals(organization.getPresidentId())) {
            isAuthorized = true;
            creatorRole = "President";
        } else if (userId.equals(organization.getVicePresidentId())) {
            isAuthorized = true;
            creatorRole = "Vice President";
        } else {
            Optional<OrganizationMember> memberOpt = organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, userId);
            if (memberOpt.isPresent()) {
                OrganizationMember member = memberOpt.get();
                String role = member.getRole();
                if (role != null) {
                    String normRole = role.trim().toLowerCase();
                    if (normRole.equals("president")) {
                        isAuthorized = true;
                        creatorRole = "President";
                    } else if (normRole.equals("vice president")) {
                        isAuthorized = true;
                        creatorRole = "Vice President";
                    }
                }
            }
        }

        if (!isAuthorized) {
            throw new BusinessException.BadRequestException("Unauthorized: Only the President or Vice President can post ripples.");
        }

        // Get user details
        User user = userService.getUserByUserId(userId);
        String creatorName = user.getName() != null ? user.getName() : "Anonymous";
        String creatorPicture = user.getPicture() != null ? user.getPicture() : "";

        Ripple ripple = Ripple.builder()
                .organizationId(organizationId)
                .creatorId(userId)
                .creatorName(creatorName)
                .creatorPicture(creatorPicture)
                .creatorRole(creatorRole)
                .content(content.trim())
                .createdAt(Instant.now())
                .build();

        Ripple savedRipple = rippleRepository.save(ripple);

        // Send notifications to all organization members (excluding the creator themselves)
        try {
            List<OrganizationMember> members = organizationMemberRepository.findByOrganizationId(organizationId);
            for (OrganizationMember member : members) {
                if (member.getUserId() != null && !member.getUserId().equals(userId)) {
                    Map<String, String> data = new HashMap<>();
                    data.put("type", "organization_ripple_added");
                    data.put("organizationId", organizationId);
                    data.put("organizationName", organization.getName());
                    data.put("organizationLogo", organization.getLogo() != null ? organization.getLogo() : "");

                    String title = organization.getName() + " • New Ripple";
                    String body = creatorName + " (" + creatorRole + "): " + 
                            (content.length() > 60 ? content.substring(0, 57) + "..." : content);

                    notificationService.createNotification(
                            member.getUserId(),
                            title,
                            body,
                            com.cax.cax_backend.common.enums.NotificationEnums.NotificationType.SYSTEM,
                            data
                    );
                }
            }
        } catch (Exception e) {
            log.error("Failed to send ripple notifications: {}", e.getMessage(), e);
        }

        return savedRipple;
    }

    public void deleteRipple(String userId, String rippleId) {
        Ripple ripple = rippleRepository.findById(rippleId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Ripple", rippleId));

        // Check 2-minute time lock
        Duration duration = Duration.between(ripple.getCreatedAt(), Instant.now());
        if (duration.getSeconds() > 120) {
            throw new BusinessException.BadRequestException("Delete window expired: Ripples can only be deleted within 2 minutes of creation.");
        }

        // Check authorization (only creator or organization President/VP can delete)
        boolean isAuthorized = userId.equals(ripple.getCreatorId());
        if (!isAuthorized) {
            // Also allow the President/VP of the organization to delete it
            Organization organization = organizationRepository.findById(ripple.getOrganizationId())
                    .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Organization", ripple.getOrganizationId()));
            
            if (userId.equals(organization.getPresidentId()) || userId.equals(organization.getVicePresidentId())) {
                isAuthorized = true;
            } else {
                Optional<OrganizationMember> memberOpt = organizationMemberRepository.findByOrganizationIdAndUserId(ripple.getOrganizationId(), userId);
                if (memberOpt.isPresent()) {
                    String role = memberOpt.get().getRole();
                    if (role != null) {
                        String normRole = role.trim().toLowerCase();
                        if (normRole.equals("president") || normRole.equals("vice president")) {
                            isAuthorized = true;
                        }
                    }
                }
            }
        }

        if (!isAuthorized) {
            throw new BusinessException.BadRequestException("Unauthorized: You do not have permission to delete this ripple.");
        }

        rippleRepository.delete(ripple);
    }
}
