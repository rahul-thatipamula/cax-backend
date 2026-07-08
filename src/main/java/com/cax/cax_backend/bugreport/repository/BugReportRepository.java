package com.cax.cax_backend.bugreport.repository;

import com.cax.cax_backend.bugreport.model.BugReport;
import com.cax.cax_backend.common.enums.BugReportEnums.BugStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;

public interface BugReportRepository extends MongoRepository<BugReport, String> {
    @Query("{ 'userId': ?0, 'deleted': { $ne: true } }")
    List<BugReport> findByUserId(String userId);

    @Query(value = "{ 'userId': ?0, 'deleted': { $ne: true } }", sort = "{ 'createdAt': -1 }")
    List<BugReport> findByUserIdOrderByCreatedAtDesc(String userId);

    // Admin-only listing — intentionally not deleted-filtered so admins retain full visibility (mirrors ThoughtRepository convention).
    List<BugReport> findAllByOrderByCreatedAtDesc();
    List<BugReport> findByStatus(BugStatus status);
}
