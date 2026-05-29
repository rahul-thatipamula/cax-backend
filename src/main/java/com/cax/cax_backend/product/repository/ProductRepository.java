package com.cax.cax_backend.product.repository;

import com.cax.cax_backend.common.enums.ProductEnums.*;
import com.cax.cax_backend.product.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ProductRepository extends MongoRepository<Product, String> {
    List<Product> findByCollegeIdAndStatusNot(String collegeId, ProductStatus status);
    List<Product> findByCollegeId(String collegeId);
    List<Product> findByCollegeIdAndCategory(String collegeId, ProductCategory category);
    List<Product> findByCollegeIdAndTrendingTrueOrderByTrendingScoreDesc(String collegeId);
    List<Product> findByCollegeIdAndFeaturedTrue(String collegeId);
    List<Product> findByUserId(String userId);
    List<Product> findByCollegeIdAndUserId(String collegeId, String userId);
    Page<Product> findByCollegeId(String collegeId, Pageable pageable);
    long countByModerationStatus(ModerationStatus status);
    List<Product> findByModerationStatus(ModerationStatus status);
}
