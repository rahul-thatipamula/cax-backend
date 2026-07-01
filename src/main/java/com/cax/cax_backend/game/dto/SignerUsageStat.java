package com.cax.cax_backend.game.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignerUsageStat {
    private String caxId;
    private String name;
    private long count;
    private Integer maxAllowed; // null = unlimited
}
