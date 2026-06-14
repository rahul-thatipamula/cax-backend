package com.cax.cax_backend.ad.repository;

import com.cax.cax_backend.ad.model.UserAdTracking;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;
import java.util.List;

public interface UserAdTrackingRepository extends MongoRepository<UserAdTracking, String> {
    Optional<UserAdTracking> findByUserIdAndAdId(String userId, String adId);
    List<UserAdTracking> findByUserId(String userId);
    List<UserAdTracking> findByAdId(String adId);
}
