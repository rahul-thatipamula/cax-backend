package com.cax.cax_backend.organization.model;

import com.cax.cax_backend.common.enums.OrganizationType;
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
@Document(collection = "organizations")
public class Organization {
    @Id
    private String id;

    @Indexed
    private String name;

    private String logo;
    private String description;
    private String coverPhoto;

    @Indexed
    private String collegeId;

    @Builder.Default
    private OrganizationType type = OrganizationType.CLUB;

    private String presidentId;
    private String vicePresidentId;

    private String createdByUserId;

    @Builder.Default
    private boolean isApprovalRequired = false;

    @Builder.Default
    private boolean allowJoining = false;

    @Builder.Default
    private boolean isPaid = false;

    @Builder.Default
    private Double price = 0.0;

    private String upiId;
    private String qrCodeUrl;

    @Builder.Default
    private List<OrganizationRole> customRoles = List.of(
        new OrganizationRole("Lead"),
        new OrganizationRole("Manager"),
        new OrganizationRole("Secretary"),
        new OrganizationRole("Treasurer")
    );

    @Builder.Default
    private List<String> memories = new java.util.ArrayList<>();

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    public List<OrganizationRole> getCustomRoles() {
        if (customRoles == null || customRoles.isEmpty()) {
            return List.of(
                new OrganizationRole("Lead"),
                new OrganizationRole("Manager"),
                new OrganizationRole("Secretary"),
                new OrganizationRole("Treasurer")
            );
        }
        return customRoles;
    }

    @Data
    @NoArgsConstructor
    public static class OrganizationRole {
        private String name;

        public OrganizationRole(String name) {
            this.name = name;
        }
    }
}
