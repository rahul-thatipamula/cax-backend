package com.cax.cax_backend.chat.controller;

import com.cax.cax_backend.chat.handler.ChatWebSocketHandler;
import com.cax.cax_backend.chat.model.ClubMessage;
import com.cax.cax_backend.chat.service.ChatService;
import com.cax.cax_backend.club.service.ClubService;
import com.cax.cax_backend.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/clubs/{clubId}/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ClubService clubService;
    private final ChatWebSocketHandler chatWebSocketHandler;

    @GetMapping("/messages")
    public ResponseEntity<ApiResponse<List<ClubMessage>>> getClubMessages(
            Authentication auth,
            @PathVariable String clubId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).build();
        }
        
        String userId = (String) auth.getPrincipal();
        List<ClubMessage> messages = chatService.getClubMessages(userId, clubId, page, size);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    @PostMapping("/messages")
    public ResponseEntity<ApiResponse<ClubMessage>> sendClubMessage(
            Authentication auth,
            @PathVariable String clubId,
            @RequestBody Map<String, String> body) {
        
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).build();
        }
        
        String userId = (String) auth.getPrincipal();
        String content = body.get("content");
        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        String replyToId = body.get("replyToId");
        String replyToName = body.get("replyToName");
        String replyToContent = body.get("replyToContent");
        
        ClubMessage message = chatService.sendMessage(userId, clubId, content, replyToId, replyToName, replyToContent);
        
        try {
            chatWebSocketHandler.broadcastToClub(clubId, message);
        } catch (Exception e) {
            log.error("Failed to broadcast message over WebSocket: {}", e.getMessage());
        }
        
        return ResponseEntity.ok(ApiResponse.success("Message sent successfully", message));
    }

    @PutMapping("/mute")
    public ResponseEntity<ApiResponse<Void>> toggleMuteChat(
            Authentication auth,
            @PathVariable String clubId,
            @RequestParam boolean isMuted) {
        
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).build();
        }
        
        String userId = (String) auth.getPrincipal();
        clubService.toggleMuteClub(userId, clubId, isMuted);
        String message = isMuted ? "Notifications silenced for this club chat" : "Notifications enabled for this club chat";
        return ResponseEntity.ok(ApiResponse.success(message));
    }

    @PostMapping("/read")
    public ResponseEntity<ApiResponse<Void>> markChatAsRead(
            Authentication auth,
            @PathVariable String clubId) {
        
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).build();
        }
        
        String userId = (String) auth.getPrincipal();
        chatService.markClubChatAsRead(userId, clubId);
        return ResponseEntity.ok(ApiResponse.success("Club chat marked as read"));
    }
}
