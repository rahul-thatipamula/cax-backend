package com.cax.cax_backend.order.repository;

import com.cax.cax_backend.common.enums.OrderEnums.OrderStatus;
import com.cax.cax_backend.order.model.Order;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface OrderRepository extends MongoRepository<Order, String> {
    List<Order> findByBuyerIdOrderByCreatedAtDesc(String buyerId);
    List<Order> findBySellerIdOrderByCreatedAtDesc(String sellerId);
    List<Order> findByBuyerIdOrSellerIdOrderByCreatedAtDesc(String buyerId, String sellerId);
    List<Order> findByStatus(OrderStatus status);
    long countByStatus(OrderStatus status);
}
