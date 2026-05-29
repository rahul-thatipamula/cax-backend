package com.cax.cax_backend.user.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SocialLinks {
    private String instagram;
    private String twitter;
    private String linkedin;
    private String github;
    private String whatsapp;
    private String telegram;
    private String website;

    public boolean isEmpty() {
        return (instagram == null || instagram.isEmpty()) &&
                (twitter == null || twitter.isEmpty()) &&
                (linkedin == null || linkedin.isEmpty()) &&
                (github == null || github.isEmpty()) &&
                (whatsapp == null || whatsapp.isEmpty()) &&
                (telegram == null || telegram.isEmpty()) &&
                (website == null || website.isEmpty());
    }
}
