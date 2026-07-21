package com.cax.cax_backend.organization.service;

import com.cax.cax_backend.organization.model.Organization;
import com.cax.cax_backend.organization.model.OrganizationMember;
import com.cax.cax_backend.organization.model.OrganizationPost;
import com.cax.cax_backend.organization.repository.OrganizationMemberRepository;
import com.cax.cax_backend.organization.repository.OrganizationPostRepository;
import com.cax.cax_backend.organization.repository.OrganizationRepository;
import com.cax.cax_backend.common.enums.UserRole;
import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.common.service.R2StorageService;
import com.cax.cax_backend.organization.event.OrganizationPostCreatedEvent;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationPostService {

    private final OrganizationPostRepository organizationPostRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final UserService userService;
    private final R2StorageService r2StorageService;
    private final ApplicationEventPublisher eventPublisher;

    public OrganizationPost createPost(String userId, String organizationId, String caption, List<String> images, boolean isPoll, String pollQuestion, List<String> pollOptions) {
        Organization club = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Organization", organizationId));

        if (!canManagePosts(userId, organizationId)) {
            throw new BusinessException.BadRequestException("Unauthorized: You do not have permission to manage posts for this club.");
        }

        List<OrganizationPost.PollOption> options = new ArrayList<>();
        if (isPoll && pollOptions != null) {
            for (String optText : pollOptions) {
                if (optText != null && !optText.trim().isEmpty()) {
                    options.add(OrganizationPost.PollOption.builder()
                            .optionId(UUID.randomUUID().toString())
                            .text(optText.trim())
                            .votes(new ArrayList<>())
                            .build());
                }
            }
        }

        OrganizationPost post = OrganizationPost.builder()
                .organizationId(organizationId)
                .organizationName(club.getName())
                .clubLogo(club.getLogo())
                .collegeId(club.getCollegeId())
                .creatorId(userId)
                .caption(caption)
                .images(images != null ? images : new ArrayList<>())
                .likes(new ArrayList<>())
                .comments(new ArrayList<>())
                .isPoll(isPoll)
                .pollQuestion(pollQuestion)
                .pollOptions(options)
                .createdAt(Instant.now())
                .build();

        OrganizationPost saved = organizationPostRepository.save(post);
        eventPublisher.publishEvent(new OrganizationPostCreatedEvent(this, saved));
        return saved;
    }

    public OrganizationPost getPostById(String userId, String postId) {
        OrganizationPost post = organizationPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("OrganizationPost", postId));

        if (post.isDeleted()) {
            throw new BusinessException.ResourceNotFoundException("OrganizationPost", postId);
        }

        verifyUserCollegeMatchesOrganization(userId, post.getOrganizationId());

        return post;
    }

    public OrganizationPost votePoll(String userId, String postId, String optionId) {
        OrganizationPost post = organizationPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("OrganizationPost", postId));

        if (post.isDeleted()) {
            throw new BusinessException.ResourceNotFoundException("OrganizationPost", postId);
        }

        verifyUserCollegeMatchesOrganization(userId, post.getOrganizationId());

        if (!post.isPoll()) {
            throw new BusinessException.BadRequestException("This post is not a poll.");
        }

        if (post.getPollOptions() == null) {
            post.setPollOptions(new ArrayList<>());
        }

        // Check if user has already voted for this specific option
        boolean alreadyVotedForThis = false;
        for (OrganizationPost.PollOption option : post.getPollOptions()) {
            if (option.getVotes() != null && option.getOptionId().equals(optionId) && option.getVotes().contains(userId)) {
                alreadyVotedForThis = true;
                break;
            }
        }

        boolean optionFound = false;
        for (OrganizationPost.PollOption option : post.getPollOptions()) {
            if (option.getVotes() == null) {
                option.setVotes(new ArrayList<>());
            }
            option.getVotes().remove(userId);
            
            if (option.getOptionId().equals(optionId)) {
                optionFound = true;
                if (!alreadyVotedForThis) {
                    option.getVotes().add(userId);
                }
            }
        }

        if (!optionFound) {
            throw new BusinessException.ResourceNotFoundException("PollOption", optionId);
        }

        post.setUpdatedAt(Instant.now());
        return organizationPostRepository.save(post);
    }

    public void deletePost(String userId, String postId) {
        OrganizationPost post = organizationPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("OrganizationPost", postId));

        if (post.isDeleted()) {
            throw new BusinessException.ResourceNotFoundException("OrganizationPost", postId);
        }

        if (!canManagePosts(userId, post.getOrganizationId())) {
            throw new BusinessException.BadRequestException("Unauthorized: You do not have permission to manage posts for this club.");
        }

        // Clean up images from R2 storage
        if (post.getImages() != null && !post.getImages().isEmpty()) {
            for (String imageUrl : post.getImages()) {
                try {
                    r2StorageService.deleteFile(imageUrl);
                } catch (Exception e) {
                    log.warn("Failed to delete image from R2 during post deletion: {}", imageUrl, e);
                }
            }
        }

        post.setDeleted(true);
        post.setDeletedAt(Instant.now());
        organizationPostRepository.save(post);
    }

    public OrganizationPost likePost(String userId, String postId) {
        OrganizationPost post = organizationPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("OrganizationPost", postId));

        if (post.isDeleted()) {
            throw new BusinessException.ResourceNotFoundException("OrganizationPost", postId);
        }

        verifyUserCollegeMatchesOrganization(userId, post.getOrganizationId());

        List<String> likes = post.getLikes();
        if (likes == null) {
            likes = new ArrayList<>();
        }

        if (likes.contains(userId)) {
            likes.remove(userId);
        } else {
            likes.add(userId);
        }
        post.setLikes(likes);
        post.setUpdatedAt(Instant.now());
        return organizationPostRepository.save(post);
    }

    public OrganizationPost addComment(String userId, String postId, String text) {
        OrganizationPost post = organizationPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("OrganizationPost", postId));

        if (post.isDeleted()) {
            throw new BusinessException.ResourceNotFoundException("OrganizationPost", postId);
        }

        verifyUserCollegeMatchesOrganization(userId, post.getOrganizationId());

        User user = userService.getUserByUserId(userId);

        OrganizationPost.Comment comment = OrganizationPost.Comment.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .userName(user.getName())
                .userPicture(user.getPicture())
                .text(text)
                .createdAt(Instant.now())
                .build();

        List<OrganizationPost.Comment> comments = post.getComments();
        if (comments == null) {
            comments = new ArrayList<>();
        }
        comments.add(comment);
        post.setComments(comments);
        post.setUpdatedAt(Instant.now());
        return organizationPostRepository.save(post);
    }

    public OrganizationPost deleteComment(String userId, String postId, String commentId) {
        OrganizationPost post = organizationPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("OrganizationPost", postId));

        List<OrganizationPost.Comment> comments = post.getComments();
        if (comments == null) {
            throw new BusinessException.ResourceNotFoundException("Comment", commentId);
        }

        OrganizationPost.Comment targetComment = comments.stream()
                .filter(c -> c.getId().equals(commentId))
                .findFirst()
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Comment", commentId));

        if (!targetComment.getUserId().equals(userId) && !canManagePosts(userId, post.getOrganizationId())) {
            throw new BusinessException.BadRequestException("Unauthorized to delete this comment.");
        }

        comments.remove(targetComment);
        post.setComments(comments);
        post.setUpdatedAt(Instant.now());
        return organizationPostRepository.save(post);
    }

    public List<OrganizationPost> getFeed(String collegeId, int page, int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return organizationPostRepository.findByCollegeIdOrderByCreatedAtDesc(collegeId, pageable);
    }

    public List<OrganizationPost> getOrganizationPosts(String organizationId) {
        return organizationPostRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId);
    }

    private void verifyUserCollegeMatchesOrganization(String userId, String organizationId) {
        Organization club = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Organization", organizationId));
        User user = userService.getUserByUserId(userId);
        if (user.getRole() == UserRole.ADMIN) return;
        if (user.getCollegeDetails() == null || user.getCollegeDetails().getCollegeId() == null) {
            throw new BusinessException.BadRequestException("User has no college assigned.");
        }
        if (!club.getCollegeId().equals(user.getCollegeDetails().getCollegeId())) {
            throw new BusinessException.BadRequestException("You cannot interact with posts from another college.");
        }
    }

    public boolean canManagePosts(String userId, String organizationId) {
        Organization club = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Organization", organizationId));

        try {
            User user = userService.getUserByUserId(userId);
            if (user.getRole() == UserRole.ADMIN || (user.getRole() == UserRole.SUPER_STUDENT && user.isIdVerified())) {
                return true;
            }
        } catch (Exception e) {}

        if (userId.equals(club.getPresidentId()) || userId.equals(club.getVicePresidentId())) {
            return true;
        }

        Optional<OrganizationMember> memberOpt = organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, userId);
        if (memberOpt.isPresent()) {
            OrganizationMember member = memberOpt.get();
            String role = member.getRole();
            if (role != null) {
                String normRole = role.trim().toLowerCase();
                if (normRole.equals("president") || normRole.equals("vice president")) {
                    return true;
                }
            }
            return member.getAccessControls() != null && member.getAccessControls().contains("manage_posts");
        }

        return false;
    }
}
