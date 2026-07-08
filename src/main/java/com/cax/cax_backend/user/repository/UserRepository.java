package com.cax.cax_backend.user.repository;

import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.common.enums.UserRole;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.Optional;
import java.util.List;

public interface UserRepository extends MongoRepository<User, String>, CustomUserRepository {
    Optional<User> findByGoogleId(String googleId);
    boolean existsByEmail(String email);
    boolean existsByUserId(String userId);
    List<User> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String name, String email);
    List<User> findByCollegeDetails_CollegeId(String collegeId);
    List<User> findByCollegeDetails_CollegeIdAndRoleAndBlocked(String collegeId, UserRole role, boolean blocked);
    List<User> findByPremiumExpiresAtAfterAndBlocked(java.time.Instant now, boolean blocked);
    boolean existsByCaxId(String caxId);
    java.util.Optional<User> findByCaxId(String caxId);
    List<User> findByCaxIdIn(java.util.Collection<String> caxIds);
    List<User> findByUserIdIn(java.util.Collection<String> userIds);

    @Query("{ 'isOnline': true, '$or': [ { 'lastSeenAt': { '$lt': ?0 } }, { 'lastSeenAt': null } ] }")
    List<User> findStaleOnlineUsers(java.time.Instant threshold);

    @Query("{ 'blocked': false, 'fcmToken': { $exists: true, $ne: null }, $or: [ { 'lastSeenAt': { $gt: ?0 } }, { 'createdAt': { $gt: ?0 } } ] }")
    List<User> findActiveNotificationEligibleUsers(java.time.Instant activeThreshold);

    @Query("{ 'collegeDetails.collegeId': ?0, 'blocked': false, 'fcmToken': { $exists: true, $ne: null } }")
    List<User> findNotificationEligibleUsersByCollegeId(String collegeId);

    @Query("{ 'blocked': false, 'fcmToken': { $exists: true, $ne: null } }")
    List<User> findGlobalNotificationEligibleUsers();

    @Query("{ 'waterReminderSubscribed': true, 'blocked': false, 'fcmToken': { $exists: true, $ne: null }, '$or': [ { 'lastWaterReminderSentAt': { '$exists': false } }, { 'lastWaterReminderSentAt': null }, { 'lastWaterReminderSentAt': { '$lt': ?0 } } ] }")
    List<User> findUsersForWaterReminder(java.time.Instant threshold);

    boolean existsByDisplayName(String displayName);
    java.util.Optional<User> findByDisplayNameIgnoreCase(String displayName);
    List<User> findByDisplayNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String displayName, String email);
}
