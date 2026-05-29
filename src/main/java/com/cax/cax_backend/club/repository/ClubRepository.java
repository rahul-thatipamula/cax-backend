package com.cax.cax_backend.club.repository;

import com.cax.cax_backend.club.model.Club;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClubRepository extends MongoRepository<Club, String> {
    List<Club> findByCollegeId(String collegeId);
}
