package com.cax.cax_backend.event.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventCoordinator {

    @NotBlank(message = "Coordinator name is required")
    @Size(min = 2, max = 60, message = "Coordinator name must be between 2 and 60 characters")
    private String name;

    @NotBlank(message = "Coordinator email is required")
    @Email(message = "Coordinator email must be a valid email address")
    @Size(max = 100, message = "Coordinator email cannot exceed 100 characters")
    private String email;

    @NotBlank(message = "Coordinator phone is required")
    @Pattern(regexp = "^[0-9+\\-\\s]{7,15}$", message = "Coordinator phone must be 7–15 digits")
    private String phone;
}
