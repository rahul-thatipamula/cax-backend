package com.cax.cax_backend.settings.service;

import com.cax.cax_backend.settings.model.SystemSetting;
import com.cax.cax_backend.settings.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSettingService {

    private final SystemSettingRepository systemSettingRepository;

    public SystemSetting getSystemSetting() {
        return systemSettingRepository.findById("global")
                .orElseGet(() -> {
                    SystemSetting defaultSetting = SystemSetting.builder()
                            .id("global")
                            .onlyAllowCollegeEmails(false)
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();
                    return systemSettingRepository.save(defaultSetting);
                });
    }

    public SystemSetting updateOnlyAllowCollegeEmails(boolean onlyAllow) {
        SystemSetting setting = getSystemSetting();
        setting.setOnlyAllowCollegeEmails(onlyAllow);
        setting.setUpdatedAt(Instant.now());
        SystemSetting saved = systemSettingRepository.save(setting);
        log.info("System setting 'onlyAllowCollegeEmails' updated to {}", onlyAllow);
        return saved;
    }
}
