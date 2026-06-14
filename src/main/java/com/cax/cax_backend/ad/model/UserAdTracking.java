package com.cax.cax_backend.ad.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "user_ad_tracking")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@CompoundIndexes({
    @CompoundIndex(name = "user_ad_idx", def = "{'userId': 1, 'adId': 1}", unique = true)
})
public class UserAdTracking {

    @Id
    private String id;

    private String userId;
    private String adId;

    @Builder.Default
    private int viewCount = 0;

    @Builder.Default
    private int clickCount = 0;

    @Builder.Default
    private Instant lastViewedAt = Instant.now();

    private Instant lastClickedAt;
}
