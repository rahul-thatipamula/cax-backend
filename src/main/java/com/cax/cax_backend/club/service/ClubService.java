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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClubService {

    private final ClubRepository clubRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final ClubJoinRequestRepository clubJoinRequestRepository;
    private final UserService userService;

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

    public Club getClubById(String clubId) {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Club", clubId));
    }

    public Map<String, Object> joinClub(String userId, String clubId) {
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

        if (club.isApprovalRequired()) {
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
                    .requestedAt(Instant.now())
                    .build();

            clubJoinRequestRepository.save(joinRequest);
            result.put("status", "PENDING");
            result.put("message", "Join request sent successfully, awaiting approval.");
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

        // Assign President
        if (presidentUserId != null && !presidentUserId.isBlank()) {
            User presUser = userService.getUserByUserId(presidentUserId);
            club.setPresidentId(presidentUserId);
            
            Optional<ClubMember> existingPres = clubMemberRepository.findByClubIdAndUserId(clubId, presidentUserId);
            List<String> presPerms = List.of("manage_events", "manage_members", "manage_settings", "manage_posts");
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
        } else {
            club.setPresidentId(null);
        }

        // Assign VP
        if (vicePresidentUserId != null && !vicePresidentUserId.isBlank()) {
            User vpUser = userService.getUserByUserId(vicePresidentUserId);
            club.setVicePresidentId(vicePresidentUserId);
            
            Optional<ClubMember> existingVp = clubMemberRepository.findByClubIdAndUserId(clubId, vicePresidentUserId);
            List<String> vpPerms = List.of("manage_events", "manage_members", "manage_settings", "manage_posts");
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
        } else if ("REJECTED".equalsIgnoreCase(status)) {
            request.setStatus("REJECTED");
            clubJoinRequestRepository.save(request);
        } else {
            throw new BusinessException.BadRequestException("Invalid status update.");
        }
    }

    public void updateClubSettings(String leaderUserId, String clubId, boolean isApprovalRequired) {
        Club club = getClubById(clubId);
        verifyLeader(leaderUserId, club);

        club.setApprovalRequired(isApprovalRequired);
        club.setUpdatedAt(Instant.now());
        clubRepository.save(club);
    }

    public void updateAllowJoining(String leaderUserId, String clubId, boolean allowJoining) {
        Club club = getClubById(clubId);
        verifyLeader(leaderUserId, club);

        club.setAllowJoining(allowJoining);
        club.setUpdatedAt(Instant.now());
        clubRepository.save(club);
    }

    public void toggleAllowJoining(String leaderUserId, String clubId) {
        Club club = getClubById(clubId);
        verifyLeader(leaderUserId, club);

        club.setAllowJoining(!club.isAllowJoining());
        club.setUpdatedAt(Instant.now());
        clubRepository.save(club);
    }

    public void updateMemberRole(String leaderUserId, String clubId, String memberUserId, String role) {
        Club club = getClubById(clubId);
        verifyLeader(leaderUserId, club);

        ClubMember member = clubMemberRepository.findByClubIdAndUserId(clubId, memberUserId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("ClubMember", memberUserId));

        if (memberUserId.equals(club.getPresidentId()) || memberUserId.equals(club.getVicePresidentId())) {
            throw new BusinessException.BadRequestException("Cannot change President or Vice President roles directly. Use Super Student assignment.");
        }

        member.setRole(role);
        clubMemberRepository.save(member);
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

        // Also check if admin or super student
        try {
            User user = userService.getUserByUserId(userId);
            return user.getRole() == UserRole.ADMIN || (user.getRole() == UserRole.SUPER_STUDENT && user.isIdVerified());
        } catch (Exception e) {
            return false;
        }
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
        try {
            User user = userService.getUserByUserId(userId);
            if (user.getRole() == UserRole.ADMIN || (user.getRole() == UserRole.SUPER_STUDENT && user.isIdVerified())) {
                return;
            }
        } catch (Exception e) {}
        throw new BusinessException.BadRequestException("Unauthorized: Only the President or Vice President can perform this action.");
    }

    public void updateMemberPermissions(String leaderUserId, String clubId, String memberUserId, List<String> permissions) {
        Club club = getClubById(clubId);
        verifyLeader(leaderUserId, club);

        ClubMember member = clubMemberRepository.findByClubIdAndUserId(clubId, memberUserId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("ClubMember", memberUserId));

        if (memberUserId.equals(club.getPresidentId())) {
            throw new BusinessException.BadRequestException("Cannot change President's access list directly.");
        }

        member.setAccessControls(permissions);
        clubMemberRepository.save(member);
    }

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

    public Club updateClubProfile(String leaderUserId, String clubId, String name, String logo, String description, String coverPhoto) {
        Club club = getClubById(clubId);
        verifyPresidentOrVP(leaderUserId, club);

        if (name != null && !name.trim().isEmpty()) {
            club.setName(name.trim());
        }
        if (logo != null && !logo.trim().isEmpty()) {
            club.setLogo(logo.trim());
        }
        if (description != null) {
            club.setDescription(description.trim());
        }
        if (coverPhoto != null) {
            club.setCoverPhoto(coverPhoto.trim());
        }

        club.setUpdatedAt(Instant.now());
        return clubRepository.save(club);
    }
}
