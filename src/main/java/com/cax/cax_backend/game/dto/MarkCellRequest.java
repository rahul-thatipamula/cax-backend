package com.cax.cax_backend.game.dto;

import lombok.Data;

@Data
public class MarkCellRequest {
    private int cellIndex;
    private String signerCaxId;
}
