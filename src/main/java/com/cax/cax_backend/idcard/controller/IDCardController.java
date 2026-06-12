package com.cax.cax_backend.idcard.controller;

import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.common.enums.CarouselEnums.VerificationStatus;
import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.idcard.model.IDCard;
import com.cax.cax_backend.idcard.repository.IDCardRepository;
import com.cax.cax_backend.user.repository.UserRepository;
import com.cax.cax_backend.idcard.event.IDCardStatusChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.cax.cax_backend.common.service.R2StorageService;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/users") @RequiredArgsConstructor
public class IDCardController {
    private final IDCardRepository repo;
    private final R2StorageService r2StorageService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @GetMapping("/idcard/presigned-url")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPresignedUrl(
            Authentication auth,
            @RequestParam("extension") String extension,
            @RequestParam("contentType") String contentType) {

        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();

        String ext = extension.toLowerCase();
        if (!ext.equals(".jpg") && !ext.equals(".jpeg") && !ext.equals(".png") &&
            !ext.equals("jpg") && !ext.equals("jpeg") && !ext.equals("png")) {
            throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("Invalid file type. Only JPG, JPEG, and PNG images are allowed");
        }

        Map<String, String> urls = r2StorageService.generatePresignedUploadUrl("id-cards", userId, ext, contentType);
        return ResponseEntity.ok(ApiResponse.success(urls));
    }

    @GetMapping("/idcard")
    public ResponseEntity<ApiResponse<IDCard>> getIDCard(Authentication auth) {
        IDCard card = repo.findByUserId((String) auth.getPrincipal()).orElse(null);
        if (card != null) {
            populateUserDetails(card);
            card.setImageUrl(r2StorageService.generatePresignedGetUrl(card.getImageUrl()));
        }
        return ResponseEntity.ok(ApiResponse.success(card));
    }

    @PostMapping("/idcard")
    public ResponseEntity<ApiResponse<IDCard>> saveIDCard(Authentication auth, @RequestBody IDCard body) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();

        com.cax.cax_backend.user.model.User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("User"));

        boolean isExpired = user.getIdCardExpiresAt() != null && user.getIdCardExpiresAt().isBefore(Instant.now());
        if (user.isIdVerified() && !isExpired && !user.isReVerificationRequested()) {
            throw new BusinessException.BadRequestException("ID Card is verified and active. Uploads are locked.");
        }

        IDCard existingCard = repo.findByUserId(userId).orElse(null);
        if (existingCard != null && existingCard.getStatus() == VerificationStatus.REJECTED && user.getRejectionCount() >= 3) {
            throw new BusinessException.BadRequestException("Maximum verification attempts reached (3/3). Please contact support.");
        }

        IDCard card = repo.findByUserId(userId).orElse(new IDCard());
        card.setUserId(userId);
        card.setIdCardNumber(body.getIdCardNumber());
        card.setDepartment(body.getDepartment());
        card.setStatus(VerificationStatus.PENDING);
        card.setCreatedAt(card.getCreatedAt() != null ? card.getCreatedAt() : Instant.now());
        card.setUpdatedAt(Instant.now());
        card.setRejectionReason(null);
        card.setVerificationNotes(null);

        final String[] renamedUrl = {body.getImageUrl()};
        if (user.getCollegeDetails() != null) {
            card.setCollegeId(user.getCollegeDetails().getCollegeId());
            card.setCollegeName(user.getCollegeDetails().getCollegeName());
        }
        if (user.getEmail() != null) {
            renamedUrl[0] = r2StorageService.renameFileToEmailAndIdCardNumber(body.getImageUrl(), user.getEmail(), body.getIdCardNumber());
        }
        boolean wasVerifiedAndActive = user.isIdVerified() && (user.getIdCardExpiresAt() == null || user.getIdCardExpiresAt().isAfter(Instant.now()));
        user.setIdVerified(wasVerifiedAndActive);
        user.setIdCardImagePath(renamedUrl[0]);
        user.setReVerificationRequested(false);
        userRepository.save(user);

        card.setImageUrl(renamedUrl[0]);
        IDCard savedCard = repo.save(card);
        savedCard.setImageUrl(r2StorageService.generatePresignedGetUrl(savedCard.getImageUrl()));
        return ResponseEntity.ok(ApiResponse.created("ID card saved", savedCard));
    }

    @PutMapping("/idcard")
    public ResponseEntity<ApiResponse<IDCard>> editIDCard(Authentication auth, @RequestBody Map<String, Object> body) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        com.cax.cax_backend.user.model.User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("User"));

        boolean isExpired = user.getIdCardExpiresAt() != null && user.getIdCardExpiresAt().isBefore(Instant.now());
        if (user.isIdVerified() && !isExpired && !user.isReVerificationRequested()) {
            throw new BusinessException.BadRequestException("ID Card is verified and active. Edits are locked.");
        }

        IDCard card = repo.findByUserId(userId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("ID Card"));
        if (card.getStatus() == VerificationStatus.REJECTED && user.getRejectionCount() >= 3) {
            throw new BusinessException.BadRequestException("Maximum verification attempts reached (3/3). Please contact support.");
        }
        if (body.containsKey("imageUrl")) card.setImageUrl((String) body.get("imageUrl"));
        card.setUpdatedAt(Instant.now());
        IDCard savedCard = repo.save(card);
        savedCard.setImageUrl(r2StorageService.generatePresignedGetUrl(savedCard.getImageUrl()));
        return ResponseEntity.ok(ApiResponse.success(savedCard));
    }

    @DeleteMapping("/idcard")
    public ResponseEntity<ApiResponse<Void>> deleteIDCard(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();
        com.cax.cax_backend.user.model.User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("User"));

        boolean isExpired = user.getIdCardExpiresAt() != null && user.getIdCardExpiresAt().isBefore(Instant.now());
        if (user.isIdVerified() && !isExpired && !user.isReVerificationRequested()) {
            throw new BusinessException.BadRequestException("ID Card is verified and active. Deletion is locked.");
        }

        IDCard existingCard = repo.findByUserId(userId).orElse(null);
        if (existingCard != null && existingCard.getStatus() == VerificationStatus.REJECTED && user.getRejectionCount() >= 3) {
            throw new BusinessException.BadRequestException("Maximum verification attempts reached (3/3). Please contact support.");
        }

        repo.findByUserId(userId).ifPresent(c -> repo.delete(c));
        user.setIdVerified(false);
        user.setIdCardImagePath(null);
        user.setReVerificationRequested(false);
        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/idcard/{userId}/verify")
    public ResponseEntity<ApiResponse<IDCard>> verifyIDCard(@PathVariable String userId, @RequestBody Map<String, String> body) {
        IDCard card = repo.findByUserId(userId).orElseThrow(() -> new BusinessException.ResourceNotFoundException("ID Card"));
        card.setStatus(VerificationStatus.APPROVED);
        card.setVerificationNotes(body.get("verificationNotes"));
        card.setRejectionReason(null);
        card.setVerifiedAt(Instant.now());
        IDCard savedCard = repo.save(card);
        userRepository.findByUserId(userId).ifPresent(u -> {
            u.setIdVerified(true);
            u.setIdCardExpiresAt(Instant.now().plus(180, java.time.temporal.ChronoUnit.DAYS));
            if (u.getCaxId() == null || u.getCaxId().isEmpty()) {
                u.setCaxId(generateUniqueCaxId());
            }
            u.setRejectionCount(0);
            userRepository.save(u);
            eventPublisher.publishEvent(new IDCardStatusChangedEvent(this, u, savedCard));
        });
        savedCard.setImageUrl(r2StorageService.generatePresignedGetUrl(savedCard.getImageUrl()));
        return ResponseEntity.ok(ApiResponse.success(savedCard));
    }

    @PostMapping("/idcard/{userId}/verify-with-data")
    public ResponseEntity<ApiResponse<IDCard>> verifyWithData(@PathVariable String userId, @RequestBody Map<String, Object> body) {
        IDCard card = repo.findByUserId(userId).orElseThrow(() -> new BusinessException.ResourceNotFoundException("ID Card"));
        card.setStatus(VerificationStatus.APPROVED);
        card.setManualData(body);
        card.setRejectionReason(null);
        card.setVerifiedAt(Instant.now());
        IDCard savedCard = repo.save(card);
        userRepository.findByUserId(userId).ifPresent(u -> {
            u.setIdVerified(true);
            u.setIdCardExpiresAt(Instant.now().plus(180, java.time.temporal.ChronoUnit.DAYS));
            if (u.getCaxId() == null || u.getCaxId().isEmpty()) {
                u.setCaxId(generateUniqueCaxId());
            }
            u.setRejectionCount(0);
            userRepository.save(u);
            eventPublisher.publishEvent(new IDCardStatusChangedEvent(this, u, savedCard));
        });
        savedCard.setImageUrl(r2StorageService.generatePresignedGetUrl(savedCard.getImageUrl()));
        return ResponseEntity.ok(ApiResponse.success(savedCard));
    }

    @PostMapping("/idcard/{userId}/reject")
    public ResponseEntity<ApiResponse<IDCard>> rejectIDCard(@PathVariable String userId, @RequestBody Map<String, String> body) {
        IDCard card = repo.findByUserId(userId).orElseThrow(() -> new BusinessException.ResourceNotFoundException("ID Card"));
        card.setStatus(VerificationStatus.REJECTED);
        card.setRejectionReason(body.get("rejectionReason"));
        IDCard savedCard = repo.save(card);
        userRepository.findByUserId(userId).ifPresent(u -> {
            u.setIdVerified(false);
            u.setRejectionCount(u.getRejectionCount() + 1);
            userRepository.save(u);
            eventPublisher.publishEvent(new IDCardStatusChangedEvent(this, u, savedCard));
        });
        savedCard.setImageUrl(r2StorageService.generatePresignedGetUrl(savedCard.getImageUrl()));
        return ResponseEntity.ok(ApiResponse.success(savedCard));
    }

    @PostMapping("/idcard/{userId}/request-reverification")
    public ResponseEntity<ApiResponse<IDCard>> requestReVerification(@PathVariable String userId, @RequestBody Map<String, String> body) {
        IDCard card = repo.findByUserId(userId).orElseThrow(() -> new BusinessException.ResourceNotFoundException("ID Card"));
        String reason = body.get("reason");
        card.setRejectionReason(reason);
        IDCard savedCard = repo.save(card);
        userRepository.findByUserId(userId).ifPresent(u -> {
            u.setReVerificationRequested(true);
            userRepository.save(u);
            eventPublisher.publishEvent(new IDCardStatusChangedEvent(this, u, savedCard));
        });
        savedCard.setImageUrl(r2StorageService.generatePresignedGetUrl(savedCard.getImageUrl()));
        populateUserDetails(savedCard);
        return ResponseEntity.ok(ApiResponse.success(savedCard));
    }

    @GetMapping("/idcards")
    public ResponseEntity<ApiResponse<List<IDCard>>> getAllIDCards() {
        List<IDCard> cards = repo.findAll();
        cards.forEach(card -> {
            populateUserDetails(card);
            card.setImageUrl(r2StorageService.generatePresignedGetUrl(card.getImageUrl()));
        });
        return ResponseEntity.ok(ApiResponse.success(cards));
    }

    @GetMapping("/idcards/pending")
    public ResponseEntity<ApiResponse<List<IDCard>>> getPending() {
        List<IDCard> cards = repo.findByStatus(VerificationStatus.PENDING);
        cards.forEach(card -> {
            populateUserDetails(card);
            card.setImageUrl(r2StorageService.generatePresignedGetUrl(card.getImageUrl()));
        });
        return ResponseEntity.ok(ApiResponse.success(cards));
    }

    private void populateUserDetails(IDCard card) {
        userRepository.findByUserId(card.getUserId()).ifPresent(u -> {
            boolean needsSave = false;
            if (u.isIdVerified()) {
                if (u.getCaxId() == null || u.getCaxId().isEmpty()) {
                    u.setCaxId(generateUniqueCaxId());
                    needsSave = true;
                }
                if (u.getIdCardExpiresAt() == null) {
                    u.setIdCardExpiresAt(Instant.now().plus(180, java.time.temporal.ChronoUnit.DAYS));
                    needsSave = true;
                }
            }
            if (needsSave) {
                userRepository.save(u);
            }
            if (u.getCollegeDetails() != null) {
                String uCollegeId = u.getCollegeDetails().getCollegeId();
                String uCollegeName = u.getCollegeDetails().getCollegeName();
                if (card.getCollegeId() == null || !uCollegeId.equals(card.getCollegeId()) ||
                    card.getCollegeName() == null || !uCollegeName.equals(card.getCollegeName())) {
                    card.setCollegeId(uCollegeId);
                    card.setCollegeName(uCollegeName);
                    repo.save(card);
                }
            }
            card.setCaxId(u.getCaxId());
            card.setIdCardExpiresAt(u.getIdCardExpiresAt());
            card.setReVerificationRequested(u.isReVerificationRequested());
            card.setRejectionCount(u.getRejectionCount());
        });
    }

    private String generateUniqueCaxId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        java.util.Random rnd = new java.util.Random();
        String caxId;
        do {
            StringBuilder sb = new StringBuilder("CX");
            for (int i = 0; i < 8; i++) {
                sb.append(chars.charAt(rnd.nextInt(chars.length())));
            }
            caxId = sb.toString();
        } while (userRepository.existsByCaxId(caxId));
        return caxId;
    }

    @GetMapping({"/idcards/statistics", "/idcard/statistics"})
    public ResponseEntity<ApiResponse<Map<String, Long>>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "total", repo.count(),
                "pending", repo.countByStatus(VerificationStatus.PENDING),
                "approved", repo.countByStatus(VerificationStatus.APPROVED),
                "rejected", repo.countByStatus(VerificationStatus.REJECTED)
        )));
    }
}
