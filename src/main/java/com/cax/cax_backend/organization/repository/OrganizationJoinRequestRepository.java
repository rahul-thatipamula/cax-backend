package com.cax.cax_backend.organization.repository;

import com.cax.cax_backend.organization.model.OrganizationJoinRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationJoinRequestRepository extends MongoRepository<OrganizationJoinRequest, String> {
    List<OrganizationJoinRequest> findByOrganizationId(String organizationId);
    List<OrganizationJoinRequest> findByOrganizationIdAndStatus(String organizationId, String status);
    Optional<OrganizationJoinRequest> findByOrganizationIdAndUserId(String organizationId, String userId);
    Optional<OrganizationJoinRequest> findByOrganizationIdAndUserIdAndStatus(String organizationId, String userId, String status);
    List<OrganizationJoinRequest> findByUserId(String userId);
}
