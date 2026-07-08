package com.cax.cax_backend.organization.repository;

import com.cax.cax_backend.organization.model.Ripple;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;
import java.util.List;

@Repository
public interface RippleRepository extends MongoRepository<Ripple, String> {
    @Query(value = "{ 'organizationId': ?0, 'deleted': { $ne: true } }", sort = "{ 'createdAt': -1 }")
    List<Ripple> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);

    @Query(value = "{ 'organizationId': ?0, 'deleted': { $ne: true } }", sort = "{ 'createdAt': -1 }")
    List<Ripple> findByOrganizationIdOrderByCreatedAtDesc(String organizationId, Pageable pageable);
}
