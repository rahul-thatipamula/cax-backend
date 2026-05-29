package com.cax.cax_backend.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public class FeedEnums {

    public enum PostStatus {
        ACTIVE("active"), EDITED("edited"), HIDDEN("hidden"), REMOVED("removed"),
        FLAGGED("flagged"), PENDING_REVIEW("pending_review"), REJECTED("rejected"), ARCHIVED("archived");
        private final String value;
        PostStatus(String v) { this.value = v; }
        @JsonValue public String getValue() { return value; }
    }

    public enum MediaType {
        IMAGE("image"), VIDEO("video"), PDF("pdf"), DOC("doc"), AUDIO("audio");
        private final String value;
        MediaType(String v) { this.value = v; }
        @JsonValue public String getValue() { return value; }
    }

    public enum ModerationAction {
        HIDE("hide"), REJECT("reject"), RESTORE("restore"),
        WARN("warn"), DELETE("delete"), APPROVE("approve");
        private final String value;
        ModerationAction(String v) { this.value = v; }
        @JsonValue public String getValue() { return value; }
    }
}
