package com.cax.cax_backend.idcard.model;

import com.cax.cax_backend.common.enums.CarouselEnums.VerificationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "idCards")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IDCard {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    private String imageUrl;
    private String idCardNumber;
    private String department;
    private String collegeId;
    private String collegeName;

    @Builder.Default
    private VerificationStatus status = VerificationStatus.PENDING;

    private String verificationNotes;
    private String rejectionReason;
    private String verifiedBy;

    // Extracted data
    private Map<String, Object> extractedData;
    private Map<String, Object> manualData;

    @Builder.Default
    private Instant createdAt = Instant.now();
    private Instant updatedAt;
    private Instant verifiedAt;

    @org.springframework.data.annotation.Transient
    private String caxId;

    @org.springframework.data.annotation.Transient
    private Instant idCardExpiresAt;

    @org.springframework.data.annotation.Transient
    private Boolean reVerificationRequested;

    @org.springframework.data.annotation.Transient
    private Integer rejectionCount;

    @org.springframework.data.annotation.Transient
    private String verificationStatus;

    @org.springframework.data.annotation.Transient
    private String documentUrl;

    @com.fasterxml.jackson.annotation.JsonProperty("verificationStatus")
    public String getVerificationStatus() {
        return this.status != null ? this.status.name() : null;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("documentUrl")
    public String getDocumentUrl() {
        return this.imageUrl;
    }
}
