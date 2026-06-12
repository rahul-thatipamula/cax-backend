package com.cax.cax_backend.chat.service;

import com.cax.cax_backend.chat.handler.ChatSessionTracker;
import com.cax.cax_backend.club.model.Club;
import com.cax.cax_backend.club.model.ClubMember;
import com.cax.cax_backend.club.repository.ClubMemberRepository;
import com.cax.cax_backend.common.enums.NotificationEnums.NotificationType;
import com.cax.cax_backend.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatNotificationService {

    private final ClubMemberRepository clubMemberRepository;
    private final ChatSessionTracker chatSessionTracker;
    private final NotificationService notificationService;

    @Async("taskExecutor")
    public void sendChatNotificationsAsync(String senderId, Club club, String senderName, String textContent) {
        log.info("Starting async chat notifications for club: {} by sender: {}", club.getId(), senderName);
        List<ClubMember> members = clubMemberRepository.findByClubId(club.getId());
        
        Map<String, String> data = new HashMap<>();
        data.put("type", "club_chat");
        data.put("clubId", club.getId());

        String title = "New message in " + club.getName();
        String body = senderName + ": " + textContent;

        for (ClubMember member : members) {
            // Skip the sender themselves
            if (member.getUserId().equals(senderId)) {
                continue;
            }
            // Skip if the user is currently active on the chat screen for this club
            if (chatSessionTracker.isUserActiveInClub(member.getUserId(), club.getId())) {
                log.debug("User {} is active on the chat screen for club {}, skipping notification", member.getUserId(), club.getId());
                continue;
            }
            // Skip if the user has muted this club
            if (member.isMuted()) {
                log.debug("User {} muted club {}, skipping notification", member.getUserId(), club.getId());
                continue;
            }

            try {
                notificationService.createNotification(
                        member.getUserId(),
                        title,
                        body,
                        NotificationType.CLUB_CHAT,
                        data
                );
            } catch (Exception e) {
                log.error("Failed to send chat notification to user: {}", member.getUserId(), e);
            }
        }
        log.info("Finished async chat notifications for club: {}", club.getId());
    }
}
