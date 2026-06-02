package com.cax.cax_backend.newsletter.controller;

import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.newsletter.model.NewsletterSubscription;
import com.cax.cax_backend.newsletter.service.NewsletterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/newsletter")
@RequiredArgsConstructor
public class NewsletterController {

    private final NewsletterService newsletterService;

    /**
     * POST /api/newsletter/subscribe - Subscribe a new email to the newsletter.
     */
    @PostMapping("/subscribe")
    public ResponseEntity<ApiResponse<NewsletterSubscription>> subscribe(
            @RequestBody Map<String, String> body) {
        String email = body.get("email");
        NewsletterSubscription subscription = newsletterService.subscribe(email);
        return ResponseEntity.ok(ApiResponse.created("Subscribed successfully", subscription));
    }
}
