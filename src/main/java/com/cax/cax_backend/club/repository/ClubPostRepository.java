package com.cax.cax_backend.club.repository;

import com.cax.cax_backend.club.model.ClubPost;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClubPostRepository extends MongoRepository<ClubPost, String> {
    List<ClubPost> findByCollegeIdOrderByCreatedAtDesc(String collegeId);
    List<ClubPost> findByClubIdOrderByCreatedAtDesc(String clubId);
}
