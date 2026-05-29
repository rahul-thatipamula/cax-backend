package com.cax.cax_backend.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public class CarouselEnums {

    public enum CarouselType {
        BANNER("banner"),
        PROMOTIONAL("promotional"),
        FEATURED("featured"),
        OFFER("offer"),
        FESTIVAL("festival"),
        ANNOUNCEMENT("announcement"),
        SPONSOR("sponsor");
        
        private final String value;
        
        CarouselType(String v) { 
            this.value = v; 
        }
        
        @JsonValue 
        public String getValue() { 
            return value; 
        }
        
        public static CarouselType fromValue(String value) {
            if (value == null) {
                return null;
            }
            for (CarouselType type : CarouselType.values()) {
                if (type.value.equalsIgnoreCase(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("No enum constant " + CarouselType.class.getCanonicalName() + "." + value);
        }
    }

    public enum VerificationStatus {
        PENDING("pending"), APPROVED("approved"), REJECTED("rejected"), MANUAL_FILL("manual_fill");
        private final String value;
        VerificationStatus(String v) { this.value = v; }
        @JsonValue public String getValue() { return value; }
    }
}
