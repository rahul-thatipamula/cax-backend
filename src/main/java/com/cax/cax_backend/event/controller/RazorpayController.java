package com.cax.cax_backend.event.controller;

import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.common.exception.AuthException;
import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.event.model.EventParticipant;
import com.cax.cax_backend.event.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/events/{eventId}/razorpay")
@RequiredArgsConstructor
public class RazorpayController {

    private final EventService eventService;

    /**
     * Creates a Razorpay order for the authenticated user and the given event.
     * Returns: { orderId, amount (paise), currency, keyId, eventName, eventFee }
     */
    @PostMapping("/create-order")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createOrder(
            Authentication auth,
            @PathVariable String eventId) {

        String userId = requireUserId(auth);
        Map<String, Object> orderDetails = eventService.initRazorpayPayment(userId, eventId);
        return ResponseEntity.ok(ApiResponse.success("Razorpay order created", orderDetails));
    }

    /**
     * Verifies the Razorpay payment signature.
     * If valid, the participant status is set to VERIFIED and a ticket code is issued.
     * Body: { razorpayPaymentId, razorpayOrderId, razorpaySignature }
     */
    @PostMapping("/verify-payment")
    public ResponseEntity<ApiResponse<EventParticipant>> verifyPayment(
            Authentication auth,
            @PathVariable String eventId,
            @RequestBody Map<String, String> body) {

        String userId = requireUserId(auth);

        String paymentId = body.get("razorpayPaymentId");
        String orderId   = body.get("razorpayOrderId");
        String signature = body.get("razorpaySignature");

        if (paymentId == null || orderId == null || signature == null ||
                paymentId.isBlank() || orderId.isBlank() || signature.isBlank()) {
            throw new BusinessException.BadRequestException(
                    "razorpayPaymentId, razorpayOrderId, and razorpaySignature are all required.");
        }

        EventParticipant participant = eventService.confirmRazorpayPayment(
                userId, eventId, paymentId, orderId, signature);
        return ResponseEntity.ok(ApiResponse.success("Payment verified successfully", participant));
    }

    private String requireUserId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new AuthException.UnauthorizedException("User is not authenticated");
        }
        return (String) auth.getPrincipal();
    }
}
