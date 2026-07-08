package com.cax.cax_backend.organization.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.cax.cax_backend.organization.model.Organization;
import com.cax.cax_backend.organization.model.OrganizationJoinRequest;
import com.cax.cax_backend.organization.model.OrganizationMember;
import com.cax.cax_backend.organization.repository.OrganizationJoinRequestRepository;
import com.cax.cax_backend.organization.repository.OrganizationMemberRepository;
import com.cax.cax_backend.organization.repository.OrganizationRepository;
import com.cax.cax_backend.common.enums.OrganizationType;
import com.cax.cax_backend.common.enums.UserRole;
import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.service.UserService;
import com.cax.cax_backend.notification.service.NotificationService;
import com.cax.cax_backend.email.service.EmailService;
import com.cax.cax_backend.common.enums.NotificationEnums.NotificationType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final OrganizationJoinRequestRepository organizationJoinRequestRepository;
    private final UserService userService;
    private final NotificationService notificationService;
    private final EmailService emailService;

    public Organization createOrganization(String creatorUserId, Organization clubData) {
        User user = userService.getUserByUserId(creatorUserId);
        boolean isAllowed = user.getRole() == UserRole.ADMIN || (user.getRole() == UserRole.SUPER_STUDENT && user.isIdVerified());
        if (!isAllowed) {
            throw new BusinessException.BadRequestException("Only verified Super Students or Admins can create clubs.");
        }
        if (user.getRole() == UserRole.ADMIN) {
            if (clubData.getCollegeId() == null || clubData.getCollegeId().isBlank()) {
                throw new BusinessException.BadRequestException("collegeId is required.");
            }
        } else {
            if (user.getCollegeDetails() == null || user.getCollegeDetails().getCollegeId() == null) {
                throw new BusinessException.BadRequestException("Creator does not have college details added.");
            }
            clubData.setCollegeId(user.getCollegeDetails().getCollegeId());
        }

        clubData.setCreatedByUserId(creatorUserId);
        clubData.setCreatedAt(Instant.now());
        clubData.setUpdatedAt(Instant.now());

        // Save club - DO NOT assign creator as president
        // Super student creates on behalf of college, not for themselves
        // President will be assigned later via assignOrganizationLeaders()
        Organization saved = organizationRepository.save(clubData);

        return saved;
    }

    public List<Organization> getOrganizationsByCollege(String collegeId) {
        return organizationRepository.findByCollegeId(collegeId);
    }

    public List<Organization> getAllOrganizations(String collegeId) {
        if (collegeId == null || collegeId.isBlank()) {
            return organizationRepository.findAll();
        }
        return organizationRepository.findByCollegeId(collegeId);
    }

    public List<Organization> getOrganizationsByCollege(String collegeId, int page, int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return organizationRepository.findByCollegeId(collegeId, pageable);
    }

    @Cacheable(value = "clubs", key = "#organizationId", unless = "#result == null")
    public Organization getOrganizationById(String organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Organization", organizationId));
    }

    // Creates a new membership, or reactivates a previously soft-deleted one for the
    // same (organizationId, userId) pair — the unique index on that pair means a plain
    // insert would fail with a duplicate-key error if the user had left and rejoined.
    private OrganizationMember createOrReactivateMember(String organizationId, String userId, OrganizationMember data) {
        Optional<OrganizationMember> existing = organizationMemberRepository
                .findAnyByOrganizationIdAndUserId(organizationId, userId);
        if (existing.isPresent()) {
            OrganizationMember member = existing.get();
            member.setName(data.getName());
            member.setEmail(data.getEmail());
            member.setPicture(data.getPicture());
            member.setRole(data.getRole());
            member.setAccessControls(data.getAccessControls());
            member.setJoinedAt(Instant.now());
            member.setMuted(false);
            member.setDeleted(false);
            member.setDeletedAt(null);
            return organizationMemberRepository.save(member);
        }
        return organizationMemberRepository.save(data);
    }

    public Map<String, Object> joinOrganization(String userId, String organizationId, String paymentScreenshot, String utr) {
        Organization club = getOrganizationById(organizationId);
        User user = userService.getUserByUserId(userId);

        // Check if joining is allowed
        if (!club.isAllowJoining()) {
            throw new BusinessException.BadRequestException("Joining this club is currently disabled by the club leaders.");
        }

        if (user.getCollegeDetails() == null || !club.getCollegeId().equals(user.getCollegeDetails().getCollegeId())) {
            throw new BusinessException.BadRequestException("You can only join clubs in your own college.");
        }

        if (organizationMemberRepository.existsByOrganizationIdAndUserId(organizationId, userId)) {
            throw new BusinessException.BadRequestException("You are already a member of this club.");
        }

        Map<String, Object> result = new HashMap<>();

        if (club.isPaid()) {
            if (paymentScreenshot == null || paymentScreenshot.isBlank() || utr == null || utr.isBlank()) {
                throw new BusinessException.BadRequestException("Payment screenshot and UTR are required for paid clubs.");
            }

            Optional<OrganizationJoinRequest> existingRequest = organizationJoinRequestRepository.findByOrganizationIdAndUserIdAndStatus(organizationId, userId, "PENDING");
            if (existingRequest.isPresent()) {
                throw new BusinessException.BadRequestException("Your join request is already pending.");
            }

            OrganizationJoinRequest joinRequest = OrganizationJoinRequest.builder()
                    .organizationId(organizationId)
                    .userId(userId)
                    .name(user.getName())
                    .email(user.getEmail())
                    .picture(user.getPicture())
                    .status("PENDING")
                    .paymentScreenshot(paymentScreenshot)
                    .utr(utr)
                    .amountPaid(club.getPrice())
                    .requestedAt(Instant.now())
                    .build();

            OrganizationJoinRequest savedRequest = organizationJoinRequestRepository.save(joinRequest);
            result.put("status", "PENDING");
            result.put("message", "Join request submitted with payment details, awaiting approval.");

            List<String> leadersToNotify = getOrganizationLeadersWithAcceptPermission(club);
            for (String leaderId : leadersToNotify) {
                Map<String, String> data = new HashMap<>();
                data.put("organizationId", organizationId);
                data.put("requestId", savedRequest.getId());
                data.put("type", "organization_join_request");
                notificationService.createNotification(
                    leaderId,
                    "New Join Request",
                    user.getName() + " has requested to join your club: " + club.getName(),
                    NotificationType.SYSTEM,
                    data
                );
            }
        } else if (club.isApprovalRequired()) {
            Optional<OrganizationJoinRequest> existingRequest = organizationJoinRequestRepository.findByOrganizationIdAndUserIdAndStatus(organizationId, userId, "PENDING");
            if (existingRequest.isPresent()) {
                throw new BusinessException.BadRequestException("Your join request is already pending.");
            }

            OrganizationJoinRequest joinRequest = OrganizationJoinRequest.builder()
                    .organizationId(organizationId)
                    .userId(userId)
                    .name(user.getName())
                    .email(user.getEmail())
                    .picture(user.getPicture())
                    .status("PENDING")
                    .amountPaid(0.0)
                    .requestedAt(Instant.now())
                    .build();

            OrganizationJoinRequest savedRequest = organizationJoinRequestRepository.save(joinRequest);
            result.put("status", "PENDING");
            result.put("message", "Join request sent successfully, awaiting approval.");

            List<String> leadersToNotify = getOrganizationLeadersWithAcceptPermission(club);
            for (String leaderId : leadersToNotify) {
                Map<String, String> data = new HashMap<>();
                data.put("organizationId", organizationId);
                data.put("requestId", savedRequest.getId());
                data.put("type", "organization_join_request");
                notificationService.createNotification(
                    leaderId,
                    "New Join Request",
                    user.getName() + " has requested to join your club: " + club.getName(),
                    NotificationType.SYSTEM,
                    data
                );
            }
        } else {
            OrganizationMember member = OrganizationMember.builder()
                    .organizationId(organizationId)
                    .userId(userId)
                    .name(user.getName())
                    .email(user.getEmail())
                    .picture(user.getPicture())
                    .role("Member")
                    .joinedAt(Instant.now())
                    .build();

            createOrReactivateMember(organizationId, userId, member);
            result.put("status", "JOINED");
            result.put("message", "Joined club successfully.");

            List<String> leadersToNotify = getOrganizationLeadersWithAcceptPermission(club);
            for (String leaderId : leadersToNotify) {
                Map<String, String> data = new HashMap<>();
                data.put("organizationId", organizationId);
                data.put("type", "organization_new_member");
                notificationService.createNotification(
                    leaderId,
                    "New Organization Member",
                    user.getName() + " has joined your club: " + club.getName(),
                    NotificationType.SYSTEM,
                    data
                );
            }
        }

        return result;
    }

    public void leaveOrganization(String userId, String organizationId) {
        Organization club = getOrganizationById(organizationId);
        OrganizationMember member = organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new BusinessException.BadRequestException("You are not a member of this club."));

        // If leaving member was President or Vice President, clear it from club settings
        boolean updated = false;
        if (userId.equals(club.getPresidentId())) {
            club.setPresidentId(null);
            updated = true;
        } else if (userId.equals(club.getVicePresidentId())) {
            club.setVicePresidentId(null);
            updated = true;
        }

        if (updated) {
            organizationRepository.save(club);
        }

        member.setDeleted(true);
        member.setDeletedAt(Instant.now());
        organizationMemberRepository.save(member);
    }

    @CacheEvict(value = "clubs", key = "#organizationId")
    public void assignOrganizationLeaders(String creatorUserId, String organizationId, String presidentUserId, String vicePresidentUserId) {
        Organization club = getOrganizationById(organizationId);
        User creator = userService.getUserByUserId(creatorUserId);

        boolean isAllowed = creator.getRole() == UserRole.ADMIN || (creator.getRole() == UserRole.SUPER_STUDENT && creator.isIdVerified());
        if (!isAllowed) {
            throw new BusinessException.BadRequestException("Only verified Super Students or Admins can assign leaders.");
        }

        if (creator.getRole() == UserRole.SUPER_STUDENT
                && !club.getCollegeId().equals(creator.getCollegeDetails().getCollegeId())) {
            throw new BusinessException.BadRequestException("You can only manage clubs in your own college.");
        }

        if (presidentUserId != null && !presidentUserId.isBlank()
                && vicePresidentUserId != null && !vicePresidentUserId.isBlank()
                && presidentUserId.equals(vicePresidentUserId)) {
            throw new BusinessException.BadRequestException("A user cannot be assigned as both President and Vice President.");
        }

        String oldPresidentId = club.getPresidentId();
        String oldVicePresidentId = club.getVicePresidentId();

        // Demote old President if changed or cleared
        if (oldPresidentId != null && !oldPresidentId.isBlank() && !oldPresidentId.equals(presidentUserId)) {
            organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, oldPresidentId).ifPresent(pm -> {
                pm.setRole("Member");
                pm.setAccessControls(List.of());
                organizationMemberRepository.save(pm);
            });
        }

        // Demote old Vice President if changed or cleared
        if (oldVicePresidentId != null && !oldVicePresidentId.isBlank() && !oldVicePresidentId.equals(vicePresidentUserId)) {
            organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, oldVicePresidentId).ifPresent(vpm -> {
                vpm.setRole("Member");
                vpm.setAccessControls(List.of());
                organizationMemberRepository.save(vpm);
            });
        }

        // Assign President
        if (presidentUserId != null && !presidentUserId.isBlank()) {
            boolean isNewPres = !presidentUserId.equals(oldPresidentId);
            User presUser = userService.getUserByUserId(presidentUserId);
            club.setPresidentId(presidentUserId);
            
            Optional<OrganizationMember> existingPres = organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, presidentUserId);
            List<String> presPerms = List.of("manage_events", "manage_members", "manage_settings", "manage_posts", "manage_memories");
            if (existingPres.isPresent()) {
                OrganizationMember pm = existingPres.get();
                pm.setRole("President");
                pm.setAccessControls(presPerms);
                organizationMemberRepository.save(pm);
            } else {
                createOrReactivateMember(organizationId, presidentUserId, OrganizationMember.builder()
                        .organizationId(organizationId)
                        .userId(presidentUserId)
                        .name(presUser.getName())
                        .email(presUser.getEmail())
                        .picture(presUser.getPicture())
                        .role("President")
                        .accessControls(presPerms)
                        .joinedAt(Instant.now())
                        .build());
            }

            if (isNewPres) {
                Map<String, String> data = new HashMap<>();
                data.put("organizationId", organizationId);
                data.put("role", "President");
                data.put("type", "organization_leader_assigned");
                notificationService.createNotification(
                    presidentUserId,
                    "Assigned as Organization President",
                    "You have been assigned as the President of the organization: " + club.getName(),
                    NotificationType.SYSTEM,
                    data
                );
                try {
                    emailService.sendOrganizationLeaderAssignmentEmail(presUser, club, "President");
                } catch (Exception e) {
                    log.error("Failed to send President assignment email: ", e);
                }
            }
        } else {
            club.setPresidentId(null);
        }

        // Assign VP
        if (vicePresidentUserId != null && !vicePresidentUserId.isBlank()) {
            boolean isNewVp = !vicePresidentUserId.equals(oldVicePresidentId);
            User vpUser = userService.getUserByUserId(vicePresidentUserId);
            club.setVicePresidentId(vicePresidentUserId);
            
            Optional<OrganizationMember> existingVp = organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, vicePresidentUserId);
            List<String> vpPerms = List.of("manage_events", "manage_members", "manage_settings", "manage_posts", "manage_memories");
            if (existingVp.isPresent()) {
                OrganizationMember vpm = existingVp.get();
                vpm.setRole("Vice President");
                vpm.setAccessControls(vpPerms);
                organizationMemberRepository.save(vpm);
            } else {
                createOrReactivateMember(organizationId, vicePresidentUserId, OrganizationMember.builder()
                        .organizationId(organizationId)
                        .userId(vicePresidentUserId)
                        .name(vpUser.getName())
                        .email(vpUser.getEmail())
                        .picture(vpUser.getPicture())
                        .role("Vice President")
                        .accessControls(vpPerms)
                        .joinedAt(Instant.now())
                        .build());
            }

            if (isNewVp) {
                Map<String, String> data = new HashMap<>();
                data.put("organizationId", organizationId);
                data.put("role", "Vice President");
                data.put("type", "organization_leader_assigned");
                notificationService.createNotification(
                    vicePresidentUserId,
                    "Assigned as Organization Vice President",
                    "You have been assigned as the Vice President of the organization: " + club.getName(),
                    NotificationType.SYSTEM,
                    data
                );
                try {
                    emailService.sendOrganizationLeaderAssignmentEmail(vpUser, club, "Vice President");
                } catch (Exception e) {
                    log.error("Failed to send VP assignment email: ", e);
                }
            }
        } else {
            club.setVicePresidentId(null);
        }

        club.setUpdatedAt(Instant.now());
        organizationRepository.save(club);
    }

    public List<OrganizationJoinRequest> getOrganizationJoinRequests(String leaderUserId, String organizationId) {
        Organization club = getOrganizationById(organizationId);
        verifyOrganizationLeader(leaderUserId, club);

        return organizationJoinRequestRepository.findByOrganizationId(organizationId);
    }

    public void manageOrganizationJoinRequest(String leaderUserId, String organizationId, String requestId, String status) {
        Organization club = getOrganizationById(organizationId);
        verifyOrganizationLeader(leaderUserId, club);

        OrganizationJoinRequest request = organizationJoinRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Request", requestId));

        if (!request.getOrganizationId().equals(organizationId)) {
            throw new BusinessException.BadRequestException("Request does not belong to this club.");
        }

        if (!"PENDING".equals(request.getStatus())) {
            throw new BusinessException.BadRequestException("Request has already been processed.");
        }

        if ("ACCEPTED".equalsIgnoreCase(status)) {
            request.setStatus("ACCEPTED");
            organizationJoinRequestRepository.save(request);

            if (!organizationMemberRepository.existsByOrganizationIdAndUserId(organizationId, request.getUserId())) {
                createOrReactivateMember(organizationId, request.getUserId(), OrganizationMember.builder()
                        .organizationId(organizationId)
                        .userId(request.getUserId())
                        .name(request.getName())
                        .email(request.getEmail())
                        .picture(request.getPicture())
                        .role("Member")
                        .joinedAt(Instant.now())
                        .build());
            }

            // Notify user that they were accepted
            Map<String, String> data = new HashMap<>();
            data.put("organizationId", organizationId);
            data.put("status", "ACCEPTED");
            data.put("type", "organization_join_approved");
            notificationService.createNotification(
                request.getUserId(),
                "Organization Join Request Approved",
                "Your request to join the club " + club.getName() + " has been approved!",
                NotificationType.SYSTEM,
                data
            );
        } else if ("REJECTED".equalsIgnoreCase(status)) {
            request.setStatus("REJECTED");
            organizationJoinRequestRepository.save(request);

            // Notify user that they were rejected
            Map<String, String> data = new HashMap<>();
            data.put("organizationId", organizationId);
            data.put("status", "REJECTED");
            data.put("type", "organization_join_rejected");
            notificationService.createNotification(
                request.getUserId(),
                "Organization Join Request Rejected",
                "Your request to join the club " + club.getName() + " has been rejected.",
                NotificationType.SYSTEM,
                data
            );
        } else {
            throw new BusinessException.BadRequestException("Invalid status update.");
        }
    }

    @CacheEvict(value = "clubs", key = "#organizationId")
    public void updateOrganizationSettings(String leaderUserId, String organizationId, boolean isApprovalRequired, boolean isPaid, Double price, String upiId, String qrCodeUrl) {
        Organization club = getOrganizationById(organizationId);
        verifyOrganizationLeader(leaderUserId, club);

        club.setApprovalRequired(isApprovalRequired);
        club.setPaid(isPaid);
        club.setPrice(price);
        club.setUpiId(upiId);
        club.setQrCodeUrl(qrCodeUrl);
        club.setUpdatedAt(Instant.now());
        organizationRepository.save(club);
    }

    @CacheEvict(value = "clubs", key = "#organizationId")
    public void updateOrganizationAllowJoining(String leaderUserId, String organizationId, boolean allowJoining) {
        Organization club = getOrganizationById(organizationId);
        verifyOrganizationLeader(leaderUserId, club);

        club.setAllowJoining(allowJoining);
        club.setUpdatedAt(Instant.now());
        organizationRepository.save(club);
    }

    @CacheEvict(value = "clubs", key = "#organizationId")
    public void toggleOrganizationAllowJoining(String leaderUserId, String organizationId) {
        Organization club = getOrganizationById(organizationId);
        verifyOrganizationLeader(leaderUserId, club);

        club.setAllowJoining(!club.isAllowJoining());
        club.setUpdatedAt(Instant.now());
        organizationRepository.save(club);
    }

    @CacheEvict(value = "clubs", key = "#organizationId")
    public void updateOrganizationMemberRole(String leaderUserId, String organizationId, String memberUserId, String role) {
        Organization club = getOrganizationById(organizationId);
        verifyOrganizationLeader(leaderUserId, club);

        OrganizationMember member = organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, memberUserId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("OrganizationMember", memberUserId));

        if (memberUserId.equals(club.getPresidentId()) || memberUserId.equals(club.getVicePresidentId())) {
            throw new BusinessException.BadRequestException("Cannot change President or Vice President roles directly. Use Super Student assignment.");
        }

        boolean hadPermissionBefore = hasOrganizationAcceptPermission(member, club);

        member.setRole(role);
        OrganizationMember savedMember = organizationMemberRepository.save(member);

        boolean hasPermissionAfter = hasOrganizationAcceptPermission(savedMember, club);

        if (!hadPermissionBefore && hasPermissionAfter) {
            sendLeaderRoleAssignedNotification(memberUserId, role, club);
        }
    }

    public void removeOrganizationMember(String leaderUserId, String organizationId, String memberUserId) {
        Organization club = getOrganizationById(organizationId);
        verifyOrganizationLeader(leaderUserId, club);

        OrganizationMember member = organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, memberUserId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("OrganizationMember", memberUserId));

        if (memberUserId.equals(club.getPresidentId()) || memberUserId.equals(club.getVicePresidentId())) {
            throw new BusinessException.BadRequestException("Cannot remove the President or Vice President. Reassign them first.");
        }

        member.setDeleted(true);
        member.setDeletedAt(Instant.now());
        organizationMemberRepository.save(member);
    }

    public List<OrganizationMember> getOrganizationMembers(String organizationId) {
        return organizationMemberRepository.findByOrganizationId(organizationId);
    }

    public List<OrganizationMember> getUserOrganizationMemberships(String userId) {
        return organizationMemberRepository.findByUserId(userId);
    }

    public Optional<OrganizationJoinRequest> getUserOrganizationJoinRequest(String organizationId, String userId) {
        return organizationJoinRequestRepository.findByOrganizationIdAndUserId(organizationId, userId);
    }

    public boolean isOrganizationMember(String userId, String organizationId) {
        return organizationMemberRepository.existsByOrganizationIdAndUserId(organizationId, userId);
    }

    public boolean isOrganizationLeaderOrManager(String userId, String organizationId) {
        Organization club = getOrganizationById(organizationId);
        if (userId.equals(club.getPresidentId()) || userId.equals(club.getVicePresidentId())) {
            return true;
        }

        Optional<OrganizationMember> memberOpt = organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, userId);
        if (memberOpt.isPresent()) {
            String role = memberOpt.get().getRole();
            if (role != null) {
                String normalized = role.trim().toLowerCase();
                if (normalized.equals("president") || 
                    normalized.equals("vice president") || 
                    normalized.equals("lead") || 
                    normalized.equals("manager") || 
                    normalized.equals("organization manager")) {
                    return true;
                }
            }
        }

        // Admins have global access; Super Students only for their own college's clubs
        try {
            User user = userService.getUserByUserId(userId);
            if (user.getRole() == UserRole.ADMIN) return true;
            if (user.getRole() == UserRole.SUPER_STUDENT && user.isIdVerified()) {
                return user.getCollegeDetails() != null
                        && club.getCollegeId().equals(user.getCollegeDetails().getCollegeId());
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean hasOrganizationAcceptPermission(OrganizationMember member, Organization club) {
        if (member == null) return false;
        if (member.getUserId().equals(club.getPresidentId()) || member.getUserId().equals(club.getVicePresidentId())) {
            return true;
        }
        String role = member.getRole();
        if (role == null || "Member".equalsIgnoreCase(role.trim())) {
            return false;
        }
        return member.getAccessControls() != null && member.getAccessControls().contains("manage_members");
    }

    public List<String> getOrganizationLeadersWithAcceptPermission(Organization club) {
        List<OrganizationMember> members = organizationMemberRepository.findByOrganizationId(club.getId());
        java.util.Set<String> leaderIds = new java.util.HashSet<>();
        if (club.getPresidentId() != null && !club.getPresidentId().isBlank()) {
            leaderIds.add(club.getPresidentId());
        }
        if (club.getVicePresidentId() != null && !club.getVicePresidentId().isBlank()) {
            leaderIds.add(club.getVicePresidentId());
        }
        for (OrganizationMember member : members) {
            if (hasOrganizationAcceptPermission(member, club)) {
                leaderIds.add(member.getUserId());
            }
        }
        return new java.util.ArrayList<>(leaderIds);
    }

    private void sendLeaderRoleAssignedNotification(String userId, String role, Organization club) {
        Map<String, String> data = new HashMap<>();
        data.put("organizationId", club.getId());
        data.put("role", role);
        data.put("type", "organization_leader_assigned");
        notificationService.createNotification(
            userId,
            "Assigned Organization Leader Role",
            "You have been assigned as " + role + " of the club " + club.getName() + " with permission to accept members.",
            NotificationType.SYSTEM,
            data
        );
    }

    private void verifyOrganizationLeader(String userId, Organization club) {
        if (isOrganizationLeaderOrManager(userId, club.getId())) {
            return;
        }
        throw new BusinessException.BadRequestException("Unauthorized: You must be a Organization Leader or Manager to perform this action.");
    }

    private void verifyOrganizationPresidentOrVP(String userId, Organization club) {
        if (userId.equals(club.getPresidentId()) || userId.equals(club.getVicePresidentId())) {
            return;
        }
        throw new BusinessException.BadRequestException("Unauthorized: Only the President or Vice President can perform this action.");
    }

    @CacheEvict(value = "clubs", key = "#organizationId")
    public void updateOrganizationMemberPermissions(String leaderUserId, String organizationId, String memberUserId, List<String> permissions) {
        Organization club = getOrganizationById(organizationId);
        verifyOrganizationLeader(leaderUserId, club);

        OrganizationMember member = organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, memberUserId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("OrganizationMember", memberUserId));

        if (memberUserId.equals(club.getPresidentId())) {
            throw new BusinessException.BadRequestException("Cannot change President's access list directly.");
        }

        boolean hadPermissionBefore = hasOrganizationAcceptPermission(member, club);

        member.setAccessControls(permissions);
        OrganizationMember savedMember = organizationMemberRepository.save(member);

        boolean hasPermissionAfter = hasOrganizationAcceptPermission(savedMember, club);

        if (!hadPermissionBefore && hasPermissionAfter) {
            sendLeaderRoleAssignedNotification(memberUserId, member.getRole(), club);
        }
    }

    @CacheEvict(value = "clubs", key = "#organizationId")
    public Organization updateOrganizationRoles(String leaderUserId, String organizationId, List<Organization.OrganizationRole> customRoles) {
        Organization club = getOrganizationById(organizationId);
        verifyOrganizationPresidentOrVP(leaderUserId, club);

        List<String> newRoleNames = customRoles.stream().map(Organization.OrganizationRole::getName).toList();
        List<OrganizationMember> members = organizationMemberRepository.findByOrganizationId(organizationId);
        for (OrganizationMember member : members) {
            String currentRole = member.getRole();
            if (currentRole != null && 
                !currentRole.equalsIgnoreCase("President") && 
                !currentRole.equalsIgnoreCase("Vice President") && 
                !currentRole.equalsIgnoreCase("Member")) {
                
                boolean roleStillExists = newRoleNames.stream()
                        .anyMatch(name -> name.equalsIgnoreCase(currentRole));
                if (!roleStillExists) {
                    member.setRole("Member");
                    organizationMemberRepository.save(member);
                }
            }
        }

        club.setCustomRoles(customRoles);
        club.setUpdatedAt(Instant.now());
        return organizationRepository.save(club);
    }

    @CacheEvict(value = "clubs", key = "#organizationId")
    public Organization updateOrganizationProfile(String leaderUserId, String organizationId, String name, String logo, String description, String coverPhoto) {
        Organization club = getOrganizationById(organizationId);
        verifyOrganizationPresidentOrVP(leaderUserId, club);

        if (name != null && !name.trim().isEmpty()) {
            String trimmedName = name.trim();
            if (trimmedName.length() > 50) {
                throw new BusinessException.BadRequestException("Organization name cannot exceed 50 characters.");
            }
            club.setName(trimmedName);
        }
        if (logo != null && !logo.trim().isEmpty()) {
            club.setLogo(logo.trim());
        }
        if (description != null) {
            String trimmedDescription = description.trim();
            if (trimmedDescription.length() > 500) {
                throw new BusinessException.BadRequestException("Organization description cannot exceed 500 characters.");
            }
            club.setDescription(trimmedDescription);
        }
        if (coverPhoto != null) {
            club.setCoverPhoto(coverPhoto.trim());
        }

        club.setUpdatedAt(Instant.now());
        return organizationRepository.save(club);
    }

    public void toggleMuteOrganization(String userId, String organizationId, boolean isMuted) {
        OrganizationMember member = organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("OrganizationMember", userId));
        member.setMuted(isMuted);
        organizationMemberRepository.save(member);
    }

    @CacheEvict(value = "clubs", key = "#organizationId")
    public Organization uploadOrganizationMemory(String userId, String organizationId, String imageUrl) {
        Organization club = getOrganizationById(organizationId);
        if (!hasOrganizationMemoryPermission(userId, organizationId)) {
            throw new BusinessException.BadRequestException("Unauthorized: You do not have permission to manage memories.");
        }
        if (club.getMemories() == null) {
            club.setMemories(new java.util.ArrayList<>());
        }
        club.getMemories().add(imageUrl);
        club.setUpdatedAt(Instant.now());
        return organizationRepository.save(club);
    }

    @CacheEvict(value = "clubs", key = "#organizationId")
    public Organization deleteOrganizationMemory(String userId, String organizationId, String imageUrl) {
        Organization club = getOrganizationById(organizationId);
        if (!hasOrganizationMemoryPermission(userId, organizationId)) {
            throw new BusinessException.BadRequestException("Unauthorized: You do not have permission to manage memories.");
        }
        if (club.getMemories() != null) {
            club.getMemories().remove(imageUrl);
        }
        club.setUpdatedAt(Instant.now());
        return organizationRepository.save(club);
    }

    private boolean hasOrganizationMemoryPermission(String userId, String organizationId) {
        if (isOrganizationLeaderOrManager(userId, organizationId)) {
            return true;
        }
        Optional<OrganizationMember> memberOpt = organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, userId);
        if (memberOpt.isPresent()) {
            OrganizationMember m = memberOpt.get();
            return m.getAccessControls() != null && m.getAccessControls().contains("manage_memories");
        }
        return false;
    }

    public List<Organization> getOrganizationsByIds(List<String> organizationIds) {
        if (organizationIds == null || organizationIds.isEmpty()) {
            return List.of();
        }
        return organizationRepository.findAllById(organizationIds);
    }

    public Optional<OrganizationMember> getOrganizationMember(String organizationId, String userId) {
        return organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, userId);
    }

    @CacheEvict(value = "clubs", key = "#organizationId")
    public Organization updateOrganizationType(String requesterId, String organizationId, String typeValue) {
        User requester = userService.getUserByUserId(requesterId);
        boolean isSuperStudent = requester.getRole() == UserRole.SUPER_STUDENT && requester.isIdVerified();
        boolean isAdmin = requester.getRole() == UserRole.ADMIN;

        if (!isSuperStudent && !isAdmin) {
            throw new BusinessException.BadRequestException("Only verified Super Students or Admins can change the organization type.");
        }

        Organization club = getOrganizationById(organizationId);

        if (isSuperStudent) {
            if (requester.getCollegeDetails() == null || requester.getCollegeDetails().getCollegeId() == null) {
                throw new BusinessException.BadRequestException("Super Student does not have college details.");
            }
            if (!club.getCollegeId().equals(requester.getCollegeDetails().getCollegeId())) {
                throw new BusinessException.BadRequestException("You can only manage organizations in your own college.");
            }
        }

        OrganizationType newType = OrganizationType.fromValue(typeValue);
        club.setType(newType);
        club.setUpdatedAt(Instant.now());
        return organizationRepository.save(club);
    }
}
