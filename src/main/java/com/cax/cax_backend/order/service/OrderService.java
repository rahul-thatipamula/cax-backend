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

    public Order cancelOrder(String orderId, String userId, String reason) {
        Order order = getOrder(orderId);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(reason);
        order.setCancelledBy(userId);
        order.setUpdatedAt(Instant.now());
        log.info("Order {} cancelled by {}", orderId, userId);
        return orderRepository.save(order);
    }
}
