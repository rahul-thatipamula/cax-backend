package com.cax.cax_backend.user.listener;

import com.cax.cax_backend.email.service.EmailService;
import com.cax.cax_backend.notification.service.NotificationService;
import com.cax.cax_backend.common.enums.UserRole;
import com.cax.cax_backend.common.enums.NotificationEnums.NotificationType;
import com.cax.cax_backend.user.event.UserRoleChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserRoleChangedListener {

    private final EmailService emailService;
    private final NotificationService notificationService;

    @Async("taskExecutor")
    @EventListener
    public void handleUserRoleChangedEvent(UserRoleChangedEvent event) {
        log.info("Received UserRoleChangedEvent for user: {}, from {} to {}", 
                event.getUser().getUserId(), event.getPreviousRole(), event.getNewRole());
        
        try {
            UserRole prev = event.getPreviousRole();
            UserRole next = event.getNewRole();
            
            if (next == UserRole.SUPER_STUDENT) {
                // Promotion to Super Student
                emailService.sendSuperStudentPromotionEmail(event.getUser());
                
                Map<String, String> data = new HashMap<>();
                data.put("type", NotificationType.SYSTEM.getValue());
                data.put("role", next.getValue());
                
                notificationService.createNotification(
                    event.getUser().getUserId(),
                    "Role Promoted",
                    "You have been promoted to the role of Super Student.",
                    NotificationType.SYSTEM,
                    data
                );
            } else if (prev == UserRole.SUPER_STUDENT && next == UserRole.STUDENT) {
                // Demotion to standard Student
                emailService.sendSuperStudentDemotionEmail(event.getUser());
                
                Map<String, String> data = new HashMap<>();
                data.put("type", NotificationType.SYSTEM.getValue());
                data.put("role", next.getValue());
                
                notificationService.createNotification(
                    event.getUser().getUserId(),
                    "Role Updated",
                    "Your Super Student privileges have been rescinded, and your role has been reverted to Student.",
                    NotificationType.SYSTEM,
                    data
                );
            }
        } catch (Exception e) {
            log.error("Error handling role change notifications for user: " + event.getUser().getUserId(), e);
        }
    }
}
