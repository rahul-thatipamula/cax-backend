package com.cax.cax_backend.attendance.repository;

import com.cax.cax_backend.attendance.model.AttendanceTerm;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceTermRepository extends MongoRepository<AttendanceTerm, String> {
    List<AttendanceTerm> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<AttendanceTerm> findByIdAndUserId(String id, String userId);

    long countByUserId(String userId);

    void deleteByIdAndUserId(String id, String userId);
}
