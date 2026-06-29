package com.cax.cax_backend.organization.repository;

import com.cax.cax_backend.organization.model.OrganizationMember;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationMemberRepository extends MongoRepository<OrganizationMember, String> {
    List<OrganizationMember> findByOrganizationId(String organizationId);
    List<OrganizationMember> findByUserId(String userId);
    Optional<OrganizationMember> findByOrganizationIdAndUserId(String organizationId, String userId);
    boolean existsByOrganizationIdAndUserId(String organizationId, String userId);
}
