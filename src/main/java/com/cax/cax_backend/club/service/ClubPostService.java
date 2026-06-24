package com.cax.cax_backend.club.service;

import com.cax.cax_backend.club.model.Club;
import com.cax.cax_backend.club.model.ClubMember;
import com.cax.cax_backend.club.model.ClubPost;
import com.cax.cax_backend.club.repository.ClubMemberRepository;
import com.cax.cax_backend.club.repository.ClubPostRepository;
import com.cax.cax_backend.club.repository.ClubRepository;
import com.cax.cax_backend.common.enums.UserRole;
import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.common.service.R2StorageService;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClubPostService {

    private final ClubPostRepository clubPostRepository;
    private final ClubRepository clubRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final UserService userService;
    private final R2StorageService r2StorageService;

    public ClubPost createPost(String userId, String clubId, String caption, List<String> images, boolean isPoll, String pollQuestion, List<String> pollOptions) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Club", clubId));

        if (!canManagePosts(userId, clubId)) {
            throw new BusinessException.BadRequestException("Unauthorized: You do not have permission to manage posts for this club.");
        }

        List<ClubPost.PollOption> options = new ArrayList<>();
        if (isPoll && pollOptions != null) {
            for (String optText : pollOptions) {
                if (optText != null && !optText.trim().isEmpty()) {
                    options.add(ClubPost.PollOption.builder()
                            .optionId(UUID.randomUUID().toString())
                            .text(optText.trim())
                            .votes(new ArrayList<>())
                            .build());
                }
            }
        }

        ClubPost post = ClubPost.builder()
                .clubId(clubId)
                .clubName(club.getName())
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

        return clubPostRepository.save(post);
    }

    public ClubPost votePoll(String userId, String postId, String optionId) {
        ClubPost post = clubPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("ClubPost", postId));

        verifyUserCollegeMatchesClub(userId, post.getClubId());

        if (!post.isPoll()) {
            throw new BusinessException.BadRequestException("This post is not a poll.");
        }

        if (post.getPollOptions() == null) {
            post.setPollOptions(new ArrayList<>());
        }

        // Check if user has already voted for this specific option
        boolean alreadyVotedForThis = false;
        for (ClubPost.PollOption option : post.getPollOptions()) {
            if (option.getVotes() != null && option.getOptionId().equals(optionId) && option.getVotes().contains(userId)) {
                alreadyVotedForThis = true;
                break;
            }
        }

        boolean optionFound = false;
        for (ClubPost.PollOption option : post.getPollOptions()) {
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
        return clubPostRepository.save(post);
    }

    public void deletePost(String userId, String postId) {
        ClubPost post = clubPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("ClubPost", postId));

        if (!canManagePosts(userId, post.getClubId())) {
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

        clubPostRepository.delete(post);
    }

    public ClubPost likePost(String userId, String postId) {
        ClubPost post = clubPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("ClubPost", postId));

        verifyUserCollegeMatchesClub(userId, post.getClubId());

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
        return clubPostRepository.save(post);
    }

    public ClubPost addComment(String userId, String postId, String text) {
        ClubPost post = clubPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("ClubPost", postId));

        verifyUserCollegeMatchesClub(userId, post.getClubId());

        User user = userService.getUserByUserId(userId);

        ClubPost.Comment comment = ClubPost.Comment.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .userName(user.getName())
                .userPicture(user.getPicture())
                .text(text)
                .createdAt(Instant.now())
                .build();

        List<ClubPost.Comment> comments = post.getComments();
        if (comments == null) {
            comments = new ArrayList<>();
        }
        comments.add(comment);
        post.setComments(comments);
        post.setUpdatedAt(Instant.now());
        return clubPostRepository.save(post);
    }

    public ClubPost deleteComment(String userId, String postId, String commentId) {
        ClubPost post = clubPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("ClubPost", postId));

        List<ClubPost.Comment> comments = post.getComments();
        if (comments == null) {
            throw new BusinessException.ResourceNotFoundException("Comment", commentId);
        }

        ClubPost.Comment targetComment = comments.stream()
                .filter(c -> c.getId().equals(commentId))
                .findFirst()
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Comment", commentId));

        if (!targetComment.getUserId().equals(userId) && !canManagePosts(userId, post.getClubId())) {
            throw new BusinessException.BadRequestException("Unauthorized to delete this comment.");
        }

        comments.remove(targetComment);
        post.setComments(comments);
        post.setUpdatedAt(Instant.now());
        return clubPostRepository.save(post);
    }

    public List<ClubPost> getFeed(String collegeId, int page, int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return clubPostRepository.findByCollegeIdOrderByCreatedAtDesc(collegeId, pageable);
    }

    public List<ClubPost> getClubPosts(String clubId) {
        return clubPostRepository.findByClubIdOrderByCreatedAtDesc(clubId);
    }

    private void verifyUserCollegeMatchesClub(String userId, String clubId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Club", clubId));
        User user = userService.getUserByUserId(userId);
        if (user.getRole() == UserRole.ADMIN) return;
        if (user.getCollegeDetails() == null || user.getCollegeDetails().getCollegeId() == null) {
            throw new BusinessException.BadRequestException("User has no college assigned.");
        }
        if (!club.getCollegeId().equals(user.getCollegeDetails().getCollegeId())) {
            throw new BusinessException.BadRequestException("You cannot interact with posts from another college.");
        }
    }

    public boolean canManagePosts(String userId, String clubId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Club", clubId));

        try {
            User user = userService.getUserByUserId(userId);
            if (user.getRole() == UserRole.ADMIN || (user.getRole() == UserRole.SUPER_STUDENT && user.isIdVerified())) {
                return true;
            }
        } catch (Exception e) {}

        if (userId.equals(club.getPresidentId()) || userId.equals(club.getVicePresidentId())) {
            return true;
        }

        Optional<ClubMember> memberOpt = clubMemberRepository.findByClubIdAndUserId(clubId, userId);
        if (memberOpt.isPresent()) {
            ClubMember member = memberOpt.get();
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
