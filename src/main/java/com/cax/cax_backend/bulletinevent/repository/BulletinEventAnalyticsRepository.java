package com.cax.cax_backend.bulletinevent.repository;

import com.cax.cax_backend.bulletinevent.model.BulletinEventAnalytics;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BulletinEventAnalyticsRepository extends MongoRepository<BulletinEventAnalytics, String> {

    Optional<BulletinEventAnalytics> findByBulletinEventId(String bulletinEventId);

    void deleteByBulletinEventId(String bulletinEventId);
}
