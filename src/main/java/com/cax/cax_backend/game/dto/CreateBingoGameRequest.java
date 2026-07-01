package com.cax.cax_backend.game.dto;

import lombok.Data;
import java.util.List;

@Data
public class CreateBingoGameRequest {
    private String title;
    private List<String> prompts;
    private String organizationId; // required — game is always scoped to an organization
    private Integer maxSignerUsesPerGame; // optional — null means unlimited
}
