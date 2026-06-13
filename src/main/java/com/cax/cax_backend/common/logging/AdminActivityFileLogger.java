package com.cax.cax_backend.common.logging;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Thread-safe file logger specifically for admin activities.
 * Writes audit entries into logs/admin-activity.log.
 */
@Component
public class AdminActivityFileLogger {

    private static final Path LOG_FILE = Paths.get("logs/admin-activity.log");
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @PostConstruct
    public void init() {
        try {
            if (LOG_FILE.getParent() != null) {
                Files.createDirectories(LOG_FILE.getParent());
            }
        } catch (IOException e) {
            System.err.println("Failed to create logs directory: " + e.getMessage());
        }
    }

    /**
     * Appends a structured log entry into the file.
     */
    public synchronized void log(String adminId, String action, String resourceId, String details, String status) {
        String timestamp = LocalDateTime.now().format(formatter);
        String formattedAdmin = adminId != null ? adminId : "UNKNOWN";
        String formattedResource = (resourceId != null && !resourceId.isBlank()) ? resourceId : "N/A";
        String formattedDetails = (details != null && !details.isBlank()) ? details : "N/A";
        
        String logEntry = String.format("[%s] ADMIN: %s | ACTION: %s | RESOURCE: %s | DETAILS: %s | STATUS: %s\n",
                timestamp, formattedAdmin, action, formattedResource, formattedDetails, status);

        try {
            Files.write(LOG_FILE, logEntry.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Failed to write to admin activity log file: " + e.getMessage());
        }
    }
}
