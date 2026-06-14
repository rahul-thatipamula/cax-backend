package com.cax.cax_backend.ad.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAdAnalyticsDto {
    private String userId;
    private String userName;
    private String userEmail;
    private int viewCount;
    private int clickCount;
    private Instant lastViewedAt;
    private Instant lastClickedAt;
}
