package com.cax.cax_backend.carousel.model;

import com.cax.cax_backend.common.enums.CarouselEnums.CarouselType;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "carousels")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Carousel {

    @Id
    private String id;

    private String title;
    private String description;
    private String imageUrl;
    private String actionUrl;
    @JsonAlias("actionUrl")
    private String actionLink;
    private CarouselType type;
    private String designType;
    private int displayOrder;

    @Builder.Default
    private boolean isActive = true;

    private String collegeId;
    private String targetAudience;
    private String createdBy;
    private int views;
    private int clicks;

    @Builder.Default
    private Instant createdAt = Instant.now();
    private Instant updatedAt;
    private Instant expiresAt;

    @Builder.Default
    private boolean deleted = false;
    private Instant deletedAt;

    @JsonProperty("actionLink")
    public String getActionLink() {
        return actionLink != null ? actionLink : actionUrl;
    }

    @JsonProperty("priority")
    public int getPriority() {
        return displayOrder;
    }

    @JsonProperty("priority")
    public void setPriority(int priority) {
        this.displayOrder = priority;
    }

    @JsonProperty("visible")
    public boolean isVisible() {
        return isActive;
    }

    @JsonProperty("visible")
    public void setVisible(boolean visible) {
        this.isActive = visible;
    }
}
