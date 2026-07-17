package com.cax.cax_backend.event.controller;

import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.event.service.EventTeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EventTeamController {

    private final EventTeamService eventTeamService;

    private String requireUserId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        return (String) auth.getPrincipal();
    }

    /**
     * Create a team and register the caller as its leader.
     * Body: { "teamName": "...", ...requiredFields }
     */
    @PostMapping("/events/{eventId}/teams")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createTeam(
            Authentication auth,
            @PathVariable String eventId,
            @RequestBody Map<String, Object> body) {
        String userId = requireUserId(auth);
        String teamName = body.get("teamName") != null ? body.get("teamName").toString() : null;
        Map<String, Object> details = new HashMap<>(body);
        details.remove("teamName");
        Map<String, Object> result = eventTeamService.createTeam(userId, eventId, teamName, details);
        return ResponseEntity.ok(ApiResponse.created("Team created successfully", result));
    }

    /**
     * Join an existing team with its invite code.
     * Body: { "teamCode": "...", ...requiredFields }
     */
    @PostMapping("/events/{eventId}/teams/join")
    public ResponseEntity<ApiResponse<Map<String, Object>>> joinTeam(
            Authentication auth,
            @PathVariable String eventId,
            @RequestBody Map<String, Object> body) {
        String userId = requireUserId(auth);
        String teamCode = body.get("teamCode") != null ? body.get("teamCode").toString() : null;
        Map<String, Object> details = new HashMap<>(body);
        details.remove("teamCode");
        Map<String, Object> result = eventTeamService.joinTeam(userId, eventId, teamCode, details);
        return ResponseEntity.ok(ApiResponse.success("Joined team successfully", result));
    }

    /** The caller's team for this event, with member summaries. */
    @GetMapping("/events/{eventId}/teams/my")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyTeam(
            Authentication auth,
            @PathVariable String eventId) {
        String userId = requireUserId(auth);
        Map<String, Object> result = eventTeamService.getMyTeam(userId, eventId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** Leave the caller's team (leader leaving as sole member disbands it). */
    @DeleteMapping("/events/{eventId}/teams/my")
    public ResponseEntity<ApiResponse<Void>> leaveTeam(
            Authentication auth,
            @PathVariable String eventId) {
        String userId = requireUserId(auth);
        eventTeamService.leaveTeam(userId, eventId);
        return ResponseEntity.ok(ApiResponse.success("Left the team successfully"));
    }

    /** Leader or event manager removes a member from the team. */
    @DeleteMapping("/events/{eventId}/teams/{teamId}/members/{memberUserId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            Authentication auth,
            @PathVariable String eventId,
            @PathVariable String teamId,
            @PathVariable String memberUserId) {
        String userId = requireUserId(auth);
        eventTeamService.removeMember(userId, eventId, teamId, memberUserId);
        return ResponseEntity.ok(ApiResponse.success("Member removed from team"));
    }

    /** Bulk check-in of all eligible members of a team (organizers only). */
    @PostMapping("/events/{eventId}/teams/{teamId}/checkin")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkInTeam(
            Authentication auth,
            @PathVariable String eventId,
            @PathVariable String teamId) {
        String userId = requireUserId(auth);
        Map<String, Object> result = eventTeamService.checkInTeam(userId, eventId, teamId);
        return ResponseEntity.ok(ApiResponse.success("Team check-in completed", result));
    }
}
