package com.cax.cax_backend.event.listener;

import com.cax.cax_backend.event.model.EventParticipant;
import com.cax.cax_backend.common.util.EncryptionUtils;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterLoadEvent;
import org.springframework.data.mongodb.core.mapping.event.BeforeSaveEvent;
import org.springframework.stereotype.Component;
import org.bson.Document;
import java.util.List;

@Component
public class EventParticipantListener extends AbstractMongoEventListener<EventParticipant> {
    
    @Override
    public void onBeforeSave(BeforeSaveEvent<EventParticipant> event) {
        EventParticipant p = event.getSource();
        Document doc = event.getDocument();
        if (p != null && doc != null) {
            if (p.getName() != null) {
                doc.put("name", EncryptionUtils.encryptIfNeeded(p.getName()));
            }
            if (p.getIdCardNumber() != null) {
                doc.put("idCardNumber", EncryptionUtils.encryptIfNeeded(p.getIdCardNumber()));
            }
            if (p.getUtrNumber() != null) {
                doc.put("utrNumber", EncryptionUtils.encryptIfNeeded(p.getUtrNumber()));
            }
            
            try {
                List<Document> history = doc.getList("paymentHistory", Document.class);
                if (history != null) {
                    for (Document entry : history) {
                        String utr = entry.getString("utrNumber");
                        if (utr != null) {
                            entry.put("utrNumber", EncryptionUtils.encryptIfNeeded(utr));
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore if mapping structure differs in test mocks
            }
        }
    }

    @Override
    public void onAfterLoad(AfterLoadEvent<EventParticipant> event) {
        Document doc = event.getDocument();
        if (doc != null) {
            decryptField(doc, "name");
            decryptField(doc, "idCardNumber");
            decryptField(doc, "utrNumber");
            
            try {
                List<Document> history = doc.getList("paymentHistory", Document.class);
                if (history != null) {
                    for (Document entry : history) {
                        decryptField(entry, "utrNumber");
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    private void decryptField(Document doc, String fieldName) {
        String val = doc.getString(fieldName);
        if (val != null) {
            try {
                doc.put(fieldName, EncryptionUtils.decrypt(val));
            } catch (Exception e) {
                // Keep as-is if already decrypted or legacy plaintext
            }
        }
    }
}
