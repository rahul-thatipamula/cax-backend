package com.cax.cax_backend.common.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.io.InputStream;

@Slf4j
@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                log.info("Initializing Firebase Admin SDK...");
                
                FirebaseOptions options;
                try {
                    // Try initializing with Google Application Default Credentials
                    options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.getApplicationDefault())
                            .build();
                    FirebaseApp.initializeApp(options);
                    log.info("Firebase Admin SDK initialized successfully via Application Default Credentials.");
                } catch (Exception e) {
                    log.warn("Application Default Credentials not found or invalid: {}. Trying service-account.json fallback...", e.getMessage());
                    
                    // Fallback to loading service-account.json from class path
                    InputStream serviceAccount = getClass().getClassLoader().getResourceAsStream("service-account.json");
                    if (serviceAccount == null) {
                        log.warn("service-account.json not found in classpath resources. Push notification functionality will be disabled.");
                        return;
                    }
                    
                    options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                            .build();
                    FirebaseApp.initializeApp(options);
                    log.info("Firebase Admin SDK initialized successfully via service-account.json.");
                }
            } else {
                log.info("Firebase App already initialized.");
            }
        } catch (Exception e) {
            log.error("Fatal error: Failed to initialize Firebase Admin SDK: {}", e.getMessage(), e);
        }
    }
}
