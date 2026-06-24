package com.cax.cax_backend.event.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventGuest {

    @NotBlank(message = "Guest name is required")
    @Size(min = 2, max = 60, message = "Guest name must be between 2 and 60 characters")
    private String name;

    @NotBlank(message = "Guest designation is required")
    @Size(min = 2, max = 100, message = "Guest designation must be between 2 and 100 characters")
    private String designation;

    private String image; // Cloudflare R2 URL
}
