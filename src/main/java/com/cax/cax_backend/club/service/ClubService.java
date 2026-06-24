package com.cax.cax_backend.club.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.cax.cax_backend.club.model.Club;
import com.cax.cax_backend.club.model.ClubJoinRequest;
import com.cax.cax_backend.club.model.ClubMember;
import com.cax.cax_backend.club.repository.ClubJoinRequestRepository;
import com.cax.cax_backend.club.repository.ClubMemberRepository;
import com.cax.cax_backend.club.repository.ClubRepository;
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
public class ClubService {

    private final ClubRepository clubRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final ClubJoinRequestRepository clubJoinRequestRepository;
    private final UserService userService;
    private final NotificationService notificationService;
    private final EmailService emailService;

    public Club createClub(String creatorUserId, Club clubData) {
        User user = userService.getUserByUserId(creatorUserId);
        boolean isAllowed = user.getRole() == UserRole.ADMIN || (user.getRole() == UserRole.SUPER_STUDENT && user.isIdVerified());
        if (!isAllowed) {
            throw new BusinessException.BadRequestException("Only verified Super Students or Admins can create clubs.");
        }
        if (user.getCollegeDetails() == null || user.getCollegeDetails().getCollegeId() == null) {
            throw new BusinessException.BadRequestException("Creator does not have college details added.");
        }

        clubData.setCollegeId(user.getCollegeDetails().getCollegeId());
        clubData.setCreatedAt(Instant.now());
        clubData.setUpdatedAt(Instant.now());

        // Save club - DO NOT assign creator as president
        // Super student creates on behalf of college, not for themselves
        // President will be assigned later via assignLeaders()
        Club saved = clubRepository.save(clubData);

        return saved;
    }

    public List<Club> getClubsByCollege(String collegeId) {
        return clubRepository.findByCollegeId(collegeId);
    }

    public List<Club> getClubsByCollege(String collegeId, int page, int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return clubRepository.findByCollegeId(collegeId, pageable);
    }

    @Cacheable(value = "clubs", key = "#clubId", unless = "#result == null")
    public Club getClubById(String clubId) {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Club", clubId));
    }

    public Map<String, Object> joinClub(String userId, String clubId, String paymentScreenshot, String utr) {
        Club club = getClubById(clubId);
        User user = userService.getUserByUserId(userId);

        // Check if joining is allowed
        if (!club.isAllowJoining()) {
            throw new BusinessException.BadRequestException("Joining this club is currently disabled by the club leaders.");
        }

        if (user.getCollegeDetails() == null || !club.getCollegeId().equals(user.getCollegeDetails().getCollegeId())) {
            throw new BusinessException.BadRequestException("You can only join clubs in your own college.");
        }

        if (clubMemberRepository.existsByClubIdAndUserId(clubId, userId)) {
            throw new BusinessException.BadRequestException("You are already a member of this club.");
        }

        Map<String, Object> result = new HashMap<>();

        if (club.isPaid()) {
            if (paymentScreenshot == null || paymentScreenshot.isBlank() || utr == null || utr.isBlank()) {
                throw new BusinessException.BadRequestException("Payment screenshot and UTR are required for paid clubs.");
            }

            Optional<ClubJoinRequest> existingRequest = clubJoinRequestRepository.findByClubIdAndUserIdAndStatus(clubId, userId, "PENDING");
            if (existingRequest.isPresent()) {
                throw new BusinessException.BadRequestException("Your join request is already pending.");
            }

            ClubJoinRequest joinRequest = ClubJoinRequest.builder()
                    .clubId(clubId)
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

            ClubJoinRequest savedRequest = clubJoinRequestRepository.save(joinRequest);
            result.put("status", "PENDING");
            result.put("message", "Join request submitted with payment details, awaiting approval.");

            List<String> leadersToNotify = getLeadersWithAcceptPermission(club);
            for (String leaderId : leadersToNotify) {
                Map<String, String> data = new HashMap<>();
                data.put("clubId", clubId);
                data.put("requestId", savedRequest.getId());
                data.put("type", "club_join_request");
                notificationService.createNotification(
                    leaderId,
                    "New Join Request",
                    user.getName() + " has requested to join your club: " + club.getName(),
                    NotificationType.SYSTEM,
                    data
                );
            }
        } else if (club.isApprovalRequired()) {
            Optional<ClubJoinRequest> existingRequest = clubJoinRequestRepository.findByClubIdAndUserIdAndStatus(clubId, userId, "PENDING");
            if (existingRequest.isPresent()) {
                throw new BusinessException.BadRequestException("Your join request is already pending.");
            }

            ClubJoinRequest joinRequest = ClubJoinRequest.builder()
                    .clubId(clubId)
                    .userId(userId)
                    .name(user.getName())
                    .email(user.getEmail())
                    .picture(user.getPicture())
                    .status("PENDING")
                    .amountPaid(0.0)
                    .requestedAt(Instant.now())
                    .build();

            ClubJoinRequest savedRequest = clubJoinRequestRepository.save(joinRequest);
            result.put("status", "PENDING");
            result.put("message", "Join request sent successfully, awaiting approval.");

            List<String> leadersToNotify = getLeadersWithAcceptPermission(club);
            for (String leaderId : leadersToNotify) {
                Map<String, String> data = new HashMap<>();
                data.put("clubId", clubId);
                data.put("requestId", savedRequest.getId());
                data.put("type", "club_join_request");
                notificationService.createNotification(
                    leaderId,
                    "New Join Request",
                    user.getName() + " has requested to join your club: " + club.getName(),
                    NotificationType.SYSTEM,
                    data
                );
            }
        } else {
            ClubMember member = ClubMember.builder()
                    .clubId(clubId)
                    .userId(userId)
                    .name(user.getName())
                    .email(user.getEmail())
                    .picture(user.getPicture())
                    .role("Member")
                    .joinedAt(Instant.now())
                    .build();

            clubMemberRepository.save(member);
            result.put("status", "JOINED");
            result.put("message", "Joined club successfully.");

            List<String> leadersToNotify = getLeadersWithAcceptPermission(club);
            for (String leaderId : leadersToNotify) {
                Map<String, String> data = new HashMap<>();
                data.put("clubId", clubId);
                data.put("type", "club_new_member");
                notificationService.createNotification(
                    leaderId,
                    "New Club Member",
                    user.getName() + " has joined your club: " + club.getName(),
                    NotificationType.SYSTEM,
                    data
                );
            }
        }

        return result;
    }

    public void leaveClub(String userId, String clubId) {
        Club club = getClubById(clubId);
        ClubMember member = clubMemberRepository.findByClubIdAndUserId(clubId, userId)
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
            clubRepository.save(club);
        }

        clubMemberRepository.delete(member);
    }

    @CacheEvict(value = "clubs", key = "#clubId")
    public void assignLeaders(String creatorUserId, String clubId, String presidentUserId, String vicePresidentUserId) {
        Club club = getClubById(clubId);
        User creator = userService.getUserByUserId(creatorUserId);

        boolean isAllowed = creator.getRole() == UserRole.ADMIN || (creator.getRole() == UserRole.SUPER_STUDENT && creator.isIdVerified());
        if (!isAllowed) {
            throw new BusinessException.BadRequestException("Only verified Super Students or Admins can assign leaders.");
        }

        if (!club.getCollegeId().equals(creator.getCollegeDetails().getCollegeId())) {
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
            clubMemberRepository.findByClubIdAndUserId(clubId, oldPresidentId).ifPresent(pm -> {
                pm.setRole("Member");
                pm.setAccessControls(List.of());
                clubMemberRepository.save(pm);
            });
        }

        // Demote old Vice President if changed or cleared
        if (oldVicePresidentId != null && !oldVicePresidentId.isBlank() && !oldVicePresidentId.equals(vicePresidentUserId)) {
            clubMemberRepository.findByClubIdAndUserId(clubId, oldVicePresidentId).ifPresent(vpm -> {
                vpm.setRole("Member");
                vpm.setAccessControls(List.of());
                clubMemberRepository.save(vpm);
            });
        }

        // Assign President
        if (presidentUserId != null && !presidentUserId.isBlank()) {
            boolean isNewPres = !presidentUserId.equals(oldPresidentId);
            User presUser = userService.getUserByUserId(presidentUserId);
            club.setPresidentId(presidentUserId);
            
            Optional<ClubMember> existingPres = clubMemberRepository.findByClubIdAndUserId(clubId, presidentUserId);
            List<String> presPerms = List.of("manage_events", "manage_members", "manage_settings", "manage_posts", "manage_memories");
            if (existingPres.isPresent()) {
                ClubMember pm = existingPres.get();
                pm.setRole("President");
                pm.setAccessControls(presPerms);
                clubMemberRepository.save(pm);
            } else {
                clubMemberRepository.save(ClubMember.builder()
                        .clubId(clubId)
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
                data.put("clubId", clubId);
                data.put("role", "President");
                data.put("type", "club_leader_assigned");
                notificationService.createNotification(
                    presidentUserId,
                    "Assigned as Club President",
                    "You have been assigned as the President of the club: " + club.getName(),
                    NotificationType.SYSTEM,
                    data
                );
                try {
                    emailService.sendClubLeaderAssignmentEmail(presUser, club, "President");
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
            
            Optional<ClubMember> existingVp = clubMemberRepository.findByClubIdAndUserId(clubId, vicePresidentUserId);
            List<String> vpPerms = List.of("manage_events", "manage_members", "manage_settings", "manage_posts", "manage_memories");
            if (existingVp.isPresent()) {
                ClubMember vpm = existingVp.get();
                vpm.setRole("Vice President");
                vpm.setAccessControls(vpPerms);
                clubMemberRepository.save(vpm);
            } else {
                clubMemberRepository.save(ClubMember.builder()
                        .clubId(clubId)
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
                data.put("clubId", clubId);
                data.put("role", "Vice President");
                data.put("type", "club_leader_assigned");
                notificationService.createNotification(
                    vicePresidentUserId,
                    "Assigned as Club Vice President",
                    "You have been assigned as the Vice President of the club: " + club.getName(),
                    NotificationType.SYSTEM,
                    data
                );
                try {
                    emailService.sendClubLeaderAssignmentEmail(vpUser, club, "Vice President");
                } catch (Exception e) {
                    log.error("Failed to send VP assignment email: ", e);
                }
            }
        } else {
            club.setVicePresidentId(null);
        }

        club.setUpdatedAt(Instant.now());
        clubRepository.save(club);
    }

    public List<ClubJoinRequest> getJoinRequests(String leaderUserId, String clubId) {
        Club club = getClubById(clubId);
        verifyLeader(leaderUserId, club);

        return clubJoinRequestRepository.findByClubId(clubId);
    }

    public void manageJoinRequest(String leaderUserId, String clubId, String requestId, String status) {
        Club club = getClubById(clubId);
        verifyLeader(leaderUserId, club);

        ClubJoinRequest request = clubJoinRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Request", requestId));

        if (!request.getClubId().equals(clubId)) {
            throw new BusinessException.BadRequestException("Request does not belong to this club.");
        }

        if (!"PENDING".equals(request.getStatus())) {
            throw new BusinessException.BadRequestException("Request has already been processed.");
        }

        if ("ACCEPTED".equalsIgnoreCase(status)) {
            request.setStatus("ACCEPTED");
            clubJoinRequestRepository.save(request);

            if (!clubMemberRepository.existsByClubIdAndUserId(clubId, request.getUserId())) {
                clubMemberRepository.save(ClubMember.builder()
                        .clubId(clubId)
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
            data.put("clubId", clubId);
            data.put("status", "ACCEPTED");
            data.put("type", "club_join_approved");
            notificationService.createNotification(
                request.getUserId(),
                "Club Join Request Approved",
                "Your request to join the club " + club.getName() + " has been approved!",
                NotificationType.SYSTEM,
                data
            );
        } else if ("REJECTED".equalsIgnoreCase(status)) {
            request.setStatus("REJECTED");
            clubJoinRequestRepository.save(request);

            // Notify user that they were rejected
            Map<String, String> data = new HashMap<>();
            data.put("clubId", clubId);
            data.put("status", "REJECTED");
            data.put("type", "club_join_rejected");
            notificationService.createNotification(
                request.getUserId(),
                "Club Join Request Rejected",
                "Your request to join the club " + club.getName() + " has been rejected.",
                NotificationType.SYSTEM,
                data
            );
        } else {
            throw new BusinessException.BadRequestException("Invalid status update.");
        }
    }

    @CacheEvict(value = "clubs", key = "#clubId")
    public void updateClubSettings(String leaderUserId, String clubId, boolean isApprovalRequired, boolean isPaid, Double price, String upiId, String qrCodeUrl) {
        Club club = getClubById(clubId);
        verifyLeader(leaderUserId, club);

        club.setApprovalRequired(isApprovalRequired);
        club.setPaid(isPaid);
        club.setPrice(price);
        club.setUpiId(upiId);
        club.setQrCodeUrl(qrCodeUrl);
        club.setUpdatedAt(Instant.now());
        clubRepository.save(club);
    }

    @CacheEvict(value = "clubs", key = "#clubId")
    public void updateAllowJoining(String leaderUserId, String clubId, boolean allowJoining) {
        Club club = getClubById(clubId);
        verifyLeader(leaderUserId, club);

        club.setAllowJoining(allowJoining);
        club.setUpdatedAt(Instant.now());
        clubRepository.save(club);
    }

    @CacheEvict(value = "clubs", key = "#clubId")
    public void toggleAllowJoining(String leaderUserId, String clubId) {
        Club club = getClubById(clubId);
        verifyLeader(leaderUserId, club);

        club.setAllowJoining(!club.isAllowJoining());
        club.setUpdatedAt(Instant.now());
        clubRepository.save(club);
    }

    @CacheEvict(value = "clubs", key = "#clubId")
    public void updateMemberRole(String leaderUserId, String clubId, String memberUserId, String role) {
        Club club = getClubById(clubId);
        verifyLeader(leaderUserId, club);

        ClubMember member = clubMemberRepository.findByClubIdAndUserId(clubId, memberUserId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("ClubMember", memberUserId));

        if (memberUserId.equals(club.getPresidentId()) || memberUserId.equals(club.getVicePresidentId())) {
            throw new BusinessException.BadRequestException("Cannot change President or Vice President roles directly. Use Super Student assignment.");
        }

        boolean hadPermissionBefore = hasAcceptPermission(member, club);

        member.setRole(role);
        ClubMember savedMember = clubMemberRepository.save(member);

        boolean hasPermissionAfter = hasAcceptPermission(savedMember, club);

        if (!hadPermissionBefore && hasPermissionAfter) {
            sendLeaderRoleAssignedNotification(memberUserId, role, club);
        }
    }

    public void removeMember(String leaderUserId, String clubId, String memberUserId) {
        Club club = getClubById(clubId);
        verifyLeader(leaderUserId, club);

        ClubMember member = clubMemberRepository.findByClubIdAndUserId(clubId, memberUserId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("ClubMember", memberUserId));

        if (memberUserId.equals(club.getPresidentId()) || memberUserId.equals(club.getVicePresidentId())) {
            throw new BusinessException.BadRequestException("Cannot remove the President or Vice President. Reassign them first.");
        }

        clubMemberRepository.delete(member);
    }

    public List<ClubMember> getClubMembers(String clubId) {
        return clubMemberRepository.findByClubId(clubId);
    }

    public List<ClubMember> getUserMemberships(String userId) {
        return clubMemberRepository.findByUserId(userId);
    }

    public Optional<ClubJoinRequest> getUserJoinRequest(String clubId, String userId) {
        return clubJoinRequestRepository.findByClubIdAndUserId(clubId, userId);
    }

    public boolean isClubMember(String userId, String clubId) {
        return clubMemberRepository.existsByClubIdAndUserId(clubId, userId);
    }

    public boolean isClubLeaderOrManager(String userId, String clubId) {
        Club club = getClubById(clubId);
        if (userId.equals(club.getPresidentId()) || userId.equals(club.getVicePresidentId())) {
            return true;
        }

        Optional<ClubMember> memberOpt = clubMemberRepository.findByClubIdAndUserId(clubId, userId);
        if (memberOpt.isPresent()) {
            String role = memberOpt.get().getRole();
            if (role != null) {
                String normalized = role.trim().toLowerCase();
                if (normalized.equals("president") || 
                    normalized.equals("vice president") || 
                    normalized.equals("lead") || 
                    normalized.equals("manager") || 
                    normalized.equals("club manager")) {
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

    public boolean hasAcceptPermission(ClubMember member, Club club) {
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

    public List<String> getLeadersWithAcceptPermission(Club club) {
        List<ClubMember> members = clubMemberRepository.findByClubId(club.getId());
        java.util.Set<String> leaderIds = new java.util.HashSet<>();
        if (club.getPresidentId() != null && !club.getPresidentId().isBlank()) {
            leaderIds.add(club.getPresidentId());
        }
        if (club.getVicePresidentId() != null && !club.getVicePresidentId().isBlank()) {
            leaderIds.add(club.getVicePresidentId());
        }
        for (ClubMember member : members) {
            if (hasAcceptPermission(member, club)) {
                leaderIds.add(member.getUserId());
            }
        }
        return new java.util.ArrayList<>(leaderIds);
    }

    private void sendLeaderRoleAssignedNotification(String userId, String role, Club club) {
        Map<String, String> data = new HashMap<>();
        data.put("clubId", club.getId());
        data.put("role", role);
        data.put("type", "club_leader_assigned");
        notificationService.createNotification(
            userId,
            "Assigned Club Leader Role",
            "You have been assigned as " + role + " of the club " + club.getName() + " with permission to accept members.",
            NotificationType.SYSTEM,
            data
        );
    }

    private void verifyLeader(String userId, Club club) {
        if (isClubLeaderOrManager(userId, club.getId())) {
            return;
        }
        throw new BusinessException.BadRequestException("Unauthorized: You must be a Club Leader or Manager to perform this action.");
    }

    private void verifyPresidentOrVP(String userId, Club club) {
        if (userId.equals(club.getPresidentId()) || userId.equals(club.getVicePresidentId())) {
            return;
        }
        throw new BusinessException.BadRequestException("Unauthorized: Only the President or Vice President can perform this action.");
    }

    @CacheEvict(value = "clubs", key = "#clubId")
    public void updateMemberPermissions(String leaderUserId, String clubId, String memberUserId, List<String> permissions) {
        Club club = getClubById(clubId);
        verifyLeader(leaderUserId, club);

        ClubMember member = clubMemberRepository.findByClubIdAndUserId(clubId, memberUserId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("ClubMember", memberUserId));

        if (memberUserId.equals(club.getPresidentId())) {
            throw new BusinessException.BadRequestException("Cannot change President's access list directly.");
        }

        boolean hadPermissionBefore = hasAcceptPermission(member, club);

        member.setAccessControls(permissions);
        ClubMember savedMember = clubMemberRepository.save(member);

        boolean hasPermissionAfter = hasAcceptPermission(savedMember, club);

        if (!hadPermissionBefore && hasPermissionAfter) {
            sendLeaderRoleAssignedNotification(memberUserId, member.getRole(), club);
        }
    }

    @CacheEvict(value = "clubs", key = "#clubId")
    public Club updateClubRoles(String leaderUserId, String clubId, List<Club.ClubRole> customRoles) {
        Club club = getClubById(clubId);
        verifyPresidentOrVP(leaderUserId, club);

        List<String> newRoleNames = customRoles.stream().map(Club.ClubRole::getName).toList();
        List<ClubMember> members = clubMemberRepository.findByClubId(clubId);
        for (ClubMember member : members) {
            String currentRole = member.getRole();
            if (currentRole != null && 
                !currentRole.equalsIgnoreCase("President") && 
                !currentRole.equalsIgnoreCase("Vice President") && 
                !currentRole.equalsIgnoreCase("Member")) {
                
                boolean roleStillExists = newRoleNames.stream()
                        .anyMatch(name -> name.equalsIgnoreCase(currentRole));
                if (!roleStillExists) {
                    member.setRole("Member");
                    clubMemberRepository.save(member);
                }
            }
        }

        club.setCustomRoles(customRoles);
        club.setUpdatedAt(Instant.now());
        return clubRepository.save(club);
    }

    @CacheEvict(value = "clubs", key = "#clubId")
    public Club updateClubProfile(String leaderUserId, String clubId, String name, String logo, String description, String coverPhoto) {
        Club club = getClubById(clubId);
        verifyPresidentOrVP(leaderUserId, club);

        if (name != null && !name.trim().isEmpty()) {
            String trimmedName = name.trim();
            if (trimmedName.length() > 50) {
                throw new BusinessException.BadRequestException("Club name cannot exceed 50 characters.");
            }
            club.setName(trimmedName);
        }
        if (logo != null && !logo.trim().isEmpty()) {
            club.setLogo(logo.trim());
        }
        if (description != null) {
            String trimmedDescription = description.trim();
            if (trimmedDescription.length() > 500) {
                throw new BusinessException.BadRequestException("Club description cannot exceed 500 characters.");
            }
            club.setDescription(trimmedDescription);
        }
        if (coverPhoto != null) {
            club.setCoverPhoto(coverPhoto.trim());
        }

        club.setUpdatedAt(Instant.now());
        return clubRepository.save(club);
    }

    public void toggleMuteClub(String userId, String clubId, boolean isMuted) {
        ClubMember member = clubMemberRepository.findByClubIdAndUserId(clubId, userId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("ClubMember", userId));
        member.setMuted(isMuted);
        clubMemberRepository.save(member);
    }

    @CacheEvict(value = "clubs", key = "#clubId")
    public Club uploadMemory(String userId, String clubId, String imageUrl) {
        Club club = getClubById(clubId);
        if (!hasMemoryPermission(userId, clubId)) {
            throw new BusinessException.BadRequestException("Unauthorized: You do not have permission to manage memories.");
        }
        if (club.getMemories() == null) {
            club.setMemories(new java.util.ArrayList<>());
        }
        club.getMemories().add(imageUrl);
        club.setUpdatedAt(Instant.now());
        return clubRepository.save(club);
    }

    @CacheEvict(value = "clubs", key = "#clubId")
    public Club deleteMemory(String userId, String clubId, String imageUrl) {
        Club club = getClubById(clubId);
        if (!hasMemoryPermission(userId, clubId)) {
            throw new BusinessException.BadRequestException("Unauthorized: You do not have permission to manage memories.");
        }
        if (club.getMemories() != null) {
            club.getMemories().remove(imageUrl);
        }
        club.setUpdatedAt(Instant.now());
        return clubRepository.save(club);
    }

    private boolean hasMemoryPermission(String userId, String clubId) {
        if (isClubLeaderOrManager(userId, clubId)) {
            return true;
        }
        Optional<ClubMember> memberOpt = clubMemberRepository.findByClubIdAndUserId(clubId, userId);
        if (memberOpt.isPresent()) {
            ClubMember m = memberOpt.get();
            return m.getAccessControls() != null && m.getAccessControls().contains("manage_memories");
        }
        return false;
    }

    public List<Club> getClubsByIds(List<String> clubIds) {
        if (clubIds == null || clubIds.isEmpty()) {
            return List.of();
        }
        return clubRepository.findAllById(clubIds);
    }

    public Optional<ClubMember> getClubMember(String clubId, String userId) {
        return clubMemberRepository.findByClubIdAndUserId(clubId, userId);
    }
}
