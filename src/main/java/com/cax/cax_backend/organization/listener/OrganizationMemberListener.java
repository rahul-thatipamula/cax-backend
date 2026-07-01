package com.cax.cax_backend.organization.listener;

import com.cax.cax_backend.organization.model.OrganizationMember;
import com.cax.cax_backend.common.util.EncryptionUtils;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterLoadEvent;
import org.springframework.data.mongodb.core.mapping.event.BeforeSaveEvent;
import org.springframework.stereotype.Component;
import org.bson.Document;

@Component
public class OrganizationMemberListener extends AbstractMongoEventListener<OrganizationMember> {
    
    @Override
    public void onBeforeSave(BeforeSaveEvent<OrganizationMember> event) {
        OrganizationMember member = event.getSource();
        Document document = event.getDocument();
        if (member != null && document != null) {
            String name = member.getName();
            if (name != null) {
                document.put("name", EncryptionUtils.encryptIfNeeded(name));
            }
        }
    }

    @Override
    public void onAfterLoad(AfterLoadEvent<OrganizationMember> event) {
        Document document = event.getDocument();
        if (document != null) {
            String name = document.getString("name");
            if (name != null) {
                try {
                    document.put("name", EncryptionUtils.decrypt(name));
                } catch (Exception e) {
                    // Keep as-is if already decrypted or legacy plaintext
                }
            }
        }
    }
}
