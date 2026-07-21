package com.cax.cax_backend.bookmark.service;

import com.cax.cax_backend.bookmark.dto.BookmarksResponse;
import com.cax.cax_backend.bookmark.model.Bookmark;
import com.cax.cax_backend.bookmark.model.BookmarkTargetType;
import com.cax.cax_backend.bookmark.repository.BookmarkRepository;
import com.cax.cax_backend.organization.model.OrganizationPost;
import com.cax.cax_backend.organization.repository.OrganizationPostRepository;
import com.cax.cax_backend.thought.model.Thought;
import com.cax.cax_backend.thought.repository.ThoughtRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final ThoughtRepository thoughtRepository;
    private final OrganizationPostRepository organizationPostRepository;

    public Bookmark addBookmark(String userId, BookmarkTargetType targetType, String targetId) {
        return bookmarkRepository
                .findByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId)
                .orElseGet(() -> bookmarkRepository.save(Bookmark.builder()
                        .userId(userId)
                        .targetType(targetType)
                        .targetId(targetId)
                        .build()));
    }

    public void removeBookmark(String userId, BookmarkTargetType targetType, String targetId) {
        bookmarkRepository
                .findByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId)
                .ifPresent(bookmarkRepository::delete);
    }

    public boolean isBookmarked(String userId, BookmarkTargetType targetType, String targetId) {
        return bookmarkRepository.existsByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId);
    }

    /** Hydrated bookmarks for a user, each list ordered most-recently-bookmarked first. */
    public BookmarksResponse listMine(String userId) {
        List<Bookmark> bookmarks = bookmarkRepository.findByUserIdOrderByCreatedAtDesc(userId);

        List<String> thoughtIds = new ArrayList<>();
        List<String> organizationPostIds = new ArrayList<>();
        for (Bookmark bookmark : bookmarks) {
            if (bookmark.getTargetType() == BookmarkTargetType.THOUGHT) {
                thoughtIds.add(bookmark.getTargetId());
            } else {
                organizationPostIds.add(bookmark.getTargetId());
            }
        }

        Map<String, Thought> thoughtsById = new HashMap<>();
        for (Thought thought : thoughtRepository.findAllById(thoughtIds)) {
            thoughtsById.put(thought.getId(), thought);
        }

        Map<String, OrganizationPost> postsById = new HashMap<>();
        for (OrganizationPost post : organizationPostRepository.findAllById(organizationPostIds)) {
            postsById.put(post.getId(), post);
        }

        List<Thought> thoughts = new ArrayList<>();
        for (String id : thoughtIds) {
            Thought thought = thoughtsById.get(id);
            // Skip bookmarks whose target has since been deleted.
            if (thought != null && !thought.isDeleted()) thoughts.add(thought);
        }

        List<OrganizationPost> organizationPosts = new ArrayList<>();
        for (String id : organizationPostIds) {
            OrganizationPost post = postsById.get(id);
            if (post != null && !post.isDeleted()) organizationPosts.add(post);
        }

        return BookmarksResponse.builder()
                .thoughts(thoughts)
                .organizationPosts(organizationPosts)
                .build();
    }
}
