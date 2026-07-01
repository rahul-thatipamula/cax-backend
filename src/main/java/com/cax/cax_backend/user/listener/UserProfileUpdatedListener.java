package com.cax.cax_backend.user.listener;

import com.cax.cax_backend.organization.model.OrganizationJoinRequest;
import com.cax.cax_backend.organization.model.OrganizationMember;
import com.cax.cax_backend.organization.model.OrganizationPost;
import com.cax.cax_backend.organization.repository.OrganizationMemberRepository;
import com.cax.cax_backend.organization.repository.OrganizationJoinRequestRepository;
import com.cax.cax_backend.organization.repository.OrganizationPostRepository;
import com.cax.cax_backend.event.model.EventParticipant;
import com.cax.cax_backend.event.repository.EventParticipantRepository;
import com.cax.cax_backend.thought.model.Thought;
import com.cax.cax_backend.thought.repository.ThoughtRepository;
import com.cax.cax_backend.notification.repository.NotificationRepository;
import com.cax.cax_backend.user.event.UserProfileUpdatedEvent;
import com.cax.cax_backend.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserProfileUpdatedListener {

    private final OrganizationMemberRepository organizationMemberRepository;
    private final EventParticipantRepository eventParticipantRepository;
    private final OrganizationJoinRequestRepository organizationJoinRequestRepository;
    private final ThoughtRepository thoughtRepository;
    private final OrganizationPostRepository organizationPostRepository;
    private final NotificationRepository notificationRepository;

    @Async("taskExecutor")
    @EventListener
    public void handleUserProfileUpdatedEvent(UserProfileUpdatedEvent event) {
        User user = event.getUser();
        String userId = user.getUserId();
        String updatedName = user.getName();
        String updatedNickname = user.getThoughtsDisplayName();
        String updatedPicture = user.getPicture();

        log.info("Received UserProfileUpdatedEvent. Syncing profile for user: {}", userId);

        // 1. Sync OrganizationMember records
        try {
            List<OrganizationMember> memberships = organizationMemberRepository.findByUserId(userId);
            for (OrganizationMember member : memberships) {
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
                    organizationMemberRepository.save(member);
                }
            }
            log.info("Synced {} OrganizationMember records for user {}", memberships.size(), userId);
        } catch (Exception e) {
            log.error("Failed to sync OrganizationMember records: ", e);
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

        // 3. Sync OrganizationJoinRequest records
        try {
            List<OrganizationJoinRequest> requests = organizationJoinRequestRepository.findByUserId(userId);
            for (OrganizationJoinRequest request : requests) {
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
                    organizationJoinRequestRepository.save(request);
                }
            }
            log.info("Synced {} OrganizationJoinRequest records for user {}", requests.size(), userId);
        } catch (Exception e) {
            log.error("Failed to sync OrganizationJoinRequest records: ", e);
        }

        // 4. Sync Thought creator info
        try {
            List<Thought> posts = thoughtRepository.findByUserId(userId);
            for (Thought post : posts) {
                boolean changed = false;
                if (updatedNickname != null && !updatedNickname.equals(post.getCreatorName())) {
                    post.setCreatorName(updatedNickname);
                    changed = true;
                }
                if (updatedPicture != null && !updatedPicture.equals(post.getCreatorPicture())) {
                    post.setCreatorPicture(updatedPicture);
                    changed = true;
                }
                if (changed) {
                    thoughtRepository.save(post);
                }
            }
            log.info("Synced {} Thought creator info records for user {}", posts.size(), userId);
        } catch (Exception e) {
            log.error("Failed to sync Thought creator info records: ", e);
        }

        // 5. Sync Thought comments info
        try {
            List<Thought> postsWithComments = thoughtRepository.findByCommentsUserId(userId);
            for (Thought post : postsWithComments) {
                boolean changed = false;
                if (post.getComments() != null) {
                    for (Thought.Comment comment : post.getComments()) {
                        if (userId.equals(comment.getUserId())) {
                            if (updatedNickname != null && !updatedNickname.equals(comment.getUserName())) {
                                comment.setUserName(updatedNickname);
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
                    thoughtRepository.save(post);
                }
            }
            log.info("Synced {} Thought comment records for user {}", postsWithComments.size(), userId);
        } catch (Exception e) {
            log.error("Failed to sync Thought comment records: ", e);
        }

        // 6. Sync OrganizationPost comments info
        try {
            List<OrganizationPost> postsWithComments = organizationPostRepository.findByCommentsUserId(userId);
            for (OrganizationPost post : postsWithComments) {
                boolean changed = false;
                if (post.getComments() != null) {
                    for (OrganizationPost.Comment comment : post.getComments()) {
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
                    organizationPostRepository.save(post);
                }
            }
            log.info("Synced {} OrganizationPost comment records for user {}", postsWithComments.size(), userId);
        } catch (Exception e) {
            log.error("Failed to sync OrganizationPost comment records: ", e);
        }

        // 7. Sync Notification records where this user was the actor
        try {
            List<com.cax.cax_backend.notification.model.Notification> notifications = notificationRepository.findByActorId(userId);
            for (com.cax.cax_backend.notification.model.Notification notif : notifications) {
                boolean changed = false;
                Map<String, String> data = notif.getData();
                
                // Update actor picture (imageUrl)
                if (updatedPicture != null && !updatedPicture.equals(notif.getImageUrl())) {
                    notif.setImageUrl(updatedPicture);
                    changed = true;
                }
                
                if (data != null && data.containsKey("actorName")) {
                    String oldName = data.get("actorName");
                    if (updatedNickname != null && !updatedNickname.equals(oldName)) {
                        // Update actorName in data map
                        data.put("actorName", updatedNickname);
                        
                        // Update body text replacing oldName with updatedNickname
                        String body = notif.getBody();
                        if (body != null && oldName != null && body.contains(oldName)) {
                            notif.setBody(body.replace(oldName, updatedNickname));
                        }
                        
                        // Update title text replacing oldName with updatedNickname
                        String title = notif.getTitle();
                        if (title != null && oldName != null && title.contains(oldName)) {
                            notif.setTitle(title.replace(oldName, updatedNickname));
                        }
                        
                        changed = true;
                    }
                }
                
                if (changed) {
                    notificationRepository.save(notif);
                }
            }
            log.info("Synced {} Notification records where user {} was actor", notifications.size(), userId);
        } catch (Exception e) {
            log.error("Failed to sync Notification records: ", e);
        }
    }
}
