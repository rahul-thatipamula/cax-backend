package com.cax.cax_backend.club.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "clubs")
public class Club {
    @Id
    private String id;

    @Indexed
    private String name;

    private String logo;
    private String description;
    private String coverPhoto;

    @Indexed
    private String collegeId;

    private String presidentId;
    private String vicePresidentId;

    @Builder.Default
    private boolean isApprovalRequired = false;

    @Builder.Default
    private boolean allowJoining = true;

    @Builder.Default
    private boolean isPaid = false;

    @Builder.Default
    private Double price = 0.0;

    private String upiId;
    private String qrCodeUrl;

    @Builder.Default
    private List<ClubRole> customRoles = List.of(
        new ClubRole("Lead"),
        new ClubRole("Manager"),
        new ClubRole("Secretary"),
        new ClubRole("Treasurer")
    );

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    public List<ClubRole> getCustomRoles() {
        if (customRoles == null || customRoles.isEmpty()) {
            return List.of(
                new ClubRole("Lead"),
                new ClubRole("Manager"),
                new ClubRole("Secretary"),
                new ClubRole("Treasurer")
            );
        }
        return customRoles;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClubRole {
        private String name;
    }
}
