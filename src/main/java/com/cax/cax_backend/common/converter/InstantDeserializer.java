package com.cax.cax_backend.common.converter;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

/**
 * Custom Jackson deserializer for Instant that handles various ISO 8601 formats
 * including timestamps without 'Z' suffix
 */
public class InstantDeserializer extends JsonDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
        
        String value = p.getValueAsString();
        
        if (value == null || value.isEmpty()) {
            return null;
        }
        
        try {
            // If it doesn't end with Z, add it
            if (!value.endsWith("Z")) {
                value = value + "Z";
            }
            
            // Parse using DateTimeFormatter which handles ISO 8601 with Z suffix
            return Instant.from(DateTimeFormatter.ISO_INSTANT.parse(value));
        } catch (Exception e) {
            throw new IOException("Failed to deserialize Instant: " + e.getMessage(), e);
        }
    }
}
