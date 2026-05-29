package com.cax.cax_backend.club.controller;

import com.cax.cax_backend.club.model.Club;
import com.cax.cax_backend.club.model.ClubJoinRequest;
import com.cax.cax_backend.club.model.ClubMember;
import com.cax.cax_backend.club.service.ClubService;
import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.service.UserService;
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClubDetailResponse {
        private Club club;
        private String userRole; // "President", "Vice President", "Member", etc. (null if not member)
        private String joinRequestStatus; // "PENDING", "ACCEPTED", "REJECTED" (null if no request)
        private List<String> userPermissions;
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

    @PostMapping
    public ResponseEntity<ApiResponse<Club>> createClub(Authentication auth, @RequestBody Club clubData) {
        String userId = (String) auth.getPrincipal();
        Club created = clubService.createClub(userId, clubData);
        return ResponseEntity.ok(ApiResponse.created("Club created successfully", created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Club>>> getClubs(Authentication auth, @RequestParam(required = false) String collegeId) {
        String finalCollegeId = collegeId;
        if (finalCollegeId == null || finalCollegeId.isBlank()) {
            String userId = (String) auth.getPrincipal();
            User user = userService.getUserByUserId(userId);
            if (user.getCollegeDetails() != null) {
                finalCollegeId = user.getCollegeDetails().getCollegeId();
            }
        }
        
        if (finalCollegeId == null) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }
        
        return ResponseEntity.ok(ApiResponse.success(clubService.getClubsByCollege(finalCollegeId)));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<Club>>> getMyClubs(Authentication auth) {
        String userId = (String) auth.getPrincipal();
        List<ClubMember> memberships = clubService.getUserMemberships(userId);
        List<Club> myClubs = memberships.stream()
                .map(m -> {
                    try {
                        return clubService.getClubById(m.getClubId());
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(c -> c != null)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(myClubs));
    }

    @GetMapping("/{clubId}")
    public ResponseEntity<ApiResponse<ClubDetailResponse>> getClubDetails(Authentication auth, @PathVariable String clubId) {
        String userId = (String) auth.getPrincipal();
        Club club = clubService.getClubById(clubId);
        
        Optional<ClubMember> membership = clubService.getClubMembers(clubId).stream()
                .filter(m -> userId.equals(m.getUserId()))
                .findFirst();
        
        Optional<ClubJoinRequest> joinRequest = clubService.getUserJoinRequest(clubId, userId);

        List<String> permissions = List.of();
        if (membership.isPresent()) {
            ClubMember m = membership.get();
            if ("President".equalsIgnoreCase(m.getRole()) || "Vice President".equalsIgnoreCase(m.getRole())) {
                permissions = List.of("manage_events", "manage_members", "manage_settings", "manage_posts");
            } else {
                permissions = m.getAccessControls();
                if (permissions == null) {
                    permissions = List.of();
                }
            }
        } else {
            // Check system-level bypasses for guests
            try {
                User user = userService.getUserByUserId(userId);
                if (user.getRole() == com.cax.cax_backend.common.enums.UserRole.ADMIN || (user.getRole() == com.cax.cax_backend.common.enums.UserRole.SUPER_STUDENT && user.isIdVerified())) {
                    permissions = List.of("manage_events", "manage_members", "manage_settings", "manage_posts");
                }
            } catch (Exception e) {}
        }

        ClubDetailResponse response = ClubDetailResponse.builder()
                .club(club)
                .userRole(membership.map(ClubMember::getRole).orElse(null))
                .joinRequestStatus(joinRequest.map(ClubJoinRequest::getStatus).orElse(null))
                .userPermissions(permissions)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{clubId}/join")
    public ResponseEntity<ApiResponse<Map<String, Object>>> joinClub(Authentication auth, @PathVariable String clubId) {
        String userId = (String) auth.getPrincipal();
        Map<String, Object> joinResult = clubService.joinClub(userId, clubId);
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
    public ResponseEntity<ApiResponse<Void>> updateSettings(Authentication auth, @PathVariable String clubId, @RequestParam boolean isApprovalRequired) {
        String leaderUserId = (String) auth.getPrincipal();
        clubService.updateClubSettings(leaderUserId, clubId, isApprovalRequired);
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
    public ResponseEntity<ApiResponse<List<ClubMember>>> getMembers(@PathVariable String clubId) {
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
}
