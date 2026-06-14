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

    @Query("{ 'isOnline': true, '$or': [ { 'lastSeenAt': { '$lt': ?0 } }, { 'lastSeenAt': null } ] }")
    List<User> findStaleOnlineUsers(java.time.Instant threshold);
}
