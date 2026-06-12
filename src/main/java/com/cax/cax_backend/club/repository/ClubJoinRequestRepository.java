package com.cax.cax_backend.club.repository;

import com.cax.cax_backend.club.model.ClubJoinRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClubJoinRequestRepository extends MongoRepository<ClubJoinRequest, String> {
    List<ClubJoinRequest> findByClubId(String clubId);
    List<ClubJoinRequest> findByClubIdAndStatus(String clubId, String status);
    Optional<ClubJoinRequest> findByClubIdAndUserId(String clubId, String userId);
    Optional<ClubJoinRequest> findByClubIdAndUserIdAndStatus(String clubId, String userId, String status);
    List<ClubJoinRequest> findByUserId(String userId);
}
