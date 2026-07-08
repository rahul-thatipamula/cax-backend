package com.cax.cax_backend.ad.repository;

import com.cax.cax_backend.ad.model.Ad;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;
import java.util.Optional;

public interface AdRepository extends MongoRepository<Ad, String> {
    @Query("{ 'adId': ?0, 'deleted': { $ne: true } }")
    Optional<Ad> findByAdId(String adId);

    @Query("{ 'active': ?0, 'deleted': { $ne: true } }")
    List<Ad> findByActive(boolean active);

    @Cacheable(value = "ads", key = "'global'")
    @Query("{ 'active': ?0, 'global': ?1, 'deleted': { $ne: true } }")
    List<Ad> findByActiveAndGlobal(boolean active, boolean global);

    @Cacheable(value = "ads", key = "#collegeId")
    @Query("{ 'active': ?0, 'collegeId': ?1, 'deleted': { $ne: true } }")
    List<Ad> findByActiveAndCollegeId(boolean active, String collegeId);
}
