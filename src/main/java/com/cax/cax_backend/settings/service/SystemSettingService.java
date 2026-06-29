package com.cax.cax_backend.settings.service;

import com.cax.cax_backend.settings.model.SystemSetting;
import com.cax.cax_backend.settings.repository.SystemSettingRepository;
import com.cax.cax_backend.settings.dto.VersionCount;
import com.cax.cax_backend.settings.dto.VersionSettingsRequest;
import com.cax.cax_backend.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSettingService {

    private final SystemSettingRepository systemSettingRepository;
    private final MongoTemplate mongoTemplate;

    public SystemSetting getSystemSetting() {
        return systemSettingRepository.findById("global")
                .orElseGet(() -> {
                    SystemSetting defaultSetting = SystemSetting.builder()
                            .id("global")
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();
                    return systemSettingRepository.save(defaultSetting);
                });
    }


    public boolean isPlayStoreTestingEnabled() {
        return getSystemSetting().isPlayStoreTestingMode();
    }

    public boolean isRazorpayEnabled() {
        return getSystemSetting().isRazorpayEnabled();
    }

    public SystemSetting setPlayStoreTesting(boolean enabled) {
        SystemSetting setting = getSystemSetting();
        setting.setPlayStoreTestingMode(enabled);
        setting.setUpdatedAt(Instant.now());
        SystemSetting saved = systemSettingRepository.save(setting);
        log.info("Play Store testing mode set to: {}", enabled);
        return saved;
    }

    public SystemSetting setRazorpayEnabled(boolean enabled) {
        SystemSetting setting = getSystemSetting();
        setting.setRazorpayEnabled(enabled);
        setting.setUpdatedAt(Instant.now());
        SystemSetting saved = systemSettingRepository.save(setting);
        log.info("Razorpay enabled status set to: {}", enabled);
        return saved;
    }

    public SystemSetting updateVersionSettings(VersionSettingsRequest request) {
        SystemSetting setting = getSystemSetting();
        setting.setLatestVersion(request.getLatestVersion());
        setting.setMinRequiredVersion(request.getMinRequiredVersion());
        setting.setLatestBuildNumber(request.getLatestBuildNumber());
        setting.setMinRequiredBuildNumber(request.getMinRequiredBuildNumber());
        setting.setUpdateMessage(request.getUpdateMessage());
        setting.setStoreUrl(request.getStoreUrl());
        setting.setUpdatedAt(Instant.now());
        SystemSetting saved = systemSettingRepository.save(setting);
        log.info("System version settings updated: latest={}, min={}", request.getLatestVersion(), request.getMinRequiredVersion());
        return saved;
    }

    public Map<String, Long> getVersionStats() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.group("appVersion").count().as("count"),
                Aggregation.project("count").and("_id").as("version")
        );
        AggregationResults<VersionCount> results = mongoTemplate.aggregate(aggregation, User.class, VersionCount.class);

        Map<String, Long> stats = new HashMap<>();
        for (VersionCount vc : results.getMappedResults()) {
            String ver = vc.getVersion();
            if (ver == null || ver.isBlank()) {
                ver = "Unknown / Legacy";
            }
            stats.put(ver, vc.getCount());
        }
        return stats;
    }
}
