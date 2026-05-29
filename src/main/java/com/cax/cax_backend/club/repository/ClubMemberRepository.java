package com.cax.cax_backend.club.repository;

import com.cax.cax_backend.club.model.ClubMember;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClubMemberRepository extends MongoRepository<ClubMember, String> {
    List<ClubMember> findByClubId(String clubId);
    List<ClubMember> findByUserId(String userId);
    Optional<ClubMember> findByClubIdAndUserId(String clubId, String userId);
    boolean existsByClubIdAndUserId(String clubId, String userId);
}
