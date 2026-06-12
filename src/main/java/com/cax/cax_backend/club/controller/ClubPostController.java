package com.cax.cax_backend.club.controller;

import com.cax.cax_backend.club.model.ClubPost;
import com.cax.cax_backend.club.service.ClubPostService;
import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.common.service.R2StorageService;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.service.UserService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/clubs")
@RequiredArgsConstructor
public class ClubPostController {

    private final ClubPostService clubPostService;
    private final UserService userService;
    private final R2StorageService r2StorageService;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePostRequest {
        private String caption;
        private List<String> images;
        @com.fasterxml.jackson.annotation.JsonProperty("isPoll")
        private boolean isPoll;
        private String pollQuestion;
        private List<String> pollOptions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddCommentRequest {
        private String text;
    }

    @PostMapping(value = "/{clubId}/posts/upload-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<List<String>>> uploadPostImages(
            Authentication auth,
            @PathVariable String clubId,
            @RequestParam("files") List<MultipartFile> files) throws IOException {

        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();

        if (!clubPostService.canManagePosts(userId, clubId)) {
            throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException(
                     "Unauthorized: You do not have permission to manage posts for this club.");
        }

        if (files == null || files.isEmpty()) {
            throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("No files uploaded");
        }

        // Validate files first
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("One of the files is empty");
            }
            if (file.getSize() > 5 * 1024 * 1024) {
                throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("File size exceeds 5MB limit");
            }
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.contains(".")) {
                throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("Invalid filename");
            }
            String extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
            boolean isValidExtension = extension.equals(".jpg") || extension.equals(".jpeg") || extension.equals(".png");

            String contentType = file.getContentType();
            boolean isValidMimeType = false;
            if (contentType == null || contentType.equalsIgnoreCase("application/octet-stream")) {
                isValidMimeType = true;
            } else {
                String mime = contentType.toLowerCase();
                isValidMimeType = mime.equals("image/jpeg") || mime.equals("image/jpg") || mime.equals("image/png");
            }

            if (!isValidExtension || !isValidMimeType) {
                throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException(
                        "Invalid file type. Only JPG, JPEG, and PNG are allowed");
            }
        }

        // Upload to R2
        List<String> urls = new ArrayList<>();
        String folder = "club-posts/" + clubId;
        for (MultipartFile file : files) {
            String url = r2StorageService.uploadFile(file, folder);
            urls.add(url);
        }

        return ResponseEntity.ok(ApiResponse.success("Images uploaded successfully", urls));
    }

    @GetMapping("/posts/feed")
    public ResponseEntity<ApiResponse<List<ClubPost>>> getFeed(
            Authentication auth,
            @RequestParam(required = false) String collegeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        String finalCollegeId = collegeId;
        if (finalCollegeId == null || finalCollegeId.isBlank()) {
            String userId = (String) auth.getPrincipal();
            User user = userService.getUserByUserId(userId);
            if (user.getCollegeDetails() != null) {
                finalCollegeId = user.getCollegeDetails().getCollegeId();
            }
        }
        
        if (finalCollegeId == null || finalCollegeId.isBlank()) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }
        
        return ResponseEntity.ok(ApiResponse.success(clubPostService.getFeed(finalCollegeId, page, size)));
    }

    @GetMapping("/{clubId}/posts")
    public ResponseEntity<ApiResponse<List<ClubPost>>> getClubPosts(@PathVariable String clubId) {
        return ResponseEntity.ok(ApiResponse.success(clubPostService.getClubPosts(clubId)));
    }

    @PostMapping("/{clubId}/posts")
    public ResponseEntity<ApiResponse<ClubPost>> createPost(
            Authentication auth,
            @PathVariable String clubId,
            @RequestBody CreatePostRequest request) {
        String userId = (String) auth.getPrincipal();
        ClubPost post = clubPostService.createPost(
                userId,
                clubId,
                request.getCaption(),
                request.getImages(),
                request.isPoll(),
                request.getPollQuestion(),
                request.getPollOptions()
        );
        return ResponseEntity.ok(ApiResponse.created("Post created successfully", post));
    }

    @PostMapping("/posts/{postId}/vote")
    public ResponseEntity<ApiResponse<ClubPost>> votePoll(
            Authentication auth,
            @PathVariable String postId,
            @RequestParam String optionId) {
        String userId = (String) auth.getPrincipal();
        ClubPost post = clubPostService.votePoll(userId, postId, optionId);
        return ResponseEntity.ok(ApiResponse.success("Vote registered successfully", post));
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<ApiResponse<Void>> deletePost(Authentication auth, @PathVariable String postId) {
        String userId = (String) auth.getPrincipal();
        clubPostService.deletePost(userId, postId);
        return ResponseEntity.ok(ApiResponse.success("Post deleted successfully"));
    }

    @PostMapping("/posts/{postId}/like")
    public ResponseEntity<ApiResponse<ClubPost>> likePost(Authentication auth, @PathVariable String postId) {
        String userId = (String) auth.getPrincipal();
        ClubPost post = clubPostService.likePost(userId, postId);
        return ResponseEntity.ok(ApiResponse.success("Like updated successfully", post));
    }

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<ClubPost>> addComment(
            Authentication auth,
            @PathVariable String postId,
            @RequestBody AddCommentRequest request) {
        String userId = (String) auth.getPrincipal();
        ClubPost post = clubPostService.addComment(userId, postId, request.getText());
        return ResponseEntity.ok(ApiResponse.success("Comment added successfully", post));
    }

    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<ApiResponse<ClubPost>> deleteComment(
            Authentication auth,
            @PathVariable String postId,
            @PathVariable String commentId) {
        String userId = (String) auth.getPrincipal();
        ClubPost post = clubPostService.deleteComment(userId, postId, commentId);
        return ResponseEntity.ok(ApiResponse.success("Comment deleted successfully", post));
    }
}
