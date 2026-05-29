package com.cax.cax_backend.product.service;

import com.cax.cax_backend.common.enums.ProductEnums.*;
import com.cax.cax_backend.common.exception.AuthException;
import com.cax.cax_backend.common.exception.BusinessException;
import com.cax.cax_backend.product.model.Product;
import com.cax.cax_backend.product.repository.ProductRepository;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public Product createProduct(String userId, Product data) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(AuthException.UserNotFoundException::new);
        if (user.getCollegeDetails() == null) {
            throw new BusinessException.BadRequestException("User not in college");
        }
        data.setUserId(userId);
        data.setCollegeId(user.getCollegeDetails().getCollegeId());
        data.setCollegeName(user.getCollegeDetails().getCollegeName());
        data.setStatus(ProductStatus.PENDING_REVIEW);
        data.setModerationStatus(ModerationStatus.PENDING);
        data.setCreatedAt(Instant.now());
        data.setUpdatedAt(Instant.now());
        Product saved = productRepository.save(data);
        log.info("Product created: {} by user: {}", saved.getId(), userId);
        return saved;
    }

    public Product getProduct(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Product", productId));
        product.setViews(product.getViews() + 1);
        productRepository.save(product);
        return product;
    }

    public Product updateProduct(String userId, String productId, Map<String, Object> updates) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Product", productId));
        if (!product.getUserId().equals(userId)) {
            throw new BusinessException.BadRequestException("Unauthorized to update this product");
        }
        if (updates.containsKey("name")) product.setName((String) updates.get("name"));
        if (updates.containsKey("description")) product.setDescription((String) updates.get("description"));
        if (updates.containsKey("price")) product.setPrice(((Number) updates.get("price")).doubleValue());
        if (updates.containsKey("stock")) product.setStock(((Number) updates.get("stock")).intValue());
        if (updates.containsKey("images")) product.setImages((List<String>) updates.get("images"));
        if (updates.containsKey("tags")) product.setTags((List<String>) updates.get("tags"));

        if (product.getModerationStatus() == ModerationStatus.REJECTED) {
            product.setModerationStatus(ModerationStatus.PENDING);
            product.setStatus(ProductStatus.PENDING_REVIEW);
        }
        product.setUpdatedAt(Instant.now());
        return productRepository.save(product);
    }

    public void deleteProduct(String userId, String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Product", productId));
        if (!product.getUserId().equals(userId)) {
            throw new BusinessException.BadRequestException("Unauthorized to delete this product");
        }
        productRepository.deleteById(productId);
        log.info("Product deleted: {}", productId);
    }

    public List<Product> getProductsByCollege(String collegeId) {
        return productRepository.findByCollegeId(collegeId);
    }

    public List<Product> getUserProducts(String userId) {
        return productRepository.findByUserId(userId);
    }

    public List<Product> getAllProductsForUser(String userId) {
        User user = userRepository.findByUserId(userId).orElse(null);
        if (user == null || user.getCollegeDetails() == null) return List.of();
        return productRepository.findByCollegeId(user.getCollegeDetails().getCollegeId());
    }

    public Page<Product> getProductsWithPagination(String userId, int page, int size) {
        User user = userRepository.findByUserId(userId).orElse(null);
        if (user == null || user.getCollegeDetails() == null) return Page.empty();
        return productRepository.findByCollegeId(user.getCollegeDetails().getCollegeId(), PageRequest.of(page, size));
    }

    // Admin methods
    public Product approveProduct(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Product", productId));
        product.setModerationStatus(ModerationStatus.APPROVED);
        product.setStatus(ProductStatus.ACTIVE);
        product.setModerationReason(null);
        product.setUpdatedAt(Instant.now());
        log.info("Product approved: {}", productId);
        return productRepository.save(product);
    }

    public Product rejectProduct(String productId, String reason) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException.ResourceNotFoundException("Product", productId));
        product.setModerationStatus(ModerationStatus.REJECTED);
        product.setStatus(ProductStatus.BANNED);
        product.setModerationReason(reason);
        product.setUpdatedAt(Instant.now());
        log.info("Product rejected: {}", productId);
        return productRepository.save(product);
    }

    public Map<String, Long> getProductStats() {
        return Map.of(
                "total", productRepository.count(),
                "pending", productRepository.countByModerationStatus(ModerationStatus.PENDING),
                "approved", productRepository.countByModerationStatus(ModerationStatus.APPROVED),
                "rejected", productRepository.countByModerationStatus(ModerationStatus.REJECTED)
        );
    }

    public List<Product> getAllProductsAdmin() {
        return productRepository.findAll();
    }

    public List<Product> getProductsByModerationStatus(ModerationStatus status) {
        return productRepository.findByModerationStatus(status);
    }
}
