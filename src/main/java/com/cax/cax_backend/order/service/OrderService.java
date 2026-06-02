package com.cax.cax_backend.order.service;

import com.cax.cax_backend.common.enums.OrderEnums.*;
import com.cax.cax_backend.common.exception.AuthException;
import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.order.model.Order;
import com.cax.cax_backend.order.repository.OrderRepository;
import com.cax.cax_backend.product.model.Product;
import com.cax.cax_backend.product.repository.ProductRepository;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public Order createOrder(String buyerId, String productId, int quantity) {
        User buyer = userRepository.findByUserId(buyerId)
                .orElseThrow(AuthException.UserNotFoundException::new);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Product", productId));
        User seller = userRepository.findByUserId(product.getUserId())
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Seller"));

        Order order = Order.builder()
                .buyerId(buyerId)
                .sellerId(product.getUserId())
                .productId(productId)
                .collegeId(product.getCollegeId())
                .quantity(quantity)
                .unitPrice(product.getPrice())
                .totalPrice(product.getPrice() * quantity)
                .productSnapshot(Order.ProductSnapshot.builder()
                        .productId(product.getId())
                        .name(product.getName())
                        .price(product.getPrice())
                        .image(product.getImages() != null && !product.getImages().isEmpty() ? product.getImages().get(0) : null)
                        .category(product.getCategory() != null ? product.getCategory().getValue() : null)
                        .build())
                .buyerSnapshot(Order.UserSnapshot.builder()
                        .userId(buyer.getUserId()).name(buyer.getName())
                        .picture(buyer.getPicture())
                        .collegeName(buyer.getCollegeDetails() != null ? buyer.getCollegeDetails().getCollegeName() : null)
                        .build())
                .sellerSnapshot(Order.UserSnapshot.builder()
                        .userId(seller.getUserId()).name(seller.getName())
                        .picture(seller.getPicture())
                        .collegeName(seller.getCollegeDetails() != null ? seller.getCollegeDetails().getCollegeName() : null)
                        .build())
                .status(OrderStatus.PENDING)
                .build();

        Order saved = orderRepository.save(order);
        log.info("Order created: {} by buyer: {}", saved.getId(), buyerId);
        return saved;
    }

    public List<Order> getUserOrders(String userId) {
        return orderRepository.findByBuyerIdOrSellerIdOrderByCreatedAtDesc(userId, userId);
    }

    public List<Order> getBuyerOrders(String buyerId) {
        return orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId);
    }

    public List<Order> getSellerOrders(String sellerId) {
        return orderRepository.findBySellerIdOrderByCreatedAtDesc(sellerId);
    }

    public Order getOrder(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Order", orderId));
    }

    public Order updateOrderStatus(String orderId, String userId, OrderStatus newStatus) {
        Order order = getOrder(orderId);
        order.setStatus(newStatus);
        order.setUpdatedAt(Instant.now());
        if (newStatus == OrderStatus.COMPLETED || newStatus == OrderStatus.DELIVERED) {
            order.setCompletedAt(Instant.now());
        }
        log.info("Order {} status updated to {}", orderId, newStatus);
        return orderRepository.save(order);
    }

    public Order acceptOrder(String orderId, String userId, String pickupLocation, Instant pickupDate) {
        Order order = getOrder(orderId);
        order.setStatus(OrderStatus.ACCEPTED);
        order.setPickup(Order.PickupInfo.builder()
                .location(pickupLocation)
                .scheduledAt(pickupDate)
                .build());
        order.setUpdatedAt(Instant.now());
        log.info("Order {} accepted with pickup details at {}", orderId, pickupLocation);
        return orderRepository.save(order);
    }

    public Order cancelOrder(String orderId, String userId, String reason) {
        Order order = getOrder(orderId);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(reason);
        order.setCancelledBy(userId);
        order.setUpdatedAt(Instant.now());
        log.info("Order {} cancelled by {}", orderId, userId);
        return orderRepository.save(order);
    }

    public void requestCall(String orderId, String userId) {
        Order order = getOrder(orderId);
        order.setCallRequestPending(true);
        order.setCallNextAvailableAt(Instant.now().plusSeconds(60)); // 60 seconds cooldown
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
    }

    public void sendUpdate(String orderId, String userId, String message) {
        Order order = getOrder(orderId);
        order.setUpdateNextAvailableAt(Instant.now().plusSeconds(60)); // 60 seconds cooldown
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
    }

    public boolean shareEmail(String orderId, String userId, String email) {
        Order order = getOrder(orderId);
        if (userId.equals(order.getBuyerId())) {
            order.setBuyerSharedEmail(email);
        } else {
            order.setSellerSharedEmail(email);
        }
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        return true;
    }

    public boolean sharePhone(String orderId, String userId, String phone) {
        Order order = getOrder(orderId);
        if (userId.equals(order.getBuyerId())) {
            order.setBuyerSharedPhone(phone);
        } else {
            order.setSellerSharedPhone(phone);
        }
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        return true;
    }

    public boolean skipPhoneShare(String orderId, String userId) {
        Order order = getOrder(orderId);
        if (userId.equals(order.getBuyerId())) {
            order.setBuyerSkippedPhoneShare(true);
        } else {
            order.setSellerSkippedPhoneShare(true);
        }
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        return true;
    }

    public Map<String, Object> getContactCooldowns(String orderId, String userId) {
        Order order = getOrder(orderId);
        long callCooldownMs = 0;
        if (order.getCallNextAvailableAt() != null) {
            callCooldownMs = Math.max(0, order.getCallNextAvailableAt().toEpochMilli() - Instant.now().toEpochMilli());
        }
        long updateCooldownMs = 0;
        if (order.getUpdateNextAvailableAt() != null) {
            updateCooldownMs = Math.max(0, order.getUpdateNextAvailableAt().toEpochMilli() - Instant.now().toEpochMilli());
        }

        Map<String, Object> cooldowns = new HashMap<>();
        cooldowns.put("callCooldownMs", callCooldownMs > 0 ? callCooldownMs : null);
        cooldowns.put("callNextAvailableAt", order.getCallNextAvailableAt() != null ? order.getCallNextAvailableAt().toString() : null);
        cooldowns.put("updateCooldownMs", updateCooldownMs > 0 ? updateCooldownMs : null);
        cooldowns.put("updateNextAvailableAt", order.getUpdateNextAvailableAt() != null ? order.getUpdateNextAvailableAt().toString() : null);
        cooldowns.put("buyerSharedEmail", order.getBuyerSharedEmail());
        cooldowns.put("sellerSharedEmail", order.getSellerSharedEmail());
        cooldowns.put("buyerSharedPhone", order.getBuyerSharedPhone());
        cooldowns.put("sellerSharedPhone", order.getSellerSharedPhone());
        cooldowns.put("callRequestPending", order.isCallRequestPending());
        cooldowns.put("buyerSkippedPhoneShare", order.isBuyerSkippedPhoneShare());
        cooldowns.put("sellerSkippedPhoneShare", order.isSellerSkippedPhoneShare());
        return cooldowns;
    }
}
