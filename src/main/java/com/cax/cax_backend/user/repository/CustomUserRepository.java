package com.cax.cax_backend.user.repository;

import com.cax.cax_backend.user.model.User;
import java.util.Optional;

public interface CustomUserRepository {
    Optional<User> findByUserId(String userId);
    Optional<User> findByEmail(String email);
}
