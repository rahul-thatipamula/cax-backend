package com.cax.cax_backend.idcard.listener;

import com.cax.cax_backend.common.util.EncryptionUtils;
import com.cax.cax_backend.idcard.model.IDCard;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.mapping.event.AfterConvertCallback;
import org.springframework.data.mongodb.core.mapping.event.BeforeSaveCallback;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class IDCardEncryptionListener implements BeforeSaveCallback<IDCard>, AfterConvertCallback<IDCard> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public IDCard onBeforeSave(IDCard entity, Document document, String collection) {
        log.debug("IDCardEncryptionListener: Encrypting IDCard document before saving");
        try {
            // 1. Encrypt idCardNumber in the DB document
            if (document.containsKey("idCardNumber") && document.get("idCardNumber") != null) {
                String rawNumber = document.getString("idCardNumber");
                document.put("idCardNumber", EncryptionUtils.encrypt(rawNumber));
            }

            // 2. Encrypt extractedData map (serialize as JSON, encrypt, and store as single-key document)
            if (document.containsKey("extractedData") && document.get("extractedData") != null) {
                Object data = document.get("extractedData");
                String json = objectMapper.writeValueAsString(data);
                String encryptedJson = EncryptionUtils.encrypt(json);
                document.put("extractedData", new Document("encrypted", encryptedJson));
            }

            // 3. Encrypt manualData map
            if (document.containsKey("manualData") && document.get("manualData") != null) {
                Object data = document.get("manualData");
                String json = objectMapper.writeValueAsString(data);
                String encryptedJson = EncryptionUtils.encrypt(json);
                document.put("manualData", new Document("encrypted", encryptedJson));
            }
        } catch (Exception e) {
            log.error("IDCardEncryptionListener: Failed to encrypt IDCard", e);
            throw new RuntimeException("Encryption failed", e);
        }
        return entity;
    }

    @Override
    public IDCard onAfterConvert(IDCard entity, Document document, String collection) {
        log.debug("IDCardEncryptionListener: Decrypting IDCard after loading from DB");
        try {
            // 1. Decrypt idCardNumber
            if (entity.getIdCardNumber() != null) {
                entity.setIdCardNumber(EncryptionUtils.decrypt(entity.getIdCardNumber()));
            }

            // 2. Decrypt extractedData
            if (entity.getExtractedData() != null && entity.getExtractedData().containsKey("encrypted")) {
                Object encVal = entity.getExtractedData().get("encrypted");
                if (encVal instanceof String) {
                    String decryptedJson = EncryptionUtils.decrypt((String) encVal);
                    Map<String, Object> data = objectMapper.readValue(decryptedJson, new TypeReference<Map<String, Object>>() {});
                    entity.setExtractedData(data);
                }
            }

            // 3. Decrypt manualData
            if (entity.getManualData() != null && entity.getManualData().containsKey("encrypted")) {
                Object encVal = entity.getManualData().get("encrypted");
                if (encVal instanceof String) {
                    String decryptedJson = EncryptionUtils.decrypt((String) encVal);
                    Map<String, Object> data = objectMapper.readValue(decryptedJson, new TypeReference<Map<String, Object>>() {});
                    entity.setManualData(data);
                }
            }
        } catch (Exception e) {
            log.error("IDCardEncryptionListener: Failed to decrypt IDCard ID: {}", entity.getId(), e);
        }
        return entity;
    }
}
