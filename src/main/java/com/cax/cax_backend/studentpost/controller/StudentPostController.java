package com.cax.cax_backend.studentpost.controller;

import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.studentpost.model.StudentPost;
import com.cax.cax_backend.studentpost.service.StudentPostService;
import com.cax.cax_backend.studentpost.service.ThoughtReportService;
import com.cax.cax_backend.studentpost.dto.ReportedPostDetailDto;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.service.UserService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import io.jsonwebtoken.Claims;

import java.util.List;

@RestController
@RequestMapping("/api/student-posts")
@RequiredArgsConstructor
public class StudentPostController {

    private final StudentPostService studentPostService;
    private final UserService userService;
    private final ThoughtReportService thoughtReportService;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateStudentPostRequest {
        private String heading;
        private String content;
        private String sharedLink;
        private List<ThoughtImageRequest> images;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThoughtImageRequest {
        private String url;
        private String alignment;
        private Double widthRatio;
        private Integer insertAfterLine;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddCommentRequest {
        private String text;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportPostRequest {
        private String reason;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StudentPost>> createPost(
            Authentication auth,
            @RequestBody CreateStudentPostRequest request) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        StudentPost post = studentPostService.createPost(
                userId,
                request.getHeading(),
                request.getContent(),
                request.getSharedLink(),
                request.getImages()
        );
        return ResponseEntity.ok(ApiResponse.created("Post created successfully", post));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<StudentPost>>> getMyPosts(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(studentPostService.getMyPosts(userId, page, size)));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<StudentPost>>> getActivePostsByUserId(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(studentPostService.getActivePostsByUserId(userId, page, size)));
    }

    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<List<StudentPost>>> getFeed(
            Authentication auth,
            @RequestParam(required = false) String collegeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null && page == 0) {
            String userId = (String) auth.getPrincipal();
            studentPostService.updateLastSeenFeed(userId);
        }
        return ResponseEntity.ok(ApiResponse.success(studentPostService.getFeed(collegeId, page, size)));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<StudentPost>> getPostById(
            Authentication auth,
            @PathVariable String postId) {
        StudentPost post = studentPostService.getPostById(postId);
        if (post.isDisabled()) {
            boolean isAllowed = false;
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) {
                String userId = (String) auth.getPrincipal();
                Claims claims = (Claims) auth.getCredentials();
                boolean isAdmin = Boolean.TRUE.equals(claims.get("isAdmin", Boolean.class));
                boolean isAuthor = post.getUserId().equals(userId);
                isAllowed = isAdmin || isAuthor;
            }
            if (!isAllowed) {
                throw new com.cax.cax_backend.common.exception.BusinessException.ResourceNotFoundException("StudentPost", postId);
            }
        }
        return ResponseEntity.ok(ApiResponse.success(post));
    }

    @GetMapping("/admin/all")
    public ResponseEntity<ApiResponse<List<StudentPost>>> getAllPostsForAdmin(
            Authentication auth,
            @RequestParam(required = false) String collegeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success(studentPostService.getAllPostsForAdmin(collegeId, page, size)));
    }

    @PutMapping("/admin/{postId}/toggle-disabled")
    public ResponseEntity<ApiResponse<StudentPost>> togglePostDisabled(
            Authentication auth,
            @PathVariable String postId) {
        checkAdmin(auth);
        StudentPost post = studentPostService.togglePostDisabled(postId);
        return ResponseEntity.ok(ApiResponse.success("Post status toggled successfully", post));
    }

    @PostMapping("/{postId}/report")
    public ResponseEntity<ApiResponse<Void>> reportPost(
            Authentication auth,
            @PathVariable String postId,
            @RequestBody ReportPostRequest request) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        thoughtReportService.reportPost(userId, postId, request.getReason());
        return ResponseEntity.ok(ApiResponse.success("Post reported successfully"));
    }

    @GetMapping("/admin/reports")
    public ResponseEntity<ApiResponse<List<ReportedPostDetailDto>>> getReportedPostsForAdmin(
            Authentication auth) {
        checkAdmin(auth);
        return ResponseEntity.ok(ApiResponse.success(thoughtReportService.getReportedPostsForAdmin()));
    }

    @DeleteMapping("/admin/reports/{postId}")
    public ResponseEntity<ApiResponse<Void>> dismissReports(
            Authentication auth,
            @PathVariable String postId) {
        checkAdmin(auth);
        thoughtReportService.dismissReports(postId);
        return ResponseEntity.ok(ApiResponse.success("Reports dismissed successfully"));
    }

    private void checkAdmin(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        Claims claims = (Claims) auth.getCredentials();
        Boolean isAdmin = claims.get("isAdmin", Boolean.class);
        if (!Boolean.TRUE.equals(isAdmin)) {
            throw new com.cax.cax_backend.common.exception.AuthException.AdminOnlyException();
        }
    }

    @PostMapping("/{postId}/like")
    public ResponseEntity<ApiResponse<StudentPost>> likePost(
            Authentication auth,
            @PathVariable String postId) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        StudentPost post = studentPostService.likePost(userId, postId);
        return ResponseEntity.ok(ApiResponse.success("Like updated successfully", post));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            Authentication auth,
            @PathVariable String postId) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        studentPostService.deletePost(userId, postId);
        return ResponseEntity.ok(ApiResponse.success("Post deleted successfully"));
    }

    @PostMapping("/{postId}/comments")
    public ResponseEntity<ApiResponse<StudentPost>> addComment(
            Authentication auth,
            @PathVariable String postId,
            @RequestBody AddCommentRequest request) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        StudentPost post = studentPostService.addComment(userId, postId, request.getText());
        return ResponseEntity.ok(ApiResponse.success("Comment added successfully", post));
    }

    @DeleteMapping("/{postId}/comments/{commentId}")
    public ResponseEntity<ApiResponse<StudentPost>> deleteComment(
            Authentication auth,
            @PathVariable String postId,
            @PathVariable String commentId) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        StudentPost post = studentPostService.deleteComment(userId, postId, commentId);
        return ResponseEntity.ok(ApiResponse.success("Comment deleted successfully", post));
    }
}
