package com.cax.cax_backend.common.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdminActivityFileLoggerTest {

    private static final Path LOG_FILE = Paths.get("logs/admin-activity.log");
    private AdminActivityFileLogger logger;
    private List<String> originalLines;

    @BeforeEach
    void setUp() throws IOException {
        logger = new AdminActivityFileLogger();
        logger.init();
        if (Files.exists(LOG_FILE)) {
            originalLines = Files.readAllLines(LOG_FILE, StandardCharsets.UTF_8);
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        if (originalLines != null) {
            Files.write(LOG_FILE, originalLines, StandardCharsets.UTF_8);
        } else {
            Files.deleteIfExists(LOG_FILE);
        }
    }

    @Test
    void testLogOutput() throws IOException {
        String adminId = "test_admin";
        String action = "Block User";
        String resourceId = "test_user_123";
        String details = "Reason: spamming";
        String status = "SUCCESS";

        logger.log(adminId, action, resourceId, details, status);

        assertTrue(Files.exists(LOG_FILE));
        List<String> lines = Files.readAllLines(LOG_FILE, StandardCharsets.UTF_8);
        assertFalse(lines.isEmpty());
        String lastLine = lines.get(lines.size() - 1);

        assertTrue(lastLine.contains("ADMIN: test_admin"));
        assertTrue(lastLine.contains("ACTION: Block User"));
        assertTrue(lastLine.contains("RESOURCE: test_user_123"));
        assertTrue(lastLine.contains("DETAILS: Reason: spamming"));
        assertTrue(lastLine.contains("STATUS: SUCCESS"));
    }
}
