package com.cax.cax_backend.common.config;

import com.cax.cax_backend.common.util.EncryptionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class EncryptionKeyInitializer {

    @Value("${app.encryption.key:CaxGroupChatSecr}")
    private String key;

    @PostConstruct
    public void init() {
        EncryptionUtils.init(key);
    }
}
