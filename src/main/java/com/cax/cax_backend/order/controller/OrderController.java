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

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<Order>> getOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrder(orderId)));
    }

    @PostMapping("/{orderId}/accept")
    public ResponseEntity<ApiResponse<Order>> acceptOrder(Authentication auth, @PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.updateOrderStatus(orderId, (String) auth.getPrincipal(), OrderStatus.ACCEPTED)));
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
}
