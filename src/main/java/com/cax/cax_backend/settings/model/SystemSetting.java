package com.cax.cax_backend.settings.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "system_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SystemSetting {

    @Id
    @Builder.Default
    private String id = "global";



    @Builder.Default
    private boolean playStoreTestingMode = false;

    @Builder.Default
    private boolean razorpayEnabled = true;

    @Builder.Default
    private String latestVersion = "1.0.0";

    @Builder.Default
    private String minRequiredVersion = "1.0.0";

    @Builder.Default
    private int latestBuildNumber = 1;

    @Builder.Default
    private int minRequiredBuildNumber = 1;

    @Builder.Default
    private String updateMessage = "A new version of CAX is available. Please update to continue.";

    @Builder.Default
    private String storeUrl = "";

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();
}
