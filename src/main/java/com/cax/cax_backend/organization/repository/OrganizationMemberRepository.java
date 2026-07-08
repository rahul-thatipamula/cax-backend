package com.cax.cax_backend.organization.repository;

import com.cax.cax_backend.organization.model.OrganizationMember;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationMemberRepository extends MongoRepository<OrganizationMember, String> {
    @Query("{ 'organizationId': ?0, 'deleted': { $ne: true } }")
    List<OrganizationMember> findByOrganizationId(String organizationId);

    @Query("{ 'userId': ?0, 'deleted': { $ne: true } }")
    List<OrganizationMember> findByUserId(String userId);

    @Query("{ 'organizationId': ?0, 'userId': ?1, 'deleted': { $ne: true } }")
    Optional<OrganizationMember> findByOrganizationIdAndUserId(String organizationId, String userId);

    @Query(value = "{ 'organizationId': ?0, 'userId': ?1, 'deleted': { $ne: true } }", exists = true)
    boolean existsByOrganizationIdAndUserId(String organizationId, String userId);

    // Ignores the deleted flag on purpose: used to find a possibly soft-deleted
    // membership doc so it can be reactivated instead of violating the unique
    // (organizationId, userId) index by inserting a duplicate.
    @Query("{ 'organizationId': ?0, 'userId': ?1 }")
    Optional<OrganizationMember> findAnyByOrganizationIdAndUserId(String organizationId, String userId);
}
