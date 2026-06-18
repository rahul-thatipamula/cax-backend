package com.cax.cax_backend.chat.service;

import com.cax.cax_backend.chat.model.ClubMessage;
import com.cax.cax_backend.chat.repository.ClubMessageRepository;
import com.cax.cax_backend.club.model.Club;
import com.cax.cax_backend.club.service.ClubService;
import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.common.util.EncryptionUtils;
import com.cax.cax_backend.notification.service.NotificationService;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ClubMessageRepository clubMessageRepository;
    private final ClubService clubService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final ChatNotificationService chatNotificationService;

    public ClubMessage sendMessage(String senderId, String clubId, String content) {
        return sendMessage(senderId, clubId, content, null, null, null);
    }

    public ClubMessage sendMessage(String senderId, String clubId, String content, String replyToId, String replyToName, String replyToContent) {
        // Verify sender is a member of the club
        if (!clubService.isClubMember(senderId, clubId)) {
            throw new BusinessException.BadRequestException("You must be a member of this club to send messages.");
        }

        User sender = userService.getUserByUserId(senderId);
        Club club = clubService.getClubById(clubId);

        // Encrypt message content before saving
        String encryptedContent = EncryptionUtils.encrypt(content);

        ClubMessage message = ClubMessage.builder()
                .clubId(clubId)
                .senderId(senderId)
                .senderName(sender.getName())
                .senderPicture(sender.getPicture())
                .content(encryptedContent)
                .replyToId(replyToId)
                .replyToName(replyToName)
                .replyToContent(replyToContent != null ? EncryptionUtils.encrypt(replyToContent) : null)
                .createdAt(Instant.now())
                .build();

        ClubMessage savedMessage = clubMessageRepository.save(message);

        // Decrypt for returning
        savedMessage.setContent(content);
        if (savedMessage.getReplyToContent() != null) {
            savedMessage.setReplyToContent(EncryptionUtils.decrypt(savedMessage.getReplyToContent()));
        }

        // Send notifications to all other members asynchronously
        chatNotificationService.sendChatNotificationsAsync(senderId, club, sender.getName(), content);

        return savedMessage;
    }

    public List<ClubMessage> getClubMessages(String userId, String clubId, int page, int size) {
        // Verify user is a member of the club
        if (!clubService.isClubMember(userId, clubId)) {
            throw new BusinessException.BadRequestException("You must be a member of this club to view messages.");
        }

        Pageable pageable = PageRequest.of(page, size);
        List<ClubMessage> messages = clubMessageRepository.findByClubIdOrderByCreatedAtDesc(clubId, pageable);

        // Decrypt content of each message
        messages.forEach(msg -> {
            try {
                msg.setContent(EncryptionUtils.decrypt(msg.getContent()));
                if (msg.getReplyToContent() != null) {
                    msg.setReplyToContent(EncryptionUtils.decrypt(msg.getReplyToContent()));
                }
            } catch (Exception e) {
                log.error("Failed to decrypt message {}: {}", msg.getId(), e.getMessage());
            }
        });

        return messages;
    }

    public void markClubChatAsRead(String userId, String clubId) {
        notificationService.markClubChatAsRead(userId, clubId);
    }
}
