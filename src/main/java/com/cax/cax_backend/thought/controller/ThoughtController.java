package com.cax.cax_backend.thought.controller;

import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.common.exception.AuthException;
import com.cax.cax_backend.common.util.EmailDomainUtils;
import com.cax.cax_backend.settings.service.SystemSettingService;
import com.cax.cax_backend.thought.dto.ReportedThoughtDetailDto;
import com.cax.cax_backend.thought.dto.ThoughtImageRequest;
import com.cax.cax_backend.thought.model.Thought;
import com.cax.cax_backend.thought.service.ThoughtEngagementService;
import com.cax.cax_backend.thought.service.ThoughtReportService;
import com.cax.cax_backend.thought.service.ThoughtService;
import io.jsonwebtoken.Claims;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/thoughts")
@RequiredArgsConstructor
public class ThoughtController {

    private final ThoughtService thoughtService;
    private final ThoughtReportService thoughtReportService;
    private final ThoughtEngagementService engagementService;
    private final SystemSettingService systemSettingService;

    // ──────────────────────────────────────────────────────────
    // Request bodies
    // ──────────────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CreateThoughtRequest {
        private String heading;
        private String content;
        private String sharedLink;
        private List<ThoughtImageRequest> images;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class AddCommentRequest {
        private String text;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ReportRequest {
        private String reason;
    }

    // ──────────────────────────────────────────────────────────
    // Public / auth-required endpoints
    // ──────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ApiResponse<Thought>> create(
            Authentication auth,
            @RequestBody CreateThoughtRequest req) {
        requireAuth(auth);
        String userId = (String) auth.getPrincipal();
        Thought thought = thoughtService.create(userId, req.getHeading(), req.getContent(),
                req.getSharedLink(), req.getImages());
        return ResponseEntity.ok(ApiResponse.created("Thought created successfully", thought));
    }

    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<List<Thought>>> getFeed(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        requireAuth(auth);
        if (isCAXoneAuth(auth)) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }
        String userId = (String) auth.getPrincipal();
        if (page == 0) thoughtService.updateLastSeenFeed(userId);
        return ResponseEntity.ok(ApiResponse.success(thoughtService.getFeed(null, page, size)));
    }

    @GetMapping("/trending")
    public ResponseEntity<ApiResponse<List<Thought>>> getTrending(
            Authentication auth,
            @RequestParam(required = false) String collegeId) {
        requireAuth(auth);
        return ResponseEntity.ok(ApiResponse.success(thoughtService.getTrending(collegeId)));
    }

    @PostMapping("/{thoughtId}/view")
    public ResponseEntity<ApiResponse<Void>> recordView(
            Authentication auth,
            @PathVariable String thoughtId) {
        requireAuth(auth);
        engagementService.onView(thoughtId);
        return ResponseEntity.ok(ApiResponse.success("View recorded"));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<Thought>>> getMyThoughts(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        requireAuth(auth);
        String userId = (String) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(thoughtService.getMyThoughts(userId, page, size)));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<Thought>>> getByUser(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(thoughtService.getActiveByUser(userId, page, size)));
    }

    @GetMapping("/{thoughtId}")
    public ResponseEntity<ApiResponse<Thought>> getById(
            Authentication auth,
            @PathVariable String thoughtId) {
        Thought thought = thoughtService.getById(thoughtId);
        if (thought.isDisabled()) {
            boolean allowed = false;
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) {
                String userId = (String) auth.getPrincipal();
                Claims claims = (Claims) auth.getCredentials();
                boolean isAdmin = Boolean.TRUE.equals(claims.get("isAdmin", Boolean.class));
                allowed = isAdmin || thought.getUserId().equals(userId);
            }
            if (!allowed)
                throw new com.cax.cax_backend.common.exception.BusinessException.ResourceNotFoundException("Thought", thoughtId);
        }
        return ResponseEntity.ok(ApiResponse.success(thought));
    }

    @PostMapping("/{thoughtId}/like")
    public ResponseEntity<ApiResponse<Thought>> toggleLike(
            Authentication auth,
            @PathVariable String thoughtId) {
        requireAuth(auth);
        String userId = (String) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Like updated", thoughtService.toggleLike(userId, thoughtId)));
    }

    @PutMapping("/{thoughtId}")
    public ResponseEntity<ApiResponse<Thought>> edit(
            Authentication auth,
            @PathVariable String thoughtId,
            @RequestBody CreateThoughtRequest req) {
        requireAuth(auth);
        String userId = (String) auth.getPrincipal();
        Thought thought = thoughtService.edit(userId, thoughtId, req.getHeading(), req.getContent(),
                req.getImages());
        return ResponseEntity.ok(ApiResponse.success("Thought updated successfully", thought));
    }

    @DeleteMapping("/{thoughtId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            Authentication auth,
            @PathVariable String thoughtId) {
        requireAuth(auth);
        String userId = (String) auth.getPrincipal();
        thoughtService.delete(userId, thoughtId);
        return ResponseEntity.ok(ApiResponse.success("Thought deleted successfully"));
    }

    @PostMapping("/{thoughtId}/comments")
    public ResponseEntity<ApiResponse<Thought>> addComment(
            Authentication auth,
            @PathVariable String thoughtId,
            @RequestBody AddCommentRequest req) {
        requireAuth(auth);
        String userId = (String) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Comment added", thoughtService.addComment(userId, thoughtId, req.getText())));
    }

    @DeleteMapping("/{thoughtId}/comments/{commentId}")
    public ResponseEntity<ApiResponse<Thought>> deleteComment(
            Authentication auth,
            @PathVariable String thoughtId,
            @PathVariable String commentId) {
        requireAuth(auth);
        String userId = (String) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Comment deleted", thoughtService.deleteComment(userId, thoughtId, commentId)));
    }

    @PostMapping("/{thoughtId}/report")
    public ResponseEntity<ApiResponse<Void>> report(
            Authentication auth,
            @PathVariable String thoughtId,
            @RequestBody ReportRequest req) {
        requireAuth(auth);
        String userId = (String) auth.getPrincipal();
        thoughtReportService.reportThought(userId, thoughtId, req.getReason());
        return ResponseEntity.ok(ApiResponse.success("Thought reported successfully"));
    }

    // ──────────────────────────────────────────────────────────
    // Admin endpoints
    // ──────────────────────────────────────────────────────────

    @GetMapping("/admin/all")
    public ResponseEntity<ApiResponse<List<Thought>>> adminGetAll(
            Authentication auth,
            @RequestParam(required = false) String collegeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success(thoughtService.getAllForAdmin(collegeId, page, size)));
    }

    @PutMapping("/admin/{thoughtId}/toggle-disabled")
    public ResponseEntity<ApiResponse<Thought>> adminToggleDisabled(
            Authentication auth,
            @PathVariable String thoughtId) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success("Status toggled", thoughtService.toggleDisabled(thoughtId)));
    }

    @GetMapping("/admin/reports")
    public ResponseEntity<ApiResponse<List<ReportedThoughtDetailDto>>> adminGetReports(Authentication auth) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success(thoughtReportService.getReportedThoughtsForAdmin()));
    }

    @DeleteMapping("/admin/reports/{thoughtId}")
    public ResponseEntity<ApiResponse<Void>> adminDismissReports(
            Authentication auth,
            @PathVariable String thoughtId) {
        checkAdmin(auth);
        thoughtReportService.dismissReports(thoughtId);
        return ResponseEntity.ok(ApiResponse.success("Reports dismissed"));
    }

    @PostMapping("/admin/recompute-scores")
    public ResponseEntity<ApiResponse<String>> adminRecomputeScores(Authentication auth) {
        checkAdmin(auth);
        engagementService.recomputeAllScores();
        return ResponseEntity.ok(ApiResponse.success("Score recomputation triggered"));
    }

    // ──────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────

    private void requireAuth(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null)
            throw new AuthException.UnauthorizedException("User is not authenticated");
    }

    private void checkAdmin(Authentication auth) {
        requireAuth(auth);
        Claims claims = (Claims) auth.getCredentials();
        if (!Boolean.TRUE.equals(claims.get("isAdmin", Boolean.class)))
            throw new AuthException.AdminOnlyException();
    }

    private boolean isCAXoneAuth(Authentication auth) {
        if (!systemSettingService.isPlayStoreTestingEnabled()) return false;
        if (auth == null || auth.getCredentials() == null) return false;
        Claims claims = (Claims) auth.getCredentials();
        String email = claims.get("email", String.class);
        if (email == null) return false;
        int at = email.indexOf('@');
        String domain = at != -1 ? email.substring(at + 1) : "";
        return EmailDomainUtils.isPersonalEmailDomain(domain);
    }
}
