package com.cax.cax_backend.club.controller;

import com.cax.cax_backend.club.model.Club;
import com.cax.cax_backend.club.model.ClubJoinRequest;
import com.cax.cax_backend.club.model.ClubMember;
import com.cax.cax_backend.club.service.ClubService;
import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.service.UserService;
import com.cax.cax_backend.notification.repository.NotificationRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/clubs")
@RequiredArgsConstructor
public class ClubController {

    private final ClubService clubService;
    private final UserService userService;
    private final NotificationRepository notificationRepository;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClubDetailResponse {
        private Club club;
        private String userRole; // "President", "Vice President", "Member", etc. (null if not member)
        private String joinRequestStatus; // "PENDING", "ACCEPTED", "REJECTED" (null if no request)
        private List<String> userPermissions;
        private long unreadChatCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateClubProfileRequest {
        private String name;
        private String logo;
        private String description;
        private String coverPhoto;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JoinClubRequest {
        private String paymentScreenshot;
        private String utr;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Club>> createClub(Authentication auth, @RequestBody Club clubData) {
        String userId = (String) auth.getPrincipal();
        Club created = clubService.createClub(userId, clubData);
        return ResponseEntity.ok(ApiResponse.created("Club created successfully", created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Club>>> getClubs(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        String userId = (String) auth.getPrincipal();
        User user = userService.getUserByUserId(userId);
        if (user.getCollegeDetails() == null || user.getCollegeDetails().getCollegeId() == null) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }
        String collegeId = user.getCollegeDetails().getCollegeId();
        return ResponseEntity.ok(ApiResponse.success(clubService.getClubsByCollege(collegeId, page, size)));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<Club>>> getMyClubs(Authentication auth) {
        String userId = (String) auth.getPrincipal();
        List<ClubMember> memberships = clubService.getUserMemberships(userId);
        List<String> clubIds = memberships.stream()
                .map(ClubMember::getClubId)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        List<Club> myClubs = clubService.getClubsByIds(clubIds);
        return ResponseEntity.ok(ApiResponse.success(myClubs));
    }

    @GetMapping("/{clubId}")
    public ResponseEntity<ApiResponse<ClubDetailResponse>> getClubDetails(Authentication auth, @PathVariable String clubId) {
        String userId = (String) auth.getPrincipal();
        Club club = clubService.getClubById(clubId);
        User user = userService.getUserByUserId(userId);
        
        boolean isSystemAdmin = user.getRole() == com.cax.cax_backend.common.enums.UserRole.ADMIN;
        if (!isSystemAdmin) {
            if (user.getCollegeDetails() == null || user.getCollegeDetails().getCollegeId() == null) {
                throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("User has no college assigned.");
            }
            if (!club.getCollegeId().equals(user.getCollegeDetails().getCollegeId())) {
                throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("You cannot access a club from another college.");
            }
        }
        
        Optional<ClubMember> membership = clubService.getClubMember(clubId, userId);
        
        Optional<ClubJoinRequest> joinRequest = clubService.getUserJoinRequest(clubId, userId);

        List<String> permissions = List.of();
        if (membership.isPresent()) {
            ClubMember m = membership.get();
            if ("President".equalsIgnoreCase(m.getRole()) || "Vice President".equalsIgnoreCase(m.getRole())) {
                permissions = List.of("manage_events", "manage_members", "manage_settings", "manage_posts", "manage_memories");
            } else {
                permissions = m.getAccessControls();
                if (permissions == null) {
                    permissions = List.of();
                }
            }
        }
        // Non-members get no permissions — they must join the club first

        long unreadCount = 0;
        if (membership.isPresent()) {
            try {
                unreadCount = notificationRepository.countUnreadChatNotifications(
                    userId,
                    com.cax.cax_backend.common.enums.NotificationEnums.NotificationType.CLUB_CHAT,
                    clubId
                );
            } catch (Exception e) {
                // Ignore or log
            }
        }

        ClubDetailResponse response = ClubDetailResponse.builder()
                .club(club)
                .userRole(membership.map(ClubMember::getRole).orElse(null))
                .joinRequestStatus(joinRequest.map(ClubJoinRequest::getStatus).orElse(null))
                .userPermissions(permissions)
                .unreadChatCount(unreadCount)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{clubId}/join")
    public ResponseEntity<ApiResponse<Map<String, Object>>> joinClub(
            Authentication auth, 
            @PathVariable String clubId,
            @RequestBody(required = false) JoinClubRequest joinRequestDto) {
        String userId = (String) auth.getPrincipal();
        String paymentScreenshot = null;
        String utr = null;
        if (joinRequestDto != null) {
            paymentScreenshot = joinRequestDto.getPaymentScreenshot();
            utr = joinRequestDto.getUtr();
        }
        Map<String, Object> joinResult = clubService.joinClub(userId, clubId, paymentScreenshot, utr);
        return ResponseEntity.ok(ApiResponse.success("Success", joinResult));
    }

    @PostMapping("/{clubId}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveClub(Authentication auth, @PathVariable String clubId) {
        String userId = (String) auth.getPrincipal();
        clubService.leaveClub(userId, clubId);
        return ResponseEntity.ok(ApiResponse.success("Left club successfully"));
    }

    @PostMapping("/{clubId}/assign-leaders")
    public ResponseEntity<ApiResponse<Void>> assignLeaders(Authentication auth, @PathVariable String clubId, @RequestBody Map<String, String> body) {
        String creatorUserId = (String) auth.getPrincipal();
        String presidentUserId = body.get("presidentUserId");
        String vicePresidentUserId = body.get("vicePresidentUserId");
        
        clubService.assignLeaders(creatorUserId, clubId, presidentUserId, vicePresidentUserId);
        return ResponseEntity.ok(ApiResponse.success("Leaders assigned successfully"));
    }

    @GetMapping("/{clubId}/requests")
    public ResponseEntity<ApiResponse<List<ClubJoinRequest>>> getJoinRequests(Authentication auth, @PathVariable String clubId) {
        String leaderUserId = (String) auth.getPrincipal();
        List<ClubJoinRequest> requests = clubService.getJoinRequests(leaderUserId, clubId);
        return ResponseEntity.ok(ApiResponse.success(requests));
    }

    @PutMapping("/{clubId}/requests/{requestId}")
    public ResponseEntity<ApiResponse<Void>> manageJoinRequest(Authentication auth, @PathVariable String clubId, @PathVariable String requestId, @RequestParam String status) {
        String leaderUserId = (String) auth.getPrincipal();
        clubService.manageJoinRequest(leaderUserId, clubId, requestId, status);
        return ResponseEntity.ok(ApiResponse.success("Request updated successfully"));
    }

    @PutMapping("/{clubId}/settings")
    public ResponseEntity<ApiResponse<Void>> updateSettings(
            Authentication auth, 
            @PathVariable String clubId, 
            @RequestParam boolean isApprovalRequired,
            @RequestParam(required = false, defaultValue = "false") boolean isPaid,
            @RequestParam(required = false, defaultValue = "0.0") Double price,
            @RequestParam(required = false) String upiId,
            @RequestParam(required = false) String qrCodeUrl) {
        String leaderUserId = (String) auth.getPrincipal();
        clubService.updateClubSettings(leaderUserId, clubId, isApprovalRequired, isPaid, price, upiId, qrCodeUrl);
        return ResponseEntity.ok(ApiResponse.success("Settings updated successfully"));
    }

    @PutMapping("/{clubId}/allow-joining")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateAllowJoining(Authentication auth, @PathVariable String clubId, @RequestBody Map<String, Boolean> body) {
        String leaderUserId = (String) auth.getPrincipal();
        Boolean allowJoining = body.get("allowJoining");
        if (allowJoining == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("allowJoining field is required", 1400, 400));
        }
        clubService.updateAllowJoining(leaderUserId, clubId, allowJoining);
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("allowJoining", allowJoining);
        response.put("message", allowJoining ? "Club joining is now enabled" : "Club joining is now disabled");
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{clubId}/toggle-joining")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleAllowJoining(Authentication auth, @PathVariable String clubId) {
        String leaderUserId = (String) auth.getPrincipal();
        clubService.toggleAllowJoining(leaderUserId, clubId);
        Club club = clubService.getClubById(clubId);
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("allowJoining", club.isAllowJoining());
        response.put("message", club.isAllowJoining() ? "Club joining is now enabled" : "Club joining is now disabled");
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{clubId}/members")
    public ResponseEntity<ApiResponse<List<ClubMember>>> getMembers(
            Authentication auth,
            @PathVariable String clubId) {
        String userId = (String) auth.getPrincipal();
        Club club = clubService.getClubById(clubId);
        User user = userService.getUserByUserId(userId);
        boolean isSystemAdmin = user.getRole() == com.cax.cax_backend.common.enums.UserRole.ADMIN;
        if (!isSystemAdmin) {
            if (user.getCollegeDetails() == null || user.getCollegeDetails().getCollegeId() == null) {
                throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("User has no college assigned.");
            }
            if (!club.getCollegeId().equals(user.getCollegeDetails().getCollegeId())) {
                throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("You cannot access members of a club from another college.");
            }
        }
        List<ClubMember> members = clubService.getClubMembers(clubId);
        return ResponseEntity.ok(ApiResponse.success(members));
    }

    @PutMapping("/{clubId}/members/{memberUserId}/role")
    public ResponseEntity<ApiResponse<Void>> updateMemberRole(Authentication auth, @PathVariable String clubId, @PathVariable String memberUserId, @RequestParam String role) {
        String leaderUserId = (String) auth.getPrincipal();
        clubService.updateMemberRole(leaderUserId, clubId, memberUserId, role);
        return ResponseEntity.ok(ApiResponse.success("Member role updated successfully"));
    }

    @PutMapping("/{clubId}/members/{memberUserId}/permissions")
    public ResponseEntity<ApiResponse<Void>> updateMemberPermissions(
            Authentication auth, 
            @PathVariable String clubId, 
            @PathVariable String memberUserId, 
            @RequestBody List<String> permissions) {
        String leaderUserId = (String) auth.getPrincipal();
        clubService.updateMemberPermissions(leaderUserId, clubId, memberUserId, permissions);
        return ResponseEntity.ok(ApiResponse.success("Member permissions updated successfully"));
    }

    @DeleteMapping("/{clubId}/members/{memberUserId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(Authentication auth, @PathVariable String clubId, @PathVariable String memberUserId) {
        String leaderUserId = (String) auth.getPrincipal();
        clubService.removeMember(leaderUserId, clubId, memberUserId);
        return ResponseEntity.ok(ApiResponse.success("Member removed from club successfully"));
    }

    @PutMapping("/{clubId}/roles")
    public ResponseEntity<ApiResponse<Club>> updateCustomRoles(
            Authentication auth,
            @PathVariable String clubId,
            @RequestBody List<Club.ClubRole> customRoles) {
        String leaderUserId = (String) auth.getPrincipal();
        Club updated = clubService.updateClubRoles(leaderUserId, clubId, customRoles);
        return ResponseEntity.ok(ApiResponse.success("Custom roles updated successfully", updated));
    }

    @PutMapping("/{clubId}/profile")
    public ResponseEntity<ApiResponse<Club>> updateClubProfile(
            Authentication auth,
            @PathVariable String clubId,
            @RequestBody UpdateClubProfileRequest request) {
        String leaderUserId = (String) auth.getPrincipal();
        Club updated = clubService.updateClubProfile(
                leaderUserId, 
                clubId, 
                request.getName(), 
                request.getLogo(), 
                request.getDescription(),
                request.getCoverPhoto()
        );
        return ResponseEntity.ok(ApiResponse.success("Club profile updated successfully", updated));
    }

    @PostMapping("/{clubId}/memories")
    public ResponseEntity<ApiResponse<Club>> uploadMemory(
            Authentication auth,
            @PathVariable String clubId,
            @RequestBody Map<String, String> body) {
        String userId = (String) auth.getPrincipal();
        String imageUrl = body.get("imageUrl");
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("imageUrl is required");
        }
        Club updated = clubService.uploadMemory(userId, clubId, imageUrl);
        return ResponseEntity.ok(ApiResponse.success("Memory uploaded successfully", updated));
    }

    @DeleteMapping("/{clubId}/memories")
    public ResponseEntity<ApiResponse<Club>> deleteMemory(
            Authentication auth,
            @PathVariable String clubId,
            @RequestParam String imageUrl) {
        String userId = (String) auth.getPrincipal();
        Club updated = clubService.deleteMemory(userId, clubId, imageUrl);
        return ResponseEntity.ok(ApiResponse.success("Memory deleted successfully", updated));
    }
}
