package com.cax.cax_backend.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletEmbedded {
    @Builder.Default
    private double balance = 0;
    @Builder.Default
    private double totalEarned = 0;
    @Builder.Default
    private double totalSpent = 0;
}
