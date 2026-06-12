package com.cax.cax_backend.studentpost.service;

import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.studentpost.controller.StudentPostController;
import com.cax.cax_backend.studentpost.model.StudentPost;
import com.cax.cax_backend.studentpost.event.StudentPostCommentedEvent;
import com.cax.cax_backend.studentpost.event.StudentPostLikedEvent;
import com.cax.cax_backend.studentpost.event.StudentPostDisabledEvent;
import com.cax.cax_backend.studentpost.repository.StudentPostRepository;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentPostService {

    private final StudentPostRepository studentPostRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    public StudentPost getPostById(String postId) {
        return studentPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("StudentPost", postId));
    }

    public StudentPost createPost(String userId, String heading, String content, String sharedLink,
                                   List<StudentPostController.ThoughtImageRequest> images) {
        if (heading == null || heading.trim().isEmpty()) {
            throw new BusinessException.BadRequestException("Heading must not be empty");
        }
        if (heading.trim().length() > 100) {
            throw new BusinessException.BadRequestException("Heading must not exceed 100 characters");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException.BadRequestException("Content must not be empty");
        }
        if (sharedLink != null && sharedLink.trim().length() > 255) {
            throw new BusinessException.BadRequestException("Shared link must not exceed 255 characters");
        }

        // Validate images
        List<StudentPost.ThoughtImage> thoughtImages = new ArrayList<>();
        if (images != null) {
            if (images.size() > 5) {
                throw new BusinessException.BadRequestException("A post can have at most 5 images");
            }
            List<String> validAlignments = List.of("left", "center", "right");
            for (StudentPostController.ThoughtImageRequest img : images) {
                if (img.getUrl() == null || img.getUrl().trim().isEmpty()) {
                    throw new BusinessException.BadRequestException("Each image must have a non-empty URL");
                }
                String alignment = img.getAlignment() != null ? img.getAlignment() : "center";
                if (!validAlignments.contains(alignment)) {
                    throw new BusinessException.BadRequestException("Image alignment must be one of: left, center, right");
                }
                double widthRatio = img.getWidthRatio() != null ? img.getWidthRatio() : 1.0;
                if (widthRatio < 0.1 || widthRatio > 1.0) {
                    throw new BusinessException.BadRequestException("Image widthRatio must be between 0.1 and 1.0");
                }
                int insertAfterLine = img.getInsertAfterLine() != null ? img.getInsertAfterLine() : 0;

                thoughtImages.add(StudentPost.ThoughtImage.builder()
                        .url(img.getUrl().trim())
                        .alignment(alignment)
                        .widthRatio(widthRatio)
                        .insertAfterLine(insertAfterLine)
                        .build());
            }
        }

        User user = userService.getUserByUserId(userId);
        if (user.getCollegeDetails() == null || user.getCollegeDetails().getCollegeId() == null || user.getCollegeDetails().getCollegeId().isEmpty()) {
            throw new BusinessException.BadRequestException("User has not configured a college. Cannot post.");
        }

        StudentPost post = StudentPost.builder()
                .userId(userId)
                .creatorName(user.getName())
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

        return studentPostRepository.save(post);
    }

    public List<StudentPost> getFeed(String collegeId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (collegeId != null && !collegeId.trim().isEmpty()) {
            return studentPostRepository.findActiveByCollegeId(collegeId.trim(), pageable);
        }
        return studentPostRepository.findActiveAll(pageable);
    }

    public List<StudentPost> getAllPostsForAdmin(String collegeId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (collegeId != null && !collegeId.trim().isEmpty()) {
            return studentPostRepository.findByCollegeIdOrderByCreatedAtDesc(collegeId.trim(), pageable);
        }
        return studentPostRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public List<StudentPost> getMyPosts(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return studentPostRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public StudentPost togglePostDisabled(String postId) {
        StudentPost post = studentPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("StudentPost", postId));
        post.setDisabled(!post.isDisabled());
        post.setUpdatedAt(Instant.now());
        StudentPost saved = studentPostRepository.save(post);
        if (saved.isDisabled()) {
            eventPublisher.publishEvent(new StudentPostDisabledEvent(this, saved));
        }
        return saved;
    }

    public StudentPost likePost(String userId, String postId) {
        StudentPost post = studentPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("StudentPost", postId));

        if (post.isDisabled()) {
            throw new BusinessException.ResourceNotFoundException("StudentPost", postId);
        }

        List<String> likes = post.getLikes();
        if (likes == null) {
            likes = new ArrayList<>();
        }

        boolean isLiked = false;
        if (likes.contains(userId)) {
            likes.remove(userId);
        } else {
            likes.add(userId);
            isLiked = true;
        }
        post.setLikes(likes);
        post.setUpdatedAt(Instant.now());
        StudentPost saved = studentPostRepository.save(post);
        if (isLiked) {
            eventPublisher.publishEvent(new StudentPostLikedEvent(this, saved, userId));
        }
        return saved;
    }

    public void deletePost(String userId, String postId) {
        StudentPost post = studentPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("StudentPost", postId));

        if (!post.getUserId().equals(userId)) {
            throw new BusinessException.BadRequestException("Unauthorized: You do not have permission to delete this post.");
        }

        studentPostRepository.delete(post);
    }

    public StudentPost addComment(String userId, String postId, String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new BusinessException.BadRequestException("Comment text must not be empty");
        }

        StudentPost post = studentPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("StudentPost", postId));

        if (post.isDisabled()) {
            throw new BusinessException.ResourceNotFoundException("StudentPost", postId);
        }

        User user = userService.getUserByUserId(userId);

        StudentPost.Comment comment = StudentPost.Comment.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .userName(user.getName())
                .userPicture(user.getPicture())
                .text(text.trim())
                .createdAt(Instant.now())
                .build();

        List<StudentPost.Comment> comments = post.getComments();
        if (comments == null) {
            comments = new ArrayList<>();
        }
        comments.add(comment);
        post.setComments(comments);
        post.setUpdatedAt(Instant.now());
        StudentPost saved = studentPostRepository.save(post);
        eventPublisher.publishEvent(new StudentPostCommentedEvent(this, saved, comment));
        return saved;
    }

    public StudentPost deleteComment(String userId, String postId, String commentId) {
        StudentPost post = studentPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("StudentPost", postId));

        if (post.isDisabled()) {
            throw new BusinessException.ResourceNotFoundException("StudentPost", postId);
        }

        List<StudentPost.Comment> comments = post.getComments();
        if (comments == null) {
            throw new BusinessException.ResourceNotFoundException("Comment", commentId);
        }

        StudentPost.Comment targetComment = comments.stream()
                .filter(c -> c.getId().equals(commentId))
                .findFirst()
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Comment", commentId));

        if (!targetComment.getUserId().equals(userId) && !post.getUserId().equals(userId)) {
            throw new BusinessException.BadRequestException("Unauthorized to delete this comment.");
        }

        comments.remove(targetComment);
        post.setComments(comments);
        post.setUpdatedAt(Instant.now());
        return studentPostRepository.save(post);
    }

    public List<StudentPost> getActivePostsByUserId(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return studentPostRepository.findActiveByUserId(userId, pageable);
    }
}
