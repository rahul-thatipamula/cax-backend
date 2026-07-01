package com.cax.cax_backend.game.controller;

import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/public/users")
@RequiredArgsConstructor
public class PublicUserController {

    private final UserRepository userRepository;

    @GetMapping("/cax/{caxId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> getUserByCaxId(@PathVariable String caxId) {
        User user = userRepository.findByCaxId(caxId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("User", caxId));

        String name = user.getName();
        try {
            name = com.cax.cax_backend.common.util.EncryptionUtils.decrypt(name);
        } catch (Exception ignored) {}

        String collegeName = user.getCollegeDetails() != null ? user.getCollegeDetails().getCollegeName() : null;

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "caxId", caxId,
                "name", name != null ? name : "",
                "collegeName", collegeName != null ? collegeName : ""
        )));
    }
}
