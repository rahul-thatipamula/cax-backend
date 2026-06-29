package com.cax.cax_backend.organization.repository;

import com.cax.cax_backend.organization.model.Organization;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationRepository extends MongoRepository<Organization, String> {
    List<Organization> findByCollegeId(String collegeId);
    List<Organization> findByCollegeId(String collegeId, Pageable pageable);
    Optional<Organization> findByCollegeIdAndName(String collegeId, String name);
}
