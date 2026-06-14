package com.cax.cax_backend.chat.repository;

import com.cax.cax_backend.chat.model.ClubMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClubMessageRepository extends MongoRepository<ClubMessage, String> {
    List<ClubMessage> findByClubIdOrderByCreatedAtDesc(String clubId, Pageable pageable);
    List<ClubMessage> findBySenderId(String senderId);
    List<ClubMessage> findByReplyToIdIn(List<String> replyToIds);
}
