package com.cax.cax_backend.settings.dto;

import lombok.Data;

@Data
public class VersionSettingsRequest {
    private String latestVersion;
    private String minRequiredVersion;
    private int latestBuildNumber;
    private int minRequiredBuildNumber;
    private String updateMessage;
    private String storeUrl;
}
