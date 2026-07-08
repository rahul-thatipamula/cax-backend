package com.cax.cax_backend.organization.repository;

import com.cax.cax_backend.organization.model.OrganizationPost;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;
import java.util.List;

@Repository
public interface OrganizationPostRepository extends MongoRepository<OrganizationPost, String> {
    @Query(value = "{ 'collegeId': ?0, 'deleted': { $ne: true } }", sort = "{ 'createdAt': -1 }")
    List<OrganizationPost> findByCollegeIdOrderByCreatedAtDesc(String collegeId, Pageable pageable);

    @Query(value = "{ 'organizationId': ?0, 'deleted': { $ne: true } }", sort = "{ 'createdAt': -1 }")
    List<OrganizationPost> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);

    List<OrganizationPost> findByCommentsUserId(String userId);
}
