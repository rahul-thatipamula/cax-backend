package com.cax.cax_backend.bookmark.repository;

import com.cax.cax_backend.bookmark.model.Bookmark;
import com.cax.cax_backend.bookmark.model.BookmarkTargetType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookmarkRepository extends MongoRepository<Bookmark, String> {
    Optional<Bookmark> findByUserIdAndTargetTypeAndTargetId(
            String userId, BookmarkTargetType targetType, String targetId);

    boolean existsByUserIdAndTargetTypeAndTargetId(
            String userId, BookmarkTargetType targetType, String targetId);

    List<Bookmark> findByUserIdOrderByCreatedAtDesc(String userId);
}
