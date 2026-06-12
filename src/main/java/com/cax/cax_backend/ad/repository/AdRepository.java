package com.cax.cax_backend.ad.repository;

import com.cax.cax_backend.ad.model.Ad;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface AdRepository extends MongoRepository<Ad, String> {
    Optional<Ad> findByAdId(String adId);
    List<Ad> findByActive(boolean active);

    @Cacheable(value = "ads", key = "'global'")
    List<Ad> findByActiveAndGlobal(boolean active, boolean global);

    @Cacheable(value = "ads", key = "#collegeId")
    List<Ad> findByActiveAndCollegeId(boolean active, String collegeId);
}
