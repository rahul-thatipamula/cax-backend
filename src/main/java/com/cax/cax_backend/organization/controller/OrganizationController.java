package com.cax.cax_backend.organization.controller;

import com.cax.cax_backend.organization.model.Organization;
import com.cax.cax_backend.organization.model.OrganizationJoinRequest;
import com.cax.cax_backend.organization.model.OrganizationMember;
import com.cax.cax_backend.organization.service.OrganizationService;
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
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;
    private final UserService userService;
    private final NotificationRepository notificationRepository;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrganizationDetailResponse {
        private Organization organization;
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JoinClubRequest {
        private String paymentScreenshot;
        private String utr;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Organization>> createOrganization(Authentication auth, @RequestBody Organization clubData) {
        String userId = (String) auth.getPrincipal();
        Organization created = organizationService.createOrganization(userId, clubData);
        return ResponseEntity.ok(ApiResponse.created("Organization created successfully", created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Organization>>> getClubs(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        String userId = (String) auth.getPrincipal();
        User user = userService.getUserByUserId(userId);
        if (user.getCollegeDetails() == null || user.getCollegeDetails().getCollegeId() == null) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }
        String collegeId = user.getCollegeDetails().getCollegeId();
        return ResponseEntity.ok(ApiResponse.success(organizationService.getOrganizationsByCollege(collegeId, page, size)));
    }

    /** Admin-only — lists organizations across all colleges (or filtered by collegeId) for the admin console. */
    @GetMapping("/admin/all")
    public ResponseEntity<ApiResponse<List<Organization>>> getAllOrganizationsForAdmin(
            Authentication auth,
            @RequestParam(required = false) String collegeId) {
        String userId = (String) auth.getPrincipal();
        User user = userService.getUserByUserId(userId);
        if (user.getRole() != com.cax.cax_backend.common.enums.UserRole.ADMIN) {
            throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("Only Admins can view all organizations.");
        }
        return ResponseEntity.ok(ApiResponse.success(organizationService.getAllOrganizations(collegeId)));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<Organization>>> getMyClubs(Authentication auth) {
        String userId = (String) auth.getPrincipal();
        List<OrganizationMember> memberships = organizationService.getUserOrganizationMemberships(userId);
        List<String> organizationIds = memberships.stream()
                .map(OrganizationMember::getOrganizationId)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        List<Organization> myOrganizations = organizationService.getOrganizationsByIds(organizationIds);
        return ResponseEntity.ok(ApiResponse.success(myOrganizations));
    }

    /** Returns orgs where the caller is President or Vice President — used by the org game-management portal. */
    @GetMapping("/my-leadership")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMyLeadership(Authentication auth) {
        String userId = (String) auth.getPrincipal();
        List<OrganizationMember> allMemberships = organizationService.getUserOrganizationMemberships(userId);

        List<OrganizationMember> leaderMemberships = allMemberships.stream()
                .filter(m -> "President".equalsIgnoreCase(m.getRole()) || "Vice President".equalsIgnoreCase(m.getRole()))
                .toList();

        if (leaderMemberships.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }

        List<String> orgIds = leaderMemberships.stream()
                .map(OrganizationMember::getOrganizationId)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        List<Organization> orgs = organizationService.getOrganizationsByIds(orgIds);

        List<Map<String, Object>> result = leaderMemberships.stream()
                .map(m -> {
                    Organization org = orgs.stream()
                            .filter(o -> o.getId().equals(m.getOrganizationId()))
                            .findFirst().orElse(null);
                    if (org == null) return null;
                    java.util.Map<String, Object> item = new java.util.HashMap<>();
                    item.put("organizationId", org.getId());
                    item.put("organizationName", org.getName());
                    item.put("logo", org.getLogo() != null ? org.getLogo() : "");
                    item.put("role", m.getRole());
                    return item;
                })
                .filter(item -> item != null)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{organizationId}")
    public ResponseEntity<ApiResponse<OrganizationDetailResponse>> getClubDetails(Authentication auth, @PathVariable String organizationId) {
        String userId = (String) auth.getPrincipal();
        Organization organization = organizationService.getOrganizationById(organizationId);
        User user = userService.getUserByUserId(userId);
        
        boolean isSystemAdmin = user.getRole() == com.cax.cax_backend.common.enums.UserRole.ADMIN;
        if (!isSystemAdmin) {
            if (user.getCollegeDetails() == null || user.getCollegeDetails().getCollegeId() == null) {
                throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("User has no college assigned.");
            }
            if (!organization.getCollegeId().equals(user.getCollegeDetails().getCollegeId())) {
                throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("You cannot access an organization from another college.");
            }
        }
        
        Optional<OrganizationMember> membership = organizationService.getOrganizationMember(organizationId, userId);
        
        Optional<OrganizationJoinRequest> joinRequest = organizationService.getUserOrganizationJoinRequest(organizationId, userId);

        List<String> permissions = List.of();
        if (membership.isPresent()) {
            OrganizationMember m = membership.get();
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

        OrganizationDetailResponse response = OrganizationDetailResponse.builder()
                .organization(organization)
                .userRole(membership.map(OrganizationMember::getRole).orElse(null))
                .joinRequestStatus(joinRequest.map(OrganizationJoinRequest::getStatus).orElse(null))
                .userPermissions(permissions)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{organizationId}/join")
    public ResponseEntity<ApiResponse<Map<String, Object>>> joinOrganization(
            Authentication auth, 
            @PathVariable String organizationId,
            @RequestBody(required = false) JoinClubRequest joinRequestDto) {
        String userId = (String) auth.getPrincipal();
        String paymentScreenshot = null;
        String utr = null;
        if (joinRequestDto != null) {
            paymentScreenshot = joinRequestDto.getPaymentScreenshot();
            utr = joinRequestDto.getUtr();
        }
        Map<String, Object> joinResult = organizationService.joinOrganization(userId, organizationId, paymentScreenshot, utr);
        return ResponseEntity.ok(ApiResponse.success("Success", joinResult));
    }

    @PostMapping("/{organizationId}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveOrganization(Authentication auth, @PathVariable String organizationId) {
        String userId = (String) auth.getPrincipal();
        organizationService.leaveOrganization(userId, organizationId);
        return ResponseEntity.ok(ApiResponse.success("Left club successfully"));
    }

    @PostMapping("/{organizationId}/assign-leaders")
    public ResponseEntity<ApiResponse<Void>> assignOrganizationLeaders(Authentication auth, @PathVariable String organizationId, @RequestBody Map<String, String> body) {
        String creatorUserId = (String) auth.getPrincipal();
        String presidentUserId = body.get("presidentUserId");
        String vicePresidentUserId = body.get("vicePresidentUserId");
        
        organizationService.assignOrganizationLeaders(creatorUserId, organizationId, presidentUserId, vicePresidentUserId);
        return ResponseEntity.ok(ApiResponse.success("Leaders assigned successfully"));
    }

    @GetMapping("/{organizationId}/requests")
    public ResponseEntity<ApiResponse<List<OrganizationJoinRequest>>> getOrganizationJoinRequests(Authentication auth, @PathVariable String organizationId) {
        String leaderUserId = (String) auth.getPrincipal();
        List<OrganizationJoinRequest> requests = organizationService.getOrganizationJoinRequests(leaderUserId, organizationId);
        return ResponseEntity.ok(ApiResponse.success(requests));
    }

    @PutMapping("/{organizationId}/requests/{requestId}")
    public ResponseEntity<ApiResponse<Void>> manageOrganizationJoinRequest(Authentication auth, @PathVariable String organizationId, @PathVariable String requestId, @RequestParam String status) {
        String leaderUserId = (String) auth.getPrincipal();
        organizationService.manageOrganizationJoinRequest(leaderUserId, organizationId, requestId, status);
        return ResponseEntity.ok(ApiResponse.success("Request updated successfully"));
    }

    @PutMapping("/{organizationId}/settings")
    public ResponseEntity<ApiResponse<Void>> updateSettings(
            Authentication auth, 
            @PathVariable String organizationId, 
            @RequestParam boolean isApprovalRequired,
            @RequestParam(required = false, defaultValue = "false") boolean isPaid,
            @RequestParam(required = false, defaultValue = "0.0") Double price,
            @RequestParam(required = false) String upiId,
            @RequestParam(required = false) String qrCodeUrl) {
        String leaderUserId = (String) auth.getPrincipal();
        organizationService.updateOrganizationSettings(leaderUserId, organizationId, isApprovalRequired, isPaid, price, upiId, qrCodeUrl);
        return ResponseEntity.ok(ApiResponse.success("Settings updated successfully"));
    }

    @PutMapping("/{organizationId}/allow-joining")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateOrganizationAllowJoining(Authentication auth, @PathVariable String organizationId, @RequestBody Map<String, Boolean> body) {
        String leaderUserId = (String) auth.getPrincipal();
        Boolean allowJoining = body.get("allowJoining");
        if (allowJoining == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("allowJoining field is required", 1400, 400));
        }
        organizationService.updateOrganizationAllowJoining(leaderUserId, organizationId, allowJoining);
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("allowJoining", allowJoining);
        response.put("message", allowJoining ? "Organization joining is now enabled" : "Organization joining is now disabled");
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{organizationId}/toggle-joining")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleOrganizationAllowJoining(Authentication auth, @PathVariable String organizationId) {
        String leaderUserId = (String) auth.getPrincipal();
        organizationService.toggleOrganizationAllowJoining(leaderUserId, organizationId);
        Organization organization = organizationService.getOrganizationById(organizationId);
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("allowJoining", organization.isAllowJoining());
        response.put("message", organization.isAllowJoining() ? "Organization joining is now enabled" : "Organization joining is now disabled");
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{organizationId}/members")
    public ResponseEntity<ApiResponse<List<OrganizationMember>>> getMembers(
            Authentication auth,
            @PathVariable String organizationId) {
        String userId = (String) auth.getPrincipal();
        Organization organization = organizationService.getOrganizationById(organizationId);
        User user = userService.getUserByUserId(userId);
        boolean isSystemAdmin = user.getRole() == com.cax.cax_backend.common.enums.UserRole.ADMIN;
        if (!isSystemAdmin) {
            if (user.getCollegeDetails() == null || user.getCollegeDetails().getCollegeId() == null) {
                throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("User has no college assigned.");
            }
            if (!organization.getCollegeId().equals(user.getCollegeDetails().getCollegeId())) {
                throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("You cannot access members of an organization from another college.");
            }
        }
        List<OrganizationMember> members = organizationService.getOrganizationMembers(organizationId);
        return ResponseEntity.ok(ApiResponse.success(members));
    }

    @PutMapping("/{organizationId}/members/{memberUserId}/role")
    public ResponseEntity<ApiResponse<Void>> updateOrganizationMemberRole(Authentication auth, @PathVariable String organizationId, @PathVariable String memberUserId, @RequestParam String role) {
        String leaderUserId = (String) auth.getPrincipal();
        organizationService.updateOrganizationMemberRole(leaderUserId, organizationId, memberUserId, role);
        return ResponseEntity.ok(ApiResponse.success("Member role updated successfully"));
    }

    @PutMapping("/{organizationId}/members/{memberUserId}/permissions")
    public ResponseEntity<ApiResponse<Void>> updateOrganizationMemberPermissions(
            Authentication auth, 
            @PathVariable String organizationId, 
            @PathVariable String memberUserId, 
            @RequestBody List<String> permissions) {
        String leaderUserId = (String) auth.getPrincipal();
        organizationService.updateOrganizationMemberPermissions(leaderUserId, organizationId, memberUserId, permissions);
        return ResponseEntity.ok(ApiResponse.success("Member permissions updated successfully"));
    }

    @DeleteMapping("/{organizationId}/members/{memberUserId}")
    public ResponseEntity<ApiResponse<Void>> removeOrganizationMember(Authentication auth, @PathVariable String organizationId, @PathVariable String memberUserId) {
        String leaderUserId = (String) auth.getPrincipal();
        organizationService.removeOrganizationMember(leaderUserId, organizationId, memberUserId);
        return ResponseEntity.ok(ApiResponse.success("Member removed from club successfully"));
    }

    @PutMapping("/{organizationId}/roles")
    public ResponseEntity<ApiResponse<Organization>> updateCustomRoles(
            Authentication auth,
            @PathVariable String organizationId,
            @RequestBody List<Organization.OrganizationRole> customRoles) {
        String leaderUserId = (String) auth.getPrincipal();
        Organization updated = organizationService.updateOrganizationRoles(leaderUserId, organizationId, customRoles);
        return ResponseEntity.ok(ApiResponse.success("Custom roles updated successfully", updated));
    }

    @PutMapping("/{organizationId}/profile")
    public ResponseEntity<ApiResponse<Organization>> updateOrganizationProfile(
            Authentication auth,
            @PathVariable String organizationId,
            @RequestBody UpdateClubProfileRequest request) {
        String leaderUserId = (String) auth.getPrincipal();
        Organization updated = organizationService.updateOrganizationProfile(
                leaderUserId, 
                organizationId, 
                request.getName(), 
                request.getLogo(), 
                request.getDescription(),
                request.getCoverPhoto()
        );
        return ResponseEntity.ok(ApiResponse.success("Organization profile updated successfully", updated));
    }

    @PostMapping("/{organizationId}/memories")
    public ResponseEntity<ApiResponse<Organization>> uploadOrganizationMemory(
            Authentication auth,
            @PathVariable String organizationId,
            @RequestBody Map<String, String> body) {
        String userId = (String) auth.getPrincipal();
        String imageUrl = body.get("imageUrl");
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("imageUrl is required");
        }
        Organization updated = organizationService.uploadOrganizationMemory(userId, organizationId, imageUrl);
        return ResponseEntity.ok(ApiResponse.success("Memory uploaded successfully", updated));
    }

    @DeleteMapping("/{organizationId}/memories")
    public ResponseEntity<ApiResponse<Organization>> deleteOrganizationMemory(
            Authentication auth,
            @PathVariable String organizationId,
            @RequestParam String imageUrl) {
        String userId = (String) auth.getPrincipal();
        Organization updated = organizationService.deleteOrganizationMemory(userId, organizationId, imageUrl);
        return ResponseEntity.ok(ApiResponse.success("Memory deleted successfully", updated));
    }

    /**
     * Super Student / Admin only — change an organization's type (Organization, Community, Society, …).
     * Adding a new type in future only requires updating the OrganizationType enum.
     */
    @PutMapping("/{organizationId}/type")
    public ResponseEntity<ApiResponse<Organization>> updateOrganizationType(
            Authentication auth,
            @PathVariable String organizationId,
            @RequestBody Map<String, String> body) {
        String userId = (String) auth.getPrincipal();
        String type = body.get("type");
        if (type == null || type.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("type field is required", 1400, 400));
        }
        Organization updated = organizationService.updateOrganizationType(userId, organizationId, type);
        return ResponseEntity.ok(ApiResponse.success("Organization type updated successfully", updated));
    }
}
