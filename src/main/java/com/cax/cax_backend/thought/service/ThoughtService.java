package com.cax.cax_backend.thought.service;

import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.common.util.ProfanityFilter;
import com.cax.cax_backend.thought.dto.ThoughtImageRequest;
import com.cax.cax_backend.thought.event.ThoughtCommentedEvent;
import com.cax.cax_backend.thought.event.ThoughtCreatedEvent;
import com.cax.cax_backend.thought.event.ThoughtDisabledEvent;
import com.cax.cax_backend.thought.event.ThoughtLikedEvent;
import com.cax.cax_backend.thought.model.Thought;
import com.cax.cax_backend.thought.model.ThoughtEngagementScore;
import com.cax.cax_backend.thought.repository.ThoughtRepository;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.cax.cax_backend.boost.service.ThoughtBoostService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThoughtService {

    private final ThoughtRepository thoughtRepository;
    private final ThoughtEngagementService engagementService;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;
    private final ThoughtBoostService thoughtBoostService;

    public Thought getById(String thoughtId) {
        Thought thought = thoughtRepository.findById(thoughtId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Thought", thoughtId));
        if (thought.isDeleted())
            throw new BusinessException.ResourceNotFoundException("Thought", thoughtId);
        return thought;
    }

    public Thought create(String userId, String heading, String content, String sharedLink,
                          List<ThoughtImageRequest> images) {
        if (heading == null || heading.trim().isEmpty())
            throw new BusinessException.BadRequestException("Heading must not be empty");
        if (heading.trim().length() > 100)
            throw new BusinessException.BadRequestException("Heading must not exceed 100 characters");
        if (content == null || content.trim().isEmpty())
            throw new BusinessException.BadRequestException("Content must not be empty");
        if (sharedLink != null && sharedLink.trim().length() > 255)
            throw new BusinessException.BadRequestException("Shared link must not exceed 255 characters");
        if (ProfanityFilter.isOffensive(heading) || ProfanityFilter.isOffensive(content))
            throw new BusinessException.BadRequestException("Your post contains language that isn't allowed. Please revise it.");

        List<Thought.ThoughtImage> thoughtImages = new ArrayList<>();
        if (images != null) {
            if (images.size() > 5)
                throw new BusinessException.BadRequestException("A thought can have at most 5 images");
            List<String> validAlignments = List.of("left", "center", "right");
            for (ThoughtImageRequest img : images) {
                if (img.getUrl() == null || img.getUrl().trim().isEmpty())
                    throw new BusinessException.BadRequestException("Each image must have a non-empty URL");
                String alignment = img.getAlignment() != null ? img.getAlignment() : "center";
                if (!validAlignments.contains(alignment))
                    throw new BusinessException.BadRequestException("Image alignment must be one of: left, center, right");
                double widthRatio = img.getWidthRatio() != null ? img.getWidthRatio() : 1.0;
                if (widthRatio < 0.1 || widthRatio > 1.0)
                    throw new BusinessException.BadRequestException("Image widthRatio must be between 0.1 and 1.0");
                int insertAfterLine = img.getInsertAfterLine() != null ? img.getInsertAfterLine() : 0;
                thoughtImages.add(Thought.ThoughtImage.builder()
                        .url(img.getUrl().trim())
                        .alignment(alignment)
                        .widthRatio(widthRatio)
                        .insertAfterLine(insertAfterLine)
                        .build());
            }
        }

        User user = userService.getUserByUserId(userId);
        if (user.getCollegeDetails() == null || user.getCollegeDetails().getCollegeId() == null
                || user.getCollegeDetails().getCollegeId().isEmpty())
            throw new BusinessException.BadRequestException("User has not configured a college. Cannot post.");
        if ("CAXONE".equalsIgnoreCase(user.getCollegeDetails().getCollegeCode()))
            throw new BusinessException.BadRequestException("Posting thoughts is not available for your account.");

        Thought thought = Thought.builder()
                .userId(userId)
                .creatorName(user.getThoughtsDisplayName())
                .creatorPicture(user.getPicture())
                .creatorVerified(user.isIdVerified())
                .collegeId(user.getCollegeDetails().getCollegeId())
                .collegeName(user.getCollegeDetails().getCollegeName())
                .heading(heading.trim())
                .content(content.trim())
                .sharedLink(sharedLink != null ? sharedLink.trim() : null)
                .images(thoughtImages)
                .likes(new ArrayList<>())
                .comments(new ArrayList<>())
                .createdAt(Instant.now())
                .build();

        user.setLastSeenFeedAt(Instant.now());
        userService.saveUser(user);

        Thought saved = thoughtRepository.save(thought);
        eventPublisher.publishEvent(new ThoughtCreatedEvent(this, saved));
        return saved;
    }

    public List<Thought> getFeed(String collegeId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (collegeId != null && !collegeId.trim().isEmpty()) {
            return thoughtRepository.findActiveByCollegeId(collegeId.trim(), pageable);
        }
        return thoughtRepository.findActiveAll(pageable);
    }

    public List<Thought> getAllForAdmin(String collegeId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (collegeId != null && !collegeId.trim().isEmpty()) {
            return thoughtRepository.findByCollegeIdOrderByCreatedAtDesc(collegeId.trim(), pageable);
        }
        return thoughtRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public List<Thought> getMyThoughts(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return thoughtRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public List<Thought> getActiveByUser(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return thoughtRepository.findActiveByUserId(userId, pageable);
    }

    /**
     * Returns up to 8 trending thoughts: top 5 by engagement score + up to 3 currently
     * active boosted thoughts, shuffled together so boosted posts aren't predictably placed.
     */
    public List<Thought> getTrending(String collegeId) {
        // 1. Organic top-5
        List<ThoughtEngagementScore> scores = collegeId != null && !collegeId.isBlank()
                ? engagementService.getTopTrendingByCollege(collegeId)
                : engagementService.getTopTrending(5);

        List<String> organicIds = scores.stream()
                .map(ThoughtEngagementScore::getThoughtId)
                .collect(Collectors.toList());

        List<Thought> fetched = thoughtRepository.findAllById(organicIds);
        List<Thought> organic = organicIds.stream()
                .map(id -> fetched.stream().filter(t -> t.getId().equals(id)).findFirst().orElse(null))
                .filter(t -> t != null && !t.isDisabled() && !t.isDeleted())
                .limit(5)
                .collect(Collectors.toList());

        // 2. Active boosted (up to 3), excluding any already in organic list
        Set<String> organicIdSet = organic.stream().map(Thought::getId).collect(Collectors.toSet());
        List<Thought> boosted = thoughtBoostService.getActiveBoostedThoughts().stream()
                .filter(t -> !organicIdSet.contains(t.getId()))
                .limit(3)
                .collect(Collectors.toList());

        // 3. Merge and shuffle so boosted posts aren't always at a fixed position
        List<Thought> combined = new ArrayList<>();
        combined.addAll(organic);
        combined.addAll(boosted);
        Collections.shuffle(combined);
        return combined;
    }

    public Thought toggleDisabled(String thoughtId) {
        Thought thought = getById(thoughtId);
        thought.setDisabled(!thought.isDisabled());
        thought.setUpdatedAt(Instant.now());
        Thought saved = thoughtRepository.save(thought);
        if (saved.isDisabled()) {
            eventPublisher.publishEvent(new ThoughtDisabledEvent(this, saved));
        }
        return saved;
    }

    public Thought toggleLike(String userId, String thoughtId) {
        Thought thought = getById(thoughtId);
        if (thought.isDisabled())
            throw new BusinessException.ResourceNotFoundException("Thought", thoughtId);

        List<String> likes = thought.getLikes() != null ? thought.getLikes() : new ArrayList<>();
        boolean isLiked;
        if (likes.contains(userId)) {
            likes.remove(userId);
            isLiked = false;
        } else {
            likes.add(userId);
            isLiked = true;
        }
        thought.setLikes(likes);
        thought.setUpdatedAt(Instant.now());
        Thought saved = thoughtRepository.save(thought);
        if (isLiked) {
            eventPublisher.publishEvent(new ThoughtLikedEvent(this, saved, userId));
        } else {
            // Still update score on unlike
            engagementService.onLikeChanged(saved);
        }
        return saved;
    }

    public void delete(String userId, String thoughtId) {
        Thought thought = getById(thoughtId);
        if (!thought.getUserId().equals(userId))
            throw new BusinessException.BadRequestException("Unauthorized: You do not have permission to delete this thought.");
        thought.setDeleted(true);
        thought.setDeletedAt(Instant.now());
        thoughtRepository.save(thought);
        engagementService.onThoughtDeleted(thoughtId);
    }

    public Thought addComment(String userId, String thoughtId, String text) {
        if (text == null || text.trim().isEmpty())
            throw new BusinessException.BadRequestException("Comment text must not be empty");

        Thought thought = getById(thoughtId);
        if (thought.isDisabled())
            throw new BusinessException.ResourceNotFoundException("Thought", thoughtId);

        User user = userService.getUserByUserId(userId);
        Thought.Comment comment = Thought.Comment.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .userName(user.getThoughtsDisplayName())
                .userPicture(user.getPicture())
                .text(text.trim())
                .createdAt(Instant.now())
                .build();

        List<Thought.Comment> comments = thought.getComments() != null ? thought.getComments() : new ArrayList<>();
        comments.add(comment);
        thought.setComments(comments);
        thought.setUpdatedAt(Instant.now());
        Thought saved = thoughtRepository.save(thought);
        eventPublisher.publishEvent(new ThoughtCommentedEvent(this, saved, comment));
        return saved;
    }

    public Thought deleteComment(String userId, String thoughtId, String commentId) {
        Thought thought = getById(thoughtId);
        if (thought.isDisabled())
            throw new BusinessException.ResourceNotFoundException("Thought", thoughtId);

        List<Thought.Comment> comments = thought.getComments();
        if (comments == null)
            throw new BusinessException.ResourceNotFoundException("Comment", commentId);

        Thought.Comment target = comments.stream()
                .filter(c -> c.getId().equals(commentId))
                .findFirst()
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Comment", commentId));

        if (!target.getUserId().equals(userId) && !thought.getUserId().equals(userId))
            throw new BusinessException.BadRequestException("Unauthorized to delete this comment.");

        comments.remove(target);
        thought.setComments(comments);
        thought.setUpdatedAt(Instant.now());
        Thought saved = thoughtRepository.save(thought);
        engagementService.onCommentChanged(saved);
        return saved;
    }

    @Async("taskExecutor")
    public void updateLastSeenFeed(String userId) {
        try {
            userService.getUserOptByUserId(userId).ifPresent(user -> {
                user.setLastSeenFeedAt(Instant.now());
                userService.saveUser(user);
            });
        } catch (Exception e) {
            log.error("Failed to update last seen feed for user: {}, error: {}", userId, e.getMessage());
        }
    }

    public Thought edit(String userId, String thoughtId, String heading, String content,
                        List<ThoughtImageRequest> images) {
        Thought thought = getById(thoughtId);
        if (thought.isDisabled())
            throw new BusinessException.ResourceNotFoundException("Thought", thoughtId);
        if (!thought.getUserId().equals(userId))
            throw new BusinessException.BadRequestException("Unauthorized: You do not have permission to edit this thought.");

        if (heading == null || heading.trim().isEmpty())
            throw new BusinessException.BadRequestException("Heading must not be empty");
        if (heading.trim().length() > 100)
            throw new BusinessException.BadRequestException("Heading must not exceed 100 characters");
        if (content == null || content.trim().isEmpty())
            throw new BusinessException.BadRequestException("Content must not be empty");
        if (ProfanityFilter.isOffensive(heading) || ProfanityFilter.isOffensive(content))
            throw new BusinessException.BadRequestException("Your post contains language that isn't allowed. Please revise it.");

        List<Thought.ThoughtImage> thoughtImages = new ArrayList<>();
        if (images != null) {
            if (images.size() > 5)
                throw new BusinessException.BadRequestException("A thought can have at most 5 images");
            List<String> validAlignments = List.of("left", "center", "right");
            for (ThoughtImageRequest img : images) {
                if (img.getUrl() == null || img.getUrl().trim().isEmpty())
                    throw new BusinessException.BadRequestException("Each image must have a non-empty URL");
                String alignment = img.getAlignment() != null ? img.getAlignment() : "center";
                if (!validAlignments.contains(alignment))
                    throw new BusinessException.BadRequestException("Image alignment must be one of: left, center, right");
                double widthRatio = img.getWidthRatio() != null ? img.getWidthRatio() : 1.0;
                if (widthRatio < 0.1 || widthRatio > 1.0)
                    throw new BusinessException.BadRequestException("Image widthRatio must be between 0.1 and 1.0");
                int insertAfterLine = img.getInsertAfterLine() != null ? img.getInsertAfterLine() : 0;
                thoughtImages.add(Thought.ThoughtImage.builder()
                        .url(img.getUrl().trim())
                        .alignment(alignment)
                        .widthRatio(widthRatio)
                        .insertAfterLine(insertAfterLine)
                        .build());
            }
        }

        if (thought.getHistory() == null) {
            thought.setHistory(new ArrayList<>());
        }

        thought.getHistory().add(Thought.ThoughtHistory.builder()
                .heading(thought.getHeading())
                .content(thought.getContent())
                .images(thought.getImages() != null ? new ArrayList<>(thought.getImages()) : new ArrayList<>())
                .editedAt(thought.getUpdatedAt() != null ? thought.getUpdatedAt() : thought.getCreatedAt())
                .build());

        thought.setHeading(heading.trim());
        thought.setContent(content.trim());
        thought.setImages(thoughtImages);
        thought.setUpdatedAt(Instant.now());

        return thoughtRepository.save(thought);
    }
}
