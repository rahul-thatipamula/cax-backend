package com.cax.cax_backend.bulletinevent.repository;

import com.cax.cax_backend.bulletinevent.model.BulletinEventScore;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BulletinEventScoreRepository extends MongoRepository<BulletinEventScore, String> {
    Optional<BulletinEventScore> findByBulletinEventId(String bulletinEventId);
}
