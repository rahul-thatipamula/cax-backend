package com.cax.cax_backend.idcard.repository;

import com.cax.cax_backend.common.enums.CarouselEnums.VerificationStatus;
import com.cax.cax_backend.idcard.model.IDCard;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface IDCardRepository extends MongoRepository<IDCard, String> {
    Optional<IDCard> findByUserId(String userId);
    List<IDCard> findByStatus(VerificationStatus status);
    long countByStatus(VerificationStatus status);
}
