package com.cax.cax_backend.notification.repository;

import com.cax.cax_backend.notification.model.Notification;
import com.cax.cax_backend.common.enums.NotificationEnums.NotificationType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;

public interface NotificationRepository extends MongoRepository<Notification, String> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(String userId);
    long countByUserIdAndReadFalse(String userId);

    @Query("{ 'data.actorId': ?0 }")
    List<Notification> findByActorId(String actorId);
}
