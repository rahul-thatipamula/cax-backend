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
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/users") @RequiredArgsConstructor
public class IDCardController {
    private final IDCardRepository repo;
    private final R2StorageService r2StorageService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @PostMapping(value = "/idcard/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<IDCard>> uploadIDCard(
            Authentication auth,
            @RequestParam("file") MultipartFile file,
            @RequestParam("idCardNumber") String idCardNumber,
            @RequestParam(value = "department", required = false) String department) throws java.io.IOException {

        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();

        if (file.isEmpty()) {
            throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("File is empty");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("File size exceeds 5MB limit");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("Invalid filename");
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        
        boolean isValidExtension = extension.equals(".jpg") || extension.equals(".jpeg") || extension.equals(".png") || extension.equals(".pdf");
        
        String contentType = file.getContentType();
        boolean isValidMimeType = false;
        if (contentType == null || contentType.equalsIgnoreCase("application/octet-stream")) {
            isValidMimeType = true;
        } else {
            String mime = contentType.toLowerCase();
            isValidMimeType = mime.equals("image/jpeg") || mime.equals("image/jpg") || mime.equals("image/png") || mime.equals("application/pdf");
        }

        if (!isValidExtension || !isValidMimeType) {
            throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("Invalid file type. Only JPG, PNG, and PDF are allowed");
        }

        String folder = "id-cards/" + userId;
        String r2Url = r2StorageService.uploadFile(file, folder);

        IDCard card = repo.findByUserId(userId).orElse(new IDCard());
        card.setUserId(userId);
        card.setImageUrl(r2Url);
        card.setIdCardNumber(idCardNumber);
        card.setDepartment(department);
        card.setStatus(VerificationStatus.PENDING);
        card.setCreatedAt(card.getCreatedAt() != null ? card.getCreatedAt() : Instant.now());
        card.setUpdatedAt(Instant.now());
        card.setRejectionReason(null);
        card.setVerificationNotes(null);

        userRepository.findByUserId(userId).ifPresent(u -> {
            if (u.getCollegeDetails() != null) {
                card.setCollegeId(u.getCollegeDetails().getCollegeId());
                card.setCollegeName(u.getCollegeDetails().getCollegeName());
            }
        });

        IDCard savedCard = repo.save(card);
        // Also update the User model
        userRepository.findByUserId(userId).ifPresent(u -> {
            u.setIdVerified(false);
            u.setIdCardImagePath(r2Url);
            userRepository.save(u);
        });
        return ResponseEntity.ok(ApiResponse.success("ID card uploaded successfully", savedCard));
    }

    @GetMapping("/idcard")
    public ResponseEntity<ApiResponse<IDCard>> getIDCard(Authentication auth) {
        IDCard card = repo.findByUserId((String) auth.getPrincipal()).orElse(null);
        if (card != null) {
            populateCollegeDetails(card);
        }
        return ResponseEntity.ok(ApiResponse.success(card));
    }

    @PostMapping("/idcard")
    public ResponseEntity<ApiResponse<IDCard>> saveIDCard(Authentication auth, @RequestBody IDCard body) {
        String userId = (String) auth.getPrincipal();
        body.setUserId(userId);
        body.setStatus(VerificationStatus.PENDING);
        body.setCreatedAt(Instant.now());
        userRepository.findByUserId(userId).ifPresent(u -> {
            if (u.getCollegeDetails() != null) {
                body.setCollegeId(u.getCollegeDetails().getCollegeId());
                body.setCollegeName(u.getCollegeDetails().getCollegeName());
            }
        });
        return ResponseEntity.ok(ApiResponse.created("ID card saved", repo.save(body)));
    }

    @PutMapping("/idcard")
    public ResponseEntity<ApiResponse<IDCard>> editIDCard(Authentication auth, @RequestBody Map<String, Object> body) {
        IDCard card = repo.findByUserId((String) auth.getPrincipal())
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("ID Card"));
        if (body.containsKey("imageUrl")) card.setImageUrl((String) body.get("imageUrl"));
        card.setUpdatedAt(Instant.now());
        return ResponseEntity.ok(ApiResponse.success(repo.save(card)));
    }

    @DeleteMapping("/idcard")
    public ResponseEntity<ApiResponse<Void>> deleteIDCard(Authentication auth) {
        String userId = (String) auth.getPrincipal();
        repo.findByUserId(userId).ifPresent(c -> repo.delete(c));
        userRepository.findByUserId(userId).ifPresent(u -> {
            u.setIdVerified(false);
            u.setIdCardImagePath(null);
            userRepository.save(u);
        });
        return ResponseEntity.ok(ApiResponse.success("ID card deleted"));
    }

    @PostMapping("/idcard/{userId}/verify")
    public ResponseEntity<ApiResponse<IDCard>> verifyIDCard(@PathVariable String userId, @RequestBody Map<String, String> body) {
        IDCard card = repo.findByUserId(userId).orElseThrow(() -> new BusinessException.ResourceNotFoundException("ID Card"));
        card.setStatus(VerificationStatus.APPROVED);
        card.setVerificationNotes(body.get("verificationNotes"));
        card.setVerifiedAt(Instant.now());
        IDCard savedCard = repo.save(card);
        userRepository.findByUserId(userId).ifPresent(u -> {
            u.setIdVerified(true);
            userRepository.save(u);
            eventPublisher.publishEvent(new IDCardStatusChangedEvent(this, u, savedCard));
        });
        return ResponseEntity.ok(ApiResponse.success(savedCard));
    }

    @PostMapping("/idcard/{userId}/verify-with-data")
    public ResponseEntity<ApiResponse<IDCard>> verifyWithData(@PathVariable String userId, @RequestBody Map<String, Object> body) {
        IDCard card = repo.findByUserId(userId).orElseThrow(() -> new BusinessException.ResourceNotFoundException("ID Card"));
        card.setStatus(VerificationStatus.APPROVED);
        card.setManualData(body);
        card.setVerifiedAt(Instant.now());
        IDCard savedCard = repo.save(card);
        userRepository.findByUserId(userId).ifPresent(u -> {
            u.setIdVerified(true);
            userRepository.save(u);
            eventPublisher.publishEvent(new IDCardStatusChangedEvent(this, u, savedCard));
        });
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
            userRepository.save(u);
            eventPublisher.publishEvent(new IDCardStatusChangedEvent(this, u, savedCard));
        });
        return ResponseEntity.ok(ApiResponse.success(savedCard));
    }

    @GetMapping("/idcards")
    public ResponseEntity<ApiResponse<List<IDCard>>> getAllIDCards() {
        List<IDCard> cards = repo.findAll();
        cards.forEach(this::populateCollegeDetails);
        return ResponseEntity.ok(ApiResponse.success(cards));
    }

    @GetMapping("/idcards/pending")
    public ResponseEntity<ApiResponse<List<IDCard>>> getPending() {
        List<IDCard> cards = repo.findByStatus(VerificationStatus.PENDING);
        cards.forEach(this::populateCollegeDetails);
        return ResponseEntity.ok(ApiResponse.success(cards));
    }

    private void populateCollegeDetails(IDCard card) {
        userRepository.findByUserId(card.getUserId()).ifPresent(u -> {
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
        });
    }

    @GetMapping("/idcards/statistics")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "total", repo.count(),
                "pending", repo.countByStatus(VerificationStatus.PENDING),
                "approved", repo.countByStatus(VerificationStatus.APPROVED),
                "rejected", repo.countByStatus(VerificationStatus.REJECTED)
        )));
    }
}
