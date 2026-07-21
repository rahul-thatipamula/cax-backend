package com.cax.cax_backend.bookmark.controller;

import com.cax.cax_backend.bookmark.dto.BookmarksResponse;
import com.cax.cax_backend.bookmark.model.Bookmark;
import com.cax.cax_backend.bookmark.model.BookmarkTargetType;
import com.cax.cax_backend.bookmark.service.BookmarkService;
import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.common.exception.AuthException;
import com.cax.cax_backend.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @PostMapping("/{targetType}/{targetId}")
    public ResponseEntity<ApiResponse<Bookmark>> addBookmark(
            Authentication auth,
            @PathVariable String targetType,
            @PathVariable String targetId) {
        requireAuth(auth);
        String userId = (String) auth.getPrincipal();
        Bookmark bookmark = bookmarkService.addBookmark(userId, parseTargetType(targetType), targetId);
        return ResponseEntity.ok(ApiResponse.success("Bookmarked", bookmark));
    }

    @DeleteMapping("/{targetType}/{targetId}")
    public ResponseEntity<ApiResponse<Void>> removeBookmark(
            Authentication auth,
            @PathVariable String targetType,
            @PathVariable String targetId) {
        requireAuth(auth);
        String userId = (String) auth.getPrincipal();
        bookmarkService.removeBookmark(userId, parseTargetType(targetType), targetId);
        return ResponseEntity.ok(ApiResponse.success("Bookmark removed"));
    }

    @GetMapping("/{targetType}/{targetId}/status")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> status(
            Authentication auth,
            @PathVariable String targetType,
            @PathVariable String targetId) {
        requireAuth(auth);
        String userId = (String) auth.getPrincipal();
        boolean bookmarked = bookmarkService.isBookmarked(userId, parseTargetType(targetType), targetId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("bookmarked", bookmarked)));
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<BookmarksResponse>> mine(Authentication auth) {
        requireAuth(auth);
        String userId = (String) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(bookmarkService.listMine(userId)));
    }

    private BookmarkTargetType parseTargetType(String raw) {
        if ("thought".equalsIgnoreCase(raw)) return BookmarkTargetType.THOUGHT;
        if ("organization-post".equalsIgnoreCase(raw)) return BookmarkTargetType.ORGANIZATION_POST;
        throw new BusinessException.BadRequestException("Unknown bookmark target type: " + raw);
    }

    private void requireAuth(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null)
            throw new AuthException.UnauthorizedException("User is not authenticated");
    }
}
