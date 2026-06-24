package com.cax.cax_backend.thought.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ThoughtImageRequest {
    private String url;
    private String alignment;
    private Double widthRatio;
    private Integer insertAfterLine;
}
