package com.cax.cax_backend.boost.repository;

import com.cax.cax_backend.boost.model.BoostStatus;
import com.cax.cax_backend.boost.model.ThoughtBoostRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ThoughtBoostRequestRepository extends MongoRepository<ThoughtBoostRequest, String> {

    List<ThoughtBoostRequest> findByStatusOrderByRequestedAtAsc(BoostStatus status, Pageable pageable);

    List<ThoughtBoostRequest> findByStatus(BoostStatus status);

    List<ThoughtBoostRequest> findByUserId(String userId);

    Optional<ThoughtBoostRequest> findByThoughtIdAndStatus(String thoughtId, BoostStatus status);

    boolean existsByThoughtIdAndStatusIn(String thoughtId, List<BoostStatus> statuses);
}
