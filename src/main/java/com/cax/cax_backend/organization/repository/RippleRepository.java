package com.cax.cax_backend.organization.repository;

import com.cax.cax_backend.organization.model.Ripple;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;
import java.util.List;

@Repository
public interface RippleRepository extends MongoRepository<Ripple, String> {
    List<Ripple> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);
    List<Ripple> findByOrganizationIdOrderByCreatedAtDesc(String organizationId, Pageable pageable);
}
