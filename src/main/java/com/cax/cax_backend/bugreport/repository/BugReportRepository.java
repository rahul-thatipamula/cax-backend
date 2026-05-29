package com.cax.cax_backend.bugreport.repository;

import com.cax.cax_backend.bugreport.model.BugReport;
import com.cax.cax_backend.common.enums.BugReportEnums.BugStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface BugReportRepository extends MongoRepository<BugReport, String> {
    List<BugReport> findByUserId(String userId);
    List<BugReport> findByUserIdOrderByCreatedAtDesc(String userId);
    List<BugReport> findAllByOrderByCreatedAtDesc();
    List<BugReport> findByStatus(BugStatus status);
}
