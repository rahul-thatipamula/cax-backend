package com.cax.cax_backend.user.listener;

import com.cax.cax_backend.club.model.ClubJoinRequest;
import com.cax.cax_backend.club.model.ClubMember;
import com.cax.cax_backend.club.model.ClubPost;
import com.cax.cax_backend.club.repository.ClubJoinRequestRepository;
import com.cax.cax_backend.club.repository.ClubMemberRepository;
import com.cax.cax_backend.club.repository.ClubPostRepository;
import com.cax.cax_backend.event.model.EventParticipant;
import com.cax.cax_backend.event.repository.EventParticipantRepository;
import com.cax.cax_backend.studentpost.model.StudentPost;
import com.cax.cax_backend.studentpost.repository.StudentPostRepository;
import com.cax.cax_backend.user.event.UserProfileUpdatedEvent;
import com.cax.cax_backend.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserProfileUpdatedListener {

    private final ClubMemberRepository clubMemberRepository;
    private final EventParticipantRepository eventParticipantRepository;
    private final ClubJoinRequestRepository clubJoinRequestRepository;
    private final StudentPostRepository studentPostRepository;
    private final ClubPostRepository clubPostRepository;

    @Async("taskExecutor")
    @EventListener
    public void handleUserProfileUpdatedEvent(UserProfileUpdatedEvent event) {
        User user = event.getUser();
        String userId = user.getUserId();
        String updatedName = user.getName();
        String updatedPicture = user.getPicture();

        log.info("Received UserProfileUpdatedEvent. Syncing profile for user: {}", userId);

        // 1. Sync ClubMember records
        try {
            List<ClubMember> memberships = clubMemberRepository.findByUserId(userId);
            for (ClubMember member : memberships) {
                boolean changed = false;
                if (updatedName != null && !updatedName.equals(member.getName())) {
                    member.setName(updatedName);
                    changed = true;
                }
                if (updatedPicture != null && !updatedPicture.equals(member.getPicture())) {
                    member.setPicture(updatedPicture);
                    changed = true;
                }
                if (changed) {
                    clubMemberRepository.save(member);
                }
            }
            log.info("Synced {} ClubMember records for user {}", memberships.size(), userId);
        } catch (Exception e) {
            log.error("Failed to sync ClubMember records: ", e);
        }

        // 2. Sync EventParticipant records
        try {
            List<EventParticipant> registrations = eventParticipantRepository.findByUserId(userId);
            for (EventParticipant participant : registrations) {
                boolean changed = false;
                if (updatedName != null && !updatedName.equals(participant.getName())) {
                    participant.setName(updatedName);
                    changed = true;
                }
                if (updatedPicture != null && !updatedPicture.equals(participant.getPicture())) {
                    participant.setPicture(updatedPicture);
                    changed = true;
                }
                if (changed) {
                    eventParticipantRepository.save(participant);
                }
            }
            log.info("Synced {} EventParticipant records for user {}", registrations.size(), userId);
        } catch (Exception e) {
            log.error("Failed to sync EventParticipant records: ", e);
        }

        // 3. Sync ClubJoinRequest records
        try {
            List<ClubJoinRequest> requests = clubJoinRequestRepository.findByUserId(userId);
            for (ClubJoinRequest request : requests) {
                boolean changed = false;
                if (updatedName != null && !updatedName.equals(request.getName())) {
                    request.setName(updatedName);
                    changed = true;
                }
                if (updatedPicture != null && !updatedPicture.equals(request.getPicture())) {
                    request.setPicture(updatedPicture);
                    changed = true;
                }
                if (changed) {
                    clubJoinRequestRepository.save(request);
                }
            }
            log.info("Synced {} ClubJoinRequest records for user {}", requests.size(), userId);
        } catch (Exception e) {
            log.error("Failed to sync ClubJoinRequest records: ", e);
        }

        // 4. Sync StudentPost creator info
        try {
            List<StudentPost> posts = studentPostRepository.findByUserId(userId);
            for (StudentPost post : posts) {
                boolean changed = false;
                if (updatedName != null && !updatedName.equals(post.getCreatorName())) {
                    post.setCreatorName(updatedName);
                    changed = true;
                }
                if (updatedPicture != null && !updatedPicture.equals(post.getCreatorPicture())) {
                    post.setCreatorPicture(updatedPicture);
                    changed = true;
                }
                if (changed) {
                    studentPostRepository.save(post);
                }
            }
            log.info("Synced {} StudentPost creator info records for user {}", posts.size(), userId);
        } catch (Exception e) {
            log.error("Failed to sync StudentPost creator info records: ", e);
        }

        // 5. Sync StudentPost comments info
        try {
            List<StudentPost> postsWithComments = studentPostRepository.findByCommentsUserId(userId);
            for (StudentPost post : postsWithComments) {
                boolean changed = false;
                if (post.getComments() != null) {
                    for (StudentPost.Comment comment : post.getComments()) {
                        if (userId.equals(comment.getUserId())) {
                            if (updatedName != null && !updatedName.equals(comment.getUserName())) {
                                comment.setUserName(updatedName);
                                changed = true;
                            }
                            if (updatedPicture != null && !updatedPicture.equals(comment.getUserPicture())) {
                                comment.setUserPicture(updatedPicture);
                                changed = true;
                            }
                        }
                    }
                }
                if (changed) {
                    studentPostRepository.save(post);
                }
            }
            log.info("Synced {} StudentPost comment records for user {}", postsWithComments.size(), userId);
        } catch (Exception e) {
            log.error("Failed to sync StudentPost comment records: ", e);
        }

        // 6. Sync ClubPost comments info
        try {
            List<ClubPost> postsWithComments = clubPostRepository.findByCommentsUserId(userId);
            for (ClubPost post : postsWithComments) {
                boolean changed = false;
                if (post.getComments() != null) {
                    for (ClubPost.Comment comment : post.getComments()) {
                        if (userId.equals(comment.getUserId())) {
                            if (updatedName != null && !updatedName.equals(comment.getUserName())) {
                                comment.setUserName(updatedName);
                                changed = true;
                            }
                            if (updatedPicture != null && !updatedPicture.equals(comment.getUserPicture())) {
                                comment.setUserPicture(updatedPicture);
                                changed = true;
                            }
                        }
                    }
                }
                if (changed) {
                    clubPostRepository.save(post);
                }
            }
            log.info("Synced {} ClubPost comment records for user {}", postsWithComments.size(), userId);
        } catch (Exception e) {
            log.error("Failed to sync ClubPost comment records: ", e);
        }
    }
}
