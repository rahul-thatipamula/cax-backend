package com.cax.cax_backend.user.repository;

import com.cax.cax_backend.user.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserRepositoryImpl implements CustomUserRepository {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public Optional<User> findByUserId(String userId) {
        Query query = new Query(Criteria.where("userId").is(userId));
        List<User> list = mongoTemplate.find(query, User.class);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        Query query = new Query(Criteria.where("email").is(email));
        List<User> list = mongoTemplate.find(query, User.class);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
