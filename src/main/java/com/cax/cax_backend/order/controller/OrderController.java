package com.cax.cax_backend.order.controller;

import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.common.enums.OrderEnums.OrderStatus;
import com.cax.cax_backend.order.model.Order;
import com.cax.cax_backend.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.time.Instant;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<Order>> createOrder(Authentication auth, @RequestBody Map<String, Object> body) {
        String productId = (String) body.get("productId");
        int quantity = body.containsKey("quantity") ? ((Number) body.get("quantity")).intValue() : 1;
        return ResponseEntity.ok(ApiResponse.created("Order created", orderService.createOrder((String) auth.getPrincipal(), productId, quantity)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Order>>> getUserOrders(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getUserOrders((String) auth.getPrincipal())));
    }

    @GetMapping("/buyer")
    public ResponseEntity<ApiResponse<List<Order>>> getBuyerOrders(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getBuyerOrders((String) auth.getPrincipal())));
    }

    @GetMapping("/seller")
    public ResponseEntity<ApiResponse<List<Order>>> getSellerOrders(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getSellerOrders((String) auth.getPrincipal())));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<Order>> getOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrder(orderId)));
    }

    @PostMapping("/{orderId}/accept")
    public ResponseEntity<ApiResponse<Order>> acceptOrder(Authentication auth, @PathVariable String orderId, @RequestBody Map<String, Object> body) {
        String pickupLocation = (String) body.get("pickupLocation");
        Instant pickupDate = body.containsKey("pickupDate") && body.get("pickupDate") != null 
                ? Instant.parse((String) body.get("pickupDate")) 
                : null;
        return ResponseEntity.ok(ApiResponse.success(orderService.acceptOrder(orderId, (String) auth.getPrincipal(), pickupLocation, pickupDate)));
    }

    @PostMapping("/{orderId}/reject")
    public ResponseEntity<ApiResponse<Order>> rejectOrder(Authentication auth, @PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.updateOrderStatus(orderId, (String) auth.getPrincipal(), OrderStatus.REJECTED)));
    }

    @PostMapping("/{orderId}/deliver")
    public ResponseEntity<ApiResponse<Order>> deliverOrder(Authentication auth, @PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.updateOrderStatus(orderId, (String) auth.getPrincipal(), OrderStatus.DELIVERED)));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<Order>> cancelOrder(Authentication auth, @PathVariable String orderId, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success(orderService.cancelOrder(orderId, (String) auth.getPrincipal(), body.get("reason"))));
    }

    @PostMapping("/{orderId}/request-call")
    public ResponseEntity<ApiResponse<Void>> requestCall(Authentication auth, @PathVariable String orderId) {
        orderService.requestCall(orderId, (String) auth.getPrincipal());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{orderId}/send-update")
    public ResponseEntity<ApiResponse<Void>> sendUpdate(Authentication auth, @PathVariable String orderId, @RequestBody Map<String, String> body) {
        orderService.sendUpdate(orderId, (String) auth.getPrincipal(), body.get("message"));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{orderId}/share-email")
    public ResponseEntity<ApiResponse<Boolean>> shareEmail(Authentication auth, @PathVariable String orderId, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success(orderService.shareEmail(orderId, (String) auth.getPrincipal(), body.get("email"))));
    }

    @PostMapping("/{orderId}/share-phone")
    public ResponseEntity<ApiResponse<Boolean>> sharePhone(Authentication auth, @PathVariable String orderId, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success(orderService.sharePhone(orderId, (String) auth.getPrincipal(), body.get("phone"))));
    }

    @PostMapping("/{orderId}/skip-phone-share")
    public ResponseEntity<ApiResponse<Boolean>> skipPhoneShare(Authentication auth, @PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.skipPhoneShare(orderId, (String) auth.getPrincipal())));
    }

    @GetMapping("/{orderId}/contact-cooldowns")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getContactCooldowns(Authentication auth, @PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getContactCooldowns(orderId, (String) auth.getPrincipal())));
    }
}
