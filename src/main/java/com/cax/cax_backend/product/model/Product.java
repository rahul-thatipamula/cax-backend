package com.cax.cax_backend.product.model;

import com.cax.cax_backend.common.enums.ProductEnums.*;
import com.cax.cax_backend.common.converter.InstantDeserializer;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Product {

    @Id
    private String id;

    private String name;
    private String description;
    private double price;
    private double originalPrice;
    private int stock;

    private ProductCategory category;
    private ProductCondition condition;

    @Builder.Default
    private ProductStatus status = ProductStatus.PENDING_REVIEW;

    @Builder.Default
    private ModerationStatus moderationStatus = ModerationStatus.PENDING;

    private String moderationReason;

    @Indexed
    private String userId;

    @Indexed
    private String collegeId;

    private String collegeName;

    private List<String> images;
    private List<String> tags;

    @Builder.Default
    private boolean featured = false;
    @Builder.Default
    private boolean trending = false;
    @Builder.Default
    private boolean hidden = false;
    @Builder.Default
    private boolean forSale = true;
    private Boolean isForSale;

    @Builder.Default
    private int version = 1;
    @Builder.Default
    private int views = 0;
    @Builder.Default
    private int likes = 0;
    @Builder.Default
    private int purchases = 0;
    @Builder.Default
    private int wishlistCount = 0;
    @Builder.Default
    private int ratingCount = 0;
    @Builder.Default
    private double averageRating = 0;
    @Builder.Default
    private double trendingScore = 0;
    @Builder.Default
    private double sellerScore = 0;

    @Builder.Default
    @JsonDeserialize(using = InstantDeserializer.class)
    private Instant createdAt = Instant.now();
    @JsonDeserialize(using = InstantDeserializer.class)
    private Instant updatedAt;
}
